package com.drtdrc.goafk;

import com.drtdrc.goafk.storage.AFKAnchorsState;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GoAFK implements ModInitializer {
    public static final String MOD_ID = "goafk";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            for (ServerWorld world : server.getWorlds()) {
                var anchors = AFKAnchorsState.get(world).getAll();
                int radius = Math.min(
                        Math.max(server.getPlayerManager().getViewDistance(), server.getPlayerManager().getSimulationDistance()),
                        AFKManager.computeRadius(server)
                );
                for (BlockPos pos : anchors) {
                    AFKManager.addTicketsAround(world, pos, radius);
                }
            }
        });
        CommandRegistrationCallback.EVENT.register(AFKCommand::register);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> AFKManager.onPlayerJoin(handler.player));
    }
}
