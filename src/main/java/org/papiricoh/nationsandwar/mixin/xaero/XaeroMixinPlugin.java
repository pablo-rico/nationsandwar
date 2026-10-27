package org.papiricoh.nationsandwar.mixin.xaero;

import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Applies the Xaero mixins only when their mod is there (the World Map's names and highlights; the minimap's only without the World Map). */
public final class XaeroMixinPlugin implements IMixinConfigPlugin {
    private static boolean loaded(String id) {
        LoadingModList list = LoadingModList.get();
        return list != null && list.getModFileById(id) != null;
    }

    @Override
    public boolean shouldApplyMixin(String target, String mixin) {
        if (mixin.endsWith("WorldMapRegistryMixin") || mixin.endsWith("WorldMapLabelsMixin")) return loaded("xaeroworldmap");
        if (mixin.endsWith("MinimapRegistryMixin")) return loaded("xaerominimap") && !loaded("xaeroworldmap");
        return true;
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
