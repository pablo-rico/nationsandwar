package org.papiricoh.nationsandwar.model;

/**
 * Who may do what in a town's chunks: a bit per (group, action). Towns hold a default set; a chunk may override it.
 */
public record ChunkPerms(int bits) {
    public enum Group {RESIDENT, NATION, ALLY, OUTSIDER}

    public enum Action {BUILD, DESTROY, SWITCH, CONTAINER, ITEM_USE}

    /** Residents do everything, the nation and allies may use doors and buttons, outsiders nothing. */
    public static final ChunkPerms DEFAULT = new ChunkPerms(0)
            .with(Group.RESIDENT, Action.BUILD, true).with(Group.RESIDENT, Action.DESTROY, true).with(Group.RESIDENT, Action.SWITCH, true)
            .with(Group.RESIDENT, Action.CONTAINER, true).with(Group.RESIDENT, Action.ITEM_USE, true)
            .with(Group.NATION, Action.SWITCH, true).with(Group.NATION, Action.ITEM_USE, true)
            .with(Group.ALLY, Action.SWITCH, true);

    private static int bit(Group g, Action a) {
        return 1 << (g.ordinal() * Action.values().length + a.ordinal());
    }

    public boolean allows(Group g, Action a) {
        return (bits & bit(g, a)) != 0;
    }

    public ChunkPerms with(Group g, Action a, boolean on) {
        return new ChunkPerms(on ? bits | bit(g, a) : bits & ~bit(g, a));
    }
}
