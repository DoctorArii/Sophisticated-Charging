package com.doctorarii.sophisticatedcharging.init;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import javax.annotation.Nullable;
import java.util.function.Function;

/**
 * Test items that exist only in the development environment (never in the released jar's game): simple FE batteries and
 * tools so the in-game tests don't depend on other mods.
 */
public final class DevContent {
	private static final String ENERGY_TAG = "Energy";
	/** 100k FE, 10k FE/t in and out. */
	public static RegistryObject<Item> TEST_BATTERY;
	/** 10M FE, 1M FE/t in and out. */
	public static RegistryObject<Item> TEST_BIG_BATTERY;
	/** Takes energy but never gives it back: 50k FE, 5k FE/t in. */
	public static RegistryObject<Item> TEST_TOOL;
	/** Always full, never runs empty. */
	public static RegistryObject<Item> TEST_CREATIVE_BATTERY;
	/** Like the test battery but stacks to 16. */
	public static RegistryObject<Item> TEST_STACKABLE_BATTERY;

	private DevContent() {}

	static void register(DeferredRegister<Item> items, IEventBus modBus) {
		TEST_BATTERY = items.register("test_battery", () -> new EnergyItem(1, s -> new NbtEnergy(s, 100_000, 10_000, 10_000)));
		TEST_BIG_BATTERY = items.register("test_big_battery", () -> new EnergyItem(1, s -> new NbtEnergy(s, 10_000_000, 1_000_000, 1_000_000)));
		TEST_TOOL = items.register("test_tool", () -> new EnergyItem(1, s -> new NbtEnergy(s, 50_000, 5_000, 0)));
		TEST_CREATIVE_BATTERY = items.register("test_creative_battery", () -> new EnergyItem(1, s -> new CreativeEnergy()));
		TEST_STACKABLE_BATTERY = items.register("test_stackable_battery", () -> new EnergyItem(16, s -> new NbtEnergy(s, 100_000, 10_000, 10_000)));
	}

	static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
		event.accept(TEST_BATTERY);
		event.accept(TEST_BIG_BATTERY);
		event.accept(TEST_TOOL);
		event.accept(TEST_CREATIVE_BATTERY);
		event.accept(TEST_STACKABLE_BATTERY);
	}

	public static ItemStack charged(RegistryObject<Item> item, int energy) {
		ItemStack stack = new ItemStack(item.get());
		stack.getOrCreateTag().putInt(ENERGY_TAG, energy);
		return stack;
	}

	public static int energy(ItemStack stack) {
		return stack.getTag() == null ? 0 : stack.getTag().getInt(ENERGY_TAG);
	}

	private static final class EnergyItem extends Item {
		private final Function<ItemStack, IEnergyStorage> energy;

		EnergyItem(int stackSize, Function<ItemStack, IEnergyStorage> energy) {
			super(new Item.Properties().stacksTo(stackSize));
			this.energy = energy;
		}

		@Override
		public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
			LazyOptional<IEnergyStorage> storage = LazyOptional.of(() -> energy.apply(stack));
			return new ICapabilityProvider() {
				@Override
				public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
					return cap == ForgeCapabilities.ENERGY ? storage.cast() : LazyOptional.empty();
				}
			};
		}
	}

	private record NbtEnergy(ItemStack stack, int capacity, int maxReceive, int maxExtract) implements IEnergyStorage {
		@Override
		public int receiveEnergy(int max, boolean simulate) {
			int got = Math.min(capacity - getEnergyStored(), Math.min(maxReceive, max));
			if (!simulate && got > 0) {
				stack.getOrCreateTag().putInt(ENERGY_TAG, getEnergyStored() + got);
			}
			return Math.max(0, got);
		}

		@Override
		public int extractEnergy(int max, boolean simulate) {
			int got = Math.min(getEnergyStored(), Math.min(maxExtract, max));
			if (!simulate && got > 0) {
				stack.getOrCreateTag().putInt(ENERGY_TAG, getEnergyStored() - got);
			}
			return Math.max(0, got);
		}

		@Override
		public int getEnergyStored() {
			return energy(stack);
		}

		@Override
		public int getMaxEnergyStored() {
			return capacity;
		}

		@Override
		public boolean canExtract() {
			return maxExtract > 0;
		}

		@Override
		public boolean canReceive() {
			return maxReceive > 0;
		}
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
