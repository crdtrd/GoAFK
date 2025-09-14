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
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class AFKAnchorsState extends PersistentState {
    public static final String ID = "goafk_anchors";

    public record AFKAnchor(BlockPos pos, String name) {
        public static final Codec<AFKAnchor> CODEC = RecordCodecBuilder.create(inst ->
                inst.group(
                        BlockPos.CODEC.fieldOf("pos").forGetter(AFKAnchor::pos),
                        Codec.STRING.optionalFieldOf("name", null).forGetter(e -> e.name)
                ).apply(inst, AFKAnchor::new)
        );
    }

    public static final Codec<AFKAnchorsState> CODEC = RecordCodecBuilder.create(inst ->
            inst.group(
                    AFKAnchor.CODEC.listOf().optionalFieldOf("anchors", List.of()).forGetter(s -> s.afkAnchors)
            ).apply(inst, AFKAnchorsState::new)
    );

    public static final PersistentStateType<AFKAnchorsState> TYPE =
            new PersistentStateType<>(ID, AFKAnchorsState::new, CODEC, DataFixTypes.SAVED_DATA_MAP_DATA);

    private final ObjectArrayList<AFKAnchor> afkAnchors;

    public AFKAnchorsState() { this.afkAnchors = new ObjectArrayList<>(); }
    private AFKAnchorsState(List<AFKAnchor> loaded) { this.afkAnchors = new ObjectArrayList<>(loaded); }

    public static AFKAnchorsState get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(TYPE);
    }

    public List<AFKAnchor> getAllEntries() { return Collections.unmodifiableList(afkAnchors); }

    public List<BlockPos> getAllPositions() {
        return afkAnchors.stream().map(AFKAnchor::pos).toList();
    }

    public boolean add(BlockPos pos, String name) {
        // dedupe by exact pos+name; also avoid duplicate pos by any name (optional)
        boolean exists = afkAnchors.stream().anyMatch(e -> e.pos.equals(pos) && e.name.equals(name));
        if (exists) return false;
        afkAnchors.add(new AFKAnchor(pos.toImmutable(), name));
        this.markDirty();
        return true;
    }

    public List<AFKAnchor> removeAll(BlockPos pos, String name) {
        List<AFKAnchor> removed = new ArrayList<>();
        Iterator<AFKAnchor> it = afkAnchors.iterator();
        while (it.hasNext()) {
            AFKAnchor afkAnchor = it.next();
            if (afkAnchor.pos.equals(pos) || (afkAnchor.name.equals(name) && !name.isEmpty())) {
                removed.add(afkAnchor);
                it.remove();
            }
        }
        if (!removed.isEmpty()) this.markDirty();
        return removed;
    }

}
