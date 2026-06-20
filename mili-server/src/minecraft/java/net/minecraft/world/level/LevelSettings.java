package net.minecraft.world.level;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.gamerules.GameRules;
import org.slf4j.Logger;

public final class LevelSettings {
    private static final Logger LOGGER = LogUtils.getLogger();
    public String levelName;
    private final GameType gameType;
    public boolean hardcore;
    private final Difficulty difficulty;
    private final boolean allowCommands;
    private final GameRules gameRules;
    private final WorldDataConfiguration dataConfiguration;

    public LevelSettings(
        String levelName,
        GameType gameType,
        boolean hardcore,
        Difficulty difficulty,
        boolean allowCommands,
        GameRules gameRules,
        WorldDataConfiguration dataConfiguration
    ) {
        this.levelName = levelName;
        this.gameType = gameType;
        this.hardcore = hardcore;
        this.difficulty = difficulty;
        this.allowCommands = allowCommands;
        this.gameRules = gameRules;
        this.dataConfiguration = dataConfiguration;
    }

    public static LevelSettings parse(Dynamic<?> levelData, WorldDataConfiguration dataConfiguration) {
        GameType gameType = GameType.byId(levelData.get("GameType").asInt(0));
        return new LevelSettings(
            levelData.get("LevelName").asString(""),
            gameType,
            levelData.get("hardcore").asBoolean(false),
            levelData.get("Difficulty").asNumber().map(number -> Difficulty.byId(number.byteValue())).result().orElse(Difficulty.NORMAL),
            levelData.get("allowCommands").asBoolean(gameType == GameType.CREATIVE),
            GameRules.codec(dataConfiguration.enabledFeatures())
                .parse(levelData.get("game_rules").orElseEmptyMap())
                .resultOrPartial(LOGGER::warn)
                .orElseThrow(),
            dataConfiguration
        );
    }

    public String levelName() {
        return this.levelName;
    }

    public GameType gameType() {
        return this.gameType;
    }

    public boolean hardcore() {
        return this.hardcore;
    }

    public Difficulty difficulty() {
        return this.difficulty;
    }

    public boolean allowCommands() {
        return this.allowCommands;
    }

    public GameRules gameRules() {
        return this.gameRules;
    }

    public WorldDataConfiguration getDataConfiguration() {
        return this.dataConfiguration;
    }

    public LevelSettings withGameType(GameType gameType) {
        return new LevelSettings(this.levelName, gameType, this.hardcore, this.difficulty, this.allowCommands, this.gameRules, this.dataConfiguration);
    }

    public LevelSettings withDifficulty(Difficulty difficulty) {
        return new LevelSettings(this.levelName, this.gameType, this.hardcore, difficulty, this.allowCommands, this.gameRules, this.dataConfiguration);
    }

    public LevelSettings withDataConfiguration(WorldDataConfiguration dataConfiguration) {
        return new LevelSettings(this.levelName, this.gameType, this.hardcore, this.difficulty, this.allowCommands, this.gameRules, dataConfiguration);
    }

    public LevelSettings copy() {
        return new LevelSettings(
            this.levelName,
            this.gameType,
            this.hardcore,
            this.difficulty,
            this.allowCommands,
            this.gameRules.copy(this.dataConfiguration.enabledFeatures()),
            this.dataConfiguration
        );
    }
}
