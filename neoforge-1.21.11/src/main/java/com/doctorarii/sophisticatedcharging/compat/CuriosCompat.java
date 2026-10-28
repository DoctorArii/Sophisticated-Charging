package com.doctorarii.sophisticatedcharging.compat;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

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
				for (ICurioStacksHandler slotType : curios.getCurios().values()) {
					IDynamicStackHandler stacks = slotType.getStacks();
					for (int slot = 0; slot < stacks.getSlots(); slot++) {
						action.accept(stacks.getStackInSlot(slot));
					}
				}
			});
		}
	}
}
