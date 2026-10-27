package org.papiricoh.nationsandwar.client.screen.modal;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.papiricoh.nationsandwar.client.gui.Draw;
import org.papiricoh.nationsandwar.client.gui.Ui;
import org.papiricoh.nationsandwar.client.screen.tab.Tab;
import org.papiricoh.nationsandwar.model.GovernmentType;
import org.papiricoh.nationsandwar.view.PoliticsView;

import java.util.List;

/**
 * The six governments as raised plates with their leader's and governor's titles. To found a nation (a name field first,
 * paid from my town's bank; mayors only) or to change my nation's government (its leader only; only names and titles
 * change).
 */
public final class GovernmentModal extends Modal {
    private final boolean found;
    private EditBox name;
    private GovernmentType pick;

    /** @param found founding a new nation (else changing mine) */
    public GovernmentModal(boolean found) {
        super(Component.translatable(K + (found ? "found_nation" : "government")), Draw.Icon.NATION, 300, found ? 222 : 164);
        this.found = found;
    }

    @Override
    protected void init() {
        if (found) name = field("nation.found", 24, tr("nation_name"), false, "");
    }

    @Override
    public boolean valid(PoliticsView v) {
        return found ? v.town() != null && v.nation() == null : v.nation() != null;
    }

    @Override
    public void render(GuiGraphics g, PoliticsView v, float partial) {
        if (pick == null) pick = found ? GovernmentType.KINGDOM : GovernmentType.byId(v.nation().government());
        int sy = y + 1;
        if (found) {
            sy = text(g, tr("found_nation_hint", v.prices().nation()), x, sy, w, Draw.MUTED) + 3;
            label(g, tr("name"), x, sy);
            place(name, x, sy + 8, w);
            sy += 28;
        }
        boolean may = denied(v) == null;
        GovernmentType[] all = GovernmentType.values();
        int cw = (w - 6) / 2, rh = 30;
        for (int i = 0; i < all.length; i++) {
            GovernmentType gt = all[i];
            int cx = x + (i % 2) * (cw + 6), cy = sy + (i / 2) * (rh + 3);
            boolean sel = gt == pick, cur = !found && gt.id().equals(v.nation().government()), hover = may && ui.over(cx, cy, cw, rh - 3);
            Draw.slab(g, cx, cy, cw - 3, rh - 3, 2, sel ? 0xFF3A3424 : hover ? 0xFF2C3440 : 0xFF232A34, sel ? 0xFF2A2518 : 0xFF1A1F27);
            if (sel) Draw.frame(g, cx, cy, cw - 3, rh - 3, Draw.GOLD);
            g.drawString(font, Component.translatable(gt.key()), cx + 5, cy + 4, sel ? Draw.GOLD : Draw.TEXT, false);
            if (cur) Draw.text(g, font, tr("current"), cx + cw - 8 - font.width(tr("current")) * 0.7f, cy + 5, 0.7f, Draw.GOOD, false);
            Component titles = Component.translatable(gt.key("leader")).append(" · ").append(Component.translatable(gt.key("governor")));
            Draw.text(g, font, Draw.fit(font, titles, (int) ((cw - 12) / 0.7f)), cx + 5, cy + 15, 0.7f, Draw.MUTED, false);
            if (may) ui.zone(cx, cy, cw - 3, rh - 3, () -> pick = gt);
        }
        sy += 3 * (rh + 3);
        Draw.text(g, font, Draw.fit(font, tr("gov_hint"), (int) (w / 0.7f)), x, sy + 1, 0.7f, 0xFF6A7384, false);
    }

    private Component denied(PoliticsView v) {
        if (found) return Tab.leads(v.town().roles(), v.me()) ? null : tr("mayor_only");
        return Tab.leads(v.nation().roles(), v.me()) ? null : tr("leader_only");
    }

    @Override
    public List<Action> actions(PoliticsView v) {
        Component why = denied(v);
        GovernmentType gt = pick == null ? GovernmentType.KINGDOM : pick;
        if (found) {
            if (why == null && name.getValue().isBlank()) why = tr("type_name");
            return List.of(action(tr("found_cost", v.prices().nation()), Ui.Style.GOLD, why,
                    () -> act("nation.found", "name", name.getValue().trim(), "gov", gt.id())));
        }
        if (why == null && gt.id().equals(v.nation().government())) why = tr("already_current");
        return List.of(action(tr("change"), Ui.Style.GOLD, why, () -> act("nation.gov", "gov", gt.id())));
    }
}
