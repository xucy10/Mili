package net.minecraft.world.level.storage;

import net.minecraft.SharedConstants;

public record DataVersion(int version, String series) {
    public static final String MAIN_SERIES = "main";

    public boolean isSideSeries() {
        return !this.series.equals("main");
    }

    public boolean isCompatible(DataVersion dataVersion) {
        return SharedConstants.DEBUG_OPEN_INCOMPATIBLE_WORLDS || this.series().equals(dataVersion.series());
    }
}
