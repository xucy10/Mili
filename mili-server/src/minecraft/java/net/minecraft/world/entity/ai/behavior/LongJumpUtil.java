package net.minecraft.world.entity.ai.behavior;

import java.util.Optional;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;

public final class LongJumpUtil {
    public static Optional<Vec3> calculateJumpVectorForAngle(Mob mob, Vec3 target, float maxJumpVelocity, int angle, boolean requireClearTransition) {
        Vec3 vec3 = mob.position();
        Vec3 vec31 = new Vec3(target.x - vec3.x, 0.0, target.z - vec3.z).normalize().scale(0.5);
        Vec3 vec32 = target.subtract(vec31);
        Vec3 vec33 = vec32.subtract(vec3);
        float f = angle * (float) Math.PI / 180.0F;
        double atan2 = Math.atan2(vec33.z, vec33.x);
        double d = vec33.subtract(0.0, vec33.y, 0.0).lengthSqr();
        double squareRoot = Math.sqrt(d);
        double d1 = vec33.y;
        double gravity = mob.getGravity();
        double sin = Math.sin(2.0F * f);
        double d2 = Math.pow(Math.cos(f), 2.0);
        double sin1 = Math.sin(f);
        double cos = Math.cos(f);
        double sin2 = Math.sin(atan2);
        double cos1 = Math.cos(atan2);
        double d3 = d * gravity / (squareRoot * sin - 2.0 * d1 * d2);
        if (d3 < 0.0) {
            return Optional.empty();
        } else {
            double squareRoot1 = Math.sqrt(d3);
            if (squareRoot1 > maxJumpVelocity) {
                return Optional.empty();
            } else {
                double d4 = squareRoot1 * cos;
                double d5 = squareRoot1 * sin1;
                if (requireClearTransition) {
                    int i = Mth.ceil(squareRoot / d4) * 2;
                    double d6 = 0.0;
                    Vec3 vec34 = null;
                    EntityDimensions dimensions = mob.getDimensions(Pose.LONG_JUMPING);

                    for (int i1 = 0; i1 < i - 1; i1++) {
                        d6 += squareRoot / i;
                        double d7 = sin1 / cos * d6 - Math.pow(d6, 2.0) * gravity / (2.0 * d3 * Math.pow(cos, 2.0));
                        double d8 = d6 * cos1;
                        double d9 = d6 * sin2;
                        Vec3 vec35 = new Vec3(vec3.x + d8, vec3.y + d7, vec3.z + d9);
                        if (vec34 != null && !isClearTransition(mob, dimensions, vec34, vec35)) {
                            return Optional.empty();
                        }

                        vec34 = vec35;
                    }
                }

                return Optional.of(new Vec3(d4 * cos1, d5, d4 * sin2).scale(0.95F));
            }
        }
    }

    private static boolean isClearTransition(Mob mob, EntityDimensions dimensions, Vec3 startPos, Vec3 endPos) {
        Vec3 vec3 = endPos.subtract(startPos);
        double d = Math.min(dimensions.width(), dimensions.height());
        int ceil = Mth.ceil(vec3.length() / d);
        Vec3 vec31 = vec3.normalize();
        Vec3 vec32 = startPos;

        for (int i = 0; i < ceil; i++) {
            vec32 = i == ceil - 1 ? endPos : vec32.add(vec31.scale(d * 0.9F));
            if (!mob.level().noCollision(mob, dimensions.makeBoundingBox(vec32))) {
                return false;
            }
        }

        return true;
    }
}
