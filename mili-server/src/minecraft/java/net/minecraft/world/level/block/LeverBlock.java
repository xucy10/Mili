package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.redstone.ExperimentalRedstoneUtils;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class LeverBlock extends FaceAttachedHorizontalDirectionalBlock {
    public static final MapCodec<LeverBlock> CODEC = simpleCodec(LeverBlock::new);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    private final Function<BlockState, VoxelShape> shapes;

    @Override
    public MapCodec<LeverBlock> codec() {
        return CODEC;
    }

    protected LeverBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, false).setValue(FACE, AttachFace.WALL));
        this.shapes = this.makeShapes();
    }

    private Function<BlockState, VoxelShape> makeShapes() {
        Map<AttachFace, Map<Direction, VoxelShape>> map = Shapes.rotateAttachFace(Block.boxZ(6.0, 8.0, 10.0, 16.0));
        return this.getShapeForEachState(blockState -> map.get(blockState.getValue(FACE)).get(blockState.getValue(FACING)), POWERED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.shapes.apply(state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            BlockState blockState = state.cycle(POWERED);
            if (blockState.getValue(POWERED)) {
                makeParticle(blockState, level, pos, 1.0F);
            }
        } else {
            // CraftBukkit start - Interact Lever
            boolean powered = state.getValue(LeverBlock.POWERED); // Old powered state
            org.bukkit.block.Block block = org.bukkit.craftbukkit.block.CraftBlock.at(level, pos);
            int old = (powered) ? 15 : 0;
            int current = (!powered) ? 15 : 0;

            org.bukkit.event.block.BlockRedstoneEvent eventRedstone = new org.bukkit.event.block.BlockRedstoneEvent(block, old, current);
            level.getCraftServer().getPluginManager().callEvent(eventRedstone);

            if ((eventRedstone.getNewCurrent() > 0) != (!powered)) {
                return InteractionResult.SUCCESS;
            }
            // CraftBukkit end
            this.pull(state, level, pos, null);
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    protected void onExplosionHit(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion, BiConsumer<ItemStack, BlockPos> dropConsumer) {
        if (explosion.canTriggerBlocks()) {
            this.pull(state, level, pos, null);
        }

        super.onExplosionHit(state, level, pos, explosion, dropConsumer);
    }

    public void pull(BlockState state, Level level, BlockPos pos, @Nullable Player player) {
        state = state.cycle(POWERED);
        level.setBlock(pos, state, Block.UPDATE_ALL);
        this.updateNeighbours(state, level, pos);
        playSound(player, level, pos, state);
        level.gameEvent(player, state.getValue(POWERED) ? GameEvent.BLOCK_ACTIVATE : GameEvent.BLOCK_DEACTIVATE, pos);
    }

    protected static void playSound(@Nullable Player player, LevelAccessor level, BlockPos pos, BlockState state) {
        float f = state.getValue(POWERED) ? 0.6F : 0.5F;
        level.playSound(player, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.3F, f);
    }

    private static void makeParticle(BlockState state, LevelAccessor level, BlockPos pos, float alpha) {
        Direction opposite = state.getValue(FACING).getOpposite();
        Direction opposite1 = getConnectedDirection(state).getOpposite();
        double d = pos.getX() + 0.5 + 0.1 * opposite.getStepX() + 0.2 * opposite1.getStepX();
        double d1 = pos.getY() + 0.5 + 0.1 * opposite.getStepY() + 0.2 * opposite1.getStepY();
        double d2 = pos.getZ() + 0.5 + 0.1 * opposite.getStepZ() + 0.2 * opposite1.getStepZ();
        level.addParticle(new DustParticleOptions(16711680, alpha), d, d1, d2, 0.0, 0.0, 0.0);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(POWERED) && random.nextFloat() < 0.25F) {
            makeParticle(state, level, pos, 0.5F);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        if (!movedByPiston && state.getValue(POWERED)) {
            this.updateNeighbours(state, level, pos);
        }
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return state.getValue(POWERED) ? 15 : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return state.getValue(POWERED) && getConnectedDirection(state) == side ? 15 : 0;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    private void updateNeighbours(BlockState state, Level level, BlockPos pos) {
        Direction opposite = getConnectedDirection(state).getOpposite();
        Orientation orientation = ExperimentalRedstoneUtils.initialOrientation(
            level, opposite, opposite.getAxis().isHorizontal() ? Direction.UP : state.getValue(FACING)
        );
        level.updateNeighborsAt(pos, this, orientation);
        level.updateNeighborsAt(pos.relative(opposite), this, orientation);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACE, FACING, POWERED);
    }
}
