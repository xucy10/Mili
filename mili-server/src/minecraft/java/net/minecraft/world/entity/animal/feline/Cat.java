package net.minecraft.world.entity.animal.feline;

import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.CatLieOnBedGoal;
import net.minecraft.world.entity.ai.goal.CatSitOnBlockGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.OcelotAttackGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NonTameRandomTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.rabbit.Rabbit;
import net.minecraft.world.entity.animal.turtle.Turtle;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.variant.SpawnContext;
import net.minecraft.world.entity.variant.VariantUtils;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

public class Cat extends TamableAnimal {
    public static final double TEMPT_SPEED_MOD = 0.6;
    public static final double WALK_SPEED_MOD = 0.8;
    public static final double SPRINT_SPEED_MOD = 1.33;
    private static final EntityDataAccessor<Holder<CatVariant>> DATA_VARIANT_ID = SynchedEntityData.defineId(Cat.class, EntityDataSerializers.CAT_VARIANT);
    private static final EntityDataAccessor<Boolean> IS_LYING = SynchedEntityData.defineId(Cat.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> RELAX_STATE_ONE = SynchedEntityData.defineId(Cat.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_COLLAR_COLOR = SynchedEntityData.defineId(Cat.class, EntityDataSerializers.INT);
    private static final ResourceKey<CatVariant> DEFAULT_VARIANT = CatVariants.BLACK;
    private static final DyeColor DEFAULT_COLLAR_COLOR = DyeColor.RED;
    private Cat.@Nullable CatAvoidEntityGoal<Player> avoidPlayersGoal;
    private @Nullable TemptGoal temptGoal;
    private float lieDownAmount;
    private float lieDownAmountO;
    private float lieDownAmountTail;
    private float lieDownAmountOTail;
    private boolean isLyingOnTopOfSleepingPlayer;
    private float relaxStateOneAmount;
    private float relaxStateOneAmountO;

    public Cat(EntityType<? extends Cat> type, Level level) {
        super(type, level);
        this.reassessTameGoals();
    }

    @Override
    protected void registerGoals() {
        this.temptGoal = new Cat.CatTemptGoal(this, 0.6, stack -> stack.is(ItemTags.CAT_FOOD), true);
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(1, new TamableAnimal.TamableAnimalPanicGoal(1.5));
        this.goalSelector.addGoal(2, new SitWhenOrderedToGoal(this));
        this.goalSelector.addGoal(3, new Cat.CatRelaxOnOwnerGoal(this));
        this.goalSelector.addGoal(4, this.temptGoal);
        this.goalSelector.addGoal(5, new CatLieOnBedGoal(this, 1.1, 8));
        this.goalSelector.addGoal(6, new FollowOwnerGoal(this, 1.0, 10.0F, 5.0F));
        this.goalSelector.addGoal(7, new CatSitOnBlockGoal(this, 0.8));
        this.goalSelector.addGoal(8, new LeapAtTargetGoal(this, 0.3F));
        this.goalSelector.addGoal(9, new OcelotAttackGoal(this));
        this.goalSelector.addGoal(10, new BreedGoal(this, 0.8));
        this.goalSelector.addGoal(11, new WaterAvoidingRandomStrollGoal(this, 0.8, 1.0000001E-5F));
        this.goalSelector.addGoal(12, new LookAtPlayerGoal(this, Player.class, 10.0F));
        this.targetSelector.addGoal(1, new NonTameRandomTargetGoal<>(this, Rabbit.class, false, null));
        this.targetSelector.addGoal(1, new NonTameRandomTargetGoal<>(this, Turtle.class, false, Turtle.BABY_ON_LAND_SELECTOR));
    }

    public Holder<CatVariant> getVariant() {
        return this.entityData.get(DATA_VARIANT_ID);
    }

    public void setVariant(Holder<CatVariant> variant) {
        this.entityData.set(DATA_VARIANT_ID, variant);
    }

    @Override
    public <T> @Nullable T get(DataComponentType<? extends T> component) {
        if (component == DataComponents.CAT_VARIANT) {
            return castComponentValue((DataComponentType<T>)component, this.getVariant());
        } else {
            return component == DataComponents.CAT_COLLAR ? castComponentValue((DataComponentType<T>)component, this.getCollarColor()) : super.get(component);
        }
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter componentGetter) {
        this.applyImplicitComponentIfPresent(componentGetter, DataComponents.CAT_VARIANT);
        this.applyImplicitComponentIfPresent(componentGetter, DataComponents.CAT_COLLAR);
        super.applyImplicitComponents(componentGetter);
    }

    @Override
    protected <T> boolean applyImplicitComponent(DataComponentType<T> component, T value) {
        if (component == DataComponents.CAT_VARIANT) {
            this.setVariant(castComponentValue(DataComponents.CAT_VARIANT, value));
            return true;
        } else if (component == DataComponents.CAT_COLLAR) {
            this.setCollarColor(castComponentValue(DataComponents.CAT_COLLAR, value));
            return true;
        } else {
            return super.applyImplicitComponent(component, value);
        }
    }

    public void setLying(boolean lying) {
        this.entityData.set(IS_LYING, lying);
    }

    public boolean isLying() {
        return this.entityData.get(IS_LYING);
    }

    public void setRelaxStateOne(boolean relaxStateOne) {
        this.entityData.set(RELAX_STATE_ONE, relaxStateOne);
    }

    public boolean isRelaxStateOne() {
        return this.entityData.get(RELAX_STATE_ONE);
    }

    public DyeColor getCollarColor() {
        return DyeColor.byId(this.entityData.get(DATA_COLLAR_COLOR));
    }

    public void setCollarColor(DyeColor color) {
        this.entityData.set(DATA_COLLAR_COLOR, color.getId());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_VARIANT_ID, VariantUtils.getDefaultOrAny(this.registryAccess(), DEFAULT_VARIANT));
        builder.define(IS_LYING, false);
        builder.define(RELAX_STATE_ONE, false);
        builder.define(DATA_COLLAR_COLOR, DEFAULT_COLLAR_COLOR.getId());
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        VariantUtils.writeVariant(output, this.getVariant());
        output.store("CollarColor", DyeColor.LEGACY_ID_CODEC, this.getCollarColor());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        VariantUtils.readVariant(input, Registries.CAT_VARIANT).ifPresent(this::setVariant);
        this.setCollarColor(input.read("CollarColor", DyeColor.LEGACY_ID_CODEC).orElse(DEFAULT_COLLAR_COLOR));
    }

    @Override
    public void customServerAiStep(ServerLevel level) {
        if (this.getMoveControl().hasWanted()) {
            double speedModifier = this.getMoveControl().getSpeedModifier();
            if (speedModifier == 0.6) {
                this.setPose(Pose.CROUCHING);
                this.setSprinting(false);
            } else if (speedModifier == 1.33) {
                this.setPose(Pose.STANDING);
                this.setSprinting(true);
            } else {
                this.setPose(Pose.STANDING);
                this.setSprinting(false);
            }
        } else {
            this.setPose(Pose.STANDING);
            this.setSprinting(false);
        }
    }

    @Override
    public @Nullable SoundEvent getAmbientSound() {
        if (this.isTame()) {
            if (this.isInLove()) {
                return SoundEvents.CAT_PURR;
            } else {
                return this.random.nextInt(4) == 0 ? SoundEvents.CAT_PURREOW : SoundEvents.CAT_AMBIENT;
            }
        } else {
            return SoundEvents.CAT_STRAY_AMBIENT;
        }
    }

    @Override
    public int getAmbientSoundInterval() {
        return 120;
    }

    public void hiss() {
        this.makeSound(SoundEvents.CAT_HISS);
    }

    @Override
    public SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.CAT_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.CAT_DEATH;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes().add(Attributes.MAX_HEALTH, 10.0).add(Attributes.MOVEMENT_SPEED, 0.3F).add(Attributes.ATTACK_DAMAGE, 3.0);
    }

