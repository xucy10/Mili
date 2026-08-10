package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.FrontAndTop;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.JigsawBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.BlockHitResult;

public class JigsawBlock extends Block implements EntityBlock, GameMasterBlock {
    public static final MapCodec<JigsawBlock> CODEC = simpleCodec(JigsawBlock::new);
    public static final EnumProperty<FrontAndTop> ORIENTATION = BlockStateProperties.ORIENTATION;

    @Override
    public MapCodec<JigsawBlock> codec() {
        return CODEC;
    }

    protected JigsawBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(ORIENTATION, FrontAndTop.NORTH_UP));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ORIENTATION);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(ORIENTATION, rotation.rotation().rotate(state.getValue(ORIENTATION)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(ORIENTATION, mirror.rotation().rotate(state.getValue(ORIENTATION)));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clickedFace = context.getClickedFace();
        Direction opposite;
        if (clickedFace.getAxis() == Direction.Axis.Y) {
            opposite = context.getHorizontalDirection().getOpposite();
        } else {
            opposite = Direction.UP;
        }

        return this.defaultBlockState().setValue(ORIENTATION, FrontAndTop.fromFrontAndTop(clickedFace, opposite));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new JigsawBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof JigsawBlockEntity && player.canUseGameMasterBlocks()) {
            player.openJigsawBlock((JigsawBlockEntity)blockEntity);
            return InteractionResult.SUCCESS;
        } else {
            return InteractionResult.PASS;
        }
    }

    public static boolean canAttach(StructureTemplate.JigsawBlockInfo parent, StructureTemplate.JigsawBlockInfo child) {
        Direction frontFacing = getFrontFacing(parent.info().state());
        Direction frontFacing1 = getFrontFacing(child.info().state());
        Direction topFacing = getTopFacing(parent.info().state());
        Direction topFacing1 = getTopFacing(child.info().state());
        JigsawBlockEntity.JointType jointType = parent.jointType();
        boolean flag = jointType == JigsawBlockEntity.JointType.ROLLABLE;
        return frontFacing == frontFacing1.getOpposite() && (flag || topFacing == topFacing1) && parent.target().equals(child.name());
    }

    public static Direction getFrontFacing(BlockState state) {
        return state.getValue(ORIENTATION).front();
    }

    public static Direction getTopFacing(BlockState state) {
        return state.getValue(ORIENTATION).top();
    }
}
