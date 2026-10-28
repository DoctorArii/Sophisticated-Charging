package com.doctorarii.sophisticatedcharging.energy;

import com.doctorarii.sophisticatedcharging.Config;
import com.doctorarii.sophisticatedcharging.SophisticatedCharging;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TieredItem;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackItem;
import team.reborn.energy.api.EnergyStorage;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Decides which items are batteries (energy sources for the pool) and which items get charged.
 * <p>
 * Mods don't agree on how to say "I am a battery": some let machines pull energy out of all their items, some batteries
 * say they can't give energy while empty. So a battery is recognised by what it does: put a little energy into a copy and
 * see if it gives some back. Armor, tools and weapons are never batteries. Tags override all of this. Every test runs on
 * a detached copy inside a transaction that is always rolled back, so no real item is ever changed by it.
 */
public final class BatteryRules {
	/** Always treated as a battery. */
	public static final TagKey<Item> BATTERIES = TagKey.create(Registries.ITEM, SophisticatedCharging.rl("batteries"));
	/** Never treated as a battery: never drained, charged like gear. */
	public static final TagKey<Item> NOT_BATTERIES = TagKey.create(Registries.ITEM, SophisticatedCharging.rl("not_batteries"));
	/** Batteries that may not be used in the pool (they are still not charged, as batteries). */
	public static final TagKey<Item> BANNED_BATTERIES = TagKey.create(Registries.ITEM, SophisticatedCharging.rl("banned_batteries"));
	/** Never charged. */
	public static final TagKey<Item> BANNED_CHARGE_TARGETS = TagKey.create(Registries.ITEM, SophisticatedCharging.rl("banned_charge_targets"));

	private static final long PROBE_ENERGY = 1_000;
	private static final Map<Item, Boolean> BATTERY_ITEMS = new ConcurrentHashMap<>();
	private static final Map<Item, Boolean> INFINITE_ITEMS = new ConcurrentHashMap<>();

	private BatteryRules() {}

	public static void clearCaches() {
		BATTERY_ITEMS.clear();
		INFINITE_ITEMS.clear();
	}

	/** The energy of a detached copy of {@code stack}: for reading it and for tests, never for real transfers. */
	@Nullable
	public static EnergyStorage energyOf(ItemStack stack) {
		return stack.isEmpty() ? null : EnergyStorage.ITEM.find(stack, ContainerItemContext.withInitial(stack));
	}

	/** Opens a transaction inside the current one if there is one (e.g. a cable's), else a new outer one. */
	public static Transaction openTransaction() {
		TransactionContext current = Transaction.getCurrentUnsafe();
		return current != null ? current.openNested() : Transaction.openOuter();
	}

	/** Backpacks expose their own energy (and ours); they are never batteries or charge targets, or energy would loop. */
	public static boolean isBackpack(ItemStack stack) {
		return stack.getItem() instanceof BackpackItem;
	}

	/** Armor and other wearables, tools and weapons: things you use, never energy sources. */
	public static boolean isGear(ItemStack stack) {
		Item item = stack.getItem();
		return Equipable.get(stack) != null || item instanceof TieredItem || item instanceof ProjectileWeaponItem
				|| item instanceof ShieldItem;
	}

	/** Is this kind of item a battery (something meant to store energy and give it back)? Says nothing about bans. */
	public static boolean isBatteryKind(ItemStack stack) {
		if (stack.is(BATTERIES)) {
			return true;
		}
		if (stack.is(NOT_BATTERIES) || isBackpack(stack) || isGear(stack)) {
			return false;
		}
		Boolean known = BATTERY_ITEMS.get(stack.getItem());
		if (known != null) {
			return known;
		}
		Boolean gives = givesEnergyBack(stack);
		if (gives == null) {
			return false; // empty and takes no energy: useless as a battery right now; asked again next time
		}
		BATTERY_ITEMS.put(stack.getItem(), gives);
		return gives;
	}

	/** Puts a little energy into a copy (if it is empty) and checks whether it gives energy back. Null = can't tell. */
	@Nullable
	private static Boolean givesEnergyBack(ItemStack stack) {
		EnergyStorage energy = energyOf(stack);
		if (energy == null) {
			return null;
		}
		try (Transaction probe = openTransaction()) {
			if (energy.getAmount() <= 0 && energy.supportsInsertion()) {
				energy.insert(PROBE_ENERGY, probe);
			}
			long stored = energy.getAmount();
			if (stored <= 0) {
				return null;
			}
			return energy.supportsExtraction() && energy.extract(Math.min(stored, PROBE_ENERGY), probe) > 0;
		}
	}

	/** Can this stack, sitting in a backpack, be part of the shared battery pool? */
	public static boolean isPoolBattery(ItemStack stack) {
		if (stack.isEmpty() || stack.getCount() != 1 || isBackpack(stack)) {
			return false;
		}
		EnergyStorage energy = energyOf(stack);
		if (energy == null || energy.getCapacity() <= 0 || !isBatteryKind(stack) || isBannedBattery(stack)) {
			return false;
		}
		if (Config.isLoaded()) {
			int maxCapacity = Config.SERVER.maxBatteryCapacity.get();
			if (maxCapacity > 0 && energy.getCapacity() > maxCapacity) {
				return false;
			}
			if (Config.SERVER.banInfiniteBatteries.get() && isInfinite(stack)) {
				return false;
			}
		}
		return true;
	}

	public static boolean isBannedBattery(ItemStack stack) {
		return stack.is(BANNED_BATTERIES) || Config.bannedBatteries().matches(stack);
	}

	public static boolean isBannedChargeTarget(ItemStack stack) {
		return stack.is(BANNED_CHARGE_TARGETS) || Config.bannedChargeTargets().matches(stack);
	}

	/** Can this stack be charged? Batteries outside the backpack are only charged when the config allows it. */
	public static boolean isChargeTarget(ItemStack stack, EnergyStorage energy) {
		if (isBackpack(stack) || !energy.supportsInsertion() || isBannedChargeTarget(stack)) {
			return false;
		}
		if (isBatteryKind(stack)) {
			return Config.isLoaded() && Config.SERVER.chargeBatteriesOutsideBackpack.get();
		}
		return true;
	}

	/**
	 * Creative / infinite batteries: draining a copy does not lower its charge. Tested once per item type (and only when the
	 * copy has energy to test with).
	 */
	public static boolean isInfinite(ItemStack stack) {
		Boolean known = INFINITE_ITEMS.get(stack.getItem());
		if (known != null) {
			return known;
		}
		EnergyStorage energy = energyOf(stack);
		if (energy == null) {
			return false;
		}
		long before = energy.getAmount();
		if (before <= 0 || !energy.supportsExtraction()) {
			return false; // can't tell yet; decide when we see a charged one
		}
		boolean infinite;
		try (Transaction probe = openTransaction()) {
			if (energy.extract(Math.min(before, PROBE_ENERGY), probe) <= 0) {
				return false; // can't tell (e.g. a battery switched to "input only")
			}
			infinite = energy.getAmount() >= before;
		}
		INFINITE_ITEMS.put(stack.getItem(), infinite);
		if (infinite) {
			SophisticatedCharging.LOGGER.info("{} looks like an infinite energy source; it is not used as a battery (config: banInfiniteBatteries)",
					stack.getItem());
		}
		return infinite;
	}
}
