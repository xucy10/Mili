package net.minecraft.world.entity;

import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.scores.PlayerTeam;
import org.jspecify.annotations.Nullable;

public abstract class TamableAnimal extends Animal implements OwnableEntity {
    public static final int TELEPORT_WHEN_DISTANCE_IS_SQ = 144;
    private static final int MIN_HORIZONTAL_DISTANCE_FROM_TARGET_AFTER_TELEPORTING = 2;
    private static final int MAX_HORIZONTAL_DISTANCE_FROM_TARGET_AFTER_TELEPORTING = 3;
    private static final int MAX_VERTICAL_DISTANCE_FROM_TARGET_AFTER_TELEPORTING = 1;
    private static final boolean DEFAULT_ORDERED_TO_SIT = false;
    protected static final EntityDataAccessor<Byte> DATA_FLAGS_ID = SynchedEntityData.defineId(TamableAnimal.class, EntityDataSerializers.BYTE);
    protected static final EntityDataAccessor<Optional<EntityReference<LivingEntity>>> DATA_OWNERUUID_ID = SynchedEntityData.defineId(
        TamableAnimal.class, EntityDataSerializers.OPTIONAL_LIVING_ENTITY_REFERENCE
    );
    private boolean orderedToSit = false;

    protected TamableAnimal(EntityType<? extends TamableAnimal> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_FLAGS_ID, (byte)0);
        builder.define(DATA_OWNERUUID_ID, Optional.empty());
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        EntityReference<LivingEntity> ownerReference = this.getOwnerReference();
        EntityReference.store(ownerReference, output, "Owner");
        output.putBoolean("Sitting", this.orderedToSit);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        EntityReference<LivingEntity> withOldOwnerConversion = EntityReference.readWithOldOwnerConversion(input, "Owner", this.level());
        if (withOldOwnerConversion != null) {
            try {
                this.entityData.set(DATA_OWNERUUID_ID, Optional.of(withOldOwnerConversion));
                this.setTame(true, false);
            } catch (Throwable var4) {
                this.setTame(false, true);
            }
        } else {
            this.entityData.set(DATA_OWNERUUID_ID, Optional.empty());
            this.setTame(false, true);
        }

