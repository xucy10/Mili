package net.minecraft.world.entity.monster;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class Vex extends Monster implements TraceableEntity {
    public static final float FLAP_DEGREES_PER_TICK = 45.836624F;
    public static final int TICKS_PER_FLAP = Mth.ceil((float) (Math.PI * 5.0 / 4.0));
    protected static final EntityDataAccessor<Byte> DATA_FLAGS_ID = SynchedEntityData.defineId(Vex.class, EntityDataSerializers.BYTE);
    private static final int FLAG_IS_CHARGING = 1;
    public @Nullable EntityReference<Mob> owner;
    private @Nullable BlockPos boundOrigin;
    public boolean hasLimitedLife;
    public int limitedLifeTicks;

    public Vex(EntityType<? extends Vex> type, Level level) {
        super(type, level);
        this.moveControl = new Vex.VexMoveControl(this);
        this.xpReward = 3;
    }

    @Override
    public boolean isFlapping() {
        return this.tickCount % TICKS_PER_FLAP == 0;
    }

    @Override
    public boolean isAffectedByBlocks() {
        return !this.isRemoved();
    }

    @Override
    public void tick() {
        this.noPhysics = true;
        super.tick();
        this.noPhysics = false;
        this.setNoGravity(true);
        if (this.hasLimitedLife && --this.limitedLifeTicks <= 0) {
            this.limitedLifeTicks = 20;
            this.hurt(this.damageSources().starve(), 1.0F);
        }
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(4, new Vex.VexChargeAttackGoal());
        this.goalSelector.addGoal(8, new Vex.VexRandomMoveGoal());
        this.goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 3.0F, 1.0F));
        this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, Mob.class, 8.0F));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, Raider.class).setAlertOthers());
        this.targetSelector.addGoal(2, new Vex.VexCopyOwnerTargetGoal(this));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 14.0).add(Attributes.ATTACK_DAMAGE, 4.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_FLAGS_ID, (byte)0);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.boundOrigin = input.read("bound_pos", BlockPos.CODEC).orElse(null);
        input.getInt("life_ticks").ifPresentOrElse(this::setLimitedLife, () -> this.hasLimitedLife = false);
        this.owner = EntityReference.read(input, "owner");
    }

    @Override
    public void restoreFrom(Entity entity) {
        super.restoreFrom(entity);
        if (entity instanceof Vex vex) {
            this.owner = vex.owner;
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.storeNullable("bound_pos", BlockPos.CODEC, this.boundOrigin);
        if (this.hasLimitedLife) {
            output.putInt("life_ticks", this.limitedLifeTicks);
        }

        EntityReference.store(this.owner, output, "owner");
    }

    @Override
    public @Nullable Mob getOwner() {
        return EntityReference.get(this.owner, this.level(), Mob.class);
    }

    public @Nullable BlockPos getBoundOrigin() {
        return this.boundOrigin;
    }

    public void setBoundOrigin(@Nullable BlockPos boundOrigin) {
        this.boundOrigin = boundOrigin;
    }

    private boolean getVexFlag(int mask) {
        int i = this.entityData.get(DATA_FLAGS_ID);
        return (i & mask) != 0;
    }

    private void setVexFlag(int mask, boolean value) {
        int i = this.entityData.get(DATA_FLAGS_ID);
        if (value) {
            i |= mask;
        } else {
            i &= ~mask;
        }

        this.entityData.set(DATA_FLAGS_ID, (byte)(i & 0xFF));
    }

    public boolean isCharging() {
        return this.getVexFlag(FLAG_IS_CHARGING);
    }

    public void setIsCharging(boolean charging) {
        this.setVexFlag(FLAG_IS_CHARGING, charging);
    }

    public void setOwner(Mob owner) {
        this.owner = EntityReference.of(owner);
    }

    public void setLimitedLife(int limitedLifeTicks) {
        this.hasLimitedLife = true;
        this.limitedLifeTicks = limitedLifeTicks;
    }

    @Override
    public SoundEvent getAmbientSound() {
        return SoundEvents.VEX_AMBIENT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.VEX_DEATH;
    }

    @Override
    public SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.VEX_HURT;
    }

    @Override
    public float getLightLevelDependentMagicValue() {
        return 1.0F;
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(
        ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData spawnGroupData
    ) {
        RandomSource random = level.getRandom();
        this.populateDefaultEquipmentSlots(random, difficulty);
        this.populateDefaultEquipmentEnchantments(level, random, difficulty);
        return super.finalizeSpawn(level, difficulty, spawnReason, spawnGroupData);
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
    }

    class VexChargeAttackGoal extends Goal {
        public VexChargeAttackGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = Vex.this.getTarget();
            return target != null
                && target.isAlive()
                && !Vex.this.getMoveControl().hasWanted()
                && Vex.this.random.nextInt(reducedTickDelay(7)) == 0
                && Vex.this.distanceToSqr(target) > 4.0;
        }

        @Override
        public boolean canContinueToUse() {
            return Vex.this.getMoveControl().hasWanted() && Vex.this.isCharging() && Vex.this.getTarget() != null && Vex.this.getTarget().isAlive();
        }

        @Override
        public void start() {
            LivingEntity target = Vex.this.getTarget();
            if (target != null) {
                Vec3 eyePosition = target.getEyePosition();
                Vex.this.moveControl.setWantedPosition(eyePosition.x, eyePosition.y, eyePosition.z, 1.0);
            }

            Vex.this.setIsCharging(true);
            Vex.this.playSound(SoundEvents.VEX_CHARGE, 1.0F, 1.0F);
        }

        @Override
        public void stop() {
            Vex.this.setIsCharging(false);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = Vex.this.getTarget();
            if (target != null) {
                if (Vex.this.getBoundingBox().intersects(target.getBoundingBox())) {
                    Vex.this.doHurtTarget(getServerLevel(Vex.this.level()), target);
                    Vex.this.setIsCharging(false);
                } else {
                    double d = Vex.this.distanceToSqr(target);
                    if (d < 9.0) {
                        Vec3 eyePosition = target.getEyePosition();
                        Vex.this.moveControl.setWantedPosition(eyePosition.x, eyePosition.y, eyePosition.z, 1.0);
                    }
                }
            }
        }
    }

    class VexCopyOwnerTargetGoal extends TargetGoal {
        private final TargetingConditions copyOwnerTargeting = TargetingConditions.forNonCombat().ignoreLineOfSight().ignoreInvisibilityTesting();

        public VexCopyOwnerTargetGoal(final PathfinderMob mob) {
            super(mob, false);
        }

        @Override
        public boolean canUse() {
            Mob owner = Vex.this.getOwner();
            return owner != null && owner.getTarget() != null && this.canAttack(owner.getTarget(), this.copyOwnerTargeting);
        }

        @Override
        public void start() {
            Mob owner = Vex.this.getOwner();
            Vex.this.setTarget(owner != null ? owner.getTarget() : null, org.bukkit.event.entity.EntityTargetEvent.TargetReason.OWNER_ATTACKED_TARGET); // CraftBukkit
            super.start();
        }
    }

    class VexMoveControl extends MoveControl {
        public VexMoveControl(final Vex mob) {
            super(mob);
        }

        @Override
        public void tick() {
            if (this.operation == MoveControl.Operation.MOVE_TO) {
                Vec3 vec3 = new Vec3(this.wantedX - Vex.this.getX(), this.wantedY - Vex.this.getY(), this.wantedZ - Vex.this.getZ());
                double len = vec3.length();
                if (len < Vex.this.getBoundingBox().getSize()) {
                    this.operation = MoveControl.Operation.WAIT;
                    Vex.this.setDeltaMovement(Vex.this.getDeltaMovement().scale(0.5));
                } else {
                    Vex.this.setDeltaMovement(Vex.this.getDeltaMovement().add(vec3.scale(this.speedModifier * 0.05 / len)));
                    if (Vex.this.getTarget() == null) {
                        Vec3 deltaMovement = Vex.this.getDeltaMovement();
                        Vex.this.setYRot(-((float)Mth.atan2(deltaMovement.x, deltaMovement.z)) * (180.0F / (float)Math.PI));
                        Vex.this.yBodyRot = Vex.this.getYRot();
                    } else {
                        double d = Vex.this.getTarget().getX() - Vex.this.getX();
                        double d1 = Vex.this.getTarget().getZ() - Vex.this.getZ();
                        Vex.this.setYRot(-((float)Mth.atan2(d, d1)) * (180.0F / (float)Math.PI));
                        Vex.this.yBodyRot = Vex.this.getYRot();
                    }
                }
            }
        }
    }

    class VexRandomMoveGoal extends Goal {
        public VexRandomMoveGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return !Vex.this.getMoveControl().hasWanted() && Vex.this.random.nextInt(reducedTickDelay(7)) == 0;
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void tick() {
            BlockPos boundOrigin = Vex.this.getBoundOrigin();
            if (boundOrigin == null || !ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor((net.minecraft.server.level.ServerLevel)Vex.this.level(), boundOrigin)) { // Folia - region threading
                boundOrigin = Vex.this.blockPosition();
            }

            for (int i = 0; i < 3; i++) {
                BlockPos blockPos = boundOrigin.offset(Vex.this.random.nextInt(15) - 7, Vex.this.random.nextInt(11) - 5, Vex.this.random.nextInt(15) - 7);
                // Paper start - Don't load chunks
                final net.minecraft.world.level.block.state.BlockState blockState = Vex.this.level().getBlockStateIfLoaded(blockPos);
                if (blockState != null && blockState.isAir()) {
                    // Paper end - Don't load chunks
                    Vex.this.moveControl.setWantedPosition(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5, 0.25);
                    if (Vex.this.getTarget() == null) {
                        Vex.this.getLookControl().setLookAt(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5, 180.0F, 20.0F);
                    }
                    break;
                }
            }
        }
    }
}
