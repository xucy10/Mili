package net.minecraft.world.level.pathfinder;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

public class FlyNodeEvaluator extends WalkNodeEvaluator {
    private final Long2ObjectMap<PathType> pathTypeByPosCache = new Long2ObjectOpenHashMap<>();
    private static final float SMALL_MOB_SIZE = 1.0F;
    private static final float SMALL_MOB_INFLATED_START_NODE_BOUNDING_BOX = 1.1F;
    private static final int MAX_START_NODE_CANDIDATES = 10;

    @Override
    public void prepare(PathNavigationRegion level, Mob mob) {
        super.prepare(level, mob);
        this.pathTypeByPosCache.clear();
        mob.onPathfindingStart();
    }

    @Override
    public void done() {
        this.mob.onPathfindingDone();
        this.pathTypeByPosCache.clear();
        super.done();
    }

    @Override
    public Node getStart() {
        int blockY;
        if (this.canFloat() && this.mob.isInWater()) {
            blockY = this.mob.getBlockY();
            BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos(this.mob.getX(), (double)blockY, this.mob.getZ());

            for (BlockState blockState = this.currentContext.getBlockState(mutableBlockPos);
                blockState.is(Blocks.WATER);
                blockState = this.currentContext.getBlockState(mutableBlockPos)
            ) {
                mutableBlockPos.set(this.mob.getX(), (double)(++blockY), this.mob.getZ());
            }
        } else {
            blockY = Mth.floor(this.mob.getY() + 0.5);
        }

        BlockPos blockPos = BlockPos.containing(this.mob.getX(), blockY, this.mob.getZ());
        if (!this.canStartAt(blockPos)) {
            for (BlockPos blockPos1 : this.iteratePathfindingStartNodeCandidatePositions(this.mob)) {
                if (this.canStartAt(blockPos1)) {
                    return super.getStartNode(blockPos1);
                }
            }
        }

        return super.getStartNode(blockPos);
    }

    @Override
    protected boolean canStartAt(BlockPos pos) {
        PathType cachedPathType = this.getCachedPathType(pos.getX(), pos.getY(), pos.getZ());
        return this.mob.getPathfindingMalus(cachedPathType) >= 0.0F;
    }

    @Override
    public Target getTarget(double x, double y, double z) {
        return this.getTargetNodeAt(x, y, z);
    }