        this.orderedToSit = input.getBooleanOr("Sitting", false);
        this.setInSittingPose(this.orderedToSit, false); // Paper - Add EntityToggleSitEvent
    }

    @Override
    public boolean canBeLeashed() {
        return true;
    }

    protected void spawnTamingParticles(boolean tamed) {
        ParticleOptions particleOptions = ParticleTypes.HEART;
        if (!tamed) {
            particleOptions = ParticleTypes.SMOKE;
        }

        for (int i = 0; i < 7; i++) {
            double d = this.random.nextGaussian() * 0.02;
            double d1 = this.random.nextGaussian() * 0.02;
            double d2 = this.random.nextGaussian() * 0.02;
            this.level().addParticle(particleOptions, this.getRandomX(1.0), this.getRandomY() + 0.5, this.getRandomZ(1.0), d, d1, d2);
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EntityEvent.TAMING_SUCCEEDED) {
            this.spawnTamingParticles(true);
        } else if (id == EntityEvent.TAMING_FAILED) {
            this.spawnTamingParticles(false);
        } else {
            super.handleEntityEvent(id);
        }
    }

    public boolean isTame() {
        return (this.entityData.get(DATA_FLAGS_ID) & 4) != 0;
    }

    public void setTame(boolean tame, boolean applyTamingSideEffects) {
        byte b = this.entityData.get(DATA_FLAGS_ID);
        if (tame) {
            this.entityData.set(DATA_FLAGS_ID, (byte)(b | 4));
        } else {
            this.entityData.set(DATA_FLAGS_ID, (byte)(b & -5));
        }

        if (applyTamingSideEffects) {
            this.applyTamingSideEffects();
        }
    }

    protected void applyTamingSideEffects() {
    }

    public boolean isInSittingPose() {
        return (this.entityData.get(DATA_FLAGS_ID) & 1) != 0;
    }

    public void setInSittingPose(boolean sitting) {
        // Paper start - Add EntityToggleSitEvent
        this.setInSittingPose(sitting, true);
    }

    public void setInSittingPose(boolean sitting, boolean callEvent) {
        if (callEvent && !new io.papermc.paper.event.entity.EntityToggleSitEvent(this.getBukkitEntity(), sitting).callEvent()) return;
        // Paper end - Add EntityToggleSitEvent
        byte b = this.entityData.get(DATA_FLAGS_ID);
        if (sitting) {
            this.entityData.set(DATA_FLAGS_ID, (byte)(b | 1));
        } else {
            this.entityData.set(DATA_FLAGS_ID, (byte)(b & -2));
        }
    }

    @Override
    public @Nullable EntityReference<LivingEntity> getOwnerReference() {
        return this.entityData.get(DATA_OWNERUUID_ID).orElse(null);
    }

    public void setOwner(@Nullable LivingEntity owner) {
        this.entityData.set(DATA_OWNERUUID_ID, Optional.ofNullable(owner).map(EntityReference::of));
    }

    public void setOwnerReference(@Nullable EntityReference<LivingEntity> owner) {
        this.entityData.set(DATA_OWNERUUID_ID, Optional.ofNullable(owner));
    }

    public void tame(Player player) {
        this.setTame(true, true);
        this.setOwner(player);
        if (player instanceof ServerPlayer serverPlayer) {
            CriteriaTriggers.TAME_ANIMAL.trigger(serverPlayer, this);
        }
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !this.isOwnedBy(target) && super.canAttack(target);
    }

    public boolean isOwnedBy(LivingEntity entity) {
        return entity == this.getOwner();
    }

    public boolean wantsToAttack(LivingEntity target, LivingEntity owner) {
        return true;
    }

    @Override
    public @Nullable PlayerTeam getTeam() {
        PlayerTeam playerTeam = super.getTeam();
        if (playerTeam != null) {
            return playerTeam;
        } else {
            if (this.isTame()) {
                LivingEntity rootOwner = this.getRootOwner();
                if (rootOwner != null) {
                    return rootOwner.getTeam();
                }
            }

            return null;
        }
    }

    @Override
    protected boolean considersEntityAsAlly(Entity entity) {
        if (this.isTame()) {
            LivingEntity rootOwner = this.getRootOwner();
            if (entity == rootOwner) {
                return true;
            }

            if (rootOwner != null) {
                return rootOwner.considersEntityAsAlly(entity);
            }
        }

        return super.considersEntityAsAlly(entity);
    }

    @Override
    public void die(DamageSource damageSource) {
        if (this.level() instanceof ServerLevel serverLevel
            && serverLevel.getGameRules().get(GameRules.SHOW_DEATH_MESSAGES)
            && this.getOwner() instanceof ServerPlayer serverPlayer) {
            // Paper start - Add TameableDeathMessageEvent
            io.papermc.paper.event.entity.TameableDeathMessageEvent event = new io.papermc.paper.event.entity.TameableDeathMessageEvent((org.bukkit.entity.Tameable) getBukkitEntity(), io.papermc.paper.adventure.PaperAdventure.asAdventure(this.getCombatTracker().getDeathMessage()));
            if (event.callEvent()) {
                serverPlayer.sendSystemMessage(io.papermc.paper.adventure.PaperAdventure.asVanilla(event.deathMessage()));
            }
            // Paper end - Add TameableDeathMessageEvent
        }

        super.die(damageSource);
    }

    public boolean isOrderedToSit() {
        return this.orderedToSit;
    }

    public void setOrderedToSit(boolean orderedToSit) {
        this.orderedToSit = orderedToSit;
    }

    public void tryToTeleportToOwner() {
        LivingEntity owner = this.getOwner();
        if (owner != null) {
            // Folia start - region threading
            if (owner.isRemoved() || owner.level() != this.level()) {
                return;
            }
            // Folia end - region threading
            this.teleportToAroundBlockPos(owner.blockPosition());
        }
    }

    public boolean shouldTryTeleportToOwner() {
        LivingEntity owner = this.getOwner();
        return owner != null && this.distanceToSqr(this.getOwner()) >= 144.0;
    }

    private void teleportToAroundBlockPos(BlockPos pos) {
        for (int i = 0; i < 10; i++) {
            int randomInt = this.random.nextIntBetweenInclusive(-3, 3);
            int randomInt1 = this.random.nextIntBetweenInclusive(-3, 3);
            if (Math.abs(randomInt) >= 2 || Math.abs(randomInt1) >= 2) {
                int randomInt2 = this.random.nextIntBetweenInclusive(-1, 1);
                if (this.maybeTeleportTo(pos.getX() + randomInt, pos.getY() + randomInt2, pos.getZ() + randomInt1)) {
                    return;
                }
            }
        }
    }

    private boolean maybeTeleportTo(int x, int y, int z) {
        if (!this.canTeleportTo(new BlockPos(x, y, z))) {
            return false;
        } else {
            // CraftBukkit start
            org.bukkit.event.entity.EntityTeleportEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callEntityTeleportEvent(this, x + 0.5, y, z + 0.5);
            if (event.isCancelled() || event.getTo() == null) {
                return false;
            }
            org.bukkit.Location to = event.getTo();
            // Folia start - region threading - can't teleport here, we may be removed by teleport logic - delay until next tick
            // also, use teleportAsync so that crossing region boundaries will not blow up
            org.bukkit.Location finalTo = to;
            Level sourceWorld = this.level();
            this.getBukkitEntity().taskScheduler.schedule((TamableAnimal nmsEntity) -> {
                if (nmsEntity.level() == sourceWorld) {
                    nmsEntity.teleportAsync(
                        (net.minecraft.server.level.ServerLevel)nmsEntity.level(),
                        new net.minecraft.world.phys.Vec3(finalTo.getX(), finalTo.getY(), finalTo.getZ()),
                        Float.valueOf(finalTo.getYaw()), Float.valueOf(finalTo.getPitch()),
                        net.minecraft.world.phys.Vec3.ZERO, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.UNKNOWN, Entity.TELEPORT_FLAG_LOAD_CHUNK,
                        null
                    );
                }
            }, null, 1L);
            // Folia end - region threading - can't teleport here, we may be removed by teleport logic - delay until next tick
            // CraftBukkit end
            this.navigation.stop();
            return true;
        }
    }

    private boolean canTeleportTo(BlockPos pos) {
        PathType pathTypeStatic = WalkNodeEvaluator.getPathTypeStatic(this, pos);
        if (pathTypeStatic != PathType.WALKABLE) {
            return false;
        } else {
            BlockState blockState = this.level().getBlockStateIfLoaded(pos.below()); // Luminol - Prevent tamable animals check can teleport in an unloaded chunk
            if (blockState == null) return false;  // Luminol - Prevent tamable animals check can teleport in an unloaded chunk
            if (!this.canFlyToOwner() && blockState.getBlock() instanceof LeavesBlock) {
                return false;
            } else {
                BlockPos blockPos = pos.subtract(this.blockPosition());
                return this.level().noCollision(this, this.getBoundingBox().move(blockPos));
            }
        }
    }

    public final boolean unableToMoveToOwner() {
        return this.isOrderedToSit() || this.isPassenger() || this.mayBeLeashed() || this.getOwner() != null && this.getOwner().isSpectator();
    }

    protected boolean canFlyToOwner() {
        return false;
    }

    public class TamableAnimalPanicGoal extends PanicGoal {
        public TamableAnimalPanicGoal(final double speedModifier, final TagKey<DamageType> panicCausingDamageTypes) {
            super(TamableAnimal.this, speedModifier, panicCausingDamageTypes);
        }

        public TamableAnimalPanicGoal(final double speedModifier) {
            super(TamableAnimal.this, speedModifier);
        }

        @Override
        public void tick() {
            if (!TamableAnimal.this.unableToMoveToOwner() && TamableAnimal.this.shouldTryTeleportToOwner()) {
                TamableAnimal.this.tryToTeleportToOwner();
            }

            super.tick();
        }
    }
}
