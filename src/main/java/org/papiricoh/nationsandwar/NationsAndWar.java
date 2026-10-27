package org.papiricoh.nationsandwar;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.papiricoh.nationsandwar.network.NWNetwork;
import org.papiricoh.nationsandwar.registry.NWRegistry;
import org.slf4j.Logger;

@Mod(NationsAndWar.MODID)
public final class NationsAndWar {
    public static final String MODID = "nationsandwar";
    public static final Logger LOGGER = LogUtils.getLogger();

    public NationsAndWar(IEventBus modBus, ModContainer container) {
        NWRegistry.register(modBus);
        modBus.addListener(NWNetwork::register);
        modBus.addListener(NWRegistry::attributes);
        container.registerConfig(ModConfig.Type.SERVER, NWConfig.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, NWClientConfig.SPEC);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
