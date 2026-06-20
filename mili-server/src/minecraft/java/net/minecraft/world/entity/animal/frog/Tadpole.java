package net.minecraft.world.entity.animal.frog;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Dynamic;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.SmoothSwimmingLookControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Bucketable;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class Tadpole extends AbstractFish {
    private static final int DEFAULT_AGE = 0;
    @VisibleForTesting
    public static int ticksToBeFrog = Math.abs(-24000);
    public static final float HITBOX_WIDTH = 0.4F;
    public static final float HITBOX_HEIGHT = 0.3F;
    public int age = 0;
    protected static final ImmutableList<SensorType<? extends Sensor<? super Tadpole>>> SENSOR_TYPES = ImmutableList.of(
        SensorType.NEAREST_LIVING_ENTITIES, SensorType.NEAREST_PLAYERS, SensorType.HURT_BY, SensorType.FROG_TEMPTATIONS
    );
    protected static final ImmutableList<MemoryModuleType<?>> MEMORY_TYPES = ImmutableList.of(
        MemoryModuleType.LOOK_TARGET,
        MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
        MemoryModuleType.WALK_TARGET,
        MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE,
        MemoryModuleType.PATH,
        MemoryModuleType.NEAREST_VISIBLE_ADULT,
        MemoryModuleType.TEMPTATION_COOLDOWN_TICKS,
        MemoryModuleType.IS_TEMPTED,
        MemoryModuleType.TEMPTING_PLAYER,
        MemoryModuleType.BREED_TARGET,
        MemoryModuleType.IS_PANICKING
    );
    public boolean ageLocked; // Paper

    public Tadpole(EntityType<? extends AbstractFish> type, Level level) {
        super(type, level);
        this.moveControl = new SmoothSwimmingMoveControl(this, 85, 10, 0.02F, 0.1F, true);
        this.lookControl = new SmoothSwimmingLookControl(this, 10);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new WaterBoundPathNavigation(this, level);
    }

    @Override
    protected Brain.Provider<Tadpole> brainProvider() {
        return Brain.provider(MEMORY_TYPES, SENSOR_TYPES);
    }

    @Override
    protected Brain<?> makeBrain(Dynamic<?> dynamic) {
        return TadpoleAi.makeBrain(this.brainProvider().makeBrain(dynamic));
    }

    @Override
    public Brain<Tadpole> getBrain() {
        return (Brain<Tadpole>)super.getBrain();
    }

    @Override
    protected SoundEvent getFlopSound() {
        return SoundEvents.TADPOLE_FLOP;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        ProfilerFiller profilerFiller = Profiler.get();
        profilerFiller.push("tadpoleBrain");
        this.getBrain().tick(level, this);
        profilerFiller.pop();
        profilerFiller.push("tadpoleActivityUpdate");
        TadpoleAi.updateActivity(this);
        profilerFiller.pop();
        super.customServerAiStep(level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes().add(Attributes.MOVEMENT_SPEED, 1.0).add(Attributes.MAX_HEALTH, 6.0);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide() && !this.ageLocked) { // Paper
            this.setAge(this.age + 1);
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("Age", this.age);
        output.putBoolean("AgeLocked", this.ageLocked); // Paper
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setAge(input.getIntOr("Age", 0));
        this.ageLocked = input.getBooleanOr("AgeLocked", false); // Paper
    }

    @Override
    public @Nullable SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    public @Nullable SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.TADPOLE_HURT;
    }

    @Override
    public @Nullable SoundEvent getDeathSound() {
        return SoundEvents.TADPOLE_DEATH;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemInHand = player.getItemInHand(hand);
        if (this.isFood(itemInHand)) {
            this.feed(player, itemInHand);
            return InteractionResult.SUCCESS;
        } else {
            return Bucketable.bucketMobPickup(player, hand, this).orElse(super.mobInteract(player, hand));
        }
    }

    @Override
    public boolean fromBucket() {
        return true;
    }

    @Override
    public void setFromBucket(boolean fromBucket) {
    }

    @Override
    public void saveToBucketTag(ItemStack stack) {
        Bucketable.saveDefaultDataToBucketTag(this, stack);
        // Paper start - Save tadpole age
        CustomData.update(DataComponents.BUCKET_ENTITY_DATA, stack, compoundTag -> {
            compoundTag.putInt("Age", this.getAge());
            compoundTag.putBoolean("AgeLocked", this.ageLocked);
        });
        // Paper end - Save tadpole age
    }

    @Override
    public void loadFromBucketTag(CompoundTag tag) {
        Bucketable.loadDefaultDataFromBucketTag(this, tag);
        tag.getInt("Age").ifPresent(this::setAge);
        this.ageLocked = tag.getBooleanOr("AgeLocked", false); // Paper
    }

    @Override
    public ItemStack getBucketItemStack() {
        return new ItemStack(Items.TADPOLE_BUCKET);
    }

    @Override
    public SoundEvent getPickupSound() {
        return SoundEvents.BUCKET_FILL_TADPOLE;
    }

    private boolean isFood(ItemStack stack) {
        return stack.is(ItemTags.FROG_FOOD);
    }

    private void feed(Player player, ItemStack stack) {
        this.usePlayerItem(player, stack);
        this.ageUp(AgeableMob.getSpeedUpSecondsWhenFeeding(this.getTicksLeftUntilAdult()));
        this.level().addParticle(ParticleTypes.HAPPY_VILLAGER, this.getRandomX(1.0), this.getRandomY() + 0.5, this.getRandomZ(1.0), 0.0, 0.0, 0.0);
    }

    private void usePlayerItem(Player player, ItemStack stack) {
        stack.consume(1, player);
    }

    private int getAge() {
        return this.age;
    }

    private void ageUp(int offset) {
        if (this.ageLocked) return; // Paper
        this.setAge(this.age + offset * 20);
    }

    private void setAge(int age) {
        this.age = age;
        if (this.age >= ticksToBeFrog) {
            this.ageUp();
        }
    }

    private void ageUp() {
        if (this.level() instanceof ServerLevel serverLevel) {
            Frog converted = this.convertTo(EntityType.FROG, ConversionParams.single(this, false, false), mob -> { // CraftBukkit
                mob.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(mob.blockPosition()), EntitySpawnReason.CONVERSION, null);
                mob.setPersistenceRequired();
                mob.fudgePositionAfterSizeChange(this.getDimensions(this.getPose()));
                this.playSound(SoundEvents.TADPOLE_GROW_UP, 0.15F, 1.0F);
            // CraftBukkit start
            }, org.bukkit.event.entity.EntityTransformEvent.TransformReason.METAMORPHOSIS, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.METAMORPHOSIS);
            if (converted == null) {
                this.setAge(0); // Sets the age to 0 for avoid a loop if the event is canceled
            }
            // CraftBukkit end
        }
    }

    private int getTicksLeftUntilAdult() {
        return Math.max(0, ticksToBeFrog - this.age);
    }

    @Override
    public boolean shouldDropExperience() {
        return false;
    }
}
