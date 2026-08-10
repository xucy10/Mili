package net.minecraft.world.entity.player;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.math.IntMath;
import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Either;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.server.players.NameAndId;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.Unit;
import net.minecraft.util.Util;
import net.minecraft.world.Container;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.nautilus.AbstractNautilus;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.warden.WardenSpawnTracker;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.entity.vehicle.minecart.MinecartCommandBlock;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.entity.JigsawBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.entity.TestBlockEntity;
import net.minecraft.world.level.block.entity.TestInstanceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import org.jspecify.annotations.Nullable;

public abstract class Player extends Avatar implements ContainerUser {
    public static final int MAX_HEALTH = 20;
    public static final int SLEEP_DURATION = 100;
    public static final int WAKE_UP_DURATION = 10;
    public static final int ENDER_SLOT_OFFSET = 200;
    public static final int HELD_ITEM_SLOT = 499;
    public static final int CRAFTING_SLOT_OFFSET = 500;
    public static final float DEFAULT_BLOCK_INTERACTION_RANGE = 4.5F;
    public static final float DEFAULT_ENTITY_INTERACTION_RANGE = 3.0F;
    private static final int CURRENT_IMPULSE_CONTEXT_RESET_GRACE_TIME_TICKS = 40;
    private static final EntityDataAccessor<Float> DATA_PLAYER_ABSORPTION_ID = SynchedEntityData.defineId(Player.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_SCORE_ID = SynchedEntityData.defineId(Player.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<OptionalInt> DATA_SHOULDER_PARROT_LEFT = SynchedEntityData.defineId(
        Player.class, EntityDataSerializers.OPTIONAL_UNSIGNED_INT
    );
    private static final EntityDataAccessor<OptionalInt> DATA_SHOULDER_PARROT_RIGHT = SynchedEntityData.defineId(
        Player.class, EntityDataSerializers.OPTIONAL_UNSIGNED_INT
    );
    private static final short DEFAULT_SLEEP_TIMER = 0;
    private static final float DEFAULT_EXPERIENCE_PROGRESS = 0.0F;
    private static final int DEFAULT_EXPERIENCE_LEVEL = 0;
    private static final int DEFAULT_TOTAL_EXPERIENCE = 0;
    private static final int NO_ENCHANTMENT_SEED = 0;
    private static final int DEFAULT_SELECTED_SLOT = 0;
    private static final int DEFAULT_SCORE = 0;
    private static final boolean DEFAULT_IGNORE_FALL_DAMAGE_FROM_CURRENT_IMPULSE = false;
    private static final int DEFAULT_CURRENT_IMPULSE_CONTEXT_RESET_GRACE_TIME = 0;
    public static final float CREATIVE_ENTITY_INTERACTION_RANGE_MODIFIER_VALUE = 2.0F;
    final Inventory inventory;
    protected PlayerEnderChestContainer enderChestInventory = new PlayerEnderChestContainer(this); // CraftBukkit - add "this" to constructor
    public final InventoryMenu inventoryMenu;
    public AbstractContainerMenu containerMenu;
    protected FoodData foodData = new FoodData();
    protected int jumpTriggerTime;
    public int takeXpDelay;
    public int sleepCounter = 0;
    protected boolean wasUnderwater;
    private final Abilities abilities = new Abilities();
    public int experienceLevel = 0;
    public int totalExperience = 0;
    public float experienceProgress = 0.0F;
    public int enchantmentSeed = 0;
    protected final float defaultFlySpeed = 0.02F;
    private int lastLevelUpTime;
    public GameProfile gameProfile;
    private boolean reducedDebugInfo;
    protected ItemStack lastItemInMainHand = ItemStack.EMPTY;
    private final ItemCooldowns cooldowns = this.createItemCooldowns();
    private Optional<GlobalPos> lastDeathLocation = Optional.empty();
    public @Nullable FishingHook fishing;
    public float hurtDir;
    public @Nullable Vec3 currentImpulseImpactPos;
    public @Nullable Entity currentExplosionCause;
    private boolean ignoreFallDamageFromCurrentImpulse = false;
    private int currentImpulseContextResetGraceTime = 0;
    public boolean affectsSpawning = true; // Paper - Affects Spawning API
    public net.kyori.adventure.util.TriState flyingFallDamage = net.kyori.adventure.util.TriState.NOT_SET; // Paper - flying fall damage

    // CraftBukkit start
    public boolean fauxSleeping;
    public int oldLevel = -1;

    @Override
    public org.bukkit.craftbukkit.entity.CraftHumanEntity getBukkitEntity() {
        return (org.bukkit.craftbukkit.entity.CraftHumanEntity) super.getBukkitEntity();
    }
    // CraftBukkit end

    public Player(Level level, GameProfile gameProfile) {
        super(EntityType.PLAYER, level);
        this.setUUID(gameProfile.id());
        this.gameProfile = gameProfile;
        this.inventory = new Inventory(this, this.equipment);
        this.inventoryMenu = new InventoryMenu(this.inventory, !level.isClientSide(), this);
        this.containerMenu = this.inventoryMenu;
    }

    @Override
    protected EntityEquipment createEquipment() {
        return new PlayerEquipment(this);
    }

    public boolean blockActionRestricted(Level level, BlockPos pos, GameType gameMode) {
        if (!gameMode.isBlockPlacingRestricted()) {
            return false;
        } else if (gameMode == GameType.SPECTATOR) {
            return true;
        } else if (this.mayBuild()) {
            return false;
        } else {
            ItemStack mainHandItem = this.getMainHandItem();
            return mainHandItem.isEmpty() || !mainHandItem.canBreakBlockInAdventureMode(new BlockInWorld(level, pos, false));
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes()
            .add(Attributes.ATTACK_DAMAGE, 1.0)
            .add(Attributes.MOVEMENT_SPEED, 0.1F)
            .add(Attributes.ATTACK_SPEED)
            .add(Attributes.LUCK)
            .add(Attributes.BLOCK_INTERACTION_RANGE, 4.5)
            .add(Attributes.ENTITY_INTERACTION_RANGE, 3.0)
            .add(Attributes.BLOCK_BREAK_SPEED)
            .add(Attributes.SUBMERGED_MINING_SPEED)
            .add(Attributes.SNEAKING_SPEED)
            .add(Attributes.MINING_EFFICIENCY)
            .add(Attributes.SWEEPING_DAMAGE_RATIO)
            .add(Attributes.WAYPOINT_TRANSMIT_RANGE, 6.0E7)
            .add(Attributes.WAYPOINT_RECEIVE_RANGE, 6.0E7);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PLAYER_ABSORPTION_ID, 0.0F);
        builder.define(DATA_SCORE_ID, 0);
        builder.define(DATA_SHOULDER_PARROT_LEFT, OptionalInt.empty());
        builder.define(DATA_SHOULDER_PARROT_RIGHT, OptionalInt.empty());
    }

    @Override
    public void tick() {
        this.noPhysics = this.isCreativeFlyOrSpectator(); // Leaves - creative no clip
        if (this.isCreativeFlyOrSpectator() || this.isPassenger()) { // Leaves - creative no clip
            this.setOnGround(false);
        }

        if (this.takeXpDelay > 0) {
            this.takeXpDelay--;
        }

        if (this.isSleeping()) {
            this.sleepCounter++;
            // Paper start - Add PlayerDeepSleepEvent
            if (this.sleepCounter == SLEEP_DURATION) {
                if (!new io.papermc.paper.event.player.PlayerDeepSleepEvent((org.bukkit.entity.Player) getBukkitEntity()).callEvent()) {
                    this.sleepCounter = Integer.MIN_VALUE;
                }
            }
            // Paper end - Add PlayerDeepSleepEvent
            if (this.sleepCounter > 100) {
                this.sleepCounter = 100;
            }

            if (!this.level().isClientSide()
                && !this.level().environmentAttributes().getValue(EnvironmentAttributes.BED_RULE, this.position()).canSleep(this.level())) {
                this.stopSleepInBed(false, true);
            }
        } else if (this.sleepCounter > 0) {
            this.sleepCounter++;
            if (this.sleepCounter >= 110) {
                this.sleepCounter = 0;
            }
        }

        this.updateIsUnderwater();
        super.tick();
        int i = 29999999;
        double d = Mth.clamp(this.getX(), -2.9999999E7, 2.9999999E7);
        double d1 = Mth.clamp(this.getZ(), -2.9999999E7, 2.9999999E7);
        if (d != this.getX() || d1 != this.getZ()) {
            this.setPos(d, this.getY(), d1);
        }

        this.attackStrengthTicker++;
        this.itemSwapTicker++;
        ItemStack mainHandItem = this.getMainHandItem();
        if (!ItemStack.matches(this.lastItemInMainHand, mainHandItem)) {
            if (!ItemStack.isSameItem(this.lastItemInMainHand, mainHandItem)) {
                if (!fun.bm.mili.config.modules.function.OldFeatureConfig.spearInstantLunge || !mainHandItem.has(DataComponents.KINETIC_WEAPON)) this.resetAttackStrengthTicker(); // Paper - diff on change (detectEquipmentUpdates override) // Leaves - spear instant lunge
            }

            this.lastItemInMainHand = mainHandItem.copy();
        }

        if (!this.isEyeInFluid(FluidTags.WATER) && this.isEquipped(Items.TURTLE_HELMET)) {
            this.turtleHelmetTick();
        }

        this.cooldowns.tick();
        this.updatePlayerPose();
        if (this.currentImpulseContextResetGraceTime > 0) {
            this.currentImpulseContextResetGraceTime--;
        }
    }

    // Leaves start - fakeplayer
    protected void livingEntityTick() {
        super.tick();
    }
    // Leaves end - fakeplayer

    @Override
    protected float getMaxHeadRotationRelativeToBody() {
        return this.isBlocking() ? 15.0F : super.getMaxHeadRotationRelativeToBody();
    }

    public boolean isSecondaryUseActive() {
        return this.isShiftKeyDown();
    }

    protected boolean wantsToStopRiding() {
        return this.isShiftKeyDown();
    }

    protected boolean isStayingOnGroundSurface() {
        return this.isShiftKeyDown();
    }

    protected boolean updateIsUnderwater() {
        this.wasUnderwater = this.isEyeInFluid(FluidTags.WATER);
        return this.wasUnderwater;
    }

    @Override
    public void onAboveBubbleColumn(boolean downwards, BlockPos pos) {
        if (!this.getAbilities().flying) {
            super.onAboveBubbleColumn(downwards, pos);
        }
    }

    @Override
    public void onInsideBubbleColumn(boolean downwards) {
        if (!this.getAbilities().flying) {
            super.onInsideBubbleColumn(downwards);
        }
    }

    private void turtleHelmetTick() {
        this.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 200, 0, false, false, true), org.bukkit.event.entity.EntityPotionEffectEvent.Cause.TURTLE_HELMET); // CraftBukkit
    }

