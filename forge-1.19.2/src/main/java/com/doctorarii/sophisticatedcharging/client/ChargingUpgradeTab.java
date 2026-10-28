package com.doctorarii.sophisticatedcharging.client;

import com.doctorarii.sophisticatedcharging.SophisticatedCharging;
import com.doctorarii.sophisticatedcharging.upgrade.ChargingUpgradeContainer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase;
import net.p3pp3rf1y.sophisticatedcore.client.gui.UpgradeSettingsTab;
import net.p3pp3rf1y.sophisticatedcore.client.gui.controls.ButtonDefinition;
import net.p3pp3rf1y.sophisticatedcore.client.gui.controls.ButtonDefinitions;
import net.p3pp3rf1y.sophisticatedcore.client.gui.controls.ToggleButton;
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.Dimension;
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.Position;
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.TextureBlitData;
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.UV;
import net.p3pp3rf1y.sophisticatedcore.upgrades.FilterLogic;
import net.p3pp3rf1y.sophisticatedcore.upgrades.FilterLogicContainer;
import net.p3pp3rf1y.sophisticatedcore.upgrades.FilterLogicControl;

public abstract class ChargingUpgradeTab extends UpgradeSettingsTab<ChargingUpgradeContainer> {
	private static final ResourceLocation ICONS = SophisticatedCharging.rl("textures/gui/icons.png");

	private static final ButtonDefinition.Toggle<Boolean> CHARGE_INVENTORY = toggle(0, "charge_inventory", "do_not_charge_inventory");
	private static final ButtonDefinition.Toggle<Boolean> CHARGE_CURIOS = toggle(32, "charge_curios", "do_not_charge_curios");
	private static final ButtonDefinition.Toggle<Boolean> CHARGE_CONTENTS = toggle(64, "charge_contents", "do_not_charge_contents");

	protected ChargingUpgradeTab(ChargingUpgradeContainer container, Position position, StorageScreenBase<?> screen, String name) {
		super(container, position, screen, Component.translatable("gui.sophisticatedcharging.upgrades." + name),
				Component.translatable("gui.sophisticatedcharging.upgrades." + name + ".tooltip"));
	}

	private static ButtonDefinition.Toggle<Boolean> toggle(int u, String onKey, String offKey) {
		return ButtonDefinitions.createToggleButtonDefinition(ButtonDefinitions.getBooleanStateData(state(u, onKey), state(u + 16, offKey)));
	}

	private static ToggleButton.StateData state(int u, String key) {
		return new ToggleButton.StateData(new TextureBlitData(ICONS, new Position(1, 1), Dimension.SQUARE_256, new UV(u, 0), Dimension.SQUARE_16),
				Component.translatable("gui.sophisticatedcharging.buttons." + key));
	}

	public static class Basic extends ChargingUpgradeTab {
		public Basic(ChargingUpgradeContainer container, Position position, StorageScreenBase<?> screen) {
			super(container, position, screen, "charging");
			addHideableChild(new EnergyInfoWidget(new Position(x + 4, y + 24), () -> getContainer().getWrapper().getPool()));
		}

		@Override
		protected void moveSlotsToTab() {
			// no slots
		}
	}

	public static class Advanced extends ChargingUpgradeTab {
		private final FilterLogicControl<FilterLogic, FilterLogicContainer<FilterLogic>> filterLogicControl;

		public Advanced(ChargingUpgradeContainer container, Position position, StorageScreenBase<?> screen, int slotsPerRow) {
			super(container, position, screen, "advanced_charging");
			addHideableChild(new ToggleButton<>(new Position(x + 3, y + 24), CHARGE_INVENTORY,
					button -> getContainer().setChargesInventory(!getContainer().chargesInventory()), () -> getContainer().chargesInventory()));
			addHideableChild(new ToggleButton<>(new Position(x + 21, y + 24), CHARGE_CURIOS,
					button -> getContainer().setChargesCurios(!getContainer().chargesCurios()), () -> getContainer().chargesCurios()));
			addHideableChild(new ToggleButton<>(new Position(x + 39, y + 24), CHARGE_CONTENTS,
					button -> getContainer().setChargesContents(!getContainer().chargesContents()), () -> getContainer().chargesContents()));
			addHideableChild(new EnergyInfoWidget(new Position(x + 4, y + 46), () -> getContainer().getWrapper().getPool()));
			FilterLogicContainer<FilterLogic> filterContainer = getContainer().getFilterLogicContainer();
			filterLogicControl = filterContainer == null ? null
					: addHideableChild(new FilterLogicControl.Advanced(screen, new Position(x + 3, y + 78), filterContainer, slotsPerRow));
		}

		@Override
		protected void moveSlotsToTab() {
			if (filterLogicControl != null) {
				filterLogicControl.moveSlotsToView();
			}
		}
	}
}
