package net.minecraft.world.effect;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.AbstractWindCharge;
import net.minecraft.world.level.Level;

class WindChargedMobEffect extends MobEffect {
    protected WindChargedMobEffect(MobEffectCategory category, int color) {
        super(category, color, ParticleTypes.SMALL_GUST);
    }

    @Override
    public void onMobRemoved(ServerLevel level, LivingEntity entity, int amplifier, Entity.RemovalReason reason) {
        if (reason == Entity.RemovalReason.KILLED) {
            double x = entity.getX();
            double d = entity.getY() + entity.getBbHeight() / 2.0F;
            double z = entity.getZ();
            float f = 3.0F + entity.getRandom().nextFloat() * 2.0F;
            level.explode(
                entity,
                null,
                AbstractWindCharge.EXPLOSION_DAMAGE_CALCULATOR,
                x,
                d,
                z,
                f,
                false,
                Level.ExplosionInteraction.TRIGGER,
                ParticleTypes.GUST_EMITTER_SMALL,
                ParticleTypes.GUST_EMITTER_LARGE,
                WeightedList.of(),
                SoundEvents.BREEZE_WIND_CHARGE_BURST
            );
        }
    }
}
