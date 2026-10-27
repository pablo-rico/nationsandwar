package org.papiricoh.nationsandwar.war;

import net.minecraft.nbt.CompoundTag;
import org.papiricoh.nationsandwar.model.Nbt;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The peace a winner dictates: fallen towns annexed, a tribute of emeralds from the loser's bank, and days of vassalage.
 * Built from {@link #defaults} when the war ends; the winner may edit it during the treaty window, then it is applied.
 */
public final class Treaty {
    public final Set<UUID> annex = new LinkedHashSet<>();
    public int tribute;
    public int vassalDays;

    /** All fallen towns, {@code tributeShare} of the loser's bank, and {@code vassalDays} if the loser lost its capital. */
    public static Treaty defaults(War war, int loserBank, double tributeShare, UUID loserCapital, int vassalDays) {
        Treaty t = new Treaty();
        t.annex.addAll(war.fallen);
        if (loserCapital != null) t.annex.remove(loserCapital);
        t.tribute = (int) Math.floor(Math.max(0, loserBank) * tributeShare);
        t.vassalDays = loserCapital != null && war.fallen.contains(loserCapital) ? vassalDays : 0;
        return t;
    }

    /** Whether a term is one the winner may ask for: only fallen towns (never the capital), tribute ≤ bank, vassalage ≤ max. */
    public static boolean allowed(War war, Treaty t, int loserBank, UUID loserCapital, int maxVassalDays) {
        for (UUID town : t.annex) if (!war.fallen.contains(town) || town.equals(loserCapital)) return false;
        return t.tribute >= 0 && t.tribute <= loserBank && t.vassalDays >= 0 && t.vassalDays <= maxVassalDays;
    }

    CompoundTag save() {
        CompoundTag c = new CompoundTag();
        c.put("annex", Nbt.uuids(annex));
        c.putInt("tribute", tribute);
        c.putInt("vassal", vassalDays);
        return c;
    }

    static Treaty load(CompoundTag c) {
        Treaty t = new Treaty();
        t.annex.addAll(Nbt.uuids(c, "annex"));
        t.tribute = c.getInt("tribute");
        t.vassalDays = c.getInt("vassal");
        return t;
    }
}
