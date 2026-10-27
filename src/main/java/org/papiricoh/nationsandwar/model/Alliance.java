package org.papiricoh.nationsandwar.model;

import net.minecraft.nbt.CompoundTag;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** A pact between nations: members are allies (shared chunk perms, join each other's defensive wars). */
public final class Alliance {
    public final UUID id;
    public String name;
    public int color;
    public UUID leader;
    public final Set<UUID> members = new LinkedHashSet<>();
    public final Set<UUID> invites = new LinkedHashSet<>();

    public Alliance(UUID id, String name, UUID leader) {
        this.id = id;
        this.name = name;
        this.leader = leader;
        members.add(leader);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putString("name", name);
        t.putInt("color", color);
        t.putUUID("leader", leader);
        t.put("members", Nbt.uuids(members));
        t.put("invites", Nbt.uuids(invites));
        return t;
    }

    public static Alliance load(CompoundTag t) {
        Alliance a = new Alliance(t.getUUID("id"), t.getString("name"), t.getUUID("leader"));
        a.color = t.getInt("color");
        a.members.addAll(Nbt.uuids(t, "members"));
        a.invites.addAll(Nbt.uuids(t, "invites"));
        return a;
    }
}
