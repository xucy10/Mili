package net.minecraft.world.level.levelgen.placement;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;

public class FixedPlacement extends PlacementModifier {
    public static final MapCodec<FixedPlacement> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(BlockPos.CODEC.listOf().fieldOf("positions").forGetter(placement -> placement.positions))
            .apply(instance, FixedPlacement::new)
    );
    private final List<BlockPos> positions;

    public static FixedPlacement of(BlockPos... positions) {
        return new FixedPlacement(List.of(positions));
    }

    private FixedPlacement(List<BlockPos> positions) {
        this.positions = positions;
    }

    @Override
    public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos pos) {
        int sectionPosX = SectionPos.blockToSectionCoord(pos.getX());
        int sectionPosZ = SectionPos.blockToSectionCoord(pos.getZ());
        boolean flag = false;

        for (BlockPos blockPos : this.positions) {
            if (isSameChunk(sectionPosX, sectionPosZ, blockPos)) {
                flag = true;
                break;
            }
        }

        return !flag ? Stream.empty() : this.positions.stream().filter(pos1 -> isSameChunk(sectionPosX, sectionPosZ, pos1));
    }

    private static boolean isSameChunk(int x, int z, BlockPos pos) {
        return x == SectionPos.blockToSectionCoord(pos.getX()) && z == SectionPos.blockToSectionCoord(pos.getZ());
    }

    @Override
    public PlacementModifierType<?> type() {
        return PlacementModifierType.FIXED_PLACEMENT;
    }
}
