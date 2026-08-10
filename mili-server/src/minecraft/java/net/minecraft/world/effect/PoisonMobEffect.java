package net.minecraft.world.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

public class PoisonMobEffect extends MobEffect {
    public static final int DAMAGE_INTERVAL = 25;

    protected PoisonMobEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
        if (entity.getHealth() > 1.0F) {
            entity.hurtServer(level, entity.damageSources().magic().knownCause(org.bukkit.event.entity.EntityDamageEvent.DamageCause.POISON), 1.0F); // CraftBukkit
        }

        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        int i = 25 >> amplifier;
        return i <= 0 || duration % i == 0;
    }
}
