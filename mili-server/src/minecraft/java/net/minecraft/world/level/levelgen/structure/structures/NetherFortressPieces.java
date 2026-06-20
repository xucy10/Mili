package net.minecraft.world.level.levelgen.structure.structures;

import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePieceAccessor;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import org.jspecify.annotations.Nullable;

public class NetherFortressPieces {
    private static final int MAX_DEPTH = 30;
    private static final int LOWEST_Y_POSITION = 10;
    public static final int MAGIC_START_Y = 64;
    static final NetherFortressPieces.PieceWeight[] BRIDGE_PIECE_WEIGHTS = new NetherFortressPieces.PieceWeight[]{
        new NetherFortressPieces.PieceWeight(NetherFortressPieces.BridgeStraight.class, 30, 0, true),
        new NetherFortressPieces.PieceWeight(NetherFortressPieces.BridgeCrossing.class, 10, 4),
        new NetherFortressPieces.PieceWeight(NetherFortressPieces.RoomCrossing.class, 10, 4),
        new NetherFortressPieces.PieceWeight(NetherFortressPieces.StairsRoom.class, 10, 3),
        new NetherFortressPieces.PieceWeight(NetherFortressPieces.MonsterThrone.class, 5, 2),
        new NetherFortressPieces.PieceWeight(NetherFortressPieces.CastleEntrance.class, 5, 1)
    };
    static final NetherFortressPieces.PieceWeight[] CASTLE_PIECE_WEIGHTS = new NetherFortressPieces.PieceWeight[]{
        new NetherFortressPieces.PieceWeight(NetherFortressPieces.CastleSmallCorridorPiece.class, 25, 0, true),
        new NetherFortressPieces.PieceWeight(NetherFortressPieces.CastleSmallCorridorCrossingPiece.class, 15, 5),
        new NetherFortressPieces.PieceWeight(NetherFortressPieces.CastleSmallCorridorRightTurnPiece.class, 5, 10),
        new NetherFortressPieces.PieceWeight(NetherFortressPieces.CastleSmallCorridorLeftTurnPiece.class, 5, 10),
        new NetherFortressPieces.PieceWeight(NetherFortressPieces.CastleCorridorStairsPiece.class, 10, 3, true),
        new NetherFortressPieces.PieceWeight(NetherFortressPieces.CastleCorridorTBalconyPiece.class, 7, 2),
        new NetherFortressPieces.PieceWeight(NetherFortressPieces.CastleStalkRoom.class, 5, 2)
    };

    static NetherFortressPieces.@Nullable NetherBridgePiece findAndCreateBridgePieceFactory(
        NetherFortressPieces.PieceWeight weight, StructurePieceAccessor pieces, RandomSource random, int x, int y, int z, Direction orientation, int genDepth
    ) {
        Class<? extends NetherFortressPieces.NetherBridgePiece> clazz = weight.pieceClass;
        NetherFortressPieces.NetherBridgePiece netherBridgePiece = null;
        if (clazz == NetherFortressPieces.BridgeStraight.class) {
            netherBridgePiece = NetherFortressPieces.BridgeStraight.createPiece(pieces, random, x, y, z, orientation, genDepth);
        } else if (clazz == NetherFortressPieces.BridgeCrossing.class) {
            netherBridgePiece = NetherFortressPieces.BridgeCrossing.createPiece(pieces, x, y, z, orientation, genDepth);
        } else if (clazz == NetherFortressPieces.RoomCrossing.class) {
            netherBridgePiece = NetherFortressPieces.RoomCrossing.createPiece(pieces, x, y, z, orientation, genDepth);
        } else if (clazz == NetherFortressPieces.StairsRoom.class) {
            netherBridgePiece = NetherFortressPieces.StairsRoom.createPiece(pieces, x, y, z, genDepth, orientation);
        } else if (clazz == NetherFortressPieces.MonsterThrone.class) {
            netherBridgePiece = NetherFortressPieces.MonsterThrone.createPiece(pieces, x, y, z, genDepth, orientation);
        } else if (clazz == NetherFortressPieces.CastleEntrance.class) {
            netherBridgePiece = NetherFortressPieces.CastleEntrance.createPiece(pieces, random, x, y, z, orientation, genDepth);
        } else if (clazz == NetherFortressPieces.CastleSmallCorridorPiece.class) {
            netherBridgePiece = NetherFortressPieces.CastleSmallCorridorPiece.createPiece(pieces, x, y, z, orientation, genDepth);
        } else if (clazz == NetherFortressPieces.CastleSmallCorridorRightTurnPiece.class) {
            netherBridgePiece = NetherFortressPieces.CastleSmallCorridorRightTurnPiece.createPiece(pieces, random, x, y, z, orientation, genDepth);
        } else if (clazz == NetherFortressPieces.CastleSmallCorridorLeftTurnPiece.class) {
            netherBridgePiece = NetherFortressPieces.CastleSmallCorridorLeftTurnPiece.createPiece(pieces, random, x, y, z, orientation, genDepth);
        } else if (clazz == NetherFortressPieces.CastleCorridorStairsPiece.class) {
            netherBridgePiece = NetherFortressPieces.CastleCorridorStairsPiece.createPiece(pieces, x, y, z, orientation, genDepth);
        } else if (clazz == NetherFortressPieces.CastleCorridorTBalconyPiece.class) {
            netherBridgePiece = NetherFortressPieces.CastleCorridorTBalconyPiece.createPiece(pieces, x, y, z, orientation, genDepth);
        } else if (clazz == NetherFortressPieces.CastleSmallCorridorCrossingPiece.class) {
            netherBridgePiece = NetherFortressPieces.CastleSmallCorridorCrossingPiece.createPiece(pieces, x, y, z, orientation, genDepth);
        } else if (clazz == NetherFortressPieces.CastleStalkRoom.class) {
            netherBridgePiece = NetherFortressPieces.CastleStalkRoom.createPiece(pieces, x, y, z, orientation, genDepth);
        }

        return netherBridgePiece;
    }

    public static class BridgeCrossing extends NetherFortressPieces.NetherBridgePiece {
        private static final int WIDTH = 19;
        private static final int HEIGHT = 10;
        private static final int DEPTH = 19;

        public BridgeCrossing(int genDepth, BoundingBox box, Direction orientation) {
            super(StructurePieceType.NETHER_FORTRESS_BRIDGE_CROSSING, genDepth, box);
            this.setOrientation(orientation);
        }

        protected BridgeCrossing(int x, int z, Direction orientation) {
            super(StructurePieceType.NETHER_FORTRESS_BRIDGE_CROSSING, 0, StructurePiece.makeBoundingBox(x, 64, z, orientation, 19, 10, 19));
            this.setOrientation(orientation);
        }

        protected BridgeCrossing(StructurePieceType type, CompoundTag tag) {
            super(type, tag);
        }

        public BridgeCrossing(CompoundTag tag) {
            this(StructurePieceType.NETHER_FORTRESS_BRIDGE_CROSSING, tag);
        }

        @Override
        public void addChildren(StructurePiece piece, StructurePieceAccessor pieces, RandomSource random) {
            this.generateChildForward((NetherFortressPieces.StartPiece)piece, pieces, random, 8, 3, false);
            this.generateChildLeft((NetherFortressPieces.StartPiece)piece, pieces, random, 3, 8, false);
            this.generateChildRight((NetherFortressPieces.StartPiece)piece, pieces, random, 3, 8, false);
        }

