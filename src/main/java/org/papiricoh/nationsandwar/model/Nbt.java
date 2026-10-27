package org.papiricoh.nationsandwar.model;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** NBT helpers for the political records (tolerant: a missing field reads as empty). */
public final class Nbt {
    private Nbt() {
    }

    public static ListTag uuids(Collection<UUID> ids) {
        ListTag l = new ListTag();
        for (UUID u : ids) l.add(NbtUtils.createUUID(u));
        return l;
    }

    public static Set<UUID> uuids(CompoundTag t, String key) {
        Set<UUID> s = new LinkedHashSet<>();
        for (Tag e : t.getList(key, Tag.TAG_INT_ARRAY)) s.add(NbtUtils.loadUUID(e));
        return s;
    }

    public static LongArrayTag longs(Collection<Long> values) {
        long[] a = new long[values.size()];
        int i = 0;
        for (long v : values) a[i++] = v;
        return new LongArrayTag(a);
    }

    public static Set<Long> longs(CompoundTag t, String key) {
        Set<Long> s = new LinkedHashSet<>();
        for (long v : t.getLongArray(key)) s.add(v);
        return s;
    }

    public static void putUuid(CompoundTag t, String key, UUID u) {
        if (u != null) t.putUUID(key, u);
    }

    public static UUID getUuid(CompoundTag t, String key) {
        return t.hasUUID(key) ? t.getUUID(key) : null;
    }
}
