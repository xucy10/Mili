package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class BubbleColumnBlock extends Block implements BucketPickup {
    public static final MapCodec<BubbleColumnBlock> CODEC = simpleCodec(BubbleColumnBlock::new);
    public static final BooleanProperty DRAG_DOWN = BlockStateProperties.DRAG;
    private static final int CHECK_PERIOD = 5;

    @Override
    public MapCodec<BubbleColumnBlock> codec() {
        return CODEC;
    }

    public BubbleColumnBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(DRAG_DOWN, true));
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean pastEdges) {
        if (!new io.papermc.paper.event.entity.EntityInsideBlockEvent(entity.getBukkitEntity(), org.bukkit.craftbukkit.block.CraftBlock.at(level, pos)).callEvent()) { return; } // Paper - Add EntityInsideBlockEvent
        if (pastEdges) {
            BlockState blockState = level.getBlockState(pos.above());
            boolean flag = blockState.getCollisionShape(level, pos).isEmpty() && blockState.getFluidState().isEmpty();
            if (flag) {
                entity.onAboveBubbleColumn(state.getValue(DRAG_DOWN), pos);
            } else {
                entity.onInsideBubbleColumn(state.getValue(DRAG_DOWN));
            }
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        updateColumn(level, pos, state, level.getBlockState(pos.below()));
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return Fluids.WATER.getSource(false);
    }

    public static void updateColumn(LevelAccessor level, BlockPos pos, BlockState state) {
        updateColumn(level, pos, level.getBlockState(pos), state);
    }

    public static void updateColumn(LevelAccessor level, BlockPos pos, BlockState fluid, BlockState state) {
        if (canExistIn(fluid)) {
            BlockState columnState = getColumnState(state);
            level.setBlock(pos, columnState, Block.UPDATE_CLIENTS);
            BlockPos.MutableBlockPos mutableBlockPos = pos.mutable().move(Direction.UP);

            while (canExistIn(level.getBlockState(mutableBlockPos))) {
                if (!level.setBlock(mutableBlockPos, columnState, Block.UPDATE_CLIENTS)) {
                    return;
                }

                mutableBlockPos.move(Direction.UP);
            }
        }
    }

    private static boolean canExistIn(BlockState state) {
        return state.is(Blocks.BUBBLE_COLUMN) || state.is(Blocks.WATER) && state.getFluidState().getAmount() >= 8 && state.getFluidState().isSource();
    }

    private static BlockState getColumnState(BlockState state) {
        if (state.is(Blocks.BUBBLE_COLUMN)) {
            return state;
        } else if (state.is(Blocks.SOUL_SAND)) {
            return Blocks.BUBBLE_COLUMN.defaultBlockState().setValue(DRAG_DOWN, false);
        } else {
            return state.is(Blocks.MAGMA_BLOCK) ? Blocks.BUBBLE_COLUMN.defaultBlockState().setValue(DRAG_DOWN, true) : Blocks.WATER.defaultBlockState();
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double d = pos.getX();
        double d1 = pos.getY();
        double d2 = pos.getZ();
        if (state.getValue(DRAG_DOWN)) {
            level.addAlwaysVisibleParticle(ParticleTypes.CURRENT_DOWN, d + 0.5, d1 + 0.8, d2, 0.0, 0.0, 0.0);
            if (random.nextInt(200) == 0) {
                level.playLocalSound(
                    d,
                    d1,
                    d2,
                    SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_AMBIENT,
                    SoundSource.BLOCKS,
                    0.2F + random.nextFloat() * 0.2F,
                    0.9F + random.nextFloat() * 0.15F,
                    false
                );
            }
        } else {
            level.addAlwaysVisibleParticle(ParticleTypes.BUBBLE_COLUMN_UP, d + 0.5, d1, d2 + 0.5, 0.0, 0.04, 0.0);
            level.addAlwaysVisibleParticle(
                ParticleTypes.BUBBLE_COLUMN_UP, d + random.nextFloat(), d1 + random.nextFloat(), d2 + random.nextFloat(), 0.0, 0.04, 0.0
            );
            if (random.nextInt(200) == 0) {
                level.playLocalSound(
                    d,
                    d1,
                    d2,
                    SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT,
                    SoundSource.BLOCKS,
                    0.2F + random.nextFloat() * 0.2F,
                    0.9F + random.nextFloat() * 0.15F,
                    false
                );
            }
        }
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
        scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        if (!state.canSurvive(level, pos)
            || direction == Direction.DOWN
            || direction == Direction.UP && !neighborState.is(Blocks.BUBBLE_COLUMN) && canExistIn(neighborState)) {
            scheduledTickAccess.scheduleTick(pos, this, 5);
        }

        return super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState blockState = level.getBlockState(pos.below());
        return blockState.is(Blocks.BUBBLE_COLUMN) || blockState.is(Blocks.MAGMA_BLOCK) || blockState.is(Blocks.SOUL_SAND);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DRAG_DOWN);
    }

    @Override
    public ItemStack pickupBlock(@Nullable LivingEntity owner, LevelAccessor level, BlockPos pos, BlockState state) {
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL_IMMEDIATE);
        return new ItemStack(Items.WATER_BUCKET);
    }

    @Override
    public Optional<SoundEvent> getPickupSound() {
        return Fluids.WATER.getPickupSound();
    }
}
