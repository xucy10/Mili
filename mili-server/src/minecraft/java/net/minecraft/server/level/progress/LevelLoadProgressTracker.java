package net.minecraft.server.level.progress;

import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

public class LevelLoadProgressTracker implements LevelLoadListener {
    private static final int PREPARE_SERVER_WEIGHT = 10;
    private static final int EXPECTED_PLAYER_CHUNKS = Mth.square(7);
    private final boolean includePlayerChunks;
    private int totalWeight;
    private int finalizedWeight;
    private int segmentWeight;
    private float segmentFraction;
    private volatile float progress;

    public LevelLoadProgressTracker(boolean includePlayerChunks) {
        this.includePlayerChunks = includePlayerChunks;
    }

    @Override
    public void start(LevelLoadListener.Stage stage, int totalChunks) {
        if (this.tracksStage(stage)) {
            switch (stage) {
                case LOAD_INITIAL_CHUNKS:
                    int i = this.includePlayerChunks ? EXPECTED_PLAYER_CHUNKS : 0;
                    this.totalWeight = 10 + totalChunks + i;
                    this.beginSegment(10);
                    this.finishSegment();
                    this.beginSegment(totalChunks);
                    break;
                case LOAD_PLAYER_CHUNKS:
                    this.beginSegment(EXPECTED_PLAYER_CHUNKS);
            }
        }
    }

    private void beginSegment(int segmentWeight) {
        this.segmentWeight = segmentWeight;
        this.segmentFraction = 0.0F;
        this.updateProgress();
    }

    @Override
    public void update(LevelLoadListener.Stage stage, int readyChunks, int totalChunks) {
        if (this.tracksStage(stage)) {
            this.segmentFraction = totalChunks == 0 ? 0.0F : (float)readyChunks / totalChunks;
            this.updateProgress();
        }
    }

    @Override
    public void finish(LevelLoadListener.Stage stage) {
        if (this.tracksStage(stage)) {
            this.finishSegment();
        }
    }

    private void finishSegment() {
        this.finalizedWeight = this.finalizedWeight + this.segmentWeight;
        this.segmentWeight = 0;
        this.updateProgress();
    }

    private boolean tracksStage(LevelLoadListener.Stage stage) {
        return switch (stage) {
            case LOAD_INITIAL_CHUNKS -> true;
            case LOAD_PLAYER_CHUNKS -> this.includePlayerChunks;
            default -> false;
        };
    }

    private void updateProgress() {
        if (this.totalWeight == 0) {
            this.progress = 0.0F;
        } else {
            float f = this.finalizedWeight + this.segmentFraction * this.segmentWeight;
            this.progress = f / this.totalWeight;
        }
    }

    public float get() {
        return this.progress;
    }

    @Override
    public void updateFocus(ResourceKey<Level> dimension, ChunkPos chunkPos) {
    }
}
