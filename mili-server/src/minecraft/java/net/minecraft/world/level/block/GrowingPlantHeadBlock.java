package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class GrowingPlantHeadBlock extends GrowingPlantBlock implements BonemealableBlock {
    public static final IntegerProperty AGE = BlockStateProperties.AGE_25;
    public static final int MAX_AGE = 25;
    private final double growPerTickProbability;

    protected GrowingPlantHeadBlock(
        BlockBehaviour.Properties properties, Direction growthDirection, VoxelShape shape, boolean scheduleFluidTicks, double growPerTickProbability
    ) {
        super(properties, growthDirection, shape, scheduleFluidTicks);
        this.growPerTickProbability = growPerTickProbability;
        this.registerDefaultState(this.stateDefinition.any().setValue(AGE, 0));
    }

    @Override
    protected abstract MapCodec<? extends GrowingPlantHeadBlock> codec();

    @Override
    public BlockState getStateForPlacement(RandomSource random) {
        return this.defaultBlockState().setValue(AGE, random.nextInt(25));
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(AGE) < 25;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // Spigot start
        int modifier = 100;
        if (this == Blocks.KELP) {
            modifier = level.spigotConfig.kelpModifier;
        } else if (this == Blocks.TWISTING_VINES) {
            modifier = level.spigotConfig.twistingVinesModifier;
        } else if (this == Blocks.WEEPING_VINES) {
            modifier = level.spigotConfig.weepingVinesModifier;
        } else if (this == Blocks.CAVE_VINES) {
            modifier = level.spigotConfig.caveVinesModifier;
        }
        if (state.getValue(AGE) < 25 && random.nextDouble() < ((modifier / 100.0D) * this.growPerTickProbability)) { // Spigot - SPIGOT-7159: Better modifier resolution
            // Spigot end
            BlockPos blockPos = pos.relative(this.growthDirection);
            if (this.canGrowInto(level.getBlockState(blockPos))) {
                org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockSpreadEvent(level, pos, blockPos, this.getGrowIntoState(state, level.random, level), Block.UPDATE_ALL); // CraftBukkit // Paper - Fix Spigot growth modifiers
            }
        }
    }

    // Paper start - Fix Spigot growth modifiers
    protected BlockState getGrowIntoState(BlockState state, RandomSource random, @javax.annotation.Nullable Level level) {
        return this.getGrowIntoState(state, random);
    }
    // Paper end - Fix Spigot growth modifiers

    protected BlockState getGrowIntoState(BlockState state, RandomSource random) {
        return state.cycle(AGE);
    }

    public BlockState getMaxAgeState(BlockState state) {
        return state.setValue(AGE, 25);
    }

    public boolean isMaxAge(BlockState state) {
        return state.getValue(AGE) == 25;
    }

    protected BlockState updateBodyAfterConvertedFromHead(BlockState head, BlockState body) {
        return body;
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
        if (direction == this.growthDirection.getOpposite()) {
            if (!state.canSurvive(level, pos)) {
                scheduledTickAccess.scheduleTick(pos, this, 1);
            } else {
                BlockState blockState = level.getBlockState(pos.relative(this.growthDirection));
                if (blockState.is(this) || blockState.is(this.getBodyBlock())) {
                    return this.updateBodyAfterConvertedFromHead(state, this.getBodyBlock().defaultBlockState());
                }
            }
        }

        if (direction != this.growthDirection || !neighborState.is(this) && !neighborState.is(this.getBodyBlock())) {
            if (this.scheduleFluidTicks) {
                scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
            }

            return super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
        } else {
            return this.updateBodyAfterConvertedFromHead(state, this.getBodyBlock().defaultBlockState());
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return this.canGrowInto(level.getBlockState(pos.relative(this.growthDirection)));
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        BlockPos blockPos = pos.relative(this.growthDirection);
        int min = Math.min(state.getValue(AGE) + 1, 25);
        int blocksToGrowWhenBonemealed = this.getBlocksToGrowWhenBonemealed(random);

        for (int i = 0; i < blocksToGrowWhenBonemealed && this.canGrowInto(level.getBlockState(blockPos)); i++) {
            level.setBlockAndUpdate(blockPos, state.setValue(AGE, min));
            blockPos = blockPos.relative(this.growthDirection);
            min = Math.min(min + 1, 25);
        }
    }

    protected abstract int getBlocksToGrowWhenBonemealed(RandomSource random);

    protected abstract boolean canGrowInto(BlockState state);

    @Override
    protected GrowingPlantHeadBlock getHeadBlock() {
        return this;
    }

    // Leaves start - zero tick plants
    @Override
    public void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        if (!state.canSurvive(world, pos)) {
            world.destroyBlock(pos, true);
        } else if (fun.bm.mili.config.modules.function.OldFeatureConfig.zeroTickPlants) {
            this.randomTick(state, world, pos, random);
        }
    }
    // Leaves end - zero tick plants
}
