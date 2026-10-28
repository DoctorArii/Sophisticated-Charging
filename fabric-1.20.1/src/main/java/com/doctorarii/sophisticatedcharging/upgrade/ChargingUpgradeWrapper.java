package com.doctorarii.sophisticatedcharging.upgrade;

import com.doctorarii.sophisticatedcharging.Config;
import com.doctorarii.sophisticatedcharging.energy.BatteryPool;
import com.doctorarii.sophisticatedcharging.energy.BatteryRules;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.item.PlayerInventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.p3pp3rf1y.sophisticatedbackpacks.api.IEnergyStorageUpgradeWrapper;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.IBackpackWrapper;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import net.p3pp3rf1y.sophisticatedcore.inventory.InventoryHandler;
import net.p3pp3rf1y.sophisticatedcore.upgrades.FilterLogic;
import net.p3pp3rf1y.sophisticatedcore.upgrades.IRenderedBatteryUpgrade;
import net.p3pp3rf1y.sophisticatedcore.upgrades.ITickableUpgrade;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeWrapperBase;
import team.reborn.energy.api.EnergyStorage;
import team.reborn.energy.api.EnergyStorageUtil;

import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * Links every battery in the backpack into one pool (exposed to SB as the backpack's energy storage) and, while the
 * backpack is carried, worn or placed, charges items from that pool. The pool's charge is shown on the backpack model.
 */
public class ChargingUpgradeWrapper extends UpgradeWrapperBase<ChargingUpgradeWrapper, ChargingUpgradeItem>
		implements ITickableUpgrade, IEnergyStorageUpgradeWrapper, IRenderedBatteryUpgrade {
	@Nullable
	private final FilterLogic filterLogic;
	private final BatteryPool pool;
	private boolean baseResolved = false;
	@Nullable
	private Consumer<BatteryRenderInfo> renderInfoCallback;

	public ChargingUpgradeWrapper(IStorageWrapper storageWrapper, ItemStack upgrade, Consumer<ItemStack> upgradeSaveHandler) {
		super(storageWrapper, upgrade, upgradeSaveHandler);
		filterLogic = upgradeItem.isAdvanced()
				? new FilterLogic(upgrade, upgradeSaveHandler, upgradeItem.getFilterSlotCount())
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

	/** Called by Sophisticated Backpacks when it builds the backpack's energy storage. */
	@Override
	public EnergyStorage wrapStorage(@Nullable EnergyStorage energyStorage) {
		pool.setBase(energyStorage);
		baseResolved = true;
		return pool;
	}

	/** The battery pool, with the backpack's Battery Upgrade (or whatever SB put below us) as its base. */
	public BatteryPool getPool() {
		if (!baseResolved) {
			if (storageWrapper instanceof IBackpackWrapper backpackWrapper) {
				backpackWrapper.getEnergyStorage(); // lets SB call wrapStorage on us with the right base
			}
			if (!baseResolved) {
				pool.setBase(storageWrapper.getUpgradeHandler().getWrappersThatImplement(EnergyStorage.class).stream().findFirst().orElse(null));
				baseResolved = true;
			}
		}
		return pool;
	}

	/** Older Sophisticated Core ticks upgrades with this signature; newer versions call {@link #tick(Entity, Level, BlockPos)}. */
	public void tick(@Nullable LivingEntity entity, Level level, BlockPos pos) {
		tick((Entity) entity, level, pos);
	}

	// No @Override: implements ITickableUpgrade.tick in Sophisticated Core versions that tick with an Entity.
	public void tick(@Nullable Entity entity, Level level, BlockPos pos) {
		if (level.isClientSide() || isInCooldown(level)) {
			return;
		}
		int interval = Config.isLoaded() ? Config.SERVER.chargeInterval.get() : 10;
		setCooldown(level, interval);
		if (isEnabled()) {
			chargeOnce(entity, (long) upgradeItem.getChargeRate() * interval, interval);
		}
		forceUpdateBatteryRenderInfo();
	}

	@Override
	public void setBatteryRenderInfoUpdateCallback(Consumer<BatteryRenderInfo> callback) {
		renderInfoCallback = callback;
	}

	/**
	 * Shows the pool's charge on the backpack model (SB draws it in 8 steps and only syncs a change of step). SB's own
	 * Battery Upgrade in the same backpack reports through here too, so the model always shows the whole pool.
	 */
	@Override
	public void forceUpdateBatteryRenderInfo() {
		if (renderInfoCallback == null) {
			return;
		}
		for (IRenderedBatteryUpgrade other : storageWrapper.getUpgradeHandler().getWrappersThatImplement(IRenderedBatteryUpgrade.class)) {
			if (other != this) {
				other.setBatteryRenderInfoUpdateCallback(info -> forceUpdateBatteryRenderInfo());
			}
		}
		BatteryPool batteryPool = getPool();
		long capacity = batteryPool.getCapacity();
		renderInfoCallback.accept(new BatteryRenderInfo(capacity > 0 ? (float) ((double) batteryPool.getAmount() / capacity) : 0f));
	}

	/**
	 * One charge cycle: moves up to {@code budget} energy from the pool into the items this upgrade charges. {@code ticks} is
	 * how many ticks the cycle stands for; each item gets that many transfers, so items that limit how much they take per
	 * transfer still charge at their full per-tick speed. Returns energy moved.
	 */
	public long chargeOnce(@Nullable Entity entity, long budget, int ticks) {
		ChargeRun run = new ChargeRun(getPool(), Math.max(0, budget), Math.max(1, ticks));
		if (run.remaining <= 0 || run.pool.getAmount() <= 0) {
			return 0;
		}
		boolean advanced = isAdvanced();
		if (entity instanceof Player player) {
			Inventory inventory = player.getInventory();
			PlayerInventoryStorage slots = PlayerInventoryStorage.of(player);
			int armorStart = inventory.items.size();
			for (int i = inventory.armor.size() - 1; i >= 0; i--) {
				run.charge(slots.getSlot(armorStart + i));
			}
			run.charge(slots.getSlot(inventory.selected));
			run.charge(slots.getSlot(armorStart + inventory.armor.size()));
			if (advanced && chargesInventory() && Config.SERVER.allowInventoryCharging.get()) {
				for (int i = 0; i < inventory.items.size(); i++) {
					if (i != inventory.selected) {
						run.charge(slots.getSlot(i));
					}
				}
			}
		}
		if (advanced && chargesContents() && Config.SERVER.allowContentsCharging.get()) {
			InventoryHandler contents = storageWrapper.getInventoryHandler();
			for (int slot = 0; slot < contents.getSlotCount() && run.remaining > 0; slot++) {
				if (!contents.getStackInSlot(slot).isEmpty() && !run.pool.isBatterySlot(slot)) {
					run.charge(contents.getSlot(slot));
				}
			}
		}
		return run.moved;
	}

	private final class ChargeRun {
		private final BatteryPool pool;
		private final int transfersPerItem;
		private long remaining;
		private long moved = 0;

		private ChargeRun(BatteryPool pool, long budget, int transfersPerItem) {
			this.pool = pool;
			this.remaining = budget;
			this.transfersPerItem = transfersPerItem;
		}

		/** Charges the item in {@code slot} where it is (the energy change is written into that slot). */
		void charge(SingleSlotStorage<ItemVariant> slot) {
			if (remaining <= 0 || slot.isResourceBlank() || slot.getAmount() != 1) {
				return; // a stack of several would get the energy once per item
			}
			ItemStack stack = slot.getResource().toStack();
			EnergyStorage target = EnergyStorage.ITEM.find(stack, ContainerItemContext.ofSingleSlot(slot));
			if (target == null || !BatteryRules.isChargeTarget(stack, target)) {
				return;
			}
			if (filterLogic != null && !filterLogic.matchesFilter(stack)) {
				return;
			}
			for (int i = 0; i < transfersPerItem && remaining > 0; i++) {
				long accepted;
				try (Transaction transfer = BatteryRules.openTransaction()) {
					accepted = EnergyStorageUtil.move(pool, target, remaining, transfer);
					transfer.commit();
				}
				if (accepted <= 0) {
					break;
				}
				remaining -= accepted;
				moved += accepted;
			}
		}
	}

	// Settings of the Advanced Charging Upgrade, stored on the upgrade item.

	public boolean chargesInventory() {
		return getSetting("chargeInventory", true);
	}

	public void setChargesInventory(boolean value) {
		setSetting("chargeInventory", value);
	}

	public boolean chargesContents() {
		return getSetting("chargeContents", false);
	}

	public void setChargesContents(boolean value) {
		setSetting("chargeContents", value);
	}

	private boolean getSetting(String key, boolean defaultValue) {
		return upgrade.getTag() != null && upgrade.getTag().contains(key) ? upgrade.getTag().getBoolean(key) : defaultValue;
	}

	private void setSetting(String key, boolean value) {
		upgrade.getOrCreateTag().putBoolean(key, value);
		save();
	}
}
