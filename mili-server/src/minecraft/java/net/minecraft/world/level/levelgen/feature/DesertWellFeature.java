package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.predicate.BlockStatePredicate;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

public class DesertWellFeature extends Feature<NoneFeatureConfiguration> {
    private static final BlockStatePredicate IS_SAND = BlockStatePredicate.forBlock(Blocks.SAND);
    private final BlockState sand = Blocks.SAND.defaultBlockState();
    private final BlockState sandSlab = Blocks.SANDSTONE_SLAB.defaultBlockState();
    private final BlockState sandstone = Blocks.SANDSTONE.defaultBlockState();
    private final BlockState water = Blocks.WATER.defaultBlockState();

    public DesertWellFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel worldGenLevel = context.level();
        BlockPos blockPos = context.origin();
        blockPos = blockPos.above();

        while (worldGenLevel.isEmptyBlock(blockPos) && blockPos.getY() > worldGenLevel.getMinY() + 2) {
            blockPos = blockPos.below();
        }

        if (!IS_SAND.test(worldGenLevel.getBlockState(blockPos))) {
            return false;
        } else {
            for (int i = -2; i <= 2; i++) {
                for (int i1 = -2; i1 <= 2; i1++) {
                    if (worldGenLevel.isEmptyBlock(blockPos.offset(i, -1, i1)) && worldGenLevel.isEmptyBlock(blockPos.offset(i, -2, i1))) {
                        return false;
                    }
                }
            }

            for (int i = -2; i <= 0; i++) {
                for (int i1x = -2; i1x <= 2; i1x++) {
                    for (int i2 = -2; i2 <= 2; i2++) {
                        worldGenLevel.setBlock(blockPos.offset(i1x, i, i2), this.sandstone, Block.UPDATE_CLIENTS);
                    }
                }
            }

            worldGenLevel.setBlock(blockPos, this.water, Block.UPDATE_CLIENTS);

            for (Direction direction : Direction.Plane.HORIZONTAL) {
                worldGenLevel.setBlock(blockPos.relative(direction), this.water, Block.UPDATE_CLIENTS);
            }

            BlockPos blockPos1 = blockPos.below();
            worldGenLevel.setBlock(blockPos1, this.sand, Block.UPDATE_CLIENTS);

            for (Direction direction1 : Direction.Plane.HORIZONTAL) {
                worldGenLevel.setBlock(blockPos1.relative(direction1), this.sand, Block.UPDATE_CLIENTS);
            }

            for (int i1x = -2; i1x <= 2; i1x++) {
                for (int i2 = -2; i2 <= 2; i2++) {
                    if (i1x == -2 || i1x == 2 || i2 == -2 || i2 == 2) {
                        worldGenLevel.setBlock(blockPos.offset(i1x, 1, i2), this.sandstone, Block.UPDATE_CLIENTS);
                    }
                }
            }

            worldGenLevel.setBlock(blockPos.offset(2, 1, 0), this.sandSlab, Block.UPDATE_CLIENTS);
            worldGenLevel.setBlock(blockPos.offset(-2, 1, 0), this.sandSlab, Block.UPDATE_CLIENTS);
            worldGenLevel.setBlock(blockPos.offset(0, 1, 2), this.sandSlab, Block.UPDATE_CLIENTS);
            worldGenLevel.setBlock(blockPos.offset(0, 1, -2), this.sandSlab, Block.UPDATE_CLIENTS);

            for (int i1x = -1; i1x <= 1; i1x++) {
                for (int i2x = -1; i2x <= 1; i2x++) {
                    if (i1x == 0 && i2x == 0) {
                        worldGenLevel.setBlock(blockPos.offset(i1x, 4, i2x), this.sandstone, Block.UPDATE_CLIENTS);
                    } else {
                        worldGenLevel.setBlock(blockPos.offset(i1x, 4, i2x), this.sandSlab, Block.UPDATE_CLIENTS);
                    }
                }
            }

            for (int i1x = 1; i1x <= 3; i1x++) {
                worldGenLevel.setBlock(blockPos.offset(-1, i1x, -1), this.sandstone, Block.UPDATE_CLIENTS);
                worldGenLevel.setBlock(blockPos.offset(-1, i1x, 1), this.sandstone, Block.UPDATE_CLIENTS);
                worldGenLevel.setBlock(blockPos.offset(1, i1x, -1), this.sandstone, Block.UPDATE_CLIENTS);
                worldGenLevel.setBlock(blockPos.offset(1, i1x, 1), this.sandstone, Block.UPDATE_CLIENTS);
            }

            List<BlockPos> list = List.of(blockPos, blockPos.east(), blockPos.south(), blockPos.west(), blockPos.north());
            RandomSource randomSource = context.random();
            placeSusSand(worldGenLevel, Util.getRandom(list, randomSource).below(1));
            placeSusSand(worldGenLevel, Util.getRandom(list, randomSource).below(2));
            return true;
        }
    }

    private static void placeSusSand(WorldGenLevel level, BlockPos pos) {
        level.setBlock(pos, Blocks.SUSPICIOUS_SAND.defaultBlockState(), Block.UPDATE_ALL);
        level.getBlockEntity(pos, BlockEntityType.BRUSHABLE_BLOCK)
            .ifPresent(brushableBlockEntity -> brushableBlockEntity.setLootTable(BuiltInLootTables.DESERT_WELL_ARCHAEOLOGY, pos.asLong()));
    }
}
