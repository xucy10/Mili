package net.minecraft.world.entity.projectile.hurtingprojectile;

import java.util.List;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.PowerParticleOption;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class DragonFireball extends AbstractHurtingProjectile {
    public static final float SPLASH_RANGE = 4.0F;

    public DragonFireball(EntityType<? extends DragonFireball> type, Level level) {
        super(type, level);
    }

    public DragonFireball(Level level, LivingEntity owner, Vec3 movement) {
        super(EntityType.DRAGON_FIREBALL, owner, movement, level);
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (result.getType() != HitResult.Type.ENTITY || !this.ownedBy(((EntityHitResult)result).getEntity())) {
            if (!this.level().isClientSide()) {
                List<LivingEntity> entitiesOfClass = this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(4.0, 2.0, 4.0));
                AreaEffectCloud areaEffectCloud = new AreaEffectCloud(this.level(), this.getX(), this.getY(), this.getZ());
                Entity owner = this.getOwner();
                if (owner instanceof LivingEntity) {
                    areaEffectCloud.setOwner((LivingEntity)owner);
                }

                areaEffectCloud.setCustomParticle(PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0F));
                areaEffectCloud.setRadius(3.0F);
                areaEffectCloud.setDuration(600);
                areaEffectCloud.setRadiusPerTick((7.0F - areaEffectCloud.getRadius()) / areaEffectCloud.getDuration());
                areaEffectCloud.setPotionDurationScale(0.25F);
                areaEffectCloud.addEffect(new MobEffectInstance(MobEffects.INSTANT_DAMAGE, 1, 1));
                if (!entitiesOfClass.isEmpty()) {
                    for (LivingEntity livingEntity : entitiesOfClass) {
                        double d = this.distanceToSqr(livingEntity);
                        if (d < 16.0) {
                            areaEffectCloud.setPos(livingEntity.getX(), livingEntity.getY(), livingEntity.getZ());
                            break;
                        }
                    }
                }

                if (new com.destroystokyo.paper.event.entity.EnderDragonFireballHitEvent((org.bukkit.entity.DragonFireball) this.getBukkitEntity(), entitiesOfClass.stream().map(LivingEntity::getBukkitLivingEntity).collect(java.util.stream.Collectors.toList()), (org.bukkit.entity.AreaEffectCloud) areaEffectCloud.getBukkitEntity()).callEvent()) { // Paper - EnderDragon Events
                this.level().levelEvent(LevelEvent.PARTICLES_DRAGON_FIREBALL_SPLASH, this.blockPosition(), this.isSilent() ? -1 : 1);
                this.level().addFreshEntity(areaEffectCloud, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.EXPLOSION); // Paper - use correct spawn reason
                } else areaEffectCloud.discard(null); // Paper - EnderDragon Events
                this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.HIT); // CraftBukkit - add Bukkit remove cause
            }
        }
    }

    @Override
    protected ParticleOptions getTrailParticle() {
        return PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0F);
    }

    @Override
    protected boolean shouldBurn() {
        return false;
    }
}
