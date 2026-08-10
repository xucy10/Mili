package net.minecraft.world.entity;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.debug.DebugBrainDump;
import net.minecraft.util.debug.DebugGoalInfo;
import net.minecraft.util.debug.DebugPathInfo;
import net.minecraft.util.debug.DebugSubscriptions;
import net.minecraft.util.debug.DebugValueSource;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.Container;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.control.JumpControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.sensing.Sensing;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.UseRemainder;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.providers.VanillaEnchantmentProviders;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.ContainerSingleItem;
import org.jspecify.annotations.Nullable;

// CraftBukkit start
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.EntityUnleashEvent;
// CraftBukkit end

public abstract class Mob extends LivingEntity implements EquipmentUser, Leashable, Targeting {
    private static final EntityDataAccessor<Byte> DATA_MOB_FLAGS_ID = SynchedEntityData.defineId(Mob.class, EntityDataSerializers.BYTE);
    private static final int MOB_FLAG_NO_AI = 1;
    private static final int MOB_FLAG_LEFTHANDED = 2;
    private static final int MOB_FLAG_AGGRESSIVE = 4;
    protected static final int PICKUP_REACH = 1;
    private static final Vec3i ITEM_PICKUP_REACH = new Vec3i(1, 0, 1);
    private static final List<EquipmentSlot> EQUIPMENT_POPULATION_ORDER = List.of(
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    );
    public static final float MAX_WEARING_ARMOR_CHANCE = 0.15F;
    public static final float WEARING_ARMOR_UPGRADE_MATERIAL_CHANCE = 0.1087F;
    public static final float WEARING_ARMOR_UPGRADE_MATERIAL_ATTEMPTS = 3.0F;
    public static final float MAX_PICKUP_LOOT_CHANCE = 0.55F;
    public static final float MAX_ENCHANTED_ARMOR_CHANCE = 0.5F;
    public static final float MAX_ENCHANTED_WEAPON_CHANCE = 0.25F;
    public static final int UPDATE_GOAL_SELECTOR_EVERY_N_TICKS = 2;
    private static final double DEFAULT_ATTACK_REACH = Math.sqrt(2.04F) - 0.6F;
    private static final boolean DEFAULT_CAN_PICK_UP_LOOT = false;
    private static final boolean DEFAULT_PERSISTENCE_REQUIRED = false;
    private static final boolean DEFAULT_LEFT_HANDED = false;
    private static final boolean DEFAULT_NO_AI = false;
    protected static final Identifier RANDOM_SPAWN_BONUS_ID = Identifier.withDefaultNamespace("random_spawn_bonus");
    public static final String TAG_DROP_CHANCES = "drop_chances";
    public static final String TAG_LEFT_HANDED = "LeftHanded";
    public static final String TAG_CAN_PICK_UP_LOOT = "CanPickUpLoot";
    public static final String TAG_NO_AI = "NoAI";
    public int ambientSoundTime;
    protected int xpReward;
    protected LookControl lookControl;
    protected MoveControl moveControl;
    protected JumpControl jumpControl;
    private final BodyRotationControl bodyRotationControl;
    protected PathNavigation navigation;
    public GoalSelector goalSelector;
    public net.minecraft.world.entity.ai.goal.@Nullable FloatGoal goalFloat; // Paper - Allow nerfed mobs to jump and float
    public GoalSelector targetSelector;
    private @Nullable LivingEntity target;
    private final Sensing sensing;
    private DropChances dropChances = DropChances.DEFAULT;
    private boolean canPickUpLoot = false;
    private boolean persistenceRequired = false;
    private final Map<PathType, Float> pathfindingMalus = Maps.newEnumMap(PathType.class);
    public Optional<ResourceKey<LootTable>> lootTable = Optional.empty();
    public long lootTableSeed;
    private Leashable.@Nullable LeashData leashData;
    private BlockPos homePosition = BlockPos.ZERO;
    private int homeRadius = -1;
    public boolean aware = true; // CraftBukkit
    public net.kyori.adventure.util.TriState despawnInPeacefulOverride = net.kyori.adventure.util.TriState.NOT_SET; // Paper - allow changing despawnInPeaceful

    protected Mob(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        this.goalSelector = new GoalSelector();
        this.targetSelector = new GoalSelector();
        this.lookControl = new LookControl(this);
        this.moveControl = new MoveControl(this);
        this.jumpControl = new JumpControl(this);
        this.bodyRotationControl = this.createBodyControl();
        this.navigation = this.createNavigation(level);
        this.sensing = new Sensing(this);
        if (level instanceof ServerLevel) {
            this.registerGoals();
        }
    }

    // CraftBukkit start
    public void setPersistenceRequired(boolean persistenceRequired) {
        this.persistenceRequired = persistenceRequired;
    }
    // CraftBukkit end

    protected void registerGoals() {
    }

    public static AttributeSupplier.Builder createMobAttributes() {
        return LivingEntity.createLivingAttributes().add(Attributes.FOLLOW_RANGE, 16.0);
    }

    protected PathNavigation createNavigation(Level level) {
        return new GroundPathNavigation(this, level);
    }

    protected boolean shouldPassengersInheritMalus() {
        return false;
    }

    public float getPathfindingMalus(PathType pathType) {
        Mob mob1;
        if (this.getControlledVehicle() instanceof Mob mob && mob.shouldPassengersInheritMalus()) {
            mob1 = mob;
        } else {
            mob1 = this;
        }

        Float _float = mob1.pathfindingMalus.get(pathType);
        return _float == null ? pathType.getMalus() : _float;
    }

    public void setPathfindingMalus(PathType pathType, float malus) {
        this.pathfindingMalus.put(pathType, malus);
    }

    public void onPathfindingStart() {
    }

    public void onPathfindingDone() {
    }

    protected BodyRotationControl createBodyControl() {
        return new BodyRotationControl(this);
    }

    public LookControl getLookControl() {
        return this.lookControl;
    }

    int _pufferfish_inactiveTickDisableCounter = 0; // Pufferfish - throttle inactive goal selector ticking
    // Paper start
    @Override
    public void inactiveTick() {
        super.inactiveTick();
        boolean isThrottled = me.earthme.luminol.config.modules.optimizations.EntityGoalSelectorInactiveTickConfig.enabled && _pufferfish_inactiveTickDisableCounter++ % 20 != 0; // Pufferfish - throttle inactive goal selector ticking
        if (this.goalSelector.inactiveTick() && !isThrottled) { // Pufferfish
            this.goalSelector.tick();
        }
        if (this.targetSelector.inactiveTick()) {
            this.targetSelector.tick();
        }
    }
    // Paper end

    public MoveControl getMoveControl() {
        return this.getControlledVehicle() instanceof Mob mob ? mob.getMoveControl() : this.moveControl;
    }

    public JumpControl getJumpControl() {
        return this.jumpControl;
    }

    public PathNavigation getNavigation() {
        return this.getControlledVehicle() instanceof Mob mob ? mob.getNavigation() : this.navigation;
    }

    @Override
    public @Nullable LivingEntity getControllingPassenger() {
        Entity firstPassenger = this.getFirstPassenger();
        return !this.isNoAi() && firstPassenger instanceof Mob mob && firstPassenger.canControlVehicle() ? mob : null;
    }

    public Sensing getSensing() {
        return this.sensing;
    }

    @Override
    public @Nullable LivingEntity getTarget() {
        // Folia start - region threading
        if (this.target != null && (!ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(this.target) || this.target.isRemoved())) {
            this.target = null;
            return null;
        }
        // Folia end - region threading
        return this.target;
    }

    // Folia start - region threading
    public LivingEntity getTargetRaw() {
        return this.target;
    }
    // Folia end - region threading

    protected final @Nullable LivingEntity getTargetFromBrain() {
        return this.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
    }

    public void setTarget(@Nullable LivingEntity target) {
        // CraftBukkit start - fire event
        this.setTarget(target, EntityTargetEvent.TargetReason.UNKNOWN);
    }

