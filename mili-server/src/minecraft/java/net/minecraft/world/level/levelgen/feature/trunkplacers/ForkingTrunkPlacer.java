package net.minecraft.world.level.levelgen.feature.trunkplacers;

import com.google.common.collect.Lists;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;

public class ForkingTrunkPlacer extends TrunkPlacer {
    public static final MapCodec<ForkingTrunkPlacer> CODEC = RecordCodecBuilder.mapCodec(
        instance -> trunkPlacerParts(instance).apply(instance, ForkingTrunkPlacer::new)
    );

    public ForkingTrunkPlacer(int baseHeight, int heightRandA, int heightRandB) {
        super(baseHeight, heightRandA, heightRandB);
    }

    @Override
    protected TrunkPlacerType<?> type() {
        return TrunkPlacerType.FORKING_TRUNK_PLACER;
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
        setDirtAt(level, blockSetter, random, pos.below(), config);
        List<FoliagePlacer.FoliageAttachment> list = Lists.newArrayList();
        Direction randomDirection = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        int i = freeTreeHeight - random.nextInt(4) - 1;
        int i1 = 3 - random.nextInt(3);
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        int x = pos.getX();
        int z = pos.getZ();
        OptionalInt optionalInt = OptionalInt.empty();

        for (int i2 = 0; i2 < freeTreeHeight; i2++) {
            int i3 = pos.getY() + i2;
            if (i2 >= i && i1 > 0) {
                x += randomDirection.getStepX();
                z += randomDirection.getStepZ();
                i1--;
            }

            if (this.placeLog(level, blockSetter, random, mutableBlockPos.set(x, i3, z), config)) {
                optionalInt = OptionalInt.of(i3 + 1);
            }
        }

        if (optionalInt.isPresent()) {
            list.add(new FoliagePlacer.FoliageAttachment(new BlockPos(x, optionalInt.getAsInt(), z), 1, false));
        }

        x = pos.getX();
        z = pos.getZ();
        Direction randomDirection1 = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        if (randomDirection1 != randomDirection) {
            int i3x = i - random.nextInt(2) - 1;
            int i4 = 1 + random.nextInt(3);
            optionalInt = OptionalInt.empty();

            for (int i5 = i3x; i5 < freeTreeHeight && i4 > 0; i4--) {
                if (i5 >= 1) {
                    int i6 = pos.getY() + i5;
                    x += randomDirection1.getStepX();
                    z += randomDirection1.getStepZ();
                    if (this.placeLog(level, blockSetter, random, mutableBlockPos.set(x, i6, z), config)) {
                        optionalInt = OptionalInt.of(i6 + 1);
                    }
                }

                i5++;
            }

            if (optionalInt.isPresent()) {
                list.add(new FoliagePlacer.FoliageAttachment(new BlockPos(x, optionalInt.getAsInt(), z), 0, false));
            }
        }

        return list;
    }
}
