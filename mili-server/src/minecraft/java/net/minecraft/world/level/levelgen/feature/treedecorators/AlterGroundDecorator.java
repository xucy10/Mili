package net.minecraft.world.level.levelgen.feature.treedecorators;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

public class AlterGroundDecorator extends TreeDecorator {
    public static final MapCodec<AlterGroundDecorator> CODEC = BlockStateProvider.CODEC
        .fieldOf("provider")
        .xmap(AlterGroundDecorator::new, decorator -> decorator.provider);
    private final BlockStateProvider provider;

    public AlterGroundDecorator(BlockStateProvider provider) {
        this.provider = provider;
    }

    @Override
    protected TreeDecoratorType<?> type() {
        return TreeDecoratorType.ALTER_GROUND;
    }

    @Override
    public void place(TreeDecorator.Context context) {
        List<BlockPos> lowestTrunkOrRootOfTree = TreeFeature.getLowestTrunkOrRootOfTree(context);
        if (!lowestTrunkOrRootOfTree.isEmpty()) {
            int y = lowestTrunkOrRootOfTree.get(0).getY();
            lowestTrunkOrRootOfTree.stream().filter(pos -> pos.getY() == y).forEach(blockPos -> {
                this.placeCircle(context, blockPos.west().north());
                this.placeCircle(context, blockPos.east(2).north());
                this.placeCircle(context, blockPos.west().south(2));
                this.placeCircle(context, blockPos.east(2).south(2));

                for (int i = 0; i < 5; i++) {
                    int randomInt = context.random().nextInt(64);
                    int i1 = randomInt % 8;
                    int i2 = randomInt / 8;
                    if (i1 == 0 || i1 == 7 || i2 == 0 || i2 == 7) {
                        this.placeCircle(context, blockPos.offset(-3 + i1, 0, -3 + i2));
                    }
                }
            });
        }
    }

    private void placeCircle(TreeDecorator.Context context, BlockPos pos) {
        for (int i = -2; i <= 2; i++) {
            for (int i1 = -2; i1 <= 2; i1++) {
                if (Math.abs(i) != 2 || Math.abs(i1) != 2) {
                    this.placeBlockAt(context, pos.offset(i, 0, i1));
                }
            }
        }
    }

    private void placeBlockAt(TreeDecorator.Context context, BlockPos pos) {
        for (int i = 2; i >= -3; i--) {
            BlockPos blockPos = pos.above(i);
            if (Feature.isGrassOrDirt(context.level(), blockPos)) {
                context.setBlock(blockPos, this.provider.getState(context.random(), pos));
                break;
            }

            if (!context.isAir(blockPos) && i < 0) {
                break;
            }
        }
    }
}
