package net.minecraft.world.level.chunk;

import com.google.common.base.Stopwatch;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class ChunkGeneratorStructureState {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final RandomState randomState;
    private final BiomeSource biomeSource;
    private final long levelSeed;
    private final long concentricRingsSeed;
    private final Map<Structure, List<StructurePlacement>> placementsForStructure = new Object2ObjectOpenHashMap<>();
    private final Map<ConcentricRingsStructurePlacement, CompletableFuture<List<ChunkPos>>> ringPositions = new Object2ObjectArrayMap<>();
    private boolean hasGeneratedPositions;
    private final List<Holder<StructureSet>> possibleStructureSets;
    public final org.spigotmc.SpigotWorldConfig conf; // Paper - Add missing structure set seed configs

    public static ChunkGeneratorStructureState createForFlat(
        RandomState randomState, long levelSeed, BiomeSource biomeSource, Stream<Holder<StructureSet>> structureSets, org.spigotmc.SpigotWorldConfig conf // Spigot
    ) {
        List<Holder<StructureSet>> list = structureSets.filter(structureSet -> hasBiomesForStructureSet(structureSet.value(), biomeSource)).toList();
        return new ChunkGeneratorStructureState(randomState, biomeSource, levelSeed, 0L, ChunkGeneratorStructureState.injectSpigot(list, conf), conf); // Spigot
    }

    public static ChunkGeneratorStructureState createForNormal(
        RandomState randomState, long seed, BiomeSource biomeSource, HolderLookup<StructureSet> structureSetLookup, org.spigotmc.SpigotWorldConfig conf // Spigot
    ) {
        List<Holder<StructureSet>> list = structureSetLookup.listElements()
            .filter(structureSet -> hasBiomesForStructureSet(structureSet.value(), biomeSource))
            .collect(Collectors.toUnmodifiableList());
        return new ChunkGeneratorStructureState(randomState, biomeSource, seed, seed, ChunkGeneratorStructureState.injectSpigot(list, conf), conf); // Spigot
    }
    // Paper start - Add missing structure set seed configs; horrible hack because spigot creates a ton of direct Holders which lose track of the identifying key
    public static final class KeyedRandomSpreadStructurePlacement extends net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement {
        public final net.minecraft.resources.ResourceKey<StructureSet> key;
        public KeyedRandomSpreadStructurePlacement(net.minecraft.resources.ResourceKey<StructureSet> key, net.minecraft.core.Vec3i locateOffset, FrequencyReductionMethod frequencyReductionMethod, float frequency, int salt, java.util.Optional<StructurePlacement.ExclusionZone> exclusionZone, int spacing, int separation, net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType spreadType) {
            super(locateOffset, frequencyReductionMethod, frequency, salt, exclusionZone, spacing, separation, spreadType);
            this.key = key;
        }
    }
    // Paper end - Add missing structure set seed configs

    // Spigot start
    private static List<Holder<StructureSet>> injectSpigot(List<Holder<StructureSet>> list, org.spigotmc.SpigotWorldConfig conf) {
        return list.stream().map((holder) -> {
            StructureSet structureset = holder.value();
            final Holder<StructureSet> newHolder; // Paper - Add missing structure set seed configs
            if (structureset.placement() instanceof net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement randomConfig && holder.unwrapKey().orElseThrow().identifier().getNamespace().equals(net.minecraft.resources.Identifier.DEFAULT_NAMESPACE)) { // Paper - Add missing structure set seed configs; check namespace cause datapacks could add structure sets with the same path
                String name = holder.unwrapKey().orElseThrow().identifier().getPath();
                int seed = randomConfig.salt;

                switch (name) {
                    case "desert_pyramids":
                        seed = conf.desertSeed;
                        break;
                    case "end_cities":
                        seed = conf.endCitySeed;
                        break;
                    case "nether_complexes":
                        seed = conf.netherSeed;
                        break;
                    case "igloos":
                        seed = conf.iglooSeed;
                        break;
                    case "jungle_temples":
                        seed = conf.jungleSeed;
                        break;
                    case "woodland_mansions":
                        seed = conf.mansionSeed;
                        break;
                    case "ocean_monuments":
                        seed = conf.monumentSeed;
                        break;
                    case "nether_fossils":
                        seed = conf.fossilSeed;
                        break;
                    case "ocean_ruins":
                        seed = conf.oceanSeed;
                        break;
                    case "pillager_outposts":
                        seed = conf.outpostSeed;
                        break;
                    case "ruined_portals":
                        seed = conf.portalSeed;
                        break;
                    case "shipwrecks":
                        seed = conf.shipwreckSeed;
                        break;
                    case "swamp_huts":
                        seed = conf.swampSeed;
                        break;
                    case "villages":
                        seed = conf.villageSeed;
                        break;
                    // Paper start - Add missing structure set seed configs
                    case "ancient_cities":
                        seed = conf.ancientCitySeed;
                        break;
                    case "trail_ruins":
                        seed = conf.trailRuinsSeed;
                        break;
                    case "trial_chambers":
                        seed = conf.trialChambersSeed;
                        break;
                    // Paper end - Add missing structure set seed configs
                }

            // Paper start - Add missing structure set seed configs
                structureset = new StructureSet(structureset.structures(), new KeyedRandomSpreadStructurePlacement(holder.unwrapKey().orElseThrow(), randomConfig.locateOffset, randomConfig.frequencyReductionMethod, randomConfig.frequency, seed, randomConfig.exclusionZone, randomConfig.spacing(), randomConfig.separation(), randomConfig.spreadType()));
                newHolder = Holder.direct(structureset); // I really wish we didn't have to do this here
            } else {
                newHolder = holder;
            }
            return newHolder;
            // Paper end - Add missing structure set seed configs
        }).collect(Collectors.toUnmodifiableList());
    }
    // Spigot end

    private static boolean hasBiomesForStructureSet(StructureSet structureSet, BiomeSource biomeSource) {
        Stream<Holder<Biome>> stream = structureSet.structures().stream().flatMap(structureEntry -> {
            Structure structure = structureEntry.structure().value();
            return structure.biomes().stream();
        });
        return stream.anyMatch(biomeSource.possibleBiomes()::contains);
    }

    private ChunkGeneratorStructureState(
        RandomState randomState, BiomeSource biomeSource, long levelSeed, long concentricRingsSeed, List<Holder<StructureSet>> possibleStructureSets, org.spigotmc.SpigotWorldConfig conf // Paper - Add missing structure set seed configs
    ) {
        this.randomState = randomState;
        this.levelSeed = levelSeed;
        this.biomeSource = biomeSource;
        this.concentricRingsSeed = concentricRingsSeed;
        this.possibleStructureSets = possibleStructureSets;
        this.conf = conf; // Paper - Add missing structure set seed configs
    }

    public List<Holder<StructureSet>> possibleStructureSets() {
        return this.possibleStructureSets;
    }

    private void generatePositions() {
        Set<Holder<Biome>> set = this.biomeSource.possibleBiomes();
        this.possibleStructureSets()
            .forEach(
                structureSetHolder -> {
                    StructureSet structureSet = structureSetHolder.value();
                    boolean flag = false;

                    for (StructureSet.StructureSelectionEntry structureSelectionEntry : structureSet.structures()) {
                        Structure structure = structureSelectionEntry.structure().value();
                        if (structure.biomes().stream().anyMatch(set::contains)) {
                            this.placementsForStructure.computeIfAbsent(structure, key -> new ArrayList<>()).add(structureSet.placement());
                            flag = true;
                        }
                    }

                    if (flag && structureSet.placement() instanceof ConcentricRingsStructurePlacement concentricRingsStructurePlacement) {
                        this.ringPositions
                            .put(
                                concentricRingsStructurePlacement,
                                this.generateRingPositions((Holder<StructureSet>)structureSetHolder, concentricRingsStructurePlacement)
                            );
                    }
                }
            );
    }

    private CompletableFuture<List<ChunkPos>> generateRingPositions(Holder<StructureSet> structureSet, ConcentricRingsStructurePlacement placement) {
        if (placement.count() == 0) {
            return CompletableFuture.completedFuture(List.of());
        } else {
            Stopwatch stopwatch = Stopwatch.createStarted(Util.TICKER);
            int distance = placement.distance();
            int count = placement.count();
            List<CompletableFuture<ChunkPos>> list = new ArrayList<>(count);
            int spread = placement.spread();
            HolderSet<Biome> holderSet = placement.preferredBiomes();
            // Leaf start - Matter - Secure Seed
            RandomSource randomSource = me.earthme.luminol.config.modules.function.SecureSeedConfig.enabled
                ? new su.plo.matter.WorldgenCryptoRandom(0, 0, su.plo.matter.Globals.Salt.STRONGHOLDS, 0)
                : RandomSource.create();
            // Leaf end - Matter - Secure Seed
            if (!me.earthme.luminol.config.modules.function.SecureSeedConfig.enabled) {
            // Paper start - Add missing structure set seed configs
            if (this.conf.strongholdSeed != null && structureSet.is(net.minecraft.world.level.levelgen.structure.BuiltinStructureSets.STRONGHOLDS)) {
                randomSource.setSeed(this.conf.strongholdSeed);
            } else {
            // Paper end - Add missing structure set seed configs
            randomSource.setSeed(this.concentricRingsSeed);
            } // Paper - Add missing structure set seed configs
            } // Leaf - Matter - Secure Seed
            double d = randomSource.nextDouble() * Math.PI * 2.0;
            int i = 0;
            int i1 = 0;

            for (int i2 = 0; i2 < count; i2++) {
                double d1 = 4 * distance + distance * i1 * 6 + (randomSource.nextDouble() - 0.5) * (distance * 2.5);
                int i3 = (int)Math.round(Math.cos(d) * d1);
                int i4 = (int)Math.round(Math.sin(d) * d1);
                RandomSource randomSource1 = randomSource.fork();
                list.add(
                    CompletableFuture.supplyAsync(
                        () -> {
                            Pair<BlockPos, Holder<Biome>> pair = this.biomeSource
                                .findBiomeHorizontal(
                                    SectionPos.sectionToBlockCoord(i3, 8),
                                    0,
                                    SectionPos.sectionToBlockCoord(i4, 8),
                                    112,
                                    holderSet::contains,
                                    randomSource1,
                                    this.randomState.sampler()
                                );
                            if (pair != null) {
                                BlockPos blockPos = pair.getFirst();
                                return new ChunkPos(SectionPos.blockToSectionCoord(blockPos.getX()), SectionPos.blockToSectionCoord(blockPos.getZ()));
                            } else {
                                return new ChunkPos(i3, i4);
                            }
                        },
                        Util.backgroundExecutor().forName("structureRings")
                    )
                );
                d += (Math.PI * 2) / spread;
                if (++i == spread) {
                    i1++;
                    i = 0;
                    spread += 2 * spread / (i1 + 1);
                    spread = Math.min(spread, count - i2);
                    d += randomSource.nextDouble() * Math.PI * 2.0;
                }
            }

            return Util.sequence(list).thenApply(completed -> {
                double d2 = stopwatch.stop().elapsed(TimeUnit.MILLISECONDS) / 1000.0;
                LOGGER.debug("Calculation for {} took {}s", structureSet, d2);
                return completed;
            });
        }
    }

    public void ensureStructuresGenerated() {
        if (!this.hasGeneratedPositions) {
            this.generatePositions();
            this.hasGeneratedPositions = true;
        }
    }

    public @Nullable List<ChunkPos> getRingPositionsFor(ConcentricRingsStructurePlacement placement) {
        this.ensureStructuresGenerated();
        CompletableFuture<List<ChunkPos>> completableFuture = this.ringPositions.get(placement);
        return completableFuture != null ? completableFuture.join() : null;
    }

    public List<StructurePlacement> getPlacementsForStructure(Holder<Structure> structure) {
        this.ensureStructuresGenerated();
        return this.placementsForStructure.getOrDefault(structure.value(), List.of());
    }

    public RandomState randomState() {
        return this.randomState;
    }

    public boolean hasStructureChunkInRange(Holder<StructureSet> structureSet, int x, int z, int range) {
        StructurePlacement structurePlacement = structureSet.value().placement();

        for (int i = x - range; i <= x + range; i++) {
            for (int i1 = z - range; i1 <= z + range; i1++) {
                if (structurePlacement.isStructureChunk(this, i, i1, structurePlacement instanceof KeyedRandomSpreadStructurePlacement keyed ? keyed.key : null)) { // Paper - Add missing structure set seed configs
                    return true;
                }
            }
        }

        return false;
    }

    public long getLevelSeed() {
        return this.levelSeed;
    }
}
