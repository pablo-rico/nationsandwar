package org.papiricoh.nationsandwar.client.render;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import org.papiricoh.nationsandwar.NationsAndWar;
import org.papiricoh.nationsandwar.npc.GuardEntity;

/** A guard: a player-shaped body with one of {@link GuardEntity#SKINS} skins, armour, and its nation's tabard. */
public final class GuardRenderer extends HumanoidMobRenderer<GuardEntity, PlayerModel<GuardEntity>> {
    private static final ResourceLocation[] SKINS = new ResourceLocation[GuardEntity.SKINS];

    static {
        for (int i = 0; i < SKINS.length; i++) SKINS[i] = NationsAndWar.id("textures/entity/guard/guard_" + i + ".png");
    }

    public GuardRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
        addLayer(new HumanoidArmorLayer<>(this, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)), ctx.getModelManager()));
        addLayer(new TabardLayer<>(this, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)), GuardEntity::color));
    }

    @Override
    public ResourceLocation getTextureLocation(GuardEntity entity) {
        return SKINS[Math.floorMod(entity.skin(), SKINS.length)];
    }
}
