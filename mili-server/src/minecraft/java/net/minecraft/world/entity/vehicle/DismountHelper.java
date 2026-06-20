package net.minecraft.world.entity.vehicle;

import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class DismountHelper {
    public static int[][] offsetsForDirection(Direction direction) {
        Direction clockWise = direction.getClockWise();
        Direction opposite = clockWise.getOpposite();
        Direction opposite1 = direction.getOpposite();
        return new int[][]{
            {clockWise.getStepX(), clockWise.getStepZ()},
            {opposite.getStepX(), opposite.getStepZ()},
            {opposite1.getStepX() + clockWise.getStepX(), opposite1.getStepZ() + clockWise.getStepZ()},
            {opposite1.getStepX() + opposite.getStepX(), opposite1.getStepZ() + opposite.getStepZ()},
            {direction.getStepX() + clockWise.getStepX(), direction.getStepZ() + clockWise.getStepZ()},
            {direction.getStepX() + opposite.getStepX(), direction.getStepZ() + opposite.getStepZ()},
            {opposite1.getStepX(), opposite1.getStepZ()},
            {direction.getStepX(), direction.getStepZ()}
        };
    }

    public static boolean isBlockFloorValid(double distance) {
        return !Double.isInfinite(distance) && distance < 1.0;
    }

    public static boolean canDismountTo(CollisionGetter level, LivingEntity passenger, AABB boundingBox) {
        for (VoxelShape voxelShape : level.getBlockCollisions(passenger, boundingBox)) {
            if (!voxelShape.isEmpty()) {
                return false;
            }
        }

        return level.getWorldBorder().isWithinBounds(boundingBox);
    }

    public static boolean canDismountTo(CollisionGetter level, Vec3 offset, LivingEntity passenger, Pose pose) {
        return canDismountTo(level, passenger, passenger.getLocalBoundsForPose(pose).move(offset));
    }

    public static VoxelShape nonClimbableShape(BlockGetter level, BlockPos pos) {
        BlockState blockState = level.getBlockState(pos);
        return !blockState.is(BlockTags.CLIMBABLE) && (!(blockState.getBlock() instanceof TrapDoorBlock) || !blockState.getValue(TrapDoorBlock.OPEN))
            ? blockState.getCollisionShape(level, pos)
            : Shapes.empty();
    }

    public static double findCeilingFrom(BlockPos pos, int ceiling, Function<BlockPos, VoxelShape> shapeForPos) {
        BlockPos.MutableBlockPos mutableBlockPos = pos.mutable();
        int i = 0;

        while (i < ceiling) {
            VoxelShape voxelShape = shapeForPos.apply(mutableBlockPos);
            if (!voxelShape.isEmpty()) {
                return pos.getY() + i + voxelShape.min(Direction.Axis.Y);
            }

            i++;
            mutableBlockPos.move(Direction.UP);
        }

        return Double.POSITIVE_INFINITY;
    }

    public static @Nullable Vec3 findSafeDismountLocation(EntityType<?> entityType, CollisionGetter level, BlockPos pos, boolean onlySafePositions) {
        if (onlySafePositions && entityType.isBlockDangerous(level.getBlockState(pos))) {
            return null;
        } else {
            double blockFloorHeight = level.getBlockFloorHeight(nonClimbableShape(level, pos), () -> nonClimbableShape(level, pos.below()));
            if (!isBlockFloorValid(blockFloorHeight)) {
                return null;
            } else if (onlySafePositions && blockFloorHeight <= 0.0 && entityType.isBlockDangerous(level.getBlockState(pos.below()))) {
                return null;
            } else {
                Vec3 vec3 = Vec3.upFromBottomCenterOf(pos, blockFloorHeight);
                AABB aabb = entityType.getDimensions().makeBoundingBox(vec3);

                for (VoxelShape voxelShape : level.getBlockCollisions(null, aabb)) {
                    if (!voxelShape.isEmpty()) {
                        return null;
                    }
                }

                if (entityType != EntityType.PLAYER
                    || !level.getBlockState(pos).is(BlockTags.INVALID_SPAWN_INSIDE) && !level.getBlockState(pos.above()).is(BlockTags.INVALID_SPAWN_INSIDE)) {
                    return !level.getWorldBorder().isWithinBounds(aabb) ? null : vec3;
                } else {
                    return null;
                }
            }
        }
    }
}
