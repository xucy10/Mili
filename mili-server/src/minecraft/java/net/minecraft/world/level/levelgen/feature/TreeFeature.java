package net.minecraft.world.level.levelgen.feature;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.mojang.serialization.Codec;
import java.util.Iterator;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.LevelWriter;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecorator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;

public class TreeFeature extends Feature<TreeConfiguration> {
    @Block.UpdateFlags
    private static final int BLOCK_UPDATE_FLAGS = 19;

    public TreeFeature(Codec<TreeConfiguration> codec) {
        super(codec);
    }

    public static boolean isVine(LevelSimulatedReader level, BlockPos pos) {
        return level.isStateAtPosition(pos, state -> state.is(Blocks.VINE));
    }

    public static boolean isAirOrLeaves(LevelSimulatedReader level, BlockPos pos) {
        return level.isStateAtPosition(pos, state -> state.isAir() || state.is(BlockTags.LEAVES));
    }

    private static void setBlockKnownShape(LevelWriter level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_ALL | Block.UPDATE_KNOWN_SHAPE);
    }

    public static boolean validTreePos(LevelSimulatedReader level, BlockPos pos) {
        return level.isStateAtPosition(pos, state -> state.isAir() || state.is(BlockTags.REPLACEABLE_BY_TREES));
    }

    private boolean doPlace(
        WorldGenLevel level,
        RandomSource random,
        BlockPos pos,
        BiConsumer<BlockPos, BlockState> rootBlockSetter,
        BiConsumer<BlockPos, BlockState> trunkBlockSetter,
        FoliagePlacer.FoliageSetter foliageBlockSetter,
        TreeConfiguration config
    ) {
        int treeHeight = config.trunkPlacer.getTreeHeight(random);
        int i = config.foliagePlacer.foliageHeight(random, treeHeight, config);
        int i1 = treeHeight - i;
        int i2 = config.foliagePlacer.foliageRadius(random, i1);
        BlockPos blockPos = config.rootPlacer.<BlockPos>map(placer -> placer.getTrunkOrigin(pos, random)).orElse(pos);
        int min = Math.min(pos.getY(), blockPos.getY());
        int i3 = Math.max(pos.getY(), blockPos.getY()) + treeHeight + 1;
        if (min >= level.getMinY() + 1 && i3 <= level.getMaxY() + 1) {
            OptionalInt optionalInt = config.minimumSize.minClippedHeight();
            int maxFreeTreeHeight = this.getMaxFreeTreeHeight(level, treeHeight, blockPos, config);
            if (maxFreeTreeHeight >= treeHeight || !optionalInt.isEmpty() && maxFreeTreeHeight >= optionalInt.getAsInt()) {
                if (config.rootPlacer.isPresent() && !config.rootPlacer.get().placeRoots(level, rootBlockSetter, random, pos, blockPos, config)) {
                    return false;
                } else {
                    List<FoliagePlacer.FoliageAttachment> list = config.trunkPlacer
                        .placeTrunk(level, trunkBlockSetter, random, maxFreeTreeHeight, blockPos, config);
                    list.forEach(
                        attachment -> config.foliagePlacer.createFoliage(level, foliageBlockSetter, random, config, maxFreeTreeHeight, attachment, i, i2)
                    );
                    return true;
                }
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    private int getMaxFreeTreeHeight(LevelSimulatedReader level, int trunkHeight, BlockPos topPosition, TreeConfiguration config) {
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        for (int i = 0; i <= trunkHeight + 1; i++) {
            int sizeAtHeight = config.minimumSize.getSizeAtHeight(trunkHeight, i);

            for (int i1 = -sizeAtHeight; i1 <= sizeAtHeight; i1++) {
                for (int i2 = -sizeAtHeight; i2 <= sizeAtHeight; i2++) {
                    mutableBlockPos.setWithOffset(topPosition, i1, i, i2);
                    if (!config.trunkPlacer.isFree(level, mutableBlockPos) || !config.ignoreVines && isVine(level, mutableBlockPos)) {
                        return i - 2;
                    }
                }
            }
        }

        return trunkHeight;
    }

    @Override
    protected void setBlock(LevelWriter level, BlockPos pos, BlockState state) {
        setBlockKnownShape(level, pos, state);
    }

    @Override
    public final boolean place(FeaturePlaceContext<TreeConfiguration> context) {
        final WorldGenLevel worldGenLevel = context.level();
        RandomSource randomSource = context.random();
        BlockPos blockPos = context.origin();
        TreeConfiguration treeConfiguration = context.config();
        Set<BlockPos> set = Sets.newHashSet();
        Set<BlockPos> set1 = Sets.newHashSet();
        final Set<BlockPos> set2 = Sets.newHashSet();
        Set<BlockPos> set3 = Sets.newHashSet();
        BiConsumer<BlockPos, BlockState> biConsumer = (pos, state) -> {
            set.add(pos.immutable());
            worldGenLevel.setBlock(pos, state, Block.UPDATE_ALL | Block.UPDATE_KNOWN_SHAPE);
        };
        BiConsumer<BlockPos, BlockState> biConsumer1 = (pos, state) -> {
            set1.add(pos.immutable());
            worldGenLevel.setBlock(pos, state, Block.UPDATE_ALL | Block.UPDATE_KNOWN_SHAPE);
        };
        FoliagePlacer.FoliageSetter foliageSetter = new FoliagePlacer.FoliageSetter() {
            @Override
            public void set(BlockPos pos, BlockState state) {
                set2.add(pos.immutable());
                worldGenLevel.setBlock(pos, state, Block.UPDATE_ALL | Block.UPDATE_KNOWN_SHAPE);
            }

            @Override
            public boolean isSet(BlockPos pos) {
                return set2.contains(pos);
            }
        };
        BiConsumer<BlockPos, BlockState> biConsumer2 = (pos, state) -> {
            set3.add(pos.immutable());
            worldGenLevel.setBlock(pos, state, Block.UPDATE_ALL | Block.UPDATE_KNOWN_SHAPE);
        };
        boolean flag = this.doPlace(worldGenLevel, randomSource, blockPos, biConsumer, biConsumer1, foliageSetter, treeConfiguration);
        if (flag && (!set1.isEmpty() || !set2.isEmpty())) {
            if (!treeConfiguration.decorators.isEmpty()) {
                TreeDecorator.Context context1 = new TreeDecorator.Context(worldGenLevel, biConsumer2, randomSource, set1, set2, set);
                treeConfiguration.decorators.forEach(decorator -> decorator.place(context1));
            }

            return BoundingBox.encapsulatingPositions(Iterables.concat(set, set1, set2, set3)).map(box -> {
                DiscreteVoxelShape discreteVoxelShape = updateLeaves(worldGenLevel, box, set1, set3, set);
                StructureTemplate.updateShapeAtEdge(worldGenLevel, Block.UPDATE_ALL, discreteVoxelShape, box.minX(), box.minY(), box.minZ());
                return true;
            }).orElse(false);
        } else {
            return false;
        }
    }

    private static DiscreteVoxelShape updateLeaves(
        LevelAccessor level, BoundingBox box, Set<BlockPos> trunkPositions, Set<BlockPos> foliagePositions, Set<BlockPos> rootPositions
    ) {
        DiscreteVoxelShape discreteVoxelShape = new BitSetDiscreteVoxelShape(box.getXSpan(), box.getYSpan(), box.getZSpan());
        int i = 7;
        List<Set<BlockPos>> list = Lists.newArrayList();

        for (int i1 = 0; i1 < 7; i1++) {
            list.add(Sets.newHashSet());
        }

        for (BlockPos blockPos : Lists.newArrayList(Sets.union(foliagePositions, rootPositions))) {
            if (box.isInside(blockPos)) {
                discreteVoxelShape.fill(blockPos.getX() - box.minX(), blockPos.getY() - box.minY(), blockPos.getZ() - box.minZ());
            }
        }

        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        int i2 = 0;
        list.get(0).addAll(trunkPositions);

        while (true) {
            while (i2 >= 7 || !list.get(i2).isEmpty()) {
                if (i2 >= 7) {
                    return discreteVoxelShape;
                }

                Iterator<BlockPos> iterator = list.get(i2).iterator();
                BlockPos blockPos1 = iterator.next();
                iterator.remove();
                if (box.isInside(blockPos1)) {
                    if (i2 != 0) {
                        BlockState blockState = level.getBlockState(blockPos1);
                        setBlockKnownShape(level, blockPos1, blockState.setValue(BlockStateProperties.DISTANCE, i2));
                    }

                    discreteVoxelShape.fill(blockPos1.getX() - box.minX(), blockPos1.getY() - box.minY(), blockPos1.getZ() - box.minZ());

                    for (Direction direction : Direction.values()) {
                        mutableBlockPos.setWithOffset(blockPos1, direction);
                        if (box.isInside(mutableBlockPos)) {
                            int i3 = mutableBlockPos.getX() - box.minX();
                            int i4 = mutableBlockPos.getY() - box.minY();
                            int i5 = mutableBlockPos.getZ() - box.minZ();
                            if (!discreteVoxelShape.isFull(i3, i4, i5)) {
                                BlockState blockState1 = level.getBlockState(mutableBlockPos);
                                OptionalInt optionalDistanceAt = LeavesBlock.getOptionalDistanceAt(blockState1);
                                if (!optionalDistanceAt.isEmpty()) {
                                    int min = Math.min(optionalDistanceAt.getAsInt(), i2 + 1);
                                    if (min < 7) {
                                        list.get(min).add(mutableBlockPos.immutable());
                                        i2 = Math.min(i2, min);
                                    }
                                }
                            }
                        }
                    }
                }
            }

            i2++;
        }
    }

    public static List<BlockPos> getLowestTrunkOrRootOfTree(TreeDecorator.Context context) {
        List<BlockPos> list = Lists.newArrayList();
        List<BlockPos> list1 = context.roots();
        List<BlockPos> list2 = context.logs();
        if (list1.isEmpty()) {
            list.addAll(list2);
        } else if (!list2.isEmpty() && list1.get(0).getY() == list2.get(0).getY()) {
            list.addAll(list2);
            list.addAll(list1);
        } else {
            list.addAll(list1);
        }

        return list;
    }
}
