package net.minecraft.world.level.levelgen.structure.placement;

import com.mojang.datafixers.Products.P5;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import com.mojang.serialization.codecs.RecordCodecBuilder.Mu;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.StructureSet;

public abstract class StructurePlacement {
    public static final Codec<StructurePlacement> CODEC = BuiltInRegistries.STRUCTURE_PLACEMENT
        .byNameCodec()
        .dispatch(StructurePlacement::type, StructurePlacementType::codec);
    private static final int HIGHLY_ARBITRARY_RANDOM_SALT = 10387320;
    public final Vec3i locateOffset;
    public final StructurePlacement.FrequencyReductionMethod frequencyReductionMethod;
    public final float frequency;
    public final int salt;
    public final Optional<StructurePlacement.ExclusionZone> exclusionZone;

    protected static <S extends StructurePlacement> P5<Mu<S>, Vec3i, StructurePlacement.FrequencyReductionMethod, Float, Integer, Optional<StructurePlacement.ExclusionZone>> placementCodec(
        Instance<S> instance
    ) {
        return instance.group(
            Vec3i.offsetCodec(16).optionalFieldOf("locate_offset", Vec3i.ZERO).forGetter(StructurePlacement::locateOffset),
            StructurePlacement.FrequencyReductionMethod.CODEC
                .optionalFieldOf("frequency_reduction_method", StructurePlacement.FrequencyReductionMethod.DEFAULT)
                .forGetter(StructurePlacement::frequencyReductionMethod),
            Codec.floatRange(0.0F, 1.0F).optionalFieldOf("frequency", 1.0F).forGetter(StructurePlacement::frequency),
            ExtraCodecs.NON_NEGATIVE_INT.fieldOf("salt").forGetter(StructurePlacement::salt),
            StructurePlacement.ExclusionZone.CODEC.optionalFieldOf("exclusion_zone").forGetter(StructurePlacement::exclusionZone)
        );
    }

    protected StructurePlacement(
        Vec3i locateOffset,
        StructurePlacement.FrequencyReductionMethod frequencyReductionMethod,
        float frequency,
        int salt,
        Optional<StructurePlacement.ExclusionZone> exclusionZone
    ) {
        this.locateOffset = locateOffset;
        this.frequencyReductionMethod = frequencyReductionMethod;
        this.frequency = frequency;
        this.salt = salt;
        this.exclusionZone = exclusionZone;
    }

    protected Vec3i locateOffset() {
        return this.locateOffset;
    }

    protected StructurePlacement.FrequencyReductionMethod frequencyReductionMethod() {
        return this.frequencyReductionMethod;
    }

    protected float frequency() {
        return this.frequency;
    }

    protected int salt() {
        return this.salt;
    }

    protected Optional<StructurePlacement.ExclusionZone> exclusionZone() {
        return this.exclusionZone;
    }

    @Deprecated @io.papermc.paper.annotation.DoNotUse // Paper - Add missing structure set seed configs
    public boolean isStructureChunk(ChunkGeneratorStructureState structureState, int x, int z) {
        // Paper start - Add missing structure set seed configs
        return this.isStructureChunk(structureState, x, z, null);
    }

    public boolean isStructureChunk(ChunkGeneratorStructureState structureState, int x, int z, @javax.annotation.Nullable net.minecraft.resources.ResourceKey<StructureSet> structureSetKey) {
        Integer saltOverride = null;
        if (structureSetKey != null) {
            if (structureSetKey == net.minecraft.world.level.levelgen.structure.BuiltinStructureSets.MINESHAFTS) {
                saltOverride = structureState.conf.mineshaftSeed;
            } else if (structureSetKey == net.minecraft.world.level.levelgen.structure.BuiltinStructureSets.BURIED_TREASURES) {
                saltOverride = structureState.conf.buriedTreasureSeed;
            }
        }
        // Paper end - Add missing structure set seed configs
        return this.isPlacementChunk(structureState, x, z)
            && this.applyAdditionalChunkRestrictions(x, z, structureState.getLevelSeed(), saltOverride) // Paper - Add missing structure set seed configs
            && this.applyInteractionsWithOtherStructures(structureState, x, z);
    }

    // Paper start - Add missing structure set seed configs
    public boolean applyAdditionalChunkRestrictions(int regionX, int regionZ, long levelSeed, @javax.annotation.Nullable Integer saltOverride) {
        return !(this.frequency < 1.0F) || this.frequencyReductionMethod.shouldGenerate(levelSeed, this.salt, regionX, regionZ, this.frequency, saltOverride);
        // Paper end - Add missing structure set seed configs
    }

    public boolean applyInteractionsWithOtherStructures(ChunkGeneratorStructureState structureState, int x, int z) {
        return !this.exclusionZone.isPresent() || !this.exclusionZone.get().isPlacementForbidden(structureState, x, z);
    }

    protected abstract boolean isPlacementChunk(ChunkGeneratorStructureState structureState, int x, int z);

    public BlockPos getLocatePos(ChunkPos chunkPos) {
        return new BlockPos(chunkPos.getMinBlockX(), 0, chunkPos.getMinBlockZ()).offset(this.locateOffset());
    }

    public abstract StructurePlacementType<?> type();

