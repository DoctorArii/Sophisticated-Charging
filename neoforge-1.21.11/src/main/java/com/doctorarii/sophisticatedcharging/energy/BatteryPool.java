package com.doctorarii.sophisticatedcharging.energy;

import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import net.p3pp3rf1y.sophisticatedcore.inventory.InventoryHandler;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.ToLongFunction;

/**
 * All batteries in a backpack seen as one energy handler: the backpack's own Battery Upgrade (if any) plus every battery item
 * in its inventory. The energy always stays in those items; the pool holds none itself, so nothing can be duplicated by
 * dropping, placing or copying the backpack.
 * <p>
 * Fill order: the Battery Upgrade first, then batteries from the first slot on. Drain order: batteries from the last slot
 * back, then the Battery Upgrade.
 * <p>
 * Transactions: each battery is changed through one {@link ItemAccess} on its stack, so every step of a transaction sees
 * the steps before it and an aborted transaction leaves the stack as it was. Once the root transaction commits, the changed
 * stacks are set into the backpack again so its saved contents match. The batteries' own energy is never moved through
 * the backpack inventory's insert/extract (that would run upgrades like the Void Upgrade on them).
 */
public class BatteryPool implements EnergyHandler {
	/** Rescanned at least this often anyway: ban lists, or what an item counts as, can change without any slot changing. */
	private static final long RESCAN_NANOS = 1_000_000_000L;

	private final IStorageWrapper storage;
	private final BooleanSupplier enabled;
	@Nullable
	private EnergyHandler base;

	/** Battery slots in ascending order, the stack in each and the access to it. */
	private int[] slots = new int[0];
	private ItemStack[] stacks = new ItemStack[0];
	private ItemAccess[] accesses = new ItemAccess[0];
	/**
	 * Every slot's stack at the last scan, and the inventory handler they came from: another stack object in any slot (or a
	 * new handler) means the contents changed. Energy moves change battery stacks in place, so they don't count as a change.
	 */
	private ItemStack[] seen = new ItemStack[0];
	@Nullable
	private InventoryHandler scannedInventory;
	private long lastScan;
	private boolean dirty = true;
	private final ChangedSlots changed = new ChangedSlots();

	public BatteryPool(IStorageWrapper storage, BooleanSupplier enabled) {
		this.storage = storage;
		this.enabled = enabled;
	}

	public void setBase(@Nullable EnergyHandler base) {
		this.base = base;
	}

	@Nullable
	public EnergyHandler getBase() {
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
		if (changed.isBusy()) {
			return; // keep the same batteries (and accesses) until the transaction that changed them is done
		}
		InventoryHandler inventory = storage.getInventoryHandler();
		long now = System.nanoTime();
		int size = inventory.size();
		if (!dirty && inventory == scannedInventory && size == seen.length && now - lastScan <= RESCAN_NANOS && unchanged(inventory)) {
			return;
		}
		boolean hadBatteries = slots.length > 0;
		boolean firstScan = scannedInventory == null;
		ItemStack[] seenNow = new ItemStack[size];
		int[] foundSlots = new int[size];
		ItemStack[] foundStacks = new ItemStack[size];
		ItemAccess[] foundAccesses = new ItemAccess[size];
		int count = 0;
		for (int slot = 0; slot < size; slot++) {
			ItemStack stack = inventory.getStackInSlot(slot);
			seenNow[slot] = stack;
			if (BatteryRules.isPoolBattery(stack)) {
				foundSlots[count] = slot;
				foundStacks[count] = stack;
				foundAccesses[count++] = ItemAccess.forStack(stack);
			}
		}
		slots = Arrays.copyOf(foundSlots, count);
		stacks = Arrays.copyOf(foundStacks, count);
		accesses = Arrays.copyOf(foundAccesses, count);
		seen = seenNow;
		scannedInventory = inventory;
		lastScan = now;
		dirty = false;
		if (!firstScan && hadBatteries != count > 0) {
			// SB hides an energy handler with no capacity, and a placed backpack keeps what it handed out until its handlers
			// are refreshed: without this, cables next to it would not see the first battery put in (or the last one taken out).
			storage.refreshInventoryForInputOutput();
		}
	}

	private boolean unchanged(InventoryHandler inventory) {
		for (int slot = 0; slot < seen.length; slot++) {
			if (inventory.getStackInSlot(slot) != seen[slot]) {
				return false;
			}
		}
		return true;
	}

