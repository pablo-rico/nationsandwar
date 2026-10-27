package org.papiricoh.nationsandwar.npc;

import net.minecraft.world.level.ChunkPos;
import org.papiricoh.nationsandwar.model.Nation;
import org.papiricoh.nationsandwar.model.Politics;
import org.papiricoh.nationsandwar.model.Town;
import org.papiricoh.nationsandwar.war.War;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * What NPC nations think and decide, once per brain interval (pure). Opinions (−100..100) drift with neighbours (−),
 * allies (+), wars (−) and chance; gifts and slain guards move them from outside. A nation that hates a weaker one goes
 * to war; two NPC nations that like each other ally; a nation losing a war asks for peace.
 */
public final class FactionBrain {
    public static final int WAR_BELOW = -50, ALLY_ABOVE = 60, ACCEPT_ALLIANCE = 40, ACCEPT_PEACE = -20, BORDER_CHUNKS = 12;
    public static final double STRONGER = 1.2;

    public enum Kind {DECLARE_WAR, ALLY, OFFER_PEACE}

    public record Decision(Kind kind, UUID nation, UUID other) {
    }

    private FactionBrain() {
    }

    public static int strength(Politics pol, Nation n) {
        return n.towns.size() * 4 + pol.citizens(n).size() * 2;
    }

    public static boolean borders(Politics pol, Nation a, Nation b) {
        for (UUID ta : a.towns) {
            Town x = pol.towns.get(ta);
            if (x == null) continue;
            for (UUID tb : b.towns) {
                Town y = pol.towns.get(tb);
                if (y == null || !x.dimension.equals(y.dimension)) continue;
                int dx = Math.abs(ChunkPos.getX(x.homeChunk) - ChunkPos.getX(y.homeChunk)), dz = Math.abs(ChunkPos.getZ(x.homeChunk) - ChunkPos.getZ(y.homeChunk));
                if (Math.max(dx, dz) <= BORDER_CHUNKS) return true;
            }
        }
        return false;
    }

    /** Moves every NPC nation's opinions by one interval (never of a managed nation: another mod runs those). */
    public static void drift(Politics pol, Collection<War> wars, long day) {
        for (Nation n : pol.nations.values()) {
            if (!n.npc) continue;
            for (Nation m : pol.nations.values()) {
                if (m == n || m.managed()) continue;
                int delta = 0;
                if (pol.allied(n.id, m.id)) delta += 3;
                if (atWar(wars, n.id, m.id)) delta -= 2;
                if (borders(pol, n, m)) delta -= 2;
                Random r = new Random(day * 31 + n.id.hashCode() * 17L + m.id.hashCode());
                delta += r.nextInt(5) - 2;
                n.addOpinion(m.id, delta);
            }
        }
    }

    /**
     * This interval's decisions. {@code reachable(nation)} says whether a war on that nation could be fought now (an NPC
     * nation always; a player nation with a citizen online).
     */
    public static List<Decision> decide(Politics pol, Collection<War> wars, boolean wars_, java.util.function.Predicate<Nation> reachable) {
        List<Decision> out = new ArrayList<>();
        for (Nation n : pol.nations.values()) {
            if (!n.npc) continue;
            War current = null;
            for (War w : wars) if (w.live() && w.involves(n.id)) current = w;
            if (current != null) {
                boolean attackerSide = current.attackers.contains(n.id);
                int mine = attackerSide ? current.scoreAttackers : current.scoreDefenders, theirs = attackerSide ? current.scoreDefenders : current.scoreAttackers;
                boolean primary = current.attacker.equals(n.id) || current.defender.equals(n.id);
                if (primary && current.phase == War.Phase.ACTIVE && theirs >= Math.max(6, mine * 2))
                    out.add(new Decision(Kind.OFFER_PEACE, n.id, attackerSide ? current.defender : current.attacker));
                continue;
            }
            Nation worst = null, best = null;
            for (Nation m : pol.nations.values()) {
                if (m == n || m.managed() || pol.allied(n.id, m.id)) continue;
                int o = n.opinionOf(m.id);
                if (o <= WAR_BELOW && (worst == null || o < n.opinionOf(worst.id))) worst = m;
                if (m.npc && n.alliance == null && o >= ALLY_ABOVE && m.opinionOf(n.id) >= ALLY_ABOVE && (best == null || o > n.opinionOf(best.id))) best = m;
            }
            if (wars_ && worst != null && strength(pol, n) >= STRONGER * strength(pol, worst) && reachable.test(worst)
                    && !n.id.equals(worst.overlord) && !worst.id.equals(n.overlord)) {
                out.add(new Decision(Kind.DECLARE_WAR, n.id, worst.id));
            } else if (best != null) {
                out.add(new Decision(Kind.ALLY, n.id, best.id));
            }
        }
        return out;
    }

    public static boolean atWar(Collection<War> wars, UUID a, UUID b) {
        for (War w : wars) if (w.live() && w.enemies(a, b)) return true;
        return false;
    }

    /** Whether an NPC nation takes a white peace: it is losing, or does not hate the other side. */
    public static boolean acceptsPeace(Nation npc, UUID other, War w) {
        boolean attackerSide = w.attackers.contains(npc.id);
        int mine = attackerSide ? w.scoreAttackers : w.scoreDefenders, theirs = attackerSide ? w.scoreDefenders : w.scoreAttackers;
        return theirs > mine || npc.opinionOf(other) >= ACCEPT_PEACE;
    }

    /** Opinion gained from a gift of emeralds. */
    public static int giftOpinion(int emeralds) {
        return Math.min(30, emeralds / 4);
    }
}
