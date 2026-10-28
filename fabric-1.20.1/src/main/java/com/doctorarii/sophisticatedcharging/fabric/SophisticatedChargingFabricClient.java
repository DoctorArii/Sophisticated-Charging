package com.doctorarii.sophisticatedcharging.fabric;

import com.doctorarii.sophisticatedcharging.client.ClientSetup;
import net.fabricmc.api.ClientModInitializer;

public final class SophisticatedChargingFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientSetup.init();
    }
}
