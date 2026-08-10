package net.minecraft.world.level.levelgen.structure.structures;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.minecart.MinecartChest;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePieceAccessor;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jspecify.annotations.Nullable;

public class MineshaftPieces {
    private static final int DEFAULT_SHAFT_WIDTH = 3;
    private static final int DEFAULT_SHAFT_HEIGHT = 3;
    private static final int DEFAULT_SHAFT_LENGTH = 5;
    private static final int MAX_PILLAR_HEIGHT = 20;
    private static final int MAX_CHAIN_HEIGHT = 50;
    private static final int MAX_DEPTH = 8;
    public static final int MAGIC_START_Y = 50;

    private static MineshaftPieces.@Nullable MineShaftPiece createRandomShaftPiece(
        StructurePieceAccessor pieces, RandomSource random, int x, int y, int z, Direction orientation, int genDepth, MineshaftStructure.Type type
    ) {
        int randomInt = random.nextInt(100);
        if (randomInt >= 80) {
            BoundingBox boundingBox = MineshaftPieces.MineShaftCrossing.findCrossing(pieces, random, x, y, z, orientation);
            if (boundingBox != null) {
                return new MineshaftPieces.MineShaftCrossing(genDepth, boundingBox, orientation, type);
            }
        } else if (randomInt >= 70) {
            BoundingBox boundingBox = MineshaftPieces.MineShaftStairs.findStairs(pieces, random, x, y, z, orientation);
            if (boundingBox != null) {
                return new MineshaftPieces.MineShaftStairs(genDepth, boundingBox, orientation, type);
            }
        } else {
            BoundingBox boundingBox = MineshaftPieces.MineShaftCorridor.findCorridorSize(pieces, random, x, y, z, orientation);
            if (boundingBox != null) {
                return new MineshaftPieces.MineShaftCorridor(genDepth, random, boundingBox, orientation, type);
            }
        }

        return null;
    }

    static MineshaftPieces.@Nullable MineShaftPiece generateAndAddPiece(
        StructurePiece piece, StructurePieceAccessor pieces, RandomSource random, int x, int y, int z, Direction direction, int genDepth
    ) {
        if (genDepth > 8) {
            return null;
        } else if (Math.abs(x - piece.getBoundingBox().minX()) <= 80 && Math.abs(z - piece.getBoundingBox().minZ()) <= 80) {
            MineshaftStructure.Type type = ((MineshaftPieces.MineShaftPiece)piece).type;
            MineshaftPieces.MineShaftPiece mineShaftPiece = createRandomShaftPiece(pieces, random, x, y, z, direction, genDepth + 1, type);
            if (mineShaftPiece != null) {
                pieces.addPiece(mineShaftPiece);
                mineShaftPiece.addChildren(piece, pieces, random);
            }

            return mineShaftPiece;
        } else {
            return null;
        }
    }

    public static class MineShaftCorridor extends MineshaftPieces.MineShaftPiece {
        private final boolean hasRails;
        private final boolean spiderCorridor;
        private boolean hasPlacedSpider;
        private final int numSections;

        public MineShaftCorridor(CompoundTag tag) {
            super(StructurePieceType.MINE_SHAFT_CORRIDOR, tag);
            this.hasRails = tag.getBooleanOr("hr", false);
            this.spiderCorridor = tag.getBooleanOr("sc", false);
            this.hasPlacedSpider = tag.getBooleanOr("hps", false);
            this.numSections = tag.getIntOr("Num", 0);
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            super.addAdditionalSaveData(context, tag);
            tag.putBoolean("hr", this.hasRails);
            tag.putBoolean("sc", this.spiderCorridor);
            tag.putBoolean("hps", this.hasPlacedSpider);
            tag.putInt("Num", this.numSections);
        }

        public MineShaftCorridor(int genDepth, RandomSource random, BoundingBox boundingBox, Direction orientation, MineshaftStructure.Type type) {
            super(StructurePieceType.MINE_SHAFT_CORRIDOR, genDepth, type, boundingBox);
            this.setOrientation(orientation);
            this.hasRails = random.nextInt(3) == 0;
            this.spiderCorridor = !this.hasRails && random.nextInt(23) == 0;
            if (this.getOrientation().getAxis() == Direction.Axis.Z) {
                this.numSections = boundingBox.getZSpan() / 5;
            } else {
                this.numSections = boundingBox.getXSpan() / 5;
            }
        }

        public static @Nullable BoundingBox findCorridorSize(StructurePieceAccessor pieces, RandomSource random, int x, int y, int z, Direction direction) {
            for (int i = random.nextInt(3) + 2; i > 0; i--) {
                int i1 = i * 5;

                BoundingBox boundingBox = switch (direction) {
                    default -> new BoundingBox(0, 0, -(i1 - 1), 2, 2, 0);
                    case SOUTH -> new BoundingBox(0, 0, 0, 2, 2, i1 - 1);
                    case WEST -> new BoundingBox(-(i1 - 1), 0, 0, 0, 2, 2);
                    case EAST -> new BoundingBox(0, 0, 0, i1 - 1, 2, 2);
                };
                boundingBox.move(x, y, z);
                if (pieces.findCollisionPiece(boundingBox) == null) {
                    return boundingBox;
                }
            }

            return null;
        }

