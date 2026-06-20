package net.minecraft.world.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

class HealOrHarmMobEffect extends InstantenousMobEffect {
    private final boolean isHarm;

    public HealOrHarmMobEffect(MobEffectCategory category, int color, boolean isHarm) {
        super(category, color);
        this.isHarm = isHarm;
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
        if (this.isHarm == entity.isInvertedHealAndHarm()) {
            entity.heal(Math.max(4 << amplifier, 0), org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason.MAGIC); // CraftBukkit
        } else {
            entity.hurtServer(level, entity.damageSources().magic(), 6 << amplifier);
        }

        return true;
    }

    @Override
    public void applyInstantenousEffect(
        ServerLevel level, @Nullable Entity source, @Nullable Entity indirectSource, LivingEntity entity, int amplifier, double health
    ) {
        if (!new io.papermc.paper.event.entity.EntityEffectTickEvent(entity.getBukkitLivingEntity(), org.bukkit.craftbukkit.potion.CraftPotionEffectType.minecraftToBukkit(this), amplifier).callEvent()) { return; } // Paper - Add EntityEffectTickEvent
        if (this.isHarm == entity.isInvertedHealAndHarm()) {
            int i = (int)(health * (4 << amplifier) + 0.5);
            entity.heal(i, org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason.MAGIC); // CraftBukkit
        } else {
            int i = (int)(health * (6 << amplifier) + 0.5);
            if (source == null) {
                entity.hurtServer(level, entity.damageSources().magic(), i);
            } else {
                entity.hurtServer(level, entity.damageSources().indirectMagic(source, indirectSource), i);
            }
        }
    }
}
