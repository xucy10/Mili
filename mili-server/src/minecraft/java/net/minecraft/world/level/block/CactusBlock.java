package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CactusBlock extends Block {
    public static final MapCodec<CactusBlock> CODEC = simpleCodec(CactusBlock::new);
    public static final IntegerProperty AGE = BlockStateProperties.AGE_15;
    public static final int MAX_AGE = 15;
    private static final VoxelShape SHAPE = Block.column(14.0, 0.0, 16.0);
    private static final VoxelShape SHAPE_COLLISION = Block.column(14.0, 0.0, 15.0);
    private static final int MAX_CACTUS_GROWING_HEIGHT = 3;
    private static final int ATTEMPT_GROW_CACTUS_FLOWER_AGE = 8;
    private static final double ATTEMPT_GROW_CACTUS_FLOWER_SMALL_CACTUS_CHANCE = 0.1;
    private static final double ATTEMPT_GROW_CACTUS_FLOWER_TALL_CACTUS_CHANCE = 0.25;

    @Override
    public MapCodec<CactusBlock> codec() {
        return CODEC;
    }

    protected CactusBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(AGE, 0));
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.canSurvive(level, pos)) {
            level.destroyBlock(pos, true);
            // Leaves start - zero tick plants
        } else if (fun.bm.mili.config.modules.function.OldFeatureConfig.zeroTickPlants) {
            this.randomTick(state, level, pos, random);
            // Leaves end - zero tick plants
        }
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos blockPos = pos.above();
        if (level.isEmptyBlock(blockPos)) {
            int i = 1;
            int ageValue = state.getValue(AGE);

            while (level.getBlockState(pos.below(i)).is(this)) {
                if (++i == level.paperConfig().maxGrowthHeight.cactus && ageValue == 15) { // Paper - Configurable cactus/bamboo/reed growth height
                    return;
                }
            }

            if (ageValue == 8 && this.canSurvive(this.defaultBlockState(), level, pos.above())) {
                double d = i >= level.paperConfig().maxGrowthHeight.cactus ? 0.25 : 0.1; // Paper - Configurable cactus/bamboo/reed growth height
                if (random.nextDouble() <= d) {
                    org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockGrowEvent(level, blockPos, Blocks.CACTUS_FLOWER.defaultBlockState(), Block.UPDATE_ALL);
                }
            } else if (ageValue == 15 && i < level.paperConfig().maxGrowthHeight.cactus) { // Paper - Configurable cactus/bamboo/reed growth height
                // Paper start
                if (!org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockGrowEvent(level, blockPos, this.defaultBlockState(), Block.UPDATE_ALL)) {
                    return;
                }
                // Paper end
                BlockState blockState = state.setValue(AGE, 0);
                level.setBlock(pos, blockState, Block.UPDATE_NONE);
                level.neighborChanged(blockState, blockPos, this, null, false);
            }

            if (ageValue < 15) {
                level.setBlock(pos, state.setValue(AGE, ageValue + 1), Block.UPDATE_NONE);
            }
        }
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE_COLLISION;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
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
        if (!state.canSurvive(level, pos)) {
            scheduledTickAccess.scheduleTick(pos, this, 1);
        }

        return super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockState blockState = level.getBlockState(pos.relative(direction));
            if (blockState.isSolid() || level.getFluidState(pos.relative(direction)).is(FluidTags.LAVA)) {
                return false;
            }
        }

        BlockState blockState1 = level.getBlockState(pos.below());
        return (blockState1.is(Blocks.CACTUS) || blockState1.is(BlockTags.SAND)) && !level.getBlockState(pos.above()).liquid();
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean pastEdges) {
        if (!new io.papermc.paper.event.entity.EntityInsideBlockEvent(entity.getBukkitEntity(), org.bukkit.craftbukkit.block.CraftBlock.at(level, pos)).callEvent()) { return; } // Paper - Add EntityInsideBlockEvent
        entity.hurt(level.damageSources().cactus().eventBlockDamager(level, pos), 1.0F); // CraftBukkit
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }
}
