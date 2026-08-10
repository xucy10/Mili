package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;

public class SculkBlock extends DropExperienceBlock implements SculkBehaviour {
    public static final MapCodec<SculkBlock> CODEC = simpleCodec(SculkBlock::new);

    @Override
    public MapCodec<SculkBlock> codec() {
        return CODEC;
    }

    public SculkBlock(BlockBehaviour.Properties properties) {
        super(ConstantInt.of(1), properties);
    }

    @Override
    public int attemptUseCharge(
        SculkSpreader.ChargeCursor cursor, LevelAccessor level, BlockPos pos, RandomSource random, SculkSpreader spreader, boolean shouldConvertBlocks
    ) {
        int charge = cursor.getCharge();
        if (charge != 0 && random.nextInt(spreader.chargeDecayRate()) == 0) {
            BlockPos pos1 = cursor.getPos();
            boolean flag = pos1.closerThan(pos, spreader.noGrowthRadius());
            if (!flag && canPlaceGrowth(level, pos1)) {
                int growthSpawnCost = spreader.growthSpawnCost();
                if (random.nextInt(growthSpawnCost) < charge) {
                    BlockPos blockPos = pos1.above();
                    BlockState randomGrowthState = this.getRandomGrowthState(level, blockPos, random, spreader.isWorldGeneration());
                    if (org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockSpreadEvent(level, pos, blockPos, randomGrowthState, Block.UPDATE_ALL)) { // CraftBukkit - Call BlockSpreadEvent
                    level.playSound(null, pos1, randomGrowthState.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
                    } // CraftBukkit - Call BlockSpreadEvent
                }

                return Math.max(0, charge - growthSpawnCost);
            } else {
                return random.nextInt(spreader.additionalDecayRate()) != 0 ? charge : charge - (flag ? 1 : getDecayPenalty(spreader, pos1, pos, charge));
            }
        } else {
            return charge;
        }
    }

    private static int getDecayPenalty(SculkSpreader spreader, BlockPos cursorPos, BlockPos rootPos, int charge) {
        int noGrowthRadius = spreader.noGrowthRadius();
        float squared = Mth.square((float)Math.sqrt(cursorPos.distSqr(rootPos)) - noGrowthRadius);
        int squared1 = Mth.square(24 - noGrowthRadius);
        float min = Math.min(1.0F, squared / squared1);
        return Math.max(1, (int)(charge * min * 0.5F));
    }

    private BlockState getRandomGrowthState(LevelAccessor level, BlockPos pos, RandomSource random, boolean isWorldGeneration) {
        BlockState blockState;
        if (random.nextInt(11) == 0) {
            blockState = Blocks.SCULK_SHRIEKER.defaultBlockState().setValue(SculkShriekerBlock.CAN_SUMMON, isWorldGeneration);
        } else {
            blockState = Blocks.SCULK_SENSOR.defaultBlockState();
        }

        return blockState.hasProperty(BlockStateProperties.WATERLOGGED) && !level.getFluidState(pos).isEmpty()
            ? blockState.setValue(BlockStateProperties.WATERLOGGED, true)
            : blockState;
    }

    private static boolean canPlaceGrowth(LevelAccessor level, BlockPos pos) {
        BlockState blockState = level.getBlockState(pos.above());
        if (blockState.isAir() || blockState.is(Blocks.WATER) && blockState.getFluidState().is(Fluids.WATER)) {
            int i = 0;

            for (BlockPos blockPos : BlockPos.betweenClosed(pos.offset(-4, 0, -4), pos.offset(4, 2, 4))) {
                BlockState blockState1 = level.getBlockState(blockPos);
                if (blockState1.is(Blocks.SCULK_SENSOR) || blockState1.is(Blocks.SCULK_SHRIEKER)) {
                    i++;
                }

                if (i > 2) {
                    return false;
                }
            }

            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean canChangeBlockStateOnSpread() {
        return false;
    }
}
