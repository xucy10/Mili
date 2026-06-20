package net.minecraft.world.entity.monster.piglin;

import java.util.Optional;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;

public class StopAdmiringIfItemTooFarAway<E extends Piglin> {
    public static BehaviorControl<LivingEntity> create(int maxDist) {
        return BehaviorBuilder.create(
            instance -> instance.group(instance.present(MemoryModuleType.ADMIRING_ITEM), instance.registered(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM))
                .apply(instance, (admiringItem, nearestVisibleWantedItem) -> (level, entity, gameTime) -> {
                    if (!entity.getOffhandItem().isEmpty()) {
                        return false;
                    } else {
                        Optional<ItemEntity> optional = instance.tryGet(nearestVisibleWantedItem);
                        if (optional.isPresent() && optional.get().closerThan(entity, maxDist)) {
                            return false;
                        } else {
                            admiringItem.erase();
                            return true;
                        }
                    }
                })
        );
    }
}
