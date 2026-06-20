package net.minecraft.data.worldgen.placement;

import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.features.OreFeatures;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.CountPlacement;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.InSquarePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.RarityFilter;

public class OrePlacements {
    public static final ResourceKey<PlacedFeature> ORE_MAGMA = PlacementUtils.createKey("ore_magma");
    public static final ResourceKey<PlacedFeature> ORE_SOUL_SAND = PlacementUtils.createKey("ore_soul_sand");
    public static final ResourceKey<PlacedFeature> ORE_GOLD_DELTAS = PlacementUtils.createKey("ore_gold_deltas");
    public static final ResourceKey<PlacedFeature> ORE_QUARTZ_DELTAS = PlacementUtils.createKey("ore_quartz_deltas");
    public static final ResourceKey<PlacedFeature> ORE_GOLD_NETHER = PlacementUtils.createKey("ore_gold_nether");
    public static final ResourceKey<PlacedFeature> ORE_QUARTZ_NETHER = PlacementUtils.createKey("ore_quartz_nether");
    public static final ResourceKey<PlacedFeature> ORE_GRAVEL_NETHER = PlacementUtils.createKey("ore_gravel_nether");
    public static final ResourceKey<PlacedFeature> ORE_BLACKSTONE = PlacementUtils.createKey("ore_blackstone");
    public static final ResourceKey<PlacedFeature> ORE_DIRT = PlacementUtils.createKey("ore_dirt");
    public static final ResourceKey<PlacedFeature> ORE_GRAVEL = PlacementUtils.createKey("ore_gravel");
    public static final ResourceKey<PlacedFeature> ORE_GRANITE_UPPER = PlacementUtils.createKey("ore_granite_upper");
    public static final ResourceKey<PlacedFeature> ORE_GRANITE_LOWER = PlacementUtils.createKey("ore_granite_lower");
    public static final ResourceKey<PlacedFeature> ORE_DIORITE_UPPER = PlacementUtils.createKey("ore_diorite_upper");
    public static final ResourceKey<PlacedFeature> ORE_DIORITE_LOWER = PlacementUtils.createKey("ore_diorite_lower");
    public static final ResourceKey<PlacedFeature> ORE_ANDESITE_UPPER = PlacementUtils.createKey("ore_andesite_upper");
    public static final ResourceKey<PlacedFeature> ORE_ANDESITE_LOWER = PlacementUtils.createKey("ore_andesite_lower");
    public static final ResourceKey<PlacedFeature> ORE_TUFF = PlacementUtils.createKey("ore_tuff");
    public static final ResourceKey<PlacedFeature> ORE_COAL_UPPER = PlacementUtils.createKey("ore_coal_upper");
    public static final ResourceKey<PlacedFeature> ORE_COAL_LOWER = PlacementUtils.createKey("ore_coal_lower");
    public static final ResourceKey<PlacedFeature> ORE_IRON_UPPER = PlacementUtils.createKey("ore_iron_upper");
    public static final ResourceKey<PlacedFeature> ORE_IRON_MIDDLE = PlacementUtils.createKey("ore_iron_middle");
    public static final ResourceKey<PlacedFeature> ORE_IRON_SMALL = PlacementUtils.createKey("ore_iron_small");
    public static final ResourceKey<PlacedFeature> ORE_GOLD_EXTRA = PlacementUtils.createKey("ore_gold_extra");
    public static final ResourceKey<PlacedFeature> ORE_GOLD = PlacementUtils.createKey("ore_gold");
    public static final ResourceKey<PlacedFeature> ORE_GOLD_LOWER = PlacementUtils.createKey("ore_gold_lower");
    public static final ResourceKey<PlacedFeature> ORE_REDSTONE = PlacementUtils.createKey("ore_redstone");
    public static final ResourceKey<PlacedFeature> ORE_REDSTONE_LOWER = PlacementUtils.createKey("ore_redstone_lower");
    public static final ResourceKey<PlacedFeature> ORE_DIAMOND = PlacementUtils.createKey("ore_diamond");
    public static final ResourceKey<PlacedFeature> ORE_DIAMOND_MEDIUM = PlacementUtils.createKey("ore_diamond_medium");
    public static final ResourceKey<PlacedFeature> ORE_DIAMOND_LARGE = PlacementUtils.createKey("ore_diamond_large");
    public static final ResourceKey<PlacedFeature> ORE_DIAMOND_BURIED = PlacementUtils.createKey("ore_diamond_buried");
    public static final ResourceKey<PlacedFeature> ORE_LAPIS = PlacementUtils.createKey("ore_lapis");
    public static final ResourceKey<PlacedFeature> ORE_LAPIS_BURIED = PlacementUtils.createKey("ore_lapis_buried");
    public static final ResourceKey<PlacedFeature> ORE_INFESTED = PlacementUtils.createKey("ore_infested");
    public static final ResourceKey<PlacedFeature> ORE_EMERALD = PlacementUtils.createKey("ore_emerald");
    public static final ResourceKey<PlacedFeature> ORE_ANCIENT_DEBRIS_LARGE = PlacementUtils.createKey("ore_ancient_debris_large");
    public static final ResourceKey<PlacedFeature> ORE_ANCIENT_DEBRIS_SMALL = PlacementUtils.createKey("ore_debris_small");
    public static final ResourceKey<PlacedFeature> ORE_COPPER = PlacementUtils.createKey("ore_copper");
    public static final ResourceKey<PlacedFeature> ORE_COPPER_LARGE = PlacementUtils.createKey("ore_copper_large");
    public static final ResourceKey<PlacedFeature> ORE_CLAY = PlacementUtils.createKey("ore_clay");

