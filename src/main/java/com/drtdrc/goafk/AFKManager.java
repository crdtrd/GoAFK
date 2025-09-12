
package com.drtdrc.goafk;

import com.drtdrc.goafk.storage.AFKAnchorsState;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class AFKManager {
    private AFKManager() {}

    public static final Map<UUID, Anchor> ACTIVE = new ConcurrentHashMap<>();
    public static final int TICKET_LEVEL_RADIUS = 3;

    public static int computeRadius(@NotNull MinecraftServer server) {
        return Math.max(
                server.getPlayerManager().getViewDistance(),
                server.getPlayerManager().getSimulationDistance()
        );
    }

    static void addTicketsAround(@NotNull ServerWorld world, BlockPos pos, int radius) {
        ServerChunkManager cm = world.getChunkManager();
        ChunkPos center = new ChunkPos(pos);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                ChunkPos cp = new ChunkPos(center.x + dx, center.z + dz);
                cm.addTicket(ChunkTicketType.PLAYER_LOADING, cp, TICKET_LEVEL_RADIUS);
                cm.addTicket(ChunkTicketType.PLAYER_SIMULATION, cp, TICKET_LEVEL_RADIUS);
            }
        }
    }

    private static void removeTicketsAround(@NotNull ServerWorld world, BlockPos pos, int radius) {
        ServerChunkManager cm = world.getChunkManager();
        ChunkPos center = new ChunkPos(pos);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                cm.removeTicket(ChunkTicketType.PLAYER_LOADING, new ChunkPos(center.x + dx, center.z + dz), AFKManager.TICKET_LEVEL_RADIUS);
                cm.removeTicket(ChunkTicketType.PLAYER_SIMULATION, new ChunkPos(center.x + dx, center.z + dz), AFKManager.TICKET_LEVEL_RADIUS);
            }
        }
    }

    public static boolean toggleAfkAndKick(@NotNull ServerPlayerEntity player) {
        UUID id = player.getUuid();
        MinecraftServer server = player.getServer();
        int radius = computeRadius(Objects.requireNonNull(server));

        if (ACTIVE.containsKey(id)) {
            Anchor a = ACTIVE.remove(id);
            if (a != null) {
                // drop tickets from memory path
                ServerWorld w = server.getWorld(a.dim);
                if (w != null) {
                    removeTicketsAround(w, a.pos, a.radiusChunks);
                    AFKAnchorsState.get(w).remove(a.pos);
                }
            }
            return false; // now disabled
        }

        ServerWorld world = player.getWorld();
        BlockPos pos = player.getBlockPos();

        // persist the anchor
        AFKAnchorsState.get(world).add(pos);

        // create tickets now
        addTicketsAround(world, pos, radius);

        // remember for auto-clean when they rejoin soon after
        ACTIVE.put(id, new Anchor(world.getRegistryKey(), pos, radius));

        player.networkHandler.disconnect(Text.literal("You are now AFK!"));

        return true;
    }

    public static void onPlayerJoin(@NotNull ServerPlayerEntity player) {
        Anchor a = ACTIVE.remove(player.getUuid());
        if (a != null) {
            MinecraftServer server = Objects.requireNonNull(player.getServer());
            ServerWorld w = server.getWorld(a.dim);
            if (w != null) {
                removeTicketsAround(w, a.pos, a.radiusChunks);
                AFKAnchorsState.get(w).remove(a.pos);
            }
        }
    }

    /** Admin/API toggle: add/remove anchor and tickets at current block. */
    public static boolean toggleAnchor(ServerWorld world, BlockPos pos) {
        var state = AFKAnchorsState.get(world);
        int radius = computeRadius(world.getServer());
        if (state.contains(pos)) {
            state.remove(pos);
            removeTicketsAround(world, pos, radius);
            return false;
        } else {
            state.add(pos);
            addTicketsAround(world, pos, radius);
            return true;
        }
    }

    public static @NotNull @Unmodifiable List<BlockPos> getAnchorPositions(ServerWorld world) {
        return AFKAnchorsState.get(world).getAll();
    }

    public record Anchor(RegistryKey<World> dim, BlockPos pos, int radiusChunks) {}
}
