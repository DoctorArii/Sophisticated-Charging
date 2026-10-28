package com.doctorarii.sophisticatedcharging.upgrade;

import com.doctorarii.sophisticatedcharging.Config;
import com.doctorarii.sophisticatedcharging.energy.EnergyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.p3pp3rf1y.sophisticatedcore.upgrades.IUpgradeCountLimitConfig;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeGroup;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeItemBase;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeType;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

public class ChargingUpgradeItem extends UpgradeItemBase<ChargingUpgradeWrapper> {
	public static final UpgradeType<ChargingUpgradeWrapper> TYPE = new UpgradeType<>(ChargingUpgradeWrapper::new);

	/** Backpacks only (one per backpack); not accepted by Sophisticated Storage blocks. */
	private static final IUpgradeCountLimitConfig LIMITS = new IUpgradeCountLimitConfig() {
		@Override
		public int getMaxUpgradesPerStorage(String storageType, @Nullable Identifier upgradeRegistryName) {
			return "backpack".equals(storageType) ? 1 : 0;
		}

		@Override
		public int getMaxUpgradesInGroupPerStorage(String storageType, UpgradeGroup upgradeGroup) {
			return Integer.MAX_VALUE;
		}
	};

	private static final List<UpgradeConflictDefinition> CONFLICTS = List.of(new UpgradeConflictDefinition(ChargingUpgradeItem.class::isInstance, 0,
			Component.translatable("gui.sophisticatedcharging.status.charging_upgrade_present")));

	private final boolean advanced;

	public ChargingUpgradeItem(boolean advanced, Item.Properties properties) {
		super(LIMITS, properties);
		this.advanced = advanced;
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
	public List<UpgradeConflictDefinition> getUpgradeConflicts() {
		return CONFLICTS;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		super.appendHoverText(stack, context, display, tooltip, flag);
		tooltip.accept(Component.translatable("item.sophisticatedcharging.charging_upgrade.rate", EnergyFormat.format(getChargeRate()))
				.withStyle(ChatFormatting.GRAY));
	}
}
