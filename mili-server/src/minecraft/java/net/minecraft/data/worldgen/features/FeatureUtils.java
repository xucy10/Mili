package net.minecraft.data.worldgen.features;

import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.placement.PlacementUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.RandomPatchConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

public class FeatureUtils {
    public static void bootstrap(BootstrapContext<ConfiguredFeature<?, ?>> context) {
        AquaticFeatures.bootstrap(context);
        CaveFeatures.bootstrap(context);
        EndFeatures.bootstrap(context);
        MiscOverworldFeatures.bootstrap(context);
        NetherFeatures.bootstrap(context);
        OreFeatures.bootstrap(context);
        PileFeatures.bootstrap(context);
        TreeFeatures.bootstrap(context);
        VegetationFeatures.bootstrap(context);
    }

    private static BlockPredicate simplePatchPredicate(List<Block> blocks) {
        BlockPredicate blockPredicate;
        if (!blocks.isEmpty()) {
            blockPredicate = BlockPredicate.allOf(BlockPredicate.ONLY_IN_AIR_PREDICATE, BlockPredicate.matchesBlocks(Direction.DOWN.getUnitVec3i(), blocks));
        } else {
            blockPredicate = BlockPredicate.ONLY_IN_AIR_PREDICATE;
        }

        return blockPredicate;
    }

    public static RandomPatchConfiguration simpleRandomPatchConfiguration(int tries, Holder<PlacedFeature> feature) {
        return new RandomPatchConfiguration(tries, 7, 3, feature);
    }

    public static <FC extends FeatureConfiguration, F extends Feature<FC>> RandomPatchConfiguration simplePatchConfiguration(
        F feature, FC config, List<Block> blocks, int tries
    ) {
        return simpleRandomPatchConfiguration(tries, PlacementUtils.filtered(feature, config, simplePatchPredicate(blocks)));
    }

    public static <FC extends FeatureConfiguration, F extends Feature<FC>> RandomPatchConfiguration simplePatchConfiguration(
        F feature, FC config, List<Block> blocks
    ) {
        return simplePatchConfiguration(feature, config, blocks, 96);
    }

    public static <FC extends FeatureConfiguration, F extends Feature<FC>> RandomPatchConfiguration simplePatchConfiguration(F feature, FC config) {
        return simplePatchConfiguration(feature, config, List.of(), 96);
    }

    public static ResourceKey<ConfiguredFeature<?, ?>> createKey(String name) {
        return ResourceKey.create(Registries.CONFIGURED_FEATURE, Identifier.withDefaultNamespace(name));
    }

    public static void register(
        BootstrapContext<ConfiguredFeature<?, ?>> context, ResourceKey<ConfiguredFeature<?, ?>> key, Feature<NoneFeatureConfiguration> feature
    ) {
        register(context, key, feature, FeatureConfiguration.NONE);
    }

    public static <FC extends FeatureConfiguration, F extends Feature<FC>> void register(
        BootstrapContext<ConfiguredFeature<?, ?>> context, ResourceKey<ConfiguredFeature<?, ?>> key, F feature, FC config
    ) {
        context.register(key, new ConfiguredFeature(feature, config));
    }
}