    @Override
    protected void playEatingSound() {
        this.playSound(SoundEvents.CAT_EAT, 1.0F, 1.0F);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.temptGoal != null && this.temptGoal.isRunning() && !this.isTame() && this.tickCount % 100 == 0) {
            this.playSound(SoundEvents.CAT_BEG_FOR_FOOD, 1.0F, 1.0F);
        }

        this.handleLieDown();
    }

    private void handleLieDown() {
        if ((this.isLying() || this.isRelaxStateOne()) && this.tickCount % 5 == 0) {
            this.playSound(SoundEvents.CAT_PURR, 0.6F + 0.4F * (this.random.nextFloat() - this.random.nextFloat()), 1.0F);
        }

        this.updateLieDownAmount();
        this.updateRelaxStateOneAmount();
        this.isLyingOnTopOfSleepingPlayer = false;
        if (this.isLying()) {
            BlockPos blockPos = this.blockPosition();

            for (Player player : this.level().getEntitiesOfClass(Player.class, new AABB(blockPos).inflate(2.0, 2.0, 2.0))) {
                if (player.isSleeping()) {
                    this.isLyingOnTopOfSleepingPlayer = true;
                    break;
                }
            }
        }
    }

    public boolean isLyingOnTopOfSleepingPlayer() {
        return this.isLyingOnTopOfSleepingPlayer;
    }

    private void updateLieDownAmount() {
        this.lieDownAmountO = this.lieDownAmount;
        this.lieDownAmountOTail = this.lieDownAmountTail;
        if (this.isLying()) {
            this.lieDownAmount = Math.min(1.0F, this.lieDownAmount + 0.15F);
            this.lieDownAmountTail = Math.min(1.0F, this.lieDownAmountTail + 0.08F);
        } else {
            this.lieDownAmount = Math.max(0.0F, this.lieDownAmount - 0.22F);
            this.lieDownAmountTail = Math.max(0.0F, this.lieDownAmountTail - 0.13F);
        }
    }

    private void updateRelaxStateOneAmount() {
        this.relaxStateOneAmountO = this.relaxStateOneAmount;
        if (this.isRelaxStateOne()) {
            this.relaxStateOneAmount = Math.min(1.0F, this.relaxStateOneAmount + 0.1F);
        } else {
            this.relaxStateOneAmount = Math.max(0.0F, this.relaxStateOneAmount - 0.13F);
        }
    }

    public float getLieDownAmount(float partialTick) {
        return Mth.lerp(partialTick, this.lieDownAmountO, this.lieDownAmount);
    }

    public float getLieDownAmountTail(float partialTick) {
        return Mth.lerp(partialTick, this.lieDownAmountOTail, this.lieDownAmountTail);
    }

    public float getRelaxStateOneAmount(float partialTick) {
        return Mth.lerp(partialTick, this.relaxStateOneAmountO, this.relaxStateOneAmount);
    }

    @Override
    public @Nullable Cat getBreedOffspring(ServerLevel level, AgeableMob partner) {
        Cat cat = EntityType.CAT.create(level, EntitySpawnReason.BREEDING);
        if (cat != null && partner instanceof Cat cat1) {
            if (this.random.nextBoolean()) {
                cat.setVariant(this.getVariant());
            } else {
                cat.setVariant(cat1.getVariant());
            }

            if (this.isTame()) {
                cat.setOwnerReference(this.getOwnerReference());
                cat.setTame(true, true);
                DyeColor collarColor = this.getCollarColor();
                DyeColor collarColor1 = cat1.getCollarColor();
                cat.setCollarColor(DyeColor.getMixedColor(level, collarColor, collarColor1));
            }
        }

        return cat;
    }

    @Override
    public boolean canMate(Animal otherAnimal) {
        return this.isTame() && otherAnimal instanceof Cat cat && cat.isTame() && super.canMate(otherAnimal);
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(
        ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData spawnGroupData
    ) {
        spawnGroupData = super.finalizeSpawn(level, difficulty, spawnReason, spawnGroupData);
        VariantUtils.selectVariantToSpawn(SpawnContext.create(level, this.blockPosition()), Registries.CAT_VARIANT).ifPresent(this::setVariant);
        return spawnGroupData;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemInHand = player.getItemInHand(hand);
        Item item = itemInHand.getItem();
        if (this.isTame()) {
            if (this.isOwnedBy(player)) {
                if (item instanceof DyeItem dyeItem) {
                    DyeColor dyeColor = dyeItem.getDyeColor();
                    if (dyeColor != this.getCollarColor()) {
                        // Paper start - Add EntityDyeEvent and CollarColorable interface
                        final io.papermc.paper.event.entity.EntityDyeEvent event = new io.papermc.paper.event.entity.EntityDyeEvent(this.getBukkitEntity(), org.bukkit.DyeColor.getByWoolData((byte) dyeColor.getId()), ((net.minecraft.server.level.ServerPlayer) player).getBukkitEntity());
                        if (!event.callEvent()) return InteractionResult.FAIL;
                        dyeColor = DyeColor.byId(event.getColor().getWoolData());
                        // Paper end - Add EntityDyeEvent and CollarColorable interface
                        if (!this.level().isClientSide()) {
                            this.setCollarColor(dyeColor);
                            itemInHand.consume(1, player);
                            this.setPersistenceRequired();
                        }

                        return InteractionResult.SUCCESS;
                    }
                } else if (this.isFood(itemInHand) && this.getHealth() < this.getMaxHealth()) {
                    if (!this.level().isClientSide()) {
                        this.usePlayerItem(player, hand, itemInHand);
                        FoodProperties foodProperties = itemInHand.get(DataComponents.FOOD);
                        this.heal(foodProperties != null ? foodProperties.nutrition() : 1.0F, org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason.EATING); // Paper - Add missing regain reason
                        this.playEatingSound();
                    }

                    return InteractionResult.SUCCESS;
                }

                InteractionResult interactionResult = super.mobInteract(player, hand);
                if (!interactionResult.consumesAction()) {
                    this.setOrderedToSit(!this.isOrderedToSit());
                    return InteractionResult.SUCCESS;
                }

                return interactionResult;
            }
        } else if (this.isFood(itemInHand)) {
            if (!this.level().isClientSide()) {
                this.usePlayerItem(player, hand, itemInHand);
                this.tryToTame(player);
                this.setPersistenceRequired();
                this.playEatingSound();
            }

            return InteractionResult.SUCCESS;
        }

        InteractionResult interactionResult = super.mobInteract(player, hand);
        if (interactionResult.consumesAction()) {
            this.setPersistenceRequired();
        }

        return interactionResult;
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(ItemTags.CAT_FOOD);
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return !this.isTame() && this.tickCount > 2400;
    }

    @Override
    public void setTame(boolean tame, boolean applyTamingSideEffects) {
        super.setTame(tame, applyTamingSideEffects);
        this.reassessTameGoals();
    }

    protected void reassessTameGoals() {
        if (this.avoidPlayersGoal == null) {
            this.avoidPlayersGoal = new Cat.CatAvoidEntityGoal<>(this, Player.class, 16.0F, 0.8, 1.33);
        }

        this.goalSelector.removeGoal(this.avoidPlayersGoal);
        if (!this.isTame()) {
            this.goalSelector.addGoal(4, this.avoidPlayersGoal);
        }
    }

    private void tryToTame(Player player) {
        if (this.random.nextInt(3) == 0 && !org.bukkit.craftbukkit.event.CraftEventFactory.callEntityTameEvent(this, player).isCancelled()) { // CraftBukkit
            this.tame(player);
            this.setOrderedToSit(true);
            this.level().broadcastEntityEvent(this, EntityEvent.TAMING_SUCCEEDED);
        } else {
            this.level().broadcastEntityEvent(this, EntityEvent.TAMING_FAILED);
        }
    }

    @Override
    public boolean isSteppingCarefully() {
        return this.isCrouching() || super.isSteppingCarefully();
    }

    static class CatAvoidEntityGoal<T extends LivingEntity> extends AvoidEntityGoal<T> {
        private final Cat cat;

        public CatAvoidEntityGoal(Cat cat, Class<T> avoidClass, float maxDist, double walkSpeedModifier, double sprintSpeedModifier) {
            super(cat, avoidClass, maxDist, walkSpeedModifier, sprintSpeedModifier, EntitySelector.NO_CREATIVE_OR_SPECTATOR);
            this.cat = cat;
        }

        @Override
        public boolean canUse() {
            return !this.cat.isTame() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return !this.cat.isTame() && super.canContinueToUse();
        }
    }

    static class CatRelaxOnOwnerGoal extends Goal {
        private final Cat cat;
        private @Nullable Player ownerPlayer;
        private @Nullable BlockPos goalPos;
        private int onBedTicks;

        public CatRelaxOnOwnerGoal(Cat cat) {
            this.cat = cat;
        }

        @Override
        public boolean canUse() {
            if (!this.cat.isTame()) {
                return false;
            } else if (this.cat.isOrderedToSit()) {
                return false;
            } else {
                LivingEntity owner = this.cat.getOwner();
                if (owner instanceof Player player) {
                    this.ownerPlayer = player;
                    if (!owner.isSleeping()) {
                        return false;
                    }

                    if (this.cat.distanceToSqr(this.ownerPlayer) > 100.0) {
                        return false;
                    }

                    BlockPos blockPos = this.ownerPlayer.blockPosition();
                    BlockState blockState = this.cat.level().getBlockState(blockPos);
                    if (blockState.is(BlockTags.BEDS)) {
                        this.goalPos = blockState.getOptionalValue(BedBlock.FACING)
                            .map(pos -> blockPos.relative(pos.getOpposite()))
                            .orElseGet(() -> new BlockPos(blockPos));
                        return !this.spaceIsOccupied();
                    }
                }

                return false;
            }
        }

        private boolean spaceIsOccupied() {
            for (Cat cat : this.cat.level().getEntitiesOfClass(Cat.class, new AABB(this.goalPos).inflate(2.0))) {
                if (cat != this.cat && (cat.isLying() || cat.isRelaxStateOne())) {
                    return true;
                }
            }

            return false;
        }

        @Override
        public boolean canContinueToUse() {
            return this.cat.isTame()
                && !this.cat.isOrderedToSit()
                && this.ownerPlayer != null
                && this.ownerPlayer.isSleeping()
                && this.goalPos != null
                && !this.spaceIsOccupied();
        }

        @Override
        public void start() {
            if (this.goalPos != null) {
                this.cat.setInSittingPose(false);
                this.cat.getNavigation().moveTo(this.goalPos.getX(), this.goalPos.getY(), this.goalPos.getZ(), 1.1F);
            }
        }

        @Override
        public void stop() {
            this.cat.setLying(false);
            if (this.ownerPlayer.getSleepTimer() >= 100
                && this.cat.level().getRandom().nextFloat()
                    < this.cat.level().environmentAttributes().getValue(EnvironmentAttributes.CAT_WAKING_UP_GIFT_CHANCE, this.cat.position())) {
                this.giveMorningGift();
            }

            this.onBedTicks = 0;
            this.cat.setRelaxStateOne(false);
            this.cat.getNavigation().stop();
        }

        private void giveMorningGift() {
            RandomSource random = this.cat.getRandom();
            BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
            mutableBlockPos.set(this.cat.isLeashed() ? this.cat.getLeashHolder().blockPosition() : this.cat.blockPosition());
            this.cat
                .randomTeleport(
                    mutableBlockPos.getX() + random.nextInt(11) - 5,
                    mutableBlockPos.getY() + random.nextInt(5) - 2,
                    mutableBlockPos.getZ() + random.nextInt(11) - 5,
                    false
                );
            mutableBlockPos.set(this.cat.blockPosition());
            this.cat
                .dropFromGiftLootTable(
                    getServerLevel(this.cat),
                    BuiltInLootTables.CAT_MORNING_GIFT,
                    // CraftBukkit start
                    (level, stack) -> {
                        final ItemEntity item = new ItemEntity(
                            level,
                            (double)mutableBlockPos.getX() - Mth.sin(this.cat.yBodyRot * (float) (Math.PI / 180.0)),
                            mutableBlockPos.getY(),
                            (double)mutableBlockPos.getZ() + Mth.cos(this.cat.yBodyRot * (float) (Math.PI / 180.0)),
                            stack
                        );
                        org.bukkit.event.entity.EntityDropItemEvent event = new org.bukkit.event.entity.EntityDropItemEvent(this.cat.getBukkitEntity(), (org.bukkit.entity.Item) item.getBukkitEntity());
                        if (!event.callEvent()) return;
                        level.addFreshEntity(item);
                    }
                    // CraftBukkit end
                );
        }

        @Override
        public void tick() {
            if (this.ownerPlayer != null && this.goalPos != null) {
                this.cat.setInSittingPose(false);
                this.cat.getNavigation().moveTo(this.goalPos.getX(), this.goalPos.getY(), this.goalPos.getZ(), 1.1F);
                if (this.cat.distanceToSqr(this.ownerPlayer) < 2.5) {
                    this.onBedTicks++;
                    if (this.onBedTicks > this.adjustedTickDelay(16)) {
                        this.cat.setLying(true);
                        this.cat.setRelaxStateOne(false);
                    } else {
                        this.cat.lookAt(this.ownerPlayer, 45.0F, 45.0F);
                        this.cat.setRelaxStateOne(true);
                    }
                } else {
                    this.cat.setLying(false);
                }
            }
        }
    }

    static class CatTemptGoal extends TemptGoal {
        private @Nullable LivingEntity selectedPlayer; // CraftBukkit
        private final Cat cat;

        public CatTemptGoal(Cat cat, double speedModifier, Predicate<ItemStack> items, boolean canScare) {
            super(cat, speedModifier, items, canScare);
            this.cat = cat;
        }

        @Override
        public void tick() {
            super.tick();
            if (this.selectedPlayer == null && this.mob.getRandom().nextInt(this.adjustedTickDelay(600)) == 0) {
                this.selectedPlayer = this.player;
            } else if (this.mob.getRandom().nextInt(this.adjustedTickDelay(500)) == 0) {
                this.selectedPlayer = null;
            }
        }

        @Override
        protected boolean canScare() {
            return (this.selectedPlayer == null || !this.selectedPlayer.equals(this.player)) && super.canScare();
        }

        @Override
        public boolean canUse() {
            return super.canUse() && !this.cat.isTame();
        }
    }
}
