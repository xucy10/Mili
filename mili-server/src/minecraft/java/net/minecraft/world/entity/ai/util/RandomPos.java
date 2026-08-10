package net.minecraft.world.entity.ai.util;

import com.google.common.annotations.VisibleForTesting;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class RandomPos {
    private static final int RANDOM_POS_ATTEMPTS = 10;

    public static BlockPos generateRandomDirection(RandomSource random, int horizontalDistance, int verticalDistance) {
        int i = random.nextInt(2 * horizontalDistance + 1) - horizontalDistance;
        int i1 = random.nextInt(2 * verticalDistance + 1) - verticalDistance;
        int i2 = random.nextInt(2 * horizontalDistance + 1) - horizontalDistance;
        return new BlockPos(i, i1, i2);
    }

    public static @Nullable BlockPos generateRandomDirectionWithinRadians(
        RandomSource random, double minDistance, double maxDistance, int yRange, int y, double x, double z, double maxAngleDelta
    ) {
        double d = Mth.atan2(z, x) - (float) (Math.PI / 2);
        double d1 = d + (2.0F * random.nextFloat() - 1.0F) * maxAngleDelta;
        double d2 = Mth.lerp(Math.sqrt(random.nextDouble()), minDistance, maxDistance) * Mth.SQRT_OF_TWO;
        double d3 = -d2 * Math.sin(d1);
        double d4 = d2 * Math.cos(d1);
        if (!(Math.abs(d3) > maxDistance) && !(Math.abs(d4) > maxDistance)) {
            int i = random.nextInt(2 * yRange + 1) - yRange + y;
            return BlockPos.containing(d3, i, d4);
        } else {
            return null;
        }
    }

    @VisibleForTesting
    public static BlockPos moveUpOutOfSolid(BlockPos pos, int maxY, Predicate<BlockPos> posPredicate) {
        if (!posPredicate.test(pos)) {
            return pos;
        } else {
            BlockPos.MutableBlockPos mutableBlockPos = pos.mutable().move(Direction.UP);

            while (mutableBlockPos.getY() <= maxY && posPredicate.test(mutableBlockPos)) {
                mutableBlockPos.move(Direction.UP);
            }

            return mutableBlockPos.immutable();
        }
    }

    @VisibleForTesting
    public static BlockPos moveUpToAboveSolid(BlockPos pos, int aboveSolidAmount, int maxY, Predicate<BlockPos> posPredicate) {
        if (aboveSolidAmount < 0) {
            throw new IllegalArgumentException("aboveSolidAmount was " + aboveSolidAmount + ", expected >= 0");
        } else if (!posPredicate.test(pos)) {
            return pos;
        } else {
            BlockPos.MutableBlockPos mutableBlockPos = pos.mutable().move(Direction.UP);

            while (mutableBlockPos.getY() <= maxY && posPredicate.test(mutableBlockPos)) {
                mutableBlockPos.move(Direction.UP);
            }

            int y = mutableBlockPos.getY();

            while (mutableBlockPos.getY() <= maxY && mutableBlockPos.getY() - y < aboveSolidAmount) {
                mutableBlockPos.move(Direction.UP);
                if (posPredicate.test(mutableBlockPos)) {
                    mutableBlockPos.move(Direction.DOWN);
                    break;
                }
            }

            return mutableBlockPos.immutable();
        }
    }

    public static @Nullable Vec3 generateRandomPos(PathfinderMob mob, Supplier<@Nullable BlockPos> posSupplier) {
        return generateRandomPos(posSupplier, mob::getWalkTargetValue);
    }

    public static @Nullable Vec3 generateRandomPos(Supplier<@Nullable BlockPos> posSupplier, ToDoubleFunction<BlockPos> toDoubleFunction) {
        double d = Double.NEGATIVE_INFINITY;
        BlockPos blockPos = null;

        for (int i = 0; i < 10; i++) {
            BlockPos blockPos1 = posSupplier.get();
            if (blockPos1 != null) {
                double d1 = toDoubleFunction.applyAsDouble(blockPos1);
                if (d1 > d) {
                    d = d1;
                    blockPos = blockPos1;
                }
            }
        }

        return blockPos != null ? Vec3.atBottomCenterOf(blockPos) : null;
    }

    public static BlockPos generateRandomPosTowardDirection(PathfinderMob mob, double range, RandomSource random, BlockPos pos) {
        double d = pos.getX();
        double d1 = pos.getZ();
        if (mob.hasHome() && range > 1.0) {
            BlockPos homePosition = mob.getHomePosition();
            if (mob.getX() > homePosition.getX()) {
                d -= random.nextDouble() * range / 2.0;
            } else {
                d += random.nextDouble() * range / 2.0;
            }

            if (mob.getZ() > homePosition.getZ()) {
                d1 -= random.nextDouble() * range / 2.0;
            } else {
                d1 += random.nextDouble() * range / 2.0;
            }
        }

        return BlockPos.containing(d + mob.getX(), pos.getY() + mob.getY(), d1 + mob.getZ());
    }
}
