package com.doctorarii.sophisticatedcharging.init;

import com.doctorarii.sophisticatedcharging.SophisticatedCharging;
import com.doctorarii.sophisticatedcharging.upgrade.ChargingUpgradeContainer;
import com.doctorarii.sophisticatedcharging.upgrade.ChargingUpgradeItem;
import com.doctorarii.sophisticatedcharging.upgrade.ChargingUpgradeWrapper;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerRegistry;
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerType;

public final class ModContent {
	public static final ChargingUpgradeItem CHARGING_UPGRADE = new ChargingUpgradeItem(false);
	public static final ChargingUpgradeItem ADVANCED_CHARGING_UPGRADE = new ChargingUpgradeItem(true);

	public static final UpgradeContainerType<ChargingUpgradeWrapper, ChargingUpgradeContainer> CHARGING_TYPE = new UpgradeContainerType<>(
			ChargingUpgradeContainer::new);
	public static final UpgradeContainerType<ChargingUpgradeWrapper, ChargingUpgradeContainer> ADVANCED_CHARGING_TYPE = new UpgradeContainerType<>(
			ChargingUpgradeContainer::new);

	/** The creative tab of the Fabric port of Sophisticated Backpacks. */
	private static final ResourceKey<CreativeModeTab> BACKPACKS_TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
			new ResourceLocation("sophisticatedbackpacks", "item_group"));

	private ModContent() {}

	public static void register() {
		ResourceLocation charging = SophisticatedCharging.rl("charging_upgrade");
		ResourceLocation advanced = SophisticatedCharging.rl("advanced_charging_upgrade");
		Registry.register(BuiltInRegistries.ITEM, charging, CHARGING_UPGRADE);
		Registry.register(BuiltInRegistries.ITEM, advanced, ADVANCED_CHARGING_UPGRADE);
		UpgradeContainerRegistry.register(charging, CHARGING_TYPE);
		UpgradeContainerRegistry.register(advanced, ADVANCED_CHARGING_TYPE);
		ItemGroupEvents.modifyEntriesEvent(BACKPACKS_TAB).register(entries -> {
			entries.accept(CHARGING_UPGRADE);
			entries.accept(ADVANCED_CHARGING_UPGRADE);
		});
	}
}
