package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.configurations.RandomPatchConfiguration;

public class RandomPatchFeature extends Feature<RandomPatchConfiguration> {
    public RandomPatchFeature(Codec<RandomPatchConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<RandomPatchConfiguration> context) {
        RandomPatchConfiguration randomPatchConfiguration = context.config();
        RandomSource randomSource = context.random();
        BlockPos blockPos = context.origin();
        WorldGenLevel worldGenLevel = context.level();
        int i = 0;
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        int i1 = randomPatchConfiguration.xzSpread() + 1;
        int i2 = randomPatchConfiguration.ySpread() + 1;

        for (int i3 = 0; i3 < randomPatchConfiguration.tries(); i3++) {
            mutableBlockPos.setWithOffset(
                blockPos,
                randomSource.nextInt(i1) - randomSource.nextInt(i1),
                randomSource.nextInt(i2) - randomSource.nextInt(i2),
                randomSource.nextInt(i1) - randomSource.nextInt(i1)
            );
            if (randomPatchConfiguration.feature().value().place(worldGenLevel, context.chunkGenerator(), randomSource, mutableBlockPos)) {
                i++;
            }
        }

        return i > 0;
    }
}
