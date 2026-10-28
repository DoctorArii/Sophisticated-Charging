package com.doctorarii.sophisticatedcharging.client;

import com.doctorarii.sophisticatedcharging.Config;
import com.doctorarii.sophisticatedcharging.SophisticatedCharging;
import com.doctorarii.sophisticatedcharging.init.ModContent;
import fuzs.forgeconfigapiport.api.config.v2.ForgeConfigRegistry;
import net.minecraftforge.fml.config.ModConfig;
import com.doctorarii.sophisticatedcharging.upgrade.ChargingUpgradeContainer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase;
import net.p3pp3rf1y.sophisticatedcore.client.gui.UpgradeGuiManager;
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.Position;

public final class ClientSetup {
	/** Filter slots per row in the advanced tab (same as most SB upgrades). */
	private static final int FILTER_SLOTS_IN_ROW = 3;

	private ClientSetup() {}

	public static void init() {
		ForgeConfigRegistry.INSTANCE.register(SophisticatedCharging.MOD_ID, ModConfig.Type.CLIENT, Config.CLIENT_SPEC);
		UpgradeGuiManager.registerTab(ModContent.CHARGING_TYPE, ChargingUpgradeTab.Basic::new);
		UpgradeGuiManager.registerTab(ModContent.ADVANCED_CHARGING_TYPE,
				(ChargingUpgradeContainer container, Position position, StorageScreenBase<?> screen) -> new ChargingUpgradeTab.Advanced(container,
						position, screen, FILTER_SLOTS_IN_ROW));
		ItemTooltipCallback.EVENT.register(ChargeTooltip::onTooltip);
	}
}
