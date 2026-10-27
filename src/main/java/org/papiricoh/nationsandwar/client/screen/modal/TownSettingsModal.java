package org.papiricoh.nationsandwar.client.screen.modal;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.papiricoh.nationsandwar.client.gui.Draw;
import org.papiricoh.nationsandwar.client.screen.tab.Tab;
import org.papiricoh.nationsandwar.model.ChunkPerms;
import org.papiricoh.nationsandwar.model.Flag;
import org.papiricoh.nationsandwar.model.Perm;
import org.papiricoh.nationsandwar.view.PoliticsView;

/**
 * My town's settings, each applied as soon as it is clicked: the town-wide flags (MANAGE_FLAGS), open or closed
 * (INVITE), and the chunk permission matrix, group × action (MANAGE_PLOTS).
 */
public final class TownSettingsModal extends Modal {
    /** The lang suffixes of {@link ChunkPerms.Action}, in order. */
    public static final String[] ACTIONS = {"build", "destroy", "switch", "container", "item_use"};

    public TownSettingsModal() {
        super(Component.translatable(K + "settings"), Draw.Icon.TOWN, 280, 226);
    }

    @Override
    public boolean valid(PoliticsView v) {
        return v.town() != null;
    }

    @Override
    public void render(GuiGraphics g, PoliticsView v, float partial) {
        PoliticsView.TownView t = v.town();
        int perms = t.myPerms(), sy = y + 1;
        section(g, tr("flags"), sy);
        sy += 12;
        Flag[] flags = Flag.values();
        for (int i = 0; i < flags.length; i++) {
            Flag f = flags[i];
            boolean on = (t.flags() & 1 << f.ordinal()) != 0;
            ui.check(g, x + 4 + (i % 2) * (w / 2), sy + (i / 2) * 14, Component.translatable(f.langKey()), on, Tab.need(perms, Perm.MANAGE_FLAGS),
                    () -> act("town.flag", "flag", f.name(), "value", (byte) (on ? 0 : 1)));
        }
        sy += 32;
        section(g, tr("membership"), sy);
        sy += 12;
        ui.check(g, x + 4, sy, tr(t.open() ? "open" : "closed"), t.open(), Tab.need(perms, Perm.INVITE), () -> act("town.open", "value", !t.open()));
        sy += 20;
        section(g, tr("chunk_perms"), sy);
        sy += 12;
        ChunkPerms cp = new ChunkPerms(t.chunkPerms());
        int labelW = 70, cell = (w - labelW - 4) / ACTIONS.length;
        for (int a = 0; a < ACTIONS.length; a++) {
            Component name = tr("action." + ACTIONS[a]);
            int ax = x + labelW + a * cell;
            Component fit = Draw.fit(font, name, (int) ((cell - 2) / 0.75f));
            Draw.text(g, font, fit, ax + (cell - font.width(fit) * 0.75f) / 2, sy, 0.75f, Draw.MUTED, false);
        }
        sy += 9;
        Component denied = Tab.need(perms, Perm.MANAGE_PLOTS);
        boolean odd = false;
        for (ChunkPerms.Group grp : ChunkPerms.Group.values()) {
            if (odd) g.fill(x, sy - 1, x + w, sy + 13, 0x12FFFFFF);
            odd = !odd;
            g.drawString(font, Draw.fit(font, tr("group." + grp.name().toLowerCase()), labelW - 6), x + 4, sy + 2, Draw.TEXT, false);
            for (int a = 0; a < ACTIONS.length; a++) {
                ChunkPerms.Action ac = ChunkPerms.Action.values()[a];
                boolean on = cp.allows(grp, ac);
                ui.check(g, x + labelW + a * cell + (cell - 11) / 2, sy + 1, Component.empty(), on, denied,
                        () -> act("town.perm", "group", grp.name(), "perm", ac.name(), "value", !on));
            }
            sy += 14;
        }
        Draw.text(g, font, Draw.fit(font, tr("chunk_perms_hint"), (int) (w / 0.7f)), x, sy + 3, 0.7f, 0xFF6A7384, false);
    }

    private void section(GuiGraphics g, Component text, int sy) {
        g.drawString(font, text, x, sy, Draw.GOLD, false);
        int tw = font.width(text) + 4;
        g.fill(x + tw, sy + 4, x + w, sy + 5, 0x40E3B556);
    }
}
