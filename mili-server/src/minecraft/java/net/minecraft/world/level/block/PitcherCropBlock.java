package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class PitcherCropBlock extends DoublePlantBlock implements BonemealableBlock {
    public static final MapCodec<PitcherCropBlock> CODEC = simpleCodec(PitcherCropBlock::new);
    public static final int MAX_AGE = 4;
    public static final IntegerProperty AGE = BlockStateProperties.AGE_4;
    public static final EnumProperty<DoubleBlockHalf> HALF = DoublePlantBlock.HALF;
    private static final int DOUBLE_PLANT_AGE_INTERSECTION = 3;
    private static final int BONEMEAL_INCREASE = 1;
    private static final VoxelShape SHAPE_BULB = Block.column(6.0, -1.0, 3.0);
    private static final VoxelShape SHAPE_CROP = Block.column(10.0, -1.0, 5.0);
    private final Function<BlockState, VoxelShape> shapes = this.makeShapes();

    @Override
    public MapCodec<PitcherCropBlock> codec() {
        return CODEC;
    }

    public PitcherCropBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    private Function<BlockState, VoxelShape> makeShapes() {
        int[] ints = new int[]{0, 9, 11, 22, 26};
        return this.getShapeForEachState(blockState -> {
            int i = (blockState.getValue(AGE) == 0 ? 4 : 6) + ints[blockState.getValue(AGE)];
            int i1 = blockState.getValue(AGE) == 0 ? 6 : 10;

            return switch ((DoubleBlockHalf)blockState.getValue(HALF)) {
                case LOWER -> Block.column(i1, -1.0, Math.min(16, -1 + i));
                case UPPER -> Block.column(i1, 0.0, Math.max(0, -1 + i - 16));
            };
        });
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState();
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.shapes.apply(state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER) {
            return state.getValue(AGE) == 0 ? SHAPE_BULB : SHAPE_CROP;
        } else {
            return Shapes.empty();
        }
    }

    @Override
    public BlockState updateShape(
        BlockState state,
        LevelReader level,
        ScheduledTickAccess scheduledTickAccess,
        BlockPos pos,
        Direction direction,
        BlockPos neighborPos,
        BlockState neighborState,
        RandomSource random
    ) {
        if (isDouble(state.getValue(AGE))) {
            return super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
        } else {
            return state.canSurvive(level, pos) ? state : Blocks.AIR.defaultBlockState();
        }
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return (!isLower(state) || sufficientLight(level, pos)) && super.canSurvive(state, level, pos);
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(Blocks.FARMLAND);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
        super.createBlockStateDefinition(builder);
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean pastEdges) {
        if (!new io.papermc.paper.event.entity.EntityInsideBlockEvent(entity.getBukkitEntity(), org.bukkit.craftbukkit.block.CraftBlock.at(level, pos)).callEvent()) { return; } // Paper - Add EntityInsideBlockEvent
        if (level instanceof ServerLevel serverLevel && entity instanceof Ravager && serverLevel.getGameRules().get(GameRules.MOB_GRIEFING)) {
            serverLevel.destroyBlock(pos, true, entity);
        }
    }

    @Override
    public boolean canBeReplaced(BlockState state, BlockPlaceContext useContext) {
        return false;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER && !this.isMaxAge(state);
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        float growthSpeed = CropBlock.getGrowthSpeed(this, level, pos);
        boolean flag = random.nextFloat() < (level.spigotConfig.pitcherPlantModifier / (100.0F * (Math.floor(25.0F / growthSpeed) + 1))); // Paper - Fix Spigot growth modifiers
        if (flag) {
            this.grow(level, state, pos, 1);
        }
    }

    private void grow(ServerLevel level, BlockState state, BlockPos pos, int ageIncrement) {
        int min = Math.min(state.getValue(AGE) + ageIncrement, 4);
        if (this.canGrow(level, pos, state, min)) {
            BlockState blockState = state.setValue(AGE, min);
            if (!org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockGrowEvent(level, pos, blockState, Block.UPDATE_CLIENTS)) return; // Paper
            if (isDouble(min)) {
                level.setBlock(pos.above(), blockState.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
            }
        }
    }

    private static boolean canGrowInto(LevelReader level, BlockPos pos) {
        BlockState blockState = level.getBlockState(pos);
        return blockState.isAir() || blockState.is(Blocks.PITCHER_CROP);
    }

    private static boolean sufficientLight(LevelReader level, BlockPos pos) {
        return CropBlock.hasSufficientLight(level, pos);
    }

    private static boolean isLower(BlockState state) {
        return state.is(Blocks.PITCHER_CROP) && state.getValue(HALF) == DoubleBlockHalf.LOWER;
    }

    private static boolean isDouble(int age) {
        return age >= 3;
    }

    private boolean canGrow(LevelReader level, BlockPos pos, BlockState state, int age) {
        return !this.isMaxAge(state) && sufficientLight(level, pos) && (!isDouble(age) || canGrowInto(level, pos.above()));
    }

    private boolean isMaxAge(BlockState state) {
        return state.getValue(AGE) >= 4;
    }

    private PitcherCropBlock.@Nullable PosAndState getLowerHalf(LevelReader level, BlockPos pos, BlockState state) {
        if (isLower(state)) {
            return new PitcherCropBlock.PosAndState(pos, state);
        } else {
            BlockPos blockPos = pos.below();
            BlockState blockState = level.getBlockState(blockPos);
            return isLower(blockState) ? new PitcherCropBlock.PosAndState(blockPos, blockState) : null;
        }
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        PitcherCropBlock.PosAndState lowerHalf = this.getLowerHalf(level, pos, state);
        return lowerHalf != null && this.canGrow(level, lowerHalf.pos, lowerHalf.state, lowerHalf.state.getValue(AGE) + 1);
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        PitcherCropBlock.PosAndState lowerHalf = this.getLowerHalf(level, pos, state);
        if (lowerHalf != null) {
            this.grow(level, lowerHalf.state, lowerHalf.pos, 1);
        }
    }

    record PosAndState(BlockPos pos, BlockState state) {
    }
}