    public boolean setTarget(@Nullable LivingEntity target, EntityTargetEvent.@Nullable TargetReason reason) {
        if (this.getTargetRaw() == target) { // Folia - region threading
            return false;
        }
        // Luminol start - Fix off-region targeting
        if (!ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(target)) {
            return false;
        }
        // Luminol end
        if (reason != null) {
            if (reason == EntityTargetEvent.TargetReason.UNKNOWN && this.getTarget() != null && target == null) {
                reason = this.getTarget().isAlive() ? EntityTargetEvent.TargetReason.FORGOT_TARGET : EntityTargetEvent.TargetReason.TARGET_DIED;
            }
            if (reason == EntityTargetEvent.TargetReason.UNKNOWN) {
                this.level().getCraftServer().getLogger().log(java.util.logging.Level.WARNING, "Unknown target reason, please report on the issue tracker", new Exception());
            }
            org.bukkit.craftbukkit.entity.CraftLivingEntity ctarget = null;
            if (target != null) {
                ctarget = (org.bukkit.craftbukkit.entity.CraftLivingEntity) target.getBukkitEntity();
            }
            org.bukkit.event.entity.EntityTargetLivingEntityEvent event = new org.bukkit.event.entity.EntityTargetLivingEntityEvent(this.getBukkitEntity(), ctarget, reason);
            if (!event.callEvent()) {
                return false;
            }

            if (event.getTarget() != null) {
                target = ((org.bukkit.craftbukkit.entity.CraftLivingEntity) event.getTarget()).getHandle();
            } else {
                target = null;
            }
        }
        this.target = target;
        return true;
        // CraftBukkit end
    }

    @Override
    public boolean canAttackType(EntityType<?> type) {
        return type != EntityType.GHAST;
    }

    public boolean canUseNonMeleeWeapon(ItemStack stack) {
        return false;
    }

