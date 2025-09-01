
package com.drtdrc.goafk;

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

    /** Active AFK ticket areas by player UUID. */
    private static final Map<UUID, PlayerTicketArea> ACTIVE = new ConcurrentHashMap<>();

    /** Safety clamp to avoid accidentally blanketing the world. */
    private static final int MAX_RADIUS = 12; // chunks

    /** Ticket level to use. Lower = higher priority. 1 is safe and strong. */
    private static final int PLAYER_TICKET_LEVEL = 1;

    public static boolean toggleAfkAndKick(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        MinecraftServer server = player.getServer();
        if (ACTIVE.containsKey(id)) {
            PlayerTicketArea area = ACTIVE.remove(id);

            area.removeTickets(Objects.requireNonNull(server));

            return false; // now disabled
        }

        ServerWorld world = player.getWorld();
        BlockPos pos = player.getBlockPos();

        int view = Objects.requireNonNull(server).getPlayerManager().getViewDistance();
        int sim  = server.getPlayerManager().getSimulationDistance();
        int radius = Math.max(view, sim);
        radius = Math.min(radius, MAX_RADIUS);
        GoAFK.LOGGER.info(String.valueOf(radius));

        PlayerTicketArea area = PlayerTicketArea.create(world, pos, radius);
        area.addTickets(world);
        ACTIVE.put(id, area);

        player.networkHandler.disconnect(Text.literal("AFK enabled.\n" +
                "Chunks near your location are held with PLAYER tickets.\n" +
                "Rejoin to disable."));

        return true; // now enabled
    }

    /** Auto-clean on join. */
    public static void onPlayerJoin(ServerPlayerEntity player) {
        PlayerTicketArea area = ACTIVE.remove(player.getUuid());
        if (area != null) {
            area.removeTickets(Objects.requireNonNull(player.getServer()));
            player.sendMessage(Text.literal("Welcome back! Your AFK player tickets were removed."), false);
        }
    }

    /** Clean up on server stop. */
    public static void clearAll(MinecraftServer server) {
        for (PlayerTicketArea a : ACTIVE.values()) a.removeTickets(server);
        ACTIVE.clear();
        GoAFK.LOGGER.info("cleared load tickets");
    }

    /** A square of chunks kept loaded by PLAYER tickets. */
    private record PlayerTicketArea(RegistryKey<World> dimension, ChunkPos center, int radius, List<ChunkPos> chunks) {
        static PlayerTicketArea create(ServerWorld world, BlockPos around, int radius) {
            ChunkPos c = new ChunkPos(around);
            List<ChunkPos> list = new ArrayList<>();
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    list.add(new ChunkPos(c.x + dx, c.z + dz));
                }
            }
            GoAFK.LOGGER.info("create complete");
            return new PlayerTicketArea(world.getRegistryKey(), c, radius, list);
        }

        void addTickets(ServerWorld world) {
            ServerChunkManager cm = world.getChunkManager();
            for (ChunkPos cp : chunks) {
                // Arg commonly equals the position; level=1 ensures strong loading.
                cm.addTicket(ChunkTicketType.PLAYER_SIMULATION, cp, PLAYER_TICKET_LEVEL);

            }
            GoAFK.LOGGER.info("addTickets complete");
        }

        void removeTickets(MinecraftServer server) {
            ServerWorld world = server.getWorld(dimension);
            if (world == null) return;
            ServerChunkManager cm = world.getChunkManager();
            for (ChunkPos cp : chunks) {
                cm.removeTicket(ChunkTicketType.PLAYER_SIMULATION, cp, PLAYER_TICKET_LEVEL);
            }
            GoAFK.LOGGER.info("remove tickets success");
        }
    }
}
