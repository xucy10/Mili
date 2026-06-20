package net.minecraft.util.datafix.fixes;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.types.templates.List.ListType;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.util.datafix.PackedBitStorage;
import org.jspecify.annotations.Nullable;

public class LeavesFix extends DataFix {
    private static final int NORTH_WEST_MASK = 128;
    private static final int WEST_MASK = 64;
    private static final int SOUTH_WEST_MASK = 32;
    private static final int SOUTH_MASK = 16;
    private static final int SOUTH_EAST_MASK = 8;
    private static final int EAST_MASK = 4;
    private static final int NORTH_EAST_MASK = 2;
    private static final int NORTH_MASK = 1;
    private static final int[][] DIRECTIONS = new int[][]{{-1, 0, 0}, {1, 0, 0}, {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}};
    private static final int DECAY_DISTANCE = 7;
    private static final int SIZE_BITS = 12;
    private static final int SIZE = 4096;
    static final Object2IntMap<String> LEAVES = DataFixUtils.make(new Object2IntOpenHashMap<>(), map -> {
        map.put("minecraft:acacia_leaves", 0);
        map.put("minecraft:birch_leaves", 1);
        map.put("minecraft:dark_oak_leaves", 2);
        map.put("minecraft:jungle_leaves", 3);
        map.put("minecraft:oak_leaves", 4);
        map.put("minecraft:spruce_leaves", 5);
    });
    static final Set<String> LOGS = ImmutableSet.of(
        "minecraft:acacia_bark",
        "minecraft:birch_bark",
        "minecraft:dark_oak_bark",
        "minecraft:jungle_bark",
        "minecraft:oak_bark",
        "minecraft:spruce_bark",
        "minecraft:acacia_log",
        "minecraft:birch_log",
        "minecraft:dark_oak_log",
        "minecraft:jungle_log",
        "minecraft:oak_log",
        "minecraft:spruce_log",
        "minecraft:stripped_acacia_log",
        "minecraft:stripped_birch_log",
        "minecraft:stripped_dark_oak_log",
        "minecraft:stripped_jungle_log",
        "minecraft:stripped_oak_log",
        "minecraft:stripped_spruce_log"
    );

