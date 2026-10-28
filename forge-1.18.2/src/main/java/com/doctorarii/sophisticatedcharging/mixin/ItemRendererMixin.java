package com.doctorarii.sophisticatedcharging.mixin;

import com.doctorarii.sophisticatedcharging.client.ChargeBarDecorator;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

/** The charge bar under backpack icons (Forge 1.18.2 has no item decorator API). */
@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {
	@Inject(method = "renderGuiItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
			at = @At("TAIL"))
	private void sophisticatedcharging$chargeBar(Font font, ItemStack stack, int x, int y, @Nullable String text, CallbackInfo ci) {
		ChargeBarDecorator.render(stack, x, y);
	}
}
