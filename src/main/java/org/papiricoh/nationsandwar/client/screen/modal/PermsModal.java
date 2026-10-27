package org.papiricoh.nationsandwar.client.screen.modal;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.papiricoh.nationsandwar.client.ClientPolitics;
import org.papiricoh.nationsandwar.client.gui.Draw;
import org.papiricoh.nationsandwar.client.screen.tab.AtlasTab;
import org.papiricoh.nationsandwar.client.screen.tab.Tab;
import org.papiricoh.nationsandwar.model.Perm;
import org.papiricoh.nationsandwar.view.PoliticsView;
import org.papiricoh.nationsandwar.view.TreeView;

import java.util.ArrayList;
import java.util.List;

/**
 * The permissions of one rank of my town's or nation's tree, toggled at once: only those I hold can be granted, a town tree
 * hides the nation-only ones, and the leader's rank holds them all.
 */
public final class PermsModal extends Modal {
    private final int role;

    public PermsModal(Component rank, int role) {
        super(Component.translatable(K + "perms_of", rank), Draw.Icon.ROLES, 300, bodyHeight());
        this.role = role;
    }

    /** Room for the rows of checkboxes a town's (fewer perms) or a nation's tree shows, and two lines of hint. */
    private static int bodyHeight() {
        TreeView t = ClientPolitics.tree();
        int shown = 0;
        for (Perm p : Perm.values()) if (t == null || t.nation() || !Perm.NATION_ONLY.contains(p)) shown++;
        return TITLE + 6 + 2 + (shown + 1) / 2 * 14 + 30 + 8 + FOOTER;
    }

    private PoliticsView.RoleView role(TreeView t) {
        if (t == null) return null;
        for (PoliticsView.RoleView r : t.roles()) if (r.id() == role) return r;
        return null;
    }

    @Override
    public boolean valid(PoliticsView v) {
        TreeView t = ClientPolitics.tree();
        return t != null && t.mine() && role(t) != null;
    }

    @Override
    public void render(GuiGraphics g, PoliticsView v, float partial) {
        TreeView t = ClientPolitics.tree();
        PoliticsView.RoleView r = role(t);
        AtlasTab.Rights rights = new AtlasTab.Rights(t, v);
        List<Perm> shown = new ArrayList<>();
        for (Perm p : Perm.values()) if (t.nation() || !Perm.NATION_ONLY.contains(p)) shown.add(p);
        Component denied = r.parent() < 0 ? tr("leader_all") : rights.manages(r) ? null : tr("cannot_manage");
        int mine = rights.myPerms(), cw = (w - 6) / 2, rows = (shown.size() + 1) / 2;
        for (int i = 0; i < shown.size(); i++) {
            Perm p = shown.get(i);
            int px = x + (i / rows) * (cw + 6), py = y + 2 + (i % rows) * 14;
            boolean on = r.parent() < 0 || Tab.has(r.perms(), p);
            Component why = denied != null ? denied : !Tab.has(mine, p) && !rights.leader() ? tr("not_yours") : null;
            ui.check(g, px, py, Draw.fit(font, Component.translatable(p.langKey()), cw - 16), on, why,
                    () -> act("role.perms", "scope", t.scope(), "id", r.id(), "perms", r.perms() ^ 1 << p.ordinal()));
        }
        int hy = y + 2 + rows * 14 + 4;
        g.fill(x, hy, x + w, hy + 1, 0x30FFFFFF);
        Component hint = denied != null ? denied : tr("perms_hint");
        text(g, hint, x, hy + 5, w, denied != null ? Draw.WARN : 0xFF6A7384);
    }
}
