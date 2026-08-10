package net.minecraft.world.level.biome;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.ImmutableList.Builder;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.util.Graph;
import net.minecraft.util.Util;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.apache.commons.lang3.mutable.MutableInt;

public class FeatureSorter {
    public static <T> List<FeatureSorter.StepFeatureData> buildFeaturesPerStep(
        List<T> featureSetSources, Function<T, List<HolderSet<PlacedFeature>>> toFeatureSetFunction, boolean notRecursiveFlag
    ) {
        Object2IntMap<PlacedFeature> map = new Object2IntOpenHashMap<>();
        MutableInt mutableInt = new MutableInt(0);

        record FeatureData(int featureIndex, int step, PlacedFeature feature) {
        }

        Comparator<FeatureData> comparator = Comparator.comparingInt(FeatureData::step).thenComparingInt(FeatureData::featureIndex);
        Map<FeatureData, Set<FeatureData>> map1 = new TreeMap<>(comparator);
        int i = 0;

        for (T object : featureSetSources) {
            List<FeatureData> list = Lists.newArrayList();
            List<HolderSet<PlacedFeature>> list1 = toFeatureSetFunction.apply(object);
            i = Math.max(i, list1.size());

            for (int i1 = 0; i1 < list1.size(); i1++) {
                for (Holder<PlacedFeature> holder : list1.get(i1)) {
                    PlacedFeature placedFeature = holder.value();
                    list.add(new FeatureData(map.computeIfAbsent(placedFeature, key -> mutableInt.getAndIncrement()), i1, placedFeature));
                }
            }

            for (int i1 = 0; i1 < list.size(); i1++) {
                Set<FeatureData> set = map1.computeIfAbsent(list.get(i1), key -> new TreeSet<>(comparator));
                if (i1 < list.size() - 1) {
                    set.add(list.get(i1 + 1));
                }
            }
        }

        Set<FeatureData> set1 = new TreeSet<>(comparator);
        Set<FeatureData> set2 = new TreeSet<>(comparator);
        List<FeatureData> list = Lists.newArrayList();

        for (FeatureData featureData : map1.keySet()) {
            if (!set2.isEmpty()) {
                throw new IllegalStateException("You somehow broke the universe; DFS bork (iteration finished with non-empty in-progress vertex set");
            }

            if (!set1.contains(featureData) && Graph.depthFirstSearch(map1, set1, set2, list::add, featureData)) {
                if (!notRecursiveFlag) {
                    throw new IllegalStateException("Feature order cycle found");
                }

                List<T> list2 = new ArrayList<>(featureSetSources);

                int size;
                do {
                    size = list2.size();
                    ListIterator<T> listIterator = list2.listIterator();

                    while (listIterator.hasNext()) {
                        T object1 = listIterator.next();
                        listIterator.remove();

                        try {
                            buildFeaturesPerStep(list2, toFeatureSetFunction, false);
                        } catch (IllegalStateException var18) {
                            continue;
                        }

                        listIterator.add(object1);
                    }
                } while (size != list2.size());

                throw new IllegalStateException("Feature order cycle found, involved sources: " + list2);
            }
        }

        Collections.reverse(list);
        Builder<FeatureSorter.StepFeatureData> builder = ImmutableList.builder();

        for (int i1x = 0; i1x < i; i1x++) {
            int i2 = i1x;
            List<PlacedFeature> list3 = list.stream().filter(featureData1 -> featureData1.step() == i2).map(FeatureData::feature).collect(Collectors.toList());
            builder.add(new FeatureSorter.StepFeatureData(list3));
        }

        return builder.build();
    }

    public record StepFeatureData(List<PlacedFeature> features, ToIntFunction<PlacedFeature> indexMapping) {
        StepFeatureData(List<PlacedFeature> features) {
            this(features, Util.createIndexIdentityLookup(features));
        }
    }
}
