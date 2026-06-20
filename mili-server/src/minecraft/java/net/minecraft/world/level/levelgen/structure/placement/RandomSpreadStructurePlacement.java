package net.minecraft.world.level.levelgen.structure.placement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;

public class RandomSpreadStructurePlacement extends StructurePlacement {
    public static final MapCodec<RandomSpreadStructurePlacement> CODEC = RecordCodecBuilder.<RandomSpreadStructurePlacement>mapCodec(
            instance -> placementCodec(instance)
                .and(
                    instance.group(
                        Codec.intRange(0, 4096).fieldOf("spacing").forGetter(RandomSpreadStructurePlacement::spacing),
                        Codec.intRange(0, 4096).fieldOf("separation").forGetter(RandomSpreadStructurePlacement::separation),
                        RandomSpreadType.CODEC.optionalFieldOf("spread_type", RandomSpreadType.LINEAR).forGetter(RandomSpreadStructurePlacement::spreadType)
                    )
                )
                .apply(instance, RandomSpreadStructurePlacement::new)
        )
        .validate(RandomSpreadStructurePlacement::validate);
    private final int spacing;
    private final int separation;
    private final RandomSpreadType spreadType;

    private static DataResult<RandomSpreadStructurePlacement> validate(RandomSpreadStructurePlacement placement) {
        return placement.spacing <= placement.separation ? DataResult.error(() -> "Spacing has to be larger than separation") : DataResult.success(placement);
    }

    public RandomSpreadStructurePlacement(
        Vec3i locateOffset,
        StructurePlacement.FrequencyReductionMethod frequencyReductionMethod,
        float frequency,
        int salt,
        Optional<StructurePlacement.ExclusionZone> exclusionZone,
        int spacing,
        int separation,
        RandomSpreadType spreadType
    ) {
        super(locateOffset, frequencyReductionMethod, frequency, salt, exclusionZone);
        this.spacing = spacing;
        this.separation = separation;
        this.spreadType = spreadType;
    }

    public RandomSpreadStructurePlacement(int spacing, int separation, RandomSpreadType spreadType, int salt) {
        this(Vec3i.ZERO, StructurePlacement.FrequencyReductionMethod.DEFAULT, 1.0F, salt, Optional.empty(), spacing, separation, spreadType);
    }

    public int spacing() {
        return this.spacing;
    }

    public int separation() {
        return this.separation;
    }

    public RandomSpreadType spreadType() {
        return this.spreadType;
    }

    public ChunkPos getPotentialStructureChunk(long seed, int regionX, int regionZ) {
        int i = Math.floorDiv(regionX, this.spacing);
        int i1 = Math.floorDiv(regionZ, this.spacing);
        // Leaf start - Matter - Secure Seed
        WorldgenRandom worldgenRandom;
        if (me.earthme.luminol.config.modules.function.SecureSeedConfig.enabled) {
            worldgenRandom = new su.plo.matter.WorldgenCryptoRandom(i, i1, su.plo.matter.Globals.Salt.POTENTIONAL_FEATURE, this.salt);
        } else {
            worldgenRandom = new WorldgenRandom(new LegacyRandomSource(0L));
            worldgenRandom.setLargeFeatureWithSalt(seed, i, i1, this.salt());
        }
        // Leaf end - Matter - Secure Seed
        int i2 = this.spacing - this.separation;
        int i3 = this.spreadType.evaluate(worldgenRandom, i2);
        int i4 = this.spreadType.evaluate(worldgenRandom, i2);
        return new ChunkPos(i * this.spacing + i3, i1 * this.spacing + i4);
    }

    @Override
    protected boolean isPlacementChunk(ChunkGeneratorStructureState structureState, int x, int z) {
        ChunkPos potentialStructureChunk = this.getPotentialStructureChunk(structureState.getLevelSeed(), x, z);
        return potentialStructureChunk.x == x && potentialStructureChunk.z == z;
    }

    @Override
    public StructurePlacementType<?> type() {
        return StructurePlacementType.RANDOM_SPREAD;
    }
}
