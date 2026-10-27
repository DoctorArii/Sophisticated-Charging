package com.doctorarii.sophisticatedcharging.energy;

import com.doctorarii.sophisticatedcharging.SophisticatedCharging;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A ban list from the config: single items ("modid:item"), whole mods ("modid:*" or "@modid") and item tags ("#ns:tag").
 */
public final class ItemMatcher {
	public static final ItemMatcher EMPTY = new ItemMatcher(Set.of(), Set.of(), List.of());

	private final Set<ResourceLocation> items;
	private final Set<String> mods;
	private final List<TagKey<Item>> tags;

	private ItemMatcher(Set<ResourceLocation> items, Set<String> mods, List<TagKey<Item>> tags) {
		this.items = items;
		this.mods = mods;
		this.tags = tags;
	}

	public static boolean isValidEntry(String raw) {
		String entry = raw.trim().toLowerCase(Locale.ROOT);
		if (entry.startsWith("@")) {
			return ResourceLocation.isValidNamespace(entry.substring(1)) && entry.length() > 1;
		}
		if (entry.endsWith(":*")) {
			String mod = entry.substring(0, entry.length() - 2);
			return !mod.isEmpty() && ResourceLocation.isValidNamespace(mod);
		}
		if (entry.startsWith("#")) {
			return ResourceLocation.tryParse(entry.substring(1)) != null && entry.length() > 1;
		}
		return ResourceLocation.tryParse(entry) != null && entry.contains(":");
	}

	public static ItemMatcher parse(List<? extends String> entries) {
		Set<ResourceLocation> items = new HashSet<>();
		Set<String> mods = new HashSet<>();
		List<TagKey<Item>> tags = new ArrayList<>();
		for (String raw : entries) {
			String entry = raw.trim().toLowerCase(Locale.ROOT);
			if (!isValidEntry(entry)) {
				SophisticatedCharging.LOGGER.warn("Ignoring invalid ban list entry '{}'", raw);
				continue;
			}
			if (entry.startsWith("@")) {
				mods.add(entry.substring(1));
			} else if (entry.endsWith(":*")) {
				mods.add(entry.substring(0, entry.length() - 2));
			} else if (entry.startsWith("#")) {
				tags.add(TagKey.create(Registries.ITEM, new ResourceLocation(entry.substring(1))));
			} else {
				items.add(new ResourceLocation(entry));
			}
		}
		if (items.isEmpty() && mods.isEmpty() && tags.isEmpty()) {
			return EMPTY;
		}
		return new ItemMatcher(Set.copyOf(items), Set.copyOf(mods), List.copyOf(tags));
	}

	public boolean matches(ItemStack stack) {
		if (this == EMPTY || stack.isEmpty()) {
			return false;
		}
		ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		if (items.contains(id) || mods.contains(id.getNamespace())) {
			return true;
		}
		for (TagKey<Item> tag : tags) {
			if (stack.is(tag)) {
				return true;
			}
		}
		return false;
	}
}
