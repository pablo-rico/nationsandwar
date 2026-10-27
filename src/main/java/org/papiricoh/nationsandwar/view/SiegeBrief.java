package org.papiricoh.nationsandwar.view;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A running siege as clients see it, refreshed every second ({@code network.SiegesPayload}). {@code control} −1..1: +1 the
 * chunk falls to the besiegers. Colours are the besieging and besieged nations'.
 */
public record SiegeBrief(UUID war, int id, UUID town, String dimension, long chunk, long banner, float control,
                         int besiegerColor, int defenderColor, String besieger, String defender) {
    public CompoundTag write() {
        CompoundTag t = new CompoundTag();
        t.putUUID("war", war);
        t.putInt("id", id);
        t.putUUID("town", town);
        t.putString("dim", dimension);
        t.putLong("chunk", chunk);
        t.putLong("banner", banner);
        t.putFloat("control", control);
        t.putInt("bc", besiegerColor);
        t.putInt("dc", defenderColor);
        t.putString("b", besieger);
        t.putString("d", defender);
        return t;
    }

    public static SiegeBrief read(CompoundTag t) {
        return new SiegeBrief(t.getUUID("war"), t.getInt("id"), t.getUUID("town"), t.getString("dim"), t.getLong("chunk"), t.getLong("banner"),
                t.getFloat("control"), t.getInt("bc"), t.getInt("dc"), t.getString("b"), t.getString("d"));
    }

    public static CompoundTag writeAll(List<SiegeBrief> list) {
        ListTag l = new ListTag();
        list.forEach(s -> l.add(s.write()));
        CompoundTag t = new CompoundTag();
        t.put("sieges", l);
        return t;
    }

    public static List<SiegeBrief> readAll(CompoundTag t) {
        List<SiegeBrief> out = new ArrayList<>();
        for (Tag x : t.getList("sieges", Tag.TAG_COMPOUND)) out.add(read((CompoundTag) x));
        return out;
    }
}
