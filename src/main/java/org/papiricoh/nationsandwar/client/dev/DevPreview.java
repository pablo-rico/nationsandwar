package org.papiricoh.nationsandwar.client.dev;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.papiricoh.nationsandwar.NationsAndWar;
import org.papiricoh.nationsandwar.api.client.ManagedNationScreens;
import org.papiricoh.nationsandwar.client.ClientPolitics;
import org.papiricoh.nationsandwar.client.notice.NoticeHud;
import org.papiricoh.nationsandwar.client.screen.NationScreen;
import org.papiricoh.nationsandwar.client.screen.tab.AtlasTab;
import org.papiricoh.nationsandwar.compat.MapLayer;
import org.papiricoh.nationsandwar.compat.MapStyle;
import org.papiricoh.nationsandwar.network.NoticePayload;
import org.papiricoh.nationsandwar.view.Briefs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Development aid, inert unless {@code NW_PREVIEW} is set: {@code NW_PREVIEW=gui,notices ./gradlew runClient -Ppreview}
 * (world {@code runs/client/saves/nw_preview}, e.g. a copy of {@code runs/gameTestServer/world}). Seeds sample politics
 * ({@link PreviewSample}), plays each named scene, saves {@code runs/client/screenshots/nw_<scene>.png} and quits.
 * Scenes are registered in {@link #SCENES}; add your own with {@link #scene}. {@code gui:<tab>} opens the management GUI on a
 * tab ({@code gui:<tab>.modal}: with its first modal open; {@code gui:atlas}: the Atlas on the allied NPC nation); {@code envoy} talks to the enemy NPC nation's envoy; {@code treaty} ends the sample war in Valoria's favour (it changes
 * the world, so play it last); {@code notices} shows one notice of each kind plus a territory change and {@code siegehud}
 * the siege panel by the sample siege. {@code layer:<id>} opens the map tab on a map layer (nations, provinces, towns,
 * alliances); {@code jm:<id>} / {@code xaero:<id>} open JourneyMap's / Xaero's full-screen map on it (with -Pjm / -Pxaero).
 */
@EventBusSubscriber(modid = NationsAndWar.MODID, value = Dist.CLIENT)
public final class DevPreview {
    /**
     * A scene: {@code server} runs on the server thread first (the local player), then {@code screen} opens (null keeps
     * the world view, e.g. for HUD notices), {@code client} runs, and after {@code settle} ticks the screenshot is taken.
     */
    public record Scene(Consumer<ServerPlayer> server, Supplier<Screen> screen, Runnable client, int settle, boolean hud) {
    }

    public static final Map<String, Scene> SCENES = new LinkedHashMap<>();

    static {
        scene("gui", new Scene(p -> {
            if (arg().equals("managedmap")) PreviewSample.toManagedView(p);
        }, () -> new NationScreen(switch (arg()) {
            case "managed" -> "diplomacy";
            case "managedmap" -> "map";
            default -> arg().split("\\.")[0];
        }), DevPreview::guiPage, 60, false));
        scene("envoy", new Scene(PreviewSample::envoy, () -> null, () -> {
        }, 70, false));
        scene("treaty", new Scene(PreviewSample::treaty, () -> new NationScreen("war"), () -> {
        }, 60, false));
        scene("world", new Scene(p -> {
        }, () -> null, () -> {
        }, 60, true));
        scene("showcase", new Scene(PreviewSample::showcase, () -> null, () -> {
        }, 80, false));
        // the showcase with the HUD on, so name tags show (the envoy's: its court character and role)
        scene("nameplate", new Scene(PreviewSample::showcase, () -> null, () -> {
        }, 120, true));
        scene("notices", new Scene(PreviewSample::toAshdale, () -> null, DevPreview::notices, 30, true));
        scene("siegehud", new Scene(PreviewSample::toSiege, () -> null, NoticeHud::clear, 45, true));
        scene("layer", new Scene(DevPreview::alive, () -> new NationScreen("map"), () -> {
            pickLayer();
            var window = Minecraft.getInstance().getWindow();
            org.lwjgl.glfw.GLFW.glfwSetCursorPos(window.getWindow(), 4, 4);
        }, 60, false));
        scene("jm", new Scene(DevPreview::alive, () -> null, () -> {
            Minecraft.getInstance().setScreen(null);
            pickLayer();
            openJourneyMap();
        }, 100, false));
        scene("xaero", new Scene(DevPreview::alive, () -> null, () -> {
            Minecraft.getInstance().setScreen(null);
            pickLayer();
            pressKey("gui.xaero_open_map");
        }, 100, false));
    }

    /** Brings the preview player back if a night in survival killed it, and keeps it safe (creative) for the map scenes. */
    private static void alive(ServerPlayer p) {
        if (p.isDeadOrDying())
            p.connection.handleClientCommand(new net.minecraft.network.protocol.game.ServerboundClientCommandPacket(
                    net.minecraft.network.protocol.game.ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
        ServerPlayer now = p.server.getPlayerList().getPlayer(p.getUUID());
        if (now != null) now.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
    }

    /** {@code layer:<id>}, {@code jm:<id>}, {@code xaero:<id>}: the map layer to show (default nations). */
    private static void pickLayer() {
        for (MapLayer l : MapLayer.values()) if (l.id().equals(arg())) MapStyle.setLayer(l);
        if (arg().isEmpty()) MapStyle.setLayer(MapLayer.NATIONS);
    }

    /** JourneyMap's full-screen map (internal class, so by reflection; only with {@code -Pjm}). */
    private static void openJourneyMap() {
        try {
            Class<?> ui = Class.forName("journeymap.client.ui.UIManager");
            Object instance = ui.getField("INSTANCE").get(null);
            ui.getMethod("getOrOpenFullscreenMap").invoke(instance);
        } catch (ReflectiveOperationException e) {
            NationsAndWar.LOGGER.warn("[preview] cannot open JourneyMap", e);
        }
    }

    /** Presses a key mapping by name for one tick (e.g. Xaero's open-map key; only with {@code -Pxaero}). */
    private static void pressKey(String name) {
        for (var k : Minecraft.getInstance().options.keyMappings)
            if (k.getName().equals(name)) {
                net.minecraft.client.KeyMapping.click(k.getKey());
                return;
            }
        NationsAndWar.LOGGER.warn("[preview] no key mapping {}", name);
    }

    /** One notice of each server kind (the walk to Ashdale adds the territory one), on a cleared stack. */
    private static void notices() {
        NoticeHud.clear();
        NoticeHud.push(NoticePayload.Kind.WAR, Component.translatable("nationsandwar.notice.war_declared"),
                Component.translatable("nationsandwar.notice.vs", "Dravenholt", "Valoria"), 0xC8373B, "war");
        NoticeHud.push(NoticePayload.Kind.POLITICS, Component.translatable("nationsandwar.notice.governor"), Component.literal("North March"), 0xD9A43A, "crown");
        NoticeHud.push(NoticePayload.Kind.PERMISSION, Component.translatable("nationsandwar.notice.denied.build"), Component.literal("Calmere"), 0x3F9E4F, "shield");
    }

    /**
     * {@code gui:<tab>.modal} (or {@code gui:<tab>.<n>}) opens the first (the n-th) modal of that tab's action bar, so
     * {@code gui:roles.1} shows the permissions of my town's selected rank. {@code gui:atlas}: the Atlas on the allied NPC
     * nation. {@code gui:managed} / {@code gui:managedmap}: the diplomacy tab / map tab with the sample managed nation's card.
     */
    private static void guiPage() {
        if (!(Minecraft.getInstance().screen instanceof NationScreen s)) return;
        if (s.current() instanceof org.papiricoh.nationsandwar.client.screen.tab.DiplomacyTab d && arg().equals("managed")) d.pickManaged(PreviewSample.PIT);
        if (s.current() instanceof org.papiricoh.nationsandwar.client.screen.tab.MapTab m && arg().equals("managedmap")) m.pickManaged(PreviewSample.PIT);
        String[] parts = arg().split("\\.");
        if (parts[0].equals("atlas"))
            s.atlas().focusLater(v -> {
                for (var n : v.nations())
                    if ("ally".equals(n.relation()) && n.npc()) return new AtlasTab.Target("nation", n.id());
                return null;
            });
        if (parts.length > 1) s.previewModal(parts[1].equals("modal") ? 0 : Integer.parseInt(parts[1]));
    }

    /** The dev-only card handler of the sample managed nation: pressing its button closes the GUI and shows a toast. */
    private static void registerManagedCard() {
        ManagedNationScreens.register(PreviewSample.PIT, new ManagedNationScreens.Handler() {
            @Override
            public void open(Player player, Briefs.NationBrief nation) {
                Minecraft mc = Minecraft.getInstance();
                mc.setScreen(null);
                SystemToast.add(mc.getToasts(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION, Component.literal(nation.name()),
                        Component.literal("Preview: handed over to the managing mod"));
            }
        });
    }

    public static void scene(String name, Scene scene) {
        SCENES.put(name, scene);
    }

    private static final String RAW = System.getenv("NW_PREVIEW");
    private static List<String> queue;
    private static int warmup = 120, timer = -1, index;
    private static boolean seeded;
    private static String arg = "";

    /** The text after ':' in the current scene's name ({@code gui:roles} → "roles"), or "". */
    public static String arg() {
        return arg;
    }

    private DevPreview() {
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (RAW == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
        mc.options.pauseOnLostFocus = false;
        if (queue == null) {
            queue = new ArrayList<>();
            for (String s : RAW.split(",")) if (!s.isBlank()) queue.add(s.trim());
        }
        if (!seeded) {
            seeded = true;
            registerManagedCard();
            var server = mc.getSingleplayerServer();
            server.execute(() -> PreviewSample.seed(server.getPlayerList().getPlayers().getFirst()));
        }
        if (warmup-- > 0) return;
        if (index >= queue.size()) {
            mc.stop();
            return;
        }
        String name = queue.get(index);
        Scene scene = SCENES.get(name.contains(":") ? name.substring(0, name.indexOf(':')) : name);
        if (scene == null) {
            NationsAndWar.LOGGER.warn("[preview] unknown scene {}", name);
            index++;
            return;
        }
        if (timer < 0) {
            arg = name.contains(":") ? name.substring(name.indexOf(':') + 1) : "";
            timer = scene.settle();
            var server = mc.getSingleplayerServer();
            server.execute(() -> scene.server().accept(server.getPlayerList().getPlayers().getFirst()));
            ClientPolitics.requestView();
            mc.options.hideGui = !scene.hud();
            Screen s = scene.screen().get();
            mc.setScreen(s);
            scene.client().run();
            return;
        }
        if (--timer > 0) return;
        String file = "nw_" + name.replace(':', '_') + ".png";
        Screenshot.grab(mc.gameDirectory, file, mc.getMainRenderTarget(), msg -> NationsAndWar.LOGGER.info("[preview] {}", msg.getString()));
        timer = -1;
        index++;
    }
}
