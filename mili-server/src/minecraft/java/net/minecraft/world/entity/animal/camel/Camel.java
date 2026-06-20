package net.minecraft.world.entity.animal.camel;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.serialization.Dynamic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class Camel extends AbstractHorse {
    public static final float BABY_SCALE = 0.45F;
    public static final int DASH_COOLDOWN_TICKS = 55;
    public static final int MAX_HEAD_Y_ROT = 30;
    private static final float RUNNING_SPEED_BONUS = 0.1F;
    private static final float DASH_VERTICAL_MOMENTUM = 1.4285F;
    private static final float DASH_HORIZONTAL_MOMENTUM = 22.2222F;
    private static final int DASH_MINIMUM_DURATION_TICKS = 5;
    private static final int SITDOWN_DURATION_TICKS = 40;
    private static final int STANDUP_DURATION_TICKS = 52;
    private static final int IDLE_MINIMAL_DURATION_TICKS = 80;
    private static final float SITTING_HEIGHT_DIFFERENCE = 1.43F;
    private static final long DEFAULT_LAST_POSE_CHANGE_TICK = 0L;
    public static final EntityDataAccessor<Boolean> DASH = SynchedEntityData.defineId(Camel.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Long> LAST_POSE_CHANGE_TICK = SynchedEntityData.defineId(Camel.class, EntityDataSerializers.LONG);
    public final AnimationState sitAnimationState = new AnimationState();
    public final AnimationState sitPoseAnimationState = new AnimationState();
    public final AnimationState sitUpAnimationState = new AnimationState();
    public final AnimationState idleAnimationState = new AnimationState();
    public final AnimationState dashAnimationState = new AnimationState();
    private static final EntityDimensions SITTING_DIMENSIONS = EntityDimensions.scalable(EntityType.CAMEL.getWidth(), EntityType.CAMEL.getHeight() - 1.43F)
        .withEyeHeight(0.845F);
    private int dashCooldown = 0;
    private int idleAnimationTimeout = 0;

    public Camel(EntityType<? extends Camel> type, Level level) {
        super(type, level);
        this.moveControl = new Camel.CamelMoveControl();
        this.lookControl = new Camel.CamelLookControl();
        GroundPathNavigation groundPathNavigation = (GroundPathNavigation)this.getNavigation();
        groundPathNavigation.setCanFloat(true);
        groundPathNavigation.setCanWalkOverFences(true);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putLong("LastPoseTick", this.entityData.get(LAST_POSE_CHANGE_TICK));
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        long longOr = input.getLongOr("LastPoseTick", 0L);
        if (longOr < 0L) {
            this.setPose(Pose.SITTING);
        }

        this.resetLastPoseChangeTick(longOr);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createBaseHorseAttributes()
            .add(Attributes.MAX_HEALTH, 32.0)
            .add(Attributes.MOVEMENT_SPEED, 0.09F)
            .add(Attributes.JUMP_STRENGTH, 0.42F)
            .add(Attributes.STEP_HEIGHT, 1.5);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DASH, false);
        builder.define(LAST_POSE_CHANGE_TICK, 0L);
    }

    @Override
    public SpawnGroupData finalizeSpawn(
        ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData spawnGroupData
    ) {
        CamelAi.initMemories(this, level.getRandom());
        this.resetLastPoseChangeTickToFullStand(level.getLevel().getGameTime());
        return super.finalizeSpawn(level, difficulty, spawnReason, spawnGroupData);
    }

    public static boolean checkCamelSpawnRules(
        EntityType<Camel> entityType, LevelAccessor level, EntitySpawnReason spawnReason, BlockPos pos, RandomSource random
    ) {
        return level.getBlockState(pos.below()).is(BlockTags.CAMELS_SPAWNABLE_ON) && isBrightEnoughToSpawn(level, pos);
    }

    @Override
    protected Brain.Provider<Camel> brainProvider() {
        return CamelAi.brainProvider();
    }

    @Override
    protected void registerGoals() {
    }

    @Override
    protected Brain<?> makeBrain(Dynamic<?> dynamic) {
        return CamelAi.makeBrain(this.brainProvider().makeBrain(dynamic));
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        return pose == Pose.SITTING ? SITTING_DIMENSIONS.scale(this.getAgeScale()) : super.getDefaultDimensions(pose);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        ProfilerFiller profilerFiller = Profiler.get();
        profilerFiller.push("camelBrain");
        Brain<?> brain = this.getBrain();
        ((Brain<Camel>)brain).tick(level, this);
        profilerFiller.pop();
        profilerFiller.push("camelActivityUpdate");
        CamelAi.updateActivity(this);
        profilerFiller.pop();
        super.customServerAiStep(level);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.isDashing() && this.dashCooldown < 50 && (this.onGround() || this.isInLiquid() || this.isPassenger())) {
            this.setDashing(false);
        }

        if (this.dashCooldown > 0) {
            this.dashCooldown--;
            if (this.dashCooldown == 0) {
                this.level().playSound(null, this.blockPosition(), this.getDashReadySound(), SoundSource.NEUTRAL, 1.0F, 1.0F);
            }
        }

        if (this.level().isClientSide()) {
            this.setupAnimationStates();
        }

        if (this.refuseToMove()) {
            this.clampHeadRotationToBody();
        }

        if (this.isCamelSitting() && this.isInWater()) {
            this.standUpInstantly();
        }
    }

    private void setupAnimationStates() {
        if (this.idleAnimationTimeout <= 0) {
            this.idleAnimationTimeout = this.random.nextInt(40) + 80;
            this.idleAnimationState.start(this.tickCount);
        } else {
            this.idleAnimationTimeout--;
        }

        if (this.isCamelVisuallySitting()) {
            this.sitUpAnimationState.stop();
            this.dashAnimationState.stop();
            if (this.isVisuallySittingDown()) {
                this.sitAnimationState.startIfStopped(this.tickCount);
                this.sitPoseAnimationState.stop();
            } else {
                this.sitAnimationState.stop();
                this.sitPoseAnimationState.startIfStopped(this.tickCount);
            }
        } else {
            this.sitAnimationState.stop();
            this.sitPoseAnimationState.stop();
            this.dashAnimationState.animateWhen(this.isDashing(), this.tickCount);
            this.sitUpAnimationState.animateWhen(this.isInPoseTransition() && this.getPoseTime() >= 0L, this.tickCount);
        }
    }

    @Override
    protected void updateWalkAnimation(float partialTick) {
        float min;
        if (this.getPose() == Pose.STANDING && !this.dashAnimationState.isStarted()) {
            min = Math.min(partialTick * 6.0F, 1.0F);
        } else {
            min = 0.0F;
        }

        this.walkAnimation.update(min, 0.2F, this.isBaby() ? 3.0F : 1.0F);
    }

    @Override
    public void travel(Vec3 travelVector) {
        if (this.refuseToMove() && this.onGround()) {
            this.setDeltaMovement(this.getDeltaMovement().multiply(0.0, 1.0, 0.0));
            travelVector = travelVector.multiply(0.0, 1.0, 0.0);
        }

        super.travel(travelVector);
    }

    @Override
    protected void tickRidden(Player player, Vec3 travelVector) {
        super.tickRidden(player, travelVector);
        if (player.zza > 0.0F && this.isCamelSitting() && !this.isInPoseTransition()) {
            this.standUp();
        }
    }

    public boolean refuseToMove() {
        return this.isCamelSitting() || this.isInPoseTransition();
    }

    @Override
    protected float getRiddenSpeed(Player player) {
        float f = player.isSprinting() && this.getJumpCooldown() == 0 ? 0.1F : 0.0F;
        return (float)this.getAttributeValue(Attributes.MOVEMENT_SPEED) + f;
    }

    @Override
    protected Vec2 getRiddenRotation(LivingEntity entity) {
        return this.refuseToMove() ? new Vec2(this.getXRot(), this.getYRot()) : super.getRiddenRotation(entity);
    }

    @Override
    protected Vec3 getRiddenInput(Player player, Vec3 travelVector) {
        return this.refuseToMove() ? Vec3.ZERO : super.getRiddenInput(player, travelVector);
    }

    @Override
    public boolean canJump() {
        return !this.refuseToMove() && super.canJump();
    }

    @Override
    public void onPlayerJump(int jumpPower) {
        if (this.isSaddled() && this.dashCooldown <= 0 && this.onGround()) {
            super.onPlayerJump(jumpPower);
        }
    }

    @Override
    public boolean canSprint() {
        return true;
    }

    @Override
    protected void executeRidersJump(float playerJumpPendingScale, Vec3 travelVector) {
        double d = this.getJumpPower();
        this.addDeltaMovement(
            this.getLookAngle()
                .multiply(1.0, 0.0, 1.0)
                .normalize()
                .scale(22.2222F * playerJumpPendingScale * this.getAttributeValue(Attributes.MOVEMENT_SPEED) * this.getBlockSpeedFactor())
                .add(0.0, 1.4285F * playerJumpPendingScale * d, 0.0)
        );
        this.dashCooldown = 55;
        this.setDashing(true);
        this.needsSync = true;
    }

    public boolean isDashing() {
        return this.entityData.get(DASH);
    }

    public void setDashing(boolean dashing) {
        this.entityData.set(DASH, dashing);
    }

    @Override
    public void handleStartJump(int jumpPower) {
        this.makeSound(this.getDashingSound());
        this.gameEvent(GameEvent.ENTITY_ACTION);
        this.setDashing(true);
    }

    protected SoundEvent getDashingSound() {
        return SoundEvents.CAMEL_DASH;
    }

    protected SoundEvent getDashReadySound() {
        return SoundEvents.CAMEL_DASH_READY;
    }

    @Override
    public void handleStopJump() {
    }

    @Override
    public int getJumpCooldown() {
        return this.dashCooldown;
    }

    @Override
    public SoundEvent getAmbientSound() {
        return SoundEvents.CAMEL_AMBIENT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.CAMEL_DEATH;
    }

    @Override
    public SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.CAMEL_HURT;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState block) {
        if (block.is(BlockTags.CAMEL_SAND_STEP_SOUND_BLOCKS)) {
            this.playSound(SoundEvents.CAMEL_STEP_SAND, 1.0F, 1.0F);
        } else {
            this.playSound(SoundEvents.CAMEL_STEP, 1.0F, 1.0F);
        }
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(ItemTags.CAMEL_FOOD);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemInHand = player.getItemInHand(hand);
        if (player.isSecondaryUseActive() && !this.isBaby()) {
            this.openCustomInventoryScreen(player);
            return InteractionResult.SUCCESS;
        } else {
            InteractionResult interactionResult = itemInHand.interactLivingEntity(player, this, hand);
            if (interactionResult.consumesAction()) {
                return interactionResult;
            } else if (this.isFood(itemInHand)) {
                return this.fedFood(player, itemInHand);
            } else {
                if (this.getPassengers().size() < 2 && !this.isBaby()) {
                    this.doPlayerRide(player);
                }

                return InteractionResult.CONSUME;
            }
        }
    }

    @Override
    public void onElasticLeashPull() {
        super.onElasticLeashPull();
        if (this.isCamelSitting() && !this.isInPoseTransition() && this.canCamelChangePose()) {
            this.standUp();
        }
    }

    @Override
    public Vec3[] getQuadLeashOffsets() {
        return Leashable.createQuadLeashOffsets(this, 0.02, 0.48, 0.25, 0.82);
    }

    public boolean canCamelChangePose() {
        return this.wouldNotSuffocateAtTargetPose(this.isCamelSitting() ? Pose.STANDING : Pose.SITTING);
    }

    @Override
    protected boolean handleEating(Player player, ItemStack stack) {
        if (!this.isFood(stack)) {
            return false;
        } else {
            boolean flag = this.getHealth() < this.getMaxHealth();
            if (flag) {
                this.heal(2.0F, org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason.EATING); // Paper - Add missing regain reason
            }

            boolean flag1 = this.isTamed() && this.getAge() == 0 && this.canFallInLove();
            if (flag1) {
                this.setInLove(player, stack.copy()); // Paper - Fix EntityBreedEvent copying
            }

            boolean isBaby = this.isBaby();
            if (isBaby) {
                this.level().addParticle(ParticleTypes.HAPPY_VILLAGER, this.getRandomX(1.0), this.getRandomY() + 0.5, this.getRandomZ(1.0), 0.0, 0.0, 0.0);
                if (!this.level().isClientSide()) {
                    this.ageUp(10);
                }
            }

            if (!flag && !flag1 && !isBaby) {
                return false;
            } else {
                if (!this.isSilent()) {
                    SoundEvent eatingSound = this.getEatingSound();
                    if (eatingSound != null) {
                        this.level()
                            .playSound(
                                null,
                                this.getX(),
                                this.getY(),
                                this.getZ(),
                                eatingSound,
                                this.getSoundSource(),
                                1.0F,
                                1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.2F
                            );
                    }
                }

                this.gameEvent(GameEvent.EAT);
                return true;
            }
        }
    }

    @Override
    protected boolean canPerformRearing() {
        return false;
    }

    @Override
    public boolean canMate(Animal partner) {
        return partner != this && partner instanceof Camel camel && this.canParent() && camel.canParent();
    }

    @Override
    public @Nullable Camel getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return EntityType.CAMEL.create(level, EntitySpawnReason.BREEDING);
    }

    @Override
    protected SoundEvent getEatingSound() {
        return SoundEvents.CAMEL_EAT;
    }

    @Override
    // CraftBukkit start - void -> boolean
    public boolean actuallyHurt(ServerLevel level, DamageSource damageSource, float amount, org.bukkit.event.entity.EntityDamageEvent event) {
        boolean damageResult = super.actuallyHurt(level, damageSource, amount, event);
        if (!damageResult) return false;
        // CraftBukkit end
        this.standUpInstantly();
        return true; // CraftBukkit
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity entity, EntityDimensions dimensions, float partialTick) {
        int max = Math.max(this.getPassengers().indexOf(entity), 0);
        boolean flag = max == 0;
        float f = 0.5F;
        float f1 = (float)(this.isRemoved() ? 0.01F : this.getBodyAnchorAnimationYOffset(flag, 0.0F, dimensions, partialTick));
        if (this.getPassengers().size() > 1) {
            if (!flag) {
                f = -0.7F;
            }

            if (entity instanceof Animal) {
                f += 0.2F;
            }
        }

        return new Vec3(0.0, f1, f * partialTick).yRot(-this.getYRot() * (float) (Math.PI / 180.0));
    }

    @Override
    public float getAgeScale() {
        return this.isBaby() ? 0.45F : 1.0F;
    }

    private double getBodyAnchorAnimationYOffset(boolean firstPassenger, float partialTick, EntityDimensions dimensions, float scale) {
        double d = dimensions.height() - 0.375F * scale;
        float f = scale * 1.43F;
        float f1 = f - scale * 0.2F;
        float f2 = f - f1;
        boolean isInPoseTransition = this.isInPoseTransition();
        boolean isCamelSitting = this.isCamelSitting();
        if (isInPoseTransition) {
            int i = isCamelSitting ? 40 : 52;
            int i1;
            float f3;
            if (isCamelSitting) {
                i1 = 28;
                f3 = firstPassenger ? 0.5F : 0.1F;
            } else {
                i1 = firstPassenger ? 24 : 32;
                f3 = firstPassenger ? 0.6F : 0.35F;
            }

            float f4 = Mth.clamp((float)this.getPoseTime() + partialTick, 0.0F, (float)i);
            boolean flag = f4 < i1;
            float f5 = flag ? f4 / i1 : (f4 - i1) / (i - i1);
            float f6 = f - f3 * f1;
            d += isCamelSitting ? Mth.lerp(f5, flag ? f : f6, flag ? f6 : f2) : Mth.lerp(f5, flag ? f2 - f : f2 - f6, flag ? f2 - f6 : 0.0F);
        }

        if (isCamelSitting && !isInPoseTransition) {
            d += f2;
        }

        return d;
    }

    @Override
    public Vec3 getLeashOffset(float partialTick) {
        EntityDimensions dimensions = this.getDimensions(this.getPose());
        float ageScale = this.getAgeScale();
        return new Vec3(0.0, this.getBodyAnchorAnimationYOffset(true, partialTick, dimensions, ageScale) - 0.2F * ageScale, dimensions.width() * 0.56F);
    }

    @Override
    public int getMaxHeadYRot() {
        return 30;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().size() <= 2;
    }

    public boolean isCamelSitting() {
        return this.entityData.get(LAST_POSE_CHANGE_TICK) < 0L;
    }

    public boolean isCamelVisuallySitting() {
        return this.getPoseTime() < 0L != this.isCamelSitting();
    }

    public boolean isInPoseTransition() {
        long poseTime = this.getPoseTime();
        return poseTime < (this.isCamelSitting() ? 40 : 52);
    }

    private boolean isVisuallySittingDown() {
        return this.isCamelSitting() && this.getPoseTime() < 40L && this.getPoseTime() >= 0L;
    }

    public void sitDown() {
        if (!this.isCamelSitting() && new io.papermc.paper.event.entity.EntityToggleSitEvent(this.getBukkitEntity(), true).callEvent()) { // Paper - Add EntityToggleSitEvent
            this.makeSound(this.getSitDownSound());
            this.setPose(Pose.SITTING);
            this.gameEvent(GameEvent.ENTITY_ACTION);
            this.resetLastPoseChangeTick(-this.level().getGameTime());
        }
    }

    public void standUp() {
        if (this.isCamelSitting() && new io.papermc.paper.event.entity.EntityToggleSitEvent(this.getBukkitEntity(), false).callEvent()) { // Paper - Add EntityToggleSitEvent
            this.makeSound(this.getStandUpSound());
            this.setPose(Pose.STANDING);
            this.gameEvent(GameEvent.ENTITY_ACTION);
            this.resetLastPoseChangeTick(this.level().getGameTime());
        }
    }

    protected SoundEvent getStandUpSound() {
        return SoundEvents.CAMEL_STAND;
    }

    protected SoundEvent getSitDownSound() {
        return SoundEvents.CAMEL_SIT;
    }

    public void standUpInstantly() {
        if (this.isCamelSitting() && !new io.papermc.paper.event.entity.EntityToggleSitEvent(this.getBukkitEntity(), false).callEvent()) return; // Paper - Add EntityToggleSitEvent
        this.setPose(Pose.STANDING);
        this.gameEvent(GameEvent.ENTITY_ACTION);
        this.resetLastPoseChangeTickToFullStand(this.level().getGameTime());
    }

    @VisibleForTesting
    public void resetLastPoseChangeTick(long lastPoseChangeTick) {
        this.entityData.set(LAST_POSE_CHANGE_TICK, lastPoseChangeTick);
    }

    private void resetLastPoseChangeTickToFullStand(long lastPoseChangedTick) {
        this.resetLastPoseChangeTick(Math.max(0L, lastPoseChangedTick - 52L - 1L));
    }

    public long getPoseTime() {
        return this.level().getGameTime() - Math.abs(this.entityData.get(LAST_POSE_CHANGE_TICK));
    }

    @Override
    protected Holder<SoundEvent> getEquipSound(EquipmentSlot slot, ItemStack stack, Equippable equippable) {
        return (Holder<SoundEvent>)(slot == EquipmentSlot.SADDLE ? this.getSaddleSound() : super.getEquipSound(slot, stack, equippable));
    }

    protected Holder.Reference<SoundEvent> getSaddleSound() {
        return SoundEvents.CAMEL_SADDLE;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        if (!this.firstTick && DASH.equals(key)) {
            this.dashCooldown = this.dashCooldown == 0 ? 55 : this.dashCooldown;
        }

        super.onSyncedDataUpdated(key);
    }

    @Override
    public boolean isTamed() {
        return true;
    }

    @Override
    public void openCustomInventoryScreen(Player player) {
        if (!this.level().isClientSide()) {
            player.openHorseInventory(this, this.inventory);
        }
    }

    @Override
    protected BodyRotationControl createBodyControl() {
        return new Camel.CamelBodyRotationControl(this);
    }

    class CamelBodyRotationControl extends BodyRotationControl {
        public CamelBodyRotationControl(final Camel mob) {
            super(mob);
        }

        @Override
        public void clientTick() {
            if (!Camel.this.refuseToMove()) {
                super.clientTick();
            }
        }
    }

    class CamelLookControl extends LookControl {
        CamelLookControl() {
            super(Camel.this);
        }

        @Override
        public void tick() {
            if (!Camel.this.hasControllingPassenger()) {
                super.tick();
            }
        }
    }

    class CamelMoveControl extends MoveControl {
        public CamelMoveControl() {
            super(Camel.this);
        }

        @Override
        public void tick() {
            if (this.operation == MoveControl.Operation.MOVE_TO
                && !Camel.this.isLeashed()
                && Camel.this.isCamelSitting()
                && !Camel.this.isInPoseTransition()
                && Camel.this.canCamelChangePose()) {
                Camel.this.standUp();
            }

            super.tick();
        }
    }
}
