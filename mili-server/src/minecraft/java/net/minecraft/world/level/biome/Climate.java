package net.minecraft.world.level.biome;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import org.jspecify.annotations.Nullable;

public class Climate {
    private static final boolean DEBUG_SLOW_BIOME_SEARCH = false;
    private static final float QUANTIZATION_FACTOR = 10000.0F;
    @VisibleForTesting
    protected static final int PARAMETER_COUNT = 7;

    public static Climate.TargetPoint target(float temperature, float humidity, float continentalness, float erosion, float depth, float weirdness) {
        return new Climate.TargetPoint(
            quantizeCoord(temperature),
            quantizeCoord(humidity),
            quantizeCoord(continentalness),
            quantizeCoord(erosion),
            quantizeCoord(depth),
            quantizeCoord(weirdness)
        );
    }

    public static Climate.ParameterPoint parameters(
        float temperature, float humidity, float continentalness, float erosion, float depth, float weirdness, float offset
    ) {
        return new Climate.ParameterPoint(
            Climate.Parameter.point(temperature),
            Climate.Parameter.point(humidity),
            Climate.Parameter.point(continentalness),
            Climate.Parameter.point(erosion),
            Climate.Parameter.point(depth),
            Climate.Parameter.point(weirdness),
            quantizeCoord(offset)
        );
    }

    public static Climate.ParameterPoint parameters(
        Climate.Parameter temperature,
        Climate.Parameter humidity,
        Climate.Parameter continentalness,
        Climate.Parameter erosion,
        Climate.Parameter depth,
        Climate.Parameter weirdness,
        float offset
    ) {
        return new Climate.ParameterPoint(temperature, humidity, continentalness, erosion, depth, weirdness, quantizeCoord(offset));
    }

    public static long quantizeCoord(float coord) {
        return (long)(coord * 10000.0F);
    }

    public static float unquantizeCoord(long coord) {
        return (float)coord / 10000.0F;
    }

    public static Climate.Sampler empty() {
        DensityFunction densityFunction = DensityFunctions.zero();
        return new Climate.Sampler(densityFunction, densityFunction, densityFunction, densityFunction, densityFunction, densityFunction, List.of());
    }

    public static BlockPos findSpawnPosition(List<Climate.ParameterPoint> points, Climate.Sampler sampler) {
        return (new Climate.SpawnFinder(points, sampler)).result.location();
    }

    interface DistanceMetric<T> {
        long distance(Climate.RTree.Node<T> node, long[] searchedValues);
    }

    public record Parameter(long min, long max) {
        public static final Codec<Climate.Parameter> CODEC = ExtraCodecs.intervalCodec(
            Codec.floatRange(-2.0F, 2.0F),
            "min",
            "max",
            (min, max) -> min.compareTo(max) > 0
                ? DataResult.error(() -> "Cannon construct interval, min > max (" + min + " > " + max + ")")
                : DataResult.success(new Climate.Parameter(Climate.quantizeCoord(min), Climate.quantizeCoord(max))),
            climateParameter -> Climate.unquantizeCoord(climateParameter.min()),
            climateParameter -> Climate.unquantizeCoord(climateParameter.max())
        );

        public static Climate.Parameter point(float value) {
            return span(value, value);
        }

        public static Climate.Parameter span(float min, float max) {
            if (min > max) {
                throw new IllegalArgumentException("min > max: " + min + " " + max);
            } else {
                return new Climate.Parameter(Climate.quantizeCoord(min), Climate.quantizeCoord(max));
            }
        }

        public static Climate.Parameter span(Climate.Parameter min, Climate.Parameter max) {
            if (min.min() > max.max()) {
                throw new IllegalArgumentException("min > max: " + min + " " + max);
            } else {
                return new Climate.Parameter(min.min(), max.max());
            }
        }

        @Override
        public String toString() {
            return this.min == this.max ? String.format(Locale.ROOT, "%d", this.min) : String.format(Locale.ROOT, "[%d-%d]", this.min, this.max);
        }

        public long distance(long pointValue) {
            long l = pointValue - this.max;
            long l1 = this.min - pointValue;
            return l > 0L ? l : Math.max(l1, 0L);
        }

