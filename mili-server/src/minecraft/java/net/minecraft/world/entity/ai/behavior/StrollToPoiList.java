package net.minecraft.world.entity.ai.behavior;

import java.util.List;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import org.apache.commons.lang3.mutable.MutableLong;

public class StrollToPoiList {
    public static BehaviorControl<Villager> create(
        MemoryModuleType<List<GlobalPos>> poiListMemory,
        float speedModifier,
        int closeEnoughDist,
        int maxDistFromPoi,
        MemoryModuleType<GlobalPos> mustBeCloseToMemory
    ) {
        MutableLong mutableLong = new MutableLong(0L);
        return BehaviorBuilder.create(
            instance -> instance.group(
                    instance.registered(MemoryModuleType.WALK_TARGET), instance.present(poiListMemory), instance.present(mustBeCloseToMemory)
                )
                .apply(
                    instance,
                    (walkTarget, poiList, mustBeCloseTo) -> (level, villager, gameTime) -> {
                        List<GlobalPos> list = instance.get(poiList);
                        GlobalPos globalPos = instance.get(mustBeCloseTo);
                        if (list.isEmpty()) {
                            return false;
                        } else {
                            GlobalPos globalPos1 = list.get(level.getRandom().nextInt(list.size()));
                            if (globalPos1 != null
                                && level.dimension() == globalPos1.dimension()
                                && globalPos.pos().closerToCenterThan(villager.position(), maxDistFromPoi)) {
                                if (gameTime > mutableLong.longValue()) {
                                    walkTarget.set(new WalkTarget(globalPos1.pos(), speedModifier, closeEnoughDist));
                                    mutableLong.setValue(gameTime + 100L);
                                }

                                return true;
                            } else {
                                return false;
                            }
                        }
                    }
                )
        );
    }
}
