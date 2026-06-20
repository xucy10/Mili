package net.minecraft.world.entity.animal.allay;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Dynamic;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.GameEventTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.DynamicGameEventListener;
import net.minecraft.world.level.gameevent.EntityPositionSource;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class Allay extends PathfinderMob implements InventoryCarrier, VibrationSystem {
    private static final Vec3i ITEM_PICKUP_REACH = new Vec3i(1, 1, 1);
    private static final int LIFTING_ITEM_ANIMATION_DURATION = 5;
    private static final float DANCING_LOOP_DURATION = 55.0F;
    private static final float SPINNING_ANIMATION_DURATION = 15.0F;
    private static final int DEFAULT_DUPLICATION_COOLDOWN = 0;
    private static final int DUPLICATION_COOLDOWN_TICKS = 6000;
    private static final int NUM_OF_DUPLICATION_HEARTS = 3;
    public static final int MAX_NOTEBLOCK_DISTANCE = 1024;
    private static final EntityDataAccessor<Boolean> DATA_DANCING = SynchedEntityData.defineId(Allay.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_CAN_DUPLICATE = SynchedEntityData.defineId(Allay.class, EntityDataSerializers.BOOLEAN);
    protected static final ImmutableList<SensorType<? extends Sensor<? super Allay>>> SENSOR_TYPES = ImmutableList.of(
        SensorType.NEAREST_LIVING_ENTITIES, SensorType.NEAREST_PLAYERS, SensorType.HURT_BY, SensorType.NEAREST_ITEMS
    );
    protected static final ImmutableList<MemoryModuleType<?>> MEMORY_TYPES = ImmutableList.of(
        MemoryModuleType.PATH,
        MemoryModuleType.LOOK_TARGET,
        MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
        MemoryModuleType.WALK_TARGET,
        MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE,
        MemoryModuleType.HURT_BY,
        MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM,
        MemoryModuleType.LIKED_PLAYER,
        MemoryModuleType.LIKED_NOTEBLOCK_POSITION,
        MemoryModuleType.LIKED_NOTEBLOCK_COOLDOWN_TICKS,
        MemoryModuleType.ITEM_PICKUP_COOLDOWN_TICKS,
        MemoryModuleType.IS_PANICKING
    );
    public static final ImmutableList<Float> THROW_SOUND_PITCHES = ImmutableList.of(
        0.5625F, 0.625F, 0.75F, 0.9375F, 1.0F, 1.0F, 1.125F, 1.25F, 1.5F, 1.875F, 2.0F, 2.25F, 2.5F, 3.0F, 3.75F, 4.0F
    );
    private final DynamicGameEventListener<VibrationSystem.Listener> dynamicVibrationListener;
    private VibrationSystem.Data vibrationData;
    private final VibrationSystem.User vibrationUser;
    private final DynamicGameEventListener<Allay.JukeboxListener> dynamicJukeboxListener;
    private final SimpleContainer inventory = new SimpleContainer(1);
    public @Nullable BlockPos jukeboxPos;
    public long duplicationCooldown = 0L;
    private float holdingItemAnimationTicks;
    private float holdingItemAnimationTicks0;
    private float dancingAnimationTicks;
    private float spinningAnimationTicks;
    private float spinningAnimationTicks0;
    public boolean forceDancing = false; // CraftBukkit

    public Allay(EntityType<? extends Allay> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 20, true);
        this.setCanPickUpLoot(this.canPickUpLoot());
        this.vibrationUser = new Allay.VibrationUser();
        this.vibrationData = new VibrationSystem.Data();
        this.dynamicVibrationListener = new DynamicGameEventListener<>(new VibrationSystem.Listener(this));
        this.dynamicJukeboxListener = new DynamicGameEventListener<>(
            new Allay.JukeboxListener(this.vibrationUser.getPositionSource(), GameEvent.JUKEBOX_PLAY.value().notificationRadius())
        );
    }

    // CraftBukkit start
    public void setCanDuplicate(boolean canDuplicate) {
        this.entityData.set(Allay.DATA_CAN_DUPLICATE, canDuplicate);
    }
    // CraftBukkit end

    @Override
    protected Brain.Provider<Allay> brainProvider() {
        return Brain.provider(MEMORY_TYPES, SENSOR_TYPES);
    }

    @Override
    protected Brain<?> makeBrain(Dynamic<?> dynamic) {
        return AllayAi.makeBrain(this.brainProvider().makeBrain(dynamic));
    }

    @Override
    public Brain<Allay> getBrain() {
        return (Brain<Allay>)super.getBrain();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 20.0)
            .add(Attributes.FLYING_SPEED, 0.1F)
            .add(Attributes.MOVEMENT_SPEED, 0.1F)
            .add(Attributes.ATTACK_DAMAGE, 2.0);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation flyingPathNavigation = new FlyingPathNavigation(this, level);
        flyingPathNavigation.setCanOpenDoors(false);
        flyingPathNavigation.setCanFloat(true);
        flyingPathNavigation.setRequiredPathLength(48.0F);
        return flyingPathNavigation;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_DANCING, false);
        builder.define(DATA_CAN_DUPLICATE, true);
    }

    @Override
    public void travel(Vec3 travelVector) {
        this.travelFlying(travelVector, this.getSpeed());
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        return !this.isLikedPlayer(damageSource.getEntity()) && super.hurtServer(level, damageSource, amount);
    }

    @Override
    protected boolean considersEntityAsAlly(Entity entity) {
        return this.isLikedPlayer(entity) || super.considersEntityAsAlly(entity);
    }

    private boolean isLikedPlayer(@Nullable Entity entity) {
        if (!(entity instanceof Player player)) {
            return false;
        } else {
            Optional<UUID> memory = this.getBrain().getMemory(MemoryModuleType.LIKED_PLAYER);
            return memory.isPresent() && player.getUUID().equals(memory.get());
        }
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    @Override
    public SoundEvent getAmbientSound() {
        return this.hasItemInSlot(EquipmentSlot.MAINHAND) ? SoundEvents.ALLAY_AMBIENT_WITH_ITEM : SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM;
    }

    @Override
    public SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.ALLAY_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.ALLAY_DEATH;
    }

    @Override
    public float getSoundVolume() {
        return 0.4F;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        ProfilerFiller profilerFiller = Profiler.get();
        profilerFiller.push("allayBrain");
        this.getBrain().tick(level, this);
        profilerFiller.pop();
        profilerFiller.push("allayActivityUpdate");
        AllayAi.updateActivity(this);
        profilerFiller.pop();
        super.customServerAiStep(level);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide() && this.isAlive() && this.tickCount % 10 == 0) {
            this.heal(1.0F, org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason.REGEN); // CraftBukkit
        }

        if (this.isDancing() && this.shouldStopDancing() && this.tickCount % 20 == 0) {
            this.setDancing(false);
            this.jukeboxPos = null;
        }

        this.updateDuplicationCooldown();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            this.holdingItemAnimationTicks0 = this.holdingItemAnimationTicks;
            if (this.hasItemInHand()) {
                this.holdingItemAnimationTicks = Mth.clamp(this.holdingItemAnimationTicks + 1.0F, 0.0F, 5.0F);
            } else {
                this.holdingItemAnimationTicks = Mth.clamp(this.holdingItemAnimationTicks - 1.0F, 0.0F, 5.0F);
            }

            if (this.isDancing()) {
                this.dancingAnimationTicks++;
                this.spinningAnimationTicks0 = this.spinningAnimationTicks;
                if (this.isSpinning()) {
                    this.spinningAnimationTicks++;
                } else {
                    this.spinningAnimationTicks--;
                }

                this.spinningAnimationTicks = Mth.clamp(this.spinningAnimationTicks, 0.0F, 15.0F);
            } else {
                this.dancingAnimationTicks = 0.0F;
                this.spinningAnimationTicks = 0.0F;
                this.spinningAnimationTicks0 = 0.0F;
            }
        } else {
            VibrationSystem.Ticker.tick(this.level(), this.vibrationData, this.vibrationUser);
            if (this.isPanicking()) {
                this.setDancing(false);
            }
        }
    }

    @Override
    public boolean canPickUpLoot() {
        return !this.isOnPickupCooldown() && this.hasItemInHand();
    }

    public boolean hasItemInHand() {
        return !this.getItemInHand(InteractionHand.MAIN_HAND).isEmpty();
    }

    @Override
    protected boolean canDispenserEquipIntoSlot(EquipmentSlot slot) {
        return false;
    }

    private boolean isOnPickupCooldown() {
        return this.getBrain().checkMemory(MemoryModuleType.ITEM_PICKUP_COOLDOWN_TICKS, MemoryStatus.VALUE_PRESENT);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemInHand = player.getItemInHand(hand);
        ItemStack itemInHand1 = this.getItemInHand(InteractionHand.MAIN_HAND);
        if (this.isDancing() && itemInHand.is(ItemTags.DUPLICATES_ALLAYS) && this.canDuplicate()) {
            // CraftBukkit start - handle cancel duplication
            Allay allay = this.duplicateAllay();
            if (allay == null) {
                return InteractionResult.SUCCESS;
            }
            // CraftBukkit end
            this.level().broadcastEntityEvent(this, EntityEvent.IN_LOVE_HEARTS);
            this.level().playSound(player, this, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 2.0F, 1.0F);
            this.removeInteractionItem(player, itemInHand);
            return InteractionResult.SUCCESS;
        } else if (itemInHand1.isEmpty() && !itemInHand.isEmpty()) {
            ItemStack itemStack = itemInHand.copyWithCount(1);
            this.setItemInHand(InteractionHand.MAIN_HAND, itemStack);
            this.removeInteractionItem(player, itemInHand);
            this.level().playSound(player, this, SoundEvents.ALLAY_ITEM_GIVEN, SoundSource.NEUTRAL, 2.0F, 1.0F);
            this.getBrain().setMemory(MemoryModuleType.LIKED_PLAYER, player.getUUID());
            return InteractionResult.SUCCESS;
        } else if (!itemInHand1.isEmpty() && hand == InteractionHand.MAIN_HAND && itemInHand.isEmpty()) {
            this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            this.level().playSound(player, this, SoundEvents.ALLAY_ITEM_TAKEN, SoundSource.NEUTRAL, 2.0F, 1.0F);
            this.swing(InteractionHand.MAIN_HAND);

            for (ItemStack itemStack1 : this.getInventory().removeAllItems()) {
                BehaviorUtils.throwItem(this, itemStack1, this.position());
            }

            this.getBrain().eraseMemory(MemoryModuleType.LIKED_PLAYER);
            player.addItem(itemInHand1);
            return InteractionResult.SUCCESS;
        } else {
            return super.mobInteract(player, hand);
        }
    }

    public void setJukeboxPlaying(BlockPos jukeboxPos, boolean jukeboxPlaying) {
        if (jukeboxPlaying) {
            if (!this.isDancing()) {
                this.jukeboxPos = jukeboxPos;
                this.setDancing(true);
            }
        } else if (jukeboxPos.equals(this.jukeboxPos) || this.jukeboxPos == null) {
            this.jukeboxPos = null;
            this.setDancing(false);
        }
    }

    @Override
    public SimpleContainer getInventory() {
        return this.inventory;
    }

    @Override
    protected Vec3i getPickupReach() {
        return ITEM_PICKUP_REACH;
    }

    @Override
    public boolean wantsToPickUp(ServerLevel level, ItemStack stack) {
        ItemStack itemInHand = this.getItemInHand(InteractionHand.MAIN_HAND);
        return !itemInHand.isEmpty()
            && level.getGameRules().get(GameRules.MOB_GRIEFING)
            && this.inventory.canAddItem(stack)
            && this.allayConsidersItemEqual(itemInHand, stack);
    }

    private boolean allayConsidersItemEqual(ItemStack first, ItemStack second) {
        return ItemStack.isSameItem(first, second) && !this.hasNonMatchingPotion(first, second);
    }

    private boolean hasNonMatchingPotion(ItemStack first, ItemStack second) {
        PotionContents potionContents = first.get(DataComponents.POTION_CONTENTS);
        PotionContents potionContents1 = second.get(DataComponents.POTION_CONTENTS);
        return !Objects.equals(potionContents, potionContents1);
    }

    @Override
    protected void pickUpItem(ServerLevel level, ItemEntity entity) {
        InventoryCarrier.pickUpItem(level, this, this, entity);
    }

    @Override
    public boolean isFlapping() {
        return !this.onGround();
    }

    @Override
    public void updateDynamicGameEventListener(BiConsumer<DynamicGameEventListener<?>, ServerLevel> listenerConsumer) {
        if (this.level() instanceof ServerLevel serverLevel) {
            listenerConsumer.accept(this.dynamicVibrationListener, serverLevel);
            listenerConsumer.accept(this.dynamicJukeboxListener, serverLevel);
        }
    }

    public boolean isDancing() {
        return this.entityData.get(DATA_DANCING);
    }

    public void setDancing(boolean dancing) {
        if (!this.level().isClientSide() && this.isEffectiveAi() && (!dancing || !this.isPanicking())) {
            this.entityData.set(DATA_DANCING, dancing);
        }
    }

    private boolean shouldStopDancing() {
        if (this.forceDancing) {return false;} // CraftBukkit
        return this.jukeboxPos == null
            || !this.jukeboxPos.closerToCenterThan(this.position(), GameEvent.JUKEBOX_PLAY.value().notificationRadius())
            || !this.level().getBlockState(this.jukeboxPos).is(Blocks.JUKEBOX);
    }

    public float getHoldingItemAnimationProgress(float partialTick) {
        return Mth.lerp(partialTick, this.holdingItemAnimationTicks0, this.holdingItemAnimationTicks) / 5.0F;
    }

    public boolean isSpinning() {
        float f = this.dancingAnimationTicks % 55.0F;
        return f < 15.0F;
    }

    public float getSpinningProgress(float partialTick) {
        return Mth.lerp(partialTick, this.spinningAnimationTicks0, this.spinningAnimationTicks) / 15.0F;
    }

    @Override
    public boolean equipmentHasChanged(ItemStack oldItem, ItemStack newItem) {
        return !this.allayConsidersItemEqual(oldItem, newItem);
    }

    @Override
    protected void dropEquipment(ServerLevel level) {
        super.dropEquipment(level);
        this.inventory.removeAllItems().forEach(stack -> this.spawnAtLocation(level, stack));
        ItemStack itemBySlot = this.getItemBySlot(EquipmentSlot.MAINHAND);
        if (!itemBySlot.isEmpty() && !EnchantmentHelper.has(itemBySlot, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) {
            this.spawnAtLocation(level, itemBySlot);
            this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        this.writeInventoryToTag(output);
        output.store("listener", VibrationSystem.Data.CODEC, this.vibrationData);
        output.putLong("DuplicationCooldown", this.duplicationCooldown);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.readInventoryFromTag(input);
        this.vibrationData = input.read("listener", VibrationSystem.Data.CODEC).orElseGet(VibrationSystem.Data::new);
        this.setDuplicationCooldown(input.getLongOr("DuplicationCooldown", 0)); // Paper - Load as long
    }

    @Override
    protected boolean shouldStayCloseToLeashHolder() {
        return false;
    }

    private void updateDuplicationCooldown() {
        if (!this.level().isClientSide() && this.duplicationCooldown > 0L) {
            this.setDuplicationCooldown(this.duplicationCooldown - 1L);
        }
    }

    private void setDuplicationCooldown(long duplicationCooldown) {
        this.duplicationCooldown = duplicationCooldown;
        this.entityData.set(DATA_CAN_DUPLICATE, duplicationCooldown == 0L);
    }

    @Nullable public Allay duplicateAllay() { // CraftBukkit - return allay
        Allay allay = EntityType.ALLAY.create(this.level(), EntitySpawnReason.BREEDING);
        if (allay != null) {
            allay.snapTo(this.position());
            allay.setPersistenceRequired();
            allay.resetDuplicationCooldown();
            this.resetDuplicationCooldown();
            this.level().addFreshEntity(allay, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.DUPLICATION); // CraftBukkit - reason for duplicated allay
        }

        return allay; // CraftBukkit
    }

    public void resetDuplicationCooldown() {
        this.setDuplicationCooldown(6000L);
    }

    public boolean canDuplicate() {
        return this.entityData.get(DATA_CAN_DUPLICATE);
    }

    private void removeInteractionItem(Player player, ItemStack stack) {
        stack.consume(1, player);
    }

    @Override
    public Vec3 getLeashOffset() {
        return new Vec3(0.0, this.getEyeHeight() * 0.6, this.getBbWidth() * 0.1);
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EntityEvent.IN_LOVE_HEARTS) {
            for (int i = 0; i < 3; i++) {
                this.spawnHeartParticle();
            }
        } else {
            super.handleEntityEvent(id);
        }
    }

    private void spawnHeartParticle() {
        double d = this.random.nextGaussian() * 0.02;
        double d1 = this.random.nextGaussian() * 0.02;
        double d2 = this.random.nextGaussian() * 0.02;
        this.level().addParticle(ParticleTypes.HEART, this.getRandomX(1.0), this.getRandomY() + 0.5, this.getRandomZ(1.0), d, d1, d2);
    }

    @Override
    public VibrationSystem.Data getVibrationData() {
        return this.vibrationData;
    }

    @Override
    public VibrationSystem.User getVibrationUser() {
        return this.vibrationUser;
    }

    class JukeboxListener implements GameEventListener {
        private final PositionSource listenerSource;
        private final int listenerRadius;

        public JukeboxListener(final PositionSource listenerSource, final int listenerRadius) {
            this.listenerSource = listenerSource;
            this.listenerRadius = listenerRadius;
        }

        @Override
        public PositionSource getListenerSource() {
            return this.listenerSource;
        }

        @Override
        public int getListenerRadius() {
            return this.listenerRadius;
        }

        @Override
        public boolean handleGameEvent(ServerLevel level, Holder<GameEvent> gameEvent, GameEvent.Context context, Vec3 pos) {
            if (gameEvent.is(GameEvent.JUKEBOX_PLAY)) {
                Allay.this.setJukeboxPlaying(BlockPos.containing(pos), true);
                return true;
            } else if (gameEvent.is(GameEvent.JUKEBOX_STOP_PLAY)) {
                Allay.this.setJukeboxPlaying(BlockPos.containing(pos), false);
                return true;
            } else {
                return false;
            }
        }
    }

    class VibrationUser implements VibrationSystem.User {
        private static final int VIBRATION_EVENT_LISTENER_RANGE = 16;
        private final PositionSource positionSource = new EntityPositionSource(Allay.this, Allay.this.getEyeHeight());

        @Override
        public int getListenerRadius() {
            return 16;
        }

        @Override
        public PositionSource getPositionSource() {
            return this.positionSource;
        }

        @Override
        public boolean canReceiveVibration(ServerLevel level, BlockPos pos, Holder<GameEvent> gameEvent, GameEvent.Context context) {
            if (Allay.this.isNoAi()) {
                return false;
            } else {
                Optional<GlobalPos> memory = Allay.this.getBrain().getMemory(MemoryModuleType.LIKED_NOTEBLOCK_POSITION);
                if (memory.isEmpty()) {
                    return true;
                } else {
                    GlobalPos globalPos = memory.get();
                    return globalPos.isCloseEnough(level.dimension(), Allay.this.blockPosition(), 1024) && globalPos.pos().equals(pos);
                }
            }
        }

        @Override
        public void onReceiveVibration(
            ServerLevel level, BlockPos pos, Holder<GameEvent> gameEvent, @Nullable Entity entity, @Nullable Entity playerEntity, float distance
        ) {
            if (gameEvent.is(GameEvent.NOTE_BLOCK_PLAY)) {
                AllayAi.hearNoteblock(Allay.this, new BlockPos(pos));
            }
        }

        @Override
        public TagKey<GameEvent> getListenableEvents() {
            return GameEventTags.ALLAY_CAN_LISTEN;
        }
    }
}
