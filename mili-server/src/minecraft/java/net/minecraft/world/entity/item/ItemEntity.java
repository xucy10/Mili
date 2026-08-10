package net.minecraft.world.entity.item;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
// Leaves start - Lithium Sleeping Block Entity
import org.leavesmc.leaves.lithium.common.util.change_tracking.ChangePublisher;
import org.leavesmc.leaves.lithium.common.util.change_tracking.ChangeSubscriber;
// Leaves end - Lithium Sleeping Block Entity

public class ItemEntity extends Entity implements TraceableEntity, ChangePublisher<ItemEntity>, ChangeSubscriber.CountChangeSubscriber<ItemStack> { // Leaves - Lithium Sleeping Block Entity
    private static final EntityDataAccessor<ItemStack> DATA_ITEM = SynchedEntityData.defineId(ItemEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final float FLOAT_HEIGHT = 0.1F;
    public static final float EYE_HEIGHT = 0.2125F;
    private static final int LIFETIME = 6000;
    private static final int INFINITE_PICKUP_DELAY = 32767;
    private static final int INFINITE_LIFETIME = -32768;
    private static final int DEFAULT_HEALTH = 5;
    private static final short DEFAULT_AGE = 0;
    private static final short DEFAULT_PICKUP_DELAY = 0;
    public int age = 0;
    public int pickupDelay = 0;
    public int health = 5;
    public @Nullable EntityReference<Entity> thrower;
    public @Nullable UUID target;
    public final float bobOffs = this.random.nextFloat() * (float) Math.PI * 2.0F;
    public boolean canMobPickup = true; // Paper - Item#canEntityPickup
    private int despawnRate = -1; // Paper - Alternative item-despawn-rate
    public net.kyori.adventure.util.TriState frictionState = net.kyori.adventure.util.TriState.NOT_SET; // Paper - Friction API

    public ItemEntity(EntityType<? extends ItemEntity> type, Level level) {
        super(type, level);
        this.setYRot(this.random.nextFloat() * 360.0F);
    }

    public ItemEntity(Level level, double posX, double posY, double posZ, ItemStack stack) {
        // Paper start - Don't use level random in entity constructors (to make them thread-safe)
        this(EntityType.ITEM, level);
        this.setPos(posX, posY, posZ);
        this.setDeltaMovement(this.random.nextDouble() * 0.2 - 0.1, 0.2, this.random.nextDouble() * 0.2 - 0.1);
        this.setItem(stack);
        // Paper end - Don't use level random in entity constructors
    }

    public ItemEntity(Level level, double posX, double posY, double posZ, ItemStack stack, double deltaX, double deltaY, double deltaZ) {
        this(EntityType.ITEM, level);
        this.setPos(posX, posY, posZ);
        this.setDeltaMovement(deltaX, deltaY, deltaZ);
        this.setItem(stack);
    }

    // Paper start - Require item entities to send their location precisely (Fixes MC-4)
    {
        if (io.papermc.paper.configuration.GlobalConfiguration.get().misc.sendFullPosForItemEntities) {
            this.setRequiresPrecisePosition(true);
        }
    }
    // Paper end - Require item entities to send their location precisely (Fixes MC-4)

    @Override
    public boolean dampensVibrations() {
        return this.getItem().is(ItemTags.DAMPENS_VIBRATIONS);
    }

    @Override
    public @Nullable Entity getOwner() {
        return EntityReference.getEntity(this.thrower, this.level());
    }

    @Override
    public void restoreFrom(Entity entity) {
        super.restoreFrom(entity);
        if (entity instanceof ItemEntity itemEntity) {
            this.thrower = itemEntity.thrower;
        }
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.NONE;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_ITEM, ItemStack.EMPTY);
    }

    @Override
    protected double getDefaultGravity() {
        return 0.04;
    }

    // Paper start - EAR 2
    @Override
    public void inactiveTick() {
        super.inactiveTick();
        if (this.pickupDelay > 0 && this.pickupDelay != 32767) {
            this.pickupDelay--;
        }
        if (this.age != -32768) {
            this.age++;
        }

        if (!this.level().isClientSide() && this.age >= this.despawnRate) {// Paper - Alternative item-despawn-rate
            // CraftBukkit start - fire ItemDespawnEvent
            if (org.bukkit.craftbukkit.event.CraftEventFactory.callItemDespawnEvent(this).isCancelled()) {
                this.age = 0;
                return;
            }
            // CraftBukkit end
            this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
        }
    }
    // Paper end - EAR 2

