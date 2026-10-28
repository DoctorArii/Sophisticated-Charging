package com.doctorarii.sophisticatedcharging.energy;

import com.doctorarii.sophisticatedcharging.Config;
import com.doctorarii.sophisticatedcharging.SophisticatedCharging;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackItem;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Decides which items are batteries (energy sources for the pool) and which items get charged.
 * <p>
 * Every energy item can be asked to give energy back, and mods don't agree on how to say "I am a battery": Mekanism lets
 * machines pull energy out of all its items (tools, armor and devices too), Powah batteries refuse while empty. So a
 * battery is recognised by what it does: put a little energy into a copy and see if it gives some back. Armor, tools and
 * weapons are never batteries. Tags override all of this.
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

	private static final int PROBE_FE = 1_000;
	private static final Map<Item, Boolean> BATTERY_ITEMS = new ConcurrentHashMap<>();
	private static final Map<Item, Boolean> INFINITE_ITEMS = new ConcurrentHashMap<>();

	private BatteryRules() {}

	public static void clearCaches() {
		BATTERY_ITEMS.clear();
		INFINITE_ITEMS.clear();
	}

	@Nullable
	public static EnergyHandler energyOf(ItemAccess access) {
		return access.getCapability(Capabilities.Energy.ITEM);
	}

	/** The energy of a stack; committed changes are written into the stack itself. */
	@Nullable
	public static EnergyHandler energyOf(ItemStack stack) {
		return stack.isEmpty() ? null : energyOf(ItemAccess.forStack(stack));
	}

	/** Backpacks expose their own energy (and ours); they are never batteries or charge targets, or energy would loop. */
	public static boolean isBackpack(ItemStack stack) {
		return stack.getItem() instanceof BackpackItem;
	}

	/** Armor and other wearables, tools and weapons: things you use, never energy sources. */
	public static boolean isGear(ItemStack stack) {
		return stack.has(DataComponents.EQUIPPABLE) || stack.has(DataComponents.TOOL) || stack.has(DataComponents.WEAPON)
				|| stack.has(DataComponents.BLOCKS_ATTACKS) || stack.getItem() instanceof ProjectileWeaponItem;
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

	/**
	 * Puts a little energy into a copy and checks whether it gives energy back, all in a transaction that is never committed,
	 * so the item (and any remote store it shares) ends up unchanged. Null = can't tell.
	 */
	@Nullable
	private static Boolean givesEnergyBack(ItemStack stack) {
		try (Transaction probe = Transactions.open()) {
			EnergyHandler energy = probe == null ? null : energyOf(stack.copy());
			if (energy == null) {
				return null;
			}
			if (energy.getAmountAsLong() <= 0) {
				energy.insert(PROBE_FE, probe);
			}
			long stored = energy.getAmountAsLong();
			if (stored <= 0) {
				return null;
			}
			return energy.extract((int) Math.min(stored, PROBE_FE), probe) > 0;
		}
	}

	/** Can this stack, sitting in a backpack, be part of the shared battery pool? */
	public static boolean isPoolBattery(ItemStack stack) {
		if (stack.isEmpty() || stack.getCount() != 1 || isBackpack(stack)) {
			return false;
		}
		EnergyHandler energy = energyOf(stack);
		return energy != null && isPoolBattery(stack, energy);
	}

	public static boolean isPoolBattery(ItemStack stack, EnergyHandler energy) {
		long capacity = energy.getCapacityAsLong();
		if (capacity <= 0 || !isBatteryKind(stack) || isBannedBattery(stack)) {
			return false;
		}
		if (Config.isLoaded()) {
			int maxCapacity = Config.SERVER.maxBatteryCapacity.get();
			if (maxCapacity > 0 && capacity > maxCapacity) {
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
	public static boolean isChargeTarget(ItemStack stack) {
		if (isBackpack(stack) || isBannedChargeTarget(stack)) {
			return false;
		}
		if (isBatteryKind(stack)) {
			return Config.isLoaded() && Config.SERVER.chargeBatteriesOutsideBackpack.get();
		}
		return true;
	}

	/**
	 * Creative / infinite batteries: draining a copy does not lower its charge. Tested once per item type (and only when the
	 * copy has energy to test with), in a transaction that is never committed, so items that share a remote store (ender
	 * batteries and the like) end up unchanged.
	 */
	public static boolean isInfinite(ItemStack stack) {
		Boolean known = INFINITE_ITEMS.get(stack.getItem());
		if (known != null) {
			return known;
		}
		try (Transaction probe = Transactions.open()) {
			EnergyHandler energy = probe == null ? null : energyOf(stack.copy());
			if (energy == null) {
				return false;
			}
			long before = energy.getAmountAsLong();
			if (before <= 0) {
				return false; // can't tell yet; decide when we see a charged one
			}
			if (energy.extract((int) Math.min(before, PROBE_FE), probe) <= 0) {
				return false; // can't tell (e.g. a battery switched to "input only")
			}
			boolean infinite = energy.getAmountAsLong() >= before;
			INFINITE_ITEMS.put(stack.getItem(), infinite);
			if (infinite) {
				SophisticatedCharging.LOGGER.info("{} looks like an infinite energy source; it is not used as a battery (config: banInfiniteBatteries)",
						stack.getItem());
			}
			return infinite;
		}
	}
}
