package net.minecraft.world.entity.animal.golem;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Crackiness;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.GolemRandomStrollInVillageGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveBackToVillageGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsTargetGoal;
import net.minecraft.world.entity.ai.goal.OfferFlowerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.DefendVillageTargetGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.ResetUniversalAngerTargetGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class IronGolem extends AbstractGolem implements NeutralMob {
    protected static final EntityDataAccessor<Byte> DATA_FLAGS_ID = SynchedEntityData.defineId(IronGolem.class, EntityDataSerializers.BYTE);
    private static final int IRON_INGOT_HEAL_AMOUNT = 25;
    private static final boolean DEFAULT_PLAYER_CREATED = false;
    private int attackAnimationTick;
    private int offerFlowerTick;
    private static final UniformInt PERSISTENT_ANGER_TIME = TimeUtil.rangeOfSeconds(20, 39);
    private long persistentAngerEndTime;
    private @Nullable EntityReference<LivingEntity> persistentAngerTarget;

    public IronGolem(EntityType<? extends IronGolem> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0, true));
        this.goalSelector.addGoal(2, new MoveTowardsTargetGoal(this, 0.9, 32.0F));
        this.goalSelector.addGoal(2, new MoveBackToVillageGoal(this, 0.6, false));
        this.goalSelector.addGoal(4, new GolemRandomStrollInVillageGoal(this, 0.6));
        this.goalSelector.addGoal(5, new OfferFlowerGoal(this));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new DefendVillageTargetGoal(this));
        this.targetSelector.addGoal(2, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, this::isAngryAt));
        this.targetSelector
            .addGoal(
                3,
                new NearestAttackableTargetGoal<>(this, Mob.class, 5, false, false, (entity, level) -> entity instanceof Enemy && !(entity instanceof Creeper))
            );
        this.targetSelector.addGoal(4, new ResetUniversalAngerTargetGoal<>(this, false));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_FLAGS_ID, (byte)0);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 100.0)
            .add(Attributes.MOVEMENT_SPEED, 0.25)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
            .add(Attributes.ATTACK_DAMAGE, 15.0)
            .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected int decreaseAirSupply(int air) {
        return air;
    }

    @Override
    protected void doPush(Entity entity) {
        if (entity instanceof Enemy && !(entity instanceof Creeper) && this.getRandom().nextInt(20) == 0) {
            this.setTarget((LivingEntity)entity, org.bukkit.event.entity.EntityTargetLivingEntityEvent.TargetReason.COLLISION); // CraftBukkit - set reason
        }

        super.doPush(entity);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.attackAnimationTick > 0) {
            this.attackAnimationTick--;
        }

        if (this.offerFlowerTick > 0) {
            this.offerFlowerTick--;
        }

        if (!this.level().isClientSide()) {
            this.updatePersistentAnger((ServerLevel)this.level(), true);
        }
    }

    @Override
    public boolean canSpawnSprintParticle() {
        return this.getDeltaMovement().horizontalDistanceSqr() > 2.5000003E-7F && this.random.nextInt(5) == 0;
    }

    @Override
    public boolean canAttackType(EntityType<?> type) {
        return (!this.isPlayerCreated() || type != EntityType.PLAYER) && type != EntityType.CREEPER && super.canAttackType(type);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("PlayerCreated", this.isPlayerCreated());
        this.addPersistentAngerSaveData(output);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setPlayerCreated(input.getBooleanOr("PlayerCreated", false));
        this.readPersistentAngerSaveData(this.level(), input);
    }

    @Override
    public void startPersistentAngerTimer() {
        this.setTimeToRemainAngry(PERSISTENT_ANGER_TIME.sample(this.random));
    }

    @Override
    public void setPersistentAngerEndTime(long absoluteTime) {
        this.persistentAngerEndTime = absoluteTime;
    }

    @Override
    public long getPersistentAngerEndTime() {
        return this.persistentAngerEndTime;
    }

    @Override
    public void setPersistentAngerTarget(@Nullable EntityReference<LivingEntity> target) {
        this.persistentAngerTarget = target;
    }

    @Override
    public @Nullable EntityReference<LivingEntity> getPersistentAngerTarget() {
        return this.persistentAngerTarget;
    }

    private float getAttackDamage() {
        return (float)this.getAttributeValue(Attributes.ATTACK_DAMAGE);
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        this.attackAnimationTick = 10;
        level.broadcastEntityEvent(this, EntityEvent.START_ATTACKING);
        float attackDamage = this.getAttackDamage();
        float f = (int)attackDamage > 0 ? attackDamage / 2.0F + this.random.nextInt((int)attackDamage) : attackDamage;
        DamageSource damageSource = this.damageSources().mobAttack(this);
        boolean flag = target.hurtServer(level, damageSource, f);
        if (flag) {
            double d = target instanceof LivingEntity livingEntity ? livingEntity.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) : 0.0;
            double max = Math.max(0.0, 1.0 - d);
            target.setDeltaMovement(target.getDeltaMovement().add(0.0, 0.4F * max, 0.0));
            EnchantmentHelper.doPostAttackEffects(level, target, damageSource);
        }

        this.playSound(SoundEvents.IRON_GOLEM_ATTACK, 1.0F, 1.0F);
        return flag;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        Crackiness.Level crackiness = this.getCrackiness();
        boolean flag = super.hurtServer(level, damageSource, amount);
        if (flag && this.getCrackiness() != crackiness) {
            this.playSound(SoundEvents.IRON_GOLEM_DAMAGE, 1.0F, 1.0F);
        }

        return flag;
    }

    public Crackiness.Level getCrackiness() {
        return Crackiness.GOLEM.byFraction(this.getHealth() / this.getMaxHealth());
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EntityEvent.START_ATTACKING) {
            this.attackAnimationTick = 10;
            this.playSound(SoundEvents.IRON_GOLEM_ATTACK, 1.0F, 1.0F);
        } else if (id == EntityEvent.OFFER_FLOWER) {
            this.offerFlowerTick = 400;
        } else if (id == EntityEvent.STOP_OFFER_FLOWER) {
            this.offerFlowerTick = 0;
        } else {
            super.handleEntityEvent(id);
        }
    }

    public int getAttackAnimationTick() {
        return this.attackAnimationTick;
    }

    public void offerFlower(boolean offeringFlower) {
        if (offeringFlower) {
            this.offerFlowerTick = 400;
            this.level().broadcastEntityEvent(this, EntityEvent.OFFER_FLOWER);
        } else {
            this.offerFlowerTick = 0;
            this.level().broadcastEntityEvent(this, EntityEvent.STOP_OFFER_FLOWER);
        }
    }

    @Override
    public SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.IRON_GOLEM_DEATH;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemInHand = player.getItemInHand(hand);
        if (!itemInHand.is(Items.IRON_INGOT)) {
            return InteractionResult.PASS;
        } else {
            float health = this.getHealth();
            this.heal(25.0F);
            if (this.getHealth() == health) {
                return InteractionResult.PASS;
            } else {
                float f = 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.2F;
                this.playSound(SoundEvents.IRON_GOLEM_REPAIR, 1.0F, f);
                itemInHand.consume(1, player);
                return InteractionResult.SUCCESS;
            }
        }
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState block) {
        this.playSound(SoundEvents.IRON_GOLEM_STEP, 1.0F, 1.0F);
    }

    public int getOfferFlowerTick() {
        return this.offerFlowerTick;
    }

    public boolean isPlayerCreated() {
        return (this.entityData.get(DATA_FLAGS_ID) & 1) != 0;
    }

    public void setPlayerCreated(boolean playerCreated) {
        byte b = this.entityData.get(DATA_FLAGS_ID);
        if (playerCreated) {
            this.entityData.set(DATA_FLAGS_ID, (byte)(b | 1));
        } else {
            this.entityData.set(DATA_FLAGS_ID, (byte)(b & -2));
        }
    }

    @Override
    public void die(DamageSource damageSource) {
        super.die(damageSource);
    }

    @Override
    public boolean checkSpawnObstruction(LevelReader level) {
        BlockPos blockPos = this.blockPosition();
        BlockPos blockPos1 = blockPos.below();
        BlockState blockState = level.getBlockState(blockPos1);
        if (!blockState.entityCanStandOn(level, blockPos1, this) && !this.level().paperConfig().entities.spawning.ironGolemsCanSpawnInAir) { // Paper - Add option to allow iron golems to spawn in air
            return false;
        } else {
            for (int i = 1; i < 3; i++) {
                BlockPos blockPos2 = blockPos.above(i);
                BlockState blockState1 = level.getBlockState(blockPos2);
                if (!NaturalSpawner.isValidEmptySpawnBlock(level, blockPos2, blockState1, blockState1.getFluidState(), EntityType.IRON_GOLEM)) {
                    return false;
                }
            }

            return NaturalSpawner.isValidEmptySpawnBlock(
                    level, blockPos, level.getBlockState(blockPos), Fluids.EMPTY.defaultFluidState(), EntityType.IRON_GOLEM
                )
                && level.isUnobstructed(this);
        }
    }

    @Override
    public Vec3 getLeashOffset() {
        return new Vec3(0.0, 0.875F * this.getEyeHeight(), this.getBbWidth() * 0.4F);
    }
}
