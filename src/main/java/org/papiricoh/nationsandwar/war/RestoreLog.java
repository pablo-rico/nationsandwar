package org.papiricoh.nationsandwar.war;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What a war's fighting changed in towns: each block's state (and block-entity data) from before its first change, so the
 * end of the war puts every town back as it was. Broken blocks drop nothing in war zones (no duplication through restore).
 */
public final class RestoreLog {
    private record Entry(String dim, long pos, CompoundTag state, CompoundTag blockEntity) {
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public int size() {
        return entries.size();
    }

    /** Remembers {@code pos} as it is now, unless it was already remembered. Call before the change. */
    public void record(ServerLevel level, BlockPos pos, BlockState before) {
        String dim = level.dimension().location().toString();
        String key = dim + "|" + pos.asLong();
        if (entries.containsKey(key)) return;
        BlockEntity be = level.getBlockEntity(pos);
        CompoundTag beTag = be == null ? null : be.saveWithFullMetadata(level.registryAccess());
        entries.put(key, new Entry(dim, pos.asLong(), NbtUtils.writeBlockState(before), beTag));
    }

    public boolean contains(ServerLevel level, BlockPos pos) {
        return entries.containsKey(level.dimension().location() + "|" + pos.asLong());
    }

    /** Puts every remembered block back (latest first, so stacked changes unwind cleanly) and forgets them. */
    public int restore(MinecraftServer server) {
        int n = 0;
        var list = new java.util.ArrayList<>(entries.values());
        java.util.Collections.reverse(list);
        for (Entry e : list) {
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(e.dim)));
            if (level == null) continue;
            BlockPos pos = BlockPos.of(e.pos);
            BlockState state = NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), e.state);
            level.removeBlockEntity(pos);
            level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            if (e.blockEntity != null) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be != null) be.loadWithComponents(e.blockEntity, level.registryAccess());
            }
            n++;
        }
        entries.clear();
        return n;
    }

    public ListTag save() {
        ListTag l = new ListTag();
        for (Entry e : entries.values()) {
            CompoundTag t = new CompoundTag();
            t.putString("dim", e.dim);
            t.putLong("pos", e.pos);
            t.put("state", e.state);
            if (e.blockEntity != null) t.put("be", e.blockEntity);
            l.add(t);
        }
        return l;
    }

    public static RestoreLog load(ListTag l) {
        RestoreLog log = new RestoreLog();
        for (Tag x : l) {
            CompoundTag t = (CompoundTag) x;
            Entry e = new Entry(t.getString("dim"), t.getLong("pos"), t.getCompound("state"), t.contains("be") ? t.getCompound("be") : null);
            log.entries.put(e.dim + "|" + e.pos, e);
        }
        return log;
    }
}
