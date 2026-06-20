package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.sniffer.Sniffer;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SnifferEggBlock extends Block {
    public static final MapCodec<SnifferEggBlock> CODEC = simpleCodec(SnifferEggBlock::new);
    public static final int MAX_HATCH_LEVEL = 2;
    public static final IntegerProperty HATCH = BlockStateProperties.HATCH;
    private static final int REGULAR_HATCH_TIME_TICKS = 24000;
    private static final int BOOSTED_HATCH_TIME_TICKS = 12000;
    private static final int RANDOM_HATCH_OFFSET_TICKS = 300;
    private static final VoxelShape SHAPE = Block.column(14.0, 12.0, 0.0, 16.0);

    @Override
    public MapCodec<SnifferEggBlock> codec() {
        return CODEC;
    }

    public SnifferEggBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(HATCH, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HATCH);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    public int getHatchLevel(BlockState state) {
        return state.getValue(HATCH);
    }

    private boolean isReadyToHatch(BlockState state) {
        return this.getHatchLevel(state) == 2;
    }

    // Paper start - Call BlockFadeEvent
    private void rescheduleTick(ServerLevel level, BlockPos pos) {
        int baseDelay = hatchBoost(level, pos) ? level.paperConfig().entities.sniffer.boostedHatchTime.or(BOOSTED_HATCH_TIME_TICKS) : level.paperConfig().entities.sniffer.hatchTime.or(REGULAR_HATCH_TIME_TICKS); // Paper - Configure sniffer egg hatch time
        level.scheduleTick(pos, this, (baseDelay / 3) + level.random.nextInt(RANDOM_HATCH_OFFSET_TICKS));
        // reschedule to avoid being stuck here and behave like the other calls (see #onPlace)
    }
    // Paper end - Call BlockFadeEvent

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!this.isReadyToHatch(state)) {
            // Paper start
            if (!org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockGrowEvent(level, pos, state.setValue(HATCH, this.getHatchLevel(state) + 1), Block.UPDATE_CLIENTS)) {
                this.rescheduleTick(level, pos);
                return;
            }
            // Paper end
            level.playSound(null, pos, SoundEvents.SNIFFER_EGG_CRACK, SoundSource.BLOCKS, 0.7F, 0.9F + random.nextFloat() * 0.2F);
        } else {
            // Paper start - Call BlockFadeEvent
            if (org.bukkit.craftbukkit.event.CraftEventFactory.callBlockFadeEvent(level, pos, state.getFluidState().createLegacyBlock()).isCancelled()) {
                this.rescheduleTick(level, pos);
                return;
            }
            // Paper end - Call BlockFadeEvent
            level.playSound(null, pos, SoundEvents.SNIFFER_EGG_HATCH, SoundSource.BLOCKS, 0.7F, 0.9F + random.nextFloat() * 0.2F);
            level.destroyBlock(pos, false);
            Sniffer sniffer = EntityType.SNIFFER.create(level, EntitySpawnReason.BREEDING);
            if (sniffer != null) {
                Vec3 center = pos.getCenter();
                sniffer.setBaby(true);
                sniffer.snapTo(center.x(), center.y(), center.z(), Mth.wrapDegrees(level.random.nextFloat() * 360.0F), 0.0F);
                level.addFreshEntity(sniffer, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.EGG); // Paper - use correct spawn reason
            }
        }
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        boolean flag = hatchBoost(level, pos);
        if (!level.isClientSide() && flag) {
            level.levelEvent(LevelEvent.PARTICLES_EGG_CRACK, pos, 0);
        }

        int i = flag ? level.paperConfig().entities.sniffer.boostedHatchTime.or(BOOSTED_HATCH_TIME_TICKS) : level.paperConfig().entities.sniffer.hatchTime.or(REGULAR_HATCH_TIME_TICKS); // Paper
        int i1 = i / 3;
        level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(state));
        level.scheduleTick(pos, this, i1 + level.random.nextInt(300));
    }

    @Override
    public boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }

    public static boolean hatchBoost(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos.below()).is(BlockTags.SNIFFER_EGG_HATCH_BOOST);
    }
}
