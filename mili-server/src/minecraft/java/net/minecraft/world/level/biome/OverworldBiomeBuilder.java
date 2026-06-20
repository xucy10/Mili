package net.minecraft.world.level.biome;

import com.mojang.datafixers.util.Pair;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.NoiseData;
import net.minecraft.data.worldgen.TerrainProvider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.BoundedFloatFunction;
import net.minecraft.util.CubicSpline;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseRouterData;

public final class OverworldBiomeBuilder {
    private static final float VALLEY_SIZE = 0.05F;
    private static final float LOW_START = 0.26666668F;
    public static final float HIGH_START = 0.4F;
    private static final float HIGH_END = 0.93333334F;
    private static final float PEAK_SIZE = 0.1F;
    public static final float PEAK_START = 0.56666666F;
    private static final float PEAK_END = 0.7666667F;
    public static final float NEAR_INLAND_START = -0.11F;
    public static final float MID_INLAND_START = 0.03F;
    public static final float FAR_INLAND_START = 0.3F;
    public static final float EROSION_INDEX_1_START = -0.78F;
    public static final float EROSION_INDEX_2_START = -0.375F;
    private static final float EROSION_DEEP_DARK_DRYNESS_THRESHOLD = -0.225F;
    private static final float DEPTH_DEEP_DARK_DRYNESS_THRESHOLD = 0.9F;
    private final Climate.Parameter FULL_RANGE = Climate.Parameter.span(-1.0F, 1.0F);
    private final Climate.Parameter[] temperatures = new Climate.Parameter[]{
        Climate.Parameter.span(-1.0F, -0.45F),
        Climate.Parameter.span(-0.45F, -0.15F),
        Climate.Parameter.span(-0.15F, 0.2F),
        Climate.Parameter.span(0.2F, 0.55F),
        Climate.Parameter.span(0.55F, 1.0F)
    };
    private final Climate.Parameter[] humidities = new Climate.Parameter[]{
        Climate.Parameter.span(-1.0F, -0.35F),
        Climate.Parameter.span(-0.35F, -0.1F),
        Climate.Parameter.span(-0.1F, 0.1F),
        Climate.Parameter.span(0.1F, 0.3F),
        Climate.Parameter.span(0.3F, 1.0F)
    };
    private final Climate.Parameter[] erosions = new Climate.Parameter[]{
        Climate.Parameter.span(-1.0F, -0.78F),
        Climate.Parameter.span(-0.78F, -0.375F),
        Climate.Parameter.span(-0.375F, -0.2225F),
        Climate.Parameter.span(-0.2225F, 0.05F),
        Climate.Parameter.span(0.05F, 0.45F),
        Climate.Parameter.span(0.45F, 0.55F),
        Climate.Parameter.span(0.55F, 1.0F)
    };
    private final Climate.Parameter FROZEN_RANGE = this.temperatures[0];
    private final Climate.Parameter UNFROZEN_RANGE = Climate.Parameter.span(this.temperatures[1], this.temperatures[4]);
    private final Climate.Parameter mushroomFieldsContinentalness = Climate.Parameter.span(-1.2F, -1.05F);
    private final Climate.Parameter deepOceanContinentalness = Climate.Parameter.span(-1.05F, -0.455F);
    private final Climate.Parameter oceanContinentalness = Climate.Parameter.span(-0.455F, -0.19F);
    private final Climate.Parameter coastContinentalness = Climate.Parameter.span(-0.19F, -0.11F);
    private final Climate.Parameter inlandContinentalness = Climate.Parameter.span(-0.11F, 0.55F);
    private final Climate.Parameter nearInlandContinentalness = Climate.Parameter.span(-0.11F, 0.03F);
    private final Climate.Parameter midInlandContinentalness = Climate.Parameter.span(0.03F, 0.3F);
    private final Climate.Parameter farInlandContinentalness = Climate.Parameter.span(0.3F, 1.0F);
    private final ResourceKey<Biome>[][] OCEANS = new ResourceKey[][]{
        {Biomes.DEEP_FROZEN_OCEAN, Biomes.DEEP_COLD_OCEAN, Biomes.DEEP_OCEAN, Biomes.DEEP_LUKEWARM_OCEAN, Biomes.WARM_OCEAN},
        {Biomes.FROZEN_OCEAN, Biomes.COLD_OCEAN, Biomes.OCEAN, Biomes.LUKEWARM_OCEAN, Biomes.WARM_OCEAN}
    };
    private final ResourceKey<Biome>[][] MIDDLE_BIOMES = new ResourceKey[][]{
        {Biomes.SNOWY_PLAINS, Biomes.SNOWY_PLAINS, Biomes.SNOWY_PLAINS, Biomes.SNOWY_TAIGA, Biomes.TAIGA},
        {Biomes.PLAINS, Biomes.PLAINS, Biomes.FOREST, Biomes.TAIGA, Biomes.OLD_GROWTH_SPRUCE_TAIGA},
        {Biomes.FLOWER_FOREST, Biomes.PLAINS, Biomes.FOREST, Biomes.BIRCH_FOREST, Biomes.DARK_FOREST},
        {Biomes.SAVANNA, Biomes.SAVANNA, Biomes.FOREST, Biomes.JUNGLE, Biomes.JUNGLE},
        {Biomes.DESERT, Biomes.DESERT, Biomes.DESERT, Biomes.DESERT, Biomes.DESERT}
    };
    private final ResourceKey<Biome>[][] MIDDLE_BIOMES_VARIANT = new ResourceKey[][]{
        {Biomes.ICE_SPIKES, null, Biomes.SNOWY_TAIGA, null, null},
        {null, null, null, null, Biomes.OLD_GROWTH_PINE_TAIGA},
        {Biomes.SUNFLOWER_PLAINS, null, null, Biomes.OLD_GROWTH_BIRCH_FOREST, null},
        {null, null, Biomes.PLAINS, Biomes.SPARSE_JUNGLE, Biomes.BAMBOO_JUNGLE},
        {null, null, null, null, null}
    };
    private final ResourceKey<Biome>[][] PLATEAU_BIOMES = new ResourceKey[][]{
        {Biomes.SNOWY_PLAINS, Biomes.SNOWY_PLAINS, Biomes.SNOWY_PLAINS, Biomes.SNOWY_TAIGA, Biomes.SNOWY_TAIGA},
        {Biomes.MEADOW, Biomes.MEADOW, Biomes.FOREST, Biomes.TAIGA, Biomes.OLD_GROWTH_SPRUCE_TAIGA},
        {Biomes.MEADOW, Biomes.MEADOW, Biomes.MEADOW, Biomes.MEADOW, Biomes.PALE_GARDEN},
        {Biomes.SAVANNA_PLATEAU, Biomes.SAVANNA_PLATEAU, Biomes.FOREST, Biomes.FOREST, Biomes.JUNGLE},
        {Biomes.BADLANDS, Biomes.BADLANDS, Biomes.BADLANDS, Biomes.WOODED_BADLANDS, Biomes.WOODED_BADLANDS}
    };
    private final ResourceKey<Biome>[][] PLATEAU_BIOMES_VARIANT = new ResourceKey[][]{
        {Biomes.ICE_SPIKES, null, null, null, null},
        {Biomes.CHERRY_GROVE, null, Biomes.MEADOW, Biomes.MEADOW, Biomes.OLD_GROWTH_PINE_TAIGA},
        {Biomes.CHERRY_GROVE, Biomes.CHERRY_GROVE, Biomes.FOREST, Biomes.BIRCH_FOREST, null},
        {null, null, null, null, null},
        {Biomes.ERODED_BADLANDS, Biomes.ERODED_BADLANDS, null, null, null}
    };
    private final ResourceKey<Biome>[][] SHATTERED_BIOMES = new ResourceKey[][]{
        {Biomes.WINDSWEPT_GRAVELLY_HILLS, Biomes.WINDSWEPT_GRAVELLY_HILLS, Biomes.WINDSWEPT_HILLS, Biomes.WINDSWEPT_FOREST, Biomes.WINDSWEPT_FOREST},
        {Biomes.WINDSWEPT_GRAVELLY_HILLS, Biomes.WINDSWEPT_GRAVELLY_HILLS, Biomes.WINDSWEPT_HILLS, Biomes.WINDSWEPT_FOREST, Biomes.WINDSWEPT_FOREST},
        {Biomes.WINDSWEPT_HILLS, Biomes.WINDSWEPT_HILLS, Biomes.WINDSWEPT_HILLS, Biomes.WINDSWEPT_FOREST, Biomes.WINDSWEPT_FOREST},
        {null, null, null, null, null},
        {null, null, null, null, null}
    };

