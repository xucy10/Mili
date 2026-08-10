package net.minecraft.world.level.levelgen.feature.trunkplacers;

import com.google.common.collect.Lists;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;

public class DarkOakTrunkPlacer extends TrunkPlacer {
    public static final MapCodec<DarkOakTrunkPlacer> CODEC = RecordCodecBuilder.mapCodec(
        instance -> trunkPlacerParts(instance).apply(instance, DarkOakTrunkPlacer::new)
    );

    public DarkOakTrunkPlacer(int baseHeight, int heightRandA, int heightRandB) {
        super(baseHeight, heightRandA, heightRandB);
    }

    @Override
    protected TrunkPlacerType<?> type() {
        return TrunkPlacerType.DARK_OAK_TRUNK_PLACER;
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
        BlockPos blockPos = pos.below();
        setDirtAt(level, blockSetter, random, blockPos, config);
        setDirtAt(level, blockSetter, random, blockPos.east(), config);
        setDirtAt(level, blockSetter, random, blockPos.south(), config);
        setDirtAt(level, blockSetter, random, blockPos.south().east(), config);
        Direction randomDirection = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        int i = freeTreeHeight - random.nextInt(4);
        int i1 = 2 - random.nextInt(3);
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        int i2 = x;
        int i3 = z;
        int i4 = y + freeTreeHeight - 1;

        for (int i5 = 0; i5 < freeTreeHeight; i5++) {
            if (i5 >= i && i1 > 0) {
                i2 += randomDirection.getStepX();
                i3 += randomDirection.getStepZ();
                i1--;
            }

            int i6 = y + i5;
            BlockPos blockPos1 = new BlockPos(i2, i6, i3);
            if (TreeFeature.isAirOrLeaves(level, blockPos1)) {
                this.placeLog(level, blockSetter, random, blockPos1, config);
                this.placeLog(level, blockSetter, random, blockPos1.east(), config);
                this.placeLog(level, blockSetter, random, blockPos1.south(), config);
                this.placeLog(level, blockSetter, random, blockPos1.east().south(), config);
            }
        }

        list.add(new FoliagePlacer.FoliageAttachment(new BlockPos(i2, i4, i3), 0, true));

        for (int i5 = -1; i5 <= 2; i5++) {
            for (int i6 = -1; i6 <= 2; i6++) {
                if ((i5 < 0 || i5 > 1 || i6 < 0 || i6 > 1) && random.nextInt(3) <= 0) {
                    int i7 = random.nextInt(3) + 2;

                    for (int i8 = 0; i8 < i7; i8++) {
                        this.placeLog(level, blockSetter, random, new BlockPos(x + i5, i4 - i8 - 1, z + i6), config);
                    }

                    list.add(new FoliagePlacer.FoliageAttachment(new BlockPos(x + i5, i4, z + i6), 0, false));
                }
            }
        }

        return list;
    }
}
