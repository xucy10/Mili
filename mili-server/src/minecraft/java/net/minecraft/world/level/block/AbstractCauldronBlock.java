package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class AbstractCauldronBlock extends Block {
    protected static final int FLOOR_LEVEL = 4;
    private static final VoxelShape SHAPE_INSIDE = Block.column(12.0, 4.0, 16.0);
    protected static final VoxelShape SHAPE = Util.make(
        () -> {
            int i = 4;
            int i1 = 3;
            int i2 = 2;
            return Shapes.join(
                Shapes.block(),
                Shapes.or(Block.column(16.0, 8.0, 0.0, 3.0), Block.column(8.0, 16.0, 0.0, 3.0), Block.column(12.0, 0.0, 3.0), SHAPE_INSIDE),
                BooleanOp.ONLY_FIRST
            );
        }
    );
    protected final CauldronInteraction.InteractionMap interactions;

    @Override
    protected abstract MapCodec<? extends AbstractCauldronBlock> codec();

    public AbstractCauldronBlock(BlockBehaviour.Properties properties, CauldronInteraction.InteractionMap interactions) {
        super(properties);
        this.interactions = interactions;
    }

    protected double getContentHeight(BlockState state) {
        return 0.0;
    }

    @Override
    protected InteractionResult useItemOn(
        ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult
    ) {
        CauldronInteraction cauldronInteraction = this.interactions.map().get(stack.getItem());
        return cauldronInteraction.interact(state, level, pos, player, hand, stack, hitResult.getDirection()); // Paper - pass hit direction
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return SHAPE_INSIDE;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }

    public abstract boolean isFull(BlockState state);

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos blockPos = PointedDripstoneBlock.findStalactiteTipAboveCauldron(level, pos);
        if (blockPos != null) {
            Fluid cauldronFillFluidType = PointedDripstoneBlock.getCauldronFillFluidType(level, blockPos);
            if (cauldronFillFluidType != Fluids.EMPTY && this.canReceiveStalactiteDrip(cauldronFillFluidType)) {
                this.receiveStalactiteDrip(state, level, pos, cauldronFillFluidType);
            }
        }
    }

    protected boolean canReceiveStalactiteDrip(Fluid fluid) {
        return false;
    }

    protected void receiveStalactiteDrip(BlockState state, Level level, BlockPos pos, Fluid fluid) {
    }
}
