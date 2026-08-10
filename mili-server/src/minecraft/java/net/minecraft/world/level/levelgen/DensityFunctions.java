package net.minecraft.world.level.levelgen;

import com.mojang.datafixers.util.Either;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.doubles.Double2DoubleFunction;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.BoundedFloatFunction;
import net.minecraft.util.CubicSpline;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import org.slf4j.Logger;

public final class DensityFunctions {
    private static final Codec<DensityFunction> CODEC = BuiltInRegistries.DENSITY_FUNCTION_TYPE
        .byNameCodec()
        .dispatch(densityFunction -> densityFunction.codec().codec(), Function.identity());
    protected static final double MAX_REASONABLE_NOISE_VALUE = 1000000.0;
    static final Codec<Double> NOISE_VALUE_CODEC = Codec.doubleRange(-1000000.0, 1000000.0);
    public static final Codec<DensityFunction> DIRECT_CODEC = Codec.either(NOISE_VALUE_CODEC, CODEC)
        .xmap(
            either -> either.map(DensityFunctions::constant, Function.identity()),
            densityFunction -> densityFunction instanceof DensityFunctions.Constant constant ? Either.left(constant.value()) : Either.right(densityFunction)
        );

    public static MapCodec<? extends DensityFunction> bootstrap(Registry<MapCodec<? extends DensityFunction>> registry) {
        register(registry, "blend_alpha", DensityFunctions.BlendAlpha.CODEC);
        register(registry, "blend_offset", DensityFunctions.BlendOffset.CODEC);
        register(registry, "beardifier", DensityFunctions.BeardifierMarker.CODEC);
        register(registry, "old_blended_noise", BlendedNoise.CODEC);

        for (DensityFunctions.Marker.Type type : DensityFunctions.Marker.Type.values()) {
            register(registry, type.getSerializedName(), type.codec);
        }

        register(registry, "noise", DensityFunctions.Noise.CODEC);
        register(registry, "end_islands", DensityFunctions.EndIslandDensityFunction.CODEC);
        register(registry, "weird_scaled_sampler", DensityFunctions.WeirdScaledSampler.CODEC);
        register(registry, "shifted_noise", DensityFunctions.ShiftedNoise.CODEC);
        register(registry, "range_choice", DensityFunctions.RangeChoice.CODEC);
        register(registry, "shift_a", DensityFunctions.ShiftA.CODEC);
        register(registry, "shift_b", DensityFunctions.ShiftB.CODEC);
        register(registry, "shift", DensityFunctions.Shift.CODEC);
        register(registry, "blend_density", DensityFunctions.BlendDensity.CODEC);
        register(registry, "clamp", DensityFunctions.Clamp.CODEC);

        for (DensityFunctions.Mapped.Type type1 : DensityFunctions.Mapped.Type.values()) {
            register(registry, type1.getSerializedName(), type1.codec);
        }

        for (DensityFunctions.TwoArgumentSimpleFunction.Type type2 : DensityFunctions.TwoArgumentSimpleFunction.Type.values()) {
            register(registry, type2.getSerializedName(), type2.codec);
        }

        register(registry, "spline", DensityFunctions.Spline.CODEC);
        register(registry, "constant", DensityFunctions.Constant.CODEC);
        register(registry, "y_clamped_gradient", DensityFunctions.YClampedGradient.CODEC);
        return register(registry, "find_top_surface", DensityFunctions.FindTopSurface.CODEC);
    }

    private static MapCodec<? extends DensityFunction> register(
        Registry<MapCodec<? extends DensityFunction>> registry, String name, KeyDispatchDataCodec<? extends DensityFunction> codec
    ) {
        return Registry.register(registry, name, codec.codec());
    }

    static <A, O> KeyDispatchDataCodec<O> singleArgumentCodec(Codec<A> codec, Function<A, O> fromFunction, Function<O, A> toFunction) {
        return KeyDispatchDataCodec.of(codec.fieldOf("argument").xmap(fromFunction, toFunction));
    }

    static <O> KeyDispatchDataCodec<O> singleFunctionArgumentCodec(Function<DensityFunction, O> fromFunction, Function<O, DensityFunction> toFunction) {
        return singleArgumentCodec(DensityFunction.HOLDER_HELPER_CODEC, fromFunction, toFunction);
    }

    static <O> KeyDispatchDataCodec<O> doubleFunctionArgumentCodec(
        BiFunction<DensityFunction, DensityFunction, O> fromFunction, Function<O, DensityFunction> primary, Function<O, DensityFunction> secondary
    ) {
        return KeyDispatchDataCodec.of(
            RecordCodecBuilder.mapCodec(
                instance -> instance.group(
                        DensityFunction.HOLDER_HELPER_CODEC.fieldOf("argument1").forGetter(primary),
                        DensityFunction.HOLDER_HELPER_CODEC.fieldOf("argument2").forGetter(secondary)
                    )
                    .apply(instance, fromFunction)
            )
        );
    }

    static <O> KeyDispatchDataCodec<O> makeCodec(MapCodec<O> mapCodec) {
        return KeyDispatchDataCodec.of(mapCodec);
    }

    private DensityFunctions() {
    }

    public static DensityFunction interpolated(DensityFunction wrapped) {
        return new DensityFunctions.Marker(DensityFunctions.Marker.Type.Interpolated, wrapped);
    }

    public static DensityFunction flatCache(DensityFunction wrapped) {
        return new DensityFunctions.Marker(DensityFunctions.Marker.Type.FlatCache, wrapped);
    }

    public static DensityFunction cache2d(DensityFunction wrapped) {
        return new DensityFunctions.Marker(DensityFunctions.Marker.Type.Cache2D, wrapped);
    }

    public static DensityFunction cacheOnce(DensityFunction wrapped) {
        return new DensityFunctions.Marker(DensityFunctions.Marker.Type.CacheOnce, wrapped);
    }

    public static DensityFunction cacheAllInCell(DensityFunction wrapped) {
        return new DensityFunctions.Marker(DensityFunctions.Marker.Type.CacheAllInCell, wrapped);
    }

    public static DensityFunction mappedNoise(
        Holder<NormalNoise.NoiseParameters> noiseData, @Deprecated double xzScale, double yScale, double fromY, double toY
    ) {
        return mapFromUnitTo(new DensityFunctions.Noise(new DensityFunction.NoiseHolder(noiseData), xzScale, yScale), fromY, toY);
    }