    private boolean isEquipped(Item item) {
        for (EquipmentSlot equipmentSlot : EquipmentSlot.VALUES) {
            ItemStack itemBySlot = this.getItemBySlot(equipmentSlot);
            Equippable equippable = itemBySlot.get(DataComponents.EQUIPPABLE);
            if (itemBySlot.is(item) && equippable != null && equippable.slot() == equipmentSlot) {
                return true;
            }
        }

        return false;
    }

    protected ItemCooldowns createItemCooldowns() {
        return new ItemCooldowns();
    }

    protected void updatePlayerPose() {
        if (this.canPlayerFitWithinBlocksAndEntitiesWhen(Pose.SWIMMING)) {
            Pose desiredPose = this.getDesiredPose();
            Pose pose;
            if (this.isCreativeFlyOrSpectator() || this.isPassenger() || this.canPlayerFitWithinBlocksAndEntitiesWhen(desiredPose)) { // Leaves - creative no clip
                pose = desiredPose;
            } else if (this.canPlayerFitWithinBlocksAndEntitiesWhen(Pose.CROUCHING)) {
                pose = Pose.CROUCHING;
            } else {
                pose = Pose.SWIMMING;
            }

            this.setPose(pose);
        }
    }

    private Pose getDesiredPose() {
        if (this.isSleeping()) {
            return Pose.SLEEPING;
        } else if (this.isSwimming()) {
            return Pose.SWIMMING;
        } else if (this.isFallFlying()) {
            return Pose.FALL_FLYING;
        } else if (this.isAutoSpinAttack()) {
            return Pose.SPIN_ATTACK;
        } else {
            return this.isShiftKeyDown() && !this.abilities.flying ? Pose.CROUCHING : Pose.STANDING;
        }
    }

    protected boolean canPlayerFitWithinBlocksAndEntitiesWhen(Pose pose) {
        return this.level().noCollision(this, this.getDimensions(pose).makeBoundingBox(this.position()).deflate(1.0E-7));
    }

    @Override
    public SoundEvent getSwimSound() {
        return SoundEvents.PLAYER_SWIM;
    }

    @Override
    public SoundEvent getSwimSplashSound() {
        return SoundEvents.PLAYER_SPLASH;
    }

    @Override
    public SoundEvent getSwimHighSpeedSplashSound() {
        return SoundEvents.PLAYER_SPLASH_HIGH_SPEED;
    }

    @Override
    public int getDimensionChangingDelay() {
        return 10;
    }

    @Override
    public void playSound(SoundEvent sound, float volume, float pitch) {
        this.level().playSound(this, this.getX(), this.getY(), this.getZ(), sound, this.getSoundSource(), volume, pitch);
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.PLAYERS;
    }

