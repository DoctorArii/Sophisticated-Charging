package com.doctorarii.sophisticatedcharging.client;

import com.doctorarii.sophisticatedcharging.Config;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.IItemDecorator;
import net.minecraftforge.client.event.RegisterItemDecorationsEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackItem;
import net.p3pp3rf1y.sophisticatedcore.upgrades.IRenderedBatteryUpgrade.BatteryRenderInfo;

/**
 * A thin energy bar under backpack icons, read from the battery charge Sophisticated Backpacks keeps on the backpack item
 * (the pool of a Charging Upgrade, or SB's own Battery Upgrade). Gold, so it is never mistaken for a durability bar.
 */
public final class ChargeBarDecorator implements IItemDecorator {
	private static final String RENDER_INFO_KEY = "renderInfo";
	private static final String BATTERY_KEY = "battery";
	private static final int BAR_COLOR = 0xFFFFC21F;

	private ChargeBarDecorator() {}

	static void register(RegisterItemDecorationsEvent event) {
		ChargeBarDecorator decorator = new ChargeBarDecorator();
		for (Item item : ForgeRegistries.ITEMS) {
			if (item instanceof BackpackItem) {
				event.register(item, decorator);
			}
		}
	}

	/** Charge (0 to 1) of the backpack's battery display, or -1 if it has none. */
	public static float chargeRatio(ItemStack stack) {
		CompoundTag renderInfo = stack.getTagElement(RENDER_INFO_KEY);
		if (renderInfo == null || !renderInfo.contains(BATTERY_KEY)) {
			return -1;
		}
		return BatteryRenderInfo.deserialize(renderInfo.getCompound(BATTERY_KEY)).getChargeRatio();
	}

	/** Drawn like the vanilla durability bar of this Minecraft version. */
	@Override
	public boolean render(Font font, ItemStack stack, int x, int y, float blitOffset) {
		if (!Config.CLIENT_SPEC.isLoaded() || !Config.CLIENT.showChargeBar.get()) {
			return false;
		}
		float ratio = chargeRatio(stack);
		if (ratio < 0) {
			return false;
		}
		int width = Math.round(Math.min(1f, ratio) * 13);
		RenderSystem.disableDepthTest();
		RenderSystem.disableTexture();
		RenderSystem.disableBlend();
		BufferBuilder buffer = Tesselator.getInstance().getBuilder();
		fillRect(buffer, x + 2, y + 13, 13, 2, 0xFF000000);
		fillRect(buffer, x + 2, y + 13, width, 1, BAR_COLOR);
		RenderSystem.enableBlend();
		RenderSystem.enableTexture();
		RenderSystem.enableDepthTest();
		return false;
	}

	private static void fillRect(BufferBuilder buffer, int x, int y, int width, int height, int argb) {
		int r = argb >> 16 & 255;
		int g = argb >> 8 & 255;
		int b = argb & 255;
		RenderSystem.setShader(GameRenderer::getPositionColorShader);
		buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
		buffer.vertex(x, y, 0).color(r, g, b, 255).endVertex();
		buffer.vertex(x, y + height, 0).color(r, g, b, 255).endVertex();
		buffer.vertex(x + width, y + height, 0).color(r, g, b, 255).endVertex();
		buffer.vertex(x + width, y, 0).color(r, g, b, 255).endVertex();
		BufferUploader.drawWithShader(buffer.end());
	}
}
