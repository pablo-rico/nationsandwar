package org.papiricoh.nationsandwar.client;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.network.PacketDistributor;
import org.papiricoh.nationsandwar.client.notice.NoticeHud;
import org.papiricoh.nationsandwar.client.screen.EnvoyScreen;
import org.papiricoh.nationsandwar.client.screen.NationScreen;
import org.papiricoh.nationsandwar.network.ActionPayload;
import org.papiricoh.nationsandwar.network.NoticePayload;
import org.papiricoh.nationsandwar.network.OpenScreenPayload;
import org.papiricoh.nationsandwar.network.ViewPayload;
import org.papiricoh.nationsandwar.view.PoliticsView;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The client's side of the management GUI: the latest {@link PoliticsView}, sending actions, and opening screens. A screen
 * calls {@link #requestView()} when it opens and {@link #closeView()} when it closes; while open, the server pushes a fresh
 * view after every change, heard through {@link #listen}.
 */
public final class ClientPolitics {
    private static PoliticsView view;
    private static final List<Consumer<PoliticsView>> LISTENERS = new CopyOnWriteArrayList<>();

    private ClientPolitics() {
    }

    public static PoliticsView view() {
        return view;
    }

    public static void view(ViewPayload p) {
        view = PoliticsView.read(p.view());
        for (var l : LISTENERS) l.accept(view);
    }

    public static void listen(Consumer<PoliticsView> l) {
        LISTENERS.add(l);
    }

    public static void unlisten(Consumer<PoliticsView> l) {
        LISTENERS.remove(l);
    }

    public static void notice(NoticePayload p) {
        NoticeHud.push(p.kind(), p.title(), p.subtitle(), p.color(), p.icon());
    }

    public static void open(OpenScreenPayload p) {
        Minecraft mc = Minecraft.getInstance();
        switch (p.screen()) {
            case "envoy" -> mc.setScreen(new EnvoyScreen(UUID.fromString(p.args().getString("nation")), p.args().getInt("entity")));
            default -> mc.setScreen(new NationScreen(p.args().getString("tab")));
        }
    }

    /** Sends a political action (see docs/CONTRACT.md for names and arguments). */
    public static void act(String action, CompoundTag args) {
        PacketDistributor.sendToServer(new ActionPayload(action, args == null ? new CompoundTag() : args));
    }

    public static void requestView() {
        act("view", null);
    }

    public static void closeView() {
        act("close", null);
    }

    public static void clear() {
        view = null;
    }
}
