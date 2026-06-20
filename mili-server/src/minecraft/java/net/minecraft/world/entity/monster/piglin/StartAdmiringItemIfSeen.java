package net.minecraft.world.entity.monster.piglin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;

public class StartAdmiringItemIfSeen {
    public static BehaviorControl<LivingEntity> create(int admireDuration) {
        return BehaviorBuilder.create(
            instance -> instance.group(
                    instance.present(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM),
                    instance.absent(MemoryModuleType.ADMIRING_ITEM),
                    instance.absent(MemoryModuleType.ADMIRING_DISABLED),
                    instance.absent(MemoryModuleType.DISABLE_WALK_TO_ADMIRE_ITEM)
                )
                .apply(instance, (nearestVisibleWantedItem, admiringItem, admiringDisabled, disableWalkToAdmireItem) -> (level, entity, gameTime) -> {
                    ItemEntity itemEntity = instance.get(nearestVisibleWantedItem);
                    if (!PiglinAi.isLovedItem(itemEntity.getItem())) {
                        return false;
                    } else {
                        admiringItem.setWithExpiry(true, admireDuration);
                        return true;
                    }
                })
        );
    }
}
