// src/main/java/com/drtdrc/goafk/storage/AFKAnchorsState.java
package com.drtdrc.goafk.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

import java.util.Collections;
import java.util.List;

public final class AFKAnchorsState extends PersistentState {
    public static final String ID = "goafk_anchors";

    // Codec: store a simple list of BlockPos under "anchors"
    public static final Codec<AFKAnchorsState> CODEC = RecordCodecBuilder.create(inst ->
            inst.group(
                    BlockPos.CODEC.listOf().optionalFieldOf("anchors", List.of()).forGetter(s -> s.anchors)
            ).apply(inst, AFKAnchorsState::new)
    );

    public static final PersistentStateType<AFKAnchorsState> TYPE =
            new PersistentStateType<>(ID, AFKAnchorsState::new, CODEC, DataFixTypes.SAVED_DATA_MAP_DATA);

    private final ObjectArrayList<BlockPos> anchors;

    public AFKAnchorsState() { this.anchors = new ObjectArrayList<>(); }
    private AFKAnchorsState(List<BlockPos> loaded) {
        this.anchors = new ObjectArrayList<>(loaded);
    }

    public static AFKAnchorsState get(ServerWorld world) {
        // Stored under <dimension>/data/goafk_anchors.dat
        return world.getPersistentStateManager().getOrCreate(TYPE);
    }

    public List<BlockPos> getAll() {
        return Collections.unmodifiableList(anchors);
    }

    public boolean contains(BlockPos pos) { return anchors.contains(pos); }

    public boolean add(BlockPos pos) {
        if (anchors.contains(pos)) return false;
        anchors.add(pos.toImmutable());
        this.markDirty();
        return true;
    }

    public boolean remove(BlockPos pos) {
        boolean changed = anchors.remove(pos);
        if (changed) this.markDirty();
        return changed;
    }

    public boolean removeExact(BlockPos pos) { return remove(pos); } // alias if you like
}
