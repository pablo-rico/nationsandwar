package org.papiricoh.nationsandwar.war;

import net.minecraft.world.level.ChunkPos;
import org.papiricoh.nationsandwar.model.Nation;
import org.papiricoh.nationsandwar.model.Politics;
import org.papiricoh.nationsandwar.model.Town;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * How an NPC side wages a war (pure): which enemy town to march on, which chunk to besiege next (always closer to the
 * town's home chunk, so a campaign ends at the Core), what to free first at home, how many fronts and how many soldiers
 * it can afford, and when it has bled enough to ask for peace.
 */
public final class WarPlanner {
    /** Chunks of extra distance a capital counts as (capitals are taken last). */
    public static final int CAPITAL_PENALTY = 40;
    /** Chunks a town already being taken counts as closer (finish what was started). */
    public static final int MOMENTUM = 20;

    private WarPlanner() {
    }

    /** A chunk of {@code town} may be besieged if it touches land that is not the town's, or a chunk of it already occupied. */
    public static boolean onFront(War w, Town town, long chunk) {
        for (long c : around(chunk)) if (!town.claims.contains(c) || w.isOccupied(town.id, c)) return true;
        return false;
    }

    /**
     * The next chunk to besiege in {@code town}: the free, unbesieged chunk on the front that lies closest to the home chunk
     * (walking through the town's claims), or null when none is left.
     */
    public static Long nextChunk(War w, Town town) {
        Map<Long, Integer> dist = distances(town);
        Long best = null;
        int bestD = Integer.MAX_VALUE;
        for (long c : town.claims) {
            if (w.isOccupied(town.id, c) || w.siegeAt(town.dimension, c) != null || !onFront(w, town, c)) continue;
            int d = dist.getOrDefault(c, Integer.MAX_VALUE - 1);
            if (d < bestD || d == bestD && best != null && c < best) {
                best = c;
                bestD = d;
            }
        }
        return best;
    }

    /** The occupied chunk of {@code town} to free first: its home chunk, else the one closest to it; null if none (or all besieged). */
    public static Long liberationTarget(War w, Town town) {
        var occ = w.occupied.get(town.id);
        if (occ == null || occ.isEmpty()) return null;
        if (occ.contains(town.homeChunk) && w.siegeAt(town.dimension, town.homeChunk) == null) return town.homeChunk;
        Map<Long, Integer> dist = distances(town);
        Long best = null;
        int bestD = Integer.MAX_VALUE;
        for (long c : occ) {
            if (w.siegeAt(town.dimension, c) != null) continue;
            int d = dist.getOrDefault(c, Integer.MAX_VALUE - 1);
            if (d < bestD || d == bestD && best != null && c < best) {
                best = c;
                bestD = d;
            }
        }
        return best;
    }

    /**
     * The enemy town {@code me} marches on: the weakest and nearest of the other side's towns that still has a chunk to
     * besiege (capitals last, towns already being taken first). {@code reachable} filters towns that may not be attacked
     * now (a player town with nobody near).
     */
    public static Town targetTown(Politics pol, War w, Nation me, Predicate<Town> reachable) {
        Boolean side = w.sideOf(me.id);
        if (side == null) return null;
        Town best = null;
        int bestScore = Integer.MAX_VALUE;
        for (UUID enemyId : side ? w.defenders : w.attackers) {
            Nation enemy = pol.nations.get(enemyId);
            if (enemy == null) continue;
            for (UUID tid : enemy.towns) {
                Town t = pol.towns.get(tid);
                if (t == null || w.fallen.contains(t.id) || nextChunk(w, t) == null || !reachable.test(t)) continue;
                int score = distance(pol, me, t) + t.claims.size() + t.residents.size() * 4;
                if (t.id.equals(enemy.capital) && enemy.towns.size() > 1) score += CAPITAL_PENALTY;
                if (w.occupied.containsKey(t.id)) score -= MOMENTUM;
                if (score < bestScore) {
                    best = t;
                    bestScore = score;
                }
            }
        }
        return best;
    }

    /** Sieges a side runs at once: one, plus one for each half of strength it has over the other side, up to {@code max}. */
    public static int fronts(int mine, int theirs, int max) {
        double ratio = mine / (double) Math.max(1, theirs);
        int n = 1 + (int) Math.floor(Math.max(0, ratio - 1) / 0.5);
        return Math.max(1, Math.min(max, n));
    }

    /** Soldiers a side sends to one banner: the base party scaled by its strength over the enemy's (×0.75..×2). */
    public static int party(int base, int mine, int theirs) {
        double ratio = Math.max(0.75, Math.min(2, mine / (double) Math.max(1, theirs)));
        return Math.max(1, (int) Math.round(base * ratio));
    }

    /** Fighters a nation may lose in one war before it sends no more. */
    public static int reserve(int strength) {
        return Math.max(8, strength * 2);
    }

    /** Whether a nation has bled enough to ask for peace. */
    public static boolean shouldRetreat(int losses, int strength) {
        return losses >= reserve(strength) * 3 / 4;
    }

    /** Steps from the home chunk to each claim, walking only through the town's claims. */
    static Map<Long, Integer> distances(Town town) {
        Map<Long, Integer> dist = new HashMap<>();
        Deque<Long> queue = new ArrayDeque<>();
        dist.put(town.homeChunk, 0);
        queue.add(town.homeChunk);
        while (!queue.isEmpty()) {
            long c = queue.poll();
            int d = dist.get(c);
            for (long n : around(c)) {
                if (!town.claims.contains(n) || dist.containsKey(n)) continue;
                dist.put(n, d + 1);
                queue.add(n);
            }
        }
        return dist;
    }

    /** Chunks from {@code n}'s nearest town (same dimension) to {@code t}'s home chunk; far when they share none. */
    private static int distance(Politics pol, Nation n, Town t) {
        int best = 10_000;
        for (UUID id : n.towns) {
            Town mine = pol.towns.get(id);
            if (mine == null || !mine.dimension.equals(t.dimension)) continue;
            best = Math.min(best, Math.max(Math.abs(ChunkPos.getX(mine.homeChunk) - ChunkPos.getX(t.homeChunk)),
                    Math.abs(ChunkPos.getZ(mine.homeChunk) - ChunkPos.getZ(t.homeChunk))));
        }
        return best;
    }

    /** The strength of one side of a war (the sum of its nations'). */
    public static int sideStrength(Politics pol, Collection<UUID> side, java.util.function.ToIntFunction<Nation> strength) {
        int s = 0;
        for (UUID id : side) {
            Nation n = pol.nations.get(id);
            if (n != null) s += strength.applyAsInt(n);
        }
        return s;
    }

    private static long[] around(long c) {
        int x = ChunkPos.getX(c), z = ChunkPos.getZ(c);
        return new long[]{ChunkPos.asLong(x + 1, z), ChunkPos.asLong(x - 1, z), ChunkPos.asLong(x, z + 1), ChunkPos.asLong(x, z - 1)};
    }
}
