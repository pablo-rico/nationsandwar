package org.papiricoh.nationsandwar.model;

import net.minecraft.nbt.CompoundTag;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A group of a nation's towns under one governor, with a provincial capital. Provinces have no role tree of their own.
 * {@code color} is −1 until someone picks one: maps then show a shade of the nation's colour ({@link #color(int)}).
 */
public final class Province {
    public final UUID id;
    public String name;
    public final UUID nation;
    public UUID governor, capital;
    public final Set<UUID> towns = new LinkedHashSet<>();
    public int color = -1;

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
        t.putInt("color", color);
        return t;
    }

    public static Province load(CompoundTag t) {
        Province p = new Province(t.getUUID("id"), t.getString("name"), t.getUUID("nation"));
        p.governor = Nbt.getUuid(t, "governor");
        p.capital = Nbt.getUuid(t, "capital");
        p.towns.addAll(Nbt.uuids(t, "towns"));
        p.color = t.contains("color") ? t.getInt("color") : -1;
        return p;
    }

    /** The colour maps show: the picked one, else a shade of the nation's colour fixed by the province's id. */
    public int color(int nationColor) {
        if (color >= 0) return color;
        float[] shades = {-0.28f, 0.22f, -0.14f, 0.34f};
        float f = shades[Math.floorMod(id.hashCode(), shades.length)];
        int target = f < 0 ? 0 : 0xFFFFFF;
        float t = Math.abs(f);
        int r = Math.round((nationColor >> 16 & 255) * (1 - t) + (target >> 16 & 255) * t);
        int g = Math.round((nationColor >> 8 & 255) * (1 - t) + (target >> 8 & 255) * t);
        int b = Math.round((nationColor & 255) * (1 - t) + (target & 255) * t);
        return r << 16 | g << 8 | b;
    }
}
