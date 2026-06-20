package net.minecraft.world.level.levelgen.feature.trunkplacers;

import com.google.common.collect.Lists;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;

public class MegaJungleTrunkPlacer extends GiantTrunkPlacer {
    public static final MapCodec<MegaJungleTrunkPlacer> CODEC = RecordCodecBuilder.mapCodec(
        instance -> trunkPlacerParts(instance).apply(instance, MegaJungleTrunkPlacer::new)
    );

    public MegaJungleTrunkPlacer(int baseHeight, int heightRandA, int heightRandB) {
        super(baseHeight, heightRandA, heightRandB);
    }

    @Override
    protected TrunkPlacerType<?> type() {
        return TrunkPlacerType.MEGA_JUNGLE_TRUNK_PLACER;
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
        List<FoliagePlacer.FoliageAttachment> list = Lists.newArrayList();
        list.addAll(super.placeTrunk(level, blockSetter, random, freeTreeHeight, pos, config));

        for (int i = freeTreeHeight - 2 - random.nextInt(4); i > freeTreeHeight / 2; i -= 2 + random.nextInt(4)) {
            float f = random.nextFloat() * (float) (Math.PI * 2);
            int i1 = 0;
            int i2 = 0;

            for (int i3 = 0; i3 < 5; i3++) {
                i1 = (int)(1.5F + Mth.cos(f) * i3);
                i2 = (int)(1.5F + Mth.sin(f) * i3);
                BlockPos blockPos = pos.offset(i1, i - 3 + i3 / 2, i2);
                this.placeLog(level, blockSetter, random, blockPos, config);
            }

            list.add(new FoliagePlacer.FoliageAttachment(pos.offset(i1, i, i2), -2, false));
        }

        return list;
    }
}
