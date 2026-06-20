package net.minecraft.world.entity.ai.behavior;

import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;

public class LocateHidingPlace {
    public static OneShot<LivingEntity> create(int radius, float speedModifier, int closeEnoughDist) {
        return BehaviorBuilder.create(
            instance -> instance.group(
                    instance.absent(MemoryModuleType.WALK_TARGET),
                    instance.registered(MemoryModuleType.HOME),
                    instance.registered(MemoryModuleType.HIDING_PLACE),
                    instance.registered(MemoryModuleType.PATH),
                    instance.registered(MemoryModuleType.LOOK_TARGET),
                    instance.registered(MemoryModuleType.BREED_TARGET),
                    instance.registered(MemoryModuleType.INTERACTION_TARGET)
                )
                .apply(
                    instance,
                    (walkTarget, home, hidingPlace, navigationPath, lookTarget, breedTarget, interactionTarget) -> (level, entity, gameTime) -> {
                        level.getPoiManager()
                            .find(poi -> poi.is(PoiTypes.HOME), pos -> true, entity.blockPosition(), closeEnoughDist + 1, PoiManager.Occupancy.ANY)
                            .filter(poiPos -> poiPos.closerToCenterThan(entity.position(), closeEnoughDist))
                            .or(
                                () -> level.getPoiManager()
                                    .getRandom(
                                        poi -> poi.is(PoiTypes.HOME), pos -> true, PoiManager.Occupancy.ANY, entity.blockPosition(), radius, entity.getRandom()
                                    )
                            )
                            .or(() -> instance.<GlobalPos>tryGet(home).map(GlobalPos::pos))
                            .ifPresent(walkPos -> {
                                navigationPath.erase();
                                lookTarget.erase();
                                breedTarget.erase();
                                interactionTarget.erase();
                                hidingPlace.set(GlobalPos.of(level.dimension(), walkPos));
                                if (!walkPos.closerToCenterThan(entity.position(), closeEnoughDist)) {
                                    walkTarget.set(new WalkTarget(walkPos, speedModifier, closeEnoughDist));
                                }
                            });
                        return true;
                    }
                )
        );
    }
}
