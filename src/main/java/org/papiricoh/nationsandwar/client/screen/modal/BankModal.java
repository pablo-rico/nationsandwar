package org.papiricoh.nationsandwar.client.screen.modal;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.papiricoh.nationsandwar.client.gui.Draw;
import org.papiricoh.nationsandwar.client.gui.Ui;
import org.papiricoh.nationsandwar.client.screen.tab.Tab;
import org.papiricoh.nationsandwar.model.Perm;
import org.papiricoh.nationsandwar.view.PoliticsView;

import java.util.List;

/**
 * My town's or my nation's bank: the balance on a gold plate, an amount (typed or picked from quick amounts), and Deposit
 * (from my inventory, the primary action) or Withdraw (needs BANK_WITHDRAW). It stays open for several moves.
 */
public final class BankModal extends Modal {
    private static final int[] QUICK = {16, 64, 256, 1024};
    private final boolean nation;
    private EditBox amount;

    public BankModal(boolean nation) {
        super(Component.translatable(K + (nation ? "nation_bank" : "town_bank")), nation ? Draw.Icon.NATION : Draw.Icon.TOWN, 220, 150);
        this.nation = nation;
    }

    @Override
    protected void init() {
        amount = field((nation ? "nation" : "town") + ".amount", 7, tr("amount"), true, "");
    }

    @Override
    public boolean valid(PoliticsView v) {
        return nation ? v.nation() != null : v.town() != null;
    }

    private int bank(PoliticsView v) {
        return nation ? v.nation().bank() : v.town().bank();
    }

    private int perms(PoliticsView v) {
        return nation ? v.nation().myPerms() : v.town().myPerms();
    }

    @Override
    public void render(GuiGraphics g, PoliticsView v, float partial) {
        Draw.shadow(g, x, y + 2, w - 4, 30, 5);
        Draw.slab(g, x, y + 2, w - 4, 30, 3, 0xFF3A3424, 0xFF262014);
        Draw.frame(g, x, y + 2, w - 4, 30, Draw.alpha(Draw.GOLD, 0x70));
        label(g, Component.translatable("nationsandwar.info.bank"), x + 6, y + 6);
        Component sum = Tab.money(bank(v));
        Draw.text(g, font, sum, x + w - 10 - font.width(sum) * 1.5f, y + 11, 1.5f, Draw.GOLD, true);
        int ay = y + 42;
        label(g, tr("amount"), x, ay);
        place(amount, x, ay + 8, w);
        int qx = x, qw = (w - 9) / 4;
        for (int q : QUICK) {
            ui.button(g, qx, ay + 25, qw, 12, Component.literal(q + " ◆"), Ui.Style.NORMAL, null, () -> amount.setValue(String.valueOf(q)));
            qx += qw + 3;
        }
        Draw.text(g, font, Draw.fit(font, tr("bank_hint"), (int) (w / 0.7f)), x, ay + 42, 0.7f, 0xFF6A7384, false);
    }

    @Override
    public List<Action> actions(PoliticsView v) {
        int n = number(amount);
        Component none = n <= 0 ? tr("type_amount") : null;
        Component out = none != null ? none : Tab.need(perms(v), Perm.BANK_WITHDRAW);
        String scope = nation ? "nation" : "town";
        return List.of(new Action(tr("withdraw"), Ui.Style.GOLD, out, () -> {
            act(scope + ".withdraw", "amount", n);
            amount.setValue("");
        }, false), new Action(tr("deposit"), Ui.Style.GOOD, none, () -> {
            act(scope + ".deposit", "amount", n);
            amount.setValue("");
        }, false));
    }
}
