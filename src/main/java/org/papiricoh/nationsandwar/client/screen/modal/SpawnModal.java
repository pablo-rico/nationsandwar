package org.papiricoh.nationsandwar.client.screen.modal;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.GuiGraphics;
import org.papiricoh.nationsandwar.client.gui.Draw;
import org.papiricoh.nationsandwar.client.gui.Ui;
import org.papiricoh.nationsandwar.client.screen.tab.Tab;
import org.papiricoh.nationsandwar.model.Perm;
import org.papiricoh.nationsandwar.view.PoliticsView;

import java.util.List;

/** My town's spawn: where it is, and the two things to do with it — move it to where I stand, or travel there. */
public final class SpawnModal extends Modal {
    public SpawnModal() {
        super(Component.translatable(K + "spawn"), Draw.Icon.TOWN, 230, 126);
    }

    @Override
    public boolean valid(PoliticsView v) {
        return v.town() != null;
    }

    @Override
    public void render(GuiGraphics g, PoliticsView v, float partial) {
        PoliticsView.TownView t = v.town();
        BlockPos p = BlockPos.of(t.spawn());
        Draw.inset(g, x, y + 2, w, 30);
        label(g, tr("spawn_at"), x + 6, y + 6);
        Component where = Component.literal(p.getX() + ", " + p.getY() + ", " + p.getZ());
        g.drawString(font, where, x + 6, y + 17, Draw.TEXT, false);
        Component dim = Component.literal(t.dimension().replace("minecraft:", ""));
        Draw.text(g, font, dim, x + w - 6 - font.width(dim) * 0.75f, y + 18, 0.75f, Draw.MUTED, false);
        text(g, tr("spawn_hint"), x, y + 38, w, 0xFF6A7384);
    }

    @Override
    public List<Action> actions(PoliticsView v) {
        return List.of(action(tr("set_spawn_here"), Ui.Style.NORMAL, Tab.need(v.town().myPerms(), Perm.SET_SPAWN), () -> act("town.setspawn")),
                action(tr("go_spawn"), Ui.Style.GOLD, null, () -> act("town.spawn")));
    }
}
