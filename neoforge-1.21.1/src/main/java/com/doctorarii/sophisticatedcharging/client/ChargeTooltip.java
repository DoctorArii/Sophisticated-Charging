package com.doctorarii.sophisticatedcharging.client;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackItem;

/** "Charge: 73%" under the name of backpacks that show a charge bar (same value as the bar). */
public final class ChargeTooltip {
	private ChargeTooltip() {}

	static void onTooltip(ItemTooltipEvent event) {
		if (!(event.getItemStack().getItem() instanceof BackpackItem) || event.getToolTip().isEmpty()) {
			return;
		}
		float ratio = ChargeBarDecorator.chargeRatio(event.getItemStack());
		if (ratio < 0) {
			return;
		}
		int percent = (int) Math.floor(Math.min(1f, ratio) * 100);
		event.getToolTip().add(1, Component.translatable("tooltip.sophisticatedcharging.charge", percent + "%")
				.withStyle(ChatFormatting.GOLD));
	}
}
