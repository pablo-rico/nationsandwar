package org.papiricoh.nationsandwar.war;

import org.papiricoh.nationsandwar.model.Alliance;
import org.papiricoh.nationsandwar.model.Nation;
import org.papiricoh.nationsandwar.model.Politics;
import org.papiricoh.nationsandwar.model.Result;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** The pure rules of war: who may declare on whom, who joins, and when a war is decided. */
public final class WarRules {
    private WarRules() {
    }

    /** Whether {@code attacker} may declare on {@code defender} now (no perms or money checked here). */
    public static Result canDeclare(Politics pol, Collection<War> wars, Map<String, Long> lastWar, Nation attacker, Nation defender,
                                    long now, long cooldown) {
        if (attacker == defender) return Result.fail("war.self");
        if (pol.allied(attacker.id, defender.id)) return Result.fail("war.ally");
        if (defender.id.equals(attacker.overlord) || attacker.id.equals(defender.overlord)) return Result.fail("war.vassal");
        for (War w : wars) if (w.live() && w.enemies(attacker.id, defender.id)) return Result.fail("war.already");
        for (War w : wars) if (w.live() && w.involves(attacker.id) && w.attacker.equals(attacker.id)) return Result.fail("war.busy");
        Long last = lastWar.get(pair(attacker.id, defender.id));
        if (last != null && now - last < cooldown) return Result.fail("war.cooldown", (cooldown - (now - last)) / 1200 + 1);
        return Result.ok("ok");
    }

    /** Fills both sides: each primary's alliance members join it (never a nation already on the other side). */
    public static void gatherSides(Politics pol, War w) {
        join(pol, w, w.attacker, true);
        join(pol, w, w.defender, false);
    }

    private static void join(Politics pol, War w, UUID primary, boolean attackers) {
        Nation n = pol.nations.get(primary);
        if (n == null || n.alliance == null) return;
        Alliance a = pol.alliances.get(n.alliance);
        if (a == null) return;
        for (UUID m : a.members) {
            if (w.involves(m)) continue;
            (attackers ? w.attackers : w.defenders).add(m);
        }
    }

    public static War.Outcome byScore(int attackers, int defenders) {
        return attackers == defenders ? War.Outcome.WHITE_PEACE : attackers > defenders ? War.Outcome.ATTACKERS : War.Outcome.DEFENDERS;
    }

    /** The outcome if the war should end now (target reached or deadline passed), else NONE. The war's own target wins. */
    public static War.Outcome check(War w, int target, long now) {
        if (w.phase != War.Phase.ACTIVE) return War.Outcome.NONE;
        if (w.targetScore > 0) target = w.targetScore;
        if (w.scoreAttackers >= target) return War.Outcome.ATTACKERS;
        if (w.scoreDefenders >= target) return War.Outcome.DEFENDERS;
        return now >= w.deadline ? byScore(w.scoreAttackers, w.scoreDefenders) : War.Outcome.NONE;
    }

    public static String pair(UUID a, UUID b) {
        return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
    }
}
