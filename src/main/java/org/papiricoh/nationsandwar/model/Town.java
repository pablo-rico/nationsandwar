package org.papiricoh.nationsandwar.model;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A town: claimed chunks in one dimension, its residents and its role tree, a bank of emeralds, flags and chunk perms.
 * Chunks are {@code ChunkPos.asLong} keys; positions are {@code BlockPos.asLong}.
 */
public final class Town {
    /** A chunk's own settings: flags and perms that override the town's (null = follow the town). */
    public static final class ChunkSettings {
        public final Map<Flag, Boolean> flags = new EnumMap<>(Flag.class);
        public ChunkPerms perms;

        boolean isEmpty() {
            return flags.isEmpty() && perms == null;
        }
    }

    public final UUID id;
    public String name;
    public int color;
    public final String dimension;
    public long homeChunk;
    public long spawn;
    /** The Town Core's block position (the heart a siege must break). */
    public long core;
    public final Set<UUID> residents = new LinkedHashSet<>();
    public RoleTree roles;
    public final Set<Long> claims = new LinkedHashSet<>();
    /** Claims allowed apart from the rest (each one also in {@link #claims}). */
    public final Set<Long> outposts = new LinkedHashSet<>();
    public int bank;
    public final Map<Flag, Boolean> flags = new EnumMap<>(Flag.class);
    public ChunkPerms perms = ChunkPerms.DEFAULT;
    public final Map<Long, ChunkSettings> chunkSettings = new HashMap<>();
    public UUID province, nation;
    public boolean npc, open;
    public final Set<UUID> invites = new LinkedHashSet<>();
    public long founded;
    /** The provider's key when another mod runs this town (it then has no residents of its own); null otherwise. */
    public String managedBy;
    /** Data other mods keep on the town, one key per mod. Saved, never read by this mod. */
    public CompoundTag ext = new CompoundTag();
    /** NPC towns: the characters who hold its roles ({@link Persona}), by id. Empty for every other town. */
    public final Map<UUID, Persona> court = new LinkedHashMap<>();

    public Town(UUID id, String name, String dimension, long homeChunk) {
        this.id = id;
        this.name = name;
        this.dimension = dimension;
        this.homeChunk = homeChunk;
    }

    public boolean managed() {
        return managedBy != null;
    }

    public TownLevel level() {
        return TownLevel.of(residents.size());
    }

    public UUID mayor() {
        return roles.leader();
    }

    public boolean flag(Flag f, long chunk) {
        ChunkSettings s = chunkSettings.get(chunk);
        if (s != null && s.flags.containsKey(f)) return s.flags.get(f);
        return flags.getOrDefault(f, f.defaultOn);
    }

    public ChunkPerms perms(long chunk) {
        ChunkSettings s = chunkSettings.get(chunk);
        return s != null && s.perms != null ? s.perms : perms;
    }

    public ChunkSettings settings(long chunk) {
        return chunkSettings.computeIfAbsent(chunk, k -> new ChunkSettings());
    }

    public void tidy(long chunk) {
        ChunkSettings s = chunkSettings.get(chunk);
        if (s != null && s.isEmpty()) chunkSettings.remove(chunk);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putString("name", name);
        t.putInt("color", color);
        t.putString("dim", dimension);
        t.putLong("home", homeChunk);
        t.putLong("spawn", spawn);
        t.putLong("core", core);
        t.put("residents", Nbt.uuids(residents));
        t.put("roles", roles.save());
        t.put("claims", Nbt.longs(claims));
        t.put("outposts", Nbt.longs(outposts));
        t.putInt("bank", bank);
        CompoundTag f = new CompoundTag();
        flags.forEach((k, v) -> f.putBoolean(k.name(), v));
        t.put("flags", f);
        t.putInt("perms", perms.bits());
        ListTag cs = new ListTag();
        chunkSettings.forEach((chunk, s) -> {
            CompoundTag c = new CompoundTag();
            c.putLong("chunk", chunk);
            CompoundTag cf = new CompoundTag();
            s.flags.forEach((k, v) -> cf.putBoolean(k.name(), v));
            c.put("flags", cf);
            if (s.perms != null) c.putInt("perms", s.perms.bits());
            cs.add(c);
        });
        t.put("chunks", cs);
        Nbt.putUuid(t, "province", province);
        Nbt.putUuid(t, "nation", nation);
        t.putBoolean("npc", npc);
        t.putBoolean("open", open);
        t.put("invites", Nbt.uuids(invites));
        t.putLong("founded", founded);
        if (managedBy != null) t.putString("managedBy", managedBy);
        if (!ext.isEmpty()) t.put("ext", ext.copy());
        if (!court.isEmpty()) {
            ListTag c = new ListTag();
            for (Persona p : court.values()) c.add(p.save());
            t.put("court", c);
        }
        return t;
    }

    public static Town load(CompoundTag t) {
        Town town = new Town(t.getUUID("id"), t.getString("name"), t.getString("dim"), t.getLong("home"));
        town.color = t.getInt("color");
        town.spawn = t.getLong("spawn");
        town.core = t.getLong("core");
        town.residents.addAll(Nbt.uuids(t, "residents"));
        town.roles = t.contains("roles") ? RoleTree.load(t.getCompound("roles")) : GovernmentType.defaultTownTree(null);
        town.claims.addAll(Nbt.longs(t, "claims"));
        town.outposts.addAll(Nbt.longs(t, "outposts"));
        town.bank = t.getInt("bank");
        CompoundTag f = t.getCompound("flags");
        for (Flag fl : Flag.values()) if (f.contains(fl.name())) town.flags.put(fl, f.getBoolean(fl.name()));
        if (t.contains("perms")) town.perms = new ChunkPerms(t.getInt("perms"));
        for (Tag e : t.getList("chunks", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) e;
            ChunkSettings s = town.settings(c.getLong("chunk"));
            CompoundTag cf = c.getCompound("flags");
            for (Flag fl : Flag.values()) if (cf.contains(fl.name())) s.flags.put(fl, cf.getBoolean(fl.name()));
            if (c.contains("perms")) s.perms = new ChunkPerms(c.getInt("perms"));
        }
        town.province = Nbt.getUuid(t, "province");
        town.nation = Nbt.getUuid(t, "nation");
        town.npc = t.getBoolean("npc");
        town.open = t.getBoolean("open");
        town.invites.addAll(Nbt.uuids(t, "invites"));
        town.founded = t.getLong("founded");
        if (t.contains("managedBy")) town.managedBy = t.getString("managedBy");
        if (t.contains("ext")) town.ext = t.getCompound("ext").copy();
        for (Tag e : t.getList("court", Tag.TAG_COMPOUND)) {
            Persona p = Persona.load((CompoundTag) e);
            town.court.put(p.id(), p);
        }
        return town;
    }
}
