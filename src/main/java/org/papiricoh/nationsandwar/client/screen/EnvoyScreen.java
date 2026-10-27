package org.papiricoh.nationsandwar.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import org.papiricoh.nationsandwar.client.ClientClaims;
import org.papiricoh.nationsandwar.client.ClientPolitics;
import org.papiricoh.nationsandwar.client.gui.Draw;
import org.papiricoh.nationsandwar.client.gui.Ui;
import org.papiricoh.nationsandwar.client.screen.tab.DiplomacyTab;
import org.papiricoh.nationsandwar.view.Briefs;
import org.papiricoh.nationsandwar.view.PoliticsView;

import java.util.UUID;

/**
 * Talking to an NPC nation's envoy: the envoy in person, the nation's name, colour and government, its opinion of my
 * nation, and the three things to ask — accept a gift of emeralds, an alliance, or peace when we are at war
 * ({@code npc.gift}, {@code npc.propose_alliance}, {@code npc.ask_peace}).
 */
public class EnvoyScreen extends Screen {
    private static final String K = "nationsandwar.gui.";
    protected final UUID nation;
    protected final int entity;
    private Ui ui;
    private EditBox amount;
    private int px, py, pw, ph;

    public EnvoyScreen(UUID nation, int entity) {
        super(Component.translatable("nationsandwar.gui.envoy"));
        this.nation = nation;
        this.entity = entity;
    }

    @Override
    protected void init() {
        if (ui == null) {
            ui = new Ui(font);
            ClientPolitics.requestView();
        }
        pw = Math.min(width - 16, 300);
        ph = Math.min(height - 16, 150);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        String keep = amount == null ? "16" : amount.getValue();
        amount = new EditBox(font, 0, 0, 50, 13, Component.translatable(K + "amount"));
        amount.setMaxLength(6);
        amount.setFilter(s -> s.matches("\\d{0,6}"));
        amount.setValue(keep);
        addRenderableWidget(amount);
    }

    private void act(String action, CompoundTag args) {
        args.putString("nation", nation.toString());
        ClientPolitics.act(action, args);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
        g.fill(0, 0, width, height, Draw.DIM);
        ui.begin(mouseX, mouseY);
        Briefs.NationBrief n = ClientClaims.nation(nation);
        int color = n == null ? 0x888888 : n.color();
        Draw.shadow(g, px, py, pw, ph, 10);
        Draw.slab(g, px, py + 3, pw - 3, ph - 3, 3, 0xFF222A36, 0xFF161B22);
        int left = 92;
        g.fillGradient(px + 1, py + 4, px + left, py + ph - 1, Draw.alpha(color, 0x90), Draw.alpha(color, 0x18));
        g.fill(px + left, py + 4, px + left + 1, py + ph - 1, Draw.alpha(color, 0xC0));
        if (minecraft != null && minecraft.level != null && minecraft.level.getEntity(entity) instanceof LivingEntity e)
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, px + 6, py + 12, px + left - 6, py + ph - 14, 44, 0.0625f, mouseX, mouseY, e);
        int rx = px + left + 10, rw = pw - left - 20, ry = py + 12;
        ui.button(g, px + pw - 20, py + 7, 14, 13, Component.literal("✕"), Ui.Style.BAD, null, this::onClose);
        if (n == null) {
            g.drawString(font, title, rx, ry, Draw.TEXT, true);
            return;
        }
        g.drawString(font, Component.translatable(K + "envoy_of"), rx, ry, Draw.MUTED, false);
        g.drawString(font, Draw.fit(font, Component.literal(n.name()), rw - 20), rx, ry + 11, Draw.mix(Draw.opaque(color), 0xFFFFFFFF, 0.35f), true);
        String titles = ClientClaims.titles(n.id(), n.government());
        Component gov = Component.translatable(titles).append(" · ").append(Component.translatable(titles + ".leader")).append(" " + n.leader());
        Draw.text(g, font, Draw.fit(font, gov, (int) (rw / 0.75f)), rx, ry + 23, 0.75f, Draw.MUTED, false);
        ry += 38;

        PoliticsView v = ClientPolitics.view();
        PoliticsView.NationSummary s = v == null ? null : v.nationSummary(nation);
        UUID mine = v == null || v.nation() == null ? null : v.nation().id();
        int opinion = s == null ? 0 : s.opinion();
        String relation = s == null ? "neutral" : s.relation();
        g.drawString(font, Component.translatable(K + "their_opinion"), rx, ry, Draw.TEXT, false);
        Component val = Component.literal((opinion > 0 ? "+" : "") + opinion);
        g.drawString(font, val, rx + rw - font.width(val), ry, opinion >= 0 ? Draw.GOOD : Draw.BAD, false);
        DiplomacyTab.opinion(g, rx, ry + 12, rw, opinion);
        String mood = opinion >= 60 ? "warm" : opinion >= 20 ? "friendly" : opinion > -20 ? "wary" : opinion > -50 ? "cold" : "hostile";
        Draw.text(g, font, Component.translatable(K + "mood." + mood), rx, ry + 20, 0.7f, Draw.MUTED, false);
        ui.chip(g, rx + rw - 50, ry + 19, Component.translatable(K + "relation." + relation), DiplomacyTab.relationColor(relation));
        ry += 36;

        Component noNation = mine == null ? Component.translatable(K + "no_nation") : null;
        amount.setX(rx);
        amount.setY(ry);
        amount.setWidth(52);
        int gift = parse(amount.getValue());
        Component giftDenied = noNation != null ? noNation : gift <= 0 ? Component.translatable(K + "type_amount") : null;
        ui.button(g, rx + 56, ry, rw - 56, 13, Component.translatable(K + "gift", gift), Ui.Style.GOLD, giftDenied, () -> {
            CompoundTag t = new CompoundTag();
            t.putInt("amount", gift);
            act("npc.gift", t);
        });
        ry += 17;
        boolean ally = "ally".equals(relation), enemy = "enemy".equals(relation) || ClientClaims.enemies(mine, nation);
        Component allyDenied = noNation != null ? noNation : ally ? Component.translatable(K + "is_ally") : enemy ? Component.translatable(K + "at_war") : null;
        ui.button(g, rx, ry, rw, 13, Component.translatable(K + "propose_alliance"), Ui.Style.GOOD, allyDenied, () -> act("npc.propose_alliance", new CompoundTag()));
        ry += 17;
        Component peaceDenied = noNation != null ? noNation : !enemy ? Component.translatable(K + "not_at_war") : null;
        ui.button(g, rx, ry, rw, 13, Component.translatable(K + "ask_peace"), Ui.Style.NORMAL, peaceDenied, () -> act("npc.ask_peace", new CompoundTag()));
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        ui.tooltip(g);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        setFocused(null);
        return button == 0 && ui.click(mx, my);
    }

    @Override
    public void removed() {
        ClientPolitics.closeView();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
