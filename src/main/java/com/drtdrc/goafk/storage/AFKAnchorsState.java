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
import java.util.stream.Collectors;

public final class AFKAnchorsState extends PersistentState {
    public static final String ID = "goafk_anchors";

    public record Entry(BlockPos pos, String owner) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(inst ->
                inst.group(
                        BlockPos.CODEC.fieldOf("pos").forGetter(Entry::pos),
                        Codec.STRING.optionalFieldOf("owner", null).forGetter(e -> e.owner)
                ).apply(inst, Entry::new)
        );
    }

    public static final Codec<AFKAnchorsState> CODEC = RecordCodecBuilder.create(inst ->
            inst.group(
                    Entry.CODEC.listOf().optionalFieldOf("anchors", List.of()).forGetter(s -> s.entries)
            ).apply(inst, AFKAnchorsState::new)
    );

    public static final PersistentStateType<AFKAnchorsState> TYPE =
            new PersistentStateType<>(ID, AFKAnchorsState::new, CODEC, DataFixTypes.SAVED_DATA_MAP_DATA);

    private final ObjectArrayList<Entry> entries;

    public AFKAnchorsState() { this.entries = new ObjectArrayList<>(); }
    private AFKAnchorsState(List<Entry> loaded) { this.entries = new ObjectArrayList<>(loaded); }

    public static AFKAnchorsState get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(TYPE);
    }

    public List<Entry> getAllEntries() { return Collections.unmodifiableList(entries); }

    public List<BlockPos> getAllPositions() {
        return entries.stream().map(Entry::pos).toList();
    }

    public boolean add(BlockPos pos, String owner) {
        // dedupe by exact pos+owner; also avoid duplicate pos by any owner (optional)
        boolean exists = entries.stream().anyMatch(e -> e.pos.equals(pos) && e.owner.equals(owner));
        if (exists) return false;
        entries.add(new Entry(pos.toImmutable(), owner));
        this.markDirty();
        return true;
    }

    public boolean remove(BlockPos pos) {
        boolean changed = entries.removeIf(e -> e.pos.equals(pos));
        if (changed) this.markDirty();
        return changed;
    }

    public @NotNull List<BlockPos> removeAllByOwner(String owner) {
        if (owner == null || owner.isBlank()) return Collections.emptyList();

        List<BlockPos> removed = new ArrayList<>();
        Iterator<Entry> it = entries.iterator();
        while (it.hasNext()) {
            Entry e = it.next();
            String o = e.owner;
            if (o != null && o.equals(owner)) {
                removed.add(e.pos);
                it.remove();
            }
        }
        if (!removed.isEmpty()) this.markDirty();
        return removed;
    }

}
