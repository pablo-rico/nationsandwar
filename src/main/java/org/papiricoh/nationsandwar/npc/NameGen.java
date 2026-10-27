package org.papiricoh.nationsandwar.npc;

import java.util.Random;

/** Names for NPC towns and nations, from syllables (stable for a seed). */
public final class NameGen {
    private static final String[] START = {"Ald", "Bel", "Cor", "Dra", "Eld", "Fen", "Gal", "Hal", "Ist", "Kar", "Lor", "Mar", "Nor", "Os",
            "Pel", "Quel", "Ros", "Sar", "Tal", "Ul", "Val", "Wes", "Yor", "Zan", "Brin", "Thal", "Mor", "Ash", "Cal", "Der"};
    private static final String[] MIDDLE = {"a", "e", "i", "o", "an", "en", "ar", "or", "el", "il", "ath", "ver", "ran", "dor", "mir"};
    private static final String[] TOWN_END = {"ford", "holm", "wick", "stead", "burg", "mere", "dale", "haven", "ton", "gate", "brook", "fell",
            "crest", "moor", "vale"};
    private static final String[] NATION_END = {"ia", "or", "heim", "land", "mark", "oria", "esh", "anth", "eth", "una", "aria", "os"};

    private NameGen() {
    }

    public static String town(long seed) {
        Random r = new Random(seed);
        String s = pick(r, START) + (r.nextInt(3) == 0 ? pick(r, MIDDLE) : "") + pick(r, TOWN_END);
        return capitalize(s);
    }

    public static String nation(long seed) {
        Random r = new Random(seed ^ 0x5DEECE66DL);
        String s = pick(r, START) + (r.nextBoolean() ? pick(r, MIDDLE) : "") + pick(r, NATION_END);
        return capitalize(s);
    }

    private static String pick(Random r, String[] a) {
        return a[r.nextInt(a.length)];
    }

    private static String capitalize(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }
}
