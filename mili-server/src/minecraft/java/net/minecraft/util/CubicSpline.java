package net.minecraft.util;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.floats.FloatList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.lang3.mutable.MutableObject;

public interface CubicSpline<C, I extends BoundedFloatFunction<C>> extends BoundedFloatFunction<C> {
    @VisibleForDebug
    String parityString();

    CubicSpline<C, I> mapAll(CubicSpline.CoordinateVisitor<I> visitor);

    static <C, I extends BoundedFloatFunction<C>> Codec<CubicSpline<C, I>> codec(Codec<I> valueCodec) {
        MutableObject<Codec<CubicSpline<C, I>>> mutableObject = new MutableObject<>();

        record Point<C, I extends BoundedFloatFunction<C>>(float location, CubicSpline<C, I> value, float derivative) {
        }

        Codec<Point<C, I>> codec = RecordCodecBuilder.create(
            instance -> instance.group(
                    Codec.FLOAT.fieldOf("location").forGetter(Point::location),
                    Codec.lazyInitialized(mutableObject).fieldOf("value").forGetter(Point::value),
                    Codec.FLOAT.fieldOf("derivative").forGetter(Point::derivative)
                )
                .apply(instance, (location, value, derivative) -> new Point<>(location, value, derivative))
        );
        Codec<CubicSpline.Multipoint<C, I>> codec1 = RecordCodecBuilder.create(
            locations -> locations.group(
                    valueCodec.fieldOf("coordinate").forGetter(CubicSpline.Multipoint::coordinate),
                    ExtraCodecs.nonEmptyList(codec.listOf())
                        .fieldOf("points")
                        .forGetter(
                            multipoint -> IntStream.range(0, multipoint.locations.length)
                                .mapToObj(i -> new Point<>(multipoint.locations()[i], multipoint.values().get(i), multipoint.derivatives()[i]))
                                .toList()
                        )
                )
                .apply(locations, (coordinate, list) -> {
                    float[] floats = new float[list.size()];
                    ImmutableList.Builder<CubicSpline<C, I>> builder = ImmutableList.builder();
                    float[] floats1 = new float[list.size()];

                    for (int i = 0; i < list.size(); i++) {
                        Point<C, I> point = list.get(i);
                        floats[i] = point.location();
                        builder.add(point.value());
                        floats1[i] = point.derivative();
                    }

                    return CubicSpline.Multipoint.create(coordinate, floats, builder.build(), floats1);
                })
        );
        mutableObject.setValue(
            Codec.either(Codec.FLOAT, codec1)
                .xmap(
                    either -> either.map(CubicSpline.Constant::new, multipoint -> multipoint),
                    cubicSpline -> cubicSpline instanceof CubicSpline.Constant<C, I> constant
                        ? Either.left(constant.value())
                        : Either.right((CubicSpline.Multipoint<C, I>)cubicSpline)
                )
        );
        return mutableObject.get();
    }

    static <C, I extends BoundedFloatFunction<C>> CubicSpline<C, I> constant(float value) {
        return new CubicSpline.Constant<>(value);
    }

    static <C, I extends BoundedFloatFunction<C>> CubicSpline.Builder<C, I> builder(I coordinate) {
        return new CubicSpline.Builder<>(coordinate);
    }

    static <C, I extends BoundedFloatFunction<C>> CubicSpline.Builder<C, I> builder(I coordinate, BoundedFloatFunction<Float> valueTransformer) {
        return new CubicSpline.Builder<>(coordinate, valueTransformer);
    }

    public static final class Builder<C, I extends BoundedFloatFunction<C>> {
        private final I coordinate;
        private final BoundedFloatFunction<Float> valueTransformer;
        private final FloatList locations = new FloatArrayList();
        private final List<CubicSpline<C, I>> values = Lists.newArrayList();
        private final FloatList derivatives = new FloatArrayList();

        protected Builder(I coordinate) {
            this(coordinate, BoundedFloatFunction.IDENTITY);
        }

        protected Builder(I coordinate, BoundedFloatFunction<Float> valueTransformer) {
            this.coordinate = coordinate;
            this.valueTransformer = valueTransformer;
        }

        public CubicSpline.Builder<C, I> addPoint(float location, float value) {
            return this.addPoint(location, new CubicSpline.Constant<>(this.valueTransformer.apply(value)), 0.0F);
        }

