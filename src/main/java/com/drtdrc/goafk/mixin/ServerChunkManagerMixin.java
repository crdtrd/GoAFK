// src/main/java/com/drtdrc/goafk/mixin/ServerChunkManagerMixin.java
package com.drtdrc.goafk.mixin;

import com.drtdrc.goafk.AFKManager;

import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.world.SpawnDensityCapper;
import net.minecraft.world.SpawnHelper;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Mixin(ServerChunkManager.class)
public abstract class ServerChunkManagerMixin {
    @Final
    @Shadow private ServerWorld world;

    // chunk ring matching 24..128 blocks → 2..8 chunks (Chebyshev distance)
    @Unique
    private static final int MIN_RING = 2;
    @Unique
    private static final int MAX_RING = 8;
    @Final
    @Mutable
    @Shadow private List<WorldChunk> spawningChunks;

    @Redirect(
            method = "tickChunks(Lnet/minecraft/util/profiler/Profiler;J)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/SpawnHelper;setupSpawn(ILjava/lang/Iterable;Lnet/minecraft/world/SpawnHelper$ChunkSource;Lnet/minecraft/world/SpawnDensityCapper;)Lnet/minecraft/world/SpawnHelper$Info;"
            )
    )
    private SpawnHelper.Info goafk$setupSpawnAddAnchors(
            int spawningChunkCount,
            Iterable<Entity> entities,
            SpawnHelper.ChunkSource chunkSource,
            SpawnDensityCapper capper
    ) {
        int extra = countAnchorSpawningChunks();
        return SpawnHelper.setupSpawn(spawningChunkCount + extra, entities, chunkSource, capper);
    }

    @Unique
    private int countAnchorSpawningChunks() {
        List<BlockPos> anchors = AFKManager.getAnchorPositions(this.world);
        if (anchors.isEmpty()) return 0;

        ServerChunkManager cm = (ServerChunkManager)(Object)this;
        Set<Long> unique = new HashSet<>(); // dedupe across anchors
        for (BlockPos ap : anchors) {
            ChunkPos center = new ChunkPos(ap);
            for (int dx = -MAX_RING; dx <= MAX_RING; dx++) {
                for (int dz = -MAX_RING; dz <= MAX_RING; dz++) {
                    int ring = Math.max(Math.abs(dx), Math.abs(dz));
                    if (ring < MIN_RING || ring > MAX_RING) continue;
                    WorldChunk wc = cm.getWorldChunk(center.x + dx, center.z + dz);
                    if (wc != null) unique.add(wc.getPos().toLong());
                }
            }
        }
        return unique.size();
    }

    @Inject(
            method = "tickChunks(Lnet/minecraft/util/profiler/Profiler;J)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/world/ServerChunkLoadingManager;collectSpawningChunks(Ljava/util/List;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void goafk$appendAnchorSpawningChunks(Profiler profiler, long timeDelta, CallbackInfo ci) {
        List<BlockPos> anchors = AFKManager.getAnchorPositions(this.world);
        if (anchors.isEmpty()) return;

        ServerChunkManager cm = (ServerChunkManager)(Object)this;
        for (BlockPos ap : anchors) {
            ChunkPos center = new ChunkPos(ap);
            for (int dx = -MAX_RING; dx <= MAX_RING; dx++) {
                for (int dz = -MAX_RING; dz <= MAX_RING; dz++) {
                    int ring = Math.max(Math.abs(dx), Math.abs(dz));
                    if (ring < MIN_RING || ring > MAX_RING) continue;
                    WorldChunk wc = cm.getWorldChunk(center.x + dx, center.z + dz);
                    if (wc != null && !this.spawningChunks.contains(wc)) {
                        this.spawningChunks.add(wc);
                    }
                }
            }
        }
    }
}