    @Override
    public void tick() {
        if (this.getItem().isEmpty()) {
            this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
        } else {
            super.tick();
            if (this.pickupDelay > 0 && this.pickupDelay != 32767) {
                this.pickupDelay--;
            }

            this.xo = this.getX();
            this.yo = this.getY();
            this.zo = this.getZ();
            Vec3 deltaMovement = this.getDeltaMovement();
            if (this.isInWater() && this.getFluidHeight(FluidTags.WATER) > 0.1F) {
                this.setUnderwaterMovement();
            } else if (this.isInLava() && this.getFluidHeight(FluidTags.LAVA) > 0.1F) {
                this.setUnderLavaMovement();
            } else {
                this.applyGravity();
            }

            if (this.level().isClientSide()) {
                this.noPhysics = false;
            } else {
                this.noPhysics = !this.level().noCollision(this, this.getBoundingBox().deflate(1.0E-7));
                if (this.noPhysics) {
                    this.moveTowardsClosestSpace(this.getX(), (this.getBoundingBox().minY + this.getBoundingBox().maxY) / 2.0, this.getZ());
                }
            }

            if (!this.onGround() || this.getDeltaMovement().horizontalDistanceSqr() > 1.0E-5F || (this.tickCount + this.getId()) % 4 == 0) { // Paper - Diff on change; ActivationRange immunity
                this.move(MoverType.SELF, this.getDeltaMovement());
                this.applyEffectsFromBlocks();
                float f = 0.98F;
                // Paper start - Friction API
                if (this.frictionState == net.kyori.adventure.util.TriState.FALSE) {
                    f = 1F;
                } else if (this.onGround()) {
                    // Paper end - Friction API
                    f = this.level().getBlockState(this.getBlockPosBelowThatAffectsMyMovement()).getBlock().getFriction() * 0.98F;
                }

                this.setDeltaMovement(this.getDeltaMovement().multiply(f, 0.98, f));
                if (this.onGround()) {
                    Vec3 deltaMovement1 = this.getDeltaMovement();
                    if (deltaMovement1.y < 0.0) {
                        this.setDeltaMovement(deltaMovement1.multiply(1.0, -0.5, 1.0));
                    }
                }
            }

            boolean flag = Mth.floor(this.xo) != Mth.floor(this.getX())
                || Mth.floor(this.yo) != Mth.floor(this.getY())
                || Mth.floor(this.zo) != Mth.floor(this.getZ());
            int i = flag ? 2 : 40;
            if (this.tickCount % i == 0 && !this.level().isClientSide() && this.isMergable()) {
                this.mergeWithNeighbours();
            }

            if (this.age != -32768) {
                this.age++;
            }

            this.needsSync = this.needsSync | this.updateInWaterStateAndDoFluidPushing();
            if (!this.level().isClientSide()) {
                double d = this.getDeltaMovement().subtract(deltaMovement).lengthSqr();
                if (d > 0.01) {
                    this.needsSync = true;
                }
            }

            if (!this.level().isClientSide() && this.age >= this.despawnRate) { // Spigot // Paper - Alternative item-despawn-rate
                // CraftBukkit start - fire ItemDespawnEvent
                if (org.bukkit.craftbukkit.event.CraftEventFactory.callItemDespawnEvent(this).isCancelled()) {
                    this.age = 0;
                    return;
                }
                // CraftBukkit end
                this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
            }
        }
    }

    @Override
    public BlockPos getBlockPosBelowThatAffectsMyMovement() {
        return this.getOnPos(0.999999F);
    }

    private void setUnderwaterMovement() {
        this.setFluidMovement(0.99F);
    }

    private void setUnderLavaMovement() {
        this.setFluidMovement(0.95F);
    }

    private void setFluidMovement(double multiplier) {
        Vec3 deltaMovement = this.getDeltaMovement();
        this.setDeltaMovement(deltaMovement.x * multiplier, deltaMovement.y + (deltaMovement.y < 0.06F ? 5.0E-4F : 0.0F), deltaMovement.z * multiplier);
    }

