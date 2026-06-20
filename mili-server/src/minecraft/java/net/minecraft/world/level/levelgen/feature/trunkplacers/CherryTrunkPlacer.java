package net.minecraft.world.level.levelgen.feature.trunkplacers;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;

public class CherryTrunkPlacer extends TrunkPlacer {
    private static final Codec<UniformInt> BRANCH_START_CODEC = UniformInt.CODEC
        .codec()
        .validate(
            uniformInt -> uniformInt.getMaxValue() - uniformInt.getMinValue() < 1
                ? DataResult.error(() -> "Need at least 2 blocks variation for the branch starts to fit both branches")
                : DataResult.success(uniformInt)
        );
    public static final MapCodec<CherryTrunkPlacer> CODEC = RecordCodecBuilder.mapCodec(
        instance -> trunkPlacerParts(instance)
            .and(
                instance.group(
                    IntProvider.codec(1, 3).fieldOf("branch_count").forGetter(trunkPlacer -> trunkPlacer.branchCount),
                    IntProvider.codec(2, 16).fieldOf("branch_horizontal_length").forGetter(trunkPlacer -> trunkPlacer.branchHorizontalLength),
                    IntProvider.validateCodec(-16, 0, BRANCH_START_CODEC)
                        .fieldOf("branch_start_offset_from_top")
                        .forGetter(trunkPlacer -> trunkPlacer.branchStartOffsetFromTop),
                    IntProvider.codec(-16, 16).fieldOf("branch_end_offset_from_top").forGetter(trunkPlacer -> trunkPlacer.branchEndOffsetFromTop)
                )
            )
            .apply(instance, CherryTrunkPlacer::new)
    );
    private final IntProvider branchCount;
    private final IntProvider branchHorizontalLength;
    private final UniformInt branchStartOffsetFromTop;
    private final UniformInt secondBranchStartOffsetFromTop;
    private final IntProvider branchEndOffsetFromTop;

    public CherryTrunkPlacer(
        int baseHeight,
        int heightRandA,
        int heightRandB,
        IntProvider branchCount,
        IntProvider branchHorizontalLength,
        UniformInt branchStartOffsetFromTop,
        IntProvider branchEndOffsetFromTop
    ) {
        super(baseHeight, heightRandA, heightRandB);
        this.branchCount = branchCount;
        this.branchHorizontalLength = branchHorizontalLength;
        this.branchStartOffsetFromTop = branchStartOffsetFromTop;
        this.secondBranchStartOffsetFromTop = UniformInt.of(branchStartOffsetFromTop.getMinValue(), branchStartOffsetFromTop.getMaxValue() - 1);
        this.branchEndOffsetFromTop = branchEndOffsetFromTop;
    }

    @Override
    protected TrunkPlacerType<?> type() {
        return TrunkPlacerType.CHERRY_TRUNK_PLACER;
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
        int max = Math.max(0, freeTreeHeight - 1 + this.branchStartOffsetFromTop.sample(random));
        int max1 = Math.max(0, freeTreeHeight - 1 + this.secondBranchStartOffsetFromTop.sample(random));
        if (max1 >= max) {
            max1++;
        }

        int i = this.branchCount.sample(random);
        boolean flag = i == 3;
        boolean flag1 = i >= 2;
        int i1;
        if (flag) {
            i1 = freeTreeHeight;
        } else if (flag1) {
            i1 = Math.max(max, max1) + 1;
        } else {
            i1 = max + 1;
        }

        for (int i2 = 0; i2 < i1; i2++) {
            this.placeLog(level, blockSetter, random, pos.above(i2), config);
        }

        List<FoliagePlacer.FoliageAttachment> list = new ArrayList<>();
        if (flag) {
            list.add(new FoliagePlacer.FoliageAttachment(pos.above(i1), 0, false));
        }

        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        Direction randomDirection = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        Function<BlockState, BlockState> function = blockState -> blockState.trySetValue(RotatedPillarBlock.AXIS, randomDirection.getAxis());
        list.add(this.generateBranch(level, blockSetter, random, freeTreeHeight, pos, config, function, randomDirection, max, max < i1 - 1, mutableBlockPos));
        if (flag1) {
            list.add(
                this.generateBranch(
                    level, blockSetter, random, freeTreeHeight, pos, config, function, randomDirection.getOpposite(), max1, max1 < i1 - 1, mutableBlockPos
                )
            );
        }

        return list;
    }

    private FoliagePlacer.FoliageAttachment generateBranch(
        LevelSimulatedReader level,
        BiConsumer<BlockPos, BlockState> blockSetter,
        RandomSource random,
        int freeTreeHeight,
        BlockPos pos,
        TreeConfiguration config,
        Function<BlockState, BlockState> propertySetter,
        Direction direction,
        int secondBranchStartOffsetFromTop,
        boolean doubleBranch,
        BlockPos.MutableBlockPos currentPos
    ) {
        currentPos.set(pos).move(Direction.UP, secondBranchStartOffsetFromTop);
        int i = freeTreeHeight - 1 + this.branchEndOffsetFromTop.sample(random);
        boolean flag = doubleBranch || i < secondBranchStartOffsetFromTop;
        int i1 = this.branchHorizontalLength.sample(random) + (flag ? 1 : 0);
        BlockPos blockPos = pos.relative(direction, i1).above(i);
        int i2 = flag ? 2 : 1;

        for (int i3 = 0; i3 < i2; i3++) {
            this.placeLog(level, blockSetter, random, currentPos.move(direction), config, propertySetter);
        }

        Direction direction1 = blockPos.getY() > currentPos.getY() ? Direction.UP : Direction.DOWN;

        while (true) {
            int i4 = currentPos.distManhattan(blockPos);
            if (i4 == 0) {
                return new FoliagePlacer.FoliageAttachment(blockPos.above(), 0, false);
            }

            float f = (float)Math.abs(blockPos.getY() - currentPos.getY()) / i4;
            boolean flag1 = random.nextFloat() < f;
            currentPos.move(flag1 ? direction1 : direction);
            this.placeLog(level, blockSetter, random, currentPos, config, flag1 ? Function.identity() : propertySetter);
        }
    }
}
