package com.doctorarii.sophisticatedcharging.upgrade;

import com.doctorarii.sophisticatedcharging.Config;
import com.doctorarii.sophisticatedcharging.energy.EnergyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.p3pp3rf1y.sophisticatedbackpacks.SophisticatedBackpacks;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeSlotChangeResult;
import net.p3pp3rf1y.sophisticatedcore.upgrades.IUpgradeCountLimitConfig;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeGroup;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeItemBase;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeType;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ChargingUpgradeItem extends UpgradeItemBase<ChargingUpgradeWrapper> {
	public static final UpgradeType<ChargingUpgradeWrapper> TYPE = new UpgradeType<>(ChargingUpgradeWrapper::new);

	/** Backpacks only (one per backpack); not accepted by Sophisticated Storage blocks. */
	private static final IUpgradeCountLimitConfig LIMITS = new IUpgradeCountLimitConfig() {
		@Override
		public int getMaxUpgradesPerStorage(String storageType, @Nullable ResourceLocation upgradeRegistryName) {
			return "backpack".equals(storageType) ? 1 : 0;
		}

		@Override
		public int getMaxUpgradesInGroupPerStorage(String storageType, UpgradeGroup upgradeGroup) {
			return Integer.MAX_VALUE;
		}
	};

	private final boolean advanced;

	public ChargingUpgradeItem(boolean advanced) {
		super(SophisticatedBackpacks.ITEM_GROUP, LIMITS);
		this.advanced = advanced;
	}

	/** Only one Charging Upgrade (basic or advanced) per backpack: one pool. */
	@Override
	public UpgradeSlotChangeResult canAddUpgradeTo(IStorageWrapper storageWrapper, ItemStack upgradeStack, boolean firstLevelStorage,
			boolean isClientSide) {
		UpgradeSlotChangeResult result = super.canAddUpgradeTo(storageWrapper, upgradeStack, firstLevelStorage, isClientSide);
		if (!result.isSuccessful()) {
			return result;
		}
		Set<Integer> present = new HashSet<>();
		storageWrapper.getUpgradeHandler().getSlotWrappers().forEach((slot, wrapper) -> {
			if (wrapper.getUpgradeStack().getItem() instanceof ChargingUpgradeItem) {
				present.add(slot);
			}
		});
		return present.isEmpty() ? result : new UpgradeSlotChangeResult.Fail(
				new TranslatableComponent("gui.sophisticatedcharging.status.charging_upgrade_present"), present, Set.of(), Set.of());
	}

	public boolean isAdvanced() {
		return advanced;
	}

	public int getChargeRate() {
		if (!Config.isLoaded()) {
			return advanced ? 10_000 : 1_000;
		}
		return advanced ? Config.SERVER.advancedChargeRate.get() : Config.SERVER.basicChargeRate.get();
	}

	public int getFilterSlotCount() {
		return Config.isLoaded() ? Config.SERVER.advancedFilterSlots.get() : 9;
	}

	@Override
	public UpgradeType<ChargingUpgradeWrapper> getType() {
		return TYPE;
	}

	@Override
	public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
		super.appendHoverText(stack, level, tooltip, flag);
		tooltip.add(new TranslatableComponent("item.sophisticatedcharging.charging_upgrade.rate", EnergyFormat.format(getChargeRate()))
				.withStyle(ChatFormatting.GRAY));
	}
}
