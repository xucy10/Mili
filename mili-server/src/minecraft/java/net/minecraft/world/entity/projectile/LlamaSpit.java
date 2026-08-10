package net.minecraft.world.entity.projectile;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.equine.Llama;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class LlamaSpit extends Projectile {
    public LlamaSpit(EntityType<? extends LlamaSpit> type, Level level) {
        super(type, level);
    }

    public LlamaSpit(Level level, Llama spitter) {
        this(EntityType.LLAMA_SPIT, level);
        this.setOwner(spitter);
        this.setPos(
            spitter.getX() - (spitter.getBbWidth() + 1.0F) * 0.5 * Mth.sin(spitter.yBodyRot * (float) (Math.PI / 180.0)),
            spitter.getEyeY() - 0.1F,
            spitter.getZ() + (spitter.getBbWidth() + 1.0F) * 0.5 * Mth.cos(spitter.yBodyRot * (float) (Math.PI / 180.0))
        );
    }

    @Override
    protected double getDefaultGravity() {
        return 0.06;
    }

    @Override
    public void tick() {
        super.tick();
        // Folia start - region threading - make sure entities do not move into regions they do not own
        if (!ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor((net.minecraft.server.level.ServerLevel)this.level(), this.position(), this.getDeltaMovement(), 1)) {
            return;
        }
        // Folia end - region threading - make sure entities do not move into regions they do not own
        Vec3 deltaMovement = this.getDeltaMovement();
        HitResult hitResultOnMoveVector = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        this.preHitTargetOrDeflectSelf(hitResultOnMoveVector); // CraftBukkit - projectile hit event
        double d = this.getX() + deltaMovement.x;
        double d1 = this.getY() + deltaMovement.y;
        double d2 = this.getZ() + deltaMovement.z;
        this.updateRotation();
        float f = 0.99F;
        if (this.level().getBlockStates(this.getBoundingBox()).noneMatch(BlockBehaviour.BlockStateBase::isAir)) {
            this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
        } else if (this.isInWater()) {
            this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
        } else {
            this.setDeltaMovement(deltaMovement.scale(0.99F));
            this.applyGravity();
            this.setPos(d, d1, d2);
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (this.getOwner() instanceof LivingEntity livingEntity) {
            Entity entity = result.getEntity();
            DamageSource damageSource = this.damageSources().spit(this, livingEntity);
            if (this.level() instanceof ServerLevel serverLevel && entity.hurtServer(serverLevel, damageSource, 1.0F)) {
                EnchantmentHelper.doPostAttackEffects(serverLevel, entity, damageSource);
            }
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (!this.level().isClientSide()) {
            this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.HIT); // CraftBukkit - add Bukkit remove cause
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        Vec3 movement = packet.getMovement();

        for (int i = 0; i < 7; i++) {
            double d = 0.4 + 0.1 * i;
            this.level().addParticle(ParticleTypes.SPIT, this.getX(), this.getY(), this.getZ(), movement.x * d, movement.y, movement.z * d);
        }

        this.setDeltaMovement(movement);
    }
}
