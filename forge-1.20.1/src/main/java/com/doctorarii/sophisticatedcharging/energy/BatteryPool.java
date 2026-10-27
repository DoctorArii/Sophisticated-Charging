package com.doctorarii.sophisticatedcharging.energy;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.energy.IEnergyStorage;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import net.p3pp3rf1y.sophisticatedcore.inventory.InventoryHandler;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.function.BooleanSupplier;
import java.util.function.ToIntFunction;

/**
 * All batteries in a backpack seen as one energy storage: the backpack's own Battery Upgrade (if any) plus every battery item
 * in its inventory. The energy always stays in those items; the pool holds none itself, so nothing can be duplicated by
 * dropping, placing or copying the backpack.
 * <p>
 * Fill order: the Battery Upgrade first, then batteries from the first slot on. Drain order: batteries from the last slot
 * back, then the Battery Upgrade.
 */
public class BatteryPool implements IEnergyStorage {
	/** Batteries added to the backpack are picked up within this time even if nothing else notices them. */
	private static final long RESCAN_NANOS = 1_000_000_000L;

	private final IStorageWrapper storage;
	private final BooleanSupplier enabled;
	@Nullable
	private IEnergyStorage base;

	/** Battery slots in ascending order, and the stack seen in each (to notice when a slot changes). */
	private int[] slots = new int[0];
	private ItemStack[] stacks = new ItemStack[0];
	private long lastScan;
	private boolean dirty = true;

	public BatteryPool(IStorageWrapper storage, BooleanSupplier enabled) {
		this.storage = storage;
		this.enabled = enabled;
	}

	public void setBase(@Nullable IEnergyStorage base) {
		this.base = base;
	}

	@Nullable
	public IEnergyStorage getBase() {
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
			stale = slots[i] >= inventory.getSlots() || inventory.getStackInSlot(slots[i]) != stacks[i];
		}
		if (!stale) {
			return;
		}
		int size = inventory.getSlots();
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
		slots = Arrays.copyOf(foundSlots, count);
		stacks = Arrays.copyOf(foundStacks, count);
		lastScan = now;
		dirty = false;
	}

	/** Moves energy through battery {@code i}; a real change is saved at once so the stored backpack matches the item. */
	private int transfer(int i, int amount, boolean simulate, boolean receive) {
		IEnergyStorage energy = BatteryRules.energyOf(stacks[i]);
		if (energy == null) {
			return 0;
		}
		int moved = receive ? energy.receiveEnergy(amount, simulate) : energy.extractEnergy(amount, simulate);
		if (moved > 0 && !simulate) {
			storage.getInventoryHandler().setStackInSlot(slots[i], stacks[i]);
		}
		return moved;
	}

	@Override
	public int receiveEnergy(int maxReceive, boolean simulate) {
		if (maxReceive <= 0) {
			return 0;
		}
		int received = base != null && base.canReceive() ? base.receiveEnergy(maxReceive, simulate) : 0;
		if (enabled.getAsBoolean()) {
			refresh();
			for (int i = 0; i < slots.length && received < maxReceive; i++) {
				received += transfer(i, maxReceive - received, simulate, true);
			}
		}
		return received;
	}

	@Override
	public int extractEnergy(int maxExtract, boolean simulate) {
		if (maxExtract <= 0) {
			return 0;
		}
		int extracted = 0;
		if (enabled.getAsBoolean()) {
			refresh();
			for (int i = slots.length - 1; i >= 0 && extracted < maxExtract; i--) {
				extracted += transfer(i, maxExtract - extracted, simulate, false);
			}
		}
		if (extracted < maxExtract && base != null && base.canExtract()) {
			extracted += base.extractEnergy(maxExtract - extracted, simulate);
		}
		return extracted;
	}

	private long sum(ToIntFunction<IEnergyStorage> value) {
		long total = base != null ? value.applyAsInt(base) : 0;
		if (enabled.getAsBoolean()) {
			refresh();
			for (ItemStack stack : stacks) {
				IEnergyStorage energy = BatteryRules.energyOf(stack);
				if (energy != null) {
					total += value.applyAsInt(energy);
				}
			}
		}
		return total;
	}

	public long getEnergyStoredLong() {
		return sum(IEnergyStorage::getEnergyStored);
	}

	public long getMaxEnergyStoredLong() {
		return sum(IEnergyStorage::getMaxEnergyStored);
	}

	@Override
	public int getEnergyStored() {
		return (int) Math.min(Integer.MAX_VALUE, getEnergyStoredLong());
	}

	@Override
	public int getMaxEnergyStored() {
		return (int) Math.min(Integer.MAX_VALUE, getMaxEnergyStoredLong());
	}

	@Override
	public boolean canExtract() {
		return true;
	}

	@Override
	public boolean canReceive() {
		return true;
	}
}