        public static NetherFortressPieces.@Nullable BridgeCrossing createPiece(
            StructurePieceAccessor pieces, int x, int y, int z, Direction orientation, int genDepth
        ) {
            BoundingBox boundingBox = BoundingBox.orientBox(x, y, z, -8, -3, 0, 19, 10, 19, orientation);
            return isOkBox(boundingBox) && pieces.findCollisionPiece(boundingBox) == null
                ? new NetherFortressPieces.BridgeCrossing(genDepth, boundingBox, orientation)
                : null;
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
            this.generateBox(level, box, 7, 3, 0, 11, 4, 18, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 0, 3, 7, 18, 4, 11, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 8, 5, 0, 10, 7, 18, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.generateBox(level, box, 0, 5, 8, 18, 7, 10, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.generateBox(level, box, 7, 5, 0, 7, 5, 7, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 7, 5, 11, 7, 5, 18, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 11, 5, 0, 11, 5, 7, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 11, 5, 11, 11, 5, 18, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 0, 5, 7, 7, 5, 7, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 11, 5, 7, 18, 5, 7, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 0, 5, 11, 7, 5, 11, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 11, 5, 11, 18, 5, 11, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 7, 2, 0, 11, 2, 5, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 7, 2, 13, 11, 2, 18, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 7, 0, 0, 11, 1, 3, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 7, 0, 15, 11, 1, 18, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);

            for (int i = 7; i <= 11; i++) {
                for (int i1 = 0; i1 <= 2; i1++) {
                    this.fillColumnDown(level, Blocks.NETHER_BRICKS.defaultBlockState(), i, -1, i1, box);
                    this.fillColumnDown(level, Blocks.NETHER_BRICKS.defaultBlockState(), i, -1, 18 - i1, box);
                }
            }

            this.generateBox(level, box, 0, 2, 7, 5, 2, 11, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 13, 2, 7, 18, 2, 11, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 0, 0, 7, 3, 1, 11, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 15, 0, 7, 18, 1, 11, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);

            for (int i = 0; i <= 2; i++) {
                for (int i1 = 7; i1 <= 11; i1++) {
                    this.fillColumnDown(level, Blocks.NETHER_BRICKS.defaultBlockState(), i, -1, i1, box);
                    this.fillColumnDown(level, Blocks.NETHER_BRICKS.defaultBlockState(), 18 - i, -1, i1, box);
                }
            }
        }
    }

    public static class BridgeEndFiller extends NetherFortressPieces.NetherBridgePiece {
        private static final int WIDTH = 5;
        private static final int HEIGHT = 10;
        private static final int DEPTH = 8;
        private final int selfSeed;

        public BridgeEndFiller(int genDepth, RandomSource random, BoundingBox box, Direction orientation) {
            super(StructurePieceType.NETHER_FORTRESS_BRIDGE_END_FILLER, genDepth, box);
            this.setOrientation(orientation);
            this.selfSeed = random.nextInt();
        }

        public BridgeEndFiller(CompoundTag tag) {
            super(StructurePieceType.NETHER_FORTRESS_BRIDGE_END_FILLER, tag);
            this.selfSeed = tag.getIntOr("Seed", 0);
        }

        public static NetherFortressPieces.@Nullable BridgeEndFiller createPiece(
            StructurePieceAccessor pieces, RandomSource random, int x, int y, int z, Direction orientation, int genDepth
        ) {
            BoundingBox boundingBox = BoundingBox.orientBox(x, y, z, -1, -3, 0, 5, 10, 8, orientation);
            return isOkBox(boundingBox) && pieces.findCollisionPiece(boundingBox) == null
                ? new NetherFortressPieces.BridgeEndFiller(genDepth, random, boundingBox, orientation)
                : null;
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            super.addAdditionalSaveData(context, tag);
            tag.putInt("Seed", this.selfSeed);
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
            RandomSource randomSource = RandomSource.create(this.selfSeed);

            for (int i = 0; i <= 4; i++) {
                for (int i1 = 3; i1 <= 4; i1++) {
                    int randomInt = randomSource.nextInt(8);
                    this.generateBox(
                        level, box, i, i1, 0, i, i1, randomInt, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false
                    );
                }
            }

            int i = randomSource.nextInt(8);
            this.generateBox(level, box, 0, 5, 0, 0, 5, i, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            i = randomSource.nextInt(8);
            this.generateBox(level, box, 4, 5, 0, 4, 5, i, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);

            for (int ix = 0; ix <= 4; ix++) {
                int i1 = randomSource.nextInt(5);
                this.generateBox(level, box, ix, 2, 0, ix, 2, i1, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            }

            for (int ix = 0; ix <= 4; ix++) {
                for (int i1 = 0; i1 <= 1; i1++) {
                    int randomInt = randomSource.nextInt(3);
                    this.generateBox(
                        level, box, ix, i1, 0, ix, i1, randomInt, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false
                    );
                }
            }
        }
    }

    public static class BridgeStraight extends NetherFortressPieces.NetherBridgePiece {
        private static final int WIDTH = 5;
        private static final int HEIGHT = 10;
        private static final int DEPTH = 19;

        public BridgeStraight(int genDepth, RandomSource random, BoundingBox box, Direction orientation) {
            super(StructurePieceType.NETHER_FORTRESS_BRIDGE_STRAIGHT, genDepth, box);
            this.setOrientation(orientation);
        }

        public BridgeStraight(CompoundTag tag) {
            super(StructurePieceType.NETHER_FORTRESS_BRIDGE_STRAIGHT, tag);
        }

        @Override
        public void addChildren(StructurePiece piece, StructurePieceAccessor pieces, RandomSource random) {
            this.generateChildForward((NetherFortressPieces.StartPiece)piece, pieces, random, 1, 3, false);
        }

        public static NetherFortressPieces.@Nullable BridgeStraight createPiece(
            StructurePieceAccessor pieces, RandomSource random, int x, int y, int z, Direction orientation, int genDepth
        ) {
            BoundingBox boundingBox = BoundingBox.orientBox(x, y, z, -1, -3, 0, 5, 10, 19, orientation);
            return isOkBox(boundingBox) && pieces.findCollisionPiece(boundingBox) == null
                ? new NetherFortressPieces.BridgeStraight(genDepth, random, boundingBox, orientation)
                : null;
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
            this.generateBox(level, box, 0, 3, 0, 4, 4, 18, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 1, 5, 0, 3, 7, 18, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.generateBox(level, box, 0, 5, 0, 0, 5, 18, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 4, 5, 0, 4, 5, 18, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 0, 2, 0, 4, 2, 5, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 0, 2, 13, 4, 2, 18, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 0, 0, 0, 4, 1, 3, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 0, 0, 15, 4, 1, 18, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);

            for (int i = 0; i <= 4; i++) {
                for (int i1 = 0; i1 <= 2; i1++) {
                    this.fillColumnDown(level, Blocks.NETHER_BRICKS.defaultBlockState(), i, -1, i1, box);
                    this.fillColumnDown(level, Blocks.NETHER_BRICKS.defaultBlockState(), i, -1, 18 - i1, box);
                }
            }

            BlockState blockState = Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.NORTH, true).setValue(FenceBlock.SOUTH, true);
            BlockState blockState1 = blockState.setValue(FenceBlock.EAST, true);
            BlockState blockState2 = blockState.setValue(FenceBlock.WEST, true);
            this.generateBox(level, box, 0, 1, 1, 0, 4, 1, blockState1, blockState1, false);
            this.generateBox(level, box, 0, 3, 4, 0, 4, 4, blockState1, blockState1, false);
            this.generateBox(level, box, 0, 3, 14, 0, 4, 14, blockState1, blockState1, false);
            this.generateBox(level, box, 0, 1, 17, 0, 4, 17, blockState1, blockState1, false);
            this.generateBox(level, box, 4, 1, 1, 4, 4, 1, blockState2, blockState2, false);
            this.generateBox(level, box, 4, 3, 4, 4, 4, 4, blockState2, blockState2, false);
            this.generateBox(level, box, 4, 3, 14, 4, 4, 14, blockState2, blockState2, false);
            this.generateBox(level, box, 4, 1, 17, 4, 4, 17, blockState2, blockState2, false);
        }
    }

    public static class CastleCorridorStairsPiece extends NetherFortressPieces.NetherBridgePiece {
        private static final int WIDTH = 5;
        private static final int HEIGHT = 14;
        private static final int DEPTH = 10;

        public CastleCorridorStairsPiece(int genDepth, BoundingBox box, Direction orientation) {
            super(StructurePieceType.NETHER_FORTRESS_CASTLE_CORRIDOR_STAIRS, genDepth, box);
            this.setOrientation(orientation);
        }

        public CastleCorridorStairsPiece(CompoundTag tag) {
            super(StructurePieceType.NETHER_FORTRESS_CASTLE_CORRIDOR_STAIRS, tag);
        }

        @Override
        public void addChildren(StructurePiece piece, StructurePieceAccessor pieces, RandomSource random) {
            this.generateChildForward((NetherFortressPieces.StartPiece)piece, pieces, random, 1, 0, true);
        }

        public static NetherFortressPieces.@Nullable CastleCorridorStairsPiece createPiece(
            StructurePieceAccessor pieces, int x, int y, int z, Direction orientation, int genDepth
        ) {
            BoundingBox boundingBox = BoundingBox.orientBox(x, y, z, -1, -7, 0, 5, 14, 10, orientation);
            return isOkBox(boundingBox) && pieces.findCollisionPiece(boundingBox) == null
                ? new NetherFortressPieces.CastleCorridorStairsPiece(genDepth, boundingBox, orientation)
                : null;
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
            BlockState blockState = Blocks.NETHER_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH);
            BlockState blockState1 = Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.NORTH, true).setValue(FenceBlock.SOUTH, true);

            for (int i = 0; i <= 9; i++) {
                int max = Math.max(1, 7 - i);
                int min = Math.min(Math.max(max + 5, 14 - i), 13);
                int i1 = i;
                this.generateBox(level, box, 0, 0, i, 4, max, i, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
                this.generateBox(level, box, 1, max + 1, i, 3, min - 1, i, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
                if (i <= 6) {
                    this.placeBlock(level, blockState, 1, max + 1, i, box);
                    this.placeBlock(level, blockState, 2, max + 1, i, box);
                    this.placeBlock(level, blockState, 3, max + 1, i, box);
                }

                this.generateBox(level, box, 0, min, i, 4, min, i, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
                this.generateBox(
                    level, box, 0, max + 1, i, 0, min - 1, i, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false
                );
                this.generateBox(
                    level, box, 4, max + 1, i, 4, min - 1, i, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false
                );
                if ((i & 1) == 0) {
                    this.generateBox(level, box, 0, max + 2, i, 0, max + 3, i, blockState1, blockState1, false);
                    this.generateBox(level, box, 4, max + 2, i, 4, max + 3, i, blockState1, blockState1, false);
                }

                for (int i2 = 0; i2 <= 4; i2++) {
                    this.fillColumnDown(level, Blocks.NETHER_BRICKS.defaultBlockState(), i2, -1, i1, box);
                }
            }
        }
    }

    public static class CastleCorridorTBalconyPiece extends NetherFortressPieces.NetherBridgePiece {
        private static final int WIDTH = 9;
        private static final int HEIGHT = 7;
        private static final int DEPTH = 9;

        public CastleCorridorTBalconyPiece(int genDepth, BoundingBox box, Direction orientation) {
            super(StructurePieceType.NETHER_FORTRESS_CASTLE_CORRIDOR_T_BALCONY, genDepth, box);
            this.setOrientation(orientation);
        }

        public CastleCorridorTBalconyPiece(CompoundTag tag) {
            super(StructurePieceType.NETHER_FORTRESS_CASTLE_CORRIDOR_T_BALCONY, tag);
        }

        @Override
        public void addChildren(StructurePiece piece, StructurePieceAccessor pieces, RandomSource random) {
            int i = 1;
            Direction orientation = this.getOrientation();
            if (orientation == Direction.WEST || orientation == Direction.NORTH) {
                i = 5;
            }

            this.generateChildLeft((NetherFortressPieces.StartPiece)piece, pieces, random, 0, i, random.nextInt(8) > 0);
            this.generateChildRight((NetherFortressPieces.StartPiece)piece, pieces, random, 0, i, random.nextInt(8) > 0);
        }

        public static NetherFortressPieces.@Nullable CastleCorridorTBalconyPiece createPiece(
            StructurePieceAccessor pieces, int x, int y, int z, Direction orientation, int genDepth
        ) {
            BoundingBox boundingBox = BoundingBox.orientBox(x, y, z, -3, 0, 0, 9, 7, 9, orientation);
            return isOkBox(boundingBox) && pieces.findCollisionPiece(boundingBox) == null
                ? new NetherFortressPieces.CastleCorridorTBalconyPiece(genDepth, boundingBox, orientation)
                : null;
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
            BlockState blockState = Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.NORTH, true).setValue(FenceBlock.SOUTH, true);
            BlockState blockState1 = Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.WEST, true).setValue(FenceBlock.EAST, true);
            this.generateBox(level, box, 0, 0, 0, 8, 1, 8, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 0, 2, 0, 8, 5, 8, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.generateBox(level, box, 0, 6, 0, 8, 6, 5, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 0, 2, 0, 2, 5, 0, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 6, 2, 0, 8, 5, 0, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 1, 3, 0, 1, 4, 0, blockState1, blockState1, false);
            this.generateBox(level, box, 7, 3, 0, 7, 4, 0, blockState1, blockState1, false);
            this.generateBox(level, box, 0, 2, 4, 8, 2, 8, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 1, 1, 4, 2, 2, 4, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.generateBox(level, box, 6, 1, 4, 7, 2, 4, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.generateBox(level, box, 1, 3, 8, 7, 3, 8, blockState1, blockState1, false);
            this.placeBlock(level, Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.EAST, true).setValue(FenceBlock.SOUTH, true), 0, 3, 8, box);
            this.placeBlock(level, Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.WEST, true).setValue(FenceBlock.SOUTH, true), 8, 3, 8, box);
            this.generateBox(level, box, 0, 3, 6, 0, 3, 7, blockState, blockState, false);
            this.generateBox(level, box, 8, 3, 6, 8, 3, 7, blockState, blockState, false);
            this.generateBox(level, box, 0, 3, 4, 0, 5, 5, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 8, 3, 4, 8, 5, 5, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 1, 3, 5, 2, 5, 5, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 6, 3, 5, 7, 5, 5, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 1, 4, 5, 1, 5, 5, blockState1, blockState1, false);
            this.generateBox(level, box, 7, 4, 5, 7, 5, 5, blockState1, blockState1, false);

            for (int i = 0; i <= 5; i++) {
                for (int i1 = 0; i1 <= 8; i1++) {
                    this.fillColumnDown(level, Blocks.NETHER_BRICKS.defaultBlockState(), i1, -1, i, box);
                }
            }
        }
    }

    public static class CastleEntrance extends NetherFortressPieces.NetherBridgePiece {
        private static final int WIDTH = 13;
        private static final int HEIGHT = 14;
        private static final int DEPTH = 13;

        public CastleEntrance(int genDepth, RandomSource random, BoundingBox box, Direction orientation) {
            super(StructurePieceType.NETHER_FORTRESS_CASTLE_ENTRANCE, genDepth, box);
            this.setOrientation(orientation);
        }

        public CastleEntrance(CompoundTag tag) {
            super(StructurePieceType.NETHER_FORTRESS_CASTLE_ENTRANCE, tag);
        }

        @Override
        public void addChildren(StructurePiece piece, StructurePieceAccessor pieces, RandomSource random) {
            this.generateChildForward((NetherFortressPieces.StartPiece)piece, pieces, random, 5, 3, true);
        }

        public static NetherFortressPieces.@Nullable CastleEntrance createPiece(
            StructurePieceAccessor pieces, RandomSource random, int x, int y, int z, Direction orientation, int genDepth
        ) {
            BoundingBox boundingBox = BoundingBox.orientBox(x, y, z, -5, -3, 0, 13, 14, 13, orientation);
            return isOkBox(boundingBox) && pieces.findCollisionPiece(boundingBox) == null
                ? new NetherFortressPieces.CastleEntrance(genDepth, random, boundingBox, orientation)
                : null;
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
            this.generateBox(level, box, 0, 3, 0, 12, 4, 12, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 0, 5, 0, 12, 13, 12, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.generateBox(level, box, 0, 5, 0, 1, 12, 12, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 11, 5, 0, 12, 12, 12, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 2, 5, 11, 4, 12, 12, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 8, 5, 11, 10, 12, 12, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 5, 9, 11, 7, 12, 12, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 2, 5, 0, 4, 12, 1, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 8, 5, 0, 10, 12, 1, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 5, 9, 0, 7, 12, 1, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 2, 11, 2, 10, 12, 10, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 5, 8, 0, 7, 8, 0, Blocks.NETHER_BRICK_FENCE.defaultBlockState(), Blocks.NETHER_BRICK_FENCE.defaultBlockState(), false);
            BlockState blockState = Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.WEST, true).setValue(FenceBlock.EAST, true);
            BlockState blockState1 = Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.NORTH, true).setValue(FenceBlock.SOUTH, true);

            for (int i = 1; i <= 11; i += 2) {
                this.generateBox(level, box, i, 10, 0, i, 11, 0, blockState, blockState, false);
                this.generateBox(level, box, i, 10, 12, i, 11, 12, blockState, blockState, false);
                this.generateBox(level, box, 0, 10, i, 0, 11, i, blockState1, blockState1, false);
                this.generateBox(level, box, 12, 10, i, 12, 11, i, blockState1, blockState1, false);
                this.placeBlock(level, Blocks.NETHER_BRICKS.defaultBlockState(), i, 13, 0, box);
                this.placeBlock(level, Blocks.NETHER_BRICKS.defaultBlockState(), i, 13, 12, box);
                this.placeBlock(level, Blocks.NETHER_BRICKS.defaultBlockState(), 0, 13, i, box);
                this.placeBlock(level, Blocks.NETHER_BRICKS.defaultBlockState(), 12, 13, i, box);
                if (i != 11) {
                    this.placeBlock(level, blockState, i + 1, 13, 0, box);
                    this.placeBlock(level, blockState, i + 1, 13, 12, box);
                    this.placeBlock(level, blockState1, 0, 13, i + 1, box);
                    this.placeBlock(level, blockState1, 12, 13, i + 1, box);
                }
            }

            this.placeBlock(
                level, Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.NORTH, true).setValue(FenceBlock.EAST, true), 0, 13, 0, box
            );
            this.placeBlock(
                level, Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.SOUTH, true).setValue(FenceBlock.EAST, true), 0, 13, 12, box
            );
            this.placeBlock(
                level, Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.SOUTH, true).setValue(FenceBlock.WEST, true), 12, 13, 12, box
            );
            this.placeBlock(
                level, Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.NORTH, true).setValue(FenceBlock.WEST, true), 12, 13, 0, box
            );

