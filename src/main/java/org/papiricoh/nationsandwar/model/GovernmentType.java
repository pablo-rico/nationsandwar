package org.papiricoh.nationsandwar.model;

import java.util.EnumSet;
import java.util.UUID;

/**
 * How a nation calls itself and its default ranks. Purely names: the rules are the same for every government. Each entry's
 * keys: {@code nationsandwar.gov.<id>} (the noun, "Kingdom"), {@code .leader} ("King"), {@code .second} (the leader's
 * right hand), {@code .marshal}, {@code .treasurer}, {@code .governor}.
 */
public enum GovernmentType {
    KINGDOM, REPUBLIC, EMPIRE, FEDERATION, THEOCRACY, TRIBE;

    public String id() {
        return name().toLowerCase();
    }

    public String key() {
        return "nationsandwar.gov." + id();
    }

    public String key(String rank) {
        return key() + "." + rank;
    }

    public static GovernmentType byId(String id) {
        for (GovernmentType g : values()) if (g.id().equalsIgnoreCase(id)) return g;
        return KINGDOM;
    }

    /** A fresh nation tree: leader → (second → marshal, treasurer), governors. Names are lang keys until renamed. */
    public RoleTree defaultTree(UUID leader) {
        RoleTree t = RoleTree.of("@" + key("leader"), 0xFFD54F, leader);
        Role second = t.addBuiltIn(Role.Kind.NORMAL, "@" + key("second"), 0xE57373, RoleTree.ROOT,
                EnumSet.of(Perm.INVITE, Perm.KICK, Perm.DIPLOMACY, Perm.MANAGE_PROVINCES, Perm.MANAGE_TOWNS, Perm.BANK_DEPOSIT, Perm.MANAGE_ROLES));
        t.addBuiltIn(Role.Kind.NORMAL, "@" + key("marshal"), 0xB71C1C, second.id, EnumSet.of(Perm.DECLARE_WAR, Perm.SIGN_TREATY, Perm.BANK_DEPOSIT));
        t.addBuiltIn(Role.Kind.NORMAL, "@" + key("treasurer"), 0x43A047, second.id, EnumSet.of(Perm.BANK_DEPOSIT, Perm.BANK_WITHDRAW));
        t.addBuiltIn(Role.Kind.GOVERNOR, "@" + key("governor"), 0x5C6BC0, RoleTree.ROOT,
                EnumSet.of(Perm.CLAIM, Perm.UNCLAIM, Perm.INVITE, Perm.KICK, Perm.MANAGE_FLAGS, Perm.MANAGE_PLOTS));
        return t;
    }

    /** A fresh town tree (same for every government): mayor → assistant, builder. */
    public static RoleTree defaultTownTree(UUID mayor) {
        RoleTree t = RoleTree.of("@nationsandwar.role.mayor", 0xFFB300, mayor);
        Role assistant = t.addBuiltIn(Role.Kind.NORMAL, "@nationsandwar.role.assistant", 0x4FC3F7, RoleTree.ROOT,
                EnumSet.of(Perm.CLAIM, Perm.UNCLAIM, Perm.INVITE, Perm.KICK, Perm.MANAGE_FLAGS, Perm.MANAGE_PLOTS, Perm.BANK_DEPOSIT, Perm.SET_SPAWN, Perm.MANAGE_ROLES));
        t.addBuiltIn(Role.Kind.NORMAL, "@nationsandwar.role.builder", 0x8D6E63, assistant.id, EnumSet.of(Perm.CLAIM, Perm.BANK_DEPOSIT));
        return t;
    }
}
