package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

public class BonusChestFeature extends Feature<NoneFeatureConfiguration> {
    public BonusChestFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        RandomSource randomSource = context.random();
        WorldGenLevel worldGenLevel = context.level();
        ChunkPos chunkPos = new ChunkPos(context.origin());
        IntArrayList list = Util.toShuffledList(IntStream.rangeClosed(chunkPos.getMinBlockX(), chunkPos.getMaxBlockX()), randomSource);
        IntArrayList list1 = Util.toShuffledList(IntStream.rangeClosed(chunkPos.getMinBlockZ(), chunkPos.getMaxBlockZ()), randomSource);
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        for (Integer integer : list) {
            for (Integer integer1 : list1) {
                mutableBlockPos.set(integer, 0, integer1);
                BlockPos heightmapPos = worldGenLevel.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, mutableBlockPos);
                if (worldGenLevel.isEmptyBlock(heightmapPos)
                    || worldGenLevel.getBlockState(heightmapPos).getCollisionShape(worldGenLevel, heightmapPos).isEmpty()) {
                    worldGenLevel.setBlock(heightmapPos, Blocks.CHEST.defaultBlockState(), Block.UPDATE_CLIENTS);
                    RandomizableContainer.setBlockEntityLootTable(worldGenLevel, randomSource, heightmapPos, BuiltInLootTables.SPAWN_BONUS_CHEST);
                    BlockState blockState = Blocks.TORCH.defaultBlockState();

                    for (Direction direction : Direction.Plane.HORIZONTAL) {
                        BlockPos blockPos = heightmapPos.relative(direction);
                        if (blockState.canSurvive(worldGenLevel, blockPos)) {
                            worldGenLevel.setBlock(blockPos, blockState, Block.UPDATE_CLIENTS);
                        }
                    }

                    return true;
                }
            }
        }

        return false;
    }
}