            for (int ix = 3; ix <= 9; ix += 2) {
                this.generateBox(
                    level, box, 1, 7, ix, 1, 8, ix, blockState1.setValue(FenceBlock.WEST, true), blockState1.setValue(FenceBlock.WEST, true), false
                );
                this.generateBox(
                    level, box, 11, 7, ix, 11, 8, ix, blockState1.setValue(FenceBlock.EAST, true), blockState1.setValue(FenceBlock.EAST, true), false
                );
            }

            this.generateBox(level, box, 4, 2, 0, 8, 2, 12, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 0, 2, 4, 12, 2, 8, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 4, 0, 0, 8, 1, 3, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 4, 0, 9, 8, 1, 12, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 0, 0, 4, 3, 1, 8, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 9, 0, 4, 12, 1, 8, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);

            for (int ix = 4; ix <= 8; ix++) {
                for (int i1 = 0; i1 <= 2; i1++) {
                    this.fillColumnDown(level, Blocks.NETHER_BRICKS.defaultBlockState(), ix, -1, i1, box);
                    this.fillColumnDown(level, Blocks.NETHER_BRICKS.defaultBlockState(), ix, -1, 12 - i1, box);
                }
            }

            for (int ix = 0; ix <= 2; ix++) {
                for (int i1 = 4; i1 <= 8; i1++) {
                    this.fillColumnDown(level, Blocks.NETHER_BRICKS.defaultBlockState(), ix, -1, i1, box);
                    this.fillColumnDown(level, Blocks.NETHER_BRICKS.defaultBlockState(), 12 - ix, -1, i1, box);
                }
            }

