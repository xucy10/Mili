package net.minecraft.util.datafix.fixes;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.OptionalDynamic;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import it.unimi.dsi.fastutil.ints.Int2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.util.Util;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableObject;
import org.jspecify.annotations.Nullable;

public class ChunkHeightAndBiomeFix extends DataFix {
    public static final String DATAFIXER_CONTEXT_TAG = "__context";
    private static final String NAME = "ChunkHeightAndBiomeFix";
    private static final int OLD_SECTION_COUNT = 16;
    private static final int NEW_SECTION_COUNT = 24;
    private static final int NEW_MIN_SECTION_Y = -4;
    public static final int BLOCKS_PER_SECTION = 4096;
    private static final int LONGS_PER_SECTION = 64;
    private static final int HEIGHTMAP_BITS = 9;
    private static final long HEIGHTMAP_MASK = 511L;
    private static final int HEIGHTMAP_OFFSET = 64;
    private static final String[] HEIGHTMAP_TYPES = new String[]{
        "WORLD_SURFACE_WG", "WORLD_SURFACE", "WORLD_SURFACE_IGNORE_SNOW", "OCEAN_FLOOR_WG", "OCEAN_FLOOR", "MOTION_BLOCKING", "MOTION_BLOCKING_NO_LEAVES"
    };
    private static final Set<String> STATUS_IS_OR_AFTER_SURFACE = Set.of(
        "surface", "carvers", "liquid_carvers", "features", "light", "spawn", "heightmaps", "full"
    );
    private static final Set<String> STATUS_IS_OR_AFTER_NOISE = Set.of(
        "noise", "surface", "carvers", "liquid_carvers", "features", "light", "spawn", "heightmaps", "full"
    );
    private static final Set<String> BLOCKS_BEFORE_FEATURE_STATUS = Set.of(
        "minecraft:air",
        "minecraft:basalt",
        "minecraft:bedrock",
        "minecraft:blackstone",
        "minecraft:calcite",
        "minecraft:cave_air",
        "minecraft:coarse_dirt",
        "minecraft:crimson_nylium",
        "minecraft:dirt",
        "minecraft:end_stone",
        "minecraft:grass_block",
        "minecraft:gravel",
        "minecraft:ice",
        "minecraft:lava",
        "minecraft:mycelium",
        "minecraft:nether_wart_block",
        "minecraft:netherrack",
        "minecraft:orange_terracotta",
        "minecraft:packed_ice",
        "minecraft:podzol",
        "minecraft:powder_snow",
        "minecraft:red_sand",
        "minecraft:red_sandstone",
        "minecraft:sand",
        "minecraft:sandstone",
        "minecraft:snow_block",
        "minecraft:soul_sand",
        "minecraft:soul_soil",
        "minecraft:stone",
        "minecraft:terracotta",
        "minecraft:warped_nylium",
        "minecraft:warped_wart_block",
        "minecraft:water",
        "minecraft:white_terracotta"
    );
    private static final int BIOME_CONTAINER_LAYER_SIZE = 16;
    private static final int BIOME_CONTAINER_SIZE = 64;
    private static final int BIOME_CONTAINER_TOP_LAYER_OFFSET = 1008;
    public static final String DEFAULT_BIOME = "minecraft:plains";
    private static final Int2ObjectMap<String> BIOMES_BY_ID = new Int2ObjectOpenHashMap<>();

