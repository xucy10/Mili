package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public class CoralMushroomFeature extends CoralFeature {
    public CoralMushroomFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    protected boolean placeFeature(LevelAccessor level, RandomSource random, BlockPos pos, BlockState state) {
        int i = random.nextInt(3) + 3;
        int i1 = random.nextInt(3) + 3;
        int i2 = random.nextInt(3) + 3;
        int i3 = random.nextInt(3) + 1;
        BlockPos.MutableBlockPos mutableBlockPos = pos.mutable();

        for (int i4 = 0; i4 <= i1; i4++) {
            for (int i5 = 0; i5 <= i; i5++) {
                for (int i6 = 0; i6 <= i2; i6++) {
                    mutableBlockPos.set(i4 + pos.getX(), i5 + pos.getY(), i6 + pos.getZ());
                    mutableBlockPos.move(Direction.DOWN, i3);
                    if ((i4 != 0 && i4 != i1 || i5 != 0 && i5 != i)
                        && (i6 != 0 && i6 != i2 || i5 != 0 && i5 != i)
                        && (i4 != 0 && i4 != i1 || i6 != 0 && i6 != i2)
                        && (i4 == 0 || i4 == i1 || i5 == 0 || i5 == i || i6 == 0 || i6 == i2)
                        && !(random.nextFloat() < 0.1F)
                        && !this.placeCoralBlock(level, random, mutableBlockPos, state)) {
                    }
                }
            }
        }

        return true;
    }
}
