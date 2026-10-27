package org.papiricoh.nationsandwar.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * What a role lets its holders do for their town or nation. A town role's perms act on that town; a nation role's on the
 * nation and on every town in it; the nation's Governor role's on the towns of the province each holder governs.
 */
public enum Perm {
    CLAIM, UNCLAIM, INVITE, KICK, MANAGE_ROLES, MANAGE_FLAGS, MANAGE_PLOTS, BANK_DEPOSIT, BANK_WITHDRAW, SET_SPAWN,
    DIPLOMACY, DECLARE_WAR, SIGN_TREATY, MANAGE_PROVINCES, MANAGE_TOWNS;

    /** The perms that only mean something on a nation's tree. */
    public static final Set<Perm> NATION_ONLY = EnumSet.of(DIPLOMACY, DECLARE_WAR, SIGN_TREATY, MANAGE_PROVINCES, MANAGE_TOWNS);

    public String langKey() {
        return "nationsandwar.perm." + name().toLowerCase();
    }

    public static int mask(Set<Perm> perms) {
        int m = 0;
        for (Perm p : perms) m |= 1 << p.ordinal();
        return m;
    }

    public static EnumSet<Perm> ofMask(int mask) {
        EnumSet<Perm> s = EnumSet.noneOf(Perm.class);
        for (Perm p : values()) if ((mask & 1 << p.ordinal()) != 0) s.add(p);
        return s;
    }
}
