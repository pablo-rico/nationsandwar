package org.papiricoh.nationsandwar.model;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * One node of a {@link RoleTree}. A name starting with {@code @} is a lang key (the defaults, so they read in the player's
 * language until someone renames them); any other name is shown as typed.
 */
public final class Role {
    public enum Kind {
        /** The tree's root: the mayor or the nation's leader. Holds every perm, cannot be deleted or moved. */
        LEADER,
        /** A nation's governors: holders are the provinces' governors (set on the province, never assigned here). */
        GOVERNOR,
        NORMAL
    }

    public final int id;
    public final Kind kind;
    public String name;
    public int color;
    /** The parent role's id, or -1 for the root. */
    public int parent;
    public final EnumSet<Perm> perms;
    public final Set<UUID> holders = new LinkedHashSet<>();

    public Role(int id, Kind kind, String name, int color, int parent, Set<Perm> perms) {
        this.id = id;
        this.kind = kind;
        this.name = name;
        this.color = color;
        this.parent = parent;
        this.perms = perms.isEmpty() ? EnumSet.noneOf(Perm.class) : EnumSet.copyOf(perms);
    }

    public boolean has(Perm p) {
        return kind == Kind.LEADER || perms.contains(p);
    }

    CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("id", id);
        t.putString("kind", kind.name());
        t.putString("name", name);
        t.putInt("color", color);
        t.putInt("parent", parent);
        t.putInt("perms", Perm.mask(perms));
        ListTag h = new ListTag();
        for (UUID u : holders) h.add(NbtUtils.createUUID(u));
        t.put("holders", h);
        return t;
    }

    static Role load(CompoundTag t) {
        Kind kind;
        try {
            kind = Kind.valueOf(t.getString("kind"));
        } catch (IllegalArgumentException e) {
            kind = Kind.NORMAL;
        }
        Role r = new Role(t.getInt("id"), kind, t.getString("name"), t.getInt("color"), t.getInt("parent"), Perm.ofMask(t.getInt("perms")));
        for (Tag h : t.getList("holders", Tag.TAG_INT_ARRAY)) r.holders.add(NbtUtils.loadUUID(h));
        return r;
    }
}
