package org.papiricoh.nationsandwar.client.render;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;
import org.papiricoh.nationsandwar.NationsAndWar;
import org.papiricoh.nationsandwar.npc.EnvoyEntity;

/** An envoy: robed, with its nation's tabard. */
public final class EnvoyRenderer extends HumanoidMobRenderer<EnvoyEntity, PlayerModel<EnvoyEntity>> {
    private static final ResourceLocation SKIN = NationsAndWar.id("textures/entity/envoy.png");

    public EnvoyRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
        addLayer(new TabardLayer<>(this, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)), EnvoyEntity::color));
    }

    @Override
    public ResourceLocation getTextureLocation(EnvoyEntity entity) {
        return SKIN;
    }
}
