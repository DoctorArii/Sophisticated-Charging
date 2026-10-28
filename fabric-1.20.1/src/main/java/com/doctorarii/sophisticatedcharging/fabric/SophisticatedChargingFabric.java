package com.doctorarii.sophisticatedcharging.fabric;

import com.doctorarii.sophisticatedcharging.SophisticatedCharging;
import net.fabricmc.api.ModInitializer;

public final class SophisticatedChargingFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        SophisticatedCharging.init();
    }
}
