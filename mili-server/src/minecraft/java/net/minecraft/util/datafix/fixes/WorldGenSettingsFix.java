package net.minecraft.util.datafix.fixes;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.ImmutableMap.Builder;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicLike;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.OptionalDynamic;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableInt;

public class WorldGenSettingsFix extends DataFix {
    private static final String VILLAGE = "minecraft:village";
    private static final String DESERT_PYRAMID = "minecraft:desert_pyramid";
    private static final String IGLOO = "minecraft:igloo";
    private static final String JUNGLE_TEMPLE = "minecraft:jungle_pyramid";
    private static final String SWAMP_HUT = "minecraft:swamp_hut";
    private static final String PILLAGER_OUTPOST = "minecraft:pillager_outpost";
    private static final String END_CITY = "minecraft:endcity";
    private static final String WOODLAND_MANSION = "minecraft:mansion";
    private static final String OCEAN_MONUMENT = "minecraft:monument";
    private static final ImmutableMap<String, WorldGenSettingsFix.StructureFeatureConfiguration> DEFAULTS = ImmutableMap.<String, WorldGenSettingsFix.StructureFeatureConfiguration>builder()
        .put("minecraft:village", new WorldGenSettingsFix.StructureFeatureConfiguration(32, 8, 10387312))
        .put("minecraft:desert_pyramid", new WorldGenSettingsFix.StructureFeatureConfiguration(32, 8, 14357617))
        .put("minecraft:igloo", new WorldGenSettingsFix.StructureFeatureConfiguration(32, 8, 14357618))
        .put("minecraft:jungle_pyramid", new WorldGenSettingsFix.StructureFeatureConfiguration(32, 8, 14357619))
        .put("minecraft:swamp_hut", new WorldGenSettingsFix.StructureFeatureConfiguration(32, 8, 14357620))
        .put("minecraft:pillager_outpost", new WorldGenSettingsFix.StructureFeatureConfiguration(32, 8, 165745296))
        .put("minecraft:monument", new WorldGenSettingsFix.StructureFeatureConfiguration(32, 5, 10387313))
        .put("minecraft:endcity", new WorldGenSettingsFix.StructureFeatureConfiguration(20, 11, 10387313))
        .put("minecraft:mansion", new WorldGenSettingsFix.StructureFeatureConfiguration(80, 20, 10387319))
        .build();

    public WorldGenSettingsFix(Schema outputSchema) {
        super(outputSchema, true);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped(
            "WorldGenSettings building",
            this.getInputSchema().getType(References.WORLD_GEN_SETTINGS),
            typed -> typed.update(DSL.remainderFinder(), WorldGenSettingsFix::fix)
        );
    }

    private static <T> Dynamic<T> noise(long seed, DynamicLike<T> data, Dynamic<T> settings, Dynamic<T> biomeNoise) {
        return data.createMap(
            ImmutableMap.of(
                data.createString("type"),
                data.createString("minecraft:noise"),
                data.createString("biome_source"),
                biomeNoise,
                data.createString("seed"),
                data.createLong(seed),
                data.createString("settings"),
                settings
            )
        );
    }

    private static <T> Dynamic<T> vanillaBiomeSource(Dynamic<T> data, long seed, boolean legacyBiomeInitLayer, boolean largeBiomes) {
        Builder<Dynamic<T>, Dynamic<T>> builder = ImmutableMap.<Dynamic<T>, Dynamic<T>>builder()
            .put(data.createString("type"), data.createString("minecraft:vanilla_layered"))
            .put(data.createString("seed"), data.createLong(seed))
            .put(data.createString("large_biomes"), data.createBoolean(largeBiomes));
        if (legacyBiomeInitLayer) {
            builder.put(data.createString("legacy_biome_init_layer"), data.createBoolean(legacyBiomeInitLayer));
        }

        return data.createMap(builder.build());
    }

