package net.minecraft.world.entity.ai.behavior;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;

public class SetWalkTargetFromBlockMemory {
    public static OneShot<Villager> create(
        MemoryModuleType<GlobalPos> blockTargetMemory, float speedModifier, int closeEnoughDist, int tooFarDistance, int tooLongUnreachableDuration
    ) {
        return BehaviorBuilder.create(
            instance -> instance.group(
                    instance.registered(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE),
                    instance.absent(MemoryModuleType.WALK_TARGET),
                    instance.present(blockTargetMemory)
                )
                .apply(instance, (cantReachWalkTargetSince, walkTarget, blockTarget) -> (level, villager, gameTime) -> {
                    GlobalPos globalPos = instance.get(blockTarget);
                    Optional<Long> optional = instance.tryGet(cantReachWalkTargetSince);
                    if (globalPos.dimension() == level.dimension()
                        && (!optional.isPresent() || level.getGameTime() - optional.get() <= tooLongUnreachableDuration)) {
                        if (globalPos.pos().distManhattan(villager.blockPosition()) > tooFarDistance) {
                            Vec3 vec3 = null;
                            int i = 0;
                            int i1 = 1000;

                            while (vec3 == null || BlockPos.containing(vec3).distManhattan(villager.blockPosition()) > tooFarDistance) {
                                vec3 = DefaultRandomPos.getPosTowards(villager, 15, 7, Vec3.atBottomCenterOf(globalPos.pos()), (float) (Math.PI / 2));
                                if (++i == 1000) {
                                    villager.releasePoi(blockTargetMemory);
                                    blockTarget.erase();
                                    cantReachWalkTargetSince.set(gameTime);
                                    return true;
                                }
                            }

                            walkTarget.set(new WalkTarget(vec3, speedModifier, closeEnoughDist));
                        } else if (globalPos.pos().distManhattan(villager.blockPosition()) > closeEnoughDist) {
                            walkTarget.set(new WalkTarget(globalPos.pos(), speedModifier, closeEnoughDist));
                        }
                    } else {
                        villager.releasePoi(blockTargetMemory);
                        blockTarget.erase();
                        cantReachWalkTargetSince.set(gameTime);
                    }

                    return true;
                })
        );
    }
}
