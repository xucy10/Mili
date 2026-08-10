package net.minecraft.world.level.storage;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.CrashReportCategory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.timers.TimerQueue;
import org.jspecify.annotations.Nullable;

public interface ServerLevelData extends WritableLevelData {
    String getLevelName();

    void setThundering(boolean thundering);

    int getRainTime();

    void setRainTime(int time);

    void setThunderTime(int time);

    int getThunderTime();

    @Override
    default void fillCrashReportCategory(CrashReportCategory category, LevelHeightAccessor level) {
        WritableLevelData.super.fillCrashReportCategory(category, level);
        category.setDetail("Level name", this::getLevelName);
        category.setDetail(
            "Level game mode",
            () -> String.format(
                Locale.ROOT,
                "Game mode: %s (ID %d). Hardcore: %b. Commands: %b",
                this.getGameType().getName(),
                this.getGameType().getId(),
                this.isHardcore(),
                this.isAllowCommands()
            )
        );
        category.setDetail(
            "Level weather",
            () -> String.format(
                Locale.ROOT,
                "Rain time: %d (now: %b), thunder time: %d (now: %b)",
                this.getRainTime(),
                this.isRaining(),
                this.getThunderTime(),
                this.isThundering()
            )
        );
    }

    int getClearWeatherTime();

    void setClearWeatherTime(int time);

    int getWanderingTraderSpawnDelay();

    void setWanderingTraderSpawnDelay(int delay);

    int getWanderingTraderSpawnChance();

    void setWanderingTraderSpawnChance(int chance);

    @Nullable UUID getWanderingTraderId();

    void setWanderingTraderId(UUID id);

    GameType getGameType();

    @Deprecated
    Optional<WorldBorder.Settings> getLegacyWorldBorderSettings();

    @Deprecated
    void setLegacyWorldBorderSettings(Optional<WorldBorder.Settings> settings);

    boolean isInitialized();

    void setInitialized(boolean initialized);

    boolean isAllowCommands();

    void setGameType(GameType type);

    TimerQueue<MinecraftServer> getScheduledEvents();

    void setGameTime(long time);

    void setDayTime(long time);

    GameRules getGameRules();
}
