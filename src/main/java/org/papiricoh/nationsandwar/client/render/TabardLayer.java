package org.papiricoh.nationsandwar.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import org.papiricoh.nationsandwar.NationsAndWar;

import java.util.function.ToIntFunction;

/** A tabard over the body in the wearer's nation colour (a white armour-layout texture, tinted). */
public final class TabardLayer<T extends Mob, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
    private static final ResourceLocation TEXTURE = NationsAndWar.id("textures/entity/tabard.png");
    private final HumanoidModel<T> model;
    private final ToIntFunction<T> color;

    public TabardLayer(RenderLayerParent<T, M> parent, HumanoidModel<T> model, ToIntFunction<T> color) {
        super(parent);
        this.model = model;
        this.color = color;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity, float limbSwing, float limbSwingAmount, float partialTick,
                       float ageInTicks, float netHeadYaw, float headPitch) {
        if (entity.isInvisible()) return;
        getParentModel().copyPropertiesTo(model);
        model.setAllVisible(false);
        model.body.visible = model.leftArm.visible = model.rightArm.visible = model.rightLeg.visible = model.leftLeg.visible = true;
        renderColoredCutoutModel(model, TEXTURE, pose, buffers, light, entity, 0xFF000000 | color.applyAsInt(entity));
    }
}