    @Override
    public int getFireImmuneTicks() {
        return 20;
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EntityEvent.USE_ITEM_COMPLETE) {
            this.completeUsingItem();
        } else if (id == EntityEvent.FULL_DEBUG_INFO) {
            this.setReducedDebugInfo(false);
        } else if (id == EntityEvent.REDUCED_DEBUG_INFO) {
            this.setReducedDebugInfo(true);
        } else {
            super.handleEntityEvent(id);
        }
    }

    // Paper start - Inventory close reason; unused code, but to keep signatures aligned
    public void closeContainer(org.bukkit.event.inventory.InventoryCloseEvent.Reason reason) {
        this.closeContainer();
        this.containerMenu = this.inventoryMenu;
    }
    // Paper end - Inventory close reason
    // Paper start - special close for unloaded inventory
    public void closeUnloadedInventory(org.bukkit.event.inventory.InventoryCloseEvent.Reason reason) {
        this.containerMenu = this.inventoryMenu;
    }
    // Paper end - special close for unloaded inventory

    public void closeContainer() {
        this.containerMenu = this.inventoryMenu;
    }

    protected void doCloseContainer() {
    }

    @Override
    public void rideTick() {
        if (!this.level().isClientSide() && this.wantsToStopRiding() && this.isPassenger()) {
            this.stopRiding();
            // CraftBukkit start - SPIGOT-7316: no longer passenger, dismount and return
            if (!this.isPassenger()) {
                this.setShiftKeyDown(false);
                return;
            }
        }
        {
            // CraftBukkit end
            super.rideTick();
        }
    }

    @Override
    public void aiStep() {
        if (this.jumpTriggerTime > 0) {
            this.jumpTriggerTime--;
        }

        this.tickRegeneration();
        this.inventory.tick();
        if (this.abilities.flying && !this.isPassenger()) {
            this.resetFallDistance();
        }

        super.aiStep();
        this.updateSwingTime();
        this.yHeadRot = this.getYRot();
        this.setSpeed((float)this.getAttributeValue(Attributes.MOVEMENT_SPEED));
        if (this.getHealth() > 0.0F && !this.isCreativeFlyOrSpectator()) { // Leaves - creative no clip
            AABB aabb;
            if (this.isPassenger() && !this.getVehicle().isRemoved()) {
                aabb = this.getBoundingBox().minmax(this.getVehicle().getBoundingBox()).inflate(1.0, 0.0, 1.0);
            } else {
                aabb = this.getBoundingBox().inflate(1.0, 0.5, 1.0);
            }

            List<Entity> entities = this.level().getEntities(this, aabb);
            List<Entity> list = Lists.newArrayList();

            for (Entity entity : entities) {
                if (entity.getType() == EntityType.EXPERIENCE_ORB) {
                    list.add(entity);
                } else if (!entity.isRemoved()) {
                    this.touch(entity);
                }
            }

            if (!list.isEmpty()) {
                if (fun.bm.mili.carpet.config.modules.GeneralCompatConfig.xpNoCooldown) {
                    for (Entity entity : list) {
                        this.touch(entity);
                    }
                } else {
                    this.touch(Util.getRandom(list, this.random));
                }
            }
        }

        this.handleShoulderEntities();
    }

    protected void tickRegeneration() {
    }

    public void handleShoulderEntities() {
    }

    public void removeEntitiesOnShoulder() {
    }

    public void touch(Entity entity) { // Leaves - private -> public
        entity.playerTouch(this);
    }

    public int getScore() {
        return this.entityData.get(DATA_SCORE_ID);
    }

    public void setScore(int score) {
        this.entityData.set(DATA_SCORE_ID, score);
    }

    public void increaseScore(int score) {
        int score1 = this.getScore();
        this.entityData.set(DATA_SCORE_ID, score1 + score);
    }

    public void startAutoSpinAttack(int ticks, float damageAmount, ItemStack stack) {
        this.autoSpinAttackTicks = ticks;
        this.autoSpinAttackDmg = damageAmount;
        this.autoSpinAttackItemStack = stack;
        if (!this.level().isClientSide()) {
            this.removeEntitiesOnShoulder();
            this.setLivingEntityFlag(LivingEntity.LIVING_ENTITY_FLAG_SPIN_ATTACK, true);
        }
    }

    @Override
    public ItemStack getWeaponItem() {
        return this.isAutoSpinAttack() && this.autoSpinAttackItemStack != null ? this.autoSpinAttackItemStack : super.getWeaponItem();
    }

    @Override
    public void die(DamageSource damageSource) {
        super.die(damageSource);
        this.reapplyPosition();
        if (!this.isSpectator() && this.level() instanceof ServerLevel serverLevel) {
            this.dropAllDeathLoot(serverLevel, damageSource);
        }

        if (damageSource != null) {
            this.setDeltaMovement(
                -Mth.cos((this.getHurtDir() + this.getYRot()) * (float) (Math.PI / 180.0)) * 0.1F,
                0.1F,
                -Mth.sin((this.getHurtDir() + this.getYRot()) * (float) (Math.PI / 180.0)) * 0.1F
            );
        } else {
            this.setDeltaMovement(0.0, 0.1, 0.0);
        }

        this.awardStat(Stats.DEATHS);
        this.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_DEATH));
        this.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
        this.clearFire();
        this.setSharedFlagOnFire(false);
        this.setLastDeathLocation(Optional.of(GlobalPos.of(this.level().dimension(), this.blockPosition())));
    }

    @Override
    protected void dropEquipment(ServerLevel level) {
        super.dropEquipment(level);
        if (!level.getGameRules().get(GameRules.KEEP_INVENTORY)) {
            this.destroyVanishingCursedItems();
            this.inventory.dropAll();
        }
    }

    protected void destroyVanishingCursedItems() {
        for (int i = 0; i < this.inventory.getContainerSize(); i++) {
            ItemStack item = this.inventory.getItem(i);
            if (!item.isEmpty() && EnchantmentHelper.has(item, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) {
                this.inventory.removeItemNoUpdate(i);
            }
        }
    }

    @Override
    public SoundEvent getHurtSound(DamageSource damageSource) {
        return damageSource.type().effects().sound();
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.PLAYER_DEATH;
    }

    public void handleCreativeModeItemDrop(ItemStack stack) {
    }

    public @Nullable ItemEntity drop(ItemStack stack, boolean includeThrowerName) {
        return this.drop(stack, false, includeThrowerName);
    }

    public float getDestroySpeed(BlockState state) {
        float destroySpeed = this.inventory.getSelectedItem().getDestroySpeed(state);
        if (destroySpeed > 1.0F) {
            destroySpeed += (float)this.getAttributeValue(Attributes.MINING_EFFICIENCY);
        }

        if (MobEffectUtil.hasDigSpeed(this)) {
            destroySpeed *= 1.0F + (MobEffectUtil.getDigSpeedAmplification(this) + 1) * 0.2F;
        }

        if (this.hasEffect(MobEffects.MINING_FATIGUE)) {
            float f = switch (this.getEffect(MobEffects.MINING_FATIGUE).getAmplifier()) {
                case 0 -> 0.3F;
                case 1 -> 0.09F;
                case 2 -> 0.0027F;
                default -> 8.1E-4F;
            };
            destroySpeed *= f;
        }

        destroySpeed *= (float)this.getAttributeValue(Attributes.BLOCK_BREAK_SPEED);
        if (this.isEyeInFluid(FluidTags.WATER)) {
            destroySpeed *= (float)this.getAttribute(Attributes.SUBMERGED_MINING_SPEED).getValue();
        }

        if (!this.onGround()) {
            destroySpeed /= 5.0F;
        }

        return destroySpeed;
    }

    public boolean hasCorrectToolForDrops(BlockState state) {
        return !state.requiresCorrectToolForDrops() || this.inventory.getSelectedItem().isCorrectToolForDrops(state);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setUUID(this.gameProfile.id());
        this.inventory.load(input.listOrEmpty("Inventory", ItemStackWithSlot.CODEC));
        this.inventory.setSelectedSlot(input.getIntOr("SelectedItemSlot", 0));
        this.sleepCounter = input.getShortOr("SleepTimer", (short)0);
        this.experienceProgress = input.getFloatOr("XpP", 0.0F);
        this.experienceLevel = input.getIntOr("XpLevel", 0);
        this.totalExperience = input.getIntOr("XpTotal", 0);
        this.enchantmentSeed = input.getIntOr("XpSeed", 0);
        if (this.enchantmentSeed == 0) {
            this.enchantmentSeed = this.random.nextInt();
        }

        this.setScore(input.getIntOr("Score", 0));
        this.foodData.readAdditionalSaveData(input);
        input.read("abilities", Abilities.Packed.CODEC).ifPresent(this.abilities::apply);
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(this.abilities.getWalkingSpeed());
        this.enderChestInventory.fromSlots(input.listOrEmpty("EnderItems", ItemStackWithSlot.CODEC));
        this.setLastDeathLocation(input.read("LastDeathLocation", GlobalPos.CODEC));
        this.currentImpulseImpactPos = input.read("current_explosion_impact_pos", Vec3.CODEC).orElse(null);
        this.ignoreFallDamageFromCurrentImpulse = input.getBooleanOr("ignore_fall_damage_from_current_explosion", false);
        this.currentImpulseContextResetGraceTime = input.getIntOr("current_impulse_context_reset_grace_time", 0);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        NbtUtils.addCurrentDataVersion(output);
        this.inventory.save(output.list("Inventory", ItemStackWithSlot.CODEC));
        output.putInt("SelectedItemSlot", this.inventory.getSelectedSlot());
        output.putShort("SleepTimer", (short)this.sleepCounter);
        output.putFloat("XpP", this.experienceProgress);
        output.putInt("XpLevel", this.experienceLevel);
        output.putInt("XpTotal", this.totalExperience);
        output.putInt("XpSeed", this.enchantmentSeed);
        output.putInt("Score", this.getScore());
        this.foodData.addAdditionalSaveData(output);
        output.store("abilities", Abilities.Packed.CODEC, this.abilities.pack());
        this.enderChestInventory.storeAsSlots(output.list("EnderItems", ItemStackWithSlot.CODEC));
        this.lastDeathLocation.ifPresent(globalPos -> output.store("LastDeathLocation", GlobalPos.CODEC, globalPos));
        output.storeNullable("current_explosion_impact_pos", Vec3.CODEC, this.currentImpulseImpactPos);
        output.putBoolean("ignore_fall_damage_from_current_explosion", this.ignoreFallDamageFromCurrentImpulse);
        output.putInt("current_impulse_context_reset_grace_time", this.currentImpulseContextResetGraceTime);
    }

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource damageSource) {
        if (super.isInvulnerableTo(level, damageSource)) {
            return true;
        } else if (damageSource.is(DamageTypeTags.IS_DROWNING)) {
            return !level.getGameRules().get(GameRules.DROWNING_DAMAGE);
        } else if (damageSource.is(DamageTypeTags.IS_FALL)) {
            return !level.getGameRules().get(GameRules.FALL_DAMAGE);
        } else {
            return damageSource.is(DamageTypeTags.IS_FIRE)
                ? !level.getGameRules().get(GameRules.FIRE_DAMAGE)
                : damageSource.is(DamageTypeTags.IS_FREEZING) && !level.getGameRules().get(GameRules.FREEZE_DAMAGE);
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        if (this.isInvulnerableTo(level, damageSource)) {
            return false;
        } else if (this.abilities.invulnerable && !damageSource.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        } else {
            this.noActionTime = 0;
            if (this.isDeadOrDying()) {
                return false;
            } else {
                // this.removeEntitiesOnShoulder(); // CraftBukkit - moved down
                if (damageSource.scalesWithDifficulty()) {
                    if (level.getDifficulty() == Difficulty.PEACEFUL) {
                        return false; // CraftBukkit - f = 0.0f -> return false
                    }

                    if (level.getDifficulty() == Difficulty.EASY) {
                        amount = Math.min(amount / 2.0F + 1.0F, amount);
                    }

                    if (level.getDifficulty() == Difficulty.HARD) {
                        amount = amount * 3.0F / 2.0F;
                    }
                }

                // return amount != 0.0F && super.hurtServer(level, damageSource, amount);
                // CraftBukkit start - Don't filter out 0 damage
                boolean damaged = super.hurtServer(level, damageSource, amount);
                if (damaged) {
                    this.removeEntitiesOnShoulder();
                }
                return damaged;
                // CraftBukkit end
            }
        }
    }

    @Override
    protected void blockUsingItem(ServerLevel level, LivingEntity entity) {
        super.blockUsingItem(level, entity);
        ItemStack itemBlockingWith = this.getItemBlockingWith();
        BlocksAttacks blocksAttacks = itemBlockingWith != null ? itemBlockingWith.get(DataComponents.BLOCKS_ATTACKS) : null;
        float secondsToDisableBlocking = entity.getSecondsToDisableBlocking();
        if (secondsToDisableBlocking > 0.0F && blocksAttacks != null) {
            blocksAttacks.disable(level, this, secondsToDisableBlocking, itemBlockingWith, entity); // Paper - Add PlayerShieldDisableEvent
        }
    }

    @Override
    public boolean canBeSeenAsEnemy() {
        return !this.getAbilities().invulnerable && super.canBeSeenAsEnemy();
    }

    public boolean canHarmPlayer(Player other) {
        // CraftBukkit start - Change to check OTHER player's scoreboard team according to API
        // To summarize this method's logic, it's "Can parameter hurt this"
        org.bukkit.scoreboard.Team team;
        if (other instanceof ServerPlayer) {
            ServerPlayer thatPlayer = (ServerPlayer) other;
            team = thatPlayer.getBukkitEntity().getScoreboard().getPlayerTeam(thatPlayer.getBukkitEntity());
            if (team == null || team.allowFriendlyFire()) {
                return true;
            }
        } else {
            // This should never be called, but is implemented anyway
            org.bukkit.OfflinePlayer thisPlayer = other.level().getCraftServer().getOfflinePlayer(other.getScoreboardName());
            team = other.level().getCraftServer().getScoreboardManager().getMainScoreboard().getPlayerTeam(thisPlayer);
            if (team == null || team.allowFriendlyFire()) {
                return true;
            }
        }

        if (this instanceof ServerPlayer) {
            return !team.hasPlayer(((ServerPlayer) this).getBukkitEntity());
        }
        return !team.hasPlayer(this.level().getCraftServer().getOfflinePlayer(this.getScoreboardName()));
        // CraftBukkit end
    }

    @Override
    protected void hurtArmor(DamageSource damageSource, float damageAmount) {
        this.doHurtEquipment(damageSource, damageAmount, EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD);
    }

    @Override
    protected void hurtHelmet(DamageSource damageSource, float damageAmount) {
        this.doHurtEquipment(damageSource, damageAmount, EquipmentSlot.HEAD);
    }

    @Override
    // CraftBukkit start
    protected boolean actuallyHurt(ServerLevel level, DamageSource damageSource, float amount, org.bukkit.event.entity.EntityDamageEvent event) { // void -> boolean
        if (true) {
            return super.actuallyHurt(level, damageSource, amount, event);
        }
        // CraftBukkit end
        if (!this.isInvulnerableTo(level, damageSource)) {
            amount = this.getDamageAfterArmorAbsorb(damageSource, amount);
            amount = this.getDamageAfterMagicAbsorb(damageSource, amount);
            float var8 = Math.max(amount - this.getAbsorptionAmount(), 0.0F);
            this.setAbsorptionAmount(this.getAbsorptionAmount() - (amount - var8));
            float f1 = amount - var8;
            if (f1 > 0.0F && f1 < 3.4028235E37F) {
                this.awardStat(Stats.DAMAGE_ABSORBED, Math.round(f1 * 10.0F));
            }

            if (var8 != 0.0F) {
                this.causeFoodExhaustion(damageSource.getFoodExhaustion(), org.bukkit.event.entity.EntityExhaustionEvent.ExhaustionReason.DAMAGED); // CraftBukkit - EntityExhaustionEvent
                this.getCombatTracker().recordDamage(damageSource, var8);
                this.setHealth(this.getHealth() - var8);
                if (var8 < 3.4028235E37F) {
                    this.awardStat(Stats.DAMAGE_TAKEN, Math.round(var8 * 10.0F));
                }

                this.gameEvent(GameEvent.ENTITY_DAMAGE);
            }
        }
        return false; // CraftBukkit
    }

    public boolean isTextFilteringEnabled() {
        return false;
    }

    public void openTextEdit(SignBlockEntity signEntity, boolean isFrontText) {
    }

    public void openMinecartCommandBlock(MinecartCommandBlock minecart) {
    }

    public void openCommandBlock(CommandBlockEntity commandBlockEntity) {
    }

    public void openStructureBlock(StructureBlockEntity structureEntity) {
    }

    public void openTestBlock(TestBlockEntity testBlockEntity) {
    }

    public void openTestInstanceBlock(TestInstanceBlockEntity testInstanceBlockEntity) {
    }

    public void openJigsawBlock(JigsawBlockEntity jigsawBlockEntity) {
    }

    public void openHorseInventory(AbstractHorse horse, Container inventory) {
    }

    public void openNautilusInventory(AbstractNautilus nautilus, Container inventory) {
    }

    public OptionalInt openMenu(@Nullable MenuProvider menu) {
        return OptionalInt.empty();
    }

    public void openDialog(Holder<Dialog> dialog) {
    }

    public void sendMerchantOffers(int containerId, MerchantOffers offers, int villagerLevel, int villagerXp, boolean showProgress, boolean canRestock) {
    }

    public void openItemGui(ItemStack stack, InteractionHand hand) {
    }

    public InteractionResult interactOn(Entity entityToInteractOn, InteractionHand hand) {
        if (this.isSpectator()) {
            if (entityToInteractOn instanceof MenuProvider) {
                this.openMenu((MenuProvider)entityToInteractOn);
            }

            return InteractionResult.PASS;
        } else {
            ItemStack itemInHand = this.getItemInHand(hand);
            ItemStack itemStack = itemInHand.copy();
            InteractionResult interactionResult = entityToInteractOn.interact(this, hand);
            if (interactionResult.consumesAction()) {
                if (this.hasInfiniteMaterials() && itemInHand == this.getItemInHand(hand) && itemInHand.getCount() < itemStack.getCount()) {
                    itemInHand.setCount(itemStack.getCount());
                }

                return interactionResult;
            } else {
                if (!itemInHand.isEmpty() && entityToInteractOn instanceof LivingEntity) {
                    if (this.hasInfiniteMaterials()) {
                        itemInHand = itemStack;
                    }

                    InteractionResult interactionResult1 = itemInHand.interactLivingEntity(this, (LivingEntity)entityToInteractOn, hand);
                    if (interactionResult1.consumesAction()) {
                        this.level().gameEvent(GameEvent.ENTITY_INTERACT, entityToInteractOn.position(), GameEvent.Context.of(this));
                        if (itemInHand.isEmpty() && !this.hasInfiniteMaterials()) {
                            this.setItemInHand(hand, ItemStack.EMPTY);
                        }

                        return interactionResult1;
                    }
                }

                return InteractionResult.PASS;
            }
        }
    }

    @Override
    // Paper start - Force entity dismount during teleportation
    public void removeVehicle(boolean suppressCancellation) {
        super.removeVehicle(suppressCancellation);
        // Paper end - Force entity dismount during teleportation
        this.boardingCooldown = 0;
    }

    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || this.isSleeping() || this.isRemoved() || !valid; // Paper - players who are dead or not in a world shouldn't move...
    }

    @Override
    public boolean isAffectedByFluids() {
        return !this.abilities.flying;
    }

    @Override
    protected Vec3 maybeBackOffFromEdge(Vec3 movement, MoverType type) {
        float f = this.maxUpStep();
        if (!this.abilities.flying
            && !(movement.y > 0.0)
            && (type == MoverType.SELF || type == MoverType.PLAYER)
            && this.isStayingOnGroundSurface()
            && this.isAboveGround(f)) {
            double d = movement.x;
            double d1 = movement.z;
            double d2 = 0.05;
            double d3 = Math.signum(d) * 0.05;

            double d4;
            for (d4 = Math.signum(d1) * 0.05; d != 0.0 && this.canFallAtLeast(d, 0.0, f); d -= d3) {
                if (Math.abs(d) <= 0.05) {
                    d = 0.0;
                    break;
                }
            }

            while (d1 != 0.0 && this.canFallAtLeast(0.0, d1, f)) {
                if (Math.abs(d1) <= 0.05) {
                    d1 = 0.0;
                    break;
                }

                d1 -= d4;
            }

            while (d != 0.0 && d1 != 0.0 && this.canFallAtLeast(d, d1, f)) {
                if (Math.abs(d) <= 0.05) {
                    d = 0.0;
                } else {
                    d -= d3;
                }

                if (Math.abs(d1) <= 0.05) {
                    d1 = 0.0;
                } else {
                    d1 -= d4;
                }
            }

            return new Vec3(d, movement.y, d1);
        } else {
            return movement;
        }
    }

    private boolean isAboveGround(float maxUpStep) {
        return this.onGround() || this.fallDistance < maxUpStep && !this.canFallAtLeast(0.0, 0.0, maxUpStep - this.fallDistance);
    }

    private boolean canFallAtLeast(double x, double z, double distance) {
        AABB boundingBox = this.getBoundingBox();
        return this.level()
            .noCollision(
                this,
                new AABB(
                    boundingBox.minX + 1.0E-7 + x,
                    boundingBox.minY - distance - 1.0E-7,
                    boundingBox.minZ + 1.0E-7 + z,
                    boundingBox.maxX - 1.0E-7 + x,
                    boundingBox.minY,
                    boundingBox.maxZ - 1.0E-7 + z
                )
            );
    }

    public void attack(Entity target) {
        // Paper start - PlayerAttackEntityEvent
        boolean willAttack = !this.cannotAttack(target); // Vanilla logic
        io.papermc.paper.event.player.PrePlayerAttackEntityEvent playerAttackEntityEvent = new io.papermc.paper.event.player.PrePlayerAttackEntityEvent(
            (org.bukkit.entity.Player) this.getBukkitEntity(),
            target.getBukkitEntity(),
            willAttack
        );

        if (playerAttackEntityEvent.callEvent() && willAttack) { // Logic moved to willAttack local variable.
        // Paper end - PlayerAttackEntityEvent
            if (this.level() instanceof ServerLevel serverLevel
                && fun.bm.mili.carpet.config.modules.GeneralCompatConfig.creativeOneHitKill
                && this.isCreative()
                && net.minecraft.world.entity.EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(target)) {
                if (this.isShiftKeyDown()) {
                    for (Entity entity : this.level().getEntities(this, target.getBoundingBox().inflate(2.0D, 0.5D, 2.0D), candidate ->
                        candidate.isAttackable() && net.minecraft.world.entity.EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(candidate))) {
                        this.creativeInstantKill(serverLevel, entity);
                    }
                    this.playServerSideSound(SoundEvents.PLAYER_ATTACK_SWEEP);
                } else {
                    this.creativeInstantKill(serverLevel, target);
                    this.playServerSideSound(SoundEvents.PLAYER_ATTACK_CRIT);
                }
                return;
            }
            float f = this.isAutoSpinAttack() ? this.autoSpinAttackDmg : (float)this.getAttributeValue(Attributes.ATTACK_DAMAGE);
            ItemStack weaponItem = this.getWeaponItem();
            DamageSource damageSource = this.createAttackSource(weaponItem); final DamageSource dmgSourceFinal = damageSource; // Paper - damage events
            float attackStrengthScale = this.getAttackStrengthScale(0.5F);
            float f1 = attackStrengthScale * (this.getEnchantedDamage(target, f, damageSource) - f);
            f *= this.baseDamageScaleFactor();
            this.onAttack();
            final float dmgFinal = f1; // Paper - damage events
            if (!this.deflectProjectile(target, () -> !org.bukkit.craftbukkit.event.CraftEventFactory.handleNonLivingEntityDamageEvent(target, dmgSourceFinal, dmgFinal, false))) {
                if (f > 0.0F || f1 > 0.0F) {
                    boolean flag = attackStrengthScale > 0.9F;
                    boolean flag1;
                    if (this.isSprinting() && flag) {
                        this.playServerSideSound(SoundEvents.PLAYER_ATTACK_KNOCKBACK);
                        flag1 = true;
                    } else {
                        flag1 = false;
                    }

                    f += weaponItem.getItem().getAttackDamageBonus(target, f, damageSource);
                    boolean flag2 = flag && this.canCriticalAttack(target);
                    flag2 = flag2 && !this.level().paperConfig().entities.behavior.disablePlayerCrits; // Paper - Toggleable player crits
                    if (flag2) {
                        damageSource = damageSource.critical(); // Paper - critical damage API
                        f *= 1.5F;
                    }

                    float f2 = f + f1;
                    boolean isSweepAttack = this.isSweepAttack(flag, flag2, flag1);
                    float f3 = 0.0F;
                    if (target instanceof LivingEntity livingEntity) {
                        f3 = livingEntity.getHealth();
                    }

                    Vec3 deltaMovement = target.getDeltaMovement();
                    boolean flag3 = target.hurtOrSimulate(damageSource, f2);
                    if (flag3) {
                        this.causeExtraKnockback(target, this.getKnockback(target, damageSource) + (flag1 ? 0.5F : 0.0F), deltaMovement);
                        if (isSweepAttack) {
                            this.doSweepAttack(target, f, damageSource, attackStrengthScale);
                        }

                        this.attackVisualEffects(target, flag2, isSweepAttack, flag, false, f1);
                        this.setLastHurtMob(target);
                        this.itemAttackInteraction(target, weaponItem, damageSource, true);
                        this.damageStatsAndHearts(target, f3);
                        this.causeFoodExhaustion(this.level().spigotConfig.combatExhaustion, org.bukkit.event.entity.EntityExhaustionEvent.ExhaustionReason.ATTACK); // CraftBukkit - EntityExhaustionEvent // Spigot - Change to use configurable value
                    } else {
                        this.playServerSideSound(SoundEvents.PLAYER_ATTACK_NODAMAGE);
                    }
                }

                this.lungeForwardMaybe();
            }
        }
    }

    private void creativeInstantKill(ServerLevel level, Entity target) {
        if (target instanceof EnderDragonPart enderDragonPart) {
            java.util.Arrays.stream(enderDragonPart.parentMob.getSubEntities()).forEach(part -> part.kill(level));
            enderDragonPart.parentMob.kill(level);
            return;
        }

        target.kill(level);
    }

    private void playServerSideSound(SoundEvent sound) {
        sendSoundEffect(this, this.getX(), this.getY(), this.getZ(), sound, this.getSoundSource(), 1.0F, 1.0F); // Paper - send while respecting visibility
    }

    private DamageSource createAttackSource(ItemStack stack) {
        return stack.getDamageSource(this, () -> this.damageSources().playerAttack(this));
    }

    private boolean cannotAttack(Entity target) {
        return !target.isAttackable() || target.skipAttackInteraction(this);
    }

    private boolean deflectProjectile(Entity target, java.util.function.BooleanSupplier callEvent) { // Paper - damage events
        if (target.getType().is(EntityTypeTags.REDIRECTABLE_PROJECTILE)
            && target instanceof Projectile projectile
            && callEvent.getAsBoolean() // Paper - damage events
            && projectile.deflect(ProjectileDeflection.AIM_DEFLECT, this, EntityReference.of(this), true)) {
            this.makeSound(SoundEvents.PLAYER_ATTACK_NODAMAGE); // Paper - Use makeSound to avoid duplicating client-side sound for source player
            return true;
        } else {
            return false;
        }
    }

    private boolean canCriticalAttack(Entity target) {
        return this.fallDistance > 0.0
            && !this.onGround()
            && !this.onClimbable()
            && !this.isInWater()
            && !this.isMobilityRestricted()
            && !this.isPassenger()
            && target instanceof LivingEntity
            && !this.isSprinting();
    }

    private boolean isSweepAttack(boolean isStrong, boolean isCritical, boolean extraKnockback) {
        if (isStrong && !isCritical && !extraKnockback && this.onGround()) {
            double d = this.getKnownMovement().horizontalDistanceSqr();
            double d1 = this.getSpeed() * 2.5;
            if (d < Mth.square(d1)) {
                return this.getItemInHand(InteractionHand.MAIN_HAND).is(ItemTags.SWORDS);
            }
        }

        return false;
    }

    private void attackVisualEffects(Entity target, boolean isCritical, boolean isSweep, boolean isStrong, boolean isStab, float damageAmount) {
        if (isCritical) {
            this.playServerSideSound(SoundEvents.PLAYER_ATTACK_CRIT);
            this.crit(target);
        }

        if (!isCritical && !isSweep && !isStab) {
            this.playServerSideSound(isStrong ? SoundEvents.PLAYER_ATTACK_STRONG : SoundEvents.PLAYER_ATTACK_WEAK);
        }

        if (damageAmount > 0.0F) {
            this.magicCrit(target);
        }
    }

    private void damageStatsAndHearts(Entity target, float previousHealth) {
        if (target instanceof LivingEntity) {
            float f = previousHealth - ((LivingEntity)target).getHealth();
            this.awardStat(Stats.DAMAGE_DEALT, Math.round(f * 10.0F));
            if (this.level() instanceof ServerLevel && f > 2.0F) {
                int i = (int)(f * 0.5);
                ((ServerLevel)this.level())
                    .sendParticles(ParticleTypes.DAMAGE_INDICATOR, target.getX(), target.getY(0.5), target.getZ(), i, 0.1, 0.0, 0.1, 0.2);
            }
        }
    }

    private void itemAttackInteraction(Entity target, ItemStack stack, DamageSource damageSource, boolean postAttackEffects) {
        Entity entity = target;
        if (target instanceof EnderDragonPart) {
            entity = ((EnderDragonPart)target).parentMob;
        }

        boolean flag = false;
        if (this.level() instanceof ServerLevel serverLevel) {
            if (entity instanceof LivingEntity livingEntity) {
                flag = stack.hurtEnemy(livingEntity, this);
            }

            if (postAttackEffects) {
                EnchantmentHelper.doPostAttackEffectsWithItemSource(serverLevel, target, damageSource, stack);
            }
        }

        if (!this.level().isClientSide() && !stack.isEmpty() && entity instanceof LivingEntity) {
            if (flag) {
                stack.postHurtEnemy((LivingEntity)entity, this);
            }

            if (stack.isEmpty()) {
                if (stack == this.getMainHandItem()) {
                    this.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                } else {
                    this.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                }
            }
        }
    }

    @Override
    public void causeExtraKnockback(Entity target, float strength, Vec3 currentMovement) {
        if (strength > 0.0F) {
            if (target instanceof LivingEntity livingEntity) {
                livingEntity.knockback(strength, Mth.sin(this.getYRot() * (float) (Math.PI / 180.0)), -Mth.cos(this.getYRot() * (float) (Math.PI / 180.0)), this, io.papermc.paper.event.entity.EntityKnockbackEvent.Cause.ENTITY_ATTACK); // Paper - knockback events
            } else {
                target.push(
                    -Mth.sin(this.getYRot() * (float) (Math.PI / 180.0)) * strength, 0.1, Mth.cos(this.getYRot() * (float) (Math.PI / 180.0)) * strength
                    , this // Paper - Add EntityKnockbackByEntityEvent and EntityPushedByEntityAttackEvent
                );
            }

            this.setDeltaMovement(this.getDeltaMovement().multiply(0.6, 1.0, 0.6));
            // Paper start - Configurable sprint interruption on attack
            if (!this.level().paperConfig().misc.disableSprintInterruptionOnAttack) {
            this.setSprinting(false);
            }
            // Paper end - Configurable sprint interruption on attack
        }

        if ((target instanceof ServerPlayer && !(target instanceof org.leavesmc.leaves.bot.ServerBot)) && target.hurtMarked) { // Leaves - bot knockback
            // CraftBukkit start - Add Velocity Event
            boolean cancelled = false;
            org.bukkit.entity.Player player = (org.bukkit.entity.Player) target.getBukkitEntity();
            org.bukkit.util.Vector velocity = org.bukkit.craftbukkit.util.CraftVector.toBukkit(currentMovement);

            org.bukkit.event.player.PlayerVelocityEvent event = new org.bukkit.event.player.PlayerVelocityEvent(player, velocity.clone());
            this.level().getCraftServer().getPluginManager().callEvent(event);

            if (event.isCancelled()) {
                cancelled = true;
            } else if (!velocity.equals(event.getVelocity())) {
                player.setVelocity(event.getVelocity());
            }

            if (!cancelled) {
            ((ServerPlayer)target).connection.send(new ClientboundSetEntityMotionPacket(target));
            target.hurtMarked = false;
            target.setDeltaMovement(currentMovement);
            }
            // CraftBukkit end
        }
    }

    @Override
    public float getVoicePitch() {
        return 1.0F;
    }

    private void doSweepAttack(Entity entity, float damageAmount, DamageSource damageSource, float strengthScale) {
        this.playServerSideSound(SoundEvents.PLAYER_ATTACK_SWEEP);
        if (this.level() instanceof ServerLevel serverLevel) {
            float var12 = 1.0F + (float)this.getAttributeValue(Attributes.SWEEPING_DAMAGE_RATIO) * damageAmount;

            for (LivingEntity livingEntity : this.level().getEntitiesOfClass(LivingEntity.class, entity.getBoundingBox().inflate(1.0, 0.25, 1.0))) {
                if (livingEntity != this
                    && livingEntity != entity
                    && !this.isAlliedTo(livingEntity)
                    && !(livingEntity instanceof ArmorStand armorStand && armorStand.isMarker())
                    && this.distanceToSqr(livingEntity) < 9.0) {
                    float f1 = this.getEnchantedDamage(livingEntity, var12, damageSource) * strengthScale;
                    // Paper start - Only apply knockback if the event is not canceled
                    livingEntity.lastDamageCancelled = false;
                    if (livingEntity.hurtServer(serverLevel, damageSource.knownCause(org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK), f1) && !livingEntity.lastDamageCancelled) {
                        // Paper end - Only apply knockback if the event is not canceled
                        livingEntity.knockback(0.4F, Mth.sin(this.getYRot() * (float) (Math.PI / 180.0)), -Mth.cos(this.getYRot() * (float) (Math.PI / 180.0)), this, io.papermc.paper.event.entity.EntityKnockbackEvent.Cause.SWEEP_ATTACK); // Paper - knockback events
                        EnchantmentHelper.doPostAttackEffects(serverLevel, livingEntity, damageSource);
                    }
                }
            }

            double d = -Mth.sin(this.getYRot() * (float) (Math.PI / 180.0));
            double d1 = Mth.cos(this.getYRot() * (float) (Math.PI / 180.0));
            serverLevel.sendParticles(ParticleTypes.SWEEP_ATTACK, this.getX() + d, this.getY(0.5), this.getZ() + d1, 0, d, 0.0, d1, 0.0);
        }
    }

    protected float getEnchantedDamage(Entity entity, float damage, DamageSource damageSource) {
        return damage;
    }

    @Override
    protected void doAutoAttackOnTouch(LivingEntity target) {
        this.attack(target);
    }

    public void crit(Entity target) {
    }

    private float baseDamageScaleFactor() {
        float attackStrengthScale = this.getAttackStrengthScale(0.5F);
        return 0.2F + attackStrengthScale * attackStrengthScale * 0.8F;
    }

    @Override
    public boolean stabAttack(EquipmentSlot slot, Entity target, float damageAmount, boolean damage, boolean knockback, boolean dismount) {
        // Paper start - PlayerAttackEntityEvent
        boolean cannotAttack = this.cannotAttack(target); // Vanilla logic
        io.papermc.paper.event.player.PrePlayerAttackEntityEvent playerAttackEntityEvent = new io.papermc.paper.event.player.PrePlayerAttackEntityEvent(
            (org.bukkit.entity.Player) this.getBukkitEntity(),
            target.getBukkitEntity(),
            !cannotAttack
        );

        if (!playerAttackEntityEvent.callEvent() || cannotAttack) { // Logic moved to cannotAttack local variable.
        // Paper end - PlayerAttackEntityEvent
            return false;
        } else {
            ItemStack itemBySlot = this.getItemBySlot(slot);
            DamageSource damageSource = this.createAttackSource(itemBySlot);
            float f = this.getEnchantedDamage(target, damageAmount, damageSource) - damageAmount;
            if (!this.isUsingItem() || this.getUsedItemHand().asEquipmentSlot() != slot) {
                f *= this.getAttackStrengthScale(0.5F);
                damageAmount *= this.baseDamageScaleFactor();
            }

            final float dmgFinal = f; // Paper - damage events
            if (knockback && this.deflectProjectile(target, () -> !org.bukkit.craftbukkit.event.CraftEventFactory.handleNonLivingEntityDamageEvent(target, damageSource, dmgFinal, false))) { // Paper - damage events
                return true;
            } else {
                float f1 = damage ? damageAmount + f : 0.0F;
                float f2 = 0.0F;
                if (target instanceof LivingEntity livingEntity) {
                    f2 = livingEntity.getHealth();
                }

                Vec3 deltaMovement = target.getDeltaMovement();
                boolean flag = damage && target.hurtOrSimulate(damageSource, f1);
                if (knockback) {
                    this.causeExtraKnockback(target, 0.4F + this.getKnockback(target, damageSource), deltaMovement);
                }

                boolean flag1 = false;
                if (dismount && target.isPassenger()) {
                    flag1 = true;
                    target.stopRiding();
                }

                if (!flag && !knockback && !flag1) {
                    return false;
                } else {
                    this.attackVisualEffects(target, false, false, damage, true, f);
                    this.setLastHurtMob(target);
                    this.itemAttackInteraction(target, itemBySlot, damageSource, flag);
                    this.damageStatsAndHearts(target, f2);
                    this.causeFoodExhaustion(this.level().spigotConfig.combatExhaustion, org.bukkit.event.entity.EntityExhaustionEvent.ExhaustionReason.ATTACK); // CraftBukkit - EntityExhaustionEvent // Spigot - Change to use configurable value
                    return true;
                }
            }
        }
    }

    public void magicCrit(Entity target) {
    }

    @Override
    public void remove(Entity.RemovalReason reason, org.bukkit.event.entity.EntityRemoveEvent.@Nullable Cause eventCause) { // CraftBukkit - add Bukkit remove cause
        super.remove(reason, eventCause); // CraftBukkit - add Bukkit remove cause
        this.inventoryMenu.removed(this);
        if (this.hasContainerOpen()) {
            this.doCloseContainer();
        }
    }

    @Override
    public boolean isClientAuthoritative() {
        return true;
    }

    @Override
    protected boolean isLocalClientAuthoritative() {
        return this.isLocalPlayer();
    }

    // Folia start - region threading
    @Override
    protected void preRemove(RemovalReason reason) {
        super.preRemove(reason);
        this.fishing = null;
    }
    // Folia end - region threading

    public boolean isLocalPlayer() {
        return false;
    }

    @Override
    public boolean canSimulateMovement() {
        return !this.level().isClientSide() || this.isLocalPlayer();
    }

    @Override
    public boolean isEffectiveAi() {
        return !this.level().isClientSide() || this.isLocalPlayer();
    }

    public GameProfile getGameProfile() {
        return this.gameProfile;
    }

    public NameAndId nameAndId() {
        return new NameAndId(this.gameProfile);
    }

    public Inventory getInventory() {
        return this.inventory;
    }

    public Abilities getAbilities() {
        return this.abilities;
    }

    @Override
    public boolean hasInfiniteMaterials() {
        return this.abilities.instabuild;
    }

    public boolean preventsBlockDrops() {
        return this.abilities.instabuild;
    }

    public void updateTutorialInventoryAction(ItemStack carried, ItemStack clicked, ClickAction action) {
    }

    public boolean hasContainerOpen() {
        return this.containerMenu != this.inventoryMenu;
    }

    public boolean canDropItems() {
        return true;
    }

    // KioCG start
    @Override
    public boolean shouldTickHot() {
        return false;
    }
    // KioCG end

    public Either<Player.BedSleepingProblem, Unit> startSleepInBed(BlockPos bedPos) {
        // CraftBukkit start
        return this.startSleepInBed(bedPos, false);
    }

    public Either<Player.BedSleepingProblem, Unit> startSleepInBed(BlockPos bedPos, boolean force) {
        // CraftBukkit end
        this.startSleeping(bedPos);
        this.sleepCounter = 0;
        return Either.right(Unit.INSTANCE);
    }

    public void stopSleepInBed(boolean wakeImmediately, boolean updateLevelForSleepingPlayers) {
        super.stopSleeping();
        if (this.level() instanceof ServerLevel && updateLevelForSleepingPlayers) {
            ((ServerLevel)this.level()).updateSleepingPlayerList();
        }

        this.sleepCounter = wakeImmediately ? 0 : 100;
    }

    @Override
    public void stopSleeping() {
        this.stopSleepInBed(true, true);
    }

    public boolean isSleepingLongEnough() {
        return this.isSleeping() && this.sleepCounter >= 100;
    }

    public int getSleepTimer() {
        return this.sleepCounter;
    }

    public void displayClientMessage(Component message, boolean overlay) {
    }

    public void awardStat(Identifier statKey) {
        this.awardStat(Stats.CUSTOM.get(statKey));
    }

    public void awardStat(Identifier stat, int increment) {
        this.awardStat(Stats.CUSTOM.get(stat), increment);
    }

    public void awardStat(Stat<?> stat) {
        this.awardStat(stat, 1);
    }

    public void awardStat(Stat<?> stat, int increment) {
    }

    public void resetStat(Stat<?> stat) {
    }

    public int awardRecipes(Collection<RecipeHolder<?>> recipes) {
        return 0;
    }

    public void triggerRecipeCrafted(RecipeHolder<?> recipe, List<ItemStack> items) {
    }

    public void awardRecipesByKey(List<ResourceKey<Recipe<?>>> recipes) {
    }

    public int resetRecipes(Collection<RecipeHolder<?>> recipes) {
        return 0;
    }

    @Override
    public void travel(Vec3 travelVector) {
        if (this.isPassenger()) {
            super.travel(travelVector);
        } else {
            if (this.isSwimming()) {
                double d = this.getLookAngle().y;
                double d1 = d < -0.2 ? 0.085 : 0.06;
                if (d <= 0.0 || this.jumping || !this.level().getFluidState(BlockPos.containing(this.getX(), this.getY() + 1.0 - 0.1, this.getZ())).isEmpty()) {
                    Vec3 deltaMovement = this.getDeltaMovement();
                    this.setDeltaMovement(deltaMovement.add(0.0, (d - deltaMovement.y) * d1, 0.0));
                }
            }

            if (this.getAbilities().flying) {
                double d = this.getDeltaMovement().y;
                super.travel(travelVector);
                this.setDeltaMovement(this.getDeltaMovement().with(Direction.Axis.Y, d * 0.6));
            } else {
                super.travel(travelVector);
            }
        }
    }

    @Override
    protected boolean canGlide() {
        return !this.abilities.flying && super.canGlide();
    }

    @Override
    public void updateSwimming() {
        if (this.abilities.flying) {
            this.setSwimming(false);
        } else {
            super.updateSwimming();
        }
    }

    protected boolean freeAt(BlockPos pos) {
        return !this.level().getBlockState(pos).isSuffocating(this.level(), pos);
    }

    @Override
    public float getSpeed() {
        return (float)this.getAttributeValue(Attributes.MOVEMENT_SPEED);
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageMultiplier, DamageSource damageSource) {
        if (this.abilities.mayfly && !this.flyingFallDamage.toBooleanOrElse(false)) { // Paper - flying fall damage
            return false;
        } else {
            if (fallDistance >= 2.0) {
                this.awardStat(Stats.FALL_ONE_CM, (int)Math.round(fallDistance * 100.0));
            }

            boolean flag = this.currentImpulseImpactPos != null && this.ignoreFallDamageFromCurrentImpulse;
            double min;
            if (flag) {
                min = Math.min(fallDistance, this.currentImpulseImpactPos.y - this.getY());
                boolean flag1 = min <= 0.0;
                if (flag1) {
                    this.resetCurrentImpulseContext();
                } else {
                    this.tryResetCurrentImpulseContext();
                }
            } else {
                min = fallDistance;
            }

            if (min > 0.0 && super.causeFallDamage(min, damageMultiplier, damageSource)) {
                this.resetCurrentImpulseContext();
                return true;
            } else {
                this.propagateFallToPassengers(fallDistance, damageMultiplier, damageSource);
                return false;
            }
        }
    }

    public boolean tryToStartFallFlying() {
        if (!this.isFallFlying() && this.canGlide() && !this.isInWater()) {
            this.startFallFlying();
            return true;
        } else {
            return false;
        }
    }

    public void startFallFlying() {
        // CraftBukkit start
        if (!org.bukkit.craftbukkit.event.CraftEventFactory.callToggleGlideEvent(this, true).isCancelled()) {
            this.setSharedFlag(Entity.FLAG_FALL_FLYING, true);
        } else {
            // SPIGOT-5542: must toggle like below
            this.setSharedFlag(Entity.FLAG_FALL_FLYING, true);
            this.setSharedFlag(Entity.FLAG_FALL_FLYING, false);
        }
        // CraftBukkit end
    }

    @Override
    protected void doWaterSplashEffect() {
        if (!this.isSpectator()) {
            super.doWaterSplashEffect();
        }
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        if (this.isInWater()) {
            this.waterSwimSound();
            this.playMuffledStepSound(state);
        } else {
            BlockPos primaryStepSoundBlockPos = this.getPrimaryStepSoundBlockPos(pos);
            if (!pos.equals(primaryStepSoundBlockPos)) {
                BlockState blockState = this.level().getBlockState(primaryStepSoundBlockPos);
                if (blockState.is(BlockTags.COMBINATION_STEP_SOUND_BLOCKS)) {
                    this.playCombinationStepSounds(blockState, state);
                } else {
                    super.playStepSound(primaryStepSoundBlockPos, blockState);
                }
            } else {
                super.playStepSound(pos, state);
            }
        }
    }

    @Override
    public LivingEntity.Fallsounds getFallSounds() {
        return new LivingEntity.Fallsounds(SoundEvents.PLAYER_SMALL_FALL, SoundEvents.PLAYER_BIG_FALL);
    }

    @Override
    public boolean killedEntity(ServerLevel level, LivingEntity entity, DamageSource damageSource) {
        this.awardStat(Stats.ENTITY_KILLED.get(entity.getType()));
        return true;
    }

    @Override
    public void makeStuckInBlock(BlockState state, Vec3 motionMultiplier) {
        if (!this.abilities.flying) {
            super.makeStuckInBlock(state, motionMultiplier);
        }

        this.tryResetCurrentImpulseContext();
    }

    public void giveExperiencePoints(int xpPoints) {
        this.increaseScore(xpPoints);
        this.experienceProgress = this.experienceProgress + (float)xpPoints / this.getXpNeededForNextLevel();
        this.totalExperience = Mth.clamp(this.totalExperience + xpPoints, 0, Integer.MAX_VALUE);

        while (this.experienceProgress < 0.0F) {
            float f = this.experienceProgress * this.getXpNeededForNextLevel();
            if (this.experienceLevel > 0) {
                this.giveExperienceLevels(-1);
                this.experienceProgress = 1.0F + f / this.getXpNeededForNextLevel();
            } else {
                this.giveExperienceLevels(-1);
                this.experienceProgress = 0.0F;
            }
        }

        while (this.experienceProgress >= 1.0F) {
            this.experienceProgress = (this.experienceProgress - 1.0F) * this.getXpNeededForNextLevel();
            this.giveExperienceLevels(1);
            this.experienceProgress = this.experienceProgress / this.getXpNeededForNextLevel();
        }
    }

    public int getEnchantmentSeed() {
        return this.enchantmentSeed;
    }

    public void onEnchantmentPerformed(ItemStack enchantedItem, int levelCost) {
        this.experienceLevel -= levelCost;
        if (this.experienceLevel < 0) {
            this.experienceLevel = 0;
            this.experienceProgress = 0.0F;
            this.totalExperience = 0;
        }

        this.enchantmentSeed = this.random.nextInt();
    }

    public void giveExperienceLevels(int levels) {
        this.experienceLevel = IntMath.saturatedAdd(this.experienceLevel, levels);
        if (this.experienceLevel < 0) {
            this.experienceLevel = 0;
            this.experienceProgress = 0.0F;
            this.totalExperience = 0;
        }

        if (levels > 0 && this.experienceLevel % 5 == 0 && this.lastLevelUpTime < this.tickCount - 100.0F) {
            float f = this.experienceLevel > 30 ? 1.0F : this.experienceLevel / 30.0F;
            Player.sendSoundEffect(this, this.getX(), this.getY(), this.getZ(), SoundEvents.PLAYER_LEVELUP, this.getSoundSource(), f * 0.75F, 1.0F); // Paper - send while respecting visibility
            this.lastLevelUpTime = this.tickCount;
        }
    }

    public int getXpNeededForNextLevel() {
        if (this.experienceLevel >= 30) {
            return 112 + (this.experienceLevel - 30) * 9;
        } else { // Paper - diff on change; calculateTotalExperiencePoints
            return this.experienceLevel >= 15 ? 37 + (this.experienceLevel - 15) * 5 : 7 + this.experienceLevel * 2;
        }
    }

    // Paper start - send while respecting visibility
    private static void sendSoundEffect(Player fromEntity, double x, double y, double z, SoundEvent soundEffect, SoundSource soundCategory, float volume, float pitch) {
        fromEntity.level().playSound(fromEntity, x, y, z, soundEffect, soundCategory, volume, pitch); // This will not send the effect to the entity itself
        if (fromEntity instanceof ServerPlayer serverPlayer) {
            serverPlayer.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.wrapAsHolder(soundEffect), soundCategory, x, y, z, volume, pitch, fromEntity.random.nextLong()));
        }
    }
    // Paper end - send while respecting visibility

    public void causeFoodExhaustion(float exhaustion) {
        // CraftBukkit start
        this.causeFoodExhaustion(exhaustion, org.bukkit.event.entity.EntityExhaustionEvent.ExhaustionReason.UNKNOWN);
    }

    public void causeFoodExhaustion(float exhaustion, org.bukkit.event.entity.EntityExhaustionEvent.ExhaustionReason reason) {
        // CraftBukkit end
        if (!this.abilities.invulnerable) {
            if (!this.level().isClientSide()) {
                // CraftBukkit start
                org.bukkit.event.entity.EntityExhaustionEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerExhaustionEvent(this, reason, exhaustion);
                if (!event.isCancelled()) {
                    this.foodData.addExhaustion(event.getExhaustion());
                }
                // CraftBukkit end
            }
        }
    }

    @Override
    public void lungeForwardMaybe() {
        if (this.hasEnoughFoodToDoExhaustiveManoeuvres()) {
            super.lungeForwardMaybe();
        }
    }

    protected boolean hasEnoughFoodToDoExhaustiveManoeuvres() {
        return this.getFoodData().hasEnoughFood() || this.getAbilities().mayfly;
    }

    public Optional<WardenSpawnTracker> getWardenSpawnTracker() {
        return Optional.empty();
    }

    public FoodData getFoodData() {
        return this.foodData;
    }

    public boolean canEat(boolean canAlwaysEat) {
        return this.abilities.invulnerable || canAlwaysEat || this.foodData.needsFood();
    }

    public boolean isHurt() {
        return this.getHealth() > 0.0F && this.getHealth() < this.getMaxHealth();
    }

    public boolean mayBuild() {
        return this.abilities.mayBuild;
    }

    public boolean mayUseItemAt(BlockPos pos, Direction facing, ItemStack stack) {
        if (this.abilities.mayBuild) {
            return true;
        } else {
            BlockPos blockPos = pos.relative(facing.getOpposite());
            BlockInWorld blockInWorld = new BlockInWorld(this.level(), blockPos, false);
            return stack.canPlaceOnBlockInAdventureMode(blockInWorld);
        }
    }

    @Override
    protected int getBaseExperienceReward(ServerLevel level) {
        return !level.getGameRules().get(GameRules.KEEP_INVENTORY) && !this.isSpectator() ? Math.min(this.experienceLevel * 7, 100) : 0;
    }

    @Override
    protected boolean isAlwaysExperienceDropper() {
        return true;
    }

    @Override
    public boolean shouldShowName() {
        return true;
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return this.abilities.flying || this.onGround() && this.isDiscrete() ? Entity.MovementEmission.NONE : Entity.MovementEmission.ALL;
    }

    public void onUpdateAbilities() {
    }

    @Override
    public Component getName() {
        return Component.literal(this.gameProfile.name());
    }

    @Override
    public String getPlainTextName() {
        return this.gameProfile.name();
    }

    public PlayerEnderChestContainer getEnderChestInventory() {
        return this.enderChestInventory;
    }

    @Override
    protected boolean doesEmitEquipEvent(EquipmentSlot slot) {
        return slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR;
    }

    public boolean addItem(ItemStack stack) {
        return this.inventory.add(stack);
    }

    public abstract @Nullable GameType gameMode();

    @Override
    public boolean isSpectator() {
        return this.gameMode() == GameType.SPECTATOR;
    }

    // Leaves start - creative no clip
    public boolean isCreativeFlyOrSpectator() {
        return isSpectator() || (fun.bm.mili.config.modules.function.CreativeFlyNoClipConfig.enabled && isCreative() && getAbilities().flying);
    }

    public boolean canSpectatingPlace(net.minecraft.world.level.LevelReader world, BlockState state, BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
        if (this.isCreativeFlyOrSpectator()) {
            net.minecraft.world.phys.shapes.VoxelShape voxelShape = state.getCollisionShape(world, pos, context);
            return voxelShape.isEmpty() || world.isUnobstructed(this, voxelShape.move(pos.getX(), pos.getY(), pos.getZ()));
        } else {
            return world.isUnobstructed(state, pos, context);
        }
    }

    @Override
    public boolean isCollidable(boolean ignoreClimbing) {
        return !isCreativeFlyOrSpectator() && super.isCollidable(ignoreClimbing);
    }
    // Leaves end - creative no clip

    @Override
    public boolean canBeHitByProjectile() {
        return !this.isCreativeFlyOrSpectator() && super.canBeHitByProjectile(); // Leaves - creative no clip
    }

    @Override
    public boolean isSwimming() {
        return !this.abilities.flying && !this.isSpectator() && super.isSwimming();
    }

    public boolean isCreative() {
        return this.gameMode() == GameType.CREATIVE;
    }

    @Override
    public boolean isPushedByFluid() {
        return !this.abilities.flying;
    }

    @Override
    public Component getDisplayName() {
        MutableComponent mutableComponent = PlayerTeam.formatNameForTeam(this.getTeam(), this.getName());
        return this.decorateDisplayNameComponent(mutableComponent);
    }

    private MutableComponent decorateDisplayNameComponent(MutableComponent displayName) {
        String string = this.getGameProfile().name();
        return displayName.withStyle(
            style -> style.withClickEvent(new ClickEvent.SuggestCommand("/tell " + string + " ")).withHoverEvent(this.createHoverEvent()).withInsertion(string)
        );
    }

    @Override
    public String getScoreboardName() {
        return this.getGameProfile().name();
    }

    @Override
    protected void internalSetAbsorptionAmount(float absorptionAmount) {
        this.getEntityData().set(DATA_PLAYER_ABSORPTION_ID, absorptionAmount);
    }

    @Override
    public float getAbsorptionAmount() {
        return this.getEntityData().get(DATA_PLAYER_ABSORPTION_ID);
    }

    @Override
    public @Nullable SlotAccess getSlot(int slot) {
        if (slot == 499) {
            return new SlotAccess() {
                @Override
                public ItemStack get() {
                    return Player.this.containerMenu.getCarried();
                }

                @Override
                public boolean set(ItemStack carried) {
                    Player.this.containerMenu.setCarried(carried);
                    return true;
                }
            };
        } else {
            final int i = slot - 500;
            if (i >= 0 && i < 4) {
                return new SlotAccess() {
                    @Override
                    public ItemStack get() {
                        return Player.this.inventoryMenu.getCraftSlots().getItem(i);
                    }

                    @Override
                    public boolean set(ItemStack carried) {
                        Player.this.inventoryMenu.getCraftSlots().setItem(i, carried);
                        Player.this.inventoryMenu.slotsChanged(Player.this.inventory);
                        return true;
                    }
                };
            } else if (slot >= 0 && slot < this.inventory.getNonEquipmentItems().size()) {
                return this.inventory.getSlot(slot);
            } else {
                int i1 = slot - 200;
                return i1 >= 0 && i1 < this.enderChestInventory.getContainerSize() ? this.enderChestInventory.getSlot(i1) : super.getSlot(slot);
            }
        }
    }

    public boolean isReducedDebugInfo() {
        return this.reducedDebugInfo;
    }

    public void setReducedDebugInfo(boolean reducedDebugInfo) {
        this.reducedDebugInfo = reducedDebugInfo;
    }

    @Override
    public void setRemainingFireTicks(int ticks) {
        super.setRemainingFireTicks(this.abilities.invulnerable ? Math.min(ticks, 1) : ticks);
    }

    protected static Optional<Parrot.Variant> extractParrotVariant(CompoundTag tag) {
        if (!tag.isEmpty()) {
            EntityType<?> entityType = tag.read("id", EntityType.CODEC).orElse(null);
            if (entityType == EntityType.PARROT) {
                return tag.read("Variant", Parrot.Variant.LEGACY_CODEC);
            }
        }

        return Optional.empty();
    }

    protected static OptionalInt convertParrotVariant(Optional<Parrot.Variant> variant) {
        return variant.<OptionalInt>map(variant1 -> OptionalInt.of(variant1.getId())).orElse(OptionalInt.empty());
    }

    private static Optional<Parrot.Variant> convertParrotVariant(OptionalInt variantId) {
        return variantId.isPresent() ? Optional.of(Parrot.Variant.byId(variantId.getAsInt())) : Optional.empty();
    }

    public void setShoulderParrotLeft(Optional<Parrot.Variant> variant) {
        this.entityData.set(DATA_SHOULDER_PARROT_LEFT, convertParrotVariant(variant));
    }

    public Optional<Parrot.Variant> getShoulderParrotLeft() {
        return convertParrotVariant(this.entityData.get(DATA_SHOULDER_PARROT_LEFT));
    }

    public void setShoulderParrotRight(Optional<Parrot.Variant> variant) {
        this.entityData.set(DATA_SHOULDER_PARROT_RIGHT, convertParrotVariant(variant));
    }

    public Optional<Parrot.Variant> getShoulderParrotRight() {
        return convertParrotVariant(this.entityData.get(DATA_SHOULDER_PARROT_RIGHT));
    }

    public float getCurrentItemAttackStrengthDelay() {
        return (float)(1.0 / this.getAttributeValue(Attributes.ATTACK_SPEED) * 20.0);
    }

    public boolean cannotAttackWithItem(ItemStack stack, int adjustTicks) {
        float orDefault = stack.getOrDefault(DataComponents.MINIMUM_ATTACK_CHARGE, 0.0F);
        float f = (this.attackStrengthTicker + adjustTicks) / this.getCurrentItemAttackStrengthDelay();
        return orDefault > 0.0F && f < orDefault;
    }

    public float getAttackStrengthScale(float adjustTicks) {
        return Mth.clamp((this.attackStrengthTicker + adjustTicks) / this.getCurrentItemAttackStrengthDelay(), 0.0F, 1.0F);
    }

    public float getItemSwapScale(float adjustTicks) {
        return Mth.clamp((this.itemSwapTicker + adjustTicks) / this.getCurrentItemAttackStrengthDelay(), 0.0F, 1.0F);
    }

    public void resetAttackStrengthTicker() {
        this.attackStrengthTicker = 0;
        this.itemSwapTicker = 0;
    }

    @Override
    public void onAttack() {
        // this.resetOnlyAttackStrengthTicker(); // CraftBukkit - Moved to EntityLiving to reset the cooldown after the damage is dealt
        super.onAttack();
    }

    // Paper start - Force update attributes.
    @Override
    public void detectEquipmentUpdates() {
        super.detectEquipmentUpdates();

        if (!io.papermc.paper.configuration.GlobalConfiguration.get().unsupportedSettings.updateEquipmentOnPlayerActions) {
            return;
        }

        // Copied from the tick method
        ItemStack mainHandItem = this.getMainHandItem();
        if (!ItemStack.matches(this.lastItemInMainHand, mainHandItem)) {
            if (!ItemStack.isSameItem(this.lastItemInMainHand, mainHandItem)) {
                if (!fun.bm.mili.config.modules.function.OldFeatureConfig.spearInstantLunge || !mainHandItem.has(DataComponents.KINETIC_WEAPON)) this.resetAttackStrengthTicker(); // Leaves - spear instant lunge
            }

            this.lastItemInMainHand = mainHandItem.copy();
        }
    }
    // Paper end - Force update attributes.

    public void resetOnlyAttackStrengthTicker() {
        this.attackStrengthTicker = 0;
    }

    public ItemCooldowns getCooldowns() {
        return this.cooldowns;
    }

    @Override
    protected float getBlockSpeedFactor() {
        return !this.abilities.flying && !this.isFallFlying() ? super.getBlockSpeedFactor() : 1.0F;
    }

    @Override
    public float getLuck() {
        return (float)this.getAttributeValue(Attributes.LUCK);
    }

    public boolean canUseGameMasterBlocks() {
        return this.abilities.instabuild && this.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    public PermissionSet permissions() {
        return PermissionSet.NO_PERMISSIONS;
    }

    @Override
    public ImmutableList<Pose> getDismountPoses() {
        return ImmutableList.of(Pose.STANDING, Pose.CROUCHING, Pose.SWIMMING);
    }

    // Paper start - PlayerReadyArrowEvent
    // We pass a result mutable boolean in to allow the caller of this method to know if the event was cancelled.
    protected boolean tryReadyArrow(ItemStack bow, ItemStack itemstack, final org.apache.commons.lang3.mutable.MutableBoolean cancelled) {
        if (!(this instanceof final ServerPlayer serverPlayer)) return true;
        final boolean notCancelled = new com.destroystokyo.paper.event.player.PlayerReadyArrowEvent(
            serverPlayer.getBukkitEntity(),
            org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(bow),
            org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(itemstack)
        ).callEvent();
        if (!notCancelled) cancelled.setValue(true);
        return notCancelled;
    }
    // Paper end - PlayerReadyArrowEvent

    @Override
    public ItemStack getProjectile(ItemStack shootable) {
        if (!(shootable.getItem() instanceof ProjectileWeaponItem)) {
            return ItemStack.EMPTY;
        } else {
            final org.apache.commons.lang3.mutable.MutableBoolean anyEventCancelled = new org.apache.commons.lang3.mutable.MutableBoolean(); // Paper - PlayerReadyArrowEvent
            Predicate<ItemStack> supportedHeldProjectiles = ((ProjectileWeaponItem)shootable.getItem()).getSupportedHeldProjectiles().and(item -> this.tryReadyArrow(shootable, item, anyEventCancelled)); // Paper - PlayerReadyArrowEvent
            ItemStack heldProjectile = ProjectileWeaponItem.getHeldProjectile(this, supportedHeldProjectiles);
            if (!heldProjectile.isEmpty()) {
                return heldProjectile;
            } else {
                supportedHeldProjectiles = ((ProjectileWeaponItem)shootable.getItem()).getAllSupportedProjectiles().and(item -> this.tryReadyArrow(shootable, item, anyEventCancelled)); // Paper - PlayerReadyArrowEvent

                for (int i = 0; i < this.inventory.getContainerSize(); i++) {
                    ItemStack item = this.inventory.getItem(i);
                    if (supportedHeldProjectiles.test(item)) {
                        return item;
                    }
                }

                // Mili start - bow infinity fix
                if ((fun.bm.mili.config.modules.function.OldFeatureConfig.bowInfinityFix || anyEventCancelled.booleanValue()) && !this.abilities.instabuild && this instanceof final ServerPlayer player) this.resyncUsingItem(player); // Paper - resync if no item matched the Predicate
                return this.hasInfiniteMaterials() || (fun.bm.mili.config.modules.function.OldFeatureConfig.bowInfinityFix && net.minecraft.world.item.enchantment.EnchantmentHelper.processAmmoUse((ServerLevel) this.level(), shootable, new ItemStack(Items.ARROW), 1) <= 0) ? new ItemStack(Items.ARROW) : ItemStack.EMPTY;
                // Mili end - bow infinity fix
            }
        }
    }

    @Override
    public Vec3 getRopeHoldPosition(float partialTick) {
        double d = 0.22 * (this.getMainArm() == HumanoidArm.RIGHT ? -1.0 : 1.0);
        float f = Mth.lerp(partialTick * 0.5F, this.getXRot(), this.xRotO) * (float) (Math.PI / 180.0);
        float f1 = Mth.lerp(partialTick, this.yBodyRotO, this.yBodyRot) * (float) (Math.PI / 180.0);
        if (this.isFallFlying() || this.isAutoSpinAttack()) {
            Vec3 viewVector = this.getViewVector(partialTick);
            Vec3 deltaMovement = this.getDeltaMovement();
            double d1 = deltaMovement.horizontalDistanceSqr();
            double d2 = viewVector.horizontalDistanceSqr();
            float f2;
            if (d1 > 0.0 && d2 > 0.0) {
                double d3 = (deltaMovement.x * viewVector.x + deltaMovement.z * viewVector.z) / Math.sqrt(d1 * d2);
                double d4 = deltaMovement.x * viewVector.z - deltaMovement.z * viewVector.x;
                f2 = (float)(Math.signum(d4) * Math.acos(d3));
            } else {
                f2 = 0.0F;
            }

            return this.getPosition(partialTick).add(new Vec3(d, -0.11, 0.85).zRot(-f2).xRot(-f).yRot(-f1));
        } else if (this.isVisuallySwimming()) {
            return this.getPosition(partialTick).add(new Vec3(d, 0.2, -0.15).xRot(-f).yRot(-f1));
        } else {
            double d5 = this.getBoundingBox().getYsize() - 1.0;
            double d1 = this.isCrouching() ? -0.2 : 0.07;
            return this.getPosition(partialTick).add(new Vec3(d, d5, d1).yRot(-f1));
        }
    }

    @Override
    public boolean isAlwaysTicking() {
        return true;
    }

    public boolean isScoping() {
        return this.isUsingItem() && this.getUseItem().is(Items.SPYGLASS);
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    public Optional<GlobalPos> getLastDeathLocation() {
        return this.lastDeathLocation;
    }

    public void setLastDeathLocation(Optional<GlobalPos> lastDeathLocation) {
        this.lastDeathLocation = lastDeathLocation;
    }

    @Override
    public float getHurtDir() {
        return this.hurtDir;
    }

    @Override
    public void animateHurt(float yaw) {
        super.animateHurt(yaw);
        this.hurtDir = yaw;
    }

    public boolean isMobilityRestricted() {
        return this.hasEffect(MobEffects.BLINDNESS);
    }

    @Override
    public boolean canSprint() {
        return true;
    }

    @Override
    protected float getFlyingSpeed() {
        if (this.abilities.flying && !this.isPassenger()) {
            return this.isSprinting() ? this.abilities.getFlyingSpeed() * 2.0F : this.abilities.getFlyingSpeed();
        } else {
            return this.isSprinting() ? 0.025999999F : 0.02F;
        }
    }

    @Override
    public boolean hasContainerOpen(ContainerOpenersCounter openersCounter, BlockPos pos) {
        return openersCounter.isOwnContainer(this);
    }

    @Override
    public double getContainerInteractionRange() {
        return this.blockInteractionRange();
    }

    public double blockInteractionRange() {
        return this.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
    }

    public double entityInteractionRange() {
        return this.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
    }

    public boolean isWithinEntityInteractionRange(Entity entity, double buffer) {
        return !entity.isRemoved() && this.isWithinEntityInteractionRange(entity.getBoundingBox(), buffer);
    }

    public boolean isWithinEntityInteractionRange(AABB aabb, double buffer) {
        double d = this.entityInteractionRange() + buffer;
        double d1 = aabb.distanceToSqr(this.getEyePosition());
        return d1 < d * d;
    }

    public boolean isWithinAttackRange(AABB aabb, double buffer) {
        return this.entityAttackRange().isInRange(this, aabb, buffer);
    }

    public boolean isWithinBlockInteractionRange(BlockPos pos, double buffer) {
        double d = this.blockInteractionRange() + buffer;
        return new AABB(pos).distanceToSqr(this.getEyePosition()) < d * d;
    }

    public void setIgnoreFallDamageFromCurrentImpulse(boolean ignoreFallDamageFromCurrentImpulse) {
        this.ignoreFallDamageFromCurrentImpulse = ignoreFallDamageFromCurrentImpulse;
        if (ignoreFallDamageFromCurrentImpulse) {
            this.applyPostImpulseGraceTime(40);
        } else {
            this.currentImpulseContextResetGraceTime = 0;
        }
    }

    public void applyPostImpulseGraceTime(int ticks) {
        this.currentImpulseContextResetGraceTime = Math.max(this.currentImpulseContextResetGraceTime, ticks);
    }

    public boolean isIgnoringFallDamageFromCurrentImpulse() {
        return this.ignoreFallDamageFromCurrentImpulse;
    }

    public void tryResetCurrentImpulseContext() {
        if (this.currentImpulseContextResetGraceTime == 0) {
            this.resetCurrentImpulseContext();
        }
    }

    public boolean isInPostImpulseGraceTime() {
        return this.currentImpulseContextResetGraceTime > 0;
    }

    public void resetCurrentImpulseContext() {
        this.currentImpulseContextResetGraceTime = 0;
        this.currentExplosionCause = null;
        this.currentImpulseImpactPos = null;
        this.ignoreFallDamageFromCurrentImpulse = false;
    }

    public boolean shouldRotateWithMinecart() {
        return false;
    }

    @Override
    public boolean onClimbable() {
        return !this.abilities.flying && super.onClimbable();
    }

    public String debugInfo() {
        return MoreObjects.toStringHelper(this)
            .add("name", this.getPlainTextName())
            .add("id", this.getId())
            .add("pos", this.position())
            .add("mode", this.gameMode())
            .add("permission", this.permissions())
            .toString();
    }

    public record BedSleepingProblem(@Nullable Component message) {
        public static final Player.BedSleepingProblem TOO_FAR_AWAY = new Player.BedSleepingProblem(Component.translatable("block.minecraft.bed.too_far_away"));
        public static final Player.BedSleepingProblem OBSTRUCTED = new Player.BedSleepingProblem(Component.translatable("block.minecraft.bed.obstructed"));
        public static final Player.BedSleepingProblem OTHER_PROBLEM = new Player.BedSleepingProblem(null);
        public static final Player.BedSleepingProblem NOT_SAFE = new Player.BedSleepingProblem(Component.translatable("block.minecraft.bed.not_safe"));
        public static final Player.BedSleepingProblem EXPLOSION = new Player.BedSleepingProblem(null); // Paper - Added to properly handle explosions in bed events
    }
}
