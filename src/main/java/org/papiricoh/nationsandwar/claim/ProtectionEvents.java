package org.papiricoh.nationsandwar.claim;

import org.papiricoh.nationsandwar.api.ProtectionBypass;
import org.papiricoh.nationsandwar.api.ProtectionBypass.Bypass;
import org.papiricoh.nationsandwar.api.WarApi;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.FireChargeItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
import org.papiricoh.nationsandwar.NationsAndWar;
import org.papiricoh.nationsandwar.core.NW;
import org.papiricoh.nationsandwar.core.Notices;
import org.papiricoh.nationsandwar.data.NWSavedData;
import org.papiricoh.nationsandwar.model.ChunkPerms;
import org.papiricoh.nationsandwar.model.Flag;
import org.papiricoh.nationsandwar.model.Nation;
import org.papiricoh.nationsandwar.model.Politics;
import org.papiricoh.nationsandwar.model.Result;
import org.papiricoh.nationsandwar.model.Town;
import org.papiricoh.nationsandwar.network.NoticePayload;
import org.papiricoh.nationsandwar.registry.NWRegistry;
import org.papiricoh.nationsandwar.war.War;
import org.papiricoh.nationsandwar.war.WarService;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps towns safe: who may build, break, use and open in each chunk ({@link ChunkPerms} by group), the flags (PvP, mobs,
 * fire, explosions), pistons across borders. In a war zone both sides may build and break; every such change is logged
 * for restoration and broken blocks drop nothing. Siege Banners and Town Cores follow the war's rules.
 */
@EventBusSubscriber(modid = NationsAndWar.MODID)
public final class ProtectionEvents {
    // Another mod's zones (boss arenas) switch protections off: ProtectionBypass.bypassed is asked first in every handler,
    // before the war allowance, so nothing done inside a zone goes in a war's restore log.

    private static final Map<UUID, Long> LAST_WARNING = new HashMap<>();

    private ProtectionEvents() {
    }

    /** Whether {@code player} may do {@code action} at {@code pos} (no war allowance). Wilderness is free. */
    public static boolean allowed(Player player, Level level, BlockPos pos, ChunkPerms.Action action) {
        if (level.isClientSide || NW.BYPASS.contains(player.getUUID())) return true;
        Town town = NW.townAt(level, pos);
        if (town == null) return true;
        Politics pol = NW.pol(level.getServer());
        return town.perms(NW.chunk(pos)).allows(pol.group(player.getUUID(), town), action);
    }

    private static void deny(Player player, Town town, ChunkPerms.Action action) {
        if (!(player instanceof ServerPlayer sp)) return;
        long now = sp.level().getGameTime();
        Long last = LAST_WARNING.get(sp.getUUID());
        if (last != null && now - last < 60) return;
        LAST_WARNING.put(sp.getUUID(), now);
        Notices.send(sp, NoticePayload.Kind.PERMISSION, Component.translatable("nationsandwar.notice.denied." + action.name().toLowerCase()),
                Component.literal(town.name), town.color, "shield");
    }

