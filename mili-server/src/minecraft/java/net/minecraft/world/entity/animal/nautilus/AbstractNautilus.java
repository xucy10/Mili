package net.minecraft.world.entity.animal.nautilus;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HasCustomInventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.SmoothSwimmingLookControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.AbstractMountInventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public abstract class AbstractNautilus extends TamableAnimal implements HasCustomInventoryScreen, PlayerRideableJumping {
    public static final int INVENTORY_SLOT_OFFSET = 500;
    public static final int INVENTORY_ROWS = 3;
    public static final int SMALL_RESTRICTION_RADIUS = 16;
    public static final int LARGE_RESTRICTION_RADIUS = 32;
    public static final int RESTRICTION_RADIUS_BUFFER = 8;
    private static final int EFFECT_DURATION = 60;
    private static final int EFFECT_REFRESH_RATE = 40;
    private static final double NAUTILUS_WATER_RESISTANCE = 0.9;
    private static final float IN_WATER_SPEED_MODIFIER = 0.011F;
    private static final float RIDDEN_SPEED_MODIFIER_IN_WATER = 0.0325F;
    private static final float RIDDEN_SPEED_MODIFIER_ON_LAND = 0.02F;
    private static final EntityDataAccessor<Boolean> DASH = SynchedEntityData.defineId(AbstractNautilus.class, EntityDataSerializers.BOOLEAN);
    private static final int DASH_COOLDOWN_TICKS = 40;
    private static final int DASH_MINIMUM_DURATION_TICKS = 5;
    private static final float DASH_MOMENTUM_IN_WATER = 1.2F;
    private static final float DASH_MOMENTUM_ON_LAND = 0.5F;
    private int dashCooldown = 0;
    protected float playerJumpPendingScale;
    public SimpleContainer inventory;
    private static final double BUBBLE_SPREAD_FACTOR = 0.8;
    private static final double BUBBLE_DIRECTION_SCALE = 1.1;
    private static final double BUBBLE_Y_OFFSET = 0.25;
    private static final double BUBBLE_PROBABILITY_MULTIPLIER = 2.0;
    private static final float BUBBLE_PROBABILITY_MIN = 0.15F;
    private static final float BUBBLE_PROBABILITY_MAX = 1.0F;

    protected AbstractNautilus(EntityType<? extends AbstractNautilus> type, Level level) {
        super(type, level);
        this.moveControl = new SmoothSwimmingMoveControl(this, 85, 10, 0.011F, 0.0F, true);
        this.lookControl = new SmoothSwimmingLookControl(this, 10);
        this.setPathfindingMalus(PathType.WATER, 0.0F);
        this.createInventory();
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return !this.isTame() && !this.isBaby() ? stack.is(ItemTags.NAUTILUS_TAMING_ITEMS) : stack.is(ItemTags.NAUTILUS_FOOD);
    }

    @Override
    protected void usePlayerItem(Player player, InteractionHand hand, ItemStack stack) {
        if (stack.is(ItemTags.NAUTILUS_BUCKET_FOOD)) {
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.WATER_BUCKET)));
        } else {
            super.usePlayerItem(player, hand, stack);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes()
            .add(Attributes.MAX_HEALTH, 15.0)
            .add(Attributes.MOVEMENT_SPEED, 1.0)
            .add(Attributes.ATTACK_DAMAGE, 3.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0.3F);
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new WaterBoundPathNavigation(this, level);
    }

    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        return 0.0F;
    }

    public static boolean checkNautilusSpawnRules(
        EntityType<? extends AbstractNautilus> type, LevelAccessor level, EntitySpawnReason spawnReason, BlockPos pos, RandomSource random
    ) {
        int seaLevel = level.getSeaLevel();
        int i = seaLevel - 25;
        // Paper start - Make water animal spawn height configurable
        seaLevel = level.getMinecraftWorld().paperConfig().entities.spawning.wateranimalSpawnHeight.maximum.or(seaLevel - 5);
        i = level.getMinecraftWorld().paperConfig().entities.spawning.wateranimalSpawnHeight.minimum.or(i);
        // Paper end - Make water animal spawn height configurable
        return pos.getY() >= i
            && pos.getY() <= seaLevel // Paper - Make water animal spawn height configurable
            && level.getFluidState(pos.below()).is(FluidTags.WATER)
            && level.getBlockState(pos.above()).is(Blocks.WATER);
    }

    @Override
    public boolean checkSpawnObstruction(LevelReader level) {
        return level.isUnobstructed(this);
    }

    @Override
    public boolean canUseSlot(EquipmentSlot slot) {
        return slot != EquipmentSlot.SADDLE && slot != EquipmentSlot.BODY ? super.canUseSlot(slot) : this.isAlive() && !this.isBaby() && this.isTame();
    }

    @Override
    protected boolean canDispenserEquipIntoSlot(EquipmentSlot slot) {
        return slot == EquipmentSlot.BODY || slot == EquipmentSlot.SADDLE || super.canDispenserEquipIntoSlot(slot);
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return !this.isVehicle();
    }

    @Override
    public @Nullable LivingEntity getControllingPassenger() {
        return (LivingEntity)(this.isSaddled() && this.getFirstPassenger() instanceof Player player ? player : super.getControllingPassenger());
    }

    @Override
    protected Vec3 getRiddenInput(Player player, Vec3 travelVector) {
        float f = player.xxa;
        float f1 = 0.0F;
        float f2 = 0.0F;
        if (player.zza != 0.0F) {
            float cos = Mth.cos(player.getXRot() * (float) (Math.PI / 180.0));
            float f3 = -Mth.sin(player.getXRot() * (float) (Math.PI / 180.0));
            if (player.zza < 0.0F) {
                cos *= -0.5F;
                f3 *= -0.5F;
            }

            f2 = f3;
            f1 = cos;
        }

        return new Vec3(f, f2, f1);
    }

    protected Vec2 getRiddenRotation(LivingEntity entity) {
        return new Vec2(entity.getXRot() * 0.5F, entity.getYRot());
    }

    @Override
    protected void tickRidden(Player player, Vec3 travelVector) {
        super.tickRidden(player, travelVector);
        Vec2 riddenRotation = this.getRiddenRotation(player);
        float yRot = this.getYRot();
        float f = Mth.wrapDegrees(riddenRotation.y - yRot);
        float f1 = 0.5F;
        yRot += f * 0.5F;
        this.setRot(yRot, riddenRotation.x);
        this.yRotO = this.yBodyRot = this.yHeadRot = yRot;
        if (this.isLocalInstanceAuthoritative()) {
            if (this.playerJumpPendingScale > 0.0F && !this.isJumping()) {
                this.executeRidersJump(this.playerJumpPendingScale, player);
            }

            this.playerJumpPendingScale = 0.0F;
        }
    }

    @Override
    protected void travelInWater(Vec3 travelVector, double gravity, boolean isFalling, double previousY) {
        float speed = this.getSpeed();
        this.moveRelative(speed, travelVector);
        this.move(MoverType.SELF, this.getDeltaMovement());
        this.setDeltaMovement(this.getDeltaMovement().scale(0.9));
    }

    @Override
    protected float getRiddenSpeed(Player player) {
        return this.isInWater()
            ? 0.0325F * (float)this.getAttributeValue(Attributes.MOVEMENT_SPEED)
            : 0.02F * (float)this.getAttributeValue(Attributes.MOVEMENT_SPEED);
    }

    protected void doPlayerRide(Player player) {
        if (!this.level().isClientSide()) {
            player.startRiding(this);
            if (!this.isVehicle()) {
                this.clearHome();
            }
        }
    }

    private int getNautilusRestrictionRadius() {
        return !this.isBaby() && this.getItemBySlot(EquipmentSlot.SADDLE).isEmpty() ? 32 : 16;
    }

    protected void checkRestriction() {
        if (!this.isLeashed() && !this.isVehicle() && this.isTame()) {
            int nautilusRestrictionRadius = this.getNautilusRestrictionRadius();
            if (!this.hasHome()
                || !this.getHomePosition().closerThan(this.blockPosition(), nautilusRestrictionRadius + 8)
                || nautilusRestrictionRadius != this.getHomeRadius()) {
                this.setHomeTo(this.blockPosition(), nautilusRestrictionRadius);
            }
        }
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        this.checkRestriction();
        super.customServerAiStep(level);
    }

    private void applyEffects(Level level) {
        if (this.getFirstPassenger() instanceof Player player) {
            boolean hasEffect = player.hasEffect(MobEffects.BREATH_OF_THE_NAUTILUS);
            boolean flag = level.getGameTime() % 40L == 0L;
            if (!hasEffect || flag) {
                player.addEffect(new MobEffectInstance(MobEffects.BREATH_OF_THE_NAUTILUS, 60, 0, true, true, true), org.bukkit.event.entity.EntityPotionEffectEvent.Cause.NAUTILUS); // Paper - Add cause
            }
        }
    }

    private void spawnBubbles() {
        double len = this.getDeltaMovement().length();
        double d = Mth.clamp(len * 2.0, 0.15F, 1.0);
        if (this.random.nextFloat() < d) {
            float yRot = this.getYRot();
            float f = Mth.clamp(this.getXRot(), -10.0F, 10.0F);
            Vec3 vec3 = this.calculateViewVector(f, yRot);
            double d1 = this.random.nextDouble() * 0.8 * (1.0 + len);
            double d2 = (this.random.nextFloat() - 0.5) * d1;
            double d3 = (this.random.nextFloat() - 0.5) * d1;
            double d4 = (this.random.nextFloat() - 0.5) * d1;
            this.level().addParticle(ParticleTypes.BUBBLE, this.getX() - vec3.x * 1.1, this.getY() - vec3.y + 0.25, this.getZ() - vec3.z * 1.1, d2, d3, d4);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide()) {
            this.applyEffects(this.level());
        }

        if (this.isDashing() && this.dashCooldown < 35) {
            this.setDashing(false);
        }

        if (this.dashCooldown > 0) {
            this.dashCooldown--;
            if (this.dashCooldown == 0) {
                this.makeSound(this.getDashReadySound());
            }
        }

        if (this.isInWater()) {
            this.spawnBubbles();
        }
    }

    @Override
    public boolean canJump() {
        return this.isSaddled();
    }

    @Override
    public void onPlayerJump(int jumpPower) {
        if (this.isSaddled() && this.dashCooldown <= 0) {
            this.playerJumpPendingScale = this.getPlayerJumpPendingScale(jumpPower);
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DASH, false);
    }

    public boolean isDashing() {
        return this.entityData.get(DASH);
    }

    public void setDashing(boolean dash) {
        this.entityData.set(DASH, dash);
    }

    protected void executeRidersJump(float playerJumpPendingScale, Player player) {
        this.addDeltaMovement(
            player.getLookAngle()
                .scale(
                    (this.isInWater() ? 1.2F : 0.5F) * playerJumpPendingScale * this.getAttributeValue(Attributes.MOVEMENT_SPEED) * this.getBlockSpeedFactor()
                )
        );
        this.dashCooldown = 40;
        this.setDashing(true);
        this.needsSync = true;
    }

    @Override
    public void handleStartJump(int jumpPower) {
        this.makeSound(this.getDashSound());
        this.gameEvent(GameEvent.ENTITY_ACTION);
        this.setDashing(true);
    }

    @Override
    public int getJumpCooldown() {
        return this.dashCooldown;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        if (!this.firstTick && DASH.equals(key)) {
            this.dashCooldown = this.dashCooldown == 0 ? 40 : this.dashCooldown;
        }

        super.onSyncedDataUpdated(key);
    }

    @Override
    public void handleStopJump() {
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
    }

    protected @Nullable SoundEvent getDashSound() {
        return null;
    }

    protected @Nullable SoundEvent getDashReadySound() {
        return null;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        this.setPersistenceRequired();
        return super.interact(player, hand);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemInHand = player.getItemInHand(hand);
        if (this.isBaby()) {
            return super.mobInteract(player, hand);
        } else if (this.isTame() && player.isSecondaryUseActive()) {
            this.openCustomInventoryScreen(player);
            return InteractionResult.SUCCESS;
        } else {
            if (!itemInHand.isEmpty()) {
                if (!this.level().isClientSide() && !this.isTame() && this.isFood(itemInHand)) {
                    this.usePlayerItem(player, hand, itemInHand);
                    this.tryToTame(player);
                    return InteractionResult.SUCCESS_SERVER;
                }

                if (this.isFood(itemInHand) && this.getHealth() < this.getMaxHealth()) {
                    FoodProperties foodProperties = itemInHand.get(DataComponents.FOOD);
                    this.heal(foodProperties != null ? 2 * foodProperties.nutrition() : 1.0F, org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason.EATING); // Paper - Add regain reason
                    this.usePlayerItem(player, hand, itemInHand);
                    this.playEatingSound();
                    return InteractionResult.SUCCESS;
                }

                InteractionResult interactionResult = itemInHand.interactLivingEntity(player, this, hand);
                if (interactionResult.consumesAction()) {
                    return interactionResult;
                }
            }

            if (this.isTame() && !player.isSecondaryUseActive() && !this.isFood(itemInHand)) {
                this.doPlayerRide(player);
                return InteractionResult.SUCCESS;
            } else {
                return super.mobInteract(player, hand);
            }
        }
    }

    private void tryToTame(Player player) {
        if (this.random.nextInt(3) == 0 && !org.bukkit.craftbukkit.event.CraftEventFactory.callEntityTameEvent(this, player).isCancelled()) { // CraftBukkit - added event call
            this.tame(player);
            this.navigation.stop();
            this.level().broadcastEntityEvent(this, EntityEvent.TAMING_SUCCEEDED);
        } else {
            this.level().broadcastEntityEvent(this, EntityEvent.TAMING_FAILED);
        }

        this.playEatingSound();
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return true;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        boolean flag = super.hurtServer(level, damageSource, amount);
        if (flag && damageSource.getEntity() instanceof LivingEntity livingEntity) {
            NautilusAi.setAngerTarget(level, this, livingEntity);
        }

        return flag;
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effectInstance) {
        return effectInstance.getEffect() != MobEffects.POISON && super.canBeAffected(effectInstance);
    }

    @Override
    public SpawnGroupData finalizeSpawn(
        ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData spawnGroupData
    ) {
        RandomSource random = level.getRandom();
        NautilusAi.initMemories(this, random);
        return super.finalizeSpawn(level, difficulty, spawnReason, spawnGroupData);
    }

    @Override
    protected Holder<SoundEvent> getEquipSound(EquipmentSlot slot, ItemStack stack, Equippable equippable) {
        if (slot == EquipmentSlot.SADDLE && this.isUnderWater()) {
            return SoundEvents.NAUTILUS_SADDLE_UNDERWATER_EQUIP;
        } else {
            return (Holder<SoundEvent>)(slot == EquipmentSlot.SADDLE ? SoundEvents.NAUTILUS_SADDLE_EQUIP : super.getEquipSound(slot, stack, equippable));
        }
    }

    public final int getInventorySize() {
        return AbstractMountInventoryMenu.getInventorySize(this.getInventoryColumns());
    }

    protected void createInventory() {
        SimpleContainer simpleContainer = this.inventory;
        this.inventory = new SimpleContainer(this.getInventorySize(), (org.bukkit.entity.AbstractNautilus) this.getBukkitEntity()); // Paper - add owner
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
    public void openCustomInventoryScreen(Player player) {
        if (!this.level().isClientSide() && (!this.isVehicle() || this.hasPassenger(player)) && this.isTame()) {
            player.openNautilusInventory(this, this.inventory);
        }
    }

    @Override
    public @Nullable SlotAccess getSlot(int slot) {
        int i = slot - 500;
        return i >= 0 && i < this.inventory.getContainerSize() ? this.inventory.getSlot(i) : super.getSlot(slot);
    }

    public boolean hasInventoryChanged(Container oldInventory) {
        return this.inventory != oldInventory;
    }

    public int getInventoryColumns() {
        return 0;
    }

    protected boolean isMobControlled() {
        return this.getFirstPassenger() instanceof Mob;
    }

    protected boolean isAggravated() {
        return this.getBrain().hasMemoryValue(MemoryModuleType.ANGRY_AT) || this.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET);
    }
}
