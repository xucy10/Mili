package net.minecraft.data.worldgen.placement;

import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.features.CaveFeatures;
import net.minecraft.data.worldgen.features.VegetationFeatures;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.valueproviders.ClampedNormalInt;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.CountPlacement;
import net.minecraft.world.level.levelgen.placement.EnvironmentScanPlacement;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.InSquarePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.RandomOffsetPlacement;
import net.minecraft.world.level.levelgen.placement.RarityFilter;
import net.minecraft.world.level.levelgen.placement.SurfaceRelativeThresholdFilter;

public class CavePlacements {
    public static final ResourceKey<PlacedFeature> MONSTER_ROOM = PlacementUtils.createKey("monster_room");
    public static final ResourceKey<PlacedFeature> MONSTER_ROOM_DEEP = PlacementUtils.createKey("monster_room_deep");
    public static final ResourceKey<PlacedFeature> FOSSIL_UPPER = PlacementUtils.createKey("fossil_upper");
    public static final ResourceKey<PlacedFeature> FOSSIL_LOWER = PlacementUtils.createKey("fossil_lower");
    public static final ResourceKey<PlacedFeature> DRIPSTONE_CLUSTER = PlacementUtils.createKey("dripstone_cluster");
    public static final ResourceKey<PlacedFeature> LARGE_DRIPSTONE = PlacementUtils.createKey("large_dripstone");
    public static final ResourceKey<PlacedFeature> POINTED_DRIPSTONE = PlacementUtils.createKey("pointed_dripstone");
    public static final ResourceKey<PlacedFeature> UNDERWATER_MAGMA = PlacementUtils.createKey("underwater_magma");
    public static final ResourceKey<PlacedFeature> GLOW_LICHEN = PlacementUtils.createKey("glow_lichen");
    public static final ResourceKey<PlacedFeature> ROOTED_AZALEA_TREE = PlacementUtils.createKey("rooted_azalea_tree");
    public static final ResourceKey<PlacedFeature> CAVE_VINES = PlacementUtils.createKey("cave_vines");
    public static final ResourceKey<PlacedFeature> LUSH_CAVES_VEGETATION = PlacementUtils.createKey("lush_caves_vegetation");
    public static final ResourceKey<PlacedFeature> LUSH_CAVES_CLAY = PlacementUtils.createKey("lush_caves_clay");
    public static final ResourceKey<PlacedFeature> LUSH_CAVES_CEILING_VEGETATION = PlacementUtils.createKey("lush_caves_ceiling_vegetation");
    public static final ResourceKey<PlacedFeature> SPORE_BLOSSOM = PlacementUtils.createKey("spore_blossom");
    public static final ResourceKey<PlacedFeature> CLASSIC_VINES = PlacementUtils.createKey("classic_vines_cave_feature");
    public static final ResourceKey<PlacedFeature> AMETHYST_GEODE = PlacementUtils.createKey("amethyst_geode");
    public static final ResourceKey<PlacedFeature> SCULK_PATCH_DEEP_DARK = PlacementUtils.createKey("sculk_patch_deep_dark");
    public static final ResourceKey<PlacedFeature> SCULK_PATCH_ANCIENT_CITY = PlacementUtils.createKey("sculk_patch_ancient_city");
    public static final ResourceKey<PlacedFeature> SCULK_VEIN = PlacementUtils.createKey("sculk_vein");

