package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity;
import net.minecraft.world.level.levelgen.feature.configurations.EndGatewayConfiguration;

public class EndGatewayFeature extends Feature<EndGatewayConfiguration> {
    public EndGatewayFeature(Codec<EndGatewayConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<EndGatewayConfiguration> context) {
        BlockPos blockPos = context.origin();
        WorldGenLevel worldGenLevel = context.level();
        EndGatewayConfiguration endGatewayConfiguration = context.config();

        for (BlockPos blockPos1 : BlockPos.betweenClosed(blockPos.offset(-1, -2, -1), blockPos.offset(1, 2, 1))) {
            boolean flag = blockPos1.getX() == blockPos.getX();
            boolean flag1 = blockPos1.getY() == blockPos.getY();
            boolean flag2 = blockPos1.getZ() == blockPos.getZ();
            boolean flag3 = Math.abs(blockPos1.getY() - blockPos.getY()) == 2;
            if (flag && flag1 && flag2) {
                BlockPos blockPos2 = blockPos1.immutable();
                this.setBlock(worldGenLevel, blockPos2, Blocks.END_GATEWAY.defaultBlockState());
                endGatewayConfiguration.getExit().ifPresent(pos -> {
                    if (worldGenLevel.getBlockEntity(blockPos2) instanceof TheEndGatewayBlockEntity theEndGatewayBlockEntity) {
                        theEndGatewayBlockEntity.setExitPosition(pos, endGatewayConfiguration.isExitExact());
                    }
                });
            } else if (flag1) {
                this.setBlock(worldGenLevel, blockPos1, Blocks.AIR.defaultBlockState());
            } else if (flag3 && flag && flag2) {
                this.setBlock(worldGenLevel, blockPos1, Blocks.BEDROCK.defaultBlockState());
            } else if ((flag || flag2) && !flag3) {
                this.setBlock(worldGenLevel, blockPos1, Blocks.BEDROCK.defaultBlockState());
            } else {
                this.setBlock(worldGenLevel, blockPos1, Blocks.AIR.defaultBlockState());
            }
        }

        return true;
    }
}
