package org.papiricoh.nationsandwar.model;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A nation: towns (some grouped in provinces), a capital, a government (names only), a role tree whose root is the leader
 * and whose Governor role lists the provinces' governors, a bank, an alliance, and (for NPC nations) opinions of others.
 */
public final class Nation {
    public final UUID id;
    public String name;
    public int color;
    public GovernmentType government;
    public UUID capital;
    public RoleTree roles;
    public final Set<UUID> towns = new LinkedHashSet<>();
    public final Set<UUID> provinces = new LinkedHashSet<>();
    public int bank;
    public UUID alliance;
    public boolean npc;
    /** Towns invited to join. */
    public final Set<UUID> invites = new LinkedHashSet<>();
    /** The overlord after a treaty of vassalage, until {@link #vassalUntil} (game time). */
    public UUID overlord;
    public long vassalUntil;
    /** NPC nations: opinion of other nations, -100..100. */
    public final Map<UUID, Integer> opinion = new HashMap<>();
    public long founded;
    /**
     * Set when another mod runs this nation through the API ({@code api.ManagedNations}): the provider's key, e.g.
     * "supernaturalcraft:pit". A managed nation takes no players, alliances or ordinary wars; null for every other nation.
     */
    public String managedBy;
    /** A lang prefix for the government's names that replaces {@link #government}'s ("modid.gov.pit"; null: the government's). */
    public String titles;
    /** Data other mods keep on the nation, one key per mod ("supernaturalcraft:guild"). Saved, never read by this mod. */
    public CompoundTag ext = new CompoundTag();
    /** NPC nations: the characters who hold its roles ({@link Persona}), by id. Empty for every other nation. */
    public final Map<UUID, Persona> court = new LinkedHashMap<>();

    public Nation(UUID id, String name, GovernmentType government) {
        this.id = id;
        this.name = name;
        this.government = government;
    }

    public boolean managed() {
        return managedBy != null;
    }

    /** NPC and managed nations always count as present to defend (they have no citizens who must be online). */
    public boolean alwaysDefended() {
        return npc || managedBy != null;
    }

    /** The lang prefix of the government's names: the managed titles, or {@code nationsandwar.gov.<id>}. */
    public String titleKey() {
        return titles != null ? titles : government.key();
    }

    public UUID leader() {
        return roles.leader();
    }

    public int opinionOf(UUID other) {
        return opinion.getOrDefault(other, 0);
    }

    public void addOpinion(UUID other, int delta) {
        opinion.put(other, Math.max(-100, Math.min(100, opinionOf(other) + delta)));
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putString("name", name);
        t.putInt("color", color);
        t.putString("gov", government.id());
        Nbt.putUuid(t, "capital", capital);
        t.put("roles", roles.save());
        t.put("towns", Nbt.uuids(towns));
        t.put("provinces", Nbt.uuids(provinces));
        t.putInt("bank", bank);
        Nbt.putUuid(t, "alliance", alliance);
        t.putBoolean("npc", npc);
        t.put("invites", Nbt.uuids(invites));
        Nbt.putUuid(t, "overlord", overlord);
        t.putLong("vassalUntil", vassalUntil);
        CompoundTag o = new CompoundTag();
        opinion.forEach((k, v) -> o.putInt(k.toString(), v));
        t.put("opinion", o);
        t.putLong("founded", founded);
        if (managedBy != null) t.putString("managedBy", managedBy);
        if (titles != null) t.putString("titles", titles);
        if (!ext.isEmpty()) t.put("ext", ext.copy());
        if (!court.isEmpty()) {
            ListTag c = new ListTag();
            for (Persona p : court.values()) c.add(p.save());
            t.put("court", c);
        }
        return t;
    }

    public static Nation load(CompoundTag t) {
        Nation n = new Nation(t.getUUID("id"), t.getString("name"), GovernmentType.byId(t.getString("gov")));
        n.color = t.getInt("color");
        n.capital = Nbt.getUuid(t, "capital");
        n.roles = t.contains("roles") ? RoleTree.load(t.getCompound("roles")) : n.government.defaultTree(null);
        n.towns.addAll(Nbt.uuids(t, "towns"));
        n.provinces.addAll(Nbt.uuids(t, "provinces"));
        n.bank = t.getInt("bank");
        n.alliance = Nbt.getUuid(t, "alliance");
        n.npc = t.getBoolean("npc");
        n.invites.addAll(Nbt.uuids(t, "invites"));
        n.overlord = Nbt.getUuid(t, "overlord");
        n.vassalUntil = t.getLong("vassalUntil");
        CompoundTag o = t.getCompound("opinion");
        for (String k : o.getAllKeys()) {
            try {
                n.opinion.put(UUID.fromString(k), o.getInt(k));
            } catch (IllegalArgumentException ignored) {
            }
        }
        n.founded = t.getLong("founded");
        if (t.contains("managedBy")) n.managedBy = t.getString("managedBy");
        if (t.contains("titles")) n.titles = t.getString("titles");
        if (t.contains("ext")) n.ext = t.getCompound("ext").copy();
        for (Tag e : t.getList("court", Tag.TAG_COMPOUND)) {
            Persona p = Persona.load((CompoundTag) e);
            n.court.put(p.id(), p);
        }
        return n;
    }
}
