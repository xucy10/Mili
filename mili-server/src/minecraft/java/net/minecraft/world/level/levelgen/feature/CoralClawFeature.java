package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public class CoralClawFeature extends CoralFeature {
    public CoralClawFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    protected boolean placeFeature(LevelAccessor level, RandomSource random, BlockPos pos, BlockState state) {
        if (!this.placeCoralBlock(level, random, pos, state)) {
            return false;
        } else {
            Direction randomDirection = Direction.Plane.HORIZONTAL.getRandomDirection(random);
            int i = random.nextInt(2) + 2;
            List<Direction> list = Util.toShuffledList(
                Stream.of(randomDirection, randomDirection.getClockWise(), randomDirection.getCounterClockWise()), random
            );

            for (Direction direction : list.subList(0, i)) {
                BlockPos.MutableBlockPos mutableBlockPos = pos.mutable();
                int i1 = random.nextInt(2) + 1;
                mutableBlockPos.move(direction);
                int i2;
                Direction direction1;
                if (direction == randomDirection) {
                    direction1 = randomDirection;
                    i2 = random.nextInt(3) + 2;
                } else {
                    mutableBlockPos.move(Direction.UP);
                    Direction[] directions = new Direction[]{direction, Direction.UP};
                    direction1 = Util.getRandom(directions, random);
                    i2 = random.nextInt(3) + 3;
                }

                for (int i3 = 0; i3 < i1 && this.placeCoralBlock(level, random, mutableBlockPos, state); i3++) {
                    mutableBlockPos.move(direction1);
                }

                mutableBlockPos.move(direction1.getOpposite());
                mutableBlockPos.move(Direction.UP);

                for (int i3 = 0; i3 < i2; i3++) {
                    mutableBlockPos.move(randomDirection);
                    if (!this.placeCoralBlock(level, random, mutableBlockPos, state)) {
                        break;
                    }

                    if (random.nextFloat() < 0.25F) {
                        mutableBlockPos.move(Direction.UP);
                    }
                }
            }

            return true;
        }
    }
}
