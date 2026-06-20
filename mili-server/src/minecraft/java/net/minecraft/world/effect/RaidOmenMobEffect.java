package net.minecraft.world.effect;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

class RaidOmenMobEffect extends MobEffect {
    protected RaidOmenMobEffect(MobEffectCategory category, int color, ParticleOptions particle) {
        super(category, color, particle);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration == 1;
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
        if (entity instanceof ServerPlayer serverPlayer && !entity.isSpectator()) {
            BlockPos raidOmenPosition = serverPlayer.getRaidOmenPosition();
            if (raidOmenPosition != null) {
                level.getRaids().createOrExtendRaid(serverPlayer, raidOmenPosition);
                serverPlayer.clearRaidOmenPosition();
                return false;
            }
        }

        return true;
    }
}
