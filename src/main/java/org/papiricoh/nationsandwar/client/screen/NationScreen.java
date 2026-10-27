package org.papiricoh.nationsandwar.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.papiricoh.nationsandwar.client.ClientPolitics;
import org.papiricoh.nationsandwar.client.NWClient;
import org.papiricoh.nationsandwar.client.gui.Draw;
import org.papiricoh.nationsandwar.client.gui.Ui;
import org.papiricoh.nationsandwar.client.screen.tab.DiplomacyTab;
import org.papiricoh.nationsandwar.client.screen.tab.MapTab;
import org.papiricoh.nationsandwar.client.screen.tab.NationTab;
import org.papiricoh.nationsandwar.client.screen.tab.OverviewTab;
import org.papiricoh.nationsandwar.client.screen.tab.ProvincesTab;
import org.papiricoh.nationsandwar.client.screen.tab.RolesTab;
import org.papiricoh.nationsandwar.client.screen.tab.Tab;
import org.papiricoh.nationsandwar.client.screen.tab.TownTab;
import org.papiricoh.nationsandwar.client.screen.tab.WarTab;
import org.papiricoh.nationsandwar.view.PoliticsView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The management GUI (key N, {@code /nw}): a framed panel with a rail of tabs on the left and the chosen tab's page,
 * drawn every frame from {@link ClientPolitics#view()} so a fresh view from the server shows at once. {@code tab} may
 * name the tab to open ("overview", "town", "roles", "provinces", "nation", "nationroles", "diplomacy", "war", "map").
 */
public class NationScreen extends Screen {
    /** What the viewer typed in each field, kept while switching tabs or reopening the screen. */
    private static final Map<String, String> DRAFTS = new HashMap<>();
    private static String lastTab = "overview";

    private final List<Tab> tabs = new ArrayList<>();
    private final List<EditBox> fields = new ArrayList<>();
    private final Consumer<PoliticsView> listener = this::viewChanged;
    private Tab tab;
    private Ui ui;
    private int px, py, pw, ph;

    public NationScreen(String tab) {
        super(Component.translatable("nationsandwar.gui.title"));
        tabs.add(new OverviewTab(this));
        tabs.add(new TownTab(this));
        tabs.add(new RolesTab(this, false));
        tabs.add(new ProvincesTab(this));
        tabs.add(new NationTab(this));
        tabs.add(new RolesTab(this, true));
        tabs.add(new DiplomacyTab(this));
        tabs.add(new WarTab(this));
        tabs.add(new MapTab(this));
        this.tab = find(tab == null || tab.isEmpty() ? lastTab : tab);
    }

    private Tab find(String id) {
        for (Tab t : tabs) if (t.id().equals(id)) return t;
        return tabs.getFirst();
    }

    public Tab current() {
        return tab;
    }

    public void select(String id) {
        Tab t = find(id);
        if (t == tab) return;
        tab = t;
        lastTab = t.id();
        rebuildWidgets();
    }

    @Override
    protected void init() {
        if (ui == null) {
            ui = new Ui(font);
            ClientPolitics.listen(listener);
            ClientPolitics.requestView();
        }
        pw = Math.min(width - 8, 600);
        ph = Math.min(height - 8, 340);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        fields.clear();
        for (Tab t : tabs) t.bind(ui, font, px + 32, py + 25, pw - 37, ph - 30);
        tab.init();
    }

    private void viewChanged(PoliticsView v) {
        for (Tab t : tabs) t.viewChanged(v);
    }

    /** A text field for the current tab; its text survives tab switches. */
    public EditBox field(String key, int maxLength, Component hint, boolean digits) {
        EditBox b = new EditBox(font, 0, 0, 80, 13, hint);
        b.setMaxLength(maxLength);
        b.setValue(DRAFTS.getOrDefault(key, ""));
        if (digits) b.setFilter(s -> s.matches("\\d{0,7}"));
        b.setResponder(s -> DRAFTS.put(key, s));
        b.setHint(hint.copy().withStyle(s -> s.withColor(0x6A7384)));
        b.visible = false;
        addRenderableWidget(b);
        fields.add(b);
        return b;
    }

    public void clearField(EditBox b) {
        b.setValue("");
    }

    // --- drawing -----------------------------------------------------------------------------------------------------------

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
        g.fill(0, 0, width, height, Draw.DIM);
        ui.begin(mouseX, mouseY);
        for (EditBox f : fields) f.visible = false;
        Draw.shadow(g, px, py, pw, ph, 10);
        Draw.slab(g, px, py + 3, pw - 3, ph - 3, 3, 0xFF202733, 0xFF161B22);
        PoliticsView v = ClientPolitics.view();
        drawHeader(g, v);
        drawRail(g, v);
        int cx = px + 32, cy = py + 25, cw = pw - 37, ch = ph - 30;
        g.fill(cx - 2, cy - 2, cx + cw + 1, cy + ch + 1, 0xFF13171D);
        if (v == null) {
            g.drawCenteredString(font, Component.translatable("nationsandwar.gui.loading"), cx + cw / 2, cy + ch / 2 - 4, Draw.MUTED);
            return;
        }
        Component why = tab.unavailable(v);
        if (why != null) {
            g.drawCenteredString(font, why, cx + cw / 2, cy + ch / 2 - 4, Draw.MUTED);
            return;
        }
        tab.render(g, v, partial);
    }

    private void drawHeader(GuiGraphics g, PoliticsView v) {
        int hy = py + 6;
        Draw.icon(g, tab.icon(), px + 8, hy - 2);
        Component name = Component.translatable("nationsandwar.gui.tab." + tab.id());
        g.drawString(font, title, px + 28, hy + 2, Draw.GOLD, true);
        g.drawString(font, Component.literal("  ›  ").append(name), px + 28 + font.width(title), hy + 2, Draw.TEXT, false);
        int rx = px + pw - 22;
        ui.button(g, rx, hy - 1, 14, 13, Component.literal("✕"), Ui.Style.BAD, null, this::onClose);
        if (v == null) return;
        rx -= 4;
        if (v.alliance() != null) rx = crumb(g, rx, hy, v.alliance().name(), v.alliance().color());
        if (v.nation() != null) rx = crumb(g, rx, hy, v.nation().name(), v.nation().color());
        if (v.town() != null) crumb(g, rx, hy, v.town().name(), v.town().color());
    }

    /** A right-aligned name chip with a colour dot; returns the x for the next chip to its left. */
    private int crumb(GuiGraphics g, int right, int y, String name, int color) {
        int w = font.width(name) + 14, x = right - w;
        g.fill(x, y - 1, right, y + 11, 0xFF11151B);
        Draw.frame(g, x, y - 1, w, 12, Draw.alpha(color, 0x90));
        g.fill(x + 3, y + 2, x + 8, y + 7, Draw.opaque(color));
        g.drawString(font, name, x + 10, y + 1, Draw.TEXT, false);
        return x - 3;
    }

    private void drawRail(GuiGraphics g, PoliticsView v) {
        int rx = px + 5, ry = py + 25;
        for (Tab t : tabs) {
            Component why = v == null ? null : t.unavailable(v);
            boolean sel = t == tab, hover = ui.over(rx, ry, 24, 20);
            if (sel) {
                g.fill(rx, ry, rx + 27, ry + 20, 0xFF13171D);
                g.fill(rx, ry, rx + 2, ry + 20, Draw.GOLD);
            } else {
                g.fillGradient(rx, ry, rx + 24, ry + 20, hover ? 0xFF323C4B : 0xFF252C37, hover ? 0xFF262E3A : 0xFF1B2029);
                Draw.frame(g, rx, ry, 24, 20, 0xFF0D1015);
            }
            Draw.icon(g, t.icon(), rx + 4, ry + 2);
            if (why != null && !sel) g.fill(rx + 1, ry + 1, rx + 23, ry + 19, 0x9A12151A);
            Component name = Component.translatable("nationsandwar.gui.tab." + t.id());
            if (why != null) ui.tip(rx, ry, 24, 20, name, why.copy().withStyle(s -> s.withColor(Draw.MUTED & 0xFFFFFF)));
            else ui.tip(rx, ry, 24, 20, name);
            ui.zone(rx, ry, 24, 20, () -> select(t.id()));
            ry += 22;
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        ui.tooltip(g);
    }

    // --- input -------------------------------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        setFocused(null);
        if (button == 0 && ui.click(mx, my)) return true;
        return ClientPolitics.view() != null && tab.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return tab.mouseDragged(mx, my, button, dx, dy) || super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        return tab.mouseReleased(mx, my, button) || super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        return ui.scroll(mx, my, sy) || tab.mouseScrolled(mx, my, sy);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (!(getFocused() instanceof EditBox) && NWClient.OPEN_GUI.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void removed() {
        ClientPolitics.unlisten(listener);
        ClientPolitics.closeView();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
