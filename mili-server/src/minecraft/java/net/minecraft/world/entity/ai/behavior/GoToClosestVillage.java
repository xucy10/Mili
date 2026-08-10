package net.minecraft.world.entity.ai.behavior;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;

public class GoToClosestVillage {
    public static BehaviorControl<Villager> create(float speedModifier, int closeEnoughDist) {
        return BehaviorBuilder.create(
            instance -> instance.group(instance.absent(MemoryModuleType.WALK_TARGET)).apply(instance, walkTarget -> (level, villager, gameTime) -> {
                if (level.isVillage(villager.blockPosition())) {
                    return false;
                } else {
                    PoiManager poiManager = level.getPoiManager();
                    int i = poiManager.sectionsToVillage(SectionPos.of(villager.blockPosition()));
                    Vec3 vec3 = null;

                    for (int i1 = 0; i1 < 5; i1++) {
                        Vec3 pos = LandRandomPos.getPos(villager, 15, 7, pos1 -> -poiManager.sectionsToVillage(SectionPos.of(pos1)));
                        if (pos != null) {
                            int i2 = poiManager.sectionsToVillage(SectionPos.of(BlockPos.containing(pos)));
                            if (i2 < i) {
                                vec3 = pos;
                                break;
                            }

                            if (i2 == i) {
                                vec3 = pos;
                            }
                        }
                    }

                    if (vec3 != null) {
                        walkTarget.set(new WalkTarget(vec3, speedModifier, closeEnoughDist));
                    }

                    return true;
                }
            })
        );
    }
}