    private static <T> Dynamic<T> fix(Dynamic<T> data) {
        DynamicOps<T> ops = data.getOps();
        long _long = data.get("RandomSeed").asLong(0L);
        Optional<String> optional = data.get("generatorName").asString().map(string1 -> string1.toLowerCase(Locale.ROOT)).result();
        Optional<String> optional1 = data.get("legacy_custom_options")
            .asString()
            .result()
            .map(Optional::of)
            .orElseGet(() -> optional.equals(Optional.of("customized")) ? data.get("generatorOptions").asString().result() : Optional.empty());
        boolean flag = false;
        Dynamic<T> dynamic;
        if (optional.equals(Optional.of("customized"))) {
            dynamic = defaultOverworld(data, _long);
        } else if (optional.isEmpty()) {
            dynamic = defaultOverworld(data, _long);
        } else {
            String _boolean = optional.get();
            switch (_boolean) {
                case "flat":
                    OptionalDynamic<T> optionalDynamic = data.get("generatorOptions");
                    Map<Dynamic<T>, Dynamic<T>> map = fixFlatStructures(ops, optionalDynamic);
                    dynamic = data.createMap(
                        ImmutableMap.of(
                            data.createString("type"),
                            data.createString("minecraft:flat"),
                            data.createString("settings"),
                            data.createMap(
                                ImmutableMap.of(
                                    data.createString("structures"),
                                    data.createMap(map),
                                    data.createString("layers"),
                                    optionalDynamic.get("layers")
                                        .result()
                                        .orElseGet(
                                            () -> data.createList(
                                                Stream.of(
                                                    data.createMap(
                                                        ImmutableMap.of(
                                                            data.createString("height"),
                                                            data.createInt(1),
                                                            data.createString("block"),
                                                            data.createString("minecraft:bedrock")
                                                        )
                                                    ),
                                                    data.createMap(
                                                        ImmutableMap.of(
                                                            data.createString("height"),
                                                            data.createInt(2),
                                                            data.createString("block"),
                                                            data.createString("minecraft:dirt")
                                                        )
                                                    ),
                                                    data.createMap(
                                                        ImmutableMap.of(
                                                            data.createString("height"),
                                                            data.createInt(1),
                                                            data.createString("block"),
                                                            data.createString("minecraft:grass_block")
                                                        )
                                                    )
                                                )
                                            )
                                        ),
                                    data.createString("biome"),
                                    data.createString(optionalDynamic.get("biome").asString("minecraft:plains"))
                                )
                            )
                        )
                    );
                    break;
                case "debug_all_block_states":
                    dynamic = data.createMap(ImmutableMap.of(data.createString("type"), data.createString("minecraft:debug")));
                    break;
                case "buffet":
                    OptionalDynamic<T> optionalDynamic1 = data.get("generatorOptions");
                    OptionalDynamic<?> optionalDynamic2 = optionalDynamic1.get("chunk_generator");
                    Optional<String> optional2 = optionalDynamic2.get("type").asString().result();
                    Dynamic<T> dynamic1;
                    if (Objects.equals(optional2, Optional.of("minecraft:caves"))) {
                        dynamic1 = data.createString("minecraft:caves");
                        flag = true;
                    } else if (Objects.equals(optional2, Optional.of("minecraft:floating_islands"))) {
                        dynamic1 = data.createString("minecraft:floating_islands");
                    } else {
                        dynamic1 = data.createString("minecraft:overworld");
                    }

                    Dynamic<T> dynamic2 = optionalDynamic1.get("biome_source")
                        .result()
                        .orElseGet(() -> data.createMap(ImmutableMap.of(data.createString("type"), data.createString("minecraft:fixed"))));
                    Dynamic<T> dynamic3;
                    if (dynamic2.get("type").asString().result().equals(Optional.of("minecraft:fixed"))) {
                        String string = dynamic2.get("options")
                            .get("biomes")
                            .asStream()
                            .findFirst()
                            .flatMap(dynamic4 -> dynamic4.asString().result())
                            .orElse("minecraft:ocean");
                        dynamic3 = dynamic2.remove("options").set("biome", data.createString(string));
                    } else {
                        dynamic3 = dynamic2;
                    }

                    dynamic = noise(_long, data, dynamic1, dynamic3);
                    break;
                default:
                    boolean flag1 = optional.get().equals("default");
                    boolean flag2 = optional.get().equals("default_1_1") || flag1 && data.get("generatorVersion").asInt(0) == 0;
                    boolean flag3 = optional.get().equals("amplified");
                    boolean flag4 = optional.get().equals("largebiomes");
                    dynamic = noise(
                        _long, data, data.createString(flag3 ? "minecraft:amplified" : "minecraft:overworld"), vanillaBiomeSource(data, _long, flag2, flag4)
                    );
            }
        }

        boolean _boolean = data.get("MapFeatures").asBoolean(true);
        boolean _boolean1 = data.get("BonusChest").asBoolean(false);
        Builder<T, T> builder = ImmutableMap.builder();
        builder.put(ops.createString("seed"), ops.createLong(_long));
        builder.put(ops.createString("generate_features"), ops.createBoolean(_boolean));
        builder.put(ops.createString("bonus_chest"), ops.createBoolean(_boolean1));
        builder.put(ops.createString("dimensions"), vanillaLevels(data, _long, dynamic, flag));
        optional1.ifPresent(string1 -> builder.put(ops.createString("legacy_custom_options"), ops.createString(string1)));
        return new Dynamic<>(ops, ops.createMap(builder.build()));
    }

