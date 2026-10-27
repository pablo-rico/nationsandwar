package org.papiricoh.nationsandwar.war;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * A Siege Banner planted in an enemy town's chunk. {@link #control} runs from -1 (the defenders hold: the siege fails) to
 * +1 (the chunk falls to the besiegers), pushed every second by who stands near the banner ({@link SiegeControl}).
 */
public final class Siege {
    public final int id;
    public final UUID town;
    public final String dimension;
    public final long chunk, banner;
    /** Whether the besiegers are the war's attacking side. */
    public final boolean byAttackers;
    public final long started;
    public float control;

    public Siege(int id, UUID town, String dimension, long chunk, long banner, boolean byAttackers, long started) {
        this.id = id;
        this.town = town;
        this.dimension = dimension;
        this.chunk = chunk;
        this.banner = banner;
        this.byAttackers = byAttackers;
        this.started = started;
    }

    CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("id", id);
        t.putUUID("town", town);
        t.putString("dim", dimension);
        t.putLong("chunk", chunk);
        t.putLong("banner", banner);
        t.putBoolean("byA", byAttackers);
        t.putLong("started", started);
        t.putFloat("control", control);
        return t;
    }

    static Siege load(CompoundTag t) {
        Siege s = new Siege(t.getInt("id"), t.getUUID("town"), t.getString("dim"), t.getLong("chunk"), t.getLong("banner"),
                t.getBoolean("byA"), t.getLong("started"));
        s.control = t.getFloat("control");
        return s;
    }
}
