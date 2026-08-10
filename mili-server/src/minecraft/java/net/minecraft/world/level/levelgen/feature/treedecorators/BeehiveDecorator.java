package net.minecraft.world.level.levelgen.feature.treedecorators;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class BeehiveDecorator extends TreeDecorator {
    public static final MapCodec<BeehiveDecorator> CODEC = Codec.floatRange(0.0F, 1.0F)
        .fieldOf("probability")
        .xmap(BeehiveDecorator::new, decorator -> decorator.probability);
    private static final Direction WORLDGEN_FACING = Direction.SOUTH;
    private static final Direction[] SPAWN_DIRECTIONS = Direction.Plane.HORIZONTAL
        .stream()
        .filter(direction -> direction != WORLDGEN_FACING.getOpposite())
        .toArray(Direction[]::new);
    private final float probability;

    public BeehiveDecorator(float probability) {
        this.probability = probability;
    }

    @Override
    protected TreeDecoratorType<?> type() {
        return TreeDecoratorType.BEEHIVE;
    }

    @Override
    public void place(TreeDecorator.Context context) {
        List<BlockPos> list = context.leaves();
        List<BlockPos> list1 = context.logs();
        if (!list1.isEmpty()) {
            RandomSource randomSource = context.random();
            if (!(randomSource.nextFloat() >= this.probability)) {
                int i = !list.isEmpty()
                    ? Math.max(list.getFirst().getY() - 1, list1.getFirst().getY() + 1)
                    : Math.min(list1.getFirst().getY() + 1 + randomSource.nextInt(3), list1.getLast().getY());
                List<BlockPos> list2 = list1.stream()
                    .filter(blockPos -> blockPos.getY() == i)
                    .flatMap(blockPos -> Stream.of(SPAWN_DIRECTIONS).map(blockPos::relative))
                    .collect(Collectors.toList());
                if (!list2.isEmpty()) {
                    Util.shuffle(list2, randomSource);
                    Optional<BlockPos> optional = list2.stream()
                        .filter(blockPos -> context.isAir(blockPos) && context.isAir(blockPos.relative(WORLDGEN_FACING)))
                        .findFirst();
                    if (!optional.isEmpty()) {
                        context.setBlock(optional.get(), Blocks.BEE_NEST.defaultBlockState().setValue(BeehiveBlock.FACING, WORLDGEN_FACING));
                        context.level().getBlockEntity(optional.get(), BlockEntityType.BEEHIVE).ifPresent(beehiveBlockEntity -> {
                            int i1 = 2 + randomSource.nextInt(2);

                            for (int i2 = 0; i2 < i1; i2++) {
                                beehiveBlockEntity.storeBee(BeehiveBlockEntity.Occupant.create(randomSource.nextInt(599)));
                            }
                        });
                    }
                }
            }
        }
    }
}
