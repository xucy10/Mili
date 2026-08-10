package net.minecraft.world.level.levelgen;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public final class RandomState {
    final PositionalRandomFactory random;
    private final HolderGetter<NormalNoise.NoiseParameters> noises;
    private final NoiseRouter router;
    private final Climate.Sampler sampler;
    private final SurfaceSystem surfaceSystem;
    private final PositionalRandomFactory aquiferRandom;
    private final PositionalRandomFactory oreRandom;
    private final Map<ResourceKey<NormalNoise.NoiseParameters>, NormalNoise> noiseIntances;
    private final Map<Identifier, PositionalRandomFactory> positionalRandoms;

    public static RandomState create(HolderGetter.Provider registries, ResourceKey<NoiseGeneratorSettings> settingsKey, long levelSeed) {
        return create(
            registries.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(settingsKey).value(), registries.lookupOrThrow(Registries.NOISE), levelSeed
        );
    }

    public static RandomState create(NoiseGeneratorSettings settings, HolderGetter<NormalNoise.NoiseParameters> noiseParametersGetter, long levelSeed) {
        return new RandomState(settings, noiseParametersGetter, levelSeed);
    }

    private RandomState(NoiseGeneratorSettings settings, HolderGetter<NormalNoise.NoiseParameters> noiseParametersGetter, final long levelSeed) {
        // this.random = settings.getRandomSource().newInstance(levelSeed).forkPositional(); // Luminol - Add secure seed V2 with Blake3
        // Luminol start - Add secure seed V2 with Blake3
        final long[] secureWorldSeed = (me.earthme.luminol.config.modules.function.SecureSeedConfig.enabled && me.earthme.luminol.config.modules.function.SecureSeedConfig.version == 2)
            ? su.plo.matter.HashingV2.expandLevelSeedTo1024Bits(levelSeed)
            : null;

        long terrainSeed = (me.earthme.luminol.config.modules.function.SecureSeedConfig.enabled && me.earthme.luminol.config.modules.function.SecureSeedConfig.version == 2)
            ? su.plo.matter.HashingV2.getTerrainSeed(secureWorldSeed, su.plo.matter.HashingV2.TerrainType.BASE_TERRAIN)
            : levelSeed;
        long aquiferSeed = (me.earthme.luminol.config.modules.function.SecureSeedConfig.enabled && me.earthme.luminol.config.modules.function.SecureSeedConfig.version == 2)
            ? su.plo.matter.HashingV2.getTerrainSeed(secureWorldSeed, su.plo.matter.HashingV2.TerrainType.AQUIFER)
            : levelSeed;
        long oreSeed = (me.earthme.luminol.config.modules.function.SecureSeedConfig.enabled && me.earthme.luminol.config.modules.function.SecureSeedConfig.version == 2)
            ? su.plo.matter.HashingV2.getTerrainSeed(secureWorldSeed, su.plo.matter.HashingV2.TerrainType.ORE)
            : levelSeed;
        // Luminol end
        this.random = settings.getRandomSource().newInstance(terrainSeed).forkPositional(); // Luminol - Add secure seed V2 with Blake3
        this.noises = noiseParametersGetter;
        //this.aquiferRandom = this.random.fromHashOf(Identifier.withDefaultNamespace("aquifer")).forkPositional(); // Luminol - Add secure seed V2 with Blake3
        //this.oreRandom = this.random.fromHashOf(Identifier.withDefaultNamespace("ore")).forkPositional(); // Luminol - Add secure seed V2 with Blake3
        this.aquiferRandom = settings.getRandomSource().newInstance(aquiferSeed).forkPositional(); // Luminol - Add secure seed V2 with Blake3
        this.oreRandom = settings.getRandomSource().newInstance(oreSeed).forkPositional(); // Luminol - Add secure seed V2 with Blake3
        this.noiseIntances = new ConcurrentHashMap<>();
        this.positionalRandoms = new ConcurrentHashMap<>();
        this.surfaceSystem = new SurfaceSystem(this, settings.defaultBlock(), settings.seaLevel(), this.random);
        final boolean useLegacyRandomSource = settings.useLegacyRandomSource();

        class NoiseWiringHelper implements DensityFunction.Visitor {
            private final Map<DensityFunction, DensityFunction> wrapped = new HashMap<>();

            private RandomSource newLegacyInstance(long seed) {
                // Luminol start - Add secure seed V2 with Blake3
                if (me.earthme.luminol.config.modules.function.SecureSeedConfig.enabled && me.earthme.luminol.config.modules.function.SecureSeedConfig.version == 2) {
                    long climateSeed = su.plo.matter.HashingV2.getTerrainSeed(secureWorldSeed, su.plo.matter.HashingV2.TerrainType.CLIMATE);
                    return new su.plo.matter.WorldgenCryptoRandom(0, 0, su.plo.matter.Globals.Salt.UNDEFINED, climateSeed + seed);
                }
                // Luminol end
                return new LegacyRandomSource(levelSeed + seed);
            }

            @Override
            public DensityFunction.NoiseHolder visitNoise(DensityFunction.NoiseHolder noiseHolder) {
                Holder<NormalNoise.NoiseParameters> holder = noiseHolder.noiseData();
                if (useLegacyRandomSource) {
                    if (holder.is(Noises.TEMPERATURE)) {
                        NormalNoise normalNoise = NormalNoise.createLegacyNetherBiome(this.newLegacyInstance(0L), new NormalNoise.NoiseParameters(-7, 1.0, 1.0));
                        return new DensityFunction.NoiseHolder(holder, normalNoise);
                    }

                    if (holder.is(Noises.VEGETATION)) {
                        NormalNoise normalNoise = NormalNoise.createLegacyNetherBiome(this.newLegacyInstance(1L), new NormalNoise.NoiseParameters(-7, 1.0, 1.0));
                        return new DensityFunction.NoiseHolder(holder, normalNoise);
                    }

                    if (holder.is(Noises.SHIFT)) {
                        NormalNoise normalNoise = NormalNoise.create(
                            RandomState.this.random.fromHashOf(Noises.SHIFT.identifier()), new NormalNoise.NoiseParameters(0, 0.0)
                        );
                        return new DensityFunction.NoiseHolder(holder, normalNoise);
                    }
                }

                NormalNoise normalNoise = RandomState.this.getOrCreateNoise(holder.unwrapKey().orElseThrow());
                return new DensityFunction.NoiseHolder(holder, normalNoise);
            }

            private DensityFunction wrapNew(DensityFunction densityFunction) {
                if (densityFunction instanceof BlendedNoise blendedNoise) {
                    RandomSource randomSource = useLegacyRandomSource
                        ? this.newLegacyInstance(0L)
                        : RandomState.this.random.fromHashOf(Identifier.withDefaultNamespace("terrain"));
                    return blendedNoise.withNewRandom(randomSource);
                } else {
                    return (DensityFunction)(densityFunction instanceof DensityFunctions.EndIslandDensityFunction
                        //? new DensityFunctions.EndIslandDensityFunction(levelSeed) // Luminol - Add secure seed V2 with Blake3
                        // Luminol start - Add secure seed V2 with Blake3
                        ? new DensityFunctions.EndIslandDensityFunction((me.earthme.luminol.config.modules.function.SecureSeedConfig.enabled && me.earthme.luminol.config.modules.function.SecureSeedConfig.version == 2)
                            ? su.plo.matter.HashingV2.getTerrainSeed(secureWorldSeed, su.plo.matter.HashingV2.TerrainType.SURFACE)
                            : levelSeed)
                       // Luminol end
                        : densityFunction);
                }
            }

            @Override
            public DensityFunction apply(DensityFunction densityFunction) {
                return this.wrapped.computeIfAbsent(densityFunction, this::wrapNew);
            }
        }

        this.router = settings.noiseRouter().mapAll(new NoiseWiringHelper());
        DensityFunction.Visitor visitor = new DensityFunction.Visitor() {
            private final Map<DensityFunction, DensityFunction> wrapped = new HashMap<>();

            private DensityFunction wrapNew(DensityFunction densityFunction) {
                if (densityFunction instanceof DensityFunctions.HolderHolder holderHolder) {
                    return holderHolder.function().value();
                } else {
                    return densityFunction instanceof DensityFunctions.Marker marker ? marker.wrapped() : densityFunction;
                }
            }

            @Override
            public DensityFunction apply(DensityFunction densityFunction) {
                return this.wrapped.computeIfAbsent(densityFunction, this::wrapNew);
            }
        };
        this.sampler = new Climate.Sampler(
            this.router.temperature().mapAll(visitor),
            this.router.vegetation().mapAll(visitor),
            this.router.continents().mapAll(visitor),
            this.router.erosion().mapAll(visitor),
            this.router.depth().mapAll(visitor),
            this.router.ridges().mapAll(visitor),
            settings.spawnTarget()
        );
    }

    public NormalNoise getOrCreateNoise(ResourceKey<NormalNoise.NoiseParameters> resourceKey) {
        return this.noiseIntances.computeIfAbsent(resourceKey, resourceKey1 -> Noises.instantiate(this.noises, this.random, resourceKey));
    }

    public PositionalRandomFactory getOrCreateRandomFactory(Identifier location) {
        return this.positionalRandoms.computeIfAbsent(location, identifier -> this.random.fromHashOf(location).forkPositional());
    }

    public NoiseRouter router() {
        return this.router;
    }

    public Climate.Sampler sampler() {
        return this.sampler;
    }

    public SurfaceSystem surfaceSystem() {
        return this.surfaceSystem;
    }

    public PositionalRandomFactory aquiferRandom() {
        return this.aquiferRandom;
    }

    public PositionalRandomFactory oreRandom() {
        return this.oreRandom;
    }
}
