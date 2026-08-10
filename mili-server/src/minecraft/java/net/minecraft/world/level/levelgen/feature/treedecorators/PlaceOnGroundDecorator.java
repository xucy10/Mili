package net.minecraft.world.level.levelgen.feature.treedecorators;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public class PlaceOnGroundDecorator extends TreeDecorator {
    public static final MapCodec<PlaceOnGroundDecorator> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                ExtraCodecs.POSITIVE_INT.fieldOf("tries").orElse(128).forGetter(decorator -> decorator.tries),
                ExtraCodecs.NON_NEGATIVE_INT.fieldOf("radius").orElse(2).forGetter(decorator -> decorator.radius),
                ExtraCodecs.NON_NEGATIVE_INT.fieldOf("height").orElse(1).forGetter(decorator -> decorator.height),
                BlockStateProvider.CODEC.fieldOf("block_state_provider").forGetter(decorator -> decorator.blockStateProvider)
            )
            .apply(instance, PlaceOnGroundDecorator::new)
    );
    private final int tries;
    private final int radius;
    private final int height;
    private final BlockStateProvider blockStateProvider;

    public PlaceOnGroundDecorator(int tries, int radius, int height, BlockStateProvider blockStateProvider) {
        this.tries = tries;
        this.radius = radius;
        this.height = height;
        this.blockStateProvider = blockStateProvider;
    }

    @Override
    protected TreeDecoratorType<?> type() {
        return TreeDecoratorType.PLACE_ON_GROUND;
    }

    @Override
    public void place(TreeDecorator.Context context) {
        List<BlockPos> lowestTrunkOrRootOfTree = TreeFeature.getLowestTrunkOrRootOfTree(context);
        if (!lowestTrunkOrRootOfTree.isEmpty()) {
            BlockPos blockPos = lowestTrunkOrRootOfTree.getFirst();
            int y = blockPos.getY();
            int x = blockPos.getX();
            int x1 = blockPos.getX();
            int z = blockPos.getZ();
            int z1 = blockPos.getZ();

            for (BlockPos blockPos1 : lowestTrunkOrRootOfTree) {
                if (blockPos1.getY() == y) {
                    x = Math.min(x, blockPos1.getX());
                    x1 = Math.max(x1, blockPos1.getX());
                    z = Math.min(z, blockPos1.getZ());
                    z1 = Math.max(z1, blockPos1.getZ());
                }
            }

            RandomSource randomSource = context.random();
            BoundingBox boundingBox = new BoundingBox(x, y, z, x1, y, z1).inflatedBy(this.radius, this.height, this.radius);
            BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

            for (int i = 0; i < this.tries; i++) {
                mutableBlockPos.set(
                    randomSource.nextIntBetweenInclusive(boundingBox.minX(), boundingBox.maxX()),
                    randomSource.nextIntBetweenInclusive(boundingBox.minY(), boundingBox.maxY()),
                    randomSource.nextIntBetweenInclusive(boundingBox.minZ(), boundingBox.maxZ())
                );
                this.attemptToPlaceBlockAbove(context, mutableBlockPos);
            }
        }
    }

    private void attemptToPlaceBlockAbove(TreeDecorator.Context context, BlockPos pos) {
        BlockPos blockPos = pos.above();
        if (context.level().isStateAtPosition(blockPos, blockState -> blockState.isAir() || blockState.is(Blocks.VINE))
            && context.checkBlock(pos, BlockBehaviour.BlockStateBase::isSolidRender)
            && context.level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos).getY() <= blockPos.getY()) {
            context.setBlock(blockPos, this.blockStateProvider.getState(context.random(), blockPos));
        }
    }
}
