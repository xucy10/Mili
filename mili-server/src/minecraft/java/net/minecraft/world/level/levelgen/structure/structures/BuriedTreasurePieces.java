package net.minecraft.world.level.levelgen.structure.structures;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

public class BuriedTreasurePieces {
    public static class BuriedTreasurePiece extends StructurePiece {
        public BuriedTreasurePiece(BlockPos pos) {
            super(StructurePieceType.BURIED_TREASURE_PIECE, 0, new BoundingBox(pos));
        }

        public BuriedTreasurePiece(CompoundTag tag) {
            super(StructurePieceType.BURIED_TREASURE_PIECE, tag);
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        }

        @Override
        public void postProcess(
            WorldGenLevel level,
            StructureManager structureManager,
            ChunkGenerator generator,
            RandomSource random,
            BoundingBox box,
            ChunkPos chunkPos,
            BlockPos pos
        ) {
            int height = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, this.boundingBox.minX(), this.boundingBox.minZ());
            BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos(this.boundingBox.minX(), height, this.boundingBox.minZ());

            while (mutableBlockPos.getY() > level.getMinY()) {
                BlockState blockState = level.getBlockState(mutableBlockPos);
                BlockState blockState1 = level.getBlockState(mutableBlockPos.below());
                if (blockState1 == Blocks.SANDSTONE.defaultBlockState()
                    || blockState1 == Blocks.STONE.defaultBlockState()
                    || blockState1 == Blocks.ANDESITE.defaultBlockState()
                    || blockState1 == Blocks.GRANITE.defaultBlockState()
                    || blockState1 == Blocks.DIORITE.defaultBlockState()) {
                    BlockState blockState2 = !blockState.isAir() && !this.isLiquid(blockState) ? blockState : Blocks.SAND.defaultBlockState();

                    for (Direction direction : Direction.values()) {
                        BlockPos blockPos = mutableBlockPos.relative(direction);
                        BlockState blockState3 = level.getBlockState(blockPos);
                        if (blockState3.isAir() || this.isLiquid(blockState3)) {
                            BlockPos blockPos1 = blockPos.below();
                            BlockState blockState4 = level.getBlockState(blockPos1);
                            if ((blockState4.isAir() || this.isLiquid(blockState4)) && direction != Direction.UP) {
                                level.setBlock(blockPos, blockState1, Block.UPDATE_ALL);
                            } else {
                                level.setBlock(blockPos, blockState2, Block.UPDATE_ALL);
                            }
                        }
                    }

                    this.boundingBox = new BoundingBox(mutableBlockPos);
                    this.createChest(level, box, random, mutableBlockPos, BuiltInLootTables.BURIED_TREASURE, null);
                    return;
                }

                mutableBlockPos.move(0, -1, 0);
            }
        }

        private boolean isLiquid(BlockState state) {
            return state == Blocks.WATER.defaultBlockState() || state == Blocks.LAVA.defaultBlockState();
        }
    }
}