        public long distance(Climate.Parameter parameter) {
            long l = parameter.min() - this.max;
            long l1 = this.min - parameter.max();
            return l > 0L ? l : Math.max(l1, 0L);
        }

        public Climate.Parameter span(Climate.@Nullable Parameter param) {
            return param == null ? this : new Climate.Parameter(Math.min(this.min, param.min()), Math.max(this.max, param.max()));
        }
    }

    public static class ParameterList<T> {
        private final List<Pair<Climate.ParameterPoint, T>> values;
        private final Climate.RTree<T> index;

        public static <T> Codec<Climate.ParameterList<T>> codec(MapCodec<T> codec) {
            return ExtraCodecs.nonEmptyList(
                    RecordCodecBuilder.<Pair<Climate.ParameterPoint, T>>create(
                            instance -> instance.group(
                                    Climate.ParameterPoint.CODEC.fieldOf("parameters").forGetter(Pair::getFirst), codec.forGetter(Pair::getSecond)
                                )
                                .apply(instance, Pair::of)
                        )
                        .listOf()
                )
                .xmap(Climate.ParameterList::new, Climate.ParameterList::values);
        }

        public ParameterList(List<Pair<Climate.ParameterPoint, T>> values) {
            this.values = values;
            this.index = Climate.RTree.create(values);
        }

        public List<Pair<Climate.ParameterPoint, T>> values() {
            return this.values;
        }

        public T findValue(Climate.TargetPoint targetPoint) {
            return this.findValueIndex(targetPoint);
        }

        @VisibleForTesting
        public T findValueBruteForce(Climate.TargetPoint targetPoint) {
            Iterator<Pair<Climate.ParameterPoint, T>> iterator = this.values().iterator();
            Pair<Climate.ParameterPoint, T> pair = iterator.next();
            long l = pair.getFirst().fitness(targetPoint);
            T second = pair.getSecond();

            while (iterator.hasNext()) {
                Pair<Climate.ParameterPoint, T> pair1 = iterator.next();
                long l1 = pair1.getFirst().fitness(targetPoint);
                if (l1 < l) {
                    l = l1;
                    second = pair1.getSecond();
                }
            }

            return second;
        }

        public T findValueIndex(Climate.TargetPoint targetPoint) {
            return this.findValueIndex(targetPoint, Climate.RTree.Node::distance);
        }

        protected T findValueIndex(Climate.TargetPoint targetPoint, Climate.DistanceMetric<T> distanceMetric) {
            return this.index.search(targetPoint, distanceMetric);
        }
    }

