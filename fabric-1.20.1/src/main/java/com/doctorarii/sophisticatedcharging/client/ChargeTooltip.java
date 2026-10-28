package com.doctorarii.sophisticatedcharging.client;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackItem;

import java.util.List;

/** "Charge: 73%" under the name of backpacks that show a charge bar (same value as the bar). */
public final class ChargeTooltip {
	private ChargeTooltip() {}

	static void onTooltip(ItemStack stack, TooltipFlag flag, List<Component> lines) {
		if (!(stack.getItem() instanceof BackpackItem) || lines.isEmpty()) {
			return;
		}
		float ratio = ChargeBarDecorator.chargeRatio(stack);
		if (ratio < 0) {
			return;
		}
		int percent = (int) Math.floor(Math.min(1f, ratio) * 100);
		lines.add(1, Component.translatable("tooltip.sophisticatedcharging.charge", percent + "%").withStyle(ChatFormatting.GOLD));
	}
}
