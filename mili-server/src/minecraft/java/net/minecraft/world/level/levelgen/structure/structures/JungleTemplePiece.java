package net.minecraft.world.level.levelgen.structure.structures;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TripWireBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.ScatteredFeaturePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

public class JungleTemplePiece extends ScatteredFeaturePiece {
    public static final int WIDTH = 12;
    public static final int DEPTH = 15;
    private boolean placedMainChest;
    private boolean placedHiddenChest;
    private boolean placedTrap1;
    private boolean placedTrap2;
    private static final JungleTemplePiece.MossStoneSelector STONE_SELECTOR = new JungleTemplePiece.MossStoneSelector();

    public JungleTemplePiece(RandomSource random, int x, int z) {
        super(StructurePieceType.JUNGLE_PYRAMID_PIECE, x, 64, z, 12, 10, 15, getRandomHorizontalDirection(random));
    }

    public JungleTemplePiece(CompoundTag tag) {
        super(StructurePieceType.JUNGLE_PYRAMID_PIECE, tag);
        this.placedMainChest = tag.getBooleanOr("placedMainChest", false);
        this.placedHiddenChest = tag.getBooleanOr("placedHiddenChest", false);
        this.placedTrap1 = tag.getBooleanOr("placedTrap1", false);
        this.placedTrap2 = tag.getBooleanOr("placedTrap2", false);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.putBoolean("placedMainChest", this.placedMainChest);
        tag.putBoolean("placedHiddenChest", this.placedHiddenChest);
        tag.putBoolean("placedTrap1", this.placedTrap1);
        tag.putBoolean("placedTrap2", this.placedTrap2);
    }

