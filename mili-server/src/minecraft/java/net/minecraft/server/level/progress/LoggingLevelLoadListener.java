package net.minecraft.server.level.progress;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

public class LoggingLevelLoadListener implements LevelLoadListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final boolean includePlayerChunks;
    private final LevelLoadProgressTracker progressTracker;
    private boolean closed;
    private long startTime = Long.MAX_VALUE;
    private long nextLogTime = Long.MAX_VALUE;

    // Paper start - add level
    private final net.minecraft.server.level.ServerLevel level;
    public LoggingLevelLoadListener(boolean includePlayerChunks, net.minecraft.server.level.ServerLevel level) {
        this.level = level;
        // Paper end - add level
        this.includePlayerChunks = includePlayerChunks;
        this.progressTracker = new LevelLoadProgressTracker(includePlayerChunks);
    }

    public static LoggingLevelLoadListener forDedicatedServer() {
        return new LoggingLevelLoadListener(false, null); // Paper
    }

    public static LoggingLevelLoadListener forSingleplayer() {
        return new LoggingLevelLoadListener(true, null); // Paper
    }

    @Override
    public void start(LevelLoadListener.Stage stage, int totalChunks) {
        if (!this.closed) {
            if (this.startTime == Long.MAX_VALUE) {
                long millis = Util.getMillis();
                this.startTime = millis;
                this.nextLogTime = millis;
            }

            this.progressTracker.start(stage, totalChunks);
            switch (stage) {
                case PREPARE_GLOBAL_SPAWN:
                    // Paper start - log dimension
                    if (this.level != null) {
                        LOGGER.info("Selecting spawn point for world '{}'...", this.level.dimension().identifier());
                    } else {
                    LOGGER.info("Selecting global world spawn...");
                    }
                    break;
                case LOAD_INITIAL_CHUNKS:
                    if (this.level != null) {
                        LOGGER.info("Loading {} persistent chunks for world '{}'...", totalChunks, this.level.dimension().identifier());
                    } else {
                    LOGGER.info("Loading {} persistent chunks...", totalChunks);
                    }
                    // Paper end - log dimension
                    break;
                case LOAD_PLAYER_CHUNKS:
                    LOGGER.info("Loading {} chunks for player spawn...", totalChunks);
            }
        }
    }

    @Override
    public void update(LevelLoadListener.Stage stage, int readyChunks, int totalChunks) {
        if (!this.closed) {
            this.progressTracker.update(stage, readyChunks, totalChunks);
            if (Util.getMillis() > this.nextLogTime) {
                this.nextLogTime += 500L;
                int floor = Mth.floor(this.progressTracker.get() * 100.0F);
                LOGGER.info(Component.translatable("menu.preparingSpawn", floor).getString());
            }
        }
    }

    @Override
    public void finish(LevelLoadListener.Stage stage) {
        if (!this.closed) {
            this.progressTracker.finish(stage);
            LevelLoadListener.Stage stage1 = this.includePlayerChunks
                ? LevelLoadListener.Stage.LOAD_PLAYER_CHUNKS
                : LevelLoadListener.Stage.LOAD_INITIAL_CHUNKS;
            if (stage == stage1) {
                LOGGER.info("Prepared spawn area in {} ms", Util.getMillis() - this.startTime); // Paper
                this.nextLogTime = Long.MAX_VALUE;
                this.closed = true;
            }
        }
    }

    @Override
    public void updateFocus(ResourceKey<Level> dimension, ChunkPos chunkPos) {
    }
}