    protected static <T> Dynamic<T> defaultOverworld(Dynamic<T> data, long seed) {
        return noise(seed, data, data.createString("minecraft:overworld"), vanillaBiomeSource(data, seed, false, false));
    }

    protected static <T> T vanillaLevels(Dynamic<T> data, long seed, Dynamic<T> generator, boolean caves) {
        DynamicOps<T> ops = data.getOps();
        return ops.createMap(
            ImmutableMap.of(
                ops.createString("minecraft:overworld"),
                ops.createMap(
                    ImmutableMap.of(
                        ops.createString("type"),
                        ops.createString("minecraft:overworld" + (caves ? "_caves" : "")),
                        ops.createString("generator"),
                        generator.getValue()
                    )
                ),
                ops.createString("minecraft:the_nether"),
                ops.createMap(
                    ImmutableMap.of(
                        ops.createString("type"),
                        ops.createString("minecraft:the_nether"),
                        ops.createString("generator"),
                        noise(
                                seed,
                                data,
                                data.createString("minecraft:nether"),
                                data.createMap(
                                    ImmutableMap.of(
                                        data.createString("type"),
                                        data.createString("minecraft:multi_noise"),
                                        data.createString("seed"),
                                        data.createLong(seed),
                                        data.createString("preset"),
                                        data.createString("minecraft:nether")
                                    )
                                )
                            )
                            .getValue()
                    )
                ),
                ops.createString("minecraft:the_end"),
                ops.createMap(
                    ImmutableMap.of(
                        ops.createString("type"),
                        ops.createString("minecraft:the_end"),
                        ops.createString("generator"),
                        noise(
                                seed,
                                data,
                                data.createString("minecraft:end"),
                                data.createMap(
                                    ImmutableMap.of(
                                        data.createString("type"), data.createString("minecraft:the_end"), data.createString("seed"), data.createLong(seed)
                                    )
                                )
                            )
                            .getValue()
                    )
                )
            )
        );
    }