    private void mergeWithNeighbours() {
        if (this.isMergable()) {
            double radius = this.level().spigotConfig.itemMerge; // Spigot
            for (ItemEntity itemEntity : this.level()
                .getEntitiesOfClass(ItemEntity.class, this.getBoundingBox().inflate(radius, this.level().paperConfig().entities.behavior.onlyMergeItemsHorizontally ? 0 : radius - 0.5D, radius), neighbour -> neighbour != this && neighbour.isMergable())) { // Spigot // Paper - configuration to only merge items horizontally
                if (itemEntity.isMergable()) {
                    // Paper start - Fix items merging through walls
                    if (this.level().paperConfig().fixes.fixItemsMergingThroughWalls) {
                        if (this.level().clipDirect(this.position(), itemEntity.position(),
                            net.minecraft.world.phys.shapes.CollisionContext.of(this)) == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                            continue;
                        }
                    }
                    // Paper end - Fix items merging through walls
                    this.tryToMerge(itemEntity);
                    if (this.isRemoved()) {
                        break;
                    }
                }
            }
        }
    }

    private boolean isMergable() {
        ItemStack item = this.getItem();
        return this.isAlive() && this.pickupDelay != 32767 && this.age != -32768 && this.age < this.despawnRate && item.getCount() < org.leavesmc.leaves.util.ItemOverstackUtils.getItemStackMaxCount(item); // Paper - Alternative item-despawn-rate // Mili - item over-stack util
    }

    private void tryToMerge(ItemEntity itemEntity) {
        // Mili start - item over-stack util
        if (org.leavesmc.leaves.util.ItemOverstackUtils.tryStackItems(this, itemEntity)) {
            return;
        }
        // Mili end - item over-stack util
        ItemStack item = this.getItem();
        ItemStack item1 = itemEntity.getItem();
        if (Objects.equals(this.target, itemEntity.target) && areMergable(item, item1)) {
            if (fun.bm.mili.config.modules.misc.ItemEntityConfig.followTickSequenceMerge || item1.getCount() < item.getCount()) { // Mili - add follow Tick Sequence Merge, see Paper#13073
                merge(this, item, itemEntity, item1);
            } else {
                merge(itemEntity, item1, this, item);
            }
        }
    }

    public static boolean areMergable(ItemStack destinationStack, ItemStack originStack) {
        return originStack.getCount() + destinationStack.getCount() <= originStack.getMaxStackSize()
            && ItemStack.isSameItemSameComponents(destinationStack, originStack);
    }

    public static ItemStack merge(ItemStack destinationStack, ItemStack originStack, int amount) {
        int min = Math.min(Math.min(destinationStack.getMaxStackSize(), amount) - destinationStack.getCount(), originStack.getCount());
        ItemStack itemStack = destinationStack.copyWithCount(destinationStack.getCount() + min);
        originStack.shrink(min);
        return itemStack;
    }

    private static void merge(ItemEntity destinationEntity, ItemStack destinationStack, ItemStack originStack) {
        ItemStack itemStack = merge(destinationStack, originStack, 64);
        destinationEntity.setItem(itemStack);
    }

