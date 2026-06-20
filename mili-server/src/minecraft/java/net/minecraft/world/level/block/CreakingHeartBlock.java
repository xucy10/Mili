package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.CreakingHeartBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.CreakingHeartState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jspecify.annotations.Nullable;

public class CreakingHeartBlock extends BaseEntityBlock {
    public static final MapCodec<CreakingHeartBlock> CODEC = simpleCodec(CreakingHeartBlock::new);
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;
    public static final EnumProperty<CreakingHeartState> STATE = BlockStateProperties.CREAKING_HEART_STATE;
    public static final BooleanProperty NATURAL = BlockStateProperties.NATURAL;

    @Override
    public MapCodec<CreakingHeartBlock> codec() {
        return CODEC;
    }

    protected CreakingHeartBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(
            this.defaultBlockState().setValue(AXIS, Direction.Axis.Y).setValue(STATE, CreakingHeartState.UPROOTED).setValue(NATURAL, false)
        );
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CreakingHeartBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (level.isClientSide()) {
            return null;
        } else {
            return state.getValue(STATE) != CreakingHeartState.UPROOTED
                ? createTickerHelper(blockEntityType, BlockEntityType.CREAKING_HEART, CreakingHeartBlockEntity::serverTick)
                : null;
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (level.environmentAttributes().getValue(EnvironmentAttributes.CREAKING_ACTIVE, pos)) {
            if (state.getValue(STATE) != CreakingHeartState.UPROOTED) {
                if (random.nextInt(16) == 0 && isSurroundedByLogs(level, pos)) {
                    level.playLocalSound(pos.getX(), pos.getY(), pos.getZ(), SoundEvents.CREAKING_HEART_IDLE, SoundSource.BLOCKS, 1.0F, 1.0F, false);
                }
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
        scheduledTickAccess.scheduleTick(pos, this, 1);
        return super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockState blockState = updateState(state, level, pos);
        if (blockState != state) {
            level.setBlock(pos, blockState, Block.UPDATE_ALL);
        }
    }

    private static BlockState updateState(BlockState state, Level level, BlockPos pos) {
        boolean hasRequiredLogs = hasRequiredLogs(state, level, pos);
        boolean flag = state.getValue(STATE) == CreakingHeartState.UPROOTED;
        return hasRequiredLogs && flag
            ? state.setValue(
                STATE,
                level.environmentAttributes().getValue(EnvironmentAttributes.CREAKING_ACTIVE, pos) ? CreakingHeartState.AWAKE : CreakingHeartState.DORMANT
            )
            : state;
    }

    public static boolean hasRequiredLogs(BlockState state, LevelReader level, BlockPos pos) {
        Direction.Axis axis = state.getValue(AXIS);

        for (Direction direction : axis.getDirections()) {
            BlockState blockState = level.getBlockState(pos.relative(direction));
            if (!blockState.is(BlockTags.PALE_OAK_LOGS) || blockState.getValue(AXIS) != axis) {
                return false;
            }
        }

        return true;
    }

    private static boolean isSurroundedByLogs(LevelAccessor level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos blockPos = pos.relative(direction);
            BlockState blockState = level.getBlockState(blockPos);
            if (!blockState.is(BlockTags.PALE_OAK_LOGS)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return updateState(this.defaultBlockState().setValue(AXIS, context.getClickedFace().getAxis()), context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return RotatedPillarBlock.rotatePillar(state, rotation);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, STATE, NATURAL);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        Containers.updateNeighboursAfterDestroy(state, level, pos);
    }

    @Override
    protected void onExplosionHit(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion, BiConsumer<ItemStack, BlockPos> dropConsumer) {
        if (level.getBlockEntity(pos) instanceof CreakingHeartBlockEntity creakingHeartBlockEntity
            && explosion instanceof ServerExplosion serverExplosion
            && explosion.getBlockInteraction().shouldAffectBlocklikeEntities()) {
            creakingHeartBlockEntity.removeProtector(serverExplosion.getDamageSource());
            if (explosion.getIndirectSourceEntity() instanceof Player player && explosion.getBlockInteraction().shouldAffectBlocklikeEntities()) {
                this.tryAwardExperience(player, state, level, pos);
            }
        }

        super.onExplosionHit(state, level, pos, explosion, dropConsumer);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level.getBlockEntity(pos) instanceof CreakingHeartBlockEntity creakingHeartBlockEntity) {
            creakingHeartBlockEntity.removeProtector(player.damageSources().playerAttack(player));
            this.tryAwardExperience(player, state, level, pos);
        }

        return super.playerWillDestroy(level, pos, state, player);
    }

    private void tryAwardExperience(Player player, BlockState state, Level level, BlockPos pos) {
        if (!player.preventsBlockDrops() && !player.isSpectator() && state.getValue(NATURAL) && level instanceof ServerLevel serverLevel) {
            this.popExperience(serverLevel, pos, level.random.nextIntBetweenInclusive(20, 24));
        }
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        if (state.getValue(STATE) == CreakingHeartState.UPROOTED) {
            return 0;
        } else {
            return level.getBlockEntity(pos) instanceof CreakingHeartBlockEntity creakingHeartBlockEntity
                ? creakingHeartBlockEntity.getAnalogOutputSignal()
                : 0;
        }
    }
}
