package net.minecraft.world.level.levelgen.feature.trunkplacers;

import com.google.common.collect.Lists;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;

public class FancyTrunkPlacer extends TrunkPlacer {
    public static final MapCodec<FancyTrunkPlacer> CODEC = RecordCodecBuilder.mapCodec(
        instance -> trunkPlacerParts(instance).apply(instance, FancyTrunkPlacer::new)
    );
    private static final double TRUNK_HEIGHT_SCALE = 0.618;
    private static final double CLUSTER_DENSITY_MAGIC = 1.382;
    private static final double BRANCH_SLOPE = 0.381;
    private static final double BRANCH_LENGTH_MAGIC = 0.328;

    public FancyTrunkPlacer(int baseHeight, int heightRandA, int heightRandB) {
        super(baseHeight, heightRandA, heightRandB);
    }

    @Override
    protected TrunkPlacerType<?> type() {
        return TrunkPlacerType.FANCY_TRUNK_PLACER;
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
        int i = 5;
        int i1 = freeTreeHeight + 2;
        int floor = Mth.floor(i1 * 0.618);
        setDirtAt(level, blockSetter, random, pos.below(), config);
        double d = 1.0;
        int min = Math.min(1, Mth.floor(1.382 + Math.pow(1.0 * i1 / 13.0, 2.0)));
        int i2 = pos.getY() + floor;
        int i3 = i1 - 5;
        List<FancyTrunkPlacer.FoliageCoords> list = Lists.newArrayList();
        list.add(new FancyTrunkPlacer.FoliageCoords(pos.above(i3), i2));

        for (; i3 >= 0; i3--) {
            float f = treeShape(i1, i3);
            if (!(f < 0.0F)) {
                for (int i4 = 0; i4 < min; i4++) {
                    double d1 = 1.0;
                    double d2 = 1.0 * f * (random.nextFloat() + 0.328);
                    double d3 = random.nextFloat() * 2.0F * Math.PI;
                    double d4 = d2 * Math.sin(d3) + 0.5;
                    double d5 = d2 * Math.cos(d3) + 0.5;
                    BlockPos blockPos = pos.offset(Mth.floor(d4), i3 - 1, Mth.floor(d5));
                    BlockPos blockPos1 = blockPos.above(5);
                    if (this.makeLimb(level, blockSetter, random, blockPos, blockPos1, false, config)) {
                        int i5 = pos.getX() - blockPos.getX();
                        int i6 = pos.getZ() - blockPos.getZ();
                        double d6 = blockPos.getY() - Math.sqrt(i5 * i5 + i6 * i6) * 0.381;
                        int i7 = d6 > i2 ? i2 : (int)d6;
                        BlockPos blockPos2 = new BlockPos(pos.getX(), i7, pos.getZ());
                        if (this.makeLimb(level, blockSetter, random, blockPos2, blockPos, false, config)) {
                            list.add(new FancyTrunkPlacer.FoliageCoords(blockPos, blockPos2.getY()));
                        }
                    }
                }
            }
        }

        this.makeLimb(level, blockSetter, random, pos, pos.above(floor), true, config);
        this.makeBranches(level, blockSetter, random, i1, pos, list, config);
        List<FoliagePlacer.FoliageAttachment> list1 = Lists.newArrayList();

        for (FancyTrunkPlacer.FoliageCoords foliageCoords : list) {
            if (this.trimBranches(i1, foliageCoords.getBranchBase() - pos.getY())) {
                list1.add(foliageCoords.attachment);
            }
        }

        return list1;
    }

    private boolean makeLimb(
        LevelSimulatedReader level,
        BiConsumer<BlockPos, BlockState> blockSetter,
        RandomSource random,
        BlockPos basePos,
        BlockPos offsetPos,
        boolean modifyWorld,
        TreeConfiguration config
    ) {
        if (!modifyWorld && Objects.equals(basePos, offsetPos)) {
            return true;
        } else {
            BlockPos blockPos = offsetPos.offset(-basePos.getX(), -basePos.getY(), -basePos.getZ());
            int steps = this.getSteps(blockPos);
            float f = (float)blockPos.getX() / steps;
            float f1 = (float)blockPos.getY() / steps;
            float f2 = (float)blockPos.getZ() / steps;

            for (int i = 0; i <= steps; i++) {
                BlockPos blockPos1 = basePos.offset(Mth.floor(0.5F + i * f), Mth.floor(0.5F + i * f1), Mth.floor(0.5F + i * f2));
                if (modifyWorld) {
                    this.placeLog(
                        level,
                        blockSetter,
                        random,
                        blockPos1,
                        config,
                        blockState -> blockState.trySetValue(RotatedPillarBlock.AXIS, this.getLogAxis(basePos, blockPos1))
                    );
                } else if (!this.isFree(level, blockPos1)) {
                    return false;
                }
            }

            return true;
        }
    }

    private int getSteps(BlockPos pos) {
        int abs = Mth.abs(pos.getX());
        int abs1 = Mth.abs(pos.getY());
        int abs2 = Mth.abs(pos.getZ());
        return Math.max(abs, Math.max(abs1, abs2));
    }

    private Direction.Axis getLogAxis(BlockPos pos, BlockPos otherPos) {
        Direction.Axis axis = Direction.Axis.Y;
        int abs = Math.abs(otherPos.getX() - pos.getX());
        int abs1 = Math.abs(otherPos.getZ() - pos.getZ());
        int max = Math.max(abs, abs1);
        if (max > 0) {
            if (abs == max) {
                axis = Direction.Axis.X;
            } else {
                axis = Direction.Axis.Z;
            }
        }

        return axis;
    }

    private boolean trimBranches(int maxHeight, int currentHeight) {
        return currentHeight >= maxHeight * 0.2;
    }

    private void makeBranches(
        LevelSimulatedReader level,
        BiConsumer<BlockPos, BlockState> blockSetter,
        RandomSource random,
        int maxHeight,
        BlockPos pos,
        List<FancyTrunkPlacer.FoliageCoords> foliageCoords,
        TreeConfiguration config
    ) {
        for (FancyTrunkPlacer.FoliageCoords foliageCoords1 : foliageCoords) {
            int branchBase = foliageCoords1.getBranchBase();
            BlockPos blockPos = new BlockPos(pos.getX(), branchBase, pos.getZ());
            if (!blockPos.equals(foliageCoords1.attachment.pos()) && this.trimBranches(maxHeight, branchBase - pos.getY())) {
                this.makeLimb(level, blockSetter, random, blockPos, foliageCoords1.attachment.pos(), true, config);
            }
        }
    }

    private static float treeShape(int height, int currentY) {
        if (currentY < height * 0.3F) {
            return -1.0F;
        } else {
            float f = height / 2.0F;
            float f1 = f - currentY;
            float squareRoot = Mth.sqrt(f * f - f1 * f1);
            if (f1 == 0.0F) {
                squareRoot = f;
            } else if (Math.abs(f1) >= f) {
                return 0.0F;
            }

            return squareRoot * 0.5F;
        }
    }

    static class FoliageCoords {
        final FoliagePlacer.FoliageAttachment attachment;
        private final int branchBase;

        public FoliageCoords(BlockPos attachmentPos, int branchBase) {
            this.attachment = new FoliagePlacer.FoliageAttachment(attachmentPos, 0, false);
            this.branchBase = branchBase;
        }

        public int getBranchBase() {
            return this.branchBase;
        }
    }
}
