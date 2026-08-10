package net.minecraft.world.entity.monster.piglin;

import java.util.List;
import net.minecraft.world.entity.ai.behavior.OneShot;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.hoglin.Hoglin;

public class StartHuntingHoglin {
    public static OneShot<Piglin> create() {
        return BehaviorBuilder.create(
            instance -> instance.group(
                    instance.present(MemoryModuleType.NEAREST_VISIBLE_HUNTABLE_HOGLIN),
                    instance.absent(MemoryModuleType.ANGRY_AT),
                    instance.absent(MemoryModuleType.HUNTED_RECENTLY),
                    instance.registered(MemoryModuleType.NEAREST_VISIBLE_ADULT_PIGLINS)
                )
                .apply(
                    instance,
                    (nearestVisibleHuntableHoglin, angryAt, huntedRecently, nearestVisibleAdultPiglins) -> (level, piglin, gameTime) -> {
                        if (!piglin.isBaby()
                            && !instance.tryGet(nearestVisibleAdultPiglins)
                                .map(adultPiglin -> adultPiglin.stream().anyMatch(StartHuntingHoglin::hasHuntedRecently))
                                .isPresent()) {
                            Hoglin hoglin = instance.get(nearestVisibleHuntableHoglin);
                            PiglinAi.setAngerTarget(level, piglin, hoglin);
                            PiglinAi.dontKillAnyMoreHoglinsForAWhile(piglin);
                            PiglinAi.broadcastAngerTarget(level, piglin, hoglin);
                            instance.tryGet(nearestVisibleAdultPiglins)
                                .ifPresent(adultPiglin -> adultPiglin.forEach(PiglinAi::dontKillAnyMoreHoglinsForAWhile));
                            return true;
                        } else {
                            return false;
                        }
                    }
                )
        );
    }

    private static boolean hasHuntedRecently(AbstractPiglin piglin) {
        return piglin.getBrain().hasMemoryValue(MemoryModuleType.HUNTED_RECENTLY);
    }
}