    private static void merge(ItemEntity destinationEntity, ItemStack destinationStack, ItemEntity originEntity, ItemStack originStack) {
        // CraftBukkit start
        if (!org.bukkit.craftbukkit.event.CraftEventFactory.callItemMergeEvent(originEntity, destinationEntity)) {
            return;
        }
        // CraftBukkit end
        merge(destinationEntity, destinationStack, originStack);
        destinationEntity.pickupDelay = Math.max(destinationEntity.pickupDelay, originEntity.pickupDelay);
        destinationEntity.age = Math.min(destinationEntity.age, originEntity.age);
        if (originStack.isEmpty()) {
            originEntity.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.MERGE); // CraftBukkit - add Bukkit remove cause
        }
    }

    @Override
    public boolean fireImmune() {
        return !this.getItem().canBeHurtBy(this.damageSources().inFire()) || super.fireImmune();
    }

    @Override
    protected boolean shouldPlayLavaHurtSound() {
        return this.health <= 0 || this.tickCount % 10 == 0;
    }

    @Override
    public final boolean hurtClient(DamageSource damageSource) {
        return !this.isInvulnerableToBase(damageSource) && this.getItem().canBeHurtBy(damageSource);
    }

    @Override
    public final boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        if (this.isInvulnerableToBase(damageSource)) {
            return false;
        } else if (!level.getGameRules().get(GameRules.MOB_GRIEFING) && damageSource.getEntity() instanceof Mob) {
            return false;
        } else if (!this.getItem().canBeHurtBy(damageSource)) {
            return false;
        } else {
            // CraftBukkit start
            if (org.bukkit.craftbukkit.event.CraftEventFactory.handleNonLivingEntityDamageEvent(this, damageSource, amount)) {
                return false;
            }
            // CraftBukkit end
            this.markHurt();
            this.health = (int)(this.health - amount);
            this.gameEvent(GameEvent.ENTITY_DAMAGE, damageSource.getEntity());
            if (this.health <= 0) {
                this.getItem().onDestroyed(this);
                this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.DEATH); // CraftBukkit - add Bukkit remove cause
            }

            return true;
        }
    }

    @Override
    public boolean ignoreExplosion(Explosion explosion) {
        return !explosion.shouldAffectBlocklikeEntities() || super.ignoreExplosion(explosion);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putShort("Health", (short)this.health);
        output.putShort("Age", (short)this.age);
        output.putShort("PickupDelay", (short)this.pickupDelay);
        EntityReference.store(this.thrower, output, "Thrower");
        output.storeNullable("Owner", UUIDUtil.CODEC, this.target);
        if (!this.getItem().isEmpty()) {
            output.store("Item", ItemStack.CODEC, this.getItem());
        }
        // Paper start - Friction API
        if (this.frictionState != net.kyori.adventure.util.TriState.NOT_SET) {
            output.putString("Paper.FrictionState", this.frictionState.toString());
        }
        // Paper end - Friction API
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.health = input.getShortOr("Health", (short)5);
        this.age = input.getShortOr("Age", (short)0);
        this.pickupDelay = input.getShortOr("PickupDelay", (short)0);
        this.target = input.read("Owner", UUIDUtil.CODEC).orElse(null);
        this.thrower = EntityReference.read(input, "Thrower");
        this.setItem(input.read("Item", ItemStack.CODEC).orElse(ItemStack.EMPTY));
        // Paper start - Friction API
        input.getString("Paper.FrictionState").ifPresent(frictionState -> {
            try {
                this.frictionState = net.kyori.adventure.util.TriState.valueOf(frictionState);
            } catch (Exception ignored) {
                com.mojang.logging.LogUtils.getLogger().error("Unknown friction state {} for {}", frictionState, this);
            }
        });
        // Paper end - Friction API
        if (this.getItem().isEmpty()) {
            this.discard(null); // CraftBukkit - add Bukkit remove cause
        }
    }

    @Override
    public void playerTouch(Player entity) {
        if (!this.level().isClientSide()) {
            ItemStack item = this.getItem();
            Item item1 = item.getItem();
            int count = item.getCount();
            // CraftBukkit start - fire PlayerPickupItemEvent
            int canHold = entity.hasInfiniteMaterials() ? count : entity.getInventory().canHold(item); // Luminol - Fix creative item picking
            int remaining = count - canHold;
            boolean flyAtPlayer = false; // Paper

            // Paper start - PlayerAttemptPickupItemEvent
            if (this.pickupDelay <= 0) {
                org.bukkit.event.player.PlayerAttemptPickupItemEvent attemptEvent = new org.bukkit.event.player.PlayerAttemptPickupItemEvent((org.bukkit.entity.Player) entity.getBukkitEntity(), (org.bukkit.entity.Item) this.getBukkitEntity(), remaining);
                this.level().getCraftServer().getPluginManager().callEvent(attemptEvent);

                flyAtPlayer = attemptEvent.getFlyAtPlayer();
                if (attemptEvent.isCancelled()) {
                    if (flyAtPlayer) {
                        entity.take(this, count);
                    }

                    return;
                }
            }

            if (this.pickupDelay <= 0 && canHold > 0) {
                item.setCount(canHold);
                // Call legacy event
                org.bukkit.event.player.PlayerPickupItemEvent playerEvent = new org.bukkit.event.player.PlayerPickupItemEvent((org.bukkit.entity.Player) entity.getBukkitEntity(), (org.bukkit.entity.Item) this.getBukkitEntity(), remaining);
                playerEvent.setCancelled(!playerEvent.getPlayer().getCanPickupItems());
                this.level().getCraftServer().getPluginManager().callEvent(playerEvent);
                flyAtPlayer = playerEvent.getFlyAtPlayer(); // Paper
                if (playerEvent.isCancelled()) {
                    item.setCount(count); // SPIGOT-5294 - restore count
                    // Paper start
                    if (flyAtPlayer) {
                        entity.take(this, count);
                    }
                    // Paper end
                    return;
                }

                // Call newer event afterwards
                org.bukkit.event.entity.EntityPickupItemEvent entityEvent = new org.bukkit.event.entity.EntityPickupItemEvent(entity.getBukkitEntity(), (org.bukkit.entity.Item) this.getBukkitEntity(), remaining);
                entityEvent.setCancelled(!entityEvent.getEntity().getCanPickupItems());
                this.level().getCraftServer().getPluginManager().callEvent(entityEvent);
                if (entityEvent.isCancelled()) {
                    item.setCount(count); // SPIGOT-5294 - restore count
                    return;
                }

                // Update the ItemStack if it was changed in the event
                ItemStack current = this.getItem();
                if (!item.equals(current)) {
                    item = current;
                } else {
                    item.setCount(canHold + remaining); // = i
                }

                // Possibly < 0; fix here so we do not have to modify code below
                this.pickupDelay = 0;
            } else if (this.pickupDelay == 0) {
                // ensure that the code below isn't triggered if canHold says we can't pick the items up
                this.pickupDelay = -1;
            }
            // CraftBukkit end
            // Paper end - PlayerAttemptPickupItemEvent
            if (this.pickupDelay == 0 && (this.target == null || this.target.equals(entity.getUUID())) && entity.getInventory().add(item)) {
                if (flyAtPlayer) // Paper - PlayerPickupItemEvent
                entity.take(this, count);
                if (item.isEmpty()) {
                    this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.PICKUP); // CraftBukkit - add Bukkit remove cause
                    item.setCount(count);
                }

                entity.awardStat(Stats.ITEM_PICKED_UP.get(item1), count);
                entity.onItemPickup(this);
            }
        }
    }

    @Override
    public Component getName() {
        Component customName = this.getCustomName();
        return customName != null ? customName : this.getItem().getItemName();
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    // Folia start - region threading
    @Override
    public void postChangeDimension() {
        super.postChangeDimension();
        if (!this.level().isClientSide() && this instanceof ItemEntity itemEntity) {
            itemEntity.mergeWithNeighbours();
        }
    }
    // Folia end - region threading

    @Override
    public @Nullable Entity teleport(TeleportTransition teleportTransition) {
        Entity entity = super.teleport(teleportTransition);
        if (entity != null) entity.postChangeDimension(); // Folia - region threading - move to post change

        return entity;
    }

    public ItemStack getItem() {
        return this.getEntityData().get(DATA_ITEM);
    }

    public void setItem(ItemStack stack) {
        // Leaves start - Lithium Sleeping Block Entity
        if (me.earthme.luminol.config.modules.optimizations.LeavesSleepingBlockEntityConfig.enabled && this.subscriber != null) {
            ItemStack oldStack = this.getItem();
            if (oldStack != stack) {
                if (!oldStack.isEmpty()) {
                    oldStack.lithium$unsubscribe(this);
                }

                if (!stack.isEmpty()) {
                    stack.lithium$subscribe(this, this.subscriberData);
                    this.subscriber.lithium$notify((ItemEntity) (Object) this, this.subscriberData);
                } else {
                    this.subscriber.lithium$forceUnsubscribe((ItemEntity) (Object) this, this.subscriberData);
                    this.subscriber = null;
                    this.subscriberData = 0;
                }
            }
        }
        // Leaves end - Lithium Sleeping Block Entity
        this.getEntityData().set(DATA_ITEM, stack);
        this.despawnRate = this.level().paperConfig().entities.spawning.altItemDespawnRate.enabled ? this.level().paperConfig().entities.spawning.altItemDespawnRate.items.getOrDefault(stack.getItem(), this.level().spigotConfig.itemDespawnRate) : this.level().spigotConfig.itemDespawnRate; // Paper - Alternative item-despawn-rate
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_ITEM.equals(key)) {
            this.getItem().setEntityRepresentation(this);
        }
    }

    public void setTarget(@Nullable UUID target) {
        this.target = target;
    }

    public void setThrower(Entity thrower) {
        this.thrower = EntityReference.of(thrower);
    }

    public int getAge() {
        return this.age;
    }

    public void setDefaultPickUpDelay() {
        this.pickupDelay = 10;
    }

    public void setNoPickUpDelay() {
        this.pickupDelay = 0;
    }

    public void setNeverPickUp() {
        this.pickupDelay = 32767;
    }

    public void setPickUpDelay(int pickupDelay) {
        this.pickupDelay = pickupDelay;
    }

    public boolean hasPickUpDelay() {
        return this.pickupDelay > 0;
    }

    public void setUnlimitedLifetime() {
        this.age = -32768;
    }

    public void setExtendedLifetime() {
        this.age = -6000;
    }

    public void makeFakeItem() {
        this.setNeverPickUp();
        this.age = this.despawnRate - 1; // Spigot // Paper - Alternative item-despawn-rate
    }

    public static float getSpin(float age, float bobOffset) {
        return age / 20.0F + bobOffset;
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.AMBIENT;
    }

    @Override
    public float getVisualRotationYInDegrees() {
        return 180.0F - getSpin(this.getAge() + 0.5F, this.bobOffs) / (float) (Math.PI * 2) * 360.0F;
    }

    @Override
    public @Nullable SlotAccess getSlot(int slot) {
        return slot == 0 ? SlotAccess.of(this::getItem, this::setItem) : super.getSlot(slot);
    }

    // Leaves start - Lithium Sleeping Block Entity
    private ChangeSubscriber<ItemEntity> subscriber;
    //Stores the data of the subscriber, unless the subscriber is a Multi which stores the data in a list, in which case this variable stores 0
    private int subscriberData;

    private void startTrackingChanges() {
        ItemStack stack = this.getItem();
        if (!stack.isEmpty()) {
            stack.lithium$subscribe(this, 0);
        }
    }

    @Override
    public void lithium$subscribe(ChangeSubscriber<ItemEntity> subscriber, int subscriberData) {
        if (this.subscriber == null) {
            this.startTrackingChanges();
        }
        this.subscriber = ChangeSubscriber.combine(this.subscriber, this.subscriberData, subscriber, subscriberData);
        if (this.subscriber instanceof ChangeSubscriber.Multi<?>) {
            this.subscriberData = 0;
        } else {
            this.subscriberData = subscriberData;
        }
    }

    @Override
    public int lithium$unsubscribe(ChangeSubscriber<ItemEntity> subscriber) {
        int retval = ChangeSubscriber.dataOf(this.subscriber, subscriber, this.subscriberData);
        this.subscriberData = ChangeSubscriber.dataWithout(this.subscriber, subscriber, this.subscriberData);
        this.subscriber = ChangeSubscriber.without(this.subscriber, subscriber);

        if (this.subscriber == null) {
            ItemStack stack = this.getItem();
            if (!stack.isEmpty()) {
                stack.lithium$unsubscribe(this);
            }
        }
        return retval;
    }

    @Override
    public void lithium$notify(ItemStack publisher, int subscriberData) {
        if (publisher != this.getItem()) {
            throw new IllegalStateException("Received notification from an unexpected publisher");
        }

        if (this.subscriber != null) {
            this.subscriber.lithium$notify(this, this.subscriberData);
        }
    }

    @Override
    public void lithium$forceUnsubscribe(ItemStack publisher, int subscriberData) {
        if (this.subscriber != null) {
            this.subscriber.lithium$forceUnsubscribe(this, this.subscriberData);
            this.subscriber = null;
            this.subscriberData = 0;
        }
    }

    @Override
    public void lithium$notifyCount(ItemStack publisher, int subscriberData, int newCount) {
        if (publisher != this.getItem()) {
            throw new IllegalStateException("Received notification from an unexpected publisher");
        }

        if (this.subscriber instanceof ChangeSubscriber.CountChangeSubscriber<ItemEntity> countChangeSubscriber) {
            countChangeSubscriber.lithium$notifyCount(this, this.subscriberData, newCount);
        }
    }
    // Leaves end - Lithium Sleeping Block Entity
}
