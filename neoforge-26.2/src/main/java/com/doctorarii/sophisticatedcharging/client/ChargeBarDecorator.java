package com.doctorarii.sophisticatedcharging.client;

import com.doctorarii.sophisticatedcharging.Config;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.IItemDecorator;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackItem;
import net.p3pp3rf1y.sophisticatedcore.init.ModCoreDataComponents;
import net.p3pp3rf1y.sophisticatedcore.renderdata.RenderData;

/**
 * A thin energy bar under backpack icons, read from the battery charge Sophisticated Backpacks keeps on the backpack item
 * (the pool of a Charging Upgrade, or SB's own Battery Upgrade). Gold, so it is never mistaken for a durability bar.
 */
public final class ChargeBarDecorator implements IItemDecorator {
	private static final int BAR_COLOR = 0xFFFFC21F;

	private ChargeBarDecorator() {}

	static void register(RegisterItemDecorationsEvent event) {
		ChargeBarDecorator decorator = new ChargeBarDecorator();
		for (Item item : BuiltInRegistries.ITEM) {
			if (item instanceof BackpackItem) {
				event.register(item, decorator);
			}
		}
	}

	/** Charge (0 to 1) of the backpack's battery display, or -1 if it has none. */
	public static float chargeRatio(ItemStack stack) {
		RenderData renderData = stack.get(ModCoreDataComponents.RENDER_DATA.get());
		if (renderData == null) {
			return -1;
		}
		return renderData.battery().map(RenderData.BatteryRenderData::chargeRatio).orElse(-1f);
	}

	@Override
	public boolean render(GuiGraphicsExtractor guiGraphics, Font font, ItemStack stack, int x, int y) {
		if (!Config.CLIENT_SPEC.isLoaded() || !Config.CLIENT.showChargeBar.get()) {
			return false;
		}
		float ratio = chargeRatio(stack);
		if (ratio < 0) {
			return false;
		}
		int width = Math.round(Math.min(1f, ratio) * 13);
		guiGraphics.fill(RenderPipelines.GUI, x + 2, y + 13, x + 15, y + 15, 0xFF000000);
		guiGraphics.fill(RenderPipelines.GUI, x + 2, y + 13, x + 2 + width, y + 14, BAR_COLOR);
		return false;
	}
}