    private static List<PlacementModifier> orePlacement(PlacementModifier countPlacement, PlacementModifier heightRange) {
        return List.of(countPlacement, InSquarePlacement.spread(), heightRange, BiomeFilter.biome());
    }

    private static List<PlacementModifier> commonOrePlacement(int count, PlacementModifier heightRange) {
        return orePlacement(CountPlacement.of(count), heightRange);
    }

    private static List<PlacementModifier> rareOrePlacement(int chance, PlacementModifier heightRange) {
        return orePlacement(RarityFilter.onAverageOnceEvery(chance), heightRange);
    }

    public static void bootstrap(BootstrapContext<PlacedFeature> context) {
        HolderGetter<ConfiguredFeature<?, ?>> holderGetter = context.lookup(Registries.CONFIGURED_FEATURE);
        Holder<ConfiguredFeature<?, ?>> orThrow = holderGetter.getOrThrow(OreFeatures.ORE_MAGMA);
        Holder<ConfiguredFeature<?, ?>> orThrow1 = holderGetter.getOrThrow(OreFeatures.ORE_SOUL_SAND);
        Holder<ConfiguredFeature<?, ?>> orThrow2 = holderGetter.getOrThrow(OreFeatures.ORE_NETHER_GOLD);
        Holder<ConfiguredFeature<?, ?>> orThrow3 = holderGetter.getOrThrow(OreFeatures.ORE_QUARTZ);
        Holder<ConfiguredFeature<?, ?>> orThrow4 = holderGetter.getOrThrow(OreFeatures.ORE_GRAVEL_NETHER);
        Holder<ConfiguredFeature<?, ?>> orThrow5 = holderGetter.getOrThrow(OreFeatures.ORE_BLACKSTONE);
        Holder<ConfiguredFeature<?, ?>> orThrow6 = holderGetter.getOrThrow(OreFeatures.ORE_DIRT);
        Holder<ConfiguredFeature<?, ?>> orThrow7 = holderGetter.getOrThrow(OreFeatures.ORE_GRAVEL);
        Holder<ConfiguredFeature<?, ?>> orThrow8 = holderGetter.getOrThrow(OreFeatures.ORE_GRANITE);
        Holder<ConfiguredFeature<?, ?>> orThrow9 = holderGetter.getOrThrow(OreFeatures.ORE_DIORITE);
        Holder<ConfiguredFeature<?, ?>> orThrow10 = holderGetter.getOrThrow(OreFeatures.ORE_ANDESITE);
        Holder<ConfiguredFeature<?, ?>> orThrow11 = holderGetter.getOrThrow(OreFeatures.ORE_TUFF);
        Holder<ConfiguredFeature<?, ?>> orThrow12 = holderGetter.getOrThrow(OreFeatures.ORE_COAL);
        Holder<ConfiguredFeature<?, ?>> orThrow13 = holderGetter.getOrThrow(OreFeatures.ORE_COAL_BURIED);
        Holder<ConfiguredFeature<?, ?>> orThrow14 = holderGetter.getOrThrow(OreFeatures.ORE_IRON);
        Holder<ConfiguredFeature<?, ?>> orThrow15 = holderGetter.getOrThrow(OreFeatures.ORE_IRON_SMALL);
        Holder<ConfiguredFeature<?, ?>> orThrow16 = holderGetter.getOrThrow(OreFeatures.ORE_GOLD);
        Holder<ConfiguredFeature<?, ?>> orThrow17 = holderGetter.getOrThrow(OreFeatures.ORE_GOLD_BURIED);
        Holder<ConfiguredFeature<?, ?>> orThrow18 = holderGetter.getOrThrow(OreFeatures.ORE_REDSTONE);
        Holder<ConfiguredFeature<?, ?>> orThrow19 = holderGetter.getOrThrow(OreFeatures.ORE_DIAMOND_SMALL);
        Holder<ConfiguredFeature<?, ?>> orThrow20 = holderGetter.getOrThrow(OreFeatures.ORE_DIAMOND_MEDIUM);
        Holder<ConfiguredFeature<?, ?>> orThrow21 = holderGetter.getOrThrow(OreFeatures.ORE_DIAMOND_LARGE);
        Holder<ConfiguredFeature<?, ?>> orThrow22 = holderGetter.getOrThrow(OreFeatures.ORE_DIAMOND_BURIED);
        Holder<ConfiguredFeature<?, ?>> orThrow23 = holderGetter.getOrThrow(OreFeatures.ORE_LAPIS);
        Holder<ConfiguredFeature<?, ?>> orThrow24 = holderGetter.getOrThrow(OreFeatures.ORE_LAPIS_BURIED);
        Holder<ConfiguredFeature<?, ?>> orThrow25 = holderGetter.getOrThrow(OreFeatures.ORE_INFESTED);
        Holder<ConfiguredFeature<?, ?>> orThrow26 = holderGetter.getOrThrow(OreFeatures.ORE_EMERALD);
        Holder<ConfiguredFeature<?, ?>> orThrow27 = holderGetter.getOrThrow(OreFeatures.ORE_ANCIENT_DEBRIS_LARGE);
        Holder<ConfiguredFeature<?, ?>> orThrow28 = holderGetter.getOrThrow(OreFeatures.ORE_ANCIENT_DEBRIS_SMALL);
        Holder<ConfiguredFeature<?, ?>> orThrow29 = holderGetter.getOrThrow(OreFeatures.ORE_COPPPER_SMALL);
        Holder<ConfiguredFeature<?, ?>> orThrow30 = holderGetter.getOrThrow(OreFeatures.ORE_COPPER_LARGE);
        Holder<ConfiguredFeature<?, ?>> orThrow31 = holderGetter.getOrThrow(OreFeatures.ORE_CLAY);
        PlacementUtils.register(
            context, ORE_MAGMA, orThrow, commonOrePlacement(4, HeightRangePlacement.uniform(VerticalAnchor.absolute(27), VerticalAnchor.absolute(36)))
        );
        PlacementUtils.register(
            context, ORE_SOUL_SAND, orThrow1, commonOrePlacement(12, HeightRangePlacement.uniform(VerticalAnchor.bottom(), VerticalAnchor.absolute(31)))
        );
        PlacementUtils.register(context, ORE_GOLD_DELTAS, orThrow2, commonOrePlacement(20, PlacementUtils.RANGE_10_10));
        PlacementUtils.register(context, ORE_QUARTZ_DELTAS, orThrow3, commonOrePlacement(32, PlacementUtils.RANGE_10_10));
        PlacementUtils.register(context, ORE_GOLD_NETHER, orThrow2, commonOrePlacement(10, PlacementUtils.RANGE_10_10));
        PlacementUtils.register(context, ORE_QUARTZ_NETHER, orThrow3, commonOrePlacement(16, PlacementUtils.RANGE_10_10));
        PlacementUtils.register(
            context, ORE_GRAVEL_NETHER, orThrow4, commonOrePlacement(2, HeightRangePlacement.uniform(VerticalAnchor.absolute(5), VerticalAnchor.absolute(41)))
        );
        PlacementUtils.register(
            context, ORE_BLACKSTONE, orThrow5, commonOrePlacement(2, HeightRangePlacement.uniform(VerticalAnchor.absolute(5), VerticalAnchor.absolute(31)))
        );
        PlacementUtils.register(
            context, ORE_DIRT, orThrow6, commonOrePlacement(7, HeightRangePlacement.uniform(VerticalAnchor.absolute(0), VerticalAnchor.absolute(160)))
        );
        PlacementUtils.register(
            context, ORE_GRAVEL, orThrow7, commonOrePlacement(14, HeightRangePlacement.uniform(VerticalAnchor.bottom(), VerticalAnchor.top()))
        );
        PlacementUtils.register(
            context, ORE_GRANITE_UPPER, orThrow8, rareOrePlacement(6, HeightRangePlacement.uniform(VerticalAnchor.absolute(64), VerticalAnchor.absolute(128)))
        );
        PlacementUtils.register(
            context, ORE_GRANITE_LOWER, orThrow8, commonOrePlacement(2, HeightRangePlacement.uniform(VerticalAnchor.absolute(0), VerticalAnchor.absolute(60)))
        );
        PlacementUtils.register(
            context, ORE_DIORITE_UPPER, orThrow9, rareOrePlacement(6, HeightRangePlacement.uniform(VerticalAnchor.absolute(64), VerticalAnchor.absolute(128)))
        );
        PlacementUtils.register(
            context, ORE_DIORITE_LOWER, orThrow9, commonOrePlacement(2, HeightRangePlacement.uniform(VerticalAnchor.absolute(0), VerticalAnchor.absolute(60)))
        );
        PlacementUtils.register(
            context,
            ORE_ANDESITE_UPPER,
            orThrow10,
            rareOrePlacement(6, HeightRangePlacement.uniform(VerticalAnchor.absolute(64), VerticalAnchor.absolute(128)))
        );
        PlacementUtils.register(
            context,
            ORE_ANDESITE_LOWER,
            orThrow10,
            commonOrePlacement(2, HeightRangePlacement.uniform(VerticalAnchor.absolute(0), VerticalAnchor.absolute(60)))
        );
        PlacementUtils.register(
            context, ORE_TUFF, orThrow11, commonOrePlacement(2, HeightRangePlacement.uniform(VerticalAnchor.bottom(), VerticalAnchor.absolute(0)))
        );
        PlacementUtils.register(
            context, ORE_COAL_UPPER, orThrow12, commonOrePlacement(30, HeightRangePlacement.uniform(VerticalAnchor.absolute(136), VerticalAnchor.top()))
        );
        PlacementUtils.register(
            context, ORE_COAL_LOWER, orThrow13, commonOrePlacement(20, HeightRangePlacement.triangle(VerticalAnchor.absolute(0), VerticalAnchor.absolute(192)))
        );
        PlacementUtils.register(
            context,
            ORE_IRON_UPPER,
            orThrow14,
            commonOrePlacement(90, HeightRangePlacement.triangle(VerticalAnchor.absolute(80), VerticalAnchor.absolute(384)))
        );
        PlacementUtils.register(
            context,
            ORE_IRON_MIDDLE,
            orThrow14,
            commonOrePlacement(10, HeightRangePlacement.triangle(VerticalAnchor.absolute(-24), VerticalAnchor.absolute(56)))
        );
        PlacementUtils.register(
            context, ORE_IRON_SMALL, orThrow15, commonOrePlacement(10, HeightRangePlacement.uniform(VerticalAnchor.bottom(), VerticalAnchor.absolute(72)))
        );
        PlacementUtils.register(
            context, ORE_GOLD_EXTRA, orThrow16, commonOrePlacement(50, HeightRangePlacement.uniform(VerticalAnchor.absolute(32), VerticalAnchor.absolute(256)))
        );
        PlacementUtils.register(
            context, ORE_GOLD, orThrow17, commonOrePlacement(4, HeightRangePlacement.triangle(VerticalAnchor.absolute(-64), VerticalAnchor.absolute(32)))
        );
        PlacementUtils.register(
            context,
            ORE_GOLD_LOWER,
            orThrow17,
            orePlacement(CountPlacement.of(UniformInt.of(0, 1)), HeightRangePlacement.uniform(VerticalAnchor.absolute(-64), VerticalAnchor.absolute(-48)))
        );
        PlacementUtils.register(
            context, ORE_REDSTONE, orThrow18, commonOrePlacement(4, HeightRangePlacement.uniform(VerticalAnchor.bottom(), VerticalAnchor.absolute(15)))
        );
        PlacementUtils.register(
            context,
            ORE_REDSTONE_LOWER,
            orThrow18,
            commonOrePlacement(8, HeightRangePlacement.triangle(VerticalAnchor.aboveBottom(-32), VerticalAnchor.aboveBottom(32)))
        );
        PlacementUtils.register(
            context,
            ORE_DIAMOND,
            orThrow19,
            commonOrePlacement(7, HeightRangePlacement.triangle(VerticalAnchor.aboveBottom(-80), VerticalAnchor.aboveBottom(80)))
        );
        PlacementUtils.register(
            context,
            ORE_DIAMOND_MEDIUM,
            orThrow20,
            commonOrePlacement(2, HeightRangePlacement.uniform(VerticalAnchor.absolute(-64), VerticalAnchor.absolute(-4)))
        );
        PlacementUtils.register(
            context,
            ORE_DIAMOND_LARGE,
            orThrow21,
            rareOrePlacement(9, HeightRangePlacement.triangle(VerticalAnchor.aboveBottom(-80), VerticalAnchor.aboveBottom(80)))
        );
        PlacementUtils.register(
            context,
            ORE_DIAMOND_BURIED,
            orThrow22,
            commonOrePlacement(4, HeightRangePlacement.triangle(VerticalAnchor.aboveBottom(-80), VerticalAnchor.aboveBottom(80)))
        );
        PlacementUtils.register(
            context, ORE_LAPIS, orThrow23, commonOrePlacement(2, HeightRangePlacement.triangle(VerticalAnchor.absolute(-32), VerticalAnchor.absolute(32)))
        );
        PlacementUtils.register(
            context, ORE_LAPIS_BURIED, orThrow24, commonOrePlacement(4, HeightRangePlacement.uniform(VerticalAnchor.bottom(), VerticalAnchor.absolute(64)))
        );
        PlacementUtils.register(
            context, ORE_INFESTED, orThrow25, commonOrePlacement(14, HeightRangePlacement.uniform(VerticalAnchor.bottom(), VerticalAnchor.absolute(63)))
        );
        PlacementUtils.register(
            context, ORE_EMERALD, orThrow26, commonOrePlacement(100, HeightRangePlacement.triangle(VerticalAnchor.absolute(-16), VerticalAnchor.absolute(480)))
        );
        PlacementUtils.register(
            context,
            ORE_ANCIENT_DEBRIS_LARGE,
            orThrow27,
            InSquarePlacement.spread(),
            HeightRangePlacement.triangle(VerticalAnchor.absolute(8), VerticalAnchor.absolute(24)),
            BiomeFilter.biome()
        );
        PlacementUtils.register(context, ORE_ANCIENT_DEBRIS_SMALL, orThrow28, InSquarePlacement.spread(), PlacementUtils.RANGE_8_8, BiomeFilter.biome());
        PlacementUtils.register(
            context, ORE_COPPER, orThrow29, commonOrePlacement(16, HeightRangePlacement.triangle(VerticalAnchor.absolute(-16), VerticalAnchor.absolute(112)))
        );
        PlacementUtils.register(
            context,
            ORE_COPPER_LARGE,
            orThrow30,
            commonOrePlacement(16, HeightRangePlacement.triangle(VerticalAnchor.absolute(-16), VerticalAnchor.absolute(112)))
        );
        PlacementUtils.register(context, ORE_CLAY, orThrow31, commonOrePlacement(46, PlacementUtils.RANGE_BOTTOM_TO_MAX_TERRAIN_HEIGHT));
    }
}
