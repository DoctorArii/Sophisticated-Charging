package com.doctorarii.sophisticatedcharging.energy;

import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.world.item.ItemStack;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import net.p3pp3rf1y.sophisticatedcore.inventory.InventoryHandler;
import team.reborn.energy.api.EnergyStorage;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.function.BooleanSupplier;
import java.util.function.ToLongFunction;

/**
 * All batteries in a backpack seen as one energy storage: the backpack's own Battery Upgrade (if any) plus every battery item
 * in its inventory. The energy always stays in those items; the pool holds none itself, so nothing can be duplicated by
 * dropping, placing or copying the backpack. Transfers run in the caller's transaction against each battery's backpack slot,
 * so a rolled-back transfer leaves every battery as it was.
 * <p>
 * Fill order: the Battery Upgrade first, then batteries from the first slot on. Drain order: batteries from the last slot
 * back, then the Battery Upgrade.
 */
public class BatteryPool implements EnergyStorage {
	/** Batteries added to the backpack are picked up within this time even if nothing else notices them. */
	private static final long RESCAN_NANOS = 1_000_000_000L;

	private final IStorageWrapper storage;
	private final BooleanSupplier enabled;
	@Nullable
	private EnergyStorage base;

	/** Battery slots in ascending order, and the stack seen in each (to notice when a slot changes). */
	private int[] slots = new int[0];
	private ItemStack[] stacks = new ItemStack[0];
	private boolean scanned = false;
	private long lastScan;
	private boolean dirty = true;

	public BatteryPool(IStorageWrapper storage, BooleanSupplier enabled) {
		this.storage = storage;
		this.enabled = enabled;
	}

	public void setBase(@Nullable EnergyStorage base) {
		this.base = base;
	}

	@Nullable
	public EnergyStorage getBase() {
		return base;
	}

	public boolean isBatterySlot(int slot) {
		refresh();
		return Arrays.binarySearch(slots, slot) >= 0;
	}

	public int getBatteryCount() {
		refresh();
		return slots.length;
	}

	private void refresh() {
		InventoryHandler inventory = storage.getInventoryHandler();
		long now = System.nanoTime();
		boolean stale = dirty || now - lastScan > RESCAN_NANOS;
		for (int i = 0; !stale && i < slots.length; i++) {
			if (slots[i] >= inventory.getSlotCount()) {
				stale = true;
				break;
			}
			ItemStack current = inventory.getStackInSlot(slots[i]);
			if (current != stacks[i]) {
				// Energy changes replace the slot's stack with a new one: same battery, keep it without a full rescan.
				if (current.getItem() == stacks[i].getItem() && current.getCount() == 1) {
					stacks[i] = current;
				} else {
					stale = true;
				}
			}
		}
		if (!stale) {
			return;
		}
		int size = inventory.getSlotCount();
		int[] foundSlots = new int[size];
		ItemStack[] foundStacks = new ItemStack[size];
		int count = 0;
		for (int slot = 0; slot < size; slot++) {
			ItemStack stack = inventory.getStackInSlot(slot);
			if (BatteryRules.isPoolBattery(stack)) {
				foundSlots[count] = slot;
				foundStacks[count++] = stack;
			}
		}
		boolean hadBatteries = slots.length > 0;
		slots = Arrays.copyOf(foundSlots, count);
		stacks = Arrays.copyOf(foundStacks, count);
		lastScan = now;
		dirty = false;
		boolean firstScan = !scanned;
		scanned = true;
		if (!firstScan && hadBatteries != count > 0) {
			// SB hides an energy storage with no capacity, and a placed backpack keeps what it handed to cables until its handlers
			// are refreshed: without this, cables next to it would not see the first battery put in (or the last one taken out).
			storage.refreshInventoryForInputOutput();
		}
	}

	/** The energy of battery {@code i} in its backpack slot: changes go straight into the backpack. */
	@Nullable
	private EnergyStorage slotEnergy(int i) {
		InventoryHandler inventory = storage.getInventoryHandler();
		ItemStack stack = inventory.getStackInSlot(slots[i]);
		return stack.isEmpty() ? null : EnergyStorage.ITEM.find(stack, ContainerItemContext.ofSingleSlot(inventory.getSlot(slots[i])));
	}

	@Override
	public long insert(long maxAmount, TransactionContext transaction) {
		if (maxAmount <= 0) {
			return 0;
		}
		long inserted = base != null && base.supportsInsertion() ? base.insert(maxAmount, transaction) : 0;
		if (enabled.getAsBoolean()) {
			refresh();
			for (int i = 0; i < slots.length && inserted < maxAmount; i++) {
				EnergyStorage energy = slotEnergy(i);
				if (energy != null && energy.supportsInsertion()) {
					inserted += energy.insert(maxAmount - inserted, transaction);
				}
			}
		}
		return inserted;
	}

	@Override
	public long extract(long maxAmount, TransactionContext transaction) {
		if (maxAmount <= 0) {
			return 0;
		}
		long extracted = 0;
		if (enabled.getAsBoolean()) {
			refresh();
			for (int i = slots.length - 1; i >= 0 && extracted < maxAmount; i--) {
				EnergyStorage energy = slotEnergy(i);
				if (energy != null && energy.supportsExtraction()) {
					extracted += energy.extract(maxAmount - extracted, transaction);
				}
			}
		}
		if (extracted < maxAmount && base != null && base.supportsExtraction()) {
			extracted += base.extract(maxAmount - extracted, transaction);
		}
		return extracted;
	}

	private long sum(ToLongFunction<EnergyStorage> value) {
		long total = base != null ? value.applyAsLong(base) : 0;
		if (enabled.getAsBoolean()) {
			refresh();
			for (ItemStack stack : stacks) {
				EnergyStorage energy = BatteryRules.energyOf(stack);
				if (energy != null) {
					total += value.applyAsLong(energy);
				}
			}
		}
		return total;
	}

	@Override
	public long getAmount() {
		return sum(EnergyStorage::getAmount);
	}

	@Override
	public long getCapacity() {
		return sum(EnergyStorage::getCapacity);
	}

	/** Same as {@link #getAmount()}; named like the Forge versions so shared code reads the same. */
	public long getEnergyStoredLong() {
		return getAmount();
	}

	public long getMaxEnergyStoredLong() {
		return getCapacity();
	}
}
