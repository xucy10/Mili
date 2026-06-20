package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SculkBehaviour;
import net.minecraft.world.level.block.SculkShriekerBlock;
import net.minecraft.world.level.block.SculkSpreader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.SculkPatchConfiguration;

public class SculkPatchFeature extends Feature<SculkPatchConfiguration> {
    public SculkPatchFeature(Codec<SculkPatchConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<SculkPatchConfiguration> context) {
        WorldGenLevel worldGenLevel = context.level();
        BlockPos blockPos = context.origin();
        if (!this.canSpreadFrom(worldGenLevel, blockPos)) {
            return false;
        } else {
            SculkPatchConfiguration sculkPatchConfiguration = context.config();
            RandomSource randomSource = context.random();
            SculkSpreader sculkSpreader = SculkSpreader.createWorldGenSpreader();
            int i = sculkPatchConfiguration.spreadRounds() + sculkPatchConfiguration.growthRounds();

            for (int i1 = 0; i1 < i; i1++) {
                for (int i2 = 0; i2 < sculkPatchConfiguration.chargeCount(); i2++) {
                    sculkSpreader.addCursors(blockPos, sculkPatchConfiguration.amountPerCharge());
                }

                boolean flag = i1 < sculkPatchConfiguration.spreadRounds();

                for (int i3 = 0; i3 < sculkPatchConfiguration.spreadAttempts(); i3++) {
                    sculkSpreader.updateCursors(worldGenLevel, blockPos, randomSource, flag);
                }

                sculkSpreader.clear();
            }

            BlockPos blockPos1 = blockPos.below();
            if (randomSource.nextFloat() <= sculkPatchConfiguration.catalystChance()
                && worldGenLevel.getBlockState(blockPos1).isCollisionShapeFullBlock(worldGenLevel, blockPos1)) {
                worldGenLevel.setBlock(blockPos, Blocks.SCULK_CATALYST.defaultBlockState(), Block.UPDATE_ALL);
            }

            int i2 = sculkPatchConfiguration.extraRareGrowths().sample(randomSource);

            for (int i3 = 0; i3 < i2; i3++) {
                BlockPos blockPos2 = blockPos.offset(randomSource.nextInt(5) - 2, 0, randomSource.nextInt(5) - 2);
                if (worldGenLevel.getBlockState(blockPos2).isAir()
                    && worldGenLevel.getBlockState(blockPos2.below()).isFaceSturdy(worldGenLevel, blockPos2.below(), Direction.UP)) {
                    worldGenLevel.setBlock(blockPos2, Blocks.SCULK_SHRIEKER.defaultBlockState().setValue(SculkShriekerBlock.CAN_SUMMON, true), Block.UPDATE_ALL);
                }
            }

            return true;
        }
    }

    private boolean canSpreadFrom(LevelAccessor level, BlockPos pos) {
        BlockState blockState = level.getBlockState(pos);
        return blockState.getBlock() instanceof SculkBehaviour
            || (blockState.isAir() || blockState.is(Blocks.WATER) && blockState.getFluidState().isSource())
                && Direction.stream().map(pos::relative).anyMatch(blockPos -> level.getBlockState(blockPos).isCollisionShapeFullBlock(level, blockPos));
    }
}
