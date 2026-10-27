package org.papiricoh.nationsandwar.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.papiricoh.nationsandwar.client.ClientPolitics;
import org.papiricoh.nationsandwar.client.NWClient;
import org.papiricoh.nationsandwar.client.gui.Draw;
import org.papiricoh.nationsandwar.client.gui.Ui;
import org.papiricoh.nationsandwar.client.screen.modal.Modal;
import org.papiricoh.nationsandwar.client.screen.tab.AtlasTab;
import org.papiricoh.nationsandwar.client.screen.tab.DiplomacyTab;
import org.papiricoh.nationsandwar.client.screen.tab.MapTab;
import org.papiricoh.nationsandwar.client.screen.tab.NationTab;
import org.papiricoh.nationsandwar.client.screen.tab.OverviewTab;
import org.papiricoh.nationsandwar.client.screen.tab.ProvincesTab;
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
 * name the tab to open ("overview", "town", "atlas", "provinces", "nation", "diplomacy", "war", "map"; the old "roles" and
 * "nationroles" open the Atlas on my town and on my nation).
 *
 * <p>Forms live in {@link Modal}s opened with {@link #open}: while one is open the page underneath is drawn inert (mouse
 * at −1,−1, its text fields hidden) under a dim veil, the modal on top with its own text fields; Esc closes the modal
 * first and Enter runs its primary action.
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
    private Modal modal;
    private int px, py, pw, ph, pendingModal = -1;

    public NationScreen(String tab) {
        super(Component.translatable("nationsandwar.gui.title"));
        tabs.add(new OverviewTab(this));
        tabs.add(new TownTab(this));
        tabs.add(new AtlasTab(this));
        tabs.add(new ProvincesTab(this));
        tabs.add(new NationTab(this));
        tabs.add(new DiplomacyTab(this));
        tabs.add(new WarTab(this));
        tabs.add(new MapTab(this));
        String id = tab == null || tab.isEmpty() ? lastTab : tab;
        if (id.equals("roles") || id.equals("nationroles")) {
            boolean nation = id.equals("nationroles");
            atlas().focusLater(v -> nation ? v.nation() == null ? null : new AtlasTab.Target("nation", v.nation().id())
                    : v.town() == null ? null : new AtlasTab.Target("town", v.town().id()));
            id = "atlas";
        }
        this.tab = find(id);
    }

    private Tab find(String id) {
        for (Tab t : tabs) if (t.id().equals(id)) return t;
        return tabs.getFirst();
    }

    public Tab current() {
        return tab;
    }

    /** The Atlas tab (the map tab focuses it on a picked claim). */
    public AtlasTab atlas() {
        return (AtlasTab) find("atlas");
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
        if (modal != null) for (EditBox b : modal.fields()) addWidget(b);
    }

    private void viewChanged(PoliticsView v) {
        for (Tab t : tabs) t.viewChanged(v);
    }

    /** A text field for the current tab; its text survives tab switches. */
    public EditBox field(String key, int maxLength, Component hint, boolean digits) {
        EditBox b = box(key, maxLength, hint, digits, "");
        addRenderableWidget(b);
        fields.add(b);
        return b;
    }

    /** A text field for the open modal (see {@link Modal#field}); drawn by hand above the veil. */
    public EditBox modalField(String key, int maxLength, Component hint, boolean digits, String initial) {
        EditBox b = box(key, maxLength, hint, digits, initial);
        addWidget(b);
        return b;
    }

    private EditBox box(String key, int maxLength, Component hint, boolean digits, String initial) {
        EditBox b = new EditBox(font, 0, 0, 80, 13, hint);
        b.setMaxLength(maxLength);
        if (digits) b.setFilter(s -> s.matches("\\d{0,7}"));
        b.setValue(DRAFTS.getOrDefault(key, initial == null ? "" : initial));
        b.setResponder(s -> DRAFTS.put(key, s));
        b.setHint(hint.copy().withStyle(s -> s.withColor(0x6A7384)));
        b.visible = false;
        return b;
    }

    public void clearField(EditBox b) {
        b.setValue("");
    }

    // --- modals ------------------------------------------------------------------------------------------------------------

    /** Opens a modal over the page (replacing any open one). */
    public void open(Modal m) {
        if (modal != null) closeModal();
        modal = m;
        m.attach(this, ui, font);
        setFocused(null);
        for (EditBox b : m.fields()) b.setFocused(false);
        if (!m.fields().isEmpty()) {
            EditBox first = m.fields().getFirst();
            setFocused(first);
            first.moveCursorToEnd(false);
        }
    }

    /** Closes the open modal, if any (its drafts stay). */
    public void closeModal() {
        if (modal == null) return;
        Modal m = modal;
        modal = null;
        for (EditBox b : m.fields()) removeWidget(b);
        setFocused(null);
        m.onClose();
    }

    public Modal modal() {
        return modal;
    }

    /** For the preview harness: opens the modal behind the {@code index}-th modal button of the page's action bar once it is drawn. */
    public void previewModal(int index) {
        pendingModal = index;
    }

    private void runAction(Modal.Action a) {
        if (a.denied() != null || a.run() == null) return;
        Modal m = modal;
        a.run().run();
        if (a.closes() && modal == m) {
            for (String k : m.draftKeys()) DRAFTS.remove(k);
            closeModal();
        }
    }

    // --- drawing -----------------------------------------------------------------------------------------------------------

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
        g.fill(0, 0, width, height, Draw.DIM);
        boolean under = modal != null;
        ui.begin(under ? -1 : mouseX, under ? -1 : mouseY);
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
        if (under) for (EditBox f : fields) f.visible = false;
        if (pendingModal >= 0 && modal == null) {
            Modal m = tab.previewModal(pendingModal);
            if (m != null) {
                pendingModal = -1;
                open(m);
            }
        }
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
        PoliticsView v = ClientPolitics.view();
        if (modal != null && (v == null || !modal.valid(v))) closeModal();
        if (modal != null) {
            var pose = g.pose();
            pose.pushPose();
            pose.translate(0, 0, 300);
            ui.layer(mouseX, mouseY);
            g.fill(0, 0, width, height, 0x9A06080B);
            drawModal(g, v, mouseX, mouseY, partial);
            pose.popPose();
        }
        ui.tooltip(g);
    }

    /** The open modal: its slab, title bar, body, own text fields and footer. */
    private void drawModal(GuiGraphics g, PoliticsView v, int mouseX, int mouseY, float partial) {
        Modal m = modal;
        int mw = Math.min(m.width(), width - 16), mh = Math.min(m.height(), height - 16);
        int mx = (width - mw) / 2, my = (height - mh) / 2;
        Draw.shadow(g, mx, my, mw, mh, 14);
        Draw.slab(g, mx, my, mw, mh, 4, 0xFF242C38, 0xFF181D25);
        Draw.frame(g, mx, my, mw, mh, 0xFF0B0E12);
        g.fillGradient(mx + 1, my + 1, mx + mw - 1, my + Modal.TITLE, 0xFF2E3746, 0xFF222934);
        g.fill(mx + 1, my + Modal.TITLE, mx + mw - 1, my + Modal.TITLE + 1, 0x80E3B556);
        Draw.icon(g, m.icon(), mx + 4, my + 2);
        g.drawString(font, Draw.fit(font, m.title(), mw - 44), mx + 23, my + 6, Draw.GOLD, true);
        ui.button(g, mx + mw - 17, my + 4, 13, 12, Component.literal("✕"), Ui.Style.NORMAL, null, this::closeModal);
        for (EditBox b : m.fields()) b.visible = false;
        m.bind(mx + 8, my + Modal.TITLE + 6, mw - 16, mh - Modal.TITLE - Modal.FOOTER - 8);
        m.render(g, v, partial);
        if (modal != m) return;
        for (EditBox b : m.fields()) if (b.visible) b.render(g, mouseX, mouseY, partial);
        int fy = my + mh - Modal.FOOTER;
        g.fill(mx + 1, fy, mx + mw - 1, my + mh - 1, 0x40000000);
        g.fill(mx + 1, fy, mx + mw - 1, fy + 1, 0x30FFFFFF);
        List<Modal.Action> actions = m.actions(v);
        boolean closing = false;
        for (Modal.Action a : actions) closing |= a.closes();
        Component cancel = Component.translatable("nationsandwar.gui." + (closing ? "cancel" : "close"));
        ui.button(g, mx + 6, fy + 5, Math.max(48, font.width(cancel) + 12), 14, cancel, Ui.Style.NORMAL, null, this::closeModal);
        int bx = mx + mw - 6;
        for (int i = actions.size() - 1; i >= 0; i--) {
            Modal.Action a = actions.get(i);
            int bw = Math.max(56, font.width(a.label()) + 14);
            bx -= bw;
            ui.button(g, bx, fy + 5, bw, 14, a.label(), a.style(), a.denied(), () -> runAction(a));
            if (i == actions.size() - 1 && a.denied() == null) Draw.frame(g, bx - 1, fy + 4, bw + 2, 16, Draw.alpha(Draw.GOLD, 0x60));
            bx -= 4;
        }
    }

    private boolean insideModal(double x, double y) {
        int mw = Math.min(modal.width(), width - 16), mh = Math.min(modal.height(), height - 16);
        int mx = (width - mw) / 2, my = (height - mh) / 2;
        return x >= mx && x < mx + mw + 4 && y >= my - 4 && y < my + mh;
    }

    // --- input -------------------------------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (modal != null) {
            if (super.mouseClicked(mx, my, button)) return true;
            setFocused(null);
            if (button == 0 && ui.click(mx, my)) return true;
            if (!insideModal(mx, my)) closeModal();
            return true;
        }
        if (super.mouseClicked(mx, my, button)) return true;
        setFocused(null);
        if (button == 0 && ui.click(mx, my)) return true;
        return ClientPolitics.view() != null && tab.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (modal != null) return super.mouseDragged(mx, my, button, dx, dy);
        return tab.mouseDragged(mx, my, button, dx, dy) || super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (modal != null) return super.mouseReleased(mx, my, button);
        return tab.mouseReleased(mx, my, button) || super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (modal != null) return ui.scroll(mx, my, sy) || true;
        return ui.scroll(mx, my, sy) || tab.mouseScrolled(mx, my, sy);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (modal != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                closeModal();
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                PoliticsView v = ClientPolitics.view();
                List<Modal.Action> actions = v == null ? List.of() : modal.actions(v);
                if (!actions.isEmpty()) runAction(actions.getLast());
                return true;
            }
        }
        if (!(getFocused() instanceof EditBox) && NWClient.OPEN_GUI.matches(key, scan)) {
            if (modal != null) closeModal();
            else onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void removed() {
        if (modal != null) closeModal();
        ClientPolitics.unlisten(listener);
        ClientPolitics.closeView();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
