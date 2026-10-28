package com.doctorarii.sophisticatedcharging;

import com.doctorarii.sophisticatedcharging.init.ModContent;
import fuzs.forgeconfigapiport.api.config.v2.ForgeConfigRegistry;
import fuzs.forgeconfigapiport.api.config.v2.ModConfigEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Sophisticated Charging for the unofficial Fabric port of Sophisticated Backpacks. */
public final class SophisticatedCharging {
	public static final String MOD_ID = "sophisticatedcharging";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private SophisticatedCharging() {}

	public static void init() {
		ForgeConfigRegistry.INSTANCE.register(MOD_ID, ModConfig.Type.SERVER, Config.SERVER_SPEC);
		ModConfigEvents.loading(MOD_ID).register(Config::onConfigChanged);
		ModConfigEvents.reloading(MOD_ID).register(Config::onConfigChanged);
		ModContent.register();
	}

	public static ResourceLocation rl(String path) {
		return new ResourceLocation(MOD_ID, path);
	}
}
