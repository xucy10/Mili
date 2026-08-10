package net.minecraft.world.level.levelgen.feature.trunkplacers;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;

public class GiantTrunkPlacer extends TrunkPlacer {
    public static final MapCodec<GiantTrunkPlacer> CODEC = RecordCodecBuilder.mapCodec(
        instance -> trunkPlacerParts(instance).apply(instance, GiantTrunkPlacer::new)
    );

    public GiantTrunkPlacer(int baseHeight, int heightRandA, int heightRandB) {
        super(baseHeight, heightRandA, heightRandB);
    }

    @Override
    protected TrunkPlacerType<?> type() {
        return TrunkPlacerType.GIANT_TRUNK_PLACER;
    }

    @Override
    public List<FoliagePlacer.FoliageAttachment> placeTrunk(
        LevelSimulatedReader level,
        BiConsumer<BlockPos, BlockState> blockSetter,
        RandomSource random,
        int freeTreeHeight,
        BlockPos pos,
        TreeConfiguration config
    ) {
        BlockPos blockPos = pos.below();
        setDirtAt(level, blockSetter, random, blockPos, config);
        setDirtAt(level, blockSetter, random, blockPos.east(), config);
        setDirtAt(level, blockSetter, random, blockPos.south(), config);
        setDirtAt(level, blockSetter, random, blockPos.south().east(), config);
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        for (int i = 0; i < freeTreeHeight; i++) {
            this.placeLogIfFreeWithOffset(level, blockSetter, random, mutableBlockPos, config, pos, 0, i, 0);
            if (i < freeTreeHeight - 1) {
                this.placeLogIfFreeWithOffset(level, blockSetter, random, mutableBlockPos, config, pos, 1, i, 0);
                this.placeLogIfFreeWithOffset(level, blockSetter, random, mutableBlockPos, config, pos, 1, i, 1);
                this.placeLogIfFreeWithOffset(level, blockSetter, random, mutableBlockPos, config, pos, 0, i, 1);
            }
        }

        return ImmutableList.of(new FoliagePlacer.FoliageAttachment(pos.above(freeTreeHeight), 0, true));
    }

    private void placeLogIfFreeWithOffset(
        LevelSimulatedReader level,
        BiConsumer<BlockPos, BlockState> blockSetter,
        RandomSource random,
        BlockPos.MutableBlockPos pos,
        TreeConfiguration config,
        BlockPos offsetPos,
        int offsetX,
        int offsetY,
        int offsetZ
    ) {
        pos.setWithOffset(offsetPos, offsetX, offsetY, offsetZ);
        this.placeLogIfFree(level, blockSetter, random, pos, config);
    }
}