            this.generateBox(level, box, 5, 5, 5, 7, 5, 7, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 6, 1, 6, 6, 4, 6, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.placeBlock(level, Blocks.NETHER_BRICKS.defaultBlockState(), 6, 0, 6, box);
            this.placeBlock(level, Blocks.LAVA.defaultBlockState(), 6, 5, 6, box);
            BlockPos worldPos = this.getWorldPos(6, 5, 6);
            if (box.isInside(worldPos)) {
                level.scheduleTick(worldPos, Fluids.LAVA, 0);
            }
        }
    }

    public static class CastleSmallCorridorCrossingPiece extends NetherFortressPieces.NetherBridgePiece {
        private static final int WIDTH = 5;
        private static final int HEIGHT = 7;
        private static final int DEPTH = 5;

        public CastleSmallCorridorCrossingPiece(int genDepth, BoundingBox box, Direction orientation) {
            super(StructurePieceType.NETHER_FORTRESS_CASTLE_SMALL_CORRIDOR_CROSSING, genDepth, box);
            this.setOrientation(orientation);
        }

        public CastleSmallCorridorCrossingPiece(CompoundTag tag) {
            super(StructurePieceType.NETHER_FORTRESS_CASTLE_SMALL_CORRIDOR_CROSSING, tag);
        }

        @Override
        public void addChildren(StructurePiece piece, StructurePieceAccessor pieces, RandomSource random) {
            this.generateChildForward((NetherFortressPieces.StartPiece)piece, pieces, random, 1, 0, true);
            this.generateChildLeft((NetherFortressPieces.StartPiece)piece, pieces, random, 0, 1, true);
            this.generateChildRight((NetherFortressPieces.StartPiece)piece, pieces, random, 0, 1, true);
        }

        public static NetherFortressPieces.@Nullable CastleSmallCorridorCrossingPiece createPiece(
            StructurePieceAccessor pieces, int x, int y, int z, Direction orientation, int genDepth
        ) {
            BoundingBox boundingBox = BoundingBox.orientBox(x, y, z, -1, 0, 0, 5, 7, 5, orientation);
            return isOkBox(boundingBox) && pieces.findCollisionPiece(boundingBox) == null
                ? new NetherFortressPieces.CastleSmallCorridorCrossingPiece(genDepth, boundingBox, orientation)
                : null;
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
            this.generateBox(level, box, 0, 0, 0, 4, 1, 4, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 0, 2, 0, 4, 5, 4, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.generateBox(level, box, 0, 2, 0, 0, 5, 0, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 4, 2, 0, 4, 5, 0, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 0, 2, 4, 0, 5, 4, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 4, 2, 4, 4, 5, 4, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 0, 6, 0, 4, 6, 4, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);

            for (int i = 0; i <= 4; i++) {
                for (int i1 = 0; i1 <= 4; i1++) {
                    this.fillColumnDown(level, Blocks.NETHER_BRICKS.defaultBlockState(), i, -1, i1, box);
                }
            }
        }
    }

    public static class CastleSmallCorridorLeftTurnPiece extends NetherFortressPieces.NetherBridgePiece {
        private static final int WIDTH = 5;
        private static final int HEIGHT = 7;
        private static final int DEPTH = 5;
        private boolean isNeedingChest;

        public CastleSmallCorridorLeftTurnPiece(int genDepth, RandomSource random, BoundingBox box, Direction orientation) {
            super(StructurePieceType.NETHER_FORTRESS_CASTLE_SMALL_CORRIDOR_LEFT_TURN, genDepth, box);
            this.setOrientation(orientation);
            this.isNeedingChest = random.nextInt(3) == 0;
        }

        public CastleSmallCorridorLeftTurnPiece(CompoundTag tag) {
            super(StructurePieceType.NETHER_FORTRESS_CASTLE_SMALL_CORRIDOR_LEFT_TURN, tag);
            this.isNeedingChest = tag.getBooleanOr("Chest", false);
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            super.addAdditionalSaveData(context, tag);
            tag.putBoolean("Chest", this.isNeedingChest);
        }

        @Override
        public void addChildren(StructurePiece piece, StructurePieceAccessor pieces, RandomSource random) {
            this.generateChildLeft((NetherFortressPieces.StartPiece)piece, pieces, random, 0, 1, true);
        }

        public static NetherFortressPieces.@Nullable CastleSmallCorridorLeftTurnPiece createPiece(
            StructurePieceAccessor pieces, RandomSource random, int x, int y, int z, Direction orientation, int genDepth
        ) {
            BoundingBox boundingBox = BoundingBox.orientBox(x, y, z, -1, 0, 0, 5, 7, 5, orientation);
            return isOkBox(boundingBox) && pieces.findCollisionPiece(boundingBox) == null
                ? new NetherFortressPieces.CastleSmallCorridorLeftTurnPiece(genDepth, random, boundingBox, orientation)
                : null;
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
            this.generateBox(level, box, 0, 0, 0, 4, 1, 4, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 0, 2, 0, 4, 5, 4, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            BlockState blockState = Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.WEST, true).setValue(FenceBlock.EAST, true);
            BlockState blockState1 = Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.NORTH, true).setValue(FenceBlock.SOUTH, true);
            this.generateBox(level, box, 4, 2, 0, 4, 5, 4, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 4, 3, 1, 4, 4, 1, blockState1, blockState1, false);
            this.generateBox(level, box, 4, 3, 3, 4, 4, 3, blockState1, blockState1, false);
            this.generateBox(level, box, 0, 2, 0, 0, 5, 0, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 0, 2, 4, 3, 5, 4, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 1, 3, 4, 1, 4, 4, blockState, blockState, false);
            this.generateBox(level, box, 3, 3, 4, 3, 4, 4, blockState, blockState, false);
            if (this.isNeedingChest && box.isInside(this.getWorldPos(3, 2, 3))) {
                this.isNeedingChest = false;
                this.createChest(level, box, random, 3, 2, 3, BuiltInLootTables.NETHER_BRIDGE);
            }

            this.generateBox(level, box, 0, 6, 0, 4, 6, 4, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);

            for (int i = 0; i <= 4; i++) {
                for (int i1 = 0; i1 <= 4; i1++) {
                    this.fillColumnDown(level, Blocks.NETHER_BRICKS.defaultBlockState(), i, -1, i1, box);
                }
            }
        }
    }

    public static class CastleSmallCorridorPiece extends NetherFortressPieces.NetherBridgePiece {
        private static final int WIDTH = 5;
        private static final int HEIGHT = 7;
        private static final int DEPTH = 5;

        public CastleSmallCorridorPiece(int genDepth, BoundingBox box, Direction orientation) {
            super(StructurePieceType.NETHER_FORTRESS_CASTLE_SMALL_CORRIDOR, genDepth, box);
            this.setOrientation(orientation);
        }

        public CastleSmallCorridorPiece(CompoundTag tag) {
            super(StructurePieceType.NETHER_FORTRESS_CASTLE_SMALL_CORRIDOR, tag);
        }

        @Override
        public void addChildren(StructurePiece piece, StructurePieceAccessor pieces, RandomSource random) {
            this.generateChildForward((NetherFortressPieces.StartPiece)piece, pieces, random, 1, 0, true);
        }

