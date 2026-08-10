package net.minecraft.world.level.levelgen.feature;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.apache.commons.lang3.mutable.MutableInt;
import org.slf4j.Logger;

public class FeatureCountTracker {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final LoadingCache<ServerLevel, FeatureCountTracker.LevelData> data = CacheBuilder.newBuilder()
        .weakKeys()
        .expireAfterAccess(5L, TimeUnit.MINUTES)
        .build(new CacheLoader<ServerLevel, FeatureCountTracker.LevelData>() {
            @Override
            public FeatureCountTracker.LevelData load(ServerLevel level) {
                return new FeatureCountTracker.LevelData(Object2IntMaps.synchronize(new Object2IntOpenHashMap<>()), new MutableInt(0));
            }
        });

    public static void chunkDecorated(ServerLevel level) {
        try {
            data.get(level).chunksWithFeatures().increment();
        } catch (Exception var2) {
            LOGGER.error("Failed to increment chunk count", (Throwable)var2);
        }
    }

    public static void featurePlaced(ServerLevel level, ConfiguredFeature<?, ?> feature, Optional<PlacedFeature> topFeature) {
        try {
            data.get(level)
                .featureData()
                .computeInt(new FeatureCountTracker.FeatureData(feature, topFeature), (featureData, integer) -> integer == null ? 1 : integer + 1);
        } catch (Exception var4) {
            LOGGER.error("Failed to increment feature count", (Throwable)var4);
        }
    }

    public static void clearCounts() {
        data.invalidateAll();
        LOGGER.debug("Cleared feature counts");
    }

    public static void logCounts() {
        LOGGER.debug("Logging feature counts:");
        data.asMap()
            .forEach(
                (serverLevel, levelData) -> {
                    String string = serverLevel.dimension().identifier().toString();
                    boolean isRunning = serverLevel.getServer().isRunning();
                    Registry<PlacedFeature> registry = serverLevel.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE);
                    String string1 = (isRunning ? "running" : "dead") + " " + string;
                    int i = levelData.chunksWithFeatures().intValue();
                    LOGGER.debug("{} total_chunks: {}", string1, i);
                    levelData.featureData()
                        .forEach(
                            (featureData, i1) -> LOGGER.debug(
                                "{} {} {} {} {} {}",
                                string1,
                                String.format(Locale.ROOT, "%10d", i1),
                                String.format(Locale.ROOT, "%10f", (double)i1 / i),
                                featureData.topFeature().flatMap(registry::getResourceKey).map(ResourceKey::identifier),
                                featureData.feature().feature(),
                                featureData.feature()
                            )
                        );
                }
            );
    }

    record FeatureData(ConfiguredFeature<?, ?> feature, Optional<PlacedFeature> topFeature) {
    }

    record LevelData(Object2IntMap<FeatureCountTracker.FeatureData> featureData, MutableInt chunksWithFeatures) {
    }
}
