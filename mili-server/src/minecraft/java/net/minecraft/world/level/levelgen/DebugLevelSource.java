package net.minecraft.world.level.levelgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.blending.Blender;

public class DebugLevelSource extends ChunkGenerator {
    public static final MapCodec<DebugLevelSource> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(RegistryOps.retrieveElement(Biomes.PLAINS)).apply(instance, instance.stable(DebugLevelSource::new))
    );
    private static final int BLOCK_MARGIN = 2;
    private static final List<BlockState> ALL_BLOCKS = StreamSupport.stream(BuiltInRegistries.BLOCK.spliterator(), false)
        .flatMap(block -> block.getStateDefinition().getPossibleStates().stream())
        .collect(Collectors.toList());
    private static final int GRID_WIDTH = Mth.ceil(Mth.sqrt(ALL_BLOCKS.size()));
    private static final int GRID_HEIGHT = Mth.ceil((float)ALL_BLOCKS.size() / GRID_WIDTH);
    protected static final BlockState AIR = Blocks.AIR.defaultBlockState();
    protected static final BlockState BARRIER = Blocks.BARRIER.defaultBlockState();
    public static final int HEIGHT = 70;
    public static final int BARRIER_HEIGHT = 60;

    public DebugLevelSource(Holder.Reference<Biome> biomeSource) {
        super(new FixedBiomeSource(biomeSource));
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structureManager, RandomState random, ChunkAccess chunk) {
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        ChunkPos pos = chunk.getPos();
        int i = pos.x;
        int i1 = pos.z;

        for (int i2 = 0; i2 < 16; i2++) {
            for (int i3 = 0; i3 < 16; i3++) {
                int blockPosCoord = SectionPos.sectionToBlockCoord(i, i2);
                int blockPosCoord1 = SectionPos.sectionToBlockCoord(i1, i3);
                level.setBlock(mutableBlockPos.set(blockPosCoord, 60, blockPosCoord1), BARRIER, Block.UPDATE_CLIENTS);
                BlockState blockStateFor = getBlockStateFor(blockPosCoord, blockPosCoord1);
                level.setBlock(mutableBlockPos.set(blockPosCoord, 70, blockPosCoord1), blockStateFor, Block.UPDATE_CLIENTS);
            }
        }
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess chunk) {
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        return 0;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor height, RandomState random) {
        return new NoiseColumn(0, new BlockState[0]);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
    }

    public static BlockState getBlockStateFor(int chunkX, int chunkZ) {
        BlockState blockState = AIR;
        if (chunkX > 0 && chunkZ > 0 && chunkX % 2 != 0 && chunkZ % 2 != 0) {
            chunkX /= 2;
            chunkZ /= 2;
            if (chunkX <= GRID_WIDTH && chunkZ <= GRID_HEIGHT) {
                int abs = Mth.abs(chunkX * GRID_WIDTH + chunkZ);
                if (abs < ALL_BLOCKS.size()) {
                    blockState = ALL_BLOCKS.get(abs);
                }
            }
        }

        return blockState;
    }

    @Override
    public void applyCarvers(
        WorldGenRegion region, long seed, RandomState random, BiomeManager biomeManager, StructureManager structureManager, ChunkAccess chunk
    ) {
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
    }

    @Override
    public int getMinY() {
        return 0;
    }

    @Override
    public int getGenDepth() {
        return 384;
    }

    @Override
    public int getSeaLevel() {
        return 63;
    }
}
