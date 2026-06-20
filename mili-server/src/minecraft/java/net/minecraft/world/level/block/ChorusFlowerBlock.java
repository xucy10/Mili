package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.projectile.Projectile;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class ChorusFlowerBlock extends Block {
    public static final MapCodec<ChorusFlowerBlock> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                BuiltInRegistries.BLOCK.byNameCodec().fieldOf("plant").forGetter(chorusFlowerBlock -> chorusFlowerBlock.plant), propertiesCodec()
            )
            .apply(instance, ChorusFlowerBlock::new)
    );
    public static final int DEAD_AGE = 5;
    public static final IntegerProperty AGE = BlockStateProperties.AGE_5;
    private static final VoxelShape SHAPE_BLOCK_SUPPORT = Block.column(14.0, 0.0, 15.0);
    private final Block plant;

    @Override
    public MapCodec<ChorusFlowerBlock> codec() {
        return CODEC;
    }

    protected ChorusFlowerBlock(Block plant, BlockBehaviour.Properties properties) {
        super(properties);
        this.plant = plant;
        this.registerDefaultState(this.stateDefinition.any().setValue(AGE, 0));
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.canSurvive(level, pos)) {
            level.destroyBlock(pos, true);
        }
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(AGE) < 5;
    }

    @Override
    public VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return SHAPE_BLOCK_SUPPORT;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos blockPos = pos.above();
        if (level.isEmptyBlock(blockPos) && blockPos.getY() <= level.getMaxY()) {
            int ageValue = state.getValue(AGE);
            if (ageValue < 5) {
                boolean flag = false;
                boolean flag1 = false;
                BlockState blockState = level.getBlockState(pos.below());
                if (blockState.is(Blocks.END_STONE)) {
                    flag = true;
                } else if (blockState.is(this.plant)) {
                    int i = 1;

                    for (int i1 = 0; i1 < 4; i1++) {
                        BlockState blockState1 = level.getBlockState(pos.below(i + 1));
                        if (!blockState1.is(this.plant)) {
                            if (blockState1.is(Blocks.END_STONE)) {
                                flag1 = true;
                            }
                            break;
                        }

                        i++;
                    }

                    if (i < 2 || i <= random.nextInt(flag1 ? 5 : 4)) {
                        flag = true;
                    }
                } else if (blockState.isAir()) {
                    flag = true;
                }

                if (flag && allNeighborsEmpty(level, blockPos, null) && level.isEmptyBlock(pos.above(2))) {
                    if (org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockSpreadEvent(level, pos, blockPos, this.defaultBlockState().setValue(AGE, ageValue), Block.UPDATE_CLIENTS)) { // CraftBukkit - add event
                    level.setBlock(pos, ChorusPlantBlock.getStateWithConnections(level, pos, this.plant.defaultBlockState()), Block.UPDATE_CLIENTS);
                    this.placeGrownFlower(level, blockPos, ageValue);
                    } // CraftBukkit
                } else if (ageValue < 4) {
                    int i = random.nextInt(4);
                    if (flag1) {
                        i++;
                    }

                    boolean flag2 = false;

                    for (int i2 = 0; i2 < i; i2++) {
                        Direction randomDirection = Direction.Plane.HORIZONTAL.getRandomDirection(random);
                        BlockPos blockPos1 = pos.relative(randomDirection);
                        if (level.isEmptyBlock(blockPos1)
                            && level.isEmptyBlock(blockPos1.below())
                            && allNeighborsEmpty(level, blockPos1, randomDirection.getOpposite())) {
                            if (org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockSpreadEvent(level, pos, blockPos1, this.defaultBlockState().setValue(AGE, ageValue + 1), Block.UPDATE_CLIENTS)) { // CraftBukkit - add event
                            this.placeGrownFlower(level, blockPos1, ageValue + 1);
                            flag2 = true;
                            } // CraftBukkit
                        }
                    }

                    if (flag2) {
                        level.setBlock(pos, ChorusPlantBlock.getStateWithConnections(level, pos, this.plant.defaultBlockState()), Block.UPDATE_CLIENTS);
                    } else {
                        // CraftBukkit start - add event
                        if (org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockGrowEvent(level, pos, this.defaultBlockState().setValue(AGE, 5), Block.UPDATE_CLIENTS)) {
                        this.placeDeadFlower(level, pos);
                        }
                        // CraftBukkit end
                    }
                } else {
                    // CraftBukkit start - add event
                    if (org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockGrowEvent(level, pos, this.defaultBlockState().setValue(AGE, 5), Block.UPDATE_CLIENTS)) {
                    this.placeDeadFlower(level, pos);
                    }
                    // CraftBukkit end
                }
            }
        }
    }

    private void placeGrownFlower(Level level, BlockPos pos, int age) {
        // level.setBlock(pos, this.defaultBlockState().setValue(AGE, age), Block.UPDATE_CLIENTS); // Paper - already done above in the event call
        level.levelEvent(LevelEvent.SOUND_CHORUS_GROW, pos, 0);
    }

    private void placeDeadFlower(Level level, BlockPos pos) {
        // level.setBlock(pos, this.defaultBlockState().setValue(AGE, 5), Block.UPDATE_CLIENTS); // Paper - already done above in the event call
        level.levelEvent(LevelEvent.SOUND_CHORUS_DEATH, pos, 0);
    }

    private static boolean allNeighborsEmpty(LevelReader level, BlockPos pos, @Nullable Direction excludingSide) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (direction != excludingSide && !level.isEmptyBlock(pos.relative(direction))) {
                return false;
            }
        }

        return true;
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
        if (direction != Direction.UP && !state.canSurvive(level, pos)) {
            scheduledTickAccess.scheduleTick(pos, this, 1);
        }

        return super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState blockState = level.getBlockState(pos.below());
        if (!blockState.is(this.plant) && !blockState.is(Blocks.END_STONE)) {
            if (!blockState.isAir()) {
                return false;
            } else {
                boolean flag = false;

                for (Direction direction : Direction.Plane.HORIZONTAL) {
                    BlockState blockState1 = level.getBlockState(pos.relative(direction));
                    if (blockState1.is(this.plant)) {
                        if (flag) {
                            return false;
                        }

                        flag = true;
                    } else if (!blockState1.isAir()) {
                        return false;
                    }
                }

                return flag;
            }
        } else {
            return true;
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }

    public static void generatePlant(LevelAccessor level, BlockPos pos, RandomSource random, int maxHorizontalDistance) {
        level.setBlock(pos, ChorusPlantBlock.getStateWithConnections(level, pos, Blocks.CHORUS_PLANT.defaultBlockState()), Block.UPDATE_CLIENTS);
        growTreeRecursive(level, pos, random, pos, maxHorizontalDistance, 0);
    }

    private static void growTreeRecursive(
        LevelAccessor level, BlockPos branchPos, RandomSource random, BlockPos originalBranchPos, int maxHorizontalDistance, int iterations
    ) {
        Block block = Blocks.CHORUS_PLANT;
        int i = random.nextInt(4) + 1;
        if (iterations == 0) {
            i++;
        }

        for (int i1 = 0; i1 < i; i1++) {
            BlockPos blockPos = branchPos.above(i1 + 1);
            if (!allNeighborsEmpty(level, blockPos, null)) {
                return;
            }

            level.setBlock(blockPos, ChorusPlantBlock.getStateWithConnections(level, blockPos, block.defaultBlockState()), Block.UPDATE_CLIENTS);
            level.setBlock(blockPos.below(), ChorusPlantBlock.getStateWithConnections(level, blockPos.below(), block.defaultBlockState()), Block.UPDATE_CLIENTS);
        }

        boolean flag = false;
        if (iterations < 4) {
            int randomInt = random.nextInt(4);
            if (iterations == 0) {
                randomInt++;
            }

            for (int i2 = 0; i2 < randomInt; i2++) {
                Direction randomDirection = Direction.Plane.HORIZONTAL.getRandomDirection(random);
                BlockPos blockPos1 = branchPos.above(i).relative(randomDirection);
                if (Math.abs(blockPos1.getX() - originalBranchPos.getX()) < maxHorizontalDistance
                    && Math.abs(blockPos1.getZ() - originalBranchPos.getZ()) < maxHorizontalDistance
                    && level.isEmptyBlock(blockPos1)
                    && level.isEmptyBlock(blockPos1.below())
                    && allNeighborsEmpty(level, blockPos1, randomDirection.getOpposite())) {
                    flag = true;
                    level.setBlock(blockPos1, ChorusPlantBlock.getStateWithConnections(level, blockPos1, block.defaultBlockState()), Block.UPDATE_CLIENTS);
                    level.setBlock(
                        blockPos1.relative(randomDirection.getOpposite()),
                        ChorusPlantBlock.getStateWithConnections(level, blockPos1.relative(randomDirection.getOpposite()), block.defaultBlockState()),
                        Block.UPDATE_CLIENTS
                    );
                    growTreeRecursive(level, blockPos1, random, originalBranchPos, maxHorizontalDistance, iterations + 1);
                }
            }
        }

        if (!flag) {
            level.setBlock(branchPos.above(i), Blocks.CHORUS_FLOWER.defaultBlockState().setValue(AGE, 5), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
        BlockPos blockPos = hit.getBlockPos();
        if (level instanceof ServerLevel serverLevel && projectile.mayInteract(serverLevel, blockPos) && projectile.mayBreak(serverLevel)) {
            // CraftBukkit start
            if (!org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(projectile, blockPos, state.getFluidState().createLegacyBlock())) { // Paper - fix wrong block state
                return;
            }
            // CraftBukkit end
            level.destroyBlock(blockPos, true, projectile);
        }
    }
}
