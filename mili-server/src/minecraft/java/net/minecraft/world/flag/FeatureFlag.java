package net.minecraft.world.flag;

public class FeatureFlag {
    public final FeatureFlagUniverse universe;
    public final long mask;

    FeatureFlag(FeatureFlagUniverse universe, int maskBit) {
        this.universe = universe;
        this.mask = 1L << maskBit;
    }
}
