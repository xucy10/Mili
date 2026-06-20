package net.minecraft.server.level.progress;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

public interface LevelLoadListener {
    static LevelLoadListener compose(final LevelLoadListener levelLoadListener1, final LevelLoadListener levelLoadListener2) {
        return new LevelLoadListener() {
            @Override
            public void start(LevelLoadListener.Stage stage, int totalChunks) {
                levelLoadListener1.start(stage, totalChunks);
                levelLoadListener2.start(stage, totalChunks);
            }

            @Override
            public void update(LevelLoadListener.Stage stage, int readyChunks, int totalChunks) {
                levelLoadListener1.update(stage, readyChunks, totalChunks);
                levelLoadListener2.update(stage, readyChunks, totalChunks);
            }

            @Override
            public void finish(LevelLoadListener.Stage stage) {
                levelLoadListener1.finish(stage);
                levelLoadListener2.finish(stage);
            }

            @Override
            public void updateFocus(ResourceKey<Level> dimension, ChunkPos chunkPos) {
                levelLoadListener1.updateFocus(dimension, chunkPos);
                levelLoadListener2.updateFocus(dimension, chunkPos);
            }
        };
    }

    void start(LevelLoadListener.Stage stage, int totalChunks);

    void update(LevelLoadListener.Stage stage, int readyChunks, int totalChunks);

    void finish(LevelLoadListener.Stage stage);

    void updateFocus(ResourceKey<Level> dimension, ChunkPos chunkPos);

    public static enum Stage {
        START_SERVER,
        PREPARE_GLOBAL_SPAWN,
        LOAD_INITIAL_CHUNKS,
        LOAD_PLAYER_CHUNKS;
    }

    // Paper start
    static LevelLoadListener noop() {
        return new LevelLoadListener() {
            @Override
            public void start(final net.minecraft.server.level.progress.LevelLoadListener.Stage stage, final int totalChunks) {}
            @Override
            public void update(final net.minecraft.server.level.progress.LevelLoadListener.Stage stage, final int readyChunks, final int totalChunks) {}
            @Override
            public void finish(final net.minecraft.server.level.progress.LevelLoadListener.Stage stage) {}
            @Override
            public void updateFocus(final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, final net.minecraft.world.level.ChunkPos chunkPos) {}
        };
    }
    // Paper end
}
