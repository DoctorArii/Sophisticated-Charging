package com.doctorarii.sophisticatedcharging.client;

import com.doctorarii.sophisticatedcharging.SophisticatedCharging;
import com.doctorarii.sophisticatedcharging.init.ModContent;
import com.doctorarii.sophisticatedcharging.upgrade.ChargingUpgradeContainer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase;
import net.p3pp3rf1y.sophisticatedcore.client.gui.UpgradeGuiManager;
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.Position;

/** Client-only setup; a dedicated server never loads this class. */
@Mod(value = SophisticatedCharging.MOD_ID, dist = Dist.CLIENT)
public final class ClientSetup {
	/** Filter slots per row in the advanced tab (same as most SB upgrades). */
	private static final int FILTER_SLOTS_IN_ROW = 3;

	public ClientSetup(IEventBus modBus) {
		modBus.addListener(ClientSetup::onRegisterScreens);
		modBus.addListener(ChargeBarDecorator::register);
		NeoForge.EVENT_BUS.addListener(ChargeTooltip::onTooltip);
	}

	private static void onRegisterScreens(RegisterMenuScreensEvent event) {
		UpgradeGuiManager.registerTab(ModContent.CHARGING_TYPE, ChargingUpgradeTab.Basic::new);
		UpgradeGuiManager.registerTab(ModContent.ADVANCED_CHARGING_TYPE,
				(ChargingUpgradeContainer container, Position position, StorageScreenBase<?> screen) -> new ChargingUpgradeTab.Advanced(container, position,
						screen, FILTER_SLOTS_IN_ROW));
	}
}
