package net.minecraft.world.level.levelgen.feature.foliageplacers;

import com.mojang.datafixers.Products.P2;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import com.mojang.serialization.codecs.RecordCodecBuilder.Mu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.material.Fluids;

public abstract class FoliagePlacer {
    public static final Codec<FoliagePlacer> CODEC = BuiltInRegistries.FOLIAGE_PLACER_TYPE
        .byNameCodec()
        .dispatch(FoliagePlacer::type, FoliagePlacerType::codec);
    protected final IntProvider radius;
    protected final IntProvider offset;

    protected static <P extends FoliagePlacer> P2<Mu<P>, IntProvider, IntProvider> foliagePlacerParts(Instance<P> instance) {
        return instance.group(
            IntProvider.codec(0, 16).fieldOf("radius").forGetter(foliagePlacer -> foliagePlacer.radius),
            IntProvider.codec(0, 16).fieldOf("offset").forGetter(foliagePlacer -> foliagePlacer.offset)
        );
    }

    public FoliagePlacer(IntProvider radius, IntProvider offset) {
        this.radius = radius;
        this.offset = offset;
    }

    protected abstract FoliagePlacerType<?> type();

    public void createFoliage(
        LevelSimulatedReader level,
        FoliagePlacer.FoliageSetter blockSetter,
        RandomSource random,
        TreeConfiguration config,
        int maxFreeTreeHeight,
        FoliagePlacer.FoliageAttachment attachment,
        int foliageHeight,
        int foliageRadius
    ) {
        this.createFoliage(level, blockSetter, random, config, maxFreeTreeHeight, attachment, foliageHeight, foliageRadius, this.offset(random));
    }

    protected abstract void createFoliage(
        LevelSimulatedReader level,
        FoliagePlacer.FoliageSetter blockSetter,
        RandomSource random,
        TreeConfiguration config,
        int maxFreeTreeHeight,
        FoliagePlacer.FoliageAttachment attachment,
        int foliageHeight,
        int foliageRadius,
        int offset
    );

    public abstract int foliageHeight(RandomSource random, int height, TreeConfiguration config);

    public int foliageRadius(RandomSource random, int radius) {
        return this.radius.sample(random);
    }

    private int offset(RandomSource random) {
        return this.offset.sample(random);
    }

    protected abstract boolean shouldSkipLocation(RandomSource random, int localX, int localY, int localZ, int range, boolean large);

    protected boolean shouldSkipLocationSigned(RandomSource random, int localX, int localY, int localZ, int range, boolean large) {
        int min;
        int min1;
        if (large) {
            min = Math.min(Math.abs(localX), Math.abs(localX - 1));
            min1 = Math.min(Math.abs(localZ), Math.abs(localZ - 1));
        } else {
            min = Math.abs(localX);
            min1 = Math.abs(localZ);
        }

        return this.shouldSkipLocation(random, min, localY, min1, range, large);
    }

    protected void placeLeavesRow(
        LevelSimulatedReader level,
        FoliagePlacer.FoliageSetter foliageSetter,
        RandomSource random,
        TreeConfiguration treeConfiguration,
        BlockPos pos,
        int range,
        int localY,
        boolean large
    ) {
        int i = large ? 1 : 0;
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        for (int i1 = -range; i1 <= range + i; i1++) {
            for (int i2 = -range; i2 <= range + i; i2++) {
                if (!this.shouldSkipLocationSigned(random, i1, localY, i2, range, large)) {
                    mutableBlockPos.setWithOffset(pos, i1, localY, i2);
                    tryPlaceLeaf(level, foliageSetter, random, treeConfiguration, mutableBlockPos);
                }
            }
        }
    }

    protected final void placeLeavesRowWithHangingLeavesBelow(
        LevelSimulatedReader level,
        FoliagePlacer.FoliageSetter foliageSetter,
        RandomSource random,
        TreeConfiguration treeConfiguration,
        BlockPos pos,
        int range,
        int localY,
        boolean large,
        float hangingLeavesChance,
        float hangingLeavesExtensionChance
    ) {
        this.placeLeavesRow(level, foliageSetter, random, treeConfiguration, pos, range, localY, large);
        int i = large ? 1 : 0;
        BlockPos blockPos = pos.below();
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            Direction clockWise = direction.getClockWise();
            int i1 = clockWise.getAxisDirection() == Direction.AxisDirection.POSITIVE ? range + i : range;
            mutableBlockPos.setWithOffset(pos, 0, localY - 1, 0).move(clockWise, i1).move(direction, -range);
            int i2 = -range;

            while (i2 < range + i) {
                boolean isSet = foliageSetter.isSet(mutableBlockPos.move(Direction.UP));
                mutableBlockPos.move(Direction.DOWN);
                if (isSet && tryPlaceExtension(level, foliageSetter, random, treeConfiguration, hangingLeavesChance, blockPos, mutableBlockPos)) {
                    mutableBlockPos.move(Direction.DOWN);
                    tryPlaceExtension(level, foliageSetter, random, treeConfiguration, hangingLeavesExtensionChance, blockPos, mutableBlockPos);
                    mutableBlockPos.move(Direction.UP);
                }

                i2++;
                mutableBlockPos.move(direction);
            }
        }
    }

    private static boolean tryPlaceExtension(
        LevelSimulatedReader level,
        FoliagePlacer.FoliageSetter foliageSetter,
        RandomSource random,
        TreeConfiguration treeConfiguration,
        float extensionChance,
        BlockPos logPos,
        BlockPos.MutableBlockPos pos
    ) {
        return pos.distManhattan(logPos) < 7 && !(random.nextFloat() > extensionChance) && tryPlaceLeaf(level, foliageSetter, random, treeConfiguration, pos);
    }

    protected static boolean tryPlaceLeaf(
        LevelSimulatedReader level, FoliagePlacer.FoliageSetter foliageSetter, RandomSource random, TreeConfiguration treeConfiguration, BlockPos pos
    ) {
        boolean isStateAtPosition = level.isStateAtPosition(pos, blockState -> blockState.getValueOrElse(BlockStateProperties.PERSISTENT, false));
        if (!isStateAtPosition && TreeFeature.validTreePos(level, pos)) {
            BlockState state = treeConfiguration.foliageProvider.getState(random, pos);
            if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
                state = state.setValue(BlockStateProperties.WATERLOGGED, level.isFluidAtPosition(pos, fluidState -> fluidState.isSourceOfType(Fluids.WATER)));
            }

            foliageSetter.set(pos, state);
            return true;
        } else {
            return false;
        }
    }

    public static final class FoliageAttachment {
        private final BlockPos pos;
        private final int radiusOffset;
        private final boolean doubleTrunk;

        public FoliageAttachment(BlockPos pos, int radiusOffset, boolean doubleTrunk) {
            this.pos = pos;
            this.radiusOffset = radiusOffset;
            this.doubleTrunk = doubleTrunk;
        }

        public BlockPos pos() {
            return this.pos;
        }

        public int radiusOffset() {
            return this.radiusOffset;
        }

        public boolean doubleTrunk() {
            return this.doubleTrunk;
        }
    }

    public interface FoliageSetter {
        void set(BlockPos pos, BlockState state);

        boolean isSet(BlockPos pos);
    }
}
