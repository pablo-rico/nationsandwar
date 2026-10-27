package org.papiricoh.nationsandwar.api.client;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.papiricoh.nationsandwar.view.Briefs;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * How the management GUI shows a nation another mod runs: instead of its tabs, an empty card with the handler's title and
 * text and one button that hands the player over to the other mod (e.g. a book that shows the nation's own hierarchy).
 * Client side only: register from client setup.
 */
public final class ManagedNationScreens {
    /** One provider's card. Every method but {@link #open} has a default. */
    public interface Handler {
        /** The card's title (default: the nation's name). */
        default Component title(Briefs.NationBrief nation) {
            return Component.literal(nation.name());
        }

        /** The text under it: who runs the nation and where to see it. */
        default Component description(Briefs.NationBrief nation) {
            return Component.translatable("nationsandwar.managed.description");
        }

        /** The button's label. */
        default Component button(Briefs.NationBrief nation) {
            return Component.translatable("nationsandwar.managed.open");
        }

        /** Whether the button is enabled for {@code player} (e.g. they carry the item that shows the nation). */
        default boolean canOpen(Player player, Briefs.NationBrief nation) {
            return true;
        }

        /** The tooltip of a disabled button: why it cannot be opened. */
        default Component cannotOpen(Briefs.NationBrief nation) {
            return Component.translatable("nationsandwar.managed.cannot_open");
        }

        /** The button was pressed (the management screen is still open: close it or replace it). */
        void open(Player player, Briefs.NationBrief nation);
    }

    private static final Map<String, Handler> HANDLERS = new ConcurrentHashMap<>();

    private ManagedNationScreens() {
    }

    /** {@code key}: the provider's key ({@code Nation.managedBy}). */
    public static void register(String key, Handler handler) {
        HANDLERS.put(key, handler);
    }

    /** The handler of a managed nation's key, or null (the card then shows only the name and a generic text). */
    public static Handler handler(String key) {
        return key == null ? null : HANDLERS.get(key);
    }
}
