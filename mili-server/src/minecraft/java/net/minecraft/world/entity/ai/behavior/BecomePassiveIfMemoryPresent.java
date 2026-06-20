package net.minecraft.world.entity.ai.behavior;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public class BecomePassiveIfMemoryPresent {
    public static BehaviorControl<LivingEntity> create(MemoryModuleType<?> pacifyingMemory, int pacifyDuration) {
        return BehaviorBuilder.create(
            instance -> instance.group(
                    instance.registered(MemoryModuleType.ATTACK_TARGET), instance.absent(MemoryModuleType.PACIFIED), instance.present(pacifyingMemory)
                )
                .apply(
                    instance,
                    instance.point(
                        () -> "[BecomePassive if " + pacifyingMemory + " present]", (attackTarget, pacified, pacifying) -> (level, entity, gameTime) -> {
                            pacified.setWithExpiry(true, pacifyDuration);
                            attackTarget.erase();
                            return true;
                        }
                    )
                )
        );
    }
}