    public ChunkHeightAndBiomeFix(Schema outputSchema) {
        super(outputSchema, true);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.CHUNK);
        OpticFinder<?> opticFinder = type.findField("Level");
        OpticFinder<?> opticFinder1 = opticFinder.type().findField("Sections");
        Schema outputSchema = this.getOutputSchema();
        Type<?> type1 = outputSchema.getType(References.CHUNK);
        Type<?> type2 = type1.findField("Level").type();
        Type<?> type3 = type2.findField("Sections").type();
        return this.fixTypeEverywhereTyped(
            "ChunkHeightAndBiomeFix",
            type,
            type1,
            typed -> typed.updateTyped(
                opticFinder,
                type2,
                typed1 -> {
                    Dynamic<?> dynamic = typed1.get(DSL.remainderFinder());
                    OptionalDynamic<?> optionalDynamic = typed.get(DSL.remainderFinder()).get("__context");
                    String string = optionalDynamic.get("dimension").asString().result().orElse("");
                    String string1 = optionalDynamic.get("generator").asString().result().orElse("");
                    boolean flag = "minecraft:overworld".equals(string);
                    MutableBoolean mutableBoolean = new MutableBoolean();
                    int i = flag ? -4 : 0;
                    Dynamic<?>[] biomeContainers = getBiomeContainers(dynamic, flag, i, mutableBoolean);
                    Dynamic<?> dynamic1 = makePalettedContainer(
                        dynamic.createList(Stream.of(dynamic.createMap(ImmutableMap.of(dynamic.createString("Name"), dynamic.createString("minecraft:air")))))
                    );
                    Set<String> set = Sets.newHashSet();
                    MutableObject<Supplier<ChunkProtoTickListFix.PoorMansPalettedContainer>> mutableObject = new MutableObject<>(() -> null);
                    typed1 = typed1.updateTyped(opticFinder1, type3, typed2 -> {
                        IntSet set1 = new IntOpenHashSet();
                        Dynamic<?> dynamic2 = (Dynamic<?>)typed2.write()
                            .result()
                            .orElseThrow(() -> new IllegalStateException("Malformed Chunk.Level.Sections"));
                        List<Dynamic<?>> list = dynamic2.asStream().map(dynamic4 -> {
                            int _int = dynamic4.get("Y").asInt(0);
                            Dynamic<?> dynamic5 = DataFixUtils.orElse(dynamic4.get("Palette").result().flatMap(dynamic7 -> {
                                dynamic7.asStream().map(dynamic8 -> dynamic8.get("Name").asString("minecraft:air")).forEach(set::add);
                                return dynamic4.get("BlockStates").result().map(dynamic8 -> makeOptimizedPalettedContainer(dynamic7, (Dynamic<?>)dynamic8));
                            }), dynamic1);
                            Dynamic<?> dynamic6 = (Dynamic<?>)dynamic4;
                            int i3 = _int - i;
                            if (i3 >= 0 && i3 < biomeContainers.length) {
                                dynamic6 = dynamic4.set("biomes", biomeContainers[i3]);
                            }

                            set1.add(_int);
                            if (dynamic4.get("Y").asInt(Integer.MAX_VALUE) == 0) {
                                mutableObject.setValue(() -> {
                                    List<? extends Dynamic<?>> list1 = dynamic5.get("palette").asList(Function.identity());
                                    long[] longs = dynamic5.get("data").asLongStream().toArray();
                                    return new ChunkProtoTickListFix.PoorMansPalettedContainer(list1, longs);
                                });
                            }

                            return dynamic6.set("block_states", dynamic5).remove("Palette").remove("BlockStates");
                        }).collect(Collectors.toCollection(ArrayList::new));

                        for (int i1 = 0; i1 < biomeContainers.length; i1++) {
                            int i2 = i1 + i;
                            if (set1.add(i2)) {
                                Dynamic<?> dynamic3 = dynamic.createMap(Map.of(dynamic.createString("Y"), dynamic.createInt(i2)));
                                dynamic3 = dynamic3.set("block_states", dynamic1);
                                dynamic3 = dynamic3.set("biomes", biomeContainers[i1]);
                                list.add(dynamic3);
                            }
                        }

                        return Util.readTypedOrThrow(type3, dynamic.createList(list.stream()));
                    });
                    return typed1.update(DSL.remainderFinder(), dynamic2 -> {
                        if (flag) {
                            dynamic2 = this.predictChunkStatusBeforeSurface(dynamic2, set);
                        }

                        return updateChunkTag(dynamic2, flag, mutableBoolean.booleanValue(), "minecraft:noise".equals(string1), mutableObject.get());
                    });
                }
            )
        );
    }

    private Dynamic<?> predictChunkStatusBeforeSurface(Dynamic<?> data, Set<String> blockPalette) {
        return data.update("Status", dynamic -> {
            String string = dynamic.asString("empty");
            if (STATUS_IS_OR_AFTER_SURFACE.contains(string)) {
                return dynamic;
            } else {
                blockPalette.remove("minecraft:air");
                boolean flag = !blockPalette.isEmpty();
                blockPalette.removeAll(BLOCKS_BEFORE_FEATURE_STATUS);
                boolean flag1 = !blockPalette.isEmpty();
                if (flag1) {
                    return dynamic.createString("liquid_carvers");
                } else if ("noise".equals(string) || flag) {
                    return dynamic.createString("noise");
                } else {
                    return "biomes".equals(string) ? dynamic.createString("structure_references") : dynamic;
                }
            }
        });
    }

    private static Dynamic<?>[] getBiomeContainers(Dynamic<?> data, boolean overworld, int lowestY, MutableBoolean isTallChunk) {
        Dynamic<?>[] dynamics = new Dynamic[overworld ? 24 : 16];
        int[] ints = data.get("Biomes").asIntStreamOpt().result().map(IntStream::toArray).orElse(null);
        if (ints != null && ints.length == 1536) {
            isTallChunk.setValue(true);

            for (int i = 0; i < 24; i++) {
                int i1 = i;
                dynamics[i] = makeBiomeContainer(data, i3 -> getOldBiome(ints, i1 * 64 + i3));
            }
        } else if (ints != null && ints.length == 1024) {
            for (int i = 0; i < 16; i++) {
                int i1 = i - lowestY;
                final int f_i = i;
                dynamics[i1] = makeBiomeContainer(data, i3 -> getOldBiome(ints, f_i * 64 + i3));
            }

            if (overworld) {
                Dynamic<?> dynamic = makeBiomeContainer(data, i3 -> getOldBiome(ints, i3 % 16));
                Dynamic<?> dynamic1 = makeBiomeContainer(data, i3 -> getOldBiome(ints, i3 % 16 + 1008));

                for (int i2 = 0; i2 < 4; i2++) {
                    dynamics[i2] = dynamic;
                }

                for (int i2 = 20; i2 < 24; i2++) {
                    dynamics[i2] = dynamic1;
                }
            }
        } else {
            Arrays.fill(dynamics, makePalettedContainer(data.createList(Stream.of(data.createString("minecraft:plains")))));
        }

        return dynamics;
    }

    private static int getOldBiome(int[] biomes, int index) {
        return biomes[index] & 0xFF;
    }

    private static Dynamic<?> updateChunkTag(
        Dynamic<?> chunkTag,
        boolean overworld,
        boolean isTallChunk,
        boolean isNoiseGenerator,
        Supplier<ChunkProtoTickListFix.@Nullable PoorMansPalettedContainer> paletteSupplier
    ) {
        chunkTag = chunkTag.remove("Biomes");
        if (!overworld) {
            return updateCarvingMasks(chunkTag, 16, 0);
        } else if (isTallChunk) {
            return updateCarvingMasks(chunkTag, 24, 0);
        } else {
            chunkTag = updateHeightmaps(chunkTag);
            chunkTag = addPaddingEntries(chunkTag, "LiquidsToBeTicked");
            chunkTag = addPaddingEntries(chunkTag, "PostProcessing");
            chunkTag = addPaddingEntries(chunkTag, "ToBeTicked");
            chunkTag = updateCarvingMasks(chunkTag, 24, 4);
            chunkTag = chunkTag.update("UpgradeData", ChunkHeightAndBiomeFix::shiftUpgradeData);
            if (!isNoiseGenerator) {
                return chunkTag;
            } else {
                Optional<? extends Dynamic<?>> optional = chunkTag.get("Status").result();
                if (optional.isPresent()) {
                    Dynamic<?> dynamic = (Dynamic<?>)optional.get();
                    String string = dynamic.asString("");
                    if (!"empty".equals(string)) {
                        chunkTag = chunkTag.set(
                            "blending_data",
                            chunkTag.createMap(
                                ImmutableMap.of(chunkTag.createString("old_noise"), chunkTag.createBoolean(STATUS_IS_OR_AFTER_NOISE.contains(string)))
                            )
                        );
                        if (!SharedConstants.DEBUG_DISABLE_BELOW_ZERO_RETROGENERATION) {
                            ChunkProtoTickListFix.PoorMansPalettedContainer poorMansPalettedContainer = paletteSupplier.get();
                            if (poorMansPalettedContainer != null) {
                                BitSet bitSet = new BitSet(256);
                                boolean flag = string.equals("noise");

                                for (int i = 0; i < 16; i++) {
                                    for (int i1 = 0; i1 < 16; i1++) {
                                        Dynamic<?> dynamic1 = poorMansPalettedContainer.get(i1, 0, i);
                                        boolean flag1 = dynamic1 != null && "minecraft:bedrock".equals(dynamic1.get("Name").asString(""));
                                        boolean flag2 = dynamic1 != null && "minecraft:air".equals(dynamic1.get("Name").asString(""));
                                        if (flag2) {
                                            bitSet.set(i * 16 + i1);
                                        }

                                        flag |= flag1;
                                    }
                                }

                                if (flag && bitSet.cardinality() != bitSet.size()) {
                                    Dynamic<?> dynamic2 = "full".equals(string) ? chunkTag.createString("heightmaps") : dynamic;
                                    chunkTag = chunkTag.set(
                                        "below_zero_retrogen",
                                        chunkTag.createMap(
                                            ImmutableMap.of(
                                                chunkTag.createString("target_status"),
                                                dynamic2,
                                                chunkTag.createString("missing_bedrock"),
                                                chunkTag.createLongList(LongStream.of(bitSet.toLongArray()))
                                            )
                                        )
                                    );
                                    chunkTag = chunkTag.set("Status", chunkTag.createString("empty"));
                                }

                                chunkTag = chunkTag.set("isLightOn", chunkTag.createBoolean(false));
                            }
                        }
                    }
                }

                return chunkTag;
            }
        }
    }

    private static <T> Dynamic<T> shiftUpgradeData(Dynamic<T> data) {
        return data.update("Indices", dynamic -> {
            Map<Dynamic<?>, Dynamic<?>> map = new HashMap<>();
            dynamic.getMapValues().ifSuccess(map1 -> map1.forEach((dynamic1, dynamic2) -> {
                try {
                    dynamic1.asString().result().map(Integer::parseInt).ifPresent(integer -> {
                        int i = integer - -4;
                        map.put(dynamic1.createString(Integer.toString(i)), (Dynamic<?>)dynamic2);
                    });
                } catch (NumberFormatException var4) {
                }
            }));
            return dynamic.createMap(map);
        });
    }

    private static Dynamic<?> updateCarvingMasks(Dynamic<?> data, int sectionCount, int offset) {
        Dynamic<?> dynamic = data.get("CarvingMasks").orElseEmptyMap();
        dynamic = dynamic.updateMapValues(pair -> {
            long[] longs = BitSet.valueOf(pair.getSecond().asByteBuffer().array()).toLongArray();
            long[] longs1 = new long[64 * sectionCount];
            System.arraycopy(longs, 0, longs1, 64 * offset, longs.length);
            return Pair.of(pair.getFirst(), data.createLongList(LongStream.of(longs1)));
        });
        return data.set("CarvingMasks", dynamic);
    }

    private static Dynamic<?> addPaddingEntries(Dynamic<?> data, String key) {
        List<Dynamic<?>> list = data.get(key).orElseEmptyList().asStream().collect(Collectors.toCollection(ArrayList::new));
        if (list.size() == 24) {
            return data;
        } else {
            Dynamic<?> dynamic = data.emptyList();

            for (int i = 0; i < 4; i++) {
                list.add(0, dynamic);
                list.add(dynamic);
            }

            return data.set(key, data.createList(list.stream()));
        }
    }

    private static Dynamic<?> updateHeightmaps(Dynamic<?> data) {
        return data.update("Heightmaps", dynamic -> {
            for (String string : HEIGHTMAP_TYPES) {
                dynamic = dynamic.update(string, ChunkHeightAndBiomeFix::getFixedHeightmap);
            }

            return dynamic;
        });
    }

    private static Dynamic<?> getFixedHeightmap(Dynamic<?> dynamic) {
        return dynamic.createLongList(dynamic.asLongStream().map(l -> {
            long l1 = 0L;

            for (int i = 0; i + 9 <= 64; i += 9) {
                long l2 = l >> i & 511L;
                long l3;
                if (l2 == 0L) {
                    l3 = 0L;
                } else {
                    l3 = Math.min(l2 + 64L, 511L);
                }

                l1 |= l3 << i;
            }

            return l1;
        }));
    }

    private static Dynamic<?> makeBiomeContainer(Dynamic<?> data, Int2IntFunction oldBiomeGetter) {
        Int2IntMap map = new Int2IntLinkedOpenHashMap();

        for (int i = 0; i < 64; i++) {
            int i1 = oldBiomeGetter.applyAsInt(i);
            if (!map.containsKey(i1)) {
                map.put(i1, map.size());
            }
        }

        Dynamic<?> dynamic = data.createList(
            map.keySet().stream().map(integer -> data.createString(BIOMES_BY_ID.getOrDefault(integer.intValue(), "minecraft:plains")))
        );
        int i1 = ceillog2(map.size());
        if (i1 == 0) {
            return makePalettedContainer(dynamic);
        } else {
            int i2 = 64 / i1;
            int i3 = (64 + i2 - 1) / i2;
            long[] longs = new long[i3];
            int i4 = 0;
            int i5 = 0;

            for (int i6 = 0; i6 < 64; i6++) {
                int i7 = oldBiomeGetter.applyAsInt(i6);
                longs[i4] |= (long)map.get(i7) << i5;
                i5 += i1;
                if (i5 + i1 > 64) {
                    i4++;
                    i5 = 0;
                }
            }

            Dynamic<?> dynamic1 = data.createLongList(Arrays.stream(longs));
            return makePalettedContainer(dynamic, dynamic1);
        }
    }

    private static Dynamic<?> makePalettedContainer(Dynamic<?> palette) {
        return palette.createMap(ImmutableMap.of(palette.createString("palette"), palette));
    }

    private static Dynamic<?> makePalettedContainer(Dynamic<?> palette, Dynamic<?> blockStates) {
        return palette.createMap(ImmutableMap.of(palette.createString("palette"), palette, palette.createString("data"), blockStates));
    }

    private static Dynamic<?> makeOptimizedPalettedContainer(Dynamic<?> palette, Dynamic<?> blockStates) {
        List<Dynamic<?>> list = palette.asStream().collect(Collectors.toCollection(ArrayList::new));
        if (list.size() == 1) {
            return makePalettedContainer(palette);
        } else {
            palette = padPaletteEntries(palette, blockStates, list);
            return makePalettedContainer(palette, blockStates);
        }
    }

    private static Dynamic<?> padPaletteEntries(Dynamic<?> palette, Dynamic<?> blockStates, List<Dynamic<?>> paletteEntries) {
        long l = blockStates.asLongStream().count() * 64L;
        long l1 = l / 4096L;
        int size = paletteEntries.size();
        int i = ceillog2(size);
        if (l1 <= i) {
            return palette;
        } else {
            Dynamic<?> dynamic = palette.createMap(ImmutableMap.of(palette.createString("Name"), palette.createString("minecraft:air")));
            int i1 = (1 << (int)(l1 - 1L)) + 1;
            int i2 = i1 - size;

            for (int i3 = 0; i3 < i2; i3++) {
                paletteEntries.add(dynamic);
            }

            return palette.createList(paletteEntries.stream());
        }
    }

    public static int ceillog2(int value) {
        return value == 0 ? 0 : (int)Math.ceil(Math.log(value) / Math.log(2.0));
    }

    static {
        BIOMES_BY_ID.put(0, "minecraft:ocean");
        BIOMES_BY_ID.put(1, "minecraft:plains");
        BIOMES_BY_ID.put(2, "minecraft:desert");
        BIOMES_BY_ID.put(3, "minecraft:mountains");
        BIOMES_BY_ID.put(4, "minecraft:forest");
        BIOMES_BY_ID.put(5, "minecraft:taiga");
        BIOMES_BY_ID.put(6, "minecraft:swamp");
        BIOMES_BY_ID.put(7, "minecraft:river");
        BIOMES_BY_ID.put(8, "minecraft:nether_wastes");
        BIOMES_BY_ID.put(9, "minecraft:the_end");
        BIOMES_BY_ID.put(10, "minecraft:frozen_ocean");
        BIOMES_BY_ID.put(11, "minecraft:frozen_river");
        BIOMES_BY_ID.put(12, "minecraft:snowy_tundra");
        BIOMES_BY_ID.put(13, "minecraft:snowy_mountains");
        BIOMES_BY_ID.put(14, "minecraft:mushroom_fields");
        BIOMES_BY_ID.put(15, "minecraft:mushroom_field_shore");
        BIOMES_BY_ID.put(16, "minecraft:beach");
        BIOMES_BY_ID.put(17, "minecraft:desert_hills");
        BIOMES_BY_ID.put(18, "minecraft:wooded_hills");
        BIOMES_BY_ID.put(19, "minecraft:taiga_hills");
        BIOMES_BY_ID.put(20, "minecraft:mountain_edge");
        BIOMES_BY_ID.put(21, "minecraft:jungle");
        BIOMES_BY_ID.put(22, "minecraft:jungle_hills");
        BIOMES_BY_ID.put(23, "minecraft:jungle_edge");
        BIOMES_BY_ID.put(24, "minecraft:deep_ocean");
        BIOMES_BY_ID.put(25, "minecraft:stone_shore");
        BIOMES_BY_ID.put(26, "minecraft:snowy_beach");
        BIOMES_BY_ID.put(27, "minecraft:birch_forest");
        BIOMES_BY_ID.put(28, "minecraft:birch_forest_hills");
        BIOMES_BY_ID.put(29, "minecraft:dark_forest");
        BIOMES_BY_ID.put(30, "minecraft:snowy_taiga");
        BIOMES_BY_ID.put(31, "minecraft:snowy_taiga_hills");
        BIOMES_BY_ID.put(32, "minecraft:giant_tree_taiga");
        BIOMES_BY_ID.put(33, "minecraft:giant_tree_taiga_hills");
        BIOMES_BY_ID.put(34, "minecraft:wooded_mountains");
        BIOMES_BY_ID.put(35, "minecraft:savanna");
        BIOMES_BY_ID.put(36, "minecraft:savanna_plateau");
        BIOMES_BY_ID.put(37, "minecraft:badlands");
        BIOMES_BY_ID.put(38, "minecraft:wooded_badlands_plateau");
        BIOMES_BY_ID.put(39, "minecraft:badlands_plateau");
        BIOMES_BY_ID.put(40, "minecraft:small_end_islands");
        BIOMES_BY_ID.put(41, "minecraft:end_midlands");
        BIOMES_BY_ID.put(42, "minecraft:end_highlands");
        BIOMES_BY_ID.put(43, "minecraft:end_barrens");
        BIOMES_BY_ID.put(44, "minecraft:warm_ocean");
        BIOMES_BY_ID.put(45, "minecraft:lukewarm_ocean");
        BIOMES_BY_ID.put(46, "minecraft:cold_ocean");
        BIOMES_BY_ID.put(47, "minecraft:deep_warm_ocean");
        BIOMES_BY_ID.put(48, "minecraft:deep_lukewarm_ocean");
        BIOMES_BY_ID.put(49, "minecraft:deep_cold_ocean");
        BIOMES_BY_ID.put(50, "minecraft:deep_frozen_ocean");
        BIOMES_BY_ID.put(127, "minecraft:the_void");
        BIOMES_BY_ID.put(129, "minecraft:sunflower_plains");
        BIOMES_BY_ID.put(130, "minecraft:desert_lakes");
        BIOMES_BY_ID.put(131, "minecraft:gravelly_mountains");
        BIOMES_BY_ID.put(132, "minecraft:flower_forest");
        BIOMES_BY_ID.put(133, "minecraft:taiga_mountains");
        BIOMES_BY_ID.put(134, "minecraft:swamp_hills");
        BIOMES_BY_ID.put(140, "minecraft:ice_spikes");
        BIOMES_BY_ID.put(149, "minecraft:modified_jungle");
        BIOMES_BY_ID.put(151, "minecraft:modified_jungle_edge");
        BIOMES_BY_ID.put(155, "minecraft:tall_birch_forest");
        BIOMES_BY_ID.put(156, "minecraft:tall_birch_hills");
        BIOMES_BY_ID.put(157, "minecraft:dark_forest_hills");
        BIOMES_BY_ID.put(158, "minecraft:snowy_taiga_mountains");
        BIOMES_BY_ID.put(160, "minecraft:giant_spruce_taiga");
        BIOMES_BY_ID.put(161, "minecraft:giant_spruce_taiga_hills");
        BIOMES_BY_ID.put(162, "minecraft:modified_gravelly_mountains");
        BIOMES_BY_ID.put(163, "minecraft:shattered_savanna");
        BIOMES_BY_ID.put(164, "minecraft:shattered_savanna_plateau");
        BIOMES_BY_ID.put(165, "minecraft:eroded_badlands");
        BIOMES_BY_ID.put(166, "minecraft:modified_wooded_badlands_plateau");
        BIOMES_BY_ID.put(167, "minecraft:modified_badlands_plateau");
        BIOMES_BY_ID.put(168, "minecraft:bamboo_jungle");
        BIOMES_BY_ID.put(169, "minecraft:bamboo_jungle_hills");
        BIOMES_BY_ID.put(170, "minecraft:soul_sand_valley");
        BIOMES_BY_ID.put(171, "minecraft:crimson_forest");
        BIOMES_BY_ID.put(172, "minecraft:warped_forest");
        BIOMES_BY_ID.put(173, "minecraft:basalt_deltas");
        BIOMES_BY_ID.put(174, "minecraft:dripstone_caves");
        BIOMES_BY_ID.put(175, "minecraft:lush_caves");
        BIOMES_BY_ID.put(177, "minecraft:meadow");
        BIOMES_BY_ID.put(178, "minecraft:grove");
        BIOMES_BY_ID.put(179, "minecraft:snowy_slopes");
        BIOMES_BY_ID.put(180, "minecraft:snowcapped_peaks");
        BIOMES_BY_ID.put(181, "minecraft:lofty_peaks");
        BIOMES_BY_ID.put(182, "minecraft:stony_peaks");
    }
}