        @Override
        public void addChildren(StructurePiece piece, StructurePieceAccessor pieces, RandomSource random) {
            int genDepth = this.getGenDepth();
            int randomInt = random.nextInt(4);
            Direction orientation = this.getOrientation();
            if (orientation != null) {
                switch (orientation) {
                    case NORTH:
                    default:
                        if (randomInt <= 1) {
                            MineshaftPieces.generateAndAddPiece(
                                piece,
                                pieces,
                                random,
                                this.boundingBox.minX(),
                                this.boundingBox.minY() - 1 + random.nextInt(3),
                                this.boundingBox.minZ() - 1,
                                orientation,
                                genDepth
                            );
                        } else if (randomInt == 2) {
                            MineshaftPieces.generateAndAddPiece(
                                piece,
                                pieces,
                                random,
                                this.boundingBox.minX() - 1,
                                this.boundingBox.minY() - 1 + random.nextInt(3),
                                this.boundingBox.minZ(),
                                Direction.WEST,
                                genDepth
                            );
                        } else {
                            MineshaftPieces.generateAndAddPiece(
                                piece,
                                pieces,
                                random,
                                this.boundingBox.maxX() + 1,
                                this.boundingBox.minY() - 1 + random.nextInt(3),
                                this.boundingBox.minZ(),
                                Direction.EAST,
                                genDepth
                            );
                        }
                        break;
                    case SOUTH:
                        if (randomInt <= 1) {
                            MineshaftPieces.generateAndAddPiece(
                                piece,
                                pieces,
                                random,
                                this.boundingBox.minX(),
                                this.boundingBox.minY() - 1 + random.nextInt(3),
                                this.boundingBox.maxZ() + 1,
                                orientation,
                                genDepth
                            );
                        } else if (randomInt == 2) {
                            MineshaftPieces.generateAndAddPiece(
                                piece,
                                pieces,
                                random,
                                this.boundingBox.minX() - 1,
                                this.boundingBox.minY() - 1 + random.nextInt(3),
                                this.boundingBox.maxZ() - 3,
                                Direction.WEST,
                                genDepth
                            );
                        } else {
                            MineshaftPieces.generateAndAddPiece(
                                piece,
                                pieces,
                                random,
                                this.boundingBox.maxX() + 1,
                                this.boundingBox.minY() - 1 + random.nextInt(3),
                                this.boundingBox.maxZ() - 3,
                                Direction.EAST,
                                genDepth
                            );
                        }
                        break;
                    case WEST:
                        if (randomInt <= 1) {
                            MineshaftPieces.generateAndAddPiece(
                                piece,
                                pieces,
                                random,
                                this.boundingBox.minX() - 1,
                                this.boundingBox.minY() - 1 + random.nextInt(3),
                                this.boundingBox.minZ(),
                                orientation,
                                genDepth
                            );
                        } else if (randomInt == 2) {
                            MineshaftPieces.generateAndAddPiece(
                                piece,
                                pieces,
                                random,
                                this.boundingBox.minX(),
                                this.boundingBox.minY() - 1 + random.nextInt(3),
                                this.boundingBox.minZ() - 1,
                                Direction.NORTH,
                                genDepth
                            );
                        } else {
                            MineshaftPieces.generateAndAddPiece(
                                piece,
                                pieces,
                                random,
                                this.boundingBox.minX(),
                                this.boundingBox.minY() - 1 + random.nextInt(3),
                                this.boundingBox.maxZ() + 1,
                                Direction.SOUTH,
                                genDepth
                            );
                        }
                        break;
                    case EAST:
                        if (randomInt <= 1) {
                            MineshaftPieces.generateAndAddPiece(
                                piece,
                                pieces,
                                random,
                                this.boundingBox.maxX() + 1,
                                this.boundingBox.minY() - 1 + random.nextInt(3),
                                this.boundingBox.minZ(),
                                orientation,
                                genDepth
                            );
                        } else if (randomInt == 2) {
                            MineshaftPieces.generateAndAddPiece(
                                piece,
                                pieces,
                                random,
                                this.boundingBox.maxX() - 3,
                                this.boundingBox.minY() - 1 + random.nextInt(3),
                                this.boundingBox.minZ() - 1,
                                Direction.NORTH,
                                genDepth
                            );
                        } else {
                            MineshaftPieces.generateAndAddPiece(
                                piece,
                                pieces,
                                random,
                                this.boundingBox.maxX() - 3,
                                this.boundingBox.minY() - 1 + random.nextInt(3),
                                this.boundingBox.maxZ() + 1,
                                Direction.SOUTH,
                                genDepth
                            );
                        }
                }
            }

            if (genDepth < 8) {
                if (orientation != Direction.NORTH && orientation != Direction.SOUTH) {
                    for (int i = this.boundingBox.minX() + 3; i + 3 <= this.boundingBox.maxX(); i += 5) {
                        int randomInt1 = random.nextInt(5);
                        if (randomInt1 == 0) {
                            MineshaftPieces.generateAndAddPiece(
                                piece, pieces, random, i, this.boundingBox.minY(), this.boundingBox.minZ() - 1, Direction.NORTH, genDepth + 1
                            );
                        } else if (randomInt1 == 1) {
                            MineshaftPieces.generateAndAddPiece(
                                piece, pieces, random, i, this.boundingBox.minY(), this.boundingBox.maxZ() + 1, Direction.SOUTH, genDepth + 1
                            );
                        }
                    }
                } else {
                    for (int ix = this.boundingBox.minZ() + 3; ix + 3 <= this.boundingBox.maxZ(); ix += 5) {
                        int randomInt1 = random.nextInt(5);
                        if (randomInt1 == 0) {
                            MineshaftPieces.generateAndAddPiece(
                                piece, pieces, random, this.boundingBox.minX() - 1, this.boundingBox.minY(), ix, Direction.WEST, genDepth + 1
                            );
                        } else if (randomInt1 == 1) {
                            MineshaftPieces.generateAndAddPiece(
                                piece, pieces, random, this.boundingBox.maxX() + 1, this.boundingBox.minY(), ix, Direction.EAST, genDepth + 1
                            );
                        }
                    }
                }
            }
        }

