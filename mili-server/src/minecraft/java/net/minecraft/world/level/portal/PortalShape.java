package net.minecraft.world.level.portal;

import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.BlockUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.apache.commons.lang3.mutable.MutableInt;
import org.jspecify.annotations.Nullable;

public class PortalShape {
    private static final int MIN_WIDTH = 2;
    public static final int MAX_WIDTH = 21;
    private static final int MIN_HEIGHT = 3;
    public static final int MAX_HEIGHT = 21;
    private static final BlockBehaviour.StatePredicate FRAME = (state, level, pos) -> state.is(Blocks.OBSIDIAN);
    private static final float SAFE_TRAVEL_MAX_ENTITY_XY = 4.0F;
    private static final double SAFE_TRAVEL_MAX_VERTICAL_DELTA = 1.0;
    private final Direction.Axis axis;
    private final Direction rightDir;
    private final int numPortalBlocks;
    private final BlockPos bottomLeft;
    private final int height;
    private final int width;
    // CraftBukkit start - add field
    private final org.bukkit.craftbukkit.util.BlockStateListPopulator blocks;

    private PortalShape(Direction.Axis axis, int numPortalBlocks, Direction rightDir, BlockPos bottomLeft, int width, int height, org.bukkit.craftbukkit.util.BlockStateListPopulator blocks) {
        this.blocks = blocks;
        // CraftBukkit end
        this.axis = axis;
        this.numPortalBlocks = numPortalBlocks;
        this.rightDir = rightDir;
        this.bottomLeft = bottomLeft;
        this.width = width;
        this.height = height;
    }

    public static Optional<PortalShape> findEmptyPortalShape(LevelAccessor level, BlockPos bottomLeft, Direction.Axis axis) {
        return findPortalShape(level, bottomLeft, shape -> shape.isValid() && shape.numPortalBlocks == 0, axis);
    }

    public static Optional<PortalShape> findPortalShape(LevelAccessor level, BlockPos bottomLeft, Predicate<PortalShape> predicate, Direction.Axis axis) {
        Optional<PortalShape> optional = Optional.of(findAnyShape(level, bottomLeft, axis)).filter(predicate);
        if (optional.isPresent()) {
            return optional;
        } else {
            Direction.Axis axis1 = axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
            return Optional.of(findAnyShape(level, bottomLeft, axis1)).filter(predicate);
        }
    }

    public static PortalShape findAnyShape(BlockGetter level, BlockPos bottomLeft, Direction.Axis axis) {
        org.bukkit.craftbukkit.util.BlockStateListPopulator blocks = new org.bukkit.craftbukkit.util.BlockStateListPopulator(((LevelAccessor) level).getMinecraftWorld()); // CraftBukkit
        Direction direction = axis == Direction.Axis.X ? Direction.WEST : Direction.SOUTH;
        BlockPos blockPos = calculateBottomLeft(level, direction, bottomLeft, blocks); // CraftBukkit
        if (blockPos == null) {
            return new PortalShape(axis, 0, direction, bottomLeft, 0, 0, blocks); // CraftBukkit
        } else {
            int i = calculateWidth(level, blockPos, direction, blocks); // CraftBukkit
            if (i == 0) {
                return new PortalShape(axis, 0, direction, blockPos, 0, 0, blocks); // CraftBukkit
            } else {
                MutableInt mutableInt = new MutableInt();
                int i1 = calculateHeight(level, blockPos, direction, i, mutableInt, blocks); // CraftBukkit
                return new PortalShape(axis, mutableInt.intValue(), direction, blockPos, i, i1, blocks); // CraftBukkit
            }
        }
    }

    private static @Nullable BlockPos calculateBottomLeft(BlockGetter level, Direction direction, BlockPos pos, org.bukkit.craftbukkit.util.BlockStateListPopulator blocks) { // CraftBukkit
        int max = Math.max(level.getMinY(), pos.getY() - 21);

        while (pos.getY() > max && isEmpty(level.getBlockState(pos.below()))) {
            pos = pos.below();
        }

        Direction opposite = direction.getOpposite();
        int i = getDistanceUntilEdgeAboveFrame(level, pos, opposite, blocks) - 1; // CraftBukkit
        return i < 0 ? null : pos.relative(opposite, i);
    }

