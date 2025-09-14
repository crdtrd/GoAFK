// src/main/java/com/drtdrc/goafk/mixin/ServerWorldMixin.java
package com.drtdrc.goafk.mixin;


import com.drtdrc.goafk.AFKManager;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.function.BooleanSupplier;

@Mixin(ServerWorld.class)
public abstract class ServerWorldMixin {
    private static final int MIN_RING = 2; // 24 / 16
    private static final int MAX_RING = 8; // 128 / 16

    @Inject(method = "canSpawnEntitiesAt(Lnet/minecraft/util/math/ChunkPos;)Z",
            at = @At("HEAD"), cancellable = true)
    private void goafk$anchorSpawnable(ChunkPos pos, CallbackInfoReturnable<Boolean> cir) {
        ServerWorld world = (ServerWorld)(Object)this;
        List<BlockPos> anchors = AFKManager.getAnchorPositions(world);
        if (anchors.isEmpty()) return;

        for (BlockPos ap : anchors) {
            ChunkPos center = new ChunkPos(ap);
            int ring = Math.max(Math.abs(pos.x - center.x), Math.abs(pos.z - center.z));
            if (ring >= MIN_RING && ring <= MAX_RING) {
                cir.setReturnValue(true);
                return;
            }
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void goafk$drawAnchorParticles(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        ServerWorld world = (ServerWorld)(Object)this;
        if (world.getTime() % 10 != 0) return;

        List<BlockPos> anchors = AFKManager.getAnchorPositions(world);
        if (anchors.isEmpty()) return;

        for (BlockPos p : anchors) {
            double x = p.getX() + 0.5, y = p.getY() + 1.2, z = p.getZ() + 0.5;
            // small ring
            int steps = 12;
            for (int i = 0; i < steps; i++) {
                double a = (i / (double)steps) * Math.PI * 2;
                double rx = x + Math.cos(a) * 0.3;
                double rz = z + Math.sin(a) * 0.3;
                world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, rx, y, rz, 5, 0, 0.3, 0, 0);
            }
            // a little core sparkle
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 5, 0.0, 0.3, 0.0, 0.0);
        }
    }
}
