package net.minecraft.world.entity.ai.behavior;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;

public class VillageBoundRandomStroll {
    private static final int MAX_XZ_DIST = 10;
    private static final int MAX_Y_DIST = 7;

    public static OneShot<PathfinderMob> create(float speedModifier) {
        return create(speedModifier, 10, 7);
    }

    public static OneShot<PathfinderMob> create(float speedModifier, int maxHorizontalDist, int maxVerticalDist) {
        return BehaviorBuilder.create(
            instance -> instance.group(instance.absent(MemoryModuleType.WALK_TARGET))
                .apply(
                    instance,
                    walkTarget -> (level, mob, gameTime) -> {
                        BlockPos blockPos = mob.blockPosition();
                        Vec3 pos;
                        if (level.isVillage(blockPos)) {
                            pos = LandRandomPos.getPos(mob, maxHorizontalDist, maxVerticalDist);
                        } else {
                            SectionPos sectionPos = SectionPos.of(blockPos);
                            SectionPos sectionPos1 = BehaviorUtils.findSectionClosestToVillage(level, sectionPos, 2);
                            if (sectionPos1 != sectionPos) {
                                pos = DefaultRandomPos.getPosTowards(
                                    mob, maxHorizontalDist, maxVerticalDist, Vec3.atBottomCenterOf(sectionPos1.center()), (float) (Math.PI / 2)
                                );
                            } else {
                                pos = LandRandomPos.getPos(mob, maxHorizontalDist, maxVerticalDist);
                            }
                        }

                        walkTarget.setOrErase(Optional.ofNullable(pos).map(target -> new WalkTarget(target, speedModifier, 0)));
                        return true;
                    }
                )
        );
    }
}
