package net.minecraft.world.level.levelgen.placement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;

public class SurfaceRelativeThresholdFilter extends PlacementFilter {
    public static final MapCodec<SurfaceRelativeThresholdFilter> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                Heightmap.Types.CODEC.fieldOf("heightmap").forGetter(filter -> filter.heightmap),
                Codec.INT.optionalFieldOf("min_inclusive", Integer.MIN_VALUE).forGetter(filter -> filter.minInclusive),
                Codec.INT.optionalFieldOf("max_inclusive", Integer.MAX_VALUE).forGetter(filter -> filter.maxInclusive)
            )
            .apply(instance, SurfaceRelativeThresholdFilter::new)
    );
    private final Heightmap.Types heightmap;
    private final int minInclusive;
    private final int maxInclusive;

    private SurfaceRelativeThresholdFilter(Heightmap.Types heightmap, int minInclusive, int maxInclusive) {
        this.heightmap = heightmap;
        this.minInclusive = minInclusive;
        this.maxInclusive = maxInclusive;
    }

    public static SurfaceRelativeThresholdFilter of(Heightmap.Types heightmap, int minInclusive, int maxInclusive) {
        return new SurfaceRelativeThresholdFilter(heightmap, minInclusive, maxInclusive);
    }

    @Override
    protected boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos pos) {
        long l = context.getHeight(this.heightmap, pos.getX(), pos.getZ());
        long l1 = l + this.minInclusive;
        long l2 = l + this.maxInclusive;
        return l1 <= pos.getY() && pos.getY() <= l2;
    }

    @Override
    public PlacementModifierType<?> type() {
        return PlacementModifierType.SURFACE_RELATIVE_THRESHOLD_FILTER;
    }
}
