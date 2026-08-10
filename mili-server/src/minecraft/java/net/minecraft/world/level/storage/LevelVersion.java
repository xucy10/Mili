package net.minecraft.world.level.storage;

import com.mojang.serialization.Dynamic;
import com.mojang.serialization.OptionalDynamic;
import net.minecraft.SharedConstants;

public class LevelVersion {
    private final int levelDataVersion;
    private final long lastPlayed;
    private final String minecraftVersionName;
    private final DataVersion minecraftVersion;
    private final boolean snapshot;

    private LevelVersion(int levelDataVersion, long lastPlayed, String minecraftVersionName, int minecraftVersion, String series, boolean snapshot) {
        this.levelDataVersion = levelDataVersion;
        this.lastPlayed = lastPlayed;
        this.minecraftVersionName = minecraftVersionName;
        this.minecraftVersion = new DataVersion(minecraftVersion, series);
        this.snapshot = snapshot;
    }

    public static LevelVersion parse(Dynamic<?> tag) {
        int _int = tag.get("version").asInt(0);
        long _long = tag.get("LastPlayed").asLong(0L);
        OptionalDynamic<?> optionalDynamic = tag.get("Version");
        return optionalDynamic.result().isPresent()
            ? new LevelVersion(
                _int,
                _long,
                optionalDynamic.get("Name").asString(SharedConstants.getCurrentVersion().name()),
                optionalDynamic.get("Id").asInt(SharedConstants.getCurrentVersion().dataVersion().version()),
                optionalDynamic.get("Series").asString("main"),
                optionalDynamic.get("Snapshot").asBoolean(!SharedConstants.getCurrentVersion().stable())
            )
            : new LevelVersion(_int, _long, "", 0, "main", false);
    }

    public int levelDataVersion() {
        return this.levelDataVersion;
    }

    public long lastPlayed() {
        return this.lastPlayed;
    }

    public String minecraftVersionName() {
        return this.minecraftVersionName;
    }

    public DataVersion minecraftVersion() {
        return this.minecraftVersion;
    }

    public boolean snapshot() {
        return this.snapshot;
    }
}
