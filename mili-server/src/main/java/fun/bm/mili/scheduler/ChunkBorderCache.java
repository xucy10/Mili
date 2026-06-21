package fun.bm.mili.scheduler;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * 区块边界状态缓存 / Chunk border state cache.
 *
 * <p>捕获区块四面的边界方块状态，用于跨区块红石/流体交互检测 /
 * Captures border block states on all 4 faces for cross-chunk redstone/fluid interaction.
 *
 * <p>性能优化 / Performance optimizations:
 * <ul>
 *   <li>使用 {@link BlockPos.MutableBlockPos} 避免每次循环分配 BlockPos /
 *       Uses MutableBlockPos to avoid per-iteration allocation</li>
 *   <li>遇到高交互方块提前跳出内层循环 / Early exit on high-interaction blocks</li>
 *   <li>跳过空气方块减少不必要的状态查询 / Skips air blocks to reduce state queries</li>
 * </ul>
 */
public final class ChunkBorderCache {

    public enum BorderFace {
        NORTH(Direction.NORTH),
        EAST(Direction.EAST),
        SOUTH(Direction.SOUTH),
        WEST(Direction.WEST);

        private final Direction direction;

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

    /**
     * 边界列状态快照 / Border column state snapshot.
     * 不可变记录 / Immutable record.
     */
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
    private final BorderColumn[][] faces;
    private volatile boolean dirty;
    private volatile boolean highInteraction;

    public ChunkBorderCache(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.faces = new BorderColumn[4][16];
    }

    /**
     * 捕获区块边界状态 / Capture border state of the chunk.
     *
     * <p>使用 {@link BlockPos.MutableBlockPos} 避免分配，遇到高交互方块提前跳出 /
     * Uses MutableBlockPos to avoid allocation, early exits on high-interaction blocks.
     */
    public void captureBorderState(LevelChunk chunk) {
        Level level = chunk.getLevel();
        if (!(level instanceof ServerLevel)) return;

        int minY = level.getMinY();
        int maxY = level.getMaxY();
        boolean anyHigh = false;

        // 复用 MutableBlockPos 避免每次循环分配 / Reuse MutableBlockPos to avoid per-iteration allocation
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

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
                    mutablePos.set(bx, y, bz);
                    BlockState state = level.getBlockState(mutablePos);
                    Block block = state.getBlock();

                    // 跳过空气方块减少状态查询 / Skip air blocks to reduce state queries
                    if (block == Blocks.AIR || block == Blocks.CAVE_AIR) continue;

                    if (block instanceof RedStoneWireBlock) {
                        hasRedstoneWire = true;
                        int pow = state.getValue(RedStoneWireBlock.POWER);
                        if (pow > redstonePower) redstonePower = pow;
                    } else if (block instanceof RepeaterBlock) {
                        hasRepeater = true;
                        if (state.getValue(RepeaterBlock.POWERED)) {
                            redstonePower = 15;
                        }
                    } else if (block instanceof ComparatorBlock) {
                        hasComparator = true;
                        int pow = state.getValue(BlockStateProperties.POWER);
                        if (pow > redstonePower) redstonePower = pow;
                    } else if (block == Blocks.REDSTONE_BLOCK || block instanceof RedstoneTorchBlock) {
                        redstonePower = 15;
                    }

                    int signal = state.getSignal(level, mutablePos, face.direction);
                    if (signal > redstonePower) redstonePower = signal;

                    if (!state.getFluidState().isEmpty()) hasFluid = true;

                    if (block == Blocks.PISTON || block == Blocks.STICKY_PISTON || block == Blocks.PISTON_HEAD) {
                        hasPiston = true;
                    }

                    // 红石信号满或检测到活塞+流体组合时提前跳出 / Early exit when signal maxed or piston+fluid combo
                    if (redstonePower >= 15 && hasPiston && hasFluid) break;
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
    public void markDirty() { this.dirty = true; this.highInteraction = false; }
    public int getChunkX() { return chunkX; }
    public int getChunkZ() { return chunkZ; }
}