    // --- blocks ------------------------------------------------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void breakBlock(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Player player = event.getPlayer();
        BlockPos pos = event.getPos();
        BlockState state = event.getState();
        if (state.is(NWRegistry.TOWN_CORE.get())) {
            if (player instanceof ServerPlayer sp && WarService.hitCore(level, pos, sp)) {
                event.setCanceled(true);
                return;
            }
            Town owner = NW.townAt(level, pos);
            if (owner != null && owner.core == pos.asLong() && !NW.BYPASS.contains(player.getUUID())) {
                event.setCanceled(true);
                return;
            }
        }
        Town town = NW.townAt(level, pos);
        if (town == null || ProtectionBypass.bypassed(level, pos, Bypass.DESTROY)) return;
        if (state.is(NWRegistry.SIEGE_BANNER.get()) && WarService.fightsHere(level.getServer(), player.getUUID(), town, NW.chunk(pos)) != null) {
            WarService.bannerBroken(level, pos);
            return;
        }
        if (warAllows(level, player, town, pos, state)) return;
        if (!allowed(player, level, pos, ChunkPerms.Action.DESTROY)) {
            event.setCanceled(true);
            deny(player, town, ChunkPerms.Action.DESTROY);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void place(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = event.getPos();
        Entity entity = event.getEntity();
        if (event.getPlacedBlock().is(NWRegistry.SIEGE_BANNER.get())) {
            if (!(entity instanceof ServerPlayer sp)) return;
            Result r = WarService.plantBanner(level, pos, bannerNation(level, pos, sp), false);
            if (!r.ok()) event.setCanceled(true);
            NW.reply(sp, r);
            if (r.ok()) logChange(level, pos, event.getBlockSnapshot().getState());
            return;
        }
        Town town = NW.townAt(level, pos);
        if (town == null || ProtectionBypass.bypassed(level, pos, Bypass.BUILD)) return;
        if (!(entity instanceof Player player)) {
            if (entity != null && !(entity instanceof net.minecraft.world.entity.npc.Villager)) event.setCanceled(true);
            return;
        }
        if (warAllows(level, player, town, pos, event.getBlockSnapshot().getState())) return;
        if (!allowed(player, level, pos, ChunkPerms.Action.BUILD)) {
            event.setCanceled(true);
            deny(player, town, ChunkPerms.Action.BUILD);
        }
    }

    /**
     * The nation a player plants a banner for: their own when it is at war with the town's, else the one the API's
     * combatant resolvers name (a player serving a managed nation).
     */
    private static UUID bannerNation(ServerLevel level, BlockPos pos, ServerPlayer sp) {
        var data = NW.data(level.getServer());
        Nation n = data.politics.nationOfPlayer(sp.getUUID());
        Town town = NW.townAt(level, pos);
        if (n != null && (town == null || town.nation == null || WarService.activeWar(data, n.id, town.nation) != null)) return n.id;
        UUID other = WarApi.combatantNation(sp);
        return other != null ? other : n == null ? null : n.id;
    }

    /** In a war zone a fighter may build and break; the block's prior state is logged first. */
    private static boolean warAllows(ServerLevel level, Player player, Town town, BlockPos pos, BlockState before) {
        War w = WarService.fightsHere(level.getServer(), player.getUUID(), town, NW.chunk(pos));
        if (w == null) return false;
        if (before.is(NWRegistry.TOWN_CORE.get()) || level.getBlockEntity(pos) instanceof net.minecraft.world.Container) return false;
        NW.data(level.getServer()).restoreLog(w.id).record(level, pos, before);
        NW.data(level.getServer()).setDirty();
        return true;
    }

    private static void logChange(ServerLevel level, BlockPos pos, BlockState before) {
        Town town = NW.townAt(level, pos);
        if (town == null) return;
        War w = WarService.warZone(NW.data(level.getServer()), town, NW.chunk(pos));
        if (w != null) NW.data(level.getServer()).restoreLog(w.id).record(level, pos, before);
    }

    /** No drops from blocks broken in a war zone (they come back when the war ends). */
    @SubscribeEvent
    public static void drops(BlockDropsEvent event) {
        ServerLevel level = event.getLevel();
        NWSavedData data = NW.data(level.getServer());
        if (data.restore.isEmpty()) return;
        for (var log : data.restore.values()) {
            if (log.contains(level, event.getPos())) {
                event.setCanceled(true);
                return;
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void use(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide) return;
        Player player = event.getEntity();
        BlockPos pos = event.getPos();
        Town town = NW.townAt(level, pos);
        if (town == null) return;
        BlockState state = level.getBlockState(pos);
        ChunkPerms.Action action = actionFor(level, pos, state);
        if (action != null && !ProtectionBypass.bypassed(level, pos, action == ChunkPerms.Action.SWITCH ? Bypass.SWITCH : Bypass.CONTAINER)
                && !allowed(player, level, pos, action)) {
            event.setUseBlock(TriState.FALSE);
            deny(player, town, action);
        }
        ItemStack held = event.getItemStack();
        if (isUseItem(held)) {
            BlockPos target = pos.relative(event.getFace() == null ? net.minecraft.core.Direction.UP : event.getFace());
            Town t2 = NW.townAt(level, target);
            if (t2 != null && !ProtectionBypass.bypassed(level, target, Bypass.ITEM_USE) && !allowed(player, level, target, ChunkPerms.Action.ITEM_USE)) {
                event.setUseItem(TriState.FALSE);
                deny(player, t2, ChunkPerms.Action.ITEM_USE);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void useItem(PlayerInteractEvent.RightClickItem event) {
        Level level = event.getLevel();
        if (level.isClientSide || !isUseItem(event.getItemStack())) return;
        Player player = event.getEntity();
        HitResult hit = player.pick(player.blockInteractionRange(), 0, true);
        if (!(hit instanceof BlockHitResult bh) || hit.getType() != HitResult.Type.BLOCK) return;
        BlockPos pos = bh.getBlockPos();
        Town town = NW.townAt(level, pos);
        if (town != null && !ProtectionBypass.bypassed(level, pos, Bypass.ITEM_USE) && !allowed(player, level, pos, ChunkPerms.Action.ITEM_USE)) {
            event.setCanceled(true);
            deny(player, town, ChunkPerms.Action.ITEM_USE);
        }
    }

    private static boolean isUseItem(ItemStack s) {
        return s.getItem() instanceof BucketItem || s.getItem() instanceof FlintAndSteelItem || s.getItem() instanceof FireChargeItem
                || s.getItem() instanceof SpawnEggItem || s.is(net.minecraft.world.item.Items.BONE_MEAL) || s.is(net.minecraft.world.item.Items.ARMOR_STAND)
                || s.is(net.minecraft.world.item.Items.END_CRYSTAL);
    }

    private static ChunkPerms.Action actionFor(Level level, BlockPos pos, BlockState state) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof net.minecraft.world.Container || be instanceof MenuProvider || state.getBlock() instanceof BaseEntityBlock && be != null)
            return ChunkPerms.Action.CONTAINER;
        var b = state.getBlock();
        if (b instanceof DoorBlock || b instanceof TrapDoorBlock || b instanceof FenceGateBlock || b instanceof ButtonBlock || b instanceof LeverBlock
                || b instanceof PressurePlateBlock) return ChunkPerms.Action.SWITCH;
        if (state.getMenuProvider(level, pos) != null) return ChunkPerms.Action.CONTAINER;
        return null;
    }

    @SubscribeEvent
    public static void trample(BlockEvent.FarmlandTrampleEvent event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide) return;
        Town town = NW.townAt(level, event.getPos());
        if (town == null || ProtectionBypass.bypassed(level, event.getPos(), Bypass.DESTROY)) return;
        if (!(event.getEntity() instanceof Player p) || !allowed(p, level, event.getPos(), ChunkPerms.Action.DESTROY)) event.setCanceled(true);
    }

    // --- entities ----------------------------------------------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void interactEntity(PlayerInteractEvent.EntityInteract event) {
        Entity target = event.getTarget();
        if (event.getLevel().isClientSide || !(target instanceof ArmorStand || target instanceof HangingEntity || target instanceof AbstractMinecart
                || target instanceof Boat)) return;
        Town town = NW.townAt(event.getLevel(), target.blockPosition());
        ChunkPerms.Action action = target instanceof ArmorStand || target instanceof HangingEntity ? ChunkPerms.Action.CONTAINER : ChunkPerms.Action.SWITCH;
        if (ProtectionBypass.bypassed(event.getLevel(), target.blockPosition(), action == ChunkPerms.Action.SWITCH ? Bypass.SWITCH : Bypass.CONTAINER)) return;
        if (town != null && !allowed(event.getEntity(), event.getLevel(), target.blockPosition(), action)) {
            event.setCanceled(true);
            deny(event.getEntity(), town, action);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void attackEntity(AttackEntityEvent event) {
        Entity target = event.getTarget();
        Player player = event.getEntity();
        if (player.level().isClientSide || target instanceof Player || target instanceof Enemy) return;
        Town town = NW.townAt(player.level(), target.blockPosition());
        if (town == null || ProtectionBypass.bypassed(player.level(), target.blockPosition(), Bypass.ATTACK)) return;
        if (target instanceof LivingEntity && !(target instanceof ArmorStand) && target.getType().getCategory() == MobCategory.MONSTER) return;
        if (target instanceof org.papiricoh.nationsandwar.npc.GuardEntity) return;
        if (!allowed(player, player.level(), target.blockPosition(), ChunkPerms.Action.DESTROY)) {
            event.setCanceled(true);
            deny(player, town, ChunkPerms.Action.DESTROY);
        }
    }

    /** PvP only where the town allows it, or between enemies at war. */
    @SubscribeEvent
    public static void damage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker) || attacker == victim) return;
        MinecraftServer server = victim.getServer();
        Politics pol = NW.pol(server);
        Town town = NW.townAt(victim.level(), victim.blockPosition());
        if (town == null || town.flag(Flag.PVP, NW.chunk(victim.blockPosition())) || ProtectionBypass.bypassed(victim.level(), victim.blockPosition(), Bypass.PVP)) return;
        var data = NW.data(server);
        for (War w : data.wars.values()) {
            if (w.phase != War.Phase.ACTIVE) continue;
            Boolean a = WarService.sideOf(data, w, attacker), v = WarService.sideOf(data, w, victim);
            if (a != null && v != null && !a.equals(v)) return;
        }
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void spawn(FinalizeSpawnEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        MobSpawnType type = event.getSpawnType();
        if (type != MobSpawnType.NATURAL && type != MobSpawnType.PATROL && type != MobSpawnType.REINFORCEMENT && type != MobSpawnType.CHUNK_GENERATION) return;
        if (!(event.getEntity() instanceof Enemy) || ProtectionBypass.exempt(event.getEntity())) return;
        BlockPos pos = BlockPos.containing(event.getX(), event.getY(), event.getZ());
        Town town = NW.townAt(level.getLevel(), pos);
        if (town != null && !town.flag(Flag.MOBS, NW.chunk(pos)) && !ProtectionBypass.bypassed(level.getLevel(), pos, Bypass.SPAWNS)) event.setSpawnCancelled(true);
    }

    @SubscribeEvent
    public static void grief(EntityMobGriefingEvent event) {
        Entity e = event.getEntity();
        if (e == null || e.level().isClientSide || !(e instanceof Enemy) || ProtectionBypass.exempt(e)) return;
        Town town = NW.townAt(e.level(), e.blockPosition());
        if (town != null && !town.flag(Flag.EXPLOSIONS, NW.chunk(e.blockPosition())) && !ProtectionBypass.bypassed(e.level(), e.blockPosition(), Bypass.MOB_GRIEFING))
            event.setCanGrief(false);
    }

    @SubscribeEvent
    public static void explosion(ExplosionEvent.Detonate event) {
        Level level = event.getLevel();
        if (level.isClientSide) return;
        boolean exempt = ProtectionBypass.exempt(event.getExplosion().getDirectSourceEntity()) || ProtectionBypass.exempt(event.getExplosion().getIndirectSourceEntity());
        event.getAffectedBlocks().removeIf(pos -> {
            Town town = NW.townAt(level, pos);
            if (town == null) return false;
            if (level.getBlockState(pos).is(NWRegistry.TOWN_CORE.get())) return true;
            if (exempt || ProtectionBypass.bypassed(level, pos, Bypass.EXPLOSIONS)) return false;
            return !town.flag(Flag.EXPLOSIONS, NW.chunk(pos));
        });
        if (!exempt) event.getAffectedEntities().removeIf(e -> !(e instanceof LivingEntity) || e instanceof ArmorStand ? protectedEntity(level, e) : false);
    }

    private static boolean protectedEntity(Level level, Entity e) {
        Town town = NW.townAt(level, e.blockPosition());
        return town != null && !town.flag(Flag.EXPLOSIONS, NW.chunk(e.blockPosition())) && !ProtectionBypass.bypassed(level, e.blockPosition(), Bypass.EXPLOSIONS);
    }

    /** Pistons may not move blocks across a border between owners. */
    @SubscribeEvent
    public static void piston(PistonEvent.Pre event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide) return;
        var helper = event.getStructureHelper();
        if (helper == null || ProtectionBypass.bypassed(level, event.getPos(), Bypass.PISTONS) || !helper.resolve()) return;
        UUID owner = ownerAt(level, event.getPos());
        var dir = event.getPistonMoveType() == PistonEvent.PistonMoveType.EXTEND ? event.getDirection() : event.getDirection().getOpposite();
        for (BlockPos p : helper.getToPush()) {
            if (!java.util.Objects.equals(owner, ownerAt(level, p)) || !java.util.Objects.equals(owner, ownerAt(level, p.relative(dir)))) {
                event.setCanceled(true);
                return;
            }
        }
        for (BlockPos p : helper.getToDestroy()) {
            if (!java.util.Objects.equals(owner, ownerAt(level, p))) {
                event.setCanceled(true);
                return;
            }
        }
    }

    private static UUID ownerAt(Level level, BlockPos pos) {
        Town t = NW.townAt(level, pos);
        return t == null ? null : t.id;
    }

    /** Fire may not burn or spread in a town chunk with fire off (called from the FireBlock mixin). */
    public static boolean fireBlocked(Level level, BlockPos pos) {
        if (level.isClientSide || level.getServer() == null || ProtectionBypass.bypassed(level, pos, Bypass.FIRE)) return false;
        Town town = NW.pol(level.getServer()).townAt(NW.dim(level), ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
        return town != null && !town.flag(Flag.FIRE, NW.chunk(pos));
    }
}
