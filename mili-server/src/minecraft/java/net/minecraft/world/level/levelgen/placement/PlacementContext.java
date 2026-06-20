package net.minecraft.world.level.levelgen.placement;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldGenerationContext;

public class PlacementContext extends WorldGenerationContext {
    private final WorldGenLevel level;
    private final ChunkGenerator generator;
    private final Optional<PlacedFeature> topFeature;

    public PlacementContext(WorldGenLevel level, ChunkGenerator generator, Optional<PlacedFeature> topFeature) {
        super(generator, level, level.getLevel()); // Paper - Flat bedrock generator settings
        this.level = level;
        this.generator = generator;
        this.topFeature = topFeature;
    }

    public int getHeight(Heightmap.Types heightmapType, int x, int z) {
        return this.level.getHeight(heightmapType, x, z);
    }

    public CarvingMask getCarvingMask(ChunkPos chunkPos) {
        return ((ProtoChunk)this.level.getChunk(chunkPos.x, chunkPos.z)).getOrCreateCarvingMask();
    }

    public BlockState getBlockState(BlockPos pos) {
        return this.level.getBlockState(pos);
    }

    public int getMinY() {
        return this.level.getMinY();
    }

    public WorldGenLevel getLevel() {
        return this.level;
    }

    public Optional<PlacedFeature> topFeature() {
        return this.topFeature;
    }

    public ChunkGenerator generator() {
        return this.generator;
    }
}
