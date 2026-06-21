package fun.bm.mili.scheduler;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.core.Direction;
import org.slf4j.Logger;

public final class ChunkBorderCache {

    private static final Logger LOGGER = LogUtils.getLogger();

    public enum BorderFace {
        NORTH(Direction.NORTH),
        EAST(Direction.EAST),
        SOUTH(Direction.SOUTH),
        WEST(Direction.WEST);

        final Direction direction;
        BorderFace(Direction d) { this.direction = d; }

        public Direction getDirection() { return direction; }

        public BorderFace opposite() {
            return switch (this) {
                case NORTH -> SOUTH; case SOUTH -> NORTH;
                case EAST  -> WEST;  case WEST  -> EAST;
            };
        }
        public static BorderFace fromDirection(Direction dir) {
            return switch (dir) {
                case NORTH -> NORTH; case SOUTH -> SOUTH;
                case EAST  -> EAST;  case WEST  -> WEST;
                default -> throw new IllegalArgumentException("Non-horizontal: " + dir);
            };
        }
    }

    public static final class BorderColumn {
        private final int redstonePower;
        private final boolean hasFluid;
        private final boolean hasPiston;
        private final boolean hasRepeater;
        private final boolean hasComparator;
        private final boolean hasRedstoneWire;

        BorderColumn(int redstonePower, boolean hasFluid, boolean hasPiston,
                     boolean hasRepeater, boolean hasComparator, boolean hasRedstoneWire) {
            this.redstonePower = redstonePower;
            this.hasFluid = hasFluid;
            this.hasPiston = hasPiston;
            this.hasRepeater = hasRepeater;
            this.hasComparator = hasComparator;
            this.hasRedstoneWire = hasRedstoneWire;
        }

        public int redstonePower() { return redstonePower; }
        public boolean hasFluid() { return hasFluid; }
        public boolean hasPiston() { return hasPiston; }
        public boolean hasRepeater() { return hasRepeater; }
        public boolean hasComparator() { return hasComparator; }
        public boolean hasRedstoneWire() { return hasRedstoneWire; }
        public boolean isHighInteraction() {
            return redstonePower > 0 || hasFluid || hasPiston;
        }
    }

    private final int chunkX;
    private final int chunkZ;
    private final BorderColumn[][] faces; // [face][edgeIndex]
    private volatile boolean dirty;
    private volatile boolean highInteraction;

    public ChunkBorderCache(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.faces = new BorderColumn[4][16];
    }

    /** Phase 1: capture border state BEFORE chunk tick */
    public void captureBorderState(LevelChunk chunk) {
        Level level = chunk.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) return;

        int minY = level.getMinY();
        int maxY = level.getMaxY();
        boolean anyHigh = false;

        for (BorderFace face : BorderFace.values()) {
            int fi = face.ordinal();
            for (int i = 0; i < 16; i++) {
                int bx, bz;
                switch (face) {
                    case EAST  -> { bx = chunkX * 16 + 15; bz = chunkZ * 16 + i; }
                    case WEST  -> { bx = chunkX * 16;      bz = chunkZ * 16 + i; }
                    case SOUTH -> { bx = chunkX * 16 + i;  bz = chunkZ * 16 + 15; }
                    case NORTH -> { bx = chunkX * 16 + i;  bz = chunkZ * 16;      }
                    default    -> throw new AssertionError(face);
                }

                int redstonePower = 0;
                boolean hasFluid = false;
                boolean hasPiston = false;
                boolean hasRepeater = false;
                boolean hasComparator = false;
                boolean hasRedstoneWire = false;

                for (int y = minY; y <= maxY; y++) {
                    BlockPos pos = new BlockPos(bx, y, bz);
                    BlockState state = level.getBlockState(pos);
                    Block block = state.getBlock();

                    // Redstone wire
                    if (block instanceof RedStoneWireBlock) {
                        hasRedstoneWire = true;
                        int pow = state.getValue(RedStoneWireBlock.POWER);
                        if (pow > redstonePower) redstonePower = pow;
                    }
                    // Repeater
                    if (block instanceof RepeaterBlock) {
                        hasRepeater = true;
                        if (state.getValue(RepeaterBlock.POWERED)) {
                            if (15 > redstonePower) redstonePower = 15;
                        }
                    }
                    // Comparator
                    if (block instanceof ComparatorBlock) {
                        hasComparator = true;
                        int pow = state.getValue(BlockStateProperties.POWER);
                        if (pow > redstonePower) redstonePower = pow;
                    }
                    // Redstone block / torch
                    if (block == Blocks.REDSTONE_BLOCK || block instanceof RedstoneTorchBlock) {
                        if (15 > redstonePower) redstonePower = 15;
                    }
                    // Generic signal
                    int signal = state.getSignal(level, pos, face.direction);
                    if (signal > redstonePower) redstonePower = signal;

                    // Fluid
                    FluidState fluid = state.getFluidState();
                    if (!fluid.isEmpty()) hasFluid = true;

                    // Piston
                    if (block == Blocks.PISTON || block == Blocks.STICKY_PISTON || block == Blocks.PISTON_HEAD) {
                        hasPiston = true;
                    }
                }

                faces[fi][i] = new BorderColumn(redstonePower, hasFluid, hasPiston,
                    hasRepeater, hasComparator, hasRedstoneWire);
                if (redstonePower > 0 || hasFluid || hasPiston) anyHigh = true;
            }
        }
        this.highInteraction = anyHigh;
        this.dirty = false;
    }

    public boolean isHighInteraction() { return highInteraction; }
    public boolean isReady() { return !dirty; }
    public void markDirty() { this.dirty = true; this.highInteraction = false; }
    public int getChunkX() { return chunkX; }
    public int getChunkZ() { return chunkZ; }
}
