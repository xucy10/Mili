package net.minecraft.world.entity.animal.bee;

import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.util.debug.DebugBeeInfo;
import net.minecraft.util.debug.DebugSubscriptions;
import net.minecraft.util.debug.DebugValueSource;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.ResetUniversalAngerTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.AirAndWaterRandomPos;
import net.minecraft.world.entity.ai.util.AirRandomPos;
import net.minecraft.world.entity.ai.util.HoverRandomPos;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class Bee extends Animal implements NeutralMob, FlyingAnimal {
    public static final float FLAP_DEGREES_PER_TICK = 120.32113F;
    public static final int TICKS_PER_FLAP = Mth.ceil(1.4959966F);
    private static final EntityDataAccessor<Byte> DATA_FLAGS_ID = SynchedEntityData.defineId(Bee.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Long> DATA_ANGER_END_TIME = SynchedEntityData.defineId(Bee.class, EntityDataSerializers.LONG);
    private static final int FLAG_ROLL = 2;
    private static final int FLAG_HAS_STUNG = 4;
    private static final int FLAG_HAS_NECTAR = 8;
    private static final int STING_DEATH_COUNTDOWN = 1200;
    private static final int TICKS_BEFORE_GOING_TO_KNOWN_FLOWER = 600;
    private static final int TICKS_WITHOUT_NECTAR_BEFORE_GOING_HOME = 3600;
    private static final int MIN_ATTACK_DIST = 4;
    private static final int MAX_CROPS_GROWABLE = 10;
    private static final int POISON_SECONDS_NORMAL = 10;
    private static final int POISON_SECONDS_HARD = 18;
    private static final int TOO_FAR_DISTANCE = 48;
    private static final int HIVE_CLOSE_ENOUGH_DISTANCE = 2;
    private static final int RESTRICTED_WANDER_DISTANCE_REDUCTION = 24;
    private static final int DEFAULT_WANDER_DISTANCE_REDUCTION = 16;
    private static final int PATHFIND_TO_HIVE_WHEN_CLOSER_THAN = 16;
    private static final int HIVE_SEARCH_DISTANCE = 20;
    public static final String TAG_CROPS_GROWN_SINCE_POLLINATION = "CropsGrownSincePollination";
    public static final String TAG_CANNOT_ENTER_HIVE_TICKS = "CannotEnterHiveTicks";
    public static final String TAG_TICKS_SINCE_POLLINATION = "TicksSincePollination";
    public static final String TAG_HAS_STUNG = "HasStung";
    public static final String TAG_HAS_NECTAR = "HasNectar";
    public static final String TAG_FLOWER_POS = "flower_pos";
    public static final String TAG_HIVE_POS = "hive_pos";
    public static final boolean DEFAULT_HAS_NECTAR = false;
    private static final boolean DEFAULT_HAS_STUNG = false;
    private static final int DEFAULT_TICKS_SINCE_POLLINATION = 0;
    private static final int DEFAULT_CANNOT_ENTER_HIVE_TICKS = 0;
    private static final int DEFAULT_CROPS_GROWN_SINCE_POLLINATION = 0;
    private static final UniformInt PERSISTENT_ANGER_TIME = TimeUtil.rangeOfSeconds(20, 39);
    private @Nullable EntityReference<LivingEntity> persistentAngerTarget;
    private float rollAmount;
    private float rollAmountO;
    public int timeSinceSting;
    public int ticksWithoutNectarSinceExitingHive = 0;
    public int stayOutOfHiveCountdown = 0;
    public int numCropsGrownSincePollination = 0;
    private static final int COOLDOWN_BEFORE_LOCATING_NEW_HIVE = 200;
    int remainingCooldownBeforeLocatingNewHive;
    private static final int COOLDOWN_BEFORE_LOCATING_NEW_FLOWER = 200;
    private static final int MIN_FIND_FLOWER_RETRY_COOLDOWN = 20;
    private static final int MAX_FIND_FLOWER_RETRY_COOLDOWN = 60;
    int remainingCooldownBeforeLocatingNewFlower = Mth.nextInt(this.random, 20, 60);
    @Nullable BlockPos savedFlowerPos;
    @Nullable public BlockPos hivePos;
    Bee.BeePollinateGoal beePollinateGoal;
    Bee.BeeGoToHiveGoal goToHiveGoal;
    private Bee.BeeGoToKnownFlowerGoal goToKnownFlowerGoal;
    private int underWaterTicks;
    public net.kyori.adventure.util.TriState rollingOverride = net.kyori.adventure.util.TriState.NOT_SET; // Paper - Rolling override

    public Bee(EntityType<? extends Bee> type, Level level) {
        super(type, level);
        // Paper start - Fix MC-167279
        class BeeFlyingMoveControl extends FlyingMoveControl {
            public BeeFlyingMoveControl(final Mob mob, final int maxTurn, final boolean hoversInPlace) {
                super(mob, maxTurn, hoversInPlace);
            }

            @Override
            public void tick() {
                if (this.mob.getY() <= Bee.this.level().getMinY()) {
                    this.mob.setNoGravity(false);
                }
                super.tick();
            }
        }
        this.moveControl = new BeeFlyingMoveControl(this, 20, true);
        // Paper end - Fix MC-167279
        this.lookControl = new Bee.BeeLookControl(this);
        this.setPathfindingMalus(PathType.DANGER_FIRE, -1.0F);
        this.setPathfindingMalus(PathType.WATER, -1.0F);
        this.setPathfindingMalus(PathType.WATER_BORDER, 16.0F);
        this.setPathfindingMalus(PathType.COCOA, -1.0F);
        this.setPathfindingMalus(PathType.FENCE, -1.0F);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_FLAGS_ID, (byte)0);
        builder.define(DATA_ANGER_END_TIME, -1L);
    }

    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        return level.getBlockState(pos).isAir() ? 10.0F : 0.0F;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new Bee.BeeAttackGoal(this, 1.4F, true));
        this.goalSelector.addGoal(1, new Bee.BeeEnterHiveGoal());
        this.goalSelector.addGoal(2, new BreedGoal(this, 1.0));
        this.goalSelector.addGoal(3, new TemptGoal(this, 1.25, stack -> stack.is(ItemTags.BEE_FOOD), false));
        this.goalSelector.addGoal(3, new Bee.ValidateHiveGoal());
        this.goalSelector.addGoal(3, new Bee.ValidateFlowerGoal());
        this.beePollinateGoal = new Bee.BeePollinateGoal();
        this.goalSelector.addGoal(4, this.beePollinateGoal);
        this.goalSelector.addGoal(5, new FollowParentGoal(this, 1.25));
        this.goalSelector.addGoal(5, new Bee.BeeLocateHiveGoal());
        this.goToHiveGoal = new Bee.BeeGoToHiveGoal();
        this.goalSelector.addGoal(5, this.goToHiveGoal);
        this.goToKnownFlowerGoal = new Bee.BeeGoToKnownFlowerGoal();
        this.goalSelector.addGoal(6, this.goToKnownFlowerGoal);
        this.goalSelector.addGoal(7, new Bee.BeeGrowCropGoal());
        this.goalSelector.addGoal(8, new Bee.BeeWanderGoal());
        this.goalSelector.addGoal(9, new FloatGoal(this));
        this.targetSelector.addGoal(1, new Bee.BeeHurtByOtherGoal(this).setAlertOthers());
        this.targetSelector.addGoal(2, new Bee.BeeBecomeAngryTargetGoal(this));
        this.targetSelector.addGoal(3, new ResetUniversalAngerTargetGoal<>(this, true));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        // CraftBukkit start - selectively save data
        this.addAdditionalSaveData(output, true);
    }

    @Override
    public void addAdditionalSaveData(ValueOutput output, boolean saveAll) {
        // CraftBukkit end
        super.addAdditionalSaveData(output);
        if (saveAll) { // Paper
        output.storeNullable("hive_pos", BlockPos.CODEC, this.hivePos);
        output.storeNullable("flower_pos", BlockPos.CODEC, this.savedFlowerPos);
        } // Paper
        output.putBoolean("HasNectar", this.hasNectar());
        output.putBoolean("HasStung", this.hasStung());
        output.putInt("TicksSincePollination", this.ticksWithoutNectarSinceExitingHive);
        output.putInt("CannotEnterHiveTicks", this.stayOutOfHiveCountdown);
        output.putInt("CropsGrownSincePollination", this.numCropsGrownSincePollination);
        this.addPersistentAngerSaveData(output);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setHasNectar(input.getBooleanOr("HasNectar", false));
        this.setHasStung(input.getBooleanOr("HasStung", false));
        this.ticksWithoutNectarSinceExitingHive = input.getIntOr("TicksSincePollination", 0);
        this.stayOutOfHiveCountdown = input.getIntOr("CannotEnterHiveTicks", 0);
        this.numCropsGrownSincePollination = input.getIntOr("CropsGrownSincePollination", 0);
        this.hivePos = input.read("hive_pos", BlockPos.CODEC).orElse(null);
        this.savedFlowerPos = input.read("flower_pos", BlockPos.CODEC).orElse(null);
        this.readPersistentAngerSaveData(this.level(), input);
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        DamageSource damageSource = this.damageSources().sting(this);
        boolean flag = target.hurtServer(level, damageSource, (int)this.getAttributeValue(Attributes.ATTACK_DAMAGE));
        if (flag) {
            EnchantmentHelper.doPostAttackEffects(level, target, damageSource);
            if (target instanceof LivingEntity livingEntity) {
                livingEntity.setStingerCount(livingEntity.getStingerCount() + 1);
                int i = 0;
                if (this.level().getDifficulty() == Difficulty.NORMAL) {
                    i = 10;
                } else if (this.level().getDifficulty() == Difficulty.HARD) {
                    i = 18;
                }

                if (i > 0) {
                    livingEntity.addEffect(new MobEffectInstance(MobEffects.POISON, i * 20, 0), this, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.ATTACK); // CraftBukkit
                }
            }

            this.setHasStung(true);
            this.stopBeingAngry();
            this.playSound(SoundEvents.BEE_STING, 1.0F, 1.0F);
        }

        return flag;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.hasNectar() && this.getCropsGrownSincePollination() < 10 && this.random.nextFloat() < 0.05F) {
            for (int i = 0; i < this.random.nextInt(2) + 1; i++) {
                this.spawnFluidParticle(
                    this.level(), this.getX() - 0.3F, this.getX() + 0.3F, this.getZ() - 0.3F, this.getZ() + 0.3F, this.getY(0.5), ParticleTypes.FALLING_NECTAR
                );
            }
        }

        this.updateRollAmount();
    }

    private void spawnFluidParticle(Level level, double startX, double endX, double startZ, double endZ, double posY, ParticleOptions options) {
        level.addParticle(options, Mth.lerp(level.random.nextDouble(), startX, endX), posY, Mth.lerp(level.random.nextDouble(), startZ, endZ), 0.0, 0.0, 0.0);
    }

    void pathfindRandomlyTowards(BlockPos pos) {
        Vec3 vec3 = Vec3.atBottomCenterOf(pos);
        int i = 0;
        BlockPos blockPos = this.blockPosition();
        int i1 = (int)vec3.y - blockPos.getY();
        if (i1 > 2) {
            i = 4;
        } else if (i1 < -2) {
            i = -4;
        }

        int i2 = 6;
        int i3 = 8;
        int i4 = blockPos.distManhattan(pos);
        if (i4 < 15) {
            i2 = i4 / 2;
            i3 = i4 / 2;
        }

        Vec3 posTowards = AirRandomPos.getPosTowards(this, i2, i3, i, vec3, (float) (Math.PI / 10));
        if (posTowards != null) {
            this.navigation.setMaxVisitedNodesMultiplier(0.5F);
            this.navigation.moveTo(posTowards.x, posTowards.y, posTowards.z, 1.0);
        }
    }

    public @Nullable BlockPos getSavedFlowerPos() {
        return this.savedFlowerPos;
    }

    public boolean hasSavedFlowerPos() {
        return this.savedFlowerPos != null;
    }

    public void setSavedFlowerPos(BlockPos savedFlowerPos) {
        this.savedFlowerPos = savedFlowerPos;
    }

    @VisibleForDebug
    public int getTravellingTicks() {
        return Math.max(this.goToHiveGoal.travellingTicks, this.goToKnownFlowerGoal.travellingTicks);
    }

    @VisibleForDebug
    public List<BlockPos> getBlacklistedHives() {
        return this.goToHiveGoal.blacklistedTargets;
    }

    private boolean isTiredOfLookingForNectar() {
        return this.ticksWithoutNectarSinceExitingHive > 3600;
    }

    void dropHive() {
        this.hivePos = null;
        this.remainingCooldownBeforeLocatingNewHive = 200;
    }

    void dropFlower() {
        this.savedFlowerPos = null;
        this.remainingCooldownBeforeLocatingNewFlower = Mth.nextInt(this.random, 20, 60);
    }

    boolean wantsToEnterHive() {
        if (this.stayOutOfHiveCountdown <= 0 && !this.beePollinateGoal.isPollinating() && !this.hasStung() && this.getTarget() == null) {
            boolean flag = this.hasNectar()
                || this.isTiredOfLookingForNectar()
                || this.level().environmentAttributes().getValue(EnvironmentAttributes.BEES_STAY_IN_HIVE, this.position());
            return flag && !this.isHiveNearFire();
        } else {
            return false;
        }
    }

    public void setStayOutOfHiveCountdown(int stayOutOfHiveCountdown) {
        this.stayOutOfHiveCountdown = stayOutOfHiveCountdown;
    }

    public float getRollAmount(float partialTick) {
        return Mth.lerp(partialTick, this.rollAmountO, this.rollAmount);
    }

    private void updateRollAmount() {
        this.rollAmountO = this.rollAmount;
        if (this.isRolling()) {
            this.rollAmount = Math.min(1.0F, this.rollAmount + 0.2F);
        } else {
            this.rollAmount = Math.max(0.0F, this.rollAmount - 0.24F);
        }
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        boolean hasStung = this.hasStung();
        if (this.isInWater()) {
            this.underWaterTicks++;
        } else {
            this.underWaterTicks = 0;
        }

        if (this.underWaterTicks > 20) {
            this.hurtServer(level, this.damageSources().drown(), 1.0F);
        }

        if (hasStung) {
            this.timeSinceSting++;
            if (this.timeSinceSting % 5 == 0 && this.random.nextInt(Mth.clamp(1200 - this.timeSinceSting, 1, 1200)) == 0) {
                this.hurtServer(level, this.damageSources().generic(), this.getHealth());
            }
        }

        if (!this.hasNectar()) {
            this.ticksWithoutNectarSinceExitingHive++;
        }

        this.updatePersistentAnger(level, false);
    }

    public void resetTicksWithoutNectarSinceExitingHive() {
        this.ticksWithoutNectarSinceExitingHive = 0;
    }

    private boolean isHiveNearFire() {
        BeehiveBlockEntity beehiveBlockEntity = this.getBeehiveBlockEntity();
        return beehiveBlockEntity != null && beehiveBlockEntity.isFireNearby();
    }

    @Override
    public long getPersistentAngerEndTime() {
        return this.entityData.get(DATA_ANGER_END_TIME);
    }

    @Override
    public void setPersistentAngerEndTime(long absoluteTime) {
        this.entityData.set(DATA_ANGER_END_TIME, absoluteTime);
    }

    @Override
    public @Nullable EntityReference<LivingEntity> getPersistentAngerTarget() {
        return this.persistentAngerTarget;
    }

    @Override
    public void setPersistentAngerTarget(@Nullable EntityReference<LivingEntity> target) {
        this.persistentAngerTarget = target;
    }

    @Override
    public void startPersistentAngerTimer() {
        this.setTimeToRemainAngry(PERSISTENT_ANGER_TIME.sample(this.random));
    }

    private boolean doesHiveHaveSpace(BlockPos hivePos) {
        BlockEntity blockEntity = this.level().getBlockEntity(hivePos);
        return blockEntity instanceof BeehiveBlockEntity && !((BeehiveBlockEntity)blockEntity).isFull();
    }

    @VisibleForDebug
    public boolean hasHive() {
        return this.hivePos != null;
    }

    @VisibleForDebug
    public @Nullable BlockPos getHivePos() {
        return this.hivePos;
    }

    @VisibleForDebug
    public GoalSelector getGoalSelector() {
        return this.goalSelector;
    }

    int getCropsGrownSincePollination() {
        return this.numCropsGrownSincePollination;
    }

    private void resetNumCropsGrownSincePollination() {
        this.numCropsGrownSincePollination = 0;
    }

    void incrementNumCropsGrownSincePollination() {
        this.numCropsGrownSincePollination++;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide()) {
            if (this.stayOutOfHiveCountdown > 0) {
                this.stayOutOfHiveCountdown--;
            }

            if (this.remainingCooldownBeforeLocatingNewHive > 0) {
                this.remainingCooldownBeforeLocatingNewHive--;
            }

            if (this.remainingCooldownBeforeLocatingNewFlower > 0) {
                this.remainingCooldownBeforeLocatingNewFlower--;
            }

            boolean flag = this.isAngry() && !this.hasStung() && this.getTarget() != null && this.getTarget().distanceToSqr(this) < 4.0;
            this.setRolling(flag);
            if (this.tickCount % 20 == 0 && !this.isHiveValid()) {
                this.hivePos = null;
            }
        }
    }

    @Nullable BeehiveBlockEntity getBeehiveBlockEntity() {
        if (this.hivePos == null) {
            return null;
        } else {
            // Paper start - move over logic to accommodate isTooFarAway with chunk load check
            return this.isTooFarAway(this.hivePos) || this.level().getChunkIfLoadedImmediately(this.hivePos.getX() >> 4, this.hivePos.getZ() >> 4) == null
                ? null
                : this.level().getBlockEntity(this.hivePos, BlockEntityType.BEEHIVE).orElse(null);
            // Paper end
        }
    }

    boolean isHiveValid() {
        return this.getBeehiveBlockEntity() != null;
    }

    public boolean hasNectar() {
        return this.getFlag(FLAG_HAS_NECTAR);
    }

    public void setHasNectar(boolean hasNectar) {
        if (hasNectar) {
            this.resetTicksWithoutNectarSinceExitingHive();
        }

        this.setFlag(FLAG_HAS_NECTAR, hasNectar);
    }

    public boolean hasStung() {
        return this.getFlag(FLAG_HAS_STUNG);
    }

    public void setHasStung(boolean hasStung) {
        this.setFlag(FLAG_HAS_STUNG, hasStung);
    }

    public boolean isRolling() {
        return this.getFlag(FLAG_ROLL);
    }

    public void setRolling(boolean isRolling) {
        isRolling = this.rollingOverride.toBooleanOrElse(isRolling); // Paper - Rolling override
        this.setFlag(FLAG_ROLL, isRolling);
    }

    boolean isTooFarAway(BlockPos pos) {
        return !this.closerThan(pos, 48);
    }

    private void setFlag(int flagId, boolean value) {
        if (value) {
            this.entityData.set(DATA_FLAGS_ID, (byte)(this.entityData.get(DATA_FLAGS_ID) | flagId));
        } else {
            this.entityData.set(DATA_FLAGS_ID, (byte)(this.entityData.get(DATA_FLAGS_ID) & ~flagId));
        }
    }

    private boolean getFlag(int flagId) {
        return (this.entityData.get(DATA_FLAGS_ID) & flagId) != 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes()
            .add(Attributes.MAX_HEALTH, 10.0)
            .add(Attributes.FLYING_SPEED, 0.6F)
            .add(Attributes.MOVEMENT_SPEED, 0.3F)
            .add(Attributes.ATTACK_DAMAGE, 2.0);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation flyingPathNavigation = new FlyingPathNavigation(this, level) {
            @Override
            public boolean isStableDestination(BlockPos pos) {
                return !this.level.getBlockState(pos.below()).isAir();
            }

            @Override
            public void tick() {
                if (!Bee.this.beePollinateGoal.isPollinating()) {
                    super.tick();
                }
            }
        };
        flyingPathNavigation.setCanOpenDoors(false);
        flyingPathNavigation.setCanFloat(false);
        flyingPathNavigation.setRequiredPathLength(48.0F);
        return flyingPathNavigation;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemInHand = player.getItemInHand(hand);
        if (this.isFood(itemInHand) && itemInHand.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof FlowerBlock flowerBlock) {
            MobEffectInstance beeInteractionEffect = flowerBlock.getBeeInteractionEffect();
            if (beeInteractionEffect != null) {
                this.usePlayerItem(player, hand, itemInHand);
                if (!this.level().isClientSide()) {
                    this.addEffect(beeInteractionEffect, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.FOOD); // Paper - Add missing effect cause
                }

                return InteractionResult.SUCCESS;
            }
        }

        return super.mobInteract(player, hand);
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(ItemTags.BEE_FOOD);
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState block) {
    }

    @Override
    public SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    public SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.BEE_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.BEE_DEATH;
    }

    @Override
    public float getSoundVolume() {
        return 0.4F;
    }

    @Override
    public @Nullable Bee getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return EntityType.BEE.create(level, EntitySpawnReason.BREEDING);
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    @Override
    public boolean isFlapping() {
        return this.isFlying() && this.tickCount % TICKS_PER_FLAP == 0;
    }

    @Override
    public boolean isFlying() {
        return !this.onGround();
    }

    public void dropOffNectar() {
        this.setHasNectar(false);
        this.resetNumCropsGrownSincePollination();
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        if (this.isInvulnerableTo(level, damageSource)) {
            return false;
        } else {
            if (!super.hurtServer(level, damageSource, amount)) return false; // CraftBukkit - Only stop pollinating if entity was damaged
            this.beePollinateGoal.stopPollinating();
            return true; // CraftBukkit - Only stop pollinating if entity was damaged
        }
    }

    @Override
    protected void jumpInLiquid(TagKey<Fluid> fluidTag) {
        this.setDeltaMovement(this.getDeltaMovement().add(0.0, 0.01, 0.0));
    }

    @Override
    public Vec3 getLeashOffset() {
        return new Vec3(0.0, 0.5F * this.getEyeHeight(), this.getBbWidth() * 0.2F);
    }

    boolean closerThan(BlockPos pos, int distance) {
        return pos.closerThan(this.blockPosition(), distance);
    }

    public void setHivePos(BlockPos hivePos) {
        this.hivePos = hivePos;
    }

    public static boolean attractsBees(BlockState state) {
        return state.is(BlockTags.BEE_ATTRACTIVE)
            && !state.getValueOrElse(BlockStateProperties.WATERLOGGED, false)
            && (!state.is(Blocks.SUNFLOWER) || state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER);
    }

    @Override
    public void registerDebugValues(ServerLevel level, DebugValueSource.Registration registrar) {
        super.registerDebugValues(level, registrar);
        registrar.register(
            DebugSubscriptions.BEES,
            () -> new DebugBeeInfo(
                Optional.ofNullable(this.getHivePos()), Optional.ofNullable(this.getSavedFlowerPos()), this.getTravellingTicks(), this.getBlacklistedHives()
            )
        );
    }

    abstract class BaseBeeGoal extends Goal {
        public abstract boolean canBeeUse();

        public abstract boolean canBeeContinueToUse();

        @Override
        public boolean canUse() {
            return this.canBeeUse() && !Bee.this.isAngry();
        }

        @Override
        public boolean canContinueToUse() {
            return this.canBeeContinueToUse() && !Bee.this.isAngry();
        }
    }

    class BeeAttackGoal extends MeleeAttackGoal {
        BeeAttackGoal(final PathfinderMob mob, final double speedModifier, final boolean followingTargetEvenIfNotSeen) {
            super(mob, speedModifier, followingTargetEvenIfNotSeen);
        }

        @Override
        public boolean canUse() {
            return super.canUse() && Bee.this.isAngry() && !Bee.this.hasStung();
        }

        @Override
        public boolean canContinueToUse() {
            return super.canContinueToUse() && Bee.this.isAngry() && !Bee.this.hasStung();
        }
    }

    static class BeeBecomeAngryTargetGoal extends NearestAttackableTargetGoal<Player> {
        BeeBecomeAngryTargetGoal(Bee mob) {
            super(mob, Player.class, 10, true, false, mob::isAngryAt);
        }

        @Override
        public boolean canUse() {
            return this.beeCanTarget() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            boolean flag = this.beeCanTarget();
            if (flag && this.mob.getTarget() != null) {
                return super.canContinueToUse();
            } else {
                this.targetMob = null;
                return false;
            }
        }

        private boolean beeCanTarget() {
            Bee bee = (Bee)this.mob;
            return bee.isAngry() && !bee.hasStung();
        }
    }

    class BeeEnterHiveGoal extends Bee.BaseBeeGoal {
        @Override
        public boolean canBeeUse() {
            if (Bee.this.hivePos != null && Bee.this.wantsToEnterHive() && Bee.this.hivePos.closerToCenterThan(Bee.this.position(), 2.0)) {
                BeehiveBlockEntity beehiveBlockEntity = Bee.this.getBeehiveBlockEntity();
                if (beehiveBlockEntity != null) {
                    if (!beehiveBlockEntity.isFull()) {
                        return true;
                    }

                    Bee.this.hivePos = null;
                }
            }

            return false;
        }

        @Override
        public boolean canBeeContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            BeehiveBlockEntity beehiveBlockEntity = Bee.this.getBeehiveBlockEntity();
            if (beehiveBlockEntity != null) {
                beehiveBlockEntity.addOccupant(Bee.this);
            }
        }
    }

    @VisibleForDebug
    public class BeeGoToHiveGoal extends Bee.BaseBeeGoal {
        public static final int MAX_TRAVELLING_TICKS = 2400;
        int travellingTicks;
        private static final int MAX_BLACKLISTED_TARGETS = 3;
        final List<BlockPos> blacklistedTargets = Lists.newArrayList();
        private @Nullable Path lastPath;
        private static final int TICKS_BEFORE_HIVE_DROP = 60;
        private int ticksStuck;

        BeeGoToHiveGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canBeeUse() {
            // Folia start - region threading
            if (Bee.this.hivePos != null && Bee.this.isTooFarAway(Bee.this.hivePos)) {
                Bee.this.hivePos = null;
            }
            // Folia end - region threading
            return Bee.this.hivePos != null
                && !Bee.this.isTooFarAway(Bee.this.hivePos)
                && !Bee.this.hasHome()
                && Bee.this.wantsToEnterHive()
                && !this.hasReachedTarget(Bee.this.hivePos)
                && Bee.this.level().getBlockState(Bee.this.hivePos).is(BlockTags.BEEHIVES);
        }

        @Override
        public boolean canBeeContinueToUse() {
            return this.canBeeUse();
        }

        @Override
        public void start() {
            this.travellingTicks = 0;
            this.ticksStuck = 0;
            super.start();
        }

        @Override
        public void stop() {
            this.travellingTicks = 0;
            this.ticksStuck = 0;
            Bee.this.navigation.stop();
            Bee.this.navigation.resetMaxVisitedNodesMultiplier();
        }

        @Override
        public void tick() {
            if (Bee.this.hivePos != null) {
                this.travellingTicks++;
                if (this.travellingTicks > this.adjustedTickDelay(2400)) {
                    this.dropAndBlacklistHive();
                } else if (!Bee.this.navigation.isInProgress()) {
                    if (!Bee.this.closerThan(Bee.this.hivePos, 16)) {
                        if (Bee.this.isTooFarAway(Bee.this.hivePos)) {
                            Bee.this.dropHive();
                        } else {
                            Bee.this.pathfindRandomlyTowards(Bee.this.hivePos);
                        }
                    } else {
                        boolean flag = this.pathfindDirectlyTowards(Bee.this.hivePos);
                        if (!flag) {
                            this.dropAndBlacklistHive();
                        } else if (this.lastPath != null && Bee.this.navigation.getPath().sameAs(this.lastPath)) {
                            this.ticksStuck++;
                            if (this.ticksStuck > 60) {
                                Bee.this.dropHive();
                                this.ticksStuck = 0;
                            }
                        } else {
                            this.lastPath = Bee.this.navigation.getPath();
                        }
                    }
                }
            }
        }

        private boolean pathfindDirectlyTowards(BlockPos pos) {
            int i = Bee.this.closerThan(pos, 3) ? 1 : 2;
            Bee.this.navigation.setMaxVisitedNodesMultiplier(10.0F);
            Bee.this.navigation.moveTo(pos.getX(), pos.getY(), pos.getZ(), i, 1.0);
            return Bee.this.navigation.getPath() != null && Bee.this.navigation.getPath().canReach();
        }

        boolean isTargetBlacklisted(BlockPos pos) {
            return this.blacklistedTargets.contains(pos);
        }

        private void blacklistTarget(BlockPos pos) {
            this.blacklistedTargets.add(pos);

            while (this.blacklistedTargets.size() > 3) {
                this.blacklistedTargets.remove(0);
            }
        }

        void clearBlacklist() {
            this.blacklistedTargets.clear();
        }

        private void dropAndBlacklistHive() {
            if (Bee.this.hivePos != null) {
                this.blacklistTarget(Bee.this.hivePos);
            }

            Bee.this.dropHive();
        }

        private boolean hasReachedTarget(BlockPos pos) {
            if (Bee.this.closerThan(pos, 2)) {
                return true;
            } else {
                Path path = Bee.this.navigation.getPath();
                return path != null && path.getTarget().equals(pos) && path.canReach() && path.isDone();
            }
        }
    }

    public class BeeGoToKnownFlowerGoal extends Bee.BaseBeeGoal {
        private static final int MAX_TRAVELLING_TICKS = 2400;
        int travellingTicks;

        BeeGoToKnownFlowerGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canBeeUse() {
            // Folia start - region threading
            if (Bee.this.savedFlowerPos != null && Bee.this.isTooFarAway(Bee.this.savedFlowerPos)) {
                Bee.this.savedFlowerPos = null;
            }
            // Folia end - region threading
            return Bee.this.savedFlowerPos != null && !Bee.this.hasHome() && this.wantsToGoToKnownFlower() && !Bee.this.closerThan(Bee.this.savedFlowerPos, 2);
        }

        @Override
        public boolean canBeeContinueToUse() {
            return this.canBeeUse();
        }

        @Override
        public void start() {
            this.travellingTicks = 0;
            super.start();
        }

        @Override
        public void stop() {
            this.travellingTicks = 0;
            Bee.this.navigation.stop();
            Bee.this.navigation.resetMaxVisitedNodesMultiplier();
        }

        @Override
        public void tick() {
            if (Bee.this.savedFlowerPos != null) {
                this.travellingTicks++;
                if (this.travellingTicks > this.adjustedTickDelay(2400)) {
                    Bee.this.dropFlower();
                } else if (!Bee.this.navigation.isInProgress()) {
                    if (Bee.this.isTooFarAway(Bee.this.savedFlowerPos)) {
                        Bee.this.dropFlower();
                    } else {
                        Bee.this.pathfindRandomlyTowards(Bee.this.savedFlowerPos);
                    }
                }
            }
        }

        private boolean wantsToGoToKnownFlower() {
            return Bee.this.ticksWithoutNectarSinceExitingHive > 600;
        }
    }

    class BeeGrowCropGoal extends Bee.BaseBeeGoal {
        static final int GROW_CHANCE = 30;

        @Override
        public boolean canBeeUse() {
            return Bee.this.getCropsGrownSincePollination() < 10 && !(Bee.this.random.nextFloat() < 0.3F) && Bee.this.hasNectar() && Bee.this.isHiveValid();
        }

        @Override
        public boolean canBeeContinueToUse() {
            return this.canBeeUse();
        }

        @Override
        public void tick() {
            if (Bee.this.random.nextInt(this.adjustedTickDelay(30)) == 0) {
                for (int i = 1; i <= 2; i++) {
                    BlockPos blockPos = Bee.this.blockPosition().below(i);
                    BlockState blockState = Bee.this.level().getBlockState(blockPos);
                    Block block = blockState.getBlock();
                    BlockState blockState1 = null;
                    if (blockState.is(BlockTags.BEE_GROWABLES)) {
                        if (block instanceof CropBlock cropBlock) {
                            if (!cropBlock.isMaxAge(blockState)) {
                                blockState1 = cropBlock.getStateForAge(cropBlock.getAge(blockState) + 1);
                            }
                        } else if (block instanceof StemBlock) {
                            int ageValue = blockState.getValue(StemBlock.AGE);
                            if (ageValue < 7) {
                                blockState1 = blockState.setValue(StemBlock.AGE, ageValue + 1);
                            }
                        } else if (blockState.is(Blocks.SWEET_BERRY_BUSH)) {
                            int ageValue = blockState.getValue(SweetBerryBushBlock.AGE);
                            if (ageValue < 3) {
                                blockState1 = blockState.setValue(SweetBerryBushBlock.AGE, ageValue + 1);
                            }
                        } else if (blockState.is(Blocks.CAVE_VINES) || blockState.is(Blocks.CAVE_VINES_PLANT)) {
                            BonemealableBlock bonemealableBlock = (BonemealableBlock)blockState.getBlock();
                            if (bonemealableBlock.isValidBonemealTarget(Bee.this.level(), blockPos, blockState)) {
                                bonemealableBlock.performBonemeal((ServerLevel)Bee.this.level(), Bee.this.random, blockPos, blockState);
                                blockState1 = Bee.this.level().getBlockState(blockPos);
                            }
                        }

                        if (blockState1 != null && org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(Bee.this, blockPos, blockState1)) { // CraftBukkit
                            Bee.this.level().levelEvent(LevelEvent.PARTICLES_BEE_GROWTH, blockPos, 15);
                            Bee.this.level().setBlockAndUpdate(blockPos, blockState1);
                            Bee.this.incrementNumCropsGrownSincePollination();
                        }
                    }
                }
            }
        }
    }

    class BeeHurtByOtherGoal extends HurtByTargetGoal {
        BeeHurtByOtherGoal(final Bee mob) {
            super(mob);
        }

        @Override
        public boolean canContinueToUse() {
            return Bee.this.isAngry() && super.canContinueToUse();
        }

        @Override
        protected void alertOther(Mob mob, LivingEntity target) {
            if (mob instanceof Bee && this.mob.hasLineOfSight(target)) {
                mob.setTarget(target, org.bukkit.event.entity.EntityTargetEvent.TargetReason.TARGET_ATTACKED_ENTITY); // CraftBukkit - reason
            }
        }
    }

    class BeeLocateHiveGoal extends Bee.BaseBeeGoal {
        @Override
        public boolean canBeeUse() {
            return Bee.this.remainingCooldownBeforeLocatingNewHive == 0 && !Bee.this.hasHive() && Bee.this.wantsToEnterHive();
        }

        @Override
        public boolean canBeeContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            Bee.this.remainingCooldownBeforeLocatingNewHive = 200;
            List<BlockPos> list = this.findNearbyHivesWithSpace();
            if (!list.isEmpty()) {
                for (BlockPos blockPos : list) {
                    if (!Bee.this.goToHiveGoal.isTargetBlacklisted(blockPos)) {
                        Bee.this.hivePos = blockPos;
                        return;
                    }
                }

                Bee.this.goToHiveGoal.clearBlacklist();
                Bee.this.hivePos = list.get(0);
            }
        }

        private List<BlockPos> findNearbyHivesWithSpace() {
            BlockPos blockPos = Bee.this.blockPosition();
            PoiManager poiManager = ((ServerLevel)Bee.this.level()).getPoiManager();
            Stream<PoiRecord> inRange = poiManager.getInRange(poiRecord -> poiRecord.is(PoiTypeTags.BEE_HOME), blockPos, 20, PoiManager.Occupancy.ANY);
            return inRange.map(PoiRecord::getPos)
                .filter(Bee.this::doesHiveHaveSpace)
                .sorted(Comparator.comparingDouble(pos -> pos.distSqr(blockPos)))
                .collect(Collectors.toList());
        }
    }

    class BeeLookControl extends LookControl {
        BeeLookControl(final Mob mob) {
            super(mob);
        }

        @Override
        public void tick() {
            if (!Bee.this.isAngry()) {
                super.tick();
            }
        }

        @Override
        protected boolean resetXRotOnTick() {
            return !Bee.this.beePollinateGoal.isPollinating();
        }
    }

    class BeePollinateGoal extends Bee.BaseBeeGoal {
        private static final int MIN_POLLINATION_TICKS = 400;
        private static final double ARRIVAL_THRESHOLD = 0.1;
        private static final int POSITION_CHANGE_CHANCE = 25;
        private static final float SPEED_MODIFIER = 0.35F;
        private static final float HOVER_HEIGHT_WITHIN_FLOWER = 0.6F;
        private static final float HOVER_POS_OFFSET = 0.33333334F;
        private static final int FLOWER_SEARCH_RADIUS = 5;
        private int successfulPollinatingTicks;
        private int lastSoundPlayedTick;
        private boolean pollinating;
        private @Nullable Vec3 hoverPos;
        private int pollinatingTicks;
        private static final int MAX_POLLINATING_TICKS = 600;
        private Long2LongOpenHashMap unreachableFlowerCache = new Long2LongOpenHashMap();

        BeePollinateGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canBeeUse() {
            if (Bee.this.remainingCooldownBeforeLocatingNewFlower > 0) {
                return false;
            } else if (Bee.this.hasNectar()) {
                return false;
            } else if (Bee.this.level().isRaining()) {
                return false;
            } else {
                Optional<BlockPos> optional = this.findNearbyFlower();
                if (optional.isPresent()) {
                    Bee.this.savedFlowerPos = optional.get();
                    Bee.this.navigation
                        .moveTo(Bee.this.savedFlowerPos.getX() + 0.5, Bee.this.savedFlowerPos.getY() + 0.5, Bee.this.savedFlowerPos.getZ() + 0.5, 1.2F);
                    return true;
                } else {
                    Bee.this.remainingCooldownBeforeLocatingNewFlower = Mth.nextInt(Bee.this.random, 20, 60);
                    return false;
                }
            }
        }

        @Override
        public boolean canBeeContinueToUse() {
            return this.pollinating
                && Bee.this.hasSavedFlowerPos()
                && !Bee.this.level().isRaining()
                && (!this.hasPollinatedLongEnough() || Bee.this.random.nextFloat() < 0.2F);
        }

        private boolean hasPollinatedLongEnough() {
            return this.successfulPollinatingTicks > 400;
        }

        boolean isPollinating() {
            return this.pollinating;
        }

        void stopPollinating() {
            this.pollinating = false;
        }

        @Override
        public void start() {
            this.successfulPollinatingTicks = 0;
            this.pollinatingTicks = 0;
            this.lastSoundPlayedTick = 0;
            this.pollinating = true;
            Bee.this.resetTicksWithoutNectarSinceExitingHive();
        }

        @Override
        public void stop() {
            if (this.hasPollinatedLongEnough()) {
                Bee.this.setHasNectar(true);
            }

            this.pollinating = false;
            Bee.this.navigation.stop();
            Bee.this.remainingCooldownBeforeLocatingNewFlower = 200;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (Bee.this.hasSavedFlowerPos()) {
                this.pollinatingTicks++;
                if (this.pollinatingTicks > 600) {
                    Bee.this.dropFlower();
                    this.pollinating = false;
                    Bee.this.remainingCooldownBeforeLocatingNewFlower = 200;
                } else if (Bee.this.savedFlowerPos != null) { // Paper - add null check since API can manipulate this
                    Vec3 vec3 = Vec3.atBottomCenterOf(Bee.this.savedFlowerPos).add(0.0, 0.6F, 0.0);
                    if (vec3.distanceTo(Bee.this.position()) > 1.0) {
                        this.hoverPos = vec3;
                        this.setWantedPos();
                    } else {
                        if (this.hoverPos == null) {
                            this.hoverPos = vec3;
                        }

                        boolean flag = Bee.this.position().distanceTo(this.hoverPos) <= 0.1;
                        boolean flag1 = true;
                        if (!flag && this.pollinatingTicks > 600) {
                            Bee.this.dropFlower();
                        } else {
                            if (flag) {
                                boolean flag2 = Bee.this.random.nextInt(25) == 0;
                                if (flag2) {
                                    this.hoverPos = new Vec3(vec3.x() + this.getOffset(), vec3.y(), vec3.z() + this.getOffset());
                                    Bee.this.navigation.stop();
                                } else {
                                    flag1 = false;
                                }

                                Bee.this.getLookControl().setLookAt(vec3.x(), vec3.y(), vec3.z());
                            }

                            if (flag1) {
                                this.setWantedPos();
                            }

                            this.successfulPollinatingTicks++;
                            if (Bee.this.random.nextFloat() < 0.05F && this.successfulPollinatingTicks > this.lastSoundPlayedTick + 60) {
                                this.lastSoundPlayedTick = this.successfulPollinatingTicks;
                                Bee.this.playSound(SoundEvents.BEE_POLLINATE, 1.0F, 1.0F);
                            }
                        }
                    }
                }
            }
        }

        private void setWantedPos() {
            Bee.this.getMoveControl().setWantedPosition(this.hoverPos.x(), this.hoverPos.y(), this.hoverPos.z(), 0.35F);
        }

        private float getOffset() {
            return (Bee.this.random.nextFloat() * 2.0F - 1.0F) * 0.33333334F;
        }

        private Optional<BlockPos> findNearbyFlower() {
            Iterable<BlockPos> iterable = BlockPos.withinManhattan(Bee.this.blockPosition(), 5, 5, 5);
            Long2LongOpenHashMap map = new Long2LongOpenHashMap();

            for (BlockPos blockPos : iterable) {
                long orDefault = this.unreachableFlowerCache.getOrDefault(blockPos.asLong(), Long.MIN_VALUE);
                if (Bee.this.level().getGameTime() < orDefault) {
                    map.put(blockPos.asLong(), orDefault);
                } else if (Bee.attractsBees(Bee.this.level().getBlockState(blockPos))) {
                    Path path = Bee.this.navigation.createPath(blockPos, 1);
                    if (path != null && path.canReach()) {
                        return Optional.of(blockPos);
                    }

                    map.put(blockPos.asLong(), Bee.this.level().getGameTime() + 600L);
                }
            }

            this.unreachableFlowerCache = map;
            return Optional.empty();
        }
    }

    class BeeWanderGoal extends Goal {
        BeeWanderGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return Bee.this.navigation.isDone() && Bee.this.random.nextInt(10) == 0;
        }

        @Override
        public boolean canContinueToUse() {
            return Bee.this.navigation.isInProgress();
        }

        @Override
        public void start() {
            Vec3 vec3 = this.findPos();
            if (vec3 != null) {
                Bee.this.navigation.moveTo(Bee.this.navigation.createPath(BlockPos.containing(vec3), 1), 1.0);
            }
        }

        private @Nullable Vec3 findPos() {
            Vec3 vec31;
            if (Bee.this.isHiveValid() && !Bee.this.closerThan(Bee.this.hivePos, this.getWanderThreshold())) {
                Vec3 vec3 = Vec3.atCenterOf(Bee.this.hivePos);
                vec31 = vec3.subtract(Bee.this.position()).normalize();
            } else {
                vec31 = Bee.this.getViewVector(0.0F);
            }

            int i = 8;
            Vec3 pos = HoverRandomPos.getPos(Bee.this, 8, 7, vec31.x, vec31.z, (float) (Math.PI / 2), 3, 1);
            return pos != null ? pos : AirAndWaterRandomPos.getPos(Bee.this, 8, 4, -2, vec31.x, vec31.z, (float) (Math.PI / 2));
        }

        private int getWanderThreshold() {
            int i = !Bee.this.hasHive() && !Bee.this.hasSavedFlowerPos() ? 16 : 24;
            return 48 - i;
        }
    }

    class ValidateFlowerGoal extends Bee.BaseBeeGoal {
        private final int validateFlowerCooldown = Mth.nextInt(Bee.this.random, 20, 40);
        private long lastValidateTick = -1L;

        @Override
        public void start() {
            if (Bee.this.savedFlowerPos != null && Bee.this.level().isLoaded(Bee.this.savedFlowerPos) && !this.isFlower(Bee.this.savedFlowerPos)) {
                Bee.this.dropFlower();
            }

            this.lastValidateTick = Bee.this.level().getGameTime();
        }

        @Override
        public boolean canBeeUse() {
            return Bee.this.level().getGameTime() > this.lastValidateTick + this.validateFlowerCooldown;
        }

        @Override
        public boolean canBeeContinueToUse() {
            return false;
        }

        private boolean isFlower(BlockPos pos) {
            return Bee.attractsBees(Bee.this.level().getBlockState(pos));
        }
    }

    class ValidateHiveGoal extends Bee.BaseBeeGoal {
        private final int VALIDATE_HIVE_COOLDOWN = Mth.nextInt(Bee.this.random, 20, 40);
        private long lastValidateTick = -1L;

        @Override
        public void start() {
            if (Bee.this.hivePos != null && Bee.this.level().isLoaded(Bee.this.hivePos) && !Bee.this.isHiveValid()) {
                Bee.this.dropHive();
            }

            this.lastValidateTick = Bee.this.level().getGameTime();
        }

        @Override
        public boolean canBeeUse() {
            return Bee.this.level().getGameTime() > this.lastValidateTick + this.VALIDATE_HIVE_COOLDOWN;
        }

        @Override
        public boolean canBeeContinueToUse() {
            return false;
        }
    }
}
