package net.minecraft.world.entity.animal.equine;

import java.util.function.DoubleSupplier;
import java.util.function.IntUnaryOperator;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HasCustomInventoryScreen;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStandGoal;
import net.minecraft.world.entity.ai.goal.RunAroundLikeCrazyGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.inventory.AbstractMountInventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public abstract class AbstractHorse extends Animal implements HasCustomInventoryScreen, OwnableEntity, PlayerRideableJumping {
    public static final int CHEST_SLOT_OFFSET = 499;
    public static final int INVENTORY_SLOT_OFFSET = 500;
    public static final double BREEDING_CROSS_FACTOR = 0.15;
    private static final float MIN_MOVEMENT_SPEED = (float)generateSpeed(() -> 0.0);
    private static final float MAX_MOVEMENT_SPEED = (float)generateSpeed(() -> 1.0);
    private static final float MIN_JUMP_STRENGTH = (float)generateJumpStrength(() -> 0.0);
    private static final float MAX_JUMP_STRENGTH = (float)generateJumpStrength(() -> 1.0);
    private static final float MIN_HEALTH = generateMaxHealth(i -> 0);
    private static final float MAX_HEALTH = generateMaxHealth(i -> i - 1);
    private static final float BACKWARDS_MOVE_SPEED_FACTOR = 0.25F;
    private static final float SIDEWAYS_MOVE_SPEED_FACTOR = 0.5F;
    private static final TargetingConditions.Selector PARENT_HORSE_SELECTOR = (entity, level) -> entity instanceof AbstractHorse abstractHorse
        && abstractHorse.isBred();
    private static final TargetingConditions MOMMY_TARGETING = TargetingConditions.forNonCombat()
        .range(16.0)
        .ignoreLineOfSight()
        .selector(PARENT_HORSE_SELECTOR);
    private static final EntityDataAccessor<Byte> DATA_ID_FLAGS = SynchedEntityData.defineId(AbstractHorse.class, EntityDataSerializers.BYTE);
    private static final int FLAG_TAME = 2;
    private static final int FLAG_BRED = 8;
    private static final int FLAG_EATING = 16;
    private static final int FLAG_STANDING = 32;
    private static final int FLAG_OPEN_MOUTH = 64;
    public static final int INVENTORY_ROWS = 3;
    private static final int DEFAULT_TEMPER = 0;
    private static final boolean DEFAULT_EATING_HAYSTACK = false;
    private static final boolean DEFAULT_BRED = false;
    private static final boolean DEFAULT_TAME = false;
    private int eatingCounter;
    private int mouthCounter;
    private int standCounter;
    public int tailCounter;
    public int sprintCounter;
    public SimpleContainer inventory;
    protected int temper = 0;
    protected float playerJumpPendingScale;
    protected boolean allowStandSliding;
    private float eatAnim;
    private float eatAnimO;
    private float standAnim;
    private float standAnimO;
    private float mouthAnim;
    private float mouthAnimO;
    protected boolean canGallop = true;
    protected int gallopSoundCounter;
    public @Nullable EntityReference<LivingEntity> owner;
    public int maxDomestication = 100; // CraftBukkit - store max domestication value

    protected AbstractHorse(EntityType<? extends AbstractHorse> type, Level level) {
        super(type, level);
        this.createInventory();
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new AbstractHorse.MountPanicGoal(1.2));
        this.goalSelector.addGoal(1, new RunAroundLikeCrazyGoal(this, 1.2));
        this.goalSelector.addGoal(2, new BreedGoal(this, 1.0, AbstractHorse.class));
        this.goalSelector.addGoal(4, new FollowParentGoal(this, 1.0));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        if (this.canPerformRearing()) {
            this.goalSelector.addGoal(9, new RandomStandGoal(this));
        }

        this.addBehaviourGoals();
    }

    protected void addBehaviourGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(3, new TemptGoal(this, 1.25, stack -> stack.is(ItemTags.HORSE_TEMPT_ITEMS), false));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ID_FLAGS, (byte)0);
    }

    protected boolean getFlag(int flagId) {
        return (this.entityData.get(DATA_ID_FLAGS) & flagId) != 0;
    }

    protected void setFlag(int flagId, boolean value) {
        byte b = this.entityData.get(DATA_ID_FLAGS);
        if (value) {
            this.entityData.set(DATA_ID_FLAGS, (byte)(b | flagId));
        } else {
            this.entityData.set(DATA_ID_FLAGS, (byte)(b & ~flagId));
        }
    }

    public boolean isTamed() {
        return this.getFlag(FLAG_TAME);
    }

    @Override
    public @Nullable EntityReference<LivingEntity> getOwnerReference() {
        return this.owner;
    }

    public void setOwner(@Nullable LivingEntity owner) {
        this.owner = EntityReference.of(owner);
    }

    public void setTamed(boolean tamed) {
        this.setFlag(FLAG_TAME, tamed);
    }

    @Override
    public void onElasticLeashPull() {
        super.onElasticLeashPull();
        if (this.isEating()) {
            this.setEating(false);
        }
    }

    @Override
    public boolean supportQuadLeash() {
        return true;
    }

    @Override
    public Vec3[] getQuadLeashOffsets() {
        return Leashable.createQuadLeashOffsets(this, 0.04, 0.52, 0.23, 0.87);
    }

    public boolean isEating() {
        return this.getFlag(FLAG_EATING);
    }

    public boolean isStanding() {
        return this.getFlag(FLAG_STANDING);
    }

    public boolean isBred() {
        return this.getFlag(FLAG_BRED);
    }

    public void setBred(boolean breeding) {
        this.setFlag(FLAG_BRED, breeding);
    }

    @Override
    public boolean canUseSlot(EquipmentSlot slot) {
        return slot != EquipmentSlot.SADDLE ? super.canUseSlot(slot) : this.isAlive() && !this.isBaby() && this.isTamed();
    }

    public void equipBodyArmor(Player player, ItemStack stack) {
        if (this.isEquippableInSlot(stack, EquipmentSlot.BODY)) {
            this.setBodyArmorItem(stack.consumeAndReturn(1, player));
        }
    }

    @Override
    protected boolean canDispenserEquipIntoSlot(EquipmentSlot slot) {
        return (slot == EquipmentSlot.BODY || slot == EquipmentSlot.SADDLE) && this.isTamed() || super.canDispenserEquipIntoSlot(slot);
    }

    public int getTemper() {
        return this.temper;
    }

    public void setTemper(int temper) {
        this.temper = temper;
    }

    public int modifyTemper(int addedTemper) {
        int i = Mth.clamp(this.getTemper() + addedTemper, 0, this.getMaxTemper());
        this.setTemper(i);
        return i;
    }

    @Override
    public boolean isCollidable(boolean ignoreClimbing) { // Paper - Climbing should not bypass cramming gamerule
        return !this.isVehicle();
    }

    private void eating() {
        this.openMouth();
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
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageMultiplier, DamageSource damageSource) {
        if (fallDistance > 1.0) {
            this.playSound(SoundEvents.HORSE_LAND, 0.4F, 1.0F);
        }

        int i = this.calculateFallDamage(fallDistance, damageMultiplier);
        if (i <= 0) {
            return false;
        } else {
            this.hurt(damageSource, i);
            this.propagateFallToPassengers(fallDistance, damageMultiplier, damageSource);
            this.playBlockFallSound();
            return true;
        }
    }

    public final int getInventorySize() {
        return AbstractMountInventoryMenu.getInventorySize(this.getInventoryColumns());
    }

    public void createInventory() {
        SimpleContainer simpleContainer = this.inventory;
        this.inventory = new SimpleContainer(this.getInventorySize(), (org.bukkit.entity.AbstractHorse) this.getBukkitEntity()); // CraftBukkit
        if (simpleContainer != null) {
            int min = Math.min(simpleContainer.getContainerSize(), this.inventory.getContainerSize());

            for (int i = 0; i < min; i++) {
                ItemStack item = simpleContainer.getItem(i);
                if (!item.isEmpty()) {
                    this.inventory.setItem(i, item.copy());
                }
            }
        }
    }

    @Override
    protected Holder<SoundEvent> getEquipSound(EquipmentSlot slot, ItemStack stack, Equippable equippable) {
        return (Holder<SoundEvent>)(slot == EquipmentSlot.SADDLE ? SoundEvents.HORSE_SADDLE : super.getEquipSound(slot, stack, equippable));
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        boolean flag = super.hurtServer(level, damageSource, amount);
        if (flag && this.random.nextInt(3) == 0) {
            this.standIfPossible();
        }

        return flag;
    }

    protected boolean canPerformRearing() {
        return true;
    }

    protected @Nullable SoundEvent getEatingSound() {
        return null;
    }

    protected @Nullable SoundEvent getAngrySound() {
        return null;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState block) {
        if (!block.liquid()) {
            BlockState blockState = this.level().getBlockState(pos.above());
            SoundType soundType = block.getSoundType();
            if (blockState.is(Blocks.SNOW)) {
                soundType = blockState.getSoundType();
            }

            if (this.isVehicle() && this.canGallop) {
                this.gallopSoundCounter++;
                if (this.gallopSoundCounter > 5 && this.gallopSoundCounter % 3 == 0) {
                    this.playGallopSound(soundType);
                } else if (this.gallopSoundCounter <= 5) {
                    this.playSound(SoundEvents.HORSE_STEP_WOOD, soundType.getVolume() * 0.15F, soundType.getPitch());
                }
            } else if (this.isWoodSoundType(soundType)) {
                this.playSound(SoundEvents.HORSE_STEP_WOOD, soundType.getVolume() * 0.15F, soundType.getPitch());
            } else {
                this.playSound(SoundEvents.HORSE_STEP, soundType.getVolume() * 0.15F, soundType.getPitch());
            }
        }
    }

    private boolean isWoodSoundType(SoundType soundType) {
        return soundType == SoundType.WOOD
            || soundType == SoundType.NETHER_WOOD
            || soundType == SoundType.STEM
            || soundType == SoundType.CHERRY_WOOD
            || soundType == SoundType.BAMBOO_WOOD;
    }

    protected void playGallopSound(SoundType soundType) {
        this.playSound(SoundEvents.HORSE_GALLOP, soundType.getVolume() * 0.15F, soundType.getPitch());
    }

    public static AttributeSupplier.Builder createBaseHorseAttributes() {
        return Animal.createAnimalAttributes()
            .add(Attributes.JUMP_STRENGTH, 0.7)
            .add(Attributes.MAX_HEALTH, 53.0)
            .add(Attributes.MOVEMENT_SPEED, 0.225F)
            .add(Attributes.STEP_HEIGHT, 1.0)
            .add(Attributes.SAFE_FALL_DISTANCE, 6.0)
            .add(Attributes.FALL_DAMAGE_MULTIPLIER, 0.5);
    }

    @Override
    public int getMaxSpawnClusterSize() {
        return 6;
    }

    public int getMaxTemper() {
        return this.maxDomestication; // CraftBukkit - return stored max domestication instead
    }

    @Override
    public float getSoundVolume() {
        return 0.8F;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 400;
    }

    @Override
    public void openCustomInventoryScreen(Player player) {
        if (!this.level().isClientSide() && (!this.isVehicle() || this.hasPassenger(player)) && this.isTamed()) {
            player.openHorseInventory(this, this.inventory);
        }
    }

    public InteractionResult fedFood(Player player, ItemStack stack) {
        boolean flag = this.handleEating(player, stack);
        if (flag) {
            stack.consume(1, player);
        }

        return (InteractionResult)(!flag && !this.level().isClientSide() ? InteractionResult.PASS : InteractionResult.SUCCESS_SERVER);
    }

    protected boolean handleEating(Player player, ItemStack stack) {
        boolean flag = false;
        float f = 0.0F;
        int i = 0;
        int i1 = 0;
        if (stack.is(Items.WHEAT)) {
            f = 2.0F;
            i = 20;
            i1 = 3;
        } else if (stack.is(Items.SUGAR)) {
            f = 1.0F;
            i = 30;
            i1 = 3;
        } else if (stack.is(Blocks.HAY_BLOCK.asItem())) {
            f = 20.0F;
            i = 180;
        } else if (stack.is(Items.APPLE)) {
            f = 3.0F;
            i = 60;
            i1 = 3;
        } else if (stack.is(Items.RED_MUSHROOM)) {
            f = 3.0F;
            i = 0;
            i1 = 3;
        } else if (stack.is(Items.CARROT)) {
            f = 3.0F;
            i = 60;
            i1 = 3;
        } else if (stack.is(Items.GOLDEN_CARROT)) {
            f = 4.0F;
            i = 60;
            i1 = 5;
            if (!this.level().isClientSide() && this.isTamed() && this.getAge() == 0 && !this.isInLove()) {
                flag = true;
                this.setInLove(player, stack.copy()); // Paper - Fix EntityBreedEvent copying
            }
        } else if (stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE)) {
            f = 10.0F;
            i = 240;
            i1 = 10;
            if (!this.level().isClientSide() && this.isTamed() && this.getAge() == 0 && !this.isInLove()) {
                flag = true;
                this.setInLove(player, stack.copy()); // Paper - Fix EntityBreedEvent copying
            }
        }

        if (this.getHealth() < this.getMaxHealth() && f > 0.0F) {
            this.heal(f, org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason.EATING); // CraftBukkit
            flag = true;
        }

        if (this.isBaby() && i > 0) {
            this.level().addParticle(ParticleTypes.HAPPY_VILLAGER, this.getRandomX(1.0), this.getRandomY() + 0.5, this.getRandomZ(1.0), 0.0, 0.0, 0.0);
            if (!this.level().isClientSide()) {
                this.ageUp(i);
                flag = true;
            }
        }

        if (i1 > 0 && (flag || !this.isTamed()) && this.getTemper() < this.getMaxTemper() && !this.level().isClientSide()) {
            this.modifyTemper(i1);
            flag = true;
        }

        if (flag) {
            this.eating();
            this.gameEvent(GameEvent.EAT);
        }

        return flag;
    }

    protected void doPlayerRide(Player player) {
        this.setEating(false);
        this.clearStanding();
        if (!this.level().isClientSide()) {
            player.setYRot(this.getYRot());
            player.setXRot(this.getXRot());
            player.startRiding(this);
        }
    }

    @Override
    public boolean isImmobile() {
        return super.isImmobile() && this.isVehicle() && this.isSaddled() || this.isEating() || this.isStanding();
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(ItemTags.HORSE_FOOD);
    }

    private void moveTail() {
        this.tailCounter = 1;
    }

    @Override
    protected void dropEquipment(ServerLevel level) {
        super.dropEquipment(level);
        if (this.inventory != null) {
            for (int i = 0; i < this.inventory.getContainerSize(); i++) {
                ItemStack item = this.inventory.getItem(i);
                if (!item.isEmpty() && !EnchantmentHelper.has(item, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) {
                    this.spawnAtLocation(level, item);
                }
            }
        }
    }

    @Override
    public void aiStep() {
        if (this.random.nextInt(200) == 0) {
            this.moveTail();
        }

        super.aiStep();
        if (this.level() instanceof ServerLevel serverLevel && this.isAlive()) {
            if (this.random.nextInt(900) == 0 && this.deathTime == 0) {
                this.heal(1.0F, org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason.REGEN); // CraftBukkit
            }

            if (this.canEatGrass()) {
                if (!this.isEating()
                    && !this.isVehicle()
                    && this.random.nextInt(300) == 0
                    && serverLevel.getBlockState(this.blockPosition().below()).is(Blocks.GRASS_BLOCK)) {
                    this.setEating(true);
                }

                if (this.isEating() && ++this.eatingCounter > 50) {
                    this.eatingCounter = 0;
                    this.setEating(false);
                }
            }

            this.followMommy(serverLevel);
        }
    }

    protected void followMommy(ServerLevel level) {
        if (this.isBred() && this.isBaby() && !this.isEating()) {
            LivingEntity nearestEntity = level.getNearestEntity(
                AbstractHorse.class, MOMMY_TARGETING, this, this.getX(), this.getY(), this.getZ(), this.getBoundingBox().inflate(16.0)
            );
            if (nearestEntity != null && this.distanceToSqr(nearestEntity) > 4.0) {
                this.navigation.createPath(nearestEntity, 0);
            }
        }
    }

    public boolean canEatGrass() {
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.mouthCounter > 0 && ++this.mouthCounter > 30) {
            this.mouthCounter = 0;
            this.setFlag(FLAG_OPEN_MOUTH, false);
        }

        if (this.standCounter > 0 && --this.standCounter <= 0) {
            this.clearStanding();
        }

        if (this.tailCounter > 0 && ++this.tailCounter > 8) {
            this.tailCounter = 0;
        }

        if (this.sprintCounter > 0) {
            this.sprintCounter++;
            if (this.sprintCounter > 300) {
                this.sprintCounter = 0;
            }
        }

        this.eatAnimO = this.eatAnim;
        if (this.isEating()) {
            this.eatAnim = this.eatAnim + ((1.0F - this.eatAnim) * 0.4F + 0.05F);
            if (this.eatAnim > 1.0F) {
                this.eatAnim = 1.0F;
            }
        } else {
            this.eatAnim = this.eatAnim + ((0.0F - this.eatAnim) * 0.4F - 0.05F);
            if (this.eatAnim < 0.0F) {
                this.eatAnim = 0.0F;
            }
        }

        this.standAnimO = this.standAnim;
        if (this.isStanding()) {
            this.eatAnim = 0.0F;
            this.eatAnimO = this.eatAnim;
            this.standAnim = this.standAnim + ((1.0F - this.standAnim) * 0.4F + 0.05F);
            if (this.standAnim > 1.0F) {
                this.standAnim = 1.0F;
            }
        } else {
            this.allowStandSliding = false;
            this.standAnim = this.standAnim + ((0.8F * this.standAnim * this.standAnim * this.standAnim - this.standAnim) * 0.6F - 0.05F);
            if (this.standAnim < 0.0F) {
                this.standAnim = 0.0F;
            }
        }

        this.mouthAnimO = this.mouthAnim;
        if (this.getFlag(FLAG_OPEN_MOUTH)) {
            this.mouthAnim = this.mouthAnim + ((1.0F - this.mouthAnim) * 0.7F + 0.05F);
            if (this.mouthAnim > 1.0F) {
                this.mouthAnim = 1.0F;
            }
        } else {
            this.mouthAnim = this.mouthAnim + ((0.0F - this.mouthAnim) * 0.7F - 0.05F);
            if (this.mouthAnim < 0.0F) {
                this.mouthAnim = 0.0F;
            }
        }
    }

    // Paper start - Horse API
    public void setMouthOpen(boolean open) {
        this.setFlag(FLAG_OPEN_MOUTH, open);
    }

    public boolean isMouthOpen() {
        return this.getFlag(FLAG_OPEN_MOUTH);
    }
    // Paper end - Horse API

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (this.isVehicle() || this.isBaby()) {
            return super.mobInteract(player, hand);
        } else if (this.isTamed() && player.isSecondaryUseActive()) {
            this.openCustomInventoryScreen(player);
            return InteractionResult.SUCCESS;
        } else {
            ItemStack itemInHand = player.getItemInHand(hand);
            if (!itemInHand.isEmpty()) {
                InteractionResult interactionResult = itemInHand.interactLivingEntity(player, this, hand);
                if (interactionResult.consumesAction()) {
                    return interactionResult;
                }

                if (this.isEquippableInSlot(itemInHand, EquipmentSlot.BODY) && !this.isWearingBodyArmor()) {
                    this.equipBodyArmor(player, itemInHand);
                    return InteractionResult.SUCCESS;
                }
            }

            this.doPlayerRide(player);
            return InteractionResult.SUCCESS;
        }
    }

    private void openMouth() {
        if (!this.level().isClientSide()) {
            this.mouthCounter = 1;
            this.setFlag(FLAG_OPEN_MOUTH, true);
        }
    }

    public void setEating(boolean eating) {
        this.setFlag(FLAG_EATING, eating);
    }

    public void setStanding(int standCounter) {
        this.setEating(false);
        this.setFlag(FLAG_STANDING, true);
        this.standCounter = standCounter;
    }

    public void clearStanding() {
        this.setFlag(FLAG_STANDING, false);
        this.standCounter = 0;
    }

    public @Nullable SoundEvent getAmbientStandSound() {
        return this.getAmbientSound();
    }

    public void standIfPossible() {
        if (this.canPerformRearing() && (this.isEffectiveAi() || !this.level().isClientSide())) {
            this.setStanding(20);
        }
    }

    public void makeMad() {
        if (!this.isStanding() && !this.level().isClientSide()) {
            this.standIfPossible();
            this.makeSound(this.getAngrySound());
        }
    }

    public boolean tameWithName(Player player) {
        this.setOwner(player);
        this.setTamed(true);
        if (player instanceof ServerPlayer) {
            CriteriaTriggers.TAME_ANIMAL.trigger((ServerPlayer)player, this);
        }

        this.level().broadcastEntityEvent(this, EntityEvent.TAMING_SUCCEEDED);
        return true;
    }

    @Override
    protected void tickRidden(Player player, Vec3 travelVector) {
        super.tickRidden(player, travelVector);
        Vec2 riddenRotation = this.getRiddenRotation(player);
        this.setRot(riddenRotation.y, riddenRotation.x);
        this.yRotO = this.yBodyRot = this.yHeadRot = this.getYRot();
        if (this.isLocalInstanceAuthoritative()) {
            if (travelVector.z <= 0.0) {
                this.gallopSoundCounter = 0;
            }

            if (this.onGround()) {
                if (this.playerJumpPendingScale > 0.0F && !this.isJumping()) {
                    this.executeRidersJump(this.playerJumpPendingScale, travelVector);
                }

                this.playerJumpPendingScale = 0.0F;
            }
        }
    }

    protected Vec2 getRiddenRotation(LivingEntity entity) {
        return new Vec2(entity.getXRot() * 0.5F, entity.getYRot());
    }

    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        passenger.absSnapRotationTo(this.getViewYRot(0.0F), this.getViewXRot(0.0F));
    }

    @Override
    protected Vec3 getRiddenInput(Player player, Vec3 travelVector) {
        if (this.onGround() && this.playerJumpPendingScale == 0.0F && this.isStanding() && !this.allowStandSliding) {
            return Vec3.ZERO;
        } else {
            float f = player.xxa * 0.5F;
            float f1 = player.zza;
            if (f1 <= 0.0F) {
                f1 *= 0.25F;
            }

            return new Vec3(f, 0.0, f1);
        }
    }

    @Override
    protected float getRiddenSpeed(Player player) {
        return (float)this.getAttributeValue(Attributes.MOVEMENT_SPEED);
    }

    protected void executeRidersJump(float playerJumpPendingScale, Vec3 travelVector) {
        double d = this.getJumpPower(playerJumpPendingScale);
        Vec3 deltaMovement = this.getDeltaMovement();
        this.setDeltaMovement(deltaMovement.x, d, deltaMovement.z);
        this.needsSync = true;
        if (travelVector.z > 0.0) {
            float sin = Mth.sin(this.getYRot() * (float) (Math.PI / 180.0));
            float cos = Mth.cos(this.getYRot() * (float) (Math.PI / 180.0));
            this.setDeltaMovement(this.getDeltaMovement().add(-0.4F * sin * playerJumpPendingScale, 0.0, 0.4F * cos * playerJumpPendingScale));
        }
    }

    protected void playJumpSound() {
        this.playSound(SoundEvents.HORSE_JUMP, 0.4F, 1.0F);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("EatingHaystack", this.isEating());
        output.putBoolean("Bred", this.isBred());
        output.putInt("Temper", this.getTemper());
        output.putBoolean("Tame", this.isTamed());
        EntityReference.store(this.owner, output, "Owner");
        output.putInt("Bukkit.MaxDomestication", this.maxDomestication); // Paper - max domestication
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setEating(input.getBooleanOr("EatingHaystack", false));
        this.setBred(input.getBooleanOr("Bred", false));
        this.setTemper(input.getIntOr("Temper", 0));
        this.setTamed(input.getBooleanOr("Tame", false));
        this.owner = EntityReference.readWithOldOwnerConversion(input, "Owner", this.level());
        this.maxDomestication = input.getIntOr("Bukkit.MaxDomestication", this instanceof Llama ? 30 : 100); // Paper - max domestication
    }

    @Override
    public boolean canMate(Animal partner) {
        return false;
    }

    protected boolean canParent() {
        return !this.isVehicle() && !this.isPassenger() && this.isTamed() && !this.isBaby() && this.getHealth() >= this.getMaxHealth() && this.isInLove();
    }

    public boolean isMobControlled() {
        return false;
    }

    @Override
    public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return null;
    }

    protected void setOffspringAttributes(AgeableMob parent, AbstractHorse child) {
        this.setOffspringAttribute(parent, child, Attributes.MAX_HEALTH, MIN_HEALTH, MAX_HEALTH);
        this.setOffspringAttribute(parent, child, Attributes.JUMP_STRENGTH, MIN_JUMP_STRENGTH, MAX_JUMP_STRENGTH);
        this.setOffspringAttribute(parent, child, Attributes.MOVEMENT_SPEED, MIN_MOVEMENT_SPEED, MAX_MOVEMENT_SPEED);
    }

    private void setOffspringAttribute(AgeableMob parent, AbstractHorse child, Holder<Attribute> attribute, double min, double max) {
        double d = createOffspringAttribute(this.getAttributeBaseValue(attribute), parent.getAttributeBaseValue(attribute), min, max, this.random);
        child.getAttribute(attribute).setBaseValue(d);
    }

    static double createOffspringAttribute(double value1, double value2, double min, double max, RandomSource random) {
        if (max <= min) {
            throw new IllegalArgumentException("Incorrect range for an attribute");
        } else {
            value1 = Mth.clamp(value1, min, max);
            value2 = Mth.clamp(value2, min, max);
            double d = 0.15 * (max - min);
            double d1 = Math.abs(value1 - value2) + d * 2.0;
            double d2 = (value1 + value2) / 2.0;
            double d3 = (random.nextDouble() + random.nextDouble() + random.nextDouble()) / 3.0 - 0.5;
            double d4 = d2 + d1 * d3;
            if (d4 > max) {
                double d5 = d4 - max;
                return max - d5;
            } else if (d4 < min) {
                double d5 = min - d4;
                return min + d5;
            } else {
                return d4;
            }
        }
    }

    public float getEatAnim(float partialTick) {
        return Mth.lerp(partialTick, this.eatAnimO, this.eatAnim);
    }

    public float getStandAnim(float partialTick) {
        return Mth.lerp(partialTick, this.standAnimO, this.standAnim);
    }

    public float getMouthAnim(float partialTick) {
        return Mth.lerp(partialTick, this.mouthAnimO, this.mouthAnim);
    }

    @Override
    public void onPlayerJump(int jumpPower) {
        if (this.isSaddled()) {
            if (jumpPower < 0) {
                jumpPower = 0;
            } else {
                this.allowStandSliding = true;
                this.standIfPossible();
            }

            this.playerJumpPendingScale = this.getPlayerJumpPendingScale(jumpPower);
        }
    }

    @Override
    public boolean canJump() {
        return this.isSaddled();
    }

    @Override
    public void handleStartJump(int jumpPower) {
        // CraftBukkit start
        float power;
        if (jumpPower >= 90) {
            power = 1.0F;
        } else {
            power = 0.4F + 0.4F * (float) jumpPower / 90.0F;
        }
        if (!org.bukkit.craftbukkit.event.CraftEventFactory.callHorseJumpEvent(this, power)) {
            return;
        }
        // CraftBukkit end
        this.allowStandSliding = true;
        this.standIfPossible();
        this.playJumpSound();
    }

    @Override
    public void handleStopJump() {
    }

    protected void spawnTamingParticles(boolean tamed) {
        ParticleOptions particleOptions = tamed ? ParticleTypes.HEART : ParticleTypes.SMOKE;

        for (int i = 0; i < 7; i++) {
            double d = this.random.nextGaussian() * 0.02;
            double d1 = this.random.nextGaussian() * 0.02;
            double d2 = this.random.nextGaussian() * 0.02;
            this.level().addParticle(particleOptions, this.getRandomX(1.0), this.getRandomY() + 0.5, this.getRandomZ(1.0), d, d1, d2);
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EntityEvent.TAMING_SUCCEEDED) {
            this.spawnTamingParticles(true);
        } else if (id == EntityEvent.TAMING_FAILED) {
            this.spawnTamingParticles(false);
        } else {
            super.handleEntityEvent(id);
        }
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction callback) {
        super.positionRider(passenger, callback);
        if (passenger instanceof LivingEntity) {
            ((LivingEntity)passenger).yBodyRot = this.yBodyRot;
        }
    }

    protected static float generateMaxHealth(IntUnaryOperator operator) {
        return 15.0F + operator.applyAsInt(8) + operator.applyAsInt(9);
    }

    protected static double generateJumpStrength(DoubleSupplier supplier) {
        return 0.4F + supplier.getAsDouble() * 0.2 + supplier.getAsDouble() * 0.2 + supplier.getAsDouble() * 0.2;
    }

    protected static double generateSpeed(DoubleSupplier supplier) {
        return (0.45F + supplier.getAsDouble() * 0.3 + supplier.getAsDouble() * 0.3 + supplier.getAsDouble() * 0.3) * 0.25;
    }

    @Override
    public boolean onClimbable() {
        return false;
    }

    @Override
    public @Nullable SlotAccess getSlot(int slot) {
        int i = slot - 500;
        return i >= 0 && i < this.inventory.getContainerSize() ? this.inventory.getSlot(i) : super.getSlot(slot);
    }

    @Override
    public @Nullable LivingEntity getControllingPassenger() {
        return (LivingEntity)(this.isSaddled() && this.getFirstPassenger() instanceof Player player ? player : super.getControllingPassenger());
    }

    private @Nullable Vec3 getDismountLocationInDirection(Vec3 direction, LivingEntity passenger) {
        double d = this.getX() + direction.x;
        double d1 = this.getBoundingBox().minY;
        double d2 = this.getZ() + direction.z;
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        for (Pose pose : passenger.getDismountPoses()) {
            mutableBlockPos.set(d, d1, d2);
            double d3 = this.getBoundingBox().maxY + 0.75;

            do {
                double blockFloorHeight = this.level().getBlockFloorHeight(mutableBlockPos);
                if (mutableBlockPos.getY() + blockFloorHeight > d3) {
                    break;
                }

                if (DismountHelper.isBlockFloorValid(blockFloorHeight)) {
                    AABB localBoundsForPose = passenger.getLocalBoundsForPose(pose);
                    Vec3 vec3 = new Vec3(d, mutableBlockPos.getY() + blockFloorHeight, d2);
                    if (DismountHelper.canDismountTo(this.level(), passenger, localBoundsForPose.move(vec3))) {
                        passenger.setPose(pose);
                        return vec3;
                    }
                }

                mutableBlockPos.move(Direction.UP);
            } while (mutableBlockPos.getY() < d3);
        }

        return null;
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity livingEntity) {
        Vec3 collisionHorizontalEscapeVector = getCollisionHorizontalEscapeVector(
            this.getBbWidth(), livingEntity.getBbWidth(), this.getYRot() + (livingEntity.getMainArm() == HumanoidArm.RIGHT ? 90.0F : -90.0F)
        );
        Vec3 dismountLocationInDirection = this.getDismountLocationInDirection(collisionHorizontalEscapeVector, livingEntity);
        if (dismountLocationInDirection != null) {
            return dismountLocationInDirection;
        } else {
            Vec3 collisionHorizontalEscapeVector1 = getCollisionHorizontalEscapeVector(
                this.getBbWidth(), livingEntity.getBbWidth(), this.getYRot() + (livingEntity.getMainArm() == HumanoidArm.LEFT ? 90.0F : -90.0F)
            );
            Vec3 dismountLocationInDirection1 = this.getDismountLocationInDirection(collisionHorizontalEscapeVector1, livingEntity);
            return dismountLocationInDirection1 != null ? dismountLocationInDirection1 : this.position();
        }
    }

    protected void randomizeAttributes(RandomSource random) {
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(
        ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData spawnGroupData
    ) {
        if (spawnGroupData == null) {
            spawnGroupData = new AgeableMob.AgeableMobGroupData(0.2F);
        }

        this.randomizeAttributes(level.getRandom());
        return super.finalizeSpawn(level, difficulty, spawnReason, spawnGroupData);
    }

    public boolean hasInventoryChanged(Container oldInventory) {
        return this.inventory != oldInventory;
    }

    public int getAmbientStandInterval() {
        return this.getAmbientSoundInterval();
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity entity, EntityDimensions dimensions, float partialTick) {
        return super.getPassengerAttachmentPoint(entity, dimensions, partialTick)
            .add(new Vec3(0.0, 0.15 * this.standAnimO * partialTick, -0.7 * this.standAnimO * partialTick).yRot(-this.getYRot() * (float) (Math.PI / 180.0)));
    }

    public int getInventoryColumns() {
        return 0;
    }

    class MountPanicGoal extends PanicGoal {
        public MountPanicGoal(final double speedModifier) {
            super(AbstractHorse.this, speedModifier);
        }

        @Override
        public boolean shouldPanic() {
            return !AbstractHorse.this.isMobControlled() && super.shouldPanic();
        }
    }
}