    @Override
    public void postProcess(
        WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox box, ChunkPos chunkPos, BlockPos pos
    ) {
        if (this.updateAverageGroundHeight(level, box, 0)) {
            this.generateBox(level, box, 0, -4, 0, this.width - 1, 0, this.depth - 1, false, random, STONE_SELECTOR);
            this.generateBox(level, box, 2, 1, 2, 9, 2, 2, false, random, STONE_SELECTOR);
            this.generateBox(level, box, 2, 1, 12, 9, 2, 12, false, random, STONE_SELECTOR);
            this.generateBox(level, box, 2, 1, 3, 2, 2, 11, false, random, STONE_SELECTOR);
            this.generateBox(level, box, 9, 1, 3, 9, 2, 11, false, random, STONE_SELECTOR);
            this.generateBox(level, box, 1, 3, 1, 10, 6, 1, false, random, STONE_SELECTOR);
            this.generateBox(level, box, 1, 3, 13, 10, 6, 13, false, random, STONE_SELECTOR);
            this.generateBox(level, box, 1, 3, 2, 1, 6, 12, false, random, STONE_SELECTOR);
            this.generateBox(level, box, 10, 3, 2, 10, 6, 12, false, random, STONE_SELECTOR);
            this.generateBox(level, box, 2, 3, 2, 9, 3, 12, false, random, STONE_SELECTOR);
            this.generateBox(level, box, 2, 6, 2, 9, 6, 12, false, random, STONE_SELECTOR);
            this.generateBox(level, box, 3, 7, 3, 8, 7, 11, false, random, STONE_SELECTOR);
            this.generateBox(level, box, 4, 8, 4, 7, 8, 10, false, random, STONE_SELECTOR);
            this.generateAirBox(level, box, 3, 1, 3, 8, 2, 11);
            this.generateAirBox(level, box, 4, 3, 6, 7, 3, 9);
            this.generateAirBox(level, box, 2, 4, 2, 9, 5, 12);
            this.generateAirBox(level, box, 4, 6, 5, 7, 6, 9);
            this.generateAirBox(level, box, 5, 7, 6, 6, 7, 8);
            this.generateAirBox(level, box, 5, 1, 2, 6, 2, 2);
            this.generateAirBox(level, box, 5, 2, 12, 6, 2, 12);
            this.generateAirBox(level, box, 5, 5, 1, 6, 5, 1);
            this.generateAirBox(level, box, 5, 5, 13, 6, 5, 13);
            this.placeBlock(level, Blocks.AIR.defaultBlockState(), 1, 5, 5, box);
            this.placeBlock(level, Blocks.AIR.defaultBlockState(), 10, 5, 5, box);
            this.placeBlock(level, Blocks.AIR.defaultBlockState(), 1, 5, 9, box);
            this.placeBlock(level, Blocks.AIR.defaultBlockState(), 10, 5, 9, box);

            for (int i = 0; i <= 14; i += 14) {
                this.generateBox(level, box, 2, 4, i, 2, 5, i, false, random, STONE_SELECTOR);
                this.generateBox(level, box, 4, 4, i, 4, 5, i, false, random, STONE_SELECTOR);
                this.generateBox(level, box, 7, 4, i, 7, 5, i, false, random, STONE_SELECTOR);
                this.generateBox(level, box, 9, 4, i, 9, 5, i, false, random, STONE_SELECTOR);
            }

            this.generateBox(level, box, 5, 6, 0, 6, 6, 0, false, random, STONE_SELECTOR);

            for (int i = 0; i <= 11; i += 11) {
                for (int i1 = 2; i1 <= 12; i1 += 2) {
                    this.generateBox(level, box, i, 4, i1, i, 5, i1, false, random, STONE_SELECTOR);
                }

                this.generateBox(level, box, i, 6, 5, i, 6, 5, false, random, STONE_SELECTOR);
                this.generateBox(level, box, i, 6, 9, i, 6, 9, false, random, STONE_SELECTOR);
            }

            this.generateBox(level, box, 2, 7, 2, 2, 9, 2, false, random, STONE_SELECTOR);
            this.generateBox(level, box, 9, 7, 2, 9, 9, 2, false, random, STONE_SELECTOR);
            this.generateBox(level, box, 2, 7, 12, 2, 9, 12, false, random, STONE_SELECTOR);
            this.generateBox(level, box, 9, 7, 12, 9, 9, 12, false, random, STONE_SELECTOR);
            this.generateBox(level, box, 4, 9, 4, 4, 9, 4, false, random, STONE_SELECTOR);
            this.generateBox(level, box, 7, 9, 4, 7, 9, 4, false, random, STONE_SELECTOR);
            this.generateBox(level, box, 4, 9, 10, 4, 9, 10, false, random, STONE_SELECTOR);
            this.generateBox(level, box, 7, 9, 10, 7, 9, 10, false, random, STONE_SELECTOR);
            this.generateBox(level, box, 5, 9, 7, 6, 9, 7, false, random, STONE_SELECTOR);
            BlockState blockState = Blocks.COBBLESTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST);
            BlockState blockState1 = Blocks.COBBLESTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST);
            BlockState blockState2 = Blocks.COBBLESTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH);
            BlockState blockState3 = Blocks.COBBLESTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
            this.placeBlock(level, blockState3, 5, 9, 6, box);
            this.placeBlock(level, blockState3, 6, 9, 6, box);
            this.placeBlock(level, blockState2, 5, 9, 8, box);
            this.placeBlock(level, blockState2, 6, 9, 8, box);
            this.placeBlock(level, blockState3, 4, 0, 0, box);
            this.placeBlock(level, blockState3, 5, 0, 0, box);
            this.placeBlock(level, blockState3, 6, 0, 0, box);
            this.placeBlock(level, blockState3, 7, 0, 0, box);
            this.placeBlock(level, blockState3, 4, 1, 8, box);
            this.placeBlock(level, blockState3, 4, 2, 9, box);
            this.placeBlock(level, blockState3, 4, 3, 10, box);
            this.placeBlock(level, blockState3, 7, 1, 8, box);
            this.placeBlock(level, blockState3, 7, 2, 9, box);
            this.placeBlock(level, blockState3, 7, 3, 10, box);
            this.generateBox(level, box, 4, 1, 9, 4, 1, 9, false, random, STONE_SELECTOR);
            this.generateBox(level, box, 7, 1, 9, 7, 1, 9, false, random, STONE_SELECTOR);
            this.generateBox(level, box, 4, 1, 10, 7, 2, 10, false, random, STONE_SELECTOR);
            this.generateBox(level, box, 5, 4, 5, 6, 4, 5, false, random, STONE_SELECTOR);
            this.placeBlock(level, blockState, 4, 4, 5, box);
            this.placeBlock(level, blockState1, 7, 4, 5, box);

            for (int i2 = 0; i2 < 4; i2++) {
                this.placeBlock(level, blockState2, 5, 0 - i2, 6 + i2, box);
                this.placeBlock(level, blockState2, 6, 0 - i2, 6 + i2, box);
                this.generateAirBox(level, box, 5, 0 - i2, 7 + i2, 6, 0 - i2, 9 + i2);
            }

            this.generateAirBox(level, box, 1, -3, 12, 10, -1, 13);
            this.generateAirBox(level, box, 1, -3, 1, 3, -1, 13);
            this.generateAirBox(level, box, 1, -3, 1, 9, -1, 5);

            for (int i2 = 1; i2 <= 13; i2 += 2) {
                this.generateBox(level, box, 1, -3, i2, 1, -2, i2, false, random, STONE_SELECTOR);
            }

            for (int i2 = 2; i2 <= 12; i2 += 2) {
                this.generateBox(level, box, 1, -1, i2, 3, -1, i2, false, random, STONE_SELECTOR);
            }

            this.generateBox(level, box, 2, -2, 1, 5, -2, 1, false, random, STONE_SELECTOR);
            this.generateBox(level, box, 7, -2, 1, 9, -2, 1, false, random, STONE_SELECTOR);
            this.generateBox(level, box, 6, -3, 1, 6, -3, 1, false, random, STONE_SELECTOR);
            this.generateBox(level, box, 6, -1, 1, 6, -1, 1, false, random, STONE_SELECTOR);
            this.placeBlock(
                level,
                Blocks.TRIPWIRE_HOOK.defaultBlockState().setValue(TripWireHookBlock.FACING, Direction.EAST).setValue(TripWireHookBlock.ATTACHED, true),
                1,
                -3,
                8,
                box
            );
            this.placeBlock(
                level,
                Blocks.TRIPWIRE_HOOK.defaultBlockState().setValue(TripWireHookBlock.FACING, Direction.WEST).setValue(TripWireHookBlock.ATTACHED, true),
                4,
                -3,
                8,
                box
            );
            this.placeBlock(
                level,
                Blocks.TRIPWIRE
                    .defaultBlockState()
                    .setValue(TripWireBlock.EAST, true)
                    .setValue(TripWireBlock.WEST, true)
                    .setValue(TripWireBlock.ATTACHED, true),
                2,
                -3,
                8,
                box
            );
            this.placeBlock(
                level,
                Blocks.TRIPWIRE
                    .defaultBlockState()
                    .setValue(TripWireBlock.EAST, true)
                    .setValue(TripWireBlock.WEST, true)
                    .setValue(TripWireBlock.ATTACHED, true),
                3,
                -3,
                8,
                box
            );
            BlockState blockState4 = Blocks.REDSTONE_WIRE
                .defaultBlockState()
                .setValue(RedStoneWireBlock.NORTH, RedstoneSide.SIDE)
                .setValue(RedStoneWireBlock.SOUTH, RedstoneSide.SIDE);
            this.placeBlock(level, blockState4, 5, -3, 7, box);
            this.placeBlock(level, blockState4, 5, -3, 6, box);
            this.placeBlock(level, blockState4, 5, -3, 5, box);
            this.placeBlock(level, blockState4, 5, -3, 4, box);
            this.placeBlock(level, blockState4, 5, -3, 3, box);
            this.placeBlock(level, blockState4, 5, -3, 2, box);
            this.placeBlock(
                level,
                Blocks.REDSTONE_WIRE
                    .defaultBlockState()
                    .setValue(RedStoneWireBlock.NORTH, RedstoneSide.SIDE)
                    .setValue(RedStoneWireBlock.WEST, RedstoneSide.SIDE),
                5,
                -3,
                1,
                box
            );
            this.placeBlock(
                level,
                Blocks.REDSTONE_WIRE
                    .defaultBlockState()
                    .setValue(RedStoneWireBlock.EAST, RedstoneSide.SIDE)
                    .setValue(RedStoneWireBlock.WEST, RedstoneSide.SIDE),
                4,
                -3,
                1,
                box
            );
            this.placeBlock(level, Blocks.MOSSY_COBBLESTONE.defaultBlockState(), 3, -3, 1, box);
            if (!this.placedTrap1) {
                this.placedTrap1 = this.createDispenser(level, box, random, 3, -2, 1, Direction.NORTH, BuiltInLootTables.JUNGLE_TEMPLE_DISPENSER);
            }

            this.placeBlock(level, Blocks.VINE.defaultBlockState().setValue(VineBlock.SOUTH, true), 3, -2, 2, box);
            this.placeBlock(
                level,
                Blocks.TRIPWIRE_HOOK.defaultBlockState().setValue(TripWireHookBlock.FACING, Direction.NORTH).setValue(TripWireHookBlock.ATTACHED, true),
                7,
                -3,
                1,
                box
            );
            this.placeBlock(
                level,
                Blocks.TRIPWIRE_HOOK.defaultBlockState().setValue(TripWireHookBlock.FACING, Direction.SOUTH).setValue(TripWireHookBlock.ATTACHED, true),
                7,
                -3,
                5,
                box
            );
            this.placeBlock(
                level,
                Blocks.TRIPWIRE
                    .defaultBlockState()
                    .setValue(TripWireBlock.NORTH, true)
                    .setValue(TripWireBlock.SOUTH, true)
                    .setValue(TripWireBlock.ATTACHED, true),
                7,
                -3,
                2,
                box
            );
            this.placeBlock(
                level,
                Blocks.TRIPWIRE
                    .defaultBlockState()
                    .setValue(TripWireBlock.NORTH, true)
                    .setValue(TripWireBlock.SOUTH, true)
                    .setValue(TripWireBlock.ATTACHED, true),
                7,
                -3,
                3,
                box
            );
            this.placeBlock(
                level,
                Blocks.TRIPWIRE
                    .defaultBlockState()
                    .setValue(TripWireBlock.NORTH, true)
                    .setValue(TripWireBlock.SOUTH, true)
                    .setValue(TripWireBlock.ATTACHED, true),
                7,
                -3,
                4,
                box
            );
            this.placeBlock(
                level,
                Blocks.REDSTONE_WIRE
                    .defaultBlockState()
                    .setValue(RedStoneWireBlock.EAST, RedstoneSide.SIDE)
                    .setValue(RedStoneWireBlock.WEST, RedstoneSide.SIDE),
                8,
                -3,
                6,
                box
            );
            this.placeBlock(
                level,
                Blocks.REDSTONE_WIRE
                    .defaultBlockState()
                    .setValue(RedStoneWireBlock.WEST, RedstoneSide.SIDE)
                    .setValue(RedStoneWireBlock.SOUTH, RedstoneSide.SIDE),
                9,
                -3,
                6,
                box
            );
            this.placeBlock(
                level,
                Blocks.REDSTONE_WIRE
                    .defaultBlockState()
                    .setValue(RedStoneWireBlock.NORTH, RedstoneSide.SIDE)
                    .setValue(RedStoneWireBlock.SOUTH, RedstoneSide.UP),
                9,
                -3,
                5,
                box
            );
            this.placeBlock(level, Blocks.MOSSY_COBBLESTONE.defaultBlockState(), 9, -3, 4, box);
            this.placeBlock(level, blockState4, 9, -2, 4, box);
            if (!this.placedTrap2) {
                this.placedTrap2 = this.createDispenser(level, box, random, 9, -2, 3, Direction.WEST, BuiltInLootTables.JUNGLE_TEMPLE_DISPENSER);
            }

            this.placeBlock(level, Blocks.VINE.defaultBlockState().setValue(VineBlock.EAST, true), 8, -1, 3, box);
            this.placeBlock(level, Blocks.VINE.defaultBlockState().setValue(VineBlock.EAST, true), 8, -2, 3, box);
            if (!this.placedMainChest) {
                this.placedMainChest = this.createChest(level, box, random, 8, -3, 3, BuiltInLootTables.JUNGLE_TEMPLE);
            }

            this.placeBlock(level, Blocks.MOSSY_COBBLESTONE.defaultBlockState(), 9, -3, 2, box);
            this.placeBlock(level, Blocks.MOSSY_COBBLESTONE.defaultBlockState(), 8, -3, 1, box);
            this.placeBlock(level, Blocks.MOSSY_COBBLESTONE.defaultBlockState(), 4, -3, 5, box);
            this.placeBlock(level, Blocks.MOSSY_COBBLESTONE.defaultBlockState(), 5, -2, 5, box);
            this.placeBlock(level, Blocks.MOSSY_COBBLESTONE.defaultBlockState(), 5, -1, 5, box);
            this.placeBlock(level, Blocks.MOSSY_COBBLESTONE.defaultBlockState(), 6, -3, 5, box);
            this.placeBlock(level, Blocks.MOSSY_COBBLESTONE.defaultBlockState(), 7, -2, 5, box);
            this.placeBlock(level, Blocks.MOSSY_COBBLESTONE.defaultBlockState(), 7, -1, 5, box);
            this.placeBlock(level, Blocks.MOSSY_COBBLESTONE.defaultBlockState(), 8, -3, 5, box);
            this.generateBox(level, box, 9, -1, 1, 9, -1, 5, false, random, STONE_SELECTOR);
            this.generateAirBox(level, box, 8, -3, 8, 10, -1, 10);
            this.placeBlock(level, Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), 8, -2, 11, box);
            this.placeBlock(level, Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), 9, -2, 11, box);
            this.placeBlock(level, Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), 10, -2, 11, box);
            BlockState blockState5 = Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACING, Direction.NORTH).setValue(LeverBlock.FACE, AttachFace.WALL);
            this.placeBlock(level, blockState5, 8, -2, 12, box);
            this.placeBlock(level, blockState5, 9, -2, 12, box);
            this.placeBlock(level, blockState5, 10, -2, 12, box);
            this.generateBox(level, box, 8, -3, 8, 8, -3, 10, false, random, STONE_SELECTOR);
            this.generateBox(level, box, 10, -3, 8, 10, -3, 10, false, random, STONE_SELECTOR);
            this.placeBlock(level, Blocks.MOSSY_COBBLESTONE.defaultBlockState(), 10, -2, 9, box);
            this.placeBlock(level, blockState4, 8, -2, 9, box);
            this.placeBlock(level, blockState4, 8, -2, 10, box);
            this.placeBlock(
                level,
                Blocks.REDSTONE_WIRE
                    .defaultBlockState()
                    .setValue(RedStoneWireBlock.NORTH, RedstoneSide.SIDE)
                    .setValue(RedStoneWireBlock.SOUTH, RedstoneSide.SIDE)
                    .setValue(RedStoneWireBlock.EAST, RedstoneSide.SIDE)
                    .setValue(RedStoneWireBlock.WEST, RedstoneSide.SIDE),
                10,
                -1,
                9,
                box
            );
            this.placeBlock(level, Blocks.STICKY_PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.UP), 9, -2, 8, box);
            this.placeBlock(level, Blocks.STICKY_PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.WEST), 10, -2, 8, box);
            this.placeBlock(level, Blocks.STICKY_PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.WEST), 10, -1, 8, box);
            this.placeBlock(level, Blocks.REPEATER.defaultBlockState().setValue(RepeaterBlock.FACING, Direction.NORTH), 10, -2, 10, box);
            if (!this.placedHiddenChest) {
                this.placedHiddenChest = this.createChest(level, box, random, 9, -3, 10, BuiltInLootTables.JUNGLE_TEMPLE);
            }
        }
    }

    static class MossStoneSelector extends StructurePiece.BlockSelector {
        @Override
        public void next(RandomSource random, int x, int y, int z, boolean wall) {
            if (random.nextFloat() < 0.4F) {
                this.next = Blocks.COBBLESTONE.defaultBlockState();
            } else {
                this.next = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
            }
        }
    }
}
