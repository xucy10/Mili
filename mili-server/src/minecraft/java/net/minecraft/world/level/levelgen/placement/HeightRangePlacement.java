package net.minecraft.world.level.levelgen.placement;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.heightproviders.TrapezoidHeight;
import net.minecraft.world.level.levelgen.heightproviders.UniformHeight;

public class HeightRangePlacement extends PlacementModifier {
    public static final MapCodec<HeightRangePlacement> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(HeightProvider.CODEC.fieldOf("height").forGetter(placement -> placement.height)).apply(instance, HeightRangePlacement::new)
    );
    private final HeightProvider height;

    private HeightRangePlacement(HeightProvider height) {
        this.height = height;
    }

    public static HeightRangePlacement of(HeightProvider height) {
        return new HeightRangePlacement(height);
    }

    public static HeightRangePlacement uniform(VerticalAnchor minInclusive, VerticalAnchor maxInclusive) {
        return of(UniformHeight.of(minInclusive, maxInclusive));
    }

    public static HeightRangePlacement triangle(VerticalAnchor minInclusive, VerticalAnchor maxInclusive) {
        return of(TrapezoidHeight.of(minInclusive, maxInclusive));
    }

    @Override
    public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos pos) {
        return Stream.of(pos.atY(this.height.sample(random, context)));
    }

    @Override
    public PlacementModifierType<?> type() {
        return PlacementModifierType.HEIGHT_RANGE;
    }
}
