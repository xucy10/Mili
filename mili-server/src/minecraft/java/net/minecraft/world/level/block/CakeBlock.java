package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CakeBlock extends Block {
    public static final MapCodec<CakeBlock> CODEC = simpleCodec(CakeBlock::new);
    public static final int MAX_BITES = 6;
    public static final IntegerProperty BITES = BlockStateProperties.BITES;
    public static final int FULL_CAKE_SIGNAL = getOutputSignal(0);
    private static final VoxelShape[] SHAPES = Block.boxes(6, i -> Block.box(1 + i * 2, 0.0, 1.0, 15.0, 8.0, 15.0));

    @Override
    public MapCodec<CakeBlock> codec() {
        return CODEC;
    }

    protected CakeBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(BITES, 0));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(BITES)];
    }

    @Override
    protected InteractionResult useItemOn(
        ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult
    ) {
        Item item = stack.getItem();
        if (stack.is(ItemTags.CANDLES) && state.getValue(BITES) == 0 && Block.byItem(item) instanceof CandleBlock candleBlock) {
            // Paper start - call change block event
            if (!org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(player, pos, CandleCakeBlock.byCandle(candleBlock))) {
                player.containerMenu.forceHeldSlot(hand); // update inv because candle could decrease
                return InteractionResult.TRY_WITH_EMPTY_HAND;
            }
            // Paper end - call change block event
            stack.consume(1, player);
            level.playSound(null, pos, SoundEvents.CAKE_ADD_CANDLE, SoundSource.BLOCKS, 1.0F, 1.0F);
            level.setBlockAndUpdate(pos, CandleCakeBlock.byCandle(candleBlock));
            level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
            player.awardStat(Stats.ITEM_USED.get(item));
            return InteractionResult.SUCCESS;
        } else {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            if (eat(level, pos, state, player).consumesAction()) {
                return InteractionResult.SUCCESS;
            }

            if (player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty()) {
                return InteractionResult.CONSUME;
            }
        }

        return eat(level, pos, state, player);
    }

    protected static InteractionResult eat(LevelAccessor level, BlockPos pos, BlockState state, Player player) {
        if (!player.canEat(false)) {
            return InteractionResult.PASS;
        } else {
            // Paper start - call change block event
            int bitesValue = state.getValue(CakeBlock.BITES);
            final BlockState newState = bitesValue < MAX_BITES ? state.setValue(CakeBlock.BITES, bitesValue + 1) : level.getFluidState(pos).createLegacyBlock();
            if (!org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(player, pos, newState)) {
                ((net.minecraft.server.level.ServerPlayer) player).getBukkitEntity().sendHealthUpdate();
                return InteractionResult.PASS; // return a non-consume result to cake blocks don't drop their candles
            }
            // Paper end - call change block event
            player.awardStat(Stats.EAT_CAKE_SLICE);
            // CraftBukkit start
            // player.getFoodData().eat(2, 0.1F);
            int oldFoodLevel = player.getFoodData().foodLevel;

            org.bukkit.event.entity.FoodLevelChangeEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callFoodLevelChangeEvent(player, 2 + oldFoodLevel);

            if (!event.isCancelled()) {
                player.getFoodData().eat(event.getFoodLevel() - oldFoodLevel, 0.1F);
            }

            ((net.minecraft.server.level.ServerPlayer) player).getBukkitEntity().sendHealthUpdate();
            // CraftBukkit end
            // Paper - move up
            level.gameEvent(player, GameEvent.EAT, pos);
            if (bitesValue < 6) {
                level.setBlock(pos, state.setValue(BITES, bitesValue + 1), Block.UPDATE_ALL);
            } else {
                level.removeBlock(pos, false);
                level.gameEvent(player, GameEvent.BLOCK_DESTROY, pos);
            }

            return InteractionResult.SUCCESS;
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
        return direction == Direction.DOWN && !state.canSurvive(level, pos)
            ? Blocks.AIR.defaultBlockState()
            : super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).isSolid();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BITES);
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return getOutputSignal(state.getValue(BITES));
    }

    public static int getOutputSignal(int eaten) {
        return (7 - eaten) * 2;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }
}
