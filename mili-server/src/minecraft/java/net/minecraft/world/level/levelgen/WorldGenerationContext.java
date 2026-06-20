package net.minecraft.world.level.levelgen;

import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;

public class WorldGenerationContext {
    private final int minY;
    private final int height;
    // Paper start - Flat bedrock generator settings
    private final @javax.annotation.Nullable net.minecraft.world.level.Level serverLevel;

    public WorldGenerationContext(ChunkGenerator generator, LevelHeightAccessor level) {
        this(generator, level, null);
    }
    public WorldGenerationContext(ChunkGenerator generator, LevelHeightAccessor level, @javax.annotation.Nullable net.minecraft.world.level.Level serverLevel) {
        this.serverLevel = serverLevel;
        // Paper end - Flat bedrock generator settings
        this.minY = Math.max(level.getMinY(), generator.getMinY());
        this.height = Math.min(level.getHeight(), generator.getGenDepth());
    }

    public int getMinGenY() {
        return this.minY;
    }

    public int getGenDepth() {
        return this.height;
    }

    // Paper start - Flat bedrock generator settings
    public net.minecraft.world.level.Level level() {
        if (this.serverLevel == null) {
            throw new NullPointerException("WorldGenerationContext was initialized without a Level, but WorldGenerationContext#level was called");
        }
        return this.serverLevel;
    }
    // Paper end - Flat bedrock generator settings
}
