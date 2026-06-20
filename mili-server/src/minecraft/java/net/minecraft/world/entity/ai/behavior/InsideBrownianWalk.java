package net.minecraft.world.entity.ai.behavior;

import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Util;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;

public class InsideBrownianWalk {
    public static BehaviorControl<PathfinderMob> create(float speedModifier) {
        return BehaviorBuilder.create(
            instance -> instance.group(instance.absent(MemoryModuleType.WALK_TARGET))
                .apply(
                    instance,
                    walkTarget -> (level, mob, gameTime) -> {
                        if (level.canSeeSky(mob.blockPosition())) {
                            return false;
                        } else {
                            BlockPos blockPos = mob.blockPosition();
                            List<BlockPos> list = BlockPos.betweenClosedStream(blockPos.offset(-1, -1, -1), blockPos.offset(1, 1, 1))
                                .map(BlockPos::immutable)
                                .collect(Util.toMutableList());
                            Collections.shuffle(list);
                            list.stream()
                                .filter(pos -> !level.canSeeSky(pos))
                                .filter(pos -> level.loadedAndEntityCanStandOn(pos, mob))
                                .filter(pos -> level.noCollision(mob))
                                .findFirst()
                                .ifPresent(pos -> walkTarget.set(new WalkTarget(pos, speedModifier, 0)));
                            return true;
                        }
                    }
                )
        );
    }
}
