package net.minecraft.world.level.levelgen.structure.structures;

import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Tuple;
import net.minecraft.util.Util;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import org.jspecify.annotations.Nullable;

public class WoodlandMansionPieces {
    public static void generateMansion(
        StructureTemplateManager structureTemplateManager,
        BlockPos pos,
        Rotation rotation,
        List<WoodlandMansionPieces.WoodlandMansionPiece> pieces,
        RandomSource random
    ) {
        WoodlandMansionPieces.MansionGrid mansionGrid = new WoodlandMansionPieces.MansionGrid(random);
        WoodlandMansionPieces.MansionPiecePlacer mansionPiecePlacer = new WoodlandMansionPieces.MansionPiecePlacer(structureTemplateManager, random);
        mansionPiecePlacer.createMansion(pos, rotation, pieces, mansionGrid);
    }

    static class FirstFloorRoomCollection extends WoodlandMansionPieces.FloorRoomCollection {
        @Override
        public String get1x1(RandomSource random) {
            return "1x1_a" + (random.nextInt(5) + 1);
        }

        @Override
        public String get1x1Secret(RandomSource random) {
            return "1x1_as" + (random.nextInt(4) + 1);
        }

        @Override
        public String get1x2SideEntrance(RandomSource random, boolean isStairs) {
            return "1x2_a" + (random.nextInt(9) + 1);
        }

        @Override
        public String get1x2FrontEntrance(RandomSource random, boolean isStairs) {
            return "1x2_b" + (random.nextInt(5) + 1);
        }

        @Override
        public String get1x2Secret(RandomSource random) {
            return "1x2_s" + (random.nextInt(2) + 1);
        }

        @Override
        public String get2x2(RandomSource random) {
            return "2x2_a" + (random.nextInt(4) + 1);
        }

        @Override
        public String get2x2Secret(RandomSource random) {
            return "2x2_s1";
        }
    }

    abstract static class FloorRoomCollection {
        public abstract String get1x1(RandomSource random);

        public abstract String get1x1Secret(RandomSource random);

        public abstract String get1x2SideEntrance(RandomSource random, boolean isStairs);

        public abstract String get1x2FrontEntrance(RandomSource random, boolean isStairs);

        public abstract String get1x2Secret(RandomSource random);

        public abstract String get2x2(RandomSource random);

        public abstract String get2x2Secret(RandomSource random);
    }

    static class MansionGrid {
        private static final int DEFAULT_SIZE = 11;
        private static final int CLEAR = 0;
        private static final int CORRIDOR = 1;
        private static final int ROOM = 2;
        private static final int START_ROOM = 3;
        private static final int TEST_ROOM = 4;
        private static final int BLOCKED = 5;
        private static final int ROOM_1x1 = 65536;
        private static final int ROOM_1x2 = 131072;
        private static final int ROOM_2x2 = 262144;
        private static final int ROOM_ORIGIN_FLAG = 1048576;
        private static final int ROOM_DOOR_FLAG = 2097152;
        private static final int ROOM_STAIRS_FLAG = 4194304;
        private static final int ROOM_CORRIDOR_FLAG = 8388608;
        private static final int ROOM_TYPE_MASK = 983040;
        private static final int ROOM_ID_MASK = 65535;
        private final RandomSource random;
        final WoodlandMansionPieces.SimpleGrid baseGrid;
        final WoodlandMansionPieces.SimpleGrid thirdFloorGrid;
        final WoodlandMansionPieces.SimpleGrid[] floorRooms;
        final int entranceX;
        final int entranceY;

        public MansionGrid(RandomSource random) {
            this.random = random;
            int i = 11;
            this.entranceX = 7;
            this.entranceY = 4;
            this.baseGrid = new WoodlandMansionPieces.SimpleGrid(DEFAULT_SIZE, DEFAULT_SIZE, BLOCKED);
            this.baseGrid.set(this.entranceX, this.entranceY, this.entranceX + 1, this.entranceY + 1, START_ROOM);
            this.baseGrid.set(this.entranceX - 1, this.entranceY, this.entranceX - 1, this.entranceY + 1, ROOM);
            this.baseGrid.set(this.entranceX + 2, this.entranceY - 2, this.entranceX + 3, this.entranceY + 3, BLOCKED);
            this.baseGrid.set(this.entranceX + 1, this.entranceY - 2, this.entranceX + 1, this.entranceY - 1, CORRIDOR);
            this.baseGrid.set(this.entranceX + 1, this.entranceY + 2, this.entranceX + 1, this.entranceY + 3, CORRIDOR);
            this.baseGrid.set(this.entranceX - 1, this.entranceY - 1, CORRIDOR);
            this.baseGrid.set(this.entranceX - 1, this.entranceY + 2, CORRIDOR);
            this.baseGrid.set(0, 0, 11, 1, BLOCKED);
            this.baseGrid.set(0, 9, 11, 11, BLOCKED);
            this.recursiveCorridor(this.baseGrid, this.entranceX, this.entranceY - 2, Direction.WEST, 6);
            this.recursiveCorridor(this.baseGrid, this.entranceX, this.entranceY + 3, Direction.WEST, 6);
            this.recursiveCorridor(this.baseGrid, this.entranceX - 2, this.entranceY - 1, Direction.WEST, 3);
            this.recursiveCorridor(this.baseGrid, this.entranceX - 2, this.entranceY + 2, Direction.WEST, 3);

            while (this.cleanEdges(this.baseGrid)) {
            }

            this.floorRooms = new WoodlandMansionPieces.SimpleGrid[3];
            this.floorRooms[0] = new WoodlandMansionPieces.SimpleGrid(DEFAULT_SIZE, DEFAULT_SIZE, BLOCKED);
            this.floorRooms[1] = new WoodlandMansionPieces.SimpleGrid(DEFAULT_SIZE, DEFAULT_SIZE, BLOCKED);
            this.floorRooms[2] = new WoodlandMansionPieces.SimpleGrid(DEFAULT_SIZE, DEFAULT_SIZE, BLOCKED);
            this.identifyRooms(this.baseGrid, this.floorRooms[0]);
            this.identifyRooms(this.baseGrid, this.floorRooms[1]);
            this.floorRooms[0].set(this.entranceX + 1, this.entranceY, this.entranceX + 1, this.entranceY + 1, 8388608);
            this.floorRooms[1].set(this.entranceX + 1, this.entranceY, this.entranceX + 1, this.entranceY + 1, 8388608);
            this.thirdFloorGrid = new WoodlandMansionPieces.SimpleGrid(this.baseGrid.width, this.baseGrid.height, BLOCKED);
            this.setupThirdFloor();
            this.identifyRooms(this.thirdFloorGrid, this.floorRooms[2]);
        }

        public static boolean isHouse(WoodlandMansionPieces.SimpleGrid layout, int x, int y) {
            int i = layout.get(x, y);
            return i == CORRIDOR || i == ROOM || i == START_ROOM || i == TEST_ROOM;
        }

        public boolean isRoomId(WoodlandMansionPieces.SimpleGrid layout, int x, int y, int floor, int roomId) {
            return (this.floorRooms[floor].get(x, y) & 65535) == roomId;
        }

        public @Nullable Direction get1x2RoomDirection(WoodlandMansionPieces.SimpleGrid layout, int x, int y, int floor, int roomId) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                if (this.isRoomId(layout, x + direction.getStepX(), y + direction.getStepZ(), floor, roomId)) {
                    return direction;
                }
            }

