package com.drtdrc.goafk.mixin;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.collection.Pool;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.SpawnHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.SpawnSettings;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;

import java.util.Optional;

@Mixin(SpawnHelper.class)
public interface SpawnHelperInvokers {

    // boolean isAcceptableSpawnPosition(ServerWorld, Chunk, BlockPos.Mutable, double)
    @Invoker("isAcceptableSpawnPosition")
    static boolean goafk$isAcceptableSpawnPosition(ServerWorld world, Chunk chunk, BlockPos.Mutable pos, double squaredDistance) {
        throw new AssertionError();
    }

    // Optional<SpawnSettings.SpawnEntry> pickRandomSpawnEntry(ServerWorld, StructureAccessor, ChunkGenerator, SpawnGroup, Random, BlockPos)
    @Invoker("pickRandomSpawnEntry")
    static Optional<SpawnSettings.SpawnEntry> goafk$pickRandomSpawnEntry(ServerWorld world, StructureAccessor structureAccessor,
                                                                         ChunkGenerator chunkGenerator, SpawnGroup group,
                                                                         Random random, BlockPos pos) {
        throw new AssertionError();
    }

    // boolean canSpawn(ServerWorld, SpawnGroup, StructureAccessor, ChunkGenerator, SpawnSettings.SpawnEntry, BlockPos.Mutable, double)
    @Invoker("canSpawn")
    static boolean goafk$canSpawn(ServerWorld world, SpawnGroup group, StructureAccessor structureAccessor,
                                  ChunkGenerator chunkGenerator, SpawnSettings.SpawnEntry entry,
                                  BlockPos.Mutable pos, double squaredDistance) {
        throw new AssertionError();
    }

    // @Nullable MobEntity createMob(ServerWorld, EntityType<?>)
    @Invoker("createMob")
    @Nullable
    static MobEntity goafk$createMob(ServerWorld world, EntityType<?> type) {
        throw new AssertionError();
    }

    // boolean isValidSpawn(ServerWorld, MobEntity, double)
    @Invoker("isValidSpawn")
    static boolean goafk$isValidSpawn(ServerWorld world, MobEntity entity, double squaredDistance) {
        throw new AssertionError();
    }

}
