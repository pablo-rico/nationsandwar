package org.papiricoh.nationsandwar.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.papiricoh.nationsandwar.core.NW;
import org.papiricoh.nationsandwar.data.NWSavedData;
import org.papiricoh.nationsandwar.model.Nation;
import org.papiricoh.nationsandwar.model.Town;
import org.papiricoh.nationsandwar.war.WarService;

import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

/**
 * An NPC nation's guard (or, with {@link #soldier}, a soldier of a war party). Keeps near its post, fights monsters and the
 * citizens and guards of nations at war with its own. Wears a tabard in its nation's colour ({@link #color()}).
 */
public class GuardEntity extends PathfinderMob {
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(GuardEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SKIN = SynchedEntityData.defineId(GuardEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<UUID>> NATION = SynchedEntityData.defineId(GuardEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    public static final int SKINS = 4;

    private UUID town;
    /** A war party's soldier (or a siege's reinforcement): gone once its war is over. */
    public boolean soldier;
    /** A soldier left to hold a chunk its side took (never sent on to the next banner while the chunk stays taken). */
    public boolean garrison;
    private BlockPos post;
    /** Where a guard rallied to a siege goes back to once the banner is gone (null: not rallied). */
    private BlockPos home;
    private int homeRadius;
    /** Checks in a row (every 5 s) far from the post without getting closer: at 12 it is put back at its post. */
    private int stuck;
    private double lastDistance = Double.MAX_VALUE;

    public GuardEntity(EntityType<? extends GuardEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 30).add(Attributes.ATTACK_DAMAGE, 5).add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 24).add(Attributes.ARMOR, 4);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(COLOR, 0x3B4A6B);
        builder.define(SKIN, 0);
        builder.define(NATION, Optional.empty());
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.1, true));
        goalSelector.addGoal(2, new BreakCoreGoal());
        goalSelector.addGoal(2, new OpenDoorGoal(this, true));
        goalSelector.addGoal(3, new MoveTowardsRestrictionGoal(this, 0.9));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this, GuardEntity.class));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, this::isEnemy));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, GuardEntity.class, 10, true, false, this::isEnemy));
        targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Mob.class, 10, true, false, e -> e instanceof Enemy));
    }

    /** A player or guard of a nation at war with this guard's. */
    private boolean isEnemy(LivingEntity e) {
        if (level().isClientSide || nation() == null) return false;
        NWSavedData data = NW.data(level().getServer());
        UUID other = null;
        if (e instanceof Player p) {
            Nation n = data.politics.nationOfPlayer(p.getUUID());
            other = n == null ? null : n.id;
        } else if (e instanceof GuardEntity g) {
            other = g.nation();
        }
        return other != null && WarService.activeWar(data, nation(), other) != null;
    }

    public void assign(Town town, Nation nation, BlockPos post, int radius, boolean soldier) {
        this.town = town == null ? null : town.id;
        this.soldier = soldier;
        this.post = post;
        entityData.set(NATION, Optional.ofNullable(nation == null ? null : nation.id));
        entityData.set(COLOR, nation == null ? (town == null ? 0x3B4A6B : town.color) : nation.color);
        entityData.set(SKIN, getRandom().nextInt(SKINS));
        restrictTo(post, radius);
        equip(soldier);
    }

    private void equip(boolean soldier) {
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(soldier ? Items.IRON_SWORD : getRandom().nextBoolean() ? Items.IRON_SWORD : Items.IRON_AXE));
        setItemSlot(EquipmentSlot.HEAD, new ItemStack(soldier ? Items.IRON_HELMET : Items.CHAINMAIL_HELMET));
        setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
        if (soldier) setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        for (EquipmentSlot s : EquipmentSlot.values()) setDropChance(s, 0);
    }

    public BlockPos post() {
        return post;
    }

    /** Sends the guard to a new post (the next banner, a taken chunk). */
    public void repost(BlockPos post, int radius) {
        this.post = post;
        stuck = 0;
        lastDistance = Double.MAX_VALUE;
        restrictTo(post, radius);
    }

    /** Rallies a town guard to a siege: it goes back to its post once the banner is gone. */
    public void rally(BlockPos banner, int radius) {
        if (home == null && post != null) {
            home = post;
            homeRadius = (int) getRestrictRadius();
        }
        repost(banner, radius);
    }

    public boolean rallied() {
        return home != null;
    }

    public UUID nation() {
        return entityData.get(NATION).orElse(null);
    }

    public UUID town() {
        return town;
    }

    public int color() {
        return entityData.get(COLOR);
    }

    public int skin() {
        return entityData.get(SKIN);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || tickCount % 100 != 0) return;
        NWSavedData data = NW.data(level().getServer());
        Nation n = nation() == null ? null : data.politics.nations.get(nation());
        if (n == null || soldier && !WarService.nationAtWar(level().getServer(), n.id)) {
            discard();
            return;
        }
        if (n.color != color()) entityData.set(COLOR, n.color);
        if (home != null && !WarService.bannerActive(level().getServer(), NW.dim(level()), post)) {
            repost(home, Math.max(4, homeRadius));
            home = null;
        }
        unstick();
    }

    /** A guard that cannot find its way to a far post for a minute is put back at it. */
    private void unstick() {
        if (post == null || getTarget() != null) return;
        double d = distanceToSqr(post.getX() + 0.5, post.getY(), post.getZ() + 0.5);
        double far = getRestrictRadius() + 6;
        if (d <= far * far) {
            stuck = 0;
            lastDistance = d;
            return;
        }
        stuck = d < lastDistance - 4 ? 0 : stuck + 1;
        lastDistance = Math.min(lastDistance, d);
        if (stuck >= 12) {
            BlockPos at = level().getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, post);
            moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, getYRot(), 0);
            getNavigation().stop();
            stuck = 0;
            lastDistance = Double.MAX_VALUE;
        }
    }

    /** The open Core of an enemy town within 32 blocks, while this soldier's nation is at war with it. */
    private BlockPos enemyCore() {
        if (level().isClientSide || nation() == null || !soldier) return null;
        NWSavedData data = NW.data(level().getServer());
        for (org.papiricoh.nationsandwar.war.War w : data.wars.values()) {
            if (w.phase != org.papiricoh.nationsandwar.war.War.Phase.ACTIVE || !w.involves(nation())) continue;
            for (UUID id : w.coreOpen) {
                Town t = data.politics.towns.get(id);
                if (t == null || w.fallen.contains(id) || !w.enemies(nation(), t.nation) || !t.dimension.equals(NW.dim(level()))) continue;
                BlockPos c = BlockPos.of(t.core);
                if (distanceToSqr(c.getX() + 0.5, c.getY(), c.getZ() + 0.5) <= 32 * 32) return c;
            }
        }
        return null;
    }

    /** Soldiers walk to an open enemy Core and strike it every two seconds (each strike counts as a player's). */
    private final class BreakCoreGoal extends Goal {
        private BlockPos core;
        private int cooldown;

        BreakCoreGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!soldier || tickCount % 20 != 0) return false;
            core = enemyCore();
            return core != null;
        }

        @Override
        public boolean canContinueToUse() {
            if (core == null) return false;
            if (tickCount % 20 == 0) core = enemyCore();
            return core != null;
        }

        @Override
        public void start() {
            cooldown = 20;
        }

        @Override
        public void stop() {
            core = null;
            getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            getLookControl().setLookAt(core.getX() + 0.5, core.getY() + 0.5, core.getZ() + 0.5);
            if (distanceToSqr(core.getX() + 0.5, core.getY(), core.getZ() + 0.5) > 2.5 * 2.5) {
                if (getNavigation().isDone() || tickCount % 20 == 0) getNavigation().moveTo(core.getX() + 0.5, core.getY(), core.getZ() + 0.5, 1.0);
                return;
            }
            getNavigation().stop();
            if (--cooldown > 0) return;
            cooldown = 40;
            swing(InteractionHand.MAIN_HAND);
            WarService.hitCore((ServerLevel) level(), core, nation());
        }
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level().isClientSide) return;
        if (nation() != null) WarService.fighterLost(level().getServer(), nation());
        if (source.getEntity() instanceof ServerPlayer killer) NpcService.guardKilled(killer, this);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType type, SpawnGroupData data) {
        SpawnGroupData d = super.finalizeSpawn(level, difficulty, type, data);
        if (getMainHandItem().isEmpty()) equip(false);
        return d;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (town != null) tag.putUUID("Town", town);
        if (nation() != null) tag.putUUID("Nation", nation());
        tag.putInt("Color", color());
        tag.putInt("Skin", skin());
        tag.putBoolean("Soldier", soldier);
        tag.putBoolean("Garrison", garrison);
        if (home != null) {
            tag.putLong("Home", home.asLong());
            tag.putInt("HomeRadius", homeRadius);
        }
        if (post != null) {
            tag.putLong("Post", post.asLong());
            tag.putInt("PostRadius", (int) getRestrictRadius());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        town = tag.hasUUID("Town") ? tag.getUUID("Town") : null;
        entityData.set(NATION, Optional.ofNullable(tag.hasUUID("Nation") ? tag.getUUID("Nation") : null));
        entityData.set(COLOR, tag.getInt("Color"));
        entityData.set(SKIN, tag.getInt("Skin"));
        soldier = tag.getBoolean("Soldier");
        garrison = tag.getBoolean("Garrison");
        home = tag.contains("Home") ? BlockPos.of(tag.getLong("Home")) : null;
        homeRadius = tag.getInt("HomeRadius");
        if (tag.contains("Post")) {
            post = BlockPos.of(tag.getLong("Post"));
            restrictTo(post, Math.max(4, tag.getInt("PostRadius")));
        }
    }
}