    public LeavesFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.CHUNK);
        OpticFinder<?> opticFinder = type.findField("Level");
        OpticFinder<?> opticFinder1 = opticFinder.type().findField("Sections");
        Type<?> type1 = opticFinder1.type();
        if (!(type1 instanceof ListType)) {
            throw new IllegalStateException("Expecting sections to be a list.");
        } else {
            Type<?> element = ((ListType)type1).getElement();
            OpticFinder<?> opticFinder2 = DSL.typeFinder(element);
            return this.fixTypeEverywhereTyped(
                "Leaves fix",
                type,
                typed -> typed.updateTyped(
                    opticFinder,
                    typed1 -> {
                        int[] ints = new int[]{0};
                        Typed<?> typed2 = typed1.updateTyped(
                            opticFinder1,
                            typed3 -> {
                                Int2ObjectMap<LeavesFix.LeavesSection> map = new Int2ObjectOpenHashMap<>(
                                    typed3.getAllTyped(opticFinder2)
                                        .stream()
                                        .map(typed4 -> new LeavesFix.LeavesSection((Typed<?>)typed4, this.getInputSchema()))
                                        .collect(Collectors.toMap(LeavesFix.Section::getIndex, leavesSection2 -> (LeavesFix.LeavesSection)leavesSection2))
                                );
                                if (map.values().stream().allMatch(LeavesFix.Section::isSkippable)) {
                                    return typed3;
                                } else {
                                    List<IntSet> list = Lists.newArrayList();

                                    for (int i = 0; i < 7; i++) {
                                        list.add(new IntOpenHashSet());
                                    }

                                    for (LeavesFix.LeavesSection leavesSection : map.values()) {
                                        if (!leavesSection.isSkippable()) {
                                            for (int i1 = 0; i1 < 4096; i1++) {
                                                int block = leavesSection.getBlock(i1);
                                                if (leavesSection.isLog(block)) {
                                                    list.get(0).add(leavesSection.getIndex() << 12 | i1);
                                                } else if (leavesSection.isLeaf(block)) {
                                                    int x = this.getX(i1);
                                                    int z = this.getZ(i1);
                                                    ints[0] |= getSideMask(x == 0, x == 15, z == 0, z == 15);
                                                }
                                            }
                                        }
                                    }

                                    for (int i = 1; i < 7; i++) {
                                        IntSet set = list.get(i - 1);
                                        IntSet set1 = list.get(i);
                                        IntIterator intIterator = set.iterator();

                                        while (intIterator.hasNext()) {
                                            int x = intIterator.nextInt();
                                            int z = this.getX(x);
                                            int y = this.getY(x);
                                            int z1 = this.getZ(x);

                                            for (int[] ints1 : DIRECTIONS) {
                                                int i2 = z + ints1[0];
                                                int i3 = y + ints1[1];
                                                int i4 = z1 + ints1[2];
                                                if (i2 >= 0 && i2 <= 15 && i4 >= 0 && i4 <= 15 && i3 >= 0 && i3 <= 255) {
                                                    LeavesFix.LeavesSection leavesSection1 = map.get(i3 >> 4);
                                                    if (leavesSection1 != null && !leavesSection1.isSkippable()) {
                                                        int index = getIndex(i2, i3 & 15, i4);
                                                        int block1 = leavesSection1.getBlock(index);
                                                        if (leavesSection1.isLeaf(block1)) {
                                                            int distance = leavesSection1.getDistance(block1);
                                                            if (distance > i) {
                                                                leavesSection1.setDistance(index, block1, i);
                                                                set1.add(getIndex(i2, i3, i4));
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    return typed3.updateTyped(
                                        opticFinder2, typed4 -> map.get(typed4.get(DSL.remainderFinder()).get("Y").asInt(0)).write(typed4)
                                    );
                                }
                            }
                        );
                        if (ints[0] != 0) {
                            typed2 = typed2.update(DSL.remainderFinder(), dynamic -> {
                                Dynamic<?> dynamic1 = DataFixUtils.orElse(dynamic.get("UpgradeData").result(), dynamic.emptyMap());
                                return dynamic.set(
                                    "UpgradeData", dynamic1.set("Sides", dynamic.createByte((byte)(dynamic1.get("Sides").asByte((byte)0) | ints[0])))
                                );
                            });
                        }

                        return typed2;
                    }
                )
            );
        }
    }

    public static int getIndex(int x, int y, int z) {
        return y << 8 | z << 4 | x;
    }

    private int getX(int index) {
        return index & 15;
    }

    private int getY(int index) {
        return index >> 8 & 0xFF;
    }

    private int getZ(int index) {
        return index >> 4 & 15;
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

    public static final class LeavesSection extends LeavesFix.Section {
        private static final String PERSISTENT = "persistent";
        private static final String DECAYABLE = "decayable";
        private static final String DISTANCE = "distance";
        private @Nullable IntSet leaveIds;
        private @Nullable IntSet logIds;
        private @Nullable Int2IntMap stateToIdMap;

        public LeavesSection(Typed<?> data, Schema schema) {
            super(data, schema);
        }

        @Override
        protected boolean skippable() {
            this.leaveIds = new IntOpenHashSet();
            this.logIds = new IntOpenHashSet();
            this.stateToIdMap = new Int2IntOpenHashMap();

            for (int i = 0; i < this.palette.size(); i++) {
                Dynamic<?> dynamic = this.palette.get(i);
                String string = dynamic.get("Name").asString("");
                if (LeavesFix.LEAVES.containsKey(string)) {
                    boolean flag = Objects.equals(dynamic.get("Properties").get("decayable").asString(""), "false");
                    this.leaveIds.add(i);
                    this.stateToIdMap.put(this.getStateId(string, flag, 7), i);
                    this.palette.set(i, this.makeLeafTag(dynamic, string, flag, 7));
                }

                if (LeavesFix.LOGS.contains(string)) {
                    this.logIds.add(i);
                }
            }

            return this.leaveIds.isEmpty() && this.logIds.isEmpty();
        }

        private Dynamic<?> makeLeafTag(Dynamic<?> dynamic, String name, boolean persistent, int distance) {
            Dynamic<?> dynamic1 = dynamic.emptyMap();
            dynamic1 = dynamic1.set("persistent", dynamic1.createString(persistent ? "true" : "false"));
            dynamic1 = dynamic1.set("distance", dynamic1.createString(Integer.toString(distance)));
            Dynamic<?> dynamic2 = dynamic.emptyMap();
            dynamic2 = dynamic2.set("Properties", dynamic1);
            return dynamic2.set("Name", dynamic2.createString(name));
        }

        public boolean isLog(int id) {
            return this.logIds.contains(id);
        }

        public boolean isLeaf(int id) {
            return this.leaveIds.contains(id);
        }

        int getDistance(int index) {
            return this.isLog(index) ? 0 : Integer.parseInt(this.palette.get(index).get("Properties").get("distance").asString(""));
        }

        void setDistance(int index, int block, int distance) {
            Dynamic<?> dynamic = this.palette.get(block);
            String string = dynamic.get("Name").asString("");
            boolean flag = Objects.equals(dynamic.get("Properties").get("persistent").asString(""), "true");
            int stateId = this.getStateId(string, flag, distance);
            if (!this.stateToIdMap.containsKey(stateId)) {
                int size = this.palette.size();
                this.leaveIds.add(size);
                this.stateToIdMap.put(stateId, size);
                this.palette.add(this.makeLeafTag(dynamic, string, flag, distance));
            }

            int size = this.stateToIdMap.get(stateId);
            if (1 << this.storage.getBits() <= size) {
                PackedBitStorage packedBitStorage = new PackedBitStorage(this.storage.getBits() + 1, 4096);

                for (int i = 0; i < 4096; i++) {
                    packedBitStorage.set(i, this.storage.get(i));
                }

                this.storage = packedBitStorage;
            }

            this.storage.set(index, size);
        }
    }

    public abstract static class Section {
        protected static final String BLOCK_STATES_TAG = "BlockStates";
        protected static final String NAME_TAG = "Name";
        protected static final String PROPERTIES_TAG = "Properties";
        private final Type<Pair<String, Dynamic<?>>> blockStateType = DSL.named(References.BLOCK_STATE.typeName(), DSL.remainderType());
        protected final OpticFinder<List<Pair<String, Dynamic<?>>>> paletteFinder = DSL.fieldFinder("Palette", DSL.list(this.blockStateType));
        protected final List<Dynamic<?>> palette;
        protected final int index;
        protected @Nullable PackedBitStorage storage;

        public Section(Typed<?> data, Schema schema) {
            if (!Objects.equals(schema.getType(References.BLOCK_STATE), this.blockStateType)) {
                throw new IllegalStateException("Block state type is not what was expected.");
            } else {
                Optional<List<Pair<String, Dynamic<?>>>> optional = data.getOptional(this.paletteFinder);
                this.palette = optional.<List>map(list -> list.stream().map(Pair::getSecond).collect(Collectors.toList())).orElse(ImmutableList.of());
                Dynamic<?> dynamic = data.get(DSL.remainderFinder());
                this.index = dynamic.get("Y").asInt(0);
                this.readStorage(dynamic);
            }
        }

        protected void readStorage(Dynamic<?> data) {
            if (this.skippable()) {
                this.storage = null;
            } else {
                long[] longs = data.get("BlockStates").asLongStream().toArray();
                int max = Math.max(4, DataFixUtils.ceillog2(this.palette.size()));
                this.storage = new PackedBitStorage(max, 4096, longs);
            }
        }

        public Typed<?> write(Typed<?> data) {
            return this.isSkippable()
                ? data
                : data.update(DSL.remainderFinder(), dynamic -> dynamic.set("BlockStates", dynamic.createLongList(Arrays.stream(this.storage.getRaw()))))
                    .set(
                        this.paletteFinder,
                        this.palette.stream().<Pair<String, Dynamic<?>>>map(dynamic -> Pair.of(References.BLOCK_STATE.typeName(), dynamic)).collect(Collectors.toList())
                    );
        }

        public boolean isSkippable() {
            return this.storage == null;
        }

        public int getBlock(int index) {
            return this.storage.get(index);
        }

        protected int getStateId(String name, boolean persistent, int distance) {
            return LeavesFix.LEAVES.get(name) << 5 | (persistent ? 16 : 0) | distance;
        }

        int getIndex() {
            return this.index;
        }

        protected abstract boolean skippable();
    }
}
