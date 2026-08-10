package net.minecraft.world.level.lighting;

import com.google.common.annotations.VisibleForTesting;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import org.jspecify.annotations.Nullable;

public final class SkyLightEngine extends LightEngine<SkyLightSectionStorage.SkyDataLayerStorageMap, SkyLightSectionStorage> {
    private static final long REMOVE_TOP_SKY_SOURCE_ENTRY = LightEngine.QueueEntry.decreaseAllDirections(15);
    private static final long REMOVE_SKY_SOURCE_ENTRY = LightEngine.QueueEntry.decreaseSkipOneDirection(15, Direction.UP);
    private static final long ADD_SKY_SOURCE_ENTRY = LightEngine.QueueEntry.increaseSkipOneDirection(15, false, Direction.UP);
    private final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
    private final ChunkSkyLightSources emptyChunkSources;

    public SkyLightEngine(LightChunkGetter chunkSource) {
        this(chunkSource, new SkyLightSectionStorage(chunkSource));
    }

    @VisibleForTesting
    protected SkyLightEngine(LightChunkGetter chunkSource, SkyLightSectionStorage sectionStorage) {
        super(chunkSource, sectionStorage);
        this.emptyChunkSources = new ChunkSkyLightSources(chunkSource.getLevel());
    }

    private static boolean isSourceLevel(int level) {
        return level == 15;
    }

