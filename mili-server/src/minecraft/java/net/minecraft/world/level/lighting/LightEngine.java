package net.minecraft.world.level.lighting;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public abstract class LightEngine<M extends DataLayerStorageMap<M>, S extends LayerLightSectionStorage<M>> implements LayerLightEventListener {
    public static final int MAX_LEVEL = 15;
    protected static final int MIN_OPACITY = 1;
    protected static final long PULL_LIGHT_IN_ENTRY = LightEngine.QueueEntry.decreaseAllDirections(1);
    private static final int MIN_QUEUE_SIZE = 512;
    protected static final Direction[] PROPAGATION_DIRECTIONS = Direction.values();
    protected final LightChunkGetter chunkSource;
    protected final S storage;
    private final LongOpenHashSet blockNodesToCheck = new LongOpenHashSet(512, 0.5F);
    private final LongArrayFIFOQueue decreaseQueue = new LongArrayFIFOQueue();
    private final LongArrayFIFOQueue increaseQueue = new LongArrayFIFOQueue();
    private static final int CACHE_SIZE = 2;
    private final long[] lastChunkPos = new long[2];
    private final LightChunk[] lastChunk = new LightChunk[2];

    protected LightEngine(LightChunkGetter chunkSource, S storage) {
        this.chunkSource = chunkSource;
        this.storage = storage;
        this.clearChunkCache();
    }

    public static boolean hasDifferentLightProperties(BlockState state1, BlockState state2) {
        return state2 != state1
            && (
                state2.getLightBlock() != state1.getLightBlock()
                    || state2.getLightEmission() != state1.getLightEmission()
                    || state2.useShapeForLightOcclusion()
                    || state1.useShapeForLightOcclusion()
            );
    }

    public static int getLightBlockInto(BlockState state1, BlockState state2, Direction direction, int defaultReturnValue) {
        boolean isEmptyShape = isEmptyShape(state1);
        boolean isEmptyShape1 = isEmptyShape(state2);
        if (isEmptyShape && isEmptyShape1) {
            return defaultReturnValue;
        } else {
            VoxelShape voxelShape = isEmptyShape ? Shapes.empty() : state1.getOcclusionShape();
            VoxelShape voxelShape1 = isEmptyShape1 ? Shapes.empty() : state2.getOcclusionShape();
            return Shapes.mergedFaceOccludes(voxelShape, voxelShape1, direction) ? 16 : defaultReturnValue;
        }
    }

    public static VoxelShape getOcclusionShape(BlockState state, Direction direction) {
        return isEmptyShape(state) ? Shapes.empty() : state.getFaceOcclusionShape(direction);
    }

    protected static boolean isEmptyShape(BlockState state) {
        return !state.canOcclude() || !state.useShapeForLightOcclusion();
    }

    protected BlockState getState(BlockPos pos) {
        int sectionPosX = SectionPos.blockToSectionCoord(pos.getX());
        int sectionPosZ = SectionPos.blockToSectionCoord(pos.getZ());
        LightChunk chunk = this.getChunk(sectionPosX, sectionPosZ);
        return chunk == null ? Blocks.BEDROCK.defaultBlockState() : chunk.getBlockState(pos);
    }

    protected int getOpacity(BlockState state) {
        return Math.max(1, state.getLightBlock());
    }

    protected boolean shapeOccludes(BlockState state1, BlockState state2, Direction direction) {
        VoxelShape occlusionShape = getOcclusionShape(state1, direction);
        VoxelShape occlusionShape1 = getOcclusionShape(state2, direction.getOpposite());
        return Shapes.faceShapeOccludes(occlusionShape, occlusionShape1);
    }

    protected @Nullable LightChunk getChunk(int x, int z) {
        long packedChunkPos = ChunkPos.asLong(x, z);

        for (int i = 0; i < 2; i++) {
            if (packedChunkPos == this.lastChunkPos[i]) {
                return this.lastChunk[i];
            }
        }

        LightChunk chunkForLighting = this.chunkSource.getChunkForLighting(x, z);

        for (int i1 = 1; i1 > 0; i1--) {
            this.lastChunkPos[i1] = this.lastChunkPos[i1 - 1];
            this.lastChunk[i1] = this.lastChunk[i1 - 1];
        }

        this.lastChunkPos[0] = packedChunkPos;
        this.lastChunk[0] = chunkForLighting;
        return chunkForLighting;
    }

    private void clearChunkCache() {
        Arrays.fill(this.lastChunkPos, ChunkPos.INVALID_CHUNK_POS);
        Arrays.fill(this.lastChunk, null);
    }

    @Override
    public void checkBlock(BlockPos pos) {
        this.blockNodesToCheck.add(pos.asLong());
    }

    public void queueSectionData(long sectionPos, @Nullable DataLayer data) {
        this.storage.queueSectionData(sectionPos, data);
    }

    public void retainData(ChunkPos chunkPos, boolean retainData) {
        this.storage.retainData(SectionPos.getZeroNode(chunkPos.x, chunkPos.z), retainData);
    }

    @Override
    public void updateSectionStatus(SectionPos pos, boolean isQueueEmpty) {
        this.storage.updateSectionStatus(pos.asLong(), isQueueEmpty);
    }

    @Override
    public void setLightEnabled(ChunkPos chunkPos, boolean lightEnabled) {
        this.storage.setLightEnabled(SectionPos.getZeroNode(chunkPos.x, chunkPos.z), lightEnabled);
    }

    @Override
    public int runLightUpdates() {
        LongIterator longIterator = this.blockNodesToCheck.iterator();

        while (longIterator.hasNext()) {
            this.checkNode(longIterator.nextLong());
        }

        this.blockNodesToCheck.clear();
        this.blockNodesToCheck.trim(512);
        int i = 0;
        i += this.propagateDecreases();
        i += this.propagateIncreases();
        this.clearChunkCache();
        this.storage.markNewInconsistencies(this);
        this.storage.swapSectionMap();
        return i;
    }

    private int propagateIncreases() {
        int i;
        for (i = 0; !this.increaseQueue.isEmpty(); i++) {
            long l = this.increaseQueue.dequeueLong();
            long l1 = this.increaseQueue.dequeueLong();
            int storedLevel = this.storage.getStoredLevel(l);
            int fromLevel = LightEngine.QueueEntry.getFromLevel(l1);
            if (LightEngine.QueueEntry.isIncreaseFromEmission(l1) && storedLevel < fromLevel) {
                this.storage.setStoredLevel(l, fromLevel);
                storedLevel = fromLevel;
            }

            if (storedLevel == fromLevel) {
                this.propagateIncrease(l, l1, storedLevel);
            }
        }

        return i;
    }

    private int propagateDecreases() {
        int i;
        for (i = 0; !this.decreaseQueue.isEmpty(); i++) {
            long l = this.decreaseQueue.dequeueLong();
            long l1 = this.decreaseQueue.dequeueLong();
            this.propagateDecrease(l, l1);
        }

        return i;
    }

    protected void enqueueDecrease(long packedPos1, long packedPos2) {
        this.decreaseQueue.enqueue(packedPos1);
        this.decreaseQueue.enqueue(packedPos2);
    }

    protected void enqueueIncrease(long packedPos1, long packedPos2) {
        this.increaseQueue.enqueue(packedPos1);
        this.increaseQueue.enqueue(packedPos2);
    }

    @Override
    public boolean hasLightWork() {
        return this.storage.hasInconsistencies() || !this.blockNodesToCheck.isEmpty() || !this.decreaseQueue.isEmpty() || !this.increaseQueue.isEmpty();
    }

    @Override
    public @Nullable DataLayer getDataLayerData(SectionPos sectionPos) {
        return this.storage.getDataLayerData(sectionPos.asLong());
    }

    @Override
    public int getLightValue(BlockPos pos) {
        return this.storage.getLightValue(pos.asLong());
    }

    public String getDebugData(long sectionPos) {
        return this.getDebugSectionType(sectionPos).display();
    }

    public LayerLightSectionStorage.SectionType getDebugSectionType(long sectionPos) {
        return this.storage.getDebugSectionType(sectionPos);
    }

    protected abstract void checkNode(long packedPos);

    protected abstract void propagateIncrease(long packedPos, long queueEntry, int lightLevel);

    protected abstract void propagateDecrease(long packedPos, long lightLevel);

    public static class QueueEntry {
        private static final int FROM_LEVEL_BITS = 4;
        private static final int DIRECTION_BITS = 6;
        private static final long LEVEL_MASK = 15L;
        private static final long DIRECTIONS_MASK = 1008L;
        private static final long FLAG_FROM_EMPTY_SHAPE = 1024L;
        private static final long FLAG_INCREASE_FROM_EMISSION = 2048L;

        public static long decreaseSkipOneDirection(int level, Direction direction) {
            long l = withoutDirection(1008L, direction);
            return withLevel(l, level);
        }

        public static long decreaseAllDirections(int level) {
            return withLevel(1008L, level);
        }

        public static long increaseLightFromEmission(int level, boolean fromEmptyShape) {
            long l = 1008L;
            l |= 2048L;
            if (fromEmptyShape) {
                l |= 1024L;
            }

            return withLevel(l, level);
        }

        public static long increaseSkipOneDirection(int level, boolean fromEmptyShape, Direction direction) {
            long l = withoutDirection(1008L, direction);
            if (fromEmptyShape) {
                l |= 1024L;
            }

            return withLevel(l, level);
        }

        public static long increaseOnlyOneDirection(int level, boolean fromEmptyShape, Direction direction) {
            long l = 0L;
            if (fromEmptyShape) {
                l |= 1024L;
            }

            l = withDirection(l, direction);
            return withLevel(l, level);
        }

        public static long increaseSkySourceInDirections(boolean down, boolean north, boolean south, boolean west, boolean east) {
            long l = withLevel(0L, 15);
            if (down) {
                l = withDirection(l, Direction.DOWN);
            }

            if (north) {
                l = withDirection(l, Direction.NORTH);
            }

            if (south) {
                l = withDirection(l, Direction.SOUTH);
            }

            if (west) {
                l = withDirection(l, Direction.WEST);
            }

            if (east) {
                l = withDirection(l, Direction.EAST);
            }

            return l;
        }

        public static int getFromLevel(long entry) {
            return (int)(entry & 15L);
        }

        public static boolean isFromEmptyShape(long entry) {
            return (entry & 1024L) != 0L;
        }

        public static boolean isIncreaseFromEmission(long entry) {
            return (entry & 2048L) != 0L;
        }

        public static boolean shouldPropagateInDirection(long entry, Direction direction) {
            return (entry & 1L << direction.ordinal() + 4) != 0L;
        }

        private static long withLevel(long entry, int level) {
            return entry & -16L | level & 15L;
        }

        private static long withDirection(long entry, Direction direction) {
            return entry | 1L << direction.ordinal() + 4;
        }

        private static long withoutDirection(long entry, Direction direction) {
            return entry & ~(1L << direction.ordinal() + 4);
        }
    }
}
