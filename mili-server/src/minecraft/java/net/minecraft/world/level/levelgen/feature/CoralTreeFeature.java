package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public class CoralTreeFeature extends CoralFeature {
    public CoralTreeFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    protected boolean placeFeature(LevelAccessor level, RandomSource random, BlockPos pos, BlockState state) {
        BlockPos.MutableBlockPos mutableBlockPos = pos.mutable();
        int i = random.nextInt(3) + 1;

        for (int i1 = 0; i1 < i; i1++) {
            if (!this.placeCoralBlock(level, random, mutableBlockPos, state)) {
                return true;
            }

            mutableBlockPos.move(Direction.UP);
        }

        BlockPos blockPos = mutableBlockPos.immutable();
        int i2 = random.nextInt(3) + 2;
        List<Direction> list = Direction.Plane.HORIZONTAL.shuffledCopy(random);

        for (Direction direction : list.subList(0, i2)) {
            mutableBlockPos.set(blockPos);
            mutableBlockPos.move(direction);
            int i3 = random.nextInt(5) + 2;
            int i4 = 0;

            for (int i5 = 0; i5 < i3 && this.placeCoralBlock(level, random, mutableBlockPos, state); i5++) {
                i4++;
                mutableBlockPos.move(Direction.UP);
                if (i5 == 0 || i4 >= 2 && random.nextFloat() < 0.25F) {
                    mutableBlockPos.move(direction);
                    i4 = 0;
                }
            }
        }

        return true;
    }
}