    private static int calculateWidth(BlockGetter level, BlockPos bottomLeft, Direction direction, org.bukkit.craftbukkit.util.BlockStateListPopulator blocks) { // CraftBukkit
        int distanceUntilEdgeAboveFrame = getDistanceUntilEdgeAboveFrame(level, bottomLeft, direction, blocks); // CraftBukkit
        return distanceUntilEdgeAboveFrame >= 2 && distanceUntilEdgeAboveFrame <= 21 ? distanceUntilEdgeAboveFrame : 0;
    }

    private static int getDistanceUntilEdgeAboveFrame(BlockGetter level, BlockPos pos, Direction direction, org.bukkit.craftbukkit.util.BlockStateListPopulator blocks) { // CraftBukkit
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        for (int i = 0; i <= 21; i++) {
            mutableBlockPos.set(pos).move(direction, i);
            BlockState blockState = level.getBlockState(mutableBlockPos);
            if (!isEmpty(blockState)) {
                if (FRAME.test(blockState, level, mutableBlockPos)) {
                    blocks.setBlock(mutableBlockPos, blockState, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE); // CraftBukkit - lower left / right
                    return i;
                }
                break;
            }

            BlockState blockState1 = level.getBlockState(mutableBlockPos.move(Direction.DOWN));
            if (!FRAME.test(blockState1, level, mutableBlockPos)) {
                break;
            }
            blocks.setBlock(mutableBlockPos, blockState1, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE); // CraftBukkit - bottom row
        }

        return 0;
    }

    private static int calculateHeight(BlockGetter level, BlockPos pos, Direction direction, int width, MutableInt portalBlocks, org.bukkit.craftbukkit.util.BlockStateListPopulator blocks) { // CraftBukkit
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        int distanceUntilTop = getDistanceUntilTop(level, pos, direction, mutableBlockPos, width, portalBlocks, blocks); // CraftBukkit
        return distanceUntilTop >= 3 && distanceUntilTop <= 21 && hasTopFrame(level, pos, direction, mutableBlockPos, width, distanceUntilTop, blocks) // CraftBukkit
            ? distanceUntilTop
            : 0;
    }

    private static boolean hasTopFrame(BlockGetter level, BlockPos pos, Direction direction, BlockPos.MutableBlockPos checkPos, int width, int distanceUntilTop, org.bukkit.craftbukkit.util.BlockStateListPopulator blocks) { // CraftBukkit
        for (int i = 0; i < width; i++) {
            BlockPos.MutableBlockPos mutableBlockPos = checkPos.set(pos).move(Direction.UP, distanceUntilTop).move(direction, i);
            if (!FRAME.test(level.getBlockState(mutableBlockPos), level, mutableBlockPos)) {
                return false;
            }
            blocks.setBlock(mutableBlockPos, level.getBlockState(mutableBlockPos), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE); // CraftBukkit - upper row
        }

        return true;
    }