    public static DensityFunction mappedNoise(Holder<NormalNoise.NoiseParameters> noiseData, double yScale, double fromY, double toY) {
        return mappedNoise(noiseData, 1.0, yScale, fromY, toY);
    }

    public static DensityFunction mappedNoise(Holder<NormalNoise.NoiseParameters> noiseData, double fromY, double toY) {
        return mappedNoise(noiseData, 1.0, 1.0, fromY, toY);
    }

    public static DensityFunction shiftedNoise2d(DensityFunction shiftX, DensityFunction shiftZ, double xzScale, Holder<NormalNoise.NoiseParameters> noiseData) {
        return new DensityFunctions.ShiftedNoise(shiftX, zero(), shiftZ, xzScale, 0.0, new DensityFunction.NoiseHolder(noiseData));
    }

    public static DensityFunction noise(Holder<NormalNoise.NoiseParameters> noiseData) {
        return noise(noiseData, 1.0, 1.0);
    }

    public static DensityFunction noise(Holder<NormalNoise.NoiseParameters> noiseData, double xzScale, double yScale) {
        return new DensityFunctions.Noise(new DensityFunction.NoiseHolder(noiseData), xzScale, yScale);
    }

    public static DensityFunction noise(Holder<NormalNoise.NoiseParameters> noiseData, double yScale) {
        return noise(noiseData, 1.0, yScale);
    }

    public static DensityFunction rangeChoice(
        DensityFunction input, double minInclusive, double maxExclusive, DensityFunction whenInRange, DensityFunction whenOutOfRange
    ) {
        return new DensityFunctions.RangeChoice(input, minInclusive, maxExclusive, whenInRange, whenOutOfRange);
    }

    public static DensityFunction shiftA(Holder<NormalNoise.NoiseParameters> noiseData) {
        return new DensityFunctions.ShiftA(new DensityFunction.NoiseHolder(noiseData));
    }

    public static DensityFunction shiftB(Holder<NormalNoise.NoiseParameters> noiseData) {
        return new DensityFunctions.ShiftB(new DensityFunction.NoiseHolder(noiseData));
    }

    public static DensityFunction shift(Holder<NormalNoise.NoiseParameters> noiseData) {
        return new DensityFunctions.Shift(new DensityFunction.NoiseHolder(noiseData));
    }

    public static DensityFunction blendDensity(DensityFunction input) {
        return new DensityFunctions.BlendDensity(input);
    }

    public static DensityFunction endIslands(long seed) {
        return new DensityFunctions.EndIslandDensityFunction(seed);
    }

    public static DensityFunction weirdScaledSampler(
        DensityFunction input, Holder<NormalNoise.NoiseParameters> noiseData, DensityFunctions.WeirdScaledSampler.RarityValueMapper rarityValueMapper
    ) {
        return new DensityFunctions.WeirdScaledSampler(input, new DensityFunction.NoiseHolder(noiseData), rarityValueMapper);
    }

    public static DensityFunction add(DensityFunction argument1, DensityFunction argument2) {
        return DensityFunctions.TwoArgumentSimpleFunction.create(DensityFunctions.TwoArgumentSimpleFunction.Type.ADD, argument1, argument2);
    }

    public static DensityFunction mul(DensityFunction argument1, DensityFunction argument2) {
        return DensityFunctions.TwoArgumentSimpleFunction.create(DensityFunctions.TwoArgumentSimpleFunction.Type.MUL, argument1, argument2);
    }

    public static DensityFunction min(DensityFunction argument1, DensityFunction argument2) {
        return DensityFunctions.TwoArgumentSimpleFunction.create(DensityFunctions.TwoArgumentSimpleFunction.Type.MIN, argument1, argument2);
    }

    public static DensityFunction max(DensityFunction argument1, DensityFunction argument2) {
        return DensityFunctions.TwoArgumentSimpleFunction.create(DensityFunctions.TwoArgumentSimpleFunction.Type.MAX, argument1, argument2);
    }

    public static DensityFunction spline(CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> spline) {
        return new DensityFunctions.Spline(spline);
    }

    public static DensityFunction zero() {
        return DensityFunctions.Constant.ZERO;
    }

    public static DensityFunction constant(double value) {
        return new DensityFunctions.Constant(value);
    }

    public static DensityFunction yClampedGradient(int fromY, int toY, double fromValue, double toValue) {
        return new DensityFunctions.YClampedGradient(fromY, toY, fromValue, toValue);
    }

    public static DensityFunction map(DensityFunction input, DensityFunctions.Mapped.Type type) {
        return DensityFunctions.Mapped.create(type, input);
    }

    private static DensityFunction mapFromUnitTo(DensityFunction densityFunction, double fromY, double toY) {
        double d = (fromY + toY) * 0.5;
        double d1 = (toY - fromY) * 0.5;
        return add(constant(d), mul(constant(d1), densityFunction));
    }

    public static DensityFunction blendAlpha() {
        return DensityFunctions.BlendAlpha.INSTANCE;
    }

    public static DensityFunction blendOffset() {
        return DensityFunctions.BlendOffset.INSTANCE;
    }

    public static DensityFunction lerp(DensityFunction deltaFunction, DensityFunction minFunction, DensityFunction maxFunction) {
        if (minFunction instanceof DensityFunctions.Constant constant) {
            return lerp(deltaFunction, constant.value, maxFunction);
        } else {
            DensityFunction densityFunction = cacheOnce(deltaFunction);
            DensityFunction densityFunction1 = add(mul(densityFunction, constant(-1.0)), constant(1.0));
            return add(mul(minFunction, densityFunction1), mul(maxFunction, densityFunction));
        }
    }

    public static DensityFunction lerp(DensityFunction deltaFunction, double min, DensityFunction maxFunction) {
        return add(mul(deltaFunction, add(maxFunction, constant(-min))), constant(min));
    }

    public static DensityFunction findTopSurface(DensityFunction density, DensityFunction upperBound, int lowerBound, int cellHeight) {
        return new DensityFunctions.FindTopSurface(density, upperBound, lowerBound, cellHeight);
    }

