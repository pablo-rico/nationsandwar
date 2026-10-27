package org.papiricoh.nationsandwar.view;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.papiricoh.nationsandwar.model.Nbt;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The public political facts every client holds (maps, territory notices, HUD): one brief per town, province, nation and
 * alliance, and the live wars. Written by the server ({@code core.Sync}), read into {@code client.ClientClaims}.
 */
public record Briefs(List<TownBrief> towns, List<ProvinceBrief> provinces, List<NationBrief> nations, List<AllianceBrief> alliances,
                     List<WarBrief> wars) {

    /**
     * @param flags     bit per {@code model.Flag} ordinal that is on town-wide
     * @param managedBy the key of the mod that runs the town ({@code api.ManagedNations}), "" for an ordinary or NPC town
     */
    public record TownBrief(UUID id, String name, int color, UUID nation, UUID province, int flags, boolean npc, String mayor, String managedBy) {
        public boolean managed() {
            return !managedBy.isEmpty();
        }

        CompoundTag write() {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", id);
            t.putString("name", name);
            t.putInt("color", color);
            Nbt.putUuid(t, "nation", nation);
            Nbt.putUuid(t, "province", province);
            t.putInt("flags", flags);
            t.putBoolean("npc", npc);
            t.putString("mayor", mayor);
            t.putString("managedBy", managedBy);
            return t;
        }

        static TownBrief read(CompoundTag t) {
            return new TownBrief(t.getUUID("id"), t.getString("name"), t.getInt("color"), Nbt.getUuid(t, "nation"), Nbt.getUuid(t, "province"),
                    t.getInt("flags"), t.getBoolean("npc"), t.getString("mayor"), t.getString("managedBy"));
        }
    }

    /** @param color the colour maps show (picked, or a shade of the nation's) */
    public record ProvinceBrief(UUID id, String name, UUID nation, int color) {
        CompoundTag write() {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", id);
            t.putString("name", name);
            t.putUUID("nation", nation);
            t.putInt("color", color);
            return t;
        }

        static ProvinceBrief read(CompoundTag t) {
            return new ProvinceBrief(t.getUUID("id"), t.getString("name"), t.getUUID("nation"), t.getInt("color"));
        }
    }

    /**
     * @param government a {@code GovernmentType} id ("kingdom"…)
     * @param managedBy  the key of the mod that runs the nation, "" for an ordinary or NPC nation
     * @param titles     the lang prefix of its government's names: {@code nationsandwar.gov.<id>}, or the managing mod's
     *                   ("supernaturalcraft.gov.pit"); the noun is the prefix itself, the ranks {@code <prefix>.leader}…
     */
    public record NationBrief(UUID id, String name, int color, String government, UUID alliance, UUID capital, boolean npc, String leader,
                              String managedBy, String titles) {
        public boolean managed() {
            return !managedBy.isEmpty();
        }

        CompoundTag write() {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", id);
            t.putString("name", name);
            t.putInt("color", color);
            t.putString("gov", government);
            Nbt.putUuid(t, "alliance", alliance);
            Nbt.putUuid(t, "capital", capital);
            t.putBoolean("npc", npc);
            t.putString("leader", leader);
            t.putString("managedBy", managedBy);
            t.putString("titles", titles);
            return t;
        }

        static NationBrief read(CompoundTag t) {
            return new NationBrief(t.getUUID("id"), t.getString("name"), t.getInt("color"), t.getString("gov"), Nbt.getUuid(t, "alliance"),
                    Nbt.getUuid(t, "capital"), t.getBoolean("npc"), t.getString("leader"), t.getString("managedBy"), t.getString("titles"));
        }
    }

    public record AllianceBrief(UUID id, String name, int color) {
        CompoundTag write() {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", id);
            t.putString("name", name);
            t.putInt("color", color);
            return t;
        }

        static AllianceBrief read(CompoundTag t) {
            return new AllianceBrief(t.getUUID("id"), t.getString("name"), t.getInt("color"));
        }
    }

    /**
     * A live war (preparation or active). {@code occupied}: per town id, the chunk keys taken by sieges; {@code coreOpen}:
     * towns whose Core may be broken.
     */
    public record WarBrief(UUID id, List<UUID> attackers, List<UUID> defenders, String phase, List<UUID> occupiedTowns,
                           List<long[]> occupiedChunks, List<UUID> coreOpen) {
        CompoundTag write() {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", id);
            t.put("attackers", Nbt.uuids(attackers));
            t.put("defenders", Nbt.uuids(defenders));
            t.putString("phase", phase);
            t.put("occTowns", Nbt.uuids(occupiedTowns));
            ListTag l = new ListTag();
            for (long[] c : occupiedChunks) l.add(new net.minecraft.nbt.LongArrayTag(c));
            t.put("occChunks", l);
            t.put("coreOpen", Nbt.uuids(coreOpen));
            return t;
        }

        static WarBrief read(CompoundTag t) {
            List<long[]> chunks = new ArrayList<>();
            for (Tag e : t.getList("occChunks", Tag.TAG_LONG_ARRAY)) chunks.add(((net.minecraft.nbt.LongArrayTag) e).getAsLongArray());
            return new WarBrief(t.getUUID("id"), List.copyOf(Nbt.uuids(t, "attackers")), List.copyOf(Nbt.uuids(t, "defenders")), t.getString("phase"),
                    List.copyOf(Nbt.uuids(t, "occTowns")), chunks, List.copyOf(Nbt.uuids(t, "coreOpen")));
        }
    }

    public CompoundTag write() {
        CompoundTag t = new CompoundTag();
        ListTag a = new ListTag(), b = new ListTag(), c = new ListTag(), d = new ListTag(), e = new ListTag();
        towns.forEach(x -> a.add(x.write()));
        provinces.forEach(x -> b.add(x.write()));
        nations.forEach(x -> c.add(x.write()));
        alliances.forEach(x -> d.add(x.write()));
        wars.forEach(x -> e.add(x.write()));
        t.put("towns", a);
        t.put("provinces", b);
        t.put("nations", c);
        t.put("alliances", d);
        t.put("wars", e);
        return t;
    }

    public static Briefs read(CompoundTag t) {
        List<TownBrief> towns = new ArrayList<>();
        List<ProvinceBrief> provinces = new ArrayList<>();
        List<NationBrief> nations = new ArrayList<>();
        List<AllianceBrief> alliances = new ArrayList<>();
        List<WarBrief> wars = new ArrayList<>();
        for (Tag x : t.getList("towns", Tag.TAG_COMPOUND)) towns.add(TownBrief.read((CompoundTag) x));
        for (Tag x : t.getList("provinces", Tag.TAG_COMPOUND)) provinces.add(ProvinceBrief.read((CompoundTag) x));
        for (Tag x : t.getList("nations", Tag.TAG_COMPOUND)) nations.add(NationBrief.read((CompoundTag) x));
        for (Tag x : t.getList("alliances", Tag.TAG_COMPOUND)) alliances.add(AllianceBrief.read((CompoundTag) x));
        for (Tag x : t.getList("wars", Tag.TAG_COMPOUND)) wars.add(WarBrief.read((CompoundTag) x));
        return new Briefs(towns, provinces, nations, alliances, wars);
    }
}