    public List<Climate.ParameterPoint> spawnTarget() {
        Climate.Parameter parameter = Climate.Parameter.point(0.0F);
        float f = 0.16F;
        return List.of(
            new Climate.ParameterPoint(
                this.FULL_RANGE,
                this.FULL_RANGE,
                Climate.Parameter.span(this.inlandContinentalness, this.FULL_RANGE),
                this.FULL_RANGE,
                parameter,
                Climate.Parameter.span(-1.0F, -0.16F),
                0L
            ),
            new Climate.ParameterPoint(
                this.FULL_RANGE,
                this.FULL_RANGE,
                Climate.Parameter.span(this.inlandContinentalness, this.FULL_RANGE),
                this.FULL_RANGE,
                parameter,
                Climate.Parameter.span(0.16F, 1.0F),
                0L
            )
        );
    }

    protected void addBiomes(Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> key) {
        if (SharedConstants.debugGenerateSquareTerrainWithoutNoise) {
            this.addDebugBiomes(key);
        } else {
            this.addOffCoastBiomes(key);
            this.addInlandBiomes(key);
            this.addUndergroundBiomes(key);
        }
    }

    private void addDebugBiomes(Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> key) {
        HolderLookup.Provider provider = new RegistrySetBuilder()
            .add(Registries.DENSITY_FUNCTION, NoiseRouterData::bootstrap)
            .add(Registries.NOISE, NoiseData::bootstrap)
            .build(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        HolderGetter<DensityFunction> holderGetter = provider.lookupOrThrow(Registries.DENSITY_FUNCTION);
        DensityFunctions.Spline.Coordinate coordinate = new DensityFunctions.Spline.Coordinate(holderGetter.getOrThrow(NoiseRouterData.CONTINENTS));
        DensityFunctions.Spline.Coordinate coordinate1 = new DensityFunctions.Spline.Coordinate(holderGetter.getOrThrow(NoiseRouterData.EROSION));
        DensityFunctions.Spline.Coordinate coordinate2 = new DensityFunctions.Spline.Coordinate(holderGetter.getOrThrow(NoiseRouterData.RIDGES_FOLDED));
        key.accept(
            Pair.of(
                Climate.parameters(this.FULL_RANGE, this.FULL_RANGE, this.FULL_RANGE, this.FULL_RANGE, Climate.Parameter.point(0.0F), this.FULL_RANGE, 0.01F),
                Biomes.PLAINS
            )
        );
        if (TerrainProvider.buildErosionOffsetSpline(
            coordinate1, coordinate2, -0.15F, 0.0F, 0.0F, 0.1F, 0.0F, -0.03F, false, false, BoundedFloatFunction.IDENTITY
        ) instanceof CubicSpline.Multipoint<?, ?> multipoint) {
            ResourceKey<Biome> resourceKey = Biomes.DESERT;

            for (float f : multipoint.locations()) {
                key.accept(
                    Pair.of(
                        Climate.parameters(
                            this.FULL_RANGE, this.FULL_RANGE, this.FULL_RANGE, Climate.Parameter.point(f), Climate.Parameter.point(0.0F), this.FULL_RANGE, 0.0F
                        ),
                        resourceKey
                    )
                );
                resourceKey = resourceKey == Biomes.DESERT ? Biomes.BADLANDS : Biomes.DESERT;
            }
        }

        if (TerrainProvider.overworldOffset(coordinate, coordinate1, coordinate2, false) instanceof CubicSpline.Multipoint<?, ?> multipoint1) {
            for (float f : multipoint1.locations()) {
                key.accept(
                    Pair.of(
                        Climate.parameters(
                            this.FULL_RANGE, this.FULL_RANGE, Climate.Parameter.point(f), this.FULL_RANGE, Climate.Parameter.point(0.0F), this.FULL_RANGE, 0.0F
                        ),
                        Biomes.SNOWY_TAIGA
                    )
                );
            }
        }
    }

    private void addOffCoastBiomes(Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> consumer) {
        this.addSurfaceBiome(
            consumer, this.FULL_RANGE, this.FULL_RANGE, this.mushroomFieldsContinentalness, this.FULL_RANGE, this.FULL_RANGE, 0.0F, Biomes.MUSHROOM_FIELDS
        );

        for (int i = 0; i < this.temperatures.length; i++) {
            Climate.Parameter parameter = this.temperatures[i];
            this.addSurfaceBiome(consumer, parameter, this.FULL_RANGE, this.deepOceanContinentalness, this.FULL_RANGE, this.FULL_RANGE, 0.0F, this.OCEANS[0][i]);
            this.addSurfaceBiome(consumer, parameter, this.FULL_RANGE, this.oceanContinentalness, this.FULL_RANGE, this.FULL_RANGE, 0.0F, this.OCEANS[1][i]);
        }
    }

    private void addInlandBiomes(Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> consumer) {
        this.addMidSlice(consumer, Climate.Parameter.span(-1.0F, -0.93333334F));
        this.addHighSlice(consumer, Climate.Parameter.span(-0.93333334F, -0.7666667F));
        this.addPeaks(consumer, Climate.Parameter.span(-0.7666667F, -0.56666666F));
        this.addHighSlice(consumer, Climate.Parameter.span(-0.56666666F, -0.4F));
        this.addMidSlice(consumer, Climate.Parameter.span(-0.4F, -0.26666668F));
        this.addLowSlice(consumer, Climate.Parameter.span(-0.26666668F, -0.05F));
        this.addValleys(consumer, Climate.Parameter.span(-0.05F, 0.05F));
        this.addLowSlice(consumer, Climate.Parameter.span(0.05F, 0.26666668F));
        this.addMidSlice(consumer, Climate.Parameter.span(0.26666668F, 0.4F));
        this.addHighSlice(consumer, Climate.Parameter.span(0.4F, 0.56666666F));
        this.addPeaks(consumer, Climate.Parameter.span(0.56666666F, 0.7666667F));
        this.addHighSlice(consumer, Climate.Parameter.span(0.7666667F, 0.93333334F));
        this.addMidSlice(consumer, Climate.Parameter.span(0.93333334F, 1.0F));
    }

    private void addPeaks(Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> consumer, Climate.Parameter param) {
        for (int i = 0; i < this.temperatures.length; i++) {
            Climate.Parameter parameter = this.temperatures[i];

            for (int i1 = 0; i1 < this.humidities.length; i1++) {
                Climate.Parameter parameter1 = this.humidities[i1];
                ResourceKey<Biome> resourceKey = this.pickMiddleBiome(i, i1, param);
                ResourceKey<Biome> resourceKey1 = this.pickMiddleBiomeOrBadlandsIfHot(i, i1, param);
                ResourceKey<Biome> resourceKey2 = this.pickMiddleBiomeOrBadlandsIfHotOrSlopeIfCold(i, i1, param);
                ResourceKey<Biome> resourceKey3 = this.pickPlateauBiome(i, i1, param);
                ResourceKey<Biome> resourceKey4 = this.pickShatteredBiome(i, i1, param);
                ResourceKey<Biome> resourceKey5 = this.maybePickWindsweptSavannaBiome(i, i1, param, resourceKey4);
                ResourceKey<Biome> resourceKey6 = this.pickPeakBiome(i, i1, param);
                this.addSurfaceBiome(
                    consumer,
                    parameter,
                    parameter1,
                    Climate.Parameter.span(this.coastContinentalness, this.farInlandContinentalness),
                    this.erosions[0],
                    param,
                    0.0F,
                    resourceKey6
                );
                this.addSurfaceBiome(
                    consumer,
                    parameter,
                    parameter1,
                    Climate.Parameter.span(this.coastContinentalness, this.nearInlandContinentalness),
                    this.erosions[1],
                    param,
                    0.0F,
                    resourceKey2
                );
                this.addSurfaceBiome(
                    consumer,
                    parameter,
                    parameter1,
                    Climate.Parameter.span(this.midInlandContinentalness, this.farInlandContinentalness),
                    this.erosions[1],
                    param,
                    0.0F,
                    resourceKey6
                );
                this.addSurfaceBiome(
                    consumer,
                    parameter,
                    parameter1,
                    Climate.Parameter.span(this.coastContinentalness, this.nearInlandContinentalness),
                    Climate.Parameter.span(this.erosions[2], this.erosions[3]),
                    param,
                    0.0F,
                    resourceKey
                );
                this.addSurfaceBiome(
                    consumer,
                    parameter,
                    parameter1,
                    Climate.Parameter.span(this.midInlandContinentalness, this.farInlandContinentalness),
                    this.erosions[2],
                    param,
                    0.0F,
                    resourceKey3
                );
                this.addSurfaceBiome(consumer, parameter, parameter1, this.midInlandContinentalness, this.erosions[3], param, 0.0F, resourceKey1);
                this.addSurfaceBiome(consumer, parameter, parameter1, this.farInlandContinentalness, this.erosions[3], param, 0.0F, resourceKey3);
                this.addSurfaceBiome(
                    consumer,
                    parameter,
                    parameter1,
                    Climate.Parameter.span(this.coastContinentalness, this.farInlandContinentalness),
                    this.erosions[4],
                    param,
                    0.0F,
                    resourceKey
                );
                this.addSurfaceBiome(
                    consumer,
                    parameter,
                    parameter1,
                    Climate.Parameter.span(this.coastContinentalness, this.nearInlandContinentalness),
                    this.erosions[5],
                    param,
                    0.0F,
                    resourceKey5
                );
                this.addSurfaceBiome(
                    consumer,
                    parameter,
                    parameter1,
                    Climate.Parameter.span(this.midInlandContinentalness, this.farInlandContinentalness),
                    this.erosions[5],
                    param,
                    0.0F,
                    resourceKey4
                );
                this.addSurfaceBiome(
                    consumer,
                    parameter,
                    parameter1,
                    Climate.Parameter.span(this.coastContinentalness, this.farInlandContinentalness),
                    this.erosions[6],
                    param,
                    0.0F,
                    resourceKey
                );
            }
        }
    }

    private void addHighSlice(Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> consumer, Climate.Parameter param) {
        for (int i = 0; i < this.temperatures.length; i++) {
            Climate.Parameter parameter = this.temperatures[i];

            for (int i1 = 0; i1 < this.humidities.length; i1++) {
                Climate.Parameter parameter1 = this.humidities[i1];
                ResourceKey<Biome> resourceKey = this.pickMiddleBiome(i, i1, param);
                ResourceKey<Biome> resourceKey1 = this.pickMiddleBiomeOrBadlandsIfHot(i, i1, param);
                ResourceKey<Biome> resourceKey2 = this.pickMiddleBiomeOrBadlandsIfHotOrSlopeIfCold(i, i1, param);
                ResourceKey<Biome> resourceKey3 = this.pickPlateauBiome(i, i1, param);
                ResourceKey<Biome> resourceKey4 = this.pickShatteredBiome(i, i1, param);
                ResourceKey<Biome> resourceKey5 = this.maybePickWindsweptSavannaBiome(i, i1, param, resourceKey);
                ResourceKey<Biome> resourceKey6 = this.pickSlopeBiome(i, i1, param);
                ResourceKey<Biome> resourceKey7 = this.pickPeakBiome(i, i1, param);
                this.addSurfaceBiome(
                    consumer,
                    parameter,
                    parameter1,
                    this.coastContinentalness,
                    Climate.Parameter.span(this.erosions[0], this.erosions[1]),
                    param,
                    0.0F,
                    resourceKey
                );
                this.addSurfaceBiome(consumer, parameter, parameter1, this.nearInlandContinentalness, this.erosions[0], param, 0.0F, resourceKey6);
                this.addSurfaceBiome(
                    consumer,
                    parameter,
                    parameter1,
                    Climate.Parameter.span(this.midInlandContinentalness, this.farInlandContinentalness),
                    this.erosions[0],
                    param,
                    0.0F,
                    resourceKey7
                );
                this.addSurfaceBiome(consumer, parameter, parameter1, this.nearInlandContinentalness, this.erosions[1], param, 0.0F, resourceKey2);
                this.addSurfaceBiome(
                    consumer,
                    parameter,
                    parameter1,
                    Climate.Parameter.span(this.midInlandContinentalness, this.farInlandContinentalness),
                    this.erosions[1],
                    param,
                    0.0F,
                    resourceKey6
                );
                this.addSurfaceBiome(
                    consumer,
                    parameter,
                    parameter1,
                    Climate.Parameter.span(this.coastContinentalness, this.nearInlandContinentalness),
                    Climate.Parameter.span(this.erosions[2], this.erosions[3]),
                    param,
                    0.0F,
                    resourceKey
                );
                this.addSurfaceBiome(
                    consumer,
                    parameter,
                    parameter1,
                    Climate.Parameter.span(this.midInlandContinentalness, this.farInlandContinentalness),
                    this.erosions[2],
                    param,
                    0.0F,
                    resourceKey3
                );
                this.addSurfaceBiome(consumer, parameter, parameter1, this.midInlandContinentalness, this.erosions[3], param, 0.0F, resourceKey1);
                this.addSurfaceBiome(consumer, parameter, parameter1, this.farInlandContinentalness, this.erosions[3], param, 0.0F, resourceKey3);
                this.addSurfaceBiome(
                    consumer,
                    parameter,
                    parameter1,
                    Climate.Parameter.span(this.coastContinentalness, this.farInlandContinentalness),
                    this.erosions[4],
                    param,
                    0.0F,
                    resourceKey
                );
                this.addSurfaceBiome(
                    consumer,
                    parameter,
                    parameter1,
                    Climate.Parameter.span(this.coastContinentalness, this.nearInlandContinentalness),
                    this.erosions[5],
                    param,
                    0.0F,
                    resourceKey5
                );
                this.addSurfaceBiome(
                    consumer,
                    parameter,
                    parameter1,
                    Climate.Parameter.span(this.midInlandContinentalness, this.farInlandContinentalness),
                    this.erosions[5],
                    param,
                    0.0F,
                    resourceKey4
                );
                this.addSurfaceBiome(
                    consumer,
                    parameter,
                    parameter1,
                    Climate.Parameter.span(this.coastContinentalness, this.farInlandContinentalness),
                    this.erosions[6],
                    param,
                    0.0F,
                    resourceKey
                );
            }
        }
    }

    private void addMidSlice(Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> consumer, Climate.Parameter param) {
        this.addSurfaceBiome(
            consumer,
            this.FULL_RANGE,
            this.FULL_RANGE,
            this.coastContinentalness,
            Climate.Parameter.span(this.erosions[0], this.erosions[2]),
            param,
            0.0F,
            Biomes.STONY_SHORE
        );
        this.addSurfaceBiome(
            consumer,
            Climate.Parameter.span(this.temperatures[1], this.temperatures[2]),
            this.FULL_RANGE,
            Climate.Parameter.span(this.nearInlandContinentalness, this.farInlandContinentalness),
            this.erosions[6],
            param,
            0.0F,
            Biomes.SWAMP
        );
        this.addSurfaceBiome(
            consumer,
            Climate.Parameter.span(this.temperatures[3], this.temperatures[4]),
            this.FULL_RANGE,
            Climate.Parameter.span(this.nearInlandContinentalness, this.farInlandContinentalness),
            this.erosions[6],
            param,
            0.0F,
            Biomes.MANGROVE_SWAMP
        );

        for (int i = 0; i < this.temperatures.length; i++) {
            Climate.Parameter parameter = this.temperatures[i];

            for (int i1 = 0; i1 < this.humidities.length; i1++) {
                Climate.Parameter parameter1 = this.humidities[i1];
                ResourceKey<Biome> resourceKey = this.pickMiddleBiome(i, i1, param);
                ResourceKey<Biome> resourceKey1 = this.pickMiddleBiomeOrBadlandsIfHot(i, i1, param);
                ResourceKey<Biome> resourceKey2 = this.pickMiddleBiomeOrBadlandsIfHotOrSlopeIfCold(i, i1, param);
                ResourceKey<Biome> resourceKey3 = this.pickShatteredBiome(i, i1, param);
                ResourceKey<Biome> resourceKey4 = this.pickPlateauBiome(i, i1, param);
                ResourceKey<Biome> resourceKey5 = this.pickBeachBiome(i, i1);
                ResourceKey<Biome> resourceKey6 = this.maybePickWindsweptSavannaBiome(i, i1, param, resourceKey);
                ResourceKey<Biome> resourceKey7 = this.pickShatteredCoastBiome(i, i1, param);
                ResourceKey<Biome> resourceKey8 = this.pickSlopeBiome(i, i1, param);
                this.addSurfaceBiome(
                    consumer,
                    parameter,
                    parameter1,
                    Climate.Parameter.span(this.nearInlandContinentalness, this.farInlandContinentalness),
                    this.erosions[0],
                    param,
                    0.0F,
                    resourceKey8
                );
                this.addSurfaceBiome(
                    consumer,
                    parameter,
                    parameter1,
                    Climate.Parameter.span(this.nearInlandContinentalness, this.midInlandContinentalness),
                    this.erosions[1],
                    param,
                    0.0F,
                    resourceKey2
                );
                this.addSurfaceBiome(
                    consumer, parameter, parameter1, this.farInlandContinentalness, this.erosions[1], param, 0.0F, i == 0 ? resourceKey8 : resourceKey4
                );
                this.addSurfaceBiome(consumer, parameter, parameter1, this.nearInlandContinentalness, this.erosions[2], param, 0.0F, resourceKey);
                this.addSurfaceBiome(consumer, parameter, parameter1, this.midInlandContinentalness, this.erosions[2], param, 0.0F, resourceKey1);
                this.addSurfaceBiome(consumer, parameter, parameter1, this.farInlandContinentalness, this.erosions[2], param, 0.0F, resourceKey4);
                this.addSurfaceBiome(
                    consumer,
                    parameter,
                    parameter1,
                    Climate.Parameter.span(this.coastContinentalness, this.nearInlandContinentalness),
                    this.erosions[3],
                    param,
                    0.0F,
                    resourceKey
                );
                this.addSurfaceBiome(
                    consumer,
                    parameter,
                    parameter1,
                    Climate.Parameter.span(this.midInlandContinentalness, this.farInlandContinentalness),
                    this.erosions[3],
                    param,
                    0.0F,
                    resourceKey1
                );
                if (param.max() < 0L) {
                    this.addSurfaceBiome(consumer, parameter, parameter1, this.coastContinentalness, this.erosions[4], param, 0.0F, resourceKey5);
                    this.addSurfaceBiome(
                        consumer,
                        parameter,
                        parameter1,
                        Climate.Parameter.span(this.nearInlandContinentalness, this.farInlandContinentalness),
                        this.erosions[4],
                        param,
                        0.0F,
                        resourceKey
                    );
                } else {
                    this.addSurfaceBiome(
                        consumer,
                        parameter,
                        parameter1,
                        Climate.Parameter.span(this.coastContinentalness, this.farInlandContinentalness),
                        this.erosions[4],
                        param,
                        0.0F,
                        resourceKey
                    );
                }

                this.addSurfaceBiome(consumer, parameter, parameter1, this.coastContinentalness, this.erosions[5], param, 0.0F, resourceKey7);
                this.addSurfaceBiome(consumer, parameter, parameter1, this.nearInlandContinentalness, this.erosions[5], param, 0.0F, resourceKey6);
                this.addSurfaceBiome(
                    consumer,
                    parameter,
                    parameter1,
                    Climate.Parameter.span(this.midInlandContinentalness, this.farInlandContinentalness),
                    this.erosions[5],
                    param,
                    0.0F,
                    resourceKey3
                );
                if (param.max() < 0L) {
                    this.addSurfaceBiome(consumer, parameter, parameter1, this.coastContinentalness, this.erosions[6], param, 0.0F, resourceKey5);
                } else {
                    this.addSurfaceBiome(consumer, parameter, parameter1, this.coastContinentalness, this.erosions[6], param, 0.0F, resourceKey);
                }

                if (i == 0) {
                    this.addSurfaceBiome(
                        consumer,
                        parameter,
                        parameter1,
                        Climate.Parameter.span(this.nearInlandContinentalness, this.farInlandContinentalness),
                        this.erosions[6],
                        param,
                        0.0F,
                        resourceKey
                    );
                }
            }
        }
    }

    private void addLowSlice(Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> consumer, Climate.Parameter param) {
        this.addSurfaceBiome(
            consumer,
            this.FULL_RANGE,
            this.FULL_RANGE,
            this.coastContinentalness,
            Climate.Parameter.span(this.erosions[0], this.erosions[2]),
            param,
            0.0F,
            Biomes.STONY_SHORE
        );
        this.addSurfaceBiome(
            consumer,
            Climate.Parameter.span(this.temperatures[1], this.temperatures[2]),
            this.FULL_RANGE,
            Climate.Parameter.span(this.nearInlandContinentalness, this.farInlandContinentalness),
            this.erosions[6],
            param,
            0.0F,
            Biomes.SWAMP
        );
        this.addSurfaceBiome(
            consumer,
            Climate.Parameter.span(this.temperatures[3], this.temperatures[4]),
            this.FULL_RANGE,
            Climate.Parameter.span(this.nearInlandContinentalness, this.farInlandContinentalness),
            this.erosions[6],
            param,
            0.0F,
            Biomes.MANGROVE_SWAMP
        );

        for (int i = 0; i < this.temperatures.length; i++) {
            Climate.Parameter parameter = this.temperatures[i];

            for (int i1 = 0; i1 < this.humidities.length; i1++) {
                Climate.Parameter parameter1 = this.humidities[i1];
                ResourceKey<Biome> resourceKey = this.pickMiddleBiome(i, i1, param);
                ResourceKey<Biome> resourceKey1 = this.pickMiddleBiomeOrBadlandsIfHot(i, i1, param);
                ResourceKey<Biome> resourceKey2 = this.pickMiddleBiomeOrBadlandsIfHotOrSlopeIfCold(i, i1, param);
                ResourceKey<Biome> resourceKey3 = this.pickBeachBiome(i, i1);
                ResourceKey<Biome> resourceKey4 = this.maybePickWindsweptSavannaBiome(i, i1, param, resourceKey);
                ResourceKey<Biome> resourceKey5 = this.pickShatteredCoastBiome(i, i1, param);
                this.addSurfaceBiome(
                    consumer,
                    parameter,
                    parameter1,
                    this.nearInlandContinentalness,
                    Climate.Parameter.span(this.erosions[0], this.erosions[1]),
                    param,
                    0.0F,
                    resourceKey1
                );
                this.addSurfaceBiome(
                    consumer,
                    parameter,
                    parameter1,
                    Climate.Parameter.span(this.midInlandContinentalness, this.farInlandContinentalness),
                    Climate.Parameter.span(this.erosions[0], this.erosions[1]),
                    param,
                    0.0F,
                    resourceKey2
                );
                this.addSurfaceBiome(
                    consumer,
                    parameter,
                    parameter1,
                    this.nearInlandContinentalness,
                    Climate.Parameter.span(this.erosions[2], this.erosions[3]),
                    param,
                    0.0F,
                    resourceKey
                );
                this.addSurfaceBiome(
                    consumer,
                    parameter,
                    parameter1,
                    Climate.Parameter.span(this.midInlandContinentalness, this.farInlandContinentalness),
                    Climate.Parameter.span(this.erosions[2], this.erosions[3]),
                    param,
                    0.0F,
                    resourceKey1
                );
                this.addSurfaceBiome(
                    consumer,
                    parameter,
                    parameter1,
                    this.coastContinentalness,
                    Climate.Parameter.span(this.erosions[3], this.erosions[4]),
                    param,
                    0.0F,
                    resourceKey3
                );
                this.addSurfaceBiome(
                    consumer,
                    parameter,
                    parameter1,
                    Climate.Parameter.span(this.nearInlandContinentalness, this.farInlandContinentalness),
                    this.erosions[4],
                    param,
                    0.0F,
                    resourceKey
                );
                this.addSurfaceBiome(consumer, parameter, parameter1, this.coastContinentalness, this.erosions[5], param, 0.0F, resourceKey5);
                this.addSurfaceBiome(consumer, parameter, parameter1, this.nearInlandContinentalness, this.erosions[5], param, 0.0F, resourceKey4);
                this.addSurfaceBiome(
                    consumer,
                    parameter,
                    parameter1,
                    Climate.Parameter.span(this.midInlandContinentalness, this.farInlandContinentalness),
                    this.erosions[5],
                    param,
                    0.0F,
                    resourceKey
                );
                this.addSurfaceBiome(consumer, parameter, parameter1, this.coastContinentalness, this.erosions[6], param, 0.0F, resourceKey3);
                if (i == 0) {
                    this.addSurfaceBiome(
                        consumer,
                        parameter,
                        parameter1,
                        Climate.Parameter.span(this.nearInlandContinentalness, this.farInlandContinentalness),
                        this.erosions[6],
                        param,
                        0.0F,
                        resourceKey
                    );
                }
            }
        }
    }

    private void addValleys(Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> consumer, Climate.Parameter param) {
        this.addSurfaceBiome(
            consumer,
            this.FROZEN_RANGE,
            this.FULL_RANGE,
            this.coastContinentalness,
            Climate.Parameter.span(this.erosions[0], this.erosions[1]),
            param,
            0.0F,
            param.max() < 0L ? Biomes.STONY_SHORE : Biomes.FROZEN_RIVER
        );
        this.addSurfaceBiome(
            consumer,
            this.UNFROZEN_RANGE,
            this.FULL_RANGE,
            this.coastContinentalness,
            Climate.Parameter.span(this.erosions[0], this.erosions[1]),
            param,
            0.0F,
            param.max() < 0L ? Biomes.STONY_SHORE : Biomes.RIVER
        );
        this.addSurfaceBiome(
            consumer,
            this.FROZEN_RANGE,
            this.FULL_RANGE,
            this.nearInlandContinentalness,
            Climate.Parameter.span(this.erosions[0], this.erosions[1]),
            param,
            0.0F,
            Biomes.FROZEN_RIVER
        );
        this.addSurfaceBiome(
            consumer,
            this.UNFROZEN_RANGE,
            this.FULL_RANGE,
            this.nearInlandContinentalness,
            Climate.Parameter.span(this.erosions[0], this.erosions[1]),
            param,
            0.0F,
            Biomes.RIVER
        );
        this.addSurfaceBiome(
            consumer,
            this.FROZEN_RANGE,
            this.FULL_RANGE,
            Climate.Parameter.span(this.coastContinentalness, this.farInlandContinentalness),
            Climate.Parameter.span(this.erosions[2], this.erosions[5]),
            param,
            0.0F,
            Biomes.FROZEN_RIVER
        );
        this.addSurfaceBiome(
            consumer,
            this.UNFROZEN_RANGE,
            this.FULL_RANGE,
            Climate.Parameter.span(this.coastContinentalness, this.farInlandContinentalness),
            Climate.Parameter.span(this.erosions[2], this.erosions[5]),
            param,
            0.0F,
            Biomes.RIVER
        );
        this.addSurfaceBiome(consumer, this.FROZEN_RANGE, this.FULL_RANGE, this.coastContinentalness, this.erosions[6], param, 0.0F, Biomes.FROZEN_RIVER);
        this.addSurfaceBiome(consumer, this.UNFROZEN_RANGE, this.FULL_RANGE, this.coastContinentalness, this.erosions[6], param, 0.0F, Biomes.RIVER);
        this.addSurfaceBiome(
            consumer,
            Climate.Parameter.span(this.temperatures[1], this.temperatures[2]),
            this.FULL_RANGE,
            Climate.Parameter.span(this.inlandContinentalness, this.farInlandContinentalness),
            this.erosions[6],
            param,
            0.0F,
            Biomes.SWAMP
        );
        this.addSurfaceBiome(
            consumer,
            Climate.Parameter.span(this.temperatures[3], this.temperatures[4]),
            this.FULL_RANGE,
            Climate.Parameter.span(this.inlandContinentalness, this.farInlandContinentalness),
            this.erosions[6],
            param,
            0.0F,
            Biomes.MANGROVE_SWAMP
        );
        this.addSurfaceBiome(
            consumer,
            this.FROZEN_RANGE,
            this.FULL_RANGE,
            Climate.Parameter.span(this.inlandContinentalness, this.farInlandContinentalness),
            this.erosions[6],
            param,
            0.0F,
            Biomes.FROZEN_RIVER
        );

        for (int i = 0; i < this.temperatures.length; i++) {
            Climate.Parameter parameter = this.temperatures[i];

            for (int i1 = 0; i1 < this.humidities.length; i1++) {
                Climate.Parameter parameter1 = this.humidities[i1];
                ResourceKey<Biome> resourceKey = this.pickMiddleBiomeOrBadlandsIfHot(i, i1, param);
                this.addSurfaceBiome(
                    consumer,
                    parameter,
                    parameter1,
                    Climate.Parameter.span(this.midInlandContinentalness, this.farInlandContinentalness),
                    Climate.Parameter.span(this.erosions[0], this.erosions[1]),
                    param,
                    0.0F,
                    resourceKey
                );
            }
        }
    }

    private void addUndergroundBiomes(Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> consume) {
        this.addUndergroundBiome(
            consume, this.FULL_RANGE, this.FULL_RANGE, Climate.Parameter.span(0.8F, 1.0F), this.FULL_RANGE, this.FULL_RANGE, 0.0F, Biomes.DRIPSTONE_CAVES
        );
        this.addUndergroundBiome(
            consume, this.FULL_RANGE, Climate.Parameter.span(0.7F, 1.0F), this.FULL_RANGE, this.FULL_RANGE, this.FULL_RANGE, 0.0F, Biomes.LUSH_CAVES
        );
        this.addBottomBiome(
            consume,
            this.FULL_RANGE,
            this.FULL_RANGE,
            this.FULL_RANGE,
            Climate.Parameter.span(this.erosions[0], this.erosions[1]),
            this.FULL_RANGE,
            0.0F,
            Biomes.DEEP_DARK
        );
    }

    private ResourceKey<Biome> pickMiddleBiome(int temperature, int humidity, Climate.Parameter param) {
        if (param.max() < 0L) {
            return this.MIDDLE_BIOMES[temperature][humidity];
        } else {
            ResourceKey<Biome> resourceKey = this.MIDDLE_BIOMES_VARIANT[temperature][humidity];
            return resourceKey == null ? this.MIDDLE_BIOMES[temperature][humidity] : resourceKey;
        }
    }

    private ResourceKey<Biome> pickMiddleBiomeOrBadlandsIfHot(int temperature, int humidity, Climate.Parameter param) {
        return temperature == 4 ? this.pickBadlandsBiome(humidity, param) : this.pickMiddleBiome(temperature, humidity, param);
    }

    private ResourceKey<Biome> pickMiddleBiomeOrBadlandsIfHotOrSlopeIfCold(int temperature, int humidity, Climate.Parameter param) {
        return temperature == 0 ? this.pickSlopeBiome(temperature, humidity, param) : this.pickMiddleBiomeOrBadlandsIfHot(temperature, humidity, param);
    }

    private ResourceKey<Biome> maybePickWindsweptSavannaBiome(int temperature, int humidity, Climate.Parameter param, ResourceKey<Biome> key) {
        return temperature > 1 && humidity < 4 && param.max() >= 0L ? Biomes.WINDSWEPT_SAVANNA : key;
    }

    private ResourceKey<Biome> pickShatteredCoastBiome(int temperature, int humidity, Climate.Parameter param) {
        ResourceKey<Biome> resourceKey = param.max() >= 0L ? this.pickMiddleBiome(temperature, humidity, param) : this.pickBeachBiome(temperature, humidity);
        return this.maybePickWindsweptSavannaBiome(temperature, humidity, param, resourceKey);
    }

    private ResourceKey<Biome> pickBeachBiome(int temperature, int humidity) {
        if (temperature == 0) {
            return Biomes.SNOWY_BEACH;
        } else {
            return temperature == 4 ? Biomes.DESERT : Biomes.BEACH;
        }
    }

    private ResourceKey<Biome> pickBadlandsBiome(int humidity, Climate.Parameter param) {
        if (humidity < 2) {
            return param.max() < 0L ? Biomes.BADLANDS : Biomes.ERODED_BADLANDS;
        } else {
            return humidity < 3 ? Biomes.BADLANDS : Biomes.WOODED_BADLANDS;
        }
    }

    private ResourceKey<Biome> pickPlateauBiome(int temperature, int humidity, Climate.Parameter param) {
        if (param.max() >= 0L) {
            ResourceKey<Biome> resourceKey = this.PLATEAU_BIOMES_VARIANT[temperature][humidity];
            if (resourceKey != null) {
                return resourceKey;
            }
        }

        return this.PLATEAU_BIOMES[temperature][humidity];
    }

    private ResourceKey<Biome> pickPeakBiome(int temperature, int humidity, Climate.Parameter param) {
        if (temperature <= 2) {
            return param.max() < 0L ? Biomes.JAGGED_PEAKS : Biomes.FROZEN_PEAKS;
        } else {
            return temperature == 3 ? Biomes.STONY_PEAKS : this.pickBadlandsBiome(humidity, param);
        }
    }

    private ResourceKey<Biome> pickSlopeBiome(int temperature, int humidity, Climate.Parameter param) {
        if (temperature >= 3) {
            return this.pickPlateauBiome(temperature, humidity, param);
        } else {
            return humidity <= 1 ? Biomes.SNOWY_SLOPES : Biomes.GROVE;
        }
    }

    private ResourceKey<Biome> pickShatteredBiome(int temperature, int humidity, Climate.Parameter param) {
        ResourceKey<Biome> resourceKey = this.SHATTERED_BIOMES[temperature][humidity];
        return resourceKey == null ? this.pickMiddleBiome(temperature, humidity, param) : resourceKey;
    }

    private void addSurfaceBiome(
        Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> consumer,
        Climate.Parameter temperature,
        Climate.Parameter humidity,
        Climate.Parameter continentalness,
        Climate.Parameter erosion,
        Climate.Parameter depth,
        float weirdness,
        ResourceKey<Biome> key
    ) {
        consumer.accept(Pair.of(Climate.parameters(temperature, humidity, continentalness, erosion, Climate.Parameter.point(0.0F), depth, weirdness), key));
        consumer.accept(Pair.of(Climate.parameters(temperature, humidity, continentalness, erosion, Climate.Parameter.point(1.0F), depth, weirdness), key));
    }

    private void addUndergroundBiome(
        Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> consumer,
        Climate.Parameter temperature,
        Climate.Parameter humidity,
        Climate.Parameter continentalness,
        Climate.Parameter erosion,
        Climate.Parameter depth,
        float weirdness,
        ResourceKey<Biome> key
    ) {
        consumer.accept(Pair.of(Climate.parameters(temperature, humidity, continentalness, erosion, Climate.Parameter.span(0.2F, 0.9F), depth, weirdness), key));
    }

    private void addBottomBiome(
        Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> consumer,
        Climate.Parameter temperature,
        Climate.Parameter humidity,
        Climate.Parameter continentalness,
        Climate.Parameter erosion,
        Climate.Parameter depth,
        float weirdness,
        ResourceKey<Biome> key
    ) {
        consumer.accept(Pair.of(Climate.parameters(temperature, humidity, continentalness, erosion, Climate.Parameter.point(1.1F), depth, weirdness), key));
    }

    public static boolean isDeepDarkRegion(DensityFunction erosionFunction, DensityFunction depthFunction, DensityFunction.FunctionContext functionContext) {
        return erosionFunction.compute(functionContext) < -0.225F && depthFunction.compute(functionContext) > 0.9F;
    }

    public static String getDebugStringForPeaksAndValleys(double peaksAndValleysData) {
        if (peaksAndValleysData < NoiseRouterData.peaksAndValleys(0.05F)) {
            return "Valley";
        } else if (peaksAndValleysData < NoiseRouterData.peaksAndValleys(0.26666668F)) {
            return "Low";
        } else if (peaksAndValleysData < NoiseRouterData.peaksAndValleys(0.4F)) {
            return "Mid";
        } else {
            return peaksAndValleysData < NoiseRouterData.peaksAndValleys(0.56666666F) ? "High" : "Peak";
        }
    }

    public String getDebugStringForContinentalness(double continentalness) {
        double d = Climate.quantizeCoord((float)continentalness);
        if (d < this.mushroomFieldsContinentalness.max()) {
            return "Mushroom fields";
        } else if (d < this.deepOceanContinentalness.max()) {
            return "Deep ocean";
        } else if (d < this.oceanContinentalness.max()) {
            return "Ocean";
        } else if (d < this.coastContinentalness.max()) {
            return "Coast";
        } else if (d < this.nearInlandContinentalness.max()) {
            return "Near inland";
        } else {
            return d < this.midInlandContinentalness.max() ? "Mid inland" : "Far inland";
        }
    }

    public String getDebugStringForErosion(double erosion) {
        return getDebugStringForNoiseValue(erosion, this.erosions);
    }

    public String getDebugStringForTemperature(double temperature) {
        return getDebugStringForNoiseValue(temperature, this.temperatures);
    }

    public String getDebugStringForHumidity(double humidity) {
        return getDebugStringForNoiseValue(humidity, this.humidities);
    }

    private static String getDebugStringForNoiseValue(double depth, Climate.Parameter[] values) {
        double d = Climate.quantizeCoord((float)depth);

        for (int i = 0; i < values.length; i++) {
            if (d < values[i].max()) {
                return i + "";
            }
        }

        return "?";
    }

    @VisibleForDebug
    public Climate.Parameter[] getTemperatureThresholds() {
        return this.temperatures;
    }

    @VisibleForDebug
    public Climate.Parameter[] getHumidityThresholds() {
        return this.humidities;
    }

    @VisibleForDebug
    public Climate.Parameter[] getErosionThresholds() {
        return this.erosions;
    }

    @VisibleForDebug
    public Climate.Parameter[] getContinentalnessThresholds() {
        return new Climate.Parameter[]{
            this.mushroomFieldsContinentalness,
            this.deepOceanContinentalness,
            this.oceanContinentalness,
            this.coastContinentalness,
            this.nearInlandContinentalness,
            this.midInlandContinentalness,
            this.farInlandContinentalness
        };
    }

    @VisibleForDebug
    public Climate.Parameter[] getPeaksAndValleysThresholds() {
        return new Climate.Parameter[]{
            Climate.Parameter.span(-2.0F, NoiseRouterData.peaksAndValleys(0.05F)),
            Climate.Parameter.span(NoiseRouterData.peaksAndValleys(0.05F), NoiseRouterData.peaksAndValleys(0.26666668F)),
            Climate.Parameter.span(NoiseRouterData.peaksAndValleys(0.26666668F), NoiseRouterData.peaksAndValleys(0.4F)),
            Climate.Parameter.span(NoiseRouterData.peaksAndValleys(0.4F), NoiseRouterData.peaksAndValleys(0.56666666F)),
            Climate.Parameter.span(NoiseRouterData.peaksAndValleys(0.56666666F), 2.0F)
        };
    }

    @VisibleForDebug
    public Climate.Parameter[] getWeirdnessThresholds() {
        return new Climate.Parameter[]{Climate.Parameter.span(-2.0F, 0.0F), Climate.Parameter.span(0.0F, 2.0F)};
    }
}
