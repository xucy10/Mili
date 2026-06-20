package net.minecraft.world.entity.animal.equine;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.function.IntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.Util;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.EntityAttachments;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.LlamaFollowCaravanGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.RunAroundLikeCrazyGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.LlamaSpit;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class Llama extends AbstractChestedHorse implements RangedAttackMob {
    private static final int MAX_STRENGTH = 5;
    private static final EntityDataAccessor<Integer> DATA_STRENGTH_ID = SynchedEntityData.defineId(Llama.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_VARIANT_ID = SynchedEntityData.defineId(Llama.class, EntityDataSerializers.INT);
    private static final EntityDimensions BABY_DIMENSIONS = EntityType.LLAMA
        .getDimensions()
        .withAttachments(EntityAttachments.builder().attach(EntityAttachment.PASSENGER, 0.0F, EntityType.LLAMA.getHeight() - 0.8125F, -0.3F))
        .scale(0.5F);
    boolean didSpit;
    private @Nullable Llama caravanHead;
    public @Nullable Llama caravanTail; // Paper - public

    public Llama(EntityType<? extends Llama> type, Level level) {
        super(type, level);
        this.getNavigation().setRequiredPathLength(40.0F);
        this.maxDomestication = 30; // Paper - Missing entity API; configure max temper instead of a hardcoded value
    }

    public boolean isTraderLlama() {
        return false;
    }

    public void setStrength(int strength) { // Paper - public
        this.entityData.set(DATA_STRENGTH_ID, Math.max(1, Math.min(5, strength)));
    }

    private void setRandomStrength(RandomSource random) {
        int i = random.nextFloat() < 0.04F ? 5 : 3;
        this.setStrength(1 + random.nextInt(i));
    }

    public int getStrength() {
        return this.entityData.get(DATA_STRENGTH_ID);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store("Variant", Llama.Variant.LEGACY_CODEC, this.getVariant());
        output.putInt("Strength", this.getStrength());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.setStrength(input.getIntOr("Strength", 0));
        super.readAdditionalSaveData(input);
        this.setVariant(input.read("Variant", Llama.Variant.LEGACY_CODEC).orElse(Llama.Variant.DEFAULT));
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new RunAroundLikeCrazyGoal(this, 1.2));
        this.goalSelector.addGoal(2, new LlamaFollowCaravanGoal(this, 2.1F));
        this.goalSelector.addGoal(3, new RangedAttackGoal(this, 1.25, 40, 20.0F));
        this.goalSelector.addGoal(3, new PanicGoal(this, 1.2));
        this.goalSelector.addGoal(4, new BreedGoal(this, 1.0));
        this.goalSelector.addGoal(5, new TemptGoal(this, 1.25, itemStack -> itemStack.is(ItemTags.LLAMA_TEMPT_ITEMS), false));
        this.goalSelector.addGoal(6, new FollowParentGoal(this, 1.0));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.7));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new Llama.LlamaHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new Llama.LlamaAttackWolfGoal(this));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createBaseChestedHorseAttributes();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_STRENGTH_ID, 0);
        builder.define(DATA_VARIANT_ID, 0);
    }

    public Llama.Variant getVariant() {
        return Llama.Variant.byId(this.entityData.get(DATA_VARIANT_ID));
    }

    public void setVariant(Llama.Variant variant) {
        this.entityData.set(DATA_VARIANT_ID, variant.id);
    }

    @Override
    public <T> @Nullable T get(DataComponentType<? extends T> component) {
        return component == DataComponents.LLAMA_VARIANT ? castComponentValue((DataComponentType<T>)component, this.getVariant()) : super.get(component);
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter componentGetter) {
        this.applyImplicitComponentIfPresent(componentGetter, DataComponents.LLAMA_VARIANT);
        super.applyImplicitComponents(componentGetter);
    }

    @Override
    protected <T> boolean applyImplicitComponent(DataComponentType<T> component, T value) {
        if (component == DataComponents.LLAMA_VARIANT) {
            this.setVariant(castComponentValue(DataComponents.LLAMA_VARIANT, value));
            return true;
        } else {
            return super.applyImplicitComponent(component, value);
        }
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(ItemTags.LLAMA_FOOD);
    }

    @Override
    protected boolean handleEating(Player player, ItemStack stack) {
        int i = 0;
        int i1 = 0;
        float f = 0.0F;
        boolean flag = false;
        if (stack.is(Items.WHEAT)) {
            i = 10;
            i1 = 3;
            f = 2.0F;
        } else if (stack.is(Blocks.HAY_BLOCK.asItem())) {
            i = 90;
            i1 = 6;
            f = 10.0F;
            if (this.isTamed() && this.getAge() == 0 && this.canFallInLove()) {
                flag = true;
                this.setInLove(player, stack.copy()); // Paper - Fix EntityBreedEvent copying
            }
        }

        if (this.getHealth() < this.getMaxHealth() && f > 0.0F) {
            this.heal(f, org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason.EATING); // Paper - Add missing regain reason
            flag = true;
        }

        if (this.isBaby() && i > 0) {
            this.level().addParticle(ParticleTypes.HAPPY_VILLAGER, this.getRandomX(1.0), this.getRandomY() + 0.5, this.getRandomZ(1.0), 0.0, 0.0, 0.0);
            if (!this.level().isClientSide()) {
                this.ageUp(i);
                flag = true;
            }
        }

        if (i1 > 0 && (flag || !this.isTamed()) && this.getTemper() < this.getMaxTemper() && !this.level().isClientSide()) {
            this.modifyTemper(i1);
            flag = true;
        }

        if (flag && !this.isSilent()) {
            SoundEvent eatingSound = this.getEatingSound();
            if (eatingSound != null) {
                this.level()
                    .playSound(
                        null,
                        this.getX(),
                        this.getY(),
                        this.getZ(),
                        this.getEatingSound(),
                        this.getSoundSource(),
                        1.0F,
                        1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.2F
                    );
            }
        }

        return flag;
    }

    @Override
    public boolean isImmobile() {
        return this.isDeadOrDying() || this.isEating();
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(
        ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData spawnGroupData
    ) {
        RandomSource random = level.getRandom();
        this.setRandomStrength(random);
        Llama.Variant variant;
        if (spawnGroupData instanceof Llama.LlamaGroupData) {
            variant = ((Llama.LlamaGroupData)spawnGroupData).variant;
        } else {
            variant = Util.getRandom(Llama.Variant.values(), random);
            spawnGroupData = new Llama.LlamaGroupData(variant);
        }

        this.setVariant(variant);
        return super.finalizeSpawn(level, difficulty, spawnReason, spawnGroupData);
    }

    @Override
    protected boolean canPerformRearing() {
        return false;
    }

    @Override
    protected SoundEvent getAngrySound() {
        return SoundEvents.LLAMA_ANGRY;
    }

    @Override
    public SoundEvent getAmbientSound() {
        return SoundEvents.LLAMA_AMBIENT;
    }

    @Override
    public SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.LLAMA_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.LLAMA_DEATH;
    }

    @Override
    protected SoundEvent getEatingSound() {
        return SoundEvents.LLAMA_EAT;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState block) {
        this.playSound(SoundEvents.LLAMA_STEP, 0.15F, 1.0F);
    }

    @Override
    protected void playChestEquipsSound() {
        this.playSound(SoundEvents.LLAMA_CHEST, 1.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
    }

    @Override
    public int getInventoryColumns() {
        return this.hasChest() ? this.getStrength() : 0;
    }

    @Override
    public boolean canUseSlot(EquipmentSlot slot) {
        return true;
    }

    @Override
    public int getMaxTemper() {
        return super.getMaxTemper(); // Paper - Missing entity API; delegate to parent
    }

    @Override
    public boolean canMate(Animal otherAnimal) {
        return otherAnimal != this && otherAnimal instanceof Llama && this.canParent() && ((Llama)otherAnimal).canParent();
    }

    @Override
    public @Nullable Llama getBreedOffspring(ServerLevel level, AgeableMob partner) {
        Llama llama = this.makeNewLlama();
        if (llama != null) {
            this.setOffspringAttributes(partner, llama);
            Llama llama1 = (Llama)partner;
            int i = this.random.nextInt(Math.max(this.getStrength(), llama1.getStrength())) + 1;
            if (this.random.nextFloat() < 0.03F) {
                i++;
            }

            llama.setStrength(i);
            llama.setVariant(this.random.nextBoolean() ? this.getVariant() : llama1.getVariant());
        }

        return llama;
    }

    protected @Nullable Llama makeNewLlama() {
        return EntityType.LLAMA.create(this.level(), EntitySpawnReason.BREEDING);
    }

    private void spit(LivingEntity target) {
        LlamaSpit llamaSpit = new LlamaSpit(this.level(), this);
        double d = target.getX() - this.getX();
        double d1 = target.getY(0.3333333333333333) - llamaSpit.getY();
        double d2 = target.getZ() - this.getZ();
        double d3 = Math.sqrt(d * d + d2 * d2) * 0.2F;
        if (this.level() instanceof ServerLevel serverLevel) {
            Projectile.spawnProjectileUsingShoot(llamaSpit, serverLevel, ItemStack.EMPTY, d, d1 + d3, d2, 1.5F, 10.0F);
        }

        if (!this.isSilent()) {
            this.level()
                .playSound(
                    null,
                    this.getX(),
                    this.getY(),
                    this.getZ(),
                    SoundEvents.LLAMA_SPIT,
                    this.getSoundSource(),
                    1.0F,
                    1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.2F
                );
        }

        this.didSpit = true;
    }

    void setDidSpit(boolean didSpit) {
        this.didSpit = didSpit;
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageMultiplier, DamageSource damageSource) {
        int i = this.calculateFallDamage(fallDistance, damageMultiplier);
        if (i <= 0) {
            return false;
        } else {
            if (fallDistance >= 6.0) {
                this.hurt(damageSource, i);
                this.propagateFallToPassengers(fallDistance, damageMultiplier, damageSource);
            }

            this.playBlockFallSound();
            return true;
        }
    }

    public void leaveCaravan() {
        if (this.caravanHead != null) {
            this.caravanHead.caravanTail = null;
        }

        this.caravanHead = null;
    }

    public void joinCaravan(Llama caravanHead) {
        this.caravanHead = caravanHead;
        this.caravanHead.caravanTail = this;
    }

    public boolean hasCaravanTail() {
        return this.caravanTail != null;
    }

    public boolean inCaravan() {
        return this.caravanHead != null;
    }

    public @Nullable Llama getCaravanHead() {
        return this.caravanHead;
    }

    @Override
    protected double followLeashSpeed() {
        return 2.0;
    }

    @Override
    public boolean supportQuadLeash() {
        return false;
    }

    @Override
    protected void followMommy(ServerLevel level) {
        if (!this.inCaravan() && this.isBaby()) {
            super.followMommy(level);
        }
    }

    @Override
    public boolean canEatGrass() {
        return false;
    }

    @Override
    public void performRangedAttack(LivingEntity target, float distanceFactor) {
        this.spit(target);
    }

    @Override
    public Vec3 getLeashOffset() {
        return new Vec3(0.0, 0.75 * this.getEyeHeight(), this.getBbWidth() * 0.5);
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        return this.isBaby() ? BABY_DIMENSIONS : super.getDefaultDimensions(pose);
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity entity, EntityDimensions dimensions, float partialTick) {
        return getDefaultPassengerAttachmentPoint(this, entity, dimensions.attachments());
    }

    static class LlamaAttackWolfGoal extends NearestAttackableTargetGoal<Wolf> {
        public LlamaAttackWolfGoal(Llama llama) {
            super(llama, Wolf.class, 16, false, true, (entity, level) -> !((Wolf)entity).isTame());
        }

        @Override
        protected double getFollowDistance() {
            return super.getFollowDistance() * 0.25;
        }
    }

    static class LlamaGroupData extends AgeableMob.AgeableMobGroupData {
        public final Llama.Variant variant;

        LlamaGroupData(Llama.Variant variant) {
            super(true);
            this.variant = variant;
        }
    }

    static class LlamaHurtByTargetGoal extends HurtByTargetGoal {
        public LlamaHurtByTargetGoal(Llama llama) {
            super(llama);
        }

        @Override
        public boolean canContinueToUse() {
            if (this.mob instanceof Llama llama && llama.didSpit) {
                llama.setDidSpit(false);
                return false;
            } else {
                return super.canContinueToUse();
            }
        }
    }

    public static enum Variant implements StringRepresentable {
        CREAMY(0, "creamy"),
        WHITE(1, "white"),
        BROWN(2, "brown"),
        GRAY(3, "gray");

        public static final Llama.Variant DEFAULT = CREAMY;
        private static final IntFunction<Llama.Variant> BY_ID = ByIdMap.continuous(Llama.Variant::getId, values(), ByIdMap.OutOfBoundsStrategy.CLAMP);
        public static final Codec<Llama.Variant> CODEC = StringRepresentable.fromEnum(Llama.Variant::values);
        @Deprecated
        public static final Codec<Llama.Variant> LEGACY_CODEC = Codec.INT.xmap(BY_ID::apply, Llama.Variant::getId);
        public static final StreamCodec<ByteBuf, Llama.Variant> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, Llama.Variant::getId);
        final int id;
        private final String name;

        private Variant(final int id, final String name) {
            this.id = id;
            this.name = name;
        }

        public int getId() {
            return this.id;
        }

        public static Llama.Variant byId(int id) {
            return BY_ID.apply(id);
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }
}
