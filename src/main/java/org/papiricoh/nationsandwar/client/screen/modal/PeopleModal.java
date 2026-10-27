package org.papiricoh.nationsandwar.client.screen.modal;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.papiricoh.nationsandwar.client.ClientPolitics;
import org.papiricoh.nationsandwar.client.gui.Draw;
import org.papiricoh.nationsandwar.client.gui.Skins;
import org.papiricoh.nationsandwar.client.gui.Ui;
import org.papiricoh.nationsandwar.client.screen.tab.AtlasTab;
import org.papiricoh.nationsandwar.client.screen.tab.Tab;
import org.papiricoh.nationsandwar.view.PoliticsView;
import org.papiricoh.nationsandwar.view.TreeView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Who holds one rank, and (on my own tree, for an ordinary rank I manage) the members to give it to or take it from, at
 * once. Others' trees only list the holders. Governor and leader ranks change elsewhere, which the modal says.
 */
public final class PeopleModal extends Modal {
    private final int role;
    private final Ui.Scroll scroll = new Ui.Scroll();
    private EditBox search;

    public PeopleModal(Component rank, int role) {
        super(Component.translatable(K + "people_of", rank), Draw.Icon.ROLES, 250, 240);
        this.role = role;
    }

    private PoliticsView.RoleView role(TreeView t) {
        if (t == null) return null;
        for (PoliticsView.RoleView r : t.roles()) if (r.id() == role) return r;
        return null;
    }

    @Override
    protected void init() {
        search = field("people.search", 24, tr("search"), false, "");
        search.setValue("");
    }

    @Override
    public boolean valid(PoliticsView v) {
        return role(ClientPolitics.tree()) != null;
    }

    @Override
    public void render(GuiGraphics g, PoliticsView v, float partial) {
        TreeView t = ClientPolitics.tree();
        PoliticsView.RoleView r = role(t);
        AtlasTab.Rights rights = new AtlasTab.Rights(t, v);
        boolean normal = "NORMAL".equals(r.kind()), edit = t.mine();
        Component denied = !normal ? tr(r.parent() < 0 ? "leader_fixed" : "governor_hint_short") : rights.manages(r) ? null : tr("cannot_manage");
        place(search, x, y + 1, w);
        String q = search.getValue().trim().toLowerCase(Locale.ROOT);
        List<PoliticsView.Member> holders = new ArrayList<>(), others = new ArrayList<>();
        for (PoliticsView.Member m : t.members()) {
            if (!q.isEmpty() && !m.name().toLowerCase(Locale.ROOT).contains(q)) continue;
            if (r.holders().contains(m.id())) holders.add(m);
            else if (edit && normal) others.add(m);
        }
        int top = y + 18, lh = y + h - top - (edit && denied != null ? 10 : 0);
        Draw.inset(g, x, top, w, lh);
        scroll.content = (holders.size() + others.size()) * 13 + 30;
        ui.scrollZone(x, top, w, lh, scroll);
        ui.clip(g, x + 1, top + 1, w - 2, lh - 2);
        int ry = top + 3 - scroll.offset;
        g.drawString(font, tr("holders", r.holders().size()), x + 5, ry, Draw.GOLD, false);
        ry += 11;
        for (PoliticsView.Member m : holders) {
            person(g, m, ry);
            if (edit) ui.button(g, x + w - 16, ry + 1, 12, 10, Component.literal("✕"), Ui.Style.BAD, denied,
                    () -> act("role.unassign", "scope", t.scope(), "player", m.id()));
            ry += 13;
        }
        if (holders.isEmpty()) {
            Draw.text(g, font, Component.translatable("nationsandwar.gui.vacant"), x + 8, ry + 2, 0.75f, Draw.MUTED, false);
            ry += 12;
        }
        if (!others.isEmpty()) {
            ry += 4;
            g.drawString(font, tr("give_rank"), x + 5, ry, Draw.GOLD, false);
            ry += 11;
            for (PoliticsView.Member m : others) {
                person(g, m, ry);
                PoliticsView.RoleView theirs = t.roleOf(m.id());
                boolean leader = theirs != null && theirs.parent() < 0;
                ui.button(g, x + w - 16, ry + 1, 12, 10, Component.literal("+"), Ui.Style.GOOD, leader ? tr("leader_fixed") : denied,
                        () -> act("role.assign", "scope", t.scope(), "id", r.id(), "player", m.id()));
                ry += 13;
            }
        }
        ui.unclip(g);
        ui.scrollbar(g, x + w - 3, top + 1, lh - 2, scroll);
        if (edit && denied != null) Draw.text(g, font, Draw.fit(font, denied, (int) (w / 0.75f)), x, y + h - 7, 0.75f, Draw.WARN, false);
    }

    private void person(GuiGraphics g, PoliticsView.Member m, int ry) {
        Skins.face(g, m.id(), m.name(), x + 5, ry + 1, 9);
        g.fill(x + 12, ry + 7, x + 15, ry + 10, m.online() ? Draw.GOOD : 0xFF555B66);
        g.drawString(font, Draw.fit(font, Component.literal(m.name()), w - 60), x + 18, ry + 2, m.online() ? Draw.TEXT : Draw.MUTED, false);
        PoliticsView.RoleView theirs = ClientPolitics.tree() == null ? null : ClientPolitics.tree().roleOf(m.id());
        if (theirs != null && theirs.id() != role) {
            Component rn = Draw.fit(font, Tab.roleName(theirs.name()), 60);
            Draw.text(g, font, rn, x + w - 22 - font.width(rn) * 0.7f, ry + 3, 0.7f, Draw.mix(Draw.opaque(theirs.color()), 0xFFFFFFFF, 0.3f), false);
        }
    }
}
