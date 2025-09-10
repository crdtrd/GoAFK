package com.drtdrc.goafk.mixin;


import com.drtdrc.goafk.AFKManager;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.SpawnHelper;
import net.minecraft.world.biome.SpawnSettings;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.drtdrc.goafk.mixin.SpawnHelperInvokers.*;

import java.util.List;
import java.util.Optional;

@Mixin(SpawnHelper.class)
public abstract class SpawnHelperMixin {

    @Inject(
            method = "spawnEntitiesInChunk(Lnet/minecraft/entity/SpawnGroup;Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/world/chunk/Chunk;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/world/SpawnHelper$Checker;Lnet/minecraft/world/SpawnHelper$Runner;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void onSpawnEntitiesInChunk(SpawnGroup group, ServerWorld world, Chunk chunk, BlockPos pos, SpawnHelper.Checker checker, SpawnHelper.Runner runner, CallbackInfo ci) {
        List<BlockPos> anchors = AFKManager.getAnchorPositions(world);


        if (anchors.isEmpty()) return;

        StructureAccessor structureAccessor = world.getStructureAccessor();
        ChunkGenerator chunkGenerator = world.getChunkManager().getChunkGenerator();
        int py = pos.getY();
        BlockState blockState = chunk.getBlockState(pos);
        if (blockState.isSolidBlock(chunk, pos)) {
            ci.cancel();
            return;
        }

        BlockPos.Mutable mutable = new BlockPos.Mutable();
        int totalSpawnedThisCall = 0;

        for (int attempt = 0; attempt < 3; attempt++) {
            int baseX = pos.getX();
            int baseZ = pos.getZ();
            SpawnSettings.SpawnEntry spawnEntry = null;
            EntityData entityData = null;
            int triesForPack = MathHelper.ceil(world.random.nextFloat() * 4.0F);
            int spawnedInPack = 0;

            for (int t = 0; t < triesForPack; t++) {
                baseX += world.random.nextInt(6) - world.random.nextInt(6);
                baseZ += world.random.nextInt(6) - world.random.nextInt(6);
                mutable.set(baseX, py, baseZ);

                final double px = baseX + 0.5;
                final double pz = baseZ + 0.5;

                // --- The big change: compute nearest distance to EITHER a real player OR an AFK anchor ---
                Double nearestSq = goafk$getNearestPlayerOrAnchorSq(world, anchors, px, py, pz);
                if (nearestSq == null) {
                    continue; // no players and no anchors anywhere
                }

                if (goafk$isAcceptableSpawnPosition(world, chunk, mutable, nearestSq)) {
                    if (spawnEntry == null) {
                        Optional<SpawnSettings.SpawnEntry> optional = goafk$pickRandomSpawnEntry(world, structureAccessor, chunkGenerator, group, world.random, mutable);
                        if (optional.isEmpty()) {
                            continue;
                        }
                        spawnEntry = optional.get();
                        triesForPack = spawnEntry.minGroupSize() + world.random.nextInt(1 + spawnEntry.maxGroupSize() - spawnEntry.minGroupSize());
                    }

                    if (goafk$canSpawn(world, group, structureAccessor, chunkGenerator, spawnEntry, mutable, nearestSq)
                            && checker.test(spawnEntry.type(), mutable, chunk)) {

                        MobEntity mob = goafk$createMob(world, spawnEntry.type());
                        if (mob == null) {
                            ci.cancel();
                            return;
                        }

                        mob.refreshPositionAndAngles(px, py, pz, world.random.nextFloat() * 360.0F, 0.0F);
                        if (goafk$isValidSpawn(world, mob, nearestSq)) {
                            entityData = mob.initialize(world, world.getLocalDifficulty(mob.getBlockPos()), SpawnReason.NATURAL, entityData);
                            totalSpawnedThisCall++;
                            spawnedInPack++;
                            world.spawnEntityAndPassengers(mob);
                            runner.run(mob, chunk);

                            if (totalSpawnedThisCall >= mob.getLimitPerChunk()) {
                                ci.cancel();
                                return;
                            }
                            if (mob.spawnsTooManyForEachTry(spawnedInPack)) {
                                break;
                            }
                        }
                    }
                }
            }
        }
        // We fully handled spawning; prevent vanilla from running it again.
        ci.cancel();
    }


    @Unique
    private static Double goafk$getNearestPlayerOrAnchorSq(ServerWorld world, List<BlockPos> anchors, double x, double y, double z) {
        // nearest real player (vanilla behavior)
        PlayerEntity nearestPlayer = world.getClosestPlayer(x, y, z, -1.0, false);
        double bestSq = Double.POSITIVE_INFINITY;
        if (nearestPlayer != null) {
            bestSq = nearestPlayer.squaredDistanceTo(x, y, z);
        }

        // nearest AFK anchor
        double anchorBest = Double.POSITIVE_INFINITY;
        for (BlockPos ap : anchors) {
            double dx = ap.getX() + 0.5 - x;
            double dy = ap.getY() - y;
            double dz = ap.getZ() + 0.5 - z;
            double d = dx*dx + dy*dy + dz*dz;
            if (d < anchorBest) anchorBest = d;
        }

        double result = Math.min(bestSq, anchorBest);
        return Double.isFinite(result) ? result : null;
    }

    @Unique
    private static final int MIN_RING = 2;
    @Unique
    private static final int MAX_RING = 8;

    @Redirect(
            method = "spawn(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/world/chunk/WorldChunk;Lnet/minecraft/world/SpawnHelper$Info;Ljava/util/List;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/SpawnHelper$Info;canSpawn(Lnet/minecraft/entity/SpawnGroup;Lnet/minecraft/util/math/ChunkPos;)Z"
            )
    )
    private static boolean goafk$canSpawnOrAnchor(
            SpawnHelper.Info info, SpawnGroup group, ChunkPos pos,
            ServerWorld world, WorldChunk chunk, SpawnHelper.Info infoArg, List<SpawnGroup> groups
    ) {
        // Call the original canSpawn
        boolean base = ((SpawnHelperInfoInvoker) info).goafk$invokeCanSpawn(group, pos);
        if (base) return true;

        // Allow chunks in the anchor "player ring"
        for (BlockPos ap : AFKManager.getAnchorPositions(world)) {
            ChunkPos center = new ChunkPos(ap);
            int ring = Math.max(Math.abs(pos.x - center.x), Math.abs(pos.z - center.z));
            if (ring >= MIN_RING && ring <= MAX_RING) {
                return true;
            }
        }
        return false;
    }
}
