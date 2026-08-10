package net.minecraft.world.entity.monster;

import java.util.EnumSet;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class Ghast extends Mob implements Enemy {
    private static final EntityDataAccessor<Boolean> DATA_IS_CHARGING = SynchedEntityData.defineId(Ghast.class, EntityDataSerializers.BOOLEAN);
    private static final byte DEFAULT_EXPLOSION_POWER = 1;
    private int explosionPower = 1;

    public Ghast(EntityType<? extends Ghast> type, Level level) {
        super(type, level);
        this.xpReward = 5;
        this.moveControl = new Ghast.GhastMoveControl(this, false, () -> false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(5, new Ghast.RandomFloatAroundGoal(this));
        this.goalSelector.addGoal(7, new Ghast.GhastLookGoal(this));
        this.goalSelector.addGoal(7, new Ghast.GhastShootFireballGoal(this));
        this.targetSelector
            .addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, (entity, level) -> Math.abs(entity.getY() - this.getY()) <= 4.0));
    }

    public boolean isCharging() {
        return this.entityData.get(DATA_IS_CHARGING);
    }

    public void setCharging(boolean charging) {
        this.entityData.set(DATA_IS_CHARGING, charging);
    }

    public int getExplosionPower() {
        return this.explosionPower;
    }

    // Paper start
    public void setExplosionPower(int explosionPower) {
        this.explosionPower = explosionPower;
    }
    // Paper end

    private static boolean isReflectedFireball(DamageSource damageSource) {
        return damageSource.getDirectEntity() instanceof LargeFireball && damageSource.getEntity() instanceof Player;
    }

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource damageSource) {
        return this.isInvulnerable() && !damageSource.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
            || !isReflectedFireball(damageSource) && super.isInvulnerableTo(level, damageSource);
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    @Override
    public boolean onClimbable() {
        return false;
    }

    @Override
    public void travel(Vec3 travelVector) {
        this.travelFlying(travelVector, 0.02F);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        if (isReflectedFireball(damageSource)) {
            super.hurtServer(level, damageSource, 1000.0F);
            return true;
        } else {
            return !this.isInvulnerableTo(level, damageSource) && super.hurtServer(level, damageSource, amount);
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_IS_CHARGING, false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 10.0)
            .add(Attributes.FOLLOW_RANGE, 100.0)
            .add(Attributes.CAMERA_DISTANCE, 8.0)
            .add(Attributes.FLYING_SPEED, 0.06);
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.HOSTILE;
    }

    @Override
    public SoundEvent getAmbientSound() {
        return SoundEvents.GHAST_AMBIENT;
    }

    @Override
    public SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.GHAST_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.GHAST_DEATH;
    }

    @Override
    public float getSoundVolume() {
        return 5.0F;
    }

    public static boolean checkGhastSpawnRules(
        EntityType<Ghast> entityType, LevelAccessor level, EntitySpawnReason spawnReason, BlockPos pos, RandomSource random
    ) {
        return level.getDifficulty() != Difficulty.PEACEFUL && random.nextInt(20) == 0 && checkMobSpawnRules(entityType, level, spawnReason, pos, random);
    }

    @Override
    public int getMaxSpawnClusterSize() {
        return 1;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putByte("ExplosionPower", (byte)this.explosionPower);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.explosionPower = input.getByteOr("ExplosionPower", (byte)1);
    }

    @Override
    public boolean supportQuadLeashAsHolder() {
        return true;
    }

    @Override
    public double leashElasticDistance() {
        return 10.0;
    }

    @Override
    public double leashSnapDistance() {
        return 16.0;
    }

    public static void faceMovementDirection(Mob mob) {
        if (mob.getTarget() == null) {
            Vec3 deltaMovement = mob.getDeltaMovement();
            mob.setYRot(-((float)Mth.atan2(deltaMovement.x, deltaMovement.z)) * (180.0F / (float)Math.PI));
            mob.yBodyRot = mob.getYRot();
        } else {
            LivingEntity target = mob.getTarget();
            double d = 64.0;
            if (target.distanceToSqr(mob) < 4096.0) {
                double d1 = target.getX() - mob.getX();
                double d2 = target.getZ() - mob.getZ();
                mob.setYRot(-((float)Mth.atan2(d1, d2)) * (180.0F / (float)Math.PI));
                mob.yBodyRot = mob.getYRot();
            }
        }
    }

    public static class GhastLookGoal extends Goal {
        private final Mob ghast;

        public GhastLookGoal(Mob ghast) {
            this.ghast = ghast;
            this.setFlags(EnumSet.of(Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return true;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            Ghast.faceMovementDirection(this.ghast);
        }
    }

    public static class GhastMoveControl extends MoveControl {
        private final Mob ghast;
        private int floatDuration;
        private final boolean careful;
        private final BooleanSupplier shouldBeStopped;

        public GhastMoveControl(Mob mob, boolean careful, BooleanSupplier shouldBeStopped) {
            super(mob);
            this.ghast = mob;
            this.careful = careful;
            this.shouldBeStopped = shouldBeStopped;
        }

        @Override
        public void tick() {
            if (this.shouldBeStopped.getAsBoolean()) {
                this.operation = MoveControl.Operation.WAIT;
                this.ghast.stopInPlace();
            }

            if (this.operation == MoveControl.Operation.MOVE_TO) {
                if (this.floatDuration-- <= 0) {
                    this.floatDuration = this.floatDuration + this.ghast.getRandom().nextInt(5) + 2;
                    Vec3 vec3 = new Vec3(this.wantedX - this.ghast.getX(), this.wantedY - this.ghast.getY(), this.wantedZ - this.ghast.getZ());
                    if (this.canReach(vec3)) {
                        this.ghast
                            .setDeltaMovement(
                                this.ghast.getDeltaMovement().add(vec3.normalize().scale(this.ghast.getAttributeValue(Attributes.FLYING_SPEED) * 5.0 / 3.0))
                            );
                    } else {
                        this.operation = MoveControl.Operation.WAIT;
                    }
                }
            }
        }

        private boolean canReach(Vec3 delta) {
            AABB boundingBox = this.ghast.getBoundingBox();
            AABB aabb = boundingBox.move(delta);
            if (this.careful) {
                for (BlockPos blockPos : BlockPos.betweenClosed(aabb.inflate(1.0))) {
                    if (!this.blockTraversalPossible(this.ghast.level(), null, null, blockPos, false, false)) {
                        return false;
                    }
                }
            }

            boolean isInWater = this.ghast.isInWater();
            boolean isInLava = this.ghast.isInLava();
            Vec3 vec3 = this.ghast.position();
            Vec3 vec31 = vec3.add(delta);
            return BlockGetter.forEachBlockIntersectedBetween(
                vec3,
                vec31,
                aabb,
                (pos, index) -> boundingBox.intersects(pos) || this.blockTraversalPossible(this.ghast.level(), vec3, vec31, pos, isInWater, isInLava)
            );
        }

        private boolean blockTraversalPossible(BlockGetter level, @Nullable Vec3 from, @Nullable Vec3 to, BlockPos pos, boolean isInLava, boolean isInWater) {
            BlockState blockState = level.getBlockState(pos);
            if (blockState.isAir()) {
                return true;
            } else {
                boolean flag = from != null && to != null;
                boolean flag1 = flag
                    ? !this.ghast.collidedWithShapeMovingFrom(from, to, blockState.getCollisionShape(level, pos).move(new Vec3(pos)).toAabbs())
                    : blockState.getCollisionShape(level, pos).isEmpty();
                if (!this.careful) {
                    return flag1;
                } else if (blockState.is(BlockTags.HAPPY_GHAST_AVOIDS)) {
                    return false;
                } else {
                    FluidState fluidState = level.getFluidState(pos);
                    if (!fluidState.isEmpty() && (!flag || this.ghast.collidedWithFluid(fluidState, pos, from, to))) {
                        if (fluidState.is(FluidTags.WATER)) {
                            return isInLava;
                        }

                        if (fluidState.is(FluidTags.LAVA)) {
                            return isInWater;
                        }
                    }

                    return flag1;
                }
            }
        }
    }

    static class GhastShootFireballGoal extends Goal {
        private final Ghast ghast;
        public int chargeTime;

        public GhastShootFireballGoal(Ghast ghast) {
            this.ghast = ghast;
        }

        @Override
        public boolean canUse() {
            return this.ghast.getTarget() != null;
        }

        @Override
        public void start() {
            this.chargeTime = 0;
        }

        @Override
        public void stop() {
            this.ghast.setCharging(false);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = this.ghast.getTarget();
            if (target != null) {
                double d = 64.0;
                if (target.distanceToSqr(this.ghast) < 4096.0 && this.ghast.hasLineOfSight(target)) {
                    Level level = this.ghast.level();
                    this.chargeTime++;
                    if (this.chargeTime == 10 && !this.ghast.isSilent()) {
                        level.levelEvent(null, LevelEvent.SOUND_GHAST_WARNING, this.ghast.blockPosition(), 0);
                    }

                    if (this.chargeTime == 20) {
                        double d1 = 4.0;
                        Vec3 viewVector = this.ghast.getViewVector(1.0F);
                        double d2 = target.getX() - (this.ghast.getX() + viewVector.x * 4.0);
                        double d3 = target.getY(0.5) - (0.5 + this.ghast.getY(0.5));
                        double d4 = target.getZ() - (this.ghast.getZ() + viewVector.z * 4.0);
                        Vec3 vec3 = new Vec3(d2, d3, d4);
                        if (!this.ghast.isSilent()) {
                            level.levelEvent(null, LevelEvent.SOUND_GHAST_FIREBALL, this.ghast.blockPosition(), 0);
                        }

                        LargeFireball largeFireball = new LargeFireball(level, this.ghast, vec3.normalize(), this.ghast.getExplosionPower());
                        largeFireball.bukkitYield = largeFireball.explosionPower = this.ghast.getExplosionPower(); // CraftBukkit - set bukkitYield when setting explosionPower
                        largeFireball.setPos(this.ghast.getX() + viewVector.x * 4.0, this.ghast.getY(0.5) + 0.5, largeFireball.getZ() + viewVector.z * 4.0);
                        level.addFreshEntity(largeFireball);
                        this.chargeTime = -40;
                    }
                } else if (this.chargeTime > 0) {
                    this.chargeTime--;
                }

                this.ghast.setCharging(this.chargeTime > 10);
            }
        }
    }

    public static class RandomFloatAroundGoal extends Goal {
        private static final int MAX_ATTEMPTS = 64;
        private final Mob ghast;
        private final int distanceToBlocks;

        public RandomFloatAroundGoal(Mob ghast) {
            this(ghast, 0);
        }

        public RandomFloatAroundGoal(Mob ghast, int distanceToBlocks) {
            this.ghast = ghast;
            this.distanceToBlocks = distanceToBlocks;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            MoveControl moveControl = this.ghast.getMoveControl();
            if (!moveControl.hasWanted()) {
                return true;
            } else {
                double d = moveControl.getWantedX() - this.ghast.getX();
                double d1 = moveControl.getWantedY() - this.ghast.getY();
                double d2 = moveControl.getWantedZ() - this.ghast.getZ();
                double d3 = d * d + d1 * d1 + d2 * d2;
                return d3 < 1.0 || d3 > 3600.0;
            }
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            Vec3 suitableFlyToPosition = getSuitableFlyToPosition(this.ghast, this.distanceToBlocks);
            this.ghast.getMoveControl().setWantedPosition(suitableFlyToPosition.x(), suitableFlyToPosition.y(), suitableFlyToPosition.z(), 1.0);
        }

        public static Vec3 getSuitableFlyToPosition(Mob mob, int distanceToBlocks) {
            Level level = mob.level();
            RandomSource random = mob.getRandom();
            Vec3 vec3 = mob.position();
            Vec3 vec31 = null;

            for (int i = 0; i < 64; i++) {
                vec31 = chooseRandomPositionWithRestriction(mob, vec3, random);
                if (vec31 != null && isGoodTarget(level, vec31, distanceToBlocks)) {
                    return vec31;
                }
            }

            if (vec31 == null) {
                vec31 = chooseRandomPosition(vec3, random);
            }

            BlockPos blockPos = BlockPos.containing(vec31);
            int height = level.getHeight(Heightmap.Types.MOTION_BLOCKING, blockPos.getX(), blockPos.getZ());
            if (height < blockPos.getY() && height > level.getMinY()) {
                vec31 = new Vec3(vec31.x(), mob.getY() - Math.abs(mob.getY() - vec31.y()), vec31.z());
            }

            return vec31;
        }

        private static boolean isGoodTarget(Level level, Vec3 pos, int distanceToBlocks) {
            if (distanceToBlocks <= 0) {
                return true;
            } else {
                BlockPos blockPos = BlockPos.containing(pos);
                if (!level.getBlockState(blockPos).isAir()) {
                    return false;
                } else {
                    for (Direction direction : Direction.values()) {
                        for (int i = 1; i < distanceToBlocks; i++) {
                            BlockPos blockPos1 = blockPos.relative(direction, i);
                            if (!level.getBlockState(blockPos1).isAir()) {
                                return true;
                            }
                        }
                    }

                    return false;
                }
            }
        }

        private static Vec3 chooseRandomPosition(Vec3 pos, RandomSource random) {
            double d = pos.x() + (random.nextFloat() * 2.0F - 1.0F) * 16.0F;
            double d1 = pos.y() + (random.nextFloat() * 2.0F - 1.0F) * 16.0F;
            double d2 = pos.z() + (random.nextFloat() * 2.0F - 1.0F) * 16.0F;
            return new Vec3(d, d1, d2);
        }

        private static @Nullable Vec3 chooseRandomPositionWithRestriction(Mob mob, Vec3 pos, RandomSource random) {
            Vec3 vec3 = chooseRandomPosition(pos, random);
            return mob.hasHome() && !mob.isWithinHome(vec3) ? null : vec3;
        }
    }
}
