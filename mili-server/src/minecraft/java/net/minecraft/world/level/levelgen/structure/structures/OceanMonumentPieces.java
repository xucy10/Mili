package net.minecraft.world.level.levelgen.structure.structures;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

public class OceanMonumentPieces {
    private OceanMonumentPieces() {
    }

    static class FitDoubleXRoom implements OceanMonumentPieces.MonumentRoomFitter {
        @Override
        public boolean fits(OceanMonumentPieces.RoomDefinition room) {
            return room.hasOpening[Direction.EAST.get3DDataValue()] && !room.connections[Direction.EAST.get3DDataValue()].claimed;
        }

        @Override
        public OceanMonumentPieces.OceanMonumentPiece create(Direction direction, OceanMonumentPieces.RoomDefinition room, RandomSource random) {
            room.claimed = true;
            room.connections[Direction.EAST.get3DDataValue()].claimed = true;
            return new OceanMonumentPieces.OceanMonumentDoubleXRoom(direction, room);
        }
    }

    static class FitDoubleXYRoom implements OceanMonumentPieces.MonumentRoomFitter {
        @Override
        public boolean fits(OceanMonumentPieces.RoomDefinition room) {
            if (room.hasOpening[Direction.EAST.get3DDataValue()]
                && !room.connections[Direction.EAST.get3DDataValue()].claimed
                && room.hasOpening[Direction.UP.get3DDataValue()]
                && !room.connections[Direction.UP.get3DDataValue()].claimed) {
                OceanMonumentPieces.RoomDefinition roomDefinition = room.connections[Direction.EAST.get3DDataValue()];
                return roomDefinition.hasOpening[Direction.UP.get3DDataValue()] && !roomDefinition.connections[Direction.UP.get3DDataValue()].claimed;
            } else {
                return false;
            }
        }

        @Override
        public OceanMonumentPieces.OceanMonumentPiece create(Direction direction, OceanMonumentPieces.RoomDefinition room, RandomSource random) {
            room.claimed = true;
            room.connections[Direction.EAST.get3DDataValue()].claimed = true;
            room.connections[Direction.UP.get3DDataValue()].claimed = true;
            room.connections[Direction.EAST.get3DDataValue()].connections[Direction.UP.get3DDataValue()].claimed = true;
            return new OceanMonumentPieces.OceanMonumentDoubleXYRoom(direction, room);
        }
    }

    static class FitDoubleYRoom implements OceanMonumentPieces.MonumentRoomFitter {
        @Override
        public boolean fits(OceanMonumentPieces.RoomDefinition room) {
            return room.hasOpening[Direction.UP.get3DDataValue()] && !room.connections[Direction.UP.get3DDataValue()].claimed;
        }

        @Override
        public OceanMonumentPieces.OceanMonumentPiece create(Direction direction, OceanMonumentPieces.RoomDefinition room, RandomSource random) {
            room.claimed = true;
            room.connections[Direction.UP.get3DDataValue()].claimed = true;
            return new OceanMonumentPieces.OceanMonumentDoubleYRoom(direction, room);
        }
    }

    static class FitDoubleYZRoom implements OceanMonumentPieces.MonumentRoomFitter {
        @Override
        public boolean fits(OceanMonumentPieces.RoomDefinition room) {
            if (room.hasOpening[Direction.NORTH.get3DDataValue()]
                && !room.connections[Direction.NORTH.get3DDataValue()].claimed
                && room.hasOpening[Direction.UP.get3DDataValue()]
                && !room.connections[Direction.UP.get3DDataValue()].claimed) {
                OceanMonumentPieces.RoomDefinition roomDefinition = room.connections[Direction.NORTH.get3DDataValue()];
                return roomDefinition.hasOpening[Direction.UP.get3DDataValue()] && !roomDefinition.connections[Direction.UP.get3DDataValue()].claimed;
            } else {
                return false;
            }
        }

        @Override
        public OceanMonumentPieces.OceanMonumentPiece create(Direction direction, OceanMonumentPieces.RoomDefinition room, RandomSource random) {
            room.claimed = true;
            room.connections[Direction.NORTH.get3DDataValue()].claimed = true;
            room.connections[Direction.UP.get3DDataValue()].claimed = true;
            room.connections[Direction.NORTH.get3DDataValue()].connections[Direction.UP.get3DDataValue()].claimed = true;
            return new OceanMonumentPieces.OceanMonumentDoubleYZRoom(direction, room);
        }
    }

    static class FitDoubleZRoom implements OceanMonumentPieces.MonumentRoomFitter {
        @Override
        public boolean fits(OceanMonumentPieces.RoomDefinition room) {
            return room.hasOpening[Direction.NORTH.get3DDataValue()] && !room.connections[Direction.NORTH.get3DDataValue()].claimed;
        }

        @Override
        public OceanMonumentPieces.OceanMonumentPiece create(Direction direction, OceanMonumentPieces.RoomDefinition room, RandomSource random) {
            OceanMonumentPieces.RoomDefinition roomDefinition = room;
            if (!room.hasOpening[Direction.NORTH.get3DDataValue()] || room.connections[Direction.NORTH.get3DDataValue()].claimed) {
                roomDefinition = room.connections[Direction.SOUTH.get3DDataValue()];
            }

            roomDefinition.claimed = true;
            roomDefinition.connections[Direction.NORTH.get3DDataValue()].claimed = true;
            return new OceanMonumentPieces.OceanMonumentDoubleZRoom(direction, roomDefinition);
        }
    }

    static class FitSimpleRoom implements OceanMonumentPieces.MonumentRoomFitter {
        @Override
        public boolean fits(OceanMonumentPieces.RoomDefinition room) {
            return true;
        }

        @Override
        public OceanMonumentPieces.OceanMonumentPiece create(Direction direction, OceanMonumentPieces.RoomDefinition room, RandomSource random) {
            room.claimed = true;
            return new OceanMonumentPieces.OceanMonumentSimpleRoom(direction, room, random);
        }
    }

    static class FitSimpleTopRoom implements OceanMonumentPieces.MonumentRoomFitter {
        @Override
        public boolean fits(OceanMonumentPieces.RoomDefinition room) {
            return !room.hasOpening[Direction.WEST.get3DDataValue()]
                && !room.hasOpening[Direction.EAST.get3DDataValue()]
                && !room.hasOpening[Direction.NORTH.get3DDataValue()]
                && !room.hasOpening[Direction.SOUTH.get3DDataValue()]
                && !room.hasOpening[Direction.UP.get3DDataValue()];
        }

        @Override
        public OceanMonumentPieces.OceanMonumentPiece create(Direction direction, OceanMonumentPieces.RoomDefinition room, RandomSource random) {
            room.claimed = true;
            return new OceanMonumentPieces.OceanMonumentSimpleTopRoom(direction, room);
        }
    }

    public static class MonumentBuilding extends OceanMonumentPieces.OceanMonumentPiece {
        private static final int WIDTH = 58;
        private static final int HEIGHT = 22;
        private static final int DEPTH = 58;
        public static final int BIOME_RANGE_CHECK = 29;
        private static final int TOP_POSITION = 61;
        private OceanMonumentPieces.RoomDefinition sourceRoom;
        private OceanMonumentPieces.RoomDefinition coreRoom;
        private final List<OceanMonumentPieces.OceanMonumentPiece> childPieces = Lists.newArrayList();

        public MonumentBuilding(RandomSource random, int x, int z, Direction orientation) {
            super(StructurePieceType.OCEAN_MONUMENT_BUILDING, orientation, 0, makeBoundingBox(x, 39, z, orientation, 58, 23, 58));
            this.setOrientation(orientation);
            List<OceanMonumentPieces.RoomDefinition> list = this.generateRoomGraph(random);
            this.sourceRoom.claimed = true;
            this.childPieces.add(new OceanMonumentPieces.OceanMonumentEntryRoom(orientation, this.sourceRoom));
            this.childPieces.add(new OceanMonumentPieces.OceanMonumentCoreRoom(orientation, this.coreRoom));
            List<OceanMonumentPieces.MonumentRoomFitter> list1 = Lists.newArrayList();
            list1.add(new OceanMonumentPieces.FitDoubleXYRoom());
            list1.add(new OceanMonumentPieces.FitDoubleYZRoom());
            list1.add(new OceanMonumentPieces.FitDoubleZRoom());
            list1.add(new OceanMonumentPieces.FitDoubleXRoom());
            list1.add(new OceanMonumentPieces.FitDoubleYRoom());
            list1.add(new OceanMonumentPieces.FitSimpleTopRoom());
            list1.add(new OceanMonumentPieces.FitSimpleRoom());

            for (OceanMonumentPieces.RoomDefinition roomDefinition : list) {
                if (!roomDefinition.claimed && !roomDefinition.isSpecial()) {
                    for (OceanMonumentPieces.MonumentRoomFitter monumentRoomFitter : list1) {
                        if (monumentRoomFitter.fits(roomDefinition)) {
                            this.childPieces.add(monumentRoomFitter.create(orientation, roomDefinition, random));
                            break;
                        }
                    }
                }
            }

            BlockPos worldPos = this.getWorldPos(9, 0, 22);

            for (OceanMonumentPieces.OceanMonumentPiece oceanMonumentPiece : this.childPieces) {
                oceanMonumentPiece.getBoundingBox().move(worldPos);
            }

            BoundingBox boundingBox = BoundingBox.fromCorners(this.getWorldPos(1, 1, 1), this.getWorldPos(23, 8, 21));
            BoundingBox boundingBox1 = BoundingBox.fromCorners(this.getWorldPos(34, 1, 1), this.getWorldPos(56, 8, 21));
            BoundingBox boundingBox2 = BoundingBox.fromCorners(this.getWorldPos(22, 13, 22), this.getWorldPos(35, 17, 35));
            int randomInt = random.nextInt();
            this.childPieces.add(new OceanMonumentPieces.OceanMonumentWingRoom(orientation, boundingBox, randomInt++));
            this.childPieces.add(new OceanMonumentPieces.OceanMonumentWingRoom(orientation, boundingBox1, randomInt++));
            this.childPieces.add(new OceanMonumentPieces.OceanMonumentPenthouse(orientation, boundingBox2));
        }

        public MonumentBuilding(CompoundTag tag) {
            super(StructurePieceType.OCEAN_MONUMENT_BUILDING, tag);
        }

