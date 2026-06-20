package net.minecraft.world.level.levelgen.presets;

import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.biome.TheEndBiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.DebugLevelSource;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.StructureSet;

public class WorldPresets {
    public static final ResourceKey<WorldPreset> NORMAL = register("normal");
    public static final ResourceKey<WorldPreset> FLAT = register("flat");
    public static final ResourceKey<WorldPreset> LARGE_BIOMES = register("large_biomes");
    public static final ResourceKey<WorldPreset> AMPLIFIED = register("amplified");
    public static final ResourceKey<WorldPreset> SINGLE_BIOME_SURFACE = register("single_biome_surface");
    public static final ResourceKey<WorldPreset> DEBUG = register("debug_all_block_states");

    public static void bootstrap(BootstrapContext<WorldPreset> context) {
        new WorldPresets.Bootstrap(context).bootstrap();
    }

    private static ResourceKey<WorldPreset> register(String name) {
        return ResourceKey.create(Registries.WORLD_PRESET, Identifier.withDefaultNamespace(name));
    }

    public static Optional<ResourceKey<WorldPreset>> fromSettings(WorldDimensions worldDimensions) {
        return worldDimensions.get(LevelStem.OVERWORLD).flatMap(levelStem -> {
            return switch (levelStem.generator()) {
                case FlatLevelSource flatLevelSource -> Optional.of(FLAT);
                case DebugLevelSource debugLevelSource -> Optional.of(DEBUG);
                case NoiseBasedChunkGenerator noiseBasedChunkGenerator -> Optional.of(NORMAL);
                default -> Optional.empty();
            };
        });
    }

    public static WorldDimensions createNormalWorldDimensions(HolderLookup.Provider registries) {
        return registries.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(NORMAL).value().createWorldDimensions();
    }

    public static LevelStem getNormalOverworld(HolderLookup.Provider registries) {
        return registries.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(NORMAL).value().overworld().orElseThrow();
    }

    public static WorldDimensions createFlatWorldDimensions(HolderLookup.Provider registries) {
        return registries.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(FLAT).value().createWorldDimensions();
    }

    static class Bootstrap {
        private final BootstrapContext<WorldPreset> context;
        private final HolderGetter<NoiseGeneratorSettings> noiseSettings;
        private final HolderGetter<Biome> biomes;
        private final HolderGetter<PlacedFeature> placedFeatures;
        private final HolderGetter<StructureSet> structureSets;
        private final HolderGetter<MultiNoiseBiomeSourceParameterList> multiNoiseBiomeSourceParameterLists;
        private final Holder<DimensionType> overworldDimensionType;
        private final LevelStem netherStem;
        private final LevelStem endStem;

        Bootstrap(BootstrapContext<WorldPreset> context) {
            this.context = context;
            HolderGetter<DimensionType> holderGetter = context.lookup(Registries.DIMENSION_TYPE);
            this.noiseSettings = context.lookup(Registries.NOISE_SETTINGS);
            this.biomes = context.lookup(Registries.BIOME);
            this.placedFeatures = context.lookup(Registries.PLACED_FEATURE);
            this.structureSets = context.lookup(Registries.STRUCTURE_SET);
            this.multiNoiseBiomeSourceParameterLists = context.lookup(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST);
            this.overworldDimensionType = holderGetter.getOrThrow(BuiltinDimensionTypes.OVERWORLD);
            Holder<DimensionType> orThrow = holderGetter.getOrThrow(BuiltinDimensionTypes.NETHER);
            Holder<NoiseGeneratorSettings> orThrow1 = this.noiseSettings.getOrThrow(NoiseGeneratorSettings.NETHER);
            Holder.Reference<MultiNoiseBiomeSourceParameterList> orThrow2 = this.multiNoiseBiomeSourceParameterLists
                .getOrThrow(MultiNoiseBiomeSourceParameterLists.NETHER);
            this.netherStem = new LevelStem(orThrow, new NoiseBasedChunkGenerator(MultiNoiseBiomeSource.createFromPreset(orThrow2), orThrow1));
            Holder<DimensionType> orThrow3 = holderGetter.getOrThrow(BuiltinDimensionTypes.END);
            Holder<NoiseGeneratorSettings> orThrow4 = this.noiseSettings.getOrThrow(NoiseGeneratorSettings.END);
            this.endStem = new LevelStem(orThrow3, new NoiseBasedChunkGenerator(TheEndBiomeSource.create(this.biomes), orThrow4));
        }

        private LevelStem makeOverworld(ChunkGenerator generator) {
            return new LevelStem(this.overworldDimensionType, generator);
        }

        private LevelStem makeNoiseBasedOverworld(BiomeSource biomeSource, Holder<NoiseGeneratorSettings> settings) {
            return this.makeOverworld(new NoiseBasedChunkGenerator(biomeSource, settings));
        }

        private WorldPreset createPresetWithCustomOverworld(LevelStem overworldStem) {
            return new WorldPreset(Map.of(LevelStem.OVERWORLD, overworldStem, LevelStem.NETHER, this.netherStem, LevelStem.END, this.endStem));
        }

        private void registerCustomOverworldPreset(ResourceKey<WorldPreset> dimensionKey, LevelStem levelStem) {
            this.context.register(dimensionKey, this.createPresetWithCustomOverworld(levelStem));
        }

        private void registerOverworlds(BiomeSource biomeSource) {
            Holder<NoiseGeneratorSettings> orThrow = this.noiseSettings.getOrThrow(NoiseGeneratorSettings.OVERWORLD);
            this.registerCustomOverworldPreset(WorldPresets.NORMAL, this.makeNoiseBasedOverworld(biomeSource, orThrow));
            Holder<NoiseGeneratorSettings> orThrow1 = this.noiseSettings.getOrThrow(NoiseGeneratorSettings.LARGE_BIOMES);
            this.registerCustomOverworldPreset(WorldPresets.LARGE_BIOMES, this.makeNoiseBasedOverworld(biomeSource, orThrow1));
            Holder<NoiseGeneratorSettings> orThrow2 = this.noiseSettings.getOrThrow(NoiseGeneratorSettings.AMPLIFIED);
            this.registerCustomOverworldPreset(WorldPresets.AMPLIFIED, this.makeNoiseBasedOverworld(biomeSource, orThrow2));
        }

        public void bootstrap() {
            Holder.Reference<MultiNoiseBiomeSourceParameterList> orThrow = this.multiNoiseBiomeSourceParameterLists
                .getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
            this.registerOverworlds(MultiNoiseBiomeSource.createFromPreset(orThrow));
            Holder<NoiseGeneratorSettings> orThrow1 = this.noiseSettings.getOrThrow(NoiseGeneratorSettings.OVERWORLD);
            Holder.Reference<Biome> orThrow2 = this.biomes.getOrThrow(Biomes.PLAINS);
            this.registerCustomOverworldPreset(WorldPresets.SINGLE_BIOME_SURFACE, this.makeNoiseBasedOverworld(new FixedBiomeSource(orThrow2), orThrow1));
            this.registerCustomOverworldPreset(
                WorldPresets.FLAT,
                this.makeOverworld(new FlatLevelSource(FlatLevelGeneratorSettings.getDefault(this.biomes, this.structureSets, this.placedFeatures)))
            );
            this.registerCustomOverworldPreset(WorldPresets.DEBUG, this.makeOverworld(new DebugLevelSource(orThrow2)));
        }
    }
}
