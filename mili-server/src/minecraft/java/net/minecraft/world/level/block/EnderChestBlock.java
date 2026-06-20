package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class EnderChestBlock extends AbstractChestBlock<EnderChestBlockEntity> implements SimpleWaterloggedBlock {
    public static final MapCodec<EnderChestBlock> CODEC = simpleCodec(EnderChestBlock::new);
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final VoxelShape SHAPE = Block.column(14.0, 0.0, 14.0);
    private static final Component CONTAINER_TITLE = Component.translatable("container.enderchest");

    @Override
    public MapCodec<EnderChestBlock> codec() {
        return CODEC;
    }

    protected EnderChestBlock(BlockBehaviour.Properties properties) {
        super(properties, () -> BlockEntityType.ENDER_CHEST);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(WATERLOGGED, false));
    }

    @Override
    public DoubleBlockCombiner.NeighborCombineResult<? extends ChestBlockEntity> combine(BlockState state, Level level, BlockPos pos, boolean override) {
        return DoubleBlockCombiner.Combiner::acceptNone;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        FluidState fluidState = context.getLevel().getFluidState(context.getClickedPos());
        return this.defaultBlockState()
            .setValue(FACING, context.getHorizontalDirection().getOpposite())
            .setValue(WATERLOGGED, fluidState.getType() == Fluids.WATER);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        PlayerEnderChestContainer enderChestInventory = player.getEnderChestInventory();
        if (enderChestInventory != null && level.getBlockEntity(pos) instanceof EnderChestBlockEntity enderChestBlockEntity) {
            BlockPos blockPos = pos.above();
            if (level.getBlockState(blockPos).isRedstoneConductor(level, blockPos)) { // Paper - diff on change; make sure that EnderChest#isBlocked uses the same logic
                return InteractionResult.SUCCESS;
            } else {
                // Paper start - Fix InventoryOpenEvent cancellation - moved up;
                enderChestInventory.setActiveChest(enderChestBlockEntity); // Needs to happen before ChestMenu.threeRows as it is required for opening animations
                if (level instanceof ServerLevel serverLevel && player.openMenu(
                    new SimpleMenuProvider(
                        (containerId, playerInventory, player1) -> ChestMenu.threeRows(containerId, playerInventory, enderChestInventory), CONTAINER_TITLE
                    )
                ).isPresent()) {
                // Paper end - Fix InventoryOpenEvent cancellation - moved up;
                    player.awardStat(Stats.OPEN_ENDERCHEST);
                    PiglinAi.angerNearbyPiglins(serverLevel, player, true);
                }

                return InteractionResult.SUCCESS;
            }
        } else {
            return InteractionResult.SUCCESS;
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EnderChestBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return level.isClientSide() ? createTickerHelper(blockEntityType, BlockEntityType.ENDER_CHEST, EnderChestBlockEntity::lidAnimateTick) : null;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        for (int i = 0; i < 3; i++) {
            int i1 = random.nextInt(2) * 2 - 1;
            int i2 = random.nextInt(2) * 2 - 1;
            double d = pos.getX() + 0.5 + 0.25 * i1;
            double d1 = pos.getY() + random.nextFloat();
            double d2 = pos.getZ() + 0.5 + 0.25 * i2;
            double d3 = random.nextFloat() * i1;
            double d4 = (random.nextFloat() - 0.5) * 0.125;
            double d5 = random.nextFloat() * i2;
            level.addParticle(ParticleTypes.PORTAL, d, d1, d2, d3, d4, d5);
        }
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WATERLOGGED);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected BlockState updateShape(
        BlockState state,
        LevelReader level,
        ScheduledTickAccess scheduledTickAccess,
        BlockPos pos,
        Direction direction,
        BlockPos neighborPos,
        BlockState neighborState,
        RandomSource random
    ) {
        if (state.getValue(WATERLOGGED)) {
            scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }

        return super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof EnderChestBlockEntity) {
            ((EnderChestBlockEntity)blockEntity).recheckOpen();
        }
    }
}
