package net.minecraft.world.level.lighting;

import com.google.common.annotations.VisibleForTesting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;

public final class BlockLightEngine extends LightEngine<BlockLightSectionStorage.BlockDataLayerStorageMap, BlockLightSectionStorage> {
    private final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

    public BlockLightEngine(LightChunkGetter chunkSource) {
        this(chunkSource, new BlockLightSectionStorage(chunkSource));
    }

    @VisibleForTesting
    public BlockLightEngine(LightChunkGetter chunkSource, BlockLightSectionStorage storage) {
        super(chunkSource, storage);
    }

    @Override
    protected void checkNode(long packedPos) {
        long packedSectionPos = SectionPos.blockToSection(packedPos);
        if (this.storage.storingLightForSection(packedSectionPos)) {
            BlockState state = this.getState(this.mutablePos.set(packedPos));
            int emission = this.getEmission(packedPos, state);
            int storedLevel = this.storage.getStoredLevel(packedPos);
            if (emission < storedLevel) {
                this.storage.setStoredLevel(packedPos, 0);
                this.enqueueDecrease(packedPos, LightEngine.QueueEntry.decreaseAllDirections(storedLevel));
            } else {
                this.enqueueDecrease(packedPos, PULL_LIGHT_IN_ENTRY);
            }

            if (emission > 0) {
                this.enqueueIncrease(packedPos, LightEngine.QueueEntry.increaseLightFromEmission(emission, isEmptyShape(state)));
            }
        }
    }

    @Override
    protected void propagateIncrease(long packedPos, long queueEntry, int lightLevel) {
        BlockState blockState = null;

        for (Direction direction : PROPAGATION_DIRECTIONS) {
            if (LightEngine.QueueEntry.shouldPropagateInDirection(queueEntry, direction)) {
                long offsetPackedBlockPos = BlockPos.offset(packedPos, direction);
                if (this.storage.storingLightForSection(SectionPos.blockToSection(offsetPackedBlockPos))) {
                    int storedLevel = this.storage.getStoredLevel(offsetPackedBlockPos);
                    int i = lightLevel - 1;
                    if (i > storedLevel) {
                        this.mutablePos.set(offsetPackedBlockPos);
                        BlockState state = this.getState(this.mutablePos);
                        int i1 = lightLevel - this.getOpacity(state);
                        if (i1 > storedLevel) {
                            if (blockState == null) {
                                blockState = LightEngine.QueueEntry.isFromEmptyShape(queueEntry)
                                    ? Blocks.AIR.defaultBlockState()
                                    : this.getState(this.mutablePos.set(packedPos));
                            }

                            if (!this.shapeOccludes(blockState, state, direction)) {
                                this.storage.setStoredLevel(offsetPackedBlockPos, i1);
                                if (i1 > 1) {
                                    this.enqueueIncrease(
                                        offsetPackedBlockPos, LightEngine.QueueEntry.increaseSkipOneDirection(i1, isEmptyShape(state), direction.getOpposite())
                                    );
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    protected void propagateDecrease(long packedPos, long lightLevel) {
        int fromLevel = LightEngine.QueueEntry.getFromLevel(lightLevel);

        for (Direction direction : PROPAGATION_DIRECTIONS) {
            if (LightEngine.QueueEntry.shouldPropagateInDirection(lightLevel, direction)) {
                long offsetPackedBlockPos = BlockPos.offset(packedPos, direction);
                if (this.storage.storingLightForSection(SectionPos.blockToSection(offsetPackedBlockPos))) {
                    int storedLevel = this.storage.getStoredLevel(offsetPackedBlockPos);
                    if (storedLevel != 0) {
                        if (storedLevel <= fromLevel - 1) {
                            BlockState state = this.getState(this.mutablePos.set(offsetPackedBlockPos));
                            int emission = this.getEmission(offsetPackedBlockPos, state);
                            this.storage.setStoredLevel(offsetPackedBlockPos, 0);
                            if (emission < storedLevel) {
                                this.enqueueDecrease(
                                    offsetPackedBlockPos, LightEngine.QueueEntry.decreaseSkipOneDirection(storedLevel, direction.getOpposite())
                                );
                            }

                            if (emission > 0) {
                                this.enqueueIncrease(offsetPackedBlockPos, LightEngine.QueueEntry.increaseLightFromEmission(emission, isEmptyShape(state)));
                            }
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

    private int getEmission(long packedPos, BlockState state) {
        int lightEmission = state.getLightEmission();
        return lightEmission > 0 && this.storage.lightOnInSection(SectionPos.blockToSection(packedPos)) ? lightEmission : 0;
    }

    @Override
    public void propagateLightSources(ChunkPos chunkPos) {
        this.setLightEnabled(chunkPos, true);
        LightChunk chunkForLighting = this.chunkSource.getChunkForLighting(chunkPos.x, chunkPos.z);
        if (chunkForLighting != null) {
            chunkForLighting.findBlockLightSources((blockPos, blockState) -> {
                int lightEmission = blockState.getLightEmission();
                this.enqueueIncrease(blockPos.asLong(), LightEngine.QueueEntry.increaseLightFromEmission(lightEmission, isEmptyShape(blockState)));
            });
        }
    }
}
