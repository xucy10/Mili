package net.minecraft.world.level.pathfinder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.PathNavigationRegion;
import org.jspecify.annotations.Nullable;

public class AmphibiousNodeEvaluator extends WalkNodeEvaluator {
    private final boolean prefersShallowSwimming;
    private float oldWalkableCost;
    private float oldWaterBorderCost;

    public AmphibiousNodeEvaluator(boolean prefersShallowSwimming) {
        this.prefersShallowSwimming = prefersShallowSwimming;
    }

    @Override
    public void prepare(PathNavigationRegion level, Mob mob) {
        super.prepare(level, mob);
        mob.setPathfindingMalus(PathType.WATER, 0.0F);
        this.oldWalkableCost = mob.getPathfindingMalus(PathType.WALKABLE);
        mob.setPathfindingMalus(PathType.WALKABLE, 6.0F);
        this.oldWaterBorderCost = mob.getPathfindingMalus(PathType.WATER_BORDER);
        mob.setPathfindingMalus(PathType.WATER_BORDER, 4.0F);
    }

    @Override
    public void done() {
        this.mob.setPathfindingMalus(PathType.WALKABLE, this.oldWalkableCost);
        this.mob.setPathfindingMalus(PathType.WATER_BORDER, this.oldWaterBorderCost);
        super.done();
    }

    @Override
    public Node getStart() {
        return !this.mob.isInWater()
            ? super.getStart()
            : this.getStartNode(
                new BlockPos(
                    Mth.floor(this.mob.getBoundingBox().minX), Mth.floor(this.mob.getBoundingBox().minY + 0.5), Mth.floor(this.mob.getBoundingBox().minZ)
                )
            );
    }

    @Override
    public Target getTarget(double x, double y, double z) {
        return this.getTargetNodeAt(x, y + 0.5, z);
    }

    @Override
    public int getNeighbors(Node[] outputArray, Node node) {
        int i = super.getNeighbors(outputArray, node);
        PathType cachedPathType = this.getCachedPathType(node.x, node.y + 1, node.z);
        PathType cachedPathType1 = this.getCachedPathType(node.x, node.y, node.z);
        int floor;
        if (this.mob.getPathfindingMalus(cachedPathType) >= 0.0F && cachedPathType1 != PathType.STICKY_HONEY) {
            floor = Mth.floor(Math.max(1.0F, this.mob.maxUpStep()));
        } else {
            floor = 0;
        }

        double floorLevel = this.getFloorLevel(new BlockPos(node.x, node.y, node.z));
        Node node1 = this.findAcceptedNode(node.x, node.y + 1, node.z, Math.max(0, floor - 1), floorLevel, Direction.UP, cachedPathType1);
        Node node2 = this.findAcceptedNode(node.x, node.y - 1, node.z, floor, floorLevel, Direction.DOWN, cachedPathType1);
        if (this.isVerticalNeighborValid(node1, node)) {
            outputArray[i++] = node1;
        }

        if (this.isVerticalNeighborValid(node2, node) && cachedPathType1 != PathType.TRAPDOOR) {
            outputArray[i++] = node2;
        }

        for (int i1 = 0; i1 < i; i1++) {
            Node node3 = outputArray[i1];
            if (node3.type == PathType.WATER && this.prefersShallowSwimming && node3.y < this.mob.level().getSeaLevel() - 10) {
                node3.costMalus++;
            }
        }

        return i;
    }

    private boolean isVerticalNeighborValid(@Nullable Node neighbor, Node node) {
        return this.isNeighborValid(neighbor, node) && neighbor.type == PathType.WATER;
    }

    @Override
    protected boolean isAmphibious() {
        return true;
    }

    @Override
    public PathType getPathType(PathfindingContext context, int x, int y, int z) {
        PathType pathTypeFromState = context.getPathTypeFromState(x, y, z);
        if (pathTypeFromState == PathType.WATER) {
            BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

            for (Direction direction : Direction.values()) {
                mutableBlockPos.set(x, y, z).move(direction);
                PathType pathTypeFromState1 = context.getPathTypeFromState(mutableBlockPos.getX(), mutableBlockPos.getY(), mutableBlockPos.getZ());
                if (pathTypeFromState1 == PathType.BLOCKED) {
                    return PathType.WATER_BORDER;
                }
            }

            return PathType.WATER;
        } else {
            return super.getPathType(context, x, y, z);
        }
    }
}
