package com.doctorarii.sophisticatedcharging.init;

import com.doctorarii.sophisticatedcharging.SophisticatedCharging;
import com.doctorarii.sophisticatedcharging.upgrade.ChargingUpgradeContainer;
import com.doctorarii.sophisticatedcharging.upgrade.ChargingUpgradeItem;
import com.doctorarii.sophisticatedcharging.upgrade.ChargingUpgradeWrapper;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerRegistry;
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerType;

public final class ModContent {
	private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SophisticatedCharging.MOD_ID);
	private static final DeferredRegister.DataComponents DATA_COMPONENTS = DeferredRegister.createDataComponents(SophisticatedCharging.MOD_ID);

	public static final DeferredHolder<Item, ChargingUpgradeItem> CHARGING_UPGRADE = ITEMS.register("charging_upgrade",
			() -> new ChargingUpgradeItem(false));
	public static final DeferredHolder<Item, ChargingUpgradeItem> ADVANCED_CHARGING_UPGRADE = ITEMS.register("advanced_charging_upgrade",
			() -> new ChargingUpgradeItem(true));

	public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> CHARGE_INVENTORY = bool("charge_inventory");
	public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> CHARGE_CURIOS = bool("charge_curios");
	public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> CHARGE_CONTENTS = bool("charge_contents");

	public static final UpgradeContainerType<ChargingUpgradeWrapper, ChargingUpgradeContainer> CHARGING_TYPE = new UpgradeContainerType<>(
			ChargingUpgradeContainer::new);
	public static final UpgradeContainerType<ChargingUpgradeWrapper, ChargingUpgradeContainer> ADVANCED_CHARGING_TYPE = new UpgradeContainerType<>(
			ChargingUpgradeContainer::new);

	private static final ResourceKey<CreativeModeTab> BACKPACKS_TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
			ResourceLocation.fromNamespaceAndPath("sophisticatedbackpacks", "main"));

	private ModContent() {}

	private static DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> bool(String name) {
		return DATA_COMPONENTS.registerComponentType(name, builder -> builder.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));
	}

	public static void register(IEventBus modBus) {
		ITEMS.register(modBus);
		DATA_COMPONENTS.register(modBus);
		modBus.addListener(ModContent::onCommonSetup);
		modBus.addListener(ModContent::onBuildCreativeTabs);
	}

	private static void onCommonSetup(FMLCommonSetupEvent event) {
		event.enqueueWork(() -> {
			UpgradeContainerRegistry.register(CHARGING_UPGRADE.getId(), CHARGING_TYPE);
			UpgradeContainerRegistry.register(ADVANCED_CHARGING_UPGRADE.getId(), ADVANCED_CHARGING_TYPE);
		});
	}

	private static void onBuildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
		if (event.getTabKey().equals(BACKPACKS_TAB)) {
			event.accept(CHARGING_UPGRADE.get());
			event.accept(ADVANCED_CHARGING_UPGRADE.get());
		}
	}
}
