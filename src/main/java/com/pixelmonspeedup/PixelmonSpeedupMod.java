package com.pixelmonspeedup;

import com.pixelmonspeedup.config.ConfigRuntime;
import com.pixelmonspeedup.event.WelcomeMessageHandler;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod("pixelmonspeedup")
public final class PixelmonSpeedupMod {
    public PixelmonSpeedupMod(IEventBus modEventBus) {
        ConfigRuntime.init();
        NeoForge.EVENT_BUS.addListener(WelcomeMessageHandler::onPlayerLoggedIn);
    }
}



