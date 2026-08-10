package net.minecraft.world.entity.projectile.hurtingprojectile;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public abstract class AbstractHurtingProjectile extends Projectile {
    public static final double INITAL_ACCELERATION_POWER = 0.1;
    public static final double DEFLECTION_SCALE = 0.5;
    public double accelerationPower = 0.1;
    public float bukkitYield = 1; // CraftBukkit
    public boolean isIncendiary = true; // CraftBukkit

    protected AbstractHurtingProjectile(EntityType<? extends AbstractHurtingProjectile> type, Level level) {
        super(type, level);
    }

    protected AbstractHurtingProjectile(EntityType<? extends AbstractHurtingProjectile> type, double x, double y, double z, Level level) {
        this(type, level);
        this.setPos(x, y, z);
    }

    public AbstractHurtingProjectile(EntityType<? extends AbstractHurtingProjectile> type, double x, double y, double z, Vec3 movement, Level level) {
        this(type, level);
        this.snapTo(x, y, z, this.getYRot(), this.getXRot());
        this.reapplyPosition();
        this.assignDirectionalMovement(movement, this.accelerationPower);
    }

    public AbstractHurtingProjectile(EntityType<? extends AbstractHurtingProjectile> type, LivingEntity owner, Vec3 movement, Level level) {
        this(type, owner.getX(), owner.getY(), owner.getZ(), movement, level);
        this.setOwner(owner);
        this.setRot(owner.getYRot(), owner.getXRot());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double d = this.getBoundingBox().getSize() * 4.0;
        if (Double.isNaN(d)) {
            d = 4.0;
        }

        d *= 64.0;
        return distance < d * d;
    }

    protected ClipContext.Block getClipType() {
        return ClipContext.Block.COLLIDER;
    }

    @Override
    public void tick() {
        Entity owner = this.getOwner();
        this.applyInertia();
        if (this.level().isClientSide() || (owner == null || !owner.isRemoved()) && this.level().hasChunkAt(this.blockPosition())) {
            HitResult hitResultOnMoveVector = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity, this.getClipType());
            Vec3 location;
            if (hitResultOnMoveVector.getType() != HitResult.Type.MISS) {
                location = hitResultOnMoveVector.getLocation();
            } else {
                location = this.position().add(this.getDeltaMovement());
            }

            ProjectileUtil.rotateTowardsMovement(this, 0.2F);
            this.setPos(location);
            this.applyEffectsFromBlocks();
            super.tick();
            // Folia start - region threading - make sure entities do not move into regions they do not own
            if (!ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor((net.minecraft.server.level.ServerLevel)this.level(), this.position(), this.getDeltaMovement(), 1)) {
                return;
            }
            // Folia end - region threading - make sure entities do not move into regions they do not own
            if (this.shouldBurn()) {
                this.igniteForSeconds(1.0F);
            }

            if (hitResultOnMoveVector.getType() != HitResult.Type.MISS && this.isAlive()) {
                this.preHitTargetOrDeflectSelf(hitResultOnMoveVector); // CraftBukkit - projectile hit event
            }

            this.createParticleTrail();
        } else {
            this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
        }
    }

    private void applyInertia() {
        Vec3 deltaMovement = this.getDeltaMovement();
        Vec3 vec3 = this.position();
        float liquidInertia;
        if (this.isInWater()) {
            for (int i = 0; i < 4; i++) {
                float f = 0.25F;
                this.level()
                    .addParticle(
                        ParticleTypes.BUBBLE,
                        vec3.x - deltaMovement.x * 0.25,
                        vec3.y - deltaMovement.y * 0.25,
                        vec3.z - deltaMovement.z * 0.25,
                        deltaMovement.x,
                        deltaMovement.y,
                        deltaMovement.z
                    );
            }

            liquidInertia = this.getLiquidInertia();
        } else {
            liquidInertia = this.getInertia();
        }

        this.setDeltaMovement(deltaMovement.add(deltaMovement.normalize().scale(this.accelerationPower)).scale(liquidInertia));
    }

    private void createParticleTrail() {
        ParticleOptions trailParticle = this.getTrailParticle();
        Vec3 vec3 = this.position();
        if (trailParticle != null) {
            this.level().addParticle(trailParticle, vec3.x, vec3.y + 0.5, vec3.z, 0.0, 0.0, 0.0);
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        return false;
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return super.canHitEntity(target) && !target.noPhysics;
    }

    protected boolean shouldBurn() {
        return true;
    }

    protected @Nullable ParticleOptions getTrailParticle() {
        return ParticleTypes.SMOKE;
    }

    protected float getInertia() {
        return 0.95F;
    }

    protected float getLiquidInertia() {
        return 0.8F;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putDouble("acceleration_power", this.accelerationPower);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.accelerationPower = input.getDoubleOr("acceleration_power", 0.1);
    }

    @Override
    public float getLightLevelDependentMagicValue() {
        return 1.0F;
    }

    public void assignDirectionalMovement(Vec3 movement, double accelerationPower) {
        this.setDeltaMovement(movement.normalize().scale(accelerationPower));
        this.needsSync = true;
    }

    @Override
    protected void onDeflection(boolean deflectedByPlayer) {
        super.onDeflection(deflectedByPlayer);
        if (deflectedByPlayer) {
            this.accelerationPower = 0.1;
        } else {
            this.accelerationPower *= 0.5;
        }
    }
}
