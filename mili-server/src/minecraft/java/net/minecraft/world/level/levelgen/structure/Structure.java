package net.minecraft.world.level.levelgen.structure;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.profiling.jfr.JvmProfiler;
import net.minecraft.util.profiling.jfr.callback.ProfiledDuration;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

public abstract class Structure {
    public static final Codec<Structure> DIRECT_CODEC = BuiltInRegistries.STRUCTURE_TYPE.byNameCodec().dispatch(Structure::type, StructureType::codec);
    public static final Codec<Holder<Structure>> CODEC = RegistryFileCodec.create(Registries.STRUCTURE, DIRECT_CODEC);
    protected final Structure.StructureSettings settings;

    public static <S extends Structure> RecordCodecBuilder<S, Structure.StructureSettings> settingsCodec(Instance<S> instance) {
        return Structure.StructureSettings.CODEC.forGetter(structure -> structure.settings);
    }

    public static <S extends Structure> MapCodec<S> simpleCodec(Function<Structure.StructureSettings, S> factory) {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(settingsCodec(instance)).apply(instance, factory));
    }

    protected Structure(Structure.StructureSettings settings) {
        this.settings = settings;
    }

    public HolderSet<Biome> biomes() {
        return this.settings.biomes;
    }

    public Map<MobCategory, StructureSpawnOverride> spawnOverrides() {
        return this.settings.spawnOverrides;
    }

    public GenerationStep.Decoration step() {
        return this.settings.step;
    }

    public TerrainAdjustment terrainAdaptation() {
        return this.settings.terrainAdaptation;
    }

    public BoundingBox adjustBoundingBox(BoundingBox boundingBox) {
        return this.terrainAdaptation() != TerrainAdjustment.NONE ? boundingBox.inflatedBy(12) : boundingBox;
    }

    public StructureStart generate(
        Holder<Structure> structure,
        ResourceKey<Level> level,
        RegistryAccess registryAccess,
        ChunkGenerator chunkGenerator,
        BiomeSource biomeSource,
        RandomState randomState,
        StructureTemplateManager structureTemplateManager,
        long seed,
        ChunkPos chunkPos,
        int references,
        LevelHeightAccessor heightAccessor,
        Predicate<Holder<Biome>> validBiome
    ) {
        ProfiledDuration profiledDuration = JvmProfiler.INSTANCE.onStructureGenerate(chunkPos, level, structure);
        Structure.GenerationContext generationContext = new Structure.GenerationContext(
            registryAccess, chunkGenerator, biomeSource, randomState, structureTemplateManager, seed, chunkPos, heightAccessor, validBiome
        );
        Optional<Structure.GenerationStub> optional = this.findValidGenerationPoint(generationContext);
        if (optional.isPresent()) {
            StructurePiecesBuilder piecesBuilder = optional.get().getPiecesBuilder();
            StructureStart structureStart = new StructureStart(this, chunkPos, references, piecesBuilder.build());
            if (structureStart.isValid()) {
                if (profiledDuration != null) {
                    profiledDuration.finish(true);
                }

                return structureStart;
            }
        }

        if (profiledDuration != null) {
            profiledDuration.finish(false);
        }

        return StructureStart.INVALID_START;
    }

    protected static Optional<Structure.GenerationStub> onTopOfChunkCenter(
        Structure.GenerationContext context, Heightmap.Types heightmapTypes, Consumer<StructurePiecesBuilder> generator
    ) {
        ChunkPos chunkPos = context.chunkPos();
        int middleBlockX = chunkPos.getMiddleBlockX();
        int middleBlockZ = chunkPos.getMiddleBlockZ();
        int firstOccupiedHeight = context.chunkGenerator()
            .getFirstOccupiedHeight(middleBlockX, middleBlockZ, heightmapTypes, context.heightAccessor(), context.randomState());
        return Optional.of(new Structure.GenerationStub(new BlockPos(middleBlockX, firstOccupiedHeight, middleBlockZ), generator));
    }

    private static boolean isValidBiome(Structure.GenerationStub stub, Structure.GenerationContext context) {
        BlockPos blockPos = stub.position();
        return context.validBiome
            .test(
                context.chunkGenerator
                    .getBiomeSource()
                    .getNoiseBiome(
                        QuartPos.fromBlock(blockPos.getX()),
                        QuartPos.fromBlock(blockPos.getY()),
                        QuartPos.fromBlock(blockPos.getZ()),
                        context.randomState.sampler()
                    )
            );
    }

    public void afterPlace(
        WorldGenLevel level,
        StructureManager structureManager,
        ChunkGenerator chunkGenerator,
        RandomSource random,
        BoundingBox boundingBox,
        ChunkPos chunkPos,
        PiecesContainer pieces
    ) {
    }

    private static int[] getCornerHeights(Structure.GenerationContext context, int minX, int maxX, int minZ, int maxZ) {
        ChunkGenerator chunkGenerator = context.chunkGenerator();
        LevelHeightAccessor levelHeightAccessor = context.heightAccessor();
        RandomState randomState = context.randomState();
        return new int[]{
            chunkGenerator.getFirstOccupiedHeight(minX, minZ, Heightmap.Types.WORLD_SURFACE_WG, levelHeightAccessor, randomState),
            chunkGenerator.getFirstOccupiedHeight(minX, minZ + maxZ, Heightmap.Types.WORLD_SURFACE_WG, levelHeightAccessor, randomState),
            chunkGenerator.getFirstOccupiedHeight(minX + maxX, minZ, Heightmap.Types.WORLD_SURFACE_WG, levelHeightAccessor, randomState),
            chunkGenerator.getFirstOccupiedHeight(minX + maxX, minZ + maxZ, Heightmap.Types.WORLD_SURFACE_WG, levelHeightAccessor, randomState)
        };
    }

    public static int getMeanFirstOccupiedHeight(Structure.GenerationContext context, int minX, int maxX, int minZ, int maxZ) {
        int[] cornerHeights = getCornerHeights(context, minX, maxX, minZ, maxZ);
        return (cornerHeights[0] + cornerHeights[1] + cornerHeights[2] + cornerHeights[3]) / 4;
    }

    protected static int getLowestY(Structure.GenerationContext context, int maxX, int maxZ) {
        ChunkPos chunkPos = context.chunkPos();
        int minBlockX = chunkPos.getMinBlockX();
        int minBlockZ = chunkPos.getMinBlockZ();
        return getLowestY(context, minBlockX, minBlockZ, maxX, maxZ);
    }

    protected static int getLowestY(Structure.GenerationContext context, int minX, int minZ, int maxX, int maxZ) {
        int[] cornerHeights = getCornerHeights(context, minX, maxX, minZ, maxZ);
        return Math.min(Math.min(cornerHeights[0], cornerHeights[1]), Math.min(cornerHeights[2], cornerHeights[3]));
    }

    @Deprecated
    protected BlockPos getLowestYIn5by5BoxOffset7Blocks(Structure.GenerationContext context, Rotation rotation) {
        int i = 5;
        int i1 = 5;
        if (rotation == Rotation.CLOCKWISE_90) {
            i = -5;
        } else if (rotation == Rotation.CLOCKWISE_180) {
            i = -5;
            i1 = -5;
        } else if (rotation == Rotation.COUNTERCLOCKWISE_90) {
            i1 = -5;
        }

        ChunkPos chunkPos = context.chunkPos();
        int blockX = chunkPos.getBlockX(7);
        int blockZ = chunkPos.getBlockZ(7);
        return new BlockPos(blockX, getLowestY(context, blockX, blockZ, i, i1), blockZ);
    }

    protected abstract Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context);

    public Optional<Structure.GenerationStub> findValidGenerationPoint(Structure.GenerationContext context) {
        return this.findGenerationPoint(context).filter(stub -> isValidBiome(stub, context));
    }

    public abstract StructureType<?> type();

    public record GenerationContext(
        RegistryAccess registryAccess,
        ChunkGenerator chunkGenerator,
        BiomeSource biomeSource,
        RandomState randomState,
        StructureTemplateManager structureTemplateManager,
        WorldgenRandom random,
        long seed,
        ChunkPos chunkPos,
        LevelHeightAccessor heightAccessor,
        Predicate<Holder<Biome>> validBiome
    ) {
        public GenerationContext(
            RegistryAccess registryAccess,
            ChunkGenerator chunkGenerator,
            BiomeSource biomeSource,
            RandomState randomState,
            StructureTemplateManager structureTemplateManager,
            long seed,
            ChunkPos chunkPos,
            LevelHeightAccessor heightAccessor,
            Predicate<Holder<Biome>> validBiome
        ) {
            this(
                registryAccess,
                chunkGenerator,
                biomeSource,
                randomState,
                structureTemplateManager,
                makeRandom(seed, chunkPos),
                seed,
                chunkPos,
                heightAccessor,
                validBiome
            );
        }

        private static WorldgenRandom makeRandom(long seed, ChunkPos chunkPos) {
            // Leaf start - Matter - Secure Seed
            if (me.earthme.luminol.config.modules.function.SecureSeedConfig.enabled) {
                return new su.plo.matter.WorldgenCryptoRandom(chunkPos.x, chunkPos.z, su.plo.matter.Globals.Salt.GENERATE_FEATURE, seed);
            }
            // Leaf end - Matter - Secure Seed
            WorldgenRandom worldgenRandom = new WorldgenRandom(new LegacyRandomSource(0L));
            worldgenRandom.setLargeFeatureSeed(seed, chunkPos.x, chunkPos.z);
            return worldgenRandom;
        }
    }

    public record GenerationStub(BlockPos position, Either<Consumer<StructurePiecesBuilder>, StructurePiecesBuilder> generator) {
        public GenerationStub(BlockPos position, Consumer<StructurePiecesBuilder> generator) {
            this(position, Either.left(generator));
        }

        public StructurePiecesBuilder getPiecesBuilder() {
            return this.generator.map(consumer -> {
                StructurePiecesBuilder structurePiecesBuilder = new StructurePiecesBuilder();
                consumer.accept(structurePiecesBuilder);
                return structurePiecesBuilder;
            }, builder -> (StructurePiecesBuilder)builder);
        }
    }

    public record StructureSettings(
        HolderSet<Biome> biomes, Map<MobCategory, StructureSpawnOverride> spawnOverrides, GenerationStep.Decoration step, TerrainAdjustment terrainAdaptation
    ) {
        static final Structure.StructureSettings DEFAULT = new Structure.StructureSettings(
            HolderSet.direct(), Map.of(), GenerationStep.Decoration.SURFACE_STRUCTURES, TerrainAdjustment.NONE
        );
        public static final MapCodec<Structure.StructureSettings> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    RegistryCodecs.homogeneousList(Registries.BIOME).fieldOf("biomes").forGetter(Structure.StructureSettings::biomes),
                    Codec.simpleMap(MobCategory.CODEC, StructureSpawnOverride.CODEC, StringRepresentable.keys(MobCategory.values()))
                        .fieldOf("spawn_overrides")
                        .forGetter(Structure.StructureSettings::spawnOverrides),
                    GenerationStep.Decoration.CODEC.fieldOf("step").forGetter(Structure.StructureSettings::step),
                    TerrainAdjustment.CODEC
                        .optionalFieldOf("terrain_adaptation", DEFAULT.terrainAdaptation)
                        .forGetter(Structure.StructureSettings::terrainAdaptation)
                )
                .apply(instance, Structure.StructureSettings::new)
        );

        public StructureSettings(HolderSet<Biome> biomes) {
            this(biomes, DEFAULT.spawnOverrides, DEFAULT.step, DEFAULT.terrainAdaptation);
        }

        public static class Builder {
            private final HolderSet<Biome> biomes;
            private Map<MobCategory, StructureSpawnOverride> spawnOverrides;
            private GenerationStep.Decoration step;
            private TerrainAdjustment terrainAdaption;

            public Builder(HolderSet<Biome> biomes) {
                this.spawnOverrides = Structure.StructureSettings.DEFAULT.spawnOverrides;
                this.step = Structure.StructureSettings.DEFAULT.step;
                this.terrainAdaption = Structure.StructureSettings.DEFAULT.terrainAdaptation;
                this.biomes = biomes;
            }

            public Structure.StructureSettings.Builder spawnOverrides(Map<MobCategory, StructureSpawnOverride> spawnOverrides) {
                this.spawnOverrides = spawnOverrides;
                return this;
            }

            public Structure.StructureSettings.Builder generationStep(GenerationStep.Decoration generationStep) {
                this.step = generationStep;
                return this;
            }

            public Structure.StructureSettings.Builder terrainAdapation(TerrainAdjustment terrainAdaptation) {
                this.terrainAdaption = terrainAdaptation;
                return this;
            }

            public Structure.StructureSettings build() {
                return new Structure.StructureSettings(this.biomes, this.spawnOverrides, this.step, this.terrainAdaption);
            }
        }
    }
}
