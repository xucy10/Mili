package net.minecraft.world.flag;

public class FeatureFlagUniverse {
    private final String id;

    public FeatureFlagUniverse(String id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return this.id;
    }
}
