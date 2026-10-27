package org.papiricoh.nationsandwar.model;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * A character of an NPC court: holds roles in an NPC nation's or town's tree in place of a player. {@code id} is never a
 * real player's; {@code skin} picks the guard skin its bust wears (0..3); {@code since} is the game time it took office.
 */
public record Persona(UUID id, String name, int skin, long since) {
    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putString("name", name);
        t.putInt("skin", skin);
        t.putLong("since", since);
        return t;
    }

    public static Persona load(CompoundTag t) {
        return new Persona(t.getUUID("id"), t.getString("name"), t.getInt("skin"), t.getLong("since"));
    }
}