    private static int getDistanceUntilTop(
        BlockGetter level, BlockPos pos, Direction direction, BlockPos.MutableBlockPos checkPos, int width, MutableInt portalBlocks, org.bukkit.craftbukkit.util.BlockStateListPopulator blocks // CraftBukkit
    ) {
        for (int i = 0; i < 21; i++) {
            checkPos.set(pos).move(Direction.UP, i).move(direction, -1);
            if (!FRAME.test(level.getBlockState(checkPos), level, checkPos)) {
                return i;
            }

            checkPos.set(pos).move(Direction.UP, i).move(direction, width);
            if (!FRAME.test(level.getBlockState(checkPos), level, checkPos)) {
                return i;
            }

            for (int i1 = 0; i1 < width; i1++) {
                checkPos.set(pos).move(Direction.UP, i).move(direction, i1);
                BlockState blockState = level.getBlockState(checkPos);
                if (!isEmpty(blockState)) {
                    return i;
                }

                if (blockState.is(Blocks.NETHER_PORTAL)) {
                    portalBlocks.increment();
                }
            }
            // CraftBukkit start - left and right
            blocks.setBlock(checkPos.set(pos).move(Direction.UP, i).move(direction, -1), level.getBlockState(checkPos), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            blocks.setBlock(checkPos.set(pos).move(Direction.UP, i).move(direction, width), level.getBlockState(checkPos), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            // CraftBukkit end
        }

        return 21;
    }

    private static boolean isEmpty(BlockState state) {
        return state.isAir() || state.is(BlockTags.FIRE) || state.is(Blocks.NETHER_PORTAL);
    }

    public boolean isValid() {
        return this.width >= 2 && this.width <= 21 && this.height >= 3 && this.height <= 21;
    }

    // CraftBukkit start - return boolean, add entity
    public boolean createPortalBlocks(LevelAccessor level, Entity entity) {
        org.bukkit.World bworld = level.getMinecraftWorld().getWorld();
        // Copy below for loop
        BlockState blockState = Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, this.axis);
        BlockPos.betweenClosed(this.bottomLeft, this.bottomLeft.relative(Direction.UP, this.height - 1).relative(this.rightDir, this.width - 1))
            .forEach(pos -> this.blocks.setBlock(pos, blockState, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE));
        org.bukkit.event.world.PortalCreateEvent event = new org.bukkit.event.world.PortalCreateEvent((java.util.List<org.bukkit.block.BlockState>) (java.util.List) this.blocks.getSnapshotBlocks(), bworld, (entity == null) ? null : entity.getBukkitEntity(), org.bukkit.event.world.PortalCreateEvent.CreateReason.FIRE);
        level.getMinecraftWorld().getServer().server.getPluginManager().callEvent(event); // todo the list is not really mutable here unlike other call and the portal frame is included

        if (event.isCancelled()) {
            return false;
        }
        // CraftBukkit end
        BlockPos.betweenClosed(this.bottomLeft, this.bottomLeft.relative(Direction.UP, this.height - 1).relative(this.rightDir, this.width - 1))
            .forEach(pos -> level.setBlock(pos, blockState, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE));
        return true; // CraftBukkit
    }

    public boolean isComplete() {
        return this.isValid() && this.numPortalBlocks == this.width * this.height;
    }

    public static Vec3 getRelativePosition(BlockUtil.FoundRectangle foundRectangle, Direction.Axis axis, Vec3 pos, EntityDimensions entityDimensions) {
        double d = (double)foundRectangle.axis1Size - entityDimensions.width();
        double d1 = (double)foundRectangle.axis2Size - entityDimensions.height();
        BlockPos blockPos = foundRectangle.minCorner;
        double d3;
        if (d > 0.0) {
            double d2 = blockPos.get(axis) + entityDimensions.width() / 2.0;
            d3 = Mth.clamp(Mth.inverseLerp(pos.get(axis) - d2, 0.0, d), 0.0, 1.0);
        } else {
            d3 = 0.5;
        }

        double d2;
        if (d1 > 0.0) {
            Direction.Axis axis1 = Direction.Axis.Y;
            d2 = Mth.clamp(Mth.inverseLerp(pos.get(axis1) - blockPos.get(axis1), 0.0, d1), 0.0, 1.0);
        } else {
            d2 = 0.0;
        }

        Direction.Axis axis1 = axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
        double d4 = pos.get(axis1) - (blockPos.get(axis1) + 0.5);
        return new Vec3(d3, d2, d4);
    }

    public static Vec3 findCollisionFreePosition(Vec3 pos, ServerLevel level, Entity entity, EntityDimensions dimensions) {
        if (!(dimensions.width() > 4.0F) && !(dimensions.height() > 4.0F)) {
            double d = dimensions.height() / 2.0;
            Vec3 vec3 = pos.add(0.0, d, 0.0);
            VoxelShape voxelShape = Shapes.create(AABB.ofSize(vec3, dimensions.width(), 0.0, dimensions.width()).expandTowards(0.0, 1.0, 0.0).inflate(1.0E-6));
            Optional<Vec3> optional = level.findFreePosition(entity, voxelShape, vec3, dimensions.width(), dimensions.height(), dimensions.width());
            Optional<Vec3> optional1 = optional.map(freePos -> freePos.subtract(0.0, d, 0.0));
            return optional1.orElse(pos);
        } else {
            return pos;
        }
    }
}
