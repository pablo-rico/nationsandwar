package org.papiricoh.nationsandwar.npc;

import java.util.Random;

/** Names for NPC towns, nations and court characters, from syllables (stable for a seed). */
public final class NameGen {
    private static final String[] START = {"Ald", "Bel", "Cor", "Dra", "Eld", "Fen", "Gal", "Hal", "Ist", "Kar", "Lor", "Mar", "Nor", "Os",
            "Pel", "Quel", "Ros", "Sar", "Tal", "Ul", "Val", "Wes", "Yor", "Zan", "Brin", "Thal", "Mor", "Ash", "Cal", "Der"};
    private static final String[] MIDDLE = {"a", "e", "i", "o", "an", "en", "ar", "or", "el", "il", "ath", "ver", "ran", "dor", "mir"};
    private static final String[] TOWN_END = {"ford", "holm", "wick", "stead", "burg", "mere", "dale", "haven", "ton", "gate", "brook", "fell",
            "crest", "moor", "vale"};
    private static final String[] NATION_END = {"ia", "or", "heim", "land", "mark", "oria", "esh", "anth", "eth", "una", "aria", "os"};

    private static final String[] FIRST = {"Aldric", "Bertram", "Cedric", "Doran", "Edric", "Falk", "Gerold", "Hugo", "Ivo", "Jorund",
            "Leofric", "Marek", "Osric", "Roderic", "Sigmund", "Tobin", "Ulric", "Wendel", "Alys", "Brienne", "Cerys", "Edda", "Elin",
            "Gisla", "Helena", "Isolde", "Maren", "Rowena", "Sabine", "Ysolde", "Aveline", "Matilde"};
    private static final String[] SURNAME_END = {"vor", "ric", "mont", "wyn", "hart", "dane", "mere", "wald", "ford", "ley", "ston",
            "brand", "gard", "holt", "rin", "worth"};

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

    /**
     * A character's name: a first name and a surname built from the nation's syllables ("Aldric Galvor" in Galheim). Half
     * the surnames take the nation's own opening syllable, the rest a syllable of the same stock.
     */
    public static String person(long seed, String nationName) {
        Random r = new Random(seed ^ 0x2545F4914F6CDD1DL);
        String first = pick(r, FIRST);
        String stem = stem(nationName);
        String head = stem != null && r.nextBoolean() ? stem : pick(r, START);
        String surname = capitalize(head + (r.nextInt(4) == 0 ? pick(r, MIDDLE) : "") + pick(r, SURNAME_END));
        return first + " " + surname;
    }

    /** The syllable of {@link #START} the nation's name opens with (the longest match), or null. */
    private static String stem(String nationName) {
        if (nationName == null) return null;
        String lower = nationName.toLowerCase();
        String best = null;
        for (String s : START) if (lower.startsWith(s.toLowerCase()) && (best == null || s.length() > best.length())) best = s;
        if (best == null && lower.length() >= 3 && Character.isLetter(lower.charAt(0))) best = nationName.substring(0, 3);
        return best;
    }

    private static String pick(Random r, String[] a) {
        return a[r.nextInt(a.length)];
    }

    private static String capitalize(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }
}