        public static NetherFortressPieces.@Nullable CastleSmallCorridorPiece createPiece(
            StructurePieceAccessor pieces, int x, int y, int z, Direction orientation, int genDepth
        ) {
            BoundingBox boundingBox = BoundingBox.orientBox(x, y, z, -1, 0, 0, 5, 7, 5, orientation);
            return isOkBox(boundingBox) && pieces.findCollisionPiece(boundingBox) == null
                ? new NetherFortressPieces.CastleSmallCorridorPiece(genDepth, boundingBox, orientation)
                : null;
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
            this.generateBox(level, box, 0, 0, 0, 4, 1, 4, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 0, 2, 0, 4, 5, 4, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            BlockState blockState = Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.NORTH, true).setValue(FenceBlock.SOUTH, true);
            this.generateBox(level, box, 0, 2, 0, 0, 5, 4, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 4, 2, 0, 4, 5, 4, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 0, 3, 1, 0, 4, 1, blockState, blockState, false);
            this.generateBox(level, box, 0, 3, 3, 0, 4, 3, blockState, blockState, false);
            this.generateBox(level, box, 4, 3, 1, 4, 4, 1, blockState, blockState, false);
            this.generateBox(level, box, 4, 3, 3, 4, 4, 3, blockState, blockState, false);
            this.generateBox(level, box, 0, 6, 0, 4, 6, 4, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);

            for (int i = 0; i <= 4; i++) {
                for (int i1 = 0; i1 <= 4; i1++) {
                    this.fillColumnDown(level, Blocks.NETHER_BRICKS.defaultBlockState(), i, -1, i1, box);
                }
            }
        }
    }

    public static class CastleSmallCorridorRightTurnPiece extends NetherFortressPieces.NetherBridgePiece {
        private static final int WIDTH = 5;
        private static final int HEIGHT = 7;
        private static final int DEPTH = 5;
        private boolean isNeedingChest;

        public CastleSmallCorridorRightTurnPiece(int genDepth, RandomSource random, BoundingBox box, Direction orientation) {
            super(StructurePieceType.NETHER_FORTRESS_CASTLE_SMALL_CORRIDOR_RIGHT_TURN, genDepth, box);
            this.setOrientation(orientation);
            this.isNeedingChest = random.nextInt(3) == 0;
        }

        public CastleSmallCorridorRightTurnPiece(CompoundTag tag) {
            super(StructurePieceType.NETHER_FORTRESS_CASTLE_SMALL_CORRIDOR_RIGHT_TURN, tag);
            this.isNeedingChest = tag.getBooleanOr("Chest", false);
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            super.addAdditionalSaveData(context, tag);
            tag.putBoolean("Chest", this.isNeedingChest);
        }

        @Override
        public void addChildren(StructurePiece piece, StructurePieceAccessor pieces, RandomSource random) {
            this.generateChildRight((NetherFortressPieces.StartPiece)piece, pieces, random, 0, 1, true);
        }

        public static NetherFortressPieces.@Nullable CastleSmallCorridorRightTurnPiece createPiece(
            StructurePieceAccessor pieces, RandomSource random, int x, int y, int z, Direction orientation, int genDepth
        ) {
            BoundingBox boundingBox = BoundingBox.orientBox(x, y, z, -1, 0, 0, 5, 7, 5, orientation);
            return isOkBox(boundingBox) && pieces.findCollisionPiece(boundingBox) == null
                ? new NetherFortressPieces.CastleSmallCorridorRightTurnPiece(genDepth, random, boundingBox, orientation)
                : null;
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
            this.generateBox(level, box, 0, 0, 0, 4, 1, 4, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 0, 2, 0, 4, 5, 4, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            BlockState blockState = Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.WEST, true).setValue(FenceBlock.EAST, true);
            BlockState blockState1 = Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.NORTH, true).setValue(FenceBlock.SOUTH, true);
            this.generateBox(level, box, 0, 2, 0, 0, 5, 4, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 0, 3, 1, 0, 4, 1, blockState1, blockState1, false);
            this.generateBox(level, box, 0, 3, 3, 0, 4, 3, blockState1, blockState1, false);
            this.generateBox(level, box, 4, 2, 0, 4, 5, 0, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 1, 2, 4, 4, 5, 4, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 1, 3, 4, 1, 4, 4, blockState, blockState, false);
            this.generateBox(level, box, 3, 3, 4, 3, 4, 4, blockState, blockState, false);
            if (this.isNeedingChest && box.isInside(this.getWorldPos(1, 2, 3))) {
                this.isNeedingChest = false;
                this.createChest(level, box, random, 1, 2, 3, BuiltInLootTables.NETHER_BRIDGE);
            }

            this.generateBox(level, box, 0, 6, 0, 4, 6, 4, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);

