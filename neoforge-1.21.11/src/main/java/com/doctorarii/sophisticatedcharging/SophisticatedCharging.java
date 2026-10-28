package com.doctorarii.sophisticatedcharging;

import com.doctorarii.sophisticatedcharging.init.ModContent;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(SophisticatedCharging.MOD_ID)
public class SophisticatedCharging {
	public static final String MOD_ID = "sophisticatedcharging";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public SophisticatedCharging(IEventBus modBus, ModContainer container) {
		container.registerConfig(ModConfig.Type.SERVER, Config.SERVER_SPEC);
		container.registerConfig(ModConfig.Type.CLIENT, Config.CLIENT_SPEC);
		modBus.addListener(Config::onConfigChanged);
		modBus.addListener(Config::onConfigLoaded);
		ModContent.register(modBus);
	}

	public static Identifier rl(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
