package net.minecraft.world.entity.projectile;

import it.unimi.dsi.fastutil.doubles.DoubleDoubleImmutablePair;
import java.util.List;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class FireworkRocketEntity extends Projectile implements ItemSupplier {
    public static final EntityDataAccessor<ItemStack> DATA_ID_FIREWORKS_ITEM = SynchedEntityData.defineId(
        FireworkRocketEntity.class, EntityDataSerializers.ITEM_STACK
    );
    public static final EntityDataAccessor<OptionalInt> DATA_ATTACHED_TO_TARGET = SynchedEntityData.defineId(
        FireworkRocketEntity.class, EntityDataSerializers.OPTIONAL_UNSIGNED_INT
    );
    public static final EntityDataAccessor<Boolean> DATA_SHOT_AT_ANGLE = SynchedEntityData.defineId(FireworkRocketEntity.class, EntityDataSerializers.BOOLEAN);
    private static final int DEFAULT_LIFE = 0;
    private static final int DEFAULT_LIFE_TIME = 0;
    private static final boolean DEFAULT_SHOT_AT_ANGLE = false;
    public int life = 0;
    public int lifetime = 0;
    public @Nullable LivingEntity attachedToEntity;
    public java.util.@Nullable UUID spawningEntity; // Paper

    public FireworkRocketEntity(EntityType<? extends FireworkRocketEntity> type, Level level) {
        super(type, level);
    }

    public FireworkRocketEntity(Level level, double x, double y, double z, ItemStack stack) {
        super(EntityType.FIREWORK_ROCKET, level);
        this.life = 0;
        this.setPos(x, y, z);
        this.entityData.set(DATA_ID_FIREWORKS_ITEM, stack.copy());
        int i = 1;
        Fireworks fireworks = stack.get(DataComponents.FIREWORKS);
        if (fireworks != null) {
            i += fireworks.flightDuration();
        }

        this.setDeltaMovement(this.random.triangle(0.0, 0.002297), 0.05, this.random.triangle(0.0, 0.002297));
        this.lifetime = 10 * i + this.random.nextInt(6) + this.random.nextInt(7);
    }

    public FireworkRocketEntity(Level level, @Nullable Entity shooter, double x, double y, double z, ItemStack stack) {
        this(level, x, y, z, stack);
        this.setOwner(shooter);
    }

    public FireworkRocketEntity(Level level, ItemStack stack, LivingEntity shooter) {
        this(level, shooter, shooter.getX(), shooter.getY(), shooter.getZ(), stack);
        this.entityData.set(DATA_ATTACHED_TO_TARGET, OptionalInt.of(shooter.getId()));
        this.attachedToEntity = shooter;
    }

    public FireworkRocketEntity(Level level, ItemStack stack, double x, double y, double z, boolean shotAtAngle) {
        this(level, x, y, z, stack);
        this.entityData.set(DATA_SHOT_AT_ANGLE, shotAtAngle);
    }

    public FireworkRocketEntity(Level level, ItemStack stack, Entity shooter, double x, double y, double z, boolean shotAtAngle) {
        this(level, stack, x, y, z, shotAtAngle);
        this.setOwner(shooter);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_ID_FIREWORKS_ITEM, getDefaultItem());
        builder.define(DATA_ATTACHED_TO_TARGET, OptionalInt.empty());
        builder.define(DATA_SHOT_AT_ANGLE, false);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 4096.0 && !this.isAttachedToEntity();
    }

    @Override
    public boolean shouldRender(double x, double y, double z) {
        return super.shouldRender(x, y, z) && !this.isAttachedToEntity();
    }

    // Paper start - EAR 2
    @Override
    public void inactiveTick() {
        this.life++;
        if (this.life > this.lifetime && this.level() instanceof ServerLevel serverLevel) {
            // Paper start - Call FireworkExplodeEvent
            if (org.bukkit.craftbukkit.event.CraftEventFactory.callFireworkExplodeEvent(this)) {
                this.explode(serverLevel);
            }
            // Paper end - Call FireworkExplodeEvent
        }
        super.inactiveTick();
    }
    // Paper end - EAR 2

    @Override
    public void tick() {
        super.tick();
        HitResult hitResultOnMoveVector;
        if (this.isAttachedToEntity()) {
            if (this.attachedToEntity == null) {
                this.entityData.get(DATA_ATTACHED_TO_TARGET).ifPresent(target -> {
                    Entity entity = this.level().getEntity(target);
                    if (entity instanceof LivingEntity) {
                        this.attachedToEntity = (LivingEntity)entity;
                    }
                });
            }
            // Folia start - region threading
            if (this.attachedToEntity != null && !ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(this.attachedToEntity)) {
                this.attachedToEntity = null;
            }
            // Folia end - region threading

            if (this.attachedToEntity != null) {
                Vec3 handHoldingItemAngle;
                if (this.attachedToEntity.isFallFlying()) {
                    Vec3 lookAngle = this.attachedToEntity.getLookAngle();
                    double d = 1.5;
                    double d1 = 0.1;
                    Vec3 deltaMovement = this.attachedToEntity.getDeltaMovement();
                    this.attachedToEntity
                        .setDeltaMovement(
                            deltaMovement.add(
                                lookAngle.x * 0.1 + (lookAngle.x * 1.5 - deltaMovement.x) * 0.5,
                                lookAngle.y * 0.1 + (lookAngle.y * 1.5 - deltaMovement.y) * 0.5,
                                lookAngle.z * 0.1 + (lookAngle.z * 1.5 - deltaMovement.z) * 0.5
                            )
                        );
                    handHoldingItemAngle = this.attachedToEntity.getHandHoldingItemAngle(Items.FIREWORK_ROCKET);
                } else {
                    handHoldingItemAngle = Vec3.ZERO;
                }

                this.setPos(
                    this.attachedToEntity.getX() + handHoldingItemAngle.x,
                    this.attachedToEntity.getY() + handHoldingItemAngle.y,
                    this.attachedToEntity.getZ() + handHoldingItemAngle.z
                );
                this.setDeltaMovement(this.attachedToEntity.getDeltaMovement());
            }

            hitResultOnMoveVector = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        } else {
            if (!this.isShotAtAngle()) {
                double d2 = this.horizontalCollision ? 1.0 : 1.15;
                this.setDeltaMovement(this.getDeltaMovement().multiply(d2, 1.0, d2).add(0.0, 0.04, 0.0));
            }

            Vec3 handHoldingItemAngle = this.getDeltaMovement();
            hitResultOnMoveVector = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            this.move(MoverType.SELF, handHoldingItemAngle);
            this.applyEffectsFromBlocks();
            this.setDeltaMovement(handHoldingItemAngle);
        }

        if (!this.noPhysics && this.isAlive() && hitResultOnMoveVector.getType() != HitResult.Type.MISS) {
            this.preHitTargetOrDeflectSelf(hitResultOnMoveVector); // CraftBukkit - projectile hit event
            this.needsSync = true;
        }

        this.updateRotation();
        if (this.life == 0 && !this.isSilent()) {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.AMBIENT, 3.0F, 1.0F);
        }

        this.life++;
        if (this.level().isClientSide() && this.life % 2 < 2) {
            this.level()
                .addParticle(
                    ParticleTypes.FIREWORK,
                    this.getX(),
                    this.getY(),
                    this.getZ(),
                    this.random.nextGaussian() * 0.05,
                    -this.getDeltaMovement().y * 0.5,
                    this.random.nextGaussian() * 0.05
                );
        }

        if (this.life > this.lifetime && this.level() instanceof ServerLevel serverLevel) {
            // Paper start - Call FireworkExplodeEvent
            if (org.bukkit.craftbukkit.event.CraftEventFactory.callFireworkExplodeEvent(this)) {
                this.explode(serverLevel);
            }
            // Paper end - Call FireworkExplodeEvent
        }
    }

    private void explode(ServerLevel level) {
        level.broadcastEntityEvent(this, EntityEvent.FIREWORKS_EXPLODE);
        this.gameEvent(GameEvent.EXPLODE, this.getOwner());
        this.dealExplosionDamage(level);
        this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.EXPLODE); // CraftBukkit - add Bukkit remove cause
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (this.level() instanceof ServerLevel serverLevel) {
            // Paper start - Call FireworkExplodeEvent
            if (org.bukkit.craftbukkit.event.CraftEventFactory.callFireworkExplodeEvent(this)) {
                this.explode(serverLevel);
            }
            // Paper end - Call FireworkExplodeEvent
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        BlockPos blockPos = new BlockPos(result.getBlockPos());
        this.level().getBlockState(blockPos).entityInside(this.level(), blockPos, this, InsideBlockEffectApplier.NOOP, true);
        if (this.level() instanceof ServerLevel serverLevel && this.hasExplosion()) {
            // Paper start - Call FireworkExplodeEvent
            if (org.bukkit.craftbukkit.event.CraftEventFactory.callFireworkExplodeEvent(this)) {
                this.explode(serverLevel);
            }
            // Paper end - Call FireworkExplodeEvent
        }

        super.onHitBlock(result);
    }

    private boolean hasExplosion() {
        return !this.getExplosions().isEmpty();
    }

    private void dealExplosionDamage(ServerLevel level) {
        float f = 0.0F;
        List<FireworkExplosion> explosions = this.getExplosions();
        if (!explosions.isEmpty()) {
            f = 5.0F + explosions.size() * 2;
        }

        if (f > 0.0F) {
            if (this.attachedToEntity != null) {
                this.attachedToEntity.hurtServer(level, this.damageSources().fireworks(this, this.getOwner()), 5.0F + explosions.size() * 2);
            }

            double d = 5.0;
            Vec3 vec3 = this.position();

            for (LivingEntity livingEntity : this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(5.0))) {
                if (livingEntity != this.attachedToEntity && !(this.distanceToSqr(livingEntity) > 25.0)) {
                    boolean flag = false;

                    for (int i = 0; i < 2; i++) {
                        Vec3 vec31 = new Vec3(livingEntity.getX(), livingEntity.getY(0.5 * i), livingEntity.getZ());
                        HitResult hitResult = this.level().clip(new ClipContext(vec3, vec31, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
                        if (hitResult.getType() == HitResult.Type.MISS) {
                            flag = true;
                            break;
                        }
                    }

                    if (flag) {
                        float f1 = f * (float)Math.sqrt((5.0 - this.distanceTo(livingEntity)) / 5.0);
                        livingEntity.hurtServer(level, this.damageSources().fireworks(this, this.getOwner()), f1);
                    }
                }
            }
        }
    }

    private boolean isAttachedToEntity() {
        return this.entityData.get(DATA_ATTACHED_TO_TARGET).isPresent();
    }

    public boolean isShotAtAngle() {
        return this.entityData.get(DATA_SHOT_AT_ANGLE);
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EntityEvent.FIREWORKS_EXPLODE && this.level().isClientSide()) {
            Vec3 deltaMovement = this.getDeltaMovement();
            this.level().createFireworks(this.getX(), this.getY(), this.getZ(), deltaMovement.x, deltaMovement.y, deltaMovement.z, this.getExplosions());
        }

        super.handleEntityEvent(id);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("Life", this.life);
        output.putInt("LifeTime", this.lifetime);
        output.store("FireworksItem", ItemStack.CODEC, this.getItem());
        output.putBoolean("ShotAtAngle", this.entityData.get(DATA_SHOT_AT_ANGLE));
        output.storeNullable("SpawningEntity", net.minecraft.core.UUIDUtil.CODEC, this.spawningEntity); // Paper
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.life = input.getIntOr("Life", 0);
        this.lifetime = input.getIntOr("LifeTime", 0);
        this.entityData.set(DATA_ID_FIREWORKS_ITEM, input.read("FireworksItem", ItemStack.CODEC).orElse(getDefaultItem()));
        this.entityData.set(DATA_SHOT_AT_ANGLE, input.getBooleanOr("ShotAtAngle", false));
        this.spawningEntity = input.read("SpawningEntity", net.minecraft.core.UUIDUtil.CODEC).orElse(null); // Paper
    }

    private List<FireworkExplosion> getExplosions() {
        ItemStack itemStack = this.entityData.get(DATA_ID_FIREWORKS_ITEM);
        Fireworks fireworks = itemStack.get(DataComponents.FIREWORKS);
        return fireworks != null ? fireworks.explosions() : List.of();
    }

    @Override
    public ItemStack getItem() {
        return this.entityData.get(DATA_ID_FIREWORKS_ITEM);
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    public static ItemStack getDefaultItem() {
        return new ItemStack(Items.FIREWORK_ROCKET);
    }

    @Override
    public DoubleDoubleImmutablePair calculateHorizontalHurtKnockbackDirection(LivingEntity entity, DamageSource damageSource) {
        double d = entity.position().x - this.position().x;
        double d1 = entity.position().z - this.position().z;
        return DoubleDoubleImmutablePair.of(d, d1);
    }
}