            return null;
        }

        private void recursiveCorridor(WoodlandMansionPieces.SimpleGrid layout, int x, int y, Direction direction, int length) {
            if (length > 0) {
                layout.set(x, y, CORRIDOR);
                layout.setif(x + direction.getStepX(), y + direction.getStepZ(), CLEAR, CORRIDOR);

                for (int i = 0; i < 8; i++) {
                    Direction direction1 = Direction.from2DDataValue(this.random.nextInt(4));
                    if (direction1 != direction.getOpposite() && (direction1 != Direction.EAST || !this.random.nextBoolean())) {
                        int i1 = x + direction.getStepX();
                        int i2 = y + direction.getStepZ();
                        if (layout.get(i1 + direction1.getStepX(), i2 + direction1.getStepZ()) == 0
                            && layout.get(i1 + direction1.getStepX() * 2, i2 + direction1.getStepZ() * 2) == 0) {
                            this.recursiveCorridor(
                                layout,
                                x + direction.getStepX() + direction1.getStepX(),
                                y + direction.getStepZ() + direction1.getStepZ(),
                                direction1,
                                length - 1
                            );
                            break;
                        }
                    }
                }

                Direction clockWise = direction.getClockWise();
                Direction direction1 = direction.getCounterClockWise();
                layout.setif(x + clockWise.getStepX(), y + clockWise.getStepZ(), CLEAR, ROOM);
                layout.setif(x + direction1.getStepX(), y + direction1.getStepZ(), CLEAR, ROOM);
                layout.setif(x + direction.getStepX() + clockWise.getStepX(), y + direction.getStepZ() + clockWise.getStepZ(), CLEAR, ROOM);
                layout.setif(x + direction.getStepX() + direction1.getStepX(), y + direction.getStepZ() + direction1.getStepZ(), CLEAR, ROOM);
                layout.setif(x + direction.getStepX() * 2, y + direction.getStepZ() * 2, CLEAR, ROOM);
                layout.setif(x + clockWise.getStepX() * 2, y + clockWise.getStepZ() * 2, CLEAR, ROOM);
                layout.setif(x + direction1.getStepX() * 2, y + direction1.getStepZ() * 2, CLEAR, ROOM);
            }
        }

        private boolean cleanEdges(WoodlandMansionPieces.SimpleGrid grid) {
            boolean flag = false;

            for (int i = 0; i < grid.height; i++) {
                for (int i1 = 0; i1 < grid.width; i1++) {
                    if (grid.get(i1, i) == 0) {
                        int i2 = 0;
                        i2 += isHouse(grid, i1 + 1, i) ? 1 : 0;
                        i2 += isHouse(grid, i1 - 1, i) ? 1 : 0;
                        i2 += isHouse(grid, i1, i + 1) ? 1 : 0;
                        i2 += isHouse(grid, i1, i - 1) ? 1 : 0;
                        if (i2 >= 3) {
                            grid.set(i1, i, ROOM);
                            flag = true;
                        } else if (i2 == 2) {
                            int i3 = 0;
                            i3 += isHouse(grid, i1 + 1, i + 1) ? 1 : 0;
                            i3 += isHouse(grid, i1 - 1, i + 1) ? 1 : 0;
                            i3 += isHouse(grid, i1 + 1, i - 1) ? 1 : 0;
                            i3 += isHouse(grid, i1 - 1, i - 1) ? 1 : 0;
                            if (i3 <= 1) {
                                grid.set(i1, i, ROOM);
                                flag = true;
                            }
                        }
                    }
                }
            }

            return flag;
        }

        private void setupThirdFloor() {
            List<Tuple<Integer, Integer>> list = Lists.newArrayList();
            WoodlandMansionPieces.SimpleGrid simpleGrid = this.floorRooms[1];

            for (int i = 0; i < this.thirdFloorGrid.height; i++) {
                for (int i1 = 0; i1 < this.thirdFloorGrid.width; i1++) {
                    int i2 = simpleGrid.get(i1, i);
                    int i3 = i2 & 983040;
                    if (i3 == 131072 && (i2 & 2097152) == 2097152) {
                        list.add(new Tuple<>(i1, i));
                    }
                }
            }

            if (list.isEmpty()) {
                this.thirdFloorGrid.set(0, 0, this.thirdFloorGrid.width, this.thirdFloorGrid.height, BLOCKED);
            } else {
                Tuple<Integer, Integer> tuple = list.get(this.random.nextInt(list.size()));
                int i1x = simpleGrid.get(tuple.getA(), tuple.getB());
                simpleGrid.set(tuple.getA(), tuple.getB(), i1x | 4194304);
                Direction direction = this.get1x2RoomDirection(this.baseGrid, tuple.getA(), tuple.getB(), 1, i1x & 65535);
                int i3 = tuple.getA() + direction.getStepX();
                int i4 = tuple.getB() + direction.getStepZ();

                for (int i5 = 0; i5 < this.thirdFloorGrid.height; i5++) {
                    for (int i6 = 0; i6 < this.thirdFloorGrid.width; i6++) {
                        if (!isHouse(this.baseGrid, i6, i5)) {
                            this.thirdFloorGrid.set(i6, i5, BLOCKED);
                        } else if (i6 == tuple.getA() && i5 == tuple.getB()) {
                            this.thirdFloorGrid.set(i6, i5, START_ROOM);
                        } else if (i6 == i3 && i5 == i4) {
                            this.thirdFloorGrid.set(i6, i5, START_ROOM);
                            this.floorRooms[2].set(i6, i5, 8388608);
                        }
                    }
                }

                List<Direction> list1 = Lists.newArrayList();

                for (Direction direction1 : Direction.Plane.HORIZONTAL) {
                    if (this.thirdFloorGrid.get(i3 + direction1.getStepX(), i4 + direction1.getStepZ()) == 0) {
                        list1.add(direction1);
                    }
                }

                if (list1.isEmpty()) {
                    this.thirdFloorGrid.set(0, 0, this.thirdFloorGrid.width, this.thirdFloorGrid.height, BLOCKED);
                    simpleGrid.set(tuple.getA(), tuple.getB(), i1x);
                } else {
                    Direction direction2 = list1.get(this.random.nextInt(list1.size()));
                    this.recursiveCorridor(this.thirdFloorGrid, i3 + direction2.getStepX(), i4 + direction2.getStepZ(), direction2, 4);

                    while (this.cleanEdges(this.thirdFloorGrid)) {
                    }
                }
            }
        }

        private void identifyRooms(WoodlandMansionPieces.SimpleGrid grid, WoodlandMansionPieces.SimpleGrid floorRooms) {
            ObjectArrayList<Tuple<Integer, Integer>> list = new ObjectArrayList<>();

            for (int i = 0; i < grid.height; i++) {
                for (int i1 = 0; i1 < grid.width; i1++) {
                    if (grid.get(i1, i) == ROOM) {
                        list.add(new Tuple<>(i1, i));
                    }
                }
            }

            Util.shuffle(list, this.random);
            int i = 10;

            for (Tuple<Integer, Integer> tuple : list) {
                int a = tuple.getA();
                int b = tuple.getB();
                if (floorRooms.get(a, b) == 0) {
                    int i2 = a;
                    int i3 = a;
                    int i4 = b;
                    int i5 = b;
                    int i6 = 65536;
                    if (floorRooms.get(a + 1, b) == 0
                        && floorRooms.get(a, b + 1) == 0
                        && floorRooms.get(a + 1, b + 1) == 0
                        && grid.get(a + 1, b) == ROOM
                        && grid.get(a, b + 1) == ROOM
                        && grid.get(a + 1, b + 1) == ROOM) {
                        i3 = a + 1;
                        i5 = b + 1;
                        i6 = 262144;
                    } else if (floorRooms.get(a - 1, b) == 0
                        && floorRooms.get(a, b + 1) == 0
                        && floorRooms.get(a - 1, b + 1) == 0
                        && grid.get(a - 1, b) == ROOM
                        && grid.get(a, b + 1) == ROOM
                        && grid.get(a - 1, b + 1) == ROOM) {
                        i2 = a - 1;
                        i5 = b + 1;
                        i6 = 262144;
                    } else if (floorRooms.get(a - 1, b) == 0
                        && floorRooms.get(a, b - 1) == 0
                        && floorRooms.get(a - 1, b - 1) == 0
                        && grid.get(a - 1, b) == ROOM
                        && grid.get(a, b - 1) == ROOM
                        && grid.get(a - 1, b - 1) == ROOM) {
                        i2 = a - 1;
                        i4 = b - 1;
                        i6 = 262144;
                    } else if (floorRooms.get(a + 1, b) == 0 && grid.get(a + 1, b) == ROOM) {
                        i3 = a + 1;
                        i6 = 131072;
                    } else if (floorRooms.get(a, b + 1) == 0 && grid.get(a, b + 1) == ROOM) {
                        i5 = b + 1;
                        i6 = 131072;
                    } else if (floorRooms.get(a - 1, b) == 0 && grid.get(a - 1, b) == ROOM) {
                        i2 = a - 1;
                        i6 = 131072;
                    } else if (floorRooms.get(a, b - 1) == 0 && grid.get(a, b - 1) == ROOM) {
                        i4 = b - 1;
                        i6 = 131072;
                    }

                    int i7 = this.random.nextBoolean() ? i2 : i3;
                    int i8 = this.random.nextBoolean() ? i4 : i5;
                    int i9 = 2097152;
                    if (!grid.edgesTo(i7, i8, CORRIDOR)) {
                        i7 = i7 == i2 ? i3 : i2;
                        i8 = i8 == i4 ? i5 : i4;
                        if (!grid.edgesTo(i7, i8, CORRIDOR)) {
                            i8 = i8 == i4 ? i5 : i4;
                            if (!grid.edgesTo(i7, i8, CORRIDOR)) {
                                i7 = i7 == i2 ? i3 : i2;
                                i8 = i8 == i4 ? i5 : i4;
                                if (!grid.edgesTo(i7, i8, CORRIDOR)) {
                                    i9 = 0;
                                    i7 = i2;
                                    i8 = i4;
                                }
                            }
                        }
                    }

                    for (int i10 = i4; i10 <= i5; i10++) {
                        for (int i11 = i2; i11 <= i3; i11++) {
                            if (i11 == i7 && i10 == i8) {
                                floorRooms.set(i11, i10, 1048576 | i9 | i6 | i);
                            } else {
                                floorRooms.set(i11, i10, i6 | i);
                            }
                        }
                    }

                    i++;
                }
            }
        }
    }

    static class MansionPiecePlacer {
        private final StructureTemplateManager structureTemplateManager;
        private final RandomSource random;
        private int startX;
        private int startY;

        public MansionPiecePlacer(StructureTemplateManager structureTemplateManager, RandomSource random) {
            this.structureTemplateManager = structureTemplateManager;
            this.random = random;
        }

        public void createMansion(
            BlockPos pos, Rotation rotation, List<WoodlandMansionPieces.WoodlandMansionPiece> pieces, WoodlandMansionPieces.MansionGrid grid
        ) {
            WoodlandMansionPieces.PlacementData placementData = new WoodlandMansionPieces.PlacementData();
            placementData.position = pos;
            placementData.rotation = rotation;
            placementData.wallType = "wall_flat";
            WoodlandMansionPieces.PlacementData placementData1 = new WoodlandMansionPieces.PlacementData();
            this.entrance(pieces, placementData);
            placementData1.position = placementData.position.above(8);
            placementData1.rotation = placementData.rotation;
            placementData1.wallType = "wall_window";
            if (!pieces.isEmpty()) {
            }

            WoodlandMansionPieces.SimpleGrid simpleGrid = grid.baseGrid;
            WoodlandMansionPieces.SimpleGrid simpleGrid1 = grid.thirdFloorGrid;
            this.startX = grid.entranceX + 1;
            this.startY = grid.entranceY + 1;
            int i = grid.entranceX + 1;
            int i1 = grid.entranceY;
            this.traverseOuterWalls(pieces, placementData, simpleGrid, Direction.SOUTH, this.startX, this.startY, i, i1);
            this.traverseOuterWalls(pieces, placementData1, simpleGrid, Direction.SOUTH, this.startX, this.startY, i, i1);
            WoodlandMansionPieces.PlacementData placementData2 = new WoodlandMansionPieces.PlacementData();
            placementData2.position = placementData.position.above(19);
            placementData2.rotation = placementData.rotation;
            placementData2.wallType = "wall_window";
            boolean flag = false;

            for (int i2 = 0; i2 < simpleGrid1.height && !flag; i2++) {
                for (int i3 = simpleGrid1.width - 1; i3 >= 0 && !flag; i3--) {
                    if (WoodlandMansionPieces.MansionGrid.isHouse(simpleGrid1, i3, i2)) {
                        placementData2.position = placementData2.position.relative(rotation.rotate(Direction.SOUTH), 8 + (i2 - this.startY) * 8);
                        placementData2.position = placementData2.position.relative(rotation.rotate(Direction.EAST), (i3 - this.startX) * 8);
                        this.traverseWallPiece(pieces, placementData2);
                        this.traverseOuterWalls(pieces, placementData2, simpleGrid1, Direction.SOUTH, i3, i2, i3, i2);
                        flag = true;
                    }
                }
            }

            this.createRoof(pieces, pos.above(16), rotation, simpleGrid, simpleGrid1);
            this.createRoof(pieces, pos.above(27), rotation, simpleGrid1, null);
            if (!pieces.isEmpty()) {
            }

            WoodlandMansionPieces.FloorRoomCollection[] floorRoomCollections = new WoodlandMansionPieces.FloorRoomCollection[]{
                new WoodlandMansionPieces.FirstFloorRoomCollection(),
                new WoodlandMansionPieces.SecondFloorRoomCollection(),
                new WoodlandMansionPieces.ThirdFloorRoomCollection()
            };

            for (int i3x = 0; i3x < 3; i3x++) {
                BlockPos blockPos = pos.above(8 * i3x + (i3x == 2 ? 3 : 0));
                WoodlandMansionPieces.SimpleGrid simpleGrid2 = grid.floorRooms[i3x];
                WoodlandMansionPieces.SimpleGrid simpleGrid3 = i3x == 2 ? simpleGrid1 : simpleGrid;
                String string = i3x == 0 ? "carpet_south_1" : "carpet_south_2";
                String string1 = i3x == 0 ? "carpet_west_1" : "carpet_west_2";

                for (int i4 = 0; i4 < simpleGrid3.height; i4++) {
                    for (int i5 = 0; i5 < simpleGrid3.width; i5++) {
                        if (simpleGrid3.get(i5, i4) == WoodlandMansionPieces.MansionGrid.CORRIDOR) {
                            BlockPos blockPos1 = blockPos.relative(rotation.rotate(Direction.SOUTH), 8 + (i4 - this.startY) * 8);
                            blockPos1 = blockPos1.relative(rotation.rotate(Direction.EAST), (i5 - this.startX) * 8);
                            pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, "corridor_floor", blockPos1, rotation));
                            if (simpleGrid3.get(i5, i4 - 1) == WoodlandMansionPieces.MansionGrid.CORRIDOR || (simpleGrid2.get(i5, i4 - 1) & 8388608) == 8388608
                                )
                             {
                                pieces.add(
                                    new WoodlandMansionPieces.WoodlandMansionPiece(
                                        this.structureTemplateManager, "carpet_north", blockPos1.relative(rotation.rotate(Direction.EAST), 1).above(), rotation
                                    )
                                );
                            }

                            if (simpleGrid3.get(i5 + 1, i4) == WoodlandMansionPieces.MansionGrid.CORRIDOR || (simpleGrid2.get(i5 + 1, i4) & 8388608) == 8388608
                                )
                             {
                                pieces.add(
                                    new WoodlandMansionPieces.WoodlandMansionPiece(
                                        this.structureTemplateManager,
                                        "carpet_east",
                                        blockPos1.relative(rotation.rotate(Direction.SOUTH), 1).relative(rotation.rotate(Direction.EAST), 5).above(),
                                        rotation
                                    )
                                );
                            }

                            if (simpleGrid3.get(i5, i4 + 1) == WoodlandMansionPieces.MansionGrid.CORRIDOR || (simpleGrid2.get(i5, i4 + 1) & 8388608) == 8388608
                                )
                             {
                                pieces.add(
                                    new WoodlandMansionPieces.WoodlandMansionPiece(
                                        this.structureTemplateManager,
                                        string,
                                        blockPos1.relative(rotation.rotate(Direction.SOUTH), 5).relative(rotation.rotate(Direction.WEST), 1),
                                        rotation
                                    )
                                );
                            }

                            if (simpleGrid3.get(i5 - 1, i4) == WoodlandMansionPieces.MansionGrid.CORRIDOR || (simpleGrid2.get(i5 - 1, i4) & 8388608) == 8388608
                                )
                             {
                                pieces.add(
                                    new WoodlandMansionPieces.WoodlandMansionPiece(
                                        this.structureTemplateManager,
                                        string1,
                                        blockPos1.relative(rotation.rotate(Direction.WEST), 1).relative(rotation.rotate(Direction.NORTH), 1),
                                        rotation
                                    )
                                );
                            }
                        }
                    }
                }

                String string2 = i3x == 0 ? "indoors_wall_1" : "indoors_wall_2";
                String string3 = i3x == 0 ? "indoors_door_1" : "indoors_door_2";
                List<Direction> list = Lists.newArrayList();

                for (int i6 = 0; i6 < simpleGrid3.height; i6++) {
                    for (int i7 = 0; i7 < simpleGrid3.width; i7++) {
                        boolean flag1 = i3x == 2 && simpleGrid3.get(i7, i6) == WoodlandMansionPieces.MansionGrid.START_ROOM;
                        if (simpleGrid3.get(i7, i6) == WoodlandMansionPieces.MansionGrid.ROOM || flag1) {
                            int i8 = simpleGrid2.get(i7, i6);
                            int i9 = i8 & 983040;
                            int i10 = i8 & 65535;
                            flag1 = flag1 && (i8 & 8388608) == 8388608;
                            list.clear();
                            if ((i8 & 2097152) == 2097152) {
                                for (Direction direction : Direction.Plane.HORIZONTAL) {
                                    if (simpleGrid3.get(i7 + direction.getStepX(), i6 + direction.getStepZ()) == WoodlandMansionPieces.MansionGrid.CORRIDOR) {
                                        list.add(direction);
                                    }
                                }
                            }

                            Direction direction1 = null;
                            if (!list.isEmpty()) {
                                direction1 = list.get(this.random.nextInt(list.size()));
                            } else if ((i8 & 1048576) == 1048576) {
                                direction1 = Direction.UP;
                            }

                            BlockPos blockPos2 = blockPos.relative(rotation.rotate(Direction.SOUTH), 8 + (i6 - this.startY) * 8);
                            blockPos2 = blockPos2.relative(rotation.rotate(Direction.EAST), -1 + (i7 - this.startX) * 8);
                            if (WoodlandMansionPieces.MansionGrid.isHouse(simpleGrid3, i7 - 1, i6) && !grid.isRoomId(simpleGrid3, i7 - 1, i6, i3x, i10)) {
                                pieces.add(
                                    new WoodlandMansionPieces.WoodlandMansionPiece(
                                        this.structureTemplateManager, direction1 == Direction.WEST ? string3 : string2, blockPos2, rotation
                                    )
                                );
                            }

                            if (simpleGrid3.get(i7 + 1, i6) == WoodlandMansionPieces.MansionGrid.CORRIDOR && !flag1) {
                                BlockPos blockPos3 = blockPos2.relative(rotation.rotate(Direction.EAST), 8);
                                pieces.add(
                                    new WoodlandMansionPieces.WoodlandMansionPiece(
                                        this.structureTemplateManager, direction1 == Direction.EAST ? string3 : string2, blockPos3, rotation
                                    )
                                );
                            }

                            if (WoodlandMansionPieces.MansionGrid.isHouse(simpleGrid3, i7, i6 + 1) && !grid.isRoomId(simpleGrid3, i7, i6 + 1, i3x, i10)) {
                                BlockPos blockPos3 = blockPos2.relative(rotation.rotate(Direction.SOUTH), 7);
                                blockPos3 = blockPos3.relative(rotation.rotate(Direction.EAST), 7);
                                pieces.add(
                                    new WoodlandMansionPieces.WoodlandMansionPiece(
                                        this.structureTemplateManager,
                                        direction1 == Direction.SOUTH ? string3 : string2,
                                        blockPos3,
                                        rotation.getRotated(Rotation.CLOCKWISE_90)
                                    )
                                );
                            }

                            if (simpleGrid3.get(i7, i6 - 1) == WoodlandMansionPieces.MansionGrid.CORRIDOR && !flag1) {
                                BlockPos blockPos3 = blockPos2.relative(rotation.rotate(Direction.NORTH), 1);
                                blockPos3 = blockPos3.relative(rotation.rotate(Direction.EAST), 7);
                                pieces.add(
                                    new WoodlandMansionPieces.WoodlandMansionPiece(
                                        this.structureTemplateManager,
                                        direction1 == Direction.NORTH ? string3 : string2,
                                        blockPos3,
                                        rotation.getRotated(Rotation.CLOCKWISE_90)
                                    )
                                );
                            }

                            if (i9 == 65536) {
                                this.addRoom1x1(pieces, blockPos2, rotation, direction1, floorRoomCollections[i3x]);
                            } else if (i9 == 131072 && direction1 != null) {
                                Direction direction2 = grid.get1x2RoomDirection(simpleGrid3, i7, i6, i3x, i10);
                                boolean flag2 = (i8 & 4194304) == 4194304;
                                this.addRoom1x2(pieces, blockPos2, rotation, direction2, direction1, floorRoomCollections[i3x], flag2);
                            } else if (i9 == 262144 && direction1 != null && direction1 != Direction.UP) {
                                Direction direction2 = direction1.getClockWise();
                                if (!grid.isRoomId(simpleGrid3, i7 + direction2.getStepX(), i6 + direction2.getStepZ(), i3x, i10)) {
                                    direction2 = direction2.getOpposite();
                                }

                                this.addRoom2x2(pieces, blockPos2, rotation, direction2, direction1, floorRoomCollections[i3x]);
                            } else if (i9 == 262144 && direction1 == Direction.UP) {
                                this.addRoom2x2Secret(pieces, blockPos2, rotation, floorRoomCollections[i3x]);
                            }
                        }
                    }
                }
            }
        }

        private void traverseOuterWalls(
            List<WoodlandMansionPieces.WoodlandMansionPiece> pieces,
            WoodlandMansionPieces.PlacementData data,
            WoodlandMansionPieces.SimpleGrid layout,
            Direction direction,
            int startX,
            int startY,
            int entranceX,
            int entranceY
        ) {
            int i = startX;
            int i1 = startY;
            Direction direction1 = direction;

            do {
                if (!WoodlandMansionPieces.MansionGrid.isHouse(layout, i + direction.getStepX(), i1 + direction.getStepZ())) {
                    this.traverseTurn(pieces, data);
                    direction = direction.getClockWise();
                    if (i != entranceX || i1 != entranceY || direction1 != direction) {
                        this.traverseWallPiece(pieces, data);
                    }
                } else if (WoodlandMansionPieces.MansionGrid.isHouse(layout, i + direction.getStepX(), i1 + direction.getStepZ())
                    && WoodlandMansionPieces.MansionGrid.isHouse(
                        layout,
                        i + direction.getStepX() + direction.getCounterClockWise().getStepX(),
                        i1 + direction.getStepZ() + direction.getCounterClockWise().getStepZ()
                    )) {
                    this.traverseInnerTurn(pieces, data);
                    i += direction.getStepX();
                    i1 += direction.getStepZ();
                    direction = direction.getCounterClockWise();
                } else {
                    i += direction.getStepX();
                    i1 += direction.getStepZ();
                    if (i != entranceX || i1 != entranceY || direction1 != direction) {
                        this.traverseWallPiece(pieces, data);
                    }
                }
            } while (i != entranceX || i1 != entranceY || direction1 != direction);
        }

        private void createRoof(
            List<WoodlandMansionPieces.WoodlandMansionPiece> pieces,
            BlockPos pos,
            Rotation rotation,
            WoodlandMansionPieces.SimpleGrid layout,
            WoodlandMansionPieces.@Nullable SimpleGrid nextFloorLayout
        ) {
            for (int i = 0; i < layout.height; i++) {
                for (int i1 = 0; i1 < layout.width; i1++) {
                    BlockPos blockPos = pos.relative(rotation.rotate(Direction.SOUTH), 8 + (i - this.startY) * 8);
                    blockPos = blockPos.relative(rotation.rotate(Direction.EAST), (i1 - this.startX) * 8);
                    boolean flag = nextFloorLayout != null && WoodlandMansionPieces.MansionGrid.isHouse(nextFloorLayout, i1, i);
                    if (WoodlandMansionPieces.MansionGrid.isHouse(layout, i1, i) && !flag) {
                        pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, "roof", blockPos.above(3), rotation));
                        if (!WoodlandMansionPieces.MansionGrid.isHouse(layout, i1 + 1, i)) {
                            BlockPos blockPos1 = blockPos.relative(rotation.rotate(Direction.EAST), 6);
                            pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, "roof_front", blockPos1, rotation));
                        }

                        if (!WoodlandMansionPieces.MansionGrid.isHouse(layout, i1 - 1, i)) {
                            BlockPos blockPos1 = blockPos.relative(rotation.rotate(Direction.EAST), 0);
                            blockPos1 = blockPos1.relative(rotation.rotate(Direction.SOUTH), 7);
                            pieces.add(
                                new WoodlandMansionPieces.WoodlandMansionPiece(
                                    this.structureTemplateManager, "roof_front", blockPos1, rotation.getRotated(Rotation.CLOCKWISE_180)
                                )
                            );
                        }

                        if (!WoodlandMansionPieces.MansionGrid.isHouse(layout, i1, i - 1)) {
                            BlockPos blockPos1 = blockPos.relative(rotation.rotate(Direction.WEST), 1);
                            pieces.add(
                                new WoodlandMansionPieces.WoodlandMansionPiece(
                                    this.structureTemplateManager, "roof_front", blockPos1, rotation.getRotated(Rotation.COUNTERCLOCKWISE_90)
                                )
                            );
                        }

                        if (!WoodlandMansionPieces.MansionGrid.isHouse(layout, i1, i + 1)) {
                            BlockPos blockPos1 = blockPos.relative(rotation.rotate(Direction.EAST), 6);
                            blockPos1 = blockPos1.relative(rotation.rotate(Direction.SOUTH), 6);
                            pieces.add(
                                new WoodlandMansionPieces.WoodlandMansionPiece(
                                    this.structureTemplateManager, "roof_front", blockPos1, rotation.getRotated(Rotation.CLOCKWISE_90)
                                )
                            );
                        }
                    }
                }
            }

            if (nextFloorLayout != null) {
                for (int i = 0; i < layout.height; i++) {
                    for (int i1x = 0; i1x < layout.width; i1x++) {
                        BlockPos var17 = pos.relative(rotation.rotate(Direction.SOUTH), 8 + (i - this.startY) * 8);
                        var17 = var17.relative(rotation.rotate(Direction.EAST), (i1x - this.startX) * 8);
                        boolean flag = WoodlandMansionPieces.MansionGrid.isHouse(nextFloorLayout, i1x, i);
                        if (WoodlandMansionPieces.MansionGrid.isHouse(layout, i1x, i) && flag) {
                            if (!WoodlandMansionPieces.MansionGrid.isHouse(layout, i1x + 1, i)) {
                                BlockPos blockPos1 = var17.relative(rotation.rotate(Direction.EAST), 7);
                                pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, "small_wall", blockPos1, rotation));
                            }

                            if (!WoodlandMansionPieces.MansionGrid.isHouse(layout, i1x - 1, i)) {
                                BlockPos blockPos1 = var17.relative(rotation.rotate(Direction.WEST), 1);
                                blockPos1 = blockPos1.relative(rotation.rotate(Direction.SOUTH), 6);
                                pieces.add(
                                    new WoodlandMansionPieces.WoodlandMansionPiece(
                                        this.structureTemplateManager, "small_wall", blockPos1, rotation.getRotated(Rotation.CLOCKWISE_180)
                                    )
                                );
                            }

                            if (!WoodlandMansionPieces.MansionGrid.isHouse(layout, i1x, i - 1)) {
                                BlockPos blockPos1 = var17.relative(rotation.rotate(Direction.WEST), 0);
                                blockPos1 = blockPos1.relative(rotation.rotate(Direction.NORTH), 1);
                                pieces.add(
                                    new WoodlandMansionPieces.WoodlandMansionPiece(
                                        this.structureTemplateManager, "small_wall", blockPos1, rotation.getRotated(Rotation.COUNTERCLOCKWISE_90)
                                    )
                                );
                            }

                            if (!WoodlandMansionPieces.MansionGrid.isHouse(layout, i1x, i + 1)) {
                                BlockPos blockPos1 = var17.relative(rotation.rotate(Direction.EAST), 6);
                                blockPos1 = blockPos1.relative(rotation.rotate(Direction.SOUTH), 7);
                                pieces.add(
                                    new WoodlandMansionPieces.WoodlandMansionPiece(
                                        this.structureTemplateManager, "small_wall", blockPos1, rotation.getRotated(Rotation.CLOCKWISE_90)
                                    )
                                );
                            }

                            if (!WoodlandMansionPieces.MansionGrid.isHouse(layout, i1x + 1, i)) {
                                if (!WoodlandMansionPieces.MansionGrid.isHouse(layout, i1x, i - 1)) {
                                    BlockPos blockPos1 = var17.relative(rotation.rotate(Direction.EAST), 7);
                                    blockPos1 = blockPos1.relative(rotation.rotate(Direction.NORTH), 2);
                                    pieces.add(
                                        new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, "small_wall_corner", blockPos1, rotation)
                                    );
                                }

                                if (!WoodlandMansionPieces.MansionGrid.isHouse(layout, i1x, i + 1)) {
                                    BlockPos blockPos1 = var17.relative(rotation.rotate(Direction.EAST), 8);
                                    blockPos1 = blockPos1.relative(rotation.rotate(Direction.SOUTH), 7);
                                    pieces.add(
                                        new WoodlandMansionPieces.WoodlandMansionPiece(
                                            this.structureTemplateManager, "small_wall_corner", blockPos1, rotation.getRotated(Rotation.CLOCKWISE_90)
                                        )
                                    );
                                }
                            }

                            if (!WoodlandMansionPieces.MansionGrid.isHouse(layout, i1x - 1, i)) {
                                if (!WoodlandMansionPieces.MansionGrid.isHouse(layout, i1x, i - 1)) {
                                    BlockPos blockPos1 = var17.relative(rotation.rotate(Direction.WEST), 2);
                                    blockPos1 = blockPos1.relative(rotation.rotate(Direction.NORTH), 1);
                                    pieces.add(
                                        new WoodlandMansionPieces.WoodlandMansionPiece(
                                            this.structureTemplateManager, "small_wall_corner", blockPos1, rotation.getRotated(Rotation.COUNTERCLOCKWISE_90)
                                        )
                                    );
                                }

                                if (!WoodlandMansionPieces.MansionGrid.isHouse(layout, i1x, i + 1)) {
                                    BlockPos blockPos1 = var17.relative(rotation.rotate(Direction.WEST), 1);
                                    blockPos1 = blockPos1.relative(rotation.rotate(Direction.SOUTH), 8);
                                    pieces.add(
                                        new WoodlandMansionPieces.WoodlandMansionPiece(
                                            this.structureTemplateManager, "small_wall_corner", blockPos1, rotation.getRotated(Rotation.CLOCKWISE_180)
                                        )
                                    );
                                }
                            }
                        }
                    }
                }
            }

            for (int i = 0; i < layout.height; i++) {
                for (int i1xx = 0; i1xx < layout.width; i1xx++) {
                    BlockPos var19 = pos.relative(rotation.rotate(Direction.SOUTH), 8 + (i - this.startY) * 8);
                    var19 = var19.relative(rotation.rotate(Direction.EAST), (i1xx - this.startX) * 8);
                    boolean flag = nextFloorLayout != null && WoodlandMansionPieces.MansionGrid.isHouse(nextFloorLayout, i1xx, i);
                    if (WoodlandMansionPieces.MansionGrid.isHouse(layout, i1xx, i) && !flag) {
                        if (!WoodlandMansionPieces.MansionGrid.isHouse(layout, i1xx + 1, i)) {
                            BlockPos blockPos1 = var19.relative(rotation.rotate(Direction.EAST), 6);
                            if (!WoodlandMansionPieces.MansionGrid.isHouse(layout, i1xx, i + 1)) {
                                BlockPos blockPos2 = blockPos1.relative(rotation.rotate(Direction.SOUTH), 6);
                                pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, "roof_corner", blockPos2, rotation));
                            } else if (WoodlandMansionPieces.MansionGrid.isHouse(layout, i1xx + 1, i + 1)) {
                                BlockPos blockPos2 = blockPos1.relative(rotation.rotate(Direction.SOUTH), 5);
                                pieces.add(
                                    new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, "roof_inner_corner", blockPos2, rotation)
                                );
                            }

                            if (!WoodlandMansionPieces.MansionGrid.isHouse(layout, i1xx, i - 1)) {
                                pieces.add(
                                    new WoodlandMansionPieces.WoodlandMansionPiece(
                                        this.structureTemplateManager, "roof_corner", blockPos1, rotation.getRotated(Rotation.COUNTERCLOCKWISE_90)
                                    )
                                );
                            } else if (WoodlandMansionPieces.MansionGrid.isHouse(layout, i1xx + 1, i - 1)) {
                                BlockPos blockPos2 = var19.relative(rotation.rotate(Direction.EAST), 9);
                                blockPos2 = blockPos2.relative(rotation.rotate(Direction.NORTH), 2);
                                pieces.add(
                                    new WoodlandMansionPieces.WoodlandMansionPiece(
                                        this.structureTemplateManager, "roof_inner_corner", blockPos2, rotation.getRotated(Rotation.CLOCKWISE_90)
                                    )
                                );
                            }
                        }

                        if (!WoodlandMansionPieces.MansionGrid.isHouse(layout, i1xx - 1, i)) {
                            BlockPos blockPos1x = var19.relative(rotation.rotate(Direction.EAST), 0);
                            blockPos1x = blockPos1x.relative(rotation.rotate(Direction.SOUTH), 0);
                            if (!WoodlandMansionPieces.MansionGrid.isHouse(layout, i1xx, i + 1)) {
                                BlockPos blockPos2 = blockPos1x.relative(rotation.rotate(Direction.SOUTH), 6);
                                pieces.add(
                                    new WoodlandMansionPieces.WoodlandMansionPiece(
                                        this.structureTemplateManager, "roof_corner", blockPos2, rotation.getRotated(Rotation.CLOCKWISE_90)
                                    )
                                );
                            } else if (WoodlandMansionPieces.MansionGrid.isHouse(layout, i1xx - 1, i + 1)) {
                                BlockPos blockPos2 = blockPos1x.relative(rotation.rotate(Direction.SOUTH), 8);
                                blockPos2 = blockPos2.relative(rotation.rotate(Direction.WEST), 3);
                                pieces.add(
                                    new WoodlandMansionPieces.WoodlandMansionPiece(
                                        this.structureTemplateManager, "roof_inner_corner", blockPos2, rotation.getRotated(Rotation.COUNTERCLOCKWISE_90)
                                    )
                                );
                            }

                            if (!WoodlandMansionPieces.MansionGrid.isHouse(layout, i1xx, i - 1)) {
                                pieces.add(
                                    new WoodlandMansionPieces.WoodlandMansionPiece(
                                        this.structureTemplateManager, "roof_corner", blockPos1x, rotation.getRotated(Rotation.CLOCKWISE_180)
                                    )
                                );
                            } else if (WoodlandMansionPieces.MansionGrid.isHouse(layout, i1xx - 1, i - 1)) {
                                BlockPos blockPos2 = blockPos1x.relative(rotation.rotate(Direction.SOUTH), 1);
                                pieces.add(
                                    new WoodlandMansionPieces.WoodlandMansionPiece(
                                        this.structureTemplateManager, "roof_inner_corner", blockPos2, rotation.getRotated(Rotation.CLOCKWISE_180)
                                    )
                                );
                            }
                        }
                    }
                }
            }
        }

        private void entrance(List<WoodlandMansionPieces.WoodlandMansionPiece> pieces, WoodlandMansionPieces.PlacementData data) {
            Direction direction = data.rotation.rotate(Direction.WEST);
            pieces.add(
                new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, "entrance", data.position.relative(direction, 9), data.rotation)
            );
            data.position = data.position.relative(data.rotation.rotate(Direction.SOUTH), 16);
        }

        private void traverseWallPiece(List<WoodlandMansionPieces.WoodlandMansionPiece> pieces, WoodlandMansionPieces.PlacementData data) {
            pieces.add(
                new WoodlandMansionPieces.WoodlandMansionPiece(
                    this.structureTemplateManager, data.wallType, data.position.relative(data.rotation.rotate(Direction.EAST), 7), data.rotation
                )
            );
            data.position = data.position.relative(data.rotation.rotate(Direction.SOUTH), 8);
        }

        private void traverseTurn(List<WoodlandMansionPieces.WoodlandMansionPiece> pieces, WoodlandMansionPieces.PlacementData data) {
            data.position = data.position.relative(data.rotation.rotate(Direction.SOUTH), -1);
            pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, "wall_corner", data.position, data.rotation));
            data.position = data.position.relative(data.rotation.rotate(Direction.SOUTH), -7);
            data.position = data.position.relative(data.rotation.rotate(Direction.WEST), -6);
            data.rotation = data.rotation.getRotated(Rotation.CLOCKWISE_90);
        }

        private void traverseInnerTurn(List<WoodlandMansionPieces.WoodlandMansionPiece> pieces, WoodlandMansionPieces.PlacementData data) {
            data.position = data.position.relative(data.rotation.rotate(Direction.SOUTH), 6);
            data.position = data.position.relative(data.rotation.rotate(Direction.EAST), 8);
            data.rotation = data.rotation.getRotated(Rotation.COUNTERCLOCKWISE_90);
        }

        private void addRoom1x1(
            List<WoodlandMansionPieces.WoodlandMansionPiece> pieces,
            BlockPos pos,
            Rotation rotation,
            Direction direction,
            WoodlandMansionPieces.FloorRoomCollection floorRooms
        ) {
            Rotation rotation1 = Rotation.NONE;
            String string = floorRooms.get1x1(this.random);
            if (direction != Direction.EAST) {
                if (direction == Direction.NORTH) {
                    rotation1 = rotation1.getRotated(Rotation.COUNTERCLOCKWISE_90);
                } else if (direction == Direction.WEST) {
                    rotation1 = rotation1.getRotated(Rotation.CLOCKWISE_180);
                } else if (direction == Direction.SOUTH) {
                    rotation1 = rotation1.getRotated(Rotation.CLOCKWISE_90);
                } else {
                    string = floorRooms.get1x1Secret(this.random);
                }
            }

            BlockPos zeroPositionWithTransform = StructureTemplate.getZeroPositionWithTransform(new BlockPos(1, 0, 0), Mirror.NONE, rotation1, 7, 7);
            rotation1 = rotation1.getRotated(rotation);
            zeroPositionWithTransform = zeroPositionWithTransform.rotate(rotation);
            BlockPos blockPos = pos.offset(zeroPositionWithTransform.getX(), 0, zeroPositionWithTransform.getZ());
            pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, string, blockPos, rotation1));
        }

        private void addRoom1x2(
            List<WoodlandMansionPieces.WoodlandMansionPiece> pieces,
            BlockPos pos,
            Rotation rotation,
            Direction frontDirection,
            Direction sideDirection,
            WoodlandMansionPieces.FloorRoomCollection floorRooms,
            boolean isStairs
        ) {
            if (sideDirection == Direction.EAST && frontDirection == Direction.SOUTH) {
                BlockPos blockPos = pos.relative(rotation.rotate(Direction.EAST), 1);
                pieces.add(
                    new WoodlandMansionPieces.WoodlandMansionPiece(
                        this.structureTemplateManager, floorRooms.get1x2SideEntrance(this.random, isStairs), blockPos, rotation
                    )
                );
            } else if (sideDirection == Direction.EAST && frontDirection == Direction.NORTH) {
                BlockPos blockPos = pos.relative(rotation.rotate(Direction.EAST), 1);
                blockPos = blockPos.relative(rotation.rotate(Direction.SOUTH), 6);
                pieces.add(
                    new WoodlandMansionPieces.WoodlandMansionPiece(
                        this.structureTemplateManager, floorRooms.get1x2SideEntrance(this.random, isStairs), blockPos, rotation, Mirror.LEFT_RIGHT
                    )
                );
            } else if (sideDirection == Direction.WEST && frontDirection == Direction.NORTH) {
                BlockPos blockPos = pos.relative(rotation.rotate(Direction.EAST), 7);
                blockPos = blockPos.relative(rotation.rotate(Direction.SOUTH), 6);
                pieces.add(
                    new WoodlandMansionPieces.WoodlandMansionPiece(
                        this.structureTemplateManager,
                        floorRooms.get1x2SideEntrance(this.random, isStairs),
                        blockPos,
                        rotation.getRotated(Rotation.CLOCKWISE_180)
                    )
                );
            } else if (sideDirection == Direction.WEST && frontDirection == Direction.SOUTH) {
                BlockPos blockPos = pos.relative(rotation.rotate(Direction.EAST), 7);
                pieces.add(
                    new WoodlandMansionPieces.WoodlandMansionPiece(
                        this.structureTemplateManager, floorRooms.get1x2SideEntrance(this.random, isStairs), blockPos, rotation, Mirror.FRONT_BACK
                    )
                );
            } else if (sideDirection == Direction.SOUTH && frontDirection == Direction.EAST) {
                BlockPos blockPos = pos.relative(rotation.rotate(Direction.EAST), 1);
                pieces.add(
                    new WoodlandMansionPieces.WoodlandMansionPiece(
                        this.structureTemplateManager,
                        floorRooms.get1x2SideEntrance(this.random, isStairs),
                        blockPos,
                        rotation.getRotated(Rotation.CLOCKWISE_90),
                        Mirror.LEFT_RIGHT
                    )
                );
            } else if (sideDirection == Direction.SOUTH && frontDirection == Direction.WEST) {
                BlockPos blockPos = pos.relative(rotation.rotate(Direction.EAST), 7);
                pieces.add(
                    new WoodlandMansionPieces.WoodlandMansionPiece(
                        this.structureTemplateManager,
                        floorRooms.get1x2SideEntrance(this.random, isStairs),
                        blockPos,
                        rotation.getRotated(Rotation.CLOCKWISE_90)
                    )
                );
            } else if (sideDirection == Direction.NORTH && frontDirection == Direction.WEST) {
                BlockPos blockPos = pos.relative(rotation.rotate(Direction.EAST), 7);
                blockPos = blockPos.relative(rotation.rotate(Direction.SOUTH), 6);
                pieces.add(
                    new WoodlandMansionPieces.WoodlandMansionPiece(
                        this.structureTemplateManager,
                        floorRooms.get1x2SideEntrance(this.random, isStairs),
                        blockPos,
                        rotation.getRotated(Rotation.CLOCKWISE_90),
                        Mirror.FRONT_BACK
                    )
                );
            } else if (sideDirection == Direction.NORTH && frontDirection == Direction.EAST) {
                BlockPos blockPos = pos.relative(rotation.rotate(Direction.EAST), 1);
                blockPos = blockPos.relative(rotation.rotate(Direction.SOUTH), 6);
                pieces.add(
                    new WoodlandMansionPieces.WoodlandMansionPiece(
                        this.structureTemplateManager,
                        floorRooms.get1x2SideEntrance(this.random, isStairs),
                        blockPos,
                        rotation.getRotated(Rotation.COUNTERCLOCKWISE_90)
                    )
                );
            } else if (sideDirection == Direction.SOUTH && frontDirection == Direction.NORTH) {
                BlockPos blockPos = pos.relative(rotation.rotate(Direction.EAST), 1);
                blockPos = blockPos.relative(rotation.rotate(Direction.NORTH), 8);
                pieces.add(
                    new WoodlandMansionPieces.WoodlandMansionPiece(
                        this.structureTemplateManager, floorRooms.get1x2FrontEntrance(this.random, isStairs), blockPos, rotation
                    )
                );
            } else if (sideDirection == Direction.NORTH && frontDirection == Direction.SOUTH) {
                BlockPos blockPos = pos.relative(rotation.rotate(Direction.EAST), 7);
                blockPos = blockPos.relative(rotation.rotate(Direction.SOUTH), 14);
                pieces.add(
                    new WoodlandMansionPieces.WoodlandMansionPiece(
                        this.structureTemplateManager,
                        floorRooms.get1x2FrontEntrance(this.random, isStairs),
                        blockPos,
                        rotation.getRotated(Rotation.CLOCKWISE_180)
                    )
                );
            } else if (sideDirection == Direction.WEST && frontDirection == Direction.EAST) {
                BlockPos blockPos = pos.relative(rotation.rotate(Direction.EAST), 15);
                pieces.add(
                    new WoodlandMansionPieces.WoodlandMansionPiece(
                        this.structureTemplateManager,
                        floorRooms.get1x2FrontEntrance(this.random, isStairs),
                        blockPos,
                        rotation.getRotated(Rotation.CLOCKWISE_90)
                    )
                );
            } else if (sideDirection == Direction.EAST && frontDirection == Direction.WEST) {
                BlockPos blockPos = pos.relative(rotation.rotate(Direction.WEST), 7);
                blockPos = blockPos.relative(rotation.rotate(Direction.SOUTH), 6);
                pieces.add(
                    new WoodlandMansionPieces.WoodlandMansionPiece(
                        this.structureTemplateManager,
                        floorRooms.get1x2FrontEntrance(this.random, isStairs),
                        blockPos,
                        rotation.getRotated(Rotation.COUNTERCLOCKWISE_90)
                    )
                );
            } else if (sideDirection == Direction.UP && frontDirection == Direction.EAST) {
                BlockPos blockPos = pos.relative(rotation.rotate(Direction.EAST), 15);
                pieces.add(
                    new WoodlandMansionPieces.WoodlandMansionPiece(
                        this.structureTemplateManager, floorRooms.get1x2Secret(this.random), blockPos, rotation.getRotated(Rotation.CLOCKWISE_90)
                    )
                );
            } else if (sideDirection == Direction.UP && frontDirection == Direction.SOUTH) {
                BlockPos blockPos = pos.relative(rotation.rotate(Direction.EAST), 1);
                blockPos = blockPos.relative(rotation.rotate(Direction.NORTH), 0);
                pieces.add(
                    new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, floorRooms.get1x2Secret(this.random), blockPos, rotation)
                );
            }
        }

        private void addRoom2x2(
            List<WoodlandMansionPieces.WoodlandMansionPiece> pieces,
            BlockPos pos,
            Rotation rotation,
            Direction frontDirection,
            Direction sideDirection,
            WoodlandMansionPieces.FloorRoomCollection floorRooms
        ) {
            int i = 0;
            int i1 = 0;
            Rotation rotation1 = rotation;
            Mirror mirror = Mirror.NONE;
            if (sideDirection == Direction.EAST && frontDirection == Direction.SOUTH) {
                i = -7;
            } else if (sideDirection == Direction.EAST && frontDirection == Direction.NORTH) {
                i = -7;
                i1 = 6;
                mirror = Mirror.LEFT_RIGHT;
            } else if (sideDirection == Direction.NORTH && frontDirection == Direction.EAST) {
                i = 1;
                i1 = 14;
                rotation1 = rotation.getRotated(Rotation.COUNTERCLOCKWISE_90);
            } else if (sideDirection == Direction.NORTH && frontDirection == Direction.WEST) {
                i = 7;
                i1 = 14;
                rotation1 = rotation.getRotated(Rotation.COUNTERCLOCKWISE_90);
                mirror = Mirror.LEFT_RIGHT;
            } else if (sideDirection == Direction.SOUTH && frontDirection == Direction.WEST) {
                i = 7;
                i1 = -8;
                rotation1 = rotation.getRotated(Rotation.CLOCKWISE_90);
            } else if (sideDirection == Direction.SOUTH && frontDirection == Direction.EAST) {
                i = 1;
                i1 = -8;
                rotation1 = rotation.getRotated(Rotation.CLOCKWISE_90);
                mirror = Mirror.LEFT_RIGHT;
            } else if (sideDirection == Direction.WEST && frontDirection == Direction.NORTH) {
                i = 15;
                i1 = 6;
                rotation1 = rotation.getRotated(Rotation.CLOCKWISE_180);
            } else if (sideDirection == Direction.WEST && frontDirection == Direction.SOUTH) {
                i = 15;
                mirror = Mirror.FRONT_BACK;
            }

            BlockPos blockPos = pos.relative(rotation.rotate(Direction.EAST), i);
            blockPos = blockPos.relative(rotation.rotate(Direction.SOUTH), i1);
            pieces.add(
                new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, floorRooms.get2x2(this.random), blockPos, rotation1, mirror)
            );
        }

        private void addRoom2x2Secret(
            List<WoodlandMansionPieces.WoodlandMansionPiece> pieces, BlockPos pos, Rotation rotation, WoodlandMansionPieces.FloorRoomCollection floorRooms
        ) {
            BlockPos blockPos = pos.relative(rotation.rotate(Direction.EAST), 1);
            pieces.add(
                new WoodlandMansionPieces.WoodlandMansionPiece(
                    this.structureTemplateManager, floorRooms.get2x2Secret(this.random), blockPos, rotation, Mirror.NONE
                )
            );
        }
    }

    static class PlacementData {
        public Rotation rotation;
        public BlockPos position;
        public String wallType;
    }

    static class SecondFloorRoomCollection extends WoodlandMansionPieces.FloorRoomCollection {
        @Override
        public String get1x1(RandomSource random) {
            return "1x1_b" + (random.nextInt(5) + 1);
        }

        @Override
        public String get1x1Secret(RandomSource random) {
            return "1x1_as" + (random.nextInt(4) + 1);
        }

        @Override
        public String get1x2SideEntrance(RandomSource random, boolean isStairs) {
            return isStairs ? "1x2_c_stairs" : "1x2_c" + (random.nextInt(4) + 1);
        }

        @Override
        public String get1x2FrontEntrance(RandomSource random, boolean isStairs) {
            return isStairs ? "1x2_d_stairs" : "1x2_d" + (random.nextInt(5) + 1);
        }

        @Override
        public String get1x2Secret(RandomSource random) {
            return "1x2_se" + (random.nextInt(1) + 1);
        }

        @Override
        public String get2x2(RandomSource random) {
            return "2x2_b" + (random.nextInt(5) + 1);
        }

        @Override
        public String get2x2Secret(RandomSource random) {
            return "2x2_s1";
        }
    }

    static class SimpleGrid {
        private final int[][] grid;
        final int width;
        final int height;
        private final int valueIfOutside;

        public SimpleGrid(int width, int height, int valueIfOutside) {
            this.width = width;
            this.height = height;
            this.valueIfOutside = valueIfOutside;
            this.grid = new int[width][height];
        }

        public void set(int x, int y, int value) {
            if (x >= 0 && x < this.width && y >= 0 && y < this.height) {
                this.grid[x][y] = value;
            }
        }

        public void set(int minX, int minY, int maxX, int maxY, int value) {
            for (int i = minY; i <= maxY; i++) {
                for (int i1 = minX; i1 <= maxX; i1++) {
                    this.set(i1, i, value);
                }
            }
        }

        public int get(int x, int y) {
            return x >= 0 && x < this.width && y >= 0 && y < this.height ? this.grid[x][y] : this.valueIfOutside;
        }

        public void setif(int x, int y, int oldValue, int newValue) {
            if (this.get(x, y) == oldValue) {
                this.set(x, y, newValue);
            }
        }

        public boolean edgesTo(int x, int y, int expectedValue) {
            return this.get(x - 1, y) == expectedValue
                || this.get(x + 1, y) == expectedValue
                || this.get(x, y + 1) == expectedValue
                || this.get(x, y - 1) == expectedValue;
        }
    }

    static class ThirdFloorRoomCollection extends WoodlandMansionPieces.SecondFloorRoomCollection {
    }

    public static class WoodlandMansionPiece extends TemplateStructurePiece {
        public WoodlandMansionPiece(StructureTemplateManager structureTemplateManager, String templateName, BlockPos templatePosition, Rotation rotation) {
            this(structureTemplateManager, templateName, templatePosition, rotation, Mirror.NONE);
        }

        public WoodlandMansionPiece(
            StructureTemplateManager structureTemplateManager, String templateName, BlockPos templatePosition, Rotation rotation, Mirror mirror
        ) {
            super(
                StructurePieceType.WOODLAND_MANSION_PIECE,
                0,
                structureTemplateManager,
                makeLocation(templateName),
                templateName,
                makeSettings(mirror, rotation),
                templatePosition
            );
        }

        public WoodlandMansionPiece(StructureTemplateManager structureTemplateManager, CompoundTag tag) {
            super(
                StructurePieceType.WOODLAND_MANSION_PIECE,
                tag,
                structureTemplateManager,
                identifier -> makeSettings(tag.read("Mi", Mirror.LEGACY_CODEC).orElseThrow(), tag.read("Rot", Rotation.LEGACY_CODEC).orElseThrow())
            );
        }

        @Override
        protected Identifier makeTemplateLocation() {
            return makeLocation(this.templateName);
        }

        private static Identifier makeLocation(String name) {
            return Identifier.withDefaultNamespace("woodland_mansion/" + name);
        }

        private static StructurePlaceSettings makeSettings(Mirror mirror, Rotation rotation) {
            return new StructurePlaceSettings()
                .setIgnoreEntities(true)
                .setRotation(rotation)
                .setMirror(mirror)
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            super.addAdditionalSaveData(context, tag);
            tag.store("Rot", Rotation.LEGACY_CODEC, this.placeSettings.getRotation());
            tag.store("Mi", Mirror.LEGACY_CODEC, this.placeSettings.getMirror());
        }

        @Override
        protected void handleDataMarker(String name, BlockPos pos, ServerLevelAccessor level, RandomSource random, BoundingBox box) {
            if (name.startsWith("Chest")) {
                Rotation rotation = this.placeSettings.getRotation();
                BlockState blockState = Blocks.CHEST.defaultBlockState();
                if ("ChestWest".equals(name)) {
                    blockState = blockState.setValue(ChestBlock.FACING, rotation.rotate(Direction.WEST));
                } else if ("ChestEast".equals(name)) {
                    blockState = blockState.setValue(ChestBlock.FACING, rotation.rotate(Direction.EAST));
                } else if ("ChestSouth".equals(name)) {
                    blockState = blockState.setValue(ChestBlock.FACING, rotation.rotate(Direction.SOUTH));
                } else if ("ChestNorth".equals(name)) {
                    blockState = blockState.setValue(ChestBlock.FACING, rotation.rotate(Direction.NORTH));
                }

                this.createChest(level, box, random, pos, BuiltInLootTables.WOODLAND_MANSION, blockState);
            } else {
                List<Mob> list = new ArrayList<>();
                switch (name) {
                    case "Mage":
                        list.add(EntityType.EVOKER.create(level.getLevel(), EntitySpawnReason.STRUCTURE));
                        break;
                    case "Warrior":
                        list.add(EntityType.VINDICATOR.create(level.getLevel(), EntitySpawnReason.STRUCTURE));
                        break;
                    case "Group of Allays":
                        int i = level.getRandom().nextInt(3) + 1;

                        for (int i1 = 0; i1 < i; i1++) {
                            list.add(EntityType.ALLAY.create(level.getLevel(), EntitySpawnReason.STRUCTURE));
                        }
                        break;
                    default:
                        return;
                }

                for (Mob mob : list) {
                    if (mob != null) {
                        mob.setPersistenceRequired();
                        mob.snapTo(pos, 0.0F, 0.0F);
                        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), EntitySpawnReason.STRUCTURE, null);
                        level.addFreshEntityWithPassengers(mob);
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }
}
