package net.minecraft.world.entity.animal.armadillo;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import io.netty.buffer.ByteBuf;
import java.util.function.IntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import org.jspecify.annotations.Nullable;

public class Armadillo extends Animal {
    public static final float BABY_SCALE = 0.6F;
    public static final float MAX_HEAD_ROTATION_EXTENT = 32.5F;
    public static final int SCARE_CHECK_INTERVAL = 80;
    private static final double SCARE_DISTANCE_HORIZONTAL = 7.0;
    private static final double SCARE_DISTANCE_VERTICAL = 2.0;
    private static final EntityDataAccessor<Armadillo.ArmadilloState> ARMADILLO_STATE = SynchedEntityData.defineId(
        Armadillo.class, EntityDataSerializers.ARMADILLO_STATE
    );
    private long inStateTicks = 0L;
    public final AnimationState rollOutAnimationState = new AnimationState();
    public final AnimationState rollUpAnimationState = new AnimationState();
    public final AnimationState peekAnimationState = new AnimationState();
    private int scuteTime;
    private boolean peekReceivedClient = false;

    public Armadillo(EntityType<? extends Animal> type, Level level) {
        super(type, level);
        this.getNavigation().setCanFloat(true);
        this.scuteTime = this.pickNextScuteDropTime();
    }

