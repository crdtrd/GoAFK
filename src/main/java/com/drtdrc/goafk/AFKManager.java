
package com.drtdrc.goafk;

import com.drtdrc.goafk.storage.AFKAnchorsState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;

public final class AFKManager {
    private AFKManager() {}

    public static final int TICKET_LEVEL_RADIUS = 3;

    public static String getDefaultName(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }
    public static void spawnAnchorLabel(ServerWorld world, BlockPos pos, String name) {

        DisplayEntity.TextDisplayEntity label = EntityType.TEXT_DISPLAY.create(world, SpawnReason.CHUNK_GENERATION);
        if (label == null) return;

        label.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY() + 2.1, pos.getZ() + 0.5, 0f, 0f);
        label.setNoGravity(true);
        label.setSilent(true);
        label.setInvulnerable(true);
        label.setBillboardMode(DisplayEntity.BillboardMode.CENTER);
        label.setGlowing(true);
        label.setBackground(0x40000000);
        label.setLineWidth(140);
        label.setText(Text.literal(name).formatted(Formatting.WHITE));

        world.spawnEntity(label);
    }

    private static void removeAnchorLabel(ServerWorld world, BlockPos pos, String name) {
        Box search = new Box(
                pos.getX() + 0.4, pos.getY(), pos.getZ() + 0.4,
                pos.getX() + 0.6, pos.getY() + 3.0, pos.getZ() + 0.6
        );
        for (DisplayEntity.TextDisplayEntity td :
                world.getEntitiesByClass(DisplayEntity.TextDisplayEntity.class, search, tde -> {
                    String tdeText = tde.getText().getString();
                    return tdeText.equals(name);
                })) {
                td.discard();
            }
    }

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

        // Now removeAllByPosition tickets only for chunks that are no longer covered
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
        ServerWorld world = player.getWorld();
        BlockPos pos = player.getBlockPos();
        String playerName = player.getGameProfile().getName();

        addAnchor(world, pos, playerName);

        player.networkHandler.disconnect(Text.literal("You are now AFK!"));

        return true;
    }

    public static void onPlayerJoin(@NotNull ServerPlayerEntity player) {
        String playerName = player.getGameProfile().getName();
        BlockPos pos = player.getBlockPos();
        ServerWorld world = player.getWorld();
        removeAnchor(world, pos, playerName);
    }

    public static @NotNull @Unmodifiable List<BlockPos> getAnchorPositions(ServerWorld world) {
        return AFKAnchorsState.get(world).getAllPositions();
    }

    public static boolean addAnchor(ServerWorld world, BlockPos pos, String name) {
        var state = AFKAnchorsState.get(world);
        if (!state.add(pos, name)) return false;
        addTicketsAround(world, pos, computeRadius(world.getServer()));
        spawnAnchorLabel(world, pos, name);
        return true;
    }

    public static boolean removeAnchor(ServerWorld world, BlockPos pos, String name) {

        var anchorState = AFKAnchorsState.get(world);
        List<AFKAnchorsState.AFKAnchor> afkAnchorsToRemove = anchorState.removeAnchor(pos, name);
        if (afkAnchorsToRemove.isEmpty()) return false;
        for (AFKAnchorsState.AFKAnchor a : afkAnchorsToRemove) {
            BlockPos p = a.pos();
            removeTicketsAround(world, p, computeRadius(world.getServer()));
            removeAnchorLabel(world, p, a.name());
        }
        return true;
    }


}