        public CubicSpline.Builder<C, I> addPoint(float location, float value, float derivative) {
            return this.addPoint(location, new CubicSpline.Constant<>(this.valueTransformer.apply(value)), derivative);
        }

        public CubicSpline.Builder<C, I> addPoint(float location, CubicSpline<C, I> value) {
            return this.addPoint(location, value, 0.0F);
        }

        private CubicSpline.Builder<C, I> addPoint(float location, CubicSpline<C, I> value, float derivative) {
            if (!this.locations.isEmpty() && location <= this.locations.getFloat(this.locations.size() - 1)) {
                throw new IllegalArgumentException("Please register points in ascending order");
            } else {
                this.locations.add(location);
                this.values.add(value);
                this.derivatives.add(derivative);
                return this;
            }
        }

        public CubicSpline<C, I> build() {
            if (this.locations.isEmpty()) {
                throw new IllegalStateException("No elements added");
            } else {
                return CubicSpline.Multipoint.create(
                    this.coordinate, this.locations.toFloatArray(), ImmutableList.copyOf(this.values), this.derivatives.toFloatArray()
                );
            }
        }
    }

    @VisibleForDebug
    public record Constant<C, I extends BoundedFloatFunction<C>>(float value) implements CubicSpline<C, I> {
        @Override
        public float apply(C value) {
            return this.value;
        }

        @Override
        public String parityString() {
            return String.format(Locale.ROOT, "k=%.3f", this.value);
        }

        @Override
        public float minValue() {
            return this.value;
        }

        @Override
        public float maxValue() {
            return this.value;
        }

        @Override
        public CubicSpline<C, I> mapAll(CubicSpline.CoordinateVisitor<I> visitor) {
            return this;
        }
    }

    public interface CoordinateVisitor<I> {
        I visit(I coordinate);
    }

