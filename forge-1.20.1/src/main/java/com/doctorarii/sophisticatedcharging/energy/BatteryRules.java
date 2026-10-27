package com.doctorarii.sophisticatedcharging.energy;

import com.doctorarii.sophisticatedcharging.Config;
import com.doctorarii.sophisticatedcharging.SophisticatedCharging;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TieredItem;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackItem;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Decides which items are batteries (energy sources for the pool) and which items get charged.
 * <p>
 * Mods don't agree on how to say "I am a battery": Mekanism lets machines pull energy out of all its items (so its tools,
 * armor and devices all report {@code canExtract}), Powah batteries say they can't give energy while empty. So a battery
 * is recognised by what it does: put a little energy into a copy and see if it gives some back. Armor, tools and weapons
 * are never batteries. Tags override all of this.
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
	public static IEnergyStorage energyOf(ItemStack stack) {
		return stack.isEmpty() ? null : stack.getCapability(ForgeCapabilities.ENERGY).resolve().orElse(null);
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
	public static boolean isBatteryKind(ItemStack stack, IEnergyStorage energy) {
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
	 * Puts a little energy into a copy and checks whether it gives energy back on a simulated pull. Afterwards the added energy
	 * is taken out again, so items that share a remote store end up unchanged. Null = can't tell.
	 */
	@Nullable
	private static Boolean givesEnergyBack(ItemStack stack) {
		ItemStack copy = stack.copy();
		IEnergyStorage energy = energyOf(copy);
		if (energy == null) {
			return null;
		}
		int added = 0;
		if (energy.getEnergyStored() <= 0) {
			added = energy.receiveEnergy(PROBE_FE, false);
			energy = energyOf(copy);
			if (energy == null) {
				return null;
			}
		}
		int stored = energy.getEnergyStored();
		if (stored <= 0) {
			return null;
		}
		boolean gives = energy.extractEnergy(Math.min(stored, PROBE_FE), true) > 0;
		if (added > 0) {
			energy.extractEnergy(added, false);
		}
		return gives;
	}

	/** Can this stack, sitting in a backpack, be part of the shared battery pool? */
	public static boolean isPoolBattery(ItemStack stack) {
		if (stack.isEmpty() || stack.getCount() != 1 || isBackpack(stack)) {
			return false;
		}
		IEnergyStorage energy = energyOf(stack);
		return energy != null && isPoolBattery(stack, energy);
	}

	public static boolean isPoolBattery(ItemStack stack, IEnergyStorage energy) {
		if (energy.getMaxEnergyStored() <= 0 || !isBatteryKind(stack, energy) || isBannedBattery(stack)) {
			return false;
		}
		if (Config.isLoaded()) {
			int maxCapacity = Config.SERVER.maxBatteryCapacity.get();
			if (maxCapacity > 0 && energy.getMaxEnergyStored() > maxCapacity) {
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
	public static boolean isChargeTarget(ItemStack stack, IEnergyStorage energy) {
		if (isBackpack(stack) || !energy.canReceive() || isBannedChargeTarget(stack)) {
			return false;
		}
		if (isBatteryKind(stack, energy)) {
			return Config.isLoaded() && Config.SERVER.chargeBatteriesOutsideBackpack.get();
		}
		return true;
	}

	/**
	 * Creative / infinite batteries: draining a copy does not lower its charge. Tested once per item type (and only when the
	 * copy has energy to test with); the energy taken from the copy is put back, so items that share a remote store
	 * (ender batteries and the like) end up unchanged.
	 */
	public static boolean isInfinite(ItemStack stack) {
		Boolean known = INFINITE_ITEMS.get(stack.getItem());
		if (known != null) {
			return known;
		}
		ItemStack copy = stack.copy();
		IEnergyStorage energy = energyOf(copy);
		if (energy == null) {
			return false;
		}
		int before = energy.getEnergyStored();
		if (before <= 0) {
			return false; // can't tell yet; decide when we see a charged one
		}
		int extracted = energy.extractEnergy(Math.min(before, PROBE_FE), false);
		if (extracted <= 0) {
			return false; // can't tell (e.g. a battery switched to "input only")
		}
		int after = energy.getEnergyStored();
		energy.receiveEnergy(extracted, false);
		boolean infinite = after >= before;
		INFINITE_ITEMS.put(stack.getItem(), infinite);
		if (infinite) {
			SophisticatedCharging.LOGGER.info("{} looks like an infinite energy source; it is not used as a battery (config: banInfiniteBatteries)",
					stack.getItem());
		}
		return infinite;
	}
}
