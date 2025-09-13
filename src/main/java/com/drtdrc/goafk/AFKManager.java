
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

    public static final Map<String, Anchor> ACTIVE = new ConcurrentHashMap<>(); // not sure I really need this
    public static final int TICKET_LEVEL_RADIUS = 3;

    public static int computeRadius(@NotNull MinecraftServer server) {
        return Math.max(
                server.getPlayerManager().getViewDistance(),
                server.getPlayerManager().getSimulationDistance()
        );
    }

    public static void addTicketsAround(@NotNull ServerWorld world, BlockPos pos, int radius) {
        ServerChunkManager cm = world.getChunkManager();
        ChunkPos center = new ChunkPos(pos);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                ChunkPos cp = new ChunkPos(center.x + dx, center.z + dz);
                cm.addTicket(ChunkTicketType.FORCED, cp, TICKET_LEVEL_RADIUS);
            }
        }
    }

    public static void removeTicketsAround(@NotNull ServerWorld world, BlockPos pos, int radius) {
        var state = AFKAnchorsState.get(world);
        ServerChunkManager cm = world.getChunkManager();

        // Build the set of chunks still covered by remaining anchors
        Set<ChunkPos> keep = new HashSet<>();
        for (BlockPos p : state.getAllPositions()) {
            ChunkPos c = new ChunkPos(p);
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    keep.add(new ChunkPos(c.x + dx, c.z + dz));
                }
            }
        }

        // Now remove tickets only for chunks that are no longer covered
        ChunkPos center = new ChunkPos(pos);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                ChunkPos cp = new ChunkPos(center.x + dx, center.z + dz);
                if (!keep.contains(cp)) {
                    cm.removeTicket(ChunkTicketType.FORCED, cp, TICKET_LEVEL_RADIUS);
                }
            }
        }
    }

    public static boolean goAFKAndKick(@NotNull ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        int radius = computeRadius(Objects.requireNonNull(server));
        ServerWorld world = player.getWorld();
        BlockPos pos = player.getBlockPos();
        String playerName = player.getGameProfile().getName();

        addAnchor(world, pos, playerName);

        // remember for auto-clean when they rejoin soon after
        ACTIVE.put(playerName, new Anchor(world.getRegistryKey(), pos, radius));

        player.networkHandler.disconnect(Text.literal("You are now AFK!"));

        return true;
    }

    public static void onPlayerJoin(@NotNull ServerPlayerEntity player) {
        MinecraftServer server = Objects.requireNonNull(player.getServer());
        String playerName = player.getGameProfile().getName();
        Anchor a = ACTIVE.remove(playerName);
        if (a != null) {
            ServerWorld w = server.getWorld(a.dim);
            if (w != null) {
                AFKAnchorsState.get(w).remove(a.pos);
                removeTicketsAround(w, a.pos, a.radiusChunks);
            }
        }

        int radius = computeRadius(server);
        for (ServerWorld w : server.getWorlds()) {
            var removed = AFKAnchorsState.get(w).removeAllByOwner(playerName);
            for (BlockPos pos : removed) {
                removeTicketsAround(w, pos, radius);
            }
        }
    }



    public static @NotNull @Unmodifiable List<BlockPos> getAnchorPositions(ServerWorld world) {
        return AFKAnchorsState.get(world).getAllPositions();
    }

    public static boolean addAnchor(ServerWorld world, BlockPos pos, String ownerOrNull) {
        var state = AFKAnchorsState.get(world);
        if (!state.add(pos, ownerOrNull)) return false;
        addTicketsAround(world, pos, computeRadius(world.getServer()));
        return true;
    }

    public static boolean removeAnchor(ServerWorld world, BlockPos pos) {
        var state = AFKAnchorsState.get(world);
        if (!state.remove(pos)) return false;
        removeTicketsAround(world, pos, computeRadius(world.getServer()));
        return true;
    }

    public record Anchor(RegistryKey<World> dim, BlockPos pos, int radiusChunks) {}
}
