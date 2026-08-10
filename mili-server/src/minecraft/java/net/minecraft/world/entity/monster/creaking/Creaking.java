package net.minecraft.world.entity.monster.creaking;

import com.mojang.serialization.Dynamic;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.control.JumpControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CreakingHeartBlock;
import net.minecraft.world.level.block.entity.CreakingHeartBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.CreakingHeartState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class Creaking extends Monster {
    private static final EntityDataAccessor<Boolean> CAN_MOVE = SynchedEntityData.defineId(Creaking.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> IS_ACTIVE = SynchedEntityData.defineId(Creaking.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> IS_TEARING_DOWN = SynchedEntityData.defineId(Creaking.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Optional<BlockPos>> HOME_POS = SynchedEntityData.defineId(Creaking.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final int ATTACK_ANIMATION_DURATION = 15;
    private static final int MAX_HEALTH = 1;
    private static final float ATTACK_DAMAGE = 3.0F;
    private static final float FOLLOW_RANGE = 32.0F;
    private static final float ACTIVATION_RANGE_SQ = 144.0F;
    public static final int ATTACK_INTERVAL = 40;
    private static final float MOVEMENT_SPEED_WHEN_FIGHTING = 0.4F;
    public static final float SPEED_MULTIPLIER_WHEN_IDLING = 0.3F;
    public static final int CREAKING_ORANGE = 16545810;
    public static final int CREAKING_GRAY = 6250335;
    public static final int INVULNERABILITY_ANIMATION_DURATION = 8;
    public static final int TWITCH_DEATH_DURATION = 45;
    private static final int MAX_PLAYER_STUCK_COUNTER = 4;
    private int attackAnimationRemainingTicks;
    public final AnimationState attackAnimationState = new AnimationState();
    public final AnimationState invulnerabilityAnimationState = new AnimationState();
    public final AnimationState deathAnimationState = new AnimationState();
    private int invulnerabilityAnimationRemainingTicks;
    private boolean eyesGlowing;
    private int nextFlickerTime;
    private int playerStuckCounter;

    public Creaking(EntityType<? extends Creaking> type, Level level) {
        super(type, level);
        this.lookControl = new Creaking.CreakingLookControl(this);
        this.moveControl = new Creaking.CreakingMoveControl(this);
        this.jumpControl = new Creaking.CreakingJumpControl(this);
        GroundPathNavigation groundPathNavigation = (GroundPathNavigation)this.getNavigation();
        groundPathNavigation.setCanFloat(true);
        this.xpReward = 0;
    }

    public void setTransient(BlockPos homePos) {
        this.setHomePos(homePos);
        this.setPathfindingMalus(PathType.DAMAGE_OTHER, 8.0F);
        this.setPathfindingMalus(PathType.POWDER_SNOW, 8.0F);
        this.setPathfindingMalus(PathType.LAVA, 8.0F);
        this.setPathfindingMalus(PathType.DAMAGE_FIRE, 0.0F);
        this.setPathfindingMalus(PathType.DANGER_FIRE, 0.0F);
    }

    public boolean isHeartBound() {
        return this.getHomePos() != null;
    }

    @Override
    protected BodyRotationControl createBodyControl() {
        return new Creaking.CreakingBodyRotationControl(this);
    }

    @Override
    protected Brain.Provider<Creaking> brainProvider() {
        return CreakingAi.brainProvider();
    }

    @Override
    protected Brain<?> makeBrain(Dynamic<?> dynamic) {
        return CreakingAi.makeBrain(this, this.brainProvider().makeBrain(dynamic));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CAN_MOVE, true);
        builder.define(IS_ACTIVE, false);
        builder.define(IS_TEARING_DOWN, false);
        builder.define(HOME_POS, Optional.empty());
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 1.0)
            .add(Attributes.MOVEMENT_SPEED, 0.4F)
            .add(Attributes.ATTACK_DAMAGE, 3.0)
            .add(Attributes.FOLLOW_RANGE, 32.0)
            .add(Attributes.STEP_HEIGHT, 1.0625);
    }

    public boolean canMove() {
        return this.entityData.get(CAN_MOVE);
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        if (!(target instanceof LivingEntity)) {
            return false;
        } else {
            this.attackAnimationRemainingTicks = 15;
            this.level().broadcastEntityEvent(this, EntityEvent.START_ATTACKING);
            return super.doHurtTarget(level, target);
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        BlockPos homePos = this.getHomePos();
        if (homePos == null || damageSource.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurtServer(level, damageSource, amount);
        } else if (!this.isInvulnerableTo(level, damageSource) && this.invulnerabilityAnimationRemainingTicks <= 0 && !this.isDeadOrDying()) {
            Player player = this.blameSourceForDamage(damageSource);
            Entity directEntity = damageSource.getDirectEntity();
            if (!(directEntity instanceof LivingEntity) && !(directEntity instanceof Projectile) && player == null) {
                return false;
            } else {
                this.invulnerabilityAnimationRemainingTicks = 8;
                this.level().broadcastEntityEvent(this, EntityEvent.SHAKE);
                this.gameEvent(GameEvent.ENTITY_ACTION);
                if (this.level().getBlockEntity(homePos) instanceof CreakingHeartBlockEntity creakingHeartBlockEntity
                    && creakingHeartBlockEntity.isProtector(this)) {
                    if (player != null) {
                        creakingHeartBlockEntity.creakingHurt();
                    }

                    this.playHurtSound(damageSource);
                }

                return true;
            }
        } else {
            return false;
        }
    }

    public Player blameSourceForDamage(DamageSource damageSource) {
        this.resolveMobResponsibleForDamage(damageSource);
        return this.resolvePlayerResponsibleForDamage(damageSource);
    }

    @Override
    public boolean isPushable() {
        return super.isPushable() && this.canMove();
    }

    @Override
    public void push(double x, double y, double z, @Nullable Entity pushingEntity) { // Paper - add push source entity param
        if (this.canMove()) {
            super.push(x, y, z, pushingEntity); // Paper - add push source entity param
        }
    }

    @Override
    public Brain<Creaking> getBrain() {
        return (Brain<Creaking>)super.getBrain();
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        ProfilerFiller profilerFiller = Profiler.get();
        profilerFiller.push("creakingBrain");
        this.getBrain().tick((ServerLevel)this.level(), this);
        profilerFiller.pop();
        CreakingAi.updateActivity(this);
    }

    @Override
    public void aiStep() {
        if (this.invulnerabilityAnimationRemainingTicks > 0) {
            this.invulnerabilityAnimationRemainingTicks--;
        }

        if (this.attackAnimationRemainingTicks > 0) {
            this.attackAnimationRemainingTicks--;
        }

        if (!this.level().isClientSide()) {
            boolean flag = this.entityData.get(CAN_MOVE);
            boolean flag1 = this.checkCanMove();
            if (flag1 != flag) {
                this.gameEvent(GameEvent.ENTITY_ACTION);
                if (flag1) {
                    this.makeSound(SoundEvents.CREAKING_UNFREEZE);
                } else {
                    this.stopInPlace();
                    this.makeSound(SoundEvents.CREAKING_FREEZE);
                }
            }

            this.entityData.set(CAN_MOVE, flag1);
        }

        super.aiStep();
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide()) {
            BlockPos homePos = this.getHomePos();
            if (homePos != null) {
                boolean flag = this.level().getBlockEntity(homePos) instanceof CreakingHeartBlockEntity creakingHeartBlockEntity
                    && creakingHeartBlockEntity.isProtector(this);
                if (!flag) {
                    this.setHealth(0.0F);
                }
            }
        }

        super.tick();
        if (this.level().isClientSide()) {
            this.setupAnimationStates();
            this.checkEyeBlink();
        }
    }

    @Override
    protected void tickDeath() {
        if (this.isHeartBound() && this.isTearingDown()) {
            this.deathTime++;
            if (!this.level().isClientSide() && this.deathTime > 45 && !this.isRemoved()) {
                this.tearDown();
            }
        } else {
            super.tickDeath();
        }
    }

    @Override
    protected void updateWalkAnimation(float partialTick) {
        float min = Math.min(partialTick * 25.0F, 3.0F);
        this.walkAnimation.update(min, 0.4F, 1.0F);
    }

    private void setupAnimationStates() {
        this.attackAnimationState.animateWhen(this.attackAnimationRemainingTicks > 0, this.tickCount);
        this.invulnerabilityAnimationState.animateWhen(this.invulnerabilityAnimationRemainingTicks > 0, this.tickCount);
        this.deathAnimationState.animateWhen(this.isTearingDown(), this.tickCount);
    }

    public void tearDown() {
        if (this.level() instanceof ServerLevel serverLevel) {
            AABB boundingBox = this.getBoundingBox();
            Vec3 center = boundingBox.getCenter();
            double d = boundingBox.getXsize() * 0.3;
            double d1 = boundingBox.getYsize() * 0.3;
            double d2 = boundingBox.getZsize() * 0.3;
            serverLevel.sendParticles(
                new BlockParticleOption(ParticleTypes.BLOCK_CRUMBLE, Blocks.PALE_OAK_WOOD.defaultBlockState()),
                center.x,
                center.y,
                center.z,
                100,
                d,
                d1,
                d2,
                0.0
            );
            serverLevel.sendParticles(
                new BlockParticleOption(
                    ParticleTypes.BLOCK_CRUMBLE, Blocks.CREAKING_HEART.defaultBlockState().setValue(CreakingHeartBlock.STATE, CreakingHeartState.AWAKE)
                ),
                center.x,
                center.y,
                center.z,
                10,
                d,
                d1,
                d2,
                0.0
            );
        }

        this.makeSound(this.getDeathSound());
        this.remove(Entity.RemovalReason.DISCARDED, null); // CraftBukkit - add Bukkit remove cause
    }

    public void creakingDeathEffects(DamageSource damageSource) {
        this.blameSourceForDamage(damageSource);
        this.die(damageSource);
        this.makeSound(SoundEvents.CREAKING_TWITCH);
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EntityEvent.SHAKE) {
            this.invulnerabilityAnimationRemainingTicks = 8;
            this.playHurtSound(this.damageSources().generic());
        } else if (id == EntityEvent.START_ATTACKING) {
            this.attackAnimationRemainingTicks = 15;
            this.playAttackSound();
        } else {
            super.handleEntityEvent(id);
        }
    }

    @Override
    public boolean fireImmune() {
        return this.isHeartBound() || super.fireImmune();
    }

    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return !this.isHeartBound() && super.canUsePortal(allowPassengers);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new Creaking.CreakingPathNavigation(this, level);
    }

    public boolean playerIsStuckInYou() {
        List<Player> list = this.brain.getMemory(MemoryModuleType.NEAREST_PLAYERS).orElse(List.of());
        if (list.isEmpty()) {
            this.playerStuckCounter = 0;
            return false;
        } else {
            AABB boundingBox = this.getBoundingBox();

            for (Player player : list) {
                if (boundingBox.contains(player.getEyePosition())) {
                    this.playerStuckCounter++;
                    return this.playerStuckCounter > 4;
                }
            }

            this.playerStuckCounter = 0;
            return false;
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        input.read("home_pos", BlockPos.CODEC).ifPresent(this::setTransient);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.storeNullable("home_pos", BlockPos.CODEC, this.getHomePos());
    }

    public void setHomePos(BlockPos homePos) {
        this.entityData.set(HOME_POS, Optional.of(homePos));
    }

    public @Nullable BlockPos getHomePos() {
        return this.entityData.get(HOME_POS).orElse(null);
    }

    public void setTearingDown() {
        this.entityData.set(IS_TEARING_DOWN, true);
    }

    public boolean isTearingDown() {
        return this.entityData.get(IS_TEARING_DOWN);
    }

    public boolean hasGlowingEyes() {
        return this.eyesGlowing;
    }

    public void checkEyeBlink() {
        if (this.deathTime > this.nextFlickerTime) {
            this.nextFlickerTime = this.deathTime
                + this.getRandom().nextIntBetweenInclusive(this.eyesGlowing ? 2 : this.deathTime / 4, this.eyesGlowing ? 8 : this.deathTime / 2);
            this.eyesGlowing = !this.eyesGlowing;
        }
    }

    @Override
    public void playAttackSound() {
        this.makeSound(SoundEvents.CREAKING_ATTACK);
    }

    @Override
    public SoundEvent getAmbientSound() {
        return this.isActive() ? null : SoundEvents.CREAKING_AMBIENT;
    }

    @Override
    public SoundEvent getHurtSound(DamageSource damageSource) {
        return this.isHeartBound() ? SoundEvents.CREAKING_SWAY : super.getHurtSound(damageSource);
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.CREAKING_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.CREAKING_STEP, 0.15F, 1.0F);
    }

    @Override
    public @Nullable LivingEntity getTarget() {
        return this.getTargetFromBrain();
    }

    @Override
    public void knockback(double strength, double x, double z, @Nullable Entity attacker, io.papermc.paper.event.entity.EntityKnockbackEvent.Cause cause) { // Paper - knockback events
        if (this.canMove()) {
            super.knockback(strength, x, z, attacker, cause); // Paper - knockback events
        }
    }

    public boolean checkCanMove() {
        List<Player> list = this.brain.getMemory(MemoryModuleType.NEAREST_PLAYERS).orElse(List.of());
        boolean isActive = this.isActive();
        if (list.isEmpty()) {
            if (isActive) {
                this.deactivate();
            }

            return true;
        } else {
            boolean flag = false;

            for (Player player : list) {
                if (this.canAttack(player) && !this.isAlliedTo(player)) {
                    flag = true;
                    if ((!isActive || LivingEntity.PLAYER_NOT_WEARING_DISGUISE_ITEM.test(player))
                        && this.isLookingAtMe(
                            player, 0.5, false, true, this.getEyeY(), this.getY() + 0.5 * this.getScale(), (this.getEyeY() + this.getY()) / 2.0
                        )) {
                        if (isActive) {
                            return false;
                        }

                        if (player.distanceToSqr(this) < 144.0) {
                            this.activate(player);
                            return false;
                        }
                    }
                }
            }

            if (!flag && isActive) {
                this.deactivate();
            }

            return true;
        }
    }

    public void activate(Player player) {
        this.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, player);
        this.gameEvent(GameEvent.ENTITY_ACTION);
        this.makeSound(SoundEvents.CREAKING_ACTIVATE);
        this.setIsActive(true);
    }

    public void deactivate() {
        this.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        this.gameEvent(GameEvent.ENTITY_ACTION);
        this.makeSound(SoundEvents.CREAKING_DEACTIVATE);
        this.setIsActive(false);
    }

    public void setIsActive(boolean isActive) {
        this.entityData.set(IS_ACTIVE, isActive);
    }

    public boolean isActive() {
        return this.entityData.get(IS_ACTIVE);
    }

    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        return 0.0F;
    }

    class CreakingBodyRotationControl extends BodyRotationControl {
        public CreakingBodyRotationControl(final Creaking mob) {
            super(mob);
        }

        @Override
        public void clientTick() {
            if (Creaking.this.canMove()) {
                super.clientTick();
            }
        }
    }

    class CreakingJumpControl extends JumpControl {
        public CreakingJumpControl(final Creaking mob) {
            super(mob);
        }

        @Override
        public void tick() {
            if (Creaking.this.canMove()) {
                super.tick();
            } else {
                Creaking.this.setJumping(false);
            }
        }
    }

    class CreakingLookControl extends LookControl {
        public CreakingLookControl(final Creaking mob) {
            super(mob);
        }

        @Override
        public void tick() {
            if (Creaking.this.canMove()) {
                super.tick();
            }
        }
    }

    class CreakingMoveControl extends MoveControl {
        public CreakingMoveControl(final Creaking mob) {
            super(mob);
        }

        @Override
        public void tick() {
            if (Creaking.this.canMove()) {
                super.tick();
            }
        }
    }

    class CreakingPathNavigation extends GroundPathNavigation {
        CreakingPathNavigation(final Creaking mob, final Level level) {
            super(mob, level);
        }

        @Override
        public void tick() {
            if (Creaking.this.canMove()) {
                super.tick();
            }
        }

        @Override
        protected PathFinder createPathFinder(int maxVisitedNodes) {
            this.nodeEvaluator = Creaking.this.new HomeNodeEvaluator();
            this.nodeEvaluator.setCanPassDoors(true);
            return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
        }
    }

    class HomeNodeEvaluator extends WalkNodeEvaluator {
        private static final int MAX_DISTANCE_TO_HOME_SQ = 1024;

        @Override
        public PathType getPathType(PathfindingContext context, int x, int y, int z) {
            BlockPos homePos = Creaking.this.getHomePos();
            if (homePos == null) {
                return super.getPathType(context, x, y, z);
            } else {
                double d = homePos.distSqr(new Vec3i(x, y, z));
                return d > 1024.0 && d >= homePos.distSqr(context.mobPosition()) ? PathType.BLOCKED : super.getPathType(context, x, y, z);
            }
        }
    }
}
