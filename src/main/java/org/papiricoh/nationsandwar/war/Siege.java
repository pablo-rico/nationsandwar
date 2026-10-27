package org.papiricoh.nationsandwar.war;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * A Siege Banner planted in an enemy town's chunk. {@link #control} runs from -1 (the defenders hold: the siege fails) to
 * +1 (the chunk falls to the besiegers), pushed every second by who stands near the banner ({@link SiegeControl}).
 * A {@link #liberation} siege is the town's own side taking back an occupied chunk. A siege NPC nations open in an unloaded
 * chunk has no block yet ({@link #placed} false) and runs on {@link WarSim} with the last fighters seen ({@link #lastA},
 * {@link #lastD}) until its chunk loads.
 */
public final class Siege {
    public final int id;
    public final UUID town;
    public final String dimension;
    public final long chunk;
    /** The banner's position (moved to the surface when a deferred banner is placed). */
    public long banner;
    /** Whether the besiegers are the war's attacking side. */
    public final boolean byAttackers;
    public final long started;
    public float control;
    public boolean liberation;
    /** Whether the banner block stands in the world (false: an NPC siege opened unloaded, placed when its chunk loads). */
    public boolean placed = true;
    /** Besiegers and holders last counted near the banner (the simulation's fighters while it is unloaded). */
    public int lastA, lastD;

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
        t.putBoolean("lib", liberation);
        t.putBoolean("placed", placed);
        t.putInt("lastA", lastA);
        t.putInt("lastD", lastD);
        return t;
    }

    static Siege load(CompoundTag t) {
        Siege s = new Siege(t.getInt("id"), t.getUUID("town"), t.getString("dim"), t.getLong("chunk"), t.getLong("banner"),
                t.getBoolean("byA"), t.getLong("started"));
        s.control = t.getFloat("control");
        s.liberation = t.getBoolean("lib");
        s.placed = !t.contains("placed") || t.getBoolean("placed");
        s.lastA = t.getInt("lastA");
        s.lastD = t.getInt("lastD");
        return s;
    }
}
