package net.minecraft.data.worldgen.placement;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.features.PileFeatures;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.data.worldgen.features.VegetationFeatures;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

public class VillagePlacements {
    public static final ResourceKey<PlacedFeature> PILE_HAY_VILLAGE = PlacementUtils.createKey("pile_hay");
    public static final ResourceKey<PlacedFeature> PILE_MELON_VILLAGE = PlacementUtils.createKey("pile_melon");
    public static final ResourceKey<PlacedFeature> PILE_SNOW_VILLAGE = PlacementUtils.createKey("pile_snow");
    public static final ResourceKey<PlacedFeature> PILE_ICE_VILLAGE = PlacementUtils.createKey("pile_ice");
    public static final ResourceKey<PlacedFeature> PILE_PUMPKIN_VILLAGE = PlacementUtils.createKey("pile_pumpkin");
    public static final ResourceKey<PlacedFeature> OAK_VILLAGE = PlacementUtils.createKey("oak");
    public static final ResourceKey<PlacedFeature> ACACIA_VILLAGE = PlacementUtils.createKey("acacia");
    public static final ResourceKey<PlacedFeature> SPRUCE_VILLAGE = PlacementUtils.createKey("spruce");
    public static final ResourceKey<PlacedFeature> PINE_VILLAGE = PlacementUtils.createKey("pine");
    public static final ResourceKey<PlacedFeature> PATCH_CACTUS_VILLAGE = PlacementUtils.createKey("patch_cactus");
    public static final ResourceKey<PlacedFeature> FLOWER_PLAIN_VILLAGE = PlacementUtils.createKey("flower_plain");
    public static final ResourceKey<PlacedFeature> PATCH_TAIGA_GRASS_VILLAGE = PlacementUtils.createKey("patch_taiga_grass");
    public static final ResourceKey<PlacedFeature> PATCH_BERRY_BUSH_VILLAGE = PlacementUtils.createKey("patch_berry_bush");

    public static void bootstrap(BootstrapContext<PlacedFeature> context) {
        HolderGetter<ConfiguredFeature<?, ?>> holderGetter = context.lookup(Registries.CONFIGURED_FEATURE);
        Holder<ConfiguredFeature<?, ?>> orThrow = holderGetter.getOrThrow(PileFeatures.PILE_HAY);
        Holder<ConfiguredFeature<?, ?>> orThrow1 = holderGetter.getOrThrow(PileFeatures.PILE_MELON);
        Holder<ConfiguredFeature<?, ?>> orThrow2 = holderGetter.getOrThrow(PileFeatures.PILE_SNOW);
        Holder<ConfiguredFeature<?, ?>> orThrow3 = holderGetter.getOrThrow(PileFeatures.PILE_ICE);
        Holder<ConfiguredFeature<?, ?>> orThrow4 = holderGetter.getOrThrow(PileFeatures.PILE_PUMPKIN);
        Holder<ConfiguredFeature<?, ?>> orThrow5 = holderGetter.getOrThrow(TreeFeatures.OAK);
        Holder<ConfiguredFeature<?, ?>> orThrow6 = holderGetter.getOrThrow(TreeFeatures.ACACIA);
        Holder<ConfiguredFeature<?, ?>> orThrow7 = holderGetter.getOrThrow(TreeFeatures.SPRUCE);
        Holder<ConfiguredFeature<?, ?>> orThrow8 = holderGetter.getOrThrow(TreeFeatures.PINE);
        Holder<ConfiguredFeature<?, ?>> orThrow9 = holderGetter.getOrThrow(VegetationFeatures.PATCH_CACTUS);
        Holder<ConfiguredFeature<?, ?>> orThrow10 = holderGetter.getOrThrow(VegetationFeatures.FLOWER_PLAIN);
        Holder<ConfiguredFeature<?, ?>> orThrow11 = holderGetter.getOrThrow(VegetationFeatures.PATCH_TAIGA_GRASS);
        Holder<ConfiguredFeature<?, ?>> orThrow12 = holderGetter.getOrThrow(VegetationFeatures.PATCH_BERRY_BUSH);
        PlacementUtils.register(context, PILE_HAY_VILLAGE, orThrow);
        PlacementUtils.register(context, PILE_MELON_VILLAGE, orThrow1);
        PlacementUtils.register(context, PILE_SNOW_VILLAGE, orThrow2);
        PlacementUtils.register(context, PILE_ICE_VILLAGE, orThrow3);
        PlacementUtils.register(context, PILE_PUMPKIN_VILLAGE, orThrow4);
        PlacementUtils.register(context, OAK_VILLAGE, orThrow5, PlacementUtils.filteredByBlockSurvival(Blocks.OAK_SAPLING));
        PlacementUtils.register(context, ACACIA_VILLAGE, orThrow6, PlacementUtils.filteredByBlockSurvival(Blocks.ACACIA_SAPLING));
        PlacementUtils.register(context, SPRUCE_VILLAGE, orThrow7, PlacementUtils.filteredByBlockSurvival(Blocks.SPRUCE_SAPLING));
        PlacementUtils.register(context, PINE_VILLAGE, orThrow8, PlacementUtils.filteredByBlockSurvival(Blocks.SPRUCE_SAPLING));
        PlacementUtils.register(context, PATCH_CACTUS_VILLAGE, orThrow9);
        PlacementUtils.register(context, FLOWER_PLAIN_VILLAGE, orThrow10);
        PlacementUtils.register(context, PATCH_TAIGA_GRASS_VILLAGE, orThrow11);
        PlacementUtils.register(context, PATCH_BERRY_BUSH_VILLAGE, orThrow12);
    }
}
