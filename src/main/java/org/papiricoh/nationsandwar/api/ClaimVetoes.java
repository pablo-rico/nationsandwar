package org.papiricoh.nationsandwar.api;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiPredicate;

/**
 * Land no town may claim: another mod registers a test (dimension id, {@code ChunkPos.asLong} chunk key) that returns true
 * for its reserved chunks. Checked when a town is founded, claims, or an NPC town adopts a village. Managed towns created
 * through {@link NationsAndWarApi} ignore it (the provider placed them).
 */
public final class ClaimVetoes {
    private static final List<BiPredicate<String, Long>> VETOES = new CopyOnWriteArrayList<>();

    private ClaimVetoes() {
    }

    public static void register(BiPredicate<String, Long> veto) {
        VETOES.add(veto);
    }

    public static boolean vetoed(String dimension, Long chunk) {
        for (BiPredicate<String, Long> v : VETOES) if (v.test(dimension, chunk)) return true;
        return false;
    }
}
