package net.minecraft.world.entity.ai.targeting;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;

public class TargetingConditions {
    public static final TargetingConditions DEFAULT = forCombat();
    private static final double MIN_VISIBILITY_DISTANCE_FOR_INVISIBLE_TARGET = 2.0;
    private final boolean isCombat;
    private double range = -1.0;
    private boolean checkLineOfSight = true;
    private boolean testInvisible = true;
    private TargetingConditions.@Nullable Selector selector;

    private TargetingConditions(boolean isCombat) {
        this.isCombat = isCombat;
    }

    public static TargetingConditions forCombat() {
        return new TargetingConditions(true);
    }

    public static TargetingConditions forNonCombat() {
        return new TargetingConditions(false);
    }

    public TargetingConditions copy() {
        TargetingConditions targetingConditions = this.isCombat ? forCombat() : forNonCombat();
        targetingConditions.range = this.range;
        targetingConditions.checkLineOfSight = this.checkLineOfSight;
        targetingConditions.testInvisible = this.testInvisible;
        targetingConditions.selector = this.selector;
        return targetingConditions;
    }

    public TargetingConditions range(double range) {
        this.range = range;
        return this;
    }

    public TargetingConditions ignoreLineOfSight() {
        this.checkLineOfSight = false;
        return this;
    }

    public TargetingConditions ignoreInvisibilityTesting() {
        this.testInvisible = false;
        return this;
    }

    public TargetingConditions selector(TargetingConditions.@Nullable Selector selector) {
        this.selector = selector;
        return this;
    }

    public boolean test(ServerLevel level, @Nullable LivingEntity entity, LivingEntity target) {
        if (entity == target) {
            return false;
        } else if (!target.canBeSeenByAnyone()) {
            return false;
        } else if (this.selector != null && !this.selector.test(target, level)) {
            return false;
        } else {
            if (entity == null) {
                if (this.isCombat && (!target.canBeSeenAsEnemy() || level.getDifficulty() == Difficulty.PEACEFUL)) {
                    return false;
                }
            } else {
                if (this.isCombat && (!entity.canAttack(target) || !entity.canAttackType(target.getType()) || entity.isAlliedTo(target))) {
                    return false;
                }

                if (this.range > 0.0) {
                    // Gale start - Airplane - check targeting range before getting visibility
                    // d = invisibility percent, max = follow range adjusted for invisibility, d1 = distance
                    double d1 = entity.distanceToSqr(target.getX(), target.getY(), target.getZ());
                    double followRangeRaw = this.range;

                    if (d1 > followRangeRaw * followRangeRaw) { // the actual follow range will always be this value or smaller, so if the distance is larger then it never will return true after getting invis
                        return false;
                    }

                    double d = this.testInvisible ? target.getVisibilityPercent(entity) : 1.0;
                    double max = Math.max(followRangeRaw * d, 2.0);
                    // Gale end - Airplane - check targeting range before getting visibility
                    if (d1 > max * max) {
                        return false;
                    }
                }

                if (this.checkLineOfSight && entity instanceof Mob mob && !mob.getSensing().hasLineOfSight(target)) {
                    return false;
                }
            }

            return true;
        }
    }

    @FunctionalInterface
    public interface Selector {
        boolean test(LivingEntity entity, ServerLevel level);
    }
}
