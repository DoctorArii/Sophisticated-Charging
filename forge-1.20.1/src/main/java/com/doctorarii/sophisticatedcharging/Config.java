package com.doctorarii.sophisticatedcharging;

import com.doctorarii.sophisticatedcharging.energy.BatteryRules;
import com.doctorarii.sophisticatedcharging.energy.ItemMatcher;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * Server config (per world, synced to clients). Lives in {@code <world>/serverconfig/sophisticatedcharging-server.toml};
 * defaults for new worlds can be put in {@code defaultconfigs/}.
 */
public final class Config {
	private static final String ENTRY_HELP = "Entries: \"modid:item\" for one item, \"modid:*\" or \"@modid\" for every item of a mod, \"#namespace:tag\" for an item tag.";

	public static final ForgeConfigSpec SERVER_SPEC;
	public static final Server SERVER;

	static {
		ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
		SERVER = new Server(builder);
		SERVER_SPEC = builder.build();
	}

	private static ItemMatcher bannedBatteries = ItemMatcher.EMPTY;
	private static ItemMatcher bannedChargeTargets = ItemMatcher.EMPTY;

	private Config() {}

	public static final class Server {
		public final ForgeConfigSpec.IntValue basicChargeRate;
		public final ForgeConfigSpec.IntValue advancedChargeRate;
		public final ForgeConfigSpec.IntValue chargeInterval;
		public final ForgeConfigSpec.IntValue advancedFilterSlots;
		public final ForgeConfigSpec.BooleanValue allowInventoryCharging;
		public final ForgeConfigSpec.BooleanValue allowCuriosCharging;
		public final ForgeConfigSpec.BooleanValue allowContentsCharging;

		public final ForgeConfigSpec.ConfigValue<List<? extends String>> bannedBatteries;
		public final ForgeConfigSpec.BooleanValue banInfiniteBatteries;
		public final ForgeConfigSpec.IntValue maxBatteryCapacity;

		public final ForgeConfigSpec.ConfigValue<List<? extends String>> bannedChargeTargets;
		public final ForgeConfigSpec.BooleanValue chargeBatteriesOutsideBackpack;

		Server(ForgeConfigSpec.Builder builder) {
			builder.comment("How fast the Charging Upgrades charge your gear").push("charging");
			basicChargeRate = builder.comment("FE per tick the Charging Upgrade moves from the backpack's batteries into your items (shared by all items it charges).")
					.defineInRange("basicChargeRate", 1_000, 1, Integer.MAX_VALUE);
			advancedChargeRate = builder.comment("FE per tick the Advanced Charging Upgrade moves from the backpack's batteries into your items.")
					.defineInRange("advancedChargeRate", 10_000, 1, Integer.MAX_VALUE);
			chargeInterval = builder.comment("Charge every N ticks (the rate is multiplied accordingly). Higher = less server work, chunkier charging.")
					.defineInRange("chargeInterval", 10, 1, 100);
			advancedFilterSlots = builder.comment("Number of filter slots of the Advanced Charging Upgrade.")
					.defineInRange("advancedFilterSlots", 9, 1, 16);
			allowInventoryCharging = builder.comment("Let the Advanced Charging Upgrade charge the whole player inventory (hotbar and main inventory).")
					.define("allowInventoryCharging", true);
			allowCuriosCharging = builder.comment("Let the Advanced Charging Upgrade charge items in Curios slots (needs Curios).")
					.define("allowCuriosCharging", true);
			allowContentsCharging = builder.comment("Let the Advanced Charging Upgrade charge tools and other energy items stored in the backpack itself.")
					.define("allowContentsCharging", true);
			builder.pop();

			builder.comment("Which items may act as batteries (energy sources) in the backpack's shared pool").push("batteries");
			bannedBatteries = builder.comment("Items that never count as a battery, so they are never drained or filled by the pool. " + ENTRY_HELP,
							"Items in the tag #sophisticatedcharging:banned_batteries are banned too (handy for datapacks / KubeJS).")
					.defineListAllowEmpty(List.of("bannedBatteries"), List::of, Config::isValidEntry);
			banInfiniteBatteries = builder.comment("Leave out batteries that never run empty (creative / infinite energy items). They are detected automatically by",
							"test-draining a copy of the item once.")
					.define("banInfiniteBatteries", true);
			maxBatteryCapacity = builder.comment("Batteries holding more than this many FE when full are left out. 0 = no limit.",
							"Use this to keep very large (\"OP\") batteries out without listing them one by one.")
					.defineInRange("maxBatteryCapacity", 0, 0, Integer.MAX_VALUE);
			builder.pop();

			builder.comment("Which items the Charging Upgrades may charge").push("chargeTargets");
			bannedChargeTargets = builder.comment("Items that are never charged. " + ENTRY_HELP,
							"Items in the tag #sophisticatedcharging:banned_charge_targets are banned too.")
					.defineListAllowEmpty(List.of("bannedChargeTargets"), List::of, Config::isValidEntry);
			chargeBatteriesOutsideBackpack = builder.comment("Also charge batteries that are in your inventory (outside the backpack). Off by default: it only moves",
							"energy from one battery to another, and some batteries charge the backpack back, which would loop.",
							"What counts as a battery is detected automatically; tag items #sophisticatedcharging:batteries or",
							"#sophisticatedcharging:not_batteries to override it.")
					.define("chargeBatteriesOutsideBackpack", false);
			builder.pop();
		}
	}

	private static boolean isValidEntry(Object entry) {
		return entry instanceof String s && ItemMatcher.isValidEntry(s);
	}

	public static ItemMatcher bannedBatteries() {
		return bannedBatteries;
	}

	public static ItemMatcher bannedChargeTargets() {
		return bannedChargeTargets;
	}

	/** True once the server config is loaded (it is not, e.g., on a client in the main menu). */
	public static boolean isLoaded() {
		return SERVER_SPEC.isLoaded();
	}

	static void onConfigLoaded(ModConfigEvent.Loading event) {
		refresh(event);
	}

	static void onConfigChanged(ModConfigEvent.Reloading event) {
		refresh(event);
	}

	private static void refresh(ModConfigEvent event) {
		if (event.getConfig().getSpec() != SERVER_SPEC) {
			return;
		}
		rebuildMatchers();
	}

	/** Rebuilds the ban list matchers from the current config values. */
	public static void rebuildMatchers() {
		bannedBatteries = ItemMatcher.parse(SERVER.bannedBatteries.get());
		bannedChargeTargets = ItemMatcher.parse(SERVER.bannedChargeTargets.get());
		BatteryRules.clearCaches();
	}
}