    private int getLowestSourceY(int x, int z, int defaultReturnValue) {
        ChunkSkyLightSources chunkSources = this.getChunkSources(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
        return chunkSources == null ? defaultReturnValue : chunkSources.getLowestSourceY(SectionPos.sectionRelative(x), SectionPos.sectionRelative(z));
    }

    private @Nullable ChunkSkyLightSources getChunkSources(int chunkX, int chunkZ) {
        LightChunk chunkForLighting = this.chunkSource.getChunkForLighting(chunkX, chunkZ);
        return chunkForLighting != null ? chunkForLighting.getSkyLightSources() : null;
    }

    @Override
    protected void checkNode(long pos) {
        int x = BlockPos.getX(pos);
        int y = BlockPos.getY(pos);
        int z = BlockPos.getZ(pos);
        long packedSectionPos = SectionPos.blockToSection(pos);
        int i = this.storage.lightOnInSection(packedSectionPos) ? this.getLowestSourceY(x, z, Integer.MAX_VALUE) : Integer.MAX_VALUE;
        if (i != Integer.MAX_VALUE) {
            this.updateSourcesInColumn(x, z, i);
        }

        if (this.storage.storingLightForSection(packedSectionPos)) {
            boolean flag = y >= i;
            if (flag) {
                this.enqueueDecrease(pos, REMOVE_SKY_SOURCE_ENTRY);
                this.enqueueIncrease(pos, ADD_SKY_SOURCE_ENTRY);
            } else {
                int storedLevel = this.storage.getStoredLevel(pos);
                if (storedLevel > 0) {
                    this.storage.setStoredLevel(pos, 0);
                    this.enqueueDecrease(pos, LightEngine.QueueEntry.decreaseAllDirections(storedLevel));
                } else {
                    this.enqueueDecrease(pos, PULL_LIGHT_IN_ENTRY);
                }
            }
        }
    }

    private void updateSourcesInColumn(int x, int z, int lowestY) {
        int blockPosCoord = SectionPos.sectionToBlockCoord(this.storage.getBottomSectionY());
        this.removeSourcesBelow(x, z, lowestY, blockPosCoord);
        this.addSourcesAbove(x, z, lowestY, blockPosCoord);
    }

    private void removeSourcesBelow(int x, int z, int minY, int bottomSectionY) {
        if (minY > bottomSectionY) {
            int sectionPosX = SectionPos.blockToSectionCoord(x);
            int sectionPosZ = SectionPos.blockToSectionCoord(z);
            int i = minY - 1;

            for (int sectionPosCoord = SectionPos.blockToSectionCoord(i); this.storage.hasLightDataAtOrBelow(sectionPosCoord); sectionPosCoord--) {
                if (this.storage.storingLightForSection(SectionPos.asLong(sectionPosX, sectionPosCoord, sectionPosZ))) {
                    int blockPosCoord = SectionPos.sectionToBlockCoord(sectionPosCoord);
                    int i1 = blockPosCoord + 15;

                    for (int min = Math.min(i1, i); min >= blockPosCoord; min--) {
                        long packedBlockPos = BlockPos.asLong(x, min, z);
                        if (!isSourceLevel(this.storage.getStoredLevel(packedBlockPos))) {
                            return;
                        }

                        this.storage.setStoredLevel(packedBlockPos, 0);
                        this.enqueueDecrease(packedBlockPos, min == minY - 1 ? REMOVE_TOP_SKY_SOURCE_ENTRY : REMOVE_SKY_SOURCE_ENTRY);
                    }
                }
            }
        }
    }

    private void addSourcesAbove(int x, int z, int maxY, int bottomSectionY) {
        int sectionPosX = SectionPos.blockToSectionCoord(x);
        int sectionPosZ = SectionPos.blockToSectionCoord(z);
        int max = Math.max(
            Math.max(this.getLowestSourceY(x - 1, z, Integer.MIN_VALUE), this.getLowestSourceY(x + 1, z, Integer.MIN_VALUE)),
            Math.max(this.getLowestSourceY(x, z - 1, Integer.MIN_VALUE), this.getLowestSourceY(x, z + 1, Integer.MIN_VALUE))
        );
        int max1 = Math.max(maxY, bottomSectionY);

        for (long packedSectionPos = SectionPos.asLong(sectionPosX, SectionPos.blockToSectionCoord(max1), sectionPosZ);
            !this.storage.isAboveData(packedSectionPos);
            packedSectionPos = SectionPos.offset(packedSectionPos, Direction.UP)
        ) {
            if (this.storage.storingLightForSection(packedSectionPos)) {
                int blockPosY = SectionPos.sectionToBlockCoord(SectionPos.y(packedSectionPos));
                int i = blockPosY + 15;

                for (int max2 = Math.max(blockPosY, max1); max2 <= i; max2++) {
                    long packedBlockPos = BlockPos.asLong(x, max2, z);
                    if (isSourceLevel(this.storage.getStoredLevel(packedBlockPos))) {
                        return;
                    }

                    this.storage.setStoredLevel(packedBlockPos, 15);
                    if (max2 < max || max2 == maxY) {
                        this.enqueueIncrease(packedBlockPos, ADD_SKY_SOURCE_ENTRY);
                    }
                }
            }
        }
    }

    @Override
    protected void propagateIncrease(long packedPos, long queueEntry, int lightLevel) {
        BlockState blockState = null;
        int i = this.countEmptySectionsBelowIfAtBorder(packedPos);

        for (Direction direction : PROPAGATION_DIRECTIONS) {
            if (LightEngine.QueueEntry.shouldPropagateInDirection(queueEntry, direction)) {
                long offsetPackedBlockPos = BlockPos.offset(packedPos, direction);
                if (this.storage.storingLightForSection(SectionPos.blockToSection(offsetPackedBlockPos))) {
                    int storedLevel = this.storage.getStoredLevel(offsetPackedBlockPos);
                    int i1 = lightLevel - 1;
                    if (i1 > storedLevel) {
                        this.mutablePos.set(offsetPackedBlockPos);
                        BlockState state = this.getState(this.mutablePos);
                        int i2 = lightLevel - this.getOpacity(state);
                        if (i2 > storedLevel) {
                            if (blockState == null) {
                                blockState = LightEngine.QueueEntry.isFromEmptyShape(queueEntry)
                                    ? Blocks.AIR.defaultBlockState()
                                    : this.getState(this.mutablePos.set(packedPos));
                            }

                            if (!this.shapeOccludes(blockState, state, direction)) {
                                this.storage.setStoredLevel(offsetPackedBlockPos, i2);
                                if (i2 > 1) {
                                    this.enqueueIncrease(
                                        offsetPackedBlockPos, LightEngine.QueueEntry.increaseSkipOneDirection(i2, isEmptyShape(state), direction.getOpposite())
                                    );
                                }

                                this.propagateFromEmptySections(offsetPackedBlockPos, direction, i2, true, i);
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    protected void propagateDecrease(long packedPos, long lightLevel) {
        int i = this.countEmptySectionsBelowIfAtBorder(packedPos);
        int fromLevel = LightEngine.QueueEntry.getFromLevel(lightLevel);

        for (Direction direction : PROPAGATION_DIRECTIONS) {
            if (LightEngine.QueueEntry.shouldPropagateInDirection(lightLevel, direction)) {
                long offsetPackedBlockPos = BlockPos.offset(packedPos, direction);
                if (this.storage.storingLightForSection(SectionPos.blockToSection(offsetPackedBlockPos))) {
                    int storedLevel = this.storage.getStoredLevel(offsetPackedBlockPos);
                    if (storedLevel != 0) {
                        if (storedLevel <= fromLevel - 1) {
                            this.storage.setStoredLevel(offsetPackedBlockPos, 0);
                            this.enqueueDecrease(offsetPackedBlockPos, LightEngine.QueueEntry.decreaseSkipOneDirection(storedLevel, direction.getOpposite()));
                            this.propagateFromEmptySections(offsetPackedBlockPos, direction, storedLevel, false, i);
                        } else {
                            this.enqueueIncrease(
                                offsetPackedBlockPos, LightEngine.QueueEntry.increaseOnlyOneDirection(storedLevel, false, direction.getOpposite())
                            );
                        }
                    }
                }
            }
        }
    }

    private int countEmptySectionsBelowIfAtBorder(long packedPos) {
        int y = BlockPos.getY(packedPos);
        int relativeBlockPosY = SectionPos.sectionRelative(y);
        if (relativeBlockPosY != 0) {
            return 0;
        } else {
            int x = BlockPos.getX(packedPos);
            int z = BlockPos.getZ(packedPos);
            int relativeBlockPosX = SectionPos.sectionRelative(x);
            int relativeBlockPosZ = SectionPos.sectionRelative(z);
            if (relativeBlockPosX != 0 && relativeBlockPosX != 15 && relativeBlockPosZ != 0 && relativeBlockPosZ != 15) {
                return 0;
            } else {
                int sectionPosX = SectionPos.blockToSectionCoord(x);
                int sectionPosY = SectionPos.blockToSectionCoord(y);
                int sectionPosZ = SectionPos.blockToSectionCoord(z);
                int i = 0;

                while (
                    !this.storage.storingLightForSection(SectionPos.asLong(sectionPosX, sectionPosY - i - 1, sectionPosZ))
                        && this.storage.hasLightDataAtOrBelow(sectionPosY - i - 1)
                ) {
                    i++;
                }

                return i;
            }
        }
    }

    private void propagateFromEmptySections(long packedPos, Direction direction, int level, boolean shouldIncrease, int emptySections) {
        if (emptySections != 0) {
            int x = BlockPos.getX(packedPos);
            int z = BlockPos.getZ(packedPos);
            if (crossedSectionEdge(direction, SectionPos.sectionRelative(x), SectionPos.sectionRelative(z))) {
                int y = BlockPos.getY(packedPos);
                int sectionPosX = SectionPos.blockToSectionCoord(x);
                int sectionPosZ = SectionPos.blockToSectionCoord(z);
                int i = SectionPos.blockToSectionCoord(y) - 1;
                int i1 = i - emptySections + 1;

                while (i >= i1) {
                    if (!this.storage.storingLightForSection(SectionPos.asLong(sectionPosX, i, sectionPosZ))) {
                        i--;
                    } else {
                        int blockPosCoord = SectionPos.sectionToBlockCoord(i);

                        for (int i2 = 15; i2 >= 0; i2--) {
                            long packedBlockPos = BlockPos.asLong(x, blockPosCoord + i2, z);
                            if (shouldIncrease) {
                                this.storage.setStoredLevel(packedBlockPos, level);
                                if (level > 1) {
                                    this.enqueueIncrease(packedBlockPos, LightEngine.QueueEntry.increaseSkipOneDirection(level, true, direction.getOpposite()));
                                }
                            } else {
                                this.storage.setStoredLevel(packedBlockPos, 0);
                                this.enqueueDecrease(packedBlockPos, LightEngine.QueueEntry.decreaseSkipOneDirection(level, direction.getOpposite()));
                            }
                        }

                        i--;
                    }
                }
            }
        }
    }

    private static boolean crossedSectionEdge(Direction direction, int x, int z) {
        return switch (direction) {
            case NORTH -> z == 15;
            case SOUTH -> z == 0;
            case WEST -> x == 15;
            case EAST -> x == 0;
            default -> false;
        };
    }

    @Override
    public void setLightEnabled(ChunkPos chunkPos, boolean lightEnabled) {
        super.setLightEnabled(chunkPos, lightEnabled);
        if (lightEnabled) {
            ChunkSkyLightSources chunkSkyLightSources = Objects.requireNonNullElse(this.getChunkSources(chunkPos.x, chunkPos.z), this.emptyChunkSources);
            int i = chunkSkyLightSources.getHighestLowestSourceY() - 1;
            int i1 = SectionPos.blockToSectionCoord(i) + 1;
            long zeroNode = SectionPos.getZeroNode(chunkPos.x, chunkPos.z);
            int topSectionY = this.storage.getTopSectionY(zeroNode);
            int max = Math.max(this.storage.getBottomSectionY(), i1);

            for (int i2 = topSectionY - 1; i2 >= max; i2--) {
                DataLayer dataLayerToWrite = this.storage.getDataLayerToWrite(SectionPos.asLong(chunkPos.x, i2, chunkPos.z));
                if (dataLayerToWrite != null && dataLayerToWrite.isEmpty()) {
                    dataLayerToWrite.fill(15);
                }
            }
        }
    }

    @Override
    public void propagateLightSources(ChunkPos chunkPos) {
        long zeroNode = SectionPos.getZeroNode(chunkPos.x, chunkPos.z);
        this.storage.setLightEnabled(zeroNode, true);
        ChunkSkyLightSources chunkSkyLightSources = Objects.requireNonNullElse(this.getChunkSources(chunkPos.x, chunkPos.z), this.emptyChunkSources);
        ChunkSkyLightSources chunkSkyLightSources1 = Objects.requireNonNullElse(this.getChunkSources(chunkPos.x, chunkPos.z - 1), this.emptyChunkSources);
        ChunkSkyLightSources chunkSkyLightSources2 = Objects.requireNonNullElse(this.getChunkSources(chunkPos.x, chunkPos.z + 1), this.emptyChunkSources);
        ChunkSkyLightSources chunkSkyLightSources3 = Objects.requireNonNullElse(this.getChunkSources(chunkPos.x - 1, chunkPos.z), this.emptyChunkSources);
        ChunkSkyLightSources chunkSkyLightSources4 = Objects.requireNonNullElse(this.getChunkSources(chunkPos.x + 1, chunkPos.z), this.emptyChunkSources);
        int topSectionY = this.storage.getTopSectionY(zeroNode);
        int bottomSectionY = this.storage.getBottomSectionY();
        int blockPosX = SectionPos.sectionToBlockCoord(chunkPos.x);
        int blockPosZ = SectionPos.sectionToBlockCoord(chunkPos.z);

        for (int i = topSectionY - 1; i >= bottomSectionY; i--) {
            long packedSectionPos = SectionPos.asLong(chunkPos.x, i, chunkPos.z);
            DataLayer dataLayerToWrite = this.storage.getDataLayerToWrite(packedSectionPos);
            if (dataLayerToWrite != null) {
                int blockPosCoord = SectionPos.sectionToBlockCoord(i);
                int i1 = blockPosCoord + 15;
                boolean flag = false;

                for (int i2 = 0; i2 < 16; i2++) {
                    for (int i3 = 0; i3 < 16; i3++) {
                        int lowestSourceY = chunkSkyLightSources.getLowestSourceY(i3, i2);
                        if (lowestSourceY <= i1) {
                            int i4 = i2 == 0 ? chunkSkyLightSources1.getLowestSourceY(i3, 15) : chunkSkyLightSources.getLowestSourceY(i3, i2 - 1);
                            int i5 = i2 == 15 ? chunkSkyLightSources2.getLowestSourceY(i3, 0) : chunkSkyLightSources.getLowestSourceY(i3, i2 + 1);
                            int i6 = i3 == 0 ? chunkSkyLightSources3.getLowestSourceY(15, i2) : chunkSkyLightSources.getLowestSourceY(i3 - 1, i2);
                            int i7 = i3 == 15 ? chunkSkyLightSources4.getLowestSourceY(0, i2) : chunkSkyLightSources.getLowestSourceY(i3 + 1, i2);
                            int max = Math.max(Math.max(i4, i5), Math.max(i6, i7));

                            for (int i8 = i1; i8 >= Math.max(blockPosCoord, lowestSourceY); i8--) {
                                dataLayerToWrite.set(i3, SectionPos.sectionRelative(i8), i2, 15);
                                if (i8 == lowestSourceY || i8 < max) {
                                    long packedBlockPos = BlockPos.asLong(blockPosX + i3, i8, blockPosZ + i2);
                                    this.enqueueIncrease(
                                        packedBlockPos,
                                        LightEngine.QueueEntry.increaseSkySourceInDirections(i8 == lowestSourceY, i8 < i4, i8 < i5, i8 < i6, i8 < i7)
                                    );
                                }
                            }

                            if (lowestSourceY < blockPosCoord) {
                                flag = true;
                            }
                        }
                    }
                }

                if (!flag) {
                    break;
                }
            }
        }
    }
}
