package net.minecraft.world.entity.monster.piglin;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Dynamic;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class Piglin extends AbstractPiglin implements CrossbowAttackMob, InventoryCarrier {
    private static final EntityDataAccessor<Boolean> DATA_BABY_ID = SynchedEntityData.defineId(Piglin.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_IS_CHARGING_CROSSBOW = SynchedEntityData.defineId(Piglin.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_IS_DANCING = SynchedEntityData.defineId(Piglin.class, EntityDataSerializers.BOOLEAN);
    private static final Identifier SPEED_MODIFIER_BABY_ID = Identifier.withDefaultNamespace("baby");
    private static final AttributeModifier SPEED_MODIFIER_BABY = new AttributeModifier(
        SPEED_MODIFIER_BABY_ID, 0.2F, AttributeModifier.Operation.ADD_MULTIPLIED_BASE
    );
    private static final int MAX_HEALTH = 16;
    private static final float MOVEMENT_SPEED_WHEN_FIGHTING = 0.35F;
    private static final int ATTACK_DAMAGE = 5;
    private static final float CHANCE_OF_WEARING_EACH_ARMOUR_ITEM = 0.1F;
    private static final int MAX_PASSENGERS_ON_ONE_HOGLIN = 3;
    private static final float PROBABILITY_OF_SPAWNING_AS_BABY = 0.2F;
    private static final EntityDimensions BABY_DIMENSIONS = EntityType.PIGLIN.getDimensions().scale(0.5F).withEyeHeight(0.97F);
    private static final double PROBABILITY_OF_SPAWNING_WITH_CROSSBOW_INSTEAD_OF_SWORD = 0.5;
    private static final boolean DEFAULT_IS_BABY = false;
    private static final boolean DEFAULT_CANNOT_HUNT = false;
    public final SimpleContainer inventory = new SimpleContainer(8);
    public boolean cannotHunt = false;
    protected static final ImmutableList<SensorType<? extends Sensor<? super Piglin>>> SENSOR_TYPES = ImmutableList.of(
        SensorType.NEAREST_LIVING_ENTITIES, SensorType.NEAREST_PLAYERS, SensorType.NEAREST_ITEMS, SensorType.HURT_BY, SensorType.PIGLIN_SPECIFIC_SENSOR
    );
    protected static final ImmutableList<MemoryModuleType<?>> MEMORY_TYPES = ImmutableList.of(
        MemoryModuleType.LOOK_TARGET,
        MemoryModuleType.DOORS_TO_CLOSE,
        MemoryModuleType.NEAREST_LIVING_ENTITIES,
        MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
        MemoryModuleType.NEAREST_VISIBLE_PLAYER,
        MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER,
        MemoryModuleType.NEAREST_VISIBLE_ADULT_PIGLINS,
        MemoryModuleType.NEARBY_ADULT_PIGLINS,
        MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM,
        MemoryModuleType.ITEM_PICKUP_COOLDOWN_TICKS,
        MemoryModuleType.HURT_BY,
        MemoryModuleType.HURT_BY_ENTITY,
        MemoryModuleType.WALK_TARGET,
        MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE,
        MemoryModuleType.ATTACK_TARGET,
        MemoryModuleType.ATTACK_COOLING_DOWN,
        MemoryModuleType.INTERACTION_TARGET,
        MemoryModuleType.PATH,
        MemoryModuleType.ANGRY_AT,
        MemoryModuleType.UNIVERSAL_ANGER,
        MemoryModuleType.AVOID_TARGET,
        MemoryModuleType.ADMIRING_ITEM,
        MemoryModuleType.TIME_TRYING_TO_REACH_ADMIRE_ITEM,
        MemoryModuleType.ADMIRING_DISABLED,
        MemoryModuleType.DISABLE_WALK_TO_ADMIRE_ITEM,
        MemoryModuleType.CELEBRATE_LOCATION,
        MemoryModuleType.DANCING,
        MemoryModuleType.HUNTED_RECENTLY,
        MemoryModuleType.NEAREST_VISIBLE_BABY_HOGLIN,
        MemoryModuleType.NEAREST_VISIBLE_NEMESIS,
        MemoryModuleType.NEAREST_VISIBLE_ZOMBIFIED,
        MemoryModuleType.RIDE_TARGET,
        MemoryModuleType.VISIBLE_ADULT_PIGLIN_COUNT,
        MemoryModuleType.VISIBLE_ADULT_HOGLIN_COUNT,
        MemoryModuleType.NEAREST_VISIBLE_HUNTABLE_HOGLIN,
        MemoryModuleType.NEAREST_TARGETABLE_PLAYER_NOT_WEARING_GOLD,
        MemoryModuleType.NEAREST_PLAYER_HOLDING_WANTED_ITEM,
        MemoryModuleType.ATE_RECENTLY,
        MemoryModuleType.NEAREST_REPELLENT,
        MemoryModuleType.SPEAR_FLEEING_TIME,
        MemoryModuleType.SPEAR_FLEEING_POSITION,
        MemoryModuleType.SPEAR_CHARGE_POSITION,
        MemoryModuleType.SPEAR_ENGAGE_TIME,
        MemoryModuleType.SPEAR_STATUS
    );
    // CraftBukkit start - Custom bartering and interest list
    public java.util.Set<Item> allowedBarterItems = new java.util.HashSet<>();
    public java.util.Set<Item> interestItems = new java.util.HashSet<>();
    private static final com.mojang.serialization.Codec<java.util.Set<net.minecraft.world.item.Item>> ITEM_SET_CODEC = net.minecraft.core.registries.BuiltInRegistries.ITEM
        .byNameCodec().listOf().xmap(java.util.HashSet::new, List::copyOf);
    // CraftBukkit end

    public Piglin(EntityType<? extends AbstractPiglin> type, Level level) {
        super(type, level);
        this.xpReward = 5;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("IsBaby", this.isBaby());
        output.putBoolean("CannotHunt", this.cannotHunt);
        this.writeInventoryToTag(output);
        // CraftBukkit start
        output.store("Bukkit.BarterList", ITEM_SET_CODEC, this.allowedBarterItems);
        output.store("Bukkit.InterestList", ITEM_SET_CODEC, this.interestItems);
        // CraftBukkit end
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setBaby(input.getBooleanOr("IsBaby", false));
        this.setCannotHunt(input.getBooleanOr("CannotHunt", false));
        this.readInventoryFromTag(input);
        // CraftBukkit start
        this.allowedBarterItems = input.read("Bukkit.BarterList", ITEM_SET_CODEC).orElseGet(java.util.HashSet::new);
        this.interestItems = input.read("Bukkit.InterestList", ITEM_SET_CODEC).orElseGet(java.util.HashSet::new);
        // CraftBukkit end
    }

    @VisibleForDebug
    @Override
    public SimpleContainer getInventory() {
        return this.inventory;
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource damageSource, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, damageSource, recentlyHit);
        this.inventory.removeAllItems().forEach(itemStack -> this.spawnAtLocation(level, itemStack));
    }

    protected ItemStack addToInventory(ItemStack stack) {
        return this.inventory.addItem(stack);
    }

    protected boolean canAddToInventory(ItemStack stack) {
        return this.inventory.canAddItem(stack);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_BABY_ID, false);
        builder.define(DATA_IS_CHARGING_CROSSBOW, false);
        builder.define(DATA_IS_DANCING, false);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_BABY_ID.equals(key)) {
            this.refreshDimensions();
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 16.0).add(Attributes.MOVEMENT_SPEED, 0.35F).add(Attributes.ATTACK_DAMAGE, 5.0);
    }

    public static boolean checkPiglinSpawnRules(
        EntityType<Piglin> entityType, LevelAccessor level, EntitySpawnReason spawnReason, BlockPos pos, RandomSource random
    ) {
        return !level.getBlockState(pos.below()).is(Blocks.NETHER_WART_BLOCK);
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(
        ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData spawnGroupData
    ) {
        RandomSource random = level.getRandom();
        if (spawnReason != EntitySpawnReason.STRUCTURE) {
            if (random.nextFloat() < 0.2F) {
                this.setBaby(true);
            } else if (this.isAdult()) {
                this.setItemSlot(EquipmentSlot.MAINHAND, this.createSpawnWeapon());
            }
        }

        PiglinAi.initMemories(this, level.getRandom());
        this.populateDefaultEquipmentSlots(random, difficulty);
        this.populateDefaultEquipmentEnchantments(level, random, difficulty);
        return super.finalizeSpawn(level, difficulty, spawnReason, spawnGroupData);
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return !this.isPersistenceRequired();
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        if (this.isAdult()) {
            this.maybeWearArmor(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET), random);
            this.maybeWearArmor(EquipmentSlot.CHEST, new ItemStack(Items.GOLDEN_CHESTPLATE), random);
            this.maybeWearArmor(EquipmentSlot.LEGS, new ItemStack(Items.GOLDEN_LEGGINGS), random);
            this.maybeWearArmor(EquipmentSlot.FEET, new ItemStack(Items.GOLDEN_BOOTS), random);
        }
    }

    private void maybeWearArmor(EquipmentSlot slot, ItemStack stack, RandomSource random) {
        if (random.nextFloat() < 0.1F) {
            this.setItemSlot(slot, stack);
        }
    }

    @Override
    protected Brain.Provider<Piglin> brainProvider() {
        return Brain.provider(MEMORY_TYPES, SENSOR_TYPES);
    }

    @Override
    protected Brain<?> makeBrain(Dynamic<?> dynamic) {
        return PiglinAi.makeBrain(this, this.brainProvider().makeBrain(dynamic));
    }

    @Override
    public Brain<Piglin> getBrain() {
        return (Brain<Piglin>)super.getBrain();
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        InteractionResult interactionResult = super.mobInteract(player, hand);
        if (interactionResult.consumesAction()) {
            return interactionResult;
        } else if (this.level() instanceof ServerLevel serverLevel) {
            return PiglinAi.mobInteract(serverLevel, this, player, hand);
        } else {
            boolean flag = PiglinAi.canAdmire(this, player.getItemInHand(hand)) && this.getArmPose() != PiglinArmPose.ADMIRING_ITEM;
            return (InteractionResult)(flag ? InteractionResult.SUCCESS : InteractionResult.PASS);
        }
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        return this.isBaby() ? BABY_DIMENSIONS : super.getDefaultDimensions(pose);
    }

    @Override
    public void setBaby(boolean childZombie) {
        this.getEntityData().set(DATA_BABY_ID, childZombie);
        if (!this.level().isClientSide()) {
            AttributeInstance attribute = this.getAttribute(Attributes.MOVEMENT_SPEED);
            attribute.removeModifier(SPEED_MODIFIER_BABY.id());
            if (childZombie) {
                attribute.addTransientModifier(SPEED_MODIFIER_BABY);
            }
        }
    }

    @Override
    public boolean isBaby() {
        return this.getEntityData().get(DATA_BABY_ID);
    }

    private void setCannotHunt(boolean cannotHunt) {
        this.cannotHunt = cannotHunt;
    }

    @Override
    protected boolean canHunt() {
        return !this.cannotHunt;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        ProfilerFiller profilerFiller = Profiler.get();
        profilerFiller.push("piglinBrain");
        this.getBrain().tick(level, this);
        profilerFiller.pop();
        PiglinAi.updateActivity(this);
        super.customServerAiStep(level);
    }

    @Override
    protected int getBaseExperienceReward(ServerLevel level) {
        return this.xpReward;
    }

    @Override
    protected void finishConversion(ServerLevel level) {
        PiglinAi.cancelAdmiring(level, this);
        this.forceDrops = true; // Paper - Add missing forceDrop toggles
        this.inventory.removeAllItems().forEach(itemStack -> this.spawnAtLocation(level, itemStack));
        this.forceDrops = false; // Paper - Add missing forceDrop toggles
        super.finishConversion(level);
    }

    private ItemStack createSpawnWeapon() {
        return this.random.nextFloat() < 0.5
            ? new ItemStack(Items.CROSSBOW)
            : new ItemStack(this.random.nextInt(10) == 0 ? Items.GOLDEN_SPEAR : Items.GOLDEN_SWORD);
    }

    @Override
    public @Nullable TagKey<Item> getPreferredWeaponType() {
        return this.isBaby() ? null : ItemTags.PIGLIN_PREFERRED_WEAPONS;
    }

    public boolean isChargingCrossbow() {
        return this.entityData.get(DATA_IS_CHARGING_CROSSBOW);
    }

    @Override
    public void setChargingCrossbow(boolean isCharging) {
        this.entityData.set(DATA_IS_CHARGING_CROSSBOW, isCharging);
    }

    @Override
    public void onCrossbowAttackPerformed() {
        this.noActionTime = 0;
    }

    @Override
    public PiglinArmPose getArmPose() {
        if (this.isDancing()) {
            return PiglinArmPose.DANCING;
        } else if (PiglinAi.isLovedItem(this.getOffhandItem())) {
            return PiglinArmPose.ADMIRING_ITEM;
        } else if (this.isAggressive() && this.isHoldingMeleeWeapon()) {
            return PiglinArmPose.ATTACKING_WITH_MELEE_WEAPON;
        } else if (this.isChargingCrossbow()) {
            return PiglinArmPose.CROSSBOW_CHARGE;
        } else {
            return this.isHolding(Items.CROSSBOW) && CrossbowItem.isCharged(this.getWeaponItem()) ? PiglinArmPose.CROSSBOW_HOLD : PiglinArmPose.DEFAULT;
        }
    }

    public boolean isDancing() {
        return this.entityData.get(DATA_IS_DANCING);
    }

    public void setDancing(boolean dancing) {
        this.entityData.set(DATA_IS_DANCING, dancing);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        boolean flag = super.hurtServer(level, damageSource, amount);
        if (flag && damageSource.getEntity() instanceof LivingEntity livingEntity) {
            PiglinAi.wasHurtBy(level, this, livingEntity);
        }

        return flag;
    }

    @Override
    public void performRangedAttack(LivingEntity target, float distanceFactor) {
        this.performCrossbowAttack(this, 1.6F);
    }

    @Override
    public boolean canUseNonMeleeWeapon(ItemStack stack) {
        return stack.getItem() == Items.CROSSBOW || stack.has(DataComponents.KINETIC_WEAPON);
    }

    protected void holdInMainHand(ItemStack stack) {
        this.setItemSlotAndDropWhenKilled(EquipmentSlot.MAINHAND, stack);
    }

    protected void holdInOffHand(ItemStack stack) {
        if (stack.is(PiglinAi.BARTERING_ITEM) || this.allowedBarterItems.contains(stack.getItem())) { // CraftBukkit - Changes to accept custom payment items
            this.setItemSlot(EquipmentSlot.OFFHAND, stack);
            this.setGuaranteedDrop(EquipmentSlot.OFFHAND);
        } else {
            this.setItemSlotAndDropWhenKilled(EquipmentSlot.OFFHAND, stack);
        }
    }

    @Override
    public boolean wantsToPickUp(ServerLevel level, ItemStack stack) {
        return level.getGameRules().get(GameRules.MOB_GRIEFING) && this.canPickUpLoot() && PiglinAi.wantsToPickup(this, stack);
    }

    protected boolean canReplaceCurrentItem(ItemStack candidate) {
        EquipmentSlot equipmentSlotForItem = this.getEquipmentSlotForItem(candidate);
        ItemStack itemBySlot = this.getItemBySlot(equipmentSlotForItem);
        return this.canReplaceCurrentItem(candidate, itemBySlot, equipmentSlotForItem);
    }

    @Override
    protected boolean canReplaceCurrentItem(ItemStack newItem, ItemStack currentItem, EquipmentSlot slot) {
        if (EnchantmentHelper.has(currentItem, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE)) {
            return false;
        } else {
            TagKey<Item> preferredWeaponType = this.getPreferredWeaponType();
            boolean flag = PiglinAi.isLovedItem(newItem, this) || preferredWeaponType != null && newItem.is(preferredWeaponType); // CraftBukkit
            boolean flag1 = PiglinAi.isLovedItem(currentItem, this) || preferredWeaponType != null && currentItem.is(preferredWeaponType); // CraftBukkit
            return flag && !flag1 || (flag || !flag1) && super.canReplaceCurrentItem(newItem, currentItem, slot);
        }
    }

    @Override
    protected void pickUpItem(ServerLevel level, ItemEntity entity) {
        // this.onItemPickup(entity); // Paper - EntityPickupItemEvent fixes; call in PiglinAi#pickUpItem after EntityPickupItemEvent is fired
        PiglinAi.pickUpItem(level, this, entity);
    }

    @Override
    public boolean startRiding(Entity entity, boolean force, boolean triggerEvents) {
        if (this.isBaby() && entity.getType() == EntityType.HOGLIN) {
            entity = this.getTopPassenger(entity, 3);
        }

        return super.startRiding(entity, force, triggerEvents);
    }

    private Entity getTopPassenger(Entity vehicle, int maxPosition) {
        List<Entity> passengers = vehicle.getPassengers();
        return maxPosition != 1 && !passengers.isEmpty() ? this.getTopPassenger(passengers.getFirst(), maxPosition - 1) : vehicle;
    }

    @Override
    public @Nullable SoundEvent getAmbientSound() {
        return this.level().isClientSide() ? null : PiglinAi.getSoundForCurrentActivity(this).orElse(null);
    }

    @Override
    public SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.PIGLIN_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.PIGLIN_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState block) {
        this.playSound(SoundEvents.PIGLIN_STEP, 0.15F, 1.0F);
    }

    @Override
    protected void playConvertedSound() {
        this.makeSound(SoundEvents.PIGLIN_CONVERTED_TO_ZOMBIFIED);
    }
}
