package net.minecraft.world.entity.ai.behavior;

import java.util.function.Predicate;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public class CopyMemoryWithExpiry {
    public static <E extends LivingEntity, T> BehaviorControl<E> create(
        Predicate<E> canCopyMemory, MemoryModuleType<? extends T> sourceMemory, MemoryModuleType<T> targetMemory, UniformInt durationOfCopy
    ) {
        return BehaviorBuilder.create(
            instance -> instance.group(instance.present(sourceMemory), instance.absent(targetMemory))
                .apply(instance, (source, target) -> (level, entity, gameTime) -> {
                    if (!canCopyMemory.test(entity)) {
                        return false;
                    } else {
                        target.setWithExpiry(instance.get(source), durationOfCopy.sample(level.random));
                        return true;
                    }
                })
        );
    }
}
