package net.minecraft.world.entity.monster;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class Phantom extends Mob implements Enemy {
    public static final float FLAP_DEGREES_PER_TICK = 7.448451F;
    public static final int TICKS_PER_FLAP = Mth.ceil(24.166098F);
    private static final EntityDataAccessor<Integer> ID_SIZE = SynchedEntityData.defineId(Phantom.class, EntityDataSerializers.INT);
    Vec3 moveTargetPoint = Vec3.ZERO;
    @Nullable public BlockPos anchorPoint;
    Phantom.AttackPhase attackPhase = Phantom.AttackPhase.CIRCLE;
    // Paper start
    public java.util.@Nullable UUID spawningEntity;
    public boolean shouldBurnInDay = true;
    // Paper end

    public Phantom(EntityType<? extends Phantom> type, Level level) {
        super(type, level);
        this.xpReward = 5;
        this.moveControl = new Phantom.PhantomMoveControl(this);
        this.lookControl = new Phantom.PhantomLookControl(this);
    }

    @Override
    public boolean isFlapping() {
        return (this.getUniqueFlapTickOffset() + this.tickCount) % TICKS_PER_FLAP == 0;
    }

    @Override
    protected BodyRotationControl createBodyControl() {
        return new Phantom.PhantomBodyRotationControl(this);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new Phantom.PhantomAttackStrategyGoal());
        this.goalSelector.addGoal(2, new Phantom.PhantomSweepAttackGoal());
        this.goalSelector.addGoal(3, new Phantom.PhantomCircleAroundAnchorGoal());
        this.targetSelector.addGoal(1, new Phantom.PhantomAttackPlayerTargetGoal());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ID_SIZE, 0);
    }

    public void setPhantomSize(int phantomSize) {
        this.entityData.set(ID_SIZE, Mth.clamp(phantomSize, 0, 64));
    }

    private void updatePhantomSizeInfo() {
        this.refreshDimensions();
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(6 + this.getPhantomSize());
    }

    public int getPhantomSize() {
        return this.entityData.get(ID_SIZE);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        if (ID_SIZE.equals(key)) {
            this.updatePhantomSizeInfo();
        }

        super.onSyncedDataUpdated(key);
    }

    public int getUniqueFlapTickOffset() {
        return this.getId() * 3;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            float cos = Mth.cos((this.getUniqueFlapTickOffset() + this.tickCount) * 7.448451F * (float) (Math.PI / 180.0) + (float) Math.PI);
            float cos1 = Mth.cos((this.getUniqueFlapTickOffset() + this.tickCount + 1) * 7.448451F * (float) (Math.PI / 180.0) + (float) Math.PI);
            if (cos > 0.0F && cos1 <= 0.0F) {
                this.level()
                    .playLocalSound(
                        this.getX(),
                        this.getY(),
                        this.getZ(),
                        SoundEvents.PHANTOM_FLAP,
                        this.getSoundSource(),
                        0.95F + this.random.nextFloat() * 0.05F,
                        0.95F + this.random.nextFloat() * 0.05F,
                        false
                    );
            }

            float f = this.getBbWidth() * 1.48F;
            float f1 = Mth.cos(this.getYRot() * (float) (Math.PI / 180.0)) * f;
            float f2 = Mth.sin(this.getYRot() * (float) (Math.PI / 180.0)) * f;
            float f3 = (0.3F + cos * 0.45F) * this.getBbHeight() * 2.5F;
            this.level().addParticle(ParticleTypes.MYCELIUM, this.getX() + f1, this.getY() + f3, this.getZ() + f2, 0.0, 0.0, 0.0);
            this.level().addParticle(ParticleTypes.MYCELIUM, this.getX() - f1, this.getY() + f3, this.getZ() - f2, 0.0, 0.0, 0.0);
        }
    }

    // Paper start
    @Override
    public boolean isSunBurnTick() {
        return this.shouldBurnInDay && super.isSunBurnTick();
    }
    // Paper end

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    @Override
    public boolean onClimbable() {
        return false;
    }

    @Override
    public void travel(Vec3 travelVector) {
        this.travelFlying(travelVector, 0.2F);
    }

    @Override
    public SpawnGroupData finalizeSpawn(
        ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData spawnGroupData
    ) {
        this.anchorPoint = this.blockPosition().above(5);
        this.setPhantomSize(0);
        return super.finalizeSpawn(level, difficulty, spawnReason, spawnGroupData);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.anchorPoint = input.read("anchor_pos", BlockPos.CODEC).orElse(null);
        this.setPhantomSize(input.getIntOr("size", 0));
        // Paper start
        this.spawningEntity = input.read("Paper.SpawningEntity", net.minecraft.core.UUIDUtil.CODEC).orElse(null);
        this.shouldBurnInDay = input.getBooleanOr("Paper.ShouldBurnInDay", true);
        // Paper end
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.storeNullable("anchor_pos", BlockPos.CODEC, this.anchorPoint);
        output.putInt("size", this.getPhantomSize());
        // Paper start
        output.storeNullable("Paper.SpawningEntity", net.minecraft.core.UUIDUtil.CODEC, this.spawningEntity);
        output.putBoolean("Paper.ShouldBurnInDay", this.shouldBurnInDay);
        // Paper end
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return true;
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.HOSTILE;
    }

    @Override
    public SoundEvent getAmbientSound() {
        return SoundEvents.PHANTOM_AMBIENT;
    }

    @Override
    public SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.PHANTOM_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.PHANTOM_DEATH;
    }

    @Override
    public float getSoundVolume() {
        return 1.0F;
    }

    @Override
    public boolean canAttackType(EntityType<?> type) {
        return true;
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        int phantomSize = this.getPhantomSize();
        EntityDimensions entityDimensions = super.getDefaultDimensions(pose);
        return entityDimensions.scale(1.0F + 0.15F * phantomSize);
    }

    boolean canAttack(ServerLevel level, LivingEntity entity, TargetingConditions targetingConditions) {
        return targetingConditions.test(level, this, entity);
    }

    static enum AttackPhase {
        CIRCLE,
        SWOOP;
    }

    class PhantomAttackPlayerTargetGoal extends Goal {
        private final TargetingConditions attackTargeting = TargetingConditions.forCombat().range(64.0);
        private int nextScanTick = reducedTickDelay(20);

        @Override
        public boolean canUse() {
            if (this.nextScanTick > 0) {
                this.nextScanTick--;
                return false;
            } else {
                this.nextScanTick = reducedTickDelay(60);
                ServerLevel serverLevel = getServerLevel(Phantom.this.level());
                List<Player> nearbyPlayers = serverLevel.getNearbyPlayers(
                    this.attackTargeting, Phantom.this, Phantom.this.getBoundingBox().inflate(16.0, 64.0, 16.0)
                );
                if (!nearbyPlayers.isEmpty()) {
                    nearbyPlayers.sort(Comparator.<Player, Double>comparing(Entity::getY).reversed());

                    for (Player player : nearbyPlayers) {
                        if (Phantom.this.canAttack(serverLevel, player, TargetingConditions.DEFAULT)) {
                            if (!level().paperConfig().entities.behavior.phantomsOnlyAttackInsomniacs || EntitySelector.IS_INSOMNIAC.test(player)) { // Paper - Add phantom creative and insomniac controls
                            Phantom.this.setTarget(player, org.bukkit.event.entity.EntityTargetEvent.TargetReason.CLOSEST_PLAYER); // CraftBukkit - reason
                            return true;
                            } // Paper - Add phantom creative and insomniac controls
                        }
                    }
                }

                return false;
            }
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity target = Phantom.this.getTarget();
            return target != null && Phantom.this.canAttack(getServerLevel(Phantom.this.level()), target, TargetingConditions.DEFAULT);
        }
    }

    class PhantomAttackStrategyGoal extends Goal {
        private int nextSweepTick;

        @Override
        public boolean canUse() {
            LivingEntity target = Phantom.this.getTarget();
            return target != null && Phantom.this.canAttack(getServerLevel(Phantom.this.level()), target, TargetingConditions.DEFAULT);
        }

        @Override
        public void start() {
            this.nextSweepTick = this.adjustedTickDelay(10);
            Phantom.this.attackPhase = Phantom.AttackPhase.CIRCLE;
            this.setAnchorAboveTarget();
        }

        @Override
        public void stop() {
            if (Phantom.this.anchorPoint != null) {
                Phantom.this.anchorPoint = Phantom.this.level()
                    .getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, Phantom.this.anchorPoint)
                    .above(10 + Phantom.this.random.nextInt(20));
            }
        }

        @Override
        public void tick() {
            if (Phantom.this.attackPhase == Phantom.AttackPhase.CIRCLE) {
                this.nextSweepTick--;
                if (this.nextSweepTick <= 0) {
                    Phantom.this.attackPhase = Phantom.AttackPhase.SWOOP;
                    this.setAnchorAboveTarget();
                    this.nextSweepTick = this.adjustedTickDelay((8 + Phantom.this.random.nextInt(4)) * 20);
                    Phantom.this.playSound(SoundEvents.PHANTOM_SWOOP, 10.0F, 0.95F + Phantom.this.random.nextFloat() * 0.1F);
                }
            }
        }

        private void setAnchorAboveTarget() {
            if (Phantom.this.anchorPoint != null) {
                Phantom.this.anchorPoint = Phantom.this.getTarget().blockPosition().above(20 + Phantom.this.random.nextInt(20));
                if (Phantom.this.anchorPoint.getY() < Phantom.this.level().getSeaLevel()) {
                    Phantom.this.anchorPoint = new BlockPos(
                        Phantom.this.anchorPoint.getX(), Phantom.this.level().getSeaLevel() + 1, Phantom.this.anchorPoint.getZ()
                    );
                }
            }
        }
    }

    class PhantomBodyRotationControl extends BodyRotationControl {
        public PhantomBodyRotationControl(final Mob mob) {
            super(mob);
        }

        @Override
        public void clientTick() {
            Phantom.this.yHeadRot = Phantom.this.yBodyRot;
            Phantom.this.yBodyRot = Phantom.this.getYRot();
        }
    }

    class PhantomCircleAroundAnchorGoal extends Phantom.PhantomMoveTargetGoal {
        private float angle;
        private float distance;
        private float height;
        private float clockwise;

        @Override
        public boolean canUse() {
            return Phantom.this.getTarget() == null || Phantom.this.attackPhase == Phantom.AttackPhase.CIRCLE;
        }

        @Override
        public void start() {
            this.distance = 5.0F + Phantom.this.random.nextFloat() * 10.0F;
            this.height = -4.0F + Phantom.this.random.nextFloat() * 9.0F;
            this.clockwise = Phantom.this.random.nextBoolean() ? 1.0F : -1.0F;
            this.selectNext();
        }

        @Override
        public void tick() {
            if (Phantom.this.random.nextInt(this.adjustedTickDelay(350)) == 0) {
                this.height = -4.0F + Phantom.this.random.nextFloat() * 9.0F;
            }

            if (Phantom.this.random.nextInt(this.adjustedTickDelay(250)) == 0) {
                this.distance++;
                if (this.distance > 15.0F) {
                    this.distance = 5.0F;
                    this.clockwise = -this.clockwise;
                }
            }

            if (Phantom.this.random.nextInt(this.adjustedTickDelay(450)) == 0) {
                this.angle = Phantom.this.random.nextFloat() * 2.0F * (float) Math.PI;
                this.selectNext();
            }

            if (this.touchingTarget()) {
                this.selectNext();
            }

            if (Phantom.this.moveTargetPoint.y < Phantom.this.getY() && !Phantom.this.level().isEmptyBlock(Phantom.this.blockPosition().below(1))) {
                this.height = Math.max(1.0F, this.height);
                this.selectNext();
            }

            if (Phantom.this.moveTargetPoint.y > Phantom.this.getY() && !Phantom.this.level().isEmptyBlock(Phantom.this.blockPosition().above(1))) {
                this.height = Math.min(-1.0F, this.height);
                this.selectNext();
            }
        }

        private void selectNext() {
            if (Phantom.this.anchorPoint == null) {
                Phantom.this.anchorPoint = Phantom.this.blockPosition();
            }

            this.angle = this.angle + this.clockwise * 15.0F * (float) (Math.PI / 180.0);
            Phantom.this.moveTargetPoint = Vec3.atLowerCornerOf(Phantom.this.anchorPoint)
                .add(this.distance * Mth.cos(this.angle), -4.0F + this.height, this.distance * Mth.sin(this.angle));
        }
    }

    static class PhantomLookControl extends LookControl {
        public PhantomLookControl(Mob mob) {
            super(mob);
        }

        @Override
        public void tick() {
        }
    }

    class PhantomMoveControl extends MoveControl {
        private float speed = 0.1F;

        public PhantomMoveControl(final Mob mob) {
            super(mob);
        }

        @Override
        public void tick() {
            if (Phantom.this.horizontalCollision) {
                Phantom.this.setYRot(Phantom.this.getYRot() + 180.0F);
                this.speed = 0.1F;
            }

            double d = Phantom.this.moveTargetPoint.x - Phantom.this.getX();
            double d1 = Phantom.this.moveTargetPoint.y - Phantom.this.getY();
            double d2 = Phantom.this.moveTargetPoint.z - Phantom.this.getZ();
            double squareRoot = Math.sqrt(d * d + d2 * d2);
            if (Math.abs(squareRoot) > 1.0E-5F) {
                double d3 = 1.0 - Math.abs(d1 * 0.7F) / squareRoot;
                d *= d3;
                d2 *= d3;
                squareRoot = Math.sqrt(d * d + d2 * d2);
                double squareRoot1 = Math.sqrt(d * d + d2 * d2 + d1 * d1);
                float yRot = Phantom.this.getYRot();
                float f = (float)Mth.atan2(d2, d);
                float f1 = Mth.wrapDegrees(Phantom.this.getYRot() + 90.0F);
                float f2 = Mth.wrapDegrees(f * (180.0F / (float)Math.PI));
                Phantom.this.setYRot(Mth.approachDegrees(f1, f2, 4.0F) - 90.0F);
                Phantom.this.yBodyRot = Phantom.this.getYRot();
                if (Mth.degreesDifferenceAbs(yRot, Phantom.this.getYRot()) < 3.0F) {
                    this.speed = Mth.approach(this.speed, 1.8F, 0.005F * (1.8F / this.speed));
                } else {
                    this.speed = Mth.approach(this.speed, 0.2F, 0.025F);
                }

                float f3 = (float)(-(Mth.atan2(-d1, squareRoot) * 180.0F / (float)Math.PI));
                Phantom.this.setXRot(f3);
                float f4 = Phantom.this.getYRot() + 90.0F;
                double d4 = this.speed * Mth.cos(f4 * (float) (Math.PI / 180.0)) * Math.abs(d / squareRoot1);
                double d5 = this.speed * Mth.sin(f4 * (float) (Math.PI / 180.0)) * Math.abs(d2 / squareRoot1);
                double d6 = this.speed * Mth.sin(f3 * (float) (Math.PI / 180.0)) * Math.abs(d1 / squareRoot1);
                Vec3 deltaMovement = Phantom.this.getDeltaMovement();
                Phantom.this.setDeltaMovement(deltaMovement.add(new Vec3(d4, d6, d5).subtract(deltaMovement).scale(0.2)));
            }
        }
    }

    abstract class PhantomMoveTargetGoal extends Goal {
        public PhantomMoveTargetGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        protected boolean touchingTarget() {
            return Phantom.this.moveTargetPoint.distanceToSqr(Phantom.this.getX(), Phantom.this.getY(), Phantom.this.getZ()) < 4.0;
        }
    }

    class PhantomSweepAttackGoal extends Phantom.PhantomMoveTargetGoal {
        private static final int CAT_SEARCH_TICK_DELAY = 20;
        private boolean isScaredOfCat;
        private int catSearchTick;

        @Override
        public boolean canUse() {
            return Phantom.this.getTarget() != null && Phantom.this.attackPhase == Phantom.AttackPhase.SWOOP;
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity target = Phantom.this.getTarget();
            if (target == null) {
                return false;
            } else if (!target.isAlive()) {
                return false;
            } else if (target instanceof Player player && (target.isSpectator() || player.isCreative())) {
                return false;
            } else if (!this.canUse()) {
                return false;
            } else {
                if (Phantom.this.tickCount > this.catSearchTick) {
                    this.catSearchTick = Phantom.this.tickCount + 20;
                    List<Cat> entitiesOfClass = Phantom.this.level()
                        .getEntitiesOfClass(Cat.class, Phantom.this.getBoundingBox().inflate(16.0), EntitySelector.ENTITY_STILL_ALIVE);

                    for (Cat cat : entitiesOfClass) {
                        cat.hiss();
                    }

                    this.isScaredOfCat = !entitiesOfClass.isEmpty();
                }

                return !this.isScaredOfCat;
            }
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
            Phantom.this.setTarget(null);
            Phantom.this.attackPhase = Phantom.AttackPhase.CIRCLE;
        }

        @Override
        public void tick() {
            LivingEntity target = Phantom.this.getTarget();
            if (target != null) {
                Phantom.this.moveTargetPoint = new Vec3(target.getX(), target.getY(0.5), target.getZ());
                if (Phantom.this.getBoundingBox().inflate(0.2F).intersects(target.getBoundingBox())) {
                    Phantom.this.doHurtTarget(getServerLevel(Phantom.this.level()), target);
                    Phantom.this.attackPhase = Phantom.AttackPhase.CIRCLE;
                    if (!Phantom.this.isSilent()) {
                        Phantom.this.level().levelEvent(LevelEvent.SOUND_PHANTOM_BITE, Phantom.this.blockPosition(), 0);
                    }
                } else if (Phantom.this.horizontalCollision || Phantom.this.hurtTime > 0) {
                    Phantom.this.attackPhase = Phantom.AttackPhase.CIRCLE;
                }
            }
        }
    }
}
