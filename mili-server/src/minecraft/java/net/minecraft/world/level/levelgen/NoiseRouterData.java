package net.minecraft.world.level.levelgen;

import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.TerrainProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public class NoiseRouterData {
    public static final float GLOBAL_OFFSET = -0.50375F;
    private static final float ORE_THICKNESS = 0.08F;
    private static final double VEININESS_FREQUENCY = 1.5;
    private static final double NOODLE_SPACING_AND_STRAIGHTNESS = 1.5;
    private static final double SURFACE_DENSITY_THRESHOLD = 1.5625;
    private static final double CHEESE_NOISE_TARGET = -0.703125;
    public static final double NOISE_ZERO = 0.390625;
    public static final int ISLAND_CHUNK_DISTANCE = 64;
    public static final long ISLAND_CHUNK_DISTANCE_SQR = 4096L;
    private static final int DENSITY_Y_ANCHOR_BOTTOM = -64;
    private static final int DENSITY_Y_ANCHOR_TOP = 320;
    private static final double DENSITY_Y_BOTTOM = 1.5;
    private static final double DENSITY_Y_TOP = -1.5;
    private static final int OVERWORLD_BOTTOM_SLIDE_HEIGHT = 24;
    private static final double BASE_DENSITY_MULTIPLIER = 4.0;
    private static final DensityFunction BLENDING_FACTOR = DensityFunctions.constant(10.0);
    private static final DensityFunction BLENDING_JAGGEDNESS = DensityFunctions.zero();
    private static final ResourceKey<DensityFunction> ZERO = createKey("zero");
    private static final ResourceKey<DensityFunction> Y = createKey("y");
    private static final ResourceKey<DensityFunction> SHIFT_X = createKey("shift_x");
    private static final ResourceKey<DensityFunction> SHIFT_Z = createKey("shift_z");
    private static final ResourceKey<DensityFunction> BASE_3D_NOISE_OVERWORLD = createKey("overworld/base_3d_noise");
    private static final ResourceKey<DensityFunction> BASE_3D_NOISE_NETHER = createKey("nether/base_3d_noise");
    private static final ResourceKey<DensityFunction> BASE_3D_NOISE_END = createKey("end/base_3d_noise");
    public static final ResourceKey<DensityFunction> CONTINENTS = createKey("overworld/continents");
    public static final ResourceKey<DensityFunction> EROSION = createKey("overworld/erosion");
    public static final ResourceKey<DensityFunction> RIDGES = createKey("overworld/ridges");
    public static final ResourceKey<DensityFunction> RIDGES_FOLDED = createKey("overworld/ridges_folded");
    public static final ResourceKey<DensityFunction> OFFSET = createKey("overworld/offset");
    public static final ResourceKey<DensityFunction> FACTOR = createKey("overworld/factor");
    public static final ResourceKey<DensityFunction> JAGGEDNESS = createKey("overworld/jaggedness");
    public static final ResourceKey<DensityFunction> DEPTH = createKey("overworld/depth");
    private static final ResourceKey<DensityFunction> SLOPED_CHEESE = createKey("overworld/sloped_cheese");
    public static final ResourceKey<DensityFunction> CONTINENTS_LARGE = createKey("overworld_large_biomes/continents");
    public static final ResourceKey<DensityFunction> EROSION_LARGE = createKey("overworld_large_biomes/erosion");
    private static final ResourceKey<DensityFunction> OFFSET_LARGE = createKey("overworld_large_biomes/offset");
    private static final ResourceKey<DensityFunction> FACTOR_LARGE = createKey("overworld_large_biomes/factor");
    private static final ResourceKey<DensityFunction> JAGGEDNESS_LARGE = createKey("overworld_large_biomes/jaggedness");
    private static final ResourceKey<DensityFunction> DEPTH_LARGE = createKey("overworld_large_biomes/depth");
    private static final ResourceKey<DensityFunction> SLOPED_CHEESE_LARGE = createKey("overworld_large_biomes/sloped_cheese");
    private static final ResourceKey<DensityFunction> OFFSET_AMPLIFIED = createKey("overworld_amplified/offset");
    private static final ResourceKey<DensityFunction> FACTOR_AMPLIFIED = createKey("overworld_amplified/factor");
    private static final ResourceKey<DensityFunction> JAGGEDNESS_AMPLIFIED = createKey("overworld_amplified/jaggedness");
    private static final ResourceKey<DensityFunction> DEPTH_AMPLIFIED = createKey("overworld_amplified/depth");
    private static final ResourceKey<DensityFunction> SLOPED_CHEESE_AMPLIFIED = createKey("overworld_amplified/sloped_cheese");
    private static final ResourceKey<DensityFunction> SLOPED_CHEESE_END = createKey("end/sloped_cheese");
    private static final ResourceKey<DensityFunction> SPAGHETTI_ROUGHNESS_FUNCTION = createKey("overworld/caves/spaghetti_roughness_function");
    private static final ResourceKey<DensityFunction> ENTRANCES = createKey("overworld/caves/entrances");
    private static final ResourceKey<DensityFunction> NOODLE = createKey("overworld/caves/noodle");
    private static final ResourceKey<DensityFunction> PILLARS = createKey("overworld/caves/pillars");
    private static final ResourceKey<DensityFunction> SPAGHETTI_2D_THICKNESS_MODULATOR = createKey("overworld/caves/spaghetti_2d_thickness_modulator");
    private static final ResourceKey<DensityFunction> SPAGHETTI_2D = createKey("overworld/caves/spaghetti_2d");

    private static ResourceKey<DensityFunction> createKey(String location) {
        return ResourceKey.create(Registries.DENSITY_FUNCTION, Identifier.withDefaultNamespace(location));
    }

    public static Holder<? extends DensityFunction> bootstrap(BootstrapContext<DensityFunction> context) {
        HolderGetter<NormalNoise.NoiseParameters> holderGetter = context.lookup(Registries.NOISE);
        HolderGetter<DensityFunction> holderGetter1 = context.lookup(Registries.DENSITY_FUNCTION);
        context.register(ZERO, DensityFunctions.zero());
        int i = DimensionType.MIN_Y * 2;
        int i1 = DimensionType.MAX_Y * 2;
        context.register(Y, DensityFunctions.yClampedGradient(i, i1, i, i1));
        DensityFunction densityFunction = registerAndWrap(
            context, SHIFT_X, DensityFunctions.flatCache(DensityFunctions.cache2d(DensityFunctions.shiftA(holderGetter.getOrThrow(Noises.SHIFT))))
        );
        DensityFunction densityFunction1 = registerAndWrap(
            context, SHIFT_Z, DensityFunctions.flatCache(DensityFunctions.cache2d(DensityFunctions.shiftB(holderGetter.getOrThrow(Noises.SHIFT))))
        );
        context.register(BASE_3D_NOISE_OVERWORLD, BlendedNoise.createUnseeded(0.25, 0.125, 80.0, 160.0, 8.0));
        context.register(BASE_3D_NOISE_NETHER, BlendedNoise.createUnseeded(0.25, 0.375, 80.0, 60.0, 8.0));
        context.register(BASE_3D_NOISE_END, BlendedNoise.createUnseeded(0.25, 0.25, 80.0, 160.0, 4.0));
        Holder<DensityFunction> holder = context.register(
            CONTINENTS,
            DensityFunctions.flatCache(
                DensityFunctions.shiftedNoise2d(densityFunction, densityFunction1, 0.25, holderGetter.getOrThrow(Noises.CONTINENTALNESS))
            )
        );
        Holder<DensityFunction> holder1 = context.register(
            EROSION,
            DensityFunctions.flatCache(DensityFunctions.shiftedNoise2d(densityFunction, densityFunction1, 0.25, holderGetter.getOrThrow(Noises.EROSION)))
        );
        DensityFunction densityFunction2 = registerAndWrap(
            context,
            RIDGES,
            DensityFunctions.flatCache(DensityFunctions.shiftedNoise2d(densityFunction, densityFunction1, 0.25, holderGetter.getOrThrow(Noises.RIDGE)))
        );
        context.register(RIDGES_FOLDED, peaksAndValleys(densityFunction2));
        DensityFunction densityFunction3 = DensityFunctions.noise(holderGetter.getOrThrow(Noises.JAGGED), 1500.0, 0.0);
        registerTerrainNoises(context, holderGetter1, densityFunction3, holder, holder1, OFFSET, FACTOR, JAGGEDNESS, DEPTH, SLOPED_CHEESE, false);
        Holder<DensityFunction> holder2 = context.register(
            CONTINENTS_LARGE,
            DensityFunctions.flatCache(
                DensityFunctions.shiftedNoise2d(densityFunction, densityFunction1, 0.25, holderGetter.getOrThrow(Noises.CONTINENTALNESS_LARGE))
            )
        );
        Holder<DensityFunction> holder3 = context.register(
            EROSION_LARGE,
            DensityFunctions.flatCache(DensityFunctions.shiftedNoise2d(densityFunction, densityFunction1, 0.25, holderGetter.getOrThrow(Noises.EROSION_LARGE)))
        );
        registerTerrainNoises(
            context, holderGetter1, densityFunction3, holder2, holder3, OFFSET_LARGE, FACTOR_LARGE, JAGGEDNESS_LARGE, DEPTH_LARGE, SLOPED_CHEESE_LARGE, false
        );
        registerTerrainNoises(
            context,
            holderGetter1,
            densityFunction3,
            holder,
            holder1,
            OFFSET_AMPLIFIED,
            FACTOR_AMPLIFIED,
            JAGGEDNESS_AMPLIFIED,
            DEPTH_AMPLIFIED,
            SLOPED_CHEESE_AMPLIFIED,
            true
        );
        context.register(SLOPED_CHEESE_END, DensityFunctions.add(DensityFunctions.endIslands(0L), getFunction(holderGetter1, BASE_3D_NOISE_END)));
        context.register(SPAGHETTI_ROUGHNESS_FUNCTION, spaghettiRoughnessFunction(holderGetter));
        context.register(
            SPAGHETTI_2D_THICKNESS_MODULATOR,
            DensityFunctions.cacheOnce(DensityFunctions.mappedNoise(holderGetter.getOrThrow(Noises.SPAGHETTI_2D_THICKNESS), 2.0, 1.0, -0.6, -1.3))
        );
        context.register(SPAGHETTI_2D, spaghetti2D(holderGetter1, holderGetter));
        context.register(ENTRANCES, entrances(holderGetter1, holderGetter));
        context.register(NOODLE, noodle(holderGetter1, holderGetter));
        return context.register(PILLARS, pillars(holderGetter));
    }

    private static void registerTerrainNoises(
        BootstrapContext<DensityFunction> context,
        HolderGetter<DensityFunction> densityFunctionRegistry,
        DensityFunction jaggedNoise,
        Holder<DensityFunction> continentalness,
        Holder<DensityFunction> erosion,
        ResourceKey<DensityFunction> offsetKey,
        ResourceKey<DensityFunction> factorKey,
        ResourceKey<DensityFunction> jaggednessKey,
        ResourceKey<DensityFunction> depthKey,
        ResourceKey<DensityFunction> slopedCheeseKey,
        boolean amplified
    ) {
        DensityFunctions.Spline.Coordinate coordinate = new DensityFunctions.Spline.Coordinate(continentalness);
        DensityFunctions.Spline.Coordinate coordinate1 = new DensityFunctions.Spline.Coordinate(erosion);
        DensityFunctions.Spline.Coordinate coordinate2 = new DensityFunctions.Spline.Coordinate(densityFunctionRegistry.getOrThrow(RIDGES));
        DensityFunctions.Spline.Coordinate coordinate3 = new DensityFunctions.Spline.Coordinate(densityFunctionRegistry.getOrThrow(RIDGES_FOLDED));
        DensityFunction densityFunction = registerAndWrap(
            context,
            offsetKey,
            splineWithBlending(
                DensityFunctions.add(
                    DensityFunctions.constant(-0.50375F),
                    DensityFunctions.spline(TerrainProvider.overworldOffset(coordinate, coordinate1, coordinate3, amplified))
                ),
                DensityFunctions.blendOffset()
            )
        );
        DensityFunction densityFunction1 = registerAndWrap(
            context,
            factorKey,
            splineWithBlending(
                DensityFunctions.spline(TerrainProvider.overworldFactor(coordinate, coordinate1, coordinate2, coordinate3, amplified)), BLENDING_FACTOR
            )
        );
        DensityFunction densityFunction2 = registerAndWrap(context, depthKey, offsetToDepth(densityFunction));
        DensityFunction densityFunction3 = registerAndWrap(
            context,
            jaggednessKey,
            splineWithBlending(
                DensityFunctions.spline(TerrainProvider.overworldJaggedness(coordinate, coordinate1, coordinate2, coordinate3, amplified)), BLENDING_JAGGEDNESS
            )
        );
        DensityFunction densityFunction4 = DensityFunctions.mul(densityFunction3, jaggedNoise.halfNegative());
        DensityFunction densityFunction5 = noiseGradientDensity(densityFunction1, DensityFunctions.add(densityFunction2, densityFunction4));
        context.register(slopedCheeseKey, DensityFunctions.add(densityFunction5, getFunction(densityFunctionRegistry, BASE_3D_NOISE_OVERWORLD)));
    }

    private static DensityFunction offsetToDepth(DensityFunction depth) {
        return DensityFunctions.add(DensityFunctions.yClampedGradient(-64, 320, 1.5, -1.5), depth);
    }

    private static DensityFunction registerAndWrap(BootstrapContext<DensityFunction> context, ResourceKey<DensityFunction> key, DensityFunction value) {
        return new DensityFunctions.HolderHolder(context.register(key, value));
    }

    private static DensityFunction getFunction(HolderGetter<DensityFunction> densityFunctionRegistry, ResourceKey<DensityFunction> key) {
        return new DensityFunctions.HolderHolder(densityFunctionRegistry.getOrThrow(key));
    }

    private static DensityFunction peaksAndValleys(DensityFunction densityFunction) {
        return DensityFunctions.mul(
            DensityFunctions.add(
                DensityFunctions.add(densityFunction.abs(), DensityFunctions.constant(-0.6666666666666666)).abs(),
                DensityFunctions.constant(-0.3333333333333333)
            ),
            DensityFunctions.constant(-3.0)
        );
    }

    public static float peaksAndValleys(float weirdness) {
        return -(Math.abs(Math.abs(weirdness) - 0.6666667F) - 0.33333334F) * 3.0F;
    }

    private static DensityFunction spaghettiRoughnessFunction(HolderGetter<NormalNoise.NoiseParameters> noiseParameters) {
        DensityFunction densityFunction = DensityFunctions.noise(noiseParameters.getOrThrow(Noises.SPAGHETTI_ROUGHNESS));
        DensityFunction densityFunction1 = DensityFunctions.mappedNoise(noiseParameters.getOrThrow(Noises.SPAGHETTI_ROUGHNESS_MODULATOR), 0.0, -0.1);
        return DensityFunctions.cacheOnce(DensityFunctions.mul(densityFunction1, DensityFunctions.add(densityFunction.abs(), DensityFunctions.constant(-0.4))));
    }

    private static DensityFunction entrances(HolderGetter<DensityFunction> densityFunctionRegistry, HolderGetter<NormalNoise.NoiseParameters> noiseParameters) {
        DensityFunction densityFunction = DensityFunctions.cacheOnce(DensityFunctions.noise(noiseParameters.getOrThrow(Noises.SPAGHETTI_3D_RARITY), 2.0, 1.0));
        DensityFunction densityFunction1 = DensityFunctions.mappedNoise(noiseParameters.getOrThrow(Noises.SPAGHETTI_3D_THICKNESS), -0.065, -0.088);
        DensityFunction densityFunction2 = DensityFunctions.weirdScaledSampler(
            densityFunction, noiseParameters.getOrThrow(Noises.SPAGHETTI_3D_1), DensityFunctions.WeirdScaledSampler.RarityValueMapper.TYPE1
        );
        DensityFunction densityFunction3 = DensityFunctions.weirdScaledSampler(
            densityFunction, noiseParameters.getOrThrow(Noises.SPAGHETTI_3D_2), DensityFunctions.WeirdScaledSampler.RarityValueMapper.TYPE1
        );
        DensityFunction densityFunction4 = DensityFunctions.add(DensityFunctions.max(densityFunction2, densityFunction3), densityFunction1).clamp(-1.0, 1.0);
        DensityFunction function = getFunction(densityFunctionRegistry, SPAGHETTI_ROUGHNESS_FUNCTION);
        DensityFunction densityFunction5 = DensityFunctions.noise(noiseParameters.getOrThrow(Noises.CAVE_ENTRANCE), 0.75, 0.5);
        DensityFunction densityFunction6 = DensityFunctions.add(
            DensityFunctions.add(densityFunction5, DensityFunctions.constant(0.37)), DensityFunctions.yClampedGradient(-10, 30, 0.3, 0.0)
        );
        return DensityFunctions.cacheOnce(DensityFunctions.min(densityFunction6, DensityFunctions.add(function, densityFunction4)));
    }

    private static DensityFunction noodle(HolderGetter<DensityFunction> densityFunctions, HolderGetter<NormalNoise.NoiseParameters> noiseParameters) {
        DensityFunction function = getFunction(densityFunctions, Y);
        int i = -64;
        int i1 = -60;
        int i2 = 320;
        DensityFunction densityFunction = yLimitedInterpolatable(
            function, DensityFunctions.noise(noiseParameters.getOrThrow(Noises.NOODLE), 1.0, 1.0), -60, 320, -1
        );
        DensityFunction densityFunction1 = yLimitedInterpolatable(
            function, DensityFunctions.mappedNoise(noiseParameters.getOrThrow(Noises.NOODLE_THICKNESS), 1.0, 1.0, -0.05, -0.1), -60, 320, 0
        );
        double d = 2.6666666666666665;
        DensityFunction densityFunction2 = yLimitedInterpolatable(
            function, DensityFunctions.noise(noiseParameters.getOrThrow(Noises.NOODLE_RIDGE_A), 2.6666666666666665, 2.6666666666666665), -60, 320, 0
        );
        DensityFunction densityFunction3 = yLimitedInterpolatable(
            function, DensityFunctions.noise(noiseParameters.getOrThrow(Noises.NOODLE_RIDGE_B), 2.6666666666666665, 2.6666666666666665), -60, 320, 0
        );
        DensityFunction densityFunction4 = DensityFunctions.mul(
            DensityFunctions.constant(1.5), DensityFunctions.max(densityFunction2.abs(), densityFunction3.abs())
        );
        return DensityFunctions.rangeChoice(
            densityFunction, -1000000.0, 0.0, DensityFunctions.constant(64.0), DensityFunctions.add(densityFunction1, densityFunction4)
        );
    }

    private static DensityFunction pillars(HolderGetter<NormalNoise.NoiseParameters> noiseParameters) {
        double d = 25.0;
        double d1 = 0.3;
        DensityFunction densityFunction = DensityFunctions.noise(noiseParameters.getOrThrow(Noises.PILLAR), 25.0, 0.3);
        DensityFunction densityFunction1 = DensityFunctions.mappedNoise(noiseParameters.getOrThrow(Noises.PILLAR_RARENESS), 0.0, -2.0);
        DensityFunction densityFunction2 = DensityFunctions.mappedNoise(noiseParameters.getOrThrow(Noises.PILLAR_THICKNESS), 0.0, 1.1);
        DensityFunction densityFunction3 = DensityFunctions.add(DensityFunctions.mul(densityFunction, DensityFunctions.constant(2.0)), densityFunction1);
        return DensityFunctions.cacheOnce(DensityFunctions.mul(densityFunction3, densityFunction2.cube()));
    }

    private static DensityFunction spaghetti2D(HolderGetter<DensityFunction> densityFunctionRegistry, HolderGetter<NormalNoise.NoiseParameters> noiseParameters) {
        DensityFunction densityFunction = DensityFunctions.noise(noiseParameters.getOrThrow(Noises.SPAGHETTI_2D_MODULATOR), 2.0, 1.0);
        DensityFunction densityFunction1 = DensityFunctions.weirdScaledSampler(
            densityFunction, noiseParameters.getOrThrow(Noises.SPAGHETTI_2D), DensityFunctions.WeirdScaledSampler.RarityValueMapper.TYPE2
        );
        DensityFunction densityFunction2 = DensityFunctions.mappedNoise(
            noiseParameters.getOrThrow(Noises.SPAGHETTI_2D_ELEVATION), 0.0, Math.floorDiv(-64, 8), 8.0
        );
        DensityFunction function = getFunction(densityFunctionRegistry, SPAGHETTI_2D_THICKNESS_MODULATOR);
        DensityFunction densityFunction3 = DensityFunctions.add(densityFunction2, DensityFunctions.yClampedGradient(-64, 320, 8.0, -40.0)).abs();
        DensityFunction densityFunction4 = DensityFunctions.add(densityFunction3, function).cube();
        double d = 0.083;
        DensityFunction densityFunction5 = DensityFunctions.add(densityFunction1, DensityFunctions.mul(DensityFunctions.constant(0.083), function));
        return DensityFunctions.max(densityFunction5, densityFunction4).clamp(-1.0, 1.0);
    }

    private static DensityFunction underground(
        HolderGetter<DensityFunction> densityFunctionRegistry, HolderGetter<NormalNoise.NoiseParameters> noiseParameters, DensityFunction slopedCheese
    ) {
        DensityFunction function = getFunction(densityFunctionRegistry, SPAGHETTI_2D);
        DensityFunction function1 = getFunction(densityFunctionRegistry, SPAGHETTI_ROUGHNESS_FUNCTION);
        DensityFunction densityFunction = DensityFunctions.noise(noiseParameters.getOrThrow(Noises.CAVE_LAYER), 8.0);
        DensityFunction densityFunction1 = DensityFunctions.mul(DensityFunctions.constant(4.0), densityFunction.square());
        DensityFunction densityFunction2 = DensityFunctions.noise(noiseParameters.getOrThrow(Noises.CAVE_CHEESE), 0.6666666666666666);
        DensityFunction densityFunction3 = DensityFunctions.add(
            DensityFunctions.add(DensityFunctions.constant(0.27), densityFunction2).clamp(-1.0, 1.0),
            DensityFunctions.add(DensityFunctions.constant(1.5), DensityFunctions.mul(DensityFunctions.constant(-0.64), slopedCheese)).clamp(0.0, 0.5)
        );
        DensityFunction densityFunction4 = DensityFunctions.add(densityFunction1, densityFunction3);
        DensityFunction densityFunction5 = DensityFunctions.min(
            DensityFunctions.min(densityFunction4, getFunction(densityFunctionRegistry, ENTRANCES)), DensityFunctions.add(function, function1)
        );
        DensityFunction function2 = getFunction(densityFunctionRegistry, PILLARS);
        DensityFunction densityFunction6 = DensityFunctions.rangeChoice(function2, -1000000.0, 0.03, DensityFunctions.constant(-1000000.0), function2);
        return DensityFunctions.max(densityFunction5, densityFunction6);
    }

    private static DensityFunction postProcess(DensityFunction densityFunction) {
        DensityFunction densityFunction1 = DensityFunctions.blendDensity(densityFunction);
        return DensityFunctions.mul(DensityFunctions.interpolated(densityFunction1), DensityFunctions.constant(0.64)).squeeze();
    }

    private static DensityFunction remap(DensityFunction function, double inMin, double inMax, double outMin, double outMax) {
        double d = (outMax - outMin) / (inMax - inMin);
        double d1 = outMin - inMin * d;
        return DensityFunctions.add(DensityFunctions.mul(function, DensityFunctions.constant(d)), DensityFunctions.constant(d1));
    }

    protected static NoiseRouter overworld(
        HolderGetter<DensityFunction> densityFunctionRegistry, HolderGetter<NormalNoise.NoiseParameters> noiseParameters, boolean large, boolean amplified
    ) {
        DensityFunction densityFunction = DensityFunctions.noise(noiseParameters.getOrThrow(Noises.AQUIFER_BARRIER), 0.5);
        DensityFunction densityFunction1 = DensityFunctions.noise(noiseParameters.getOrThrow(Noises.AQUIFER_FLUID_LEVEL_FLOODEDNESS), 0.67);
        DensityFunction densityFunction2 = DensityFunctions.noise(noiseParameters.getOrThrow(Noises.AQUIFER_FLUID_LEVEL_SPREAD), 0.7142857142857143);
        DensityFunction densityFunction3 = DensityFunctions.noise(noiseParameters.getOrThrow(Noises.AQUIFER_LAVA));
        DensityFunction function = getFunction(densityFunctionRegistry, SHIFT_X);
        DensityFunction function1 = getFunction(densityFunctionRegistry, SHIFT_Z);
        DensityFunction densityFunction4 = DensityFunctions.shiftedNoise2d(
            function, function1, 0.25, noiseParameters.getOrThrow(large ? Noises.TEMPERATURE_LARGE : Noises.TEMPERATURE)
        );
        DensityFunction densityFunction5 = DensityFunctions.shiftedNoise2d(
            function, function1, 0.25, noiseParameters.getOrThrow(large ? Noises.VEGETATION_LARGE : Noises.VEGETATION)
        );
        DensityFunction function2 = getFunction(densityFunctionRegistry, large ? OFFSET_LARGE : (amplified ? OFFSET_AMPLIFIED : OFFSET));
        DensityFunction function3 = getFunction(densityFunctionRegistry, large ? FACTOR_LARGE : (amplified ? FACTOR_AMPLIFIED : FACTOR));
        DensityFunction function4 = getFunction(densityFunctionRegistry, large ? DEPTH_LARGE : (amplified ? DEPTH_AMPLIFIED : DEPTH));
        DensityFunction densityFunction6 = preliminarySurfaceLevel(function2, function3, amplified);
        DensityFunction function5 = getFunction(densityFunctionRegistry, large ? SLOPED_CHEESE_LARGE : (amplified ? SLOPED_CHEESE_AMPLIFIED : SLOPED_CHEESE));
        DensityFunction densityFunction7 = DensityFunctions.min(
            function5, DensityFunctions.mul(DensityFunctions.constant(5.0), getFunction(densityFunctionRegistry, ENTRANCES))
        );
        DensityFunction densityFunction8 = DensityFunctions.rangeChoice(
            function5, -1000000.0, 1.5625, densityFunction7, underground(densityFunctionRegistry, noiseParameters, function5)
        );
        DensityFunction densityFunction9 = DensityFunctions.min(
            postProcess(slideOverworld(amplified, densityFunction8)), getFunction(densityFunctionRegistry, NOODLE)
        );
        DensityFunction function6 = getFunction(densityFunctionRegistry, Y);
        int i = Stream.of(OreVeinifier.VeinType.values()).mapToInt(veinType -> veinType.minY).min().orElse(-DimensionType.MIN_Y * 2);
        int i1 = Stream.of(OreVeinifier.VeinType.values()).mapToInt(veinType -> veinType.maxY).max().orElse(-DimensionType.MIN_Y * 2);
        DensityFunction densityFunction10 = yLimitedInterpolatable(
            function6, DensityFunctions.noise(noiseParameters.getOrThrow(Noises.ORE_VEININESS), 1.5, 1.5), i, i1, 0
        );
        float f = 4.0F;
        DensityFunction densityFunction11 = yLimitedInterpolatable(
                function6, DensityFunctions.noise(noiseParameters.getOrThrow(Noises.ORE_VEIN_A), 4.0, 4.0), i, i1, 0
            )
            .abs();
        DensityFunction densityFunction12 = yLimitedInterpolatable(
                function6, DensityFunctions.noise(noiseParameters.getOrThrow(Noises.ORE_VEIN_B), 4.0, 4.0), i, i1, 0
            )
            .abs();
        DensityFunction densityFunction13 = DensityFunctions.add(DensityFunctions.constant(-0.08F), DensityFunctions.max(densityFunction11, densityFunction12));
        DensityFunction densityFunction14 = DensityFunctions.noise(noiseParameters.getOrThrow(Noises.ORE_GAP));
        return new NoiseRouter(
            densityFunction,
            densityFunction1,
            densityFunction2,
            densityFunction3,
            densityFunction4,
            densityFunction5,
            getFunction(densityFunctionRegistry, large ? CONTINENTS_LARGE : CONTINENTS),
            getFunction(densityFunctionRegistry, large ? EROSION_LARGE : EROSION),
            function4,
            getFunction(densityFunctionRegistry, RIDGES),
            densityFunction6,
            densityFunction9,
            densityFunction10,
            densityFunction13,
            densityFunction14
        );
    }

    private static NoiseRouter noNewCaves(
        HolderGetter<DensityFunction> densityFunctions, HolderGetter<NormalNoise.NoiseParameters> noiseParameters, DensityFunction postProcessor
    ) {
        DensityFunction function = getFunction(densityFunctions, SHIFT_X);
        DensityFunction function1 = getFunction(densityFunctions, SHIFT_Z);
        DensityFunction densityFunction = DensityFunctions.shiftedNoise2d(function, function1, 0.25, noiseParameters.getOrThrow(Noises.TEMPERATURE));
        DensityFunction densityFunction1 = DensityFunctions.shiftedNoise2d(function, function1, 0.25, noiseParameters.getOrThrow(Noises.VEGETATION));
        DensityFunction densityFunction2 = postProcess(postProcessor);
        return new NoiseRouter(
            DensityFunctions.zero(),
            DensityFunctions.zero(),
            DensityFunctions.zero(),
            DensityFunctions.zero(),
            densityFunction,
            densityFunction1,
            DensityFunctions.zero(),
            DensityFunctions.zero(),
            DensityFunctions.zero(),
            DensityFunctions.zero(),
            DensityFunctions.zero(),
            densityFunction2,
            DensityFunctions.zero(),
            DensityFunctions.zero(),
            DensityFunctions.zero()
        );
    }

    private static DensityFunction slideOverworld(boolean amplified, DensityFunction densityFunction) {
        return slide(densityFunction, -64, 384, amplified ? 16 : 80, amplified ? 0 : 64, -0.078125, 0, 24, amplified ? 0.4 : 0.1171875);
    }

    private static DensityFunction slideNetherLike(HolderGetter<DensityFunction> densityFunctions, int minY, int height) {
        return slide(getFunction(densityFunctions, BASE_3D_NOISE_NETHER), minY, height, 24, 0, 0.9375, -8, 24, 2.5);
    }

    private static DensityFunction slideEndLike(DensityFunction densityFunction, int minY, int height) {
        return slide(densityFunction, minY, height, 72, -184, -23.4375, 4, 32, -0.234375);
    }

    protected static NoiseRouter nether(HolderGetter<DensityFunction> densityFunctions, HolderGetter<NormalNoise.NoiseParameters> noiseParameters) {
        return noNewCaves(densityFunctions, noiseParameters, slideNetherLike(densityFunctions, 0, 128));
    }

    protected static NoiseRouter caves(HolderGetter<DensityFunction> densityFunctions, HolderGetter<NormalNoise.NoiseParameters> noiseParameters) {
        return noNewCaves(densityFunctions, noiseParameters, slideNetherLike(densityFunctions, -64, 192));
    }

    protected static NoiseRouter floatingIslands(HolderGetter<DensityFunction> densityFunction, HolderGetter<NormalNoise.NoiseParameters> noiseParameters) {
        return noNewCaves(densityFunction, noiseParameters, slideEndLike(getFunction(densityFunction, BASE_3D_NOISE_END), 0, 256));
    }

    private static DensityFunction slideEnd(DensityFunction densityFunction) {
        return slideEndLike(densityFunction, 0, 128);
    }

    protected static NoiseRouter end(HolderGetter<DensityFunction> densityFunctions) {
        DensityFunction densityFunction = DensityFunctions.cache2d(DensityFunctions.endIslands(0L));
        DensityFunction densityFunction1 = postProcess(slideEnd(getFunction(densityFunctions, SLOPED_CHEESE_END)));
        return new NoiseRouter(
            DensityFunctions.zero(),
            DensityFunctions.zero(),
            DensityFunctions.zero(),
            DensityFunctions.zero(),
            DensityFunctions.zero(),
            DensityFunctions.zero(),
            DensityFunctions.zero(),
            densityFunction,
            DensityFunctions.zero(),
            DensityFunctions.zero(),
            DensityFunctions.zero(),
            densityFunction1,
            DensityFunctions.zero(),
            DensityFunctions.zero(),
            DensityFunctions.zero()
        );
    }

    protected static NoiseRouter none() {
        return new NoiseRouter(
            DensityFunctions.zero(),
            DensityFunctions.zero(),
            DensityFunctions.zero(),
            DensityFunctions.zero(),
            DensityFunctions.zero(),
            DensityFunctions.zero(),
            DensityFunctions.zero(),
            DensityFunctions.zero(),
            DensityFunctions.zero(),
            DensityFunctions.zero(),
            DensityFunctions.zero(),
            DensityFunctions.zero(),
            DensityFunctions.zero(),
            DensityFunctions.zero(),
            DensityFunctions.zero()
        );
    }

    private static DensityFunction splineWithBlending(DensityFunction minFunction, DensityFunction maxFunction) {
        DensityFunction densityFunction = DensityFunctions.lerp(DensityFunctions.blendAlpha(), maxFunction, minFunction);
        return DensityFunctions.flatCache(DensityFunctions.cache2d(densityFunction));
    }

    private static DensityFunction noiseGradientDensity(DensityFunction minFunction, DensityFunction maxFunction) {
        DensityFunction densityFunction = DensityFunctions.mul(maxFunction, minFunction);
        return DensityFunctions.mul(DensityFunctions.constant(4.0), densityFunction.quarterNegative());
    }

    private static DensityFunction preliminarySurfaceLevel(DensityFunction offset, DensityFunction factor, boolean amplified) {
        DensityFunction densityFunction = DensityFunctions.cache2d(factor);
        DensityFunction densityFunction1 = DensityFunctions.cache2d(offset);
        DensityFunction densityFunction2 = remap(
            DensityFunctions.add(
                DensityFunctions.mul(DensityFunctions.constant(0.2734375), densityFunction.invert()),
                DensityFunctions.mul(DensityFunctions.constant(-1.0), densityFunction1)
            ),
            1.5,
            -1.5,
            -64.0,
            320.0
        );
        densityFunction2 = densityFunction2.clamp(-40.0, 320.0);
        DensityFunction densityFunction3 = DensityFunctions.add(
            slideOverworld(
                amplified,
                DensityFunctions.add(noiseGradientDensity(densityFunction, offsetToDepth(densityFunction1)), DensityFunctions.constant(-0.703125))
                    .clamp(-64.0, 64.0)
            ),
            DensityFunctions.constant(-0.390625)
        );
        return DensityFunctions.findTopSurface(densityFunction3, densityFunction2, -64, NoiseSettings.OVERWORLD_NOISE_SETTINGS.getCellHeight());
    }

    private static DensityFunction yLimitedInterpolatable(DensityFunction input, DensityFunction whenInRange, int minY, int maxY, int whenOutOfRange) {
        return DensityFunctions.interpolated(DensityFunctions.rangeChoice(input, minY, maxY + 1, whenInRange, DensityFunctions.constant(whenOutOfRange)));
    }

    private static DensityFunction slide(
        DensityFunction input,
        int minY,
        int height,
        int topStartOffset,
        int topEndOffset,
        double topDelta,
        int bottomStartOffset,
        int bottomEndOffset,
        double bottomDelta
    ) {
        DensityFunction densityFunction1 = DensityFunctions.yClampedGradient(minY + height - topStartOffset, minY + height - topEndOffset, 1.0, 0.0);
        DensityFunction densityFunction = DensityFunctions.lerp(densityFunction1, topDelta, input);
        DensityFunction densityFunction2 = DensityFunctions.yClampedGradient(minY + bottomStartOffset, minY + bottomEndOffset, 0.0, 1.0);
        return DensityFunctions.lerp(densityFunction2, bottomDelta, densityFunction);
    }

    protected static final class QuantizedSpaghettiRarity {
        protected static double getSphaghettiRarity2D(double value) {
            if (value < -0.75) {
                return 0.5;
            } else if (value < -0.5) {
                return 0.75;
            } else if (value < 0.5) {
                return 1.0;
            } else {
                return value < 0.75 ? 2.0 : 3.0;
            }
        }

        protected static double getSpaghettiRarity3D(double value) {
            if (value < -0.5) {
                return 0.75;
            } else if (value < 0.0) {
                return 1.0;
            } else {
                return value < 0.5 ? 1.5 : 2.0;
            }
        }
    }
}
