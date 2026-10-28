package com.doctorarii.sophisticatedcharging.upgrade;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerBase;
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerType;
import net.p3pp3rf1y.sophisticatedcore.upgrades.FilterLogic;
import net.p3pp3rf1y.sophisticatedcore.upgrades.FilterLogicContainer;
import org.jspecify.annotations.Nullable;

public class ChargingUpgradeContainer extends UpgradeContainerBase<ChargingUpgradeWrapper, ChargingUpgradeContainer> {
	private static final String DATA_CHARGE_INVENTORY = "chargeInventory";
	private static final String DATA_CHARGE_CURIOS = "chargeCurios";
	private static final String DATA_CHARGE_CONTENTS = "chargeContents";

	@Nullable
	private final FilterLogicContainer<FilterLogic> filterLogicContainer;

	public ChargingUpgradeContainer(Player player, int upgradeContainerId, ChargingUpgradeWrapper upgradeWrapper,
			UpgradeContainerType<ChargingUpgradeWrapper, ChargingUpgradeContainer> type) {
		super(player, upgradeContainerId, upgradeWrapper, type);
		filterLogicContainer = upgradeWrapper.getFilterLogic() != null
				// reads the current wrapper each time (SB swaps it when the upgrade changes); older SB Core has no supplyFromWrapper
				? new FilterLogicContainer<>(() -> this.upgradeWrapper.getFilterLogic(), this, slots::add)
				: null;
	}

	@Override
	public void handlePacket(CompoundTag data) {
		if (data.contains(DATA_CHARGE_INVENTORY)) {
			upgradeWrapper.setChargesInventory(data.getBooleanOr(DATA_CHARGE_INVENTORY, true));
		} else if (data.contains(DATA_CHARGE_CURIOS)) {
			upgradeWrapper.setChargesCurios(data.getBooleanOr(DATA_CHARGE_CURIOS, true));
		} else if (data.contains(DATA_CHARGE_CONTENTS)) {
			upgradeWrapper.setChargesContents(data.getBooleanOr(DATA_CHARGE_CONTENTS, false));
		} else if (filterLogicContainer != null) {
			filterLogicContainer.handlePacket(data);
		}
	}

	@Nullable
	public FilterLogicContainer<FilterLogic> getFilterLogicContainer() {
		return filterLogicContainer;
	}

	public ChargingUpgradeWrapper getWrapper() {
		return upgradeWrapper;
	}

	public boolean chargesInventory() {
		return upgradeWrapper.chargesInventory();
	}

	public void setChargesInventory(boolean value) {
		upgradeWrapper.setChargesInventory(value);
		sendBooleanToServer(DATA_CHARGE_INVENTORY, value);
	}

	public boolean chargesCurios() {
		return upgradeWrapper.chargesCurios();
	}

	public void setChargesCurios(boolean value) {
		upgradeWrapper.setChargesCurios(value);
		sendBooleanToServer(DATA_CHARGE_CURIOS, value);
	}

	public boolean chargesContents() {
		return upgradeWrapper.chargesContents();
	}

	public void setChargesContents(boolean value) {
		upgradeWrapper.setChargesContents(value);
		sendBooleanToServer(DATA_CHARGE_CONTENTS, value);
	}
}
