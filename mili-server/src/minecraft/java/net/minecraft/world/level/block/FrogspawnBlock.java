package net.minecraft.world.level.block;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.animal.frog.Tadpole;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class FrogspawnBlock extends Block {
    public static final MapCodec<FrogspawnBlock> CODEC = simpleCodec(FrogspawnBlock::new);
    private static final int MIN_TADPOLES_SPAWN = 2;
    private static final int MAX_TADPOLES_SPAWN = 5;
    private static final int DEFAULT_MIN_HATCH_TICK_DELAY = 3600;
    private static final int DEFAULT_MAX_HATCH_TICK_DELAY = 12000;
    private static final VoxelShape SHAPE = Block.column(16.0, 0.0, 1.5);
    private static int minHatchTickDelay = 3600;
    private static int maxHatchTickDelay = 12000;

    @Override
    public MapCodec<FrogspawnBlock> codec() {
        return CODEC;
    }

    public FrogspawnBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return mayPlaceOn(level, pos.below());
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        level.scheduleTick(pos, this, getFrogspawnHatchDelay(level.getRandom()));
    }

    private static int getFrogspawnHatchDelay(RandomSource random) {
        return random.nextInt(minHatchTickDelay, maxHatchTickDelay);
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
        return !this.canSurvive(state, level, pos)
            ? Blocks.AIR.defaultBlockState()
            : super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!this.canSurvive(state, level, pos)) {
            this.destroyBlock(level, pos);
        } else {
            this.hatchFrogspawn(level, pos, random);
        }
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean pastEdges) {
        if (!new io.papermc.paper.event.entity.EntityInsideBlockEvent(entity.getBukkitEntity(), org.bukkit.craftbukkit.block.CraftBlock.at(level, pos)).callEvent()) { return; } // Paper - Add EntityInsideBlockEvent
        if (entity.getType().equals(EntityType.FALLING_BLOCK)) {
            this.destroyBlock(level, pos);
        }
    }

    private static boolean mayPlaceOn(BlockGetter level, BlockPos pos) {
        FluidState fluidState = level.getFluidState(pos);
        FluidState fluidState1 = level.getFluidState(pos.above());
        return fluidState.getType() == Fluids.WATER && fluidState1.getType() == Fluids.EMPTY;
    }

    private void hatchFrogspawn(ServerLevel level, BlockPos pos, RandomSource random) {
        // Paper start - Call BlockFadeEvent
        if (org.bukkit.craftbukkit.event.CraftEventFactory.callBlockFadeEvent(level, pos, Blocks.AIR.defaultBlockState()).isCancelled()) {
            return;
        }
        // Paper end - Call BlockFadeEven
        this.destroyBlock(level, pos);
        level.playSound(null, pos, SoundEvents.FROGSPAWN_HATCH, SoundSource.BLOCKS, 1.0F, 1.0F);
        this.spawnTadpoles(level, pos, random);
    }

    private void destroyBlock(Level level, BlockPos pos) {
        level.destroyBlock(pos, false);
    }

    private void spawnTadpoles(ServerLevel level, BlockPos pos, RandomSource random) {
        int randomInt = random.nextInt(2, 6);

        for (int i = 1; i <= randomInt; i++) {
            Tadpole tadpole = EntityType.TADPOLE.create(level, EntitySpawnReason.BREEDING);
            if (tadpole != null) {
                double d = pos.getX() + this.getRandomTadpolePositionOffset(random);
                double d1 = pos.getZ() + this.getRandomTadpolePositionOffset(random);
                int randomInt1 = random.nextInt(1, 361);
                tadpole.snapTo(d, pos.getY() - 0.5, d1, randomInt1, 0.0F);
                tadpole.setPersistenceRequired();
                level.addFreshEntity(tadpole, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.EGG); // Paper - use correct spawn reason
            }
        }
    }

    private double getRandomTadpolePositionOffset(RandomSource random) {
        double d = 0.2F;
        return Mth.clamp(random.nextDouble(), 0.2F, 0.7999999970197678);
    }

    @VisibleForTesting
    public static void setHatchDelay(int minHatchDelay, int maxHatchDelay) {
        minHatchTickDelay = minHatchDelay;
        maxHatchTickDelay = maxHatchDelay;
    }

    @VisibleForTesting
    public static void setDefaultHatchDelay() {
        minHatchTickDelay = 3600;
        maxHatchTickDelay = 12000;
    }
}