    @Override
    public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return EntityType.ARMADILLO.create(level, EntitySpawnReason.BREEDING);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes().add(Attributes.MAX_HEALTH, 12.0).add(Attributes.MOVEMENT_SPEED, 0.14);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ARMADILLO_STATE, Armadillo.ArmadilloState.IDLE);
    }

    public boolean isScared() {
        return this.entityData.get(ARMADILLO_STATE) != Armadillo.ArmadilloState.IDLE;
    }

    public boolean shouldHideInShell() {
        return this.getState().shouldHideInShell(this.inStateTicks);
    }

    public boolean shouldSwitchToScaredState() {
        return this.getState() == Armadillo.ArmadilloState.ROLLING && this.inStateTicks > Armadillo.ArmadilloState.ROLLING.animationDuration();
    }

    public Armadillo.ArmadilloState getState() {
        return this.entityData.get(ARMADILLO_STATE);
    }

    public void switchToState(Armadillo.ArmadilloState state) {
        this.entityData.set(ARMADILLO_STATE, state);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        if (ARMADILLO_STATE.equals(key)) {
            this.inStateTicks = 0L;
        }

        super.onSyncedDataUpdated(key);
    }

    @Override
    protected Brain.Provider<Armadillo> brainProvider() {
        return ArmadilloAi.brainProvider();
    }

    @Override
    protected Brain<?> makeBrain(Dynamic<?> dynamic) {
        return ArmadilloAi.makeBrain(this.brainProvider().makeBrain(dynamic));
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        ProfilerFiller profilerFiller = Profiler.get();
        profilerFiller.push("armadilloBrain");
        ((Brain<Armadillo>)this.brain).tick(level, this);
        profilerFiller.pop();
        profilerFiller.push("armadilloActivityUpdate");
        ArmadilloAi.updateActivity(this);
        profilerFiller.pop();
        if (this.isAlive() && --this.scuteTime <= 0 && this.shouldDropLoot(level)) {
            this.forceDrops = true; // CraftBukkit
            if (this.dropFromGiftLootTable(level, BuiltInLootTables.ARMADILLO_SHED, this::spawnAtLocation)) {
                this.playSound(SoundEvents.ARMADILLO_SCUTE_DROP, 1.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
                this.gameEvent(GameEvent.ENTITY_PLACE);
            }
            this.forceDrops = false; // CraftBukkit

            this.scuteTime = this.pickNextScuteDropTime();
        }

        super.customServerAiStep(level);
    }

    private int pickNextScuteDropTime() {
        return this.random.nextInt(20 * TimeUtil.SECONDS_PER_MINUTE * 5) + 20 * TimeUtil.SECONDS_PER_MINUTE * 5;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            this.setupAnimationStates();
        }

        if (this.isScared()) {
            this.clampHeadRotationToBody();
        }

        this.inStateTicks++;
    }

    @Override
    public float getAgeScale() {
        return this.isBaby() ? 0.6F : 1.0F;
    }

    private void setupAnimationStates() {
        switch (this.getState()) {
            case IDLE:
                this.rollOutAnimationState.stop();
                this.rollUpAnimationState.stop();
                this.peekAnimationState.stop();
                break;
            case ROLLING:
                this.rollOutAnimationState.stop();
                this.rollUpAnimationState.startIfStopped(this.tickCount);
                this.peekAnimationState.stop();
                break;
            case SCARED:
                this.rollOutAnimationState.stop();
                this.rollUpAnimationState.stop();
                if (this.peekReceivedClient) {
                    this.peekAnimationState.stop();
                    this.peekReceivedClient = false;
                }

                if (this.inStateTicks == 0L) {
                    this.peekAnimationState.start(this.tickCount);
                    this.peekAnimationState.fastForward(Armadillo.ArmadilloState.SCARED.animationDuration(), 1.0F);
                } else {
                    this.peekAnimationState.startIfStopped(this.tickCount);
                }
                break;
            case UNROLLING:
                this.rollOutAnimationState.startIfStopped(this.tickCount);
                this.rollUpAnimationState.stop();
                this.peekAnimationState.stop();
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EntityEvent.ARMADILLO_PEEK && this.level().isClientSide()) {
            this.peekReceivedClient = true;
            this.level().playLocalSound(this.getX(), this.getY(), this.getZ(), SoundEvents.ARMADILLO_PEEK, this.getSoundSource(), 1.0F, 1.0F, false);
        } else {
            super.handleEntityEvent(id);
        }
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(ItemTags.ARMADILLO_FOOD);
    }

    public static boolean checkArmadilloSpawnRules(
        EntityType<Armadillo> entityType, LevelAccessor level, EntitySpawnReason spawnReason, BlockPos pos, RandomSource random
    ) {
        return level.getBlockState(pos.below()).is(BlockTags.ARMADILLO_SPAWNABLE_ON) && isBrightEnoughToSpawn(level, pos);
    }

    public boolean isScaredBy(LivingEntity entity) {
        return this.getBoundingBox().inflate(7.0, 2.0, 7.0).intersects(entity.getBoundingBox())
            && (
                entity.getType().is(EntityTypeTags.UNDEAD)
                    || this.getLastHurtByMob() == entity
                    || entity instanceof Player player && !player.isSpectator() && (player.isSprinting() || player.isPassenger())
            );
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store("state", Armadillo.ArmadilloState.CODEC, this.getState());
        output.putInt("scute_time", this.scuteTime);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.switchToState(input.read("state", Armadillo.ArmadilloState.CODEC).orElse(Armadillo.ArmadilloState.IDLE));
        input.getInt("scute_time").ifPresent(integer -> this.scuteTime = integer);
    }

    public void rollUp() {
        if (!this.isScared()) {
            this.stopInPlace();
            this.resetLove();
            this.gameEvent(GameEvent.ENTITY_ACTION);
            this.makeSound(SoundEvents.ARMADILLO_ROLL);
            this.switchToState(Armadillo.ArmadilloState.ROLLING);
        }
    }

    public void rollOut() {
        if (this.isScared()) {
            this.gameEvent(GameEvent.ENTITY_ACTION);
            this.makeSound(SoundEvents.ARMADILLO_UNROLL_FINISH);
            this.switchToState(Armadillo.ArmadilloState.IDLE);
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        if (this.isScared()) {
            amount = (amount - 1.0F) / 2.0F;
        }

        return super.hurtServer(level, damageSource, amount);
    }

    @Override
    // CraftBukkit start - void -> boolean
    public boolean actuallyHurt(ServerLevel level, DamageSource damageSource, float amount, org.bukkit.event.entity.EntityDamageEvent event) {
        boolean damageResult = super.actuallyHurt(level, damageSource, amount, event);
        if (!damageResult) return false;
        // CraftBukkit end
        if (!this.isNoAi() && !this.isDeadOrDying()) {
            if (damageSource.getEntity() instanceof LivingEntity) {
                this.getBrain().setMemoryWithExpiry(MemoryModuleType.DANGER_DETECTED_RECENTLY, true, 80L);
                if (this.canStayRolledUp()) {
                    this.rollUp();
                }
            } else if (damageSource.is(DamageTypeTags.PANIC_ENVIRONMENTAL_CAUSES)) {
                this.rollOut();
            }
        }
        return true; // CraftBukkit
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemInHand = player.getItemInHand(hand);
        if (itemInHand.is(Items.BRUSH) && this.brushOffScute(player, itemInHand)) {
            itemInHand.hurtAndBreak(16, player, hand.asEquipmentSlot());
            return InteractionResult.SUCCESS;
        } else {
            return (InteractionResult)(this.isScared() ? InteractionResult.FAIL : super.mobInteract(player, hand));
        }
    }

    public boolean brushOffScute(@Nullable Entity entity, ItemStack stack) {
        if (this.isBaby()) {
            return false;
        } else {
            if (this.level() instanceof ServerLevel serverLevel) {
                this.forceDrops = true; // CraftBukkit
                this.dropFromEntityInteractLootTable(serverLevel, BuiltInLootTables.ARMADILLO_BRUSH, entity, stack, this::spawnAtLocation);
                this.forceDrops = false; // CraftBukkit
                this.playSound(SoundEvents.ARMADILLO_BRUSH);
                this.gameEvent(GameEvent.ENTITY_INTERACT);
            }

            return true;
        }
    }

    public boolean canStayRolledUp() {
        return !this.isPanicking() && !this.isInLiquid() && !this.isLeashed() && !this.isPassenger() && !this.isVehicle();
    }

    @Override
    public boolean canFallInLove() {
        return super.canFallInLove() && !this.isScared();
    }

    @Override
    public SoundEvent getAmbientSound() {
        return this.isScared() ? null : SoundEvents.ARMADILLO_AMBIENT;
    }

    @Override
    protected void playEatingSound() {
        this.makeSound(SoundEvents.ARMADILLO_EAT);
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.ARMADILLO_DEATH;
    }

    @Override
    public SoundEvent getHurtSound(DamageSource damageSource) {
        return this.isScared() ? SoundEvents.ARMADILLO_HURT_REDUCED : SoundEvents.ARMADILLO_HURT;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.ARMADILLO_STEP, 0.15F, 1.0F);
    }

    @Override
    public int getMaxHeadYRot() {
        return this.isScared() ? 0 : 32;
    }

    @Override
    protected BodyRotationControl createBodyControl() {
        return new BodyRotationControl(this) {
            @Override
            public void clientTick() {
                if (!Armadillo.this.isScared()) {
                    super.clientTick();
                }
            }
        };
    }

    public static enum ArmadilloState implements StringRepresentable {
        IDLE("idle", false, 0, 0) {
            @Override
            public boolean shouldHideInShell(long inStateTicks) {
                return false;
            }
        },
        ROLLING("rolling", true, 10, 1) {
            @Override
            public boolean shouldHideInShell(long inStateTicks) {
                return inStateTicks > 5L;
            }
        },
        SCARED("scared", true, 50, 2) {
            @Override
            public boolean shouldHideInShell(long inStateTicks) {
                return true;
            }
        },
        UNROLLING("unrolling", true, 30, 3) {
            @Override
            public boolean shouldHideInShell(long inStateTicks) {
                return inStateTicks < 26L;
            }
        };

        static final Codec<Armadillo.ArmadilloState> CODEC = StringRepresentable.fromEnum(Armadillo.ArmadilloState::values);
        private static final IntFunction<Armadillo.ArmadilloState> BY_ID = ByIdMap.continuous(
            Armadillo.ArmadilloState::id, values(), ByIdMap.OutOfBoundsStrategy.ZERO
        );
        public static final StreamCodec<ByteBuf, Armadillo.ArmadilloState> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, Armadillo.ArmadilloState::id);
        private final String name;
        private final boolean isThreatened;
        private final int animationDuration;
        private final int id;

        ArmadilloState(final String name, final boolean isThreatened, final int animationDuration, final int id) {
            this.name = name;
            this.isThreatened = isThreatened;
            this.animationDuration = animationDuration;
            this.id = id;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        private int id() {
            return this.id;
        }

        public abstract boolean shouldHideInShell(long inStateTicks);

        public boolean isThreatened() {
            return this.isThreatened;
        }

        public int animationDuration() {
            return this.animationDuration;
        }
    }
}
