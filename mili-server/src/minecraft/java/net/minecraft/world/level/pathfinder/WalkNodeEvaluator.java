package net.minecraft.world.level.pathfinder;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class WalkNodeEvaluator extends NodeEvaluator {
    public static final double SPACE_BETWEEN_WALL_POSTS = 0.5;
    private static final double DEFAULT_MOB_JUMP_HEIGHT = 1.125;
    private final Long2ObjectMap<PathType> pathTypesByPosCacheByMob = new Long2ObjectOpenHashMap<>();
    private final Object2BooleanMap<AABB> collisionCache = new Object2BooleanOpenHashMap<>();
    private final Node[] reusableNeighbors = new Node[Direction.Plane.HORIZONTAL.length()];

    @Override
    public void prepare(PathNavigationRegion level, Mob mob) {
        super.prepare(level, mob);
        mob.onPathfindingStart();
    }

    @Override
    public void done() {
        this.mob.onPathfindingDone();
        this.pathTypesByPosCacheByMob.clear();
        this.collisionCache.clear();
        super.done();
    }

    @Override
    public Node getStart() {
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        int blockY = this.mob.getBlockY();
        BlockState blockState = this.currentContext.getBlockState(mutableBlockPos.set(this.mob.getX(), (double)blockY, this.mob.getZ()));
        if (!this.mob.canStandOnFluid(blockState.getFluidState())) {
            if (this.canFloat() && this.mob.isInWater()) {
                while (true) {
                    if (!blockState.is(Blocks.WATER) && blockState.getFluidState() != Fluids.WATER.getSource(false)) {
                        blockY--;
                        break;
                    }

                    blockState = this.currentContext.getBlockState(mutableBlockPos.set(this.mob.getX(), (double)(++blockY), this.mob.getZ()));
                }
            } else if (this.mob.onGround()) {
                blockY = Mth.floor(this.mob.getY() + 0.5);
            } else {
                mutableBlockPos.set(this.mob.getX(), this.mob.getY() + 1.0, this.mob.getZ());

                while (mutableBlockPos.getY() > this.currentContext.level().getMinY()) {
                    blockY = mutableBlockPos.getY();
                    mutableBlockPos.setY(mutableBlockPos.getY() - 1);
                    BlockState blockState1 = this.currentContext.getBlockState(mutableBlockPos);
                    if (!blockState1.isAir() && !blockState1.isPathfindable(PathComputationType.LAND)) {
                        break;
                    }
                }
            }
        } else {
            while (this.mob.canStandOnFluid(blockState.getFluidState())) {
                blockState = this.currentContext.getBlockState(mutableBlockPos.set(this.mob.getX(), (double)(++blockY), this.mob.getZ()));
            }

            blockY--;
        }

        BlockPos blockPos = this.mob.blockPosition();
        if (!this.canStartAt(mutableBlockPos.set(blockPos.getX(), blockY, blockPos.getZ()))) {
            AABB boundingBox = this.mob.getBoundingBox();
            if (this.canStartAt(mutableBlockPos.set(boundingBox.minX, (double)blockY, boundingBox.minZ))
                || this.canStartAt(mutableBlockPos.set(boundingBox.minX, (double)blockY, boundingBox.maxZ))
                || this.canStartAt(mutableBlockPos.set(boundingBox.maxX, (double)blockY, boundingBox.minZ))
                || this.canStartAt(mutableBlockPos.set(boundingBox.maxX, (double)blockY, boundingBox.maxZ))) {
                return this.getStartNode(mutableBlockPos);
            }
        }

        return this.getStartNode(new BlockPos(blockPos.getX(), blockY, blockPos.getZ()));
    }

    protected Node getStartNode(BlockPos pos) {
        Node node = this.getNode(pos);
        node.type = this.getCachedPathType(node.x, node.y, node.z);
        node.costMalus = this.mob.getPathfindingMalus(node.type);
        return node;
    }

    protected boolean canStartAt(BlockPos pos) {
        PathType cachedPathType = this.getCachedPathType(pos.getX(), pos.getY(), pos.getZ());
        return cachedPathType != PathType.OPEN && this.mob.getPathfindingMalus(cachedPathType) >= 0.0F;
    }

    @Override
    public Target getTarget(double x, double y, double z) {
        return this.getTargetNodeAt(x, y, z);
    }

    @Override
    public int getNeighbors(Node[] outputArray, Node node) {
        int i = 0;
        int i1 = 0;
        PathType cachedPathType = this.getCachedPathType(node.x, node.y + 1, node.z);
        PathType cachedPathType1 = this.getCachedPathType(node.x, node.y, node.z);
        if (this.mob.getPathfindingMalus(cachedPathType) >= 0.0F && cachedPathType1 != PathType.STICKY_HONEY) {
            i1 = Mth.floor(Math.max(1.0F, this.mob.maxUpStep()));
        }

        double floorLevel = this.getFloorLevel(new BlockPos(node.x, node.y, node.z));

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            Node node1 = this.findAcceptedNode(node.x + direction.getStepX(), node.y, node.z + direction.getStepZ(), i1, floorLevel, direction, cachedPathType1);
            this.reusableNeighbors[direction.get2DDataValue()] = node1;
            if (this.isNeighborValid(node1, node)) {
                outputArray[i++] = node1;
            }
        }

        for (Direction directionx : Direction.Plane.HORIZONTAL) {
            Direction clockWise = directionx.getClockWise();
            if (this.isDiagonalValid(node, this.reusableNeighbors[directionx.get2DDataValue()], this.reusableNeighbors[clockWise.get2DDataValue()])) {
                Node node2 = this.findAcceptedNode(
                    node.x + directionx.getStepX() + clockWise.getStepX(),
                    node.y,
                    node.z + directionx.getStepZ() + clockWise.getStepZ(),
                    i1,
                    floorLevel,
                    directionx,
                    cachedPathType1
                );
                if (this.isDiagonalValid(node2)) {
                    outputArray[i++] = node2;
                }
            }
        }

        return i;
    }

    protected boolean isNeighborValid(@Nullable Node neighbor, Node node) {
        return neighbor != null && !neighbor.closed && (neighbor.costMalus >= 0.0F || node.costMalus < 0.0F);
    }

    protected boolean isDiagonalValid(Node root, @Nullable Node xNode, @Nullable Node zNode) {
        if (zNode == null || xNode == null || zNode.y > root.y || xNode.y > root.y) {
            return false;
        } else if (xNode.type != PathType.WALKABLE_DOOR && zNode.type != PathType.WALKABLE_DOOR) {
            boolean flag = zNode.type == PathType.FENCE && xNode.type == PathType.FENCE && this.mob.getBbWidth() < 0.5;
            return (zNode.y < root.y || zNode.costMalus >= 0.0F || flag) && (xNode.y < root.y || xNode.costMalus >= 0.0F || flag);
        } else {
            return false;
        }
    }

    protected boolean isDiagonalValid(@Nullable Node node) {
        return node != null && !node.closed && node.type != PathType.WALKABLE_DOOR && node.costMalus >= 0.0F;
    }

    private static boolean doesBlockHavePartialCollision(PathType pathType) {
        return pathType == PathType.FENCE || pathType == PathType.DOOR_WOOD_CLOSED || pathType == PathType.DOOR_IRON_CLOSED;
    }

    private boolean canReachWithoutCollision(Node node) {
        AABB boundingBox = this.mob.getBoundingBox();
        Vec3 vec3 = new Vec3(
            node.x - this.mob.getX() + boundingBox.getXsize() / 2.0,
            node.y - this.mob.getY() + boundingBox.getYsize() / 2.0,
            node.z - this.mob.getZ() + boundingBox.getZsize() / 2.0
        );
        int ceil = Mth.ceil(vec3.length() / boundingBox.getSize());
        vec3 = vec3.scale(1.0F / ceil);

        for (int i = 1; i <= ceil; i++) {
            boundingBox = boundingBox.move(vec3);
            if (this.hasCollisions(boundingBox)) {
                return false;
            }
        }

        return true;
    }

    protected double getFloorLevel(BlockPos pos) {
        BlockGetter blockGetter = this.currentContext.level();
        return (this.canFloat() || this.isAmphibious()) && blockGetter.getFluidState(pos).is(FluidTags.WATER)
            ? pos.getY() + 0.5
            : getFloorLevel(blockGetter, pos);
    }

    public static double getFloorLevel(BlockGetter level, BlockPos pos) {
        BlockPos blockPos = pos.below();
        VoxelShape collisionShape = level.getBlockState(blockPos).getCollisionShape(level, blockPos);
        return blockPos.getY() + (collisionShape.isEmpty() ? 0.0 : collisionShape.max(Direction.Axis.Y));
    }

    protected boolean isAmphibious() {
        return false;
    }

    protected @Nullable Node findAcceptedNode(int x, int y, int z, int verticalDeltaLimit, double nodeFloorLevel, Direction direction, PathType pathType) {
        Node node = null;
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        double floorLevel = this.getFloorLevel(mutableBlockPos.set(x, y, z));
        if (floorLevel - nodeFloorLevel > this.getMobJumpHeight()) {
            return null;
        } else {
            PathType cachedPathType = this.getCachedPathType(x, y, z);
            float pathfindingMalus = this.mob.getPathfindingMalus(cachedPathType);
            if (pathfindingMalus >= 0.0F) {
                node = this.getNodeAndUpdateCostToMax(x, y, z, cachedPathType, pathfindingMalus);
            }

            if (doesBlockHavePartialCollision(pathType) && node != null && node.costMalus >= 0.0F && !this.canReachWithoutCollision(node)) {
                node = null;
            }

            if (cachedPathType != PathType.WALKABLE && (!this.isAmphibious() || cachedPathType != PathType.WATER)) {
                if ((node == null || node.costMalus < 0.0F)
                    && verticalDeltaLimit > 0
                    && (cachedPathType != PathType.FENCE || this.canWalkOverFences())
                    && cachedPathType != PathType.UNPASSABLE_RAIL
                    && cachedPathType != PathType.TRAPDOOR
                    && cachedPathType != PathType.POWDER_SNOW) {
                    node = this.tryJumpOn(x, y, z, verticalDeltaLimit, nodeFloorLevel, direction, pathType, mutableBlockPos);
                } else if (!this.isAmphibious() && cachedPathType == PathType.WATER && !this.canFloat()) {
                    node = this.tryFindFirstNonWaterBelow(x, y, z, node);
                } else if (cachedPathType == PathType.OPEN) {
                    node = this.tryFindFirstGroundNodeBelow(x, y, z);
                } else if (doesBlockHavePartialCollision(cachedPathType) && node == null) {
                    node = this.getClosedNode(x, y, z, cachedPathType);
                }

                return node;
            } else {
                return node;
            }
        }
    }

    private double getMobJumpHeight() {
        return Math.max(1.125, (double)this.mob.maxUpStep());
    }

    private Node getNodeAndUpdateCostToMax(int x, int y, int z, PathType pathType, float malus) {
        Node node = this.getNode(x, y, z);
        node.type = pathType;
        node.costMalus = Math.max(node.costMalus, malus);
        return node;
    }

    private Node getBlockedNode(int x, int y, int z) {
        Node node = this.getNode(x, y, z);
        node.type = PathType.BLOCKED;
        node.costMalus = -1.0F;
        return node;
    }

    private Node getClosedNode(int x, int y, int z, PathType pathType) {
        Node node = this.getNode(x, y, z);
        node.closed = true;
        node.type = pathType;
        node.costMalus = pathType.getMalus();
        return node;
    }

    private @Nullable Node tryJumpOn(
        int x, int y, int z, int verticalDeltaLimit, double nodeFloorLevel, Direction direction, PathType pathType, BlockPos.MutableBlockPos pos
    ) {
        Node node = this.findAcceptedNode(x, y + 1, z, verticalDeltaLimit - 1, nodeFloorLevel, direction, pathType);
        if (node == null) {
            return null;
        } else if (this.mob.getBbWidth() >= 1.0F) {
            return node;
        } else if (node.type != PathType.OPEN && node.type != PathType.WALKABLE) {
            return node;
        } else {
            double d = x - direction.getStepX() + 0.5;
            double d1 = z - direction.getStepZ() + 0.5;
            double d2 = this.mob.getBbWidth() / 2.0;
            AABB aabb = new AABB(
                d - d2,
                this.getFloorLevel(pos.set(d, (double)(y + 1), d1)) + 0.001,
                d1 - d2,
                d + d2,
                this.mob.getBbHeight() + this.getFloorLevel(pos.set((double)node.x, (double)node.y, (double)node.z)) - 0.002,
                d1 + d2
            );
            return this.hasCollisions(aabb) ? null : node;
        }
    }

    private @Nullable Node tryFindFirstNonWaterBelow(int x, int y, int z, @Nullable Node node) {
        y--;

        while (y > this.mob.level().getMinY()) {
            PathType cachedPathType = this.getCachedPathType(x, y, z);
            if (cachedPathType != PathType.WATER) {
                return node;
            }

            node = this.getNodeAndUpdateCostToMax(x, y, z, cachedPathType, this.mob.getPathfindingMalus(cachedPathType));
            y--;
        }

        return node;
    }

    private Node tryFindFirstGroundNodeBelow(int x, int y, int z) {
        for (int i = y - 1; i >= this.mob.level().getMinY(); i--) {
            if (y - i > this.mob.getMaxFallDistance()) {
                return this.getBlockedNode(x, i, z);
            }

            PathType cachedPathType = this.getCachedPathType(x, i, z);
            float pathfindingMalus = this.mob.getPathfindingMalus(cachedPathType);
            if (cachedPathType != PathType.OPEN) {
                if (pathfindingMalus >= 0.0F) {
                    return this.getNodeAndUpdateCostToMax(x, i, z, cachedPathType, pathfindingMalus);
                }

                return this.getBlockedNode(x, i, z);
            }
        }

        return this.getBlockedNode(x, y, z);
    }

    private boolean hasCollisions(AABB boundingBox) {
        return this.collisionCache.computeIfAbsent(boundingBox, key -> !this.currentContext.level().noCollision(this.mob, boundingBox));
    }

    protected PathType getCachedPathType(int x, int y, int z) {
        return this.pathTypesByPosCacheByMob.computeIfAbsent(BlockPos.asLong(x, y, z), l -> this.getPathTypeOfMob(this.currentContext, x, y, z, this.mob));
    }

    @Override
    public PathType getPathTypeOfMob(PathfindingContext context, int x, int y, int z, Mob mob) {
        Set<PathType> pathTypeWithinMobBb = this.getPathTypeWithinMobBB(context, x, y, z);
        if (pathTypeWithinMobBb.contains(PathType.FENCE)) {
            return PathType.FENCE;
        } else if (pathTypeWithinMobBb.contains(PathType.UNPASSABLE_RAIL)) {
            return PathType.UNPASSABLE_RAIL;
        } else {
            PathType pathType = PathType.BLOCKED;

            for (PathType pathType1 : pathTypeWithinMobBb) {
                if (mob.getPathfindingMalus(pathType1) < 0.0F) {
                    return pathType1;
                }

                if (mob.getPathfindingMalus(pathType1) >= mob.getPathfindingMalus(pathType)) {
                    pathType = pathType1;
                }
            }

            return this.entityWidth <= 1
                    && pathType != PathType.OPEN
                    && mob.getPathfindingMalus(pathType) == 0.0F
                    && this.getPathType(context, x, y, z) == PathType.OPEN
                ? PathType.OPEN
                : pathType;
        }
    }

    public Set<PathType> getPathTypeWithinMobBB(PathfindingContext context, int x, int y, int z) {
        EnumSet<PathType> set = EnumSet.noneOf(PathType.class);

        for (int i = 0; i < this.entityWidth; i++) {
            for (int i1 = 0; i1 < this.entityHeight; i1++) {
                for (int i2 = 0; i2 < this.entityDepth; i2++) {
                    int i3 = i + x;
                    int i4 = i1 + y;
                    int i5 = i2 + z;
                    PathType pathType = this.getPathType(context, i3, i4, i5);
                    BlockPos blockPos = this.mob.blockPosition();
                    boolean canPassDoors = this.canPassDoors();
                    if (pathType == PathType.DOOR_WOOD_CLOSED && this.canOpenDoors() && canPassDoors) {
                        pathType = PathType.WALKABLE_DOOR;
                    }

                    if (pathType == PathType.DOOR_OPEN && !canPassDoors) {
                        pathType = PathType.BLOCKED;
                    }

                    if (pathType == PathType.RAIL
                        && this.getPathType(context, blockPos.getX(), blockPos.getY(), blockPos.getZ()) != PathType.RAIL
                        && this.getPathType(context, blockPos.getX(), blockPos.getY() - 1, blockPos.getZ()) != PathType.RAIL) {
                        pathType = PathType.UNPASSABLE_RAIL;
                    }

                    set.add(pathType);
                }
            }
        }

        return set;
    }

    @Override
    public PathType getPathType(PathfindingContext context, int x, int y, int z) {
        return getPathTypeStatic(context, new BlockPos.MutableBlockPos(x, y, z));
    }

    public static PathType getPathTypeStatic(Mob mob, BlockPos pos) {
        return getPathTypeStatic(new PathfindingContext(mob.level(), mob), pos.mutable());
    }

    public static PathType getPathTypeStatic(PathfindingContext context, BlockPos.MutableBlockPos pos) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        PathType pathTypeFromState = context.getPathTypeFromState(x, y, z);
        if (pathTypeFromState == PathType.OPEN && y >= context.level().getMinY() + 1) {
            return switch (context.getPathTypeFromState(x, y - 1, z)) {
                case OPEN, WATER, LAVA, WALKABLE -> PathType.OPEN;
                case DAMAGE_FIRE -> PathType.DAMAGE_FIRE;
                case DAMAGE_OTHER -> PathType.DAMAGE_OTHER;
                case STICKY_HONEY -> PathType.STICKY_HONEY;
                case POWDER_SNOW -> PathType.DANGER_POWDER_SNOW;
                case DAMAGE_CAUTIOUS -> PathType.DAMAGE_CAUTIOUS;
                case TRAPDOOR -> PathType.DANGER_TRAPDOOR;
                default -> checkNeighbourBlocks(context, x, y, z, PathType.WALKABLE);
            };
        } else {
            return pathTypeFromState;
        }
    }

    public static PathType checkNeighbourBlocks(PathfindingContext context, int x, int y, int z, PathType pathType) {
        for (int i = -1; i <= 1; i++) {
            for (int i1 = -1; i1 <= 1; i1++) {
                for (int i2 = -1; i2 <= 1; i2++) {
                    if (i != 0 || i2 != 0) {
                        PathType pathTypeFromState = context.getPathTypeFromState(x + i, y + i1, z + i2);
                        if (pathTypeFromState == PathType.DAMAGE_OTHER) {
                            return PathType.DANGER_OTHER;
                        }

                        if (pathTypeFromState == PathType.DAMAGE_FIRE || pathTypeFromState == PathType.LAVA) {
                            return PathType.DANGER_FIRE;
                        }

                        if (pathTypeFromState == PathType.WATER) {
                            return PathType.WATER_BORDER;
                        }

                        if (pathTypeFromState == PathType.DAMAGE_CAUTIOUS) {
                            return PathType.DAMAGE_CAUTIOUS;
                        }
                    }
                }
            }
        }

        return pathType;
    }

    protected static PathType getPathTypeFromState(BlockGetter level, BlockPos pos) {
        // Paper start - Do not load chunks during pathfinding
        BlockState blockState = level.getBlockStateIfLoaded(pos);
        if (blockState == null) {
            return PathType.BLOCKED;
        }
        // Paper end
        Block block = blockState.getBlock();
        if (blockState.isAir()) {
            return PathType.OPEN;
        } else if (blockState.is(BlockTags.TRAPDOORS) || blockState.is(Blocks.LILY_PAD) || blockState.is(Blocks.BIG_DRIPLEAF)) {
            return PathType.TRAPDOOR;
        } else if (blockState.is(Blocks.POWDER_SNOW)) {
            return PathType.POWDER_SNOW;
        } else if (blockState.is(Blocks.CACTUS) || blockState.is(Blocks.SWEET_BERRY_BUSH)) {
            return PathType.DAMAGE_OTHER;
        } else if (blockState.is(Blocks.HONEY_BLOCK)) {
            return PathType.STICKY_HONEY;
        } else if (blockState.is(Blocks.COCOA)) {
            return PathType.COCOA;
        } else if (!blockState.is(Blocks.WITHER_ROSE) && !blockState.is(Blocks.POINTED_DRIPSTONE)) {
            FluidState fluidState = blockState.getFluidState();
            if (fluidState.is(FluidTags.LAVA)) {
                return PathType.LAVA;
            } else if (isBurningBlock(blockState)) {
                return PathType.DAMAGE_FIRE;
            } else if (block instanceof DoorBlock doorBlock) {
                if (blockState.getValue(DoorBlock.OPEN)) {
                    return PathType.DOOR_OPEN;
                } else {
                    return doorBlock.type().canOpenByHand() ? PathType.DOOR_WOOD_CLOSED : PathType.DOOR_IRON_CLOSED;
                }
            } else if (block instanceof BaseRailBlock) {
                return PathType.RAIL;
            } else if (block instanceof LeavesBlock) {
                return PathType.LEAVES;
            } else if (!blockState.is(BlockTags.FENCES)
                && !blockState.is(BlockTags.WALLS)
                && (!(block instanceof FenceGateBlock) || blockState.getValue(FenceGateBlock.OPEN))) {
                if (!blockState.isPathfindable(PathComputationType.LAND)) {
                    return PathType.BLOCKED;
                } else {
                    return fluidState.is(FluidTags.WATER) ? PathType.WATER : PathType.OPEN;
                }
            } else {
                return PathType.FENCE;
            }
        } else {
            return PathType.DAMAGE_CAUTIOUS;
        }
    }
}