    private static boolean probabilityReducer(long levelSeed, int regionX, int regionZ, int salt, float frequency, @javax.annotation.Nullable Integer saltOverride) { // Paper - Add missing structure set seed configs; ignore here
        // Leaf start - Matter - Secure Seed
        WorldgenRandom worldgenRandom;
        if (me.earthme.luminol.config.modules.function.SecureSeedConfig.enabled) {
            worldgenRandom = new su.plo.matter.WorldgenCryptoRandom(regionX, regionZ, su.plo.matter.Globals.Salt.UNDEFINED, salt);
        } else {
            worldgenRandom = new WorldgenRandom(new LegacyRandomSource(0L));
            worldgenRandom.setLargeFeatureWithSalt(levelSeed, salt, regionX, regionZ);
        }
        // Leaf end - Matter - Secure Seed

        return worldgenRandom.nextFloat() < frequency;
    }

    private static boolean legacyProbabilityReducerWithDouble(long baseSeed, int salt, int chunkX, int chunkZ, float frequency, @javax.annotation.Nullable Integer saltOverride) { // Paper - Add missing structure set seed configs
        WorldgenRandom worldgenRandom = new WorldgenRandom(new LegacyRandomSource(0L));
        if (saltOverride == null) { // Paper - Add missing structure set seed configs
        worldgenRandom.setLargeFeatureSeed(baseSeed, chunkX, chunkZ);
            // Paper start - Add missing structure set seed configs
        } else {
            worldgenRandom.setLargeFeatureWithSalt(baseSeed, chunkX, chunkZ, saltOverride);
        }
        // Paper end - Add missing structure set seed configs
        return worldgenRandom.nextDouble() < frequency;
    }

    private static boolean legacyArbitrarySaltProbabilityReducer(long levelSeed, int salt, int regionX, int regionZ, float frequency, @javax.annotation.Nullable Integer saltOverride) { // Paper - Add missing structure set seed configs
        WorldgenRandom worldgenRandom = new WorldgenRandom(new LegacyRandomSource(0L));
        worldgenRandom.setLargeFeatureWithSalt(levelSeed, regionX, regionZ, saltOverride != null ? saltOverride : HIGHLY_ARBITRARY_RANDOM_SALT); // Paper - Add missing structure set seed configs
        return worldgenRandom.nextFloat() < frequency;
    }

    private static boolean legacyPillagerOutpostReducer(long levelSeed, int salt, int regionX, int regionZ, float frequency, @javax.annotation.Nullable Integer saltOverride) { // Paper - Add missing structure set seed configs; ignore here
        int i = regionX >> 4;
        int i1 = regionZ >> 4;
        WorldgenRandom worldgenRandom = new WorldgenRandom(new LegacyRandomSource(0L));
        worldgenRandom.setSeed(i ^ i1 << 4 ^ levelSeed);
        worldgenRandom.nextInt();
        return worldgenRandom.nextInt((int)(1.0F / frequency)) == 0;
    }

    @Deprecated
    public record ExclusionZone(Holder<StructureSet> otherSet, int chunkCount) {
        public static final Codec<StructurePlacement.ExclusionZone> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    RegistryFileCodec.create(Registries.STRUCTURE_SET, StructureSet.DIRECT_CODEC, false)
                        .fieldOf("other_set")
                        .forGetter(StructurePlacement.ExclusionZone::otherSet),
                    Codec.intRange(1, 16).fieldOf("chunk_count").forGetter(StructurePlacement.ExclusionZone::chunkCount)
                )
                .apply(instance, StructurePlacement.ExclusionZone::new)
        );

        boolean isPlacementForbidden(ChunkGeneratorStructureState structureState, int x, int z) {
            return structureState.hasStructureChunkInRange(this.otherSet, x, z, this.chunkCount);
        }
    }

    @FunctionalInterface
    public interface FrequencyReducer {
        boolean shouldGenerate(long levelSeed, int salt, int regionX, int regionZ, float frequency, @javax.annotation.Nullable Integer saltOverride); // Paper - Add missing structure set seed configs
    }

    public static enum FrequencyReductionMethod implements StringRepresentable {
        DEFAULT("default", StructurePlacement::probabilityReducer),
        LEGACY_TYPE_1("legacy_type_1", StructurePlacement::legacyPillagerOutpostReducer),
        LEGACY_TYPE_2("legacy_type_2", StructurePlacement::legacyArbitrarySaltProbabilityReducer),
        LEGACY_TYPE_3("legacy_type_3", StructurePlacement::legacyProbabilityReducerWithDouble);

        public static final Codec<StructurePlacement.FrequencyReductionMethod> CODEC = StringRepresentable.fromEnum(
            StructurePlacement.FrequencyReductionMethod::values
        );
        private final String name;
        private final StructurePlacement.FrequencyReducer reducer;

        private FrequencyReductionMethod(final String name, final StructurePlacement.FrequencyReducer reducer) {
            this.name = name;
            this.reducer = reducer;
        }

        public boolean shouldGenerate(long levelSeed, int salt, int regionX, int regionZ, float frequency, @javax.annotation.Nullable Integer saltOverride) { // Paper - Add missing structure set seed configs
            return this.reducer.shouldGenerate(levelSeed, salt, regionX, regionZ, frequency, saltOverride); // Paper - Add missing structure set seed configs
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }
}
