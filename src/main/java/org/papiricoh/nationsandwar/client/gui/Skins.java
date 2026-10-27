package org.papiricoh.nationsandwar.client.gui;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import org.papiricoh.nationsandwar.NationsAndWar;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * People's skins for the GUI and small 3D busts drawn from them. An online player's skin comes from their tab-list entry;
 * anyone else's is looked up once by name through the game's profile cache and {@code SkinManager} (falling back to the
 * default skin for their UUID while it loads, or for good when nothing is found).
 */
public final class Skins {
    private static final Map<UUID, PlayerSkin> RESOLVED = new HashMap<>();
    private static final Map<UUID, CompletableFuture<PlayerSkin>> PENDING = new HashMap<>();
    private static PlayerModel<?> wide, slim;
    private static HumanoidModel<?> armor;
    /** The guards' skins (player layout) and the white tabard (armour layout) that court characters' busts wear. */
    private static final ResourceLocation[] GUARDS = new ResourceLocation[4];
    private static final ResourceLocation TABARD = NationsAndWar.id("textures/entity/tabard.png");

    static {
        for (int i = 0; i < GUARDS.length; i++) GUARDS[i] = NationsAndWar.id("textures/entity/guard/guard_" + i + ".png");
    }

    private Skins() {
    }

    public static PlayerSkin skin(UUID id, String name) {
        Minecraft mc = Minecraft.getInstance();
        PlayerInfo info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(id);
        if (info != null) return info.getSkin();
        PlayerSkin done = RESOLVED.get(id);
        if (done != null) return done;
        CompletableFuture<PlayerSkin> f = PENDING.get(id);
        if (f == null) PENDING.put(id, f = lookup(id, name));
        if (f.isDone()) {
            PlayerSkin s = f.getNow(null);
            RESOLVED.put(id, s != null ? s : DefaultPlayerSkin.get(id));
            PENDING.remove(id);
            return RESOLVED.get(id);
        }
        return DefaultPlayerSkin.get(id);
    }

    private static CompletableFuture<PlayerSkin> lookup(UUID id, String name) {
        try {
            var profile = name == null || name.isEmpty() ? SkullBlockEntity.fetchGameProfile(id) : SkullBlockEntity.fetchGameProfile(name);
            return profile.thenCompose(p -> p.isPresent() ? Minecraft.getInstance().getSkinManager().getOrLoad(p.get())
                    : CompletableFuture.completedFuture((PlayerSkin) null)).exceptionally(e -> null);
        } catch (RuntimeException e) {
            return CompletableFuture.completedFuture(null);
        }
    }

    private static PlayerModel<?> model(boolean slimArms) {
        if (wide == null) {
            var models = Minecraft.getInstance().getEntityModels();
            wide = new PlayerModel<>(models.bakeLayer(ModelLayers.PLAYER), false);
            slim = new PlayerModel<>(models.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        }
        return slimArms ? slim : wide;
    }

    /**
     * A 3D bust (head, torso and arms with their outer layers) whose neck sits at (x, y), {@code size} GUI units across
     * the shoulders, turned by {@code yaw} degrees and seen a little from above. {@code z} lifts it over what is drawn
     * at the same spot.
     */
    public static void bust(GuiGraphics g, UUID id, String name, float x, float y, float size, float yaw, float z) {
        PlayerSkin skin = skin(id, name);
        PlayerModel<?> m = model(skin.model() == PlayerSkin.Model.SLIM);
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, z);
        float s = size;
        pose.scale(s, s, -s);
        pose.mulPose(Axis.XP.rotationDegrees(-14));
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        Lighting.setupForEntityInInventory();
        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.entityTranslucent(skin.texture()));
        for (ModelPart p : new ModelPart[]{m.head, m.hat, m.body, m.jacket, m.leftArm, m.rightArm, m.leftSleeve, m.rightSleeve})
            p.render(pose, vc, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        g.flush();
        Lighting.setupFor3DItems();
        pose.popPose();
    }

    /**
     * The bust of a court character (an NPC, see {@code model.Persona}): a guard skin ({@code skin} 0..3) under its tabard
     * tinted in {@code tint}, the nation's or town's colour. Never looks a profile up: the name could be a real player's.
     */
    public static void npcBust(GuiGraphics g, int skin, int tint, float x, float y, float size, float yaw, float z) {
        PlayerModel<?> m = model(false);
        if (armor == null) armor = new HumanoidModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER_INNER_ARMOR));
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, z);
        pose.scale(size, size, -size);
        pose.mulPose(Axis.XP.rotationDegrees(-14));
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        Lighting.setupForEntityInInventory();
        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.entityTranslucent(GUARDS[Math.floorMod(skin, GUARDS.length)]));
        for (ModelPart p : new ModelPart[]{m.head, m.hat, m.body, m.jacket, m.leftArm, m.rightArm, m.leftSleeve, m.rightSleeve})
            p.render(pose, vc, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        VertexConsumer tabard = g.bufferSource().getBuffer(RenderType.entityCutoutNoCull(TABARD));
        int color = 0xFF000000 | tint;
        for (ModelPart p : new ModelPart[]{armor.body, armor.leftArm, armor.rightArm})
            p.render(pose, tabard, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color);
        g.flush();
        Lighting.setupFor3DItems();
        pose.popPose();
    }

    /** A flat face (with hat layer) for dense lists. */
    public static void face(GuiGraphics g, UUID id, String name, int x, int y, int size) {
        net.minecraft.client.gui.components.PlayerFaceRenderer.draw(g, skin(id, name), x, y, size);
    }
}
