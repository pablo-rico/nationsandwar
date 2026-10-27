package org.papiricoh.nationsandwar.model;

import net.minecraft.nbt.CompoundTag;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** A group of a nation's towns under one governor, with a provincial capital. Provinces have no role tree of their own. */
public final class Province {
    public final UUID id;
    public String name;
    public final UUID nation;
    public UUID governor, capital;
    public final Set<UUID> towns = new LinkedHashSet<>();

    public Province(UUID id, String name, UUID nation) {
        this.id = id;
        this.name = name;
        this.nation = nation;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putString("name", name);
        t.putUUID("nation", nation);
        Nbt.putUuid(t, "governor", governor);
        Nbt.putUuid(t, "capital", capital);
        t.put("towns", Nbt.uuids(towns));
        return t;
    }

    public static Province load(CompoundTag t) {
        Province p = new Province(t.getUUID("id"), t.getString("name"), t.getUUID("nation"));
        p.governor = Nbt.getUuid(t, "governor");
        p.capital = Nbt.getUuid(t, "capital");
        p.towns.addAll(Nbt.uuids(t, "towns"));
        return p;
    }
}