    @Override
    public int getNeighbors(Node[] outputArray, Node node) {
        int i = 0;
        Node node1 = this.findAcceptedNode(node.x, node.y, node.z + 1);
        if (this.isOpen(node1)) {
            outputArray[i++] = node1;
        }

        Node node2 = this.findAcceptedNode(node.x - 1, node.y, node.z);
        if (this.isOpen(node2)) {
            outputArray[i++] = node2;
        }

        Node node3 = this.findAcceptedNode(node.x + 1, node.y, node.z);
        if (this.isOpen(node3)) {
            outputArray[i++] = node3;
        }

        Node node4 = this.findAcceptedNode(node.x, node.y, node.z - 1);
        if (this.isOpen(node4)) {
            outputArray[i++] = node4;
        }

        Node node5 = this.findAcceptedNode(node.x, node.y + 1, node.z);
        if (this.isOpen(node5)) {
            outputArray[i++] = node5;
        }

        Node node6 = this.findAcceptedNode(node.x, node.y - 1, node.z);
        if (this.isOpen(node6)) {
            outputArray[i++] = node6;
        }

        Node node7 = this.findAcceptedNode(node.x, node.y + 1, node.z + 1);
        if (this.isOpen(node7) && this.hasMalus(node1) && this.hasMalus(node5)) {
            outputArray[i++] = node7;
        }

        Node node8 = this.findAcceptedNode(node.x - 1, node.y + 1, node.z);
        if (this.isOpen(node8) && this.hasMalus(node2) && this.hasMalus(node5)) {
            outputArray[i++] = node8;
        }

        Node node9 = this.findAcceptedNode(node.x + 1, node.y + 1, node.z);
        if (this.isOpen(node9) && this.hasMalus(node3) && this.hasMalus(node5)) {
            outputArray[i++] = node9;
        }

        Node node10 = this.findAcceptedNode(node.x, node.y + 1, node.z - 1);
        if (this.isOpen(node10) && this.hasMalus(node4) && this.hasMalus(node5)) {
            outputArray[i++] = node10;
        }

        Node node11 = this.findAcceptedNode(node.x, node.y - 1, node.z + 1);
        if (this.isOpen(node11) && this.hasMalus(node1) && this.hasMalus(node6)) {
            outputArray[i++] = node11;
        }

        Node node12 = this.findAcceptedNode(node.x - 1, node.y - 1, node.z);
        if (this.isOpen(node12) && this.hasMalus(node2) && this.hasMalus(node6)) {
            outputArray[i++] = node12;
        }

        Node node13 = this.findAcceptedNode(node.x + 1, node.y - 1, node.z);
        if (this.isOpen(node13) && this.hasMalus(node3) && this.hasMalus(node6)) {
            outputArray[i++] = node13;
        }

        Node node14 = this.findAcceptedNode(node.x, node.y - 1, node.z - 1);
        if (this.isOpen(node14) && this.hasMalus(node4) && this.hasMalus(node6)) {
            outputArray[i++] = node14;
        }

        Node node15 = this.findAcceptedNode(node.x + 1, node.y, node.z - 1);
        if (this.isOpen(node15) && this.hasMalus(node4) && this.hasMalus(node3)) {
            outputArray[i++] = node15;
        }

        Node node16 = this.findAcceptedNode(node.x + 1, node.y, node.z + 1);
        if (this.isOpen(node16) && this.hasMalus(node1) && this.hasMalus(node3)) {
            outputArray[i++] = node16;
        }

        Node node17 = this.findAcceptedNode(node.x - 1, node.y, node.z - 1);
        if (this.isOpen(node17) && this.hasMalus(node4) && this.hasMalus(node2)) {
            outputArray[i++] = node17;
        }

        Node node18 = this.findAcceptedNode(node.x - 1, node.y, node.z + 1);
        if (this.isOpen(node18) && this.hasMalus(node1) && this.hasMalus(node2)) {
            outputArray[i++] = node18;
        }

        Node node19 = this.findAcceptedNode(node.x + 1, node.y + 1, node.z - 1);
        if (this.isOpen(node19)
            && this.hasMalus(node15)
            && this.hasMalus(node4)
            && this.hasMalus(node3)
            && this.hasMalus(node5)
            && this.hasMalus(node10)
            && this.hasMalus(node9)) {
            outputArray[i++] = node19;
        }

        Node node20 = this.findAcceptedNode(node.x + 1, node.y + 1, node.z + 1);
        if (this.isOpen(node20)
            && this.hasMalus(node16)
            && this.hasMalus(node1)
            && this.hasMalus(node3)
            && this.hasMalus(node5)
            && this.hasMalus(node7)
            && this.hasMalus(node9)) {
            outputArray[i++] = node20;
        }

        Node node21 = this.findAcceptedNode(node.x - 1, node.y + 1, node.z - 1);
        if (this.isOpen(node21)
            && this.hasMalus(node17)
            && this.hasMalus(node4)
            && this.hasMalus(node2)
            && this.hasMalus(node5)
            && this.hasMalus(node10)
            && this.hasMalus(node8)) {
            outputArray[i++] = node21;
        }

        Node node22 = this.findAcceptedNode(node.x - 1, node.y + 1, node.z + 1);
        if (this.isOpen(node22)
            && this.hasMalus(node18)
            && this.hasMalus(node1)
            && this.hasMalus(node2)
            && this.hasMalus(node5)
            && this.hasMalus(node7)
            && this.hasMalus(node8)) {
            outputArray[i++] = node22;
        }

        Node node23 = this.findAcceptedNode(node.x + 1, node.y - 1, node.z - 1);
        if (this.isOpen(node23)
            && this.hasMalus(node15)
            && this.hasMalus(node4)
            && this.hasMalus(node3)
            && this.hasMalus(node6)
            && this.hasMalus(node14)
            && this.hasMalus(node13)) {
            outputArray[i++] = node23;
        }

        Node node24 = this.findAcceptedNode(node.x + 1, node.y - 1, node.z + 1);
        if (this.isOpen(node24)
            && this.hasMalus(node16)
            && this.hasMalus(node1)
            && this.hasMalus(node3)
            && this.hasMalus(node6)
            && this.hasMalus(node11)
            && this.hasMalus(node13)) {
            outputArray[i++] = node24;
        }

        Node node25 = this.findAcceptedNode(node.x - 1, node.y - 1, node.z - 1);
        if (this.isOpen(node25)
            && this.hasMalus(node17)
            && this.hasMalus(node4)
            && this.hasMalus(node2)
            && this.hasMalus(node6)
            && this.hasMalus(node14)
            && this.hasMalus(node12)) {
            outputArray[i++] = node25;
        }

        Node node26 = this.findAcceptedNode(node.x - 1, node.y - 1, node.z + 1);
        if (this.isOpen(node26)
            && this.hasMalus(node18)
            && this.hasMalus(node1)
            && this.hasMalus(node2)
            && this.hasMalus(node6)
            && this.hasMalus(node11)
            && this.hasMalus(node12)) {
            outputArray[i++] = node26;
        }

        return i;
    }

