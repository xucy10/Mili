package net.minecraft.world.level.levelgen.structure.structures;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.ScatteredFeaturePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

public class DesertPyramidPiece extends ScatteredFeaturePiece {
    public static final int WIDTH = 21;
    public static final int DEPTH = 21;
    private final boolean[] hasPlacedChest = new boolean[4];
    private final List<BlockPos> potentialSuspiciousSandWorldPositions = new ArrayList<>();
    private BlockPos randomCollapsedRoofPos = BlockPos.ZERO;

    public DesertPyramidPiece(RandomSource random, int x, int z) {
        super(StructurePieceType.DESERT_PYRAMID_PIECE, x, 64, z, 21, 15, 21, getRandomHorizontalDirection(random));
    }

    public DesertPyramidPiece(CompoundTag tag) {
        super(StructurePieceType.DESERT_PYRAMID_PIECE, tag);
        this.hasPlacedChest[0] = tag.getBooleanOr("hasPlacedChest0", false);
        this.hasPlacedChest[1] = tag.getBooleanOr("hasPlacedChest1", false);
        this.hasPlacedChest[2] = tag.getBooleanOr("hasPlacedChest2", false);
        this.hasPlacedChest[3] = tag.getBooleanOr("hasPlacedChest3", false);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.putBoolean("hasPlacedChest0", this.hasPlacedChest[0]);
        tag.putBoolean("hasPlacedChest1", this.hasPlacedChest[1]);
        tag.putBoolean("hasPlacedChest2", this.hasPlacedChest[2]);
        tag.putBoolean("hasPlacedChest3", this.hasPlacedChest[3]);
    }

