package net.minecraft.world.level.lighting;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.BitStorage;
import net.minecraft.util.Mth;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ChunkSkyLightSources {
    private static final int SIZE = 16;
    public static final int NEGATIVE_INFINITY = Integer.MIN_VALUE;
    private final int minY;
    private final BitStorage heightmap;
    private final BlockPos.MutableBlockPos mutablePos1 = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos mutablePos2 = new BlockPos.MutableBlockPos();

    public ChunkSkyLightSources(LevelHeightAccessor level) {
        this.minY = level.getMinY() - 1;
        int i = level.getMaxY() + 1;
        int i1 = Mth.ceillog2(i - this.minY + 1);
        this.heightmap = new SimpleBitStorage(i1, 256);
    }

    public void fillFrom(ChunkAccess chunk) {
        int highestFilledSectionIndex = chunk.getHighestFilledSectionIndex();
        if (highestFilledSectionIndex == -1) {
            this.fill(this.minY);
        } else {
            for (int i = 0; i < 16; i++) {
                for (int i1 = 0; i1 < 16; i1++) {
                    int max = Math.max(this.findLowestSourceY(chunk, highestFilledSectionIndex, i1, i), this.minY);
                    this.set(index(i1, i), max);
                }
            }
        }
    }

    private int findLowestSourceY(ChunkAccess chunk, int sectionIndex, int x, int z) {
        int blockPosCoord = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(sectionIndex) + 1);
        BlockPos.MutableBlockPos mutableBlockPos = this.mutablePos1.set(x, blockPosCoord, z);
        BlockPos.MutableBlockPos mutableBlockPos1 = this.mutablePos2.setWithOffset(mutableBlockPos, Direction.DOWN);
        BlockState blockState = Blocks.AIR.defaultBlockState();

        for (int i = sectionIndex; i >= 0; i--) {
            LevelChunkSection section = chunk.getSection(i);
            if (section.hasOnlyAir()) {
                blockState = Blocks.AIR.defaultBlockState();
                int sectionYFromSectionIndex = chunk.getSectionYFromSectionIndex(i);
                mutableBlockPos.setY(SectionPos.sectionToBlockCoord(sectionYFromSectionIndex));
                mutableBlockPos1.setY(mutableBlockPos.getY() - 1);
            } else {
                for (int sectionYFromSectionIndex = 15; sectionYFromSectionIndex >= 0; sectionYFromSectionIndex--) {
                    BlockState blockState1 = section.getBlockState(x, sectionYFromSectionIndex, z);
                    if (isEdgeOccluded(blockState, blockState1)) {
                        return mutableBlockPos.getY();
                    }

                    blockState = blockState1;
                    mutableBlockPos.set(mutableBlockPos1);
                    mutableBlockPos1.move(Direction.DOWN);
                }
            }
        }

        return this.minY;
    }

    public boolean update(BlockGetter level, int x, int y, int z) {
        int i = y + 1;
        int i1 = index(x, z);
        int i2 = this.get(i1);
        if (i < i2) {
            return false;
        } else {
            BlockPos blockPos = this.mutablePos1.set(x, y + 1, z);
            BlockState blockState = level.getBlockState(blockPos);
            BlockPos blockPos1 = this.mutablePos2.set(x, y, z);
            BlockState blockState1 = level.getBlockState(blockPos1);
            if (this.updateEdge(level, i1, i2, blockPos, blockState, blockPos1, blockState1)) {
                return true;
            } else {
                BlockPos blockPos2 = this.mutablePos1.set(x, y - 1, z);
                BlockState blockState2 = level.getBlockState(blockPos2);
                return this.updateEdge(level, i1, i2, blockPos1, blockState1, blockPos2, blockState2);
            }
        }
    }

    private boolean updateEdge(BlockGetter level, int index, int minY, BlockPos pos1, BlockState state1, BlockPos pos2, BlockState state2) {
        int y = pos1.getY();
        if (isEdgeOccluded(state1, state2)) {
            if (y > minY) {
                this.set(index, y);
                return true;
            }
        } else if (y == minY) {
            this.set(index, this.findLowestSourceBelow(level, pos2, state2));
            return true;
        }

        return false;
    }

    private int findLowestSourceBelow(BlockGetter level, BlockPos pos, BlockState state) {
        BlockPos.MutableBlockPos mutableBlockPos = this.mutablePos1.set(pos);
        BlockPos.MutableBlockPos mutableBlockPos1 = this.mutablePos2.setWithOffset(pos, Direction.DOWN);
        BlockState blockState = state;

        while (mutableBlockPos1.getY() >= this.minY) {
            BlockState blockState1 = level.getBlockState(mutableBlockPos1);
            if (isEdgeOccluded(blockState, blockState1)) {
                return mutableBlockPos.getY();
            }

            blockState = blockState1;
            mutableBlockPos.set(mutableBlockPos1);
            mutableBlockPos1.move(Direction.DOWN);
        }

        return this.minY;
    }

    private static boolean isEdgeOccluded(BlockState state1, BlockState state2) {
        if (state2.getLightBlock() != 0) {
            return true;
        } else {
            VoxelShape occlusionShape = LightEngine.getOcclusionShape(state1, Direction.DOWN);
            VoxelShape occlusionShape1 = LightEngine.getOcclusionShape(state2, Direction.UP);
            return Shapes.faceShapeOccludes(occlusionShape, occlusionShape1);
        }
    }

    public int getLowestSourceY(int x, int z) {
        int i = this.get(index(x, z));
        return this.extendSourcesBelowWorld(i);
    }

    public int getHighestLowestSourceY() {
        int i = Integer.MIN_VALUE;

        for (int i1 = 0; i1 < this.heightmap.getSize(); i1++) {
            int i2 = this.heightmap.get(i1);
            if (i2 > i) {
                i = i2;
            }
        }

        return this.extendSourcesBelowWorld(i + this.minY);
    }

    private void fill(int value) {
        int i = value - this.minY;

        for (int i1 = 0; i1 < this.heightmap.getSize(); i1++) {
            this.heightmap.set(i1, i);
        }
    }

    private void set(int index, int value) {
        this.heightmap.set(index, value - this.minY);
    }

    private int get(int index) {
        return this.heightmap.get(index) + this.minY;
    }

    private int extendSourcesBelowWorld(int y) {
        return y == this.minY ? Integer.MIN_VALUE : y;
    }

    private static int index(int x, int z) {
        return x + z * 16;
    }
}
