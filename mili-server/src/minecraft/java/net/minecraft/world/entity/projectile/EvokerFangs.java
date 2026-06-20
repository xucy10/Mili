package net.minecraft.world.entity.projectile;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class EvokerFangs extends Entity implements TraceableEntity {
    public static final int ATTACK_DURATION = 20;
    public static final int LIFE_OFFSET = 2;
    public static final int ATTACK_TRIGGER_TICKS = 14;
    private static final int DEFAULT_WARMUP_DELAY = 0;
    public int warmupDelayTicks = 0;
    private boolean sentSpikeEvent;
    private int lifeTicks = 22;
    private boolean clientSideAttackStarted;
    private @Nullable EntityReference<LivingEntity> owner;

    public EvokerFangs(EntityType<? extends EvokerFangs> type, Level level) {
        super(type, level);
    }

    public EvokerFangs(Level level, double x, double y, double z, float yRot, int warmupDelay, LivingEntity owner) {
        this(EntityType.EVOKER_FANGS, level);
        this.warmupDelayTicks = warmupDelay;
        this.setOwner(owner);
        this.setYRot(yRot * (180.0F / (float)Math.PI));
        this.setPos(x, y, z);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    public void setOwner(@Nullable LivingEntity owner) {
        this.owner = EntityReference.of(owner);
    }

    @Override
    public @Nullable LivingEntity getOwner() {
        return EntityReference.getLivingEntity(this.owner, this.level());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.warmupDelayTicks = input.getIntOr("Warmup", 0);
        this.owner = EntityReference.read(input, "Owner");
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putInt("Warmup", this.warmupDelayTicks);
        EntityReference.store(this.owner, output, "Owner");
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            if (this.clientSideAttackStarted) {
                this.lifeTicks--;
                if (this.lifeTicks == 14) {
                    for (int i = 0; i < 12; i++) {
                        double d = this.getX() + (this.random.nextDouble() * 2.0 - 1.0) * this.getBbWidth() * 0.5;
                        double d1 = this.getY() + 0.05 + this.random.nextDouble();
                        double d2 = this.getZ() + (this.random.nextDouble() * 2.0 - 1.0) * this.getBbWidth() * 0.5;
                        double d3 = (this.random.nextDouble() * 2.0 - 1.0) * 0.3;
                        double d4 = 0.3 + this.random.nextDouble() * 0.3;
                        double d5 = (this.random.nextDouble() * 2.0 - 1.0) * 0.3;
                        this.level().addParticle(ParticleTypes.CRIT, d, d1 + 1.0, d2, d3, d4, d5);
                    }
                }
            }
        } else if (--this.warmupDelayTicks < 0) {
            if (this.warmupDelayTicks == -8) {
                for (LivingEntity livingEntity : this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(0.2, 0.0, 0.2))) {
                    this.dealDamageTo(livingEntity);
                }
            }

            if (!this.sentSpikeEvent) {
                this.level().broadcastEntityEvent(this, EntityEvent.START_ATTACKING);
                this.sentSpikeEvent = true;
            }

            if (--this.lifeTicks < 0) {
                this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
            }
        }
    }

    private void dealDamageTo(LivingEntity target) {
        LivingEntity owner = this.getOwner();
        if (target.isAlive() && !target.isInvulnerable() && target != owner) {
            if (owner == null) {
                target.hurt(this.damageSources().magic().eventEntityDamager(this), 6.0F); // CraftBukkit
            } else {
                if (owner.isAlliedTo(target)) {
                    return;
                }

                DamageSource damageSource = this.damageSources().indirectMagic(this, owner);
                if (this.level() instanceof ServerLevel serverLevel && target.hurtServer(serverLevel, damageSource, 6.0F)) {
                    EnchantmentHelper.doPostAttackEffects(serverLevel, target, damageSource);
                }
            }
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        super.handleEntityEvent(id);
        if (id == EntityEvent.START_ATTACKING) {
            this.clientSideAttackStarted = true;
            if (!this.isSilent()) {
                this.level()
                    .playLocalSound(
                        this.getX(),
                        this.getY(),
                        this.getZ(),
                        SoundEvents.EVOKER_FANGS_ATTACK,
                        this.getSoundSource(),
                        1.0F,
                        this.random.nextFloat() * 0.2F + 0.85F,
                        false
                    );
            }
        }
    }

    public float getAnimationProgress(float partialTick) {
        if (!this.clientSideAttackStarted) {
            return 0.0F;
        } else {
            int i = this.lifeTicks - 2;
            return i <= 0 ? 1.0F : 1.0F - (i - partialTick) / 20.0F;
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        return false;
    }
}
