package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class FireBlock extends BaseFireBlock {
    public static final MapCodec<FireBlock> CODEC = simpleCodec(FireBlock::new);
    public static final int MAX_AGE = 15;
    public static final IntegerProperty AGE = BlockStateProperties.AGE_15;
    public static final BooleanProperty NORTH = PipeBlock.NORTH;
    public static final BooleanProperty EAST = PipeBlock.EAST;
    public static final BooleanProperty SOUTH = PipeBlock.SOUTH;
    public static final BooleanProperty WEST = PipeBlock.WEST;
    public static final BooleanProperty UP = PipeBlock.UP;
    public static final Map<Direction, BooleanProperty> PROPERTY_BY_DIRECTION = PipeBlock.PROPERTY_BY_DIRECTION
        .entrySet()
        .stream()
        .filter(directionEntry -> directionEntry.getKey() != Direction.DOWN)
        .collect(Util.toMap());
    private final Function<BlockState, VoxelShape> shapes;
    private static final int IGNITE_INSTANT = 60;
    private static final int IGNITE_EASY = 30;
    private static final int IGNITE_MEDIUM = 15;
    private static final int IGNITE_HARD = 5;
    private static final int BURN_INSTANT = 100;
    private static final int BURN_EASY = 60;
    private static final int BURN_MEDIUM = 20;
    private static final int BURN_HARD = 5;
    public final Object2IntMap<Block> igniteOdds = new Object2IntOpenHashMap<>();
    private final Object2IntMap<Block> burnOdds = new Object2IntOpenHashMap<>();

    @Override
    public MapCodec<FireBlock> codec() {
        return CODEC;
    }

    public FireBlock(BlockBehaviour.Properties properties) {
        super(properties, 1.0F);
        this.registerDefaultState(
            this.stateDefinition
                .any()
                .setValue(AGE, 0)
                .setValue(NORTH, false)
                .setValue(EAST, false)
                .setValue(SOUTH, false)
                .setValue(WEST, false)
                .setValue(UP, false)
        );
        this.shapes = this.makeShapes();
    }

    private Function<BlockState, VoxelShape> makeShapes() {
        Map<Direction, VoxelShape> map = Shapes.rotateAll(Block.boxZ(16.0, 0.0, 1.0));
        return this.getShapeForEachState(blockState -> {
            VoxelShape voxelShape = Shapes.empty();

            for (Entry<Direction, BooleanProperty> entry : PROPERTY_BY_DIRECTION.entrySet()) {
                if (blockState.getValue(entry.getValue())) {
                    voxelShape = Shapes.or(voxelShape, map.get(entry.getKey()));
                }
            }

            return voxelShape.isEmpty() ? SHAPE : voxelShape;
        }, AGE);
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
        // CraftBukkit start
        if (!(level instanceof ServerLevel)) return this.canSurvive(state, level, pos) ? (BlockState) this.getStateWithAge(level, pos, (Integer) state.getValue(FireBlock.AGE)) : Blocks.AIR.defaultBlockState(); // Paper - don't fire events in world generation
        if (!this.canSurvive(state, level, pos)) {
            // Suppress during worldgen
            if (!(level instanceof Level world1)) {
                return Blocks.AIR.defaultBlockState();
            }
            org.bukkit.craftbukkit.block.CraftBlockState blockState = org.bukkit.craftbukkit.block.CraftBlockStates.getBlockState(world1, pos);
            blockState.setData(Blocks.AIR.defaultBlockState());

            org.bukkit.event.block.BlockFadeEvent event = new org.bukkit.event.block.BlockFadeEvent(blockState.getBlock(), blockState);
            world1.getCraftServer().getPluginManager().callEvent(event);

            if (!event.isCancelled()) {
                return blockState.getHandle();
            }
        }
        return this.getStateWithAge(level, pos, (Integer) state.getValue(FireBlock.AGE)); // Paper - don't fire events in world generation; diff on change, see "don't fire events in world generation"
        // CraftBukkit end
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.shapes.apply(state);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.getStateForPlacement(context.getLevel(), context.getClickedPos());
    }

    protected BlockState getStateForPlacement(BlockGetter level, BlockPos pos) {
        BlockPos blockPos = pos.below();
        BlockState blockState = level.getBlockState(blockPos);
        if (!this.canBurn(blockState) && !blockState.isFaceSturdy(level, blockPos, Direction.UP)) {
            BlockState blockState1 = this.defaultBlockState();

            for (Direction direction : Direction.values()) {
                BooleanProperty booleanProperty = PROPERTY_BY_DIRECTION.get(direction);
                if (booleanProperty != null) {
                    blockState1 = blockState1.setValue(booleanProperty, this.canBurn(level.getBlockState(pos.relative(direction))));
                }
            }

            return blockState1;
        } else {
            return this.defaultBlockState();
        }
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos blockPos = pos.below();
        return level.getBlockState(blockPos).isFaceSturdy(level, blockPos, Direction.UP) || this.isValidFireLocation(level, pos);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.scheduleTick(pos, this, getFireTickDelay(level)); // Paper - Add fire-tick-delay option
        if (level.canSpreadFireAround(pos)) {
            if (!state.canSurvive(level, pos)) {
                this.fireExtinguished(level, pos); // CraftBukkit - invalid place location
            }

            BlockState blockState = level.getBlockState(pos.below());
            boolean isTag = blockState.is(level.dimensionType().infiniburn());
            int ageValue = state.getValue(AGE);
            if (!isTag && level.isRaining() && this.isNearRain(level, pos) && random.nextFloat() < 0.2F + ageValue * 0.03F) {
                this.fireExtinguished(level, pos); // CraftBukkit - extinguished by rain
            } else {
                int min = Math.min(15, ageValue + random.nextInt(3) / 2);
                if (ageValue != min) {
                    state = state.setValue(AGE, min);
                    level.setBlock(pos, state, Block.UPDATE_NONE);
                }

                if (!isTag) {
                    if (!this.isValidFireLocation(level, pos)) {
                        BlockPos blockPos = pos.below();
                        if (!level.getBlockState(blockPos).isFaceSturdy(level, blockPos, Direction.UP) || ageValue > 3) {
                            this.fireExtinguished(level, pos); // CraftBukkit
                        }

                        return;
                    }

                    if (ageValue == 15 && random.nextInt(4) == 0 && !this.canBurn(level.getBlockState(pos.below()))) {
                        this.fireExtinguished(level, pos); // CraftBukkit
                        return;
                    }
                }

                boolean value = level.environmentAttributes().getValue(EnvironmentAttributes.INCREASED_FIRE_BURNOUT, pos);
                int i = value ? -50 : 0;
                // CraftBukkit start - add source blockPos to burn calls
                this.checkBurnOut(level, pos.east(), 300 + i, random, ageValue, pos);
                this.checkBurnOut(level, pos.west(), 300 + i, random, ageValue, pos);
                this.checkBurnOut(level, pos.below(), 250 + i, random, ageValue, pos);
                this.checkBurnOut(level, pos.above(), 250 + i, random, ageValue, pos);
                this.checkBurnOut(level, pos.north(), 300 + i, random, ageValue, pos);
                this.checkBurnOut(level, pos.south(), 300 + i, random, ageValue, pos);
                // CraftBukkit end - add source blockPos to burn calls
                BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

                for (int i1 = -1; i1 <= 1; i1++) {
                    for (int i2 = -1; i2 <= 1; i2++) {
                        for (int i3 = -1; i3 <= 4; i3++) {
                            if (i1 != 0 || i3 != 0 || i2 != 0) {
                                int i4 = 100;
                                if (i3 > 1) {
                                    i4 += (i3 - 1) * 100;
                                }

                                mutableBlockPos.setWithOffset(pos, i1, i3, i2);
                                int igniteOdds = this.getIgniteOdds(level, mutableBlockPos);
                                if (igniteOdds > 0) {
                                    int i5 = (igniteOdds + 40 + level.getDifficulty().getId() * 7) / (ageValue + 30);
                                    if (value) {
                                        i5 /= 2;
                                    }

                                    if (i5 > 0 && random.nextInt(i4) <= i5 && (!level.isRaining() || !this.isNearRain(level, mutableBlockPos))) {
                                        int min1 = Math.min(15, ageValue + random.nextInt(5) / 4);
                                        // CraftBukkit start - Call to stop spread of fire
                                        if (!level.getBlockState(mutableBlockPos).is(Blocks.FIRE)) {
                                            if (org.bukkit.craftbukkit.event.CraftEventFactory.callBlockIgniteEvent(level, mutableBlockPos, pos).isCancelled()) {
                                                continue;
                                            }

                                            org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockSpreadEvent(level, pos, mutableBlockPos, this.getStateWithAge(level, mutableBlockPos, min1), UPDATE_ALL); // CraftBukkit
                                        }
                                        // CraftBukkit end
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    protected boolean isNearRain(Level level, BlockPos pos) {
        return level.isRainingAt(pos)
            || level.isRainingAt(pos.west())
            || level.isRainingAt(pos.east())
            || level.isRainingAt(pos.north())
            || level.isRainingAt(pos.south());
    }

    private int getBurnOdds(BlockState state) {
        return state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED)
            ? 0
            : this.burnOdds.getInt(state.getBlock());
    }

    private int getIgniteOdds(BlockState state) {
        return state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED)
            ? 0
            : this.igniteOdds.getInt(state.getBlock());
    }

    private void checkBurnOut(Level level, BlockPos pos, int chance, RandomSource random, int age, BlockPos sourcePos) { // CraftBukkit add sourcePos
        int burnOdds = this.getBurnOdds(level.getBlockState(pos));
        if (random.nextInt(chance) < burnOdds) {
            BlockState blockState = level.getBlockState(pos);

            // CraftBukkit start
            org.bukkit.block.Block burnBlock = org.bukkit.craftbukkit.block.CraftBlock.at(level, pos);
            org.bukkit.block.Block sourceBlock = org.bukkit.craftbukkit.block.CraftBlock.at(level, sourcePos);

            org.bukkit.event.block.BlockBurnEvent event = new org.bukkit.event.block.BlockBurnEvent(burnBlock, sourceBlock);
            if (!event.callEvent()) {
                return;
            }

            if (blockState.getBlock() instanceof TntBlock && !org.bukkit.craftbukkit.event.CraftEventFactory.callTNTPrimeEvent(level, pos, org.bukkit.event.block.TNTPrimeEvent.PrimeCause.FIRE, null, sourcePos)) {
                return;
            }
            // CraftBukkit end
            if (random.nextInt(age + 10) < 5 && !level.isRainingAt(pos)) {
                int min = Math.min(age + random.nextInt(5) / 4, 15);
                level.setBlock(pos, this.getStateWithAge(level, pos, min), Block.UPDATE_ALL);
            } else {
                if (!blockState.is(Blocks.TNT)) level.removeBlock(pos, false); // Paper - TNTPrimeEvent; We might be cancelling it below, move the setAir down
            }

            Block block = blockState.getBlock();
            if (block instanceof TntBlock) {
                // Paper start - TNTPrimeEvent
                org.bukkit.block.Block tntBlock = org.bukkit.craftbukkit.block.CraftBlock.at(level, pos);
                if (!new com.destroystokyo.paper.event.block.TNTPrimeEvent(tntBlock, com.destroystokyo.paper.event.block.TNTPrimeEvent.PrimeReason.FIRE, null).callEvent()) {
                    return;
                }
                level.removeBlock(pos, false);
                // Paper end - TNTPrimeEvent
                TntBlock.prime(level, pos);
            }
        }
    }

    private BlockState getStateWithAge(LevelReader level, BlockPos pos, int age) {
        BlockState state = getState(level, pos);
        return state.is(Blocks.FIRE) ? state.setValue(AGE, age) : state;
    }

    private boolean isValidFireLocation(BlockGetter level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (this.canBurn(level.getBlockState(pos.relative(direction)))) {
                return true;
            }
        }

        return false;
    }

    private int getIgniteOdds(LevelReader level, BlockPos pos) {
        if (!level.isEmptyBlock(pos)) {
            return 0;
        } else {
            int i = 0;

            for (Direction direction : Direction.values()) {
                BlockState blockState = level.getBlockState(pos.relative(direction));
                i = Math.max(this.getIgniteOdds(blockState), i);
            }

            return i;
        }
    }

    @Override
    protected boolean canBurn(BlockState state) {
        return this.getIgniteOdds(state) > 0;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston, net.minecraft.world.item.context.UseOnContext context) {
        super.onPlace(state, level, pos, oldState, movedByPiston, context);
        // CraftBukkit end
        level.scheduleTick(pos, this, FireBlock.getFireTickDelay(level)); // Paper - Add fire-tick-delay option
    }

    private static int getFireTickDelay(Level level) { // Paper - Add fire-tick-delay option
        return level.paperConfig().environment.fireTickDelay + level.random.nextInt(10); // Paper - Add fire-tick-delay option
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE, NORTH, EAST, SOUTH, WEST, UP);
    }

    public void setFlammable(Block block, int encouragement, int flammability) {
        this.igniteOdds.put(block, encouragement);
        this.burnOdds.put(block, flammability);
    }

    public static void bootStrap() {
        FireBlock fireBlock = (FireBlock)Blocks.FIRE;
        fireBlock.setFlammable(Blocks.OAK_PLANKS, 5, 20);
        fireBlock.setFlammable(Blocks.SPRUCE_PLANKS, 5, 20);
        fireBlock.setFlammable(Blocks.BIRCH_PLANKS, 5, 20);
        fireBlock.setFlammable(Blocks.JUNGLE_PLANKS, 5, 20);
        fireBlock.setFlammable(Blocks.ACACIA_PLANKS, 5, 20);
        fireBlock.setFlammable(Blocks.CHERRY_PLANKS, 5, 20);
        fireBlock.setFlammable(Blocks.DARK_OAK_PLANKS, 5, 20);
        fireBlock.setFlammable(Blocks.PALE_OAK_PLANKS, 5, 20);
        fireBlock.setFlammable(Blocks.MANGROVE_PLANKS, 5, 20);
        fireBlock.setFlammable(Blocks.BAMBOO_PLANKS, 5, 20);
        fireBlock.setFlammable(Blocks.BAMBOO_MOSAIC, 5, 20);
        fireBlock.setFlammable(Blocks.OAK_SLAB, 5, 20);
        fireBlock.setFlammable(Blocks.SPRUCE_SLAB, 5, 20);
        fireBlock.setFlammable(Blocks.BIRCH_SLAB, 5, 20);
        fireBlock.setFlammable(Blocks.JUNGLE_SLAB, 5, 20);
        fireBlock.setFlammable(Blocks.ACACIA_SLAB, 5, 20);
        fireBlock.setFlammable(Blocks.CHERRY_SLAB, 5, 20);
        fireBlock.setFlammable(Blocks.DARK_OAK_SLAB, 5, 20);
        fireBlock.setFlammable(Blocks.PALE_OAK_SLAB, 5, 20);
        fireBlock.setFlammable(Blocks.MANGROVE_SLAB, 5, 20);
        fireBlock.setFlammable(Blocks.BAMBOO_SLAB, 5, 20);
        fireBlock.setFlammable(Blocks.BAMBOO_MOSAIC_SLAB, 5, 20);
        fireBlock.setFlammable(Blocks.OAK_FENCE_GATE, 5, 20);
        fireBlock.setFlammable(Blocks.SPRUCE_FENCE_GATE, 5, 20);
        fireBlock.setFlammable(Blocks.BIRCH_FENCE_GATE, 5, 20);
        fireBlock.setFlammable(Blocks.JUNGLE_FENCE_GATE, 5, 20);
        fireBlock.setFlammable(Blocks.ACACIA_FENCE_GATE, 5, 20);
        fireBlock.setFlammable(Blocks.CHERRY_FENCE_GATE, 5, 20);
        fireBlock.setFlammable(Blocks.DARK_OAK_FENCE_GATE, 5, 20);
        fireBlock.setFlammable(Blocks.PALE_OAK_FENCE_GATE, 5, 20);
        fireBlock.setFlammable(Blocks.MANGROVE_FENCE_GATE, 5, 20);
        fireBlock.setFlammable(Blocks.BAMBOO_FENCE_GATE, 5, 20);
        fireBlock.setFlammable(Blocks.OAK_FENCE, 5, 20);
        fireBlock.setFlammable(Blocks.SPRUCE_FENCE, 5, 20);
        fireBlock.setFlammable(Blocks.BIRCH_FENCE, 5, 20);
        fireBlock.setFlammable(Blocks.JUNGLE_FENCE, 5, 20);
        fireBlock.setFlammable(Blocks.ACACIA_FENCE, 5, 20);
        fireBlock.setFlammable(Blocks.CHERRY_FENCE, 5, 20);
        fireBlock.setFlammable(Blocks.DARK_OAK_FENCE, 5, 20);
        fireBlock.setFlammable(Blocks.PALE_OAK_FENCE, 5, 20);
        fireBlock.setFlammable(Blocks.MANGROVE_FENCE, 5, 20);
        fireBlock.setFlammable(Blocks.BAMBOO_FENCE, 5, 20);
        fireBlock.setFlammable(Blocks.OAK_STAIRS, 5, 20);
        fireBlock.setFlammable(Blocks.BIRCH_STAIRS, 5, 20);
        fireBlock.setFlammable(Blocks.SPRUCE_STAIRS, 5, 20);
        fireBlock.setFlammable(Blocks.JUNGLE_STAIRS, 5, 20);
        fireBlock.setFlammable(Blocks.ACACIA_STAIRS, 5, 20);
        fireBlock.setFlammable(Blocks.CHERRY_STAIRS, 5, 20);
        fireBlock.setFlammable(Blocks.DARK_OAK_STAIRS, 5, 20);
        fireBlock.setFlammable(Blocks.PALE_OAK_STAIRS, 5, 20);
        fireBlock.setFlammable(Blocks.MANGROVE_STAIRS, 5, 20);
        fireBlock.setFlammable(Blocks.BAMBOO_STAIRS, 5, 20);
        fireBlock.setFlammable(Blocks.BAMBOO_MOSAIC_STAIRS, 5, 20);
        fireBlock.setFlammable(Blocks.OAK_LOG, 5, 5);
        fireBlock.setFlammable(Blocks.SPRUCE_LOG, 5, 5);
        fireBlock.setFlammable(Blocks.BIRCH_LOG, 5, 5);
        fireBlock.setFlammable(Blocks.JUNGLE_LOG, 5, 5);
        fireBlock.setFlammable(Blocks.ACACIA_LOG, 5, 5);
        fireBlock.setFlammable(Blocks.CHERRY_LOG, 5, 5);
        fireBlock.setFlammable(Blocks.PALE_OAK_LOG, 5, 5);
        fireBlock.setFlammable(Blocks.DARK_OAK_LOG, 5, 5);
        fireBlock.setFlammable(Blocks.MANGROVE_LOG, 5, 5);
        fireBlock.setFlammable(Blocks.BAMBOO_BLOCK, 5, 5);
        fireBlock.setFlammable(Blocks.STRIPPED_OAK_LOG, 5, 5);
        fireBlock.setFlammable(Blocks.STRIPPED_SPRUCE_LOG, 5, 5);
        fireBlock.setFlammable(Blocks.STRIPPED_BIRCH_LOG, 5, 5);
        fireBlock.setFlammable(Blocks.STRIPPED_JUNGLE_LOG, 5, 5);
        fireBlock.setFlammable(Blocks.STRIPPED_ACACIA_LOG, 5, 5);
        fireBlock.setFlammable(Blocks.STRIPPED_CHERRY_LOG, 5, 5);
        fireBlock.setFlammable(Blocks.STRIPPED_DARK_OAK_LOG, 5, 5);
        fireBlock.setFlammable(Blocks.STRIPPED_PALE_OAK_LOG, 5, 5);
        fireBlock.setFlammable(Blocks.STRIPPED_MANGROVE_LOG, 5, 5);
        fireBlock.setFlammable(Blocks.STRIPPED_BAMBOO_BLOCK, 5, 5);
        fireBlock.setFlammable(Blocks.STRIPPED_OAK_WOOD, 5, 5);
        fireBlock.setFlammable(Blocks.STRIPPED_SPRUCE_WOOD, 5, 5);
        fireBlock.setFlammable(Blocks.STRIPPED_BIRCH_WOOD, 5, 5);
        fireBlock.setFlammable(Blocks.STRIPPED_JUNGLE_WOOD, 5, 5);
        fireBlock.setFlammable(Blocks.STRIPPED_ACACIA_WOOD, 5, 5);
        fireBlock.setFlammable(Blocks.STRIPPED_CHERRY_WOOD, 5, 5);
        fireBlock.setFlammable(Blocks.STRIPPED_DARK_OAK_WOOD, 5, 5);
        fireBlock.setFlammable(Blocks.STRIPPED_PALE_OAK_WOOD, 5, 5);
        fireBlock.setFlammable(Blocks.STRIPPED_MANGROVE_WOOD, 5, 5);
        fireBlock.setFlammable(Blocks.OAK_WOOD, 5, 5);
        fireBlock.setFlammable(Blocks.SPRUCE_WOOD, 5, 5);
        fireBlock.setFlammable(Blocks.BIRCH_WOOD, 5, 5);
        fireBlock.setFlammable(Blocks.JUNGLE_WOOD, 5, 5);
        fireBlock.setFlammable(Blocks.ACACIA_WOOD, 5, 5);
        fireBlock.setFlammable(Blocks.CHERRY_WOOD, 5, 5);
        fireBlock.setFlammable(Blocks.PALE_OAK_WOOD, 5, 5);
        fireBlock.setFlammable(Blocks.DARK_OAK_WOOD, 5, 5);
        fireBlock.setFlammable(Blocks.MANGROVE_WOOD, 5, 5);
        fireBlock.setFlammable(Blocks.MANGROVE_ROOTS, 5, 20);
        fireBlock.setFlammable(Blocks.OAK_LEAVES, 30, 60);
        fireBlock.setFlammable(Blocks.SPRUCE_LEAVES, 30, 60);
        fireBlock.setFlammable(Blocks.BIRCH_LEAVES, 30, 60);
        fireBlock.setFlammable(Blocks.JUNGLE_LEAVES, 30, 60);
        fireBlock.setFlammable(Blocks.ACACIA_LEAVES, 30, 60);
        fireBlock.setFlammable(Blocks.CHERRY_LEAVES, 30, 60);
        fireBlock.setFlammable(Blocks.DARK_OAK_LEAVES, 30, 60);
        fireBlock.setFlammable(Blocks.PALE_OAK_LEAVES, 30, 60);
        fireBlock.setFlammable(Blocks.MANGROVE_LEAVES, 30, 60);
        fireBlock.setFlammable(Blocks.BOOKSHELF, 30, 20);
        fireBlock.setFlammable(Blocks.TNT, 15, 100);
        fireBlock.setFlammable(Blocks.SHORT_GRASS, 60, 100);
        fireBlock.setFlammable(Blocks.FERN, 60, 100);
        fireBlock.setFlammable(Blocks.DEAD_BUSH, 60, 100);
        fireBlock.setFlammable(Blocks.SHORT_DRY_GRASS, 60, 100);
        fireBlock.setFlammable(Blocks.TALL_DRY_GRASS, 60, 100);
        fireBlock.setFlammable(Blocks.SUNFLOWER, 60, 100);
        fireBlock.setFlammable(Blocks.LILAC, 60, 100);
        fireBlock.setFlammable(Blocks.ROSE_BUSH, 60, 100);
        fireBlock.setFlammable(Blocks.PEONY, 60, 100);
        fireBlock.setFlammable(Blocks.TALL_GRASS, 60, 100);
        fireBlock.setFlammable(Blocks.LARGE_FERN, 60, 100);
        fireBlock.setFlammable(Blocks.DANDELION, 60, 100);
        fireBlock.setFlammable(Blocks.POPPY, 60, 100);
        fireBlock.setFlammable(Blocks.OPEN_EYEBLOSSOM, 60, 100);
        fireBlock.setFlammable(Blocks.CLOSED_EYEBLOSSOM, 60, 100);
        fireBlock.setFlammable(Blocks.BLUE_ORCHID, 60, 100);
        fireBlock.setFlammable(Blocks.ALLIUM, 60, 100);
        fireBlock.setFlammable(Blocks.AZURE_BLUET, 60, 100);
        fireBlock.setFlammable(Blocks.RED_TULIP, 60, 100);
        fireBlock.setFlammable(Blocks.ORANGE_TULIP, 60, 100);
        fireBlock.setFlammable(Blocks.WHITE_TULIP, 60, 100);
        fireBlock.setFlammable(Blocks.PINK_TULIP, 60, 100);
        fireBlock.setFlammable(Blocks.OXEYE_DAISY, 60, 100);
        fireBlock.setFlammable(Blocks.CORNFLOWER, 60, 100);
        fireBlock.setFlammable(Blocks.LILY_OF_THE_VALLEY, 60, 100);
        fireBlock.setFlammable(Blocks.TORCHFLOWER, 60, 100);
        fireBlock.setFlammable(Blocks.PITCHER_PLANT, 60, 100);
        fireBlock.setFlammable(Blocks.WITHER_ROSE, 60, 100);
        fireBlock.setFlammable(Blocks.PINK_PETALS, 60, 100);
        fireBlock.setFlammable(Blocks.WILDFLOWERS, 60, 100);
        fireBlock.setFlammable(Blocks.LEAF_LITTER, 60, 100);
        fireBlock.setFlammable(Blocks.CACTUS_FLOWER, 60, 100);
        fireBlock.setFlammable(Blocks.WHITE_WOOL, 30, 60);
        fireBlock.setFlammable(Blocks.ORANGE_WOOL, 30, 60);
        fireBlock.setFlammable(Blocks.MAGENTA_WOOL, 30, 60);
        fireBlock.setFlammable(Blocks.LIGHT_BLUE_WOOL, 30, 60);
        fireBlock.setFlammable(Blocks.YELLOW_WOOL, 30, 60);
        fireBlock.setFlammable(Blocks.LIME_WOOL, 30, 60);
        fireBlock.setFlammable(Blocks.PINK_WOOL, 30, 60);
        fireBlock.setFlammable(Blocks.GRAY_WOOL, 30, 60);
        fireBlock.setFlammable(Blocks.LIGHT_GRAY_WOOL, 30, 60);
        fireBlock.setFlammable(Blocks.CYAN_WOOL, 30, 60);
        fireBlock.setFlammable(Blocks.PURPLE_WOOL, 30, 60);
        fireBlock.setFlammable(Blocks.BLUE_WOOL, 30, 60);
        fireBlock.setFlammable(Blocks.BROWN_WOOL, 30, 60);
        fireBlock.setFlammable(Blocks.GREEN_WOOL, 30, 60);
        fireBlock.setFlammable(Blocks.RED_WOOL, 30, 60);
        fireBlock.setFlammable(Blocks.BLACK_WOOL, 30, 60);
        fireBlock.setFlammable(Blocks.VINE, 15, 100);
        fireBlock.setFlammable(Blocks.COAL_BLOCK, 5, 5);
        fireBlock.setFlammable(Blocks.HAY_BLOCK, 60, 20);
        fireBlock.setFlammable(Blocks.TARGET, 15, 20);
        fireBlock.setFlammable(Blocks.WHITE_CARPET, 60, 20);
        fireBlock.setFlammable(Blocks.ORANGE_CARPET, 60, 20);
        fireBlock.setFlammable(Blocks.MAGENTA_CARPET, 60, 20);
        fireBlock.setFlammable(Blocks.LIGHT_BLUE_CARPET, 60, 20);
        fireBlock.setFlammable(Blocks.YELLOW_CARPET, 60, 20);
        fireBlock.setFlammable(Blocks.LIME_CARPET, 60, 20);
        fireBlock.setFlammable(Blocks.PINK_CARPET, 60, 20);
        fireBlock.setFlammable(Blocks.GRAY_CARPET, 60, 20);
        fireBlock.setFlammable(Blocks.LIGHT_GRAY_CARPET, 60, 20);
        fireBlock.setFlammable(Blocks.CYAN_CARPET, 60, 20);
        fireBlock.setFlammable(Blocks.PURPLE_CARPET, 60, 20);
        fireBlock.setFlammable(Blocks.BLUE_CARPET, 60, 20);
        fireBlock.setFlammable(Blocks.BROWN_CARPET, 60, 20);
        fireBlock.setFlammable(Blocks.GREEN_CARPET, 60, 20);
        fireBlock.setFlammable(Blocks.RED_CARPET, 60, 20);
        fireBlock.setFlammable(Blocks.BLACK_CARPET, 60, 20);
        fireBlock.setFlammable(Blocks.PALE_MOSS_BLOCK, 5, 100);
        fireBlock.setFlammable(Blocks.PALE_MOSS_CARPET, 5, 100);
        fireBlock.setFlammable(Blocks.PALE_HANGING_MOSS, 5, 100);
        fireBlock.setFlammable(Blocks.DRIED_KELP_BLOCK, 30, 60);
        fireBlock.setFlammable(Blocks.BAMBOO, 60, 60);
        fireBlock.setFlammable(Blocks.SCAFFOLDING, 60, 60);
        fireBlock.setFlammable(Blocks.LECTERN, 30, 20);
        fireBlock.setFlammable(Blocks.COMPOSTER, 5, 20);
        fireBlock.setFlammable(Blocks.SWEET_BERRY_BUSH, 60, 100);
        fireBlock.setFlammable(Blocks.BEEHIVE, 5, 20);
        fireBlock.setFlammable(Blocks.BEE_NEST, 30, 20);
        fireBlock.setFlammable(Blocks.AZALEA_LEAVES, 30, 60);
        fireBlock.setFlammable(Blocks.FLOWERING_AZALEA_LEAVES, 30, 60);
        fireBlock.setFlammable(Blocks.CAVE_VINES, 15, 60);
        fireBlock.setFlammable(Blocks.CAVE_VINES_PLANT, 15, 60);
        fireBlock.setFlammable(Blocks.SPORE_BLOSSOM, 60, 100);
        fireBlock.setFlammable(Blocks.AZALEA, 30, 60);
        fireBlock.setFlammable(Blocks.FLOWERING_AZALEA, 30, 60);
        fireBlock.setFlammable(Blocks.BIG_DRIPLEAF, 60, 100);
        fireBlock.setFlammable(Blocks.BIG_DRIPLEAF_STEM, 60, 100);
        fireBlock.setFlammable(Blocks.SMALL_DRIPLEAF, 60, 100);
        fireBlock.setFlammable(Blocks.HANGING_ROOTS, 30, 60);
        fireBlock.setFlammable(Blocks.GLOW_LICHEN, 15, 100);
        fireBlock.setFlammable(Blocks.FIREFLY_BUSH, 60, 100);
        fireBlock.setFlammable(Blocks.BUSH, 60, 100);
        fireBlock.setFlammable(Blocks.ACACIA_SHELF, 30, 20);
        fireBlock.setFlammable(Blocks.BAMBOO_SHELF, 30, 20);
        fireBlock.setFlammable(Blocks.BIRCH_SHELF, 30, 20);
        fireBlock.setFlammable(Blocks.CHERRY_SHELF, 30, 20);
        fireBlock.setFlammable(Blocks.DARK_OAK_SHELF, 30, 20);
        fireBlock.setFlammable(Blocks.JUNGLE_SHELF, 30, 20);
        fireBlock.setFlammable(Blocks.MANGROVE_SHELF, 30, 20);
        fireBlock.setFlammable(Blocks.OAK_SHELF, 30, 20);
        fireBlock.setFlammable(Blocks.PALE_OAK_SHELF, 30, 20);
        fireBlock.setFlammable(Blocks.SPRUCE_SHELF, 30, 20);
    }
}