	/** Moves energy into or out of battery {@code i}; a real change is noted so the stack is saved into the backpack on commit. */
	private int transfer(int i, int amount, boolean receive, TransactionContext transaction) {
		EnergyHandler energy = BatteryRules.energyOf(accesses[i]);
		if (energy == null) {
			return 0;
		}
		int moved = receive ? energy.insert(amount, transaction) : energy.extract(amount, transaction);
		if (moved > 0) {
			changed.updateSnapshots(transaction);
			changed.slots.put(slots[i], new ChangedBattery(stacks[i], accesses[i]));
		}
		return moved;
	}

	@Override
	public int insert(int amount, TransactionContext transaction) {
		TransferPreconditions.checkNonNegative(amount);
		if (amount == 0) {
			return 0;
		}
		int received = base != null ? base.insert(amount, transaction) : 0;
		if (enabled.getAsBoolean()) {
			refresh();
			for (int i = 0; i < slots.length && received < amount; i++) {
				received += transfer(i, amount - received, true, transaction);
			}
		}
		return received;
	}

	@Override
	public int extract(int amount, TransactionContext transaction) {
		TransferPreconditions.checkNonNegative(amount);
		if (amount == 0) {
			return 0;
		}
		int extracted = 0;
		if (enabled.getAsBoolean()) {
			refresh();
			for (int i = slots.length - 1; i >= 0 && extracted < amount; i--) {
				extracted += transfer(i, amount - extracted, false, transaction);
			}
		}
		if (extracted < amount && base != null) {
			extracted += base.extract(amount - extracted, transaction);
		}
		return extracted;
	}

	private long sum(ToLongFunction<EnergyHandler> value) {
		long total = base != null ? value.applyAsLong(base) : 0;
		if (enabled.getAsBoolean()) {
			refresh();
			for (ItemAccess access : accesses) {
				EnergyHandler energy = BatteryRules.energyOf(access);
				if (energy != null) {
					total += value.applyAsLong(energy);
				}
			}
		}
		return total;
	}

	@Override
	public long getAmountAsLong() {
		return sum(EnergyHandler::getAmountAsLong);
	}

	@Override
	public long getCapacityAsLong() {
		return sum(EnergyHandler::getCapacityAsLong);
	}

	private record ChangedBattery(ItemStack stack, ItemAccess access) {
		/**
		 * Writes the committed state into the stack itself. Root commit callbacks run in no fixed order, so the access's own
		 * callback (which does the same, harmlessly twice) may not have run yet when the stack is saved.
		 */
		void applyCommittedState() {
			ItemResource committed = access.getResource();
			if (committed.is(stack.getItem()) && stack.getComponents() instanceof PatchedDataComponentMap components) {
				components.restorePatch(committed.getComponentsPatch());
			}
		}
	}

	/** Batteries changed in the current transaction (by slot), saved into the backpack when it commits. */
	private final class ChangedSlots extends SnapshotJournal<Map<Integer, ChangedBattery>> {
		private Map<Integer, ChangedBattery> slots = new LinkedHashMap<>();
		/** Snapshots not released yet: each one is released exactly once, after its transaction (and root commit) is done. */
		private int openSnapshots;

		boolean isBusy() {
			return openSnapshots > 0;
		}

		@Override
		protected Map<Integer, ChangedBattery> createSnapshot() {
			openSnapshots++;
			return new LinkedHashMap<>(slots);
		}

		@Override
		protected void releaseSnapshot(Map<Integer, ChangedBattery> snapshot) {
			openSnapshots--;
		}

		@Override
		protected void revertToSnapshot(Map<Integer, ChangedBattery> snapshot) {
			slots = snapshot;
		}

		@Override
		protected void onRootCommit(Map<Integer, ChangedBattery> originalState) {
			Map<Integer, ChangedBattery> toSave = slots;
			slots = new LinkedHashMap<>();
			InventoryHandler inventory = storage.getInventoryHandler();
			toSave.forEach((slot, battery) -> {
				if (slot < inventory.size() && inventory.getStackInSlot(slot) == battery.stack()) {
					battery.applyCommittedState();
					inventory.setStackInSlot(slot, battery.stack());
				}
			});
		}
	}
}
