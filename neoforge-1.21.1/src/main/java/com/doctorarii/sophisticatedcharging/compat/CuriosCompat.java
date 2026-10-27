package com.doctorarii.sophisticatedcharging.compat;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.items.IItemHandler;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.function.Consumer;

/** Curios is optional: {@link Internal} (which touches the Curios API) is only loaded when Curios is present. */
public final class CuriosCompat {
	private static final boolean LOADED = ModList.get().isLoaded("curios");

	private CuriosCompat() {}

	public static boolean isLoaded() {
		return LOADED;
	}

	/** Calls {@code action} for every item in the player's Curios slots (changed in place; Curios syncs them). */
	public static void forEachEquipped(Player player, Consumer<ItemStack> action) {
		if (LOADED) {
			Internal.forEachEquipped(player, action);
		}
	}

	private static final class Internal {
		static void forEachEquipped(Player player, Consumer<ItemStack> action) {
			CuriosApi.getCuriosInventory(player).ifPresent(curios -> {
				IItemHandler equipped = curios.getEquippedCurios();
				for (int slot = 0; slot < equipped.getSlots(); slot++) {
					action.accept(equipped.getStackInSlot(slot));
				}
			});
		}
	}
}
