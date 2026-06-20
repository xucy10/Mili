package net.minecraft.world.entity.ambient;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class Bat extends AmbientCreature {
    public static final float FLAP_LENGTH_SECONDS = 0.5F;
    public static final float TICKS_PER_FLAP = 10.0F;
    private static final EntityDataAccessor<Byte> DATA_ID_FLAGS = SynchedEntityData.defineId(Bat.class, EntityDataSerializers.BYTE);
    private static final int FLAG_RESTING = 1;
    private static final TargetingConditions BAT_RESTING_TARGETING = TargetingConditions.forNonCombat().range(4.0);
    private static final byte DEFAULT_FLAGS = 0;
    public final AnimationState flyAnimationState = new AnimationState();
    public final AnimationState restAnimationState = new AnimationState();
    public @Nullable BlockPos targetPosition;

    public Bat(EntityType<? extends Bat> type, Level level) {
        super(type, level);
        if (!level.isClientSide()) {
            this.setResting(true);
        }
    }

    @Override
    public boolean isFlapping() {
        return !this.isResting() && this.tickCount % 10.0F == 0.0F;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ID_FLAGS, (byte)0);
    }

    @Override
    public float getSoundVolume() {
        return 0.1F;
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 0.95F;
    }

    @Override
    public @Nullable SoundEvent getAmbientSound() {
        return this.isResting() && this.random.nextInt(4) != 0 ? null : SoundEvents.BAT_AMBIENT;
    }

    @Override
    public SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.BAT_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.BAT_DEATH;
    }

    @Override
    public boolean isCollidable(boolean ignoreClimbing) { // Paper - Climbing should not bypass cramming gamerule
        return false;
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    protected void pushEntities() {
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 6.0);
    }

    public boolean isResting() {
        return (this.entityData.get(DATA_ID_FLAGS) & 1) != 0;
    }

    public void setResting(boolean isResting) {
        byte b = this.entityData.get(DATA_ID_FLAGS);
        if (isResting) {
            this.entityData.set(DATA_ID_FLAGS, (byte)(b | 1));
        } else {
            this.entityData.set(DATA_ID_FLAGS, (byte)(b & -2));
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.isResting()) {
            this.setDeltaMovement(Vec3.ZERO);
            this.setPosRaw(this.getX(), Mth.floor(this.getY()) + 1.0 - this.getBbHeight(), this.getZ());
        } else {
            this.setDeltaMovement(this.getDeltaMovement().multiply(1.0, 0.6, 1.0));
        }

        this.setupAnimationStates();
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        BlockPos blockPos = this.blockPosition();
        BlockPos blockPos1 = blockPos.above();
        if (this.isResting()) {
            boolean isSilent = this.isSilent();
            if (level.getBlockState(blockPos1).isRedstoneConductor(level, blockPos)) {
                if (this.random.nextInt(200) == 0) {
                    this.yHeadRot = this.random.nextInt(360);
                }

                if (level.getNearestPlayer(BAT_RESTING_TARGETING, this) != null && org.bukkit.craftbukkit.event.CraftEventFactory.handleBatToggleSleepEvent(this, true)) { // CraftBukkit - Call BatToggleSleepEvent
                    this.setResting(false);
                    if (!isSilent) {
                        level.levelEvent(null, LevelEvent.SOUND_BAT_LIFTOFF, blockPos, 0);
                    }
                }
            } else if (org.bukkit.craftbukkit.event.CraftEventFactory.handleBatToggleSleepEvent(this, true)) { // CraftBukkit - Call BatToggleSleepEvent
                this.setResting(false);
                if (!isSilent) {
                    level.levelEvent(null, LevelEvent.SOUND_BAT_LIFTOFF, blockPos, 0);
                }
            }
        } else {
            if (this.targetPosition != null && (!level.isEmptyBlock(this.targetPosition) || this.targetPosition.getY() <= level.getMinY())) {
                this.targetPosition = null;
            }

            if (this.targetPosition == null || this.random.nextInt(30) == 0 || this.targetPosition.closerToCenterThan(this.position(), 2.0)) {
                this.targetPosition = BlockPos.containing(
                    this.getX() + this.random.nextInt(7) - this.random.nextInt(7),
                    this.getY() + this.random.nextInt(6) - 2.0,
                    this.getZ() + this.random.nextInt(7) - this.random.nextInt(7)
                );
            }

            double d = this.targetPosition.getX() + 0.5 - this.getX();
            double d1 = this.targetPosition.getY() + 0.1 - this.getY();
            double d2 = this.targetPosition.getZ() + 0.5 - this.getZ();
            Vec3 deltaMovement = this.getDeltaMovement();
            Vec3 vec3 = deltaMovement.add(
                (Math.signum(d) * 0.5 - deltaMovement.x) * 0.1F,
                (Math.signum(d1) * 0.7F - deltaMovement.y) * 0.1F,
                (Math.signum(d2) * 0.5 - deltaMovement.z) * 0.1F
            );
            this.setDeltaMovement(vec3);
            float f = (float)(Mth.atan2(vec3.z, vec3.x) * 180.0F / (float)Math.PI) - 90.0F;
            float f1 = Mth.wrapDegrees(f - this.getYRot());
            this.zza = 0.5F;
            this.setYRot(this.getYRot() + f1);
            if (this.random.nextInt(100) == 0 && level.getBlockState(blockPos1).isRedstoneConductor(level, blockPos1) && org.bukkit.craftbukkit.event.CraftEventFactory.handleBatToggleSleepEvent(this, false)) { // CraftBukkit - Call BatToggleSleepEvent
                this.setResting(true);
            }
        }
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.EVENTS;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        if (this.isInvulnerableTo(level, damageSource)) {
            return false;
        } else {
            if (this.isResting() && org.bukkit.craftbukkit.event.CraftEventFactory.handleBatToggleSleepEvent(this, true)) { // CraftBukkit - Call BatToggleSleepEvent
                this.setResting(false);
            }

            return super.hurtServer(level, damageSource, amount);
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.entityData.set(DATA_ID_FLAGS, input.getByteOr("BatFlags", (byte)0));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putByte("BatFlags", this.entityData.get(DATA_ID_FLAGS));
    }

    public static boolean checkBatSpawnRules(
        EntityType<Bat> entityType, LevelAccessor level, EntitySpawnReason spawnReason, BlockPos pos, RandomSource randomSource
    ) {
        return pos.getY() < level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, pos).getY()
            && !randomSource.nextBoolean()
            && level.getMaxLocalRawBrightness(pos) <= randomSource.nextInt(4)
            && level.getBlockState(pos.below()).is(BlockTags.BATS_SPAWNABLE_ON)
            && checkMobSpawnRules(entityType, level, spawnReason, pos, randomSource);
    }

    private void setupAnimationStates() {
        if (this.isResting()) {
            this.flyAnimationState.stop();
            this.restAnimationState.startIfStopped(this.tickCount);
        } else {
            this.restAnimationState.stop();
            this.flyAnimationState.startIfStopped(this.tickCount);
        }
    }
}
