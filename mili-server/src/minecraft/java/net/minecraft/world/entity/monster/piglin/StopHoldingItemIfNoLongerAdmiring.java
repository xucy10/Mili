package net.minecraft.world.entity.monster.piglin;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public class StopHoldingItemIfNoLongerAdmiring {
    public static BehaviorControl<Piglin> create() {
        return BehaviorBuilder.create(
            instance -> instance.group(instance.absent(MemoryModuleType.ADMIRING_ITEM)).apply(instance, admiringItem -> (level, piglin, gameTime) -> {
                if (!piglin.getOffhandItem().isEmpty() && !piglin.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS)) {
                    PiglinAi.stopHoldingOffHandItem(level, piglin, true);
                    return true;
                } else {
                    return false;
                }
            })
        );
    }
}
