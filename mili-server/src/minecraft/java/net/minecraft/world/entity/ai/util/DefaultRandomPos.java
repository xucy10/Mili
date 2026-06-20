package net.minecraft.world.entity.ai.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class DefaultRandomPos {
    public static @Nullable Vec3 getPos(PathfinderMob mob, int radius, int verticalDistance) {
        boolean flag = GoalUtils.mobRestricted(mob, radius);
        return RandomPos.generateRandomPos(mob, () -> {
            BlockPos blockPos = RandomPos.generateRandomDirection(mob.getRandom(), radius, verticalDistance);
            return generateRandomPosTowardDirection(mob, radius, flag, blockPos);
        });
    }

    public static @Nullable Vec3 getPosTowards(PathfinderMob mob, int radius, int yRange, Vec3 targetPos, double amplifier) {
        Vec3 vec3 = targetPos.subtract(mob.getX(), mob.getY(), mob.getZ());
        boolean flag = GoalUtils.mobRestricted(mob, radius);
        return RandomPos.generateRandomPos(mob, () -> {
            BlockPos blockPos = RandomPos.generateRandomDirectionWithinRadians(mob.getRandom(), 0.0, radius, yRange, 0, vec3.x, vec3.z, amplifier);
            return blockPos == null ? null : generateRandomPosTowardDirection(mob, radius, flag, blockPos);
        });
    }

    public static @Nullable Vec3 getPosAway(PathfinderMob mob, int radius, int yRange, Vec3 avoidPos) {
        Vec3 vec3 = mob.position().subtract(avoidPos);
        boolean flag = GoalUtils.mobRestricted(mob, radius);
        return RandomPos.generateRandomPos(mob, () -> {
            BlockPos blockPos = RandomPos.generateRandomDirectionWithinRadians(mob.getRandom(), 0.0, radius, yRange, 0, vec3.x, vec3.z, (float) (Math.PI / 2));
            return blockPos == null ? null : generateRandomPosTowardDirection(mob, radius, flag, blockPos);
        });
    }

    private static @Nullable BlockPos generateRandomPosTowardDirection(PathfinderMob mob, int radius, boolean isRestricted, BlockPos pos) {
        BlockPos blockPos = RandomPos.generateRandomPosTowardDirection(mob, radius, mob.getRandom(), pos);
        return !GoalUtils.isOutsideLimits(blockPos, mob)
                && !GoalUtils.isRestricted(isRestricted, mob, blockPos)
                && !GoalUtils.isNotStable(mob.getNavigation(), blockPos)
                && !GoalUtils.hasMalus(mob, blockPos)
            ? blockPos
            : null;
    }
}
