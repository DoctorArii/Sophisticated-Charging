package com.doctorarii.sophisticatedcharging;

import com.doctorarii.sophisticatedcharging.client.ClientSetup;
import com.doctorarii.sophisticatedcharging.init.ModContent;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(SophisticatedCharging.MOD_ID)
public class SophisticatedCharging {
	public static final String MOD_ID = "sophisticatedcharging";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public SophisticatedCharging() {
		IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
		ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, Config.SERVER_SPEC);
		ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, Config.CLIENT_SPEC);
		modBus.addListener(Config::onConfigChanged);
		modBus.addListener(Config::onConfigLoaded);
		ModContent.register(modBus);
		if (FMLEnvironment.dist.isClient()) {
			ClientSetup.init(modBus);
		}
	}

	public static ResourceLocation rl(String path) {
		return new ResourceLocation(MOD_ID, path);
	}
}
