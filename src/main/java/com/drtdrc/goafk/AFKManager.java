
package com.drtdrc.goafk;

import net.minecraft.entity.Entity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ChunkTicketType; // <-- If your mappings call this TicketType, adjust import.
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class AFKManager {
    private AFKManager() {}

    private static final Map<UUID, Anchor> ACTIVE = new ConcurrentHashMap<>();
    private static final int MAX_AREA_RADIUS = 12; // chunks
    private static final int TICKET_LEVEL_RADIUS = 3;
    public static boolean toggleAfkAndKick(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        MinecraftServer server = player.getServer();
        if (ACTIVE.containsKey(id)) {
            Anchor a = ACTIVE.remove(id);
            if (a != null) a.cleanup(Objects.requireNonNull(server));
            return false; // now disabled
        }

        ServerWorld world = player.getWorld();
        BlockPos pos = player.getBlockPos();

        int radius = Math.max(
                Objects.requireNonNull(server).getPlayerManager().getViewDistance(),
                server.getPlayerManager().getSimulationDistance()
        );
        radius = Math.min(radius, MAX_AREA_RADIUS);

        ServerChunkManager cm = world.getChunkManager();
        List<ChunkPos> chunks = new ArrayList<>();
        ChunkPos center = new ChunkPos(pos);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                ChunkPos cp = new ChunkPos(center.x + dx, center.z + dz);
                chunks.add(cp);
                cm.addTicket(ChunkTicketType.PLAYER_LOADING, cp, TICKET_LEVEL_RADIUS);
                cm.addTicket(ChunkTicketType.PLAYER_SIMULATION, cp, TICKET_LEVEL_RADIUS);
            }
        }

        ACTIVE.put(id, new Anchor(world.getRegistryKey(), pos.toImmutable(), chunks, TICKET_LEVEL_RADIUS));

        player.networkHandler.disconnect(Text.literal("AFK enabled.\n" +
                "Chunks near your location are held with PLAYER tickets.\n" +
                "Rejoin to disable."));

        return true; // now enabled
    }

    /** Auto-clean on join. */
    public static void onPlayerJoin(ServerPlayerEntity player) {
        Anchor a = ACTIVE.remove(player.getUuid());
        if (a != null) {
            a.cleanup(Objects.requireNonNull(player.getServer()));
        }
    }

    /** Clean up on server stop. */
    public static void clearAll(MinecraftServer server) {
        for (Anchor a : ACTIVE.values()) a.cleanup(server);
        ACTIVE.clear();
    }

    /** Exposed to mixins: all anchor positions in this world. */
    public static List<BlockPos> getAnchorPositions(ServerWorld world) {
        List<BlockPos> out = new ArrayList<>();
        for (Anchor a : ACTIVE.values()) {
            if (a.dim.equals(world.getRegistryKey())) out.add(a.pos);
        }
        return out;
    }

    private record Anchor(RegistryKey<World> dim, BlockPos pos, List<ChunkPos> chunks, int ticketLevelRadius) {
        void cleanup(MinecraftServer server) {
            ServerWorld world = server.getWorld(dim);
            if (world == null) return;

            // remove tickets
            ServerChunkManager cm = world.getChunkManager();
            for (ChunkPos cp : chunks) {
                cm.removeTicket(ChunkTicketType.PLAYER_LOADING, cp, ticketLevelRadius);
                cm.removeTicket(ChunkTicketType.PLAYER_SIMULATION, cp, ticketLevelRadius);
            }

        }
    }
}
