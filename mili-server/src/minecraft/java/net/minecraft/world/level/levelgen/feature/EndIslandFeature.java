package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public class EndIslandFeature extends Feature<NoneFeatureConfiguration> {
    public EndIslandFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel worldGenLevel = context.level();
        RandomSource randomSource = context.random();
        BlockPos blockPos = context.origin();
        float f = randomSource.nextInt(3) + 4.0F;

        for (int i = 0; f > 0.5F; i--) {
            for (int floor = Mth.floor(-f); floor <= Mth.ceil(f); floor++) {
                for (int floor1 = Mth.floor(-f); floor1 <= Mth.ceil(f); floor1++) {
                    if (floor * floor + floor1 * floor1 <= (f + 1.0F) * (f + 1.0F)) {
                        this.setBlock(worldGenLevel, blockPos.offset(floor, i, floor1), Blocks.END_STONE.defaultBlockState());
                    }
                }
            }

            f -= randomSource.nextInt(2) + 0.5F;
        }

        return true;
    }
}