    @Override
    public void postProcess(
        WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox box, ChunkPos chunkPos, BlockPos pos
    ) {
        if (this.updateHeightPositionToLowestGroundHeight(level, -random.nextInt(3))) {
            this.generateBox(
                level, box, 0, -4, 0, this.width - 1, 0, this.depth - 1, Blocks.SANDSTONE.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState(), false
            );

            for (int i = 1; i <= 9; i++) {
                this.generateBox(
                    level,
                    box,
                    i,
                    i,
                    i,
                    this.width - 1 - i,
                    i,
                    this.depth - 1 - i,
                    Blocks.SANDSTONE.defaultBlockState(),
                    Blocks.SANDSTONE.defaultBlockState(),
                    false
                );
                this.generateBox(
                    level,
                    box,
                    i + 1,
                    i,
                    i + 1,
                    this.width - 2 - i,
                    i,
                    this.depth - 2 - i,
                    Blocks.AIR.defaultBlockState(),
                    Blocks.AIR.defaultBlockState(),
                    false
                );
            }

            for (int i = 0; i < this.width; i++) {
                for (int i1 = 0; i1 < this.depth; i1++) {
                    int i2 = -5;
                    this.fillColumnDown(level, Blocks.SANDSTONE.defaultBlockState(), i, -5, i1, box);
                }
            }

            BlockState blockState = Blocks.SANDSTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
            BlockState blockState1 = Blocks.SANDSTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH);
            BlockState blockState2 = Blocks.SANDSTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST);
            BlockState blockState3 = Blocks.SANDSTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST);
            this.generateBox(level, box, 0, 0, 0, 4, 9, 4, Blocks.SANDSTONE.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.generateBox(level, box, 1, 10, 1, 3, 10, 3, Blocks.SANDSTONE.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState(), false);
            this.placeBlock(level, blockState, 2, 10, 0, box);
            this.placeBlock(level, blockState1, 2, 10, 4, box);
            this.placeBlock(level, blockState2, 0, 10, 2, box);
            this.placeBlock(level, blockState3, 4, 10, 2, box);
            this.generateBox(
                level, box, this.width - 5, 0, 0, this.width - 1, 9, 4, Blocks.SANDSTONE.defaultBlockState(), Blocks.AIR.defaultBlockState(), false
            );
            this.generateBox(
                level, box, this.width - 4, 10, 1, this.width - 2, 10, 3, Blocks.SANDSTONE.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState(), false
            );
            this.placeBlock(level, blockState, this.width - 3, 10, 0, box);
            this.placeBlock(level, blockState1, this.width - 3, 10, 4, box);
            this.placeBlock(level, blockState2, this.width - 5, 10, 2, box);
            this.placeBlock(level, blockState3, this.width - 1, 10, 2, box);
            this.generateBox(level, box, 8, 0, 0, 12, 4, 4, Blocks.SANDSTONE.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.generateBox(level, box, 9, 1, 0, 11, 3, 4, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.placeBlock(level, Blocks.CUT_SANDSTONE.defaultBlockState(), 9, 1, 1, box);
            this.placeBlock(level, Blocks.CUT_SANDSTONE.defaultBlockState(), 9, 2, 1, box);
            this.placeBlock(level, Blocks.CUT_SANDSTONE.defaultBlockState(), 9, 3, 1, box);
            this.placeBlock(level, Blocks.CUT_SANDSTONE.defaultBlockState(), 10, 3, 1, box);
            this.placeBlock(level, Blocks.CUT_SANDSTONE.defaultBlockState(), 11, 3, 1, box);
            this.placeBlock(level, Blocks.CUT_SANDSTONE.defaultBlockState(), 11, 2, 1, box);
            this.placeBlock(level, Blocks.CUT_SANDSTONE.defaultBlockState(), 11, 1, 1, box);
            this.generateBox(level, box, 4, 1, 1, 8, 3, 3, Blocks.SANDSTONE.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.generateBox(level, box, 4, 1, 2, 8, 2, 2, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.generateBox(level, box, 12, 1, 1, 16, 3, 3, Blocks.SANDSTONE.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.generateBox(level, box, 12, 1, 2, 16, 2, 2, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.generateBox(
                level, box, 5, 4, 5, this.width - 6, 4, this.depth - 6, Blocks.SANDSTONE.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState(), false
            );
            this.generateBox(level, box, 9, 4, 9, 11, 4, 11, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.generateBox(level, box, 8, 1, 8, 8, 3, 8, Blocks.CUT_SANDSTONE.defaultBlockState(), Blocks.CUT_SANDSTONE.defaultBlockState(), false);
            this.generateBox(level, box, 12, 1, 8, 12, 3, 8, Blocks.CUT_SANDSTONE.defaultBlockState(), Blocks.CUT_SANDSTONE.defaultBlockState(), false);
            this.generateBox(level, box, 8, 1, 12, 8, 3, 12, Blocks.CUT_SANDSTONE.defaultBlockState(), Blocks.CUT_SANDSTONE.defaultBlockState(), false);
            this.generateBox(level, box, 12, 1, 12, 12, 3, 12, Blocks.CUT_SANDSTONE.defaultBlockState(), Blocks.CUT_SANDSTONE.defaultBlockState(), false);
            this.generateBox(level, box, 1, 1, 5, 4, 4, 11, Blocks.SANDSTONE.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState(), false);
            this.generateBox(
                level, box, this.width - 5, 1, 5, this.width - 2, 4, 11, Blocks.SANDSTONE.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState(), false
            );
            this.generateBox(level, box, 6, 7, 9, 6, 7, 11, Blocks.SANDSTONE.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState(), false);
            this.generateBox(
                level, box, this.width - 7, 7, 9, this.width - 7, 7, 11, Blocks.SANDSTONE.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState(), false
            );
            this.generateBox(level, box, 5, 5, 9, 5, 7, 11, Blocks.CUT_SANDSTONE.defaultBlockState(), Blocks.CUT_SANDSTONE.defaultBlockState(), false);
            this.generateBox(
                level,
                box,
                this.width - 6,
                5,
                9,
                this.width - 6,
                7,
                11,
                Blocks.CUT_SANDSTONE.defaultBlockState(),
                Blocks.CUT_SANDSTONE.defaultBlockState(),
                false
            );
            this.placeBlock(level, Blocks.AIR.defaultBlockState(), 5, 5, 10, box);
            this.placeBlock(level, Blocks.AIR.defaultBlockState(), 5, 6, 10, box);
            this.placeBlock(level, Blocks.AIR.defaultBlockState(), 6, 6, 10, box);
            this.placeBlock(level, Blocks.AIR.defaultBlockState(), this.width - 6, 5, 10, box);
            this.placeBlock(level, Blocks.AIR.defaultBlockState(), this.width - 6, 6, 10, box);
            this.placeBlock(level, Blocks.AIR.defaultBlockState(), this.width - 7, 6, 10, box);
            this.generateBox(level, box, 2, 4, 4, 2, 6, 4, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.generateBox(level, box, this.width - 3, 4, 4, this.width - 3, 6, 4, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.placeBlock(level, blockState, 2, 4, 5, box);
            this.placeBlock(level, blockState, 2, 3, 4, box);
            this.placeBlock(level, blockState, this.width - 3, 4, 5, box);
            this.placeBlock(level, blockState, this.width - 3, 3, 4, box);
            this.generateBox(level, box, 1, 1, 3, 2, 2, 3, Blocks.SANDSTONE.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState(), false);
            this.generateBox(
                level, box, this.width - 3, 1, 3, this.width - 2, 2, 3, Blocks.SANDSTONE.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState(), false
            );
            this.placeBlock(level, Blocks.SANDSTONE.defaultBlockState(), 1, 1, 2, box);
            this.placeBlock(level, Blocks.SANDSTONE.defaultBlockState(), this.width - 2, 1, 2, box);
            this.placeBlock(level, Blocks.SANDSTONE_SLAB.defaultBlockState(), 1, 2, 2, box);
            this.placeBlock(level, Blocks.SANDSTONE_SLAB.defaultBlockState(), this.width - 2, 2, 2, box);
            this.placeBlock(level, blockState3, 2, 1, 2, box);
            this.placeBlock(level, blockState2, this.width - 3, 1, 2, box);
            this.generateBox(level, box, 4, 3, 5, 4, 3, 17, Blocks.SANDSTONE.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState(), false);
            this.generateBox(
                level, box, this.width - 5, 3, 5, this.width - 5, 3, 17, Blocks.SANDSTONE.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState(), false
            );
            this.generateBox(level, box, 3, 1, 5, 4, 2, 16, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.generateBox(level, box, this.width - 6, 1, 5, this.width - 5, 2, 16, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);

            for (int i3 = 5; i3 <= 17; i3 += 2) {
                this.placeBlock(level, Blocks.CUT_SANDSTONE.defaultBlockState(), 4, 1, i3, box);
                this.placeBlock(level, Blocks.CHISELED_SANDSTONE.defaultBlockState(), 4, 2, i3, box);
                this.placeBlock(level, Blocks.CUT_SANDSTONE.defaultBlockState(), this.width - 5, 1, i3, box);
                this.placeBlock(level, Blocks.CHISELED_SANDSTONE.defaultBlockState(), this.width - 5, 2, i3, box);
            }

            this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), 10, 0, 7, box);
            this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), 10, 0, 8, box);
            this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), 9, 0, 9, box);
            this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), 11, 0, 9, box);
            this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), 8, 0, 10, box);
            this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), 12, 0, 10, box);
            this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), 7, 0, 10, box);
            this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), 13, 0, 10, box);
            this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), 9, 0, 11, box);
            this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), 11, 0, 11, box);
            this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), 10, 0, 12, box);
            this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), 10, 0, 13, box);
            this.placeBlock(level, Blocks.BLUE_TERRACOTTA.defaultBlockState(), 10, 0, 10, box);

            for (int i3 = 0; i3 <= this.width - 1; i3 += this.width - 1) {
                this.placeBlock(level, Blocks.CUT_SANDSTONE.defaultBlockState(), i3, 2, 1, box);
                this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), i3, 2, 2, box);
                this.placeBlock(level, Blocks.CUT_SANDSTONE.defaultBlockState(), i3, 2, 3, box);
                this.placeBlock(level, Blocks.CUT_SANDSTONE.defaultBlockState(), i3, 3, 1, box);
                this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), i3, 3, 2, box);
                this.placeBlock(level, Blocks.CUT_SANDSTONE.defaultBlockState(), i3, 3, 3, box);
                this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), i3, 4, 1, box);
                this.placeBlock(level, Blocks.CHISELED_SANDSTONE.defaultBlockState(), i3, 4, 2, box);
                this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), i3, 4, 3, box);
                this.placeBlock(level, Blocks.CUT_SANDSTONE.defaultBlockState(), i3, 5, 1, box);
                this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), i3, 5, 2, box);
                this.placeBlock(level, Blocks.CUT_SANDSTONE.defaultBlockState(), i3, 5, 3, box);
                this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), i3, 6, 1, box);
                this.placeBlock(level, Blocks.CHISELED_SANDSTONE.defaultBlockState(), i3, 6, 2, box);
                this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), i3, 6, 3, box);
                this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), i3, 7, 1, box);
                this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), i3, 7, 2, box);
                this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), i3, 7, 3, box);
                this.placeBlock(level, Blocks.CUT_SANDSTONE.defaultBlockState(), i3, 8, 1, box);
                this.placeBlock(level, Blocks.CUT_SANDSTONE.defaultBlockState(), i3, 8, 2, box);
                this.placeBlock(level, Blocks.CUT_SANDSTONE.defaultBlockState(), i3, 8, 3, box);
            }

            for (int i3 = 2; i3 <= this.width - 3; i3 += this.width - 3 - 2) {
                this.placeBlock(level, Blocks.CUT_SANDSTONE.defaultBlockState(), i3 - 1, 2, 0, box);
                this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), i3, 2, 0, box);
                this.placeBlock(level, Blocks.CUT_SANDSTONE.defaultBlockState(), i3 + 1, 2, 0, box);
                this.placeBlock(level, Blocks.CUT_SANDSTONE.defaultBlockState(), i3 - 1, 3, 0, box);
                this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), i3, 3, 0, box);
                this.placeBlock(level, Blocks.CUT_SANDSTONE.defaultBlockState(), i3 + 1, 3, 0, box);
                this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), i3 - 1, 4, 0, box);
                this.placeBlock(level, Blocks.CHISELED_SANDSTONE.defaultBlockState(), i3, 4, 0, box);
                this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), i3 + 1, 4, 0, box);
                this.placeBlock(level, Blocks.CUT_SANDSTONE.defaultBlockState(), i3 - 1, 5, 0, box);
                this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), i3, 5, 0, box);
                this.placeBlock(level, Blocks.CUT_SANDSTONE.defaultBlockState(), i3 + 1, 5, 0, box);
                this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), i3 - 1, 6, 0, box);
                this.placeBlock(level, Blocks.CHISELED_SANDSTONE.defaultBlockState(), i3, 6, 0, box);
                this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), i3 + 1, 6, 0, box);
                this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), i3 - 1, 7, 0, box);
                this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), i3, 7, 0, box);
                this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), i3 + 1, 7, 0, box);
                this.placeBlock(level, Blocks.CUT_SANDSTONE.defaultBlockState(), i3 - 1, 8, 0, box);
                this.placeBlock(level, Blocks.CUT_SANDSTONE.defaultBlockState(), i3, 8, 0, box);
                this.placeBlock(level, Blocks.CUT_SANDSTONE.defaultBlockState(), i3 + 1, 8, 0, box);
            }

            this.generateBox(level, box, 8, 4, 0, 12, 6, 0, Blocks.CUT_SANDSTONE.defaultBlockState(), Blocks.CUT_SANDSTONE.defaultBlockState(), false);
            this.placeBlock(level, Blocks.AIR.defaultBlockState(), 8, 6, 0, box);
            this.placeBlock(level, Blocks.AIR.defaultBlockState(), 12, 6, 0, box);
            this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), 9, 5, 0, box);
            this.placeBlock(level, Blocks.CHISELED_SANDSTONE.defaultBlockState(), 10, 5, 0, box);
            this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), 11, 5, 0, box);
            this.generateBox(level, box, 8, -14, 8, 12, -11, 12, Blocks.CUT_SANDSTONE.defaultBlockState(), Blocks.CUT_SANDSTONE.defaultBlockState(), false);
            this.generateBox(
                level, box, 8, -10, 8, 12, -10, 12, Blocks.CHISELED_SANDSTONE.defaultBlockState(), Blocks.CHISELED_SANDSTONE.defaultBlockState(), false
            );
            this.generateBox(level, box, 8, -9, 8, 12, -9, 12, Blocks.CUT_SANDSTONE.defaultBlockState(), Blocks.CUT_SANDSTONE.defaultBlockState(), false);
            this.generateBox(level, box, 8, -8, 8, 12, -1, 12, Blocks.SANDSTONE.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState(), false);
            this.generateBox(level, box, 9, -11, 9, 11, -1, 11, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.placeBlock(level, Blocks.STONE_PRESSURE_PLATE.defaultBlockState(), 10, -11, 10, box);
            this.generateBox(level, box, 9, -13, 9, 11, -13, 11, Blocks.TNT.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.placeBlock(level, Blocks.AIR.defaultBlockState(), 8, -11, 10, box);
            this.placeBlock(level, Blocks.AIR.defaultBlockState(), 8, -10, 10, box);
            this.placeBlock(level, Blocks.CHISELED_SANDSTONE.defaultBlockState(), 7, -10, 10, box);
            this.placeBlock(level, Blocks.CUT_SANDSTONE.defaultBlockState(), 7, -11, 10, box);
            this.placeBlock(level, Blocks.AIR.defaultBlockState(), 12, -11, 10, box);
            this.placeBlock(level, Blocks.AIR.defaultBlockState(), 12, -10, 10, box);
            this.placeBlock(level, Blocks.CHISELED_SANDSTONE.defaultBlockState(), 13, -10, 10, box);
            this.placeBlock(level, Blocks.CUT_SANDSTONE.defaultBlockState(), 13, -11, 10, box);
            this.placeBlock(level, Blocks.AIR.defaultBlockState(), 10, -11, 8, box);
            this.placeBlock(level, Blocks.AIR.defaultBlockState(), 10, -10, 8, box);
            this.placeBlock(level, Blocks.CHISELED_SANDSTONE.defaultBlockState(), 10, -10, 7, box);
            this.placeBlock(level, Blocks.CUT_SANDSTONE.defaultBlockState(), 10, -11, 7, box);
            this.placeBlock(level, Blocks.AIR.defaultBlockState(), 10, -11, 12, box);
            this.placeBlock(level, Blocks.AIR.defaultBlockState(), 10, -10, 12, box);
            this.placeBlock(level, Blocks.CHISELED_SANDSTONE.defaultBlockState(), 10, -10, 13, box);
            this.placeBlock(level, Blocks.CUT_SANDSTONE.defaultBlockState(), 10, -11, 13, box);

            for (Direction direction : Direction.Plane.HORIZONTAL) {
                if (!this.hasPlacedChest[direction.get2DDataValue()]) {
                    int i4 = direction.getStepX() * 2;
                    int i5 = direction.getStepZ() * 2;
                    this.hasPlacedChest[direction.get2DDataValue()] = this.createChest(
                        level, box, random, 10 + i4, -11, 10 + i5, BuiltInLootTables.DESERT_PYRAMID
                    );
                }
            }

            this.addCellar(level, box);
        }
    }

    private void addCellar(WorldGenLevel level, BoundingBox box) {
        BlockPos blockPos = new BlockPos(16, -4, 13);
        this.addCellarStairs(blockPos, level, box);
        this.addCellarRoom(blockPos, level, box);
    }

    private void addCellarStairs(BlockPos pos, WorldGenLevel level, BoundingBox box) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        BlockState blockState = Blocks.SANDSTONE_STAIRS.defaultBlockState();
        this.placeBlock(level, blockState.rotate(Rotation.COUNTERCLOCKWISE_90), 13, -1, 17, box);
        this.placeBlock(level, blockState.rotate(Rotation.COUNTERCLOCKWISE_90), 14, -2, 17, box);
        this.placeBlock(level, blockState.rotate(Rotation.COUNTERCLOCKWISE_90), 15, -3, 17, box);
        BlockState blockState1 = Blocks.SAND.defaultBlockState();
        BlockState blockState2 = Blocks.SANDSTONE.defaultBlockState();
        boolean randomBoolean = level.getRandom().nextBoolean();
        this.placeBlock(level, blockState1, x - 4, y + 4, z + 4, box);
        this.placeBlock(level, blockState1, x - 3, y + 4, z + 4, box);
        this.placeBlock(level, blockState1, x - 2, y + 4, z + 4, box);
        this.placeBlock(level, blockState1, x - 1, y + 4, z + 4, box);
        this.placeBlock(level, blockState1, x, y + 4, z + 4, box);
        this.placeBlock(level, blockState1, x - 2, y + 3, z + 4, box);
        this.placeBlock(level, randomBoolean ? blockState1 : blockState2, x - 1, y + 3, z + 4, box);
        this.placeBlock(level, !randomBoolean ? blockState1 : blockState2, x, y + 3, z + 4, box);
        this.placeBlock(level, blockState1, x - 1, y + 2, z + 4, box);
        this.placeBlock(level, blockState2, x, y + 2, z + 4, box);
        this.placeBlock(level, blockState1, x, y + 1, z + 4, box);
    }

    private void addCellarRoom(BlockPos pos, WorldGenLevel level, BoundingBox box) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        BlockState blockState = Blocks.CUT_SANDSTONE.defaultBlockState();
        BlockState blockState1 = Blocks.CHISELED_SANDSTONE.defaultBlockState();
        this.generateBox(level, box, x - 3, y + 1, z - 3, x - 3, y + 1, z + 2, blockState, blockState, true);
        this.generateBox(level, box, x + 3, y + 1, z - 3, x + 3, y + 1, z + 2, blockState, blockState, true);
        this.generateBox(level, box, x - 3, y + 1, z - 3, x + 3, y + 1, z - 2, blockState, blockState, true);
        this.generateBox(level, box, x - 3, y + 1, z + 3, x + 3, y + 1, z + 3, blockState, blockState, true);
        this.generateBox(level, box, x - 3, y + 2, z - 3, x - 3, y + 2, z + 2, blockState1, blockState1, true);
        this.generateBox(level, box, x + 3, y + 2, z - 3, x + 3, y + 2, z + 2, blockState1, blockState1, true);
        this.generateBox(level, box, x - 3, y + 2, z - 3, x + 3, y + 2, z - 2, blockState1, blockState1, true);
        this.generateBox(level, box, x - 3, y + 2, z + 3, x + 3, y + 2, z + 3, blockState1, blockState1, true);
        this.generateBox(level, box, x - 3, -1, z - 3, x - 3, -1, z + 2, blockState, blockState, true);
        this.generateBox(level, box, x + 3, -1, z - 3, x + 3, -1, z + 2, blockState, blockState, true);
        this.generateBox(level, box, x - 3, -1, z - 3, x + 3, -1, z - 2, blockState, blockState, true);
        this.generateBox(level, box, x - 3, -1, z + 3, x + 3, -1, z + 3, blockState, blockState, true);
        this.placeSandBox(x - 2, y + 1, z - 2, x + 2, y + 3, z + 2);
        this.placeCollapsedRoof(level, box, x - 2, y + 4, z - 2, x + 2, z + 2);
        BlockState blockState2 = Blocks.ORANGE_TERRACOTTA.defaultBlockState();
        BlockState blockState3 = Blocks.BLUE_TERRACOTTA.defaultBlockState();
        this.placeBlock(level, blockState3, x, y, z, box);
        this.placeBlock(level, blockState2, x + 1, y, z - 1, box);
        this.placeBlock(level, blockState2, x + 1, y, z + 1, box);
        this.placeBlock(level, blockState2, x - 1, y, z - 1, box);
        this.placeBlock(level, blockState2, x - 1, y, z + 1, box);
        this.placeBlock(level, blockState2, x + 2, y, z, box);
        this.placeBlock(level, blockState2, x - 2, y, z, box);
        this.placeBlock(level, blockState2, x, y, z + 2, box);
        this.placeBlock(level, blockState2, x, y, z - 2, box);
        this.placeBlock(level, blockState2, x + 3, y, z, box);
        this.placeSand(x + 3, y + 1, z);
        this.placeSand(x + 3, y + 2, z);
        this.placeBlock(level, blockState, x + 4, y + 1, z, box);
        this.placeBlock(level, blockState1, x + 4, y + 2, z, box);
        this.placeBlock(level, blockState2, x - 3, y, z, box);
        this.placeSand(x - 3, y + 1, z);
        this.placeSand(x - 3, y + 2, z);
        this.placeBlock(level, blockState, x - 4, y + 1, z, box);
        this.placeBlock(level, blockState1, x - 4, y + 2, z, box);
        this.placeBlock(level, blockState2, x, y, z + 3, box);
        this.placeSand(x, y + 1, z + 3);
        this.placeSand(x, y + 2, z + 3);
        this.placeBlock(level, blockState2, x, y, z - 3, box);
        this.placeSand(x, y + 1, z - 3);
        this.placeSand(x, y + 2, z - 3);
        this.placeBlock(level, blockState, x, y + 1, z - 4, box);
        this.placeBlock(level, blockState1, x, -2, z - 4, box);
    }

    private void placeSand(int x, int y, int z) {
        BlockPos worldPos = this.getWorldPos(x, y, z);
        this.potentialSuspiciousSandWorldPositions.add(worldPos);
    }

    private void placeSandBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        for (int i = minY; i <= maxY; i++) {
            for (int i1 = minX; i1 <= maxX; i1++) {
                for (int i2 = minZ; i2 <= maxZ; i2++) {
                    this.placeSand(i1, i, i2);
                }
            }
        }
    }

    private void placeCollapsedRoofPiece(WorldGenLevel level, int x, int y, int z, BoundingBox box) {
        if (level.getRandom().nextFloat() < 0.33F) {
            BlockState blockState = Blocks.SANDSTONE.defaultBlockState();
            this.placeBlock(level, blockState, x, y, z, box);
        } else {
            BlockState blockState = Blocks.SAND.defaultBlockState();
            this.placeBlock(level, blockState, x, y, z, box);
        }
    }

    private void placeCollapsedRoof(WorldGenLevel level, BoundingBox box, int minX, int y, int minZ, int maxX, int maxZ) {
        for (int i = minX; i <= maxX; i++) {
            for (int i1 = minZ; i1 <= maxZ; i1++) {
                this.placeCollapsedRoofPiece(level, i, y, i1, box);
            }
        }

        RandomSource randomSource = RandomSource.create(level.getSeed()).forkPositional().at(this.getWorldPos(minX, y, minZ));
        int i1 = randomSource.nextIntBetweenInclusive(minX, maxX);
        int randomInt = randomSource.nextIntBetweenInclusive(minZ, maxZ);
        this.randomCollapsedRoofPos = new BlockPos(this.getWorldX(i1, randomInt), this.getWorldY(y), this.getWorldZ(i1, randomInt));
    }

    public List<BlockPos> getPotentialSuspiciousSandWorldPositions() {
        return this.potentialSuspiciousSandWorldPositions;
    }

    public BlockPos getRandomCollapsedRoofPos() {
        return this.randomCollapsedRoofPos;
    }
}
