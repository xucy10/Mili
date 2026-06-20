package net.minecraft.world.level.levelgen.feature.rootplacers;

import com.google.common.collect.Lists;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

public class MangroveRootPlacer extends RootPlacer {
    public static final int ROOT_WIDTH_LIMIT = 8;
    public static final int ROOT_LENGTH_LIMIT = 15;
    public static final MapCodec<MangroveRootPlacer> CODEC = RecordCodecBuilder.mapCodec(
        instance -> rootPlacerParts(instance)
            .and(MangroveRootPlacement.CODEC.fieldOf("mangrove_root_placement").forGetter(placement -> placement.mangroveRootPlacement))
            .apply(instance, MangroveRootPlacer::new)
    );
    private final MangroveRootPlacement mangroveRootPlacement;

    public MangroveRootPlacer(
        IntProvider trunkOffset, BlockStateProvider rootProvider, Optional<AboveRootPlacement> aboveRootPlacement, MangroveRootPlacement mangroveRootPlacement
    ) {
        super(trunkOffset, rootProvider, aboveRootPlacement);
        this.mangroveRootPlacement = mangroveRootPlacement;
    }

    @Override
    public boolean placeRoots(
        LevelSimulatedReader level,
        BiConsumer<BlockPos, BlockState> blockSetter,
        RandomSource random,
        BlockPos pos,
        BlockPos trunkOrigin,
        TreeConfiguration treeConfig
    ) {
        List<BlockPos> list = Lists.newArrayList();
        BlockPos.MutableBlockPos mutableBlockPos = pos.mutable();

        while (mutableBlockPos.getY() < trunkOrigin.getY()) {
            if (!this.canPlaceRoot(level, mutableBlockPos)) {
                return false;
            }

            mutableBlockPos.move(Direction.UP);
        }

        list.add(trunkOrigin.below());

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos blockPos = trunkOrigin.relative(direction);
            List<BlockPos> list1 = Lists.newArrayList();
            if (!this.simulateRoots(level, random, blockPos, direction, trunkOrigin, list1, 0)) {
                return false;
            }

            list.addAll(list1);
            list.add(trunkOrigin.relative(direction));
        }

        for (BlockPos blockPos1 : list) {
            this.placeRoot(level, blockSetter, random, blockPos1, treeConfig);
        }

        return true;
    }

    private boolean simulateRoots(
        LevelSimulatedReader level, RandomSource random, BlockPos pos, Direction direction, BlockPos trunkOrigin, List<BlockPos> roots, int length
    ) {
        int maxRootLength = this.mangroveRootPlacement.maxRootLength();
        if (length != maxRootLength && roots.size() <= maxRootLength) {
            for (BlockPos blockPos : this.potentialRootPositions(pos, direction, random, trunkOrigin)) {
                if (this.canPlaceRoot(level, blockPos)) {
                    roots.add(blockPos);
                    if (!this.simulateRoots(level, random, blockPos, direction, trunkOrigin, roots, length + 1)) {
                        return false;
                    }
                }
            }

            return true;
        } else {
            return false;
        }
    }

    protected List<BlockPos> potentialRootPositions(BlockPos pos, Direction direction, RandomSource random, BlockPos trunkOrigin) {
        BlockPos blockPos = pos.below();
        BlockPos blockPos1 = pos.relative(direction);
        int i = pos.distManhattan(trunkOrigin);
        int maxRootWidth = this.mangroveRootPlacement.maxRootWidth();
        float randomSkewChance = this.mangroveRootPlacement.randomSkewChance();
        if (i > maxRootWidth - 3 && i <= maxRootWidth) {
            return random.nextFloat() < randomSkewChance ? List.of(blockPos, blockPos1.below()) : List.of(blockPos);
        } else if (i > maxRootWidth) {
            return List.of(blockPos);
        } else if (random.nextFloat() < randomSkewChance) {
            return List.of(blockPos);
        } else {
            return random.nextBoolean() ? List.of(blockPos1) : List.of(blockPos);
        }
    }

    @Override
    protected boolean canPlaceRoot(LevelSimulatedReader level, BlockPos pos) {
        return super.canPlaceRoot(level, pos) || level.isStateAtPosition(pos, blockState -> blockState.is(this.mangroveRootPlacement.canGrowThrough()));
    }

    @Override
    protected void placeRoot(
        LevelSimulatedReader level, BiConsumer<BlockPos, BlockState> blockSetter, RandomSource random, BlockPos pos, TreeConfiguration treeConfig
    ) {
        if (level.isStateAtPosition(pos, blockState -> blockState.is(this.mangroveRootPlacement.muddyRootsIn()))) {
            BlockState state = this.mangroveRootPlacement.muddyRootsProvider().getState(random, pos);
            blockSetter.accept(pos, this.getPotentiallyWaterloggedState(level, pos, state));
        } else {
            super.placeRoot(level, blockSetter, random, pos, treeConfig);
        }
    }

    @Override
    protected RootPlacerType<?> type() {
        return RootPlacerType.MANGROVE_ROOT_PLACER;
    }
}
