package org.papiricoh.nationsandwar.war;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.papiricoh.nationsandwar.model.Nbt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A war between two sides of nations (the primaries and their alliances). Its phases: preparation after the declaration,
 * then active (sieges and kills score), then a treaty window for the winner, then ended. Times are overworld game time.
 */
public final class War {
    public enum Phase {PREPARATION, ACTIVE, TREATY, ENDED}

    public enum Outcome {NONE, ATTACKERS, DEFENDERS, WHITE_PEACE}

    public final UUID id;
    public final UUID attacker, defender;
    public final Set<UUID> attackers = new LinkedHashSet<>(), defenders = new LinkedHashSet<>();
    public Phase phase = Phase.PREPARATION;
    public long declared, activeAt, deadline, treatyUntil;
    public int scoreAttackers, scoreDefenders;
    /** Chunks taken by sieges, per town (they stay the town's until a treaty annexes the town). */
    public final Map<UUID, Set<Long>> occupied = new HashMap<>();
    /** Towns whose home chunk fell: their Core can be broken. */
    public final Set<UUID> coreOpen = new LinkedHashSet<>();
    /** Towns whose Core was broken: the winner may annex them. */
    public final Set<UUID> fallen = new LinkedHashSet<>();
    /** Hits each open Core has taken. */
    public final Map<UUID, Integer> coreDamage = new HashMap<>();
    public final List<Siege> sieges = new ArrayList<>();
    public Outcome outcome = Outcome.NONE;
    /** The side that offered white peace (null: nobody). */
    public Boolean peaceOfferedByAttackers;
    public Treaty terms;
    /** The score that wins this war (0: the config's). Set by {@code api.WarApi.WarOptions}. */
    public int targetScore;
    /** A lang key naming why the war was declared (shown by the GUI and notices; "" for an ordinary war). */
    public String reason = "";
    private int nextSiege = 1;

    public War(UUID id, UUID attacker, UUID defender) {
        this.id = id;
        this.attacker = attacker;
        this.defender = defender;
        attackers.add(attacker);
        defenders.add(defender);
    }

    public boolean involves(UUID nation) {
        return attackers.contains(nation) || defenders.contains(nation);
    }

    /** Whether two nations are on opposite sides of this war. */
    public boolean enemies(UUID a, UUID b) {
        return a != null && b != null && (attackers.contains(a) && defenders.contains(b) || defenders.contains(a) && attackers.contains(b));
    }

    /** True for the attacking side, false for the defending one, null if not in this war. */
    public Boolean sideOf(UUID nation) {
        if (attackers.contains(nation)) return true;
        if (defenders.contains(nation)) return false;
        return null;
    }

    public void score(boolean attackersSide, int points) {
        if (attackersSide) scoreAttackers += points;
        else scoreDefenders += points;
    }

    public boolean live() {
        return phase == Phase.PREPARATION || phase == Phase.ACTIVE;
    }

    public UUID winner() {
        return outcome == Outcome.ATTACKERS ? attacker : outcome == Outcome.DEFENDERS ? defender : null;
    }

    public UUID loser() {
        return outcome == Outcome.ATTACKERS ? defender : outcome == Outcome.DEFENDERS ? attacker : null;
    }

    public Siege siegeAt(String dim, long chunk) {
        for (Siege s : sieges) if (s.dimension.equals(dim) && s.chunk == chunk) return s;
        return null;
    }

    public Siege newSiege(UUID town, String dim, long chunk, long banner, boolean attackersSide, long now) {
        Siege s = new Siege(nextSiege++, town, dim, chunk, banner, attackersSide, now);
        sieges.add(s);
        return s;
    }

    public boolean isOccupied(UUID town, long chunk) {
        Set<Long> s = occupied.get(town);
        return s != null && s.contains(chunk);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putUUID("attacker", attacker);
        t.putUUID("defender", defender);
        t.put("attackers", Nbt.uuids(attackers));
        t.put("defenders", Nbt.uuids(defenders));
        t.putString("phase", phase.name());
        t.putLong("declared", declared);
        t.putLong("activeAt", activeAt);
        t.putLong("deadline", deadline);
        t.putLong("treatyUntil", treatyUntil);
        t.putInt("scoreA", scoreAttackers);
        t.putInt("scoreD", scoreDefenders);
        CompoundTag occ = new CompoundTag();
        occupied.forEach((town, chunks) -> occ.put(town.toString(), Nbt.longs(chunks)));
        t.put("occupied", occ);
        t.put("coreOpen", Nbt.uuids(coreOpen));
        t.put("fallen", Nbt.uuids(fallen));
        CompoundTag dmg = new CompoundTag();
        coreDamage.forEach((k, v) -> dmg.putInt(k.toString(), v));
        t.put("coreDamage", dmg);
        ListTag sl = new ListTag();
        for (Siege s : sieges) sl.add(s.save());
        t.put("sieges", sl);
        t.putString("outcome", outcome.name());
        if (peaceOfferedByAttackers != null) t.putBoolean("peaceBy", peaceOfferedByAttackers);
        if (terms != null) t.put("terms", terms.save());
        t.putInt("nextSiege", nextSiege);
        t.putInt("targetScore", targetScore);
        t.putString("reason", reason);
        return t;
    }

    public static War load(CompoundTag t) {
        War w = new War(t.getUUID("id"), t.getUUID("attacker"), t.getUUID("defender"));
        w.attackers.addAll(Nbt.uuids(t, "attackers"));
        w.defenders.addAll(Nbt.uuids(t, "defenders"));
        try {
            w.phase = Phase.valueOf(t.getString("phase"));
            w.outcome = Outcome.valueOf(t.getString("outcome"));
        } catch (IllegalArgumentException ignored) {
        }
        w.declared = t.getLong("declared");
        w.activeAt = t.getLong("activeAt");
        w.deadline = t.getLong("deadline");
        w.treatyUntil = t.getLong("treatyUntil");
        w.scoreAttackers = t.getInt("scoreA");
        w.scoreDefenders = t.getInt("scoreD");
        CompoundTag occ = t.getCompound("occupied");
        for (String k : occ.getAllKeys()) w.occupied.put(UUID.fromString(k), Nbt.longs(occ, k));
        w.coreOpen.addAll(Nbt.uuids(t, "coreOpen"));
        w.fallen.addAll(Nbt.uuids(t, "fallen"));
        CompoundTag dmg = t.getCompound("coreDamage");
        for (String k : dmg.getAllKeys()) w.coreDamage.put(UUID.fromString(k), dmg.getInt(k));
        for (Tag e : t.getList("sieges", Tag.TAG_COMPOUND)) w.sieges.add(Siege.load((CompoundTag) e));
        if (t.contains("peaceBy")) w.peaceOfferedByAttackers = t.getBoolean("peaceBy");
        if (t.contains("terms")) w.terms = Treaty.load(t.getCompound("terms"));
        w.nextSiege = Math.max(1, t.getInt("nextSiege"));
        w.targetScore = t.getInt("targetScore");
        w.reason = t.getString("reason");
        return w;
    }
}
