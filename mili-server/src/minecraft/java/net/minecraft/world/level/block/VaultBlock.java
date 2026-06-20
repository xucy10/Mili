package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.vault.VaultBlockEntity;
import net.minecraft.world.level.block.entity.vault.VaultState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

public class VaultBlock extends BaseEntityBlock {
    public static final MapCodec<VaultBlock> CODEC = simpleCodec(VaultBlock::new);
    public static final Property<VaultState> STATE = BlockStateProperties.VAULT_STATE;
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty OMINOUS = BlockStateProperties.OMINOUS;

    @Override
    public MapCodec<VaultBlock> codec() {
        return CODEC;
    }

    public VaultBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(STATE, VaultState.INACTIVE).setValue(OMINOUS, false));
    }

    @Override
    public InteractionResult useItemOn(
        ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult
    ) {
        if (!stack.isEmpty() && state.getValue(STATE) == VaultState.ACTIVE) {
            if (level instanceof ServerLevel serverLevel) {
                if (!(serverLevel.getBlockEntity(pos) instanceof VaultBlockEntity vaultBlockEntity)) {
                    return InteractionResult.TRY_WITH_EMPTY_HAND;
                }

                VaultBlockEntity.Server.tryInsertKey(
                    serverLevel, pos, state, vaultBlockEntity.getConfig(), vaultBlockEntity.getServerData(), vaultBlockEntity.getSharedData(), player, stack
                );
            }

            return InteractionResult.SUCCESS_SERVER;
        } else {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VaultBlockEntity(pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, STATE, OMINOUS);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return level instanceof ServerLevel serverLevel
            ? createTickerHelper(
                blockEntityType,
                BlockEntityType.VAULT,
                (level1, pos, state1, blockEntity) -> VaultBlockEntity.Server.tick(
                    serverLevel, pos, state1, blockEntity.getConfig(), blockEntity.getServerData(), blockEntity.getSharedData()
                )
            )
            : createTickerHelper(
                blockEntityType,
                BlockEntityType.VAULT,
                (level1, pos, state1, blockEntity) -> VaultBlockEntity.Client.tick(
                    level1, pos, state1, blockEntity.getClientData(), blockEntity.getSharedData()
                )
            );
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