    public static void bootstrap(BootstrapContext<PlacedFeature> context) {
        HolderGetter<ConfiguredFeature<?, ?>> holderGetter = context.lookup(Registries.CONFIGURED_FEATURE);
        Holder<ConfiguredFeature<?, ?>> orThrow = holderGetter.getOrThrow(CaveFeatures.MONSTER_ROOM);
        Holder<ConfiguredFeature<?, ?>> orThrow1 = holderGetter.getOrThrow(CaveFeatures.FOSSIL_COAL);
        Holder<ConfiguredFeature<?, ?>> orThrow2 = holderGetter.getOrThrow(CaveFeatures.FOSSIL_DIAMONDS);
        Holder<ConfiguredFeature<?, ?>> orThrow3 = holderGetter.getOrThrow(CaveFeatures.DRIPSTONE_CLUSTER);
        Holder<ConfiguredFeature<?, ?>> orThrow4 = holderGetter.getOrThrow(CaveFeatures.LARGE_DRIPSTONE);
        Holder<ConfiguredFeature<?, ?>> orThrow5 = holderGetter.getOrThrow(CaveFeatures.POINTED_DRIPSTONE);
        Holder<ConfiguredFeature<?, ?>> orThrow6 = holderGetter.getOrThrow(CaveFeatures.UNDERWATER_MAGMA);
        Holder<ConfiguredFeature<?, ?>> orThrow7 = holderGetter.getOrThrow(CaveFeatures.GLOW_LICHEN);
        Holder<ConfiguredFeature<?, ?>> orThrow8 = holderGetter.getOrThrow(CaveFeatures.ROOTED_AZALEA_TREE);
        Holder<ConfiguredFeature<?, ?>> orThrow9 = holderGetter.getOrThrow(CaveFeatures.CAVE_VINE);
        Holder<ConfiguredFeature<?, ?>> orThrow10 = holderGetter.getOrThrow(CaveFeatures.MOSS_PATCH);
        Holder<ConfiguredFeature<?, ?>> orThrow11 = holderGetter.getOrThrow(CaveFeatures.LUSH_CAVES_CLAY);
        Holder<ConfiguredFeature<?, ?>> orThrow12 = holderGetter.getOrThrow(CaveFeatures.MOSS_PATCH_CEILING);
        Holder<ConfiguredFeature<?, ?>> orThrow13 = holderGetter.getOrThrow(CaveFeatures.SPORE_BLOSSOM);
        Holder<ConfiguredFeature<?, ?>> orThrow14 = holderGetter.getOrThrow(VegetationFeatures.VINES);
        Holder<ConfiguredFeature<?, ?>> orThrow15 = holderGetter.getOrThrow(CaveFeatures.AMETHYST_GEODE);
        Holder<ConfiguredFeature<?, ?>> orThrow16 = holderGetter.getOrThrow(CaveFeatures.SCULK_PATCH_DEEP_DARK);
        Holder<ConfiguredFeature<?, ?>> orThrow17 = holderGetter.getOrThrow(CaveFeatures.SCULK_PATCH_ANCIENT_CITY);
        Holder<ConfiguredFeature<?, ?>> orThrow18 = holderGetter.getOrThrow(CaveFeatures.SCULK_VEIN);
        PlacementUtils.register(
            context,
            MONSTER_ROOM,
            orThrow,
            CountPlacement.of(10),
            InSquarePlacement.spread(),
            HeightRangePlacement.uniform(VerticalAnchor.absolute(0), VerticalAnchor.top()),
            BiomeFilter.biome()
        );
        PlacementUtils.register(
            context,
            MONSTER_ROOM_DEEP,
            orThrow,
            CountPlacement.of(4),
            InSquarePlacement.spread(),
            HeightRangePlacement.uniform(VerticalAnchor.aboveBottom(6), VerticalAnchor.absolute(-1)),
            BiomeFilter.biome()
        );
        PlacementUtils.register(
            context,
            FOSSIL_UPPER,
            orThrow1,
            RarityFilter.onAverageOnceEvery(64),
            InSquarePlacement.spread(),
            HeightRangePlacement.uniform(VerticalAnchor.absolute(0), VerticalAnchor.top()),
            BiomeFilter.biome()
        );
        PlacementUtils.register(
            context,
            FOSSIL_LOWER,
            orThrow2,
            RarityFilter.onAverageOnceEvery(64),
            InSquarePlacement.spread(),
            HeightRangePlacement.uniform(VerticalAnchor.bottom(), VerticalAnchor.absolute(-8)),
            BiomeFilter.biome()
        );
        PlacementUtils.register(
            context,
            DRIPSTONE_CLUSTER,
            orThrow3,
            CountPlacement.of(UniformInt.of(48, 96)),
            InSquarePlacement.spread(),
            PlacementUtils.RANGE_BOTTOM_TO_MAX_TERRAIN_HEIGHT,
            BiomeFilter.biome()
        );
        PlacementUtils.register(
            context,
            LARGE_DRIPSTONE,
            orThrow4,
            CountPlacement.of(UniformInt.of(10, 48)),
            InSquarePlacement.spread(),
            PlacementUtils.RANGE_BOTTOM_TO_MAX_TERRAIN_HEIGHT,
            BiomeFilter.biome()
        );
        PlacementUtils.register(
            context,
            POINTED_DRIPSTONE,
            orThrow5,
            CountPlacement.of(UniformInt.of(192, 256)),
            InSquarePlacement.spread(),
            PlacementUtils.RANGE_BOTTOM_TO_MAX_TERRAIN_HEIGHT,
            CountPlacement.of(UniformInt.of(1, 5)),
            RandomOffsetPlacement.of(ClampedNormalInt.of(0.0F, 3.0F, -10, 10), ClampedNormalInt.of(0.0F, 0.6F, -2, 2)),
            BiomeFilter.biome()
        );
        PlacementUtils.register(
            context,
            UNDERWATER_MAGMA,
            orThrow6,
            CountPlacement.of(UniformInt.of(44, 52)),
            InSquarePlacement.spread(),
            PlacementUtils.RANGE_BOTTOM_TO_MAX_TERRAIN_HEIGHT,
            SurfaceRelativeThresholdFilter.of(Heightmap.Types.OCEAN_FLOOR_WG, Integer.MIN_VALUE, -2),
            BiomeFilter.biome()
        );
        PlacementUtils.register(
            context,
            GLOW_LICHEN,
            orThrow7,
            CountPlacement.of(UniformInt.of(104, 157)),
            PlacementUtils.RANGE_BOTTOM_TO_MAX_TERRAIN_HEIGHT,
            InSquarePlacement.spread(),
            SurfaceRelativeThresholdFilter.of(Heightmap.Types.OCEAN_FLOOR_WG, Integer.MIN_VALUE, -13),
            BiomeFilter.biome()
        );
        PlacementUtils.register(
            context,
            ROOTED_AZALEA_TREE,
            orThrow8,
            CountPlacement.of(UniformInt.of(1, 2)),
            InSquarePlacement.spread(),
            PlacementUtils.RANGE_BOTTOM_TO_MAX_TERRAIN_HEIGHT,
            EnvironmentScanPlacement.scanningFor(Direction.UP, BlockPredicate.solid(), BlockPredicate.ONLY_IN_AIR_PREDICATE, 12),
            RandomOffsetPlacement.vertical(ConstantInt.of(-1)),
            BiomeFilter.biome()
        );
        PlacementUtils.register(
            context,
            CAVE_VINES,
            orThrow9,
            CountPlacement.of(188),
            InSquarePlacement.spread(),
            PlacementUtils.RANGE_BOTTOM_TO_MAX_TERRAIN_HEIGHT,
            EnvironmentScanPlacement.scanningFor(Direction.UP, BlockPredicate.hasSturdyFace(Direction.DOWN), BlockPredicate.ONLY_IN_AIR_PREDICATE, 12),
            RandomOffsetPlacement.vertical(ConstantInt.of(-1)),
            BiomeFilter.biome()
        );
        PlacementUtils.register(
            context,
            LUSH_CAVES_VEGETATION,
            orThrow10,
            CountPlacement.of(125),
            InSquarePlacement.spread(),
            PlacementUtils.RANGE_BOTTOM_TO_MAX_TERRAIN_HEIGHT,
            EnvironmentScanPlacement.scanningFor(Direction.DOWN, BlockPredicate.solid(), BlockPredicate.ONLY_IN_AIR_PREDICATE, 12),
            RandomOffsetPlacement.vertical(ConstantInt.of(1)),
            BiomeFilter.biome()
        );
        PlacementUtils.register(
            context,
            LUSH_CAVES_CLAY,
            orThrow11,
            CountPlacement.of(62),
            InSquarePlacement.spread(),
            PlacementUtils.RANGE_BOTTOM_TO_MAX_TERRAIN_HEIGHT,
            EnvironmentScanPlacement.scanningFor(Direction.DOWN, BlockPredicate.solid(), BlockPredicate.ONLY_IN_AIR_PREDICATE, 12),
            RandomOffsetPlacement.vertical(ConstantInt.of(1)),
            BiomeFilter.biome()
        );
        PlacementUtils.register(
            context,
            LUSH_CAVES_CEILING_VEGETATION,
            orThrow12,
            CountPlacement.of(125),
            InSquarePlacement.spread(),
            PlacementUtils.RANGE_BOTTOM_TO_MAX_TERRAIN_HEIGHT,
            EnvironmentScanPlacement.scanningFor(Direction.UP, BlockPredicate.solid(), BlockPredicate.ONLY_IN_AIR_PREDICATE, 12),
            RandomOffsetPlacement.vertical(ConstantInt.of(-1)),
            BiomeFilter.biome()
        );
        PlacementUtils.register(
            context,
            SPORE_BLOSSOM,
            orThrow13,
            CountPlacement.of(25),
            InSquarePlacement.spread(),
            PlacementUtils.RANGE_BOTTOM_TO_MAX_TERRAIN_HEIGHT,
            EnvironmentScanPlacement.scanningFor(Direction.UP, BlockPredicate.solid(), BlockPredicate.ONLY_IN_AIR_PREDICATE, 12),
            RandomOffsetPlacement.vertical(ConstantInt.of(-1)),
            BiomeFilter.biome()
        );
        PlacementUtils.register(
            context,
            CLASSIC_VINES,
            orThrow14,
            CountPlacement.of(256),
            InSquarePlacement.spread(),
            PlacementUtils.RANGE_BOTTOM_TO_MAX_TERRAIN_HEIGHT,
            BiomeFilter.biome()
        );
        PlacementUtils.register(
            context,
            AMETHYST_GEODE,
            orThrow15,
            RarityFilter.onAverageOnceEvery(24),
            InSquarePlacement.spread(),
            HeightRangePlacement.uniform(VerticalAnchor.aboveBottom(6), VerticalAnchor.absolute(30)),
            BiomeFilter.biome()
        );
        PlacementUtils.register(
            context,
            SCULK_PATCH_DEEP_DARK,
            orThrow16,
            CountPlacement.of(ConstantInt.of(256)),
            InSquarePlacement.spread(),
            PlacementUtils.RANGE_BOTTOM_TO_MAX_TERRAIN_HEIGHT,
            BiomeFilter.biome()
        );
        PlacementUtils.register(context, SCULK_PATCH_ANCIENT_CITY, orThrow17);
        PlacementUtils.register(
            context,
            SCULK_VEIN,
            orThrow18,
            CountPlacement.of(UniformInt.of(204, 250)),
            InSquarePlacement.spread(),
            PlacementUtils.RANGE_BOTTOM_TO_MAX_TERRAIN_HEIGHT,
            BiomeFilter.biome()
        );
    }
}
