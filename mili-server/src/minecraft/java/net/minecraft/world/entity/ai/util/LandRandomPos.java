package net.minecraft.world.entity.ai.util;

import java.util.function.ToDoubleFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class LandRandomPos {
    public static @Nullable Vec3 getPos(PathfinderMob mob, int radius, int verticalRange) {
        return getPos(mob, radius, verticalRange, mob::getWalkTargetValue);
    }

    public static @Nullable Vec3 getPos(PathfinderMob mob, int radius, int yRange, ToDoubleFunction<BlockPos> toDoubleFunction) {
        boolean flag = GoalUtils.mobRestricted(mob, radius);
        return RandomPos.generateRandomPos(() -> {
            BlockPos blockPos = RandomPos.generateRandomDirection(mob.getRandom(), radius, yRange);
            BlockPos blockPos1 = generateRandomPosTowardDirection(mob, radius, flag, blockPos);
            return blockPos1 == null ? null : movePosUpOutOfSolid(mob, blockPos1);
        }, toDoubleFunction);
    }

    public static @Nullable Vec3 getPosTowards(PathfinderMob mob, int radius, int yRange, Vec3 vectorPosition) {
        Vec3 vec3 = vectorPosition.subtract(mob.getX(), mob.getY(), mob.getZ());
        boolean flag = GoalUtils.mobRestricted(mob, radius);
        return getPosInDirection(mob, 0.0, radius, yRange, vec3, flag);
    }

    public static @Nullable Vec3 getPosAway(PathfinderMob mob, int radius, int yRange, Vec3 vectorPosition) {
        return getPosAway(mob, 0.0, radius, yRange, vectorPosition);
    }

    public static @Nullable Vec3 getPosAway(PathfinderMob mob, double minDistance, double maxDistance, int yRange, Vec3 vectorPosition) {
        Vec3 vec3 = mob.position().subtract(vectorPosition);
        if (vec3.length() == 0.0) {
            vec3 = new Vec3(mob.getRandom().nextDouble() - 0.5, 0.0, mob.getRandom().nextDouble() - 0.5);
        }

        boolean flag = GoalUtils.mobRestricted(mob, maxDistance);
        return getPosInDirection(mob, minDistance, maxDistance, yRange, vec3, flag);
    }

    private static @Nullable Vec3 getPosInDirection(
        PathfinderMob mob, double minDistance, double maxDistance, int yRange, Vec3 vectorPosition, boolean isRestricted
    ) {
        return RandomPos.generateRandomPos(
            mob,
            () -> {
                BlockPos blockPos = RandomPos.generateRandomDirectionWithinRadians(
                    mob.getRandom(), minDistance, maxDistance, yRange, 0, vectorPosition.x, vectorPosition.z, (float) (Math.PI / 2)
                );
                if (blockPos == null) {
                    return null;
                } else {
                    BlockPos blockPos1 = generateRandomPosTowardDirection(mob, maxDistance, isRestricted, blockPos);
                    return blockPos1 == null ? null : movePosUpOutOfSolid(mob, blockPos1);
                }
            }
        );
    }

    public static @Nullable BlockPos movePosUpOutOfSolid(PathfinderMob mob, BlockPos pos) {
        pos = RandomPos.moveUpOutOfSolid(pos, mob.level().getMaxY(), pos1 -> GoalUtils.isSolid(mob, pos1));
        return !GoalUtils.isWater(mob, pos) && !GoalUtils.hasMalus(mob, pos) ? pos : null;
    }

    public static @Nullable BlockPos generateRandomPosTowardDirection(PathfinderMob mob, double radius, boolean isRestricted, BlockPos pos) {
        BlockPos blockPos = RandomPos.generateRandomPosTowardDirection(mob, radius, mob.getRandom(), pos);
        return !GoalUtils.isOutsideLimits(blockPos, mob)
                && !GoalUtils.isRestricted(isRestricted, mob, blockPos)
                && !GoalUtils.isNotStable(mob.getNavigation(), blockPos)
            ? blockPos
            : null;
    }
}
