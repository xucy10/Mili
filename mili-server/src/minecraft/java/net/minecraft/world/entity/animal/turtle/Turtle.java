package net.minecraft.world.entity.animal.turtle;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.EntityAttachments;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveToBlockGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.TurtleEggBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class Turtle extends Animal {
    private static final EntityDataAccessor<Boolean> HAS_EGG = SynchedEntityData.defineId(Turtle.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> LAYING_EGG = SynchedEntityData.defineId(Turtle.class, EntityDataSerializers.BOOLEAN);
    private static final float BABY_SCALE = 0.3F;
    private static final EntityDimensions BABY_DIMENSIONS = EntityType.TURTLE
        .getDimensions()
        .withAttachments(EntityAttachments.builder().attach(EntityAttachment.PASSENGER, 0.0F, EntityType.TURTLE.getHeight(), -0.25F))
        .scale(0.3F);
    private static final boolean DEFAULT_HAS_EGG = false;
    int layEggCounter;
    public static final TargetingConditions.Selector BABY_ON_LAND_SELECTOR = (entity, level) -> entity.isBaby() && !entity.isInWater();
    public BlockPos homePos = BlockPos.ZERO;
    @Nullable BlockPos travelPos;
    public boolean goingHome;

    public Turtle(EntityType<? extends Turtle> type, Level level) {
        super(type, level);
        this.setPathfindingMalus(PathType.WATER, 0.0F);
        this.setPathfindingMalus(PathType.DOOR_IRON_CLOSED, -1.0F);
        this.setPathfindingMalus(PathType.DOOR_WOOD_CLOSED, -1.0F);
        this.setPathfindingMalus(PathType.DOOR_OPEN, -1.0F);
        this.moveControl = new Turtle.TurtleMoveControl(this);
    }

    public void setHomePos(BlockPos homePos) {
        this.homePos = homePos;
    }

    public boolean hasEgg() {
        return this.entityData.get(HAS_EGG);
    }

    public void setHasEgg(boolean hasEgg) {
        this.entityData.set(HAS_EGG, hasEgg);
    }

    public boolean isLayingEgg() {
        return this.entityData.get(LAYING_EGG);
    }

    void setLayingEgg(boolean isLayingEgg) {
        this.layEggCounter = isLayingEgg ? 1 : 0;
        this.entityData.set(LAYING_EGG, isLayingEgg);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(HAS_EGG, false);
        builder.define(LAYING_EGG, false);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store("home_pos", BlockPos.CODEC, this.homePos);
        output.putBoolean("has_egg", this.hasEgg());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.setHomePos(input.read("home_pos", BlockPos.CODEC).orElse(this.blockPosition()));
        super.readAdditionalSaveData(input);
        this.setHasEgg(input.getBooleanOr("has_egg", false));
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(
        ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData spawnGroupData
    ) {
        this.setHomePos(this.blockPosition());
        return super.finalizeSpawn(level, difficulty, spawnReason, spawnGroupData);
    }

    public static boolean checkTurtleSpawnRules(
        EntityType<Turtle> entityType, LevelAccessor level, EntitySpawnReason spawnReason, BlockPos pos, RandomSource random
    ) {
        return pos.getY() < level.getSeaLevel() + 4 && TurtleEggBlock.onSand(level, pos) && isBrightEnoughToSpawn(level, pos);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new Turtle.TurtlePanicGoal(this, 1.2));
        this.goalSelector.addGoal(1, new Turtle.TurtleBreedGoal(this, 1.0));
        this.goalSelector.addGoal(1, new Turtle.TurtleLayEggGoal(this, 1.0));
        this.goalSelector.addGoal(2, new TemptGoal(this, 1.1, itemStack -> itemStack.is(ItemTags.TURTLE_FOOD), false));
        this.goalSelector.addGoal(3, new Turtle.TurtleGoToWaterGoal(this, 1.0));
        this.goalSelector.addGoal(4, new Turtle.TurtleGoHomeGoal(this, 1.0));
        this.goalSelector.addGoal(7, new Turtle.TurtleTravelGoal(this, 1.0));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(9, new Turtle.TurtleRandomStrollGoal(this, 1.0, 100));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes().add(Attributes.MAX_HEALTH, 30.0).add(Attributes.MOVEMENT_SPEED, 0.25).add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 200;
    }

    @Override
    public @Nullable SoundEvent getAmbientSound() {
        return !this.isInWater() && this.onGround() && !this.isBaby() ? SoundEvents.TURTLE_AMBIENT_LAND : super.getAmbientSound();
    }

    @Override
    protected void playSwimSound(float volume) {
        super.playSwimSound(volume * 1.5F);
    }

    @Override
    public SoundEvent getSwimSound() {
        return SoundEvents.TURTLE_SWIM;
    }

    @Override
    public @Nullable SoundEvent getHurtSound(DamageSource damageSource) {
        return this.isBaby() ? SoundEvents.TURTLE_HURT_BABY : SoundEvents.TURTLE_HURT;
    }

    @Override
    public @Nullable SoundEvent getDeathSound() {
        return this.isBaby() ? SoundEvents.TURTLE_DEATH_BABY : SoundEvents.TURTLE_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState block) {
        SoundEvent soundEvent = this.isBaby() ? SoundEvents.TURTLE_SHAMBLE_BABY : SoundEvents.TURTLE_SHAMBLE;
        this.playSound(soundEvent, 0.15F, 1.0F);
    }

    @Override
    public boolean canFallInLove() {
        return super.canFallInLove() && !this.hasEgg();
    }

    @Override
    protected float nextStep() {
        return this.moveDist + 0.15F;
    }

    @Override
    public float getAgeScale() {
        return this.isBaby() ? 0.3F : 1.0F;
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new Turtle.TurtlePathNavigation(this, level);
    }

    @Override
    public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return EntityType.TURTLE.create(level, EntitySpawnReason.BREEDING);
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(ItemTags.TURTLE_FOOD);
    }

    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        if (!this.goingHome && level.getFluidState(pos).is(FluidTags.WATER)) {
            return 10.0F;
        } else {
            return TurtleEggBlock.onSand(level, pos) ? 10.0F : level.getPathfindingCostFromLightLevels(pos);
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.isAlive() && this.isLayingEgg() && this.layEggCounter >= 1 && this.layEggCounter % 5 == 0) {
            BlockPos blockPos = this.blockPosition();
            if (TurtleEggBlock.onSand(this.level(), blockPos)) {
                this.level().levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, blockPos, Block.getId(this.level().getBlockState(blockPos.below())));
                this.gameEvent(GameEvent.ENTITY_ACTION);
            }
        }
    }

    @Override
    protected void ageBoundaryReached() {
        super.ageBoundaryReached();
        if (!this.isBaby() && this.level() instanceof ServerLevel serverLevel && serverLevel.getGameRules().get(GameRules.MOB_DROPS)) {
            this.forceDrops = true; // CraftBukkit
            this.dropFromGiftLootTable(serverLevel, BuiltInLootTables.TURTLE_GROW, this::spawnAtLocation);
            this.forceDrops = false; // CraftBukkit
        }
    }

    @Override
    protected void travelInWater(Vec3 travelVector, double gravity, boolean isFalling, double previousY) {
        this.moveRelative(0.1F, travelVector);
        this.move(MoverType.SELF, this.getDeltaMovement());
        this.setDeltaMovement(this.getDeltaMovement().scale(0.9));
        if (this.getTarget() == null && (!this.goingHome || !this.homePos.closerToCenterThan(this.position(), 20.0))) {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0, -0.005, 0.0));
        }
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public void thunderHit(ServerLevel level, LightningBolt lightning) {
        this.hurtServer(level, this.damageSources().lightningBolt().eventEntityDamager(lightning), Float.MAX_VALUE); // CraftBukkit // Paper - fix DamageSource API
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        return this.isBaby() ? BABY_DIMENSIONS : super.getDefaultDimensions(pose);
    }

    static class TurtleBreedGoal extends BreedGoal {
        private final Turtle turtle;

        TurtleBreedGoal(Turtle turtle, double speedModifier) {
            super(turtle, speedModifier);
            this.turtle = turtle;
        }

        @Override
        public boolean canUse() {
            return super.canUse() && !this.turtle.hasEgg();
        }

        @Override
        protected void breed() {
            ServerPlayer loveCause = this.animal.getLoveCause();
            if (loveCause == null && this.partner.getLoveCause() != null) {
                loveCause = this.partner.getLoveCause();
            }
            // Paper start - Add EntityFertilizeEggEvent event
            io.papermc.paper.event.entity.EntityFertilizeEggEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callEntityFertilizeEggEvent(this.animal, this.partner);
            if (event.isCancelled()) return;
            // Paper end - Add EntityFertilizeEggEvent event

            if (loveCause != null) {
                loveCause.awardStat(Stats.ANIMALS_BRED);
                CriteriaTriggers.BRED_ANIMALS.trigger(loveCause, this.animal, this.partner, null);
            }

            this.turtle.setHasEgg(true);
            this.animal.setAge(6000);
            this.partner.setAge(6000);
            this.animal.resetLove();
            this.partner.resetLove();
            RandomSource random = this.animal.getRandom();
            if (getServerLevel(this.level).getGameRules().get(GameRules.MOB_DROPS)) {
                if (event.getExperience() > 0) this.level.addFreshEntity(new ExperienceOrb(this.level, this.animal.position(), Vec3.ZERO, event.getExperience(), org.bukkit.entity.ExperienceOrb.SpawnReason.BREED, loveCause, this.turtle)); // Paper - Add EntityFertilizeEggEvent event
            }
        }
    }

    static class TurtleGoHomeGoal extends Goal {
        private final Turtle turtle;
        private final double speedModifier;
        private boolean stuck;
        private int closeToHomeTryTicks;
        private static final int GIVE_UP_TICKS = 600;

        TurtleGoHomeGoal(Turtle turtle, double speedModifier) {
            this.turtle = turtle;
            this.speedModifier = speedModifier;
        }

        @Override
        public boolean canUse() {
            return !this.turtle.isBaby()
                && (
                    this.turtle.hasEgg()
                        || this.turtle.getRandom().nextInt(reducedTickDelay(700)) == 0 && !this.turtle.homePos.closerToCenterThan(this.turtle.position(), 64.0)
                ) && new com.destroystokyo.paper.event.entity.TurtleGoHomeEvent((org.bukkit.entity.Turtle) this.turtle.getBukkitEntity()).callEvent(); // Paper - Turtle API
        }

        @Override
        public void start() {
            this.turtle.goingHome = true;
            this.stuck = false;
            this.closeToHomeTryTicks = 0;
        }

        @Override
        public void stop() {
            this.turtle.goingHome = false;
        }

        @Override
        public boolean canContinueToUse() {
            return !this.turtle.homePos.closerToCenterThan(this.turtle.position(), 7.0)
                && !this.stuck
                && this.closeToHomeTryTicks <= this.adjustedTickDelay(600);
        }

        @Override
        public void tick() {
            BlockPos blockPos = this.turtle.homePos;
            boolean flag = blockPos.closerToCenterThan(this.turtle.position(), 16.0);
            if (flag) {
                this.closeToHomeTryTicks++;
            }

            if (this.turtle.getNavigation().isDone()) {
                Vec3 vec3 = Vec3.atBottomCenterOf(blockPos);
                Vec3 posTowards = DefaultRandomPos.getPosTowards(this.turtle, 16, 3, vec3, (float) (Math.PI / 10));
                if (posTowards == null) {
                    posTowards = DefaultRandomPos.getPosTowards(this.turtle, 8, 7, vec3, (float) (Math.PI / 2));
                }

                if (posTowards != null && !flag && !this.turtle.level().getBlockState(BlockPos.containing(posTowards)).is(Blocks.WATER)) {
                    posTowards = DefaultRandomPos.getPosTowards(this.turtle, 16, 5, vec3, (float) (Math.PI / 2));
                }

                if (posTowards == null) {
                    this.stuck = true;
                    return;
                }

                this.turtle.getNavigation().moveTo(posTowards.x, posTowards.y, posTowards.z, this.speedModifier);
            }
        }
    }

    static class TurtleGoToWaterGoal extends MoveToBlockGoal {
        private static final int GIVE_UP_TICKS = 1200;
        private final Turtle turtle;

        TurtleGoToWaterGoal(Turtle turtle, double speedModifier) {
            super(turtle, turtle.isBaby() ? 2.0 : speedModifier, 24);
            this.turtle = turtle;
            this.verticalSearchStart = -1;
        }

        @Override
        public boolean canContinueToUse() {
            return !this.turtle.isInWater() && this.tryTicks <= 1200 && this.isValidTarget(this.turtle.level(), this.blockPos);
        }

        @Override
        public boolean canUse() {
            return this.turtle.isBaby() && !this.turtle.isInWater()
                ? super.canUse()
                : !this.turtle.goingHome && !this.turtle.isInWater() && !this.turtle.hasEgg() && super.canUse();
        }

        @Override
        public boolean shouldRecalculatePath() {
            return this.tryTicks % 160 == 0;
        }

        @Override
        protected boolean isValidTarget(LevelReader level, BlockPos pos) {
            return level.getBlockState(pos).is(Blocks.WATER);
        }
    }

    static class TurtleLayEggGoal extends MoveToBlockGoal {
        private final Turtle turtle;

        TurtleLayEggGoal(Turtle turtle, double speedModifier) {
            super(turtle, speedModifier, 16);
            this.turtle = turtle;
        }

        @Override
        public boolean canUse() {
            return this.turtle.hasEgg() && this.turtle.homePos.closerToCenterThan(this.turtle.position(), 9.0) && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return super.canContinueToUse() && this.turtle.hasEgg() && this.turtle.homePos.closerToCenterThan(this.turtle.position(), 9.0);
        }

        @Override
        public void tick() {
            super.tick();
            BlockPos blockPos = this.turtle.blockPosition();
            if (!this.turtle.isInWater() && this.isReachedTarget()) {
                if (this.turtle.layEggCounter < 1) {
                    this.turtle.setLayingEgg(new com.destroystokyo.paper.event.entity.TurtleStartDiggingEvent((org.bukkit.entity.Turtle) this.turtle.getBukkitEntity(), org.bukkit.craftbukkit.util.CraftLocation.toBukkit(this.blockPos, this.turtle.level())).callEvent()); // Paper - Turtle API
                } else if (this.turtle.layEggCounter > this.adjustedTickDelay(200)) {
                    // Paper start - Turtle API
                    int eggCount = this.turtle.random.nextInt(4) + 1;
                    com.destroystokyo.paper.event.entity.TurtleLayEggEvent layEggEvent = new com.destroystokyo.paper.event.entity.TurtleLayEggEvent((org.bukkit.entity.Turtle) this.turtle.getBukkitEntity(), org.bukkit.craftbukkit.util.CraftLocation.toBukkit(this.blockPos.above(), this.turtle.level()), eggCount);
                    if (layEggEvent.callEvent() && org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(this.turtle, this.blockPos.above(), Blocks.TURTLE_EGG.defaultBlockState().setValue(TurtleEggBlock.EGGS, layEggEvent.getEggCount()))) {
                    // Paper end - Turtle API
                    Level level = this.turtle.level();
                    level.playSound(null, blockPos, SoundEvents.TURTLE_LAY_EGG, SoundSource.BLOCKS, 0.3F, 0.9F + level.random.nextFloat() * 0.2F);
                    BlockPos blockPos1 = this.blockPos.above();
                    BlockState blockState = Blocks.TURTLE_EGG.defaultBlockState().setValue(TurtleEggBlock.EGGS, layEggEvent.getEggCount()); // Paper
                    level.setBlock(blockPos1, blockState, Block.UPDATE_ALL);
                    level.gameEvent(GameEvent.BLOCK_PLACE, blockPos1, GameEvent.Context.of(this.turtle, blockState));
                    } // CraftBukkit
                    this.turtle.setHasEgg(false);
                    this.turtle.setLayingEgg(false);
                    this.turtle.setInLoveTime(600);
                }

                if (this.turtle.isLayingEgg()) {
                    this.turtle.layEggCounter++;
                }
            }
        }

        @Override
        protected boolean isValidTarget(LevelReader level, BlockPos pos) {
            return level.isEmptyBlock(pos.above()) && TurtleEggBlock.isSand(level, pos);
        }
    }

    static class TurtleMoveControl extends MoveControl {
        private final Turtle turtle;

        TurtleMoveControl(Turtle mob) {
            super(mob);
            this.turtle = mob;
        }

        private void updateSpeed() {
            if (this.turtle.isInWater()) {
                this.turtle.setDeltaMovement(this.turtle.getDeltaMovement().add(0.0, 0.005, 0.0));
                if (!this.turtle.homePos.closerToCenterThan(this.turtle.position(), 16.0)) {
                    this.turtle.setSpeed(Math.max(this.turtle.getSpeed() / 2.0F, 0.08F));
                }

                if (this.turtle.isBaby()) {
                    this.turtle.setSpeed(Math.max(this.turtle.getSpeed() / 3.0F, 0.06F));
                }
            } else if (this.turtle.onGround()) {
                this.turtle.setSpeed(Math.max(this.turtle.getSpeed() / 2.0F, 0.06F));
            }
        }

        @Override
        public void tick() {
            this.updateSpeed();
            if (this.operation == MoveControl.Operation.MOVE_TO && !this.turtle.getNavigation().isDone()) {
                double d = this.wantedX - this.turtle.getX();
                double d1 = this.wantedY - this.turtle.getY();
                double d2 = this.wantedZ - this.turtle.getZ();
                double squareRoot = Math.sqrt(d * d + d1 * d1 + d2 * d2);
                if (squareRoot < 1.0E-5F) {
                    this.mob.setSpeed(0.0F);
                } else {
                    d1 /= squareRoot;
                    float f = (float)(Mth.atan2(d2, d) * 180.0F / (float)Math.PI) - 90.0F;
                    this.turtle.setYRot(this.rotlerp(this.turtle.getYRot(), f, 90.0F));
                    this.turtle.yBodyRot = this.turtle.getYRot();
                    float f1 = (float)(this.speedModifier * this.turtle.getAttributeValue(Attributes.MOVEMENT_SPEED));
                    this.turtle.setSpeed(Mth.lerp(0.125F, this.turtle.getSpeed(), f1));
                    this.turtle.setDeltaMovement(this.turtle.getDeltaMovement().add(0.0, this.turtle.getSpeed() * d1 * 0.1, 0.0));
                }
            } else {
                this.turtle.setSpeed(0.0F);
            }
        }
    }

    static class TurtlePanicGoal extends PanicGoal {
        TurtlePanicGoal(Turtle turtle, double speedModifier) {
            super(turtle, speedModifier);
        }

        @Override
        public boolean canUse() {
            if (!this.shouldPanic()) {
                return false;
            } else {
                BlockPos blockPos = this.lookForWater(this.mob.level(), this.mob, 7);
                if (blockPos != null) {
                    this.posX = blockPos.getX();
                    this.posY = blockPos.getY();
                    this.posZ = blockPos.getZ();
                    return true;
                } else {
                    return this.findRandomPosition();
                }
            }
        }
    }

    static class TurtlePathNavigation extends AmphibiousPathNavigation {
        TurtlePathNavigation(Turtle turtle, Level level) {
            super(turtle, level);
        }

        @Override
        public boolean isStableDestination(BlockPos pos) {
            return this.mob instanceof Turtle turtle && turtle.travelPos != null
                ? this.level.getBlockState(pos).is(Blocks.WATER)
                : !this.level.getBlockState(pos.below()).isAir();
        }
    }

    static class TurtleRandomStrollGoal extends RandomStrollGoal {
        private final Turtle turtle;

        TurtleRandomStrollGoal(Turtle turtle, double speedModifier, int interval) {
            super(turtle, speedModifier, interval);
            this.turtle = turtle;
        }

        @Override
        public boolean canUse() {
            return !this.mob.isInWater() && !this.turtle.goingHome && !this.turtle.hasEgg() && super.canUse();
        }
    }

    static class TurtleTravelGoal extends Goal {
        private final Turtle turtle;
        private final double speedModifier;
        private boolean stuck;

        TurtleTravelGoal(Turtle turtle, double speedModifier) {
            this.turtle = turtle;
            this.speedModifier = speedModifier;
        }

        @Override
        public boolean canUse() {
            return !this.turtle.goingHome && !this.turtle.hasEgg() && this.turtle.isInWater();
        }

        @Override
        public void start() {
            int i = 512;
            int i1 = 4;
            RandomSource randomSource = this.turtle.random;
            int i2 = randomSource.nextInt(1025) - 512;
            int i3 = randomSource.nextInt(9) - 4;
            int i4 = randomSource.nextInt(1025) - 512;
            if (i3 + this.turtle.getY() > this.turtle.level().getSeaLevel() - 1) {
                i3 = 0;
            }

            this.turtle.travelPos = BlockPos.containing(i2 + this.turtle.getX(), i3 + this.turtle.getY(), i4 + this.turtle.getZ());
            this.stuck = false;
        }

        @Override
        public void tick() {
            if (this.turtle.travelPos == null) {
                this.stuck = true;
            } else {
                if (this.turtle.getNavigation().isDone()) {
                    Vec3 vec3 = Vec3.atBottomCenterOf(this.turtle.travelPos);
                    Vec3 posTowards = DefaultRandomPos.getPosTowards(this.turtle, 16, 3, vec3, (float) (Math.PI / 10));
                    if (posTowards == null) {
                        posTowards = DefaultRandomPos.getPosTowards(this.turtle, 8, 7, vec3, (float) (Math.PI / 2));
                    }

                    if (posTowards != null) {
                        int floor = Mth.floor(posTowards.x);
                        int floor1 = Mth.floor(posTowards.z);
                        int i = 34;
                        if (!this.turtle.level().hasChunksAt(floor - 34, floor1 - 34, floor + 34, floor1 + 34)) {
                            posTowards = null;
                        }
                    }

                    if (posTowards == null) {
                        this.stuck = true;
                        return;
                    }

                    this.turtle.getNavigation().moveTo(posTowards.x, posTowards.y, posTowards.z, this.speedModifier);
                }
            }
        }

        @Override
        public boolean canContinueToUse() {
            return !this.turtle.getNavigation().isDone() && !this.stuck && !this.turtle.goingHome && !this.turtle.isInLove() && !this.turtle.hasEgg();
        }

        @Override
        public void stop() {
            this.turtle.travelPos = null;
            super.stop();
        }
    }
}
