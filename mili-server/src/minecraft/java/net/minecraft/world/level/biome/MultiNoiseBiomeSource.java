package net.minecraft.world.level.biome;

import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.level.levelgen.NoiseRouterData;

public class MultiNoiseBiomeSource extends BiomeSource {
    private static final MapCodec<Holder<Biome>> ENTRY_CODEC = Biome.CODEC.fieldOf("biome");
    public static final MapCodec<Climate.ParameterList<Holder<Biome>>> DIRECT_CODEC = Climate.ParameterList.codec(ENTRY_CODEC).fieldOf("biomes");
    private static final MapCodec<Holder<MultiNoiseBiomeSourceParameterList>> PRESET_CODEC = MultiNoiseBiomeSourceParameterList.CODEC
        .fieldOf("preset")
        .withLifecycle(Lifecycle.stable());
    public static final MapCodec<MultiNoiseBiomeSource> CODEC = Codec.mapEither(DIRECT_CODEC, PRESET_CODEC)
        .xmap(MultiNoiseBiomeSource::new, biomeSource -> biomeSource.parameters);
    private final Either<Climate.ParameterList<Holder<Biome>>, Holder<MultiNoiseBiomeSourceParameterList>> parameters;

    private MultiNoiseBiomeSource(Either<Climate.ParameterList<Holder<Biome>>, Holder<MultiNoiseBiomeSourceParameterList>> parameters) {
        this.parameters = parameters;
    }

    public static MultiNoiseBiomeSource createFromList(Climate.ParameterList<Holder<Biome>> parameters) {
        return new MultiNoiseBiomeSource(Either.left(parameters));
    }

    public static MultiNoiseBiomeSource createFromPreset(Holder<MultiNoiseBiomeSourceParameterList> parameters) {
        return new MultiNoiseBiomeSource(Either.right(parameters));
    }

    private Climate.ParameterList<Holder<Biome>> parameters() {
        return this.parameters.map(parameters -> parameters, parameterListHolder -> parameterListHolder.value().parameters());
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return this.parameters().values().stream().map(Pair::getSecond);
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    public boolean stable(ResourceKey<MultiNoiseBiomeSourceParameterList> resourceKey) {
        Optional<Holder<MultiNoiseBiomeSourceParameterList>> optional = this.parameters.right();
        return optional.isPresent() && optional.get().is(resourceKey);
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        return this.getNoiseBiome(sampler.sample(x, y, z));
    }

    @VisibleForDebug
    public Holder<Biome> getNoiseBiome(Climate.TargetPoint targetPoint) {
        return this.parameters().findValue(targetPoint);
    }

    @Override
    public void addDebugInfo(List<String> info, BlockPos pos, Climate.Sampler sampler) {
        int quartPosX = QuartPos.fromBlock(pos.getX());
        int quartPosY = QuartPos.fromBlock(pos.getY());
        int quartPosZ = QuartPos.fromBlock(pos.getZ());
        Climate.TargetPoint targetPoint = sampler.sample(quartPosX, quartPosY, quartPosZ);
        float f = Climate.unquantizeCoord(targetPoint.continentalness());
        float f1 = Climate.unquantizeCoord(targetPoint.erosion());
        float f2 = Climate.unquantizeCoord(targetPoint.temperature());
        float f3 = Climate.unquantizeCoord(targetPoint.humidity());
        float f4 = Climate.unquantizeCoord(targetPoint.weirdness());
        double d = NoiseRouterData.peaksAndValleys(f4);
        OverworldBiomeBuilder overworldBiomeBuilder = new OverworldBiomeBuilder();
        info.add(
            "Biome builder PV: "
                + OverworldBiomeBuilder.getDebugStringForPeaksAndValleys(d)
                + " C: "
                + overworldBiomeBuilder.getDebugStringForContinentalness(f)
                + " E: "
                + overworldBiomeBuilder.getDebugStringForErosion(f1)
                + " T: "
                + overworldBiomeBuilder.getDebugStringForTemperature(f2)
                + " H: "
                + overworldBiomeBuilder.getDebugStringForHumidity(f3)
        );
    }
}
