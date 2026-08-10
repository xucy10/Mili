package net.minecraft.world.entity.ai.behavior;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.AirAndWaterRandomPos;
import net.minecraft.world.entity.ai.util.GoalUtils;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class RandomStroll {
    private static final int MAX_XZ_DIST = 10;
    private static final int MAX_Y_DIST = 7;
    private static final int[][] SWIM_XY_DISTANCE_TIERS = new int[][]{{1, 1}, {3, 3}, {5, 5}, {6, 5}, {7, 7}, {10, 7}};

    public static OneShot<PathfinderMob> stroll(float speedModifier) {
        return stroll(speedModifier, true);
    }

    public static OneShot<PathfinderMob> stroll(float speedModifier, boolean mayStrollFromWater) {
        return strollFlyOrSwim(speedModifier, mob -> LandRandomPos.getPos(mob, 10, 7), mayStrollFromWater ? mob -> true : mob -> !mob.isInWater());
    }

    public static BehaviorControl<PathfinderMob> stroll(float speedModifier, int maxHorizontalDistance, int maxVerticalDistance) {
        return strollFlyOrSwim(speedModifier, mob -> LandRandomPos.getPos(mob, maxHorizontalDistance, maxVerticalDistance), mob -> true);
    }

    public static BehaviorControl<PathfinderMob> fly(float speedModifier) {
        return strollFlyOrSwim(speedModifier, mob -> getTargetFlyPos(mob, 10, 7), mob -> true);
    }

    public static BehaviorControl<PathfinderMob> swim(float speedModifier) {
        return strollFlyOrSwim(speedModifier, RandomStroll::getTargetSwimPos, Entity::isInWater);
    }

    private static OneShot<PathfinderMob> strollFlyOrSwim(float speedModifier, Function<PathfinderMob, Vec3> target, Predicate<PathfinderMob> canStroll) {
        return BehaviorBuilder.create(
            instance -> instance.group(instance.absent(MemoryModuleType.WALK_TARGET)).apply(instance, walkTarget -> (level, mob, gameTime) -> {
                if (!canStroll.test(mob)) {
                    return false;
                } else {
                    Optional<Vec3> optional = Optional.ofNullable(target.apply(mob));
                    walkTarget.setOrErase(optional.map(targetPos -> new WalkTarget(targetPos, speedModifier, 0)));
                    return true;
                }
            })
        );
    }

    private static @Nullable Vec3 getTargetSwimPos(PathfinderMob mob) {
        Vec3 vec3 = null;
        Vec3 vec31 = null;

        for (int[] ints : SWIM_XY_DISTANCE_TIERS) {
            if (vec3 == null) {
                vec31 = BehaviorUtils.getRandomSwimmablePos(mob, ints[0], ints[1]);
            } else {
                vec31 = mob.position().add(mob.position().vectorTo(vec3).normalize().multiply(ints[0], ints[1], ints[0]));
            }

            boolean flag = GoalUtils.mobRestricted(mob, ints[0]);
            if (vec31 == null || mob.level().getFluidState(BlockPos.containing(vec31)).isEmpty() || GoalUtils.isRestricted(flag, mob, vec31)) {
                return vec3;
            }

            vec3 = vec31;
        }

        return vec31;
    }

    private static @Nullable Vec3 getTargetFlyPos(PathfinderMob mob, int maxDistance, int yRange) {
        Vec3 viewVector = mob.getViewVector(0.0F);
        return AirAndWaterRandomPos.getPos(mob, maxDistance, yRange, -2, viewVector.x, viewVector.z, (float) (Math.PI / 2));
    }
}
