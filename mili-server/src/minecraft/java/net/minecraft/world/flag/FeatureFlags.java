package net.minecraft.world.flag;

import com.mojang.serialization.Codec;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.resources.Identifier;

public class FeatureFlags {
    public static final FeatureFlag VANILLA;
    public static final FeatureFlag TRADE_REBALANCE;
    public static final FeatureFlag REDSTONE_EXPERIMENTS;
    public static final FeatureFlag MINECART_IMPROVEMENTS;
    public static final FeatureFlagRegistry REGISTRY;
    public static final Codec<FeatureFlagSet> CODEC;
    public static final FeatureFlagSet VANILLA_SET;
    public static final FeatureFlagSet DEFAULT_FLAGS;

    public static String printMissingFlags(FeatureFlagSet enabledFeatures, FeatureFlagSet requestedFeatures) {
        return printMissingFlags(REGISTRY, enabledFeatures, requestedFeatures);
    }

    public static String printMissingFlags(FeatureFlagRegistry registry, FeatureFlagSet enabledFeatures, FeatureFlagSet requestedFeatures) {
        Set<Identifier> set = registry.toNames(requestedFeatures);
        Set<Identifier> set1 = registry.toNames(enabledFeatures);
        return set.stream().filter(requestFeature -> !set1.contains(requestFeature)).map(Identifier::toString).collect(Collectors.joining(", "));
    }

    public static boolean isExperimental(FeatureFlagSet set) {
        return !set.isSubsetOf(VANILLA_SET);
    }

    static {
        FeatureFlagRegistry.Builder builder = new FeatureFlagRegistry.Builder("main");
        VANILLA = builder.createVanilla("vanilla");
        TRADE_REBALANCE = builder.createVanilla("trade_rebalance");
        REDSTONE_EXPERIMENTS = builder.createVanilla("redstone_experiments");
        MINECART_IMPROVEMENTS = builder.createVanilla("minecart_improvements");
        REGISTRY = builder.build();
        CODEC = REGISTRY.codec();
        VANILLA_SET = FeatureFlagSet.of(VANILLA);
        DEFAULT_FLAGS = VANILLA_SET;
    }
}