    private boolean hasMalus(@Nullable Node node) {
        return node != null && node.costMalus >= 0.0F;
    }

    private boolean isOpen(@Nullable Node node) {
        return node != null && !node.closed;
    }

    protected @Nullable Node findAcceptedNode(int x, int y, int z) {
        Node node = null;
        PathType cachedPathType = this.getCachedPathType(x, y, z);
        float pathfindingMalus = this.mob.getPathfindingMalus(cachedPathType);
        if (pathfindingMalus >= 0.0F) {
            node = this.getNode(x, y, z);
            node.type = cachedPathType;
            node.costMalus = Math.max(node.costMalus, pathfindingMalus);
            if (cachedPathType == PathType.WALKABLE) {
                node.costMalus++;
            }
        }

        return node;
    }

    @Override
    protected PathType getCachedPathType(int x, int y, int z) {
        return this.pathTypeByPosCache.computeIfAbsent(BlockPos.asLong(x, y, z), l -> this.getPathTypeOfMob(this.currentContext, x, y, z, this.mob));
    }

    @Override
    public PathType getPathType(PathfindingContext context, int x, int y, int z) {
        PathType pathTypeFromState = context.getPathTypeFromState(x, y, z);
        if (pathTypeFromState == PathType.OPEN && y >= context.level().getMinY() + 1) {
            BlockPos blockPos = new BlockPos(x, y - 1, z);
            PathType pathTypeFromState1 = context.getPathTypeFromState(blockPos.getX(), blockPos.getY(), blockPos.getZ());
            if (pathTypeFromState1 == PathType.DAMAGE_FIRE || pathTypeFromState1 == PathType.LAVA) {
                pathTypeFromState = PathType.DAMAGE_FIRE;
            } else if (pathTypeFromState1 == PathType.DAMAGE_OTHER) {
                pathTypeFromState = PathType.DAMAGE_OTHER;
            } else if (pathTypeFromState1 == PathType.COCOA) {
                pathTypeFromState = PathType.COCOA;
            } else if (pathTypeFromState1 == PathType.FENCE) {
                if (!blockPos.equals(context.mobPosition())) {
                    pathTypeFromState = PathType.FENCE;
                }
            } else {
                pathTypeFromState = pathTypeFromState1 != PathType.WALKABLE && pathTypeFromState1 != PathType.OPEN && pathTypeFromState1 != PathType.WATER
                    ? PathType.WALKABLE
                    : PathType.OPEN;
            }
        }

        if (pathTypeFromState == PathType.WALKABLE || pathTypeFromState == PathType.OPEN) {
            pathTypeFromState = checkNeighbourBlocks(context, x, y, z, pathTypeFromState);
        }

        return pathTypeFromState;
    }

    private Iterable<BlockPos> iteratePathfindingStartNodeCandidatePositions(Mob mob) {
        AABB boundingBox = mob.getBoundingBox();
        boolean flag = boundingBox.getSize() < 1.0;
        if (!flag) {
            return List.of(
                BlockPos.containing(boundingBox.minX, mob.getBlockY(), boundingBox.minZ),
                BlockPos.containing(boundingBox.minX, mob.getBlockY(), boundingBox.maxZ),
                BlockPos.containing(boundingBox.maxX, mob.getBlockY(), boundingBox.minZ),
                BlockPos.containing(boundingBox.maxX, mob.getBlockY(), boundingBox.maxZ)
            );
        } else {
            double max = Math.max(0.0, 1.1F - boundingBox.getZsize());
            double max1 = Math.max(0.0, 1.1F - boundingBox.getXsize());
            double max2 = Math.max(0.0, 1.1F - boundingBox.getYsize());
            AABB aabb = boundingBox.inflate(max1, max2, max);
            return BlockPos.randomBetweenClosed(
                mob.getRandom(),
                10,
                Mth.floor(aabb.minX),
                Mth.floor(aabb.minY),
                Mth.floor(aabb.minZ),
                Mth.floor(aabb.maxX),
                Mth.floor(aabb.maxY),
                Mth.floor(aabb.maxZ)
            );
        }
    }
}
