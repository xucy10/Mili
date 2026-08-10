package net.minecraft.world.level;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public interface BlockGetter extends LevelHeightAccessor {
    @Nullable BlockEntity getBlockEntity(BlockPos pos);

    default <T extends BlockEntity> Optional<T> getBlockEntity(BlockPos pos, BlockEntityType<T> type) {
        BlockEntity blockEntity = this.getBlockEntity(pos);
        return blockEntity != null && blockEntity.getType() == type ? Optional.of((T)blockEntity) : Optional.empty();
    }

    BlockState getBlockState(BlockPos pos);

    // Paper start - if loaded util
    @Nullable BlockState getBlockStateIfLoaded(BlockPos pos);

    default net.minecraft.world.level.block.@Nullable Block getBlockIfLoaded(BlockPos pos) {
        BlockState type = this.getBlockStateIfLoaded(pos);
        return type == null ? null : type.getBlock();
    }

    @Nullable FluidState getFluidIfLoaded(BlockPos pos);
    // Paper end

    FluidState getFluidState(BlockPos pos);

    default int getLightEmission(BlockPos pos) {
        return this.getBlockState(pos).getLightEmission();
    }

    default Stream<BlockState> getBlockStates(AABB area) {
        return BlockPos.betweenClosedStream(area).map(this::getBlockState);
    }

    default BlockHitResult isBlockInLine(ClipBlockStateContext context) {
        return traverseBlocks(
            context.getFrom(),
            context.getTo(),
            context,
            (traverseContext, traversePos) -> {
                BlockState blockState = this.getBlockState(traversePos);
                Vec3 vec3 = traverseContext.getFrom().subtract(traverseContext.getTo());
                return traverseContext.isTargetBlock().test(blockState)
                    ? new BlockHitResult(
                        traverseContext.getTo(), Direction.getApproximateNearest(vec3.x, vec3.y, vec3.z), BlockPos.containing(traverseContext.getTo()), false
                    )
                    : null;
            },
            failContext -> {
                Vec3 vec3 = failContext.getFrom().subtract(failContext.getTo());
                return BlockHitResult.miss(
                    failContext.getTo(), Direction.getApproximateNearest(vec3.x, vec3.y, vec3.z), BlockPos.containing(failContext.getTo())
                );
            }
        );
    }

    // CraftBukkit start - moved block handling into separate method for use by Block#rayTrace
    default BlockHitResult clip(ClipContext traverseContext, BlockPos traversePos) {
        // Paper start - Add predicate for blocks when raytracing
        return clip(traverseContext, traversePos, null);
    }

    default BlockHitResult clip(ClipContext traverseContext, BlockPos traversePos, java.util.function.@Nullable Predicate<? super org.bukkit.block.Block> canCollide) {
        // Paper end - Add predicate for blocks when raytracing
        // Paper start - Prevent raytrace from loading chunks
        BlockState blockState = this.getBlockStateIfLoaded(traversePos);
        if (blockState == null) {
            // copied the last function parameter (listed below)
            Vec3 vec3d = traverseContext.getFrom().subtract(traverseContext.getTo());

            return BlockHitResult.miss(traverseContext.getTo(), Direction.getApproximateNearest(vec3d.x, vec3d.y, vec3d.z), BlockPos.containing(traverseContext.getTo()));
        }
        // Paper end - Prevent raytrace from loading chunks
        if (blockState.isAir() || (canCollide != null && this instanceof LevelAccessor levelAccessor && !canCollide.test(org.bukkit.craftbukkit.block.CraftBlock.at(levelAccessor, traversePos)))) return null; // Paper - Perf: optimise air cases & check canCollide predicate
            FluidState fluidState = blockState.getFluidState(); // Paper - Perf: don't need to go to world state again
            Vec3 from = traverseContext.getFrom();
            Vec3 to = traverseContext.getTo();
            VoxelShape blockShape = traverseContext.getBlockShape(blockState, this, traversePos);
            BlockHitResult blockHitResult = this.clipWithInteractionOverride(from, to, traversePos, blockShape, blockState);
            VoxelShape fluidShape = traverseContext.getFluidShape(fluidState, this, traversePos);
            BlockHitResult blockHitResult1 = fluidShape.clip(from, to, traversePos);
            double d = blockHitResult == null ? Double.MAX_VALUE : traverseContext.getFrom().distanceToSqr(blockHitResult.getLocation());
            double d1 = blockHitResult1 == null ? Double.MAX_VALUE : traverseContext.getFrom().distanceToSqr(blockHitResult1.getLocation());
            return d <= d1 ? blockHitResult : blockHitResult1;
    }
    // CraftBukkit end

    default BlockHitResult clip(ClipContext context) {
        // Paper start - Add predicate for blocks when raytracing
        return clip(context, (java.util.function.Predicate<org.bukkit.block.Block>) null);
    }

    default BlockHitResult clip(ClipContext context, java.util.function.@Nullable Predicate<? super org.bukkit.block.Block> canCollide) {
        // Paper end - Add predicate for blocks when raytracing
        return (BlockHitResult) BlockGetter.traverseBlocks(context.getFrom(), context.getTo(), context, (raytrace1, blockposition) -> {
            return this.clip(raytrace1, blockposition, canCollide); // CraftBukkit - moved into separate method // Paper - Add predicate for blocks when raytracing
        }, failContext -> {
            Vec3 vec3 = failContext.getFrom().subtract(failContext.getTo());
            return BlockHitResult.miss(failContext.getTo(), Direction.getApproximateNearest(vec3.x, vec3.y, vec3.z), BlockPos.containing(failContext.getTo()));
        });
    }

    default @Nullable BlockHitResult clipWithInteractionOverride(Vec3 startVec, Vec3 endVec, BlockPos pos, VoxelShape shape, BlockState state) {
        BlockHitResult blockHitResult = shape.clip(startVec, endVec, pos);
        if (blockHitResult != null) {
            BlockHitResult blockHitResult1 = state.getInteractionShape(this, pos).clip(startVec, endVec, pos);
            if (blockHitResult1 != null
                && blockHitResult1.getLocation().subtract(startVec).lengthSqr() < blockHitResult.getLocation().subtract(startVec).lengthSqr()) {
                return blockHitResult.withDirection(blockHitResult1.getDirection());
            }
        }

        return blockHitResult;
    }

    default double getBlockFloorHeight(VoxelShape shape, Supplier<VoxelShape> belowShapeSupplier) {
        if (!shape.isEmpty()) {
            return shape.max(Direction.Axis.Y);
        } else {
            double d = belowShapeSupplier.get().max(Direction.Axis.Y);
            return d >= 1.0 ? d - 1.0 : Double.NEGATIVE_INFINITY;
        }
    }

    default double getBlockFloorHeight(BlockPos pos) {
        return this.getBlockFloorHeight(this.getBlockState(pos).getCollisionShape(this, pos), () -> {
            BlockPos blockPos = pos.below();
            return this.getBlockState(blockPos).getCollisionShape(this, blockPos);
        });
    }

    static <T, C> T traverseBlocks(Vec3 from, Vec3 to, C context, BiFunction<C, BlockPos, @Nullable T> tester, Function<C, T> onFail) {
        if (from.equals(to)) {
            return onFail.apply(context);
        } else {
            double d = Mth.lerp(-1.0E-7, to.x, from.x);
            double d1 = Mth.lerp(-1.0E-7, to.y, from.y);
            double d2 = Mth.lerp(-1.0E-7, to.z, from.z);
            double d3 = Mth.lerp(-1.0E-7, from.x, to.x);
            double d4 = Mth.lerp(-1.0E-7, from.y, to.y);
            double d5 = Mth.lerp(-1.0E-7, from.z, to.z);
            int floor = Mth.floor(d3);
            int floor1 = Mth.floor(d4);
            int floor2 = Mth.floor(d5);
            BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos(floor, floor1, floor2);
            T object = tester.apply(context, mutableBlockPos);
            if (object != null) {
                return object;
            } else {
                double d6 = d - d3;
                double d7 = d1 - d4;
                double d8 = d2 - d5;
                int i = Mth.sign(d6);
                int i1 = Mth.sign(d7);
                int i2 = Mth.sign(d8);
                double d9 = i == 0 ? Double.MAX_VALUE : i / d6;
                double d10 = i1 == 0 ? Double.MAX_VALUE : i1 / d7;
                double d11 = i2 == 0 ? Double.MAX_VALUE : i2 / d8;
                double d12 = d9 * (i > 0 ? 1.0 - Mth.frac(d3) : Mth.frac(d3));
                double d13 = d10 * (i1 > 0 ? 1.0 - Mth.frac(d4) : Mth.frac(d4));
                double d14 = d11 * (i2 > 0 ? 1.0 - Mth.frac(d5) : Mth.frac(d5));

                while (d12 <= 1.0 || d13 <= 1.0 || d14 <= 1.0) {
                    if (d12 < d13) {
                        if (d12 < d14) {
                            floor += i;
                            d12 += d9;
                        } else {
                            floor2 += i2;
                            d14 += d11;
                        }
                    } else if (d13 < d14) {
                        floor1 += i1;
                        d13 += d10;
                    } else {
                        floor2 += i2;
                        d14 += d11;
                    }

                    T object1 = tester.apply(context, mutableBlockPos.set(floor, floor1, floor2));
                    if (object1 != null) {
                        return object1;
                    }
                }

                return onFail.apply(context);
            }
        }
    }

    static boolean forEachBlockIntersectedBetween(Vec3 from, Vec3 to, AABB boundingBox, BlockGetter.BlockStepVisitor visitor) {
        Vec3 vec3 = to.subtract(from);
        if (vec3.lengthSqr() < Mth.square(1.0E-5F)) {
            for (BlockPos blockPos : BlockPos.betweenClosed(boundingBox)) {
                if (!visitor.visit(blockPos, 0)) {
                    return false;
                }
            }

            return true;
        } else {
            LongSet set = new LongOpenHashSet();

            for (BlockPos blockPos1 : BlockPos.betweenCornersInDirection(boundingBox.move(vec3.scale(-1.0)), vec3)) {
                if (!visitor.visit(blockPos1, 0)) {
                    return false;
                }

                set.add(blockPos1.asLong());
            }

            int i = addCollisionsAlongTravel(set, vec3, boundingBox, visitor);
            if (i < 0) {
                return false;
            } else {
                for (BlockPos blockPos2 : BlockPos.betweenCornersInDirection(boundingBox, vec3)) {
                    if (set.add(blockPos2.asLong()) && !visitor.visit(blockPos2, i + 1)) {
                        return false;
                    }
                }

                return true;
            }
        }
    }

    private static int addCollisionsAlongTravel(LongSet output, Vec3 travelVector, AABB boundingBox, BlockGetter.BlockStepVisitor stepVisitor) {
        double xsize = boundingBox.getXsize();
        double ysize = boundingBox.getYsize();
        double zsize = boundingBox.getZsize();
        Vec3i furthestCorner = getFurthestCorner(travelVector);
        Vec3 center = boundingBox.getCenter();
        Vec3 vec3 = new Vec3(
            center.x() + xsize * 0.5 * furthestCorner.getX(),
            center.y() + ysize * 0.5 * furthestCorner.getY(),
            center.z() + zsize * 0.5 * furthestCorner.getZ()
        );
        Vec3 vec31 = vec3.subtract(travelVector);
        int floor = Mth.floor(vec31.x);
        int floor1 = Mth.floor(vec31.y);
        int floor2 = Mth.floor(vec31.z);
        int i = Mth.sign(travelVector.x);
        int i1 = Mth.sign(travelVector.y);
        int i2 = Mth.sign(travelVector.z);
        double d = i == 0 ? Double.MAX_VALUE : i / travelVector.x;
        double d1 = i1 == 0 ? Double.MAX_VALUE : i1 / travelVector.y;
        double d2 = i2 == 0 ? Double.MAX_VALUE : i2 / travelVector.z;
        double d3 = d * (i > 0 ? 1.0 - Mth.frac(vec31.x) : Mth.frac(vec31.x));
        double d4 = d1 * (i1 > 0 ? 1.0 - Mth.frac(vec31.y) : Mth.frac(vec31.y));
        double d5 = d2 * (i2 > 0 ? 1.0 - Mth.frac(vec31.z) : Mth.frac(vec31.z));
        int i3 = 0;

        while (d3 <= 1.0 || d4 <= 1.0 || d5 <= 1.0) {
            if (d3 < d4) {
                if (d3 < d5) {
                    floor += i;
                    d3 += d;
                } else {
                    floor2 += i2;
                    d5 += d2;
                }
            } else if (d4 < d5) {
                floor1 += i1;
                d4 += d1;
            } else {
                floor2 += i2;
                d5 += d2;
            }

            Optional<Vec3> optional = AABB.clip(floor, floor1, floor2, floor + 1, floor1 + 1, floor2 + 1, vec31, vec3);
            if (!optional.isEmpty()) {
                i3++;
                Vec3 vec32 = optional.get();
                double d6 = Mth.clamp(vec32.x, floor + 1.0E-5F, floor + 1.0 - 1.0E-5F);
                double d7 = Mth.clamp(vec32.y, floor1 + 1.0E-5F, floor1 + 1.0 - 1.0E-5F);
                double d8 = Mth.clamp(vec32.z, floor2 + 1.0E-5F, floor2 + 1.0 - 1.0E-5F);
                int floor3 = Mth.floor(d6 - xsize * furthestCorner.getX());
                int floor4 = Mth.floor(d7 - ysize * furthestCorner.getY());
                int floor5 = Mth.floor(d8 - zsize * furthestCorner.getZ());
                int i4 = i3;

                for (BlockPos blockPos : BlockPos.betweenCornersInDirection(floor, floor1, floor2, floor3, floor4, floor5, travelVector)) {
                    if (output.add(blockPos.asLong()) && !stepVisitor.visit(blockPos, i4)) {
                        return -1;
                    }
                }
            }
        }

        return i3;
    }

    private static Vec3i getFurthestCorner(Vec3 vector) {
        double abs = Math.abs(Vec3.X_AXIS.dot(vector));
        double abs1 = Math.abs(Vec3.Y_AXIS.dot(vector));
        double abs2 = Math.abs(Vec3.Z_AXIS.dot(vector));
        int i = vector.x >= 0.0 ? 1 : -1;
        int i1 = vector.y >= 0.0 ? 1 : -1;
        int i2 = vector.z >= 0.0 ? 1 : -1;
        if (abs <= abs1 && abs <= abs2) {
            return new Vec3i(-i, -i2, i1);
        } else {
            return abs1 <= abs2 ? new Vec3i(i2, -i1, -i) : new Vec3i(-i1, i, -i2);
        }
    }

    @FunctionalInterface
    public interface BlockStepVisitor {
        boolean visit(BlockPos pos, int index);
    }
}
