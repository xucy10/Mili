package net.minecraft.data.worldgen.placement;

import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.features.NetherFeatures;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.valueproviders.BiasedToBottomInt;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.CountOnEveryLayerPlacement;
import net.minecraft.world.level.levelgen.placement.CountPlacement;
import net.minecraft.world.level.levelgen.placement.InSquarePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;

public class NetherPlacements {
    public static final ResourceKey<PlacedFeature> DELTA = PlacementUtils.createKey("delta");
    public static final ResourceKey<PlacedFeature> SMALL_BASALT_COLUMNS = PlacementUtils.createKey("small_basalt_columns");
    public static final ResourceKey<PlacedFeature> LARGE_BASALT_COLUMNS = PlacementUtils.createKey("large_basalt_columns");
    public static final ResourceKey<PlacedFeature> BASALT_BLOBS = PlacementUtils.createKey("basalt_blobs");
    public static final ResourceKey<PlacedFeature> BLACKSTONE_BLOBS = PlacementUtils.createKey("blackstone_blobs");
    public static final ResourceKey<PlacedFeature> GLOWSTONE_EXTRA = PlacementUtils.createKey("glowstone_extra");
    public static final ResourceKey<PlacedFeature> GLOWSTONE = PlacementUtils.createKey("glowstone");
    public static final ResourceKey<PlacedFeature> CRIMSON_FOREST_VEGETATION = PlacementUtils.createKey("crimson_forest_vegetation");
    public static final ResourceKey<PlacedFeature> WARPED_FOREST_VEGETATION = PlacementUtils.createKey("warped_forest_vegetation");
    public static final ResourceKey<PlacedFeature> NETHER_SPROUTS = PlacementUtils.createKey("nether_sprouts");
    public static final ResourceKey<PlacedFeature> TWISTING_VINES = PlacementUtils.createKey("twisting_vines");
    public static final ResourceKey<PlacedFeature> WEEPING_VINES = PlacementUtils.createKey("weeping_vines");
    public static final ResourceKey<PlacedFeature> PATCH_CRIMSON_ROOTS = PlacementUtils.createKey("patch_crimson_roots");
    public static final ResourceKey<PlacedFeature> BASALT_PILLAR = PlacementUtils.createKey("basalt_pillar");
    public static final ResourceKey<PlacedFeature> SPRING_DELTA = PlacementUtils.createKey("spring_delta");
    public static final ResourceKey<PlacedFeature> SPRING_CLOSED = PlacementUtils.createKey("spring_closed");
    public static final ResourceKey<PlacedFeature> SPRING_CLOSED_DOUBLE = PlacementUtils.createKey("spring_closed_double");
    public static final ResourceKey<PlacedFeature> SPRING_OPEN = PlacementUtils.createKey("spring_open");
    public static final ResourceKey<PlacedFeature> PATCH_SOUL_FIRE = PlacementUtils.createKey("patch_soul_fire");
    public static final ResourceKey<PlacedFeature> PATCH_FIRE = PlacementUtils.createKey("patch_fire");