            for (int i = 0; i <= 4; i++) {
                for (int i1 = 0; i1 <= 4; i1++) {
                    this.fillColumnDown(level, Blocks.NETHER_BRICKS.defaultBlockState(), i, -1, i1, box);
                }
            }
        }
    }

    public static class CastleStalkRoom extends NetherFortressPieces.NetherBridgePiece {
        private static final int WIDTH = 13;
        private static final int HEIGHT = 14;
        private static final int DEPTH = 13;

        public CastleStalkRoom(int genDepth, BoundingBox box, Direction orientation) {
            super(StructurePieceType.NETHER_FORTRESS_CASTLE_STALK_ROOM, genDepth, box);
            this.setOrientation(orientation);
        }

        public CastleStalkRoom(CompoundTag tag) {
            super(StructurePieceType.NETHER_FORTRESS_CASTLE_STALK_ROOM, tag);
        }

        @Override
        public void addChildren(StructurePiece piece, StructurePieceAccessor pieces, RandomSource random) {
            this.generateChildForward((NetherFortressPieces.StartPiece)piece, pieces, random, 5, 3, true);
            this.generateChildForward((NetherFortressPieces.StartPiece)piece, pieces, random, 5, 11, true);
        }

        public static NetherFortressPieces.@Nullable CastleStalkRoom createPiece(
            StructurePieceAccessor pieces, int x, int y, int z, Direction orientation, int genDepth
        ) {
            BoundingBox boundingBox = BoundingBox.orientBox(x, y, z, -5, -3, 0, 13, 14, 13, orientation);
            return isOkBox(boundingBox) && pieces.findCollisionPiece(boundingBox) == null
                ? new NetherFortressPieces.CastleStalkRoom(genDepth, boundingBox, orientation)
                : null;
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
            this.generateBox(level, box, 0, 3, 0, 12, 4, 12, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 0, 5, 0, 12, 13, 12, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.generateBox(level, box, 0, 5, 0, 1, 12, 12, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 11, 5, 0, 12, 12, 12, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 2, 5, 11, 4, 12, 12, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 8, 5, 11, 10, 12, 12, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 5, 9, 11, 7, 12, 12, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 2, 5, 0, 4, 12, 1, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 8, 5, 0, 10, 12, 1, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 5, 9, 0, 7, 12, 1, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 2, 11, 2, 10, 12, 10, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            BlockState blockState = Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.WEST, true).setValue(FenceBlock.EAST, true);
            BlockState blockState1 = Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.NORTH, true).setValue(FenceBlock.SOUTH, true);
            BlockState blockState2 = blockState1.setValue(FenceBlock.WEST, true);
            BlockState blockState3 = blockState1.setValue(FenceBlock.EAST, true);

            for (int i = 1; i <= 11; i += 2) {
                this.generateBox(level, box, i, 10, 0, i, 11, 0, blockState, blockState, false);
                this.generateBox(level, box, i, 10, 12, i, 11, 12, blockState, blockState, false);
                this.generateBox(level, box, 0, 10, i, 0, 11, i, blockState1, blockState1, false);
                this.generateBox(level, box, 12, 10, i, 12, 11, i, blockState1, blockState1, false);
                this.placeBlock(level, Blocks.NETHER_BRICKS.defaultBlockState(), i, 13, 0, box);
                this.placeBlock(level, Blocks.NETHER_BRICKS.defaultBlockState(), i, 13, 12, box);
                this.placeBlock(level, Blocks.NETHER_BRICKS.defaultBlockState(), 0, 13, i, box);
                this.placeBlock(level, Blocks.NETHER_BRICKS.defaultBlockState(), 12, 13, i, box);
                if (i != 11) {
                    this.placeBlock(level, blockState, i + 1, 13, 0, box);
                    this.placeBlock(level, blockState, i + 1, 13, 12, box);
                    this.placeBlock(level, blockState1, 0, 13, i + 1, box);
                    this.placeBlock(level, blockState1, 12, 13, i + 1, box);
                }
            }

            this.placeBlock(
                level, Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.NORTH, true).setValue(FenceBlock.EAST, true), 0, 13, 0, box
            );
            this.placeBlock(
                level, Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.SOUTH, true).setValue(FenceBlock.EAST, true), 0, 13, 12, box
            );
            this.placeBlock(
                level, Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.SOUTH, true).setValue(FenceBlock.WEST, true), 12, 13, 12, box
            );
            this.placeBlock(
                level, Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.NORTH, true).setValue(FenceBlock.WEST, true), 12, 13, 0, box
            );

            for (int ix = 3; ix <= 9; ix += 2) {
                this.generateBox(level, box, 1, 7, ix, 1, 8, ix, blockState2, blockState2, false);
                this.generateBox(level, box, 11, 7, ix, 11, 8, ix, blockState3, blockState3, false);
            }

            BlockState blockState4 = Blocks.NETHER_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);

            for (int i1 = 0; i1 <= 6; i1++) {
                int i2 = i1 + 4;

                for (int i3 = 5; i3 <= 7; i3++) {
                    this.placeBlock(level, blockState4, i3, 5 + i1, i2, box);
                }

                if (i2 >= 5 && i2 <= 8) {
                    this.generateBox(
                        level, box, 5, 5, i2, 7, i1 + 4, i2, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false
                    );
                } else if (i2 >= 9 && i2 <= 10) {
                    this.generateBox(
                        level, box, 5, 8, i2, 7, i1 + 4, i2, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false
                    );
                }

                if (i1 >= 1) {
                    this.generateBox(level, box, 5, 6 + i1, i2, 7, 9 + i1, i2, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
                }
            }

            for (int i1 = 5; i1 <= 7; i1++) {
                this.placeBlock(level, blockState4, i1, 12, 11, box);
            }

            this.generateBox(level, box, 5, 6, 7, 5, 7, 7, blockState3, blockState3, false);
            this.generateBox(level, box, 7, 6, 7, 7, 7, 7, blockState2, blockState2, false);
            this.generateBox(level, box, 5, 13, 12, 7, 13, 12, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.generateBox(level, box, 2, 5, 2, 3, 5, 3, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 2, 5, 9, 3, 5, 10, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 2, 5, 4, 2, 5, 8, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 9, 5, 2, 10, 5, 3, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 9, 5, 9, 10, 5, 10, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 10, 5, 4, 10, 5, 8, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            BlockState blockState5 = blockState4.setValue(StairBlock.FACING, Direction.EAST);
            BlockState blockState6 = blockState4.setValue(StairBlock.FACING, Direction.WEST);
            this.placeBlock(level, blockState6, 4, 5, 2, box);
            this.placeBlock(level, blockState6, 4, 5, 3, box);
            this.placeBlock(level, blockState6, 4, 5, 9, box);
            this.placeBlock(level, blockState6, 4, 5, 10, box);
            this.placeBlock(level, blockState5, 8, 5, 2, box);
            this.placeBlock(level, blockState5, 8, 5, 3, box);
            this.placeBlock(level, blockState5, 8, 5, 9, box);
            this.placeBlock(level, blockState5, 8, 5, 10, box);
            this.generateBox(level, box, 3, 4, 4, 4, 4, 8, Blocks.SOUL_SAND.defaultBlockState(), Blocks.SOUL_SAND.defaultBlockState(), false);
            this.generateBox(level, box, 8, 4, 4, 9, 4, 8, Blocks.SOUL_SAND.defaultBlockState(), Blocks.SOUL_SAND.defaultBlockState(), false);
            this.generateBox(level, box, 3, 5, 4, 4, 5, 8, Blocks.NETHER_WART.defaultBlockState(), Blocks.NETHER_WART.defaultBlockState(), false);
            this.generateBox(level, box, 8, 5, 4, 9, 5, 8, Blocks.NETHER_WART.defaultBlockState(), Blocks.NETHER_WART.defaultBlockState(), false);
            this.generateBox(level, box, 4, 2, 0, 8, 2, 12, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 0, 2, 4, 12, 2, 8, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 4, 0, 0, 8, 1, 3, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 4, 0, 9, 8, 1, 12, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 0, 0, 4, 3, 1, 8, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 9, 0, 4, 12, 1, 8, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);

            for (int i3 = 4; i3 <= 8; i3++) {
                for (int i4 = 0; i4 <= 2; i4++) {
                    this.fillColumnDown(level, Blocks.NETHER_BRICKS.defaultBlockState(), i3, -1, i4, box);
                    this.fillColumnDown(level, Blocks.NETHER_BRICKS.defaultBlockState(), i3, -1, 12 - i4, box);
                }
            }

            for (int i3 = 0; i3 <= 2; i3++) {
                for (int i4 = 4; i4 <= 8; i4++) {
                    this.fillColumnDown(level, Blocks.NETHER_BRICKS.defaultBlockState(), i3, -1, i4, box);
                    this.fillColumnDown(level, Blocks.NETHER_BRICKS.defaultBlockState(), 12 - i3, -1, i4, box);
                }
            }
        }
    }

    public static class MonsterThrone extends NetherFortressPieces.NetherBridgePiece {
        private static final int WIDTH = 7;
        private static final int HEIGHT = 8;
        private static final int DEPTH = 9;
        private boolean hasPlacedSpawner;

        public MonsterThrone(int genDepth, BoundingBox box, Direction orientation) {
            super(StructurePieceType.NETHER_FORTRESS_MONSTER_THRONE, genDepth, box);
            this.setOrientation(orientation);
        }

        public MonsterThrone(CompoundTag tag) {
            super(StructurePieceType.NETHER_FORTRESS_MONSTER_THRONE, tag);
            this.hasPlacedSpawner = tag.getBooleanOr("Mob", false);
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            super.addAdditionalSaveData(context, tag);
            tag.putBoolean("Mob", this.hasPlacedSpawner);
        }

        public static NetherFortressPieces.@Nullable MonsterThrone createPiece(
            StructurePieceAccessor pieces, int x, int y, int z, int genDepth, Direction orientation
        ) {
            BoundingBox boundingBox = BoundingBox.orientBox(x, y, z, -2, 0, 0, 7, 8, 9, orientation);
            return isOkBox(boundingBox) && pieces.findCollisionPiece(boundingBox) == null
                ? new NetherFortressPieces.MonsterThrone(genDepth, boundingBox, orientation)
                : null;
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
            this.generateBox(level, box, 0, 2, 0, 6, 7, 7, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.generateBox(level, box, 1, 0, 0, 5, 1, 7, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 1, 2, 1, 5, 2, 7, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 1, 3, 2, 5, 3, 7, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 1, 4, 3, 5, 4, 7, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 1, 2, 0, 1, 4, 2, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 5, 2, 0, 5, 4, 2, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 1, 5, 2, 1, 5, 3, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 5, 5, 2, 5, 5, 3, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 0, 5, 3, 0, 5, 8, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 6, 5, 3, 6, 5, 8, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 1, 5, 8, 5, 5, 8, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            BlockState blockState = Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.WEST, true).setValue(FenceBlock.EAST, true);
            BlockState blockState1 = Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.NORTH, true).setValue(FenceBlock.SOUTH, true);
            this.placeBlock(level, Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.WEST, true), 1, 6, 3, box);
            this.placeBlock(level, Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.EAST, true), 5, 6, 3, box);
            this.placeBlock(level, Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.EAST, true).setValue(FenceBlock.NORTH, true), 0, 6, 3, box);
            this.placeBlock(level, Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.WEST, true).setValue(FenceBlock.NORTH, true), 6, 6, 3, box);
            this.generateBox(level, box, 0, 6, 4, 0, 6, 7, blockState1, blockState1, false);
            this.generateBox(level, box, 6, 6, 4, 6, 6, 7, blockState1, blockState1, false);
            this.placeBlock(level, Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.EAST, true).setValue(FenceBlock.SOUTH, true), 0, 6, 8, box);
            this.placeBlock(level, Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.WEST, true).setValue(FenceBlock.SOUTH, true), 6, 6, 8, box);
            this.generateBox(level, box, 1, 6, 8, 5, 6, 8, blockState, blockState, false);
            this.placeBlock(level, Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.EAST, true), 1, 7, 8, box);
            this.generateBox(level, box, 2, 7, 8, 4, 7, 8, blockState, blockState, false);
            this.placeBlock(level, Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.WEST, true), 5, 7, 8, box);
            this.placeBlock(level, Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.EAST, true), 2, 8, 8, box);
            this.placeBlock(level, blockState, 3, 8, 8, box);
            this.placeBlock(level, Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.WEST, true), 4, 8, 8, box);
            if (!this.hasPlacedSpawner) {
                BlockPos worldPos = this.getWorldPos(3, 5, 5);
                if (box.isInside(worldPos)) {
                    this.hasPlacedSpawner = true;
                    // CraftBukkit start
                    // level.setBlock(worldPos, Blocks.SPAWNER.defaultBlockState(), Block.UPDATE_CLIENTS);
                    // if (level.getBlockEntity(worldPos) instanceof SpawnerBlockEntity spawnerBlockEntity) {
                    //     spawnerBlockEntity.setEntityId(EntityType.BLAZE, random);
                    // }
                    this.placeCraftSpawner(level, worldPos, org.bukkit.entity.EntityType.BLAZE, Block.UPDATE_CLIENTS);
                    // CraftBukkit end
                }
            }

            for (int i = 0; i <= 6; i++) {
                for (int i1 = 0; i1 <= 6; i1++) {
                    this.fillColumnDown(level, Blocks.NETHER_BRICKS.defaultBlockState(), i, -1, i1, box);
                }
            }
        }
    }

    abstract static class NetherBridgePiece extends StructurePiece {
        protected NetherBridgePiece(StructurePieceType type, int genDepth, BoundingBox boundingBox) {
            super(type, genDepth, boundingBox);
        }

        public NetherBridgePiece(StructurePieceType type, CompoundTag tag) {
            super(type, tag);
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        }

        private int updatePieceWeight(List<NetherFortressPieces.PieceWeight> weights) {
            boolean flag = false;
            int i = 0;

            for (NetherFortressPieces.PieceWeight pieceWeight : weights) {
                if (pieceWeight.maxPlaceCount > 0 && pieceWeight.placeCount < pieceWeight.maxPlaceCount) {
                    flag = true;
                }

                i += pieceWeight.weight;
            }

            return flag ? i : -1;
        }

        private NetherFortressPieces.@Nullable NetherBridgePiece generatePiece(
            NetherFortressPieces.StartPiece startPiece,
            List<NetherFortressPieces.PieceWeight> weights,
            StructurePieceAccessor pieces,
            RandomSource random,
            int x,
            int y,
            int z,
            Direction orientation,
            int genDepth
        ) {
            int i = this.updatePieceWeight(weights);
            boolean flag = i > 0 && genDepth <= 30;
            int i1 = 0;

            while (i1 < 5 && flag) {
                i1++;
                int randomInt = random.nextInt(i);

                for (NetherFortressPieces.PieceWeight pieceWeight : weights) {
                    randomInt -= pieceWeight.weight;
                    if (randomInt < 0) {
                        if (!pieceWeight.doPlace(genDepth) || pieceWeight == startPiece.previousPiece && !pieceWeight.allowInRow) {
                            break;
                        }

                        NetherFortressPieces.NetherBridgePiece netherBridgePiece = NetherFortressPieces.findAndCreateBridgePieceFactory(
                            pieceWeight, pieces, random, x, y, z, orientation, genDepth
                        );
                        if (netherBridgePiece != null) {
                            pieceWeight.placeCount++;
                            startPiece.previousPiece = pieceWeight;
                            if (!pieceWeight.isValid()) {
                                weights.remove(pieceWeight);
                            }

                            return netherBridgePiece;
                        }
                    }
                }
            }

            return NetherFortressPieces.BridgeEndFiller.createPiece(pieces, random, x, y, z, orientation, genDepth);
        }

        private @Nullable StructurePiece generateAndAddPiece(
            NetherFortressPieces.StartPiece startPiece,
            StructurePieceAccessor pieces,
            RandomSource random,
            int x,
            int y,
            int z,
            Direction orientation,
            int genDepth,
            boolean castlePiece
        ) {
            if (Math.abs(x - startPiece.getBoundingBox().minX()) <= 112 && Math.abs(z - startPiece.getBoundingBox().minZ()) <= 112) {
                List<NetherFortressPieces.PieceWeight> list = startPiece.availableBridgePieces;
                if (castlePiece) {
                    list = startPiece.availableCastlePieces;
                }

                StructurePiece structurePiece = this.generatePiece(startPiece, list, pieces, random, x, y, z, orientation, genDepth + 1);
                if (structurePiece != null) {
                    pieces.addPiece(structurePiece);
                    startPiece.pendingChildren.add(structurePiece);
                }

                return structurePiece;
            } else {
                return NetherFortressPieces.BridgeEndFiller.createPiece(pieces, random, x, y, z, orientation, genDepth);
            }
        }

        protected @Nullable StructurePiece generateChildForward(
            NetherFortressPieces.StartPiece startPiece, StructurePieceAccessor pieces, RandomSource random, int offsetX, int offsetY, boolean castlePiece
        ) {
            Direction orientation = this.getOrientation();
            if (orientation != null) {
                switch (orientation) {
                    case NORTH:
                        return this.generateAndAddPiece(
                            startPiece,
                            pieces,
                            random,
                            this.boundingBox.minX() + offsetX,
                            this.boundingBox.minY() + offsetY,
                            this.boundingBox.minZ() - 1,
                            orientation,
                            this.getGenDepth(),
                            castlePiece
                        );
                    case SOUTH:
                        return this.generateAndAddPiece(
                            startPiece,
                            pieces,
                            random,
                            this.boundingBox.minX() + offsetX,
                            this.boundingBox.minY() + offsetY,
                            this.boundingBox.maxZ() + 1,
                            orientation,
                            this.getGenDepth(),
                            castlePiece
                        );
                    case WEST:
                        return this.generateAndAddPiece(
                            startPiece,
                            pieces,
                            random,
                            this.boundingBox.minX() - 1,
                            this.boundingBox.minY() + offsetY,
                            this.boundingBox.minZ() + offsetX,
                            orientation,
                            this.getGenDepth(),
                            castlePiece
                        );
                    case EAST:
                        return this.generateAndAddPiece(
                            startPiece,
                            pieces,
                            random,
                            this.boundingBox.maxX() + 1,
                            this.boundingBox.minY() + offsetY,
                            this.boundingBox.minZ() + offsetX,
                            orientation,
                            this.getGenDepth(),
                            castlePiece
                        );
                }
            }

            return null;
        }

        protected @Nullable StructurePiece generateChildLeft(
            NetherFortressPieces.StartPiece startPiece, StructurePieceAccessor pieces, RandomSource random, int offsetY, int offsetX, boolean castlePiece
        ) {
            Direction orientation = this.getOrientation();
            if (orientation != null) {
                switch (orientation) {
                    case NORTH:
                        return this.generateAndAddPiece(
                            startPiece,
                            pieces,
                            random,
                            this.boundingBox.minX() - 1,
                            this.boundingBox.minY() + offsetY,
                            this.boundingBox.minZ() + offsetX,
                            Direction.WEST,
                            this.getGenDepth(),
                            castlePiece
                        );
                    case SOUTH:
                        return this.generateAndAddPiece(
                            startPiece,
                            pieces,
                            random,
                            this.boundingBox.minX() - 1,
                            this.boundingBox.minY() + offsetY,
                            this.boundingBox.minZ() + offsetX,
                            Direction.WEST,
                            this.getGenDepth(),
                            castlePiece
                        );
                    case WEST:
                        return this.generateAndAddPiece(
                            startPiece,
                            pieces,
                            random,
                            this.boundingBox.minX() + offsetX,
                            this.boundingBox.minY() + offsetY,
                            this.boundingBox.minZ() - 1,
                            Direction.NORTH,
                            this.getGenDepth(),
                            castlePiece
                        );
                    case EAST:
                        return this.generateAndAddPiece(
                            startPiece,
                            pieces,
                            random,
                            this.boundingBox.minX() + offsetX,
                            this.boundingBox.minY() + offsetY,
                            this.boundingBox.minZ() - 1,
                            Direction.NORTH,
                            this.getGenDepth(),
                            castlePiece
                        );
                }
            }

            return null;
        }

        protected @Nullable StructurePiece generateChildRight(
            NetherFortressPieces.StartPiece startPiece, StructurePieceAccessor pieces, RandomSource random, int offsetY, int offsetX, boolean castlePiece
        ) {
            Direction orientation = this.getOrientation();
            if (orientation != null) {
                switch (orientation) {
                    case NORTH:
                        return this.generateAndAddPiece(
                            startPiece,
                            pieces,
                            random,
                            this.boundingBox.maxX() + 1,
                            this.boundingBox.minY() + offsetY,
                            this.boundingBox.minZ() + offsetX,
                            Direction.EAST,
                            this.getGenDepth(),
                            castlePiece
                        );
                    case SOUTH:
                        return this.generateAndAddPiece(
                            startPiece,
                            pieces,
                            random,
                            this.boundingBox.maxX() + 1,
                            this.boundingBox.minY() + offsetY,
                            this.boundingBox.minZ() + offsetX,
                            Direction.EAST,
                            this.getGenDepth(),
                            castlePiece
                        );
                    case WEST:
                        return this.generateAndAddPiece(
                            startPiece,
                            pieces,
                            random,
                            this.boundingBox.minX() + offsetX,
                            this.boundingBox.minY() + offsetY,
                            this.boundingBox.maxZ() + 1,
                            Direction.SOUTH,
                            this.getGenDepth(),
                            castlePiece
                        );
                    case EAST:
                        return this.generateAndAddPiece(
                            startPiece,
                            pieces,
                            random,
                            this.boundingBox.minX() + offsetX,
                            this.boundingBox.minY() + offsetY,
                            this.boundingBox.maxZ() + 1,
                            Direction.SOUTH,
                            this.getGenDepth(),
                            castlePiece
                        );
                }
            }

            return null;
        }

        protected static boolean isOkBox(BoundingBox box) {
            return box.minY() > 10;
        }
    }

    static class PieceWeight {
        public final Class<? extends NetherFortressPieces.NetherBridgePiece> pieceClass;
        public final int weight;
        public int placeCount;
        public final int maxPlaceCount;
        public final boolean allowInRow;

        public PieceWeight(Class<? extends NetherFortressPieces.NetherBridgePiece> pieceClass, int weight, int maxPlaceCount, boolean allowInRow) {
            this.pieceClass = pieceClass;
            this.weight = weight;
            this.maxPlaceCount = maxPlaceCount;
            this.allowInRow = allowInRow;
        }

        public PieceWeight(Class<? extends NetherFortressPieces.NetherBridgePiece> pieceClass, int weight, int maxPlaceCount) {
            this(pieceClass, weight, maxPlaceCount, false);
        }

        public boolean doPlace(int genDepth) {
            return this.maxPlaceCount == 0 || this.placeCount < this.maxPlaceCount;
        }

        public boolean isValid() {
            return this.maxPlaceCount == 0 || this.placeCount < this.maxPlaceCount;
        }
    }

    public static class RoomCrossing extends NetherFortressPieces.NetherBridgePiece {
        private static final int WIDTH = 7;
        private static final int HEIGHT = 9;
        private static final int DEPTH = 7;

        public RoomCrossing(int genDepth, BoundingBox box, Direction orientation) {
            super(StructurePieceType.NETHER_FORTRESS_ROOM_CROSSING, genDepth, box);
            this.setOrientation(orientation);
        }

        public RoomCrossing(CompoundTag tag) {
            super(StructurePieceType.NETHER_FORTRESS_ROOM_CROSSING, tag);
        }

        @Override
        public void addChildren(StructurePiece piece, StructurePieceAccessor pieces, RandomSource random) {
            this.generateChildForward((NetherFortressPieces.StartPiece)piece, pieces, random, 2, 0, false);
            this.generateChildLeft((NetherFortressPieces.StartPiece)piece, pieces, random, 0, 2, false);
            this.generateChildRight((NetherFortressPieces.StartPiece)piece, pieces, random, 0, 2, false);
        }

        public static NetherFortressPieces.@Nullable RoomCrossing createPiece(
            StructurePieceAccessor pieces, int x, int y, int z, Direction orientation, int genDepth
        ) {
            BoundingBox boundingBox = BoundingBox.orientBox(x, y, z, -2, 0, 0, 7, 9, 7, orientation);
            return isOkBox(boundingBox) && pieces.findCollisionPiece(boundingBox) == null
                ? new NetherFortressPieces.RoomCrossing(genDepth, boundingBox, orientation)
                : null;
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
            this.generateBox(level, box, 0, 0, 0, 6, 1, 6, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 0, 2, 0, 6, 7, 6, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.generateBox(level, box, 0, 2, 0, 1, 6, 0, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 0, 2, 6, 1, 6, 6, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 5, 2, 0, 6, 6, 0, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 5, 2, 6, 6, 6, 6, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 0, 2, 0, 0, 6, 1, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 0, 2, 5, 0, 6, 6, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 6, 2, 0, 6, 6, 1, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 6, 2, 5, 6, 6, 6, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            BlockState blockState = Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.WEST, true).setValue(FenceBlock.EAST, true);
            BlockState blockState1 = Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.NORTH, true).setValue(FenceBlock.SOUTH, true);
            this.generateBox(level, box, 2, 6, 0, 4, 6, 0, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 2, 5, 0, 4, 5, 0, blockState, blockState, false);
            this.generateBox(level, box, 2, 6, 6, 4, 6, 6, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 2, 5, 6, 4, 5, 6, blockState, blockState, false);
            this.generateBox(level, box, 0, 6, 2, 0, 6, 4, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 0, 5, 2, 0, 5, 4, blockState1, blockState1, false);
            this.generateBox(level, box, 6, 6, 2, 6, 6, 4, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 6, 5, 2, 6, 5, 4, blockState1, blockState1, false);

            for (int i = 0; i <= 6; i++) {
                for (int i1 = 0; i1 <= 6; i1++) {
                    this.fillColumnDown(level, Blocks.NETHER_BRICKS.defaultBlockState(), i, -1, i1, box);
                }
            }
        }
    }

    public static class StairsRoom extends NetherFortressPieces.NetherBridgePiece {
        private static final int WIDTH = 7;
        private static final int HEIGHT = 11;
        private static final int DEPTH = 7;

        public StairsRoom(int genDepth, BoundingBox box, Direction orientation) {
            super(StructurePieceType.NETHER_FORTRESS_STAIRS_ROOM, genDepth, box);
            this.setOrientation(orientation);
        }

        public StairsRoom(CompoundTag tag) {
            super(StructurePieceType.NETHER_FORTRESS_STAIRS_ROOM, tag);
        }

        @Override
        public void addChildren(StructurePiece piece, StructurePieceAccessor pieces, RandomSource random) {
            this.generateChildRight((NetherFortressPieces.StartPiece)piece, pieces, random, 6, 2, false);
        }

        public static NetherFortressPieces.@Nullable StairsRoom createPiece(
            StructurePieceAccessor pieces, int x, int y, int z, int genDepth, Direction orientation
        ) {
            BoundingBox boundingBox = BoundingBox.orientBox(x, y, z, -2, 0, 0, 7, 11, 7, orientation);
            return isOkBox(boundingBox) && pieces.findCollisionPiece(boundingBox) == null
                ? new NetherFortressPieces.StairsRoom(genDepth, boundingBox, orientation)
                : null;
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
            this.generateBox(level, box, 0, 0, 0, 6, 1, 6, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 0, 2, 0, 6, 10, 6, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.generateBox(level, box, 0, 2, 0, 1, 8, 0, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 5, 2, 0, 6, 8, 0, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 0, 2, 1, 0, 8, 6, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 6, 2, 1, 6, 8, 6, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 1, 2, 6, 5, 8, 6, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            BlockState blockState = Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.WEST, true).setValue(FenceBlock.EAST, true);
            BlockState blockState1 = Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.NORTH, true).setValue(FenceBlock.SOUTH, true);
            this.generateBox(level, box, 0, 3, 2, 0, 5, 4, blockState1, blockState1, false);
            this.generateBox(level, box, 6, 3, 2, 6, 5, 2, blockState1, blockState1, false);
            this.generateBox(level, box, 6, 3, 4, 6, 5, 4, blockState1, blockState1, false);
            this.placeBlock(level, Blocks.NETHER_BRICKS.defaultBlockState(), 5, 2, 5, box);
            this.generateBox(level, box, 4, 2, 5, 4, 3, 5, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 3, 2, 5, 3, 4, 5, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 2, 2, 5, 2, 5, 5, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 1, 2, 5, 1, 6, 5, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 1, 7, 1, 5, 7, 4, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 6, 8, 2, 6, 8, 4, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.generateBox(level, box, 2, 6, 0, 4, 8, 0, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(level, box, 2, 5, 0, 4, 5, 0, blockState, blockState, false);

            for (int i = 0; i <= 6; i++) {
                for (int i1 = 0; i1 <= 6; i1++) {
                    this.fillColumnDown(level, Blocks.NETHER_BRICKS.defaultBlockState(), i, -1, i1, box);
                }
            }
        }
    }

    public static class StartPiece extends NetherFortressPieces.BridgeCrossing {
        NetherFortressPieces.@Nullable PieceWeight previousPiece;
        final List<NetherFortressPieces.PieceWeight> availableBridgePieces = new ArrayList<>();
        final List<NetherFortressPieces.PieceWeight> availableCastlePieces = new ArrayList<>();
        public final List<StructurePiece> pendingChildren = Lists.newArrayList();

        public StartPiece(RandomSource random, int x, int z) {
            super(x, z, getRandomHorizontalDirection(random));

            for (NetherFortressPieces.PieceWeight pieceWeight : NetherFortressPieces.BRIDGE_PIECE_WEIGHTS) {
                pieceWeight.placeCount = 0;
                this.availableBridgePieces.add(pieceWeight);
            }

            for (NetherFortressPieces.PieceWeight pieceWeight : NetherFortressPieces.CASTLE_PIECE_WEIGHTS) {
                pieceWeight.placeCount = 0;
                this.availableCastlePieces.add(pieceWeight);
            }
        }

        public StartPiece(CompoundTag tag) {
            super(StructurePieceType.NETHER_FORTRESS_START, tag);
        }
    }
}