        private List<OceanMonumentPieces.RoomDefinition> generateRoomGraph(RandomSource random) {
            OceanMonumentPieces.RoomDefinition[] roomDefinitions = new OceanMonumentPieces.RoomDefinition[75];

            for (int i = 0; i < 5; i++) {
                for (int i1 = 0; i1 < 4; i1++) {
                    int i2 = 0;
                    int roomIndex = getRoomIndex(i, 0, i1);
                    roomDefinitions[roomIndex] = new OceanMonumentPieces.RoomDefinition(roomIndex);
                }
            }

            for (int i = 0; i < 5; i++) {
                for (int i1 = 0; i1 < 4; i1++) {
                    int i2 = 1;
                    int roomIndex = getRoomIndex(i, 1, i1);
                    roomDefinitions[roomIndex] = new OceanMonumentPieces.RoomDefinition(roomIndex);
                }
            }

            for (int i = 1; i < 4; i++) {
                for (int i1 = 0; i1 < 2; i1++) {
                    int i2 = 2;
                    int roomIndex = getRoomIndex(i, 2, i1);
                    roomDefinitions[roomIndex] = new OceanMonumentPieces.RoomDefinition(roomIndex);
                }
            }

            this.sourceRoom = roomDefinitions[GRIDROOM_SOURCE_INDEX];

            for (int i = 0; i < 5; i++) {
                for (int i1 = 0; i1 < 5; i1++) {
                    for (int i2 = 0; i2 < 3; i2++) {
                        int roomIndex = getRoomIndex(i, i2, i1);
                        if (roomDefinitions[roomIndex] != null) {
                            for (Direction direction : Direction.values()) {
                                int i3 = i + direction.getStepX();
                                int i4 = i2 + direction.getStepY();
                                int i5 = i1 + direction.getStepZ();
                                if (i3 >= 0 && i3 < 5 && i5 >= 0 && i5 < 5 && i4 >= 0 && i4 < 3) {
                                    int roomIndex1 = getRoomIndex(i3, i4, i5);
                                    if (roomDefinitions[roomIndex1] != null) {
                                        if (i5 == i1) {
                                            roomDefinitions[roomIndex].setConnection(direction, roomDefinitions[roomIndex1]);
                                        } else {
                                            roomDefinitions[roomIndex].setConnection(direction.getOpposite(), roomDefinitions[roomIndex1]);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            OceanMonumentPieces.RoomDefinition roomDefinition = new OceanMonumentPieces.RoomDefinition(1003);
            OceanMonumentPieces.RoomDefinition roomDefinition1 = new OceanMonumentPieces.RoomDefinition(1001);
            OceanMonumentPieces.RoomDefinition roomDefinition2 = new OceanMonumentPieces.RoomDefinition(1002);
            roomDefinitions[GRIDROOM_TOP_CONNECT_INDEX].setConnection(Direction.UP, roomDefinition);
            roomDefinitions[GRIDROOM_LEFTWING_CONNECT_INDEX].setConnection(Direction.SOUTH, roomDefinition1);
            roomDefinitions[GRIDROOM_RIGHTWING_CONNECT_INDEX].setConnection(Direction.SOUTH, roomDefinition2);
            roomDefinition.claimed = true;
            roomDefinition1.claimed = true;
            roomDefinition2.claimed = true;
            this.sourceRoom.isSource = true;
            this.coreRoom = roomDefinitions[getRoomIndex(random.nextInt(4), 0, 2)];
            this.coreRoom.claimed = true;
            this.coreRoom.connections[Direction.EAST.get3DDataValue()].claimed = true;
            this.coreRoom.connections[Direction.NORTH.get3DDataValue()].claimed = true;
            this.coreRoom.connections[Direction.EAST.get3DDataValue()].connections[Direction.NORTH.get3DDataValue()].claimed = true;
            this.coreRoom.connections[Direction.UP.get3DDataValue()].claimed = true;
            this.coreRoom.connections[Direction.EAST.get3DDataValue()].connections[Direction.UP.get3DDataValue()].claimed = true;
            this.coreRoom.connections[Direction.NORTH.get3DDataValue()].connections[Direction.UP.get3DDataValue()].claimed = true;
            this.coreRoom.connections[Direction.EAST.get3DDataValue()].connections[Direction.NORTH.get3DDataValue()].connections[Direction.UP.get3DDataValue()].claimed = true;
            ObjectArrayList<OceanMonumentPieces.RoomDefinition> list = new ObjectArrayList<>();

            for (OceanMonumentPieces.RoomDefinition roomDefinition3 : roomDefinitions) {
                if (roomDefinition3 != null) {
                    roomDefinition3.updateOpenings();
                    list.add(roomDefinition3);
                }
            }

            roomDefinition.updateOpenings();
            Util.shuffle(list, random);
            int i6 = 1;

            for (OceanMonumentPieces.RoomDefinition roomDefinition4 : list) {
                int i7 = 0;
                int i3 = 0;

                while (i7 < 2 && i3 < 5) {
                    i3++;
                    int i4 = random.nextInt(6);
                    if (roomDefinition4.hasOpening[i4]) {
                        int i5 = Direction.from3DDataValue(i4).getOpposite().get3DDataValue();
                        roomDefinition4.hasOpening[i4] = false;
                        roomDefinition4.connections[i4].hasOpening[i5] = false;
                        if (roomDefinition4.findSource(i6++) && roomDefinition4.connections[i4].findSource(i6++)) {
                            i7++;
                        } else {
                            roomDefinition4.hasOpening[i4] = true;
                            roomDefinition4.connections[i4].hasOpening[i5] = true;
                        }
                    }
                }
            }

            list.add(roomDefinition);
            list.add(roomDefinition1);
            list.add(roomDefinition2);
            return list;
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
            int i = Math.max(level.getSeaLevel(), 64) - this.boundingBox.minY();
            this.generateWaterBox(level, box, 0, 0, 0, 58, i, 58);
            this.generateWing(false, 0, level, random, box);
            this.generateWing(true, 33, level, random, box);
            this.generateEntranceArchs(level, random, box);
            this.generateEntranceWall(level, random, box);
            this.generateRoofPiece(level, random, box);
            this.generateLowerWall(level, random, box);
            this.generateMiddleWall(level, random, box);
            this.generateUpperWall(level, random, box);

            for (int i1 = 0; i1 < 7; i1++) {
                int i2 = 0;

                while (i2 < 7) {
                    if (i2 == 0 && i1 == 3) {
                        i2 = 6;
                    }

                    int i3 = i1 * 9;
                    int i4 = i2 * 9;

                    for (int i5 = 0; i5 < 4; i5++) {
                        for (int i6 = 0; i6 < 4; i6++) {
                            this.placeBlock(level, BASE_LIGHT, i3 + i5, 0, i4 + i6, box);
                            this.fillColumnDown(level, BASE_LIGHT, i3 + i5, -1, i4 + i6, box);
                        }
                    }

                    if (i1 != 0 && i1 != 6) {
                        i2 += 6;
                    } else {
                        i2++;
                    }
                }
            }

            for (int i1 = 0; i1 < 5; i1++) {
                this.generateWaterBox(level, box, -1 - i1, 0 + i1 * 2, -1 - i1, -1 - i1, 23, 58 + i1);
                this.generateWaterBox(level, box, 58 + i1, 0 + i1 * 2, -1 - i1, 58 + i1, 23, 58 + i1);
                this.generateWaterBox(level, box, 0 - i1, 0 + i1 * 2, -1 - i1, 57 + i1, 23, -1 - i1);
                this.generateWaterBox(level, box, 0 - i1, 0 + i1 * 2, 58 + i1, 57 + i1, 23, 58 + i1);
            }

            for (OceanMonumentPieces.OceanMonumentPiece oceanMonumentPiece : this.childPieces) {
                if (oceanMonumentPiece.getBoundingBox().intersects(box)) {
                    oceanMonumentPiece.postProcess(level, structureManager, generator, random, box, chunkPos, pos);
                }
            }
        }

        private void generateWing(boolean wing, int x, WorldGenLevel level, RandomSource random, BoundingBox box) {
            int i = 24;
            if (this.chunkIntersects(box, x, 0, x + 23, 20)) {
                this.generateBox(level, box, x + 0, 0, 0, x + 24, 0, 20, BASE_GRAY, BASE_GRAY, false);
                this.generateWaterBox(level, box, x + 0, 1, 0, x + 24, 10, 20);

                for (int i1 = 0; i1 < 4; i1++) {
                    this.generateBox(level, box, x + i1, i1 + 1, i1, x + i1, i1 + 1, 20, BASE_LIGHT, BASE_LIGHT, false);
                    this.generateBox(level, box, x + i1 + 7, i1 + 5, i1 + 7, x + i1 + 7, i1 + 5, 20, BASE_LIGHT, BASE_LIGHT, false);
                    this.generateBox(level, box, x + 17 - i1, i1 + 5, i1 + 7, x + 17 - i1, i1 + 5, 20, BASE_LIGHT, BASE_LIGHT, false);
                    this.generateBox(level, box, x + 24 - i1, i1 + 1, i1, x + 24 - i1, i1 + 1, 20, BASE_LIGHT, BASE_LIGHT, false);
                    this.generateBox(level, box, x + i1 + 1, i1 + 1, i1, x + 23 - i1, i1 + 1, i1, BASE_LIGHT, BASE_LIGHT, false);
                    this.generateBox(level, box, x + i1 + 8, i1 + 5, i1 + 7, x + 16 - i1, i1 + 5, i1 + 7, BASE_LIGHT, BASE_LIGHT, false);
                }

                this.generateBox(level, box, x + 4, 4, 4, x + 6, 4, 20, BASE_GRAY, BASE_GRAY, false);
                this.generateBox(level, box, x + 7, 4, 4, x + 17, 4, 6, BASE_GRAY, BASE_GRAY, false);
                this.generateBox(level, box, x + 18, 4, 4, x + 20, 4, 20, BASE_GRAY, BASE_GRAY, false);
                this.generateBox(level, box, x + 11, 8, 11, x + 13, 8, 20, BASE_GRAY, BASE_GRAY, false);
                this.placeBlock(level, DOT_DECO_DATA, x + 12, 9, 12, box);
                this.placeBlock(level, DOT_DECO_DATA, x + 12, 9, 15, box);
                this.placeBlock(level, DOT_DECO_DATA, x + 12, 9, 18, box);
                int i1 = x + (wing ? 19 : 5);
                int i2 = x + (wing ? 5 : 19);

                for (int i3 = 20; i3 >= 5; i3 -= 3) {
                    this.placeBlock(level, DOT_DECO_DATA, i1, 5, i3, box);
                }

                for (int i3 = 19; i3 >= 7; i3 -= 3) {
                    this.placeBlock(level, DOT_DECO_DATA, i2, 5, i3, box);
                }

                for (int i3 = 0; i3 < 4; i3++) {
                    int i4 = wing ? x + 24 - (17 - i3 * 3) : x + 17 - i3 * 3;
                    this.placeBlock(level, DOT_DECO_DATA, i4, 5, 5, box);
                }

                this.placeBlock(level, DOT_DECO_DATA, i2, 5, 5, box);
                this.generateBox(level, box, x + 11, 1, 12, x + 13, 7, 12, BASE_GRAY, BASE_GRAY, false);
                this.generateBox(level, box, x + 12, 1, 11, x + 12, 7, 13, BASE_GRAY, BASE_GRAY, false);
            }
        }

        private void generateEntranceArchs(WorldGenLevel level, RandomSource random, BoundingBox box) {
            if (this.chunkIntersects(box, 22, 5, 35, 17)) {
                this.generateWaterBox(level, box, 25, 0, 0, 32, 8, 20);

                for (int i = 0; i < 4; i++) {
                    this.generateBox(level, box, 24, 2, 5 + i * 4, 24, 4, 5 + i * 4, BASE_LIGHT, BASE_LIGHT, false);
                    this.generateBox(level, box, 22, 4, 5 + i * 4, 23, 4, 5 + i * 4, BASE_LIGHT, BASE_LIGHT, false);
                    this.placeBlock(level, BASE_LIGHT, 25, 5, 5 + i * 4, box);
                    this.placeBlock(level, BASE_LIGHT, 26, 6, 5 + i * 4, box);
                    this.placeBlock(level, LAMP_BLOCK, 26, 5, 5 + i * 4, box);
                    this.generateBox(level, box, 33, 2, 5 + i * 4, 33, 4, 5 + i * 4, BASE_LIGHT, BASE_LIGHT, false);
                    this.generateBox(level, box, 34, 4, 5 + i * 4, 35, 4, 5 + i * 4, BASE_LIGHT, BASE_LIGHT, false);
                    this.placeBlock(level, BASE_LIGHT, 32, 5, 5 + i * 4, box);
                    this.placeBlock(level, BASE_LIGHT, 31, 6, 5 + i * 4, box);
                    this.placeBlock(level, LAMP_BLOCK, 31, 5, 5 + i * 4, box);
                    this.generateBox(level, box, 27, 6, 5 + i * 4, 30, 6, 5 + i * 4, BASE_GRAY, BASE_GRAY, false);
                }
            }
        }

        private void generateEntranceWall(WorldGenLevel level, RandomSource random, BoundingBox box) {
            if (this.chunkIntersects(box, 15, 20, 42, 21)) {
                this.generateBox(level, box, 15, 0, 21, 42, 0, 21, BASE_GRAY, BASE_GRAY, false);
                this.generateWaterBox(level, box, 26, 1, 21, 31, 3, 21);
                this.generateBox(level, box, 21, 12, 21, 36, 12, 21, BASE_GRAY, BASE_GRAY, false);
                this.generateBox(level, box, 17, 11, 21, 40, 11, 21, BASE_GRAY, BASE_GRAY, false);
                this.generateBox(level, box, 16, 10, 21, 41, 10, 21, BASE_GRAY, BASE_GRAY, false);
                this.generateBox(level, box, 15, 7, 21, 42, 9, 21, BASE_GRAY, BASE_GRAY, false);
                this.generateBox(level, box, 16, 6, 21, 41, 6, 21, BASE_GRAY, BASE_GRAY, false);
                this.generateBox(level, box, 17, 5, 21, 40, 5, 21, BASE_GRAY, BASE_GRAY, false);
                this.generateBox(level, box, 21, 4, 21, 36, 4, 21, BASE_GRAY, BASE_GRAY, false);
                this.generateBox(level, box, 22, 3, 21, 26, 3, 21, BASE_GRAY, BASE_GRAY, false);
                this.generateBox(level, box, 31, 3, 21, 35, 3, 21, BASE_GRAY, BASE_GRAY, false);
                this.generateBox(level, box, 23, 2, 21, 25, 2, 21, BASE_GRAY, BASE_GRAY, false);
                this.generateBox(level, box, 32, 2, 21, 34, 2, 21, BASE_GRAY, BASE_GRAY, false);
                this.generateBox(level, box, 28, 4, 20, 29, 4, 21, BASE_LIGHT, BASE_LIGHT, false);
                this.placeBlock(level, BASE_LIGHT, 27, 3, 21, box);
                this.placeBlock(level, BASE_LIGHT, 30, 3, 21, box);
                this.placeBlock(level, BASE_LIGHT, 26, 2, 21, box);
                this.placeBlock(level, BASE_LIGHT, 31, 2, 21, box);
                this.placeBlock(level, BASE_LIGHT, 25, 1, 21, box);
                this.placeBlock(level, BASE_LIGHT, 32, 1, 21, box);

                for (int i = 0; i < 7; i++) {
                    this.placeBlock(level, BASE_BLACK, 28 - i, 6 + i, 21, box);
                    this.placeBlock(level, BASE_BLACK, 29 + i, 6 + i, 21, box);
                }

                for (int i = 0; i < 4; i++) {
                    this.placeBlock(level, BASE_BLACK, 28 - i, 9 + i, 21, box);
                    this.placeBlock(level, BASE_BLACK, 29 + i, 9 + i, 21, box);
                }

                this.placeBlock(level, BASE_BLACK, 28, 12, 21, box);
                this.placeBlock(level, BASE_BLACK, 29, 12, 21, box);

                for (int i = 0; i < 3; i++) {
                    this.placeBlock(level, BASE_BLACK, 22 - i * 2, 8, 21, box);
                    this.placeBlock(level, BASE_BLACK, 22 - i * 2, 9, 21, box);
                    this.placeBlock(level, BASE_BLACK, 35 + i * 2, 8, 21, box);
                    this.placeBlock(level, BASE_BLACK, 35 + i * 2, 9, 21, box);
                }

                this.generateWaterBox(level, box, 15, 13, 21, 42, 15, 21);
                this.generateWaterBox(level, box, 15, 1, 21, 15, 6, 21);
                this.generateWaterBox(level, box, 16, 1, 21, 16, 5, 21);
                this.generateWaterBox(level, box, 17, 1, 21, 20, 4, 21);
                this.generateWaterBox(level, box, 21, 1, 21, 21, 3, 21);
                this.generateWaterBox(level, box, 22, 1, 21, 22, 2, 21);
                this.generateWaterBox(level, box, 23, 1, 21, 24, 1, 21);
                this.generateWaterBox(level, box, 42, 1, 21, 42, 6, 21);
                this.generateWaterBox(level, box, 41, 1, 21, 41, 5, 21);
                this.generateWaterBox(level, box, 37, 1, 21, 40, 4, 21);
                this.generateWaterBox(level, box, 36, 1, 21, 36, 3, 21);
                this.generateWaterBox(level, box, 33, 1, 21, 34, 1, 21);
                this.generateWaterBox(level, box, 35, 1, 21, 35, 2, 21);
            }
        }

        private void generateRoofPiece(WorldGenLevel level, RandomSource random, BoundingBox box) {
            if (this.chunkIntersects(box, 21, 21, 36, 36)) {
                this.generateBox(level, box, 21, 0, 22, 36, 0, 36, BASE_GRAY, BASE_GRAY, false);
                this.generateWaterBox(level, box, 21, 1, 22, 36, 23, 36);

                for (int i = 0; i < 4; i++) {
                    this.generateBox(level, box, 21 + i, 13 + i, 21 + i, 36 - i, 13 + i, 21 + i, BASE_LIGHT, BASE_LIGHT, false);
                    this.generateBox(level, box, 21 + i, 13 + i, 36 - i, 36 - i, 13 + i, 36 - i, BASE_LIGHT, BASE_LIGHT, false);
                    this.generateBox(level, box, 21 + i, 13 + i, 22 + i, 21 + i, 13 + i, 35 - i, BASE_LIGHT, BASE_LIGHT, false);
                    this.generateBox(level, box, 36 - i, 13 + i, 22 + i, 36 - i, 13 + i, 35 - i, BASE_LIGHT, BASE_LIGHT, false);
                }

                this.generateBox(level, box, 25, 16, 25, 32, 16, 32, BASE_GRAY, BASE_GRAY, false);
                this.generateBox(level, box, 25, 17, 25, 25, 19, 25, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, box, 32, 17, 25, 32, 19, 25, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, box, 25, 17, 32, 25, 19, 32, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, box, 32, 17, 32, 32, 19, 32, BASE_LIGHT, BASE_LIGHT, false);
                this.placeBlock(level, BASE_LIGHT, 26, 20, 26, box);
                this.placeBlock(level, BASE_LIGHT, 27, 21, 27, box);
                this.placeBlock(level, LAMP_BLOCK, 27, 20, 27, box);
                this.placeBlock(level, BASE_LIGHT, 26, 20, 31, box);
                this.placeBlock(level, BASE_LIGHT, 27, 21, 30, box);
                this.placeBlock(level, LAMP_BLOCK, 27, 20, 30, box);
                this.placeBlock(level, BASE_LIGHT, 31, 20, 31, box);
                this.placeBlock(level, BASE_LIGHT, 30, 21, 30, box);
                this.placeBlock(level, LAMP_BLOCK, 30, 20, 30, box);
                this.placeBlock(level, BASE_LIGHT, 31, 20, 26, box);
                this.placeBlock(level, BASE_LIGHT, 30, 21, 27, box);
                this.placeBlock(level, LAMP_BLOCK, 30, 20, 27, box);
                this.generateBox(level, box, 28, 21, 27, 29, 21, 27, BASE_GRAY, BASE_GRAY, false);
                this.generateBox(level, box, 27, 21, 28, 27, 21, 29, BASE_GRAY, BASE_GRAY, false);
                this.generateBox(level, box, 28, 21, 30, 29, 21, 30, BASE_GRAY, BASE_GRAY, false);
                this.generateBox(level, box, 30, 21, 28, 30, 21, 29, BASE_GRAY, BASE_GRAY, false);
            }
        }

        private void generateLowerWall(WorldGenLevel level, RandomSource random, BoundingBox box) {
            if (this.chunkIntersects(box, 0, 21, 6, 58)) {
                this.generateBox(level, box, 0, 0, 21, 6, 0, 57, BASE_GRAY, BASE_GRAY, false);
                this.generateWaterBox(level, box, 0, 1, 21, 6, 7, 57);
                this.generateBox(level, box, 4, 4, 21, 6, 4, 53, BASE_GRAY, BASE_GRAY, false);

                for (int i = 0; i < 4; i++) {
                    this.generateBox(level, box, i, i + 1, 21, i, i + 1, 57 - i, BASE_LIGHT, BASE_LIGHT, false);
                }

                for (int i = 23; i < 53; i += 3) {
                    this.placeBlock(level, DOT_DECO_DATA, 5, 5, i, box);
                }

                this.placeBlock(level, DOT_DECO_DATA, 5, 5, 52, box);

                for (int i = 0; i < 4; i++) {
                    this.generateBox(level, box, i, i + 1, 21, i, i + 1, 57 - i, BASE_LIGHT, BASE_LIGHT, false);
                }

                this.generateBox(level, box, 4, 1, 52, 6, 3, 52, BASE_GRAY, BASE_GRAY, false);
                this.generateBox(level, box, 5, 1, 51, 5, 3, 53, BASE_GRAY, BASE_GRAY, false);
            }

            if (this.chunkIntersects(box, 51, 21, 58, 58)) {
                this.generateBox(level, box, 51, 0, 21, 57, 0, 57, BASE_GRAY, BASE_GRAY, false);
                this.generateWaterBox(level, box, 51, 1, 21, 57, 7, 57);
                this.generateBox(level, box, 51, 4, 21, 53, 4, 53, BASE_GRAY, BASE_GRAY, false);

                for (int i = 0; i < 4; i++) {
                    this.generateBox(level, box, 57 - i, i + 1, 21, 57 - i, i + 1, 57 - i, BASE_LIGHT, BASE_LIGHT, false);
                }

                for (int i = 23; i < 53; i += 3) {
                    this.placeBlock(level, DOT_DECO_DATA, 52, 5, i, box);
                }

                this.placeBlock(level, DOT_DECO_DATA, 52, 5, 52, box);
                this.generateBox(level, box, 51, 1, 52, 53, 3, 52, BASE_GRAY, BASE_GRAY, false);
                this.generateBox(level, box, 52, 1, 51, 52, 3, 53, BASE_GRAY, BASE_GRAY, false);
            }

            if (this.chunkIntersects(box, 0, 51, 57, 57)) {
                this.generateBox(level, box, 7, 0, 51, 50, 0, 57, BASE_GRAY, BASE_GRAY, false);
                this.generateWaterBox(level, box, 7, 1, 51, 50, 10, 57);

                for (int i = 0; i < 4; i++) {
                    this.generateBox(level, box, i + 1, i + 1, 57 - i, 56 - i, i + 1, 57 - i, BASE_LIGHT, BASE_LIGHT, false);
                }
            }
        }

        private void generateMiddleWall(WorldGenLevel level, RandomSource random, BoundingBox box) {
            if (this.chunkIntersects(box, 7, 21, 13, 50)) {
                this.generateBox(level, box, 7, 0, 21, 13, 0, 50, BASE_GRAY, BASE_GRAY, false);
                this.generateWaterBox(level, box, 7, 1, 21, 13, 10, 50);
                this.generateBox(level, box, 11, 8, 21, 13, 8, 53, BASE_GRAY, BASE_GRAY, false);

                for (int i = 0; i < 4; i++) {
                    this.generateBox(level, box, i + 7, i + 5, 21, i + 7, i + 5, 54, BASE_LIGHT, BASE_LIGHT, false);
                }

                for (int i = 21; i <= 45; i += 3) {
                    this.placeBlock(level, DOT_DECO_DATA, 12, 9, i, box);
                }
            }

            if (this.chunkIntersects(box, 44, 21, 50, 54)) {
                this.generateBox(level, box, 44, 0, 21, 50, 0, 50, BASE_GRAY, BASE_GRAY, false);
                this.generateWaterBox(level, box, 44, 1, 21, 50, 10, 50);
                this.generateBox(level, box, 44, 8, 21, 46, 8, 53, BASE_GRAY, BASE_GRAY, false);

                for (int i = 0; i < 4; i++) {
                    this.generateBox(level, box, 50 - i, i + 5, 21, 50 - i, i + 5, 54, BASE_LIGHT, BASE_LIGHT, false);
                }

                for (int i = 21; i <= 45; i += 3) {
                    this.placeBlock(level, DOT_DECO_DATA, 45, 9, i, box);
                }
            }

            if (this.chunkIntersects(box, 8, 44, 49, 54)) {
                this.generateBox(level, box, 14, 0, 44, 43, 0, 50, BASE_GRAY, BASE_GRAY, false);
                this.generateWaterBox(level, box, 14, 1, 44, 43, 10, 50);

                for (int i = 12; i <= 45; i += 3) {
                    this.placeBlock(level, DOT_DECO_DATA, i, 9, 45, box);
                    this.placeBlock(level, DOT_DECO_DATA, i, 9, 52, box);
                    if (i == 12 || i == 18 || i == 24 || i == 33 || i == 39 || i == 45) {
                        this.placeBlock(level, DOT_DECO_DATA, i, 9, 47, box);
                        this.placeBlock(level, DOT_DECO_DATA, i, 9, 50, box);
                        this.placeBlock(level, DOT_DECO_DATA, i, 10, 45, box);
                        this.placeBlock(level, DOT_DECO_DATA, i, 10, 46, box);
                        this.placeBlock(level, DOT_DECO_DATA, i, 10, 51, box);
                        this.placeBlock(level, DOT_DECO_DATA, i, 10, 52, box);
                        this.placeBlock(level, DOT_DECO_DATA, i, 11, 47, box);
                        this.placeBlock(level, DOT_DECO_DATA, i, 11, 50, box);
                        this.placeBlock(level, DOT_DECO_DATA, i, 12, 48, box);
                        this.placeBlock(level, DOT_DECO_DATA, i, 12, 49, box);
                    }
                }

                for (int ix = 0; ix < 3; ix++) {
                    this.generateBox(level, box, 8 + ix, 5 + ix, 54, 49 - ix, 5 + ix, 54, BASE_GRAY, BASE_GRAY, false);
                }

                this.generateBox(level, box, 11, 8, 54, 46, 8, 54, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, box, 14, 8, 44, 43, 8, 53, BASE_GRAY, BASE_GRAY, false);
            }
        }

        private void generateUpperWall(WorldGenLevel level, RandomSource random, BoundingBox box) {
            if (this.chunkIntersects(box, 14, 21, 20, 43)) {
                this.generateBox(level, box, 14, 0, 21, 20, 0, 43, BASE_GRAY, BASE_GRAY, false);
                this.generateWaterBox(level, box, 14, 1, 22, 20, 14, 43);
                this.generateBox(level, box, 18, 12, 22, 20, 12, 39, BASE_GRAY, BASE_GRAY, false);
                this.generateBox(level, box, 18, 12, 21, 20, 12, 21, BASE_LIGHT, BASE_LIGHT, false);

                for (int i = 0; i < 4; i++) {
                    this.generateBox(level, box, i + 14, i + 9, 21, i + 14, i + 9, 43 - i, BASE_LIGHT, BASE_LIGHT, false);
                }

                for (int i = 23; i <= 39; i += 3) {
                    this.placeBlock(level, DOT_DECO_DATA, 19, 13, i, box);
                }
            }

            if (this.chunkIntersects(box, 37, 21, 43, 43)) {
                this.generateBox(level, box, 37, 0, 21, 43, 0, 43, BASE_GRAY, BASE_GRAY, false);
                this.generateWaterBox(level, box, 37, 1, 22, 43, 14, 43);
                this.generateBox(level, box, 37, 12, 22, 39, 12, 39, BASE_GRAY, BASE_GRAY, false);
                this.generateBox(level, box, 37, 12, 21, 39, 12, 21, BASE_LIGHT, BASE_LIGHT, false);

                for (int i = 0; i < 4; i++) {
                    this.generateBox(level, box, 43 - i, i + 9, 21, 43 - i, i + 9, 43 - i, BASE_LIGHT, BASE_LIGHT, false);
                }

                for (int i = 23; i <= 39; i += 3) {
                    this.placeBlock(level, DOT_DECO_DATA, 38, 13, i, box);
                }
            }

            if (this.chunkIntersects(box, 15, 37, 42, 43)) {
                this.generateBox(level, box, 21, 0, 37, 36, 0, 43, BASE_GRAY, BASE_GRAY, false);
                this.generateWaterBox(level, box, 21, 1, 37, 36, 14, 43);
                this.generateBox(level, box, 21, 12, 37, 36, 12, 39, BASE_GRAY, BASE_GRAY, false);

                for (int i = 0; i < 4; i++) {
                    this.generateBox(level, box, 15 + i, i + 9, 43 - i, 42 - i, i + 9, 43 - i, BASE_LIGHT, BASE_LIGHT, false);
                }

                for (int i = 21; i <= 36; i += 3) {
                    this.placeBlock(level, DOT_DECO_DATA, i, 13, 38, box);
                }
            }
        }
    }

    interface MonumentRoomFitter {
        boolean fits(OceanMonumentPieces.RoomDefinition room);

        OceanMonumentPieces.OceanMonumentPiece create(Direction direction, OceanMonumentPieces.RoomDefinition room, RandomSource random);
    }

    public static class OceanMonumentCoreRoom extends OceanMonumentPieces.OceanMonumentPiece {
        public OceanMonumentCoreRoom(Direction direction, OceanMonumentPieces.RoomDefinition room) {
            super(StructurePieceType.OCEAN_MONUMENT_CORE_ROOM, 1, direction, room, 2, 2, 2);
        }

        public OceanMonumentCoreRoom(CompoundTag tag) {
            super(StructurePieceType.OCEAN_MONUMENT_CORE_ROOM, tag);
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
            this.generateBoxOnFillOnly(level, box, 1, 8, 0, 14, 8, 14, BASE_GRAY);
            int i = 7;
            BlockState blockState = BASE_LIGHT;
            this.generateBox(level, box, 0, 7, 0, 0, 7, 15, blockState, blockState, false);
            this.generateBox(level, box, 15, 7, 0, 15, 7, 15, blockState, blockState, false);
            this.generateBox(level, box, 1, 7, 0, 15, 7, 0, blockState, blockState, false);
            this.generateBox(level, box, 1, 7, 15, 14, 7, 15, blockState, blockState, false);

            for (int ix = 1; ix <= 6; ix++) {
                blockState = BASE_LIGHT;
                if (ix == 2 || ix == 6) {
                    blockState = BASE_GRAY;
                }

                for (int i1 = 0; i1 <= 15; i1 += 15) {
                    this.generateBox(level, box, i1, ix, 0, i1, ix, 1, blockState, blockState, false);
                    this.generateBox(level, box, i1, ix, 6, i1, ix, 9, blockState, blockState, false);
                    this.generateBox(level, box, i1, ix, 14, i1, ix, 15, blockState, blockState, false);
                }

                this.generateBox(level, box, 1, ix, 0, 1, ix, 0, blockState, blockState, false);
                this.generateBox(level, box, 6, ix, 0, 9, ix, 0, blockState, blockState, false);
                this.generateBox(level, box, 14, ix, 0, 14, ix, 0, blockState, blockState, false);
                this.generateBox(level, box, 1, ix, 15, 14, ix, 15, blockState, blockState, false);
            }

            this.generateBox(level, box, 6, 3, 6, 9, 6, 9, BASE_BLACK, BASE_BLACK, false);
            this.generateBox(level, box, 7, 4, 7, 8, 5, 8, Blocks.GOLD_BLOCK.defaultBlockState(), Blocks.GOLD_BLOCK.defaultBlockState(), false);

            for (int ix = 3; ix <= 6; ix += 3) {
                for (int i2 = 6; i2 <= 9; i2 += 3) {
                    this.placeBlock(level, LAMP_BLOCK, i2, ix, 6, box);
                    this.placeBlock(level, LAMP_BLOCK, i2, ix, 9, box);
                }
            }

            this.generateBox(level, box, 5, 1, 6, 5, 2, 6, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 5, 1, 9, 5, 2, 9, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 10, 1, 6, 10, 2, 6, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 10, 1, 9, 10, 2, 9, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 6, 1, 5, 6, 2, 5, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 9, 1, 5, 9, 2, 5, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 6, 1, 10, 6, 2, 10, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 9, 1, 10, 9, 2, 10, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 5, 2, 5, 5, 6, 5, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 5, 2, 10, 5, 6, 10, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 10, 2, 5, 10, 6, 5, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 10, 2, 10, 10, 6, 10, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 5, 7, 1, 5, 7, 6, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 10, 7, 1, 10, 7, 6, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 5, 7, 9, 5, 7, 14, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 10, 7, 9, 10, 7, 14, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 1, 7, 5, 6, 7, 5, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 1, 7, 10, 6, 7, 10, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 9, 7, 5, 14, 7, 5, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 9, 7, 10, 14, 7, 10, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 2, 1, 2, 2, 1, 3, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 3, 1, 2, 3, 1, 2, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 13, 1, 2, 13, 1, 3, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 12, 1, 2, 12, 1, 2, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 2, 1, 12, 2, 1, 13, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 3, 1, 13, 3, 1, 13, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 13, 1, 12, 13, 1, 13, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 12, 1, 13, 12, 1, 13, BASE_LIGHT, BASE_LIGHT, false);
        }
    }

    public static class OceanMonumentDoubleXRoom extends OceanMonumentPieces.OceanMonumentPiece {
        public OceanMonumentDoubleXRoom(Direction direction, OceanMonumentPieces.RoomDefinition room) {
            super(StructurePieceType.OCEAN_MONUMENT_DOUBLE_X_ROOM, 1, direction, room, 2, 1, 1);
        }

        public OceanMonumentDoubleXRoom(CompoundTag tag) {
            super(StructurePieceType.OCEAN_MONUMENT_DOUBLE_X_ROOM, tag);
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
            OceanMonumentPieces.RoomDefinition roomDefinition = this.roomDefinition.connections[Direction.EAST.get3DDataValue()];
            OceanMonumentPieces.RoomDefinition roomDefinition1 = this.roomDefinition;
            if (this.roomDefinition.index / 25 > 0) {
                this.generateDefaultFloor(level, box, 8, 0, roomDefinition.hasOpening[Direction.DOWN.get3DDataValue()]);
                this.generateDefaultFloor(level, box, 0, 0, roomDefinition1.hasOpening[Direction.DOWN.get3DDataValue()]);
            }

            if (roomDefinition1.connections[Direction.UP.get3DDataValue()] == null) {
                this.generateBoxOnFillOnly(level, box, 1, 4, 1, 7, 4, 6, BASE_GRAY);
            }

            if (roomDefinition.connections[Direction.UP.get3DDataValue()] == null) {
                this.generateBoxOnFillOnly(level, box, 8, 4, 1, 14, 4, 6, BASE_GRAY);
            }

            this.generateBox(level, box, 0, 3, 0, 0, 3, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 15, 3, 0, 15, 3, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 1, 3, 0, 15, 3, 0, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 1, 3, 7, 14, 3, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 0, 2, 0, 0, 2, 7, BASE_GRAY, BASE_GRAY, false);
            this.generateBox(level, box, 15, 2, 0, 15, 2, 7, BASE_GRAY, BASE_GRAY, false);
            this.generateBox(level, box, 1, 2, 0, 15, 2, 0, BASE_GRAY, BASE_GRAY, false);
            this.generateBox(level, box, 1, 2, 7, 14, 2, 7, BASE_GRAY, BASE_GRAY, false);
            this.generateBox(level, box, 0, 1, 0, 0, 1, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 15, 1, 0, 15, 1, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 1, 1, 0, 15, 1, 0, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 1, 1, 7, 14, 1, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 5, 1, 0, 10, 1, 4, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 6, 2, 0, 9, 2, 3, BASE_GRAY, BASE_GRAY, false);
            this.generateBox(level, box, 5, 3, 0, 10, 3, 4, BASE_LIGHT, BASE_LIGHT, false);
            this.placeBlock(level, LAMP_BLOCK, 6, 2, 3, box);
            this.placeBlock(level, LAMP_BLOCK, 9, 2, 3, box);
            if (roomDefinition1.hasOpening[Direction.SOUTH.get3DDataValue()]) {
                this.generateWaterBox(level, box, 3, 1, 0, 4, 2, 0);
            }

            if (roomDefinition1.hasOpening[Direction.NORTH.get3DDataValue()]) {
                this.generateWaterBox(level, box, 3, 1, 7, 4, 2, 7);
            }

            if (roomDefinition1.hasOpening[Direction.WEST.get3DDataValue()]) {
                this.generateWaterBox(level, box, 0, 1, 3, 0, 2, 4);
            }

            if (roomDefinition.hasOpening[Direction.SOUTH.get3DDataValue()]) {
                this.generateWaterBox(level, box, 11, 1, 0, 12, 2, 0);
            }

            if (roomDefinition.hasOpening[Direction.NORTH.get3DDataValue()]) {
                this.generateWaterBox(level, box, 11, 1, 7, 12, 2, 7);
            }

            if (roomDefinition.hasOpening[Direction.EAST.get3DDataValue()]) {
                this.generateWaterBox(level, box, 15, 1, 3, 15, 2, 4);
            }
        }
    }

    public static class OceanMonumentDoubleXYRoom extends OceanMonumentPieces.OceanMonumentPiece {
        public OceanMonumentDoubleXYRoom(Direction direction, OceanMonumentPieces.RoomDefinition room) {
            super(StructurePieceType.OCEAN_MONUMENT_DOUBLE_XY_ROOM, 1, direction, room, 2, 2, 1);
        }

        public OceanMonumentDoubleXYRoom(CompoundTag tag) {
            super(StructurePieceType.OCEAN_MONUMENT_DOUBLE_XY_ROOM, tag);
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
            OceanMonumentPieces.RoomDefinition roomDefinition = this.roomDefinition.connections[Direction.EAST.get3DDataValue()];
            OceanMonumentPieces.RoomDefinition roomDefinition1 = this.roomDefinition;
            OceanMonumentPieces.RoomDefinition roomDefinition2 = roomDefinition1.connections[Direction.UP.get3DDataValue()];
            OceanMonumentPieces.RoomDefinition roomDefinition3 = roomDefinition.connections[Direction.UP.get3DDataValue()];
            if (this.roomDefinition.index / 25 > 0) {
                this.generateDefaultFloor(level, box, 8, 0, roomDefinition.hasOpening[Direction.DOWN.get3DDataValue()]);
                this.generateDefaultFloor(level, box, 0, 0, roomDefinition1.hasOpening[Direction.DOWN.get3DDataValue()]);
            }

            if (roomDefinition2.connections[Direction.UP.get3DDataValue()] == null) {
                this.generateBoxOnFillOnly(level, box, 1, 8, 1, 7, 8, 6, BASE_GRAY);
            }

            if (roomDefinition3.connections[Direction.UP.get3DDataValue()] == null) {
                this.generateBoxOnFillOnly(level, box, 8, 8, 1, 14, 8, 6, BASE_GRAY);
            }

            for (int i = 1; i <= 7; i++) {
                BlockState blockState = BASE_LIGHT;
                if (i == 2 || i == 6) {
                    blockState = BASE_GRAY;
                }

                this.generateBox(level, box, 0, i, 0, 0, i, 7, blockState, blockState, false);
                this.generateBox(level, box, 15, i, 0, 15, i, 7, blockState, blockState, false);
                this.generateBox(level, box, 1, i, 0, 15, i, 0, blockState, blockState, false);
                this.generateBox(level, box, 1, i, 7, 14, i, 7, blockState, blockState, false);
            }

            this.generateBox(level, box, 2, 1, 3, 2, 7, 4, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 3, 1, 2, 4, 7, 2, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 3, 1, 5, 4, 7, 5, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 13, 1, 3, 13, 7, 4, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 11, 1, 2, 12, 7, 2, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 11, 1, 5, 12, 7, 5, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 5, 1, 3, 5, 3, 4, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 10, 1, 3, 10, 3, 4, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 5, 7, 2, 10, 7, 5, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 5, 5, 2, 5, 7, 2, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 10, 5, 2, 10, 7, 2, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 5, 5, 5, 5, 7, 5, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 10, 5, 5, 10, 7, 5, BASE_LIGHT, BASE_LIGHT, false);
            this.placeBlock(level, BASE_LIGHT, 6, 6, 2, box);
            this.placeBlock(level, BASE_LIGHT, 9, 6, 2, box);
            this.placeBlock(level, BASE_LIGHT, 6, 6, 5, box);
            this.placeBlock(level, BASE_LIGHT, 9, 6, 5, box);
            this.generateBox(level, box, 5, 4, 3, 6, 4, 4, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 9, 4, 3, 10, 4, 4, BASE_LIGHT, BASE_LIGHT, false);
            this.placeBlock(level, LAMP_BLOCK, 5, 4, 2, box);
            this.placeBlock(level, LAMP_BLOCK, 5, 4, 5, box);
            this.placeBlock(level, LAMP_BLOCK, 10, 4, 2, box);
            this.placeBlock(level, LAMP_BLOCK, 10, 4, 5, box);
            if (roomDefinition1.hasOpening[Direction.SOUTH.get3DDataValue()]) {
                this.generateWaterBox(level, box, 3, 1, 0, 4, 2, 0);
            }

            if (roomDefinition1.hasOpening[Direction.NORTH.get3DDataValue()]) {
                this.generateWaterBox(level, box, 3, 1, 7, 4, 2, 7);
            }

            if (roomDefinition1.hasOpening[Direction.WEST.get3DDataValue()]) {
                this.generateWaterBox(level, box, 0, 1, 3, 0, 2, 4);
            }

            if (roomDefinition.hasOpening[Direction.SOUTH.get3DDataValue()]) {
                this.generateWaterBox(level, box, 11, 1, 0, 12, 2, 0);
            }

            if (roomDefinition.hasOpening[Direction.NORTH.get3DDataValue()]) {
                this.generateWaterBox(level, box, 11, 1, 7, 12, 2, 7);
            }

            if (roomDefinition.hasOpening[Direction.EAST.get3DDataValue()]) {
                this.generateWaterBox(level, box, 15, 1, 3, 15, 2, 4);
            }

            if (roomDefinition2.hasOpening[Direction.SOUTH.get3DDataValue()]) {
                this.generateWaterBox(level, box, 3, 5, 0, 4, 6, 0);
            }

            if (roomDefinition2.hasOpening[Direction.NORTH.get3DDataValue()]) {
                this.generateWaterBox(level, box, 3, 5, 7, 4, 6, 7);
            }

            if (roomDefinition2.hasOpening[Direction.WEST.get3DDataValue()]) {
                this.generateWaterBox(level, box, 0, 5, 3, 0, 6, 4);
            }

            if (roomDefinition3.hasOpening[Direction.SOUTH.get3DDataValue()]) {
                this.generateWaterBox(level, box, 11, 5, 0, 12, 6, 0);
            }

            if (roomDefinition3.hasOpening[Direction.NORTH.get3DDataValue()]) {
                this.generateWaterBox(level, box, 11, 5, 7, 12, 6, 7);
            }

            if (roomDefinition3.hasOpening[Direction.EAST.get3DDataValue()]) {
                this.generateWaterBox(level, box, 15, 5, 3, 15, 6, 4);
            }
        }
    }

    public static class OceanMonumentDoubleYRoom extends OceanMonumentPieces.OceanMonumentPiece {
        public OceanMonumentDoubleYRoom(Direction direction, OceanMonumentPieces.RoomDefinition room) {
            super(StructurePieceType.OCEAN_MONUMENT_DOUBLE_Y_ROOM, 1, direction, room, 1, 2, 1);
        }

        public OceanMonumentDoubleYRoom(CompoundTag tag) {
            super(StructurePieceType.OCEAN_MONUMENT_DOUBLE_Y_ROOM, tag);
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
            if (this.roomDefinition.index / 25 > 0) {
                this.generateDefaultFloor(level, box, 0, 0, this.roomDefinition.hasOpening[Direction.DOWN.get3DDataValue()]);
            }

            OceanMonumentPieces.RoomDefinition roomDefinition = this.roomDefinition.connections[Direction.UP.get3DDataValue()];
            if (roomDefinition.connections[Direction.UP.get3DDataValue()] == null) {
                this.generateBoxOnFillOnly(level, box, 1, 8, 1, 6, 8, 6, BASE_GRAY);
            }

            this.generateBox(level, box, 0, 4, 0, 0, 4, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 7, 4, 0, 7, 4, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 1, 4, 0, 6, 4, 0, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 1, 4, 7, 6, 4, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 2, 4, 1, 2, 4, 2, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 1, 4, 2, 1, 4, 2, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 5, 4, 1, 5, 4, 2, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 6, 4, 2, 6, 4, 2, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 2, 4, 5, 2, 4, 6, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 1, 4, 5, 1, 4, 5, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 5, 4, 5, 5, 4, 6, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 6, 4, 5, 6, 4, 5, BASE_LIGHT, BASE_LIGHT, false);
            OceanMonumentPieces.RoomDefinition roomDefinition1 = this.roomDefinition;

            for (int i = 1; i <= 5; i += 4) {
                int i1 = 0;
                if (roomDefinition1.hasOpening[Direction.SOUTH.get3DDataValue()]) {
                    this.generateBox(level, box, 2, i, i1, 2, i + 2, i1, BASE_LIGHT, BASE_LIGHT, false);
                    this.generateBox(level, box, 5, i, i1, 5, i + 2, i1, BASE_LIGHT, BASE_LIGHT, false);
                    this.generateBox(level, box, 3, i + 2, i1, 4, i + 2, i1, BASE_LIGHT, BASE_LIGHT, false);
                } else {
                    this.generateBox(level, box, 0, i, i1, 7, i + 2, i1, BASE_LIGHT, BASE_LIGHT, false);
                    this.generateBox(level, box, 0, i + 1, i1, 7, i + 1, i1, BASE_GRAY, BASE_GRAY, false);
                }

                int var13 = 7;
                if (roomDefinition1.hasOpening[Direction.NORTH.get3DDataValue()]) {
                    this.generateBox(level, box, 2, i, var13, 2, i + 2, var13, BASE_LIGHT, BASE_LIGHT, false);
                    this.generateBox(level, box, 5, i, var13, 5, i + 2, var13, BASE_LIGHT, BASE_LIGHT, false);
                    this.generateBox(level, box, 3, i + 2, var13, 4, i + 2, var13, BASE_LIGHT, BASE_LIGHT, false);
                } else {
                    this.generateBox(level, box, 0, i, var13, 7, i + 2, var13, BASE_LIGHT, BASE_LIGHT, false);
                    this.generateBox(level, box, 0, i + 1, var13, 7, i + 1, var13, BASE_GRAY, BASE_GRAY, false);
                }

                int i2 = 0;
                if (roomDefinition1.hasOpening[Direction.WEST.get3DDataValue()]) {
                    this.generateBox(level, box, i2, i, 2, i2, i + 2, 2, BASE_LIGHT, BASE_LIGHT, false);
                    this.generateBox(level, box, i2, i, 5, i2, i + 2, 5, BASE_LIGHT, BASE_LIGHT, false);
                    this.generateBox(level, box, i2, i + 2, 3, i2, i + 2, 4, BASE_LIGHT, BASE_LIGHT, false);
                } else {
                    this.generateBox(level, box, i2, i, 0, i2, i + 2, 7, BASE_LIGHT, BASE_LIGHT, false);
                    this.generateBox(level, box, i2, i + 1, 0, i2, i + 1, 7, BASE_GRAY, BASE_GRAY, false);
                }

                int var14 = 7;
                if (roomDefinition1.hasOpening[Direction.EAST.get3DDataValue()]) {
                    this.generateBox(level, box, var14, i, 2, var14, i + 2, 2, BASE_LIGHT, BASE_LIGHT, false);
                    this.generateBox(level, box, var14, i, 5, var14, i + 2, 5, BASE_LIGHT, BASE_LIGHT, false);
                    this.generateBox(level, box, var14, i + 2, 3, var14, i + 2, 4, BASE_LIGHT, BASE_LIGHT, false);
                } else {
                    this.generateBox(level, box, var14, i, 0, var14, i + 2, 7, BASE_LIGHT, BASE_LIGHT, false);
                    this.generateBox(level, box, var14, i + 1, 0, var14, i + 1, 7, BASE_GRAY, BASE_GRAY, false);
                }

                roomDefinition1 = roomDefinition;
            }
        }
    }

    public static class OceanMonumentDoubleYZRoom extends OceanMonumentPieces.OceanMonumentPiece {
        public OceanMonumentDoubleYZRoom(Direction direction, OceanMonumentPieces.RoomDefinition room) {
            super(StructurePieceType.OCEAN_MONUMENT_DOUBLE_YZ_ROOM, 1, direction, room, 1, 2, 2);
        }

        public OceanMonumentDoubleYZRoom(CompoundTag tag) {
            super(StructurePieceType.OCEAN_MONUMENT_DOUBLE_YZ_ROOM, tag);
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
            OceanMonumentPieces.RoomDefinition roomDefinition = this.roomDefinition.connections[Direction.NORTH.get3DDataValue()];
            OceanMonumentPieces.RoomDefinition roomDefinition1 = this.roomDefinition;
            OceanMonumentPieces.RoomDefinition roomDefinition2 = roomDefinition.connections[Direction.UP.get3DDataValue()];
            OceanMonumentPieces.RoomDefinition roomDefinition3 = roomDefinition1.connections[Direction.UP.get3DDataValue()];
            if (this.roomDefinition.index / 25 > 0) {
                this.generateDefaultFloor(level, box, 0, 8, roomDefinition.hasOpening[Direction.DOWN.get3DDataValue()]);
                this.generateDefaultFloor(level, box, 0, 0, roomDefinition1.hasOpening[Direction.DOWN.get3DDataValue()]);
            }

            if (roomDefinition3.connections[Direction.UP.get3DDataValue()] == null) {
                this.generateBoxOnFillOnly(level, box, 1, 8, 1, 6, 8, 7, BASE_GRAY);
            }

            if (roomDefinition2.connections[Direction.UP.get3DDataValue()] == null) {
                this.generateBoxOnFillOnly(level, box, 1, 8, 8, 6, 8, 14, BASE_GRAY);
            }

            for (int i = 1; i <= 7; i++) {
                BlockState blockState = BASE_LIGHT;
                if (i == 2 || i == 6) {
                    blockState = BASE_GRAY;
                }

                this.generateBox(level, box, 0, i, 0, 0, i, 15, blockState, blockState, false);
                this.generateBox(level, box, 7, i, 0, 7, i, 15, blockState, blockState, false);
                this.generateBox(level, box, 1, i, 0, 6, i, 0, blockState, blockState, false);
                this.generateBox(level, box, 1, i, 15, 6, i, 15, blockState, blockState, false);
            }

            for (int i = 1; i <= 7; i++) {
                BlockState blockState = BASE_BLACK;
                if (i == 2 || i == 6) {
                    blockState = LAMP_BLOCK;
                }

                this.generateBox(level, box, 3, i, 7, 4, i, 8, blockState, blockState, false);
            }

            if (roomDefinition1.hasOpening[Direction.SOUTH.get3DDataValue()]) {
                this.generateWaterBox(level, box, 3, 1, 0, 4, 2, 0);
            }

            if (roomDefinition1.hasOpening[Direction.EAST.get3DDataValue()]) {
                this.generateWaterBox(level, box, 7, 1, 3, 7, 2, 4);
            }

            if (roomDefinition1.hasOpening[Direction.WEST.get3DDataValue()]) {
                this.generateWaterBox(level, box, 0, 1, 3, 0, 2, 4);
            }

            if (roomDefinition.hasOpening[Direction.NORTH.get3DDataValue()]) {
                this.generateWaterBox(level, box, 3, 1, 15, 4, 2, 15);
            }

            if (roomDefinition.hasOpening[Direction.WEST.get3DDataValue()]) {
                this.generateWaterBox(level, box, 0, 1, 11, 0, 2, 12);
            }

            if (roomDefinition.hasOpening[Direction.EAST.get3DDataValue()]) {
                this.generateWaterBox(level, box, 7, 1, 11, 7, 2, 12);
            }

            if (roomDefinition3.hasOpening[Direction.SOUTH.get3DDataValue()]) {
                this.generateWaterBox(level, box, 3, 5, 0, 4, 6, 0);
            }

            if (roomDefinition3.hasOpening[Direction.EAST.get3DDataValue()]) {
                this.generateWaterBox(level, box, 7, 5, 3, 7, 6, 4);
                this.generateBox(level, box, 5, 4, 2, 6, 4, 5, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, box, 6, 1, 2, 6, 3, 2, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, box, 6, 1, 5, 6, 3, 5, BASE_LIGHT, BASE_LIGHT, false);
            }

            if (roomDefinition3.hasOpening[Direction.WEST.get3DDataValue()]) {
                this.generateWaterBox(level, box, 0, 5, 3, 0, 6, 4);
                this.generateBox(level, box, 1, 4, 2, 2, 4, 5, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, box, 1, 1, 2, 1, 3, 2, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, box, 1, 1, 5, 1, 3, 5, BASE_LIGHT, BASE_LIGHT, false);
            }

            if (roomDefinition2.hasOpening[Direction.NORTH.get3DDataValue()]) {
                this.generateWaterBox(level, box, 3, 5, 15, 4, 6, 15);
            }

            if (roomDefinition2.hasOpening[Direction.WEST.get3DDataValue()]) {
                this.generateWaterBox(level, box, 0, 5, 11, 0, 6, 12);
                this.generateBox(level, box, 1, 4, 10, 2, 4, 13, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, box, 1, 1, 10, 1, 3, 10, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, box, 1, 1, 13, 1, 3, 13, BASE_LIGHT, BASE_LIGHT, false);
            }

            if (roomDefinition2.hasOpening[Direction.EAST.get3DDataValue()]) {
                this.generateWaterBox(level, box, 7, 5, 11, 7, 6, 12);
                this.generateBox(level, box, 5, 4, 10, 6, 4, 13, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, box, 6, 1, 10, 6, 3, 10, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, box, 6, 1, 13, 6, 3, 13, BASE_LIGHT, BASE_LIGHT, false);
            }
        }
    }

    public static class OceanMonumentDoubleZRoom extends OceanMonumentPieces.OceanMonumentPiece {
        public OceanMonumentDoubleZRoom(Direction direction, OceanMonumentPieces.RoomDefinition room) {
            super(StructurePieceType.OCEAN_MONUMENT_DOUBLE_Z_ROOM, 1, direction, room, 1, 1, 2);
        }

        public OceanMonumentDoubleZRoom(CompoundTag tag) {
            super(StructurePieceType.OCEAN_MONUMENT_DOUBLE_Z_ROOM, tag);
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
            OceanMonumentPieces.RoomDefinition roomDefinition = this.roomDefinition.connections[Direction.NORTH.get3DDataValue()];
            OceanMonumentPieces.RoomDefinition roomDefinition1 = this.roomDefinition;
            if (this.roomDefinition.index / 25 > 0) {
                this.generateDefaultFloor(level, box, 0, 8, roomDefinition.hasOpening[Direction.DOWN.get3DDataValue()]);
                this.generateDefaultFloor(level, box, 0, 0, roomDefinition1.hasOpening[Direction.DOWN.get3DDataValue()]);
            }

            if (roomDefinition1.connections[Direction.UP.get3DDataValue()] == null) {
                this.generateBoxOnFillOnly(level, box, 1, 4, 1, 6, 4, 7, BASE_GRAY);
            }

            if (roomDefinition.connections[Direction.UP.get3DDataValue()] == null) {
                this.generateBoxOnFillOnly(level, box, 1, 4, 8, 6, 4, 14, BASE_GRAY);
            }

            this.generateBox(level, box, 0, 3, 0, 0, 3, 15, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 7, 3, 0, 7, 3, 15, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 1, 3, 0, 7, 3, 0, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 1, 3, 15, 6, 3, 15, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 0, 2, 0, 0, 2, 15, BASE_GRAY, BASE_GRAY, false);
            this.generateBox(level, box, 7, 2, 0, 7, 2, 15, BASE_GRAY, BASE_GRAY, false);
            this.generateBox(level, box, 1, 2, 0, 7, 2, 0, BASE_GRAY, BASE_GRAY, false);
            this.generateBox(level, box, 1, 2, 15, 6, 2, 15, BASE_GRAY, BASE_GRAY, false);
            this.generateBox(level, box, 0, 1, 0, 0, 1, 15, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 7, 1, 0, 7, 1, 15, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 1, 1, 0, 7, 1, 0, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 1, 1, 15, 6, 1, 15, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 1, 1, 1, 1, 1, 2, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 6, 1, 1, 6, 1, 2, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 1, 3, 1, 1, 3, 2, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 6, 3, 1, 6, 3, 2, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 1, 1, 13, 1, 1, 14, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 6, 1, 13, 6, 1, 14, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 1, 3, 13, 1, 3, 14, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 6, 3, 13, 6, 3, 14, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 2, 1, 6, 2, 3, 6, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 5, 1, 6, 5, 3, 6, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 2, 1, 9, 2, 3, 9, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 5, 1, 9, 5, 3, 9, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 3, 2, 6, 4, 2, 6, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 3, 2, 9, 4, 2, 9, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 2, 2, 7, 2, 2, 8, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 5, 2, 7, 5, 2, 8, BASE_LIGHT, BASE_LIGHT, false);
            this.placeBlock(level, LAMP_BLOCK, 2, 2, 5, box);
            this.placeBlock(level, LAMP_BLOCK, 5, 2, 5, box);
            this.placeBlock(level, LAMP_BLOCK, 2, 2, 10, box);
            this.placeBlock(level, LAMP_BLOCK, 5, 2, 10, box);
            this.placeBlock(level, BASE_LIGHT, 2, 3, 5, box);
            this.placeBlock(level, BASE_LIGHT, 5, 3, 5, box);
            this.placeBlock(level, BASE_LIGHT, 2, 3, 10, box);
            this.placeBlock(level, BASE_LIGHT, 5, 3, 10, box);
            if (roomDefinition1.hasOpening[Direction.SOUTH.get3DDataValue()]) {
                this.generateWaterBox(level, box, 3, 1, 0, 4, 2, 0);
            }

            if (roomDefinition1.hasOpening[Direction.EAST.get3DDataValue()]) {
                this.generateWaterBox(level, box, 7, 1, 3, 7, 2, 4);
            }

            if (roomDefinition1.hasOpening[Direction.WEST.get3DDataValue()]) {
                this.generateWaterBox(level, box, 0, 1, 3, 0, 2, 4);
            }

            if (roomDefinition.hasOpening[Direction.NORTH.get3DDataValue()]) {
                this.generateWaterBox(level, box, 3, 1, 15, 4, 2, 15);
            }

            if (roomDefinition.hasOpening[Direction.WEST.get3DDataValue()]) {
                this.generateWaterBox(level, box, 0, 1, 11, 0, 2, 12);
            }

            if (roomDefinition.hasOpening[Direction.EAST.get3DDataValue()]) {
                this.generateWaterBox(level, box, 7, 1, 11, 7, 2, 12);
            }
        }
    }

    public static class OceanMonumentEntryRoom extends OceanMonumentPieces.OceanMonumentPiece {
        public OceanMonumentEntryRoom(Direction direction, OceanMonumentPieces.RoomDefinition room) {
            super(StructurePieceType.OCEAN_MONUMENT_ENTRY_ROOM, 1, direction, room, 1, 1, 1);
        }

        public OceanMonumentEntryRoom(CompoundTag tag) {
            super(StructurePieceType.OCEAN_MONUMENT_ENTRY_ROOM, tag);
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
            this.generateBox(level, box, 0, 3, 0, 2, 3, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 5, 3, 0, 7, 3, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 0, 2, 0, 1, 2, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 6, 2, 0, 7, 2, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 0, 1, 0, 0, 1, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 7, 1, 0, 7, 1, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 0, 1, 7, 7, 3, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 1, 1, 0, 2, 3, 0, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 5, 1, 0, 6, 3, 0, BASE_LIGHT, BASE_LIGHT, false);
            if (this.roomDefinition.hasOpening[Direction.NORTH.get3DDataValue()]) {
                this.generateWaterBox(level, box, 3, 1, 7, 4, 2, 7);
            }

            if (this.roomDefinition.hasOpening[Direction.WEST.get3DDataValue()]) {
                this.generateWaterBox(level, box, 0, 1, 3, 1, 2, 4);
            }

            if (this.roomDefinition.hasOpening[Direction.EAST.get3DDataValue()]) {
                this.generateWaterBox(level, box, 6, 1, 3, 7, 2, 4);
            }
        }
    }

    public static class OceanMonumentPenthouse extends OceanMonumentPieces.OceanMonumentPiece {
        public OceanMonumentPenthouse(Direction direction, BoundingBox box) {
            super(StructurePieceType.OCEAN_MONUMENT_PENTHOUSE, direction, 1, box);
        }

        public OceanMonumentPenthouse(CompoundTag tag) {
            super(StructurePieceType.OCEAN_MONUMENT_PENTHOUSE, tag);
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
            this.generateBox(level, box, 2, -1, 2, 11, -1, 11, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 0, -1, 0, 1, -1, 11, BASE_GRAY, BASE_GRAY, false);
            this.generateBox(level, box, 12, -1, 0, 13, -1, 11, BASE_GRAY, BASE_GRAY, false);
            this.generateBox(level, box, 2, -1, 0, 11, -1, 1, BASE_GRAY, BASE_GRAY, false);
            this.generateBox(level, box, 2, -1, 12, 11, -1, 13, BASE_GRAY, BASE_GRAY, false);
            this.generateBox(level, box, 0, 0, 0, 0, 0, 13, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 13, 0, 0, 13, 0, 13, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 1, 0, 0, 12, 0, 0, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 1, 0, 13, 12, 0, 13, BASE_LIGHT, BASE_LIGHT, false);

            for (int i = 2; i <= 11; i += 3) {
                this.placeBlock(level, LAMP_BLOCK, 0, 0, i, box);
                this.placeBlock(level, LAMP_BLOCK, 13, 0, i, box);
                this.placeBlock(level, LAMP_BLOCK, i, 0, 0, box);
            }

            this.generateBox(level, box, 2, 0, 3, 4, 0, 9, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 9, 0, 3, 11, 0, 9, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 4, 0, 9, 9, 0, 11, BASE_LIGHT, BASE_LIGHT, false);
            this.placeBlock(level, BASE_LIGHT, 5, 0, 8, box);
            this.placeBlock(level, BASE_LIGHT, 8, 0, 8, box);
            this.placeBlock(level, BASE_LIGHT, 10, 0, 10, box);
            this.placeBlock(level, BASE_LIGHT, 3, 0, 10, box);
            this.generateBox(level, box, 3, 0, 3, 3, 0, 7, BASE_BLACK, BASE_BLACK, false);
            this.generateBox(level, box, 10, 0, 3, 10, 0, 7, BASE_BLACK, BASE_BLACK, false);
            this.generateBox(level, box, 6, 0, 10, 7, 0, 10, BASE_BLACK, BASE_BLACK, false);
            int i = 3;

            for (int i1 = 0; i1 < 2; i1++) {
                for (int i2 = 2; i2 <= 8; i2 += 3) {
                    this.generateBox(level, box, i, 0, i2, i, 2, i2, BASE_LIGHT, BASE_LIGHT, false);
                }

                i = 10;
            }

            this.generateBox(level, box, 5, 0, 10, 5, 2, 10, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 8, 0, 10, 8, 2, 10, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 6, -1, 7, 7, -1, 8, BASE_BLACK, BASE_BLACK, false);
            this.generateWaterBox(level, box, 6, -1, 3, 7, -1, 4);
            this.spawnElder(level, box, 6, 1, 6);
        }
    }

    protected abstract static class OceanMonumentPiece extends StructurePiece {
        protected static final BlockState BASE_GRAY = Blocks.PRISMARINE.defaultBlockState();
        protected static final BlockState BASE_LIGHT = Blocks.PRISMARINE_BRICKS.defaultBlockState();
        protected static final BlockState BASE_BLACK = Blocks.DARK_PRISMARINE.defaultBlockState();
        protected static final BlockState DOT_DECO_DATA = BASE_LIGHT;
        protected static final BlockState LAMP_BLOCK = Blocks.SEA_LANTERN.defaultBlockState();
        protected static final boolean DO_FILL = true;
        protected static final BlockState FILL_BLOCK = Blocks.WATER.defaultBlockState();
        protected static final Set<Block> FILL_KEEP = ImmutableSet.<Block>builder()
            .add(Blocks.ICE)
            .add(Blocks.PACKED_ICE)
            .add(Blocks.BLUE_ICE)
            .add(FILL_BLOCK.getBlock())
            .build();
        protected static final int GRIDROOM_WIDTH = 8;
        protected static final int GRIDROOM_DEPTH = 8;
        protected static final int GRIDROOM_HEIGHT = 4;
        protected static final int GRID_WIDTH = 5;
        protected static final int GRID_DEPTH = 5;
        protected static final int GRID_HEIGHT = 3;
        protected static final int GRID_FLOOR_COUNT = 25;
        protected static final int GRID_SIZE = 75;
        protected static final int GRIDROOM_SOURCE_INDEX = getRoomIndex(2, 0, 0);
        protected static final int GRIDROOM_TOP_CONNECT_INDEX = getRoomIndex(2, 2, 0);
        protected static final int GRIDROOM_LEFTWING_CONNECT_INDEX = getRoomIndex(0, 1, 0);
        protected static final int GRIDROOM_RIGHTWING_CONNECT_INDEX = getRoomIndex(4, 1, 0);
        protected static final int LEFTWING_INDEX = 1001;
        protected static final int RIGHTWING_INDEX = 1002;
        protected static final int PENTHOUSE_INDEX = 1003;
        protected OceanMonumentPieces.RoomDefinition roomDefinition;

        protected static int getRoomIndex(int x, int y, int z) {
            return y * 25 + z * 5 + x;
        }

        public OceanMonumentPiece(StructurePieceType type, Direction orientation, int genDepth, BoundingBox box) {
            super(type, genDepth, box);
            this.setOrientation(orientation);
        }

        protected OceanMonumentPiece(
            StructurePieceType type, int genDepth, Direction orientation, OceanMonumentPieces.RoomDefinition roomDefinition, int x, int y, int z
        ) {
            super(type, genDepth, makeBoundingBox(orientation, roomDefinition, x, y, z));
            this.setOrientation(orientation);
            this.roomDefinition = roomDefinition;
        }

        private static BoundingBox makeBoundingBox(Direction direction, OceanMonumentPieces.RoomDefinition definition, int x, int y, int z) {
            int i = definition.index;
            int i1 = i % 5;
            int i2 = i / 5 % 5;
            int i3 = i / 25;
            BoundingBox boundingBox = makeBoundingBox(0, 0, 0, direction, x * 8, y * 4, z * 8);
            switch (direction) {
                case NORTH:
                    boundingBox.move(i1 * 8, i3 * 4, -(i2 + z) * 8 + 1);
                    break;
                case SOUTH:
                    boundingBox.move(i1 * 8, i3 * 4, i2 * 8);
                    break;
                case WEST:
                    boundingBox.move(-(i2 + z) * 8 + 1, i3 * 4, i1 * 8);
                    break;
                case EAST:
                default:
                    boundingBox.move(i2 * 8, i3 * 4, i1 * 8);
            }

            return boundingBox;
        }

        public OceanMonumentPiece(StructurePieceType type, CompoundTag tag) {
            super(type, tag);
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        }

        protected void generateWaterBox(WorldGenLevel level, BoundingBox boundingBox, int x1, int y1, int z1, int x2, int y2, int z2) {
            for (int i = y1; i <= y2; i++) {
                for (int i1 = x1; i1 <= x2; i1++) {
                    for (int i2 = z1; i2 <= z2; i2++) {
                        BlockState block = this.getBlock(level, i1, i, i2, boundingBox);
                        if (!FILL_KEEP.contains(block.getBlock())) {
                            if (this.getWorldY(i) >= level.getSeaLevel() && block != FILL_BLOCK) {
                                this.placeBlock(level, Blocks.AIR.defaultBlockState(), i1, i, i2, boundingBox);
                            } else {
                                this.placeBlock(level, FILL_BLOCK, i1, i, i2, boundingBox);
                            }
                        }
                    }
                }
            }
        }

        protected void generateDefaultFloor(WorldGenLevel level, BoundingBox box, int x, int z, boolean hasOpeningDownwards) {
            if (hasOpeningDownwards) {
                this.generateBox(level, box, x + 0, 0, z + 0, x + 2, 0, z + 8 - 1, BASE_GRAY, BASE_GRAY, false);
                this.generateBox(level, box, x + 5, 0, z + 0, x + 8 - 1, 0, z + 8 - 1, BASE_GRAY, BASE_GRAY, false);
                this.generateBox(level, box, x + 3, 0, z + 0, x + 4, 0, z + 2, BASE_GRAY, BASE_GRAY, false);
                this.generateBox(level, box, x + 3, 0, z + 5, x + 4, 0, z + 8 - 1, BASE_GRAY, BASE_GRAY, false);
                this.generateBox(level, box, x + 3, 0, z + 2, x + 4, 0, z + 2, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, box, x + 3, 0, z + 5, x + 4, 0, z + 5, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, box, x + 2, 0, z + 3, x + 2, 0, z + 4, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, box, x + 5, 0, z + 3, x + 5, 0, z + 4, BASE_LIGHT, BASE_LIGHT, false);
            } else {
                this.generateBox(level, box, x + 0, 0, z + 0, x + 8 - 1, 0, z + 8 - 1, BASE_GRAY, BASE_GRAY, false);
            }
        }

        protected void generateBoxOnFillOnly(WorldGenLevel level, BoundingBox box, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, BlockState state) {
            for (int i = minY; i <= maxY; i++) {
                for (int i1 = minX; i1 <= maxX; i1++) {
                    for (int i2 = minZ; i2 <= maxZ; i2++) {
                        if (this.getBlock(level, i1, i, i2, box) == FILL_BLOCK) {
                            this.placeBlock(level, state, i1, i, i2, box);
                        }
                    }
                }
            }
        }

        protected boolean chunkIntersects(BoundingBox box, int minX, int minZ, int maxX, int maxZ) {
            int worldX = this.getWorldX(minX, minZ);
            int worldZ = this.getWorldZ(minX, minZ);
            int worldX1 = this.getWorldX(maxX, maxZ);
            int worldZ1 = this.getWorldZ(maxX, maxZ);
            return box.intersects(Math.min(worldX, worldX1), Math.min(worldZ, worldZ1), Math.max(worldX, worldX1), Math.max(worldZ, worldZ1));
        }

        protected void spawnElder(WorldGenLevel level, BoundingBox box, int x, int y, int z) {
            BlockPos worldPos = this.getWorldPos(x, y, z);
            if (box.isInside(worldPos)) {
                ElderGuardian elderGuardian = EntityType.ELDER_GUARDIAN.create(level.getLevel(), EntitySpawnReason.STRUCTURE);
                if (elderGuardian != null) {
                    elderGuardian.heal(elderGuardian.getMaxHealth());
                    elderGuardian.snapTo(worldPos.getX() + 0.5, worldPos.getY(), worldPos.getZ() + 0.5, 0.0F, 0.0F);
                    elderGuardian.finalizeSpawn(level, level.getCurrentDifficultyAt(elderGuardian.blockPosition()), EntitySpawnReason.STRUCTURE, null);
                    level.addFreshEntityWithPassengers(elderGuardian);
                }
            }
        }
    }

    public static class OceanMonumentSimpleRoom extends OceanMonumentPieces.OceanMonumentPiece {
        private int mainDesign;

        public OceanMonumentSimpleRoom(Direction direction, OceanMonumentPieces.RoomDefinition room, RandomSource random) {
            super(StructurePieceType.OCEAN_MONUMENT_SIMPLE_ROOM, 1, direction, room, 1, 1, 1);
            this.mainDesign = random.nextInt(3);
        }

        public OceanMonumentSimpleRoom(CompoundTag tag) {
            super(StructurePieceType.OCEAN_MONUMENT_SIMPLE_ROOM, tag);
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
            if (this.roomDefinition.index / 25 > 0) {
                this.generateDefaultFloor(level, box, 0, 0, this.roomDefinition.hasOpening[Direction.DOWN.get3DDataValue()]);
            }

            if (this.roomDefinition.connections[Direction.UP.get3DDataValue()] == null) {
                this.generateBoxOnFillOnly(level, box, 1, 4, 1, 6, 4, 6, BASE_GRAY);
            }

            boolean flag = this.mainDesign != 0
                && random.nextBoolean()
                && !this.roomDefinition.hasOpening[Direction.DOWN.get3DDataValue()]
                && !this.roomDefinition.hasOpening[Direction.UP.get3DDataValue()]
                && this.roomDefinition.countOpenings() > 1;
            if (this.mainDesign == 0) {
                this.generateBox(level, box, 0, 1, 0, 2, 1, 2, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, box, 0, 3, 0, 2, 3, 2, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, box, 0, 2, 0, 0, 2, 2, BASE_GRAY, BASE_GRAY, false);
                this.generateBox(level, box, 1, 2, 0, 2, 2, 0, BASE_GRAY, BASE_GRAY, false);
                this.placeBlock(level, LAMP_BLOCK, 1, 2, 1, box);
                this.generateBox(level, box, 5, 1, 0, 7, 1, 2, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, box, 5, 3, 0, 7, 3, 2, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, box, 7, 2, 0, 7, 2, 2, BASE_GRAY, BASE_GRAY, false);
                this.generateBox(level, box, 5, 2, 0, 6, 2, 0, BASE_GRAY, BASE_GRAY, false);
                this.placeBlock(level, LAMP_BLOCK, 6, 2, 1, box);
                this.generateBox(level, box, 0, 1, 5, 2, 1, 7, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, box, 0, 3, 5, 2, 3, 7, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, box, 0, 2, 5, 0, 2, 7, BASE_GRAY, BASE_GRAY, false);
                this.generateBox(level, box, 1, 2, 7, 2, 2, 7, BASE_GRAY, BASE_GRAY, false);
                this.placeBlock(level, LAMP_BLOCK, 1, 2, 6, box);
                this.generateBox(level, box, 5, 1, 5, 7, 1, 7, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, box, 5, 3, 5, 7, 3, 7, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, box, 7, 2, 5, 7, 2, 7, BASE_GRAY, BASE_GRAY, false);
                this.generateBox(level, box, 5, 2, 7, 6, 2, 7, BASE_GRAY, BASE_GRAY, false);
                this.placeBlock(level, LAMP_BLOCK, 6, 2, 6, box);
                if (this.roomDefinition.hasOpening[Direction.SOUTH.get3DDataValue()]) {
                    this.generateBox(level, box, 3, 3, 0, 4, 3, 0, BASE_LIGHT, BASE_LIGHT, false);
                } else {
                    this.generateBox(level, box, 3, 3, 0, 4, 3, 1, BASE_LIGHT, BASE_LIGHT, false);
                    this.generateBox(level, box, 3, 2, 0, 4, 2, 0, BASE_GRAY, BASE_GRAY, false);
                    this.generateBox(level, box, 3, 1, 0, 4, 1, 1, BASE_LIGHT, BASE_LIGHT, false);
                }

                if (this.roomDefinition.hasOpening[Direction.NORTH.get3DDataValue()]) {
                    this.generateBox(level, box, 3, 3, 7, 4, 3, 7, BASE_LIGHT, BASE_LIGHT, false);
                } else {
                    this.generateBox(level, box, 3, 3, 6, 4, 3, 7, BASE_LIGHT, BASE_LIGHT, false);
                    this.generateBox(level, box, 3, 2, 7, 4, 2, 7, BASE_GRAY, BASE_GRAY, false);
                    this.generateBox(level, box, 3, 1, 6, 4, 1, 7, BASE_LIGHT, BASE_LIGHT, false);
                }

                if (this.roomDefinition.hasOpening[Direction.WEST.get3DDataValue()]) {
                    this.generateBox(level, box, 0, 3, 3, 0, 3, 4, BASE_LIGHT, BASE_LIGHT, false);
                } else {
                    this.generateBox(level, box, 0, 3, 3, 1, 3, 4, BASE_LIGHT, BASE_LIGHT, false);
                    this.generateBox(level, box, 0, 2, 3, 0, 2, 4, BASE_GRAY, BASE_GRAY, false);
                    this.generateBox(level, box, 0, 1, 3, 1, 1, 4, BASE_LIGHT, BASE_LIGHT, false);
                }

                if (this.roomDefinition.hasOpening[Direction.EAST.get3DDataValue()]) {
                    this.generateBox(level, box, 7, 3, 3, 7, 3, 4, BASE_LIGHT, BASE_LIGHT, false);
                } else {
                    this.generateBox(level, box, 6, 3, 3, 7, 3, 4, BASE_LIGHT, BASE_LIGHT, false);
                    this.generateBox(level, box, 7, 2, 3, 7, 2, 4, BASE_GRAY, BASE_GRAY, false);
                    this.generateBox(level, box, 6, 1, 3, 7, 1, 4, BASE_LIGHT, BASE_LIGHT, false);
                }
            } else if (this.mainDesign == 1) {
                this.generateBox(level, box, 2, 1, 2, 2, 3, 2, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, box, 2, 1, 5, 2, 3, 5, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, box, 5, 1, 5, 5, 3, 5, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, box, 5, 1, 2, 5, 3, 2, BASE_LIGHT, BASE_LIGHT, false);
                this.placeBlock(level, LAMP_BLOCK, 2, 2, 2, box);
                this.placeBlock(level, LAMP_BLOCK, 2, 2, 5, box);
                this.placeBlock(level, LAMP_BLOCK, 5, 2, 5, box);
                this.placeBlock(level, LAMP_BLOCK, 5, 2, 2, box);
                this.generateBox(level, box, 0, 1, 0, 1, 3, 0, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, box, 0, 1, 1, 0, 3, 1, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, box, 0, 1, 7, 1, 3, 7, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, box, 0, 1, 6, 0, 3, 6, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, box, 6, 1, 7, 7, 3, 7, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, box, 7, 1, 6, 7, 3, 6, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, box, 6, 1, 0, 7, 3, 0, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, box, 7, 1, 1, 7, 3, 1, BASE_LIGHT, BASE_LIGHT, false);
                this.placeBlock(level, BASE_GRAY, 1, 2, 0, box);
                this.placeBlock(level, BASE_GRAY, 0, 2, 1, box);
                this.placeBlock(level, BASE_GRAY, 1, 2, 7, box);
                this.placeBlock(level, BASE_GRAY, 0, 2, 6, box);
                this.placeBlock(level, BASE_GRAY, 6, 2, 7, box);
                this.placeBlock(level, BASE_GRAY, 7, 2, 6, box);
                this.placeBlock(level, BASE_GRAY, 6, 2, 0, box);
                this.placeBlock(level, BASE_GRAY, 7, 2, 1, box);
                if (!this.roomDefinition.hasOpening[Direction.SOUTH.get3DDataValue()]) {
                    this.generateBox(level, box, 1, 3, 0, 6, 3, 0, BASE_LIGHT, BASE_LIGHT, false);
                    this.generateBox(level, box, 1, 2, 0, 6, 2, 0, BASE_GRAY, BASE_GRAY, false);
                    this.generateBox(level, box, 1, 1, 0, 6, 1, 0, BASE_LIGHT, BASE_LIGHT, false);
                }

                if (!this.roomDefinition.hasOpening[Direction.NORTH.get3DDataValue()]) {
                    this.generateBox(level, box, 1, 3, 7, 6, 3, 7, BASE_LIGHT, BASE_LIGHT, false);
                    this.generateBox(level, box, 1, 2, 7, 6, 2, 7, BASE_GRAY, BASE_GRAY, false);
                    this.generateBox(level, box, 1, 1, 7, 6, 1, 7, BASE_LIGHT, BASE_LIGHT, false);
                }

                if (!this.roomDefinition.hasOpening[Direction.WEST.get3DDataValue()]) {
                    this.generateBox(level, box, 0, 3, 1, 0, 3, 6, BASE_LIGHT, BASE_LIGHT, false);
                    this.generateBox(level, box, 0, 2, 1, 0, 2, 6, BASE_GRAY, BASE_GRAY, false);
                    this.generateBox(level, box, 0, 1, 1, 0, 1, 6, BASE_LIGHT, BASE_LIGHT, false);
                }

                if (!this.roomDefinition.hasOpening[Direction.EAST.get3DDataValue()]) {
                    this.generateBox(level, box, 7, 3, 1, 7, 3, 6, BASE_LIGHT, BASE_LIGHT, false);
                    this.generateBox(level, box, 7, 2, 1, 7, 2, 6, BASE_GRAY, BASE_GRAY, false);
                    this.generateBox(level, box, 7, 1, 1, 7, 1, 6, BASE_LIGHT, BASE_LIGHT, false);
                }
            } else if (this.mainDesign == 2) {
                this.generateBox(level, box, 0, 1, 0, 0, 1, 7, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, box, 7, 1, 0, 7, 1, 7, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, box, 1, 1, 0, 6, 1, 0, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, box, 1, 1, 7, 6, 1, 7, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, box, 0, 2, 0, 0, 2, 7, BASE_BLACK, BASE_BLACK, false);
                this.generateBox(level, box, 7, 2, 0, 7, 2, 7, BASE_BLACK, BASE_BLACK, false);
                this.generateBox(level, box, 1, 2, 0, 6, 2, 0, BASE_BLACK, BASE_BLACK, false);
                this.generateBox(level, box, 1, 2, 7, 6, 2, 7, BASE_BLACK, BASE_BLACK, false);
                this.generateBox(level, box, 0, 3, 0, 0, 3, 7, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, box, 7, 3, 0, 7, 3, 7, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, box, 1, 3, 0, 6, 3, 0, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, box, 1, 3, 7, 6, 3, 7, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, box, 0, 1, 3, 0, 2, 4, BASE_BLACK, BASE_BLACK, false);
                this.generateBox(level, box, 7, 1, 3, 7, 2, 4, BASE_BLACK, BASE_BLACK, false);
                this.generateBox(level, box, 3, 1, 0, 4, 2, 0, BASE_BLACK, BASE_BLACK, false);
                this.generateBox(level, box, 3, 1, 7, 4, 2, 7, BASE_BLACK, BASE_BLACK, false);
                if (this.roomDefinition.hasOpening[Direction.SOUTH.get3DDataValue()]) {
                    this.generateWaterBox(level, box, 3, 1, 0, 4, 2, 0);
                }

                if (this.roomDefinition.hasOpening[Direction.NORTH.get3DDataValue()]) {
                    this.generateWaterBox(level, box, 3, 1, 7, 4, 2, 7);
                }

                if (this.roomDefinition.hasOpening[Direction.WEST.get3DDataValue()]) {
                    this.generateWaterBox(level, box, 0, 1, 3, 0, 2, 4);
                }

                if (this.roomDefinition.hasOpening[Direction.EAST.get3DDataValue()]) {
                    this.generateWaterBox(level, box, 7, 1, 3, 7, 2, 4);
                }
            }

            if (flag) {
                this.generateBox(level, box, 3, 1, 3, 4, 1, 4, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, box, 3, 2, 3, 4, 2, 4, BASE_GRAY, BASE_GRAY, false);
                this.generateBox(level, box, 3, 3, 3, 4, 3, 4, BASE_LIGHT, BASE_LIGHT, false);
            }
        }
    }

    public static class OceanMonumentSimpleTopRoom extends OceanMonumentPieces.OceanMonumentPiece {
        public OceanMonumentSimpleTopRoom(Direction direction, OceanMonumentPieces.RoomDefinition room) {
            super(StructurePieceType.OCEAN_MONUMENT_SIMPLE_TOP_ROOM, 1, direction, room, 1, 1, 1);
        }

        public OceanMonumentSimpleTopRoom(CompoundTag tag) {
            super(StructurePieceType.OCEAN_MONUMENT_SIMPLE_TOP_ROOM, tag);
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
            if (this.roomDefinition.index / 25 > 0) {
                this.generateDefaultFloor(level, box, 0, 0, this.roomDefinition.hasOpening[Direction.DOWN.get3DDataValue()]);
            }

            if (this.roomDefinition.connections[Direction.UP.get3DDataValue()] == null) {
                this.generateBoxOnFillOnly(level, box, 1, 4, 1, 6, 4, 6, BASE_GRAY);
            }

            for (int i = 1; i <= 6; i++) {
                for (int i1 = 1; i1 <= 6; i1++) {
                    if (random.nextInt(3) != 0) {
                        int i2 = 2 + (random.nextInt(4) == 0 ? 0 : 1);
                        BlockState blockState = Blocks.WET_SPONGE.defaultBlockState();
                        this.generateBox(level, box, i, i2, i1, i, 3, i1, blockState, blockState, false);
                    }
                }
            }

            this.generateBox(level, box, 0, 1, 0, 0, 1, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 7, 1, 0, 7, 1, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 1, 1, 0, 6, 1, 0, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 1, 1, 7, 6, 1, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 0, 2, 0, 0, 2, 7, BASE_BLACK, BASE_BLACK, false);
            this.generateBox(level, box, 7, 2, 0, 7, 2, 7, BASE_BLACK, BASE_BLACK, false);
            this.generateBox(level, box, 1, 2, 0, 6, 2, 0, BASE_BLACK, BASE_BLACK, false);
            this.generateBox(level, box, 1, 2, 7, 6, 2, 7, BASE_BLACK, BASE_BLACK, false);
            this.generateBox(level, box, 0, 3, 0, 0, 3, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 7, 3, 0, 7, 3, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 1, 3, 0, 6, 3, 0, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 1, 3, 7, 6, 3, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, box, 0, 1, 3, 0, 2, 4, BASE_BLACK, BASE_BLACK, false);
            this.generateBox(level, box, 7, 1, 3, 7, 2, 4, BASE_BLACK, BASE_BLACK, false);
            this.generateBox(level, box, 3, 1, 0, 4, 2, 0, BASE_BLACK, BASE_BLACK, false);
            this.generateBox(level, box, 3, 1, 7, 4, 2, 7, BASE_BLACK, BASE_BLACK, false);
            if (this.roomDefinition.hasOpening[Direction.SOUTH.get3DDataValue()]) {
                this.generateWaterBox(level, box, 3, 1, 0, 4, 2, 0);
            }
        }
    }

    public static class OceanMonumentWingRoom extends OceanMonumentPieces.OceanMonumentPiece {
        private int mainDesign;

        public OceanMonumentWingRoom(Direction direction, BoundingBox box, int flag) {
            super(StructurePieceType.OCEAN_MONUMENT_WING_ROOM, direction, 1, box);
            this.mainDesign = flag & 1;
        }

        public OceanMonumentWingRoom(CompoundTag tag) {
            super(StructurePieceType.OCEAN_MONUMENT_WING_ROOM, tag);
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
            if (this.mainDesign == 0) {
                for (int i = 0; i < 4; i++) {
                    this.generateBox(level, box, 10 - i, 3 - i, 20 - i, 12 + i, 3 - i, 20, BASE_LIGHT, BASE_LIGHT, false);
                }

                this.generateBox(level, box, 7, 0, 6, 15, 0, 16, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, box, 6, 0, 6, 6, 3, 20, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, box, 16, 0, 6, 16, 3, 20, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, box, 7, 1, 7, 7, 1, 20, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, box, 15, 1, 7, 15, 1, 20, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, box, 7, 1, 6, 9, 3, 6, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, box, 13, 1, 6, 15, 3, 6, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, box, 8, 1, 7, 9, 1, 7, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, box, 13, 1, 7, 14, 1, 7, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, box, 9, 0, 5, 13, 0, 5, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, box, 10, 0, 7, 12, 0, 7, BASE_BLACK, BASE_BLACK, false);
                this.generateBox(level, box, 8, 0, 10, 8, 0, 12, BASE_BLACK, BASE_BLACK, false);
                this.generateBox(level, box, 14, 0, 10, 14, 0, 12, BASE_BLACK, BASE_BLACK, false);

                for (int i = 18; i >= 7; i -= 3) {
                    this.placeBlock(level, LAMP_BLOCK, 6, 3, i, box);
                    this.placeBlock(level, LAMP_BLOCK, 16, 3, i, box);
                }

                this.placeBlock(level, LAMP_BLOCK, 10, 0, 10, box);
                this.placeBlock(level, LAMP_BLOCK, 12, 0, 10, box);
                this.placeBlock(level, LAMP_BLOCK, 10, 0, 12, box);
                this.placeBlock(level, LAMP_BLOCK, 12, 0, 12, box);
                this.placeBlock(level, LAMP_BLOCK, 8, 3, 6, box);
                this.placeBlock(level, LAMP_BLOCK, 14, 3, 6, box);
                this.placeBlock(level, BASE_LIGHT, 4, 2, 4, box);
                this.placeBlock(level, LAMP_BLOCK, 4, 1, 4, box);
                this.placeBlock(level, BASE_LIGHT, 4, 0, 4, box);
                this.placeBlock(level, BASE_LIGHT, 18, 2, 4, box);
                this.placeBlock(level, LAMP_BLOCK, 18, 1, 4, box);
                this.placeBlock(level, BASE_LIGHT, 18, 0, 4, box);
                this.placeBlock(level, BASE_LIGHT, 4, 2, 18, box);
                this.placeBlock(level, LAMP_BLOCK, 4, 1, 18, box);
                this.placeBlock(level, BASE_LIGHT, 4, 0, 18, box);
                this.placeBlock(level, BASE_LIGHT, 18, 2, 18, box);
                this.placeBlock(level, LAMP_BLOCK, 18, 1, 18, box);
                this.placeBlock(level, BASE_LIGHT, 18, 0, 18, box);
                this.placeBlock(level, BASE_LIGHT, 9, 7, 20, box);
                this.placeBlock(level, BASE_LIGHT, 13, 7, 20, box);
                this.generateBox(level, box, 6, 0, 21, 7, 4, 21, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, box, 15, 0, 21, 16, 4, 21, BASE_LIGHT, BASE_LIGHT, false);
                this.spawnElder(level, box, 11, 2, 16);
            } else if (this.mainDesign == 1) {
                this.generateBox(level, box, 9, 3, 18, 13, 3, 20, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, box, 9, 0, 18, 9, 2, 18, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, box, 13, 0, 18, 13, 2, 18, BASE_LIGHT, BASE_LIGHT, false);
                int i = 9;
                int i1 = 20;
                int i2 = 5;

                for (int i3 = 0; i3 < 2; i3++) {
                    this.placeBlock(level, BASE_LIGHT, i, 6, 20, box);
                    this.placeBlock(level, LAMP_BLOCK, i, 5, 20, box);
                    this.placeBlock(level, BASE_LIGHT, i, 4, 20, box);
                    i = 13;
                }

                this.generateBox(level, box, 7, 3, 7, 15, 3, 14, BASE_LIGHT, BASE_LIGHT, false);
                int var14 = 10;

                for (int i3 = 0; i3 < 2; i3++) {
                    this.generateBox(level, box, var14, 0, 10, var14, 6, 10, BASE_LIGHT, BASE_LIGHT, false);
                    this.generateBox(level, box, var14, 0, 12, var14, 6, 12, BASE_LIGHT, BASE_LIGHT, false);
                    this.placeBlock(level, LAMP_BLOCK, var14, 0, 10, box);
                    this.placeBlock(level, LAMP_BLOCK, var14, 0, 12, box);
                    this.placeBlock(level, LAMP_BLOCK, var14, 4, 10, box);
                    this.placeBlock(level, LAMP_BLOCK, var14, 4, 12, box);
                    var14 = 12;
                }

                var14 = 8;

                for (int i3 = 0; i3 < 2; i3++) {
                    this.generateBox(level, box, var14, 0, 7, var14, 2, 7, BASE_LIGHT, BASE_LIGHT, false);
                    this.generateBox(level, box, var14, 0, 14, var14, 2, 14, BASE_LIGHT, BASE_LIGHT, false);
                    var14 = 14;
                }

                this.generateBox(level, box, 8, 3, 8, 8, 3, 13, BASE_BLACK, BASE_BLACK, false);
                this.generateBox(level, box, 14, 3, 8, 14, 3, 13, BASE_BLACK, BASE_BLACK, false);
                this.spawnElder(level, box, 11, 5, 13);
            }
        }
    }

    static class RoomDefinition {
        final int index;
        final OceanMonumentPieces.RoomDefinition[] connections = new OceanMonumentPieces.RoomDefinition[6];
        final boolean[] hasOpening = new boolean[6];
        boolean claimed;
        boolean isSource;
        private int scanIndex;

        public RoomDefinition(int index) {
            this.index = index;
        }

        public void setConnection(Direction direction, OceanMonumentPieces.RoomDefinition connectingRoom) {
            this.connections[direction.get3DDataValue()] = connectingRoom;
            connectingRoom.connections[direction.getOpposite().get3DDataValue()] = this;
        }

        public void updateOpenings() {
            for (int i = 0; i < 6; i++) {
                this.hasOpening[i] = this.connections[i] != null;
            }
        }

        public boolean findSource(int index) {
            if (this.isSource) {
                return true;
            } else {
                this.scanIndex = index;

                for (int i = 0; i < 6; i++) {
                    if (this.connections[i] != null && this.hasOpening[i] && this.connections[i].scanIndex != index && this.connections[i].findSource(index)) {
                        return true;
                    }
                }

                return false;
            }
        }

        public boolean isSpecial() {
            return this.index >= 75;
        }

        public int countOpenings() {
            int i = 0;

            for (int i1 = 0; i1 < 6; i1++) {
                if (this.hasOpening[i1]) {
                    i++;
                }
            }

            return i;
        }
    }
}
