package org.papiricoh.nationsandwar.view;

import net.minecraft.nbt.CompoundTag;

import java.util.List;
import java.util.UUID;

/**
 * The rank tree of any town or nation, for the GUI's Atlas: asked for with the {@code inspect} action and sent as
 * {@code network.TreePayload}. Trees are public, but the perms of each rank only show to the town's residents or the
 * nation's citizens: otherwise every {@link PoliticsView.RoleView#perms()} is 0 and {@code permsHidden} is set.
 *
 * <p>{@code scope}: "town" or "nation". {@code name} is "" when the town or nation does not exist ({@link #exists()}).
 * {@code government}: the nation's {@code GovernmentType} id ("" for a town); {@code level}: the town's {@code TownLevel}
 * ("" for a nation). {@code mine}: the viewer belongs to it. {@code members}: residents or citizens, with their role in
 * this tree. {@code provinces}: the nation's (empty for a town).
 */
public record TreeView(String scope, UUID id, String name, int color, String government, String level, String managedBy, boolean npc,
                       boolean mine, boolean permsHidden, List<PoliticsView.RoleView> roles, List<PoliticsView.Member> members,
                       List<PoliticsView.ProvinceView> provinces) {

    public static TreeView missing(String scope, UUID id) {
        return new TreeView(scope, id, "", 0x888888, "", "", "", false, false, true, List.of(), List.of(), List.of());
    }

    public boolean exists() {
        return !name.isEmpty();
    }

    public boolean nation() {
        return "nation".equals(scope);
    }

    public CompoundTag write() {
        CompoundTag t = new CompoundTag();
        t.putString("scope", scope);
        t.putUUID("id", id);
        t.putString("name", name);
        t.putInt("color", color);
        t.putString("gov", government);
        t.putString("level", level);
        t.putString("managedBy", managedBy);
        t.putBoolean("npc", npc);
        t.putBoolean("mine", mine);
        t.putBoolean("permsHidden", permsHidden);
        t.put("roles", PoliticsView.list(roles, PoliticsView.RoleView::write));
        t.put("members", PoliticsView.list(members, PoliticsView.Member::write));
        t.put("provinces", PoliticsView.list(provinces, PoliticsView.ProvinceView::write));
        return t;
    }

    public static TreeView read(CompoundTag t) {
        return new TreeView(t.getString("scope"), t.getUUID("id"), t.getString("name"), t.getInt("color"), t.getString("gov"),
                t.getString("level"), t.getString("managedBy"), t.getBoolean("npc"), t.getBoolean("mine"), t.getBoolean("permsHidden"),
                PoliticsView.readList(t, "roles", PoliticsView.RoleView::read), PoliticsView.readList(t, "members", PoliticsView.Member::read),
                PoliticsView.readList(t, "provinces", PoliticsView.ProvinceView::read));
    }

    /** The role a member holds in this tree, or null. */
    public PoliticsView.RoleView roleOf(UUID player) {
        for (PoliticsView.RoleView r : roles) if (r.holders().contains(player)) return r;
        return null;
    }
}
