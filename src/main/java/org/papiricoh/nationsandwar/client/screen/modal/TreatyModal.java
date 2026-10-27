package org.papiricoh.nationsandwar.client.screen.modal;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import org.papiricoh.nationsandwar.client.ClientClaims;
import org.papiricoh.nationsandwar.client.gui.Draw;
import org.papiricoh.nationsandwar.client.gui.Ui;
import org.papiricoh.nationsandwar.client.screen.tab.Tab;
import org.papiricoh.nationsandwar.model.Perm;
import org.papiricoh.nationsandwar.view.Briefs;
import org.papiricoh.nationsandwar.view.PoliticsView;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The winner's treaty terms during the treaty window: which fallen towns to annex (never a capital), the tribute and the
 * days of vassalage. Set terms sends them; the defaults apply when the window ends.
 */
public final class TreatyModal extends Modal {
    private final UUID war;
    private final Set<UUID> annex = new LinkedHashSet<>();
    private final int tributeNow, vassalNow;
    private EditBox tribute, vassal;
    private final Ui.Scroll scroll = new Ui.Scroll();

    public TreatyModal(PoliticsView.WarView war) {
        super(Component.translatable(K + "treaty_terms"), Draw.Icon.SCROLL, 290, 220);
        this.war = war.id();
        this.annex.addAll(war.annex());
        this.tributeNow = war.tribute();
        this.vassalNow = war.vassalDays();
    }

    @Override
    protected void init() {
        tribute = field("treaty.tribute." + war, 7, tr("tribute"), true, tributeNow > 0 ? String.valueOf(tributeNow) : "");
        vassal = field("treaty.vassal." + war, 4, tr("vassal_days"), true, vassalNow > 0 ? String.valueOf(vassalNow) : "");
    }

    private PoliticsView.WarView war(PoliticsView v) {
        for (PoliticsView.WarView w : v.wars()) if (w.id().equals(war)) return w;
        return null;
    }

    @Override
    public boolean valid(PoliticsView v) {
        PoliticsView.WarView w = war(v);
        return w != null && "TREATY".equals(w.phase());
    }

    private Component denied(PoliticsView v, PoliticsView.WarView w) {
        UUID winner = "ATTACKERS".equals(w.outcome()) ? w.attacker() : "DEFENDERS".equals(w.outcome()) ? w.defender() : null;
        boolean editor = v.nation() != null && v.nation().id().equals(winner);
        return !editor ? tr("winner_only") : Tab.need(v.nation().myPerms(), Perm.SIGN_TREATY);
    }

    @Override
    public void render(GuiGraphics g, PoliticsView v, float partial) {
        PoliticsView.WarView wv = war(v);
        Component denied = denied(v, wv);
        int cw = (w - 8) / 2, top = y + 12;
        g.drawString(font, tr("annex"), x, y + 1, Draw.GOLD, false);
        int lh = y + h - top - 2;
        Draw.inset(g, x, top, cw, lh);
        scroll.content = wv.fallen().size() * 14 + 4;
        ui.scrollZone(x, top, cw, lh, scroll);
        ui.clip(g, x + 1, top + 1, cw - 2, lh - 2);
        int ry = top + 3 - scroll.offset;
        for (UUID t : wv.fallen()) {
            Briefs.TownBrief tb = ClientClaims.town(t);
            Briefs.NationBrief owner = tb == null ? null : ClientClaims.nation(tb.nation());
            boolean capital = owner != null && t.equals(owner.capital()), on = annex.contains(t);
            ui.check(g, x + 4, ry, Draw.fit(font, Component.literal(Tab.townName(v, t)), cw - 24), on,
                    denied != null ? denied : capital ? tr("no_annex_capital") : null, () -> {
                        if (!annex.remove(t)) annex.add(t);
                    });
            ry += 14;
        }
        if (wv.fallen().isEmpty()) g.drawString(font, tr("none"), x + 5, ry, Draw.MUTED, false);
        ui.unclip(g);
        int fx = x + cw + 8, fy = y + 1;
        g.drawString(font, tr("tribute"), fx, fy, Draw.GOLD, false);
        place(tribute, fx, fy + 10, cw);
        fy += 30;
        g.drawString(font, tr("vassal_days_max", v.prices().maxVassalDays()), fx, fy, Draw.GOLD, false);
        place(vassal, fx, fy + 10, cw);
        tribute.setEditable(denied == null);
        vassal.setEditable(denied == null);
        fy += 32;
        g.fill(fx, fy, fx + cw, fy + 1, 0x30FFFFFF);
        text(g, tr("current_terms", wv.annex().size(), wv.tribute(), wv.vassalDays()), fx, fy + 5, cw, Draw.MUTED);
        if (denied != null) Draw.text(g, font, Draw.fit(font, denied, (int) (cw / 0.75f)), fx, y + h - 8, 0.75f, Draw.BAD, false);
    }

    @Override
    public List<Action> actions(PoliticsView v) {
        PoliticsView.WarView wv = war(v);
        return List.of(action(tr("set_terms"), Ui.Style.GOLD, wv == null ? tr("none") : denied(v, wv), () -> {
            ListTag list = new ListTag();
            for (UUID t : annex) list.add(StringTag.valueOf(t.toString()));
            act("war.terms", "annex", list, "tribute", number(tribute), "vassal", number(vassal));
        }));
    }
}
