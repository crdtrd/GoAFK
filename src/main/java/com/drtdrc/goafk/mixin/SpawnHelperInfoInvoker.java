// src/main/java/com/drtdrc/goafk/mixin/SpawnHelperInfoInvoker.java
package com.drtdrc.goafk.mixin;

import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.SpawnHelper;
import net.minecraft.entity.SpawnGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(SpawnHelper.Info.class)
public interface SpawnHelperInfoInvoker {
    @Invoker("canSpawn")
    boolean goafk$invokeCanSpawn(SpawnGroup group, ChunkPos pos);
}
