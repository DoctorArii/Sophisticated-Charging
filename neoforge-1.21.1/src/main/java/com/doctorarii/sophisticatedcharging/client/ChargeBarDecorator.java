package com.doctorarii.sophisticatedcharging.client;

import com.doctorarii.sophisticatedcharging.Config;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.client.IItemDecorator;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackItem;
import net.p3pp3rf1y.sophisticatedcore.init.ModCoreDataComponents;
import net.p3pp3rf1y.sophisticatedcore.upgrades.IRenderedBatteryUpgrade.BatteryRenderInfo;

/**
 * A thin energy bar under backpack icons, read from the battery charge Sophisticated Backpacks keeps on the backpack item
 * (the pool of a Charging Upgrade, or SB's own Battery Upgrade). Gold, so it is never mistaken for a durability bar.
 */
public final class ChargeBarDecorator implements IItemDecorator {
	private static final String BATTERY_KEY = "battery";
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
		CustomData renderInfo = stack.get(ModCoreDataComponents.RENDER_INFO_TAG.get());
		if (renderInfo == null || !renderInfo.contains(BATTERY_KEY)) {
			return -1;
		}
		return BatteryRenderInfo.deserialize(renderInfo.copyTag().getCompound(BATTERY_KEY)).getChargeRatio();
	}

	@Override
	public boolean render(GuiGraphics guiGraphics, Font font, ItemStack stack, int x, int y) {
		if (!Config.CLIENT_SPEC.isLoaded() || !Config.CLIENT.showChargeBar.get()) {
			return false;
		}
		float ratio = chargeRatio(stack);
		if (ratio < 0) {
			return false;
		}
		int width = Math.round(Math.min(1f, ratio) * 13);
		guiGraphics.pose().pushPose();
		guiGraphics.pose().translate(0, 0, 200);
		guiGraphics.fill(RenderType.guiOverlay(), x + 2, y + 13, x + 15, y + 15, 0xFF000000);
		guiGraphics.fill(RenderType.guiOverlay(), x + 2, y + 13, x + 2 + width, y + 14, BAR_COLOR);
		guiGraphics.pose().popPose();
		return false;
	}
}
