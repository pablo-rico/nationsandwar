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
import org.papiricoh.nationsandwar.network.TreePayload;
import org.papiricoh.nationsandwar.network.ViewPayload;
import org.papiricoh.nationsandwar.view.PoliticsView;
import org.papiricoh.nationsandwar.view.TreeView;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The client's side of the management GUI: the latest {@link PoliticsView}, sending actions, and opening screens. A screen
 * calls {@link #requestView()} when it opens and {@link #closeView()} when it closes; while open, the server pushes a fresh
 * view after every change, heard through {@link #listen}. The Atlas asks for any town's or nation's rank tree with
 * {@link #requestTree}; the answer lands in {@link #tree()} (heard through {@link #listenTree}) and is asked again with every
 * fresh view, so an open tree stays current.
 */
public final class ClientPolitics {
    private static PoliticsView view;
    private static final List<Consumer<PoliticsView>> LISTENERS = new CopyOnWriteArrayList<>();
    private static TreeView tree;
    private static String treeScope;
    private static UUID treeId;
    private static final List<Consumer<TreeView>> TREE_LISTENERS = new CopyOnWriteArrayList<>();

    private ClientPolitics() {
    }

    public static PoliticsView view() {
        return view;
    }

    public static void view(ViewPayload p) {
        view = PoliticsView.read(p.view());
        for (var l : LISTENERS) l.accept(view);
        if (treeScope != null) sendInspect();
    }

    /** The last tree asked for with {@link #requestTree}, once it arrived (null before). */
    public static TreeView tree() {
        return tree != null && tree.id().equals(treeId) && tree.scope().equals(treeScope) ? tree : null;
    }

    public static void tree(TreePayload p) {
        tree = TreeView.read(p.tree());
        for (var l : TREE_LISTENERS) l.accept(tree);
    }

    /** Asks the server for the rank tree of a town ({@code scope} "town") or a nation ("nation"). */
    public static void requestTree(String scope, UUID id) {
        if (scope.equals(treeScope) && id.equals(treeId)) return;
        treeScope = scope;
        treeId = id;
        sendInspect();
    }

    private static void sendInspect() {
        CompoundTag a = new CompoundTag();
        a.putString("scope", treeScope);
        a.putString("id", treeId.toString());
        act("inspect", a);
    }

    public static void listenTree(Consumer<TreeView> l) {
        TREE_LISTENERS.add(l);
    }

    public static void unlistenTree(Consumer<TreeView> l) {
        TREE_LISTENERS.remove(l);
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
        treeScope = null;
        treeId = null;
    }

    public static void clear() {
        view = null;
        tree = null;
        treeScope = null;
        treeId = null;
    }
}
