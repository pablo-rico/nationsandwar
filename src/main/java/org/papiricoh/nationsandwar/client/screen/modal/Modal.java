package org.papiricoh.nationsandwar.client.screen.modal;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.papiricoh.nationsandwar.client.gui.Draw;
import org.papiricoh.nationsandwar.client.gui.Ui;
import org.papiricoh.nationsandwar.client.screen.NationScreen;
import org.papiricoh.nationsandwar.client.screen.tab.Tab;
import org.papiricoh.nationsandwar.view.PoliticsView;

import java.util.ArrayList;
import java.util.List;

/**
 * A dialog drawn over the management GUI ({@link NationScreen#open}): a raised slab with a title bar (icon, title, ✕), a
 * body the subclass draws every frame from the latest view inside ({@link #x}, {@link #y}, {@link #w}, {@link #h}), and a
 * footer with Cancel and the modal's {@link #actions} — the last one is the primary action, run by Enter. Text fields are
 * made in {@link #init} with {@link #field} and placed every frame with {@link #place}; what they hold is kept as a draft
 * until an action that closes the modal runs.
 */
public abstract class Modal {
    protected static final String K = "nationsandwar.gui.";
    /** The height of the title bar and of the footer. */
    public static final int TITLE = 20, FOOTER = 24;

    /**
     * A footer button. {@code denied} non-null greys it out (and is its tooltip); {@code closes}: the modal closes after
     * {@code run}.
     */
    public record Action(Component label, Ui.Style style, Component denied, Runnable run, boolean closes) {
    }

    private final Component title;
    private final Draw.Icon icon;
    private final int width, height;
    private final List<EditBox> fields = new ArrayList<>();
    private final List<String> draftKeys = new ArrayList<>();
    protected NationScreen screen;
    protected Ui ui;
    protected Font font;
    protected int x, y, w, h;

    protected Modal(Component title, Draw.Icon icon, int width, int height) {
        this.title = title;
        this.icon = icon;
        this.width = width;
        this.height = height;
    }

    public Component title() {
        return title;
    }

    public Draw.Icon icon() {
        return icon;
    }

    /** The whole modal's size, title bar and footer included. */
    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /** Called by the screen when the modal opens. */
    public final void attach(NationScreen screen, Ui ui, Font font) {
        this.screen = screen;
        this.ui = ui;
        this.font = font;
        if (fields.isEmpty()) init();
    }

    /** The body rectangle for this frame (the screen lays the modal out). */
    public final void bind(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public List<EditBox> fields() {
        return fields;
    }

    /** The draft keys of the fields, forgotten once an action closes the modal. */
    public List<String> draftKeys() {
        return draftKeys;
    }

    /** Makes the text fields. */
    protected void init() {
    }

    /** Whether the modal still makes sense with this view (its rank, province or war still exists); else it closes. */
    public boolean valid(PoliticsView v) {
        return true;
    }

    /** Draws the body. */
    public abstract void render(GuiGraphics g, PoliticsView v, float partial);

    /** The footer buttons, left to right; the last is the primary action (Enter). Empty: only Close. */
    public List<Action> actions(PoliticsView v) {
        return List.of();
    }

    /** Called once the modal has closed, whatever closed it. */
    public void onClose() {
    }

    // --- helpers -----------------------------------------------------------------------------------------------------------

    /** A text field whose text is kept under {@code key} until an action closes the modal; {@code initial} when there is no draft. */
    protected EditBox field(String key, int maxLength, Component hint, boolean digits, String initial) {
        EditBox b = screen.modalField("modal." + key, maxLength, hint, digits, initial);
        fields.add(b);
        draftKeys.add("modal." + key);
        return b;
    }

    protected void place(EditBox box, int fx, int fy, int fw) {
        box.setX(fx);
        box.setY(fy);
        box.setWidth(fw);
        box.visible = true;
    }

    protected static int number(EditBox box) {
        try {
            return Integer.parseInt(box.getValue().trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    protected static MutableComponent tr(String key, Object... args) {
        return Component.translatable(K + key, args);
    }

    protected static void act(String action, Object... kv) {
        Tab.act(action, kv);
    }

    protected static Action action(Component label, Ui.Style style, Component denied, Runnable run) {
        return new Action(label, style, denied, run, true);
    }

    /** Wrapped muted text; returns the y under it. */
    protected int text(GuiGraphics g, Component text, int tx, int ty, int tw, int color) {
        for (var line : font.split(text, tw)) {
            g.drawString(font, line, tx, ty, color, false);
            ty += 10;
        }
        return ty;
    }

    /** A small label over a control. */
    protected void label(GuiGraphics g, Component text, int lx, int ly) {
        Draw.text(g, font, text, lx, ly, 0.75f, Draw.MUTED, false);
    }
}