    public record ParameterPoint(
        Climate.Parameter temperature,
        Climate.Parameter humidity,
        Climate.Parameter continentalness,
        Climate.Parameter erosion,
        Climate.Parameter depth,
        Climate.Parameter weirdness,
        long offset
    ) {
        public static final Codec<Climate.ParameterPoint> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Climate.Parameter.CODEC.fieldOf("temperature").forGetter(point -> point.temperature),
                    Climate.Parameter.CODEC.fieldOf("humidity").forGetter(point -> point.humidity),
                    Climate.Parameter.CODEC.fieldOf("continentalness").forGetter(point -> point.continentalness),
                    Climate.Parameter.CODEC.fieldOf("erosion").forGetter(point -> point.erosion),
                    Climate.Parameter.CODEC.fieldOf("depth").forGetter(point -> point.depth),
                    Climate.Parameter.CODEC.fieldOf("weirdness").forGetter(point -> point.weirdness),
                    Codec.floatRange(0.0F, 1.0F).fieldOf("offset").xmap(Climate::quantizeCoord, Climate::unquantizeCoord).forGetter(point -> point.offset)
                )
                .apply(instance, Climate.ParameterPoint::new)
        );

        long fitness(Climate.TargetPoint point) {
            return Mth.square(this.temperature.distance(point.temperature))
                + Mth.square(this.humidity.distance(point.humidity))
                + Mth.square(this.continentalness.distance(point.continentalness))
                + Mth.square(this.erosion.distance(point.erosion))
                + Mth.square(this.depth.distance(point.depth))
                + Mth.square(this.weirdness.distance(point.weirdness))
                + Mth.square(this.offset);
        }

        protected List<Climate.Parameter> parameterSpace() {
            return ImmutableList.of(
                this.temperature,
                this.humidity,
                this.continentalness,
                this.erosion,
                this.depth,
                this.weirdness,
                new Climate.Parameter(this.offset, this.offset)
            );
        }
    }

    protected static final class RTree<T> {
        private static final int CHILDREN_PER_NODE = 6;
        private final Climate.RTree.Node<T> root;
        private final ThreadLocal<Climate.RTree.@Nullable Leaf<T>> lastResult = new ThreadLocal<>();

        private RTree(Climate.RTree.Node<T> root) {
            this.root = root;
        }

        public static <T> Climate.RTree<T> create(List<Pair<Climate.ParameterPoint, T>> nodes) {
            if (nodes.isEmpty()) {
                throw new IllegalArgumentException("Need at least one value to build the search tree.");
            } else {
                int size = nodes.get(0).getFirst().parameterSpace().size();
                if (size != 7) {
                    throw new IllegalStateException("Expecting parameter space to be 7, got " + size);
                } else {
                    List<Climate.RTree.Leaf<T>> list = nodes.stream()
                        .map(node -> new Climate.RTree.Leaf<>(node.getFirst(), node.getSecond()))
                        .collect(Collectors.toCollection(ArrayList::new));
                    return new Climate.RTree<>(build(size, list));
                }
            }
        }

        private static <T> Climate.RTree.Node<T> build(int paramSpaceSize, List<? extends Climate.RTree.Node<T>> children) {
            if (children.isEmpty()) {
                throw new IllegalStateException("Need at least one child to build a node");
            } else if (children.size() == 1) {
                return (Climate.RTree.Node<T>)children.get(0);
            } else if (children.size() <= 6) {
                children.sort(Comparator.comparingLong(child -> {
                    long l2 = 0L;

                    for (int i2 = 0; i2 < paramSpaceSize; i2++) {
                        Climate.Parameter parameter = child.parameterSpace[i2];
                        l2 += Math.abs((parameter.min() + parameter.max()) / 2L);
                    }

                    return l2;
                }));
                return new Climate.RTree.SubTree<>(children);
            } else {
                long l = Long.MAX_VALUE;
                int i = -1;
                List<Climate.RTree.SubTree<T>> list = null;

                for (int i1 = 0; i1 < paramSpaceSize; i1++) {
                    sort(children, paramSpaceSize, i1, false);
                    List<Climate.RTree.SubTree<T>> list1 = bucketize(children);
                    long l1 = 0L;

                    for (Climate.RTree.SubTree<T> subTree : list1) {
                        l1 += cost(subTree.parameterSpace);
                    }

                    if (l > l1) {
                        l = l1;
                        i = i1;
                        list = list1;
                    }
                }

                sort(list, paramSpaceSize, i, true);
                return new Climate.RTree.SubTree<>(
                    list.stream().map(subTree1 -> build(paramSpaceSize, Arrays.asList(subTree1.children))).collect(Collectors.toList())
                );
            }
        }

        private static <T> void sort(List<? extends Climate.RTree.Node<T>> children, int paramSpaceSize, int size, boolean absolute) {
            Comparator<Climate.RTree.Node<T>> comparator = comparator(size, absolute);

            for (int i = 1; i < paramSpaceSize; i++) {
                comparator = comparator.thenComparing(comparator((size + i) % paramSpaceSize, absolute));
            }

            children.sort(comparator);
        }

        private static <T> Comparator<Climate.RTree.Node<T>> comparator(int size, boolean absolute) {
            return Comparator.comparingLong(node -> {
                Climate.Parameter parameter = node.parameterSpace[size];
                long l = (parameter.min() + parameter.max()) / 2L;
                return absolute ? Math.abs(l) : l;
            });
        }

        private static <T> List<Climate.RTree.SubTree<T>> bucketize(List<? extends Climate.RTree.Node<T>> nodes) {
            List<Climate.RTree.SubTree<T>> list = Lists.newArrayList();
            List<Climate.RTree.Node<T>> list1 = Lists.newArrayList();
            int i = (int)Math.pow(6.0, Math.floor(Math.log(nodes.size() - 0.01) / Math.log(6.0)));

            for (Climate.RTree.Node<T> node : nodes) {
                list1.add(node);
                if (list1.size() >= i) {
                    list.add(new Climate.RTree.SubTree<>(list1));
                    list1 = Lists.newArrayList();
                }
            }

            if (!list1.isEmpty()) {
                list.add(new Climate.RTree.SubTree<>(list1));
            }

            return list;
        }

        private static long cost(Climate.Parameter[] parameters) {
            long l = 0L;

            for (Climate.Parameter parameter : parameters) {
                l += Math.abs(parameter.max() - parameter.min());
            }

            return l;
        }

        static <T> List<Climate.Parameter> buildParameterSpace(List<? extends Climate.RTree.Node<T>> children) {
            if (children.isEmpty()) {
                throw new IllegalArgumentException("SubTree needs at least one child");
            } else {
                int i = 7;
                List<Climate.Parameter> list = Lists.newArrayList();

                for (int i1 = 0; i1 < 7; i1++) {
                    list.add(null);
                }

                for (Climate.RTree.Node<T> node : children) {
                    for (int i2 = 0; i2 < 7; i2++) {
                        list.set(i2, node.parameterSpace[i2].span(list.get(i2)));
                    }
                }

                return list;
            }
        }

        public T search(Climate.TargetPoint targetPoint, Climate.DistanceMetric<T> distanceMetric) {
            long[] longs = targetPoint.toParameterArray();
            Climate.RTree.Leaf<T> leaf = this.root.search(longs, this.lastResult.get(), distanceMetric);
            this.lastResult.set(leaf);
            return leaf.value;
        }

        static final class Leaf<T> extends Climate.RTree.Node<T> {
            final T value;

            Leaf(Climate.ParameterPoint parameters, T value) {
                super(parameters.parameterSpace());
                this.value = value;
            }

            @Override
            protected Climate.RTree.Leaf<T> search(long[] searchedValues, Climate.RTree.@Nullable Leaf<T> leaf, Climate.DistanceMetric<T> metric) {
                return this;
            }
        }

        abstract static class Node<T> {
            protected final Climate.Parameter[] parameterSpace;

            protected Node(List<Climate.Parameter> parameters) {
                this.parameterSpace = parameters.toArray(new Climate.Parameter[0]);
            }

            protected abstract Climate.RTree.Leaf<T> search(long[] searchedValues, Climate.RTree.@Nullable Leaf<T> leaf, Climate.DistanceMetric<T> metric);

            protected long distance(long[] values) {
                long l = 0L;

                for (int i = 0; i < 7; i++) {
                    l += Mth.square(this.parameterSpace[i].distance(values[i]));
                }

                return l;
            }

            @Override
            public String toString() {
                return Arrays.toString((Object[])this.parameterSpace);
            }
        }

        static final class SubTree<T> extends Climate.RTree.Node<T> {
            final Climate.RTree.Node<T>[] children;

            protected SubTree(List<? extends Climate.RTree.Node<T>> parameters) {
                this(Climate.RTree.buildParameterSpace(parameters), parameters);
            }

            protected SubTree(List<Climate.Parameter> parameters, List<? extends Climate.RTree.Node<T>> children) {
                super(parameters);
                this.children = children.toArray(new Climate.RTree.Node[0]);
            }

            @Override
            protected Climate.RTree.Leaf<T> search(long[] searchedValues, Climate.RTree.@Nullable Leaf<T> leaf, Climate.DistanceMetric<T> metric) {
                long l = leaf == null ? Long.MAX_VALUE : metric.distance(leaf, searchedValues);
                Climate.RTree.Leaf<T> leaf1 = leaf;

                for (Climate.RTree.Node<T> node : this.children) {
                    long l1 = metric.distance(node, searchedValues);
                    if (l > l1) {
                        Climate.RTree.Leaf<T> leaf2 = node.search(searchedValues, leaf1, metric);
                        long l2 = node == leaf2 ? l1 : metric.distance(leaf2, searchedValues);
                        if (l > l2) {
                            l = l2;
                            leaf1 = leaf2;
                        }
                    }
                }

                return leaf1;
            }
        }
    }

    public record Sampler(
        DensityFunction temperature,
        DensityFunction humidity,
        DensityFunction continentalness,
        DensityFunction erosion,
        DensityFunction depth,
        DensityFunction weirdness,
        List<Climate.ParameterPoint> spawnTarget
    ) {
        public Climate.TargetPoint sample(int x, int y, int z) {
            int blockPosX = QuartPos.toBlock(x);
            int blockPosY = QuartPos.toBlock(y);
            int blockPosZ = QuartPos.toBlock(z);
            DensityFunction.SinglePointContext singlePointContext = new DensityFunction.SinglePointContext(blockPosX, blockPosY, blockPosZ);
            return Climate.target(
                (float)this.temperature.compute(singlePointContext),
                (float)this.humidity.compute(singlePointContext),
                (float)this.continentalness.compute(singlePointContext),
                (float)this.erosion.compute(singlePointContext),
                (float)this.depth.compute(singlePointContext),
                (float)this.weirdness.compute(singlePointContext)
            );
        }

        public BlockPos findSpawnPosition() {
            return this.spawnTarget.isEmpty() ? BlockPos.ZERO : Climate.findSpawnPosition(this.spawnTarget, this);
        }
    }

    static class SpawnFinder {
        private static final long MAX_RADIUS = 2048L;
        Climate.SpawnFinder.Result result;

        SpawnFinder(List<Climate.ParameterPoint> points, Climate.Sampler sampler) {
            this.result = getSpawnPositionAndFitness(points, sampler, 0, 0);
            this.radialSearch(points, sampler, 2048.0F, 512.0F);
            this.radialSearch(points, sampler, 512.0F, 32.0F);
        }

        private void radialSearch(List<Climate.ParameterPoint> point, Climate.Sampler sampler, float max, float min) {
            float f = 0.0F;
            float f1 = min;
            BlockPos blockPos = this.result.location();

            while (f1 <= max) {
                int i = blockPos.getX() + (int)(Math.sin(f) * f1);
                int i1 = blockPos.getZ() + (int)(Math.cos(f) * f1);
                Climate.SpawnFinder.Result spawnPositionAndFitness = getSpawnPositionAndFitness(point, sampler, i, i1);
                if (spawnPositionAndFitness.fitness() < this.result.fitness()) {
                    this.result = spawnPositionAndFitness;
                }

                f += min / f1;
                if (f > Math.PI * 2) {
                    f = 0.0F;
                    f1 += min;
                }
            }
        }

        private static Climate.SpawnFinder.Result getSpawnPositionAndFitness(List<Climate.ParameterPoint> points, Climate.Sampler sampler, int x, int z) {
            Climate.TargetPoint targetPoint = sampler.sample(QuartPos.fromBlock(x), 0, QuartPos.fromBlock(z));
            Climate.TargetPoint targetPoint1 = new Climate.TargetPoint(
                targetPoint.temperature(), targetPoint.humidity(), targetPoint.continentalness(), targetPoint.erosion(), 0L, targetPoint.weirdness()
            );
            long l = Long.MAX_VALUE;

            for (Climate.ParameterPoint parameterPoint : points) {
                l = Math.min(l, parameterPoint.fitness(targetPoint1));
            }

            long l1 = Mth.square((long)x) + Mth.square((long)z);
            long l2 = l * Mth.square(2048L) + l1;
            return new Climate.SpawnFinder.Result(new BlockPos(x, 0, z), l2);
        }

        record Result(BlockPos location, long fitness) {
        }
    }

    public record TargetPoint(long temperature, long humidity, long continentalness, long erosion, long depth, long weirdness) {
        @VisibleForTesting
        protected long[] toParameterArray() {
            return new long[]{this.temperature, this.humidity, this.continentalness, this.erosion, this.depth, this.weirdness, 0L};
        }
    }
}
