package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.DiskConfiguration;

public class DiskFeature extends Feature<DiskConfiguration> {
    public DiskFeature(Codec<DiskConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<DiskConfiguration> context) {
        DiskConfiguration diskConfiguration = context.config();
        BlockPos blockPos = context.origin();
        WorldGenLevel worldGenLevel = context.level();
        RandomSource randomSource = context.random();
        boolean flag = false;
        int y = blockPos.getY();
        int i = y + diskConfiguration.halfHeight();
        int i1 = y - diskConfiguration.halfHeight() - 1;
        int i2 = diskConfiguration.radius().sample(randomSource);
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        for (BlockPos blockPos1 : BlockPos.betweenClosed(blockPos.offset(-i2, 0, -i2), blockPos.offset(i2, 0, i2))) {
            int i3 = blockPos1.getX() - blockPos.getX();
            int i4 = blockPos1.getZ() - blockPos.getZ();
            if (i3 * i3 + i4 * i4 <= i2 * i2) {
                flag |= this.placeColumn(diskConfiguration, worldGenLevel, randomSource, i, i1, mutableBlockPos.set(blockPos1));
            }
        }

        return flag;
    }

    protected boolean placeColumn(DiskConfiguration config, WorldGenLevel level, RandomSource random, int maxY, int minY, BlockPos.MutableBlockPos pos) {
        boolean flag = false;
        boolean flag1 = false;

        for (int i = maxY; i > minY; i--) {
            pos.setY(i);
            if (config.target().test(level, pos)) {
                BlockState state = config.stateProvider().getState(level, random, pos);
                level.setBlock(pos, state, Block.UPDATE_CLIENTS);
                if (!flag1) {
                    this.markAboveForPostProcessing(level, pos);
                }

                flag = true;
                flag1 = true;
            } else {
                flag1 = false;
            }
        }

        return flag;
    }
}
