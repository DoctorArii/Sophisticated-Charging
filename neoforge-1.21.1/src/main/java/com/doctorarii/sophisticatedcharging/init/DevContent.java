package com.doctorarii.sophisticatedcharging.init;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.ComponentEnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Test items that exist only in the development environment (never in the released jar's game): simple FE batteries and
 * tools so the in-game tests don't depend on other mods.
 */
public final class DevContent {
	public static DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> ENERGY;
	/** 100k FE, 10k FE/t in and out. */
	public static DeferredHolder<Item, Item> TEST_BATTERY;
	/** 10M FE, 1M FE/t in and out. */
	public static DeferredHolder<Item, Item> TEST_BIG_BATTERY;
	/** Takes energy but never gives it back: 50k FE, 5k FE/t in. */
	public static DeferredHolder<Item, Item> TEST_TOOL;
	/** Always full, never runs empty. */
	public static DeferredHolder<Item, Item> TEST_CREATIVE_BATTERY;
	/** Like the test battery but stacks to 16. */
	public static DeferredHolder<Item, Item> TEST_STACKABLE_BATTERY;

	private DevContent() {}

	static void register(DeferredRegister.Items items, DeferredRegister.DataComponents components, IEventBus modBus) {
		ENERGY = components.registerComponentType("dev_energy",
				builder -> builder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));
		TEST_BATTERY = items.register("test_battery", () -> new Item(new Item.Properties().stacksTo(1)));
		TEST_BIG_BATTERY = items.register("test_big_battery", () -> new Item(new Item.Properties().stacksTo(1)));
		TEST_TOOL = items.register("test_tool", () -> new Item(new Item.Properties().stacksTo(1)));
		TEST_CREATIVE_BATTERY = items.register("test_creative_battery", () -> new Item(new Item.Properties().stacksTo(1)));
		TEST_STACKABLE_BATTERY = items.register("test_stackable_battery", () -> new Item(new Item.Properties().stacksTo(16)));
		modBus.addListener(DevContent::registerCapabilities);
	}

	private static void registerCapabilities(RegisterCapabilitiesEvent event) {
		event.registerItem(Capabilities.EnergyStorage.ITEM, (stack, ctx) -> new ComponentEnergyStorage(stack, ENERGY.get(), 100_000, 10_000, 10_000),
				TEST_BATTERY.get(), TEST_STACKABLE_BATTERY.get());
		event.registerItem(Capabilities.EnergyStorage.ITEM, (stack, ctx) -> new ComponentEnergyStorage(stack, ENERGY.get(), 10_000_000, 1_000_000,
				1_000_000), TEST_BIG_BATTERY.get());
		event.registerItem(Capabilities.EnergyStorage.ITEM, (stack, ctx) -> new ComponentEnergyStorage(stack, ENERGY.get(), 50_000, 5_000, 0),
				TEST_TOOL.get());
		event.registerItem(Capabilities.EnergyStorage.ITEM, (stack, ctx) -> new CreativeEnergy(), TEST_CREATIVE_BATTERY.get());
	}

	static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
		event.accept(TEST_BATTERY.get());
		event.accept(TEST_BIG_BATTERY.get());
		event.accept(TEST_TOOL.get());
		event.accept(TEST_CREATIVE_BATTERY.get());
		event.accept(TEST_STACKABLE_BATTERY.get());
	}

	public static ItemStack charged(DeferredHolder<Item, Item> item, int energy) {
		ItemStack stack = new ItemStack(item.get());
		stack.set(ENERGY.get(), energy);
		return stack;
	}

	public static int energy(ItemStack stack) {
		return stack.getOrDefault(ENERGY.get(), 0);
	}

	private static final class CreativeEnergy implements IEnergyStorage {
		@Override
		public int receiveEnergy(int maxReceive, boolean simulate) {
			return 0;
		}

		@Override
		public int extractEnergy(int maxExtract, boolean simulate) {
			return maxExtract;
		}

		@Override
		public int getEnergyStored() {
			return Integer.MAX_VALUE;
		}

		@Override
		public int getMaxEnergyStored() {
			return Integer.MAX_VALUE;
		}

		@Override
		public boolean canExtract() {
			return true;
		}

		@Override
		public boolean canReceive() {
			return false;
		}
	}
}
