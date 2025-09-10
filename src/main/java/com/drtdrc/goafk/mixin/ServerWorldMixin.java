// src/main/java/com/drtdrc/goafk/mixin/ServerWorldMixin.java
package com.drtdrc.goafk.mixin;


import com.drtdrc.goafk.AFKManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

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
}
