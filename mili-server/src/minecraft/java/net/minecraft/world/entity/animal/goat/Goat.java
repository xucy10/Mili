package net.minecraft.world.entity.animal.goat;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Dynamic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.InstrumentTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Instrument;
import net.minecraft.world.item.InstrumentItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class Goat extends Animal {
    public static final EntityDimensions LONG_JUMPING_DIMENSIONS = EntityDimensions.scalable(0.9F, 1.3F).scale(0.7F);
    private static final int ADULT_ATTACK_DAMAGE = 2;
    private static final int BABY_ATTACK_DAMAGE = 1;
    protected static final ImmutableList<SensorType<? extends Sensor<? super Goat>>> SENSOR_TYPES = ImmutableList.of(
        SensorType.NEAREST_LIVING_ENTITIES,
        SensorType.NEAREST_PLAYERS,
        SensorType.NEAREST_ITEMS,
        SensorType.NEAREST_ADULT,
        SensorType.HURT_BY,
        SensorType.FOOD_TEMPTATIONS
    );
    protected static final ImmutableList<MemoryModuleType<?>> MEMORY_TYPES = ImmutableList.of(
        MemoryModuleType.LOOK_TARGET,
        MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
        MemoryModuleType.WALK_TARGET,
        MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE,
        MemoryModuleType.PATH,
        MemoryModuleType.ATE_RECENTLY,
        MemoryModuleType.BREED_TARGET,
        MemoryModuleType.LONG_JUMP_COOLDOWN_TICKS,
        MemoryModuleType.LONG_JUMP_MID_JUMP,
        MemoryModuleType.TEMPTING_PLAYER,
        MemoryModuleType.NEAREST_VISIBLE_ADULT,
        MemoryModuleType.TEMPTATION_COOLDOWN_TICKS,
        MemoryModuleType.IS_TEMPTED,
        MemoryModuleType.RAM_COOLDOWN_TICKS,
        MemoryModuleType.RAM_TARGET,
        MemoryModuleType.IS_PANICKING
    );
    public static final int GOAT_FALL_DAMAGE_REDUCTION = 10;
    public static final double GOAT_SCREAMING_CHANCE = 0.02;
    public static final double UNIHORN_CHANCE = 0.1F;
    private static final EntityDataAccessor<Boolean> DATA_IS_SCREAMING_GOAT = SynchedEntityData.defineId(Goat.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> DATA_HAS_LEFT_HORN = SynchedEntityData.defineId(Goat.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> DATA_HAS_RIGHT_HORN = SynchedEntityData.defineId(Goat.class, EntityDataSerializers.BOOLEAN);
    private static final boolean DEFAULT_IS_SCREAMING = false;
    private static final boolean DEFAULT_HAS_LEFT_HORN = true;
    private static final boolean DEFAULT_HAS_RIGHT_HORN = true;
    private boolean isLoweringHead;
    private int lowerHeadTick;

    public Goat(EntityType<? extends Goat> type, Level level) {
        super(type, level);
        this.getNavigation().setCanFloat(true);
        this.setPathfindingMalus(PathType.POWDER_SNOW, -1.0F);
        this.setPathfindingMalus(PathType.DANGER_POWDER_SNOW, -1.0F);
    }

    public ItemStack createHorn() {
        RandomSource randomSource = RandomSource.create(this.getUUID().hashCode());
        TagKey<Instrument> tagKey = this.isScreamingGoat() ? InstrumentTags.SCREAMING_GOAT_HORNS : InstrumentTags.REGULAR_GOAT_HORNS;
        return this.level()
            .registryAccess()
            .lookupOrThrow(Registries.INSTRUMENT)
            .getRandomElementOf(tagKey, randomSource)
            .map(holder -> InstrumentItem.create(Items.GOAT_HORN, (Holder<Instrument>)holder))
            .orElseGet(() -> new ItemStack(Items.GOAT_HORN));
    }

    @Override
    protected Brain.Provider<Goat> brainProvider() {
        return Brain.provider(MEMORY_TYPES, SENSOR_TYPES);
    }

    @Override
    protected Brain<?> makeBrain(Dynamic<?> dynamic) {
        return GoatAi.makeBrain(this.brainProvider().makeBrain(dynamic));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes().add(Attributes.MAX_HEALTH, 10.0).add(Attributes.MOVEMENT_SPEED, 0.2F).add(Attributes.ATTACK_DAMAGE, 2.0);
    }

    @Override
    protected void ageBoundaryReached() {
        if (this.isBaby()) {
            this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(1.0);
            this.removeHorns();
        } else {
            this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(2.0);
            this.addHorns();
        }
    }

    @Override
    protected int calculateFallDamage(double fallDistance, float damageMultiplier) {
        return super.calculateFallDamage(fallDistance, damageMultiplier) - 10;
    }

    @Override
    public SoundEvent getAmbientSound() {
        return this.isScreamingGoat() ? SoundEvents.GOAT_SCREAMING_AMBIENT : SoundEvents.GOAT_AMBIENT;
    }

    @Override
    public SoundEvent getHurtSound(DamageSource damageSource) {
        return this.isScreamingGoat() ? SoundEvents.GOAT_SCREAMING_HURT : SoundEvents.GOAT_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return this.isScreamingGoat() ? SoundEvents.GOAT_SCREAMING_DEATH : SoundEvents.GOAT_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.GOAT_STEP, 0.15F, 1.0F);
    }

    protected SoundEvent getMilkingSound() {
        return this.isScreamingGoat() ? SoundEvents.GOAT_SCREAMING_MILK : SoundEvents.GOAT_MILK;
    }

    @Override
    public @Nullable Goat getBreedOffspring(ServerLevel level, AgeableMob partner) {
        Goat goat = EntityType.GOAT.create(level, EntitySpawnReason.BREEDING);
        if (goat != null) {
            GoatAi.initMemories(goat, level.getRandom());
            AgeableMob ageableMob = (AgeableMob)(level.getRandom().nextBoolean() ? this : partner);
            boolean flag = ageableMob instanceof Goat goat1 && goat1.isScreamingGoat() || level.getRandom().nextDouble() < 0.02;
            goat.setScreamingGoat(flag);
        }

        return goat;
    }

    @Override
    public Brain<Goat> getBrain() {
        return (Brain<Goat>)super.getBrain();
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        ProfilerFiller profilerFiller = Profiler.get();
        profilerFiller.push("goatBrain");
        this.getBrain().tick(level, this);
        profilerFiller.pop();
        profilerFiller.push("goatActivityUpdate");
        GoatAi.updateActivity(this);
        profilerFiller.pop();
        super.customServerAiStep(level);
    }

    @Override
    public int getMaxHeadYRot() {
        return 15;
    }

    @Override
    public void setYHeadRot(float rotation) {
        int maxHeadYRot = this.getMaxHeadYRot();
        float f = Mth.degreesDifference(this.yBodyRot, rotation);
        float f1 = Mth.clamp(f, (float)(-maxHeadYRot), (float)maxHeadYRot);
        super.setYHeadRot(this.yBodyRot + f1);
    }

    @Override
    protected void playEatingSound() {
        this.level()
            .playSound(
                null,
                this,
                this.isScreamingGoat() ? SoundEvents.GOAT_SCREAMING_EAT : SoundEvents.GOAT_EAT,
                SoundSource.NEUTRAL,
                1.0F,
                Mth.randomBetween(this.level().random, 0.8F, 1.2F)
            );
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(ItemTags.GOAT_FOOD);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemInHand = player.getItemInHand(hand);
        if (itemInHand.is(Items.BUCKET) && !this.isBaby()) {
            // CraftBukkit start - Got milk?
            org.bukkit.event.player.PlayerBucketFillEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerBucketFillEvent(player.level(), player, this.blockPosition(), this.blockPosition(), null, itemInHand, Items.MILK_BUCKET, hand);

            if (event.isCancelled()) {
                player.containerMenu.sendAllDataToRemote(); // Paper - Fix inventory desync
                return InteractionResult.PASS;
            }
            // CraftBukkit end
            player.playSound(this.getMilkingSound(), 1.0F, 1.0F);
            ItemStack itemStack = ItemUtils.createFilledResult(itemInHand, player, org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(event.getItemStack())); // CraftBukkit
            player.setItemInHand(hand, itemStack);
            return InteractionResult.SUCCESS;
        } else {
            boolean isFood = this.isFood(itemInHand); // Paper - track before stack is possibly decreased to 0 (Fixes MC-244739)
            InteractionResult interactionResult = super.mobInteract(player, hand);
            if (interactionResult.consumesAction() && isFood) { // Paper
                this.playEatingSound();
            }

            return interactionResult;
        }
    }

    @Override
    public SpawnGroupData finalizeSpawn(
        ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData spawnGroupData
    ) {
        RandomSource random = level.getRandom();
        GoatAi.initMemories(this, random);
        this.setScreamingGoat(random.nextDouble() < 0.02);
        this.ageBoundaryReached();
        if (!this.isBaby() && random.nextFloat() < 0.1F) {
            EntityDataAccessor<Boolean> entityDataAccessor = random.nextBoolean() ? DATA_HAS_LEFT_HORN : DATA_HAS_RIGHT_HORN;
            this.entityData.set(entityDataAccessor, false);
        }

        return super.finalizeSpawn(level, difficulty, spawnReason, spawnGroupData);
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        return pose == Pose.LONG_JUMPING ? LONG_JUMPING_DIMENSIONS.scale(this.getAgeScale()) : super.getDefaultDimensions(pose);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("IsScreamingGoat", this.isScreamingGoat());
        output.putBoolean("HasLeftHorn", this.hasLeftHorn());
        output.putBoolean("HasRightHorn", this.hasRightHorn());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setScreamingGoat(input.getBooleanOr("IsScreamingGoat", false));
        this.entityData.set(DATA_HAS_LEFT_HORN, input.getBooleanOr("HasLeftHorn", true));
        this.entityData.set(DATA_HAS_RIGHT_HORN, input.getBooleanOr("HasRightHorn", true));
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EntityEvent.START_RAM) {
            this.isLoweringHead = true;
        } else if (id == EntityEvent.END_RAM) {
            this.isLoweringHead = false;
        } else {
            super.handleEntityEvent(id);
        }
    }

    @Override
    public void aiStep() {
        if (this.isLoweringHead) {
            this.lowerHeadTick++;
        } else {
            this.lowerHeadTick -= 2;
        }

        this.lowerHeadTick = Mth.clamp(this.lowerHeadTick, 0, 20);
        super.aiStep();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_IS_SCREAMING_GOAT, false);
        builder.define(DATA_HAS_LEFT_HORN, true);
        builder.define(DATA_HAS_RIGHT_HORN, true);
    }

    public boolean hasLeftHorn() {
        return this.entityData.get(DATA_HAS_LEFT_HORN);
    }

    public boolean hasRightHorn() {
        return this.entityData.get(DATA_HAS_RIGHT_HORN);
    }

    public boolean dropHorn() {
        boolean hasLeftHorn = this.hasLeftHorn();
        boolean hasRightHorn = this.hasRightHorn();
        if (!hasLeftHorn && !hasRightHorn) {
            return false;
        } else {
            EntityDataAccessor<Boolean> entityDataAccessor;
            if (!hasLeftHorn) {
                entityDataAccessor = DATA_HAS_RIGHT_HORN;
            } else if (!hasRightHorn) {
                entityDataAccessor = DATA_HAS_LEFT_HORN;
            } else {
                entityDataAccessor = this.random.nextBoolean() ? DATA_HAS_LEFT_HORN : DATA_HAS_RIGHT_HORN;
            }

            this.entityData.set(entityDataAccessor, false);
            Vec3 vec3 = this.position();
            ItemStack itemStack = this.createHorn();
            double d = Mth.randomBetween(this.random, -0.2F, 0.2F);
            double d1 = Mth.randomBetween(this.random, 0.3F, 0.7F);
            double d2 = Mth.randomBetween(this.random, -0.2F, 0.2F);
            ItemEntity itemEntity = new ItemEntity(this.level(), vec3.x(), vec3.y(), vec3.z(), itemStack, d, d1, d2);
            return this.spawnAtLocation((net.minecraft.server.level.ServerLevel) this.level(), itemEntity) != null; // Paper - Call EntityDropItemEvent
        }
    }

    public void addHorns() {
        this.entityData.set(DATA_HAS_LEFT_HORN, true);
        this.entityData.set(DATA_HAS_RIGHT_HORN, true);
    }

    public void removeHorns() {
        this.entityData.set(DATA_HAS_LEFT_HORN, false);
        this.entityData.set(DATA_HAS_RIGHT_HORN, false);
    }

    public boolean isScreamingGoat() {
        return this.entityData.get(DATA_IS_SCREAMING_GOAT);
    }

    public void setScreamingGoat(boolean isScreamingGoat) {
        this.entityData.set(DATA_IS_SCREAMING_GOAT, isScreamingGoat);
    }

    public float getRammingXHeadRot() {
        return this.lowerHeadTick / 20.0F * 30.0F * (float) (Math.PI / 180.0);
    }

    public static boolean checkGoatSpawnRules(
        EntityType<? extends Animal> entityType, LevelAccessor level, EntitySpawnReason spawnReason, BlockPos pos, RandomSource random
    ) {
        return level.getBlockState(pos.below()).is(BlockTags.GOATS_SPAWNABLE_ON) && isBrightEnoughToSpawn(level, pos);
    }

    // Paper start - Goat ram API
    public void ram(net.minecraft.world.entity.LivingEntity entity) {
        Brain<Goat> brain = this.getBrain();
        brain.setMemory(MemoryModuleType.RAM_TARGET, entity.position());
        brain.eraseMemory(MemoryModuleType.RAM_COOLDOWN_TICKS);
        brain.eraseMemory(MemoryModuleType.BREED_TARGET);
        brain.eraseMemory(MemoryModuleType.TEMPTING_PLAYER);
        brain.setActiveActivityIfPossible(net.minecraft.world.entity.schedule.Activity.RAM);
    }
    // Paper end - Goat ram API
}