    private static <T> Map<Dynamic<T>, Dynamic<T>> fixFlatStructures(DynamicOps<T> ops, OptionalDynamic<T> generatorOptions) {
        MutableInt mutableInt = new MutableInt(32);
        MutableInt mutableInt1 = new MutableInt(3);
        MutableInt mutableInt2 = new MutableInt(128);
        MutableBoolean mutableBoolean = new MutableBoolean(false);
        Map<String, WorldGenSettingsFix.StructureFeatureConfiguration> map = Maps.newHashMap();
        if (generatorOptions.result().isEmpty()) {
            mutableBoolean.setTrue();
            map.put("minecraft:village", DEFAULTS.get("minecraft:village"));
        }

        generatorOptions.get("structures")
            .flatMap(Dynamic::getMapValues)
            .ifSuccess(
                map1 -> map1.forEach(
                    (dynamic, dynamic1) -> dynamic1.getMapValues()
                        .result()
                        .ifPresent(
                            map2 -> map2.forEach(
                                (dynamic2, dynamic3) -> {
                                    String string = dynamic.asString("");
                                    String string1 = dynamic2.asString("");
                                    String string2 = dynamic3.asString("");
                                    if ("stronghold".equals(string)) {
                                        mutableBoolean.setTrue();
                                        switch (string1) {
                                            case "distance":
                                                mutableInt.setValue(getInt(string2, mutableInt.intValue(), 1));
                                                return;
                                            case "spread":
                                                mutableInt1.setValue(getInt(string2, mutableInt1.intValue(), 1));
                                                return;
                                            case "count":
                                                mutableInt2.setValue(getInt(string2, mutableInt2.intValue(), 1));
                                                return;
                                        }
                                    } else {
                                        switch (string1) {
                                            case "distance":
                                                switch (string) {
                                                    case "village":
                                                        setSpacing(map, "minecraft:village", string2, 9);
                                                        return;
                                                    case "biome_1":
                                                        setSpacing(map, "minecraft:desert_pyramid", string2, 9);
                                                        setSpacing(map, "minecraft:igloo", string2, 9);
                                                        setSpacing(map, "minecraft:jungle_pyramid", string2, 9);
                                                        setSpacing(map, "minecraft:swamp_hut", string2, 9);
                                                        setSpacing(map, "minecraft:pillager_outpost", string2, 9);
                                                        return;
                                                    case "endcity":
                                                        setSpacing(map, "minecraft:endcity", string2, 1);
                                                        return;
                                                    case "mansion":
                                                        setSpacing(map, "minecraft:mansion", string2, 1);
                                                        return;
                                                    default:
                                                        return;
                                                }
                                            case "separation":
                                                if ("oceanmonument".equals(string)) {
                                                    WorldGenSettingsFix.StructureFeatureConfiguration structureFeatureConfiguration = map.getOrDefault(
                                                        "minecraft:monument", DEFAULTS.get("minecraft:monument")
                                                    );
                                                    int _int = getInt(string2, structureFeatureConfiguration.separation, 1);
                                                    map.put(
                                                        "minecraft:monument",
                                                        new WorldGenSettingsFix.StructureFeatureConfiguration(
                                                            _int, structureFeatureConfiguration.separation, structureFeatureConfiguration.salt
                                                        )
                                                    );
                                                }

                                                return;
                                            case "spacing":
                                                if ("oceanmonument".equals(string)) {
                                                    setSpacing(map, "minecraft:monument", string2, 1);
                                                }

                                                return;
                                        }
                                    }
                                }
                            )
                        )
                )
            );
        Builder<Dynamic<T>, Dynamic<T>> builder = ImmutableMap.builder();
        builder.put(
            generatorOptions.createString("structures"),
            generatorOptions.createMap(
                map.entrySet()
                    .stream()
                    .collect(Collectors.toMap(entry -> generatorOptions.createString(entry.getKey()), entry -> entry.getValue().serialize(ops)))
            )
        );
        if (mutableBoolean.isTrue()) {
            builder.put(
                generatorOptions.createString("stronghold"),
                generatorOptions.createMap(
                    ImmutableMap.of(
                        generatorOptions.createString("distance"),
                        generatorOptions.createInt(mutableInt.intValue()),
                        generatorOptions.createString("spread"),
                        generatorOptions.createInt(mutableInt1.intValue()),
                        generatorOptions.createString("count"),
                        generatorOptions.createInt(mutableInt2.intValue())
                    )
                )
            );
        }

        return builder.build();
    }

    private static int getInt(String string, int defaultValue) {
        return NumberUtils.toInt(string, defaultValue);
    }

    private static int getInt(String string, int defaultValue, int minValue) {
        return Math.max(minValue, getInt(string, defaultValue));
    }

    private static void setSpacing(Map<String, WorldGenSettingsFix.StructureFeatureConfiguration> map, String structure, String spacing, int minValue) {
        WorldGenSettingsFix.StructureFeatureConfiguration structureFeatureConfiguration = map.getOrDefault(structure, DEFAULTS.get(structure));
        int _int = getInt(spacing, structureFeatureConfiguration.spacing, minValue);
        map.put(
            structure,
            new WorldGenSettingsFix.StructureFeatureConfiguration(_int, structureFeatureConfiguration.separation, structureFeatureConfiguration.salt)
        );
    }

    static final class StructureFeatureConfiguration {
        public static final Codec<WorldGenSettingsFix.StructureFeatureConfiguration> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Codec.INT.fieldOf("spacing").forGetter(config -> config.spacing),
                    Codec.INT.fieldOf("separation").forGetter(config -> config.separation),
                    Codec.INT.fieldOf("salt").forGetter(config -> config.salt)
                )
                .apply(instance, WorldGenSettingsFix.StructureFeatureConfiguration::new)
        );
        final int spacing;
        final int separation;
        final int salt;

        public StructureFeatureConfiguration(int spacing, int separation, int salt) {
            this.spacing = spacing;
            this.separation = separation;
            this.salt = salt;
        }

        public <T> Dynamic<T> serialize(DynamicOps<T> ops) {
            return new Dynamic<>(ops, CODEC.encodeStart(ops, this).result().orElse(ops.emptyMap()));
        }
    }
}
