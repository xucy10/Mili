package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.TestBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.TestBlockMode;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

public class TestBlock extends BaseEntityBlock implements GameMasterBlock {
    public static final MapCodec<TestBlock> CODEC = simpleCodec(TestBlock::new);
    public static final EnumProperty<TestBlockMode> MODE = BlockStateProperties.TEST_BLOCK_MODE;

    public TestBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TestBlockEntity(pos, state);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockItemStateProperties blockItemStateProperties = context.getItemInHand().get(DataComponents.BLOCK_STATE);
        BlockState blockState = this.defaultBlockState();
        if (blockItemStateProperties != null) {
            TestBlockMode testBlockMode = blockItemStateProperties.get(MODE);
            if (testBlockMode != null) {
                blockState = blockState.setValue(MODE, testBlockMode);
            }
        }

        return blockState;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MODE);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof TestBlockEntity testBlockEntity) {
            if (!player.canUseGameMasterBlocks()) {
                return InteractionResult.PASS;
            } else {
                if (level.isClientSide()) {
                    player.openTestBlock(testBlockEntity);
                }

                return InteractionResult.SUCCESS;
            }
        } else {
            return InteractionResult.PASS;
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        TestBlockEntity serverTestBlockEntity = getServerTestBlockEntity(level, pos);
        if (serverTestBlockEntity != null) {
            serverTestBlockEntity.reset();
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, @Nullable Orientation orientation, boolean movedByPiston) {
        TestBlockEntity serverTestBlockEntity = getServerTestBlockEntity(level, pos);
        if (serverTestBlockEntity != null) {
            if (serverTestBlockEntity.getMode() != TestBlockMode.START) {
                boolean hasNeighborSignal = level.hasNeighborSignal(pos);
                boolean isPowered = serverTestBlockEntity.isPowered();
                if (hasNeighborSignal && !isPowered) {
                    serverTestBlockEntity.setPowered(true);
                    serverTestBlockEntity.trigger();
                } else if (!hasNeighborSignal && isPowered) {
                    serverTestBlockEntity.setPowered(false);
                }
            }
        }
    }

    private static @Nullable TestBlockEntity getServerTestBlockEntity(Level level, BlockPos pos) {
        return level instanceof ServerLevel serverLevel && serverLevel.getBlockEntity(pos) instanceof TestBlockEntity testBlockEntity ? testBlockEntity : null;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        if (state.getValue(MODE) != TestBlockMode.START) {
            return 0;
        } else if (level.getBlockEntity(pos) instanceof TestBlockEntity testBlockEntity) {
            return testBlockEntity.isPowered() ? 15 : 0;
        } else {
            return 0;
        }
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        ItemStack itemStack = super.getCloneItemStack(level, pos, state, includeData);
        return setModeOnStack(itemStack, state.getValue(MODE));
    }

    public static ItemStack setModeOnStack(ItemStack stack, TestBlockMode mode) {
        stack.set(DataComponents.BLOCK_STATE, stack.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY).with(MODE, mode));
        return stack;
    }

    @Override
    protected MapCodec<TestBlock> codec() {
        return CODEC;
    }
}
