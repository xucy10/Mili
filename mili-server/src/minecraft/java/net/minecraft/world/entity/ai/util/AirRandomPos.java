package net.minecraft.world.entity.ai.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class AirRandomPos {
    public static @Nullable Vec3 getPosTowards(PathfinderMob mob, int radius, int yRange, int y, Vec3 vectorPosition, double amplifier) {
        Vec3 vec3 = vectorPosition.subtract(mob.getX(), mob.getY(), mob.getZ());
        boolean flag = GoalUtils.mobRestricted(mob, radius);
        return RandomPos.generateRandomPos(mob, () -> {
            BlockPos blockPos = AirAndWaterRandomPos.generateRandomPos(mob, radius, yRange, y, vec3.x, vec3.z, amplifier, flag);
            return blockPos != null && !GoalUtils.isWater(mob, blockPos) ? blockPos : null;
        });
    }
}
