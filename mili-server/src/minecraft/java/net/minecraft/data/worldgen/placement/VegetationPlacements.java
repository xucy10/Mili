package net.minecraft.data.worldgen.placement;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.data.worldgen.features.VegetationFeatures;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.valueproviders.ClampedInt;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.BlockPredicateFilter;
import net.minecraft.world.level.levelgen.placement.CountPlacement;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.InSquarePlacement;
import net.minecraft.world.level.levelgen.placement.NoiseBasedCountPlacement;
import net.minecraft.world.level.levelgen.placement.NoiseThresholdCountPlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.RarityFilter;
import net.minecraft.world.level.levelgen.placement.SurfaceWaterDepthFilter;
import org.jspecify.annotations.Nullable;

public class VegetationPlacements {
    public static final ResourceKey<PlacedFeature> BAMBOO_LIGHT = PlacementUtils.createKey("bamboo_light");
    public static final ResourceKey<PlacedFeature> BAMBOO = PlacementUtils.createKey("bamboo");
    public static final ResourceKey<PlacedFeature> VINES = PlacementUtils.createKey("vines");
    public static final ResourceKey<PlacedFeature> PATCH_SUNFLOWER = PlacementUtils.createKey("patch_sunflower");
    public static final ResourceKey<PlacedFeature> PATCH_PUMPKIN = PlacementUtils.createKey("patch_pumpkin");
    public static final ResourceKey<PlacedFeature> PATCH_GRASS_PLAIN = PlacementUtils.createKey("patch_grass_plain");
    public static final ResourceKey<PlacedFeature> PATCH_GRASS_MEADOW = PlacementUtils.createKey("patch_grass_meadow");
    public static final ResourceKey<PlacedFeature> PATCH_GRASS_FOREST = PlacementUtils.createKey("patch_grass_forest");
    public static final ResourceKey<PlacedFeature> PATCH_GRASS_BADLANDS = PlacementUtils.createKey("patch_grass_badlands");
    public static final ResourceKey<PlacedFeature> PATCH_GRASS_SAVANNA = PlacementUtils.createKey("patch_grass_savanna");
    public static final ResourceKey<PlacedFeature> PATCH_GRASS_NORMAL = PlacementUtils.createKey("patch_grass_normal");
    public static final ResourceKey<PlacedFeature> PATCH_GRASS_TAIGA_2 = PlacementUtils.createKey("patch_grass_taiga_2");
    public static final ResourceKey<PlacedFeature> PATCH_GRASS_TAIGA = PlacementUtils.createKey("patch_grass_taiga");
    public static final ResourceKey<PlacedFeature> PATCH_GRASS_JUNGLE = PlacementUtils.createKey("patch_grass_jungle");
    public static final ResourceKey<PlacedFeature> GRASS_BONEMEAL = PlacementUtils.createKey("grass_bonemeal");
    public static final ResourceKey<PlacedFeature> PATCH_DEAD_BUSH_2 = PlacementUtils.createKey("patch_dead_bush_2");
    public static final ResourceKey<PlacedFeature> PATCH_DEAD_BUSH = PlacementUtils.createKey("patch_dead_bush");
    public static final ResourceKey<PlacedFeature> PATCH_DEAD_BUSH_BADLANDS = PlacementUtils.createKey("patch_dead_bush_badlands");
    public static final ResourceKey<PlacedFeature> PATCH_DRY_GRASS_BADLANDS = PlacementUtils.createKey("patch_dry_grass_badlands");
    public static final ResourceKey<PlacedFeature> PATCH_DRY_GRASS_DESERT = PlacementUtils.createKey("patch_dry_grass_desert");
    public static final ResourceKey<PlacedFeature> PATCH_MELON = PlacementUtils.createKey("patch_melon");
    public static final ResourceKey<PlacedFeature> PATCH_MELON_SPARSE = PlacementUtils.createKey("patch_melon_sparse");
    public static final ResourceKey<PlacedFeature> PATCH_BERRY_COMMON = PlacementUtils.createKey("patch_berry_common");
    public static final ResourceKey<PlacedFeature> PATCH_BERRY_RARE = PlacementUtils.createKey("patch_berry_rare");
    public static final ResourceKey<PlacedFeature> PATCH_WATERLILY = PlacementUtils.createKey("patch_waterlily");
    public static final ResourceKey<PlacedFeature> PATCH_TALL_GRASS_2 = PlacementUtils.createKey("patch_tall_grass_2");
    public static final ResourceKey<PlacedFeature> PATCH_TALL_GRASS = PlacementUtils.createKey("patch_tall_grass");
    public static final ResourceKey<PlacedFeature> PATCH_LARGE_FERN = PlacementUtils.createKey("patch_large_fern");
    public static final ResourceKey<PlacedFeature> PATCH_BUSH = PlacementUtils.createKey("patch_bush");
    public static final ResourceKey<PlacedFeature> PATCH_LEAF_LITTER = PlacementUtils.createKey("patch_leaf_litter");
    public static final ResourceKey<PlacedFeature> PATCH_CACTUS_DESERT = PlacementUtils.createKey("patch_cactus_desert");
    public static final ResourceKey<PlacedFeature> PATCH_CACTUS_DECORATED = PlacementUtils.createKey("patch_cactus_decorated");
    public static final ResourceKey<PlacedFeature> PATCH_SUGAR_CANE_SWAMP = PlacementUtils.createKey("patch_sugar_cane_swamp");
    public static final ResourceKey<PlacedFeature> PATCH_SUGAR_CANE_DESERT = PlacementUtils.createKey("patch_sugar_cane_desert");
    public static final ResourceKey<PlacedFeature> PATCH_SUGAR_CANE_BADLANDS = PlacementUtils.createKey("patch_sugar_cane_badlands");
    public static final ResourceKey<PlacedFeature> PATCH_SUGAR_CANE = PlacementUtils.createKey("patch_sugar_cane");
    public static final ResourceKey<PlacedFeature> PATCH_FIREFLY_BUSH_SWAMP = PlacementUtils.createKey("patch_firefly_bush_swamp");
    public static final ResourceKey<PlacedFeature> PATCH_FIREFLY_BUSH_NEAR_WATER_SWAMP = PlacementUtils.createKey("patch_firefly_bush_near_water_swamp");
    public static final ResourceKey<PlacedFeature> PATCH_FIREFLY_BUSH_NEAR_WATER = PlacementUtils.createKey("patch_firefly_bush_near_water");
    public static final ResourceKey<PlacedFeature> BROWN_MUSHROOM_NETHER = PlacementUtils.createKey("brown_mushroom_nether");
    public static final ResourceKey<PlacedFeature> RED_MUSHROOM_NETHER = PlacementUtils.createKey("red_mushroom_nether");
    public static final ResourceKey<PlacedFeature> BROWN_MUSHROOM_NORMAL = PlacementUtils.createKey("brown_mushroom_normal");
    public static final ResourceKey<PlacedFeature> RED_MUSHROOM_NORMAL = PlacementUtils.createKey("red_mushroom_normal");
    public static final ResourceKey<PlacedFeature> BROWN_MUSHROOM_TAIGA = PlacementUtils.createKey("brown_mushroom_taiga");
    public static final ResourceKey<PlacedFeature> RED_MUSHROOM_TAIGA = PlacementUtils.createKey("red_mushroom_taiga");
    public static final ResourceKey<PlacedFeature> BROWN_MUSHROOM_OLD_GROWTH = PlacementUtils.createKey("brown_mushroom_old_growth");
    public static final ResourceKey<PlacedFeature> RED_MUSHROOM_OLD_GROWTH = PlacementUtils.createKey("red_mushroom_old_growth");
    public static final ResourceKey<PlacedFeature> BROWN_MUSHROOM_SWAMP = PlacementUtils.createKey("brown_mushroom_swamp");
    public static final ResourceKey<PlacedFeature> RED_MUSHROOM_SWAMP = PlacementUtils.createKey("red_mushroom_swamp");
    public static final ResourceKey<PlacedFeature> FLOWER_WARM = PlacementUtils.createKey("flower_warm");
    public static final ResourceKey<PlacedFeature> FLOWER_DEFAULT = PlacementUtils.createKey("flower_default");
    public static final ResourceKey<PlacedFeature> FLOWER_FLOWER_FOREST = PlacementUtils.createKey("flower_flower_forest");
    public static final ResourceKey<PlacedFeature> FLOWER_SWAMP = PlacementUtils.createKey("flower_swamp");
    public static final ResourceKey<PlacedFeature> FLOWER_PLAINS = PlacementUtils.createKey("flower_plains");
    public static final ResourceKey<PlacedFeature> FLOWER_MEADOW = PlacementUtils.createKey("flower_meadow");
    public static final ResourceKey<PlacedFeature> FLOWER_CHERRY = PlacementUtils.createKey("flower_cherry");
    public static final ResourceKey<PlacedFeature> FLOWER_PALE_GARDEN = PlacementUtils.createKey("flower_pale_garden");
    public static final ResourceKey<PlacedFeature> WILDFLOWERS_BIRCH_FOREST = PlacementUtils.createKey("wildflowers_birch_forest");
    public static final ResourceKey<PlacedFeature> WILDFLOWERS_MEADOW = PlacementUtils.createKey("wildflowers_meadow");
    public static final ResourceKey<PlacedFeature> TREES_PLAINS = PlacementUtils.createKey("trees_plains");
    public static final ResourceKey<PlacedFeature> DARK_FOREST_VEGETATION = PlacementUtils.createKey("dark_forest_vegetation");
    public static final ResourceKey<PlacedFeature> PALE_GARDEN_VEGETATION = PlacementUtils.createKey("pale_garden_vegetation");
    public static final ResourceKey<PlacedFeature> FLOWER_FOREST_FLOWERS = PlacementUtils.createKey("flower_forest_flowers");
    public static final ResourceKey<PlacedFeature> FOREST_FLOWERS = PlacementUtils.createKey("forest_flowers");
    public static final ResourceKey<PlacedFeature> PALE_GARDEN_FLOWERS = PlacementUtils.createKey("pale_garden_flowers");
    public static final ResourceKey<PlacedFeature> PALE_MOSS_PATCH = PlacementUtils.createKey("pale_moss_patch");
    public static final ResourceKey<PlacedFeature> TREES_FLOWER_FOREST = PlacementUtils.createKey("trees_flower_forest");
    public static final ResourceKey<PlacedFeature> TREES_MEADOW = PlacementUtils.createKey("trees_meadow");
    public static final ResourceKey<PlacedFeature> TREES_CHERRY = PlacementUtils.createKey("trees_cherry");
    public static final ResourceKey<PlacedFeature> TREES_TAIGA = PlacementUtils.createKey("trees_taiga");
    public static final ResourceKey<PlacedFeature> TREES_GROVE = PlacementUtils.createKey("trees_grove");
    public static final ResourceKey<PlacedFeature> TREES_BADLANDS = PlacementUtils.createKey("trees_badlands");
    public static final ResourceKey<PlacedFeature> TREES_SNOWY = PlacementUtils.createKey("trees_snowy");
    public static final ResourceKey<PlacedFeature> TREES_SWAMP = PlacementUtils.createKey("trees_swamp");
    public static final ResourceKey<PlacedFeature> TREES_WINDSWEPT_SAVANNA = PlacementUtils.createKey("trees_windswept_savanna");
    public static final ResourceKey<PlacedFeature> TREES_SAVANNA = PlacementUtils.createKey("trees_savanna");
    public static final ResourceKey<PlacedFeature> BIRCH_TALL = PlacementUtils.createKey("birch_tall");
    public static final ResourceKey<PlacedFeature> TREES_BIRCH = PlacementUtils.createKey("trees_birch");
    public static final ResourceKey<PlacedFeature> TREES_WINDSWEPT_FOREST = PlacementUtils.createKey("trees_windswept_forest");
    public static final ResourceKey<PlacedFeature> TREES_WINDSWEPT_HILLS = PlacementUtils.createKey("trees_windswept_hills");
    public static final ResourceKey<PlacedFeature> TREES_WATER = PlacementUtils.createKey("trees_water");
    public static final ResourceKey<PlacedFeature> TREES_BIRCH_AND_OAK_LEAF_LITTER = PlacementUtils.createKey("trees_birch_and_oak_leaf_litter");
    public static final ResourceKey<PlacedFeature> TREES_SPARSE_JUNGLE = PlacementUtils.createKey("trees_sparse_jungle");
    public static final ResourceKey<PlacedFeature> TREES_OLD_GROWTH_SPRUCE_TAIGA = PlacementUtils.createKey("trees_old_growth_spruce_taiga");
    public static final ResourceKey<PlacedFeature> TREES_OLD_GROWTH_PINE_TAIGA = PlacementUtils.createKey("trees_old_growth_pine_taiga");
    public static final ResourceKey<PlacedFeature> TREES_JUNGLE = PlacementUtils.createKey("trees_jungle");
    public static final ResourceKey<PlacedFeature> BAMBOO_VEGETATION = PlacementUtils.createKey("bamboo_vegetation");
    public static final ResourceKey<PlacedFeature> MUSHROOM_ISLAND_VEGETATION = PlacementUtils.createKey("mushroom_island_vegetation");
    public static final ResourceKey<PlacedFeature> TREES_MANGROVE = PlacementUtils.createKey("trees_mangrove");
    private static final PlacementModifier TREE_THRESHOLD = SurfaceWaterDepthFilter.forMaxDepth(0);

