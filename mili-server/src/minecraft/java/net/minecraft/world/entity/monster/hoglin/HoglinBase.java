package net.minecraft.world.entity.monster.hoglin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;

public interface HoglinBase {
    int ATTACK_ANIMATION_DURATION = 10;
    float PROBABILITY_OF_SPAWNING_AS_BABY = 0.2F;

    int getAttackAnimationRemainingTicks();

    static boolean hurtAndThrowTarget(ServerLevel level, LivingEntity entity, LivingEntity target) {
        float f = (float)entity.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float f1;
        if (!entity.isBaby() && (int)f > 0) {
            f1 = f / 2.0F + level.random.nextInt((int)f);
        } else {
            f1 = f;
        }

        DamageSource damageSource = entity.damageSources().mobAttack(entity);
        boolean flag = target.hurtServer(level, damageSource, f1);
        if (flag) {
            EnchantmentHelper.doPostAttackEffects(level, target, damageSource);
            if (!entity.isBaby()) {
                throwTarget(entity, target);
            }
        }

        return flag;
    }

    static void throwTarget(LivingEntity hoglin, LivingEntity target) {
        double attributeValue = hoglin.getAttributeValue(Attributes.ATTACK_KNOCKBACK);
        double attributeValue1 = target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        double d = attributeValue - attributeValue1;
        if (!(d <= 0.0)) {
            double d1 = target.getX() - hoglin.getX();
            double d2 = target.getZ() - hoglin.getZ();
            float f = hoglin.level().random.nextInt(21) - 10;
            double d3 = d * (hoglin.level().random.nextFloat() * 0.5F + 0.2F);
            Vec3 vec3 = new Vec3(d1, 0.0, d2).normalize().scale(d3).yRot(f);
            double d4 = d * hoglin.level().random.nextFloat() * 0.5;
            target.push(vec3.x, d4, vec3.z, hoglin); // Paper - Add EntityKnockbackByEntityEvent and EntityPushedByEntityAttackEvent
            target.hurtMarked = true;
        }
    }
}
