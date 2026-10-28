package com.doctorarii.sophisticatedcharging.upgrade;

import com.doctorarii.sophisticatedcharging.Config;
import com.doctorarii.sophisticatedcharging.compat.CuriosCompat;
import com.doctorarii.sophisticatedcharging.energy.BatteryPool;
import com.doctorarii.sophisticatedcharging.energy.BatteryRules;
import com.doctorarii.sophisticatedcharging.energy.Transactions;
import com.doctorarii.sophisticatedcharging.init.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.p3pp3rf1y.sophisticatedbackpacks.api.IEnergyHandlerUpgradeWrapper;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import net.p3pp3rf1y.sophisticatedcore.init.ModCoreDataComponents;
import net.p3pp3rf1y.sophisticatedcore.inventory.InventoryHandler;
import net.p3pp3rf1y.sophisticatedcore.renderdata.RenderData;
import net.p3pp3rf1y.sophisticatedcore.upgrades.FilterLogic;
import net.p3pp3rf1y.sophisticatedcore.upgrades.IRenderedBatteryUpgrade;
import net.p3pp3rf1y.sophisticatedcore.upgrades.ITickableUpgrade;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeWrapperBase;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Links every battery in the backpack into one pool (exposed to SB as the backpack's energy handler) and, while the
 * backpack is carried, worn or placed, charges items from that pool. The pool's charge is shown on the backpack model.
 */
public class ChargingUpgradeWrapper extends UpgradeWrapperBase<ChargingUpgradeWrapper, ChargingUpgradeItem>
		implements ITickableUpgrade, IEnergyHandlerUpgradeWrapper, IRenderedBatteryUpgrade {
	/** Head to feet, like the armor slots from the top. */
	private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	@Nullable
	private final FilterLogic filterLogic;
	private final BatteryPool pool;
	private boolean baseResolved = false;
	@Nullable
	private Consumer<RenderData.BatteryRenderData> renderDataCallback;

	public ChargingUpgradeWrapper(IStorageWrapper storageWrapper, ItemStack upgrade, Consumer<ItemStack> upgradeSaveHandler) {
		super(storageWrapper, upgrade, upgradeSaveHandler);
		filterLogic = upgradeItem.isAdvanced()
				? new FilterLogic(upgrade, upgradeSaveHandler, upgradeItem.getFilterSlotCount(), s -> true, ModCoreDataComponents.FILTER_ATTRIBUTES)
				: null;
		pool = new BatteryPool(storageWrapper, this::isEnabled);
	}

	public boolean isAdvanced() {
		return upgradeItem.isAdvanced();
	}

	@Nullable
	public FilterLogic getFilterLogic() {
		return filterLogic;
	}

	/** Called by Sophisticated Backpacks when it builds the backpack's energy handler. */
	@Override
	public EnergyHandler wrapStorage(@Nullable EnergyHandler energyStorage) {
		pool.setBase(energyStorage);
		baseResolved = true;
		return pool;
	}

	/** The battery pool, with the backpack's Battery Upgrade (or whatever SB put below us) as its base. */
	public BatteryPool getPool() {
		if (!baseResolved) {
			storageWrapper.getEnergyHandler(); // lets SB call wrapStorage on us with the right base
			if (!baseResolved) {
				pool.setBase(storageWrapper.getUpgradeHandler().getWrappersThatImplementFromMainStorage(EnergyHandler.class).stream().findFirst()
						.orElse(null));
				baseResolved = true;
			}
		}
		return pool;
	}

	@Override
	public void tick(@Nullable Entity entity, Level level, BlockPos pos) {
		if (level.isClientSide() || isInCooldown(level)) {
			return;
		}
		int interval = Config.isLoaded() ? Config.SERVER.chargeInterval.get() : 10;
		setCooldown(level, interval);
		if (isEnabled()) {
			chargeOnce(entity, (long) upgradeItem.getChargeRate() * interval, interval);
		}
		forceUpdateBatteryRenderData();
	}

	@Override
	public void setBatteryRenderDataUpdateCallback(Consumer<RenderData.BatteryRenderData> callback) {
		renderDataCallback = callback;
	}

	/**
	 * Shows the pool's charge on the backpack model. SB's own Battery Upgrade in the same backpack reports through here too,
	 * so the model always shows the whole pool.
	 */
	@Override
	public void forceUpdateBatteryRenderData() {
		if (renderDataCallback == null) {
			return;
		}
		for (IRenderedBatteryUpgrade other : storageWrapper.getUpgradeHandler().getWrappersThatImplement(IRenderedBatteryUpgrade.class)) {
			if (other != this) {
				other.setBatteryRenderDataUpdateCallback(data -> forceUpdateBatteryRenderData());
			}
		}
		BatteryPool batteryPool = getPool();
		long capacity = batteryPool.getCapacityAsLong();
		renderDataCallback.accept(new RenderData.BatteryRenderData(capacity > 0 ? (float) ((double) batteryPool.getAmountAsLong() / capacity) : 0f));
	}

	/**
	 * One charge cycle: moves up to {@code budget} FE from the pool into the items this upgrade charges. {@code ticks} is how many
	 * ticks the cycle stands for; each item gets that many transfers, so items that limit how much they take per transfer
	 * still charge at their full per-tick speed. Returns FE moved.
	 */
	public int chargeOnce(@Nullable Entity entity, long budget, int ticks) {
		ChargeRun run = new ChargeRun(getPool(), (int) Math.min(Integer.MAX_VALUE, Math.max(0, budget)), Math.max(1, ticks));
		if (run.remaining <= 0 || run.pool.getAmountAsLong() <= 0) {
			return 0;
		}
		boolean advanced = isAdvanced();
		if (entity instanceof Player player) {
			for (EquipmentSlot slot : ARMOR_SLOTS) {
				run.charge(player.getItemBySlot(slot), null);
			}
			Inventory inventory = player.getInventory();
			run.charge(inventory.getSelectedItem(), null);
			run.charge(player.getOffhandItem(), null);
			if (advanced && chargesInventory() && Config.SERVER.allowInventoryCharging.get()) {
				NonNullList<ItemStack> items = inventory.getNonEquipmentItems();
				for (int i = 0; i < items.size(); i++) {
					if (i != inventory.getSelectedSlot()) {
						run.charge(items.get(i), null);
					}
				}
			}
			if (advanced && chargesCurios() && Config.SERVER.allowCuriosCharging.get() && CuriosCompat.isLoaded()) {
				CuriosCompat.forEachEquipped(player, stack -> run.charge(stack, null));
			}
		}
		if (advanced && chargesContents() && Config.SERVER.allowContentsCharging.get()) {
			InventoryHandler contents = storageWrapper.getInventoryHandler();
			for (int slot = 0; slot < contents.size() && run.remaining > 0; slot++) {
				ItemStack stack = contents.getStackInSlot(slot);
				if (!stack.isEmpty() && !run.pool.isBatterySlot(slot)) {
					int s = slot;
					run.charge(stack, () -> contents.setStackInSlot(s, stack));
				}
			}
		}
		return run.moved;
	}

	private final class ChargeRun {
		private final BatteryPool pool;
		private final int transfersPerItem;
		private int remaining;
		private int moved = 0;

		private ChargeRun(BatteryPool pool, int budget, int transfersPerItem) {
			this.pool = pool;
			this.remaining = budget;
			this.transfersPerItem = transfersPerItem;
		}

		/** Charges the stack in place; {@code persist} saves it where it lives once it changed. */
		void charge(ItemStack stack, @Nullable Runnable persist) {
			if (remaining <= 0 || stack.isEmpty() || stack.getCount() != 1) {
				return; // a stack of several would get the energy once per item
			}
			EnergyHandler target = BatteryRules.energyOf(stack);
			if (target == null || !BatteryRules.isChargeTarget(stack)) {
				return;
			}
			if (filterLogic != null && !filterLogic.matchesFilter(stack)) {
				return;
			}
			int given = 0;
			for (int i = 0; i < transfersPerItem && remaining > 0; i++) {
				int accepted = transfer(target);
				if (accepted <= 0) {
					break;
				}
				given += accepted;
				remaining -= accepted;
				moved += accepted;
			}
			if (given > 0 && persist != null) {
				persist.run();
			}
		}

		/** One transfer from the pool into the item, all or nothing: if anything doesn't add up, the transaction is rolled back. */
		private int transfer(EnergyHandler target) {
			try (Transaction transaction = Transactions.open()) {
				if (transaction == null) {
					return 0;
				}
				int wanted;
				try (Transaction simulation = Transaction.open(transaction)) {
					wanted = target.insert(remaining, simulation);
				}
				if (wanted <= 0) {
					return 0;
				}
				int taken = pool.extract(wanted, transaction);
				if (taken <= 0) {
					return 0;
				}
				int accepted = target.insert(taken, transaction);
				if (accepted < taken && pool.insert(taken - accepted, transaction) != taken - accepted) {
					return 0;
				}
				transaction.commit();
				return accepted;
			}
		}
	}

	// Settings of the Advanced Charging Upgrade, stored on the upgrade item.

	public boolean chargesInventory() {
		return getSetting(ModContent.CHARGE_INVENTORY, true);
	}

	public void setChargesInventory(boolean value) {
		setSetting(ModContent.CHARGE_INVENTORY, value);
	}

	public boolean chargesCurios() {
		return getSetting(ModContent.CHARGE_CURIOS, true);
	}

	public void setChargesCurios(boolean value) {
		setSetting(ModContent.CHARGE_CURIOS, value);
	}

	public boolean chargesContents() {
		return getSetting(ModContent.CHARGE_CONTENTS, false);
	}

	public void setChargesContents(boolean value) {
		setSetting(ModContent.CHARGE_CONTENTS, value);
	}

	private boolean getSetting(Supplier<DataComponentType<Boolean>> component, boolean defaultValue) {
		return upgrade.getOrDefault(component.get(), defaultValue);
	}

	private void setSetting(Supplier<DataComponentType<Boolean>> component, boolean value) {
		upgrade.set(component.get(), value);
		save();
	}
}