        @Override
        protected boolean createChest(WorldGenLevel level, BoundingBox box, RandomSource random, int x, int y, int z, ResourceKey<LootTable> lootTable) {
            BlockPos worldPos = this.getWorldPos(x, y, z);
            if (box.isInside(worldPos) && level.getBlockState(worldPos).isAir() && !level.getBlockState(worldPos.below()).isAir()) {
                BlockState blockState = Blocks.RAIL
                    .defaultBlockState()
                    .setValue(RailBlock.SHAPE, random.nextBoolean() ? RailShape.NORTH_SOUTH : RailShape.EAST_WEST);
                this.placeBlock(level, blockState, x, y, z, box);
                MinecartChest minecartChest = EntityType.CHEST_MINECART.create(level.getLevel(), EntitySpawnReason.CHUNK_GENERATION);
                if (minecartChest != null) {
                    minecartChest.setInitialPos(worldPos.getX() + 0.5, worldPos.getY() + 0.5, worldPos.getZ() + 0.5);
                    minecartChest.setLootTable(lootTable, random.nextLong());
                    level.addFreshEntity(minecartChest);
                }

                return true;
            } else {
                return false;
            }
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
            if (!this.isInInvalidLocation(level, box)) {
                int i = 0;
                int i1 = 2;
                int i2 = 0;
                int i3 = 2;
                int i4 = this.numSections * 5 - 1;
                BlockState planksState = this.type.getPlanksState();
                this.generateBox(level, box, 0, 0, 0, 2, 1, i4, CAVE_AIR, CAVE_AIR, false);
                this.generateMaybeBox(level, box, random, 0.8F, 0, 2, 0, 2, 2, i4, CAVE_AIR, CAVE_AIR, false, false);
                if (this.spiderCorridor) {
                    this.generateMaybeBox(level, box, random, 0.6F, 0, 0, 0, 2, 1, i4, Blocks.COBWEB.defaultBlockState(), CAVE_AIR, false, true);
                }

                for (int i5 = 0; i5 < this.numSections; i5++) {
                    int i6 = 2 + i5 * 5;
                    this.placeSupport(level, box, 0, 0, i6, 2, 2, random);
                    this.maybePlaceCobWeb(level, box, random, 0.1F, 0, 2, i6 - 1);
                    this.maybePlaceCobWeb(level, box, random, 0.1F, 2, 2, i6 - 1);
                    this.maybePlaceCobWeb(level, box, random, 0.1F, 0, 2, i6 + 1);
                    this.maybePlaceCobWeb(level, box, random, 0.1F, 2, 2, i6 + 1);
                    this.maybePlaceCobWeb(level, box, random, 0.05F, 0, 2, i6 - 2);
                    this.maybePlaceCobWeb(level, box, random, 0.05F, 2, 2, i6 - 2);
                    this.maybePlaceCobWeb(level, box, random, 0.05F, 0, 2, i6 + 2);
                    this.maybePlaceCobWeb(level, box, random, 0.05F, 2, 2, i6 + 2);
                    if (random.nextInt(100) == 0) {
                        this.createChest(level, box, random, 2, 0, i6 - 1, BuiltInLootTables.ABANDONED_MINESHAFT);
                    }

                    if (random.nextInt(100) == 0) {
                        this.createChest(level, box, random, 0, 0, i6 + 1, BuiltInLootTables.ABANDONED_MINESHAFT);
                    }

                    if (this.spiderCorridor && !this.hasPlacedSpider) {
                        int i7 = 1;
                        int i8 = i6 - 1 + random.nextInt(3);
                        BlockPos worldPos = this.getWorldPos(1, 0, i8);
                        if (box.isInside(worldPos) && this.isInterior(level, 1, 0, i8, box)) {
                            this.hasPlacedSpider = true;
                            // CraftBukkit start
                            // level.setBlock(worldPos, Blocks.SPAWNER.defaultBlockState(), Block.UPDATE_CLIENTS);
                            // if (level.getBlockEntity(worldPos) instanceof SpawnerBlockEntity spawnerBlockEntity) {
                            //     spawnerBlockEntity.setEntityId(EntityType.CAVE_SPIDER, random);
                            // }
                            this.placeCraftSpawner(level, worldPos, org.bukkit.entity.EntityType.CAVE_SPIDER, Block.UPDATE_CLIENTS);
                            // CraftBukkit end
                        }
                    }
                }

                for (int i5 = 0; i5 <= 2; i5++) {
                    for (int i6x = 0; i6x <= i4; i6x++) {
                        this.setPlanksBlock(level, box, planksState, i5, -1, i6x);
                    }
                }

                int i5 = 2;
                this.placeDoubleLowerOrUpperSupport(level, box, 0, -1, 2);
                if (this.numSections > 1) {
                    int i6x = i4 - 2;
                    this.placeDoubleLowerOrUpperSupport(level, box, 0, -1, i6x);
                }

                if (this.hasRails) {
                    BlockState blockState = Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, RailShape.NORTH_SOUTH);

                    for (int i7 = 0; i7 <= i4; i7++) {
                        BlockState block = this.getBlock(level, 1, -1, i7, box);
                        if (!block.isAir() && block.isSolidRender()) {
                            float f = this.isInterior(level, 1, 0, i7, box) ? 0.7F : 0.9F;
                            this.maybeGenerateBlock(level, box, random, f, 1, 0, i7, blockState);
                        }
                    }
                }
            }
        }

        private void placeDoubleLowerOrUpperSupport(WorldGenLevel level, BoundingBox box, int x, int y, int z) {
            BlockState woodState = this.type.getWoodState();
            BlockState planksState = this.type.getPlanksState();
            if (this.getBlock(level, x, y, z, box).is(planksState.getBlock())) {
                this.fillPillarDownOrChainUp(level, woodState, x, y, z, box);
            }

            if (this.getBlock(level, x + 2, y, z, box).is(planksState.getBlock())) {
                this.fillPillarDownOrChainUp(level, woodState, x + 2, y, z, box);
            }
        }

