package net.minecraft.world.entity.monster.piglin;

import java.util.Optional;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public class StopAdmiringIfTiredOfTryingToReachItem {
    public static BehaviorControl<LivingEntity> create(int maxTimeToReachItem, int disableDuration) {
        return BehaviorBuilder.create(
            instance -> instance.group(
                    instance.present(MemoryModuleType.ADMIRING_ITEM),
                    instance.present(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM),
                    instance.registered(MemoryModuleType.TIME_TRYING_TO_REACH_ADMIRE_ITEM),
                    instance.registered(MemoryModuleType.DISABLE_WALK_TO_ADMIRE_ITEM)
                )
                .apply(
                    instance, (admiringItem, nearestVisibleWantedItem, timeTryingToReachAdmireItem, disableWalkToAdmireItem) -> (level, entity, gameTime) -> {
                        if (!entity.getOffhandItem().isEmpty()) {
                            return false;
                        } else {
                            Optional<Integer> optional = instance.tryGet(timeTryingToReachAdmireItem);
                            if (optional.isEmpty()) {
                                timeTryingToReachAdmireItem.set(0);
                            } else {
                                int i = optional.get();
                                if (i > maxTimeToReachItem) {
                                    admiringItem.erase();
                                    timeTryingToReachAdmireItem.erase();
                                    disableWalkToAdmireItem.setWithExpiry(true, disableDuration);
                                } else {
                                    timeTryingToReachAdmireItem.set(i + 1);
                                }
                            }

                            return true;
                        }
                    }
                )
        );
    }
}