    public static void bootstrap(BootstrapContext<PlacedFeature> context) {
        HolderGetter<ConfiguredFeature<?, ?>> holderGetter = context.lookup(Registries.CONFIGURED_FEATURE);
        Holder<ConfiguredFeature<?, ?>> orThrow = holderGetter.getOrThrow(NetherFeatures.DELTA);
        Holder<ConfiguredFeature<?, ?>> orThrow1 = holderGetter.getOrThrow(NetherFeatures.SMALL_BASALT_COLUMNS);
        Holder<ConfiguredFeature<?, ?>> orThrow2 = holderGetter.getOrThrow(NetherFeatures.LARGE_BASALT_COLUMNS);
        Holder<ConfiguredFeature<?, ?>> orThrow3 = holderGetter.getOrThrow(NetherFeatures.BASALT_BLOBS);
        Holder<ConfiguredFeature<?, ?>> orThrow4 = holderGetter.getOrThrow(NetherFeatures.BLACKSTONE_BLOBS);
        Holder<ConfiguredFeature<?, ?>> orThrow5 = holderGetter.getOrThrow(NetherFeatures.GLOWSTONE_EXTRA);
        Holder<ConfiguredFeature<?, ?>> orThrow6 = holderGetter.getOrThrow(NetherFeatures.CRIMSON_FOREST_VEGETATION);
        Holder<ConfiguredFeature<?, ?>> orThrow7 = holderGetter.getOrThrow(NetherFeatures.WARPED_FOREST_VEGETION);
        Holder<ConfiguredFeature<?, ?>> orThrow8 = holderGetter.getOrThrow(NetherFeatures.NETHER_SPROUTS);
        Holder<ConfiguredFeature<?, ?>> orThrow9 = holderGetter.getOrThrow(NetherFeatures.TWISTING_VINES);
        Holder<ConfiguredFeature<?, ?>> orThrow10 = holderGetter.getOrThrow(NetherFeatures.WEEPING_VINES);
        Holder<ConfiguredFeature<?, ?>> orThrow11 = holderGetter.getOrThrow(NetherFeatures.PATCH_CRIMSON_ROOTS);
        Holder<ConfiguredFeature<?, ?>> orThrow12 = holderGetter.getOrThrow(NetherFeatures.BASALT_PILLAR);
        Holder<ConfiguredFeature<?, ?>> orThrow13 = holderGetter.getOrThrow(NetherFeatures.SPRING_LAVA_NETHER);
        Holder<ConfiguredFeature<?, ?>> orThrow14 = holderGetter.getOrThrow(NetherFeatures.SPRING_NETHER_CLOSED);
        Holder<ConfiguredFeature<?, ?>> orThrow15 = holderGetter.getOrThrow(NetherFeatures.SPRING_NETHER_OPEN);
        Holder<ConfiguredFeature<?, ?>> orThrow16 = holderGetter.getOrThrow(NetherFeatures.PATCH_SOUL_FIRE);
        Holder<ConfiguredFeature<?, ?>> orThrow17 = holderGetter.getOrThrow(NetherFeatures.PATCH_FIRE);
        PlacementUtils.register(context, DELTA, orThrow, CountOnEveryLayerPlacement.of(40), BiomeFilter.biome());
        PlacementUtils.register(context, SMALL_BASALT_COLUMNS, orThrow1, CountOnEveryLayerPlacement.of(4), BiomeFilter.biome());
        PlacementUtils.register(context, LARGE_BASALT_COLUMNS, orThrow2, CountOnEveryLayerPlacement.of(2), BiomeFilter.biome());
        PlacementUtils.register(
            context, BASALT_BLOBS, orThrow3, CountPlacement.of(75), InSquarePlacement.spread(), PlacementUtils.FULL_RANGE, BiomeFilter.biome()
        );
        PlacementUtils.register(
            context, BLACKSTONE_BLOBS, orThrow4, CountPlacement.of(25), InSquarePlacement.spread(), PlacementUtils.FULL_RANGE, BiomeFilter.biome()
        );
        PlacementUtils.register(
            context,
            GLOWSTONE_EXTRA,
            orThrow5,
            CountPlacement.of(BiasedToBottomInt.of(0, 9)),
            InSquarePlacement.spread(),
            PlacementUtils.RANGE_4_4,
            BiomeFilter.biome()
        );
        PlacementUtils.register(context, GLOWSTONE, orThrow5, CountPlacement.of(10), InSquarePlacement.spread(), PlacementUtils.FULL_RANGE, BiomeFilter.biome());
        PlacementUtils.register(context, CRIMSON_FOREST_VEGETATION, orThrow6, CountOnEveryLayerPlacement.of(6), BiomeFilter.biome());
        PlacementUtils.register(context, WARPED_FOREST_VEGETATION, orThrow7, CountOnEveryLayerPlacement.of(5), BiomeFilter.biome());
        PlacementUtils.register(context, NETHER_SPROUTS, orThrow8, CountOnEveryLayerPlacement.of(4), BiomeFilter.biome());
        PlacementUtils.register(
            context, TWISTING_VINES, orThrow9, CountPlacement.of(10), InSquarePlacement.spread(), PlacementUtils.FULL_RANGE, BiomeFilter.biome()
        );
        PlacementUtils.register(
            context, WEEPING_VINES, orThrow10, CountPlacement.of(10), InSquarePlacement.spread(), PlacementUtils.FULL_RANGE, BiomeFilter.biome()
        );
        PlacementUtils.register(context, PATCH_CRIMSON_ROOTS, orThrow11, PlacementUtils.FULL_RANGE, BiomeFilter.biome());
        PlacementUtils.register(
            context, BASALT_PILLAR, orThrow12, CountPlacement.of(10), InSquarePlacement.spread(), PlacementUtils.FULL_RANGE, BiomeFilter.biome()
        );
        PlacementUtils.register(
            context, SPRING_DELTA, orThrow13, CountPlacement.of(16), InSquarePlacement.spread(), PlacementUtils.RANGE_4_4, BiomeFilter.biome()
        );
        PlacementUtils.register(
            context, SPRING_CLOSED, orThrow14, CountPlacement.of(16), InSquarePlacement.spread(), PlacementUtils.RANGE_10_10, BiomeFilter.biome()
        );
        PlacementUtils.register(
            context, SPRING_CLOSED_DOUBLE, orThrow14, CountPlacement.of(32), InSquarePlacement.spread(), PlacementUtils.RANGE_10_10, BiomeFilter.biome()
        );
        PlacementUtils.register(
            context, SPRING_OPEN, orThrow15, CountPlacement.of(8), InSquarePlacement.spread(), PlacementUtils.RANGE_4_4, BiomeFilter.biome()
        );
        List<PlacementModifier> list = List.of(
            CountPlacement.of(UniformInt.of(0, 5)), InSquarePlacement.spread(), PlacementUtils.RANGE_4_4, BiomeFilter.biome()
        );
        PlacementUtils.register(context, PATCH_SOUL_FIRE, orThrow16, list);
        PlacementUtils.register(context, PATCH_FIRE, orThrow17, list);
    }
}
