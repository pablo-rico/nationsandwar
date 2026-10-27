package org.papiricoh.nationsandwar.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.papiricoh.nationsandwar.core.NW;
import org.papiricoh.nationsandwar.core.Sync;
import org.papiricoh.nationsandwar.model.Nation;
import org.papiricoh.nationsandwar.network.OpenScreenPayload;

import java.util.Optional;
import java.util.UUID;

/**
 * An NPC nation's envoy, at its capital: cannot be hurt; talking to it opens the diplomacy screen (gifts, alliance, peace).
 */
public class EnvoyEntity extends PathfinderMob {
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(EnvoyEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<UUID>> NATION = SynchedEntityData.defineId(EnvoyEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    public EnvoyEntity(EntityType<? extends EnvoyEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.MOVEMENT_SPEED, 0.25);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(COLOR, 0x6B2E3B);
        builder.define(NATION, Optional.empty());
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8, 1f));
        goalSelector.addGoal(2, new MoveTowardsRestrictionGoal(this, 0.6));
        goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.4));
        goalSelector.addGoal(4, new RandomLookAroundGoal(this));
    }

    public void assign(Nation nation, BlockPos post) {
        entityData.set(NATION, Optional.of(nation.id));
        entityData.set(COLOR, nation.color);
        restrictTo(post, 6);
        setCustomName(net.minecraft.network.chat.Component.translatable("entity.nationsandwar.envoy.of", nation.name));
    }

    public UUID nation() {
        return entityData.get(NATION).orElse(null);
    }

    public int color() {
        return entityData.get(COLOR);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp && nation() != null) {
            CompoundTag args = new CompoundTag();
            args.putString("nation", nation().toString());
            args.putInt("entity", getId());
            Sync.sendView(sp);
            PacketDistributor.sendToPlayer(sp, new OpenScreenPayload("envoy", args));
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || tickCount % 200 != 0) return;
        Nation n = nation() == null ? null : NW.pol(level().getServer()).nations.get(nation());
        if (n == null || !n.npc) discard();
        else if (n.color != color()) entityData.set(COLOR, n.color);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (nation() != null) tag.putUUID("Nation", nation());
        tag.putInt("Color", color());
        if (hasRestriction()) tag.putLong("Post", getRestrictCenter().asLong());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(NATION, Optional.ofNullable(tag.hasUUID("Nation") ? tag.getUUID("Nation") : null));
        entityData.set(COLOR, tag.getInt("Color"));
        if (tag.contains("Post")) restrictTo(BlockPos.of(tag.getLong("Post")), 6);
    }
}