    public static List<PlacementModifier> worldSurfaceSquaredWithCount(int count) {
        return List.of(CountPlacement.of(count), InSquarePlacement.spread(), PlacementUtils.HEIGHTMAP_WORLD_SURFACE, BiomeFilter.biome());
    }

    private static List<PlacementModifier> getMushroomPlacement(int rarity, @Nullable PlacementModifier placement) {
        Builder<PlacementModifier> builder = ImmutableList.builder();
        if (placement != null) {
            builder.add(placement);
        }

        if (rarity != 0) {
            builder.add(RarityFilter.onAverageOnceEvery(rarity));
        }

        builder.add(InSquarePlacement.spread());
        builder.add(PlacementUtils.HEIGHTMAP);
        builder.add(BiomeFilter.biome());
        return builder.build();
    }

    private static Builder<PlacementModifier> treePlacementBase(PlacementModifier placement) {
        return ImmutableList.<PlacementModifier>builder()
            .add(placement)
            .add(InSquarePlacement.spread())
            .add(TREE_THRESHOLD)
            .add(PlacementUtils.HEIGHTMAP_OCEAN_FLOOR)
            .add(BiomeFilter.biome());
    }

    public static List<PlacementModifier> treePlacement(PlacementModifier placement) {
        return treePlacementBase(placement).build();
    }