    public void ate() {
        this.gameEvent(GameEvent.EAT);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_MOB_FLAGS_ID, (byte)0);
    }

    public int getAmbientSoundInterval() {
        return 80;
    }

    public void playAmbientSound() {
        this.makeSound(this.getAmbientSound());
    }

    @Override
    public void baseTick() {
        super.baseTick();
        ProfilerFiller profilerFiller = Profiler.get();
        profilerFiller.push("mobBaseTick");
        if (this.isAlive() && this.random.nextInt(1000) < this.ambientSoundTime++) {
            this.resetAmbientSoundTime();
            this.playAmbientSound();
        }

        profilerFiller.pop();
    }

    @Override
    protected void playHurtSound(DamageSource damageSource) {
        this.resetAmbientSoundTime();
        super.playHurtSound(damageSource);
    }

    private void resetAmbientSoundTime() {
        this.ambientSoundTime = -this.getAmbientSoundInterval();
    }

    @Override
    protected int getBaseExperienceReward(ServerLevel level) {
        if (this.xpReward > 0) {
            int i = this.xpReward;

            for (EquipmentSlot equipmentSlot : EquipmentSlot.VALUES) {
                if (equipmentSlot.canIncreaseExperience()) {
                    ItemStack itemBySlot = this.getItemBySlot(equipmentSlot);
                    if (!itemBySlot.isEmpty() && this.dropChances.byEquipment(equipmentSlot) <= 1.0F) {
                        i += 1 + this.random.nextInt(3);
                    }
                }
            }

            return i;
        } else {
            return this.xpReward;
        }
    }

    public void spawnAnim() {
        if (this.level().isClientSide()) {
            this.makePoofParticles();
        } else {
            this.level().broadcastEntityEvent(this, EntityEvent.SILVERFISH_MERGE_ANIM);
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EntityEvent.SILVERFISH_MERGE_ANIM) {
            this.spawnAnim();
        } else {
            super.handleEntityEvent(id);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide() && this.tickCount % 5 == 0) {
            this.updateControlFlags();
        }
        // Leaves start - ender dragon part can use end portal
        if (!fun.bm.mili.config.modules.function.OldFeatureConfig.enderDragonPartCanUseEndPortal) return;
        if (!(this instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon dragon)) return;
        for (net.minecraft.world.entity.boss.enderdragon.EnderDragonPart part : dragon.getSubEntities()) {
            PortalProcessor portalManager = part.portalProcess;
            if (portalManager == null) continue;
            if (!(portalManager.portal instanceof net.minecraft.world.level.block.EndPortalBlock)) continue;
            part.handlePortal();
        }
        // Leaves end - ender dragon part can use end portal
    }

    protected void updateControlFlags() {
        boolean flag = !(this.getControllingPassenger() instanceof Mob);
        boolean flag1 = !(this.getVehicle() instanceof AbstractBoat);
        this.goalSelector.setControlFlag(Goal.Flag.MOVE, flag);
        this.goalSelector.setControlFlag(Goal.Flag.JUMP, flag && flag1);
        this.goalSelector.setControlFlag(Goal.Flag.LOOK, flag);
    }

    @Override
    protected void tickHeadTurn(float yBodyRot) {
        this.bodyRotationControl.clientTick();
    }

    public @Nullable SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("CanPickUpLoot", this.canPickUpLoot());
        output.putBoolean("PersistenceRequired", this.persistenceRequired);
        if (!this.dropChances.equals(DropChances.DEFAULT)) {
            output.store("drop_chances", DropChances.CODEC, this.dropChances);
        }

        this.writeLeashData(output, this.leashData);
        if (this.hasHome()) {
            output.putInt("home_radius", this.homeRadius);
            output.store("home_pos", BlockPos.CODEC, this.homePosition);
        }

        output.putBoolean("LeftHanded", this.isLeftHanded());
        this.lootTable.ifPresent(resourceKey -> output.store("DeathLootTable", LootTable.KEY_CODEC, (ResourceKey<LootTable>)resourceKey));
        if (this.lootTableSeed != 0L) {
            output.putLong("DeathLootTableSeed", this.lootTableSeed);
        }

        if (this.isNoAi()) {
            output.putBoolean("NoAI", this.isNoAi());
        }
        output.putBoolean("Bukkit.Aware", this.aware); // CraftBukkit
        // Paper start - allow changing despawnInPeaceful
        if (this.despawnInPeacefulOverride != net.kyori.adventure.util.TriState.NOT_SET) {
            output.putString("Paper.DespawnInPeacefulOverride", this.despawnInPeacefulOverride.name());
        }
        // Paper end - allow changing despawnInPeaceful
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        // CraftBukkit start - If looting or persistence is false only use it if it was set after we started using it
        boolean canPickUpLoot = input.getBooleanOr("CanPickUpLoot", false);
        if (isLevelAtLeast(input, 1) || canPickUpLoot) {
            this.setCanPickUpLoot(canPickUpLoot);
        }
        boolean persistenceRequired = input.getBooleanOr("PersistenceRequired", false);
        if (isLevelAtLeast(input, 1) || persistenceRequired) {
            this.persistenceRequired = persistenceRequired;
        }
        // CraftBukkit end
        this.dropChances = input.read("drop_chances", DropChances.CODEC).orElse(DropChances.DEFAULT);
        this.readLeashData(input);
        this.homeRadius = input.getIntOr("home_radius", -1);
        if (this.homeRadius >= 0) {
            this.homePosition = input.read("home_pos", BlockPos.CODEC).orElse(BlockPos.ZERO);
        }

        this.setLeftHanded(input.getBooleanOr("LeftHanded", false));
        this.lootTable = input.read("DeathLootTable", LootTable.KEY_CODEC);
        this.lootTableSeed = input.getLongOr("DeathLootTableSeed", 0L);
        this.setNoAi(input.getBooleanOr("NoAI", false));
        this.aware = input.getBooleanOr("Bukkit.Aware", true); // CraftBukkit
        // Paper start - allow changing despawnInPeaceful
        this.despawnInPeacefulOverride = readDespawnInPeacefulOverride(input);
    }
    public static net.kyori.adventure.util.TriState readDespawnInPeacefulOverride(ValueInput input) {
        return input.read("Paper.DespawnInPeacefulOverride", io.papermc.paper.util.PaperCodecs.TRI_STATE_CODEC).orElse(net.kyori.adventure.util.TriState.NOT_SET);
        // Paper end - allow changing despawnInPeaceful
    }

    @Override
    protected void dropFromLootTable(ServerLevel level, DamageSource damageSource, boolean playerKill) {
        super.dropFromLootTable(level, damageSource, playerKill);
        this.lootTable = Optional.empty();
    }

    @Override
    public final Optional<ResourceKey<LootTable>> getLootTable() {
        return this.lootTable.isPresent() ? this.lootTable : super.getLootTable();
    }

    @Override
    public long getLootTableSeed() {
        return this.lootTableSeed;
    }

    public void setZza(float amount) {
        this.zza = amount;
    }

    public void setYya(float amount) {
        this.yya = amount;
    }

    public void setXxa(float amount) {
        this.xxa = amount;
    }

    @Override
    public void setSpeed(float speed) {
        super.setSpeed(speed);
        this.setZza(speed);
    }

    public void stopInPlace() {
        this.getNavigation().stop();
        this.setXxa(0.0F);
        this.setYya(0.0F);
        this.setSpeed(0.0F);
        this.setDeltaMovement(0.0, 0.0, 0.0);
        this.resetAngularLeashMomentum();
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.getType().is(EntityTypeTags.BURN_IN_DAYLIGHT)) {
            this.burnUndead();
        }

        ProfilerFiller profilerFiller = Profiler.get();
        profilerFiller.push("looting");
        if (this.level() instanceof ServerLevel serverLevel
            && this.canPickUpLoot()
            && this.isAlive()
            && !this.dead
            && serverLevel.getGameRules().get(GameRules.MOB_GRIEFING)) {
            Vec3i pickupReach = this.getPickupReach();

            for (ItemEntity itemEntity : this.level()
                .getEntitiesOfClass(ItemEntity.class, this.getBoundingBox().inflate(pickupReach.getX(), pickupReach.getY(), pickupReach.getZ()))) {
                if (!itemEntity.isRemoved()
                    && !itemEntity.getItem().isEmpty()
                    && !itemEntity.hasPickUpDelay()
                    && this.wantsToPickUp(serverLevel, itemEntity.getItem())) {
                    // Paper start - Item#canEntityPickup
                    if (!itemEntity.canMobPickup) {
                        continue;
                    }
                    // Paper end - Item#canEntityPickup
                    this.pickUpItem(serverLevel, itemEntity);
                }
            }
        }

        profilerFiller.pop();
    }

    protected EquipmentSlot sunProtectionSlot() {
        return EquipmentSlot.HEAD;
    }

    private void burnUndead() {
        if (this.isAlive() && this.isSunBurnTick()) {
            EquipmentSlot equipmentSlot = this.sunProtectionSlot();
            ItemStack itemBySlot = this.getItemBySlot(equipmentSlot);
            if (!itemBySlot.isEmpty()) {
                if (itemBySlot.isDamageableItem()) {
                    Item item = itemBySlot.getItem();
                    itemBySlot.setDamageValue(itemBySlot.getDamageValue() + this.random.nextInt(2));
                    if (itemBySlot.getDamageValue() >= itemBySlot.getMaxDamage()) {
                        this.onEquippedItemBroken(item, equipmentSlot);
                        this.setItemSlot(equipmentSlot, ItemStack.EMPTY);
                    }
                }
            } else {
                this.igniteForSeconds(8.0F);
            }
        }
    }

    public boolean isSunBurnTick() {
        if (!this.level().isClientSide() && this.level().environmentAttributes().getValue(EnvironmentAttributes.MONSTERS_BURN, this.position())) {
            float lightLevelDependentMagicValue = this.getLightLevelDependentMagicValue();
            BlockPos blockPos = BlockPos.containing(this.getX(), this.getEyeY(), this.getZ());
            boolean flag = this.isInWaterOrRain() || this.isInPowderSnow || this.wasInPowderSnow;
            if (lightLevelDependentMagicValue > 0.5F
                && this.random.nextFloat() * 30.0F < (lightLevelDependentMagicValue - 0.4F) * 2.0F
                && !flag
                && this.level().canSeeSky(blockPos)) {
                return true;
            }
        }

        return false;
    }

    protected Vec3i getPickupReach() {
        return ITEM_PICKUP_REACH;
    }

    protected void pickUpItem(ServerLevel level, ItemEntity entity) {
        ItemStack item = entity.getItem();
        ItemStack itemStack = this.equipItemIfPossible(level, item.copy(), entity); // CraftBukkit - add item
        if (!itemStack.isEmpty()) {
            this.onItemPickup(entity);
            this.take(entity, itemStack.getCount());
            item.shrink(itemStack.getCount());
            if (item.isEmpty()) {
                entity.discard(EntityRemoveEvent.Cause.PICKUP); // CraftBukkit - add Bukkit remove cause
            }
        }
    }

    public ItemStack equipItemIfPossible(ServerLevel level, ItemStack stack) {
        // CraftBukkit start - add item
        return this.equipItemIfPossible(level, stack, null);
    }

    public ItemStack equipItemIfPossible(ServerLevel level, ItemStack stack, @Nullable ItemEntity entity) {
        // CraftBukkit end
        EquipmentSlot equipmentSlotForItem = this.getEquipmentSlotForItem(stack);
        if (!this.isEquippableInSlot(stack, equipmentSlotForItem)) {
            return ItemStack.EMPTY;
        } else {
            ItemStack itemBySlot = this.getItemBySlot(equipmentSlotForItem);
            boolean canReplaceCurrentItem = this.canReplaceCurrentItem(stack, itemBySlot, equipmentSlotForItem);
            if (equipmentSlotForItem.isArmor() && !canReplaceCurrentItem) {
                equipmentSlotForItem = EquipmentSlot.MAINHAND;
                itemBySlot = this.getItemBySlot(equipmentSlotForItem);
                canReplaceCurrentItem = itemBySlot.isEmpty();
            }

            // CraftBukkit start
            boolean canPickup = canReplaceCurrentItem && this.canHoldItem(stack);
            if (entity != null) {
                canPickup = !org.bukkit.craftbukkit.event.CraftEventFactory.callEntityPickupItemEvent(this, entity, 0, !canPickup).isCancelled();
            }
            if (canPickup) {
                // CraftBukkit end
                double d = this.dropChances.byEquipment(equipmentSlotForItem);
                if (!itemBySlot.isEmpty() && Math.max(this.random.nextFloat() - 0.1F, 0.0F) < d) {
                    this.forceDrops = true; // CraftBukkit
                    this.spawnAtLocation(level, itemBySlot);
                    this.forceDrops = false; // CraftBukkit
                }

                ItemStack itemStack = equipmentSlotForItem.limit(stack);
                this.setItemSlotAndDropWhenKilled(equipmentSlotForItem, itemStack);
                return itemStack;
            } else {
                return ItemStack.EMPTY;
            }
        }
    }

    protected void setItemSlotAndDropWhenKilled(EquipmentSlot slot, ItemStack stack) {
        this.setItemSlot(slot, stack);
        this.setGuaranteedDrop(slot);
        this.persistenceRequired = true;
    }

    protected boolean canShearEquipment(Player player) {
        return !this.isVehicle();
    }

    public void setGuaranteedDrop(EquipmentSlot slot) {
        this.dropChances = this.dropChances.withGuaranteedDrop(slot);
    }

    protected boolean canReplaceCurrentItem(ItemStack newItem, ItemStack currentItem, EquipmentSlot slot) {
        if (currentItem.isEmpty()) {
            return true;
        } else {
            return slot.isArmor()
                ? this.compareArmor(newItem, currentItem, slot)
                : slot == EquipmentSlot.MAINHAND && this.compareWeapons(newItem, currentItem, slot);
        }
    }

    private boolean compareArmor(ItemStack newItem, ItemStack currentItem, EquipmentSlot slot) {
        if (EnchantmentHelper.has(currentItem, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE)) {
            return false;
        } else {
            double approximateAttributeWith = this.getApproximateAttributeWith(newItem, Attributes.ARMOR, slot);
            double approximateAttributeWith1 = this.getApproximateAttributeWith(currentItem, Attributes.ARMOR, slot);
            double approximateAttributeWith2 = this.getApproximateAttributeWith(newItem, Attributes.ARMOR_TOUGHNESS, slot);
            double approximateAttributeWith3 = this.getApproximateAttributeWith(currentItem, Attributes.ARMOR_TOUGHNESS, slot);
            if (approximateAttributeWith != approximateAttributeWith1) {
                return approximateAttributeWith > approximateAttributeWith1;
            } else {
                return approximateAttributeWith2 != approximateAttributeWith3
                    ? approximateAttributeWith2 > approximateAttributeWith3
                    : this.canReplaceEqualItem(newItem, currentItem);
            }
        }
    }

    private boolean compareWeapons(ItemStack newItem, ItemStack currentItem, EquipmentSlot slot) {
        TagKey<Item> preferredWeaponType = this.getPreferredWeaponType();
        if (preferredWeaponType != null) {
            if (currentItem.is(preferredWeaponType) && !newItem.is(preferredWeaponType)) {
                return false;
            }

            if (!currentItem.is(preferredWeaponType) && newItem.is(preferredWeaponType)) {
                return true;
            }
        }

        double approximateAttributeWith = this.getApproximateAttributeWith(newItem, Attributes.ATTACK_DAMAGE, slot);
        double approximateAttributeWith1 = this.getApproximateAttributeWith(currentItem, Attributes.ATTACK_DAMAGE, slot);
        return approximateAttributeWith != approximateAttributeWith1
            ? approximateAttributeWith > approximateAttributeWith1
            : this.canReplaceEqualItem(newItem, currentItem);
    }

    private double getApproximateAttributeWith(ItemStack item, Holder<Attribute> attribute, EquipmentSlot slot) {
        double d = this.getAttributes().hasAttribute(attribute) ? this.getAttributeBaseValue(attribute) : 0.0;
        ItemAttributeModifiers itemAttributeModifiers = item.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        return itemAttributeModifiers.compute(attribute, d, slot);
    }

    public boolean canReplaceEqualItem(ItemStack candidate, ItemStack existing) {
        Set<Entry<Holder<Enchantment>>> set = existing.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).entrySet();
        Set<Entry<Holder<Enchantment>>> set1 = candidate.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).entrySet();
        if (set1.size() != set.size()) {
            return set1.size() > set.size();
        } else {
            int damageValue = candidate.getDamageValue();
            int damageValue1 = existing.getDamageValue();
            return damageValue != damageValue1
                ? damageValue < damageValue1
                : candidate.has(DataComponents.CUSTOM_NAME) && !existing.has(DataComponents.CUSTOM_NAME);
        }
    }

    public boolean canHoldItem(ItemStack stack) {
        return true;
    }

    public boolean wantsToPickUp(ServerLevel level, ItemStack stack) {
        return this.canHoldItem(stack);
    }

    public @Nullable TagKey<Item> getPreferredWeaponType() {
        return null;
    }

    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return true;
    }

    public boolean requiresCustomPersistence() {
        return this.isPassenger();
    }

    // Paper start - allow changing despawnInPeaceful
    public final boolean shouldDespawnInPeaceful() {
        return this.despawnInPeacefulOverride.toBooleanOrElse(!this.getType().isTypeAllowedInPeaceful());
    }
    // Paper end - allow changing despawnInPeaceful

    @Override
    public void checkDespawn() {
        if (this.level().getDifficulty() == Difficulty.PEACEFUL && this.shouldDespawnInPeaceful()) { // Paper - allow changing despawnInPeaceful
            this.discard(EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
        } else if (!this.isPersistenceRequired() && !this.requiresCustomPersistence()) {
            Entity nearestPlayer = this.level().findNearbyPlayer(this, -1.0, EntitySelector.PLAYER_AFFECTS_SPAWNING); // Paper - Affects Spawning API
            if (nearestPlayer != null) {
                // Paper start - Configurable despawn distances
                final io.papermc.paper.configuration.WorldConfiguration.Entities.Spawning.DespawnRangePair despawnRangePair = this.level().paperConfig().entities.spawning.despawnRanges.get(this.getType().getCategory());
                final io.papermc.paper.configuration.type.DespawnRange.Shape shape = this.level().paperConfig().entities.spawning.despawnRangeShape;
                final double dy = Math.abs(nearestPlayer.getY() - this.getY());
                final double dySqr = Mth.square(dy);
                final double dxSqr = Mth.square(nearestPlayer.getX() - this.getX());
                final double dzSqr = Mth.square(nearestPlayer.getZ() - this.getZ());
                final double distanceSquared = dxSqr + dzSqr + dySqr;
                // Despawn if hard/soft limit is exceeded
                if (despawnRangePair.hard().shouldDespawn(shape, dxSqr, dySqr, dzSqr, dy) && this.removeWhenFarAway(distanceSquared)) {
                    this.discard(EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
                 }

                if (despawnRangePair.soft().shouldDespawn(shape, dxSqr, dySqr, dzSqr, dy)) {
                    if (this.noActionTime > 600 && this.random.nextInt(800) == 0 && this.removeWhenFarAway(distanceSquared)) {
                        this.discard(EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
                    }
                } else {
                    // Paper end - Configurable despawn distances
                    this.noActionTime = 0;
                }
            }
        } else {
            // Mili start - fix some bug if player is cross scheduler >> to remove monster if the region don't have any valid player
            if (fun.bm.mili.config.modules.experiment.GlobalEntitiesCounter.enabled) {
                List<net.minecraft.server.level.ServerPlayer> playerList = this.level().getServer().getPlayerList().getPlayers();
                if (!playerList.isEmpty()) {
                    double d = -1.0;
                    Vec3 vec3 = null;
                    for (net.minecraft.server.level.ServerPlayer player1 : playerList) {
                        if (player1.level() != this.level()) continue;
                        double d1 = player1.distanceToSqr(this.position());
                        if (d == -1.0 || d1 < d) {
                            d = d1;
                            vec3 = player1.position();
                        }
                    }
                    if (vec3 != null) {
                        // copy from PaperMC
                        // Paper start - Configurable despawn distances
                        final io.papermc.paper.configuration.WorldConfiguration.Entities.Spawning.DespawnRangePair despawnRangePair = this.level().paperConfig().entities.spawning.despawnRanges.get(this.getType().getCategory());
                        final io.papermc.paper.configuration.type.DespawnRange.Shape shape = this.level().paperConfig().entities.spawning.despawnRangeShape;
                        final double dy = Math.abs(vec3.y - this.getY());
                        final double dySqr = Mth.square(dy);
                        final double dxSqr = Mth.square(vec3.x - this.getX());
                        final double dzSqr = Mth.square(vec3.z - this.getZ());
                        final double distanceSquared = dxSqr + dzSqr + dySqr;
                        // Despawn if hard/soft limit is exceeded
                        if (despawnRangePair.hard().shouldDespawn(shape, dxSqr, dySqr, dzSqr, dy) && this.removeWhenFarAway(distanceSquared)) {
                            this.discard(EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
                        }

                        if (despawnRangePair.soft().shouldDespawn(shape, dxSqr, dySqr, dzSqr, dy)) {
                            if (this.noActionTime > 600 && this.random.nextInt(800) == 0 && this.removeWhenFarAway(distanceSquared)) {
                                this.discard(EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
                            }
                        }
                    }
                }
            }
            // Mili end
            this.noActionTime = 0;
        }
    }

    @Override
    protected final void serverAiStep() {
        this.noActionTime++;
        // Paper start - Allow nerfed mobs to jump and float
        if (!this.aware) {
            if (this.goalFloat != null) {
                if (this.goalFloat.canUse()) this.goalFloat.tick();
                this.getJumpControl().tick();
            }
            return;
        }
        // Paper end - Allow nerfed mobs to jump and float
        int i = this.tickCount + this.getId(); //Luminol - Petal - Move up
        ProfilerFiller profilerFiller = Profiler.get();
        profilerFiller.push("sensing");
        if (i % me.earthme.luminol.config.modules.optimizations.PetalReduceSensorWorkConfig.delayTicks == 0 || !me.earthme.luminol.config.modules.optimizations.PetalReduceSensorWorkConfig.enabled) this.sensing.tick(); // Luminol - Petal - Reduce sensor work
        profilerFiller.pop();
        if (i % 2 != 0 && this.tickCount > 1) {
            profilerFiller.push("targetSelector");
            this.targetSelector.tickRunningGoals(false);
            profilerFiller.pop();
            profilerFiller.push("goalSelector");
            this.goalSelector.tickRunningGoals(false);
            profilerFiller.pop();
        } else {
            profilerFiller.push("targetSelector");
            this.targetSelector.tick();
            profilerFiller.pop();
            profilerFiller.push("goalSelector");
            this.goalSelector.tick();
            profilerFiller.pop();
        }

        profilerFiller.push("navigation");
        this.navigation.tick();
        profilerFiller.pop();
        profilerFiller.push("mob tick");
        this.customServerAiStep((ServerLevel)this.level());
        profilerFiller.pop();
        profilerFiller.push("controls");
        profilerFiller.push("move");
        this.moveControl.tick();
        profilerFiller.popPush("look");
        this.lookControl.tick();
        profilerFiller.popPush("jump");
        this.jumpControl.tick();
        profilerFiller.pop();
        profilerFiller.pop();
    }

    protected void customServerAiStep(ServerLevel level) {
    }

    public int getMaxHeadXRot() {
        return 40;
    }

    public int getMaxHeadYRot() {
        return 75;
    }

    protected void clampHeadRotationToBody() {
        float f = this.getMaxHeadYRot();
        float yHeadRot = this.getYHeadRot();
        float f1 = Mth.wrapDegrees(this.yBodyRot - yHeadRot);
        float f2 = Mth.clamp(Mth.wrapDegrees(this.yBodyRot - yHeadRot), -f, f);
        float f3 = yHeadRot + f1 - f2;
        this.setYHeadRot(f3);
    }

    public int getHeadRotSpeed() {
        return 10;
    }

    public void lookAt(Entity entity, float maxYRotIncrease, float maxXRotIncrease) {
        double d = entity.getX() - this.getX();
        double d1 = entity.getZ() - this.getZ();
        double d2;
        if (entity instanceof LivingEntity livingEntity) {
            d2 = livingEntity.getEyeY() - this.getEyeY();
        } else {
            d2 = (entity.getBoundingBox().minY + entity.getBoundingBox().maxY) / 2.0 - this.getEyeY();
        }

        double squareRoot = Math.sqrt(d * d + d1 * d1);
        float f = (float)(Mth.atan2(d1, d) * 180.0F / (float)Math.PI) - 90.0F;
        float f1 = (float)(-(Mth.atan2(d2, squareRoot) * 180.0F / (float)Math.PI));
        this.setXRot(this.rotlerp(this.getXRot(), f1, maxXRotIncrease));
        this.setYRot(this.rotlerp(this.getYRot(), f, maxYRotIncrease));
    }

    private float rotlerp(float angle, float targetAngle, float maxIncrease) {
        float f = Mth.wrapDegrees(targetAngle - angle);
        if (f > maxIncrease) {
            f = maxIncrease;
        }

        if (f < -maxIncrease) {
            f = -maxIncrease;
        }

        return angle + f;
    }

    public static boolean checkMobSpawnRules(
        EntityType<? extends Mob> entityType, LevelAccessor level, EntitySpawnReason spawnReason, BlockPos pos, RandomSource random
    ) {
        BlockPos blockPos = pos.below();
        return EntitySpawnReason.isSpawner(spawnReason) || level.getBlockState(blockPos).isValidSpawn(level, blockPos, entityType);
    }

    public boolean checkSpawnRules(LevelAccessor level, EntitySpawnReason spawnReason) {
        return true;
    }

    public boolean checkSpawnObstruction(LevelReader level) {
        return !level.containsAnyLiquid(this.getBoundingBox()) && level.isUnobstructed(this);
    }

    public int getMaxSpawnClusterSize() {
        return 4;
    }

    public boolean isMaxGroupSizeReached(int size) {
        return false;
    }

    @Override
    public int getMaxFallDistance() {
        if (this.getTarget() == null) {
            return this.getComfortableFallDistance(0.0F);
        } else {
            int i = (int)(this.getHealth() - this.getMaxHealth() * 0.33F);
            i -= (3 - this.level().getDifficulty().getId()) * 4;
            if (i < 0) {
                i = 0;
            }

            return this.getComfortableFallDistance(i);
        }
    }

    public ItemStack getBodyArmorItem() {
        return this.getItemBySlot(EquipmentSlot.BODY);
    }

    public boolean isSaddled() {
        return this.hasValidEquippableItemForSlot(EquipmentSlot.SADDLE);
    }

    public boolean isWearingBodyArmor() {
        return this.hasValidEquippableItemForSlot(EquipmentSlot.BODY);
    }

    private boolean hasValidEquippableItemForSlot(EquipmentSlot slot) {
        return this.hasItemInSlot(slot) && this.isEquippableInSlot(this.getItemBySlot(slot), slot);
    }

    public void setBodyArmorItem(ItemStack stack) {
        this.setItemSlotAndDropWhenKilled(EquipmentSlot.BODY, stack);
    }

    public Container createEquipmentSlotContainer(final EquipmentSlot slot) {
        return new ContainerSingleItem() {
            @Override
            public ItemStack getTheItem() {
                return Mob.this.getItemBySlot(slot);
            }

            @Override
            public void setTheItem(ItemStack item) {
                Mob.this.setItemSlot(slot, item);
                if (!item.isEmpty()) {
                    Mob.this.setGuaranteedDrop(slot);
                    Mob.this.setPersistenceRequired();
                }
            }

            @Override
            public void setChanged() {
            }

            @Override
            public boolean stillValid(Player player) {
                return player.getVehicle() == Mob.this || player.isWithinEntityInteractionRange(Mob.this, 4.0);
            }

            // Paper start
            private final List<org.bukkit.entity.HumanEntity> viewers = new java.util.ArrayList<>();
            private int maxStackSize = MAX_STACK;

            @Override
            public int getMaxStackSize() {
                return this.maxStackSize;
            }

            @Override
            public List<ItemStack> getContents() {
                return java.util.Arrays.asList(this.getTheItem());
            }

            @Override
            public void onOpen(final org.bukkit.craftbukkit.entity.CraftHumanEntity player) {
                this.viewers.add(player);
            }

            @Override
            public void onClose(final org.bukkit.craftbukkit.entity.CraftHumanEntity player) {
                this.viewers.remove(player);
            }

            @Override
            public List<org.bukkit.entity.HumanEntity> getViewers() {
                return this.viewers;
            }

            @Override
            public org.bukkit.inventory.@Nullable InventoryHolder getOwner() {
                if (Mob.this.getBukkitEntity() instanceof org.bukkit.inventory.InventoryHolder inventoryHolder) {
                    return inventoryHolder;
                }
                return null;
            }

            @Override
            public void setMaxStackSize(final int size) {
                this.maxStackSize = size;
            }

            @Override
            public org.bukkit.Location getLocation() {
                return Mob.this.getBukkitEntity().getLocation();
            }
            // Paper end
        };
    }

    // Paper start
    protected boolean shouldSkipLoot(EquipmentSlot slot) { // method to avoid to fallback into the global mob loot logic (e.g. the fox)
        return false;
    }
    // Paper end

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource damageSource, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, damageSource, recentlyHit);

        for (EquipmentSlot equipmentSlot : EquipmentSlot.VALUES) {
            if (this.shouldSkipLoot(equipmentSlot)) continue; // Paper
            ItemStack itemBySlot = this.getItemBySlot(equipmentSlot);
            float f = this.dropChances.byEquipment(equipmentSlot);
            if (f != 0.0F) {
                boolean isPreserved = this.dropChances.isPreserved(equipmentSlot);
                if (damageSource.getEntity() instanceof LivingEntity livingEntity && this.level() instanceof ServerLevel serverLevel) {
                    f = EnchantmentHelper.processEquipmentDropChance(serverLevel, livingEntity, damageSource, f);
                }

                if (!itemBySlot.isEmpty()
                    && !EnchantmentHelper.has(itemBySlot, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)
                    && (recentlyHit || isPreserved)
                    && this.random.nextFloat() < f) {
                    if (!isPreserved && itemBySlot.isDamageableItem()) {
                        itemBySlot.setDamageValue(
                            itemBySlot.getMaxDamage() - this.random.nextInt(1 + this.random.nextInt(Math.max(itemBySlot.getMaxDamage() - 3, 1)))
                        );
                    }

                    this.spawnAtLocation(level, itemBySlot);
                    if (this.clearEquipmentSlots) { // Paper
                    this.setItemSlot(equipmentSlot, ItemStack.EMPTY);
                    // Paper start
                    } else {
                        this.clearedEquipmentSlots.add(equipmentSlot);
                    }
                    // Paper end
                }
            }
        }
    }

    public DropChances getDropChances() {
        return this.dropChances;
    }

    public void dropPreservedEquipment(ServerLevel level) {
        this.dropPreservedEquipment(level, itemStack -> true);
    }

    public Set<EquipmentSlot> dropPreservedEquipment(ServerLevel level, Predicate<ItemStack> filter) {
        Set<EquipmentSlot> set = new HashSet<>();

        for (EquipmentSlot equipmentSlot : EquipmentSlot.VALUES) {
            ItemStack itemBySlot = this.getItemBySlot(equipmentSlot);
            if (!itemBySlot.isEmpty()) {
                if (!filter.test(itemBySlot)) {
                    set.add(equipmentSlot);
                } else if (this.dropChances.isPreserved(equipmentSlot)) {
                    this.setItemSlot(equipmentSlot, ItemStack.EMPTY);
                    this.forceDrops = true; // Paper - Add missing forceDrop toggles
                    this.spawnAtLocation(level, itemBySlot);
                    this.forceDrops = false; // Paper - Add missing forceDrop toggles
                }
            }
        }

        return set;
    }

    private LootParams createEquipmentParams(ServerLevel level) {
        return new LootParams.Builder(level)
            .withParameter(LootContextParams.ORIGIN, this.position())
            .withParameter(LootContextParams.THIS_ENTITY, this)
            .create(LootContextParamSets.EQUIPMENT);
    }

    public void equip(EquipmentTable equipmentTable) {
        this.equip(equipmentTable.lootTable(), equipmentTable.slotDropChances());
    }

    public void equip(ResourceKey<LootTable> equipmentLootTable, Map<EquipmentSlot, Float> slotDropChances) {
        if (this.level() instanceof ServerLevel serverLevel) {
            this.equip(equipmentLootTable, this.createEquipmentParams(serverLevel), slotDropChances);
        }
    }

    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        if (random.nextFloat() < 0.15F * difficulty.getSpecialMultiplier()) {
            int randomInt = random.nextInt(3);

            for (int i = 1; i <= 3.0F; i++) {
                if (random.nextFloat() < 0.1087F) {
                    randomInt++;
                }
            }

            float f = this.level().getDifficulty() == Difficulty.HARD ? 0.1F : 0.25F;
            boolean flag = true;

            for (EquipmentSlot equipmentSlot : EQUIPMENT_POPULATION_ORDER) {
                ItemStack itemBySlot = this.getItemBySlot(equipmentSlot);
                if (!flag && random.nextFloat() < f) {
                    break;
                }

                flag = false;
                if (itemBySlot.isEmpty()) {
                    Item equipmentForSlot = getEquipmentForSlot(equipmentSlot, randomInt);
                    if (equipmentForSlot != null) {
                        this.setItemSlot(equipmentSlot, new ItemStack(equipmentForSlot));
                    }
                }
            }
        }
    }

    public static @Nullable Item getEquipmentForSlot(EquipmentSlot slot, int chance) {
        switch (slot) {
            case HEAD:
                if (chance == 0) {
                    return Items.LEATHER_HELMET;
                } else if (chance == 1) {
                    return Items.COPPER_HELMET;
                } else if (chance == 2) {
                    return Items.GOLDEN_HELMET;
                } else if (chance == 3) {
                    return Items.CHAINMAIL_HELMET;
                } else if (chance == 4) {
                    return Items.IRON_HELMET;
                } else if (chance == 5) {
                    return Items.DIAMOND_HELMET;
                }
            case CHEST:
                if (chance == 0) {
                    return Items.LEATHER_CHESTPLATE;
                } else if (chance == 1) {
                    return Items.COPPER_CHESTPLATE;
                } else if (chance == 2) {
                    return Items.GOLDEN_CHESTPLATE;
                } else if (chance == 3) {
                    return Items.CHAINMAIL_CHESTPLATE;
                } else if (chance == 4) {
                    return Items.IRON_CHESTPLATE;
                } else if (chance == 5) {
                    return Items.DIAMOND_CHESTPLATE;
                }
            case LEGS:
                if (chance == 0) {
                    return Items.LEATHER_LEGGINGS;
                } else if (chance == 1) {
                    return Items.COPPER_LEGGINGS;
                } else if (chance == 2) {
                    return Items.GOLDEN_LEGGINGS;
                } else if (chance == 3) {
                    return Items.CHAINMAIL_LEGGINGS;
                } else if (chance == 4) {
                    return Items.IRON_LEGGINGS;
                } else if (chance == 5) {
                    return Items.DIAMOND_LEGGINGS;
                }
            case FEET:
                if (chance == 0) {
                    return Items.LEATHER_BOOTS;
                } else if (chance == 1) {
                    return Items.COPPER_BOOTS;
                } else if (chance == 2) {
                    return Items.GOLDEN_BOOTS;
                } else if (chance == 3) {
                    return Items.CHAINMAIL_BOOTS;
                } else if (chance == 4) {
                    return Items.IRON_BOOTS;
                } else if (chance == 5) {
                    return Items.DIAMOND_BOOTS;
                }
            default:
                return null;
        }
    }

    protected void populateDefaultEquipmentEnchantments(ServerLevelAccessor level, RandomSource random, DifficultyInstance difficulty) {
        this.enchantSpawnedWeapon(level, random, difficulty);

        for (EquipmentSlot equipmentSlot : EquipmentSlot.VALUES) {
            if (equipmentSlot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
                this.enchantSpawnedArmor(level, random, equipmentSlot, difficulty);
            }
        }
    }

    protected void enchantSpawnedWeapon(ServerLevelAccessor level, RandomSource random, DifficultyInstance difficulty) {
        this.enchantSpawnedEquipment(level, EquipmentSlot.MAINHAND, random, 0.25F, difficulty);
    }

    protected void enchantSpawnedArmor(ServerLevelAccessor level, RandomSource random, EquipmentSlot slot, DifficultyInstance difficulty) {
        this.enchantSpawnedEquipment(level, slot, random, 0.5F, difficulty);
    }

    private void enchantSpawnedEquipment(ServerLevelAccessor level, EquipmentSlot slot, RandomSource random, float enchantChance, DifficultyInstance difficulty) {
        ItemStack itemBySlot = this.getItemBySlot(slot);
        if (!itemBySlot.isEmpty() && random.nextFloat() < enchantChance * difficulty.getSpecialMultiplier()) {
            EnchantmentHelper.enchantItemFromProvider(itemBySlot, level.registryAccess(), VanillaEnchantmentProviders.MOB_SPAWN_EQUIPMENT, difficulty, random);
            this.setItemSlot(slot, itemBySlot);
        }
    }

    public @Nullable SpawnGroupData finalizeSpawn(
        ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData spawnGroupData
    ) {
        RandomSource random = level.getRandom();
        AttributeInstance attributeInstance = Objects.requireNonNull(this.getAttribute(Attributes.FOLLOW_RANGE));
        if (!attributeInstance.hasModifier(RANDOM_SPAWN_BONUS_ID)) {
            attributeInstance.addPermanentModifier(
                new AttributeModifier(RANDOM_SPAWN_BONUS_ID, random.triangle(0.0, 0.11485000000000001), AttributeModifier.Operation.ADD_MULTIPLIED_BASE)
            );
        }

        this.setLeftHanded(random.nextFloat() < 0.05F);
        return spawnGroupData;
    }

    public void setPersistenceRequired() {
        this.persistenceRequired = true;
    }

    @Override
    public void setDropChance(EquipmentSlot slot, float chance) {
        this.dropChances = this.dropChances.withEquipmentChance(slot, chance);
    }

    @Override
    public boolean canPickUpLoot() {
        return this.canPickUpLoot;
    }

    public void setCanPickUpLoot(boolean canPickUpLoot) {
        this.canPickUpLoot = canPickUpLoot;
    }

    @Override
    protected boolean canDispenserEquipIntoSlot(EquipmentSlot slot) {
        return this.canPickUpLoot();
    }

    public boolean isPersistenceRequired() {
        return this.persistenceRequired;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!this.isAlive()) {
            return InteractionResult.PASS;
        } else {
            InteractionResult interactionResult = this.checkAndHandleImportantInteractions(player, hand);
            if (interactionResult.consumesAction()) {
                this.gameEvent(GameEvent.ENTITY_INTERACT, player);
                return interactionResult;
            } else {
                InteractionResult interactionResult1 = super.interact(player, hand);
                if (interactionResult1 != InteractionResult.PASS) {
                    return interactionResult1;
                } else {
                    interactionResult = this.mobInteract(player, hand);
                    if (interactionResult.consumesAction()) {
                        this.gameEvent(GameEvent.ENTITY_INTERACT, player);
                        return interactionResult;
                    } else {
                        return InteractionResult.PASS;
                    }
                }
            }
        }
    }

    private InteractionResult checkAndHandleImportantInteractions(Player player, InteractionHand hand) {
        ItemStack itemInHand = player.getItemInHand(hand);
        if (itemInHand.is(Items.NAME_TAG)) {
            InteractionResult interactionResult = itemInHand.interactLivingEntity(player, this, hand);
            if (interactionResult.consumesAction()) {
                return interactionResult;
            }
        }

        if (itemInHand.getItem() instanceof SpawnEggItem spawnEggItem) {
            if (this.level() instanceof ServerLevel) {
                Optional<Mob> optional = spawnEggItem.spawnOffspringFromSpawnEgg(
                    player, this, (EntityType<? extends Mob>)this.getType(), (ServerLevel)this.level(), this.position(), itemInHand
                );
                optional.ifPresent(mob -> this.onOffspringSpawnedFromEgg(player, mob));
                if (optional.isEmpty()) {
                    return InteractionResult.PASS;
                }
            }

            return InteractionResult.SUCCESS_SERVER;
        } else {
            return InteractionResult.PASS;
        }
    }

    protected void onOffspringSpawnedFromEgg(Player player, Mob child) {
    }

    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    protected void usePlayerItem(Player player, InteractionHand hand, ItemStack stack) {
        int count = stack.getCount();
        UseRemainder useRemainder = stack.get(DataComponents.USE_REMAINDER);
        stack.consume(1, player);
        if (useRemainder != null) {
            ItemStack itemStack = useRemainder.convertIntoRemainder(stack, count, player.hasInfiniteMaterials(), player::handleExtraItemsCreatedOnUse);
            player.setItemInHand(hand, itemStack);
        }
    }

    public boolean isWithinHome() {
        return this.isWithinHome(this.blockPosition());
    }

    public boolean isWithinHome(BlockPos pos) {
        return this.homeRadius == -1 || this.homePosition.distSqr(pos) < this.homeRadius * this.homeRadius;
    }

    public boolean isWithinHome(Vec3 pos) {
        return this.homeRadius == -1 || this.homePosition.distToCenterSqr(pos) < this.homeRadius * this.homeRadius;
    }

    public void setHomeTo(BlockPos pos, int radius) {
        this.homePosition = pos;
        this.homeRadius = radius;
    }

    public BlockPos getHomePosition() {
        return this.homePosition;
    }

    public int getHomeRadius() {
        return this.homeRadius;
    }

    public void clearHome() {
        this.homeRadius = -1;
    }

    public boolean hasHome() {
        return this.homeRadius != -1;
    }

    public <T extends Mob> @Nullable T convertTo(
        EntityType<T> entityType, ConversionParams conversionParams, EntitySpawnReason spawnReason, ConversionParams.AfterConversion<T> afterConversion
    ) {
        // Paper start - entity zap event - allow cancellation of conversion post creation
        return this.convertTo(entityType, conversionParams, spawnReason, afterConversion, EntityTransformEvent.TransformReason.UNKNOWN, CreatureSpawnEvent.SpawnReason.DEFAULT);
    }

    @Nullable
    public <T extends Mob> T convertTo(
        EntityType<T> entityType, ConversionParams conversionParams, EntitySpawnReason spawnReason, ConversionParams.AfterConversion<T> afterConversion, EntityTransformEvent.@Nullable TransformReason transformReason, CreatureSpawnEvent.@Nullable SpawnReason creatureSpawnReason
    ) {
        return this.convertTo(entityType, conversionParams, spawnReason, e -> { afterConversion.finalizeConversion(e); return true; }, transformReason, creatureSpawnReason);
    }
    @Nullable
    public <T extends Mob> T convertTo(
        EntityType<T> entityType, ConversionParams conversionParams, EntitySpawnReason spawnReason, ConversionParams.CancellingAfterConversion<T> afterConversion, EntityTransformEvent.@Nullable TransformReason transformReason, CreatureSpawnEvent.@Nullable SpawnReason creatureSpawnReason
    ) {
        // Paper end - entity zap event - allow cancellation of conversion post creation
        if (this.isRemoved()) {
            return null;
        } else {
            T mob = (T)entityType.create(this.level(), spawnReason);
            if (mob == null) {
                return null;
            } else {
                conversionParams.type().convert(this, mob, conversionParams);
                if (!afterConversion.finalizeConversionOrCancel(mob)) return null; // Paper - entity zap event - return null if conversion was cancelled
                // CraftBukkit start
                if (transformReason == null) {
                    // Special handling for slime split and pig lightning
                    return mob;
                }

                if (org.bukkit.craftbukkit.event.CraftEventFactory.callEntityTransformEvent(this, mob, transformReason).isCancelled()) {
                    return null;
                }
                // CraftBukkit end
                if (this.level() instanceof ServerLevel serverLevel) {
                    serverLevel.addFreshEntity(mob, creatureSpawnReason); // CraftBukkit
                }

                if (conversionParams.type().shouldDiscardAfterConversion()) {
                    this.discard(EntityRemoveEvent.Cause.TRANSFORMATION); // CraftBukkit - add Bukkit remove cause
                }

                return mob;
            }
        }
    }

    public <T extends Mob> @Nullable T convertTo(
        EntityType<T> entityType, ConversionParams conversionParams, ConversionParams.AfterConversion<T> afterConversion
    ) {
        // Paper start - entity zap event - allow cancellation of conversion post creation
        return this.convertTo(entityType, conversionParams, afterConversion, EntityTransformEvent.TransformReason.UNKNOWN, CreatureSpawnEvent.SpawnReason.DEFAULT);
    }

    public <T extends Mob> @Nullable T convertTo(EntityType<T> entityType, ConversionParams conversionParams, ConversionParams.AfterConversion<T> afterConversion, EntityTransformEvent.@Nullable TransformReason transformReason, CreatureSpawnEvent.@Nullable SpawnReason creatureSpawnReason) {
        return this.convertTo(entityType, conversionParams, e -> { afterConversion.finalizeConversion(e); return true; }, transformReason, creatureSpawnReason);
    }

    public <T extends Mob> @Nullable T convertTo(EntityType<T> entityType, ConversionParams conversionParams, ConversionParams.CancellingAfterConversion<T> afterConversion, EntityTransformEvent.@Nullable TransformReason transformReason, CreatureSpawnEvent.@Nullable SpawnReason creatureSpawnReason) {
        return this.convertTo(entityType, conversionParams, EntitySpawnReason.CONVERSION, afterConversion, transformReason, creatureSpawnReason);
        // Paper end - entity zap event - allow cancellation of conversion post creation
    }

    @Override
    public Leashable.@Nullable LeashData getLeashData() {
        return this.leashData;
    }

    private void resetAngularLeashMomentum() {
        if (this.leashData != null) {
            this.leashData.angularMomentum = 0.0;
        }
    }

    @Override
    public void setLeashData(Leashable.@Nullable LeashData leashData) {
        this.leashData = leashData;
    }

    @Override
    public void onLeashRemoved() {
        if (this.getLeashData() == null) {
            this.clearHome();
        }
    }

    @Override
    public void leashTooFarBehaviour() {
        Leashable.super.leashTooFarBehaviour();
        this.goalSelector.disableControlFlag(Goal.Flag.MOVE);
    }

    @Override
    public boolean canBeLeashed() {
        return !(this instanceof Enemy);
    }

    @Override
    public boolean startRiding(Entity entity, boolean force, boolean triggerEvents) {
        boolean flag = super.startRiding(entity, force, triggerEvents);
        if (flag && this.isLeashed()) {
            // Paper start - Expand EntityUnleashEvent
            EntityUnleashEvent event = new EntityUnleashEvent(this.getBukkitEntity(), EntityUnleashEvent.UnleashReason.UNKNOWN, true);
            if (!event.callEvent()) {
                return flag;
            }
            if (event.isDropLeash()) {
                this.dropLeash();
            } else {
                this.removeLeash();
            }
            // Paper end - Expand EntityUnleashEvent
        }

        return flag;
    }

    @Override
    public boolean isEffectiveAi() {
        return super.isEffectiveAi() && !this.isNoAi();
    }

    public void setNoAi(boolean noAi) {
        byte b = this.entityData.get(DATA_MOB_FLAGS_ID);
        this.entityData.set(DATA_MOB_FLAGS_ID, noAi ? (byte)(b | 1) : (byte)(b & -2));
    }

    public void setLeftHanded(boolean leftHanded) {
        byte b = this.entityData.get(DATA_MOB_FLAGS_ID);
        this.entityData.set(DATA_MOB_FLAGS_ID, leftHanded ? (byte)(b | 2) : (byte)(b & -3));
    }

    public void setAggressive(boolean aggressive) {
        byte b = this.entityData.get(DATA_MOB_FLAGS_ID);
        this.entityData.set(DATA_MOB_FLAGS_ID, aggressive ? (byte)(b | 4) : (byte)(b & -5));
    }

    public boolean isNoAi() {
        return (this.entityData.get(DATA_MOB_FLAGS_ID) & 1) != 0;
    }

    public boolean isLeftHanded() {
        return (this.entityData.get(DATA_MOB_FLAGS_ID) & 2) != 0;
    }

    public boolean isAggressive() {
        return (this.entityData.get(DATA_MOB_FLAGS_ID) & 4) != 0;
    }

    public void setBaby(boolean baby) {
    }

    @Override
    public HumanoidArm getMainArm() {
        return this.isLeftHanded() ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
    }

    public boolean isWithinMeleeAttackRange(LivingEntity entity) {
        AttackRange attackRange = this.getActiveItem().get(DataComponents.ATTACK_RANGE);
        double d;
        double d1;
        if (attackRange == null) {
            d = DEFAULT_ATTACK_REACH;
            d1 = 0.0;
        } else {
            d = attackRange.effectiveMaxRange(this);
            d1 = attackRange.effectiveMinRange(this);
        }

        AABB hitbox = entity.getHitbox();
        return this.getAttackBoundingBox(d).intersects(hitbox) && (d1 <= 0.0 || !this.getAttackBoundingBox(d1).intersects(hitbox));
    }

    protected AABB getAttackBoundingBox(double horizontalExpansion) {
        Entity vehicle = this.getVehicle();
        AABB aabb;
        if (vehicle != null) {
            AABB boundingBox = vehicle.getBoundingBox();
            AABB boundingBox1 = this.getBoundingBox();
            aabb = new AABB(
                Math.min(boundingBox1.minX, boundingBox.minX),
                boundingBox1.minY,
                Math.min(boundingBox1.minZ, boundingBox.minZ),
                Math.max(boundingBox1.maxX, boundingBox.maxX),
                boundingBox1.maxY,
                Math.max(boundingBox1.maxZ, boundingBox.maxZ)
            );
        } else {
            aabb = this.getBoundingBox();
        }

        return aabb.inflate(horizontalExpansion, 0.0, horizontalExpansion);
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        float f = (float)this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        ItemStack weaponItem = this.getWeaponItem();
        DamageSource damageSource = weaponItem.getDamageSource(this, () -> this.damageSources().mobAttack(this));
        f = EnchantmentHelper.modifyDamage(level, weaponItem, target, damageSource, f);
        f += weaponItem.getItem().getAttackDamageBonus(target, f, damageSource);
        Vec3 deltaMovement = target.getDeltaMovement();
        boolean flag = target.hurtServer(level, damageSource, f);
        if (flag) {
            this.causeExtraKnockback(target, this.getKnockback(target, damageSource), deltaMovement);
            if (target instanceof LivingEntity livingEntity) {
                weaponItem.hurtEnemy(livingEntity, this);
            }

            EnchantmentHelper.doPostAttackEffects(level, target, damageSource);
            this.setLastHurtMob(target);
            this.playAttackSound();
        }

        this.lungeForwardMaybe();
        return flag;
    }

    @Override
    protected void jumpInLiquid(TagKey<Fluid> fluidTag) {
        if (this.getNavigation().canFloat()) {
            super.jumpInLiquid(fluidTag);
        } else {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0, 0.3, 0.0));
        }
    }

    @VisibleForTesting
    public void removeFreeWill() {
        this.removeAllGoals(goal -> true);
        this.getBrain().removeAllBehaviors();
    }

    public void removeAllGoals(Predicate<Goal> filter) {
        this.goalSelector.removeAllGoals(filter);
    }

    // Folia start - region threading
    @Override
    protected void postRemoveAfterChangingDimensions() {
        super.postRemoveAfterChangingDimensions();
        for (EquipmentSlot equipmentSlot : EquipmentSlot.VALUES) {
            ItemStack itemBySlot = this.getItemBySlot(equipmentSlot);
            if (!itemBySlot.isEmpty()) {
                itemBySlot.setCount(0);
            }
        }
    }
    // Folia end - region threading

    @Override
    protected void removeAfterChangingDimensions() {
        super.removeAfterChangingDimensions();
        // Folia start - region threading - move inventory clearing until after the dimension change - move into postRemoveAfterChangingDimensions
//        for (EquipmentSlot equipmentSlot : EquipmentSlot.VALUES) {
//            ItemStack itemBySlot = this.getItemBySlot(equipmentSlot);
//            if (!itemBySlot.isEmpty()) {
//                itemBySlot.setCount(0);
//            }
//        }
        // Folia end - region threading - move inventory clearing until after the dimension change - move into postRemoveAfterChangingDimensions
    }

    @Override
    public @Nullable ItemStack getPickResult() {
        SpawnEggItem spawnEggItem = SpawnEggItem.byId(this.getType());
        return spawnEggItem == null ? null : new ItemStack(spawnEggItem);
    }

    @Override
    protected void onAttributeUpdated(Holder<Attribute> attribute) {
        super.onAttributeUpdated(attribute);
        if (attribute.is(Attributes.FOLLOW_RANGE) || attribute.is(Attributes.TEMPT_RANGE)) {
            this.getNavigation().updatePathfinderMaxVisitedNodes();
        }
    }

    @Override
    public void registerDebugValues(ServerLevel level, DebugValueSource.Registration registrar) {
        registrar.register(DebugSubscriptions.ENTITY_PATHS, () -> {
            Path path = this.getNavigation().getPath();
            return path != null && path.debugData() != null ? new DebugPathInfo(path.copy(), this.getNavigation().getMaxDistanceToWaypoint()) : null;
        });
        registrar.register(
            DebugSubscriptions.GOAL_SELECTORS,
            () -> {
                Set<WrappedGoal> availableGoals = this.goalSelector.getAvailableGoals();
                List<DebugGoalInfo.DebugGoal> list = new ArrayList<>(availableGoals.size());
                availableGoals.forEach(
                    wrappedGoal -> list.add(
                        new DebugGoalInfo.DebugGoal(wrappedGoal.getPriority(), wrappedGoal.isRunning(), wrappedGoal.getGoal() instanceof final com.destroystokyo.paper.entity.ai.PaperCustomGoal<?> customGoal ? customGoal.getHandle().getClass().getSimpleName() + "*" : wrappedGoal.getGoal().getClass().getSimpleName()) // Paper - display right custom goal in debugging
                    )
                );
                return new DebugGoalInfo(list);
            }
        );
        if (!this.brain.isBrainDead()) {
            registrar.register(DebugSubscriptions.BRAINS, () -> DebugBrainDump.takeBrainDump(level, this));
        }
    }

    public float chargeSpeedModifier() {
        return 1.0F;
    }

    // KioCG start
    @Override
    public boolean shouldTickHot() {
        return super.shouldTickHot() && (!this.removeWhenFarAway(0.0) || this.isPersistenceRequired() || this.requiresCustomPersistence());
    }
    // KioCG end
}