    @VisibleForDebug
    public record Multipoint<C, I extends BoundedFloatFunction<C>>(
        I coordinate, float[] locations, List<CubicSpline<C, I>> values, float[] derivatives, @Override float minValue, @Override float maxValue
    ) implements CubicSpline<C, I> {
        public Multipoint(I coordinate, float[] locations, List<CubicSpline<C, I>> values, float[] derivatives, float minValue, float maxValue) {
            validateSizes(locations, values, derivatives);
            this.coordinate = coordinate;
            this.locations = locations;
            this.values = values;
            this.derivatives = derivatives;
            this.minValue = minValue;
            this.maxValue = maxValue;
        }

        static <C, I extends BoundedFloatFunction<C>> CubicSpline.Multipoint<C, I> create(
            I coordinate, float[] locations, List<CubicSpline<C, I>> values, float[] derivatives
        ) {
            validateSizes(locations, values, derivatives);
            int i = locations.length - 1;
            float f = Float.POSITIVE_INFINITY;
            float f1 = Float.NEGATIVE_INFINITY;
            float f2 = coordinate.minValue();
            float f3 = coordinate.maxValue();
            if (f2 < locations[0]) {
                float f4 = linearExtend(f2, locations, values.get(0).minValue(), derivatives, 0);
                float f5 = linearExtend(f2, locations, values.get(0).maxValue(), derivatives, 0);
                f = Math.min(f, Math.min(f4, f5));
                f1 = Math.max(f1, Math.max(f4, f5));
            }

            if (f3 > locations[i]) {
                float f4 = linearExtend(f3, locations, values.get(i).minValue(), derivatives, i);
                float f5 = linearExtend(f3, locations, values.get(i).maxValue(), derivatives, i);
                f = Math.min(f, Math.min(f4, f5));
                f1 = Math.max(f1, Math.max(f4, f5));
            }

            for (CubicSpline<C, I> cubicSpline : values) {
                f = Math.min(f, cubicSpline.minValue());
                f1 = Math.max(f1, cubicSpline.maxValue());
            }

            for (int i1 = 0; i1 < i; i1++) {
                float f5 = locations[i1];
                float f6 = locations[i1 + 1];
                float f7 = f6 - f5;
                CubicSpline<C, I> cubicSpline1 = values.get(i1);
                CubicSpline<C, I> cubicSpline2 = values.get(i1 + 1);
                float f8 = cubicSpline1.minValue();
                float f9 = cubicSpline1.maxValue();
                float f10 = cubicSpline2.minValue();
                float f11 = cubicSpline2.maxValue();
                float f12 = derivatives[i1];
                float f13 = derivatives[i1 + 1];
                if (f12 != 0.0F || f13 != 0.0F) {
                    float f14 = f12 * f7;
                    float f15 = f13 * f7;
                    float min = Math.min(f8, f10);
                    float max = Math.max(f9, f11);
                    float f16 = f14 - f11 + f8;
                    float f17 = f14 - f10 + f9;
                    float f18 = -f15 + f10 - f9;
                    float f19 = -f15 + f11 - f8;
                    float min1 = Math.min(f16, f18);
                    float max1 = Math.max(f17, f19);
                    f = Math.min(f, min + 0.25F * min1);
                    f1 = Math.max(f1, max + 0.25F * max1);
                }
            }

            return new CubicSpline.Multipoint<>(coordinate, locations, values, derivatives, f, f1);
        }

        private static float linearExtend(float coordinate, float[] locations, float value, float[] derivatives, int index) {
            float f = derivatives[index];
            return f == 0.0F ? value : value + f * (coordinate - locations[index]);
        }

        private static <C, I extends BoundedFloatFunction<C>> void validateSizes(float[] locations, List<CubicSpline<C, I>> values, float[] derivatives) {
            if (locations.length != values.size() || locations.length != derivatives.length) {
                throw new IllegalArgumentException("All lengths must be equal, got: " + locations.length + " " + values.size() + " " + derivatives.length);
            } else if (locations.length == 0) {
                throw new IllegalArgumentException("Cannot create a multipoint spline with no points");
            }
        }

        @Override
        public float apply(C value) {
            float f = this.coordinate.apply(value);
            int i = findIntervalStart(this.locations, f);
            int i1 = this.locations.length - 1;
            if (i < 0) {
                return linearExtend(f, this.locations, this.values.get(0).apply(value), this.derivatives, 0);
            } else if (i == i1) {
                return linearExtend(f, this.locations, this.values.get(i1).apply(value), this.derivatives, i1);
            } else {
                float f1 = this.locations[i];
                float f2 = this.locations[i + 1];
                float f3 = (f - f1) / (f2 - f1);
                BoundedFloatFunction<C> boundedFloatFunction = (BoundedFloatFunction<C>)this.values.get(i);
                BoundedFloatFunction<C> boundedFloatFunction1 = (BoundedFloatFunction<C>)this.values.get(i + 1);
                float f4 = this.derivatives[i];
                float f5 = this.derivatives[i + 1];
                float f6 = boundedFloatFunction.apply(value);
                float f7 = boundedFloatFunction1.apply(value);
                float f8 = f4 * (f2 - f1) - (f7 - f6);
                float f9 = -f5 * (f2 - f1) + (f7 - f6);
                return Mth.lerp(f3, f6, f7) + f3 * (1.0F - f3) * Mth.lerp(f3, f8, f9);
            }
        }

        private static int findIntervalStart(float[] locations, float start) {
            return Mth.binarySearch(0, locations.length, i -> start < locations[i]) - 1;
        }

        @VisibleForTesting
        @Override
        public String parityString() {
            return "Spline{coordinate="
                + this.coordinate
                + ", locations="
                + this.toString(this.locations)
                + ", derivatives="
                + this.toString(this.derivatives)
                + ", values="
                + this.values.stream().map(CubicSpline::parityString).collect(Collectors.joining(", ", "[", "]"))
                + "}";
        }

        private String toString(float[] locations) {
            return "["
                + IntStream.range(0, locations.length)
                    .mapToDouble(i -> locations[i])
                    .mapToObj(d -> String.format(Locale.ROOT, "%.3f", d))
                    .collect(Collectors.joining(", "))
                + "]";
        }

        @Override
        public CubicSpline<C, I> mapAll(CubicSpline.CoordinateVisitor<I> visitor) {
            return create(
                visitor.visit(this.coordinate),
                this.locations,
                this.values().stream().map(cubicSpline -> cubicSpline.mapAll(visitor)).toList(),
                this.derivatives
            );
        }
    }
}
