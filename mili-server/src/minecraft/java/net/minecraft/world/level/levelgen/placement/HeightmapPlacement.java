package net.minecraft.world.level.levelgen.placement;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;

public class HeightmapPlacement extends PlacementModifier {
    public static final MapCodec<HeightmapPlacement> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(Heightmap.Types.CODEC.fieldOf("heightmap").forGetter(placement -> placement.heightmap))
            .apply(instance, HeightmapPlacement::new)
    );
    private final Heightmap.Types heightmap;

    private HeightmapPlacement(Heightmap.Types heightmap) {
        this.heightmap = heightmap;
    }

    public static HeightmapPlacement onHeightmap(Heightmap.Types heightmap) {
        return new HeightmapPlacement(heightmap);
    }

    @Override
    public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos pos) {
        int x = pos.getX();
        int z = pos.getZ();
        int height = context.getHeight(this.heightmap, x, z);
        return height > context.getMinY() ? Stream.of(new BlockPos(x, height, z)) : Stream.of();
    }

    @Override
    public PlacementModifierType<?> type() {
        return PlacementModifierType.HEIGHTMAP;
    }
}
