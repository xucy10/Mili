package net.minecraft.world.entity.animal.wolf;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Crackiness;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.BegGoal;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NonTameRandomTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.ai.goal.target.ResetUniversalAngerTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.equine.Llama;
import net.minecraft.world.entity.animal.turtle.Turtle;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.variant.SpawnContext;
import net.minecraft.world.entity.variant.VariantUtils;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class Wolf extends TamableAnimal implements NeutralMob {
    private static final EntityDataAccessor<Boolean> DATA_INTERESTED_ID = SynchedEntityData.defineId(Wolf.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_COLLAR_COLOR = SynchedEntityData.defineId(Wolf.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DATA_ANGER_END_TIME = SynchedEntityData.defineId(Wolf.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Holder<WolfVariant>> DATA_VARIANT_ID = SynchedEntityData.defineId(Wolf.class, EntityDataSerializers.WOLF_VARIANT);
    private static final EntityDataAccessor<Holder<WolfSoundVariant>> DATA_SOUND_VARIANT_ID = SynchedEntityData.defineId(
        Wolf.class, EntityDataSerializers.WOLF_SOUND_VARIANT
    );
    public static final TargetingConditions.Selector PREY_SELECTOR = (entity, level) -> {
        EntityType<?> type = entity.getType();
        return type == EntityType.SHEEP || type == EntityType.RABBIT || type == EntityType.FOX;
    };
    private static final float START_HEALTH = 8.0F;
    private static final float TAME_HEALTH = 40.0F;
    private static final float ARMOR_REPAIR_UNIT = 0.125F;
    public static final float DEFAULT_TAIL_ANGLE = (float) (Math.PI / 5);
    private static final DyeColor DEFAULT_COLLAR_COLOR = DyeColor.RED;
    private float interestedAngle;
    private float interestedAngleO;
    public boolean isWet;
    private boolean isShaking;
    private float shakeAnim;
    private float shakeAnimO;
    private static final UniformInt PERSISTENT_ANGER_TIME = TimeUtil.rangeOfSeconds(20, 39);
    private @Nullable EntityReference<LivingEntity> persistentAngerTarget;

    public Wolf(EntityType<? extends Wolf> type, Level level) {
        super(type, level);
        this.setTame(false, false);
        this.setPathfindingMalus(PathType.POWDER_SNOW, -1.0F);
        this.setPathfindingMalus(PathType.DANGER_POWDER_SNOW, -1.0F);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(1, new TamableAnimal.TamableAnimalPanicGoal(1.5, DamageTypeTags.PANIC_ENVIRONMENTAL_CAUSES));
        this.goalSelector.addGoal(2, new SitWhenOrderedToGoal(this));
        this.goalSelector.addGoal(3, new Wolf.WolfAvoidEntityGoal<>(this, Llama.class, 24.0F, 1.5, 1.5));
        this.goalSelector.addGoal(4, new LeapAtTargetGoal(this, 0.4F));
        this.goalSelector.addGoal(5, new MeleeAttackGoal(this, 1.0, true));
        this.goalSelector.addGoal(6, new FollowOwnerGoal(this, 1.0, 10.0F, 2.0F));
        this.goalSelector.addGoal(7, new BreedGoal(this, 1.0));
        this.goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 1.0));
        this.goalSelector.addGoal(9, new BegGoal(this, 8.0F));
        this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(10, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
        this.targetSelector.addGoal(3, new HurtByTargetGoal(this).setAlertOthers());
        this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, this::isAngryAt));
        this.targetSelector.addGoal(5, new NonTameRandomTargetGoal<>(this, Animal.class, false, PREY_SELECTOR));
        this.targetSelector.addGoal(6, new NonTameRandomTargetGoal<>(this, Turtle.class, false, Turtle.BABY_ON_LAND_SELECTOR));
        this.targetSelector.addGoal(7, new NearestAttackableTargetGoal<>(this, AbstractSkeleton.class, false));
        this.targetSelector.addGoal(8, new ResetUniversalAngerTargetGoal<>(this, true));
    }

    public Identifier getTexture() {
        WolfVariant wolfVariant = this.getVariant().value();
        if (this.isTame()) {
            return wolfVariant.assetInfo().tame().texturePath();
        } else {
            return this.isAngry() ? wolfVariant.assetInfo().angry().texturePath() : wolfVariant.assetInfo().wild().texturePath();
        }
    }

    public Holder<WolfVariant> getVariant() {
        return this.entityData.get(DATA_VARIANT_ID);
    }

    public void setVariant(Holder<WolfVariant> variant) {
        this.entityData.set(DATA_VARIANT_ID, variant);
    }

    public Holder<WolfSoundVariant> getSoundVariant() {
        return this.entityData.get(DATA_SOUND_VARIANT_ID);
    }

    public void setSoundVariant(Holder<WolfSoundVariant> soundVariant) {
        this.entityData.set(DATA_SOUND_VARIANT_ID, soundVariant);
    }

    @Override
    public <T> @Nullable T get(DataComponentType<? extends T> component) {
        if (component == DataComponents.WOLF_VARIANT) {
            return castComponentValue((DataComponentType<T>)component, this.getVariant());
        } else if (component == DataComponents.WOLF_SOUND_VARIANT) {
            return castComponentValue((DataComponentType<T>)component, this.getSoundVariant());
        } else {
            return component == DataComponents.WOLF_COLLAR ? castComponentValue((DataComponentType<T>)component, this.getCollarColor()) : super.get(component);
        }
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter componentGetter) {
        this.applyImplicitComponentIfPresent(componentGetter, DataComponents.WOLF_VARIANT);
        this.applyImplicitComponentIfPresent(componentGetter, DataComponents.WOLF_SOUND_VARIANT);
        this.applyImplicitComponentIfPresent(componentGetter, DataComponents.WOLF_COLLAR);
        super.applyImplicitComponents(componentGetter);
    }

    @Override
    protected <T> boolean applyImplicitComponent(DataComponentType<T> component, T value) {
        if (component == DataComponents.WOLF_VARIANT) {
            this.setVariant(castComponentValue(DataComponents.WOLF_VARIANT, value));
            return true;
        } else if (component == DataComponents.WOLF_SOUND_VARIANT) {
            this.setSoundVariant(castComponentValue(DataComponents.WOLF_SOUND_VARIANT, value));
            return true;
        } else if (component == DataComponents.WOLF_COLLAR) {
            this.setCollarColor(castComponentValue(DataComponents.WOLF_COLLAR, value));
            return true;
        } else {
            return super.applyImplicitComponent(component, value);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes().add(Attributes.MOVEMENT_SPEED, 0.3F).add(Attributes.MAX_HEALTH, 8.0).add(Attributes.ATTACK_DAMAGE, 4.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        Registry<WolfSoundVariant> registry = this.registryAccess().lookupOrThrow(Registries.WOLF_SOUND_VARIANT);
        builder.define(DATA_VARIANT_ID, VariantUtils.getDefaultOrAny(this.registryAccess(), WolfVariants.DEFAULT));
        builder.define(DATA_SOUND_VARIANT_ID, registry.get(WolfSoundVariants.CLASSIC).or(registry::getAny).orElseThrow());
        builder.define(DATA_INTERESTED_ID, false);
        builder.define(DATA_COLLAR_COLOR, DEFAULT_COLLAR_COLOR.getId());
        builder.define(DATA_ANGER_END_TIME, -1L);
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.WOLF_STEP, 0.15F, 1.0F);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store("CollarColor", DyeColor.LEGACY_ID_CODEC, this.getCollarColor());
        VariantUtils.writeVariant(output, this.getVariant());
        this.addPersistentAngerSaveData(output);
        this.getSoundVariant()
            .unwrapKey()
            .ifPresent(
                resourceKey -> output.store("sound_variant", ResourceKey.codec(Registries.WOLF_SOUND_VARIANT), (ResourceKey<WolfSoundVariant>)resourceKey)
            );
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        VariantUtils.readVariant(input, Registries.WOLF_VARIANT).ifPresent(this::setVariant);
        this.setCollarColor(input.read("CollarColor", DyeColor.LEGACY_ID_CODEC).orElse(DEFAULT_COLLAR_COLOR));
        this.readPersistentAngerSaveData(this.level(), input);
        input.read("sound_variant", ResourceKey.codec(Registries.WOLF_SOUND_VARIANT))
            .flatMap(resourceKey -> this.registryAccess().lookupOrThrow(Registries.WOLF_SOUND_VARIANT).get((ResourceKey<WolfSoundVariant>)resourceKey))
            .ifPresent(this::setSoundVariant);
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(
        ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData spawnGroupData
    ) {
        if (spawnGroupData instanceof Wolf.WolfPackData wolfPackData) {
            this.setVariant(wolfPackData.type);
        } else {
            Optional<? extends Holder<WolfVariant>> optional = VariantUtils.selectVariantToSpawn(
                SpawnContext.create(level, this.blockPosition()), Registries.WOLF_VARIANT
            );
            if (optional.isPresent()) {
                this.setVariant((Holder<WolfVariant>)optional.get());
                spawnGroupData = new Wolf.WolfPackData((Holder<WolfVariant>)optional.get());
            }
        }

        this.setSoundVariant(WolfSoundVariants.pickRandomSoundVariant(this.registryAccess(), level.getRandom()));
        return super.finalizeSpawn(level, difficulty, spawnReason, spawnGroupData);
    }

    @Override
    public SoundEvent getAmbientSound() {
        if (this.isAngry()) {
            return this.getSoundVariant().value().growlSound().value();
        } else if (this.random.nextInt(3) == 0) {
            return this.isTame() && this.getHealth() < 20.0F
                ? this.getSoundVariant().value().whineSound().value()
                : this.getSoundVariant().value().pantSound().value();
        } else {
            return this.getSoundVariant().value().ambientSound().value();
        }
    }

    @Override
    public SoundEvent getHurtSound(DamageSource damageSource) {
        return this.canArmorAbsorb(damageSource) ? SoundEvents.WOLF_ARMOR_DAMAGE : this.getSoundVariant().value().hurtSound().value();
    }

    @Override
    public SoundEvent getDeathSound() {
        return this.getSoundVariant().value().deathSound().value();
    }

    @Override
    public float getSoundVolume() {
        return 0.4F;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide() && this.isWet && !this.isShaking && !this.isPathFinding() && this.onGround()) {
            this.isShaking = true;
            this.shakeAnim = 0.0F;
            this.shakeAnimO = 0.0F;
            this.level().broadcastEntityEvent(this, EntityEvent.SHAKE_WETNESS);
        }

        if (!this.level().isClientSide()) {
            this.updatePersistentAnger((ServerLevel)this.level(), true);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.isAlive()) {
            this.interestedAngleO = this.interestedAngle;
            if (this.isInterested()) {
                this.interestedAngle = this.interestedAngle + (1.0F - this.interestedAngle) * 0.4F;
            } else {
                this.interestedAngle = this.interestedAngle + (0.0F - this.interestedAngle) * 0.4F;
            }

            if (this.isInWaterOrRain()) {
                this.isWet = true;
                if (this.isShaking && !this.level().isClientSide()) {
                    this.level().broadcastEntityEvent(this, EntityEvent.CANCEL_SHAKE_WETNESS);
                    this.cancelShake();
                }
            } else if ((this.isWet || this.isShaking) && this.isShaking) {
                if (this.shakeAnim == 0.0F) {
                    this.playSound(SoundEvents.WOLF_SHAKE, this.getSoundVolume(), (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
                    this.gameEvent(GameEvent.ENTITY_ACTION);
                }

                this.shakeAnimO = this.shakeAnim;
                this.shakeAnim += 0.05F;
                if (this.shakeAnimO >= 2.0F) {
                    this.isWet = false;
                    this.isShaking = false;
                    this.shakeAnimO = 0.0F;
                    this.shakeAnim = 0.0F;
                }

                if (this.shakeAnim > 0.4F) {
                    float f = (float)this.getY();
                    int i = (int)(Mth.sin((this.shakeAnim - 0.4F) * (float) Math.PI) * 7.0F);
                    Vec3 deltaMovement = this.getDeltaMovement();

                    for (int i1 = 0; i1 < i; i1++) {
                        float f1 = (this.random.nextFloat() * 2.0F - 1.0F) * this.getBbWidth() * 0.5F;
                        float f2 = (this.random.nextFloat() * 2.0F - 1.0F) * this.getBbWidth() * 0.5F;
                        this.level()
                            .addParticle(ParticleTypes.SPLASH, this.getX() + f1, f + 0.8F, this.getZ() + f2, deltaMovement.x, deltaMovement.y, deltaMovement.z);
                    }
                }
            }
        }
    }

    private void cancelShake() {
        this.isShaking = false;
        this.shakeAnim = 0.0F;
        this.shakeAnimO = 0.0F;
    }

    @Override
    public void die(DamageSource damageSource) {
        this.isWet = false;
        this.isShaking = false;
        this.shakeAnimO = 0.0F;
        this.shakeAnim = 0.0F;
        super.die(damageSource);
    }

    public float getWetShade(float partialTick) {
        return !this.isWet ? 1.0F : Math.min(0.75F + Mth.lerp(partialTick, this.shakeAnimO, this.shakeAnim) / 2.0F * 0.25F, 1.0F);
    }

    public float getShakeAnim(float partialTick) {
        return Mth.lerp(partialTick, this.shakeAnimO, this.shakeAnim);
    }

    public float getHeadRollAngle(float partialTick) {
        return Mth.lerp(partialTick, this.interestedAngleO, this.interestedAngle) * 0.15F * (float) Math.PI;
    }

    @Override
    public int getMaxHeadXRot() {
        return this.isInSittingPose() ? 20 : super.getMaxHeadXRot();
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        if (this.isInvulnerableTo(level, damageSource)) {
            return false;
        } else {
            if (!super.hurtServer(level, damageSource, amount)) return false; // CraftBukkit
            this.setOrderedToSit(false);
            return true; // CraftBukkit
        }
    }

    @Override
    public boolean actuallyHurt(ServerLevel level, DamageSource damageSource, float amount, org.bukkit.event.entity.EntityDamageEvent event) { // CraftBukkit - void -> boolean
        if (!this.canArmorAbsorb(damageSource)) {
            return super.actuallyHurt(level, damageSource, amount, event); // CraftBukkit
        } else {
            if (event.isCancelled()) return false; // CraftBukkit - SPIGOT-7815: if the damage was cancelled, no need to run the wolf armor behaviour
            ItemStack bodyArmorItem = this.getBodyArmorItem();
            int damageValue = bodyArmorItem.getDamageValue();
            int maxDamage = bodyArmorItem.getMaxDamage();
            bodyArmorItem.hurtAndBreak(Mth.ceil(amount), this, EquipmentSlot.BODY);
            if (Crackiness.WOLF_ARMOR.byDamage(damageValue, maxDamage) != Crackiness.WOLF_ARMOR.byDamage(this.getBodyArmorItem())) {
                this.playSound(SoundEvents.WOLF_ARMOR_CRACK);
                level.sendParticles(
                    new ItemParticleOption(ParticleTypes.ITEM, Items.ARMADILLO_SCUTE.getDefaultInstance()),
                    this.getX(),
                    this.getY() + 1.0,
                    this.getZ(),
                    20,
                    0.2,
                    0.1,
                    0.2,
                    0.1
                );
            }
        }
        return true; // CraftBukkit // Paper - return false ONLY if event was cancelled
    }

    private boolean canArmorAbsorb(DamageSource damageSource) {
        return this.getBodyArmorItem().is(Items.WOLF_ARMOR) && !damageSource.is(DamageTypeTags.BYPASSES_WOLF_ARMOR);
    }

    @Override
    protected void applyTamingSideEffects() {
        if (this.isTame()) {
            this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40.0);
            this.setHealth(this.getMaxHealth()); // CraftBukkit - 40.0 -> getMaxHealth()
        } else {
            this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(8.0);
        }
    }

    @Override
    protected void hurtArmor(DamageSource damageSource, float damageAmount) {
        this.doHurtEquipment(damageSource, damageAmount, EquipmentSlot.BODY);
    }

    @Override
    protected boolean canShearEquipment(Player player) {
        return this.isOwnedBy(player);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemInHand = player.getItemInHand(hand);
        Item item = itemInHand.getItem();
        if (this.isTame()) {
            if (this.isFood(itemInHand) && this.getHealth() < this.getMaxHealth()) {
                this.usePlayerItem(player, hand, itemInHand);
                FoodProperties foodProperties = itemInHand.get(DataComponents.FOOD);
                float f = foodProperties != null ? foodProperties.nutrition() : 1.0F;
                this.heal(2.0F * f, org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason.EATING); // CraftBukkit
                return InteractionResult.SUCCESS;
            }

            if (!(item instanceof DyeItem dyeItem && this.isOwnedBy(player))) {
                if (this.isEquippableInSlot(itemInHand, EquipmentSlot.BODY) && !this.isWearingBodyArmor() && this.isOwnedBy(player) && !this.isBaby()) {
                    this.setBodyArmorItem(itemInHand.copyWithCount(1));
                    itemInHand.consume(1, player);
                    return InteractionResult.SUCCESS;
                }

                if (this.isInSittingPose()
                    && this.isWearingBodyArmor()
                    && this.isOwnedBy(player)
                    && this.getBodyArmorItem().isDamaged()
                    && this.getBodyArmorItem().isValidRepairItem(itemInHand)) {
                    itemInHand.shrink(1);
                    this.playSound(SoundEvents.WOLF_ARMOR_REPAIR);
                    ItemStack bodyArmorItem = this.getBodyArmorItem();
                    int i = (int)(bodyArmorItem.getMaxDamage() * 0.125F);
                    bodyArmorItem.setDamageValue(Math.max(0, bodyArmorItem.getDamageValue() - i));
                    return InteractionResult.SUCCESS;
                }

                InteractionResult interactionResult = super.mobInteract(player, hand);
                if (!interactionResult.consumesAction() && this.isOwnedBy(player)) {
                    this.setOrderedToSit(!this.isOrderedToSit());
                    this.jumping = false;
                    this.navigation.stop();
                    this.setTarget(null, org.bukkit.event.entity.EntityTargetEvent.TargetReason.FORGOT_TARGET); // CraftBukkit - reason
                    return InteractionResult.SUCCESS.withoutItem();
                }

                return interactionResult;
            }

            DyeColor dyeColor = dyeItem.getDyeColor();
            if (dyeColor != this.getCollarColor()) {
                // Paper start - Add EntityDyeEvent and CollarColorable interface
                final io.papermc.paper.event.entity.EntityDyeEvent event = new io.papermc.paper.event.entity.EntityDyeEvent(this.getBukkitEntity(), org.bukkit.DyeColor.getByWoolData((byte) dyeColor.getId()), (org.bukkit.entity.Player) player.getBukkitEntity());
                if (!event.callEvent()) {
                    return InteractionResult.FAIL;
                }
                dyeColor = DyeColor.byId(event.getColor().getWoolData());
                // Paper end - Add EntityDyeEvent and CollarColorable interface
                this.setCollarColor(dyeColor);
                itemInHand.consume(1, player);
                return InteractionResult.SUCCESS;
            }
        } else if (!this.level().isClientSide() && itemInHand.is(Items.BONE) && !this.isAngry()) {
            itemInHand.consume(1, player);
            this.tryToTame(player);
            return InteractionResult.SUCCESS_SERVER;
        }

        return super.mobInteract(player, hand);
    }

    private void tryToTame(Player player) {
        if (this.random.nextInt(3) == 0 && !org.bukkit.craftbukkit.event.CraftEventFactory.callEntityTameEvent(this, player).isCancelled()) { // CraftBukkit - added event call
            this.tame(player);
            this.navigation.stop();
            this.setTarget(null);
            this.setOrderedToSit(true);
            this.level().broadcastEntityEvent(this, EntityEvent.TAMING_SUCCEEDED);
        } else {
            this.level().broadcastEntityEvent(this, EntityEvent.TAMING_FAILED);
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EntityEvent.SHAKE_WETNESS) {
            this.isShaking = true;
            this.shakeAnim = 0.0F;
            this.shakeAnimO = 0.0F;
        } else if (id == EntityEvent.CANCEL_SHAKE_WETNESS) {
            this.cancelShake();
        } else {
            super.handleEntityEvent(id);
        }
    }

    public float getTailAngle() {
        if (this.isAngry()) {
            return 1.5393804F;
        } else if (this.isTame()) {
            float maxHealth = this.getMaxHealth();
            float f = (maxHealth - this.getHealth()) / maxHealth;
            return (0.55F - f * 0.4F) * (float) Math.PI;
        } else {
            return (float) (Math.PI / 5);
        }
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(ItemTags.WOLF_FOOD);
    }

    @Override
    public int getMaxSpawnClusterSize() {
        return 8;
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
    public void startPersistentAngerTimer() {
        this.setTimeToRemainAngry(PERSISTENT_ANGER_TIME.sample(this.random));
    }

    @Override
    public @Nullable EntityReference<LivingEntity> getPersistentAngerTarget() {
        return this.persistentAngerTarget;
    }

    @Override
    public void setPersistentAngerTarget(@Nullable EntityReference<LivingEntity> target) {
        this.persistentAngerTarget = target;
    }

    public DyeColor getCollarColor() {
        return DyeColor.byId(this.entityData.get(DATA_COLLAR_COLOR));
    }

    public void setCollarColor(DyeColor color) {
        this.entityData.set(DATA_COLLAR_COLOR, color.getId());
    }

    @Override
    public @Nullable Wolf getBreedOffspring(ServerLevel level, AgeableMob partner) {
        Wolf wolf = EntityType.WOLF.create(level, EntitySpawnReason.BREEDING);
        if (wolf != null && partner instanceof Wolf wolf1) {
            if (this.random.nextBoolean()) {
                wolf.setVariant(this.getVariant());
            } else {
                wolf.setVariant(wolf1.getVariant());
            }

            if (this.isTame()) {
                wolf.setOwnerReference(this.getOwnerReference());
                wolf.setTame(true, true);
                DyeColor collarColor = this.getCollarColor();
                DyeColor collarColor1 = wolf1.getCollarColor();
                wolf.setCollarColor(DyeColor.getMixedColor(level, collarColor, collarColor1));
            }

            wolf.setSoundVariant(WolfSoundVariants.pickRandomSoundVariant(this.registryAccess(), this.random));
        }

        return wolf;
    }

    public void setIsInterested(boolean isInterested) {
        this.entityData.set(DATA_INTERESTED_ID, isInterested);
    }

    @Override
    public boolean canMate(Animal otherAnimal) {
        return otherAnimal != this
            && this.isTame()
            && otherAnimal instanceof Wolf wolf
            && wolf.isTame()
            && !wolf.isInSittingPose()
            && this.isInLove()
            && wolf.isInLove();
    }

    public boolean isInterested() {
        return this.entityData.get(DATA_INTERESTED_ID);
    }

    @Override
    public boolean wantsToAttack(LivingEntity target, LivingEntity owner) {
        if (target instanceof Creeper || target instanceof Ghast || target instanceof ArmorStand) {
            return false;
        } else {
            return target instanceof Wolf wolf
                ? !wolf.isTame() || wolf.getOwner() != owner
                : !(target instanceof Player player && owner instanceof Player player1 && !player1.canHarmPlayer(player))
                    && !(target instanceof AbstractHorse abstractHorse && abstractHorse.isTamed())
                    && !(target instanceof TamableAnimal tamableAnimal && tamableAnimal.isTame());
        }
    }

    @Override
    public boolean canBeLeashed() {
        return !this.isAngry();
    }

    @Override
    public Vec3 getLeashOffset() {
        return new Vec3(0.0, 0.6F * this.getEyeHeight(), this.getBbWidth() * 0.4F);
    }

    public static boolean checkWolfSpawnRules(
        EntityType<Wolf> entityType, LevelAccessor level, EntitySpawnReason spawnReason, BlockPos pos, RandomSource random
    ) {
        return level.getBlockState(pos.below()).is(BlockTags.WOLVES_SPAWNABLE_ON) && isBrightEnoughToSpawn(level, pos);
    }

    class WolfAvoidEntityGoal<T extends LivingEntity> extends AvoidEntityGoal<T> {
        private final Wolf wolf;

        public WolfAvoidEntityGoal(
            final Wolf wolf, final Class<T> avoidClass, final float maxDistance, final double walkSpeedModifier, final double sprintSpeedModifier
        ) {
            super(wolf, avoidClass, maxDistance, walkSpeedModifier, sprintSpeedModifier);
            this.wolf = wolf;
        }

        @Override
        public boolean canUse() {
            return super.canUse() && this.toAvoid instanceof Llama && !this.wolf.isTame() && this.avoidLlama((Llama)this.toAvoid);
        }

        private boolean avoidLlama(Llama llama) {
            return llama.getStrength() >= Wolf.this.random.nextInt(5);
        }

        @Override
        public void start() {
            Wolf.this.setTarget(null);
            super.start();
        }

        @Override
        public void tick() {
            Wolf.this.setTarget(null);
            super.tick();
        }
    }

    public static class WolfPackData extends AgeableMob.AgeableMobGroupData {
        public final Holder<WolfVariant> type;

        public WolfPackData(Holder<WolfVariant> type) {
            super(false);
            this.type = type;
        }
    }
}
