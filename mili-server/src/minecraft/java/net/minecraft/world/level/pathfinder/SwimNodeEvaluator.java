package net.minecraft.world.level.pathfinder;

import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.Nullable;

public class SwimNodeEvaluator extends NodeEvaluator {
    private final boolean allowBreaching;
    private final Long2ObjectMap<PathType> pathTypesByPosCache = new Long2ObjectOpenHashMap<>();

    public SwimNodeEvaluator(boolean allowBreaching) {
        this.allowBreaching = allowBreaching;
    }

    @Override
    public void prepare(PathNavigationRegion level, Mob mob) {
        super.prepare(level, mob);
        this.pathTypesByPosCache.clear();
    }

    @Override
    public void done() {
        super.done();
        this.pathTypesByPosCache.clear();
    }

    @Override
    public Node getStart() {
        return this.getNode(
            Mth.floor(this.mob.getBoundingBox().minX), Mth.floor(this.mob.getBoundingBox().minY + 0.5), Mth.floor(this.mob.getBoundingBox().minZ)
        );
    }

    @Override
    public Target getTarget(double x, double y, double z) {
        return this.getTargetNodeAt(x, y, z);
    }

    @Override
    public int getNeighbors(Node[] outputArray, Node node) {
        int i = 0;
        Map<Direction, Node> map = Maps.newEnumMap(Direction.class);

        for (Direction direction : Direction.values()) {
            Node node1 = this.findAcceptedNode(node.x + direction.getStepX(), node.y + direction.getStepY(), node.z + direction.getStepZ());
            map.put(direction, node1);
            if (this.isNodeValid(node1)) {
                outputArray[i++] = node1;
            }
        }

        for (Direction direction1 : Direction.Plane.HORIZONTAL) {
            Direction clockWise = direction1.getClockWise();
            if (hasMalus(map.get(direction1)) && hasMalus(map.get(clockWise))) {
                Node node2 = this.findAcceptedNode(
                    node.x + direction1.getStepX() + clockWise.getStepX(), node.y, node.z + direction1.getStepZ() + clockWise.getStepZ()
                );
                if (this.isNodeValid(node2)) {
                    outputArray[i++] = node2;
                }
            }
        }

        return i;
    }

    protected boolean isNodeValid(@Nullable Node node) {
        return node != null && !node.closed;
    }

    private static boolean hasMalus(@Nullable Node node) {
        return node != null && node.costMalus >= 0.0F;
    }

    protected @Nullable Node findAcceptedNode(int x, int y, int z) {
        Node node = null;
        PathType cachedBlockType = this.getCachedBlockType(x, y, z);
        if (this.allowBreaching && cachedBlockType == PathType.BREACH || cachedBlockType == PathType.WATER) {
            float pathfindingMalus = this.mob.getPathfindingMalus(cachedBlockType);
            if (pathfindingMalus >= 0.0F) {
                node = this.getNode(x, y, z);
                node.type = cachedBlockType;
                node.costMalus = Math.max(node.costMalus, pathfindingMalus);
                if (this.currentContext.level().getFluidState(new BlockPos(x, y, z)).isEmpty()) {
                    node.costMalus += 8.0F;
                }
            }
        }

        return node;
    }

    protected PathType getCachedBlockType(int x, int y, int z) {
        return this.pathTypesByPosCache.computeIfAbsent(BlockPos.asLong(x, y, z), l -> this.getPathType(this.currentContext, x, y, z));
    }

    @Override
    public PathType getPathType(PathfindingContext context, int x, int y, int z) {
        return this.getPathTypeOfMob(context, x, y, z, this.mob);
    }

    @Override
    public PathType getPathTypeOfMob(PathfindingContext context, int x, int y, int z, Mob mob) {
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        for (int i = x; i < x + this.entityWidth; i++) {
            for (int i1 = y; i1 < y + this.entityHeight; i1++) {
                for (int i2 = z; i2 < z + this.entityDepth; i2++) {
                    BlockState blockState = context.getBlockState(mutableBlockPos.set(i, i1, i2));
                    FluidState fluidState = blockState.getFluidState();
                    if (fluidState.isEmpty() && blockState.isPathfindable(PathComputationType.WATER) && blockState.isAir()) {
                        return PathType.BREACH;
                    }

                    if (!fluidState.is(FluidTags.WATER)) {
                        return PathType.BLOCKED;
                    }
                }
            }
        }

        BlockState blockState1 = context.getBlockState(mutableBlockPos);
        return blockState1.isPathfindable(PathComputationType.WATER) ? PathType.WATER : PathType.BLOCKED;
    }
}
