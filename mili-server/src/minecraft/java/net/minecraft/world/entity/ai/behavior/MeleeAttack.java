package net.minecraft.world.entity.ai.behavior;

import java.util.function.Predicate;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;

public class MeleeAttack {
    public static <T extends Mob> OneShot<T> create(int attackCooldown) {
        return create(mob -> true, attackCooldown);
    }

    public static <T extends Mob> OneShot<T> create(Predicate<T> canAttack, int attackCooldown) {
        return BehaviorBuilder.create(
            instance -> instance.group(
                    instance.registered(MemoryModuleType.LOOK_TARGET),
                    instance.present(MemoryModuleType.ATTACK_TARGET),
                    instance.absent(MemoryModuleType.ATTACK_COOLING_DOWN),
                    instance.present(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)
                )
                .apply(
                    instance,
                    (memoryAccessor, memoryAccessor1, memoryAccessor2, memoryAccessor3) -> (level, entity, gameTime) -> {
                        LivingEntity livingEntity = instance.get(memoryAccessor1);
                        if (canAttack.test(entity)
                            && !isHoldingUsableNonMeleeWeapon(entity)
                            && entity.isWithinMeleeAttackRange(livingEntity)
                            && instance.<NearestVisibleLivingEntities>get(memoryAccessor3).contains(livingEntity)) {
                            memoryAccessor.set(new EntityTracker(livingEntity, true));
                            entity.swing(InteractionHand.MAIN_HAND);
                            entity.doHurtTarget(level, livingEntity);
                            memoryAccessor2.setWithExpiry(true, attackCooldown);
                            return true;
                        } else {
                            return false;
                        }
                    }
                )
        );
    }

    private static boolean isHoldingUsableNonMeleeWeapon(Mob mob) {
        return mob.isHolding(mob::canUseNonMeleeWeapon);
    }
}
