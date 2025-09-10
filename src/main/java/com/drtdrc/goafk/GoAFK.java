package com.drtdrc.goafk;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GoAFK implements ModInitializer {
    public static final String MOD_ID = "goafk";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {

        // Register *all* your commands from one place, keep them in separate classes
        CommandRegistrationCallback.EVENT.register(AFKCommand::register);
        ServerLifecycleEvents.SERVER_STOPPING.register(AFKManager::clearAll);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> AFKManager.onPlayerJoin(handler.player));
    }
}
