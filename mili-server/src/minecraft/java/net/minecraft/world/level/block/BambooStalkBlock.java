package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BambooLeaves;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class BambooStalkBlock extends Block implements BonemealableBlock {
    public static final MapCodec<BambooStalkBlock> CODEC = simpleCodec(BambooStalkBlock::new);
    private static final VoxelShape SHAPE_SMALL = Block.column(6.0, 0.0, 16.0);
    private static final VoxelShape SHAPE_LARGE = Block.column(10.0, 0.0, 16.0);
    private static final VoxelShape SHAPE_COLLISION = Block.column(3.0, 0.0, 16.0);
    public static final IntegerProperty AGE = BlockStateProperties.AGE_1;
    public static final EnumProperty<BambooLeaves> LEAVES = BlockStateProperties.BAMBOO_LEAVES;
    public static final IntegerProperty STAGE = BlockStateProperties.STAGE;
    public static final int MAX_HEIGHT = 16;
    public static final int STAGE_GROWING = 0;
    public static final int STAGE_DONE_GROWING = 1;
    public static final int AGE_THIN_BAMBOO = 0;
    public static final int AGE_THICK_BAMBOO = 1;

    @Override
    public MapCodec<BambooStalkBlock> codec() {
        return CODEC;
    }

    public BambooStalkBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(AGE, 0).setValue(LEAVES, BambooLeaves.NONE).setValue(STAGE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE, LEAVES, STAGE);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return true;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape voxelShape = state.getValue(LEAVES) == BambooLeaves.LARGE ? SHAPE_LARGE : SHAPE_SMALL;
        return voxelShape.move(state.getOffset(pos));
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE_COLLISION.move(state.getOffset(pos));
    }

    @Override
    protected boolean isCollisionShapeFullBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return false;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        FluidState fluidState = context.getLevel().getFluidState(context.getClickedPos());
        if (!fluidState.isEmpty()) {
            return null;
        } else {
            BlockState blockState = context.getLevel().getBlockState(context.getClickedPos().below());
            if (blockState.is(BlockTags.BAMBOO_PLANTABLE_ON)) {
                if (blockState.is(Blocks.BAMBOO_SAPLING)) {
                    return this.defaultBlockState().setValue(AGE, 0);
                } else if (blockState.is(Blocks.BAMBOO)) {
                    int i = blockState.getValue(AGE) > 0 ? 1 : 0;
                    return this.defaultBlockState().setValue(AGE, i);
                } else {
                    BlockState blockState1 = context.getLevel().getBlockState(context.getClickedPos().above());
                    return blockState1.is(Blocks.BAMBOO)
                        ? this.defaultBlockState().setValue(AGE, blockState1.getValue(AGE))
                        : Blocks.BAMBOO_SAPLING.defaultBlockState();
                }
            } else {
                return null;
            }
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.canSurvive(level, pos)) {
            level.destroyBlock(pos, true);
        }
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(STAGE) == 0;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(STAGE) == 0) {
            if (random.nextFloat() < (level.spigotConfig.bambooModifier / (100.0F * 3)) && level.isEmptyBlock(pos.above()) && level.getRawBrightness(pos.above(), 0) >= 9) { // Spigot - SPIGOT-7159: Better modifier resolution
                int i = this.getHeightBelowUpToMax(level, pos) + 1;
                if (i < level.paperConfig().maxGrowthHeight.bamboo.max) { // Paper - Configurable cactus/bamboo/reed growth height
                    this.growBamboo(state, level, pos, random, i);
                }
            }
        }
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).is(BlockTags.BAMBOO_PLANTABLE_ON);
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

        return direction == Direction.UP && neighborState.is(Blocks.BAMBOO) && neighborState.getValue(AGE) > state.getValue(AGE)
            ? state.cycle(AGE)
            : super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        int heightAboveUpToMax = this.getHeightAboveUpToMax(level, pos);
        int heightBelowUpToMax = this.getHeightBelowUpToMax(level, pos);
        return heightAboveUpToMax + heightBelowUpToMax + 1 < ((Level) level).paperConfig().maxGrowthHeight.bamboo.max && level.getBlockState(pos.above(heightAboveUpToMax)).getValue(STAGE) != 1; // Paper - Configurable cactus/bamboo/reed growth height
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        int heightAboveUpToMax = this.getHeightAboveUpToMax(level, pos);
        int heightBelowUpToMax = this.getHeightBelowUpToMax(level, pos);
        int i = heightAboveUpToMax + heightBelowUpToMax + 1;
        int i1 = 1 + random.nextInt(2);

        for (int i2 = 0; i2 < i1; i2++) {
            BlockPos blockPos = pos.above(heightAboveUpToMax);
            BlockState blockState = level.getBlockState(blockPos);
            if (i >= level.paperConfig().maxGrowthHeight.bamboo.max || !blockState.is(Blocks.BAMBOO) || blockState.getValue(BambooStalkBlock.STAGE) == 1 || !level.isEmptyBlock(blockPos.above())) { // CraftBukkit - If the BlockSpreadEvent was cancelled, we have no bamboo here // Paper - Configurable cactus/bamboo/reed growth height
                return;
            }

            this.growBamboo(blockState, level, blockPos, random, i);
            heightAboveUpToMax++;
            i++;
        }
    }

    protected void growBamboo(BlockState state, Level level, BlockPos pos, RandomSource random, int age) {
        BlockState blockState = level.getBlockState(pos.below());
        BlockPos blockPos = pos.below(2);
        BlockState blockState1 = level.getBlockState(blockPos);
        BambooLeaves bambooLeaves = BambooLeaves.NONE;
        boolean shouldUpdateOthers = false; // CraftBukkit
        if (age >= 1) {
            if (!blockState.is(Blocks.BAMBOO) || blockState.getValue(LEAVES) == BambooLeaves.NONE) {
                bambooLeaves = BambooLeaves.SMALL;
            } else if (blockState.is(Blocks.BAMBOO) && blockState.getValue(LEAVES) != BambooLeaves.NONE) {
                bambooLeaves = BambooLeaves.LARGE;
                if (blockState1.is(Blocks.BAMBOO)) {
                    // CraftBukkit start - moved down
                    // level.setBlock(pos.below(), blockState.setValue(LEAVES, BambooLeaves.SMALL), Block.UPDATE_ALL);
                    // level.setBlock(blockPos, blockState1.setValue(LEAVES, BambooLeaves.NONE), Block.UPDATE_ALL);
                    shouldUpdateOthers = true;
                    // CraftBukkit end
                }
            }
        }

        int i = state.getValue(AGE) != 1 && !blockState1.is(Blocks.BAMBOO) ? 0 : 1;
        int i1 = (age < level.paperConfig().maxGrowthHeight.bamboo.min || random.nextFloat() >= 0.25F) && age != (level.paperConfig().maxGrowthHeight.bamboo.max - 1) ? 0 : 1; // Paper - Configurable cactus/bamboo/reed growth height
        // CraftBukkit start
        if (org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockSpreadEvent(level, pos, pos.above(), this.defaultBlockState().setValue(AGE, i).setValue(LEAVES, bambooLeaves).setValue(STAGE, i1), Block.UPDATE_ALL)) {
            if (shouldUpdateOthers) {
                level.setBlock(pos.below(), blockState.setValue(BambooStalkBlock.LEAVES, BambooLeaves.SMALL), Block.UPDATE_ALL);
                level.setBlock(blockPos, blockState1.setValue(BambooStalkBlock.LEAVES, BambooLeaves.NONE), Block.UPDATE_ALL);
            }
        }
        // CraftBukkit end
    }

    protected int getHeightAboveUpToMax(BlockGetter level, BlockPos pos) {
        int i = 0;

        while (i < ((Level) level).paperConfig().maxGrowthHeight.bamboo.max && level.getBlockState(pos.above(i + 1)).is(Blocks.BAMBOO)) { // Paper - Configurable cactus/bamboo/reed growth height
            i++;
        }

        return i;
    }

    protected int getHeightBelowUpToMax(BlockGetter level, BlockPos pos) {
        int i = 0;

        while (i < ((Level) level).paperConfig().maxGrowthHeight.bamboo.max && level.getBlockState(pos.below(i + 1)).is(Blocks.BAMBOO)) { // Paper - Configurable cactus/bamboo/reed growth height
            i++;
        }

        return i;
    }
}
