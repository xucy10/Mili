package net.minecraft.world.effect;

public class InstantenousMobEffect extends MobEffect {
    public InstantenousMobEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public boolean isInstantenous() {
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration >= 1;
    }
}
