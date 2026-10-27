package org.papiricoh.nationsandwar.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.papiricoh.nationsandwar.NationsAndWar;
import org.papiricoh.nationsandwar.npc.EnvoyEntity;
import org.papiricoh.nationsandwar.npc.GuardEntity;
import org.papiricoh.nationsandwar.war.SiegeBannerBlock;
import org.papiricoh.nationsandwar.war.TownCoreBlock;

/** Every registry entry of the mod. */
public final class NWRegistry {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(NationsAndWar.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NationsAndWar.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, NationsAndWar.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, NationsAndWar.MODID);

    /** A town's heart: placed when the town is founded, broken only by besiegers once its home chunk has fallen. */
    public static final DeferredBlock<TownCoreBlock> TOWN_CORE = BLOCKS.register("town_core", () -> new TownCoreBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.GOLD).strength(25f, 1200f).sound(SoundType.LODESTONE).lightLevel(s -> 10).pushReaction(PushReaction.BLOCK)
            .requiresCorrectToolForDrops().noLootTable()));
    /** Planted in an enemy town's chunk during a war to start a siege. */
    public static final DeferredBlock<SiegeBannerBlock> SIEGE_BANNER = BLOCKS.register("siege_banner", () -> new SiegeBannerBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_RED).strength(2f, 6f).sound(SoundType.WOOD).noOcclusion().pushReaction(PushReaction.BLOCK)));

    public static final DeferredItem<BlockItem> TOWN_CORE_ITEM = ITEMS.register("town_core",
            () -> new BlockItem(TOWN_CORE.get(), new Item.Properties().rarity(Rarity.EPIC).stacksTo(1)));
    public static final DeferredItem<BlockItem> SIEGE_BANNER_ITEM = ITEMS.register("siege_banner",
            () -> new BlockItem(SIEGE_BANNER.get(), new Item.Properties().rarity(Rarity.UNCOMMON).stacksTo(4)));

    public static final DeferredHolder<EntityType<?>, EntityType<GuardEntity>> GUARD = ENTITIES.register("guard",
            () -> EntityType.Builder.of(GuardEntity::new, MobCategory.MISC).sized(0.6f, 1.95f).clientTrackingRange(10).build("guard"));
    public static final DeferredHolder<EntityType<?>, EntityType<EnvoyEntity>> ENVOY = ENTITIES.register("envoy",
            () -> EntityType.Builder.of(EnvoyEntity::new, MobCategory.MISC).sized(0.6f, 1.95f).clientTrackingRange(10).build("envoy"));

    public static final DeferredItem<Item> GUARD_SPAWN_EGG = ITEMS.register("guard_spawn_egg",
            () -> new net.neoforged.neoforge.common.DeferredSpawnEggItem(GUARD, 0x3B4A6B, 0xC9A227, new Item.Properties()));
    public static final DeferredItem<Item> ENVOY_SPAWN_EGG = ITEMS.register("envoy_spawn_egg",
            () -> new net.neoforged.neoforge.common.DeferredSpawnEggItem(ENVOY, 0x6B2E3B, 0xE8D9A8, new Item.Properties()));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.nationsandwar"))
            .icon(() -> SIEGE_BANNER_ITEM.get().getDefaultInstance())
            .displayItems((params, out) -> {
                out.accept(SIEGE_BANNER_ITEM.get());
                out.accept(TOWN_CORE_ITEM.get());
                out.accept(GUARD_SPAWN_EGG.get());
                out.accept(ENVOY_SPAWN_EGG.get());
            }).build());

    private NWRegistry() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        TABS.register(modBus);
    }

    public static void attributes(EntityAttributeCreationEvent event) {
        event.put(GUARD.get(), GuardEntity.createAttributes().build());
        event.put(ENVOY.get(), EnvoyEntity.createAttributes().build());
    }
}
