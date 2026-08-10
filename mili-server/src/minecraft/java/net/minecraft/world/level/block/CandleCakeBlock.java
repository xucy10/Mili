package net.minecraft.world.level.block;

import com.google.common.collect.Maps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CandleCakeBlock extends AbstractCandleBlock {
    public static final MapCodec<CandleCakeBlock> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                BuiltInRegistries.BLOCK.byNameCodec().fieldOf("candle").forGetter(candleCakeBlock -> candleCakeBlock.candleBlock), propertiesCodec()
            )
            .apply(instance, CandleCakeBlock::new)
    );
    public static final BooleanProperty LIT = AbstractCandleBlock.LIT;
    private static final VoxelShape SHAPE = Shapes.or(Block.column(2.0, 8.0, 14.0), Block.column(14.0, 0.0, 8.0));
    private static final Map<CandleBlock, CandleCakeBlock> BY_CANDLE = Maps.newHashMap();
    private static final Iterable<Vec3> PARTICLE_OFFSETS = List.of(new Vec3(8.0, 16.0, 8.0).scale(0.0625));
    private final CandleBlock candleBlock;

    @Override
    public MapCodec<CandleCakeBlock> codec() {
        return CODEC;
    }

    protected CandleCakeBlock(Block candleBlock, BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(LIT, false));
        if (candleBlock instanceof CandleBlock candleBlock1) {
            BY_CANDLE.put(candleBlock1, this);
            this.candleBlock = candleBlock1;
        } else {
            throw new IllegalArgumentException("Expected block to be of " + CandleBlock.class + " was " + candleBlock.getClass());
        }
    }

    @Override
    protected Iterable<Vec3> getParticleOffsets(BlockState state) {
        return PARTICLE_OFFSETS;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useItemOn(
        ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult
    ) {
        if (stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE)) {
            return InteractionResult.PASS;
        } else if (candleHit(hitResult) && stack.isEmpty() && state.getValue(LIT)) {
            extinguish(player, state, level, pos);
            return InteractionResult.SUCCESS;
        } else {
            return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        InteractionResult interactionResult = CakeBlock.eat(level, pos, Blocks.CAKE.defaultBlockState(), player);
        if (interactionResult.consumesAction()) {
            dropResources(state, level, pos);
        }

        return interactionResult;
    }

    private static boolean candleHit(BlockHitResult hit) {
        return hit.getLocation().y - hit.getBlockPos().getY() > 0.5;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return new ItemStack(Blocks.CAKE);
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
        return direction == Direction.DOWN && !state.canSurvive(level, pos)
            ? Blocks.AIR.defaultBlockState()
            : super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).isSolid();
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return CakeBlock.FULL_CAKE_SIGNAL;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }

    public static BlockState byCandle(CandleBlock candle) {
        return BY_CANDLE.get(candle).defaultBlockState();
    }

    public static boolean canLight(BlockState state) {
        return state.is(BlockTags.CANDLE_CAKES, blockStateBase -> blockStateBase.hasProperty(LIT) && !state.getValue(LIT));
    }
}
