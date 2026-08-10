package net.minecraft.world.entity.animal.fox;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.ClimbOnTopOfPowderSnowGoal;
import net.minecraft.world.entity.ai.goal.FleeSunGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.JumpGoal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveToBlockGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.StrollThroughVillageGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import net.minecraft.world.entity.animal.fish.AbstractSchoolingFish;
import net.minecraft.world.entity.animal.polarbear.PolarBear;
import net.minecraft.world.entity.animal.rabbit.Rabbit;
import net.minecraft.world.entity.animal.turtle.Turtle;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class Fox extends Animal {
    private static final EntityDataAccessor<Integer> DATA_TYPE_ID = SynchedEntityData.defineId(Fox.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> DATA_FLAGS_ID = SynchedEntityData.defineId(Fox.class, EntityDataSerializers.BYTE);
    private static final int FLAG_SITTING = 1;
    public static final int FLAG_CROUCHING = 4;
    public static final int FLAG_INTERESTED = 8;
    public static final int FLAG_POUNCING = 16;
    private static final int FLAG_SLEEPING = 32;
    private static final int FLAG_FACEPLANTED = 64;
    private static final int FLAG_DEFENDING = 128;
    public static final EntityDataAccessor<Optional<EntityReference<LivingEntity>>> DATA_TRUSTED_ID_0 = SynchedEntityData.defineId(
        Fox.class, EntityDataSerializers.OPTIONAL_LIVING_ENTITY_REFERENCE
    );
    public static final EntityDataAccessor<Optional<EntityReference<LivingEntity>>> DATA_TRUSTED_ID_1 = SynchedEntityData.defineId(
        Fox.class, EntityDataSerializers.OPTIONAL_LIVING_ENTITY_REFERENCE
    );
    static final Predicate<ItemEntity> ALLOWED_ITEMS = item -> !item.hasPickUpDelay() && item.isAlive();
    private static final Predicate<Entity> TRUSTED_TARGET_SELECTOR = entity -> entity instanceof LivingEntity livingEntity
        && livingEntity.getLastHurtMob() != null
        && livingEntity.getLastHurtMobTimestamp() < livingEntity.tickCount + 600;
    static final Predicate<Entity> STALKABLE_PREY = entity -> entity instanceof Chicken || entity instanceof Rabbit;
    private static final Predicate<Entity> AVOID_PLAYERS = entity -> !entity.isDiscrete() && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(entity);
    private static final int MIN_TICKS_BEFORE_EAT = 600;
    private static final EntityDimensions BABY_DIMENSIONS = EntityType.FOX.getDimensions().scale(0.5F).withEyeHeight(0.2975F);
    private static final Codec<List<EntityReference<LivingEntity>>> TRUSTED_LIST_CODEC = EntityReference.<LivingEntity>codec().listOf();
    private static final boolean DEFAULT_SLEEPING = false;
    private static final boolean DEFAULT_SITTING = false;
    private static final boolean DEFAULT_CROUCHING = false;
    private Goal landTargetGoal;
    private Goal turtleEggTargetGoal;
    private Goal fishTargetGoal;
    private float interestedAngle;
    private float interestedAngleO;
    float crouchAmount;
    float crouchAmountO;
    private int ticksSinceEaten;

    public Fox(EntityType<? extends Fox> type, Level level) {
        super(type, level);
        this.lookControl = new Fox.FoxLookControl();
        this.moveControl = new Fox.FoxMoveControl();
        this.setPathfindingMalus(PathType.DANGER_OTHER, 0.0F);
        this.setPathfindingMalus(PathType.DAMAGE_OTHER, 0.0F);
        this.setCanPickUpLoot(true);
        this.getNavigation().setRequiredPathLength(32.0F);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_TRUSTED_ID_0, Optional.empty());
        builder.define(DATA_TRUSTED_ID_1, Optional.empty());
        builder.define(DATA_TYPE_ID, Fox.Variant.DEFAULT.getId());
        builder.define(DATA_FLAGS_ID, (byte)0);
    }

    @Override
    protected void registerGoals() {
        this.landTargetGoal = new NearestAttackableTargetGoal<>(
            this, Animal.class, 10, false, false, (entity, level) -> entity instanceof Chicken || entity instanceof Rabbit
        );
        this.turtleEggTargetGoal = new NearestAttackableTargetGoal<>(this, Turtle.class, 10, false, false, Turtle.BABY_ON_LAND_SELECTOR);
        this.fishTargetGoal = new NearestAttackableTargetGoal<>(
            this, AbstractFish.class, 20, false, false, (entity, level) -> entity instanceof AbstractSchoolingFish
        );
        this.goalSelector.addGoal(0, new Fox.FoxFloatGoal());
        this.goalSelector.addGoal(0, new ClimbOnTopOfPowderSnowGoal(this, this.level()));
        this.goalSelector.addGoal(1, new Fox.FaceplantGoal());
        this.goalSelector.addGoal(2, new Fox.FoxPanicGoal(2.2));
        this.goalSelector.addGoal(3, new Fox.FoxBreedGoal(1.0));
        this.goalSelector
            .addGoal(
                4,
                new AvoidEntityGoal<>(this, Player.class, 16.0F, 1.6, 1.4, entity -> AVOID_PLAYERS.test(entity) && !this.trusts(entity) && !this.isDefending())
            );
        this.goalSelector
            .addGoal(4, new AvoidEntityGoal<>(this, Wolf.class, 8.0F, 1.6, 1.4, livingEntity -> !((Wolf)livingEntity).isTame() && !this.isDefending()));
        this.goalSelector.addGoal(4, new AvoidEntityGoal<>(this, PolarBear.class, 8.0F, 1.6, 1.4, livingEntity -> !this.isDefending()));
        this.goalSelector.addGoal(5, new Fox.StalkPreyGoal());
        this.goalSelector.addGoal(6, new Fox.FoxPounceGoal());
        this.goalSelector.addGoal(6, new Fox.SeekShelterGoal(1.25));
        this.goalSelector.addGoal(7, new Fox.FoxMeleeAttackGoal(1.2F, true));
        this.goalSelector.addGoal(7, new Fox.SleepGoal());
        this.goalSelector.addGoal(8, new Fox.FoxFollowParentGoal(this, 1.25));
        this.goalSelector.addGoal(9, new Fox.FoxStrollThroughVillageGoal(32, 200));
        this.goalSelector.addGoal(10, new Fox.FoxEatBerriesGoal(1.2F, 12, 1));
        this.goalSelector.addGoal(10, new LeapAtTargetGoal(this, 0.4F));
        this.goalSelector.addGoal(11, new WaterAvoidingRandomStrollGoal(this, 1.0));
        this.goalSelector.addGoal(11, new Fox.FoxSearchForItemsGoal());
        this.goalSelector.addGoal(12, new Fox.FoxLookAtPlayerGoal(this, Player.class, 24.0F));
        this.goalSelector.addGoal(13, new Fox.PerchAndSearchGoal());
        this.targetSelector
            .addGoal(
                3,
                new Fox.DefendTrustedTargetGoal(
                    LivingEntity.class, false, false, (entity, level) -> TRUSTED_TARGET_SELECTOR.test(entity) && !this.trusts(entity)
                )
            );
    }

    @Override
    public void aiStep() {
        if (!this.level().isClientSide() && this.isAlive() && this.isEffectiveAi()) {
            this.ticksSinceEaten++;
            ItemStack itemBySlot = this.getItemBySlot(EquipmentSlot.MAINHAND);
            if (this.canEat(itemBySlot)) {
                if (this.ticksSinceEaten > 600) {
                    ItemStack itemStack = itemBySlot.finishUsingItem(this.level(), this);
                    if (!itemStack.isEmpty()) {
                        this.setItemSlot(EquipmentSlot.MAINHAND, itemStack);
                    }

                    this.ticksSinceEaten = 0;
                } else if (this.ticksSinceEaten > 560 && this.random.nextFloat() < 0.1F) {
                    this.playEatingSound();
                    this.level().broadcastEntityEvent(this, EntityEvent.FOX_EAT);
                }
            }

            LivingEntity target = this.getTarget();
            if (target == null || !target.isAlive()) {
                this.setIsCrouching(false);
                this.setIsInterested(false);
            }
        }

        if (this.isSleeping() || this.isImmobile()) {
            this.jumping = false;
            this.xxa = 0.0F;
            this.zza = 0.0F;
        }

        super.aiStep();
        if (this.isDefending() && this.random.nextFloat() < 0.05F) {
            this.playSound(SoundEvents.FOX_AGGRO, 1.0F, 1.0F);
        }
    }

    @Override
    protected boolean isImmobile() {
        return this.isDeadOrDying();
    }

    private boolean canEat(ItemStack stack) {
        return this.isConsumableFood(stack) && this.getTarget() == null && this.onGround() && !this.isSleeping();
    }

    private boolean isConsumableFood(ItemStack stack) {
        return stack.has(DataComponents.FOOD) && stack.has(DataComponents.CONSUMABLE);
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        if (random.nextFloat() < 0.2F) {
            float randomFloat = random.nextFloat();
            ItemStack itemStack;
            if (randomFloat < 0.05F) {
                itemStack = new ItemStack(Items.EMERALD);
            } else if (randomFloat < 0.2F) {
                itemStack = new ItemStack(Items.EGG);
            } else if (randomFloat < 0.4F) {
                itemStack = random.nextBoolean() ? new ItemStack(Items.RABBIT_FOOT) : new ItemStack(Items.RABBIT_HIDE);
            } else if (randomFloat < 0.6F) {
                itemStack = new ItemStack(Items.WHEAT);
            } else if (randomFloat < 0.8F) {
                itemStack = new ItemStack(Items.LEATHER);
            } else {
                itemStack = new ItemStack(Items.FEATHER);
            }

            this.setItemSlot(EquipmentSlot.MAINHAND, itemStack);
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EntityEvent.FOX_EAT) {
            ItemStack itemBySlot = this.getItemBySlot(EquipmentSlot.MAINHAND);
            if (!itemBySlot.isEmpty()) {
                for (int i = 0; i < 8; i++) {
                    Vec3 vec3 = new Vec3((this.random.nextFloat() - 0.5) * 0.1, this.random.nextFloat() * 0.1 + 0.1, 0.0)
                        .xRot(-this.getXRot() * (float) (Math.PI / 180.0))
                        .yRot(-this.getYRot() * (float) (Math.PI / 180.0));
                    this.level()
                        .addParticle(
                            new ItemParticleOption(ParticleTypes.ITEM, itemBySlot),
                            this.getX() + this.getLookAngle().x / 2.0,
                            this.getY(),
                            this.getZ() + this.getLookAngle().z / 2.0,
                            vec3.x,
                            vec3.y + 0.05,
                            vec3.z
                        );
                }
            }
        } else {
            super.handleEntityEvent(id);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes()
            .add(Attributes.MOVEMENT_SPEED, 0.3F)
            .add(Attributes.MAX_HEALTH, 10.0)
            .add(Attributes.ATTACK_DAMAGE, 2.0)
            .add(Attributes.SAFE_FALL_DISTANCE, 5.0)
            .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    public @Nullable Fox getBreedOffspring(ServerLevel level, AgeableMob partner) {
        Fox fox = EntityType.FOX.create(level, EntitySpawnReason.BREEDING);
        if (fox != null) {
            fox.setVariant(this.random.nextBoolean() ? this.getVariant() : ((Fox)partner).getVariant());
        }

        return fox;
    }

    public static boolean checkFoxSpawnRules(EntityType<Fox> entityType, LevelAccessor level, EntitySpawnReason spawnReason, BlockPos pos, RandomSource random) {
        return level.getBlockState(pos.below()).is(BlockTags.FOXES_SPAWNABLE_ON) && isBrightEnoughToSpawn(level, pos);
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(
        ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData spawnGroupData
    ) {
        Holder<Biome> biome = level.getBiome(this.blockPosition());
        Fox.Variant variant = Fox.Variant.byBiome(biome);
        boolean flag = false;
        if (spawnGroupData instanceof Fox.FoxGroupData foxGroupData) {
            variant = foxGroupData.variant;
            if (foxGroupData.getGroupSize() >= 2) {
                flag = true;
            }
        } else {
            spawnGroupData = new Fox.FoxGroupData(variant);
        }

        this.setVariant(variant);
        if (flag) {
            this.setAge(-24000);
        }

        if (level instanceof ServerLevel) {
            this.setTargetGoals();
        }

        this.populateDefaultEquipmentSlots(level.getRandom(), difficulty);
        return super.finalizeSpawn(level, difficulty, spawnReason, spawnGroupData);
    }

    private void setTargetGoals() {
        if (this.getVariant() == Fox.Variant.RED) {
            this.targetSelector.addGoal(4, this.landTargetGoal);
            this.targetSelector.addGoal(4, this.turtleEggTargetGoal);
            this.targetSelector.addGoal(6, this.fishTargetGoal);
        } else {
            this.targetSelector.addGoal(4, this.fishTargetGoal);
            this.targetSelector.addGoal(6, this.landTargetGoal);
            this.targetSelector.addGoal(6, this.turtleEggTargetGoal);
        }
    }

    @Override
    protected void playEatingSound() {
        this.playSound(SoundEvents.FOX_EAT, 1.0F, 1.0F);
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        return this.isBaby() ? BABY_DIMENSIONS : super.getDefaultDimensions(pose);
    }

    public Fox.Variant getVariant() {
        return Fox.Variant.byId(this.entityData.get(DATA_TYPE_ID));
    }

    public void setVariant(Fox.Variant variant) {
        this.entityData.set(DATA_TYPE_ID, variant.getId());
    }

    @Override
    public <T> @Nullable T get(DataComponentType<? extends T> component) {
        return component == DataComponents.FOX_VARIANT ? castComponentValue((DataComponentType<T>)component, this.getVariant()) : super.get(component);
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter componentGetter) {
        this.applyImplicitComponentIfPresent(componentGetter, DataComponents.FOX_VARIANT);
        super.applyImplicitComponents(componentGetter);
    }

    @Override
    protected <T> boolean applyImplicitComponent(DataComponentType<T> component, T value) {
        if (component == DataComponents.FOX_VARIANT) {
            this.setVariant(castComponentValue(DataComponents.FOX_VARIANT, value));
            return true;
        } else {
            return super.applyImplicitComponent(component, value);
        }
    }

    Stream<EntityReference<LivingEntity>> getTrustedEntities() {
        return Stream.concat(this.entityData.get(DATA_TRUSTED_ID_0).stream(), this.entityData.get(DATA_TRUSTED_ID_1).stream());
    }

    void addTrustedEntity(LivingEntity entity) {
        this.addTrustedEntity(EntityReference.of(entity));
    }

    private void addTrustedEntity(EntityReference<LivingEntity> entityReference) {
        if (this.entityData.get(DATA_TRUSTED_ID_0).isPresent()) {
            this.entityData.set(DATA_TRUSTED_ID_1, Optional.of(entityReference));
        } else {
            this.entityData.set(DATA_TRUSTED_ID_0, Optional.of(entityReference));
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store("Trusted", TRUSTED_LIST_CODEC, this.getTrustedEntities().toList());
        output.putBoolean("Sleeping", this.isSleeping());
        output.store("Type", Fox.Variant.CODEC, this.getVariant());
        output.putBoolean("Sitting", this.isSitting());
        output.putBoolean("Crouching", this.isCrouching());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.clearTrusted();
        input.read("Trusted", TRUSTED_LIST_CODEC).orElse(List.of()).forEach(this::addTrustedEntity);
        this.setSleeping(input.getBooleanOr("Sleeping", false));
        this.setVariant(input.read("Type", Fox.Variant.CODEC).orElse(Fox.Variant.DEFAULT));
        this.setSitting(input.getBooleanOr("Sitting", false), false); // Paper - Add EntityToggleSitEvent
        this.setIsCrouching(input.getBooleanOr("Crouching", false));
        if (this.level() instanceof ServerLevel) {
            this.setTargetGoals();
        }
    }

    private void clearTrusted() {
        this.entityData.set(DATA_TRUSTED_ID_0, Optional.empty());
        this.entityData.set(DATA_TRUSTED_ID_1, Optional.empty());
    }

    public boolean isSitting() {
        return this.getFlag(FLAG_SITTING);
    }

    public void setSitting(boolean sitting) {
        // Paper start - Add EntityToggleSitEvent
        this.setSitting(sitting, true);
    }
    public void setSitting(boolean sitting, boolean fireEvent) {
        if (fireEvent && !new io.papermc.paper.event.entity.EntityToggleSitEvent(this.getBukkitEntity(), sitting).callEvent()) return;
        // Paper end - Add EntityToggleSitEvent
        this.setFlag(FLAG_SITTING, sitting);
    }

    public boolean isFaceplanted() {
        return this.getFlag(FLAG_FACEPLANTED);
    }

    public void setFaceplanted(boolean faceplanted) {
        this.setFlag(FLAG_FACEPLANTED, faceplanted);
    }

    public boolean isDefending() {
        return this.getFlag(FLAG_DEFENDING);
    }

    public void setDefending(boolean defending) {
        this.setFlag(FLAG_DEFENDING, defending);
    }

    @Override
    public boolean isSleeping() {
        return this.getFlag(FLAG_SLEEPING);
    }

    public void setSleeping(boolean sleeping) {
        this.setFlag(FLAG_SLEEPING, sleeping);
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

    @Override
    protected boolean canDispenserEquipIntoSlot(EquipmentSlot slot) {
        return slot == EquipmentSlot.MAINHAND && this.canPickUpLoot();
    }

    @Override
    public boolean canHoldItem(ItemStack stack) {
        ItemStack itemBySlot = this.getItemBySlot(EquipmentSlot.MAINHAND);
        return itemBySlot.isEmpty() || this.ticksSinceEaten > 0 && this.isConsumableFood(stack) && !this.isConsumableFood(itemBySlot);
    }

    private void spitOutItem(ItemStack stack) {
        if (!stack.isEmpty() && !this.level().isClientSide()) {
            ItemEntity itemEntity = new ItemEntity(
                this.level(), this.getX() + this.getLookAngle().x, this.getY() + 1.0, this.getZ() + this.getLookAngle().z, stack
            );
            itemEntity.setPickUpDelay(40);
            itemEntity.setThrower(this);
            this.playSound(SoundEvents.FOX_SPIT, 1.0F, 1.0F);
            this.spawnAtLocation((net.minecraft.server.level.ServerLevel) this.level(), itemEntity); // Paper - Call EntityDropItemEvent
        }
    }

    private void dropItemStack(ItemStack stack) {
        ItemEntity itemEntity = new ItemEntity(this.level(), this.getX(), this.getY(), this.getZ(), stack);
        this.spawnAtLocation((net.minecraft.server.level.ServerLevel) this.level(), itemEntity); // Paper - Call EntityDropItemEvent
    }

    @Override
    protected void pickUpItem(ServerLevel level, ItemEntity entity) {
        ItemStack item = entity.getItem();
        if (!org.bukkit.craftbukkit.event.CraftEventFactory.callEntityPickupItemEvent(this, entity, item.getCount() - 1, !this.canHoldItem(item)).isCancelled()) { // CraftBukkit - call EntityPickupItemEvent
            item = entity.getItem(); // CraftBukkit - update item after event
            int count = item.getCount();
            if (count > 1) {
                this.dropItemStack(item.split(count - 1));
            }

            this.spitOutItem(this.getItemBySlot(EquipmentSlot.MAINHAND));
            this.onItemPickup(entity);
            this.setItemSlot(EquipmentSlot.MAINHAND, item.split(1));
            this.setGuaranteedDrop(EquipmentSlot.MAINHAND);
            this.take(entity, item.getCount());
            entity.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.PICKUP); // CraftBukkit - add Bukkit remove cause
            this.ticksSinceEaten = 0;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.isEffectiveAi()) {
            boolean isInWater = this.isInWater();
            if (isInWater || this.getTarget() != null || this.level().isThundering()) {
                this.wakeUp();
            }

            if (isInWater || this.isSleeping()) {
                this.setSitting(false);
            }

            if (this.isFaceplanted() && this.level().random.nextFloat() < 0.2F) {
                BlockPos blockPos = this.blockPosition();
                BlockState blockState = this.level().getBlockState(blockPos);
                this.level().levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, blockPos, Block.getId(blockState));
            }
        }

        this.interestedAngleO = this.interestedAngle;
        if (this.isInterested()) {
            this.interestedAngle = this.interestedAngle + (1.0F - this.interestedAngle) * 0.4F;
        } else {
            this.interestedAngle = this.interestedAngle + (0.0F - this.interestedAngle) * 0.4F;
        }

        this.crouchAmountO = this.crouchAmount;
        if (this.isCrouching()) {
            this.crouchAmount += 0.2F;
            if (this.crouchAmount > 3.0F) {
                this.crouchAmount = 3.0F;
            }
        } else {
            this.crouchAmount = 0.0F;
        }
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(ItemTags.FOX_FOOD);
    }

    @Override
    protected void onOffspringSpawnedFromEgg(Player player, Mob child) {
        ((Fox)child).addTrustedEntity(player);
    }

    public boolean isPouncing() {
        return this.getFlag(FLAG_POUNCING);
    }

    public void setIsPouncing(boolean isPouncing) {
        this.setFlag(FLAG_POUNCING, isPouncing);
    }

    public boolean isFullyCrouched() {
        return this.crouchAmount == 3.0F;
    }

    public void setIsCrouching(boolean isCrouching) {
        this.setFlag(FLAG_CROUCHING, isCrouching);
    }

    @Override
    public boolean isCrouching() {
        return this.getFlag(FLAG_CROUCHING);
    }

    public void setIsInterested(boolean isInterested) {
        this.setFlag(FLAG_INTERESTED, isInterested);
    }

    public boolean isInterested() {
        return this.getFlag(FLAG_INTERESTED);
    }

    public float getHeadRollAngle(float partialTick) {
        return Mth.lerp(partialTick, this.interestedAngleO, this.interestedAngle) * 0.11F * (float) Math.PI;
    }

    public float getCrouchAmount(float partialTick) {
        return Mth.lerp(partialTick, this.crouchAmountO, this.crouchAmount);
    }

    @Override
    public boolean setTarget(@Nullable LivingEntity target, org.bukkit.event.entity.EntityTargetEvent.@Nullable TargetReason reason) { // CraftBukkit
        if (this.isDefending() && target == null) {
            this.setDefending(false);
        }

        return super.setTarget(target, reason); // CraftBukkit
    }

    void wakeUp() {
        this.setSleeping(false);
    }

    void clearStates() {
        this.setIsInterested(false);
        this.setIsCrouching(false);
        this.setSitting(false);
        this.setSleeping(false);
        this.setDefending(false);
        this.setFaceplanted(false);
    }

    boolean canMove() {
        return !this.isSleeping() && !this.isSitting() && !this.isFaceplanted();
    }

    @Override
    public void playAmbientSound() {
        SoundEvent ambientSound = this.getAmbientSound();
        if (ambientSound == SoundEvents.FOX_SCREECH) {
            this.playSound(ambientSound, 2.0F, this.getVoicePitch());
        } else {
            super.playAmbientSound();
        }
    }

    @Override
    public @Nullable SoundEvent getAmbientSound() {
        if (this.isSleeping()) {
            return SoundEvents.FOX_SLEEP;
        } else {
            if (!this.level().isBrightOutside() && this.random.nextFloat() < 0.1F) {
                List<Player> entitiesOfClass = this.level()
                    .getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(16.0, 16.0, 16.0), EntitySelector.NO_SPECTATORS);
                if (entitiesOfClass.isEmpty()) {
                    return SoundEvents.FOX_SCREECH;
                }
            }

            return SoundEvents.FOX_AMBIENT;
        }
    }

    @Override
    public @Nullable SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.FOX_HURT;
    }

    @Override
    public @Nullable SoundEvent getDeathSound() {
        return SoundEvents.FOX_DEATH;
    }

    boolean trusts(LivingEntity entity) {
        return this.getTrustedEntities().anyMatch(entityReference -> entityReference.matches(entity));
    }

    // Paper start - handle the bitten item separately like vanilla
    @Override
    protected boolean shouldSkipLoot(EquipmentSlot slot) {
        return slot == EquipmentSlot.MAINHAND;
    }
    // Paper end

    @Override
    // Paper start - Cancellable death event
    protected org.bukkit.event.entity.EntityDeathEvent dropAllDeathLoot(ServerLevel level, DamageSource damageSource) {
        ItemStack itemBySlot = this.getItemBySlot(EquipmentSlot.MAINHAND);
        boolean releaseMouth = false;
        if (!itemBySlot.isEmpty() && level.getGameRules().get(GameRules.MOB_DROPS)) { // Fix MC-153010
            this.spawnAtLocation(level, itemBySlot);
            releaseMouth = true;
        }

        org.bukkit.event.entity.EntityDeathEvent deathEvent = super.dropAllDeathLoot(level, damageSource);
        // Below is code to drop
        if (deathEvent == null || deathEvent.isCancelled()) return deathEvent;

        if (releaseMouth) {
            // Paper end
            this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }

        return deathEvent; // Paper - Cancellable death event
    }

    public static boolean isPathClear(Fox fox, LivingEntity livingEntity) {
        double d = livingEntity.getZ() - fox.getZ();
        double d1 = livingEntity.getX() - fox.getX();
        double d2 = d / d1;
        int i = 6;

        for (int i1 = 0; i1 < 6; i1++) {
            double d3 = d2 == 0.0 ? 0.0 : d * (i1 / 6.0F);
            double d4 = d2 == 0.0 ? d1 * (i1 / 6.0F) : d3 / d2;

            for (int i2 = 1; i2 < 4; i2++) {
                if (!fox.level().getBlockState(BlockPos.containing(fox.getX() + d4, fox.getY() + i2, fox.getZ() + d3)).canBeReplaced()) {
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    public Vec3 getLeashOffset() {
        return new Vec3(0.0, 0.55F * this.getEyeHeight(), this.getBbWidth() * 0.4F);
    }

    class DefendTrustedTargetGoal extends NearestAttackableTargetGoal<LivingEntity> {
        private @Nullable LivingEntity trustedLastHurtBy;
        private @Nullable LivingEntity trustedLastHurt;
        private int timestamp;

        public DefendTrustedTargetGoal(
            final Class<LivingEntity> targetType, final boolean mustSee, final @Nullable boolean mustReach, final TargetingConditions.Selector selector
        ) {
            super(Fox.this, targetType, 10, mustSee, mustReach, selector);
        }

        @Override
        public boolean canUse() {
            if (this.randomInterval > 0 && this.mob.getRandom().nextInt(this.randomInterval) != 0) {
                return false;
            } else {
                ServerLevel serverLevel = getServerLevel(Fox.this.level());

                for (EntityReference<LivingEntity> entityReference : Fox.this.getTrustedEntities().toList()) {
                    LivingEntity livingEntity = entityReference.getEntity(serverLevel, LivingEntity.class);
                    if (livingEntity != null) {
                        this.trustedLastHurt = livingEntity;
                        this.trustedLastHurtBy = livingEntity.getLastHurtByMob();
                        int lastHurtByMobTimestamp = livingEntity.getLastHurtByMobTimestamp();
                        return lastHurtByMobTimestamp != this.timestamp && this.canAttack(this.trustedLastHurtBy, this.targetConditions);
                    }
                }

                return false;
            }
        }

        @Override
        public void start() {
            this.setTarget(this.trustedLastHurtBy);
            this.target = this.trustedLastHurtBy;
            if (this.trustedLastHurt != null) {
                this.timestamp = this.trustedLastHurt.getLastHurtByMobTimestamp();
            }

            Fox.this.playSound(SoundEvents.FOX_AGGRO, 1.0F, 1.0F);
            Fox.this.setDefending(true);
            Fox.this.wakeUp();
            super.start();
        }
    }

    class FaceplantGoal extends Goal {
        int countdown;

        public FaceplantGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.LOOK, Goal.Flag.JUMP, Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return Fox.this.isFaceplanted();
        }

        @Override
        public boolean canContinueToUse() {
            return this.canUse() && this.countdown > 0;
        }

        @Override
        public void start() {
            this.countdown = this.adjustedTickDelay(40);
        }

        @Override
        public void stop() {
            Fox.this.setFaceplanted(false);
        }

        @Override
        public void tick() {
            this.countdown--;
        }
    }

    public class FoxAlertableEntitiesSelector implements TargetingConditions.Selector {
        @Override
        public boolean test(LivingEntity entity, ServerLevel level) {
            if (entity instanceof Fox) {
                return false;
            } else if (entity instanceof Chicken || entity instanceof Rabbit || entity instanceof Monster) {
                return true;
            } else {
                return entity instanceof TamableAnimal
                    ? !((TamableAnimal)entity).isTame()
                    : !(entity instanceof Player player && (player.isSpectator() || player.isCreative()))
                        && !Fox.this.trusts(entity)
                        && !entity.isSleeping()
                        && !entity.isDiscrete();
            }
        }
    }

    abstract class FoxBehaviorGoal extends Goal {
        private final TargetingConditions alertableTargeting = TargetingConditions.forCombat()
            .range(12.0)
            .ignoreLineOfSight()
            .selector(Fox.this.new FoxAlertableEntitiesSelector());

        protected boolean hasShelter() {
            BlockPos blockPos = BlockPos.containing(Fox.this.getX(), Fox.this.getBoundingBox().maxY, Fox.this.getZ());
            return !Fox.this.level().canSeeSky(blockPos) && Fox.this.getWalkTargetValue(blockPos) >= 0.0F;
        }

        protected boolean alertable() {
            return !getServerLevel(Fox.this.level())
                .getNearbyEntities(LivingEntity.class, this.alertableTargeting, Fox.this, Fox.this.getBoundingBox().inflate(12.0, 6.0, 12.0))
                .isEmpty();
        }
    }

    class FoxBreedGoal extends BreedGoal {
        public FoxBreedGoal(final double speedModifier) {
            super(Fox.this, speedModifier);
        }

        @Override
        public void start() {
            ((Fox)this.animal).clearStates();
            ((Fox)this.partner).clearStates();
            super.start();
        }

        @Override
        protected void breed() {
            Fox fox = (Fox)this.animal.getBreedOffspring(this.level, this.partner);
            if (fox != null) {
                ServerPlayer loveCause = this.animal.getLoveCause();
                ServerPlayer loveCause1 = this.partner.getLoveCause();
                ServerPlayer serverPlayer = loveCause;
                if (loveCause != null) {
                    fox.addTrustedEntity(loveCause);
                } else {
                    serverPlayer = loveCause1;
                }

                if (loveCause1 != null && loveCause != loveCause1) {
                    fox.addTrustedEntity(loveCause1);
                }

                // CraftBukkit start - call EntityBreedEvent
                fox.setAge(-24000);
                fox.snapTo(this.animal.getX(), this.animal.getY(), this.animal.getZ(), 0.0F, 0.0F);
                int experience = this.animal.getRandom().nextInt(7) + 1;
                org.bukkit.event.entity.EntityBreedEvent entityBreedEvent = org.bukkit.craftbukkit.event.CraftEventFactory.callEntityBreedEvent(fox, this.animal, this.partner, loveCause, this.animal.breedItem, experience);
                if (entityBreedEvent.isCancelled()) {
                    this.animal.resetLove();
                    this.partner.resetLove();
                    return;
                }
                experience = entityBreedEvent.getExperience();
                // CraftBukkit end - call EntityBreedEvent

                if (serverPlayer != null) {
                    serverPlayer.awardStat(Stats.ANIMALS_BRED);
                    CriteriaTriggers.BRED_ANIMALS.trigger(serverPlayer, this.animal, this.partner, fox);
                }

                this.animal.setAge(6000);
                this.partner.setAge(6000);
                this.animal.resetLove();
                this.partner.resetLove();
                this.level.addFreshEntityWithPassengers(fox, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.BREEDING); // CraftBukkit - added SpawnReason
                this.level.broadcastEntityEvent(this.animal, EntityEvent.IN_LOVE_HEARTS);
                if (experience > 0 && this.level.getGameRules().get(GameRules.MOB_DROPS)) { // Paper - call EntityBreedEvent
                    this.level
                        .addFreshEntity(
                            new ExperienceOrb(this.level, this.animal.position(), net.minecraft.world.phys.Vec3.ZERO, experience, org.bukkit.entity.ExperienceOrb.SpawnReason.BREED, loveCause, fox) // Paper - call EntityBreedEvent, add spawn context
                        );
                }
            }
        }
    }

    public class FoxEatBerriesGoal extends MoveToBlockGoal {
        private static final int WAIT_TICKS = 40;
        protected int ticksWaited;

        public FoxEatBerriesGoal(final double speedModifier, final int searchRange, final int verticalSearchRange) {
            super(Fox.this, speedModifier, searchRange, verticalSearchRange);
        }

        @Override
        public double acceptedDistance() {
            return 2.0;
        }

        @Override
        public boolean shouldRecalculatePath() {
            return this.tryTicks % 100 == 0;
        }

        @Override
        protected boolean isValidTarget(LevelReader level, BlockPos pos) {
            BlockState blockState = level.getBlockState(pos);
            return blockState.is(Blocks.SWEET_BERRY_BUSH) && blockState.getValue(SweetBerryBushBlock.AGE) >= 2 || CaveVines.hasGlowBerries(blockState);
        }

        @Override
        public void tick() {
            if (this.isReachedTarget()) {
                if (this.ticksWaited >= 40) {
                    this.onReachedTarget();
                } else {
                    this.ticksWaited++;
                }
            } else if (!this.isReachedTarget() && Fox.this.random.nextFloat() < 0.05F) {
                Fox.this.playSound(SoundEvents.FOX_SNIFF, 1.0F, 1.0F);
            }

            super.tick();
        }

        protected void onReachedTarget() {
            if (getServerLevel(Fox.this.level()).getGameRules().get(GameRules.MOB_GRIEFING)) {
                BlockState blockState = Fox.this.level().getBlockState(this.blockPos);
                if (blockState.is(Blocks.SWEET_BERRY_BUSH)) {
                    this.pickSweetBerries(blockState);
                } else if (CaveVines.hasGlowBerries(blockState)) {
                    this.pickGlowBerry(blockState);
                }
            }
        }

        private void pickGlowBerry(BlockState state) {
            CaveVines.use(Fox.this, state, Fox.this.level(), this.blockPos);
        }

        private void pickSweetBerries(BlockState state) {
            int ageValue = state.getValue(SweetBerryBushBlock.AGE);
            state.setValue(SweetBerryBushBlock.AGE, 1);
            if (!org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(Fox.this, this.blockPos, state.setValue(SweetBerryBushBlock.AGE, 1))) return; // CraftBukkit - call EntityChangeBlockEvent
            int i = 1 + Fox.this.level().random.nextInt(2) + (ageValue == 3 ? 1 : 0);
            ItemStack itemBySlot = Fox.this.getItemBySlot(EquipmentSlot.MAINHAND);
            if (itemBySlot.isEmpty()) {
                Fox.this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.SWEET_BERRIES));
                i--;
            }

            if (i > 0) {
                Block.popResource(Fox.this.level(), this.blockPos, new ItemStack(Items.SWEET_BERRIES, i));
            }

            Fox.this.playSound(SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, 1.0F, 1.0F);
            Fox.this.level().setBlock(this.blockPos, state.setValue(SweetBerryBushBlock.AGE, 1), Block.UPDATE_CLIENTS);
            Fox.this.level().gameEvent(GameEvent.BLOCK_CHANGE, this.blockPos, GameEvent.Context.of(Fox.this));
        }

        @Override
        public boolean canUse() {
            return !Fox.this.isSleeping() && super.canUse();
        }

        @Override
        public void start() {
            this.ticksWaited = 0;
            Fox.this.setSitting(false);
            super.start();
        }
    }

    class FoxFloatGoal extends FloatGoal {
        public FoxFloatGoal() {
            super(Fox.this);
        }

        @Override
        public void start() {
            super.start();
            Fox.this.clearStates();
        }

        @Override
        public boolean canUse() {
            return Fox.this.isInWater() && Fox.this.getFluidHeight(FluidTags.WATER) > 0.25 || Fox.this.isInLava();
        }
    }

    static class FoxFollowParentGoal extends FollowParentGoal {
        private final Fox fox;

        public FoxFollowParentGoal(Fox fox, double speedModifier) {
            super(fox, speedModifier);
            this.fox = fox;
        }

        @Override
        public boolean canUse() {
            return !this.fox.isDefending() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return !this.fox.isDefending() && super.canContinueToUse();
        }

        @Override
        public void start() {
            this.fox.clearStates();
            super.start();
        }
    }

    public static class FoxGroupData extends AgeableMob.AgeableMobGroupData {
        public final Fox.Variant variant;

        public FoxGroupData(Fox.Variant variant) {
            super(false);
            this.variant = variant;
        }
    }

    class FoxLookAtPlayerGoal extends LookAtPlayerGoal {
        public FoxLookAtPlayerGoal(final Mob mob, final Class<? extends LivingEntity> lookAtType, final float lookDistance) {
            super(mob, lookAtType, lookDistance);
        }

        @Override
        public boolean canUse() {
            return super.canUse() && !Fox.this.isFaceplanted() && !Fox.this.isInterested();
        }

        @Override
        public boolean canContinueToUse() {
            return super.canContinueToUse() && !Fox.this.isFaceplanted() && !Fox.this.isInterested();
        }
    }

    public class FoxLookControl extends LookControl {
        public FoxLookControl() {
            super(Fox.this);
        }

        @Override
        public void tick() {
            if (!Fox.this.isSleeping()) {
                super.tick();
            }
        }

        @Override
        protected boolean resetXRotOnTick() {
            return !Fox.this.isPouncing() && !Fox.this.isCrouching() && !Fox.this.isInterested() && !Fox.this.isFaceplanted();
        }
    }

    class FoxMeleeAttackGoal extends MeleeAttackGoal {
        public FoxMeleeAttackGoal(final double speedModifier, final boolean followingTargetEvenIfNotSeen) {
            super(Fox.this, speedModifier, followingTargetEvenIfNotSeen);
        }

        @Override
        protected void checkAndPerformAttack(LivingEntity target) {
            if (this.canPerformAttack(target)) {
                this.resetAttackCooldown();
                this.mob.doHurtTarget(getServerLevel(this.mob), target);
                Fox.this.playSound(SoundEvents.FOX_BITE, 1.0F, 1.0F);
            }
        }

        @Override
        public void start() {
            Fox.this.setIsInterested(false);
            super.start();
        }

        @Override
        public boolean canUse() {
            return !Fox.this.isSitting() && !Fox.this.isSleeping() && !Fox.this.isCrouching() && !Fox.this.isFaceplanted() && super.canUse();
        }
    }

    class FoxMoveControl extends MoveControl {
        public FoxMoveControl() {
            super(Fox.this);
        }

        @Override
        public void tick() {
            if (Fox.this.canMove()) {
                super.tick();
            }
        }
    }

    class FoxPanicGoal extends PanicGoal {
        public FoxPanicGoal(final double speedModifier) {
            super(Fox.this, speedModifier);
        }

        @Override
        public boolean shouldPanic() {
            return !Fox.this.isDefending() && super.shouldPanic();
        }
    }

    public class FoxPounceGoal extends JumpGoal {
        @Override
        public boolean canUse() {
            if (!Fox.this.isFullyCrouched()) {
                return false;
            } else {
                LivingEntity target = Fox.this.getTarget();
                if (target != null && target.isAlive()) {
                    if (target.getMotionDirection() != target.getDirection()) {
                        return false;
                    } else {
                        boolean isPathClear = Fox.isPathClear(Fox.this, target);
                        if (!isPathClear) {
                            Fox.this.getNavigation().createPath(target, 0);
                            Fox.this.setIsCrouching(false);
                            Fox.this.setIsInterested(false);
                        }

                        return isPathClear;
                    }
                } else {
                    return false;
                }
            }
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity target = Fox.this.getTarget();
            if (target != null && target.isAlive()) {
                double d = Fox.this.getDeltaMovement().y;
                return (!(d * d < 0.05F) || !(Math.abs(Fox.this.getXRot()) < 15.0F) || !Fox.this.onGround()) && !Fox.this.isFaceplanted();
            } else {
                return false;
            }
        }

        @Override
        public boolean isInterruptable() {
            return false;
        }

        @Override
        public void start() {
            Fox.this.setJumping(true);
            Fox.this.setIsPouncing(true);
            Fox.this.setIsInterested(false);
            LivingEntity target = Fox.this.getTarget();
            if (target != null) {
                Fox.this.getLookControl().setLookAt(target, 60.0F, 30.0F);
                Vec3 vec3 = new Vec3(target.getX() - Fox.this.getX(), target.getY() - Fox.this.getY(), target.getZ() - Fox.this.getZ()).normalize();
                Fox.this.setDeltaMovement(Fox.this.getDeltaMovement().add(vec3.x * 0.8, 0.9, vec3.z * 0.8));
            }

            Fox.this.getNavigation().stop();
        }

        @Override
        public void stop() {
            Fox.this.setIsCrouching(false);
            Fox.this.crouchAmount = 0.0F;
            Fox.this.crouchAmountO = 0.0F;
            Fox.this.setIsInterested(false);
            Fox.this.setIsPouncing(false);
        }

        @Override
        public void tick() {
            LivingEntity target = Fox.this.getTarget();
            if (target != null) {
                Fox.this.getLookControl().setLookAt(target, 60.0F, 30.0F);
            }

            if (!Fox.this.isFaceplanted()) {
                Vec3 deltaMovement = Fox.this.getDeltaMovement();
                if (deltaMovement.y * deltaMovement.y < 0.03F && Fox.this.getXRot() != 0.0F) {
                    Fox.this.setXRot(Mth.rotLerp(0.2F, Fox.this.getXRot(), 0.0F));
                } else {
                    double d = deltaMovement.horizontalDistance();
                    double d1 = Math.signum(-deltaMovement.y) * Math.acos(d / deltaMovement.length()) * 180.0F / (float)Math.PI;
                    Fox.this.setXRot((float)d1);
                }
            }

            if (target != null && Fox.this.distanceTo(target) <= 2.0F) {
                Fox.this.doHurtTarget(getServerLevel(Fox.this.level()), target);
            } else if (Fox.this.getXRot() > 0.0F
                && Fox.this.onGround()
                && (float)Fox.this.getDeltaMovement().y != 0.0F
                && Fox.this.level().getBlockState(Fox.this.blockPosition()).is(Blocks.SNOW)) {
                Fox.this.setXRot(60.0F);
                Fox.this.setTarget(null);
                Fox.this.setFaceplanted(true);
            }
        }
    }

    class FoxSearchForItemsGoal extends Goal {
        public FoxSearchForItemsGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!Fox.this.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()) {
                return false;
            } else if (Fox.this.getTarget() != null || Fox.this.getLastHurtByMob() != null) {
                return false;
            } else if (!Fox.this.canMove()) {
                return false;
            } else if (Fox.this.getRandom().nextInt(reducedTickDelay(10)) != 0) {
                return false;
            } else {
                List<ItemEntity> entitiesOfClass = Fox.this.level()
                    .getEntitiesOfClass(ItemEntity.class, Fox.this.getBoundingBox().inflate(8.0, 8.0, 8.0), Fox.ALLOWED_ITEMS);
                return !entitiesOfClass.isEmpty() && Fox.this.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty();
            }
        }

        @Override
        public void tick() {
            List<ItemEntity> entitiesOfClass = Fox.this.level()
                .getEntitiesOfClass(ItemEntity.class, Fox.this.getBoundingBox().inflate(8.0, 8.0, 8.0), Fox.ALLOWED_ITEMS);
            ItemStack itemBySlot = Fox.this.getItemBySlot(EquipmentSlot.MAINHAND);
            if (itemBySlot.isEmpty() && !entitiesOfClass.isEmpty()) {
                Fox.this.getNavigation().moveTo(entitiesOfClass.get(0), 1.2F);
            }
        }

        @Override
        public void start() {
            List<ItemEntity> entitiesOfClass = Fox.this.level()
                .getEntitiesOfClass(ItemEntity.class, Fox.this.getBoundingBox().inflate(8.0, 8.0, 8.0), Fox.ALLOWED_ITEMS);
            if (!entitiesOfClass.isEmpty()) {
                Fox.this.getNavigation().moveTo(entitiesOfClass.get(0), 1.2F);
            }
        }
    }

    class FoxStrollThroughVillageGoal extends StrollThroughVillageGoal {
        public FoxStrollThroughVillageGoal(final int i, final int interval) {
            super(Fox.this, interval);
        }

        @Override
        public void start() {
            Fox.this.clearStates();
            super.start();
        }

        @Override
        public boolean canUse() {
            return super.canUse() && this.canFoxMove();
        }

        @Override
        public boolean canContinueToUse() {
            return super.canContinueToUse() && this.canFoxMove();
        }

        private boolean canFoxMove() {
            return !Fox.this.isSleeping() && !Fox.this.isSitting() && !Fox.this.isDefending() && Fox.this.getTarget() == null;
        }
    }

    class PerchAndSearchGoal extends Fox.FoxBehaviorGoal {
        private double relX;
        private double relZ;
        private int lookTime;
        private int looksRemaining;

        public PerchAndSearchGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return Fox.this.getLastHurtByMob() == null
                && Fox.this.getRandom().nextFloat() < 0.02F
                && !Fox.this.isSleeping()
                && Fox.this.getTarget() == null
                && Fox.this.getNavigation().isDone()
                && !this.alertable()
                && !Fox.this.isPouncing()
                && !Fox.this.isCrouching();
        }

        @Override
        public boolean canContinueToUse() {
            return this.looksRemaining > 0;
        }

        @Override
        public void start() {
            this.resetLook();
            this.looksRemaining = 2 + Fox.this.getRandom().nextInt(3);
            Fox.this.setSitting(true);
            Fox.this.getNavigation().stop();
        }

        @Override
        public void stop() {
            Fox.this.setSitting(false);
        }

        @Override
        public void tick() {
            this.lookTime--;
            if (this.lookTime <= 0) {
                this.looksRemaining--;
                this.resetLook();
            }

            Fox.this.getLookControl()
                .setLookAt(Fox.this.getX() + this.relX, Fox.this.getEyeY(), Fox.this.getZ() + this.relZ, Fox.this.getMaxHeadYRot(), Fox.this.getMaxHeadXRot());
        }

        private void resetLook() {
            double d = (Math.PI * 2) * Fox.this.getRandom().nextDouble();
            this.relX = Math.cos(d);
            this.relZ = Math.sin(d);
            this.lookTime = this.adjustedTickDelay(80 + Fox.this.getRandom().nextInt(20));
        }
    }

    class SeekShelterGoal extends FleeSunGoal {
        private int interval = reducedTickDelay(100);

        public SeekShelterGoal(final double speedModifier) {
            super(Fox.this, speedModifier);
        }

        @Override
        public boolean canUse() {
            if (!Fox.this.isSleeping() && this.mob.getTarget() == null) {
                if (Fox.this.level().isThundering() && Fox.this.level().canSeeSky(this.mob.blockPosition())) {
                    return this.setWantedPos();
                } else if (this.interval > 0) {
                    this.interval--;
                    return false;
                } else {
                    this.interval = 100;
                    BlockPos blockPos = this.mob.blockPosition();
                    return Fox.this.level().isBrightOutside()
                        && Fox.this.level().canSeeSky(blockPos)
                        && !((ServerLevel)Fox.this.level()).isVillage(blockPos)
                        && this.setWantedPos();
                }
            } else {
                return false;
            }
        }

        @Override
        public void start() {
            Fox.this.clearStates();
            super.start();
        }
    }

    class SleepGoal extends Fox.FoxBehaviorGoal {
        private static final int WAIT_TIME_BEFORE_SLEEP = reducedTickDelay(140);
        private int countdown = Fox.this.random.nextInt(WAIT_TIME_BEFORE_SLEEP);

        public SleepGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return Fox.this.xxa == 0.0F && Fox.this.yya == 0.0F && Fox.this.zza == 0.0F && (this.canSleep() || Fox.this.isSleeping());
        }

        @Override
        public boolean canContinueToUse() {
            return this.canSleep();
        }

        private boolean canSleep() {
            if (this.countdown > 0) {
                this.countdown--;
                return false;
            } else {
                return Fox.this.level().isBrightOutside() && this.hasShelter() && !this.alertable() && !Fox.this.isInPowderSnow;
            }
        }

        @Override
        public void stop() {
            this.countdown = Fox.this.random.nextInt(WAIT_TIME_BEFORE_SLEEP);
            Fox.this.clearStates();
        }

        @Override
        public void start() {
            Fox.this.setSitting(false);
            Fox.this.setIsCrouching(false);
            Fox.this.setIsInterested(false);
            Fox.this.setJumping(false);
            Fox.this.setSleeping(true);
            Fox.this.getNavigation().stop();
            Fox.this.getMoveControl().setWantedPosition(Fox.this.getX(), Fox.this.getY(), Fox.this.getZ(), 0.0);
        }
    }

    class StalkPreyGoal extends Goal {
        public StalkPreyGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (Fox.this.isSleeping()) {
                return false;
            } else {
                LivingEntity target = Fox.this.getTarget();
                return target != null
                    && target.isAlive()
                    && Fox.STALKABLE_PREY.test(target)
                    && Fox.this.distanceToSqr(target) > 36.0
                    && !Fox.this.isCrouching()
                    && !Fox.this.isInterested()
                    && !Fox.this.jumping;
            }
        }

        @Override
        public void start() {
            Fox.this.setSitting(false);
            Fox.this.setFaceplanted(false);
        }

        @Override
        public void stop() {
            LivingEntity target = Fox.this.getTarget();
            if (target != null && Fox.isPathClear(Fox.this, target)) {
                Fox.this.setIsInterested(true);
                Fox.this.setIsCrouching(true);
                Fox.this.getNavigation().stop();
                Fox.this.getLookControl().setLookAt(target, Fox.this.getMaxHeadYRot(), Fox.this.getMaxHeadXRot());
            } else {
                Fox.this.setIsInterested(false);
                Fox.this.setIsCrouching(false);
            }
        }

        @Override
        public void tick() {
            LivingEntity target = Fox.this.getTarget();
            if (target != null) {
                Fox.this.getLookControl().setLookAt(target, Fox.this.getMaxHeadYRot(), Fox.this.getMaxHeadXRot());
                if (Fox.this.distanceToSqr(target) <= 36.0) {
                    Fox.this.setIsInterested(true);
                    Fox.this.setIsCrouching(true);
                    Fox.this.getNavigation().stop();
                } else {
                    Fox.this.getNavigation().moveTo(target, 1.5);
                }
            }
        }
    }

    public static enum Variant implements StringRepresentable {
        RED(0, "red"),
        SNOW(1, "snow");

        public static final Fox.Variant DEFAULT = RED;
        public static final StringRepresentable.EnumCodec<Fox.Variant> CODEC = StringRepresentable.fromEnum(Fox.Variant::values);
        private static final IntFunction<Fox.Variant> BY_ID = ByIdMap.continuous(Fox.Variant::getId, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
        public static final StreamCodec<ByteBuf, Fox.Variant> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, Fox.Variant::getId);
        private final int id;
        private final String name;

        private Variant(final int id, final String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        public int getId() {
            return this.id;
        }

        public static Fox.Variant byId(int id) {
            return BY_ID.apply(id);
        }

        public static Fox.Variant byBiome(Holder<Biome> biome) {
            return biome.is(BiomeTags.SPAWNS_SNOW_FOXES) ? SNOW : RED;
        }
    }
}
