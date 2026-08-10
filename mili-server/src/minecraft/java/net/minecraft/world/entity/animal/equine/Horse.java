package net.minecraft.world.entity.animal.equine;

import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.EntityAttachments;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class Horse extends AbstractHorse {
    private static final EntityDataAccessor<Integer> DATA_ID_TYPE_VARIANT = SynchedEntityData.defineId(Horse.class, EntityDataSerializers.INT);
    private static final EntityDimensions BABY_DIMENSIONS = EntityType.HORSE
        .getDimensions()
        .withAttachments(EntityAttachments.builder().attach(EntityAttachment.PASSENGER, 0.0F, EntityType.HORSE.getHeight() + 0.125F, 0.0F))
        .scale(0.5F);
    private static final int DEFAULT_VARIANT = 0;

    public Horse(EntityType<? extends Horse> type, Level level) {
        super(type, level);
        this.setPathfindingMalus(PathType.DANGER_OTHER, -1.0F);
        this.setPathfindingMalus(PathType.DAMAGE_OTHER, -1.0F);
    }

    @Override
    protected void randomizeAttributes(RandomSource random) {
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(generateMaxHealth(random::nextInt));
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(generateSpeed(random::nextDouble));
        this.getAttribute(Attributes.JUMP_STRENGTH).setBaseValue(generateJumpStrength(random::nextDouble));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ID_TYPE_VARIANT, 0);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("Variant", this.getTypeVariant());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setTypeVariant(input.getIntOr("Variant", 0));
    }

    private void setTypeVariant(int typeVariant) {
        this.entityData.set(DATA_ID_TYPE_VARIANT, typeVariant);
    }

    private int getTypeVariant() {
        return this.entityData.get(DATA_ID_TYPE_VARIANT);
    }

    public void setVariantAndMarkings(Variant variant, Markings marking) {
        this.setTypeVariant(variant.getId() & 0xFF | marking.getId() << 8 & 0xFF00);
    }

    public Variant getVariant() {
        return Variant.byId(this.getTypeVariant() & 0xFF);
    }

    private void setVariant(Variant variant) {
        this.setTypeVariant(variant.getId() & 0xFF | this.getTypeVariant() & -256);
    }

    @Override
    public <T> @Nullable T get(DataComponentType<? extends T> component) {
        return component == DataComponents.HORSE_VARIANT ? castComponentValue((DataComponentType<T>)component, this.getVariant()) : super.get(component);
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter componentGetter) {
        this.applyImplicitComponentIfPresent(componentGetter, DataComponents.HORSE_VARIANT);
        super.applyImplicitComponents(componentGetter);
    }

    @Override
    protected <T> boolean applyImplicitComponent(DataComponentType<T> component, T value) {
        if (component == DataComponents.HORSE_VARIANT) {
            this.setVariant(castComponentValue(DataComponents.HORSE_VARIANT, value));
            return true;
        } else {
            return super.applyImplicitComponent(component, value);
        }
    }

    public Markings getMarkings() {
        return Markings.byId((this.getTypeVariant() & 0xFF00) >> 8);
    }

    @Override
    protected void playGallopSound(SoundType soundType) {
        super.playGallopSound(soundType);
        if (this.random.nextInt(10) == 0) {
            this.playSound(SoundEvents.HORSE_BREATHE, soundType.getVolume() * 0.6F, soundType.getPitch());
        }
    }

    @Override
    public SoundEvent getAmbientSound() {
        return SoundEvents.HORSE_AMBIENT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.HORSE_DEATH;
    }

    @Override
    protected SoundEvent getEatingSound() {
        return SoundEvents.HORSE_EAT;
    }

    @Override
    public SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.HORSE_HURT;
    }

    @Override
    protected SoundEvent getAngrySound() {
        return SoundEvents.HORSE_ANGRY;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        boolean flag = !this.isBaby() && this.isTamed() && player.isSecondaryUseActive();
        if (!this.isVehicle() && !flag) {
            ItemStack itemInHand = player.getItemInHand(hand);
            if (!itemInHand.isEmpty()) {
                if (this.isFood(itemInHand)) {
                    return this.fedFood(player, itemInHand);
                }

                if (!this.isTamed()) {
                    this.makeMad();
                    return InteractionResult.SUCCESS;
                }
            }

            return super.mobInteract(player, hand);
        } else {
            return super.mobInteract(player, hand);
        }
    }

    @Override
    public boolean canMate(Animal otherAnimal) {
        return otherAnimal != this
            && (otherAnimal instanceof Donkey || otherAnimal instanceof Horse)
            && this.canParent()
            && ((AbstractHorse)otherAnimal).canParent();
    }

    @Override
    public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        if (partner instanceof Donkey) {
            Mule mule = EntityType.MULE.create(level, EntitySpawnReason.BREEDING);
            if (mule != null) {
                this.setOffspringAttributes(partner, mule);
            }

            return mule;
        } else {
            Horse horse = (Horse)partner;
            Horse horse1 = EntityType.HORSE.create(level, EntitySpawnReason.BREEDING);
            if (horse1 != null) {
                int randomInt = this.random.nextInt(9);
                Variant variant;
                if (randomInt < 4) {
                    variant = this.getVariant();
                } else if (randomInt < 8) {
                    variant = horse.getVariant();
                } else {
                    variant = Util.getRandom(Variant.values(), this.random);
                }

                int randomInt1 = this.random.nextInt(5);
                Markings markings;
                if (randomInt1 < 2) {
                    markings = this.getMarkings();
                } else if (randomInt1 < 4) {
                    markings = horse.getMarkings();
                } else {
                    markings = Util.getRandom(Markings.values(), this.random);
                }

                horse1.setVariantAndMarkings(variant, markings);
                this.setOffspringAttributes(partner, horse1);
            }

            return horse1;
        }
    }

    @Override
    public boolean canUseSlot(EquipmentSlot slot) {
        return true;
    }

    @Override
    protected void hurtArmor(DamageSource damageSource, float damageAmount) {
        this.doHurtEquipment(damageSource, damageAmount, EquipmentSlot.BODY);
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(
        ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData spawnGroupData
    ) {
        RandomSource random = level.getRandom();
        Variant variant;
        if (spawnGroupData instanceof Horse.HorseGroupData) {
            variant = ((Horse.HorseGroupData)spawnGroupData).variant;
        } else {
            variant = Util.getRandom(Variant.values(), random);
            spawnGroupData = new Horse.HorseGroupData(variant);
        }

        this.setVariantAndMarkings(variant, Util.getRandom(Markings.values(), random));
        return super.finalizeSpawn(level, difficulty, spawnReason, spawnGroupData);
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        return this.isBaby() ? BABY_DIMENSIONS : super.getDefaultDimensions(pose);
    }

    public static class HorseGroupData extends AgeableMob.AgeableMobGroupData {
        public final Variant variant;

        public HorseGroupData(Variant variant) {
            super(true);
            this.variant = variant;
        }
    }
}
