package com.doctorarii.sophisticatedcharging.upgrade;

import com.doctorarii.sophisticatedcharging.Config;
import com.doctorarii.sophisticatedcharging.SophisticatedCharging;
import com.doctorarii.sophisticatedcharging.compat.CuriosCompat;
import com.doctorarii.sophisticatedcharging.energy.BatteryPool;
import com.doctorarii.sophisticatedcharging.energy.BatteryRules;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.energy.IEnergyStorage;
import net.p3pp3rf1y.sophisticatedbackpacks.api.IEnergyStorageUpgradeWrapper;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.IBackpackWrapper;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import net.p3pp3rf1y.sophisticatedcore.inventory.InventoryHandler;
import net.p3pp3rf1y.sophisticatedcore.upgrades.FilterLogic;
import net.p3pp3rf1y.sophisticatedcore.upgrades.IRenderedBatteryUpgrade;
import net.p3pp3rf1y.sophisticatedcore.upgrades.ITickableUpgrade;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeWrapperBase;

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
	public IEnergyStorage wrapStorage(@Nullable IEnergyStorage energyStorage) {
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
				pool.setBase(storageWrapper.getUpgradeHandler().getWrappersThatImplement(IEnergyStorage.class).stream().findFirst().orElse(null));
				baseResolved = true;
			}
		}
		return pool;
	}

	@Override
	public void tick(@Nullable LivingEntity entity, Level level, BlockPos pos) {
		tick((Entity) entity, level, pos);
	}

	private void tick(@Nullable Entity entity, Level level, BlockPos pos) {
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
		long capacity = batteryPool.getMaxEnergyStoredLong();
		renderInfoCallback.accept(new BatteryRenderInfo(capacity > 0 ? (float) ((double) batteryPool.getEnergyStoredLong() / capacity) : 0f));
	}

	/**
	 * One charge cycle: moves up to {@code budget} FE from the pool into the items this upgrade charges. {@code ticks} is how many
	 * ticks the cycle stands for; each item gets that many transfers, so items that limit how much they take per transfer
	 * still charge at their full per-tick speed. Returns FE moved.
	 */
	public int chargeOnce(@Nullable Entity entity, long budget, int ticks) {
		ChargeRun run = new ChargeRun(getPool(), (int) Math.min(Integer.MAX_VALUE, Math.max(0, budget)), Math.max(1, ticks));
		// Checks stored energy instead of a 1 FE trial pull: mods with their own energy units (Mekanism) round 1 FE down to 0.
		if (run.remaining <= 0 || run.pool.getEnergyStoredLong() <= 0) {
			return 0;
		}
		boolean advanced = isAdvanced();
		if (entity instanceof Player player) {
			Inventory inventory = player.getInventory();
			for (int i = inventory.armor.size() - 1; i >= 0; i--) {
				run.charge(inventory.armor.get(i), null);
			}
			run.charge(inventory.getSelected(), null);
			run.charge(inventory.offhand.get(0), null);
			if (advanced && chargesInventory() && Config.SERVER.allowInventoryCharging.get()) {
				for (int i = 0; i < inventory.items.size(); i++) {
					if (i != inventory.selected) {
						run.charge(inventory.items.get(i), null);
					}
				}
			}
			if (advanced && chargesCurios() && Config.SERVER.allowCuriosCharging.get() && CuriosCompat.isLoaded()) {
				CuriosCompat.forEachEquipped(player, stack -> run.charge(stack, null));
			}
		}
		if (advanced && chargesContents() && Config.SERVER.allowContentsCharging.get()) {
			InventoryHandler contents = storageWrapper.getInventoryHandler();
			for (int slot = 0; slot < contents.getSlots() && run.remaining > 0; slot++) {
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

		void charge(ItemStack stack, @Nullable Runnable persist) {
			if (remaining <= 0 || stack.isEmpty() || stack.getCount() != 1) {
				return; // a stack of several would get the energy once per item
			}
			IEnergyStorage target = BatteryRules.energyOf(stack);
			if (target == null || !BatteryRules.isChargeTarget(stack, target)) {
				return;
			}
			if (filterLogic != null && !filterLogic.matchesFilter(stack)) {
				return;
			}
			int given = 0;
			for (int i = 0; i < transfersPerItem && remaining > 0; i++) {
				int accepted = transfer(stack, target);
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

		private int transfer(ItemStack stack, IEnergyStorage target) {
			int wanted = target.receiveEnergy(remaining, true);
			if (wanted <= 0) {
				return 0;
			}
			int taken = pool.extractEnergy(wanted, false);
			if (taken <= 0) {
				return 0;
			}
			int accepted = target.receiveEnergy(taken, false);
			if (accepted < taken) {
				int returned = pool.receiveEnergy(taken - accepted, false);
				if (returned < taken - accepted) {
					SophisticatedCharging.LOGGER.warn("{} FE lost charging {}: the item took less than it offered to and the pool could not take it back",
							taken - accepted - returned, stack);
				}
			}
			return accepted;
		}
	}

	// Settings of the Advanced Charging Upgrade, stored on the upgrade item.

	public boolean chargesInventory() {
		return getSetting("chargeInventory", true);
	}

	public void setChargesInventory(boolean value) {
		setSetting("chargeInventory", value);
	}

	public boolean chargesCurios() {
		return getSetting("chargeCurios", true);
	}

	public void setChargesCurios(boolean value) {
		setSetting("chargeCurios", value);
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
