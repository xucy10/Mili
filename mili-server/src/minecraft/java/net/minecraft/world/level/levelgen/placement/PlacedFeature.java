package net.minecraft.world.level.levelgen.placement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.FeatureCountTracker;
import org.apache.commons.lang3.mutable.MutableBoolean;

public record PlacedFeature(Holder<ConfiguredFeature<?, ?>> feature, List<PlacementModifier> placement) {
    public static final Codec<PlacedFeature> DIRECT_CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                ConfiguredFeature.CODEC.fieldOf("feature").forGetter(feature -> feature.feature),
                PlacementModifier.CODEC.listOf().fieldOf("placement").forGetter(feature -> feature.placement)
            )
            .apply(instance, PlacedFeature::new)
    );
    public static final Codec<Holder<PlacedFeature>> CODEC = RegistryFileCodec.create(Registries.PLACED_FEATURE, DIRECT_CODEC);
    public static final Codec<HolderSet<PlacedFeature>> LIST_CODEC = RegistryCodecs.homogeneousList(Registries.PLACED_FEATURE, DIRECT_CODEC);
    public static final Codec<List<HolderSet<PlacedFeature>>> LIST_OF_LISTS_CODEC = RegistryCodecs.homogeneousList(
            Registries.PLACED_FEATURE, DIRECT_CODEC, true
        )
        .listOf();

    public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos pos) {
        return this.placeWithContext(new PlacementContext(level, generator, Optional.empty()), random, pos);
    }

    public boolean placeWithBiomeCheck(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos pos) {
        return this.placeWithContext(new PlacementContext(level, generator, Optional.of(this)), random, pos);
    }

    private boolean placeWithContext(PlacementContext context, RandomSource source, BlockPos pos) {
        Stream<BlockPos> stream = Stream.of(pos);

        for (PlacementModifier placementModifier : this.placement) {
            stream = stream.flatMap(blockPos -> placementModifier.getPositions(context, source, blockPos));
        }

        ConfiguredFeature<?, ?> configuredFeature = this.feature.value();
        MutableBoolean mutableBoolean = new MutableBoolean();
        stream.forEach(blockPos -> {
            if (configuredFeature.place(context.getLevel(), context.generator(), source, blockPos)) {
                mutableBoolean.setTrue();
                if (SharedConstants.DEBUG_FEATURE_COUNT) {
                    FeatureCountTracker.featurePlaced(context.getLevel().getLevel(), configuredFeature, context.topFeature());
                }
            }
        });
        return mutableBoolean.isTrue();
    }

    public Stream<ConfiguredFeature<?, ?>> getFeatures() {
        return this.feature.value().getFeatures();
    }

    @Override
    public String toString() {
        return "Placed " + this.feature;
    }
}