    record Ap2(
        @Override DensityFunctions.TwoArgumentSimpleFunction.Type type,
        @Override DensityFunction argument1,
        @Override DensityFunction argument2,
        @Override double minValue,
        @Override double maxValue
    ) implements DensityFunctions.TwoArgumentSimpleFunction {
        @Override
        public double compute(DensityFunction.FunctionContext context) {
            double d = this.argument1.compute(context);

            return switch (this.type) {
                case ADD -> d + this.argument2.compute(context);
                case MUL -> d == 0.0 ? 0.0 : d * this.argument2.compute(context);
                case MIN -> d < this.argument2.minValue() ? d : Math.min(d, this.argument2.compute(context));
                case MAX -> d > this.argument2.maxValue() ? d : Math.max(d, this.argument2.compute(context));
            };
        }

        @Override
        public void fillArray(double[] array, DensityFunction.ContextProvider contextProvider) {
            this.argument1.fillArray(array, contextProvider);
            switch (this.type) {
                case ADD:
                    double[] doubles = new double[array.length];
                    this.argument2.fillArray(doubles, contextProvider);

                    for (int i = 0; i < array.length; i++) {
                        array[i] += doubles[i];
                    }
                    break;
                case MUL:
                    for (int i1 = 0; i1 < array.length; i1++) {
                        double d = array[i1];
                        array[i1] = d == 0.0 ? 0.0 : d * this.argument2.compute(contextProvider.forIndex(i1));
                    }
                    break;
                case MIN:
                    double d1 = this.argument2.minValue();

                    for (int i2 = 0; i2 < array.length; i2++) {
                        double d2 = array[i2];
                        array[i2] = d2 < d1 ? d2 : Math.min(d2, this.argument2.compute(contextProvider.forIndex(i2)));
                    }
                    break;
                case MAX:
                    d1 = this.argument2.maxValue();

                    for (int i2 = 0; i2 < array.length; i2++) {
                        double d2 = array[i2];
                        array[i2] = d2 > d1 ? d2 : Math.max(d2, this.argument2.compute(contextProvider.forIndex(i2)));
                    }
            }
        }

        @Override
        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(DensityFunctions.TwoArgumentSimpleFunction.create(this.type, this.argument1.mapAll(visitor), this.argument2.mapAll(visitor)));
        }
    }

    protected static enum BeardifierMarker implements DensityFunctions.BeardifierOrMarker {
        INSTANCE;

        @Override
        public double compute(DensityFunction.FunctionContext context) {
            return 0.0;
        }

        @Override
        public void fillArray(double[] array, DensityFunction.ContextProvider contextProvider) {
            Arrays.fill(array, 0.0);
        }

        @Override
        public double minValue() {
            return 0.0;
        }

        @Override
        public double maxValue() {
            return 0.0;
        }
    }

    public interface BeardifierOrMarker extends DensityFunction.SimpleFunction {
        KeyDispatchDataCodec<DensityFunction> CODEC = KeyDispatchDataCodec.of(MapCodec.unit(DensityFunctions.BeardifierMarker.INSTANCE));

        @Override
        default KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }
    }

    protected static enum BlendAlpha implements DensityFunction.SimpleFunction {
        INSTANCE;

        public static final KeyDispatchDataCodec<DensityFunction> CODEC = KeyDispatchDataCodec.of(MapCodec.unit(INSTANCE));

        @Override
        public double compute(DensityFunction.FunctionContext context) {
            return 1.0;
        }

        @Override
        public void fillArray(double[] array, DensityFunction.ContextProvider contextProvider) {
            Arrays.fill(array, 1.0);
        }

        @Override
        public double minValue() {
            return 1.0;
        }

        @Override
        public double maxValue() {
            return 1.0;
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }
    }

    record BlendDensity(@Override DensityFunction input) implements DensityFunctions.TransformerWithContext {
        static final KeyDispatchDataCodec<DensityFunctions.BlendDensity> CODEC = DensityFunctions.singleFunctionArgumentCodec(
            DensityFunctions.BlendDensity::new, DensityFunctions.BlendDensity::input
        );

        @Override
        public double transform(DensityFunction.FunctionContext context, double value) {
            return context.getBlender().blendDensity(context, value);
        }

        @Override
        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(new DensityFunctions.BlendDensity(this.input.mapAll(visitor)));
        }

        @Override
        public double minValue() {
            return Double.NEGATIVE_INFINITY;
        }

        @Override
        public double maxValue() {
            return Double.POSITIVE_INFINITY;
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }
    }

    protected static enum BlendOffset implements DensityFunction.SimpleFunction {
        INSTANCE;

        public static final KeyDispatchDataCodec<DensityFunction> CODEC = KeyDispatchDataCodec.of(MapCodec.unit(INSTANCE));

        @Override
        public double compute(DensityFunction.FunctionContext context) {
            return 0.0;
        }

        @Override
        public void fillArray(double[] array, DensityFunction.ContextProvider contextProvider) {
            Arrays.fill(array, 0.0);
        }

        @Override
        public double minValue() {
            return 0.0;
        }

        @Override
        public double maxValue() {
            return 0.0;
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }
    }

    protected record Clamp(@Override DensityFunction input, @Override double minValue, @Override double maxValue) implements DensityFunctions.PureTransformer {
        private static final MapCodec<DensityFunctions.Clamp> DATA_CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    DensityFunction.DIRECT_CODEC.fieldOf("input").forGetter(DensityFunctions.Clamp::input),
                    DensityFunctions.NOISE_VALUE_CODEC.fieldOf("min").forGetter(DensityFunctions.Clamp::minValue),
                    DensityFunctions.NOISE_VALUE_CODEC.fieldOf("max").forGetter(DensityFunctions.Clamp::maxValue)
                )
                .apply(instance, DensityFunctions.Clamp::new)
        );
        public static final KeyDispatchDataCodec<DensityFunctions.Clamp> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

        @Override
        public double transform(double value) {
            return Mth.clamp(value, this.minValue, this.maxValue);
        }

        @Override
        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return new DensityFunctions.Clamp(this.input.mapAll(visitor), this.minValue, this.maxValue);
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }
    }

    record Constant(double value) implements DensityFunction.SimpleFunction {
        static final KeyDispatchDataCodec<DensityFunctions.Constant> CODEC = DensityFunctions.singleArgumentCodec(
            DensityFunctions.NOISE_VALUE_CODEC, DensityFunctions.Constant::new, DensityFunctions.Constant::value
        );
        static final DensityFunctions.Constant ZERO = new DensityFunctions.Constant(0.0);

        @Override
        public double compute(DensityFunction.FunctionContext context) {
            return this.value;
        }

        @Override
        public void fillArray(double[] array, DensityFunction.ContextProvider contextProvider) {
            Arrays.fill(array, this.value);
        }

        @Override
        public double minValue() {
            return this.value;
        }

        @Override
        public double maxValue() {
            return this.value;
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }
    }

    protected static final class EndIslandDensityFunction implements DensityFunction.SimpleFunction {
        public static final KeyDispatchDataCodec<DensityFunctions.EndIslandDensityFunction> CODEC = KeyDispatchDataCodec.of(
            MapCodec.unit(new DensityFunctions.EndIslandDensityFunction(0L))
        );
        private static final float ISLAND_THRESHOLD = -0.9F;
        private final SimplexNoise islandNoise;
        // Paper start - Perf: Optimize end generation
        private static final class NoiseCache {
            public long[] keys = new long[8192];
            public float[] values = new float[8192];
            public NoiseCache() {
                java.util.Arrays.fill(keys, Long.MIN_VALUE);
            }
        }
        private static final ThreadLocal<java.util.Map<SimplexNoise, NoiseCache>> noiseCache = ThreadLocal.withInitial(java.util.WeakHashMap::new);
        // Paper end - Perf: Optimize end generation

        public EndIslandDensityFunction(long seed) {
            RandomSource randomSource = new LegacyRandomSource(seed);
            randomSource.consumeCount(17292);
            this.islandNoise = new SimplexNoise(randomSource);
        }

        private static float getHeightValue(SimplexNoise noise, int x, int z) {
            int i = x / 2;
            int i1 = z / 2;
            int i2 = x % 2;
            int i3 = z % 2;
            float f = 100.0F - (io.papermc.paper.configuration.GlobalConfiguration.get().misc.fixFarEndTerrainGeneration ? Mth.sqrt((long)x * (long)x + (long)z * (long)z) : Mth.sqrt(x * x + z * z)) * 8.0F; // Paper - cast ints to long when MC-159283 fix enabled
            f = Mth.clamp(f, -100.0F, 80.0F);

            NoiseCache cache = noiseCache.get().computeIfAbsent(noise, noiseKey -> new NoiseCache()); // Paper - Perf: Optimize end generation
            for (int i4 = -12; i4 <= 12; i4++) {
                for (int i5 = -12; i5 <= 12; i5++) {
                    long l = i + i4; final int chunkX = (int) l; // Paper - OBFHELPER
                    long l1 = i1 + i5; final int chunkZ = (int) l1; // Paper - OBFHELPER
                    // Paper start - Perf: Optimize end generation by using a noise cache
                    final long chunkKey = net.minecraft.world.level.ChunkPos.asLong(chunkX, chunkZ);
                    final int cacheIndex = (int) it.unimi.dsi.fastutil.HashCommon.mix(chunkKey) & 8191;
                    float f1 = Float.MIN_VALUE; // noise value
                    if (cache.keys[cacheIndex] == chunkKey) {
                        // Use cache
                        f1 = cache.values[cacheIndex];
                    } else {
                        // Vanilla function
                    if (l * l + l1 * l1 > 4096L && noise.getValue(l, l1) < -0.9F) {
                        f1 = (Mth.abs((float)l) * 3439.0F + Mth.abs((float)l1) * 147.0F) % 13.0F + 9.0F;
                    }
                        cache.keys[cacheIndex] = chunkKey;
                        cache.values[cacheIndex] = f1;
                    }
                    if (f1 != Float.MIN_VALUE) {
                        // Paper end - Perf: Optimize end generation
                        float f2 = i2 - i4 * 2;
                        float f3 = i3 - i5 * 2;
                        float f4 = 100.0F - Mth.sqrt(f2 * f2 + f3 * f3) * f1;
                        f4 = Mth.clamp(f4, -100.0F, 80.0F);
                        f = Math.max(f, f4);
                    }
                }
            }

            return f;
        }

        @Override
        public double compute(DensityFunction.FunctionContext context) {
            return (getHeightValue(this.islandNoise, context.blockX() / 8, context.blockZ() / 8) - 8.0) / 128.0;
        }

        @Override
        public double minValue() {
            return -0.84375;
        }

        @Override
        public double maxValue() {
            return 0.5625;
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }
    }

    record FindTopSurface(DensityFunction density, DensityFunction upperBound, int lowerBound, int cellHeight) implements DensityFunction {
        private static final MapCodec<DensityFunctions.FindTopSurface> DATA_CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("density").forGetter(DensityFunctions.FindTopSurface::density),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("upper_bound").forGetter(DensityFunctions.FindTopSurface::upperBound),
                    Codec.intRange(DimensionType.MIN_Y * 2, DimensionType.MAX_Y * 2)
                        .fieldOf("lower_bound")
                        .forGetter(DensityFunctions.FindTopSurface::lowerBound),
                    ExtraCodecs.POSITIVE_INT.fieldOf("cell_height").forGetter(DensityFunctions.FindTopSurface::cellHeight)
                )
                .apply(instance, DensityFunctions.FindTopSurface::new)
        );
        public static final KeyDispatchDataCodec<DensityFunctions.FindTopSurface> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

        @Override
        public double compute(DensityFunction.FunctionContext context) {
            int i = Mth.floor(this.upperBound.compute(context) / this.cellHeight) * this.cellHeight;
            if (i <= this.lowerBound) {
                return this.lowerBound;
            } else {
                for (int i1 = i; i1 >= this.lowerBound; i1 -= this.cellHeight) {
                    if (this.density.compute(new DensityFunction.SinglePointContext(context.blockX(), i1, context.blockZ())) > 0.0) {
                        return i1;
                    }
                }

                return this.lowerBound;
            }
        }

        @Override
        public void fillArray(double[] array, DensityFunction.ContextProvider contextProvider) {
            contextProvider.fillAllDirectly(array, this);
        }

        @Override
        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(
                new DensityFunctions.FindTopSurface(this.density.mapAll(visitor), this.upperBound.mapAll(visitor), this.lowerBound, this.cellHeight)
            );
        }

        @Override
        public double minValue() {
            return this.lowerBound;
        }

        @Override
        public double maxValue() {
            return Math.max((double)this.lowerBound, this.upperBound.maxValue());
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }
    }

    @VisibleForDebug
    public record HolderHolder(Holder<DensityFunction> function) implements DensityFunction {
        @Override
        public double compute(DensityFunction.FunctionContext context) {
            return this.function.value().compute(context);
        }

        @Override
        public void fillArray(double[] array, DensityFunction.ContextProvider contextProvider) {
            this.function.value().fillArray(array, contextProvider);
        }

        @Override
        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(new DensityFunctions.HolderHolder(new Holder.Direct<>(this.function.value().mapAll(visitor))));
        }

        @Override
        public double minValue() {
            return this.function.isBound() ? this.function.value().minValue() : Double.NEGATIVE_INFINITY;
        }

        @Override
        public double maxValue() {
            return this.function.isBound() ? this.function.value().maxValue() : Double.POSITIVE_INFINITY;
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            throw new UnsupportedOperationException("Calling .codec() on HolderHolder");
        }
    }

    protected record Mapped(DensityFunctions.Mapped.Type type, @Override DensityFunction input, @Override double minValue, @Override double maxValue)
        implements DensityFunctions.PureTransformer {
        public static DensityFunctions.Mapped create(DensityFunctions.Mapped.Type type, DensityFunction input) {
            double d = input.minValue();
            double d1 = input.maxValue();
            double d2 = transform(type, d);
            double d3 = transform(type, d1);
            if (type == DensityFunctions.Mapped.Type.INVERT) {
                return d < 0.0 && d1 > 0.0
                    ? new DensityFunctions.Mapped(type, input, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY)
                    : new DensityFunctions.Mapped(type, input, d3, d2);
            } else {
                return type != DensityFunctions.Mapped.Type.ABS && type != DensityFunctions.Mapped.Type.SQUARE
                    ? new DensityFunctions.Mapped(type, input, d2, d3)
                    : new DensityFunctions.Mapped(type, input, Math.max(0.0, d), Math.max(d2, d3));
            }
        }

        private static double transform(DensityFunctions.Mapped.Type type, double value) {
            return switch (type) {
                case ABS -> Math.abs(value);
                case SQUARE -> value * value;
                case CUBE -> value * value * value;
                case HALF_NEGATIVE -> value > 0.0 ? value : value * 0.5;
                case QUARTER_NEGATIVE -> value > 0.0 ? value : value * 0.25;
                case INVERT -> 1.0 / value;
                case SQUEEZE -> {
                    double d = Mth.clamp(value, -1.0, 1.0);
                    yield d / 2.0 - d * d * d / 24.0;
                }
            };
        }

        @Override
        public double transform(double value) {
            return transform(this.type, value);
        }

        @Override
        public DensityFunctions.Mapped mapAll(DensityFunction.Visitor visitor) {
            return create(this.type, this.input.mapAll(visitor));
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return this.type.codec;
        }

        static enum Type implements StringRepresentable {
            ABS("abs"),
            SQUARE("square"),
            CUBE("cube"),
            HALF_NEGATIVE("half_negative"),
            QUARTER_NEGATIVE("quarter_negative"),
            INVERT("invert"),
            SQUEEZE("squeeze");

            private final String name;
            final KeyDispatchDataCodec<DensityFunctions.Mapped> codec = DensityFunctions.singleFunctionArgumentCodec(
                input -> DensityFunctions.Mapped.create(this, input), DensityFunctions.Mapped::input
            );

            private Type(final String name) {
                this.name = name;
            }

            @Override
            public String getSerializedName() {
                return this.name;
            }
        }
    }

    protected record Marker(@Override DensityFunctions.Marker.Type type, @Override DensityFunction wrapped) implements DensityFunctions.MarkerOrMarked {
        @Override
        public double compute(DensityFunction.FunctionContext context) {
            return this.wrapped.compute(context);
        }

        @Override
        public void fillArray(double[] array, DensityFunction.ContextProvider contextProvider) {
            this.wrapped.fillArray(array, contextProvider);
        }

        @Override
        public double minValue() {
            return this.wrapped.minValue();
        }

        @Override
        public double maxValue() {
            return this.wrapped.maxValue();
        }

        static enum Type implements StringRepresentable {
            Interpolated("interpolated"),
            FlatCache("flat_cache"),
            Cache2D("cache_2d"),
            CacheOnce("cache_once"),
            CacheAllInCell("cache_all_in_cell");

            private final String name;
            final KeyDispatchDataCodec<DensityFunctions.MarkerOrMarked> codec = DensityFunctions.singleFunctionArgumentCodec(
                function -> new DensityFunctions.Marker(this, function), DensityFunctions.MarkerOrMarked::wrapped
            );

            private Type(final String name) {
                this.name = name;
            }

            @Override
            public String getSerializedName() {
                return this.name;
            }
        }
    }

    public interface MarkerOrMarked extends DensityFunction {
        DensityFunctions.Marker.Type type();

        DensityFunction wrapped();

        @Override
        default KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return this.type().codec;
        }

        @Override
        default DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(new DensityFunctions.Marker(this.type(), this.wrapped().mapAll(visitor)));
        }
    }

    record MulOrAdd(
        DensityFunctions.MulOrAdd.Type specificType, @Override DensityFunction input, @Override double minValue, @Override double maxValue, double argument
    ) implements DensityFunctions.PureTransformer, DensityFunctions.TwoArgumentSimpleFunction {
        @Override
        public DensityFunctions.TwoArgumentSimpleFunction.Type type() {
            return this.specificType == DensityFunctions.MulOrAdd.Type.MUL
                ? DensityFunctions.TwoArgumentSimpleFunction.Type.MUL
                : DensityFunctions.TwoArgumentSimpleFunction.Type.ADD;
        }

        @Override
        public DensityFunction argument1() {
            return DensityFunctions.constant(this.argument);
        }

        @Override
        public DensityFunction argument2() {
            return this.input;
        }

        @Override
        public double transform(double value) {
            return switch (this.specificType) {
                case MUL -> value * this.argument;
                case ADD -> value + this.argument;
            };
        }

        @Override
        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            DensityFunction densityFunction = this.input.mapAll(visitor);
            double d = densityFunction.minValue();
            double d1 = densityFunction.maxValue();
            double d2;
            double d3;
            if (this.specificType == DensityFunctions.MulOrAdd.Type.ADD) {
                d2 = d + this.argument;
                d3 = d1 + this.argument;
            } else if (this.argument >= 0.0) {
                d2 = d * this.argument;
                d3 = d1 * this.argument;
            } else {
                d2 = d1 * this.argument;
                d3 = d * this.argument;
            }

            return new DensityFunctions.MulOrAdd(this.specificType, densityFunction, d2, d3, this.argument);
        }

        static enum Type {
            MUL,
            ADD;
        }
    }

    protected record Noise(DensityFunction.NoiseHolder noise, @Deprecated double xzScale, double yScale) implements DensityFunction {
        public static final MapCodec<DensityFunctions.Noise> DATA_CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    DensityFunction.NoiseHolder.CODEC.fieldOf("noise").forGetter(DensityFunctions.Noise::noise),
                    Codec.DOUBLE.fieldOf("xz_scale").forGetter(DensityFunctions.Noise::xzScale),
                    Codec.DOUBLE.fieldOf("y_scale").forGetter(DensityFunctions.Noise::yScale)
                )
                .apply(instance, DensityFunctions.Noise::new)
        );
        public static final KeyDispatchDataCodec<DensityFunctions.Noise> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

        @Override
        public double compute(DensityFunction.FunctionContext context) {
            return this.noise.getValue(context.blockX() * this.xzScale, context.blockY() * this.yScale, context.blockZ() * this.xzScale);
        }

        @Override
        public void fillArray(double[] array, DensityFunction.ContextProvider contextProvider) {
            contextProvider.fillAllDirectly(array, this);
        }

        @Override
        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(new DensityFunctions.Noise(visitor.visitNoise(this.noise), this.xzScale, this.yScale));
        }

        @Override
        public double minValue() {
            return -this.maxValue();
        }

        @Override
        public double maxValue() {
            return this.noise.maxValue();
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }
    }

    interface PureTransformer extends DensityFunction {
        DensityFunction input();

        @Override
        default double compute(DensityFunction.FunctionContext context) {
            return this.transform(this.input().compute(context));
        }

        @Override
        default void fillArray(double[] array, DensityFunction.ContextProvider contextProvider) {
            this.input().fillArray(array, contextProvider);

            for (int i = 0; i < array.length; i++) {
                array[i] = this.transform(array[i]);
            }
        }

        double transform(double value);
    }

    record RangeChoice(DensityFunction input, double minInclusive, double maxExclusive, DensityFunction whenInRange, DensityFunction whenOutOfRange)
        implements DensityFunction {
        public static final MapCodec<DensityFunctions.RangeChoice> DATA_CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("input").forGetter(DensityFunctions.RangeChoice::input),
                    DensityFunctions.NOISE_VALUE_CODEC.fieldOf("min_inclusive").forGetter(DensityFunctions.RangeChoice::minInclusive),
                    DensityFunctions.NOISE_VALUE_CODEC.fieldOf("max_exclusive").forGetter(DensityFunctions.RangeChoice::maxExclusive),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("when_in_range").forGetter(DensityFunctions.RangeChoice::whenInRange),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("when_out_of_range").forGetter(DensityFunctions.RangeChoice::whenOutOfRange)
                )
                .apply(instance, DensityFunctions.RangeChoice::new)
        );
        public static final KeyDispatchDataCodec<DensityFunctions.RangeChoice> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

        @Override
        public double compute(DensityFunction.FunctionContext context) {
            double d = this.input.compute(context);
            return d >= this.minInclusive && d < this.maxExclusive ? this.whenInRange.compute(context) : this.whenOutOfRange.compute(context);
        }

        @Override
        public void fillArray(double[] array, DensityFunction.ContextProvider contextProvider) {
            this.input.fillArray(array, contextProvider);

            for (int i = 0; i < array.length; i++) {
                double d = array[i];
                if (d >= this.minInclusive && d < this.maxExclusive) {
                    array[i] = this.whenInRange.compute(contextProvider.forIndex(i));
                } else {
                    array[i] = this.whenOutOfRange.compute(contextProvider.forIndex(i));
                }
            }
        }

        @Override
        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(
                new DensityFunctions.RangeChoice(
                    this.input.mapAll(visitor), this.minInclusive, this.maxExclusive, this.whenInRange.mapAll(visitor), this.whenOutOfRange.mapAll(visitor)
                )
            );
        }

        @Override
        public double minValue() {
            return Math.min(this.whenInRange.minValue(), this.whenOutOfRange.minValue());
        }

        @Override
        public double maxValue() {
            return Math.max(this.whenInRange.maxValue(), this.whenOutOfRange.maxValue());
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }
    }

    protected record Shift(@Override DensityFunction.NoiseHolder offsetNoise) implements DensityFunctions.ShiftNoise {
        static final KeyDispatchDataCodec<DensityFunctions.Shift> CODEC = DensityFunctions.singleArgumentCodec(
            DensityFunction.NoiseHolder.CODEC, DensityFunctions.Shift::new, DensityFunctions.Shift::offsetNoise
        );

        @Override
        public double compute(DensityFunction.FunctionContext context) {
            return this.compute(context.blockX(), context.blockY(), context.blockZ());
        }

        @Override
        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(new DensityFunctions.Shift(visitor.visitNoise(this.offsetNoise)));
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }
    }

    protected record ShiftA(@Override DensityFunction.NoiseHolder offsetNoise) implements DensityFunctions.ShiftNoise {
        static final KeyDispatchDataCodec<DensityFunctions.ShiftA> CODEC = DensityFunctions.singleArgumentCodec(
            DensityFunction.NoiseHolder.CODEC, DensityFunctions.ShiftA::new, DensityFunctions.ShiftA::offsetNoise
        );

        @Override
        public double compute(DensityFunction.FunctionContext context) {
            return this.compute(context.blockX(), 0.0, context.blockZ());
        }

        @Override
        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(new DensityFunctions.ShiftA(visitor.visitNoise(this.offsetNoise)));
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }
    }

    protected record ShiftB(@Override DensityFunction.NoiseHolder offsetNoise) implements DensityFunctions.ShiftNoise {
        static final KeyDispatchDataCodec<DensityFunctions.ShiftB> CODEC = DensityFunctions.singleArgumentCodec(
            DensityFunction.NoiseHolder.CODEC, DensityFunctions.ShiftB::new, DensityFunctions.ShiftB::offsetNoise
        );

        @Override
        public double compute(DensityFunction.FunctionContext context) {
            return this.compute(context.blockZ(), context.blockX(), 0.0);
        }

        @Override
        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(new DensityFunctions.ShiftB(visitor.visitNoise(this.offsetNoise)));
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }
    }

    interface ShiftNoise extends DensityFunction {
        DensityFunction.NoiseHolder offsetNoise();

        @Override
        default double minValue() {
            return -this.maxValue();
        }

        @Override
        default double maxValue() {
            return this.offsetNoise().maxValue() * 4.0;
        }

        default double compute(double x, double y, double z) {
            return this.offsetNoise().getValue(x * 0.25, y * 0.25, z * 0.25) * 4.0;
        }

        @Override
        default void fillArray(double[] array, DensityFunction.ContextProvider contextProvider) {
            contextProvider.fillAllDirectly(array, this);
        }
    }

    protected record ShiftedNoise(
        DensityFunction shiftX, DensityFunction shiftY, DensityFunction shiftZ, double xzScale, double yScale, DensityFunction.NoiseHolder noise
    ) implements DensityFunction {
        private static final MapCodec<DensityFunctions.ShiftedNoise> DATA_CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("shift_x").forGetter(DensityFunctions.ShiftedNoise::shiftX),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("shift_y").forGetter(DensityFunctions.ShiftedNoise::shiftY),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("shift_z").forGetter(DensityFunctions.ShiftedNoise::shiftZ),
                    Codec.DOUBLE.fieldOf("xz_scale").forGetter(DensityFunctions.ShiftedNoise::xzScale),
                    Codec.DOUBLE.fieldOf("y_scale").forGetter(DensityFunctions.ShiftedNoise::yScale),
                    DensityFunction.NoiseHolder.CODEC.fieldOf("noise").forGetter(DensityFunctions.ShiftedNoise::noise)
                )
                .apply(instance, DensityFunctions.ShiftedNoise::new)
        );
        public static final KeyDispatchDataCodec<DensityFunctions.ShiftedNoise> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

        @Override
        public double compute(DensityFunction.FunctionContext context) {
            double d = context.blockX() * this.xzScale + this.shiftX.compute(context);
            double d1 = context.blockY() * this.yScale + this.shiftY.compute(context);
            double d2 = context.blockZ() * this.xzScale + this.shiftZ.compute(context);
            return this.noise.getValue(d, d1, d2);
        }

        @Override
        public void fillArray(double[] array, DensityFunction.ContextProvider contextProvider) {
            contextProvider.fillAllDirectly(array, this);
        }

        @Override
        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(
                new DensityFunctions.ShiftedNoise(
                    this.shiftX.mapAll(visitor),
                    this.shiftY.mapAll(visitor),
                    this.shiftZ.mapAll(visitor),
                    this.xzScale,
                    this.yScale,
                    visitor.visitNoise(this.noise)
                )
            );
        }

        @Override
        public double minValue() {
            return -this.maxValue();
        }

        @Override
        public double maxValue() {
            return this.noise.maxValue();
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }
    }

    public record Spline(CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> spline) implements DensityFunction {
        private static final Codec<CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate>> SPLINE_CODEC = CubicSpline.codec(
            DensityFunctions.Spline.Coordinate.CODEC
        );
        private static final MapCodec<DensityFunctions.Spline> DATA_CODEC = SPLINE_CODEC.fieldOf("spline")
            .xmap(DensityFunctions.Spline::new, DensityFunctions.Spline::spline);
        public static final KeyDispatchDataCodec<DensityFunctions.Spline> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

        @Override
        public double compute(DensityFunction.FunctionContext context) {
            return this.spline.apply(new DensityFunctions.Spline.Point(context));
        }

        @Override
        public double minValue() {
            return this.spline.minValue();
        }

        @Override
        public double maxValue() {
            return this.spline.maxValue();
        }

        @Override
        public void fillArray(double[] array, DensityFunction.ContextProvider contextProvider) {
            contextProvider.fillAllDirectly(array, this);
        }

        @Override
        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(new DensityFunctions.Spline(this.spline.mapAll(coordinate -> coordinate.mapAll(visitor))));
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }

        public record Coordinate(Holder<DensityFunction> function) implements BoundedFloatFunction<DensityFunctions.Spline.Point> {
            public static final Codec<DensityFunctions.Spline.Coordinate> CODEC = DensityFunction.CODEC
                .xmap(DensityFunctions.Spline.Coordinate::new, DensityFunctions.Spline.Coordinate::function);

            @Override
            public String toString() {
                Optional<ResourceKey<DensityFunction>> optional = this.function.unwrapKey();
                if (optional.isPresent()) {
                    ResourceKey<DensityFunction> resourceKey = optional.get();
                    if (resourceKey == NoiseRouterData.CONTINENTS) {
                        return "continents";
                    }

                    if (resourceKey == NoiseRouterData.EROSION) {
                        return "erosion";
                    }

                    if (resourceKey == NoiseRouterData.RIDGES) {
                        return "weirdness";
                    }

                    if (resourceKey == NoiseRouterData.RIDGES_FOLDED) {
                        return "ridges";
                    }
                }

                return "Coordinate[" + this.function + "]";
            }

            @Override
            public float apply(DensityFunctions.Spline.Point value) {
                return (float)this.function.value().compute(value.context());
            }

            @Override
            public float minValue() {
                return this.function.isBound() ? (float)this.function.value().minValue() : Float.NEGATIVE_INFINITY;
            }

            @Override
            public float maxValue() {
                return this.function.isBound() ? (float)this.function.value().maxValue() : Float.POSITIVE_INFINITY;
            }

            public DensityFunctions.Spline.Coordinate mapAll(DensityFunction.Visitor visitor) {
                return new DensityFunctions.Spline.Coordinate(new Holder.Direct<>(this.function.value().mapAll(visitor)));
            }
        }

        public record Point(DensityFunction.FunctionContext context) {
        }
    }

    interface TransformerWithContext extends DensityFunction {
        DensityFunction input();

        @Override
        default double compute(DensityFunction.FunctionContext context) {
            return this.transform(context, this.input().compute(context));
        }

        @Override
        default void fillArray(double[] array, DensityFunction.ContextProvider contextProvider) {
            this.input().fillArray(array, contextProvider);

            for (int i = 0; i < array.length; i++) {
                array[i] = this.transform(contextProvider.forIndex(i), array[i]);
            }
        }

        double transform(DensityFunction.FunctionContext context, double value);
    }

    interface TwoArgumentSimpleFunction extends DensityFunction {
        Logger LOGGER = LogUtils.getLogger();

        static DensityFunctions.TwoArgumentSimpleFunction create(
            DensityFunctions.TwoArgumentSimpleFunction.Type type, DensityFunction argument1, DensityFunction argument2
        ) {
            double d = argument1.minValue();
            double d1 = argument2.minValue();
            double d2 = argument1.maxValue();
            double d3 = argument2.maxValue();
            if (type == DensityFunctions.TwoArgumentSimpleFunction.Type.MIN || type == DensityFunctions.TwoArgumentSimpleFunction.Type.MAX) {
                boolean flag = d >= d3;
                boolean flag1 = d1 >= d2;
                if (flag || flag1) {
                    LOGGER.warn("Creating a {} function between two non-overlapping inputs: {} and {}", type, argument1, argument2);
                }
            }
            double d4 = switch (type) {
                case ADD -> d + d1;
                case MUL -> d > 0.0 && d1 > 0.0 ? d * d1 : (d2 < 0.0 && d3 < 0.0 ? d2 * d3 : Math.min(d * d3, d2 * d1));
                case MIN -> Math.min(d, d1);
                case MAX -> Math.max(d, d1);
            };

            double d5 = switch (type) {
                case ADD -> d2 + d3;
                case MUL -> d > 0.0 && d1 > 0.0 ? d2 * d3 : (d2 < 0.0 && d3 < 0.0 ? d * d1 : Math.max(d * d1, d2 * d3));
                case MIN -> Math.min(d2, d3);
                case MAX -> Math.max(d2, d3);
            };
            if (type == DensityFunctions.TwoArgumentSimpleFunction.Type.MUL || type == DensityFunctions.TwoArgumentSimpleFunction.Type.ADD) {
                if (argument1 instanceof DensityFunctions.Constant constant) {
                    return new DensityFunctions.MulOrAdd(
                        type == DensityFunctions.TwoArgumentSimpleFunction.Type.ADD ? DensityFunctions.MulOrAdd.Type.ADD : DensityFunctions.MulOrAdd.Type.MUL,
                        argument2,
                        d4,
                        d5,
                        constant.value
                    );
                }

                if (argument2 instanceof DensityFunctions.Constant constant) {
                    return new DensityFunctions.MulOrAdd(
                        type == DensityFunctions.TwoArgumentSimpleFunction.Type.ADD ? DensityFunctions.MulOrAdd.Type.ADD : DensityFunctions.MulOrAdd.Type.MUL,
                        argument1,
                        d4,
                        d5,
                        constant.value
                    );
                }
            }

            return new DensityFunctions.Ap2(type, argument1, argument2, d4, d5);
        }

        DensityFunctions.TwoArgumentSimpleFunction.Type type();

        DensityFunction argument1();

        DensityFunction argument2();

        @Override
        default KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return this.type().codec;
        }

        public static enum Type implements StringRepresentable {
            ADD("add"),
            MUL("mul"),
            MIN("min"),
            MAX("max");

            final KeyDispatchDataCodec<DensityFunctions.TwoArgumentSimpleFunction> codec = DensityFunctions.doubleFunctionArgumentCodec(
                (from, to) -> DensityFunctions.TwoArgumentSimpleFunction.create(this, from, to),
                DensityFunctions.TwoArgumentSimpleFunction::argument1,
                DensityFunctions.TwoArgumentSimpleFunction::argument2
            );
            private final String name;

            private Type(final String name) {
                this.name = name;
            }

            @Override
            public String getSerializedName() {
                return this.name;
            }
        }
    }

    protected record WeirdScaledSampler(
        @Override DensityFunction input, DensityFunction.NoiseHolder noise, DensityFunctions.WeirdScaledSampler.RarityValueMapper rarityValueMapper
    ) implements DensityFunctions.TransformerWithContext {
        private static final MapCodec<DensityFunctions.WeirdScaledSampler> DATA_CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("input").forGetter(DensityFunctions.WeirdScaledSampler::input),
                    DensityFunction.NoiseHolder.CODEC.fieldOf("noise").forGetter(DensityFunctions.WeirdScaledSampler::noise),
                    DensityFunctions.WeirdScaledSampler.RarityValueMapper.CODEC
                        .fieldOf("rarity_value_mapper")
                        .forGetter(DensityFunctions.WeirdScaledSampler::rarityValueMapper)
                )
                .apply(instance, DensityFunctions.WeirdScaledSampler::new)
        );
        public static final KeyDispatchDataCodec<DensityFunctions.WeirdScaledSampler> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

        @Override
        public double transform(DensityFunction.FunctionContext context, double value) {
            double d = this.rarityValueMapper.mapper.get(value);
            return d * Math.abs(this.noise.getValue(context.blockX() / d, context.blockY() / d, context.blockZ() / d));
        }

        @Override
        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(new DensityFunctions.WeirdScaledSampler(this.input.mapAll(visitor), visitor.visitNoise(this.noise), this.rarityValueMapper));
        }

        @Override
        public double minValue() {
            return 0.0;
        }

        @Override
        public double maxValue() {
            return this.rarityValueMapper.maxRarity * this.noise.maxValue();
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }

        public static enum RarityValueMapper implements StringRepresentable {
            TYPE1("type_1", NoiseRouterData.QuantizedSpaghettiRarity::getSpaghettiRarity3D, 2.0),
            TYPE2("type_2", NoiseRouterData.QuantizedSpaghettiRarity::getSphaghettiRarity2D, 3.0);

            public static final Codec<DensityFunctions.WeirdScaledSampler.RarityValueMapper> CODEC = StringRepresentable.fromEnum(
                DensityFunctions.WeirdScaledSampler.RarityValueMapper::values
            );
            private final String name;
            final Double2DoubleFunction mapper;
            final double maxRarity;

            private RarityValueMapper(final String name, final Double2DoubleFunction mapper, final double maxRarity) {
                this.name = name;
                this.mapper = mapper;
                this.maxRarity = maxRarity;
            }

            @Override
            public String getSerializedName() {
                return this.name;
            }
        }
    }

    record YClampedGradient(int fromY, int toY, double fromValue, double toValue) implements DensityFunction.SimpleFunction {
        private static final MapCodec<DensityFunctions.YClampedGradient> DATA_CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    Codec.intRange(DimensionType.MIN_Y * 2, DimensionType.MAX_Y * 2).fieldOf("from_y").forGetter(DensityFunctions.YClampedGradient::fromY),
                    Codec.intRange(DimensionType.MIN_Y * 2, DimensionType.MAX_Y * 2).fieldOf("to_y").forGetter(DensityFunctions.YClampedGradient::toY),
                    DensityFunctions.NOISE_VALUE_CODEC.fieldOf("from_value").forGetter(DensityFunctions.YClampedGradient::fromValue),
                    DensityFunctions.NOISE_VALUE_CODEC.fieldOf("to_value").forGetter(DensityFunctions.YClampedGradient::toValue)
                )
                .apply(instance, DensityFunctions.YClampedGradient::new)
        );
        public static final KeyDispatchDataCodec<DensityFunctions.YClampedGradient> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

        @Override
        public double compute(DensityFunction.FunctionContext context) {
            return Mth.clampedMap((double)context.blockY(), (double)this.fromY, (double)this.toY, this.fromValue, this.toValue);
        }

        @Override
        public double minValue() {
            return Math.min(this.fromValue, this.toValue);
        }

        @Override
        public double maxValue() {
            return Math.max(this.fromValue, this.toValue);
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }
    }
}
