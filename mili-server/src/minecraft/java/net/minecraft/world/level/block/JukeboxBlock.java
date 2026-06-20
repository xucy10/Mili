package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxPlayable;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

public class JukeboxBlock extends BaseEntityBlock {
    public static final MapCodec<JukeboxBlock> CODEC = simpleCodec(JukeboxBlock::new);
    public static final BooleanProperty HAS_RECORD = BlockStateProperties.HAS_RECORD;

    @Override
    public MapCodec<JukeboxBlock> codec() {
        return CODEC;
    }

    protected JukeboxBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(HAS_RECORD, false));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        TypedEntityData<BlockEntityType<?>> typedEntityData = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (typedEntityData != null && typedEntityData.contains("RecordItem")) {
            level.setBlock(pos, state.setValue(HAS_RECORD, true), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (state.getValue(HAS_RECORD) && level.getBlockEntity(pos) instanceof JukeboxBlockEntity jukeboxBlockEntity) {
            jukeboxBlockEntity.popOutTheItem();
            return InteractionResult.SUCCESS;
        } else {
            return InteractionResult.PASS;
        }
    }

    @Override
    protected InteractionResult useItemOn(
        ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult
    ) {
        if (state.getValue(HAS_RECORD)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        } else {
            ItemStack itemInHand = player.getItemInHand(hand);
            InteractionResult interactionResult = JukeboxPlayable.tryInsertIntoJukebox(level, pos, itemInHand, player);
            return (InteractionResult)(!interactionResult.consumesAction() ? InteractionResult.TRY_WITH_EMPTY_HAND : interactionResult);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        Containers.updateNeighboursAfterDestroy(state, level, pos);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new JukeboxBlockEntity(pos, state);
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof JukeboxBlockEntity jukeboxBlockEntity && jukeboxBlockEntity.getSongPlayer().isPlaying() ? 15 : 0;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof JukeboxBlockEntity jukeboxBlockEntity ? jukeboxBlockEntity.getComparatorOutput() : 0;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HAS_RECORD);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return state.getValue(HAS_RECORD) ? createTickerHelper(blockEntityType, BlockEntityType.JUKEBOX, JukeboxBlockEntity::tick) : null;
    }
}
