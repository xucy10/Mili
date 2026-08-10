package net.minecraft.world.entity.animal.axolotl;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import io.netty.buffer.ByteBuf;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.IntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.BinaryAnimator;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.EasingType;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.Util;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.SmoothSwimmingLookControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Bucketable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class Axolotl extends Animal implements Bucketable {
    public static final int TOTAL_PLAYDEAD_TIME = 200;
    private static final int POSE_ANIMATION_TICKS = 10;
    protected static final ImmutableList<? extends SensorType<? extends Sensor<? super Axolotl>>> SENSOR_TYPES = ImmutableList.of(
        SensorType.NEAREST_LIVING_ENTITIES, SensorType.NEAREST_ADULT, SensorType.HURT_BY, SensorType.AXOLOTL_ATTACKABLES, SensorType.FOOD_TEMPTATIONS
    );
    protected static final ImmutableList<? extends MemoryModuleType<?>> MEMORY_TYPES = ImmutableList.of(
        MemoryModuleType.BREED_TARGET,
        MemoryModuleType.NEAREST_LIVING_ENTITIES,
        MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
        MemoryModuleType.NEAREST_VISIBLE_PLAYER,
        MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER,
        MemoryModuleType.LOOK_TARGET,
        MemoryModuleType.WALK_TARGET,
        MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE,
        MemoryModuleType.PATH,
        MemoryModuleType.ATTACK_TARGET,
        MemoryModuleType.ATTACK_COOLING_DOWN,
        MemoryModuleType.NEAREST_VISIBLE_ADULT,
        MemoryModuleType.HURT_BY_ENTITY,
        MemoryModuleType.PLAY_DEAD_TICKS,
        MemoryModuleType.NEAREST_ATTACKABLE,
        MemoryModuleType.TEMPTING_PLAYER,
        MemoryModuleType.TEMPTATION_COOLDOWN_TICKS,
        MemoryModuleType.IS_TEMPTED,
        MemoryModuleType.HAS_HUNTING_COOLDOWN,
        MemoryModuleType.IS_PANICKING
    );
    private static final EntityDataAccessor<Integer> DATA_VARIANT = SynchedEntityData.defineId(Axolotl.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_PLAYING_DEAD = SynchedEntityData.defineId(Axolotl.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> FROM_BUCKET = SynchedEntityData.defineId(Axolotl.class, EntityDataSerializers.BOOLEAN);
    public static final double PLAYER_REGEN_DETECTION_RANGE = 20.0;
    public static final int RARE_VARIANT_CHANCE = 1200;
    private static final int AXOLOTL_TOTAL_AIR_SUPPLY = 6000;
    public static final String VARIANT_TAG = "Variant";
    private static final int REHYDRATE_AIR_SUPPLY = 1800;
    private static final int REGEN_BUFF_MAX_DURATION = 2400;
    private static final boolean DEFAULT_FROM_BUCKET = false;
    public final BinaryAnimator playingDeadAnimator = new BinaryAnimator(10, EasingType.IN_OUT_SINE);
    public final BinaryAnimator inWaterAnimator = new BinaryAnimator(10, EasingType.IN_OUT_SINE);
    public final BinaryAnimator onGroundAnimator = new BinaryAnimator(10, EasingType.IN_OUT_SINE);
    public final BinaryAnimator movingAnimator = new BinaryAnimator(10, EasingType.IN_OUT_SINE);
    private static final int REGEN_BUFF_BASE_DURATION = 100;

    public Axolotl(EntityType<? extends Axolotl> type, Level level) {
        super(type, level);
        this.setPathfindingMalus(PathType.WATER, 0.0F);
        this.moveControl = new Axolotl.AxolotlMoveControl(this);
        this.lookControl = new Axolotl.AxolotlLookControl(this, 20);
    }

    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        return 0.0F;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_VARIANT, 0);
        builder.define(DATA_PLAYING_DEAD, false);
        builder.define(FROM_BUCKET, false);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store("Variant", Axolotl.Variant.LEGACY_CODEC, this.getVariant());
        output.putBoolean("FromBucket", this.fromBucket());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setVariant(input.read("Variant", Axolotl.Variant.LEGACY_CODEC).orElse(Axolotl.Variant.DEFAULT));
        this.setFromBucket(input.getBooleanOr("FromBucket", false));
    }

    @Override
    public void playAmbientSound() {
        if (!this.isPlayingDead()) {
            super.playAmbientSound();
        }
    }

    @Override
    public SpawnGroupData finalizeSpawn(
        ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData spawnGroupData
    ) {
        boolean flag = false;
        if (spawnReason == EntitySpawnReason.BUCKET) {
            return spawnGroupData;
        } else {
            RandomSource random = level.getRandom();
            if (spawnGroupData instanceof Axolotl.AxolotlGroupData) {
                if (((Axolotl.AxolotlGroupData)spawnGroupData).getGroupSize() >= 2) {
                    flag = true;
                }
            } else {
                spawnGroupData = new Axolotl.AxolotlGroupData(Axolotl.Variant.getCommonSpawnVariant(random), Axolotl.Variant.getCommonSpawnVariant(random));
            }

            this.setVariant(((Axolotl.AxolotlGroupData)spawnGroupData).getVariant(random));
            if (flag) {
                this.setAge(-24000);
            }

            return super.finalizeSpawn(level, difficulty, spawnReason, spawnGroupData);
        }
    }

    @Override
    public void baseTick() {
        int airSupply = this.getAirSupply();
        super.baseTick();
        if (!this.isNoAi() && this.level() instanceof ServerLevel serverLevel) {
            this.handleAirSupply(serverLevel, airSupply);
        }

        if (this.level().isClientSide()) {
            this.tickAnimations();
        }
    }

    private void tickAnimations() {
        Axolotl.AnimationState animationState;
        if (this.isPlayingDead()) {
            animationState = Axolotl.AnimationState.PLAYING_DEAD;
        } else if (this.isInWater()) {
            animationState = Axolotl.AnimationState.IN_WATER;
        } else if (this.onGround()) {
            animationState = Axolotl.AnimationState.ON_GROUND;
        } else {
            animationState = Axolotl.AnimationState.IN_AIR;
        }

        this.playingDeadAnimator.tick(animationState == Axolotl.AnimationState.PLAYING_DEAD);
        this.inWaterAnimator.tick(animationState == Axolotl.AnimationState.IN_WATER);
        this.onGroundAnimator.tick(animationState == Axolotl.AnimationState.ON_GROUND);
        boolean flag = this.walkAnimation.isMoving() || this.getXRot() != this.xRotO || this.getYRot() != this.yRotO;
        this.movingAnimator.tick(flag);
    }

    protected void handleAirSupply(ServerLevel level, int airSupply) {
        if (this.isAlive() && !this.isInWaterOrRain()) {
            this.setAirSupply(airSupply - 1);
            if (this.shouldTakeDrowningDamage()) {
                this.setAirSupply(0);
                this.hurtServer(level, this.damageSources().dryOut(), 2.0F);
            }
        } else {
            this.setAirSupply(this.getMaxAirSupply());
        }
    }

    public void rehydrate() {
        int i = this.getAirSupply() + 1800;
        this.setAirSupply(Math.min(i, this.getMaxAirSupply()));
    }

    @Override
    public int getMaxAirSupply() {
        return this.maxAirTicks; // CraftBukkit - SPIGOT-6907: re-implement LivingEntity#setMaximumAir()
    }

    public Axolotl.Variant getVariant() {
        return Axolotl.Variant.byId(this.entityData.get(DATA_VARIANT));
    }

    public void setVariant(Axolotl.Variant variant) {
        this.entityData.set(DATA_VARIANT, variant.getId());
    }

    @Override
    public <T> @Nullable T get(DataComponentType<? extends T> component) {
        return component == DataComponents.AXOLOTL_VARIANT ? castComponentValue((DataComponentType<T>)component, this.getVariant()) : super.get(component);
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter componentGetter) {
        this.applyImplicitComponentIfPresent(componentGetter, DataComponents.AXOLOTL_VARIANT);
        super.applyImplicitComponents(componentGetter);
    }

    @Override
    protected <T> boolean applyImplicitComponent(DataComponentType<T> component, T value) {
        if (component == DataComponents.AXOLOTL_VARIANT) {
            this.setVariant(castComponentValue(DataComponents.AXOLOTL_VARIANT, value));
            return true;
        } else {
            return super.applyImplicitComponent(component, value);
        }
    }

    private static boolean useRareVariant(RandomSource random) {
        return random.nextInt(1200) == 0;
    }

    @Override
    public boolean checkSpawnObstruction(LevelReader level) {
        return level.isUnobstructed(this);
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    public void setPlayingDead(boolean playingDead) {
        this.entityData.set(DATA_PLAYING_DEAD, playingDead);
    }

    public boolean isPlayingDead() {
        return this.entityData.get(DATA_PLAYING_DEAD);
    }

    @Override
    public boolean fromBucket() {
        return this.entityData.get(FROM_BUCKET);
    }

    @Override
    public void setFromBucket(boolean fromBucket) {
        this.entityData.set(FROM_BUCKET, fromBucket);
    }

    @Override
    public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        Axolotl axolotl = EntityType.AXOLOTL.create(level, EntitySpawnReason.BREEDING);
        if (axolotl != null) {
            Axolotl.Variant rareSpawnVariant;
            if (useRareVariant(this.random)) {
                rareSpawnVariant = Axolotl.Variant.getRareSpawnVariant(this.random);
            } else {
                rareSpawnVariant = this.random.nextBoolean() ? this.getVariant() : ((Axolotl)partner).getVariant();
            }

            axolotl.setVariant(rareSpawnVariant);
            axolotl.setPersistenceRequired();
        }

        return axolotl;
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(ItemTags.AXOLOTL_FOOD);
    }

    @Override
    public boolean canBeLeashed() {
        return true;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        ProfilerFiller profilerFiller = Profiler.get();
        profilerFiller.push("axolotlBrain");
        this.getBrain().tick(level, this);
        profilerFiller.pop();
        profilerFiller.push("axolotlActivityUpdate");
        AxolotlAi.updateActivity(this);
        profilerFiller.pop();
        if (!this.isNoAi()) {
            Optional<Integer> memory = this.getBrain().getMemory(MemoryModuleType.PLAY_DEAD_TICKS);
            this.setPlayingDead(memory.isPresent() && memory.get() > 0);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes()
            .add(Attributes.MAX_HEALTH, 14.0)
            .add(Attributes.MOVEMENT_SPEED, 1.0)
            .add(Attributes.ATTACK_DAMAGE, 2.0)
            .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new AmphibiousPathNavigation(this, level);
    }

    @Override
    public void playAttackSound() {
        this.playSound(SoundEvents.AXOLOTL_ATTACK, 1.0F, 1.0F);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        float health = this.getHealth();
        if (!this.isNoAi()
            && this.level().random.nextInt(3) == 0
            && (this.level().random.nextInt(3) < amount || health / this.getMaxHealth() < 0.5F)
            && amount < health
            && this.isInWater()
            && (damageSource.getEntity() != null || damageSource.getDirectEntity() != null)
            && !this.isPlayingDead()) {
            this.brain.setMemory(MemoryModuleType.PLAY_DEAD_TICKS, 200);
        }

        return super.hurtServer(level, damageSource, amount);
    }

    @Override
    public int getMaxHeadXRot() {
        return 1;
    }

    @Override
    public int getMaxHeadYRot() {
        return 1;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        return Bucketable.bucketMobPickup(player, hand, this).orElse(super.mobInteract(player, hand));
    }

    @Override
    public void saveToBucketTag(ItemStack stack) {
        Bucketable.saveDefaultDataToBucketTag(this, stack);
        stack.copyFrom(DataComponents.AXOLOTL_VARIANT, this);
        CustomData.update(DataComponents.BUCKET_ENTITY_DATA, stack, tag -> {
            tag.putInt("Age", this.getAge());
            Brain<?> brain = this.getBrain();
            if (brain.hasMemoryValue(MemoryModuleType.HAS_HUNTING_COOLDOWN)) {
                tag.putLong("HuntingCooldown", brain.getTimeUntilExpiry(MemoryModuleType.HAS_HUNTING_COOLDOWN));
            }
        });
    }

    @Override
    public void loadFromBucketTag(CompoundTag tag) {
        Bucketable.loadDefaultDataFromBucketTag(this, tag);
        this.setAge(tag.getIntOr("Age", 0));
        tag.getLong("HuntingCooldown")
            .ifPresentOrElse(
                _long -> this.getBrain().setMemoryWithExpiry(MemoryModuleType.HAS_HUNTING_COOLDOWN, true, tag.getLongOr("HuntingCooldown", 0L)),
                () -> this.getBrain().setMemory(MemoryModuleType.HAS_HUNTING_COOLDOWN, Optional.empty())
            );
    }

    @Override
    public ItemStack getBucketItemStack() {
        return new ItemStack(Items.AXOLOTL_BUCKET);
    }

    @Override
    public SoundEvent getPickupSound() {
        return SoundEvents.BUCKET_FILL_AXOLOTL;
    }

    @Override
    public boolean canBeSeenAsEnemy() {
        return !this.isPlayingDead() && super.canBeSeenAsEnemy();
    }

    public static void onStopAttacking(ServerLevel level, Axolotl axolotl, LivingEntity target) {
        if (target.isDeadOrDying()) {
            DamageSource lastDamageSource = target.getLastDamageSource();
            if (lastDamageSource != null) {
                Entity entity = lastDamageSource.getEntity();
                if (entity != null && entity.getType() == EntityType.PLAYER) {
                    Player player = (Player)entity;
                    List<Player> entitiesOfClass = level.getEntitiesOfClass(Player.class, axolotl.getBoundingBox().inflate(20.0));
                    if (entitiesOfClass.contains(player)) {
                        axolotl.applySupportingEffects(player);
                    }
                }
            }
        }
    }

    public void applySupportingEffects(Player player) {
        MobEffectInstance effect = player.getEffect(MobEffects.REGENERATION);
        if (effect == null || effect.endsWithin(2399)) {
            int i = effect != null ? effect.getDuration() : 0;
            int min = Math.min(2400, 100 + i);
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, min, 0), this, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.AXOLOTL); // CraftBukkit
        }

        player.removeEffect(MobEffects.MINING_FATIGUE, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.AXOLOTL); // Paper - Add missing effect cause
    }

    @Override
    public boolean requiresCustomPersistence() {
        return super.requiresCustomPersistence() || this.fromBucket();
    }

    @Override
    public SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.AXOLOTL_HURT;
    }

    @Override
    public @Nullable SoundEvent getDeathSound() {
        return SoundEvents.AXOLOTL_DEATH;
    }

    @Override
    public @Nullable SoundEvent getAmbientSound() {
        return this.isInWater() ? SoundEvents.AXOLOTL_IDLE_WATER : SoundEvents.AXOLOTL_IDLE_AIR;
    }

    @Override
    public SoundEvent getSwimSplashSound() {
        return SoundEvents.AXOLOTL_SPLASH;
    }

    @Override
    public SoundEvent getSwimSound() {
        return SoundEvents.AXOLOTL_SWIM;
    }

    @Override
    protected Brain.Provider<Axolotl> brainProvider() {
        return Brain.provider(MEMORY_TYPES, SENSOR_TYPES);
    }

    @Override
    protected Brain<?> makeBrain(Dynamic<?> dynamic) {
        return AxolotlAi.makeBrain(this.brainProvider().makeBrain(dynamic));
    }

    @Override
    public Brain<Axolotl> getBrain() {
        return (Brain<Axolotl>)super.getBrain();
    }

    @Override
    protected void travelInWater(Vec3 travelVector, double gravity, boolean isFalling, double previousY) {
        this.moveRelative(this.getSpeed(), travelVector);
        this.move(MoverType.SELF, this.getDeltaMovement());
        this.setDeltaMovement(this.getDeltaMovement().scale(0.9));
    }

    @Override
    protected void usePlayerItem(Player player, InteractionHand hand, ItemStack stack) {
        if (stack.is(Items.TROPICAL_FISH_BUCKET)) {
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.WATER_BUCKET)));
        } else {
            super.usePlayerItem(player, hand, stack);
        }
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return !this.fromBucket() && !this.hasCustomName();
    }

    @Override
    public @Nullable LivingEntity getTarget() {
        return this.getTargetFromBrain();
    }

    public static boolean checkAxolotlSpawnRules(
        EntityType<? extends LivingEntity> entityType, ServerLevelAccessor level, EntitySpawnReason spawnReason, BlockPos pos, RandomSource random
    ) {
        return level.getBlockState(pos.below()).is(BlockTags.AXOLOTLS_SPAWNABLE_ON);
    }

    // CraftBukkit start - SPIGOT-6907: re-implement LivingEntity#setMaximumAir()
    @Override
    public int getDefaultMaxAirSupply() {
        return AXOLOTL_TOTAL_AIR_SUPPLY;
    }
    // CraftBukkit end

    public static enum AnimationState {
        PLAYING_DEAD,
        IN_WATER,
        ON_GROUND,
        IN_AIR;
    }

    public static class AxolotlGroupData extends AgeableMob.AgeableMobGroupData {
        public final Axolotl.Variant[] types;

        public AxolotlGroupData(Axolotl.Variant... types) {
            super(false);
            this.types = types;
        }

        public Axolotl.Variant getVariant(RandomSource random) {
            return this.types[random.nextInt(this.types.length)];
        }
    }

    class AxolotlLookControl extends SmoothSwimmingLookControl {
        public AxolotlLookControl(final Axolotl mob, final int maxYRotFromCenter) {
            super(mob, maxYRotFromCenter);
        }

        @Override
        public void tick() {
            if (!Axolotl.this.isPlayingDead()) {
                super.tick();
            }
        }
    }

    static class AxolotlMoveControl extends SmoothSwimmingMoveControl {
        private final Axolotl axolotl;

        public AxolotlMoveControl(Axolotl axolotl) {
            super(axolotl, 85, 10, 0.1F, 0.5F, false);
            this.axolotl = axolotl;
        }

        @Override
        public void tick() {
            if (!this.axolotl.isPlayingDead()) {
                super.tick();
            }
        }
    }

    public static enum Variant implements StringRepresentable {
        LUCY(0, "lucy", true),
        WILD(1, "wild", true),
        GOLD(2, "gold", true),
        CYAN(3, "cyan", true),
        BLUE(4, "blue", false);

        public static final Axolotl.Variant DEFAULT = LUCY;
        private static final IntFunction<Axolotl.Variant> BY_ID = ByIdMap.continuous(Axolotl.Variant::getId, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
        public static final StreamCodec<ByteBuf, Axolotl.Variant> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, Axolotl.Variant::getId);
        public static final Codec<Axolotl.Variant> CODEC = StringRepresentable.fromEnum(Axolotl.Variant::values);
        @Deprecated
        public static final Codec<Axolotl.Variant> LEGACY_CODEC = Codec.INT.xmap(BY_ID::apply, Axolotl.Variant::getId);
        private final int id;
        private final String name;
        private final boolean common;

        private Variant(final int id, final String name, final boolean common) {
            this.id = id;
            this.name = name;
            this.common = common;
        }

        public int getId() {
            return this.id;
        }

        public String getName() {
            return this.name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        public static Axolotl.Variant byId(int id) {
            return BY_ID.apply(id);
        }

        public static Axolotl.Variant getCommonSpawnVariant(RandomSource random) {
            return getSpawnVariant(random, true);
        }

        public static Axolotl.Variant getRareSpawnVariant(RandomSource random) {
            return getSpawnVariant(random, false);
        }

        private static Axolotl.Variant getSpawnVariant(RandomSource random, boolean common) {
            Axolotl.Variant[] variants = Arrays.stream(values()).filter(variant -> variant.common == common).toArray(Axolotl.Variant[]::new);
            return Util.getRandom(variants, random);
        }
    }
}
