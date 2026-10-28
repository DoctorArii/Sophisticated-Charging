package com.doctorarii.sophisticatedcharging.client;

import com.doctorarii.sophisticatedcharging.energy.BatteryPool;
import com.doctorarii.sophisticatedcharging.energy.EnergyFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.p3pp3rf1y.sophisticatedcore.client.gui.controls.WidgetBase;
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.Dimension;
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.Position;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/** Energy bar plus "stored / capacity" and battery count of the backpack's pool. */
public class EnergyInfoWidget extends WidgetBase {
	private static final int MIN_WIDTH = 74;
	private static final int BAR_HEIGHT = 5;
	private static final int TEXT_COLOR = 0x404040;

	private final Supplier<BatteryPool> pool;

	public EnergyInfoWidget(Position position, Supplier<BatteryPool> pool) {
		super(position, new Dimension(widthForLongestText(), BAR_HEIGHT + 22));
		this.pool = pool;
	}

	/** Wide enough for the longest numbers the lines can show (in the current language), so text never runs past the tab. */
	private static int widthForLongestText() {
		Font font = Minecraft.getInstance().font;
		int stored = font.width(Component.translatable("gui.sophisticatedcharging.pool.stored", "999.9M", "999.9M"));
		int count = font.width(Component.translatable("gui.sophisticatedcharging.pool.batteries_and_upgrade", 999));
		return Math.max(MIN_WIDTH, Math.max(stored, count) + 1);
	}

	@Override
	protected void renderBg(PoseStack poseStack, Minecraft minecraft, int mouseX, int mouseY) {
		// drawn in renderWidget
	}

	@Override
	protected void renderWidget(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
		BatteryPool batteryPool = pool.get();
		long stored = batteryPool.getEnergyStoredLong();
		long capacity = batteryPool.getMaxEnergyStoredLong();
		int barX = x;
		int barY = y;
		int width = getWidth();
		fill(poseStack, barX, barY, barX + width, barY + BAR_HEIGHT, 0xFF373737);
		fill(poseStack, barX + 1, barY + 1, barX + width - 1, barY + BAR_HEIGHT - 1, 0xFF8B8B8B);
		if (capacity > 0 && stored > 0) {
			int filled = (int) Math.max(1, Math.round((width - 2) * ((double) stored / capacity)));
			fill(poseStack, barX + 1, barY + 1, barX + 1 + filled, barY + BAR_HEIGHT - 1, 0xFFE03A2E);
			fill(poseStack, barX + 1, barY + 1, barX + 1 + filled, barY + 2, 0xFFFF7A4D);
		}
		Font font = Minecraft.getInstance().font;
		font.draw(poseStack, Component.translatable("gui.sophisticatedcharging.pool.stored", EnergyFormat.format(stored),
				EnergyFormat.format(capacity)), x, y + BAR_HEIGHT + 3, TEXT_COLOR);
		int batteries = batteryPool.getBatteryCount();
		Component countLine = batteryPool.getBase() != null
				? Component.translatable("gui.sophisticatedcharging.pool.batteries_and_upgrade", batteries)
				: Component.translatable("gui.sophisticatedcharging.pool.batteries", batteries);
		font.draw(poseStack, countLine, x, y + BAR_HEIGHT + 13, TEXT_COLOR);
	}

	@Override
	public void updateNarration(NarrationElementOutput output) {
		// the numbers are in the tooltip
	}

	@Override
	public void renderTooltip(Screen screen, PoseStack poseStack, int mouseX, int mouseY) {
		super.renderTooltip(screen, poseStack, mouseX, mouseY);
		if (!isMouseOver(mouseX, mouseY)) {
			return;
		}
		BatteryPool batteryPool = pool.get();
		NumberFormat numbers = NumberFormat.getIntegerInstance(Locale.ROOT);
		List<Component> lines = List.of(
				Component.translatable("gui.sophisticatedcharging.pool.tooltip.title"),
				Component.translatable("gui.sophisticatedcharging.pool.tooltip.stored", numbers.format(batteryPool.getEnergyStoredLong()),
						numbers.format(batteryPool.getMaxEnergyStoredLong())),
				Component.translatable("gui.sophisticatedcharging.pool.tooltip.help"));
		screen.renderComponentTooltip(poseStack, lines, mouseX, mouseY);
	}
}
