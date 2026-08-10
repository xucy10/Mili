package net.minecraft.world.level.levelgen.feature;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public class EndPodiumFeature extends Feature<NoneFeatureConfiguration> {
    public static final int PODIUM_RADIUS = 4;
    public static final int PODIUM_PILLAR_HEIGHT = 4;
    public static final int RIM_RADIUS = 1;
    public static final float CORNER_ROUNDING = 0.5F;
    private static final BlockPos END_PODIUM_LOCATION = BlockPos.ZERO;
    private final boolean active;

    public static BlockPos getLocation(BlockPos pos) {
        return END_PODIUM_LOCATION.offset(pos);
    }

    public EndPodiumFeature(boolean active) {
        super(NoneFeatureConfiguration.CODEC);
        this.active = active;
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        BlockPos blockPos = context.origin();
        WorldGenLevel worldGenLevel = context.level();

        for (BlockPos blockPos1 : BlockPos.betweenClosed(
            new BlockPos(blockPos.getX() - 4, blockPos.getY() - 1, blockPos.getZ() - 4),
            new BlockPos(blockPos.getX() + 4, blockPos.getY() + 32, blockPos.getZ() + 4)
        )) {
            boolean flag = blockPos1.closerThan(blockPos, 2.5);
            if (flag || blockPos1.closerThan(blockPos, 3.5)) {
                if (blockPos1.getY() < blockPos.getY()) {
                    if (flag) {
                        this.setBlock(worldGenLevel, blockPos1, Blocks.BEDROCK.defaultBlockState());
                    } else if (blockPos1.getY() < blockPos.getY()) {
                        if (this.active) {
                            this.dropPreviousAndSetBlock(worldGenLevel, blockPos1, Blocks.END_STONE);
                        } else {
                            this.setBlock(worldGenLevel, blockPos1, Blocks.END_STONE.defaultBlockState());
                        }
                    }
                } else if (blockPos1.getY() > blockPos.getY()) {
                    if (this.active) {
                        this.dropPreviousAndSetBlock(worldGenLevel, blockPos1, Blocks.AIR);
                    } else {
                        this.setBlock(worldGenLevel, blockPos1, Blocks.AIR.defaultBlockState());
                    }
                } else if (!flag) {
                    this.setBlock(worldGenLevel, blockPos1, Blocks.BEDROCK.defaultBlockState());
                } else if (this.active) {
                    this.dropPreviousAndSetBlock(worldGenLevel, new BlockPos(blockPos1), Blocks.END_PORTAL);
                } else {
                    this.setBlock(worldGenLevel, new BlockPos(blockPos1), Blocks.AIR.defaultBlockState());
                }
            }
        }

        for (int i = 0; i < 4; i++) {
            this.setBlock(worldGenLevel, blockPos.above(i), Blocks.BEDROCK.defaultBlockState());
        }

        BlockPos blockPos2 = blockPos.above(2);

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            this.setBlock(worldGenLevel, blockPos2.relative(direction), Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, direction));
        }

        return true;
    }

    private void dropPreviousAndSetBlock(WorldGenLevel level, BlockPos pos, Block block) {
        if (!level.getBlockState(pos).is(block)) {
            level.destroyBlock(pos, true, null);
            this.setBlock(level, pos, block.defaultBlockState());
        }
    }
}
