package net.minecraft.world.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.raid.Raid;

class BadOmenMobEffect extends MobEffect {
    protected BadOmenMobEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
        if (entity instanceof ServerPlayer serverPlayer
            && !serverPlayer.isSpectator()
            && level.getDifficulty() != Difficulty.PEACEFUL
            && level.isVillage(serverPlayer.blockPosition())) {
            Raid raidAt = level.getRaidAt(serverPlayer.blockPosition());
            if (raidAt == null || raidAt.getRaidOmenLevel() < raidAt.getMaxRaidOmenLevel()) {
                serverPlayer.addEffect(new MobEffectInstance(MobEffects.RAID_OMEN, 600, amplifier));
                serverPlayer.setRaidOmenPosition(serverPlayer.blockPosition());
                return false;
            }
        }

        return true;
    }
}