        @Override
        protected void fillColumnDown(WorldGenLevel level, BlockState state, int x, int y, int z, BoundingBox box) {
            BlockPos.MutableBlockPos worldPos = this.getWorldPos(x, y, z);
            if (box.isInside(worldPos)) {
                int y1 = worldPos.getY();

                while (this.isReplaceableByStructures(level.getBlockState(worldPos)) && worldPos.getY() > level.getMinY() + 1) {
                    worldPos.move(Direction.DOWN);
                }

                if (this.canPlaceColumnOnTopOf(level, worldPos, level.getBlockState(worldPos))) {
                    while (worldPos.getY() < y1) {
                        worldPos.move(Direction.UP);
                        level.setBlock(worldPos, state, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }

        protected void fillPillarDownOrChainUp(WorldGenLevel level, BlockState state, int x, int y, int z, BoundingBox box) {
            BlockPos.MutableBlockPos worldPos = this.getWorldPos(x, y, z);
            if (box.isInside(worldPos)) {
                int y1 = worldPos.getY();
                int i = 1;
                boolean flag = true;

                for (boolean flag1 = true; flag || flag1; i++) {
                    if (flag) {
                        worldPos.setY(y1 - i);
                        BlockState blockState = level.getBlockState(worldPos);
                        boolean flag2 = this.isReplaceableByStructures(blockState) && !blockState.is(Blocks.LAVA);
                        if (!flag2 && this.canPlaceColumnOnTopOf(level, worldPos, blockState)) {
                            fillColumnBetween(level, state, worldPos, y1 - i + 1, y1);
                            return;
                        }

                        flag = i <= 20 && flag2 && worldPos.getY() > level.getMinY() + 1;
                    }

                    if (flag1) {
                        worldPos.setY(y1 + i);
                        BlockState blockState = level.getBlockState(worldPos);
                        boolean flag2 = this.isReplaceableByStructures(blockState);
                        if (!flag2 && this.canHangChainBelow(level, worldPos, blockState)) {
                            level.setBlock(worldPos.setY(y1 + 1), this.type.getFenceState(), Block.UPDATE_CLIENTS);
                            fillColumnBetween(level, Blocks.IRON_CHAIN.defaultBlockState(), worldPos, y1 + 2, y1 + i);
                            return;
                        }

                        flag1 = i <= 50 && flag2 && worldPos.getY() < level.getMaxY();
                    }
                }
            }
        }

        private static void fillColumnBetween(WorldGenLevel level, BlockState state, BlockPos.MutableBlockPos pos, int minY, int maxY) {
            for (int i = minY; i < maxY; i++) {
                level.setBlock(pos.setY(i), state, Block.UPDATE_CLIENTS);
            }
        }

        private boolean canPlaceColumnOnTopOf(LevelReader level, BlockPos pos, BlockState state) {
            return state.isFaceSturdy(level, pos, Direction.UP);
        }

        private boolean canHangChainBelow(LevelReader level, BlockPos pos, BlockState state) {
            return Block.canSupportCenter(level, pos, Direction.DOWN) && !(state.getBlock() instanceof FallingBlock);
        }

        private void placeSupport(WorldGenLevel level, BoundingBox box, int minX, int minY, int z, int maxY, int maxX, RandomSource random) {
            if (this.isSupportingBox(level, box, minX, maxX, maxY, z)) {
                BlockState planksState = this.type.getPlanksState();
                BlockState fenceState = this.type.getFenceState();
                this.generateBox(level, box, minX, minY, z, minX, maxY - 1, z, fenceState.setValue(FenceBlock.WEST, true), CAVE_AIR, false);
                this.generateBox(level, box, maxX, minY, z, maxX, maxY - 1, z, fenceState.setValue(FenceBlock.EAST, true), CAVE_AIR, false);
                if (random.nextInt(4) == 0) {
                    this.generateBox(level, box, minX, maxY, z, minX, maxY, z, planksState, CAVE_AIR, false);
                    this.generateBox(level, box, maxX, maxY, z, maxX, maxY, z, planksState, CAVE_AIR, false);
                } else {
                    this.generateBox(level, box, minX, maxY, z, maxX, maxY, z, planksState, CAVE_AIR, false);
                    this.maybeGenerateBlock(
                        level,
                        box,
                        random,
                        0.05F,
                        minX + 1,
                        maxY,
                        z - 1,
                        Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.SOUTH)
                    );
                    this.maybeGenerateBlock(
                        level,
                        box,
                        random,
                        0.05F,
                        minX + 1,
                        maxY,
                        z + 1,
                        Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.NORTH)
                    );
                }
            }
        }

        private void maybePlaceCobWeb(WorldGenLevel level, BoundingBox box, RandomSource random, float chance, int x, int y, int z) {
            if (this.isInterior(level, x, y, z, box) && random.nextFloat() < chance && this.hasSturdyNeighbours(level, box, x, y, z, 2)) {
                this.placeBlock(level, Blocks.COBWEB.defaultBlockState(), x, y, z, box);
            }
        }

        private boolean hasSturdyNeighbours(WorldGenLevel level, BoundingBox box, int x, int y, int z, int required) {
            BlockPos.MutableBlockPos worldPos = this.getWorldPos(x, y, z);
            int i = 0;

            for (Direction direction : Direction.values()) {
                worldPos.move(direction);
                if (box.isInside(worldPos) && level.getBlockState(worldPos).isFaceSturdy(level, worldPos, direction.getOpposite())) {
                    if (++i >= required) {
                        return true;
                    }
                }

                worldPos.move(direction.getOpposite());
            }

            return false;
        }
    }

    public static class MineShaftCrossing extends MineshaftPieces.MineShaftPiece {
        private final Direction direction;
        private final boolean isTwoFloored;

        public MineShaftCrossing(CompoundTag tag) {
            super(StructurePieceType.MINE_SHAFT_CROSSING, tag);
            this.isTwoFloored = tag.getBooleanOr("tf", false);
            this.direction = tag.read("D", Direction.LEGACY_ID_CODEC_2D).orElse(Direction.SOUTH);
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            super.addAdditionalSaveData(context, tag);
            tag.putBoolean("tf", this.isTwoFloored);
            tag.store("D", Direction.LEGACY_ID_CODEC_2D, this.direction);
        }

        public MineShaftCrossing(int genDepth, BoundingBox boundingBox, @Nullable Direction direction, MineshaftStructure.Type type) {
            super(StructurePieceType.MINE_SHAFT_CROSSING, genDepth, type, boundingBox);
            this.direction = direction;
            this.isTwoFloored = boundingBox.getYSpan() > 3;
        }

        public static @Nullable BoundingBox findCrossing(StructurePieceAccessor pieces, RandomSource random, int x, int y, int z, Direction direction) {
            int i;
            if (random.nextInt(4) == 0) {
                i = 6;
            } else {
                i = 2;
            }
            BoundingBox boundingBox = switch (direction) {
                default -> new BoundingBox(-1, 0, -4, 3, i, 0);
                case SOUTH -> new BoundingBox(-1, 0, 0, 3, i, 4);
                case WEST -> new BoundingBox(-4, 0, -1, 0, i, 3);
                case EAST -> new BoundingBox(0, 0, -1, 4, i, 3);
            };
            boundingBox.move(x, y, z);
            return pieces.findCollisionPiece(boundingBox) != null ? null : boundingBox;
        }

        @Override
        public void addChildren(StructurePiece piece, StructurePieceAccessor pieces, RandomSource random) {
            int genDepth = this.getGenDepth();
            switch (this.direction) {
                case NORTH:
                default:
                    MineshaftPieces.generateAndAddPiece(
                        piece, pieces, random, this.boundingBox.minX() + 1, this.boundingBox.minY(), this.boundingBox.minZ() - 1, Direction.NORTH, genDepth
                    );
                    MineshaftPieces.generateAndAddPiece(
                        piece, pieces, random, this.boundingBox.minX() - 1, this.boundingBox.minY(), this.boundingBox.minZ() + 1, Direction.WEST, genDepth
                    );
                    MineshaftPieces.generateAndAddPiece(
                        piece, pieces, random, this.boundingBox.maxX() + 1, this.boundingBox.minY(), this.boundingBox.minZ() + 1, Direction.EAST, genDepth
                    );
                    break;
                case SOUTH:
                    MineshaftPieces.generateAndAddPiece(
                        piece, pieces, random, this.boundingBox.minX() + 1, this.boundingBox.minY(), this.boundingBox.maxZ() + 1, Direction.SOUTH, genDepth
                    );
                    MineshaftPieces.generateAndAddPiece(
                        piece, pieces, random, this.boundingBox.minX() - 1, this.boundingBox.minY(), this.boundingBox.minZ() + 1, Direction.WEST, genDepth
                    );
                    MineshaftPieces.generateAndAddPiece(
                        piece, pieces, random, this.boundingBox.maxX() + 1, this.boundingBox.minY(), this.boundingBox.minZ() + 1, Direction.EAST, genDepth
                    );
                    break;
                case WEST:
                    MineshaftPieces.generateAndAddPiece(
                        piece, pieces, random, this.boundingBox.minX() + 1, this.boundingBox.minY(), this.boundingBox.minZ() - 1, Direction.NORTH, genDepth
                    );
                    MineshaftPieces.generateAndAddPiece(
                        piece, pieces, random, this.boundingBox.minX() + 1, this.boundingBox.minY(), this.boundingBox.maxZ() + 1, Direction.SOUTH, genDepth
                    );
                    MineshaftPieces.generateAndAddPiece(
                        piece, pieces, random, this.boundingBox.minX() - 1, this.boundingBox.minY(), this.boundingBox.minZ() + 1, Direction.WEST, genDepth
                    );
                    break;
                case EAST:
                    MineshaftPieces.generateAndAddPiece(
                        piece, pieces, random, this.boundingBox.minX() + 1, this.boundingBox.minY(), this.boundingBox.minZ() - 1, Direction.NORTH, genDepth
                    );
                    MineshaftPieces.generateAndAddPiece(
                        piece, pieces, random, this.boundingBox.minX() + 1, this.boundingBox.minY(), this.boundingBox.maxZ() + 1, Direction.SOUTH, genDepth
                    );
                    MineshaftPieces.generateAndAddPiece(
                        piece, pieces, random, this.boundingBox.maxX() + 1, this.boundingBox.minY(), this.boundingBox.minZ() + 1, Direction.EAST, genDepth
                    );
            }

            if (this.isTwoFloored) {
                if (random.nextBoolean()) {
                    MineshaftPieces.generateAndAddPiece(
                        piece,
                        pieces,
                        random,
                        this.boundingBox.minX() + 1,
                        this.boundingBox.minY() + 3 + 1,
                        this.boundingBox.minZ() - 1,
                        Direction.NORTH,
                        genDepth
                    );
                }

                if (random.nextBoolean()) {
                    MineshaftPieces.generateAndAddPiece(
                        piece,
                        pieces,
                        random,
                        this.boundingBox.minX() - 1,
                        this.boundingBox.minY() + 3 + 1,
                        this.boundingBox.minZ() + 1,
                        Direction.WEST,
                        genDepth
                    );
                }

                if (random.nextBoolean()) {
                    MineshaftPieces.generateAndAddPiece(
                        piece,
                        pieces,
                        random,
                        this.boundingBox.maxX() + 1,
                        this.boundingBox.minY() + 3 + 1,
                        this.boundingBox.minZ() + 1,
                        Direction.EAST,
                        genDepth
                    );
                }

                if (random.nextBoolean()) {
                    MineshaftPieces.generateAndAddPiece(
                        piece,
                        pieces,
                        random,
                        this.boundingBox.minX() + 1,
                        this.boundingBox.minY() + 3 + 1,
                        this.boundingBox.maxZ() + 1,
                        Direction.SOUTH,
                        genDepth
                    );
                }
            }
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
            if (!this.isInInvalidLocation(level, box)) {
                BlockState planksState = this.type.getPlanksState();
                if (this.isTwoFloored) {
                    this.generateBox(
                        level,
                        box,
                        this.boundingBox.minX() + 1,
                        this.boundingBox.minY(),
                        this.boundingBox.minZ(),
                        this.boundingBox.maxX() - 1,
                        this.boundingBox.minY() + 3 - 1,
                        this.boundingBox.maxZ(),
                        CAVE_AIR,
                        CAVE_AIR,
                        false
                    );
                    this.generateBox(
                        level,
                        box,
                        this.boundingBox.minX(),
                        this.boundingBox.minY(),
                        this.boundingBox.minZ() + 1,
                        this.boundingBox.maxX(),
                        this.boundingBox.minY() + 3 - 1,
                        this.boundingBox.maxZ() - 1,
                        CAVE_AIR,
                        CAVE_AIR,
                        false
                    );
                    this.generateBox(
                        level,
                        box,
                        this.boundingBox.minX() + 1,
                        this.boundingBox.maxY() - 2,
                        this.boundingBox.minZ(),
                        this.boundingBox.maxX() - 1,
                        this.boundingBox.maxY(),
                        this.boundingBox.maxZ(),
                        CAVE_AIR,
                        CAVE_AIR,
                        false
                    );
                    this.generateBox(
                        level,
                        box,
                        this.boundingBox.minX(),
                        this.boundingBox.maxY() - 2,
                        this.boundingBox.minZ() + 1,
                        this.boundingBox.maxX(),
                        this.boundingBox.maxY(),
                        this.boundingBox.maxZ() - 1,
                        CAVE_AIR,
                        CAVE_AIR,
                        false
                    );
                    this.generateBox(
                        level,
                        box,
                        this.boundingBox.minX() + 1,
                        this.boundingBox.minY() + 3,
                        this.boundingBox.minZ() + 1,
                        this.boundingBox.maxX() - 1,
                        this.boundingBox.minY() + 3,
                        this.boundingBox.maxZ() - 1,
                        CAVE_AIR,
                        CAVE_AIR,
                        false
                    );
                } else {
                    this.generateBox(
                        level,
                        box,
                        this.boundingBox.minX() + 1,
                        this.boundingBox.minY(),
                        this.boundingBox.minZ(),
                        this.boundingBox.maxX() - 1,
                        this.boundingBox.maxY(),
                        this.boundingBox.maxZ(),
                        CAVE_AIR,
                        CAVE_AIR,
                        false
                    );
                    this.generateBox(
                        level,
                        box,
                        this.boundingBox.minX(),
                        this.boundingBox.minY(),
                        this.boundingBox.minZ() + 1,
                        this.boundingBox.maxX(),
                        this.boundingBox.maxY(),
                        this.boundingBox.maxZ() - 1,
                        CAVE_AIR,
                        CAVE_AIR,
                        false
                    );
                }

                this.placeSupportPillar(level, box, this.boundingBox.minX() + 1, this.boundingBox.minY(), this.boundingBox.minZ() + 1, this.boundingBox.maxY());
                this.placeSupportPillar(level, box, this.boundingBox.minX() + 1, this.boundingBox.minY(), this.boundingBox.maxZ() - 1, this.boundingBox.maxY());
                this.placeSupportPillar(level, box, this.boundingBox.maxX() - 1, this.boundingBox.minY(), this.boundingBox.minZ() + 1, this.boundingBox.maxY());
                this.placeSupportPillar(level, box, this.boundingBox.maxX() - 1, this.boundingBox.minY(), this.boundingBox.maxZ() - 1, this.boundingBox.maxY());
                int i = this.boundingBox.minY() - 1;

                for (int x = this.boundingBox.minX(); x <= this.boundingBox.maxX(); x++) {
                    for (int z = this.boundingBox.minZ(); z <= this.boundingBox.maxZ(); z++) {
                        this.setPlanksBlock(level, box, planksState, x, i, z);
                    }
                }
            }
        }

        private void placeSupportPillar(WorldGenLevel level, BoundingBox box, int x, int y, int z, int maxY) {
            if (!this.getBlock(level, x, maxY + 1, z, box).isAir()) {
                this.generateBox(level, box, x, y, z, x, maxY, z, this.type.getPlanksState(), CAVE_AIR, false);
            }
        }
    }

    abstract static class MineShaftPiece extends StructurePiece {
        protected MineshaftStructure.Type type;

        public MineShaftPiece(StructurePieceType structurePieceType, int genDepth, MineshaftStructure.Type type, BoundingBox boundingBox) {
            super(structurePieceType, genDepth, boundingBox);
            this.type = type;
        }

        public MineShaftPiece(StructurePieceType type, CompoundTag tag) {
            super(type, tag);
            this.type = MineshaftStructure.Type.byId(tag.getIntOr("MST", 0));
        }

        @Override
        protected boolean canBeReplaced(LevelReader level, int x, int y, int z, BoundingBox box) {
            BlockState block = this.getBlock(level, x, y, z, box);
            return !block.is(this.type.getPlanksState().getBlock())
                && !block.is(this.type.getWoodState().getBlock())
                && !block.is(this.type.getFenceState().getBlock())
                && !block.is(Blocks.IRON_CHAIN);
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            tag.putInt("MST", this.type.ordinal());
        }

        protected boolean isSupportingBox(BlockGetter level, BoundingBox box, int xStart, int xEnd, int y, int z) {
            for (int i = xStart; i <= xEnd; i++) {
                if (this.getBlock(level, i, y + 1, z, box).isAir()) {
                    return false;
                }
            }

            return true;
        }

        protected boolean isInInvalidLocation(LevelAccessor level, BoundingBox boundingBox) {
            int max = Math.max(this.boundingBox.minX() - 1, boundingBox.minX());
            int max1 = Math.max(this.boundingBox.minY() - 1, boundingBox.minY());
            int max2 = Math.max(this.boundingBox.minZ() - 1, boundingBox.minZ());
            int min = Math.min(this.boundingBox.maxX() + 1, boundingBox.maxX());
            int min1 = Math.min(this.boundingBox.maxY() + 1, boundingBox.maxY());
            int min2 = Math.min(this.boundingBox.maxZ() + 1, boundingBox.maxZ());
            BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos((max + min) / 2, (max1 + min1) / 2, (max2 + min2) / 2);
            if (level.getBiome(mutableBlockPos).is(BiomeTags.MINESHAFT_BLOCKING)) {
                return true;
            } else {
                for (int i = max; i <= min; i++) {
                    for (int i1 = max2; i1 <= min2; i1++) {
                        if (level.getBlockState(mutableBlockPos.set(i, max1, i1)).liquid()) {
                            return true;
                        }

                        if (level.getBlockState(mutableBlockPos.set(i, min1, i1)).liquid()) {
                            return true;
                        }
                    }
                }

                for (int i = max; i <= min; i++) {
                    for (int i1 = max1; i1 <= min1; i1++) {
                        if (level.getBlockState(mutableBlockPos.set(i, i1, max2)).liquid()) {
                            return true;
                        }

                        if (level.getBlockState(mutableBlockPos.set(i, i1, min2)).liquid()) {
                            return true;
                        }
                    }
                }

                for (int i = max2; i <= min2; i++) {
                    for (int i1 = max1; i1 <= min1; i1++) {
                        if (level.getBlockState(mutableBlockPos.set(max, i1, i)).liquid()) {
                            return true;
                        }

                        if (level.getBlockState(mutableBlockPos.set(min, i1, i)).liquid()) {
                            return true;
                        }
                    }
                }

                return false;
            }
        }

        protected void setPlanksBlock(WorldGenLevel level, BoundingBox box, BlockState plankState, int x, int y, int z) {
            if (this.isInterior(level, x, y, z, box)) {
                BlockPos worldPos = this.getWorldPos(x, y, z);
                BlockState blockState = level.getBlockState(worldPos);
                if (!blockState.isFaceSturdy(level, worldPos, Direction.UP)) {
                    level.setBlock(worldPos, plankState, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    public static class MineShaftRoom extends MineshaftPieces.MineShaftPiece {
        private final List<BoundingBox> childEntranceBoxes = Lists.newLinkedList();

        public MineShaftRoom(int genDepth, RandomSource random, int x, int z, MineshaftStructure.Type type) {
            super(
                StructurePieceType.MINE_SHAFT_ROOM,
                genDepth,
                type,
                new BoundingBox(x, 50, z, x + 7 + random.nextInt(6), 54 + random.nextInt(6), z + 7 + random.nextInt(6))
            );
            this.type = type;
        }

        public MineShaftRoom(CompoundTag tag) {
            super(StructurePieceType.MINE_SHAFT_ROOM, tag);
            this.childEntranceBoxes.addAll(tag.read("Entrances", BoundingBox.CODEC.listOf()).orElse(List.of()));
        }

        @Override
        public void addChildren(StructurePiece piece, StructurePieceAccessor pieces, RandomSource random) {
            int genDepth = this.getGenDepth();
            int i = this.boundingBox.getYSpan() - 3 - 1;
            if (i <= 0) {
                i = 1;
            }

            int i1 = 0;

            while (i1 < this.boundingBox.getXSpan()) {
                i1 += random.nextInt(this.boundingBox.getXSpan());
                if (i1 + 3 > this.boundingBox.getXSpan()) {
                    break;
                }

                MineshaftPieces.MineShaftPiece mineShaftPiece = MineshaftPieces.generateAndAddPiece(
                    piece,
                    pieces,
                    random,
                    this.boundingBox.minX() + i1,
                    this.boundingBox.minY() + random.nextInt(i) + 1,
                    this.boundingBox.minZ() - 1,
                    Direction.NORTH,
                    genDepth
                );
                if (mineShaftPiece != null) {
                    BoundingBox boundingBox = mineShaftPiece.getBoundingBox();
                    this.childEntranceBoxes
                        .add(
                            new BoundingBox(
                                boundingBox.minX(),
                                boundingBox.minY(),
                                this.boundingBox.minZ(),
                                boundingBox.maxX(),
                                boundingBox.maxY(),
                                this.boundingBox.minZ() + 1
                            )
                        );
                }

                i1 += 4;
            }

            i1 = 0;

            while (i1 < this.boundingBox.getXSpan()) {
                i1 += random.nextInt(this.boundingBox.getXSpan());
                if (i1 + 3 > this.boundingBox.getXSpan()) {
                    break;
                }

                MineshaftPieces.MineShaftPiece mineShaftPiece = MineshaftPieces.generateAndAddPiece(
                    piece,
                    pieces,
                    random,
                    this.boundingBox.minX() + i1,
                    this.boundingBox.minY() + random.nextInt(i) + 1,
                    this.boundingBox.maxZ() + 1,
                    Direction.SOUTH,
                    genDepth
                );
                if (mineShaftPiece != null) {
                    BoundingBox boundingBox = mineShaftPiece.getBoundingBox();
                    this.childEntranceBoxes
                        .add(
                            new BoundingBox(
                                boundingBox.minX(),
                                boundingBox.minY(),
                                this.boundingBox.maxZ() - 1,
                                boundingBox.maxX(),
                                boundingBox.maxY(),
                                this.boundingBox.maxZ()
                            )
                        );
                }

                i1 += 4;
            }

            i1 = 0;

            while (i1 < this.boundingBox.getZSpan()) {
                i1 += random.nextInt(this.boundingBox.getZSpan());
                if (i1 + 3 > this.boundingBox.getZSpan()) {
                    break;
                }

                MineshaftPieces.MineShaftPiece mineShaftPiece = MineshaftPieces.generateAndAddPiece(
                    piece,
                    pieces,
                    random,
                    this.boundingBox.minX() - 1,
                    this.boundingBox.minY() + random.nextInt(i) + 1,
                    this.boundingBox.minZ() + i1,
                    Direction.WEST,
                    genDepth
                );
                if (mineShaftPiece != null) {
                    BoundingBox boundingBox = mineShaftPiece.getBoundingBox();
                    this.childEntranceBoxes
                        .add(
                            new BoundingBox(
                                this.boundingBox.minX(),
                                boundingBox.minY(),
                                boundingBox.minZ(),
                                this.boundingBox.minX() + 1,
                                boundingBox.maxY(),
                                boundingBox.maxZ()
                            )
                        );
                }

                i1 += 4;
            }

            i1 = 0;

            while (i1 < this.boundingBox.getZSpan()) {
                i1 += random.nextInt(this.boundingBox.getZSpan());
                if (i1 + 3 > this.boundingBox.getZSpan()) {
                    break;
                }

                StructurePiece structurePiece = MineshaftPieces.generateAndAddPiece(
                    piece,
                    pieces,
                    random,
                    this.boundingBox.maxX() + 1,
                    this.boundingBox.minY() + random.nextInt(i) + 1,
                    this.boundingBox.minZ() + i1,
                    Direction.EAST,
                    genDepth
                );
                if (structurePiece != null) {
                    BoundingBox boundingBox = structurePiece.getBoundingBox();
                    this.childEntranceBoxes
                        .add(
                            new BoundingBox(
                                this.boundingBox.maxX() - 1,
                                boundingBox.minY(),
                                boundingBox.minZ(),
                                this.boundingBox.maxX(),
                                boundingBox.maxY(),
                                boundingBox.maxZ()
                            )
                        );
                }

                i1 += 4;
            }
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
            if (!this.isInInvalidLocation(level, box)) {
                this.generateBox(
                    level,
                    box,
                    this.boundingBox.minX(),
                    this.boundingBox.minY() + 1,
                    this.boundingBox.minZ(),
                    this.boundingBox.maxX(),
                    Math.min(this.boundingBox.minY() + 3, this.boundingBox.maxY()),
                    this.boundingBox.maxZ(),
                    CAVE_AIR,
                    CAVE_AIR,
                    false
                );

                for (BoundingBox boundingBox : this.childEntranceBoxes) {
                    this.generateBox(
                        level,
                        box,
                        boundingBox.minX(),
                        boundingBox.maxY() - 2,
                        boundingBox.minZ(),
                        boundingBox.maxX(),
                        boundingBox.maxY(),
                        boundingBox.maxZ(),
                        CAVE_AIR,
                        CAVE_AIR,
                        false
                    );
                }

                this.generateUpperHalfSphere(
                    level,
                    box,
                    this.boundingBox.minX(),
                    this.boundingBox.minY() + 4,
                    this.boundingBox.minZ(),
                    this.boundingBox.maxX(),
                    this.boundingBox.maxY(),
                    this.boundingBox.maxZ(),
                    CAVE_AIR,
                    false
                );
            }
        }

        @Override
        public void move(int x, int y, int z) {
            super.move(x, y, z);

            for (BoundingBox boundingBox : this.childEntranceBoxes) {
                boundingBox.move(x, y, z);
            }
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            super.addAdditionalSaveData(context, tag);
            tag.store("Entrances", BoundingBox.CODEC.listOf(), this.childEntranceBoxes);
        }
    }

    public static class MineShaftStairs extends MineshaftPieces.MineShaftPiece {
        public MineShaftStairs(int genDepth, BoundingBox boundingBox, Direction orientation, MineshaftStructure.Type type) {
            super(StructurePieceType.MINE_SHAFT_STAIRS, genDepth, type, boundingBox);
            this.setOrientation(orientation);
        }

        public MineShaftStairs(CompoundTag tag) {
            super(StructurePieceType.MINE_SHAFT_STAIRS, tag);
        }

        public static @Nullable BoundingBox findStairs(StructurePieceAccessor pieces, RandomSource random, int x, int y, int z, Direction direction) {
            BoundingBox boundingBox = switch (direction) {
                default -> new BoundingBox(0, -5, -8, 2, 2, 0);
                case SOUTH -> new BoundingBox(0, -5, 0, 2, 2, 8);
                case WEST -> new BoundingBox(-8, -5, 0, 0, 2, 2);
                case EAST -> new BoundingBox(0, -5, 0, 8, 2, 2);
            };
            boundingBox.move(x, y, z);
            return pieces.findCollisionPiece(boundingBox) != null ? null : boundingBox;
        }

        @Override
        public void addChildren(StructurePiece piece, StructurePieceAccessor pieces, RandomSource random) {
            int genDepth = this.getGenDepth();
            Direction orientation = this.getOrientation();
            if (orientation != null) {
                switch (orientation) {
                    case NORTH:
                    default:
                        MineshaftPieces.generateAndAddPiece(
                            piece, pieces, random, this.boundingBox.minX(), this.boundingBox.minY(), this.boundingBox.minZ() - 1, Direction.NORTH, genDepth
                        );
                        break;
                    case SOUTH:
                        MineshaftPieces.generateAndAddPiece(
                            piece, pieces, random, this.boundingBox.minX(), this.boundingBox.minY(), this.boundingBox.maxZ() + 1, Direction.SOUTH, genDepth
                        );
                        break;
                    case WEST:
                        MineshaftPieces.generateAndAddPiece(
                            piece, pieces, random, this.boundingBox.minX() - 1, this.boundingBox.minY(), this.boundingBox.minZ(), Direction.WEST, genDepth
                        );
                        break;
                    case EAST:
                        MineshaftPieces.generateAndAddPiece(
                            piece, pieces, random, this.boundingBox.maxX() + 1, this.boundingBox.minY(), this.boundingBox.minZ(), Direction.EAST, genDepth
                        );
                }
            }
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
            if (!this.isInInvalidLocation(level, box)) {
                this.generateBox(level, box, 0, 5, 0, 2, 7, 1, CAVE_AIR, CAVE_AIR, false);
                this.generateBox(level, box, 0, 0, 7, 2, 2, 8, CAVE_AIR, CAVE_AIR, false);

                for (int i = 0; i < 5; i++) {
                    this.generateBox(level, box, 0, 5 - i - (i < 4 ? 1 : 0), 2 + i, 2, 7 - i, 2 + i, CAVE_AIR, CAVE_AIR, false);
                }
            }
        }
    }
}
