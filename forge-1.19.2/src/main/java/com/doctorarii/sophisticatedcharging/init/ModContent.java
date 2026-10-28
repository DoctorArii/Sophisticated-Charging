package com.doctorarii.sophisticatedcharging.init;

import com.doctorarii.sophisticatedcharging.SophisticatedCharging;
import com.doctorarii.sophisticatedcharging.upgrade.ChargingUpgradeContainer;
import com.doctorarii.sophisticatedcharging.upgrade.ChargingUpgradeItem;
import com.doctorarii.sophisticatedcharging.upgrade.ChargingUpgradeWrapper;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerRegistry;
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerType;

public final class ModContent {
	private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, SophisticatedCharging.MOD_ID);

	public static final RegistryObject<ChargingUpgradeItem> CHARGING_UPGRADE = ITEMS.register("charging_upgrade", () -> new ChargingUpgradeItem(false));
	public static final RegistryObject<ChargingUpgradeItem> ADVANCED_CHARGING_UPGRADE = ITEMS.register("advanced_charging_upgrade",
			() -> new ChargingUpgradeItem(true));

	public static final UpgradeContainerType<ChargingUpgradeWrapper, ChargingUpgradeContainer> CHARGING_TYPE = new UpgradeContainerType<>(
			ChargingUpgradeContainer::new);
	public static final UpgradeContainerType<ChargingUpgradeWrapper, ChargingUpgradeContainer> ADVANCED_CHARGING_TYPE = new UpgradeContainerType<>(
			ChargingUpgradeContainer::new);

	private ModContent() {}

	public static void register(IEventBus modBus) {
		ITEMS.register(modBus);
		modBus.addListener(ModContent::onCommonSetup);
	}

	private static void onCommonSetup(FMLCommonSetupEvent event) {
		event.enqueueWork(() -> {
			UpgradeContainerRegistry.register(CHARGING_UPGRADE.getId(), CHARGING_TYPE);
			UpgradeContainerRegistry.register(ADVANCED_CHARGING_UPGRADE.getId(), ADVANCED_CHARGING_TYPE);
		});
	}
}
