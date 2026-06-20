package net.minecraft.world.level.levelgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.Util;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.structure.StructureSet;

public class FlatLevelSource extends ChunkGenerator {
    public static final MapCodec<FlatLevelSource> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(FlatLevelGeneratorSettings.CODEC.fieldOf("settings").forGetter(FlatLevelSource::settings))
            .apply(instance, instance.stable(FlatLevelSource::new))
    );
    private final FlatLevelGeneratorSettings settings;

    public FlatLevelSource(FlatLevelGeneratorSettings settings) {
        // CraftBukkit start
        this(settings, new FixedBiomeSource(settings.getBiome()));
    }
    public FlatLevelSource(FlatLevelGeneratorSettings settings, net.minecraft.world.level.biome.BiomeSource biomeSource) {
        super(biomeSource, Util.memoize(settings::adjustGenerationSettings));
        // CraftBukkit end
        this.settings = settings;
    }

    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structureSetLookup, RandomState randomState, long seed, org.spigotmc.SpigotWorldConfig conf) { // Spigot
        Stream<Holder<StructureSet>> stream = this.settings
            .structureOverrides()
            .map(HolderSet::stream)
            .orElseGet(() -> structureSetLookup.listElements().map(reference -> (Holder<StructureSet>)reference));
        return ChunkGeneratorStructureState.createForFlat(randomState, seed, this.biomeSource, stream, conf); // Spigot
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    public FlatLevelGeneratorSettings settings() {
        return this.settings;
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structureManager, RandomState random, ChunkAccess chunk) {
    }

    @Override
    public int getSpawnHeight(LevelHeightAccessor level) {
        return level.getMinY() + Math.min(level.getHeight(), this.settings.getLayers().size());
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess chunk) {
        List<BlockState> layers = this.settings.getLayers();
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        Heightmap heightmapUnprimed = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap heightmapUnprimed1 = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);

        for (int i = 0; i < Math.min(chunk.getHeight(), layers.size()); i++) {
            BlockState blockState = layers.get(i);
            if (blockState != null) {
                int i1 = chunk.getMinY() + i;

                for (int i2 = 0; i2 < 16; i2++) {
                    for (int i3 = 0; i3 < 16; i3++) {
                        chunk.setBlockState(mutableBlockPos.set(i2, i1, i3), blockState);
                        heightmapUnprimed.update(i2, i1, i3, blockState);
                        heightmapUnprimed1.update(i2, i1, i3, blockState);
                    }
                }
            }
        }

        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        List<BlockState> layers = this.settings.getLayers();

        for (int min = Math.min(layers.size() - 1, level.getMaxY()); min >= 0; min--) {
            BlockState blockState = layers.get(min);
            if (blockState != null && type.isOpaque().test(blockState)) {
                return level.getMinY() + min + 1;
            }
        }

        return level.getMinY();
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor height, RandomState random) {
        return new NoiseColumn(
            height.getMinY(),
            this.settings
                .getLayers()
                .stream()
                .limit(height.getHeight())
                .map(state -> state == null ? Blocks.AIR.defaultBlockState() : state)
                .toArray(BlockState[]::new)
        );
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
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
        return -63;
    }
}
