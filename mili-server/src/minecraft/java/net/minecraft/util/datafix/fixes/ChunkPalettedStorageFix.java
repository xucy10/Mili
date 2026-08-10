package net.minecraft.util.datafix.fixes;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.util.CrudeIncrementalIntIdentityHashBiMap;
import net.minecraft.util.datafix.ExtraDataFixUtils;
import net.minecraft.util.datafix.PackedBitStorage;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class ChunkPalettedStorageFix extends DataFix {
    private static final int NORTH_WEST_MASK = 128;
    private static final int WEST_MASK = 64;
    private static final int SOUTH_WEST_MASK = 32;
    private static final int SOUTH_MASK = 16;
    private static final int SOUTH_EAST_MASK = 8;
    private static final int EAST_MASK = 4;
    private static final int NORTH_EAST_MASK = 2;
    private static final int NORTH_MASK = 1;
    static final Logger LOGGER = LogUtils.getLogger();
    private static final int SIZE = 4096;

    public ChunkPalettedStorageFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    public static String getName(Dynamic<?> data) {
        return data.get("Name").asString("");
    }

    public static String getProperty(Dynamic<?> data, String key) {
        return data.get("Properties").get(key).asString("");
    }

    public static int idFor(CrudeIncrementalIntIdentityHashBiMap<Dynamic<?>> palette, Dynamic<?> data) {
        int id = palette.getId(data);
        if (id == -1) {
            id = palette.add(data);
        }

        return id;
    }

    private Dynamic<?> fix(Dynamic<?> dynamic) {
        Optional<? extends Dynamic<?>> optional = dynamic.get("Level").result();
        return optional.isPresent() && optional.get().get("Sections").asStreamOpt().result().isPresent()
            ? dynamic.set("Level", new ChunkPalettedStorageFix.UpgradeChunk((Dynamic<?>)optional.get()).write())
            : dynamic;
    }

    @Override
    public TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.CHUNK);
        Type<?> type1 = this.getOutputSchema().getType(References.CHUNK);
        return this.writeFixAndRead("ChunkPalettedStorageFix", type, type1, this::fix);
    }

    public static int getSideMask(boolean west, boolean east, boolean north, boolean south) {
        int i = 0;
        if (north) {
            if (east) {
                i |= 2;
            } else if (west) {
                i |= 128;
            } else {
                i |= 1;
            }
        } else if (south) {
            if (west) {
                i |= 32;
            } else if (east) {
                i |= 8;
            } else {
                i |= 16;
            }
        } else if (east) {
            i |= 4;
        } else if (west) {
            i |= 64;
        }

        return i;
    }

    static class DataLayer {
        private static final int SIZE = 2048;
        private static final int NIBBLE_SIZE = 4;
        private final byte[] data;

        public DataLayer() {
            this.data = new byte[2048];
        }

        public DataLayer(byte[] data) {
            this.data = data;
            if (data.length != 2048) {
                throw new IllegalArgumentException("ChunkNibbleArrays should be 2048 bytes not: " + data.length);
            }
        }

        public int get(int x, int y, int z) {
            int position = this.getPosition(y << 8 | z << 4 | x);
            return this.isFirst(y << 8 | z << 4 | x) ? this.data[position] & 15 : this.data[position] >> 4 & 15;
        }

        private boolean isFirst(int packedPos) {
            return (packedPos & 1) == 0;
        }

        private int getPosition(int packedPos) {
            return packedPos >> 1;
        }
    }

    public static enum Direction {
        DOWN(ChunkPalettedStorageFix.Direction.AxisDirection.NEGATIVE, ChunkPalettedStorageFix.Direction.Axis.Y),
        UP(ChunkPalettedStorageFix.Direction.AxisDirection.POSITIVE, ChunkPalettedStorageFix.Direction.Axis.Y),
        NORTH(ChunkPalettedStorageFix.Direction.AxisDirection.NEGATIVE, ChunkPalettedStorageFix.Direction.Axis.Z),
        SOUTH(ChunkPalettedStorageFix.Direction.AxisDirection.POSITIVE, ChunkPalettedStorageFix.Direction.Axis.Z),
        WEST(ChunkPalettedStorageFix.Direction.AxisDirection.NEGATIVE, ChunkPalettedStorageFix.Direction.Axis.X),
        EAST(ChunkPalettedStorageFix.Direction.AxisDirection.POSITIVE, ChunkPalettedStorageFix.Direction.Axis.X);

        private final ChunkPalettedStorageFix.Direction.Axis axis;
        private final ChunkPalettedStorageFix.Direction.AxisDirection axisDirection;

        private Direction(final ChunkPalettedStorageFix.Direction.AxisDirection axisDirection, final ChunkPalettedStorageFix.Direction.Axis axis) {
            this.axis = axis;
            this.axisDirection = axisDirection;
        }

        public ChunkPalettedStorageFix.Direction.AxisDirection getAxisDirection() {
            return this.axisDirection;
        }

        public ChunkPalettedStorageFix.Direction.Axis getAxis() {
            return this.axis;
        }

        public static enum Axis {
            X,
            Y,
            Z;
        }

        public static enum AxisDirection {
            POSITIVE(1),
            NEGATIVE(-1);

            private final int step;

            private AxisDirection(final int step) {
                this.step = step;
            }

            public int getStep() {
                return this.step;
            }
        }
    }

    static class MappingConstants {
        static final BitSet VIRTUAL = new BitSet(256);
        static final BitSet FIX = new BitSet(256);
        static final Dynamic<?> PUMPKIN = ExtraDataFixUtils.blockState("minecraft:pumpkin");
        static final Dynamic<?> SNOWY_PODZOL = ExtraDataFixUtils.blockState("minecraft:podzol", Map.of("snowy", "true"));
        static final Dynamic<?> SNOWY_GRASS = ExtraDataFixUtils.blockState("minecraft:grass_block", Map.of("snowy", "true"));
        static final Dynamic<?> SNOWY_MYCELIUM = ExtraDataFixUtils.blockState("minecraft:mycelium", Map.of("snowy", "true"));
        static final Dynamic<?> UPPER_SUNFLOWER = ExtraDataFixUtils.blockState("minecraft:sunflower", Map.of("half", "upper"));
        static final Dynamic<?> UPPER_LILAC = ExtraDataFixUtils.blockState("minecraft:lilac", Map.of("half", "upper"));
        static final Dynamic<?> UPPER_TALL_GRASS = ExtraDataFixUtils.blockState("minecraft:tall_grass", Map.of("half", "upper"));
        static final Dynamic<?> UPPER_LARGE_FERN = ExtraDataFixUtils.blockState("minecraft:large_fern", Map.of("half", "upper"));
        static final Dynamic<?> UPPER_ROSE_BUSH = ExtraDataFixUtils.blockState("minecraft:rose_bush", Map.of("half", "upper"));
        static final Dynamic<?> UPPER_PEONY = ExtraDataFixUtils.blockState("minecraft:peony", Map.of("half", "upper"));
        static final Map<String, Dynamic<?>> FLOWER_POT_MAP = DataFixUtils.make(Maps.newHashMap(), map -> {
            map.put("minecraft:air0", ExtraDataFixUtils.blockState("minecraft:flower_pot"));
            map.put("minecraft:red_flower0", ExtraDataFixUtils.blockState("minecraft:potted_poppy"));
            map.put("minecraft:red_flower1", ExtraDataFixUtils.blockState("minecraft:potted_blue_orchid"));
            map.put("minecraft:red_flower2", ExtraDataFixUtils.blockState("minecraft:potted_allium"));
            map.put("minecraft:red_flower3", ExtraDataFixUtils.blockState("minecraft:potted_azure_bluet"));
            map.put("minecraft:red_flower4", ExtraDataFixUtils.blockState("minecraft:potted_red_tulip"));
            map.put("minecraft:red_flower5", ExtraDataFixUtils.blockState("minecraft:potted_orange_tulip"));
            map.put("minecraft:red_flower6", ExtraDataFixUtils.blockState("minecraft:potted_white_tulip"));
            map.put("minecraft:red_flower7", ExtraDataFixUtils.blockState("minecraft:potted_pink_tulip"));
            map.put("minecraft:red_flower8", ExtraDataFixUtils.blockState("minecraft:potted_oxeye_daisy"));
            map.put("minecraft:yellow_flower0", ExtraDataFixUtils.blockState("minecraft:potted_dandelion"));
            map.put("minecraft:sapling0", ExtraDataFixUtils.blockState("minecraft:potted_oak_sapling"));
            map.put("minecraft:sapling1", ExtraDataFixUtils.blockState("minecraft:potted_spruce_sapling"));
            map.put("minecraft:sapling2", ExtraDataFixUtils.blockState("minecraft:potted_birch_sapling"));
            map.put("minecraft:sapling3", ExtraDataFixUtils.blockState("minecraft:potted_jungle_sapling"));
            map.put("minecraft:sapling4", ExtraDataFixUtils.blockState("minecraft:potted_acacia_sapling"));
            map.put("minecraft:sapling5", ExtraDataFixUtils.blockState("minecraft:potted_dark_oak_sapling"));
            map.put("minecraft:red_mushroom0", ExtraDataFixUtils.blockState("minecraft:potted_red_mushroom"));
            map.put("minecraft:brown_mushroom0", ExtraDataFixUtils.blockState("minecraft:potted_brown_mushroom"));
            map.put("minecraft:deadbush0", ExtraDataFixUtils.blockState("minecraft:potted_dead_bush"));
            map.put("minecraft:tallgrass2", ExtraDataFixUtils.blockState("minecraft:potted_fern"));
            map.put("minecraft:cactus0", ExtraDataFixUtils.blockState("minecraft:potted_cactus"));
        });
        static final Map<String, Dynamic<?>> SKULL_MAP = DataFixUtils.make(Maps.newHashMap(), map -> {
            mapSkull(map, 0, "skeleton", "skull");
            mapSkull(map, 1, "wither_skeleton", "skull");
            mapSkull(map, 2, "zombie", "head");
            mapSkull(map, 3, "player", "head");
            mapSkull(map, 4, "creeper", "head");
            mapSkull(map, 5, "dragon", "head");
        });
        static final Map<String, Dynamic<?>> DOOR_MAP = DataFixUtils.make(Maps.newHashMap(), map -> {
            mapDoor(map, "oak_door");
            mapDoor(map, "iron_door");
            mapDoor(map, "spruce_door");
            mapDoor(map, "birch_door");
            mapDoor(map, "jungle_door");
            mapDoor(map, "acacia_door");
            mapDoor(map, "dark_oak_door");
        });
        static final Map<String, Dynamic<?>> NOTE_BLOCK_MAP = DataFixUtils.make(Maps.newHashMap(), map -> {
            for (int i = 0; i < 26; i++) {
                map.put("true" + i, ExtraDataFixUtils.blockState("minecraft:note_block", Map.of("powered", "true", "note", String.valueOf(i))));
                map.put("false" + i, ExtraDataFixUtils.blockState("minecraft:note_block", Map.of("powered", "false", "note", String.valueOf(i))));
            }
        });
        private static final Int2ObjectMap<String> DYE_COLOR_MAP = DataFixUtils.make(new Int2ObjectOpenHashMap<>(), map -> {
            map.put(0, "white");
            map.put(1, "orange");
            map.put(2, "magenta");
            map.put(3, "light_blue");
            map.put(4, "yellow");
            map.put(5, "lime");
            map.put(6, "pink");
            map.put(7, "gray");
            map.put(8, "light_gray");
            map.put(9, "cyan");
            map.put(10, "purple");
            map.put(11, "blue");
            map.put(12, "brown");
            map.put(13, "green");
            map.put(14, "red");
            map.put(15, "black");
        });
        static final Map<String, Dynamic<?>> BED_BLOCK_MAP = DataFixUtils.make(Maps.newHashMap(), map -> {
            for (Entry<String> entry : DYE_COLOR_MAP.int2ObjectEntrySet()) {
                if (!Objects.equals(entry.getValue(), "red")) {
                    addBeds(map, entry.getIntKey(), entry.getValue());
                }
            }
        });
        static final Map<String, Dynamic<?>> BANNER_BLOCK_MAP = DataFixUtils.make(Maps.newHashMap(), map -> {
            for (Entry<String> entry : DYE_COLOR_MAP.int2ObjectEntrySet()) {
                if (!Objects.equals(entry.getValue(), "white")) {
                    addBanners(map, 15 - entry.getIntKey(), entry.getValue());
                }
            }
        });
        static final Dynamic<?> AIR = ExtraDataFixUtils.blockState("minecraft:air");

        private MappingConstants() {
        }

        private static void mapSkull(Map<String, Dynamic<?>> map, int id, String skullType, String suffix) {
            map.put(id + "north", ExtraDataFixUtils.blockState("minecraft:" + skullType + "_wall_" + suffix, Map.of("facing", "north")));
            map.put(id + "east", ExtraDataFixUtils.blockState("minecraft:" + skullType + "_wall_" + suffix, Map.of("facing", "east")));
            map.put(id + "south", ExtraDataFixUtils.blockState("minecraft:" + skullType + "_wall_" + suffix, Map.of("facing", "south")));
            map.put(id + "west", ExtraDataFixUtils.blockState("minecraft:" + skullType + "_wall_" + suffix, Map.of("facing", "west")));

            for (int i = 0; i < 16; i++) {
                map.put("" + id + i, ExtraDataFixUtils.blockState("minecraft:" + skullType + "_" + suffix, Map.of("rotation", String.valueOf(i))));
            }
        }

        private static void mapDoor(Map<String, Dynamic<?>> map, String doorId) {
            String string = "minecraft:" + doorId;
            map.put(
                "minecraft:" + doorId + "eastlowerleftfalsefalse",
                ExtraDataFixUtils.blockState(string, Map.of("facing", "east", "half", "lower", "hinge", "left", "open", "false", "powered", "false"))
            );
            map.put(
                "minecraft:" + doorId + "eastlowerleftfalsetrue",
                ExtraDataFixUtils.blockState(string, Map.of("facing", "east", "half", "lower", "hinge", "left", "open", "false", "powered", "true"))
            );
            map.put(
                "minecraft:" + doorId + "eastlowerlefttruefalse",
                ExtraDataFixUtils.blockState(string, Map.of("facing", "east", "half", "lower", "hinge", "left", "open", "true", "powered", "false"))
            );
            map.put(
                "minecraft:" + doorId + "eastlowerlefttruetrue",
                ExtraDataFixUtils.blockState(string, Map.of("facing", "east", "half", "lower", "hinge", "left", "open", "true", "powered", "true"))
            );
            map.put(
                "minecraft:" + doorId + "eastlowerrightfalsefalse",
                ExtraDataFixUtils.blockState(string, Map.of("facing", "east", "half", "lower", "hinge", "right", "open", "false", "powered", "false"))
            );
            map.put(
                "minecraft:" + doorId + "eastlowerrightfalsetrue",
                ExtraDataFixUtils.blockState(string, Map.of("facing", "east", "half", "lower", "hinge", "right", "open", "false", "powered", "true"))
            );
            map.put(
                "minecraft:" + doorId + "eastlowerrighttruefalse",
                ExtraDataFixUtils.blockState(string, Map.of("facing", "east", "half", "lower", "hinge", "right", "open", "true", "powered", "false"))
            );
            map.put(
                "minecraft:" + doorId + "eastlowerrighttruetrue",
                ExtraDataFixUtils.blockState(string, Map.of("facing", "east", "half", "lower", "hinge", "right", "open", "true", "powered", "true"))
            );
            map.put(
                "minecraft:" + doorId + "eastupperleftfalsefalse",
                ExtraDataFixUtils.blockState(string, Map.of("facing", "east", "half", "upper", "hinge", "left", "open", "false", "powered", "false"))
            );
            map.put(
                "minecraft:" + doorId + "eastupperleftfalsetrue",
                ExtraDataFixUtils.blockState(string, Map.of("facing", "east", "half", "upper", "hinge", "left", "open", "false", "powered", "true"))
            );
            map.put(
                "minecraft:" + doorId + "eastupperlefttruefalse",
                ExtraDataFixUtils.blockState(string, Map.of("facing", "east", "half", "upper", "hinge", "left", "open", "true", "powered", "false"))
            );
            map.put(
                "minecraft:" + doorId + "eastupperlefttruetrue",
                ExtraDataFixUtils.blockState(string, Map.of("facing", "east", "half", "upper", "hinge", "left", "open", "true", "powered", "true"))
            );
            map.put(
                "minecraft:" + doorId + "eastupperrightfalsefalse",
                ExtraDataFixUtils.blockState(string, Map.of("facing", "east", "half", "upper", "hinge", "right", "open", "false", "powered", "false"))
            );
            map.put(
                "minecraft:" + doorId + "eastupperrightfalsetrue",
                ExtraDataFixUtils.blockState(string, Map.of("facing", "east", "half", "upper", "hinge", "right", "open", "false", "powered", "true"))
            );
            map.put(
                "minecraft:" + doorId + "eastupperrighttruefalse",
                ExtraDataFixUtils.blockState(string, Map.of("facing", "east", "half", "upper", "hinge", "right", "open", "true", "powered", "false"))
            );
            map.put(
                "minecraft:" + doorId + "eastupperrighttruetrue",
                ExtraDataFixUtils.blockState(string, Map.of("facing", "east", "half", "upper", "hinge", "right", "open", "true", "powered", "true"))
            );
            map.put(
                "minecraft:" + doorId + "northlowerleftfalsefalse",
                ExtraDataFixUtils.blockState(string, Map.of("facing", "north", "half", "lower", "hinge", "left", "open", "false", "powered", "false"))
            );
            map.put(
                "minecraft:" + doorId + "northlowerleftfalsetrue",
                ExtraDataFixUtils.blockState(string, Map.of("facing", "north", "half", "lower", "hinge", "left", "open", "false", "powered", "true"))
            );
            map.put(
                "minecraft:" + doorId + "northlowerlefttruefalse",
                ExtraDataFixUtils.blockState(string, Map.of("facing", "north", "half", "lower", "hinge", "left", "open", "true", "powered", "false"))
            );
            map.put(
                "minecraft:" + doorId + "northlowerlefttruetrue",
                ExtraDataFixUtils.blockState(string, Map.of("facing", "north", "half", "lower", "hinge", "left", "open", "true", "powered", "true"))
            );
            map.put(
                "minecraft:" + doorId + "northlowerrightfalsefalse",
                ExtraDataFixUtils.blockState(string, Map.of("facing", "north", "half", "lower", "hinge", "right", "open", "false", "powered", "false"))
            );
            map.put(
                "minecraft:" + doorId + "northlowerrightfalsetrue",
                ExtraDataFixUtils.blockState(string, Map.of("facing", "north", "half", "lower", "hinge", "right", "open", "false", "powered", "true"))
            );
            map.put(
                "minecraft:" + doorId + "northlowerrighttruefalse",
                ExtraDataFixUtils.blockState(string, Map.of("facing", "north", "half", "lower", "hinge", "right", "open", "true", "powered", "false"))
            );
            map.put(
                "minecraft:" + doorId + "northlowerrighttruetrue",
                ExtraDataFixUtils.blockState(string, Map.of("facing", "north", "half", "lower", "hinge", "right", "open", "true", "powered", "true"))
            );
            map.put(
                "minecraft:" + doorId + "northupperleftfalsefalse",
                ExtraDataFixUtils.blockState(string, Map.of("facing", "north", "half", "upper", "hinge", "left", "open", "false", "powered", "false"))
            );
            map.put(
                "minecraft:" + doorId + "northupperleftfalsetrue",
                ExtraDataFixUtils.blockState(string, Map.of("facing", "north", "half", "upper", "hinge", "left", "open", "false", "powered", "true"))
            );
            map.put(
                "minecraft:" + doorId + "northupperlefttruefalse",
                ExtraDataFixUtils.blockState(string, Map.of("facing", "north", "half", "upper", "hinge", "left", "open", "true", "powered", "false"))
            );
            map.put(
                "minecraft:" + doorId + "northupperlefttruetrue",
                ExtraDataFixUtils.blockState(string, Map.of("facing", "north", "half", "upper", "hinge", "left", "open", "true", "powered", "true"))
            );
            map.put(
                "minecraft:" + doorId + "northupperrightfalsefalse",
                ExtraDataFixUtils.blockState(string, Map.of("facing", "north", "half", "upper", "hinge", "right", "open", "false", "powered", "false"))
            );
            map.put(
                "minecraft:" + doorId + "northupperrightfalsetrue",
                ExtraDataFixUtils.blockState(string, Map.of("facing", "north", "half", "upper", "hinge", "right", "open", "false", "powered", "true"))
            );
            map.put(
                "minecraft:" + doorId + "northupperrighttruefalse",
                ExtraDataFixUtils.blockState(string, Map.of("facing", "north", "half", "upper", "hinge", "right", "open", "true", "powered", "false"))
            );
            map.put(
                "minecraft:" + doorId + "northupperrighttruetrue",
                ExtraDataFixUtils.blockState(string, Map.of("facing", "north", "half", "upper", "hinge", "right", "open", "true", "powered", "true"))
            );
            map.put(
                "minecraft:" + doorId + "southlowerleftfalsefalse",
                ExtraDataFixUtils.blockState(string, Map.of("facing", "south", "half", "lower", "hinge", "left", "open", "false", "powered", "false"))
            );
            map.put(
                "minecraft:" + doorId + "southlowerleftfalsetrue",
                ExtraDataFixUtils.blockState(string, Map.of("facing", "south", "half", "lower", "hinge", "left", "open", "false", "powered", "true"))
            );
            map.put(
                "minecraft:" + doorId + "southlowerlefttruefalse",
                ExtraDataFixUtils.blockState(string, Map.of("facing", "south", "half", "lower", "hinge", "left", "open", "true", "powered", "false"))
            );
            map.put(
                "minecraft:" + doorId + "southlowerlefttruetrue",
                ExtraDataFixUtils.blockState(string, Map.of("facing", "south", "half", "lower", "hinge", "left", "open", "true", "powered", "true"))
            );
            map.put(
                "minecraft:" + doorId + "southlowerrightfalsefalse",
                ExtraDataFixUtils.blockState(string, Map.of("facing", "south", "half", "lower", "hinge", "right", "open", "false", "powered", "false"))
            );
            map.put(
                "minecraft:" + doorId + "southlowerrightfalsetrue",
                ExtraDataFixUtils.blockState(string, Map.of("facing", "south", "half", "lower", "hinge", "right", "open", "false", "powered", "true"))
            );
            map.put(
                "minecraft:" + doorId + "southlowerrighttruefalse",
                ExtraDataFixUtils.blockState(string, Map.of("facing", "south", "half", "lower", "hinge", "right", "open", "true", "powered", "false"))
            );
            map.put(
                "minecraft:" + doorId + "southlowerrighttruetrue",
                ExtraDataFixUtils.blockState(string, Map.of("facing", "south", "half", "lower", "hinge", "right", "open", "true", "powered", "true"))
            );
            map.put(
                "minecraft:" + doorId + "southupperleftfalsefalse",
                ExtraDataFixUtils.blockState(string, Map.of("facing", "south", "half", "upper", "hinge", "left", "open", "false", "powered", "false"))
            );
            map.put(
                "minecraft:" + doorId + "southupperleftfalsetrue",
                ExtraDataFixUtils.blockState(string, Map.of("facing", "south", "half", "upper", "hinge", "left", "open", "false", "powered", "true"))
            );
            map.put(
                "minecraft:" + doorId + "southupperlefttruefalse",
                ExtraDataFixUtils.blockState(string, Map.of("facing", "south", "half", "upper", "hinge", "left", "open", "true", "powered", "false"))
            );
            map.put(
                "minecraft:" + doorId + "southupperlefttruetrue",
                ExtraDataFixUtils.blockState(string, Map.of("facing", "south", "half", "upper", "hinge", "left", "open", "true", "powered", "true"))
            );
            map.put(
                "minecraft:" + doorId + "southupperrightfalsefalse",
                ExtraDataFixUtils.blockState(string, Map.of("facing", "south", "half", "upper", "hinge", "right", "open", "false", "powered", "false"))
            );
            map.put(
                "minecraft:" + doorId + "southupperrightfalsetrue",
                ExtraDataFixUtils.blockState(string, Map.of("facing", "south", "half", "upper", "hinge", "right", "open", "false", "powered", "true"))
            );
            map.put(
                "minecraft:" + doorId + "southupperrighttruefalse",
                ExtraDataFixUtils.blockState(string, Map.of("facing", "south", "half", "upper", "hinge", "right", "open", "true", "powered", "false"))
            );
            map.put(
                "minecraft:" + doorId + "southupperrighttruetrue",
                ExtraDataFixUtils.blockState(string, Map.of("facing", "south", "half", "upper", "hinge", "right", "open", "true", "powered", "true"))
            );
            map.put(
                "minecraft:" + doorId + "westlowerleftfalsefalse",
                ExtraDataFixUtils.blockState(string, Map.of("facing", "west", "half", "lower", "hinge", "left", "open", "false", "powered", "false"))
            );
            map.put(
                "minecraft:" + doorId + "westlowerleftfalsetrue",
                ExtraDataFixUtils.blockState(string, Map.of("facing", "west", "half", "lower", "hinge", "left", "open", "false", "powered", "true"))
            );
            map.put(
                "minecraft:" + doorId + "westlowerlefttruefalse",
                ExtraDataFixUtils.blockState(string, Map.of("facing", "west", "half", "lower", "hinge", "left", "open", "true", "powered", "false"))
            );
            map.put(
                "minecraft:" + doorId + "westlowerlefttruetrue",
                ExtraDataFixUtils.blockState(string, Map.of("facing", "west", "half", "lower", "hinge", "left", "open", "true", "powered", "true"))
            );
            map.put(
                "minecraft:" + doorId + "westlowerrightfalsefalse",
                ExtraDataFixUtils.blockState(string, Map.of("facing", "west", "half", "lower", "hinge", "right", "open", "false", "powered", "false"))
            );
            map.put(
                "minecraft:" + doorId + "westlowerrightfalsetrue",
                ExtraDataFixUtils.blockState(string, Map.of("facing", "west", "half", "lower", "hinge", "right", "open", "false", "powered", "true"))
            );
            map.put(
                "minecraft:" + doorId + "westlowerrighttruefalse",
                ExtraDataFixUtils.blockState(string, Map.of("facing", "west", "half", "lower", "hinge", "right", "open", "true", "powered", "false"))
            );
            map.put(
                "minecraft:" + doorId + "westlowerrighttruetrue",
                ExtraDataFixUtils.blockState(string, Map.of("facing", "west", "half", "lower", "hinge", "right", "open", "true", "powered", "true"))
            );
            map.put(
                "minecraft:" + doorId + "westupperleftfalsefalse",
                ExtraDataFixUtils.blockState(string, Map.of("facing", "west", "half", "upper", "hinge", "left", "open", "false", "powered", "false"))
            );
            map.put(
                "minecraft:" + doorId + "westupperleftfalsetrue",
                ExtraDataFixUtils.blockState(string, Map.of("facing", "west", "half", "upper", "hinge", "left", "open", "false", "powered", "true"))
            );
            map.put(
                "minecraft:" + doorId + "westupperlefttruefalse",
                ExtraDataFixUtils.blockState(string, Map.of("facing", "west", "half", "upper", "hinge", "left", "open", "true", "powered", "false"))
            );
            map.put(
                "minecraft:" + doorId + "westupperlefttruetrue",
                ExtraDataFixUtils.blockState(string, Map.of("facing", "west", "half", "upper", "hinge", "left", "open", "true", "powered", "true"))
            );
            map.put(
                "minecraft:" + doorId + "westupperrightfalsefalse",
                ExtraDataFixUtils.blockState(string, Map.of("facing", "west", "half", "upper", "hinge", "right", "open", "false", "powered", "false"))
            );
            map.put(
                "minecraft:" + doorId + "westupperrightfalsetrue",
                ExtraDataFixUtils.blockState(string, Map.of("facing", "west", "half", "upper", "hinge", "right", "open", "false", "powered", "true"))
            );
            map.put(
                "minecraft:" + doorId + "westupperrighttruefalse",
                ExtraDataFixUtils.blockState(string, Map.of("facing", "west", "half", "upper", "hinge", "right", "open", "true", "powered", "false"))
            );
            map.put(
                "minecraft:" + doorId + "westupperrighttruetrue",
                ExtraDataFixUtils.blockState(string, Map.of("facing", "west", "half", "upper", "hinge", "right", "open", "true", "powered", "true"))
            );
        }

        private static void addBeds(Map<String, Dynamic<?>> map, int id, String bedColor) {
            map.put(
                "southfalsefoot" + id,
                ExtraDataFixUtils.blockState("minecraft:" + bedColor + "_bed", Map.of("facing", "south", "occupied", "false", "part", "foot"))
            );
            map.put(
                "westfalsefoot" + id,
                ExtraDataFixUtils.blockState("minecraft:" + bedColor + "_bed", Map.of("facing", "west", "occupied", "false", "part", "foot"))
            );
            map.put(
                "northfalsefoot" + id,
                ExtraDataFixUtils.blockState("minecraft:" + bedColor + "_bed", Map.of("facing", "north", "occupied", "false", "part", "foot"))
            );
            map.put(
                "eastfalsefoot" + id,
                ExtraDataFixUtils.blockState("minecraft:" + bedColor + "_bed", Map.of("facing", "east", "occupied", "false", "part", "foot"))
            );
            map.put(
                "southfalsehead" + id,
                ExtraDataFixUtils.blockState("minecraft:" + bedColor + "_bed", Map.of("facing", "south", "occupied", "false", "part", "head"))
            );
            map.put(
                "westfalsehead" + id,
                ExtraDataFixUtils.blockState("minecraft:" + bedColor + "_bed", Map.of("facing", "west", "occupied", "false", "part", "head"))
            );
            map.put(
                "northfalsehead" + id,
                ExtraDataFixUtils.blockState("minecraft:" + bedColor + "_bed", Map.of("facing", "north", "occupied", "false", "part", "head"))
            );
            map.put(
                "eastfalsehead" + id,
                ExtraDataFixUtils.blockState("minecraft:" + bedColor + "_bed", Map.of("facing", "east", "occupied", "false", "part", "head"))
            );
            map.put(
                "southtruehead" + id,
                ExtraDataFixUtils.blockState("minecraft:" + bedColor + "_bed", Map.of("facing", "south", "occupied", "true", "part", "head"))
            );
            map.put(
                "westtruehead" + id,
                ExtraDataFixUtils.blockState("minecraft:" + bedColor + "_bed", Map.of("facing", "west", "occupied", "true", "part", "head"))
            );
            map.put(
                "northtruehead" + id,
                ExtraDataFixUtils.blockState("minecraft:" + bedColor + "_bed", Map.of("facing", "north", "occupied", "true", "part", "head"))
            );
            map.put(
                "easttruehead" + id,
                ExtraDataFixUtils.blockState("minecraft:" + bedColor + "_bed", Map.of("facing", "east", "occupied", "true", "part", "head"))
            );
        }

        private static void addBanners(Map<String, Dynamic<?>> map, int id, String bannerColor) {
            for (int i = 0; i < 16; i++) {
                map.put(i + "_" + id, ExtraDataFixUtils.blockState("minecraft:" + bannerColor + "_banner", Map.of("rotation", String.valueOf(i))));
            }

            map.put("north_" + id, ExtraDataFixUtils.blockState("minecraft:" + bannerColor + "_wall_banner", Map.of("facing", "north")));
            map.put("south_" + id, ExtraDataFixUtils.blockState("minecraft:" + bannerColor + "_wall_banner", Map.of("facing", "south")));
            map.put("west_" + id, ExtraDataFixUtils.blockState("minecraft:" + bannerColor + "_wall_banner", Map.of("facing", "west")));
            map.put("east_" + id, ExtraDataFixUtils.blockState("minecraft:" + bannerColor + "_wall_banner", Map.of("facing", "east")));
        }

        static {
            FIX.set(2);
            FIX.set(3);
            FIX.set(110);
            FIX.set(140);
            FIX.set(144);
            FIX.set(25);
            FIX.set(86);
            FIX.set(26);
            FIX.set(176);
            FIX.set(177);
            FIX.set(175);
            FIX.set(64);
            FIX.set(71);
            FIX.set(193);
            FIX.set(194);
            FIX.set(195);
            FIX.set(196);
            FIX.set(197);
            VIRTUAL.set(54);
            VIRTUAL.set(146);
            VIRTUAL.set(25);
            VIRTUAL.set(26);
            VIRTUAL.set(51);
            VIRTUAL.set(53);
            VIRTUAL.set(67);
            VIRTUAL.set(108);
            VIRTUAL.set(109);
            VIRTUAL.set(114);
            VIRTUAL.set(128);
            VIRTUAL.set(134);
            VIRTUAL.set(135);
            VIRTUAL.set(136);
            VIRTUAL.set(156);
            VIRTUAL.set(163);
            VIRTUAL.set(164);
            VIRTUAL.set(180);
            VIRTUAL.set(203);
            VIRTUAL.set(55);
            VIRTUAL.set(85);
            VIRTUAL.set(113);
            VIRTUAL.set(188);
            VIRTUAL.set(189);
            VIRTUAL.set(190);
            VIRTUAL.set(191);
            VIRTUAL.set(192);
            VIRTUAL.set(93);
            VIRTUAL.set(94);
            VIRTUAL.set(101);
            VIRTUAL.set(102);
            VIRTUAL.set(160);
            VIRTUAL.set(106);
            VIRTUAL.set(107);
            VIRTUAL.set(183);
            VIRTUAL.set(184);
            VIRTUAL.set(185);
            VIRTUAL.set(186);
            VIRTUAL.set(187);
            VIRTUAL.set(132);
            VIRTUAL.set(139);
            VIRTUAL.set(199);
        }
    }

    static class Section {
        private final CrudeIncrementalIntIdentityHashBiMap<Dynamic<?>> palette = CrudeIncrementalIntIdentityHashBiMap.create(32);
        private final List<Dynamic<?>> listTag;
        private final Dynamic<?> section;
        private final boolean hasData;
        final Int2ObjectMap<IntList> toFix = new Int2ObjectLinkedOpenHashMap<>();
        final IntList update = new IntArrayList();
        public final int y;
        private final Set<Dynamic<?>> seen = Sets.newIdentityHashSet();
        private final int[] buffer = new int[4096];

        public Section(Dynamic<?> section) {
            this.listTag = Lists.newArrayList();
            this.section = section;
            this.y = section.get("Y").asInt(0);
            this.hasData = section.get("Blocks").result().isPresent();
        }

        public Dynamic<?> getBlock(int index) {
            if (index >= 0 && index <= 4095) {
                Dynamic<?> dynamic = this.palette.byId(this.buffer[index]);
                return dynamic == null ? ChunkPalettedStorageFix.MappingConstants.AIR : dynamic;
            } else {
                return ChunkPalettedStorageFix.MappingConstants.AIR;
            }
        }

        public void setBlock(int index, Dynamic<?> block) {
            if (this.seen.add(block)) {
                this.listTag.add("%%FILTER_ME%%".equals(ChunkPalettedStorageFix.getName(block)) ? ChunkPalettedStorageFix.MappingConstants.AIR : block);
            }

            this.buffer[index] = ChunkPalettedStorageFix.idFor(this.palette, block);
        }

        public int upgrade(int sides) {
            if (!this.hasData) {
                return sides;
            } else {
                ByteBuffer byteBuffer = this.section.get("Blocks").asByteBufferOpt().result().get();
                ChunkPalettedStorageFix.DataLayer dataLayer = this.section
                    .get("Data")
                    .asByteBufferOpt()
                    .map(byteBuffer1 -> new ChunkPalettedStorageFix.DataLayer(DataFixUtils.toArray(byteBuffer1)))
                    .result()
                    .orElseGet(ChunkPalettedStorageFix.DataLayer::new);
                ChunkPalettedStorageFix.DataLayer dataLayer1 = this.section
                    .get("Add")
                    .asByteBufferOpt()
                    .map(byteBuffer1 -> new ChunkPalettedStorageFix.DataLayer(DataFixUtils.toArray(byteBuffer1)))
                    .result()
                    .orElseGet(ChunkPalettedStorageFix.DataLayer::new);
                this.seen.add(ChunkPalettedStorageFix.MappingConstants.AIR);
                ChunkPalettedStorageFix.idFor(this.palette, ChunkPalettedStorageFix.MappingConstants.AIR);
                this.listTag.add(ChunkPalettedStorageFix.MappingConstants.AIR);

                for (int i = 0; i < 4096; i++) {
                    int i1 = i & 15;
                    int i2 = i >> 8 & 15;
                    int i3 = i >> 4 & 15;
                    int i4 = dataLayer1.get(i1, i2, i3) << 12 | (byteBuffer.get(i) & 255) << 4 | dataLayer.get(i1, i2, i3);
                    if (ChunkPalettedStorageFix.MappingConstants.FIX.get(i4 >> 4)) {
                        this.addFix(i4 >> 4, i);
                    }

                    if (ChunkPalettedStorageFix.MappingConstants.VIRTUAL.get(i4 >> 4)) {
                        int sideMask = ChunkPalettedStorageFix.getSideMask(i1 == 0, i1 == 15, i3 == 0, i3 == 15);
                        if (sideMask == 0) {
                            this.update.add(i);
                        } else {
                            sides |= sideMask;
                        }
                    }

                    this.setBlock(i, BlockStateData.getTag(i4));
                }

                return sides;
            }
        }

        private void addFix(int index, int value) {
            IntList list = this.toFix.get(index);
            if (list == null) {
                list = new IntArrayList();
                this.toFix.put(index, list);
            }

            list.add(value);
        }

        public Dynamic<?> write() {
            Dynamic<?> dynamic = this.section;
            if (!this.hasData) {
                return dynamic;
            } else {
                dynamic = dynamic.set("Palette", dynamic.createList(this.listTag.stream()));
                int max = Math.max(4, DataFixUtils.ceillog2(this.seen.size()));
                PackedBitStorage packedBitStorage = new PackedBitStorage(max, 4096);

                for (int i = 0; i < this.buffer.length; i++) {
                    packedBitStorage.set(i, this.buffer[i]);
                }

                dynamic = dynamic.set("BlockStates", dynamic.createLongList(Arrays.stream(packedBitStorage.getRaw())));
                dynamic = dynamic.remove("Blocks");
                dynamic = dynamic.remove("Data");
                return dynamic.remove("Add");
            }
        }
    }

    static final class UpgradeChunk {
        private int sides;
        private final ChunkPalettedStorageFix.@Nullable Section[] sections = new ChunkPalettedStorageFix.Section[16];
        private final Dynamic<?> level;
        private final int x;
        private final int z;
        private final Int2ObjectMap<Dynamic<?>> blockEntities = new Int2ObjectLinkedOpenHashMap<>(16);

        public UpgradeChunk(Dynamic<?> level) {
            this.level = level;
            this.x = level.get("xPos").asInt(0) << 4;
            this.z = level.get("zPos").asInt(0) << 4;
            level.get("TileEntities")
                .asStreamOpt()
                .ifSuccess(
                    stream -> stream.forEach(
                        dynamic -> {
                            int i2 = dynamic.get("x").asInt(0) - this.x & 15;
                            int _int1 = dynamic.get("y").asInt(0);
                            int i3 = dynamic.get("z").asInt(0) - this.z & 15;
                            int i4 = _int1 << 8 | i3 << 4 | i2;
                            if (this.blockEntities.put(i4, (Dynamic<?>)dynamic) != null) {
                                ChunkPalettedStorageFix.LOGGER
                                    .warn("In chunk: {}x{} found a duplicate block entity at position: [{}, {}, {}]", this.x, this.z, i2, _int1, i3);
                            }
                        }
                    )
                );
            boolean _boolean = level.get("convertedFromAlphaFormat").asBoolean(false);
            level.get("Sections").asStreamOpt().ifSuccess(stream -> stream.forEach(dynamic -> {
                ChunkPalettedStorageFix.Section section1 = new ChunkPalettedStorageFix.Section((Dynamic<?>)dynamic);
                this.sides = section1.upgrade(this.sides);
                this.sections[section1.y] = section1;
            }));

            for (ChunkPalettedStorageFix.Section section : this.sections) {
                if (section != null) {
                    for (Entry<IntList> entry : section.toFix.int2ObjectEntrySet()) {
                        int i = section.y << 12;
                        switch (entry.getIntKey()) {
                            case 2:
                                for (int i1 : entry.getValue()) {
                                    i1 |= i;
                                    Dynamic<?> block = this.getBlock(i1);
                                    if ("minecraft:grass_block".equals(ChunkPalettedStorageFix.getName(block))) {
                                        String name = ChunkPalettedStorageFix.getName(this.getBlock(relative(i1, ChunkPalettedStorageFix.Direction.UP)));
                                        if ("minecraft:snow".equals(name) || "minecraft:snow_layer".equals(name)) {
                                            this.setBlock(i1, ChunkPalettedStorageFix.MappingConstants.SNOWY_GRASS);
                                        }
                                    }
                                }
                                break;
                            case 3:
                                for (int i1xxxxxxxxx : entry.getValue()) {
                                    i1xxxxxxxxx |= i;
                                    Dynamic<?> block = this.getBlock(i1xxxxxxxxx);
                                    if ("minecraft:podzol".equals(ChunkPalettedStorageFix.getName(block))) {
                                        String name = ChunkPalettedStorageFix.getName(
                                            this.getBlock(relative(i1xxxxxxxxx, ChunkPalettedStorageFix.Direction.UP))
                                        );
                                        if ("minecraft:snow".equals(name) || "minecraft:snow_layer".equals(name)) {
                                            this.setBlock(i1xxxxxxxxx, ChunkPalettedStorageFix.MappingConstants.SNOWY_PODZOL);
                                        }
                                    }
                                }
                                break;
                            case 25:
                                for (int i1xxxxx : entry.getValue()) {
                                    i1xxxxx |= i;
                                    Dynamic<?> block = this.removeBlockEntity(i1xxxxx);
                                    if (block != null) {
                                        String name = Boolean.toString(block.get("powered").asBoolean(false))
                                            + (byte)Math.min(Math.max(block.get("note").asInt(0), 0), 24);
                                        this.setBlock(
                                            i1xxxxx,
                                            ChunkPalettedStorageFix.MappingConstants.NOTE_BLOCK_MAP
                                                .getOrDefault(name, ChunkPalettedStorageFix.MappingConstants.NOTE_BLOCK_MAP.get("false0"))
                                        );
                                    }
                                }
                                break;
                            case 26:
                                for (int i1xxxx : entry.getValue()) {
                                    i1xxxx |= i;
                                    Dynamic<?> block = this.getBlockEntity(i1xxxx);
                                    Dynamic<?> block1 = this.getBlock(i1xxxx);
                                    if (block != null) {
                                        int _int = block.get("color").asInt(0);
                                        if (_int != 14 && _int >= 0 && _int < 16) {
                                            String string = ChunkPalettedStorageFix.getProperty(block1, "facing")
                                                + ChunkPalettedStorageFix.getProperty(block1, "occupied")
                                                + ChunkPalettedStorageFix.getProperty(block1, "part")
                                                + _int;
                                            if (ChunkPalettedStorageFix.MappingConstants.BED_BLOCK_MAP.containsKey(string)) {
                                                this.setBlock(i1xxxx, ChunkPalettedStorageFix.MappingConstants.BED_BLOCK_MAP.get(string));
                                            }
                                        }
                                    }
                                }
                                break;
                            case 64:
                            case 71:
                            case 193:
                            case 194:
                            case 195:
                            case 196:
                            case 197:
                                for (int i1xxx : entry.getValue()) {
                                    i1xxx |= i;
                                    Dynamic<?> block = this.getBlock(i1xxx);
                                    if (ChunkPalettedStorageFix.getName(block).endsWith("_door")) {
                                        Dynamic<?> block1 = this.getBlock(i1xxx);
                                        if ("lower".equals(ChunkPalettedStorageFix.getProperty(block1, "half"))) {
                                            int _int = relative(i1xxx, ChunkPalettedStorageFix.Direction.UP);
                                            Dynamic<?> block2 = this.getBlock(_int);
                                            String name1 = ChunkPalettedStorageFix.getName(block1);
                                            if (name1.equals(ChunkPalettedStorageFix.getName(block2))) {
                                                String property1 = ChunkPalettedStorageFix.getProperty(block1, "facing");
                                                String property2 = ChunkPalettedStorageFix.getProperty(block1, "open");
                                                String string1 = _boolean ? "left" : ChunkPalettedStorageFix.getProperty(block2, "hinge");
                                                String string2 = _boolean ? "false" : ChunkPalettedStorageFix.getProperty(block2, "powered");
                                                this.setBlock(
                                                    i1xxx,
                                                    ChunkPalettedStorageFix.MappingConstants.DOOR_MAP
                                                        .get(name1 + property1 + "lower" + string1 + property2 + string2)
                                                );
                                                this.setBlock(
                                                    _int,
                                                    ChunkPalettedStorageFix.MappingConstants.DOOR_MAP
                                                        .get(name1 + property1 + "upper" + string1 + property2 + string2)
                                                );
                                            }
                                        }
                                    }
                                }
                                break;
                            case 86:
                                for (int i1xxxxxxxx : entry.getValue()) {
                                    i1xxxxxxxx |= i;
                                    Dynamic<?> block = this.getBlock(i1xxxxxxxx);
                                    if ("minecraft:carved_pumpkin".equals(ChunkPalettedStorageFix.getName(block))) {
                                        String name = ChunkPalettedStorageFix.getName(
                                            this.getBlock(relative(i1xxxxxxxx, ChunkPalettedStorageFix.Direction.DOWN))
                                        );
                                        if ("minecraft:grass_block".equals(name) || "minecraft:dirt".equals(name)) {
                                            this.setBlock(i1xxxxxxxx, ChunkPalettedStorageFix.MappingConstants.PUMPKIN);
                                        }
                                    }
                                }
                                break;
                            case 110:
                                for (int i1xxxxxxx : entry.getValue()) {
                                    i1xxxxxxx |= i;
                                    Dynamic<?> block = this.getBlock(i1xxxxxxx);
                                    if ("minecraft:mycelium".equals(ChunkPalettedStorageFix.getName(block))) {
                                        String name = ChunkPalettedStorageFix.getName(this.getBlock(relative(i1xxxxxxx, ChunkPalettedStorageFix.Direction.UP)));
                                        if ("minecraft:snow".equals(name) || "minecraft:snow_layer".equals(name)) {
                                            this.setBlock(i1xxxxxxx, ChunkPalettedStorageFix.MappingConstants.SNOWY_MYCELIUM);
                                        }
                                    }
                                }
                                break;
                            case 140:
                                for (int i1xx : entry.getValue()) {
                                    i1xx |= i;
                                    Dynamic<?> block = this.removeBlockEntity(i1xx);
                                    if (block != null) {
                                        String name = block.get("Item").asString("") + block.get("Data").asInt(0);
                                        this.setBlock(
                                            i1xx,
                                            ChunkPalettedStorageFix.MappingConstants.FLOWER_POT_MAP
                                                .getOrDefault(name, ChunkPalettedStorageFix.MappingConstants.FLOWER_POT_MAP.get("minecraft:air0"))
                                        );
                                    }
                                }
                                break;
                            case 144:
                                for (int i1xxxxxx : entry.getValue()) {
                                    i1xxxxxx |= i;
                                    Dynamic<?> block = this.getBlockEntity(i1xxxxxx);
                                    if (block != null) {
                                        String name = String.valueOf(block.get("SkullType").asInt(0));
                                        String property = ChunkPalettedStorageFix.getProperty(this.getBlock(i1xxxxxx), "facing");
                                        String string;
                                        if (!"up".equals(property) && !"down".equals(property)) {
                                            string = name + property;
                                        } else {
                                            string = name + block.get("Rot").asInt(0);
                                        }

                                        block.remove("SkullType");
                                        block.remove("facing");
                                        block.remove("Rot");
                                        this.setBlock(
                                            i1xxxxxx,
                                            ChunkPalettedStorageFix.MappingConstants.SKULL_MAP
                                                .getOrDefault(string, ChunkPalettedStorageFix.MappingConstants.SKULL_MAP.get("0north"))
                                        );
                                    }
                                }
                                break;
                            case 175:
                                for (int i1x : entry.getValue()) {
                                    i1x |= i;
                                    Dynamic<?> block = this.getBlock(i1x);
                                    if ("upper".equals(ChunkPalettedStorageFix.getProperty(block, "half"))) {
                                        Dynamic<?> block1 = this.getBlock(relative(i1x, ChunkPalettedStorageFix.Direction.DOWN));
                                        String property = ChunkPalettedStorageFix.getName(block1);
                                        switch (property) {
                                            case "minecraft:sunflower":
                                                this.setBlock(i1x, ChunkPalettedStorageFix.MappingConstants.UPPER_SUNFLOWER);
                                                break;
                                            case "minecraft:lilac":
                                                this.setBlock(i1x, ChunkPalettedStorageFix.MappingConstants.UPPER_LILAC);
                                                break;
                                            case "minecraft:tall_grass":
                                                this.setBlock(i1x, ChunkPalettedStorageFix.MappingConstants.UPPER_TALL_GRASS);
                                                break;
                                            case "minecraft:large_fern":
                                                this.setBlock(i1x, ChunkPalettedStorageFix.MappingConstants.UPPER_LARGE_FERN);
                                                break;
                                            case "minecraft:rose_bush":
                                                this.setBlock(i1x, ChunkPalettedStorageFix.MappingConstants.UPPER_ROSE_BUSH);
                                                break;
                                            case "minecraft:peony":
                                                this.setBlock(i1x, ChunkPalettedStorageFix.MappingConstants.UPPER_PEONY);
                                        }
                                    }
                                }
                                break;
                            case 176:
                            case 177:
                                for (int i1xxxxxxxxxx : entry.getValue()) {
                                    i1xxxxxxxxxx |= i;
                                    Dynamic<?> block = this.getBlockEntity(i1xxxxxxxxxx);
                                    Dynamic<?> block1 = this.getBlock(i1xxxxxxxxxx);
                                    if (block != null) {
                                        int _int = block.get("Base").asInt(0);
                                        if (_int != 15 && _int >= 0 && _int < 16) {
                                            String string = ChunkPalettedStorageFix.getProperty(block1, entry.getIntKey() == 176 ? "rotation" : "facing")
                                                + "_"
                                                + _int;
                                            if (ChunkPalettedStorageFix.MappingConstants.BANNER_BLOCK_MAP.containsKey(string)) {
                                                this.setBlock(i1xxxxxxxxxx, ChunkPalettedStorageFix.MappingConstants.BANNER_BLOCK_MAP.get(string));
                                            }
                                        }
                                    }
                                }
                        }
                    }
                }
            }
        }

        private @Nullable Dynamic<?> getBlockEntity(int index) {
            return this.blockEntities.get(index);
        }

        private @Nullable Dynamic<?> removeBlockEntity(int index) {
            return this.blockEntities.remove(index);
        }

        public static int relative(int data, ChunkPalettedStorageFix.Direction direction) {
            return switch (direction.getAxis()) {
                case X -> {
                    int i = (data & 15) + direction.getAxisDirection().getStep();
                    yield i >= 0 && i <= 15 ? data & -16 | i : -1;
                }
                case Y -> {
                    int i = (data >> 8) + direction.getAxisDirection().getStep();
                    yield i >= 0 && i <= 255 ? data & 0xFF | i << 8 : -1;
                }
                case Z -> {
                    int i = (data >> 4 & 15) + direction.getAxisDirection().getStep();
                    yield i >= 0 && i <= 15 ? data & -241 | i << 4 : -1;
                }
            };
        }

        private void setBlock(int index, Dynamic<?> block) {
            if (index >= 0 && index <= 65535) {
                ChunkPalettedStorageFix.Section section = this.getSection(index);
                if (section != null) {
                    section.setBlock(index & 4095, block);
                }
            }
        }

        private ChunkPalettedStorageFix.@Nullable Section getSection(int index) {
            int i = index >> 12;
            return i < this.sections.length ? this.sections[i] : null;
        }

        public Dynamic<?> getBlock(int index) {
            if (index >= 0 && index <= 65535) {
                ChunkPalettedStorageFix.Section section = this.getSection(index);
                return section == null ? ChunkPalettedStorageFix.MappingConstants.AIR : section.getBlock(index & 4095);
            } else {
                return ChunkPalettedStorageFix.MappingConstants.AIR;
            }
        }

        public Dynamic<?> write() {
            Dynamic<?> dynamic = this.level;
            if (this.blockEntities.isEmpty()) {
                dynamic = dynamic.remove("TileEntities");
            } else {
                dynamic = dynamic.set("TileEntities", dynamic.createList(this.blockEntities.values().stream()));
            }

            Dynamic<?> dynamic1 = dynamic.emptyMap();
            List<Dynamic<?>> list = Lists.newArrayList();

            for (ChunkPalettedStorageFix.Section section : this.sections) {
                if (section != null) {
                    list.add(section.write());
                    dynamic1 = dynamic1.set(String.valueOf(section.y), dynamic1.createIntList(Arrays.stream(section.update.toIntArray())));
                }
            }

            Dynamic<?> dynamic2 = dynamic.emptyMap();
            dynamic2 = dynamic2.set("Sides", dynamic2.createByte((byte)this.sides));
            dynamic2 = dynamic2.set("Indices", dynamic1);
            return dynamic.set("UpgradeData", dynamic2).set("Sections", dynamic2.createList(list.stream()));
        }
    }
}