    public static List<PlacementModifier> treePlacement(PlacementModifier placement, Block saplingBlock) {
        return treePlacementBase(placement)
            .add(BlockPredicateFilter.forPredicate(BlockPredicate.wouldSurvive(saplingBlock.defaultBlockState(), BlockPos.ZERO)))
            .build();
    }

    public static void bootstrap(BootstrapContext<PlacedFeature> context) {
        HolderGetter<ConfiguredFeature<?, ?>> holderGetter = context.lookup(Registries.CONFIGURED_FEATURE);
        Holder<ConfiguredFeature<?, ?>> orThrow = holderGetter.getOrThrow(VegetationFeatures.BAMBOO_NO_PODZOL);
        Holder<ConfiguredFeature<?, ?>> orThrow1 = holderGetter.getOrThrow(VegetationFeatures.BAMBOO_SOME_PODZOL);
        Holder<ConfiguredFeature<?, ?>> orThrow2 = holderGetter.getOrThrow(VegetationFeatures.VINES);
        Holder<ConfiguredFeature<?, ?>> orThrow3 = holderGetter.getOrThrow(VegetationFeatures.PATCH_SUNFLOWER);
        Holder<ConfiguredFeature<?, ?>> orThrow4 = holderGetter.getOrThrow(VegetationFeatures.PATCH_PUMPKIN);
        Holder<ConfiguredFeature<?, ?>> orThrow5 = holderGetter.getOrThrow(VegetationFeatures.PATCH_GRASS);
        Holder<ConfiguredFeature<?, ?>> orThrow6 = holderGetter.getOrThrow(VegetationFeatures.PATCH_GRASS_MEADOW);
        Holder<ConfiguredFeature<?, ?>> orThrow7 = holderGetter.getOrThrow(VegetationFeatures.PATCH_LEAF_LITTER);
        Holder<ConfiguredFeature<?, ?>> orThrow8 = holderGetter.getOrThrow(VegetationFeatures.PATCH_TAIGA_GRASS);
        Holder<ConfiguredFeature<?, ?>> orThrow9 = holderGetter.getOrThrow(VegetationFeatures.PATCH_GRASS_JUNGLE);
        Holder<ConfiguredFeature<?, ?>> orThrow10 = holderGetter.getOrThrow(VegetationFeatures.SINGLE_PIECE_OF_GRASS);
        Holder<ConfiguredFeature<?, ?>> orThrow11 = holderGetter.getOrThrow(VegetationFeatures.PATCH_DEAD_BUSH);
        Holder<ConfiguredFeature<?, ?>> orThrow12 = holderGetter.getOrThrow(VegetationFeatures.PATCH_DRY_GRASS);
        Holder<ConfiguredFeature<?, ?>> orThrow13 = holderGetter.getOrThrow(VegetationFeatures.PATCH_FIREFLY_BUSH);
        Holder<ConfiguredFeature<?, ?>> orThrow14 = holderGetter.getOrThrow(VegetationFeatures.PATCH_MELON);
        Holder<ConfiguredFeature<?, ?>> orThrow15 = holderGetter.getOrThrow(VegetationFeatures.PATCH_BERRY_BUSH);
        Holder<ConfiguredFeature<?, ?>> orThrow16 = holderGetter.getOrThrow(VegetationFeatures.PATCH_WATERLILY);
        Holder<ConfiguredFeature<?, ?>> orThrow17 = holderGetter.getOrThrow(VegetationFeatures.PATCH_TALL_GRASS);
        Holder<ConfiguredFeature<?, ?>> orThrow18 = holderGetter.getOrThrow(VegetationFeatures.PATCH_LARGE_FERN);
        Holder<ConfiguredFeature<?, ?>> orThrow19 = holderGetter.getOrThrow(VegetationFeatures.PATCH_BUSH);
        Holder<ConfiguredFeature<?, ?>> orThrow20 = holderGetter.getOrThrow(VegetationFeatures.PATCH_CACTUS);
        Holder<ConfiguredFeature<?, ?>> orThrow21 = holderGetter.getOrThrow(VegetationFeatures.PATCH_SUGAR_CANE);
        Holder<ConfiguredFeature<?, ?>> orThrow22 = holderGetter.getOrThrow(VegetationFeatures.PATCH_BROWN_MUSHROOM);
        Holder<ConfiguredFeature<?, ?>> orThrow23 = holderGetter.getOrThrow(VegetationFeatures.PATCH_RED_MUSHROOM);
        Holder<ConfiguredFeature<?, ?>> orThrow24 = holderGetter.getOrThrow(VegetationFeatures.FLOWER_DEFAULT);
        Holder<ConfiguredFeature<?, ?>> orThrow25 = holderGetter.getOrThrow(VegetationFeatures.FLOWER_FLOWER_FOREST);
        Holder<ConfiguredFeature<?, ?>> orThrow26 = holderGetter.getOrThrow(VegetationFeatures.FLOWER_SWAMP);
        Holder<ConfiguredFeature<?, ?>> orThrow27 = holderGetter.getOrThrow(VegetationFeatures.FLOWER_PLAIN);
        Holder<ConfiguredFeature<?, ?>> orThrow28 = holderGetter.getOrThrow(VegetationFeatures.FLOWER_MEADOW);
        Holder<ConfiguredFeature<?, ?>> orThrow29 = holderGetter.getOrThrow(VegetationFeatures.FLOWER_CHERRY);
        Holder<ConfiguredFeature<?, ?>> orThrow30 = holderGetter.getOrThrow(VegetationFeatures.FLOWER_PALE_GARDEN);
        Holder<ConfiguredFeature<?, ?>> orThrow31 = holderGetter.getOrThrow(VegetationFeatures.WILDFLOWERS_BIRCH_FOREST);
        Holder<ConfiguredFeature<?, ?>> orThrow32 = holderGetter.getOrThrow(VegetationFeatures.WILDFLOWERS_MEADOW);
        Holder<ConfiguredFeature<?, ?>> orThrow33 = holderGetter.getOrThrow(VegetationFeatures.TREES_PLAINS);
        Holder<ConfiguredFeature<?, ?>> orThrow34 = holderGetter.getOrThrow(VegetationFeatures.DARK_FOREST_VEGETATION);
        Holder<ConfiguredFeature<?, ?>> orThrow35 = holderGetter.getOrThrow(VegetationFeatures.PALE_GARDEN_VEGETATION);
        Holder<ConfiguredFeature<?, ?>> orThrow36 = holderGetter.getOrThrow(VegetationFeatures.FOREST_FLOWERS);
        Holder<ConfiguredFeature<?, ?>> orThrow37 = holderGetter.getOrThrow(VegetationFeatures.PALE_FOREST_FLOWERS);
        Holder<ConfiguredFeature<?, ?>> orThrow38 = holderGetter.getOrThrow(VegetationFeatures.PALE_MOSS_PATCH);
        Holder<ConfiguredFeature<?, ?>> orThrow39 = holderGetter.getOrThrow(VegetationFeatures.TREES_FLOWER_FOREST);
        Holder<ConfiguredFeature<?, ?>> orThrow40 = holderGetter.getOrThrow(VegetationFeatures.MEADOW_TREES);
        Holder<ConfiguredFeature<?, ?>> orThrow41 = holderGetter.getOrThrow(VegetationFeatures.TREES_TAIGA);
        Holder<ConfiguredFeature<?, ?>> orThrow42 = holderGetter.getOrThrow(VegetationFeatures.TREES_BADLANDS);
        Holder<ConfiguredFeature<?, ?>> orThrow43 = holderGetter.getOrThrow(VegetationFeatures.TREES_GROVE);
        Holder<ConfiguredFeature<?, ?>> orThrow44 = holderGetter.getOrThrow(VegetationFeatures.TREES_SNOWY);
        Holder<ConfiguredFeature<?, ?>> orThrow45 = holderGetter.getOrThrow(TreeFeatures.CHERRY_BEES_005);
        Holder<ConfiguredFeature<?, ?>> orThrow46 = holderGetter.getOrThrow(TreeFeatures.SWAMP_OAK);
        Holder<ConfiguredFeature<?, ?>> orThrow47 = holderGetter.getOrThrow(VegetationFeatures.TREES_SAVANNA);
        Holder<ConfiguredFeature<?, ?>> orThrow48 = holderGetter.getOrThrow(VegetationFeatures.BIRCH_TALL);
        Holder<ConfiguredFeature<?, ?>> orThrow49 = holderGetter.getOrThrow(VegetationFeatures.TREES_BIRCH);
        Holder<ConfiguredFeature<?, ?>> orThrow50 = holderGetter.getOrThrow(VegetationFeatures.TREES_WINDSWEPT_HILLS);
        Holder<ConfiguredFeature<?, ?>> orThrow51 = holderGetter.getOrThrow(VegetationFeatures.TREES_WATER);
        Holder<ConfiguredFeature<?, ?>> orThrow52 = holderGetter.getOrThrow(VegetationFeatures.TREES_BIRCH_AND_OAK_LEAF_LITTER);
        Holder<ConfiguredFeature<?, ?>> orThrow53 = holderGetter.getOrThrow(VegetationFeatures.TREES_SPARSE_JUNGLE);
        Holder<ConfiguredFeature<?, ?>> orThrow54 = holderGetter.getOrThrow(VegetationFeatures.TREES_OLD_GROWTH_SPRUCE_TAIGA);
        Holder<ConfiguredFeature<?, ?>> orThrow55 = holderGetter.getOrThrow(VegetationFeatures.TREES_OLD_GROWTH_PINE_TAIGA);
        Holder<ConfiguredFeature<?, ?>> orThrow56 = holderGetter.getOrThrow(VegetationFeatures.TREES_JUNGLE);
        Holder<ConfiguredFeature<?, ?>> orThrow57 = holderGetter.getOrThrow(VegetationFeatures.BAMBOO_VEGETATION);
        Holder<ConfiguredFeature<?, ?>> orThrow58 = holderGetter.getOrThrow(VegetationFeatures.MUSHROOM_ISLAND_VEGETATION);
        Holder<ConfiguredFeature<?, ?>> orThrow59 = holderGetter.getOrThrow(VegetationFeatures.MANGROVE_VEGETATION);
        PlacementUtils.register(
            context, BAMBOO_LIGHT, orThrow, RarityFilter.onAverageOnceEvery(4), InSquarePlacement.spread(), PlacementUtils.HEIGHTMAP, BiomeFilter.biome()
        );
        PlacementUtils.register(
            context,
            BAMBOO,
            orThrow1,
            NoiseBasedCountPlacement.of(160, 80.0, 0.3),
            InSquarePlacement.spread(),
            PlacementUtils.HEIGHTMAP_WORLD_SURFACE,
            BiomeFilter.biome()
        );
        PlacementUtils.register(
            context,
            VINES,
            orThrow2,
            CountPlacement.of(127),
            InSquarePlacement.spread(),
            HeightRangePlacement.uniform(VerticalAnchor.absolute(64), VerticalAnchor.absolute(100)),
            BiomeFilter.biome()
        );
        PlacementUtils.register(
            context, PATCH_SUNFLOWER, orThrow3, RarityFilter.onAverageOnceEvery(3), InSquarePlacement.spread(), PlacementUtils.HEIGHTMAP, BiomeFilter.biome()
        );
        PlacementUtils.register(
            context, PATCH_PUMPKIN, orThrow4, RarityFilter.onAverageOnceEvery(300), InSquarePlacement.spread(), PlacementUtils.HEIGHTMAP, BiomeFilter.biome()
        );
        PlacementUtils.register(
            context,
            PATCH_GRASS_PLAIN,
            orThrow5,
            NoiseThresholdCountPlacement.of(-0.8, 5, 10),
            InSquarePlacement.spread(),
            PlacementUtils.HEIGHTMAP_WORLD_SURFACE,
            BiomeFilter.biome()
        );
        PlacementUtils.register(
            context,
            PATCH_GRASS_MEADOW,
            orThrow6,
            NoiseThresholdCountPlacement.of(-0.8, 5, 10),
            InSquarePlacement.spread(),
            PlacementUtils.HEIGHTMAP_WORLD_SURFACE,
            BiomeFilter.biome()
        );
        PlacementUtils.register(context, PATCH_GRASS_FOREST, orThrow5, worldSurfaceSquaredWithCount(2));
        PlacementUtils.register(context, PATCH_LEAF_LITTER, orThrow7, worldSurfaceSquaredWithCount(2));
        PlacementUtils.register(
            context, PATCH_GRASS_BADLANDS, orThrow5, InSquarePlacement.spread(), PlacementUtils.HEIGHTMAP_WORLD_SURFACE, BiomeFilter.biome()
        );
        PlacementUtils.register(context, PATCH_GRASS_SAVANNA, orThrow5, worldSurfaceSquaredWithCount(20));
        PlacementUtils.register(context, PATCH_GRASS_NORMAL, orThrow5, worldSurfaceSquaredWithCount(5));
        PlacementUtils.register(context, PATCH_GRASS_TAIGA_2, orThrow8, InSquarePlacement.spread(), PlacementUtils.HEIGHTMAP_WORLD_SURFACE, BiomeFilter.biome());
        PlacementUtils.register(context, PATCH_GRASS_TAIGA, orThrow8, worldSurfaceSquaredWithCount(7));
        PlacementUtils.register(context, PATCH_GRASS_JUNGLE, orThrow9, worldSurfaceSquaredWithCount(25));
        PlacementUtils.register(context, GRASS_BONEMEAL, orThrow10, PlacementUtils.isEmpty());
        PlacementUtils.register(context, PATCH_DEAD_BUSH_2, orThrow11, worldSurfaceSquaredWithCount(2));
        PlacementUtils.register(context, PATCH_DEAD_BUSH, orThrow11, InSquarePlacement.spread(), PlacementUtils.HEIGHTMAP_WORLD_SURFACE, BiomeFilter.biome());
        PlacementUtils.register(context, PATCH_DEAD_BUSH_BADLANDS, orThrow11, worldSurfaceSquaredWithCount(20));
        PlacementUtils.register(
            context,
            PATCH_DRY_GRASS_BADLANDS,
            orThrow12,
            RarityFilter.onAverageOnceEvery(6),
            InSquarePlacement.spread(),
            PlacementUtils.HEIGHTMAP,
            BiomeFilter.biome()
        );
        PlacementUtils.register(
            context,
            PATCH_DRY_GRASS_DESERT,
            orThrow12,
            RarityFilter.onAverageOnceEvery(3),
            InSquarePlacement.spread(),
            PlacementUtils.HEIGHTMAP,
            BiomeFilter.biome()
        );
        PlacementUtils.register(
            context, PATCH_MELON, orThrow14, RarityFilter.onAverageOnceEvery(6), InSquarePlacement.spread(), PlacementUtils.HEIGHTMAP, BiomeFilter.biome()
        );
        PlacementUtils.register(
            context,
            PATCH_MELON_SPARSE,
            orThrow14,
            RarityFilter.onAverageOnceEvery(64),
            InSquarePlacement.spread(),
            PlacementUtils.HEIGHTMAP,
            BiomeFilter.biome()
        );
        PlacementUtils.register(
            context,
            PATCH_BERRY_COMMON,
            orThrow15,
            RarityFilter.onAverageOnceEvery(32),
            InSquarePlacement.spread(),
            PlacementUtils.HEIGHTMAP_WORLD_SURFACE,
            BiomeFilter.biome()
        );
        PlacementUtils.register(
            context,
            PATCH_BERRY_RARE,
            orThrow15,
            RarityFilter.onAverageOnceEvery(384),
            InSquarePlacement.spread(),
            PlacementUtils.HEIGHTMAP_WORLD_SURFACE,
            BiomeFilter.biome()
        );
        PlacementUtils.register(context, PATCH_WATERLILY, orThrow16, worldSurfaceSquaredWithCount(4));
        PlacementUtils.register(
            context,
            PATCH_TALL_GRASS_2,
            orThrow17,
            NoiseThresholdCountPlacement.of(-0.8, 0, 7),
            RarityFilter.onAverageOnceEvery(32),
            InSquarePlacement.spread(),
            PlacementUtils.HEIGHTMAP,
            BiomeFilter.biome()
        );
        PlacementUtils.register(
            context, PATCH_TALL_GRASS, orThrow17, RarityFilter.onAverageOnceEvery(5), InSquarePlacement.spread(), PlacementUtils.HEIGHTMAP, BiomeFilter.biome()
        );
        PlacementUtils.register(
            context, PATCH_LARGE_FERN, orThrow18, RarityFilter.onAverageOnceEvery(5), InSquarePlacement.spread(), PlacementUtils.HEIGHTMAP, BiomeFilter.biome()
        );
        PlacementUtils.register(
            context, PATCH_BUSH, orThrow19, RarityFilter.onAverageOnceEvery(4), InSquarePlacement.spread(), PlacementUtils.HEIGHTMAP, BiomeFilter.biome()
        );
        PlacementUtils.register(
            context,
            PATCH_CACTUS_DESERT,
            orThrow20,
            RarityFilter.onAverageOnceEvery(6),
            InSquarePlacement.spread(),
            PlacementUtils.HEIGHTMAP,
            BiomeFilter.biome()
        );
        PlacementUtils.register(
            context,
            PATCH_CACTUS_DECORATED,
            orThrow20,
            RarityFilter.onAverageOnceEvery(13),
            InSquarePlacement.spread(),
            PlacementUtils.HEIGHTMAP,
            BiomeFilter.biome()
        );
        PlacementUtils.register(
            context,
            PATCH_SUGAR_CANE_SWAMP,
            orThrow21,
            RarityFilter.onAverageOnceEvery(3),
            InSquarePlacement.spread(),
            PlacementUtils.HEIGHTMAP,
            BiomeFilter.biome()
        );
        PlacementUtils.register(context, PATCH_SUGAR_CANE_DESERT, orThrow21, InSquarePlacement.spread(), PlacementUtils.HEIGHTMAP, BiomeFilter.biome());
        PlacementUtils.register(
            context,
            PATCH_SUGAR_CANE_BADLANDS,
            orThrow21,
            RarityFilter.onAverageOnceEvery(5),
            InSquarePlacement.spread(),
            PlacementUtils.HEIGHTMAP,
            BiomeFilter.biome()
        );
        PlacementUtils.register(
            context, PATCH_SUGAR_CANE, orThrow21, RarityFilter.onAverageOnceEvery(6), InSquarePlacement.spread(), PlacementUtils.HEIGHTMAP, BiomeFilter.biome()
        );
        PlacementUtils.register(
            context,
            PATCH_FIREFLY_BUSH_NEAR_WATER,
            orThrow13,
            CountPlacement.of(2),
            InSquarePlacement.spread(),
            PlacementUtils.HEIGHTMAP_NO_LEAVES,
            BiomeFilter.biome(),
            VegetationFeatures.nearWaterPredicate(Blocks.FIREFLY_BUSH)
        );
        PlacementUtils.register(
            context,
            PATCH_FIREFLY_BUSH_NEAR_WATER_SWAMP,
            orThrow13,
            CountPlacement.of(3),
            InSquarePlacement.spread(),
            PlacementUtils.HEIGHTMAP,
            BiomeFilter.biome(),
            VegetationFeatures.nearWaterPredicate(Blocks.FIREFLY_BUSH)
        );
        PlacementUtils.register(
            context,
            PATCH_FIREFLY_BUSH_SWAMP,
            orThrow13,
            RarityFilter.onAverageOnceEvery(8),
            InSquarePlacement.spread(),
            PlacementUtils.HEIGHTMAP,
            BiomeFilter.biome()
        );
        PlacementUtils.register(
            context,
            BROWN_MUSHROOM_NETHER,
            orThrow22,
            RarityFilter.onAverageOnceEvery(2),
            InSquarePlacement.spread(),
            PlacementUtils.FULL_RANGE,
            BiomeFilter.biome()
        );
        PlacementUtils.register(
            context,
            RED_MUSHROOM_NETHER,
            orThrow23,
            RarityFilter.onAverageOnceEvery(2),
            InSquarePlacement.spread(),
            PlacementUtils.FULL_RANGE,
            BiomeFilter.biome()
        );
        PlacementUtils.register(context, BROWN_MUSHROOM_NORMAL, orThrow22, getMushroomPlacement(256, null));
        PlacementUtils.register(context, RED_MUSHROOM_NORMAL, orThrow23, getMushroomPlacement(512, null));
        PlacementUtils.register(context, BROWN_MUSHROOM_TAIGA, orThrow22, getMushroomPlacement(4, null));
        PlacementUtils.register(context, RED_MUSHROOM_TAIGA, orThrow23, getMushroomPlacement(256, null));
        PlacementUtils.register(context, BROWN_MUSHROOM_OLD_GROWTH, orThrow22, getMushroomPlacement(4, CountPlacement.of(3)));
        PlacementUtils.register(context, RED_MUSHROOM_OLD_GROWTH, orThrow23, getMushroomPlacement(171, null));
        PlacementUtils.register(context, BROWN_MUSHROOM_SWAMP, orThrow22, getMushroomPlacement(0, CountPlacement.of(2)));
        PlacementUtils.register(context, RED_MUSHROOM_SWAMP, orThrow23, getMushroomPlacement(64, null));
        PlacementUtils.register(
            context, FLOWER_WARM, orThrow24, RarityFilter.onAverageOnceEvery(16), InSquarePlacement.spread(), PlacementUtils.HEIGHTMAP, BiomeFilter.biome()
        );
        PlacementUtils.register(
            context, FLOWER_DEFAULT, orThrow24, RarityFilter.onAverageOnceEvery(32), InSquarePlacement.spread(), PlacementUtils.HEIGHTMAP, BiomeFilter.biome()
        );
        PlacementUtils.register(
            context,
            FLOWER_FLOWER_FOREST,
            orThrow25,
            CountPlacement.of(3),
            RarityFilter.onAverageOnceEvery(2),
            InSquarePlacement.spread(),
            PlacementUtils.HEIGHTMAP,
            BiomeFilter.biome()
        );
        PlacementUtils.register(
            context, FLOWER_SWAMP, orThrow26, RarityFilter.onAverageOnceEvery(32), InSquarePlacement.spread(), PlacementUtils.HEIGHTMAP, BiomeFilter.biome()
        );
        PlacementUtils.register(
            context,
            FLOWER_PLAINS,
            orThrow27,
            NoiseThresholdCountPlacement.of(-0.8, 15, 4),
            RarityFilter.onAverageOnceEvery(32),
            InSquarePlacement.spread(),
            PlacementUtils.HEIGHTMAP,
            BiomeFilter.biome()
        );
        PlacementUtils.register(
            context,
            FLOWER_CHERRY,
            orThrow29,
            NoiseThresholdCountPlacement.of(-0.8, 5, 10),
            InSquarePlacement.spread(),
            PlacementUtils.HEIGHTMAP,
            BiomeFilter.biome()
        );
        PlacementUtils.register(context, FLOWER_MEADOW, orThrow28, InSquarePlacement.spread(), PlacementUtils.HEIGHTMAP, BiomeFilter.biome());
        PlacementUtils.register(
            context,
            FLOWER_PALE_GARDEN,
            orThrow30,
            RarityFilter.onAverageOnceEvery(32),
            InSquarePlacement.spread(),
            PlacementUtils.HEIGHTMAP,
            BiomeFilter.biome()
        );
        PlacementUtils.register(
            context,
            WILDFLOWERS_BIRCH_FOREST,
            orThrow31,
            CountPlacement.of(3),
            RarityFilter.onAverageOnceEvery(2),
            InSquarePlacement.spread(),
            PlacementUtils.HEIGHTMAP,
            BiomeFilter.biome()
        );
        PlacementUtils.register(
            context,
            WILDFLOWERS_MEADOW,
            orThrow32,
            NoiseThresholdCountPlacement.of(-0.8, 5, 10),
            InSquarePlacement.spread(),
            PlacementUtils.HEIGHTMAP,
            BiomeFilter.biome()
        );
        PlacementModifier placementModifier = SurfaceWaterDepthFilter.forMaxDepth(0);
        PlacementUtils.register(
            context,
            TREES_PLAINS,
            orThrow33,
            PlacementUtils.countExtra(0, 0.05F, 1),
            InSquarePlacement.spread(),
            placementModifier,
            PlacementUtils.HEIGHTMAP_OCEAN_FLOOR,
            BlockPredicateFilter.forPredicate(BlockPredicate.wouldSurvive(Blocks.OAK_SAPLING.defaultBlockState(), BlockPos.ZERO)),
            BiomeFilter.biome()
        );
        PlacementUtils.register(
            context,
            DARK_FOREST_VEGETATION,
            orThrow34,
            CountPlacement.of(16),
            InSquarePlacement.spread(),
            placementModifier,
            PlacementUtils.HEIGHTMAP_OCEAN_FLOOR,
            BiomeFilter.biome()
        );
        PlacementUtils.register(
            context,
            PALE_GARDEN_VEGETATION,
            orThrow35,
            CountPlacement.of(16),
            InSquarePlacement.spread(),
            placementModifier,
            PlacementUtils.HEIGHTMAP_OCEAN_FLOOR,
            BiomeFilter.biome()
        );
        PlacementUtils.register(
            context,
            FLOWER_FOREST_FLOWERS,
            orThrow36,
            RarityFilter.onAverageOnceEvery(7),
            InSquarePlacement.spread(),
            PlacementUtils.HEIGHTMAP,
            CountPlacement.of(ClampedInt.of(UniformInt.of(-1, 3), 0, 3)),
            BiomeFilter.biome()
        );
        PlacementUtils.register(
            context,
            FOREST_FLOWERS,
            orThrow36,
            RarityFilter.onAverageOnceEvery(7),
            InSquarePlacement.spread(),
            PlacementUtils.HEIGHTMAP,
            CountPlacement.of(ClampedInt.of(UniformInt.of(-3, 1), 0, 1)),
            BiomeFilter.biome()
        );
        PlacementUtils.register(
            context,
            PALE_GARDEN_FLOWERS,
            orThrow37,
            RarityFilter.onAverageOnceEvery(8),
            InSquarePlacement.spread(),
            PlacementUtils.HEIGHTMAP_NO_LEAVES,
            BiomeFilter.biome()
        );
        PlacementUtils.register(
            context, PALE_MOSS_PATCH, orThrow38, CountPlacement.of(1), InSquarePlacement.spread(), PlacementUtils.HEIGHTMAP_NO_LEAVES, BiomeFilter.biome()
        );
        PlacementUtils.register(context, TREES_FLOWER_FOREST, orThrow39, treePlacement(PlacementUtils.countExtra(6, 0.1F, 1)));
        PlacementUtils.register(context, TREES_MEADOW, orThrow40, treePlacement(RarityFilter.onAverageOnceEvery(100)));
        PlacementUtils.register(context, TREES_CHERRY, orThrow45, treePlacement(PlacementUtils.countExtra(10, 0.1F, 1), Blocks.CHERRY_SAPLING));
        PlacementUtils.register(context, TREES_TAIGA, orThrow41, treePlacement(PlacementUtils.countExtra(10, 0.1F, 1)));
        PlacementUtils.register(context, TREES_GROVE, orThrow43, treePlacement(PlacementUtils.countExtra(10, 0.1F, 1)));
        PlacementUtils.register(context, TREES_BADLANDS, orThrow42, treePlacement(PlacementUtils.countExtra(5, 0.1F, 1), Blocks.OAK_SAPLING));
        PlacementUtils.register(context, TREES_SNOWY, orThrow44, treePlacement(PlacementUtils.countExtra(0, 0.1F, 1), Blocks.SPRUCE_SAPLING));
        PlacementUtils.register(
            context,
            TREES_SWAMP,
            orThrow46,
            PlacementUtils.countExtra(2, 0.1F, 1),
            InSquarePlacement.spread(),
            SurfaceWaterDepthFilter.forMaxDepth(2),
            PlacementUtils.HEIGHTMAP_OCEAN_FLOOR,
            BiomeFilter.biome(),
            BlockPredicateFilter.forPredicate(BlockPredicate.wouldSurvive(Blocks.OAK_SAPLING.defaultBlockState(), BlockPos.ZERO))
        );
        PlacementUtils.register(context, TREES_WINDSWEPT_SAVANNA, orThrow47, treePlacement(PlacementUtils.countExtra(2, 0.1F, 1)));
        PlacementUtils.register(context, TREES_SAVANNA, orThrow47, treePlacement(PlacementUtils.countExtra(1, 0.1F, 1)));
        PlacementUtils.register(context, BIRCH_TALL, orThrow48, treePlacement(PlacementUtils.countExtra(10, 0.1F, 1)));
        PlacementUtils.register(context, TREES_BIRCH, orThrow49, treePlacement(PlacementUtils.countExtra(10, 0.1F, 1), Blocks.BIRCH_SAPLING));
        PlacementUtils.register(context, TREES_WINDSWEPT_FOREST, orThrow50, treePlacement(PlacementUtils.countExtra(3, 0.1F, 1)));
        PlacementUtils.register(context, TREES_WINDSWEPT_HILLS, orThrow50, treePlacement(PlacementUtils.countExtra(0, 0.1F, 1)));
        PlacementUtils.register(context, TREES_WATER, orThrow51, treePlacement(PlacementUtils.countExtra(0, 0.1F, 1)));
        PlacementUtils.register(context, TREES_BIRCH_AND_OAK_LEAF_LITTER, orThrow52, treePlacement(PlacementUtils.countExtra(10, 0.1F, 1)));
        PlacementUtils.register(context, TREES_SPARSE_JUNGLE, orThrow53, treePlacement(PlacementUtils.countExtra(2, 0.1F, 1)));
        PlacementUtils.register(context, TREES_OLD_GROWTH_SPRUCE_TAIGA, orThrow54, treePlacement(PlacementUtils.countExtra(10, 0.1F, 1)));
        PlacementUtils.register(context, TREES_OLD_GROWTH_PINE_TAIGA, orThrow55, treePlacement(PlacementUtils.countExtra(10, 0.1F, 1)));
        PlacementUtils.register(context, TREES_JUNGLE, orThrow56, treePlacement(PlacementUtils.countExtra(50, 0.1F, 1)));
        PlacementUtils.register(context, BAMBOO_VEGETATION, orThrow57, treePlacement(PlacementUtils.countExtra(30, 0.1F, 1)));
        PlacementUtils.register(context, MUSHROOM_ISLAND_VEGETATION, orThrow58, InSquarePlacement.spread(), PlacementUtils.HEIGHTMAP, BiomeFilter.biome());
        PlacementUtils.register(
            context,
            TREES_MANGROVE,
            orThrow59,
            CountPlacement.of(25),
            InSquarePlacement.spread(),
            SurfaceWaterDepthFilter.forMaxDepth(5),
            PlacementUtils.HEIGHTMAP_OCEAN_FLOOR,
            BiomeFilter.biome()
        );
    }
}
