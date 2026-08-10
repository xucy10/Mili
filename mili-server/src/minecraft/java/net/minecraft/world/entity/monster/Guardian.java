package net.minecraft.world.entity.monster;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.animal.squid.Squid;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class Guardian extends Monster {
    protected static final int ATTACK_TIME = 80;
    private static final EntityDataAccessor<Boolean> DATA_ID_MOVING = SynchedEntityData.defineId(Guardian.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_ID_ATTACK_TARGET = SynchedEntityData.defineId(Guardian.class, EntityDataSerializers.INT);
    private float clientSideTailAnimation;
    private float clientSideTailAnimationO;
    private float clientSideTailAnimationSpeed;
    private float clientSideSpikesAnimation;
    private float clientSideSpikesAnimationO;
    private @Nullable LivingEntity clientSideCachedAttackTarget;
    private int clientSideAttackTime;
    private boolean clientSideTouchedGround;
    public @Nullable RandomStrollGoal randomStrollGoal;
    public Guardian.GuardianAttackGoal guardianAttackGoal; // CraftBukkit - add field

    public Guardian(EntityType<? extends Guardian> type, Level level) {
        super(type, level);
        this.xpReward = 10;
        this.setPathfindingMalus(PathType.WATER, 0.0F);
        this.moveControl = new Guardian.GuardianMoveControl(this);
        this.clientSideTailAnimation = this.random.nextFloat();
        this.clientSideTailAnimationO = this.clientSideTailAnimation;
    }

    @Override
    protected void registerGoals() {
        MoveTowardsRestrictionGoal moveTowardsRestrictionGoal = new MoveTowardsRestrictionGoal(this, 1.0);
        this.randomStrollGoal = new RandomStrollGoal(this, 1.0, 80);
        this.goalSelector.addGoal(4, this.guardianAttackGoal = new Guardian.GuardianAttackGoal(this)); // CraftBukkit - assign field
        this.goalSelector.addGoal(5, moveTowardsRestrictionGoal);
        this.goalSelector.addGoal(7, this.randomStrollGoal);
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Guardian.class, 12.0F, 0.01F));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        this.randomStrollGoal.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        moveTowardsRestrictionGoal.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false, new Guardian.GuardianAttackSelector(this)));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.ATTACK_DAMAGE, 6.0).add(Attributes.MOVEMENT_SPEED, 0.5).add(Attributes.MAX_HEALTH, 30.0);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new WaterBoundPathNavigation(this, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ID_MOVING, false);
        builder.define(DATA_ID_ATTACK_TARGET, 0);
    }

    public boolean isMoving() {
        return this.entityData.get(DATA_ID_MOVING);
    }

    void setMoving(boolean moving) {
        this.entityData.set(DATA_ID_MOVING, moving);
    }

    public int getAttackDuration() {
        return 80;
    }

    public void setActiveAttackTarget(int activeAttackTargetId) {
        this.entityData.set(DATA_ID_ATTACK_TARGET, activeAttackTargetId);
    }

    public boolean hasActiveAttackTarget() {
        return this.entityData.get(DATA_ID_ATTACK_TARGET) != 0;
    }

    public @Nullable LivingEntity getActiveAttackTarget() {
        if (!this.hasActiveAttackTarget()) {
            return null;
        } else if (this.level().isClientSide()) {
            if (this.clientSideCachedAttackTarget != null) {
                return this.clientSideCachedAttackTarget;
            } else {
                Entity entity = this.level().getEntity(this.entityData.get(DATA_ID_ATTACK_TARGET));
                if (entity instanceof LivingEntity) {
                    this.clientSideCachedAttackTarget = (LivingEntity)entity;
                    return this.clientSideCachedAttackTarget;
                } else {
                    return null;
                }
            }
        } else {
            return this.getTarget();
        }
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_ID_ATTACK_TARGET.equals(key)) {
            this.clientSideAttackTime = 0;
            this.clientSideCachedAttackTarget = null;
        }
    }

    @Override
    public int getAmbientSoundInterval() {
        return 160;
    }

    @Override
    public SoundEvent getAmbientSound() {
        return this.isInWater() ? SoundEvents.GUARDIAN_AMBIENT : SoundEvents.GUARDIAN_AMBIENT_LAND;
    }

    @Override
    public SoundEvent getHurtSound(DamageSource damageSource) {
        return this.isInWater() ? SoundEvents.GUARDIAN_HURT : SoundEvents.GUARDIAN_HURT_LAND;
    }

    @Override
    public SoundEvent getDeathSound() {
        return this.isInWater() ? SoundEvents.GUARDIAN_DEATH : SoundEvents.GUARDIAN_DEATH_LAND;
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.EVENTS;
    }

    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        return level.getFluidState(pos).is(FluidTags.WATER) ? 10.0F + level.getPathfindingCostFromLightLevels(pos) : super.getWalkTargetValue(pos, level);
    }

    @Override
    public void aiStep() {
        if (this.isAlive()) {
            if (this.level().isClientSide()) {
                this.clientSideTailAnimationO = this.clientSideTailAnimation;
                if (!this.isInWater()) {
                    this.clientSideTailAnimationSpeed = 2.0F;
                    Vec3 deltaMovement = this.getDeltaMovement();
                    if (deltaMovement.y > 0.0 && this.clientSideTouchedGround && !this.isSilent()) {
                        this.level().playLocalSound(this.getX(), this.getY(), this.getZ(), this.getFlopSound(), this.getSoundSource(), 1.0F, 1.0F, false);
                    }

                    this.clientSideTouchedGround = deltaMovement.y < 0.0 && this.level().loadedAndEntityCanStandOn(this.blockPosition().below(), this);
                } else if (this.isMoving()) {
                    if (this.clientSideTailAnimationSpeed < 0.5F) {
                        this.clientSideTailAnimationSpeed = 4.0F;
                    } else {
                        this.clientSideTailAnimationSpeed = this.clientSideTailAnimationSpeed + (0.5F - this.clientSideTailAnimationSpeed) * 0.1F;
                    }
                } else {
                    this.clientSideTailAnimationSpeed = this.clientSideTailAnimationSpeed + (0.125F - this.clientSideTailAnimationSpeed) * 0.2F;
                }

                this.clientSideTailAnimation = this.clientSideTailAnimation + this.clientSideTailAnimationSpeed;
                this.clientSideSpikesAnimationO = this.clientSideSpikesAnimation;
                if (!this.isInWater()) {
                    this.clientSideSpikesAnimation = this.random.nextFloat();
                } else if (this.isMoving()) {
                    this.clientSideSpikesAnimation = this.clientSideSpikesAnimation + (0.0F - this.clientSideSpikesAnimation) * 0.25F;
                } else {
                    this.clientSideSpikesAnimation = this.clientSideSpikesAnimation + (1.0F - this.clientSideSpikesAnimation) * 0.06F;
                }

                if (this.isMoving() && this.isInWater()) {
                    Vec3 deltaMovement = this.getViewVector(0.0F);

                    for (int i = 0; i < 2; i++) {
                        this.level()
                            .addParticle(
                                ParticleTypes.BUBBLE,
                                this.getRandomX(0.5) - deltaMovement.x * 1.5,
                                this.getRandomY() - deltaMovement.y * 1.5,
                                this.getRandomZ(0.5) - deltaMovement.z * 1.5,
                                0.0,
                                0.0,
                                0.0
                            );
                    }
                }

                if (this.hasActiveAttackTarget()) {
                    if (this.clientSideAttackTime < this.getAttackDuration()) {
                        this.clientSideAttackTime++;
                    }

                    LivingEntity activeAttackTarget = this.getActiveAttackTarget();
                    if (activeAttackTarget != null) {
                        this.getLookControl().setLookAt(activeAttackTarget, 90.0F, 90.0F);
                        this.getLookControl().tick();
                        double d = this.getAttackAnimationScale(0.0F);
                        double d1 = activeAttackTarget.getX() - this.getX();
                        double d2 = activeAttackTarget.getY(0.5) - this.getEyeY();
                        double d3 = activeAttackTarget.getZ() - this.getZ();
                        double squareRoot = Math.sqrt(d1 * d1 + d2 * d2 + d3 * d3);
                        d1 /= squareRoot;
                        d2 /= squareRoot;
                        d3 /= squareRoot;
                        double randomDouble = this.random.nextDouble();

                        while (randomDouble < squareRoot) {
                            randomDouble += 1.8 - d + this.random.nextDouble() * (1.7 - d);
                            this.level()
                                .addParticle(
                                    ParticleTypes.BUBBLE,
                                    this.getX() + d1 * randomDouble,
                                    this.getEyeY() + d2 * randomDouble,
                                    this.getZ() + d3 * randomDouble,
                                    0.0,
                                    0.0,
                                    0.0
                                );
                        }
                    }
                }
            }

            if (this.isInWater()) {
                this.setAirSupply(300);
            } else if (this.onGround()) {
                this.setDeltaMovement(
                    this.getDeltaMovement().add((this.random.nextFloat() * 2.0F - 1.0F) * 0.4F, 0.5, (this.random.nextFloat() * 2.0F - 1.0F) * 0.4F)
                );
                this.setYRot(this.random.nextFloat() * 360.0F);
                this.setOnGround(false);
                this.needsSync = true;
            }

            if (this.hasActiveAttackTarget()) {
                this.setYRot(this.yHeadRot);
            }
        }

        super.aiStep();
    }

    protected SoundEvent getFlopSound() {
        return SoundEvents.GUARDIAN_FLOP;
    }

    public float getTailAnimation(float partialTick) {
        return Mth.lerp(partialTick, this.clientSideTailAnimationO, this.clientSideTailAnimation);
    }

    public float getSpikesAnimation(float partialTick) {
        return Mth.lerp(partialTick, this.clientSideSpikesAnimationO, this.clientSideSpikesAnimation);
    }

    public float getAttackAnimationScale(float partialTick) {
        return (this.clientSideAttackTime + partialTick) / this.getAttackDuration();
    }

    public float getClientSideAttackTime() {
        return this.clientSideAttackTime;
    }

    @Override
    public boolean checkSpawnObstruction(LevelReader level) {
        return level.isUnobstructed(this);
    }

    public static boolean checkGuardianSpawnRules(
        EntityType<? extends Guardian> entityType, LevelAccessor level, EntitySpawnReason spawnReason, BlockPos pos, RandomSource random
    ) {
        return (random.nextInt(20) == 0 || !level.canSeeSkyFromBelowWater(pos))
            && level.getDifficulty() != Difficulty.PEACEFUL
            && (EntitySpawnReason.isSpawner(spawnReason) || level.getFluidState(pos).is(FluidTags.WATER))
            && level.getFluidState(pos.below()).is(FluidTags.WATER);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        if (!this.isMoving()
            && !damageSource.is(DamageTypeTags.AVOIDS_GUARDIAN_THORNS)
            && !damageSource.is(DamageTypes.THORNS)
            && damageSource.getDirectEntity() instanceof LivingEntity livingEntity) {
            livingEntity.hurtServer(level, this.damageSources().thorns(this), 2.0F);
        }

        if (this.randomStrollGoal != null) {
            this.randomStrollGoal.trigger();
        }

        return super.hurtServer(level, damageSource, amount);
    }

    @Override
    public int getMaxHeadXRot() {
        return 180;
    }

    @Override
    protected void travelInWater(Vec3 travelVector, double gravity, boolean isFalling, double previousY) {
        this.moveRelative(0.1F, travelVector);
        this.move(MoverType.SELF, this.getDeltaMovement());
        this.setDeltaMovement(this.getDeltaMovement().scale(0.9));
        if (!this.isMoving() && this.getTarget() == null) {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0, -0.005, 0.0));
        }
    }

    public static class GuardianAttackGoal extends Goal {
        private final Guardian guardian;
        public int attackTime;
        private final boolean elder;

        public GuardianAttackGoal(Guardian guardian) {
            this.guardian = guardian;
            this.elder = guardian instanceof ElderGuardian;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = this.guardian.getTarget();
            return target != null && target.isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return super.canContinueToUse()
                && (this.elder || this.guardian.getTarget() != null && this.guardian.distanceToSqr(this.guardian.getTarget()) > 9.0);
        }

        @Override
        public void start() {
            this.attackTime = -10;
            this.guardian.getNavigation().stop();
            LivingEntity target = this.guardian.getTarget();
            if (target != null) {
                this.guardian.getLookControl().setLookAt(target, 90.0F, 90.0F);
            }

            this.guardian.needsSync = true;
        }

        @Override
        public void stop() {
            this.guardian.setActiveAttackTarget(0);
            this.guardian.setTarget(null);
            this.guardian.randomStrollGoal.trigger();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = this.guardian.getTarget();
            if (target != null) {
                this.guardian.getNavigation().stop();
                this.guardian.getLookControl().setLookAt(target, 90.0F, 90.0F);
                if (!this.guardian.hasLineOfSight(target)) {
                    this.guardian.setTarget(null);
                } else {
                    this.attackTime++;
                    if (this.attackTime == 0) {
                        this.guardian.setActiveAttackTarget(target.getId());
                        if (!this.guardian.isSilent()) {
                            this.guardian.level().broadcastEntityEvent(this.guardian, EntityEvent.GUARDIAN_ATTACK_SOUND);
                        }
                    } else if (this.attackTime >= this.guardian.getAttackDuration()) {
                        float f = 1.0F;
                        if (this.guardian.level().getDifficulty() == Difficulty.HARD) {
                            f += 2.0F;
                        }

                        if (this.elder) {
                            f += 2.0F;
                        }

                        ServerLevel serverLevel = getServerLevel(this.guardian);
                        target.hurtServer(serverLevel, this.guardian.damageSources().indirectMagic(this.guardian, this.guardian), f);
                        this.guardian.doHurtTarget(serverLevel, target);
                        this.guardian.setTarget(null);
                    }

                    super.tick();
                }
            }
        }
    }

    static class GuardianAttackSelector implements TargetingConditions.Selector {
        private final Guardian guardian;

        public GuardianAttackSelector(Guardian guardian) {
            this.guardian = guardian;
        }

        @Override
        public boolean test(@Nullable LivingEntity entity, ServerLevel level) {
            return (entity instanceof Player || entity instanceof Squid || entity instanceof Axolotl) && entity.distanceToSqr(this.guardian) > 9.0;
        }
    }

    static class GuardianMoveControl extends MoveControl {
        private final Guardian guardian;

        public GuardianMoveControl(Guardian mob) {
            super(mob);
            this.guardian = mob;
        }

        @Override
        public void tick() {
            if (this.operation == MoveControl.Operation.MOVE_TO && !this.guardian.getNavigation().isDone()) {
                Vec3 vec3 = new Vec3(this.wantedX - this.guardian.getX(), this.wantedY - this.guardian.getY(), this.wantedZ - this.guardian.getZ());
                double len = vec3.length();
                double d = vec3.x / len;
                double d1 = vec3.y / len;
                double d2 = vec3.z / len;
                float f = (float)(Mth.atan2(vec3.z, vec3.x) * 180.0F / (float)Math.PI) - 90.0F;
                this.guardian.setYRot(this.rotlerp(this.guardian.getYRot(), f, 90.0F));
                this.guardian.yBodyRot = this.guardian.getYRot();
                float f1 = (float)(this.speedModifier * this.guardian.getAttributeValue(Attributes.MOVEMENT_SPEED));
                float f2 = Mth.lerp(0.125F, this.guardian.getSpeed(), f1);
                this.guardian.setSpeed(f2);
                double d3 = Math.sin((this.guardian.tickCount + this.guardian.getId()) * 0.5) * 0.05;
                double cos = Math.cos(this.guardian.getYRot() * (float) (Math.PI / 180.0));
                double sin = Math.sin(this.guardian.getYRot() * (float) (Math.PI / 180.0));
                double d4 = Math.sin((this.guardian.tickCount + this.guardian.getId()) * 0.75) * 0.05;
                this.guardian.setDeltaMovement(this.guardian.getDeltaMovement().add(d3 * cos, d4 * (sin + cos) * 0.25 + f2 * d1 * 0.1, d3 * sin));
                LookControl lookControl = this.guardian.getLookControl();
                double d5 = this.guardian.getX() + d * 2.0;
                double d6 = this.guardian.getEyeY() + d1 / len;
                double d7 = this.guardian.getZ() + d2 * 2.0;
                double wantedX = lookControl.getWantedX();
                double wantedY = lookControl.getWantedY();
                double wantedZ = lookControl.getWantedZ();
                if (!lookControl.isLookingAtTarget()) {
                    wantedX = d5;
                    wantedY = d6;
                    wantedZ = d7;
                }

                this.guardian
                    .getLookControl()
                    .setLookAt(Mth.lerp(0.125, wantedX, d5), Mth.lerp(0.125, wantedY, d6), Mth.lerp(0.125, wantedZ, d7), 10.0F, 40.0F);
                this.guardian.setMoving(true);
            } else {
                this.guardian.setSpeed(0.0F);
                this.guardian.setMoving(false);
            }
        }
    }
}
