package net.minecraft.world.level.material;

import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.objects.Object2ByteLinkedOpenHashMap;
import it.unimi.dsi.fastutil.shorts.Short2BooleanMap;
import it.unimi.dsi.fastutil.shorts.Short2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class FlowingFluid extends Fluid {
    public static final BooleanProperty FALLING = BlockStateProperties.FALLING;
    public static final IntegerProperty LEVEL = BlockStateProperties.LEVEL_FLOWING;
    private static final int CACHE_SIZE = 200;
    private static final ThreadLocal<Object2ByteLinkedOpenHashMap<FlowingFluid.BlockStatePairKey>> OCCLUSION_CACHE = ThreadLocal.withInitial(() -> {
        Object2ByteLinkedOpenHashMap<FlowingFluid.BlockStatePairKey> map = new Object2ByteLinkedOpenHashMap<FlowingFluid.BlockStatePairKey>(200) {
            @Override
            protected void rehash(int newSize) {
            }
        };
        map.defaultReturnValue((byte)127);
        return map;
    });
    private final Map<FluidState, VoxelShape> shapes = Maps.newIdentityHashMap();

    // Paper start - fluid method optimisations
    private FluidState sourceFalling;
    private FluidState sourceNotFalling;

    private static final int TOTAL_FLOWING_STATES = FALLING.getPossibleValues().size() * LEVEL.getPossibleValues().size();
    private static final int MIN_LEVEL = LEVEL.getPossibleValues().stream().sorted().findFirst().get().intValue();

    // index = (falling ? 1 : 0) + level*2
    private FluidState[] flowingLookUp;
    private volatile boolean init;

    private static final int COLLISION_OCCLUSION_CACHE_SIZE = 2048;
    private static final ThreadLocal<ca.spottedleaf.moonrise.patches.collisions.util.FluidOcclusionCacheKey[]> COLLISION_OCCLUSION_CACHE = ThreadLocal.withInitial(() -> new ca.spottedleaf.moonrise.patches.collisions.util.FluidOcclusionCacheKey[COLLISION_OCCLUSION_CACHE_SIZE]);


    /**
     * Due to init order, we need to use callbacks to initialise our state
     */
    private void init() {
        synchronized (this) {
            if (this.init) {
                return;
            }
            this.flowingLookUp = new FluidState[TOTAL_FLOWING_STATES];
            final FluidState defaultFlowState = this.getFlowing().defaultFluidState();
            for (int i = 0; i < TOTAL_FLOWING_STATES; ++i) {
                final int falling = i & 1;
                final int level = (i >>> 1) + MIN_LEVEL;

                this.flowingLookUp[i] = defaultFlowState.setValue(FALLING, falling == 1 ? Boolean.TRUE : Boolean.FALSE)
                    .setValue(LEVEL, Integer.valueOf(level));
            }

            final FluidState defaultFallState = this.getSource().defaultFluidState();
            this.sourceFalling = defaultFallState.setValue(FALLING, Boolean.TRUE);
            this.sourceNotFalling = defaultFallState.setValue(FALLING, Boolean.FALSE);

            this.init = true;
        }
    }
    // Paper end - fluid method optimisations

    @Override
    protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> builder) {
        builder.add(FALLING);
    }

    @Override
    public Vec3 getFlow(BlockGetter level, BlockPos pos, FluidState fluidState) {
        double d = 0.0;
        double d1 = 0.0;
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            mutableBlockPos.setWithOffset(pos, direction);
            FluidState fluidState1 = level.getFluidState(mutableBlockPos);
            if (this.affectsFlow(fluidState1)) {
                float ownHeight = fluidState1.getOwnHeight();
                float f = 0.0F;
                if (ownHeight == 0.0F) {
                    if (!level.getBlockState(mutableBlockPos).blocksMotion()) {
                        BlockPos blockPos = mutableBlockPos.below();
                        FluidState fluidState2 = level.getFluidState(blockPos);
                        if (this.affectsFlow(fluidState2)) {
                            ownHeight = fluidState2.getOwnHeight();
                            if (ownHeight > 0.0F) {
                                f = fluidState.getOwnHeight() - (ownHeight - 0.8888889F);
                            }
                        }
                    }
                } else if (ownHeight > 0.0F) {
                    f = fluidState.getOwnHeight() - ownHeight;
                }

                if (f != 0.0F) {
                    d += direction.getStepX() * f;
                    d1 += direction.getStepZ() * f;
                }
            }
        }

        Vec3 vec3 = new Vec3(d, 0.0, d1);
        if (fluidState.getValue(FALLING)) {
            for (Direction direction1 : Direction.Plane.HORIZONTAL) {
                mutableBlockPos.setWithOffset(pos, direction1);
                if (this.isSolidFace(level, mutableBlockPos, direction1) || this.isSolidFace(level, mutableBlockPos.above(), direction1)) {
                    vec3 = vec3.normalize().add(0.0, -6.0, 0.0);
                    break;
                }
            }
        }

        return vec3.normalize();
    }

    private boolean affectsFlow(FluidState state) {
        return state.isEmpty() || state.getType().isSame(this);
    }

    protected boolean isSolidFace(BlockGetter level, BlockPos neighborPos, Direction side) {
        BlockState blockState = level.getBlockState(neighborPos);
        FluidState fluidState = level.getFluidState(neighborPos);
        return !fluidState.getType().isSame(this)
            && (side == Direction.UP || !(blockState.getBlock() instanceof IceBlock) && blockState.isFaceSturdy(level, neighborPos, side));
    }

    protected void spread(ServerLevel level, BlockPos pos, BlockState state, FluidState fluidState) {
        if (!fluidState.isEmpty()) {
            BlockPos blockPos = pos.below();
            BlockState blockState = level.getBlockState(blockPos);
            FluidState fluidState1 = blockState.getFluidState();
            if (this.canMaybePassThrough(level, pos, state, Direction.DOWN, blockPos, blockState, fluidState1)) {
                FluidState newLiquid = this.getNewLiquid(level, blockPos, blockState);
                Fluid type = newLiquid.getType();
                if (fluidState1.canBeReplacedWith(level, blockPos, type, Direction.DOWN) && canHoldSpecificFluid(level, blockPos, blockState, type)) {
                    // CraftBukkit start
                    org.bukkit.block.Block source = org.bukkit.craftbukkit.block.CraftBlock.at(level, pos);
                    org.bukkit.event.block.BlockFromToEvent event = new org.bukkit.event.block.BlockFromToEvent(source, org.bukkit.block.BlockFace.DOWN);
                    level.getCraftServer().getPluginManager().callEvent(event);

                    if (event.isCancelled()) {
                        return;
                    }
                    // CraftBukkit end
                    this.spreadTo(level, blockPos, blockState, Direction.DOWN, newLiquid);
                    if (this.sourceNeighborCount(level, pos) >= 3) {
                        this.spreadToSides(level, pos, fluidState, state);
                    }

                    return;
                }
            }

            if (fluidState.isSource() || !this.isWaterHole(level, pos, state, blockPos, blockState)) {
                this.spreadToSides(level, pos, fluidState, state);
            }
        }
    }

    private void spreadToSides(ServerLevel level, BlockPos pos, FluidState fluidState, BlockState state) {
        int i = fluidState.getAmount() - this.getDropOff(level);
        if (fluidState.getValue(FALLING)) {
            i = 7;
        }

        if (i > 0) {
            Map<Direction, FluidState> spread = this.getSpread(level, pos, state);

            for (Entry<Direction, FluidState> entry : spread.entrySet()) {
                Direction direction = entry.getKey();
                FluidState fluidState1 = entry.getValue();
                BlockPos blockPos = pos.relative(direction);
                final BlockState blockStateIfLoaded = level.getBlockStateIfLoaded(blockPos); // Paper - Prevent chunk loading from fluid flowing
                if (blockStateIfLoaded == null) continue; // Paper - Prevent chunk loading from fluid flowing
                // CraftBukkit start
                org.bukkit.block.Block source = org.bukkit.craftbukkit.block.CraftBlock.at(level, pos);
                org.bukkit.event.block.BlockFromToEvent event = new org.bukkit.event.block.BlockFromToEvent(source, org.bukkit.craftbukkit.block.CraftBlock.notchToBlockFace(direction));
                level.getCraftServer().getPluginManager().callEvent(event);

                if (event.isCancelled()) {
                    continue;
                }
                // CraftBukkit end
                this.spreadTo(level, blockPos, blockStateIfLoaded, direction, fluidState1); // Paper - Prevent chunk loading from fluid flowing
            }
        }
    }

    protected FluidState getNewLiquid(ServerLevel level, BlockPos pos, BlockState state) {
        int i = 0;
        int i1 = 0;
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos blockPos = mutableBlockPos.setWithOffset(pos, direction);
            BlockState blockState = level.getBlockStateIfLoaded(blockPos); // Paper - Prevent chunk loading from fluid flowing
            if (blockState == null) continue; // Paper - Prevent chunk loading from fluid flowing
            FluidState fluidState = blockState.getFluidState();
            if (fluidState.getType().isSame(this) && canPassThroughWall(direction, level, pos, state, blockPos, blockState)) {
                if (fluidState.isSource()) {
                    i1++;
                }

                i = Math.max(i, fluidState.getAmount());
            }
        }

        if (i1 >= 2 && this.canConvertToSource(level)) {
            BlockState blockState1 = level.getBlockState(mutableBlockPos.setWithOffset(pos, Direction.DOWN));
            FluidState fluidState1 = blockState1.getFluidState();
            if (blockState1.isSolid() || this.isSourceBlockOfThisType(fluidState1)) {
                return this.getSource(false);
            }
        }

        BlockPos blockPos1 = mutableBlockPos.setWithOffset(pos, Direction.UP);
        BlockState blockState2 = level.getBlockState(blockPos1);
        FluidState fluidState2 = blockState2.getFluidState();
        if (!fluidState2.isEmpty() && fluidState2.getType().isSame(this) && canPassThroughWall(Direction.UP, level, pos, state, blockPos1, blockState2)) {
            return this.getFlowing(8, true);
        } else {
            int i2 = i - this.getDropOff(level);
            return i2 <= 0 ? Fluids.EMPTY.defaultFluidState() : this.getFlowing(i2, false);
        }
    }

    // Paper start - fluid method optimisations
    private static boolean canPassThroughWall(final Direction direction, final BlockGetter level,
                                              final BlockPos fromPos, final BlockState fromState,
                                              final BlockPos toPos, final BlockState toState) {
        if (((ca.spottedleaf.moonrise.patches.collisions.block.CollisionBlockState)fromState).moonrise$emptyCollisionShape() & ((ca.spottedleaf.moonrise.patches.collisions.block.CollisionBlockState)toState).moonrise$emptyCollisionShape()) {
            // don't even try to cache simple cases
            return true;
        }

        if (((ca.spottedleaf.moonrise.patches.collisions.block.CollisionBlockState)fromState).moonrise$occludesFullBlock() | ((ca.spottedleaf.moonrise.patches.collisions.block.CollisionBlockState)toState).moonrise$occludesFullBlock()) {
            // don't even try to cache simple cases
            return false;
        }

        final ca.spottedleaf.moonrise.patches.collisions.util.FluidOcclusionCacheKey[] cache = ((ca.spottedleaf.moonrise.patches.collisions.block.CollisionBlockState)fromState).moonrise$hasCache() & ((ca.spottedleaf.moonrise.patches.collisions.block.CollisionBlockState)toState).moonrise$hasCache() ?
                COLLISION_OCCLUSION_CACHE.get() : null;

        final int keyIndex
                = (((ca.spottedleaf.moonrise.patches.collisions.block.CollisionBlockState)fromState).moonrise$uniqueId1() ^ ((ca.spottedleaf.moonrise.patches.collisions.block.CollisionBlockState)toState).moonrise$uniqueId2() ^ ((ca.spottedleaf.moonrise.patches.collisions.util.CollisionDirection)(Object)direction).moonrise$uniqueId())
                & (COLLISION_OCCLUSION_CACHE_SIZE - 1);

        if (cache != null) {
            final ca.spottedleaf.moonrise.patches.collisions.util.FluidOcclusionCacheKey cached = cache[keyIndex];
            if (cached != null && cached.first() == fromState && cached.second() == toState && cached.direction() == direction) {
                return cached.result();
            }
        }

        final VoxelShape shape1 = fromState.getCollisionShape(level, fromPos);
        final VoxelShape shape2 = toState.getCollisionShape(level, toPos);

        final boolean result = !Shapes.mergedFaceOccludes(shape1, shape2, direction);

        if (cache != null) {
            // we can afford to replace in-use keys more often due to the excessive caching the collision patch does in mergedFaceOccludes
            cache[keyIndex] = new ca.spottedleaf.moonrise.patches.collisions.util.FluidOcclusionCacheKey(fromState, toState, direction, result);
        }

        return result;
    }
    // Paper end - fluid method optimisations


    public abstract Fluid getFlowing();

    public FluidState getFlowing(int level, boolean falling) {
        // Paper start - fluid method optimisations
        final int amount = level;
        if (!this.init) {
            this.init();
        }
        final int index = (falling ? 1 : 0) | ((amount - MIN_LEVEL) << 1);
        return this.flowingLookUp[index];
        // Paper end - fluid method optimisations
    }

    public abstract Fluid getSource();

    public FluidState getSource(boolean falling) {
        // Paper start - fluid method optimisations
        if (!this.init) {
            this.init();
        }
        return falling ? this.sourceFalling : this.sourceNotFalling;
        // Paper end - fluid method optimisations
    }

    protected abstract boolean canConvertToSource(ServerLevel level);

    protected void spreadTo(LevelAccessor level, BlockPos pos, BlockState state, Direction direction, FluidState fluidState) {
        if (state.getBlock() instanceof LiquidBlockContainer liquidBlockContainer) {
            liquidBlockContainer.placeLiquid(level, pos, state, fluidState);
        } else {
            if (!state.isAir()) {
                this.beforeDestroyingBlock(level, pos, state, pos.relative(direction.getOpposite())); // Paper - Add BlockBreakBlockEvent
            }

            level.setBlock(pos, fluidState.createLegacyBlock(), Block.UPDATE_ALL);
        }
    }

    protected void beforeDestroyingBlock(LevelAccessor level, BlockPos pos, BlockState state, BlockPos source) { beforeDestroyingBlock(level, pos, state); } // Paper - Add BlockBreakBlockEvent

    protected abstract void beforeDestroyingBlock(LevelAccessor level, BlockPos pos, BlockState state);

    protected int getSlopeDistance(LevelReader level, BlockPos pos, int depth, Direction direction, BlockState state, FlowingFluid.SpreadContext spreadContext) {
        int i = 1000;

        for (Direction direction1 : Direction.Plane.HORIZONTAL) {
            if (direction1 != direction) {
                BlockPos blockPos = pos.relative(direction1);
                BlockState blockState = spreadContext.getBlockStateIfLoaded(blockPos); // Paper - Prevent chunk loading from fluid flowing
                if (blockState == null) continue; // Paper - Prevent chunk loading from fluid flowing
                FluidState fluidState = blockState.getFluidState();
                if (this.canPassThrough(level, this.getFlowing(), pos, state, direction1, blockPos, blockState, fluidState)) {
                    if (spreadContext.isHole(blockPos)) {
                        return depth;
                    }

                    if (depth < this.getSlopeFindDistance(level)) {
                        int slopeDistance = this.getSlopeDistance(level, blockPos, depth + 1, direction1.getOpposite(), blockState, spreadContext);
                        if (slopeDistance < i) {
                            i = slopeDistance;
                        }
                    }
                }
            }
        }

        return i;
    }

    boolean isWaterHole(BlockGetter level, BlockPos pos, BlockState state, BlockPos belowPos, BlockState belowState) {
        return canPassThroughWall(Direction.DOWN, level, pos, state, belowPos, belowState)
            && (belowState.getFluidState().getType().isSame(this) || canHoldFluid(level, belowPos, belowState, this.getFlowing()));
    }

    private boolean canPassThrough(
        BlockGetter level, Fluid fluid, BlockPos pos, BlockState state, Direction direction, BlockPos spreadPos, BlockState spreadState, FluidState fluidState
    ) {
        return this.canMaybePassThrough(level, pos, state, direction, spreadPos, spreadState, fluidState)
            && canHoldSpecificFluid(level, spreadPos, spreadState, fluid);
    }

    private boolean canMaybePassThrough(
        BlockGetter level, BlockPos pos, BlockState state, Direction direction, BlockPos spreadPos, BlockState spreadState, FluidState fluidState
    ) {
        return !this.isSourceBlockOfThisType(fluidState)
            && canHoldAnyFluid(spreadState)
            && canPassThroughWall(direction, level, pos, state, spreadPos, spreadState);
    }

    private boolean isSourceBlockOfThisType(FluidState state) {
        return state.getType().isSame(this) && state.isSource();
    }

    protected abstract int getSlopeFindDistance(LevelReader level);

    private int sourceNeighborCount(LevelReader level, BlockPos pos) {
        int i = 0;

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos blockPos = pos.relative(direction);
            FluidState fluidState = level.getFluidState(blockPos);
            if (this.isSourceBlockOfThisType(fluidState)) {
                i++;
            }
        }

        return i;
    }

    protected Map<Direction, FluidState> getSpread(ServerLevel level, BlockPos pos, BlockState state) {
        int i = 1000;
        Map<Direction, FluidState> map = Maps.newEnumMap(Direction.class);
        FlowingFluid.SpreadContext spreadContext = null;

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos blockPos = pos.relative(direction);
            BlockState blockState = level.getBlockStateIfLoaded(blockPos); // Paper - Prevent chunk loading from fluid flowing
            if (blockState == null) continue; // Paper - Prevent chunk loading from fluid flowing
            FluidState fluidState = blockState.getFluidState();
            if (this.canMaybePassThrough(level, pos, state, direction, blockPos, blockState, fluidState)) {
                FluidState newLiquid = this.getNewLiquid(level, blockPos, blockState);
                if (canHoldSpecificFluid(level, blockPos, blockState, newLiquid.getType())) {
                    if (spreadContext == null) {
                        spreadContext = new FlowingFluid.SpreadContext(level, pos);
                    }

                    int i1;
                    if (spreadContext.isHole(blockPos)) {
                        i1 = 0;
                    } else {
                        i1 = this.getSlopeDistance(level, blockPos, 1, direction.getOpposite(), blockState, spreadContext);
                    }

                    if (i1 < i) {
                        map.clear();
                    }

                    if (i1 <= i) {
                        if (fluidState.canBeReplacedWith(level, blockPos, newLiquid.getType(), direction)) {
                            map.put(direction, newLiquid);
                        }

                        i = i1;
                    }
                }
            }
        }

        return map;
    }

    private static boolean canHoldAnyFluid(BlockState state) {
        Block block = state.getBlock();
        return block instanceof LiquidBlockContainer
            || !state.blocksMotion()
                && !(block instanceof DoorBlock)
                && !state.is(BlockTags.SIGNS)
                && !state.is(Blocks.LADDER)
                && !state.is(Blocks.SUGAR_CANE)
                && !state.is(Blocks.BUBBLE_COLUMN)
                && !state.is(Blocks.NETHER_PORTAL)
                && !state.is(Blocks.END_PORTAL)
                && !state.is(Blocks.END_GATEWAY)
                && !state.is(Blocks.STRUCTURE_VOID);
    }

    private static boolean canHoldFluid(BlockGetter level, BlockPos pos, BlockState state, Fluid fluid) {
        return canHoldAnyFluid(state) && canHoldSpecificFluid(level, pos, state, fluid);
    }

    private static boolean canHoldSpecificFluid(BlockGetter level, BlockPos pos, BlockState state, Fluid fluid) {
        return !(state.getBlock() instanceof LiquidBlockContainer liquidBlockContainer) || liquidBlockContainer.canPlaceLiquid(null, level, pos, state, fluid);
    }

    protected abstract int getDropOff(LevelReader level);

    protected int getSpreadDelay(Level level, BlockPos pos, FluidState currentState, FluidState newState) {
        return this.getTickDelay(level);
    }

    @Override
    public void tick(ServerLevel level, BlockPos pos, BlockState state, FluidState fluidState) {
        if (!fluidState.isSource()) {
            FluidState newLiquid = this.getNewLiquid(level, pos, level.getBlockState(pos));
            int spreadDelay = this.getSpreadDelay(level, pos, fluidState, newLiquid);
            if (newLiquid.isEmpty()) {
                fluidState = newLiquid;
                state = Blocks.AIR.defaultBlockState();
                // CraftBukkit start
                org.bukkit.event.block.FluidLevelChangeEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callFluidLevelChangeEvent(level, pos, state);
                if (event.isCancelled()) {
                    return;
                }
                state = ((org.bukkit.craftbukkit.block.data.CraftBlockData) event.getNewData()).getState();
                // CraftBukkit end
                level.setBlock(pos, state, Block.UPDATE_ALL);
            } else if (newLiquid != fluidState) {
                fluidState = newLiquid;
                state = newLiquid.createLegacyBlock();
                // CraftBukkit start
                org.bukkit.event.block.FluidLevelChangeEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callFluidLevelChangeEvent(level, pos, state);
                if (event.isCancelled()) {
                    return;
                }
                state = ((org.bukkit.craftbukkit.block.data.CraftBlockData) event.getNewData()).getState();
                // CraftBukkit end
                level.setBlock(pos, state, Block.UPDATE_ALL);
                level.scheduleTick(pos, newLiquid.getType(), spreadDelay);
            }
        }

        this.spread(level, pos, state, fluidState);
    }

    protected static int getLegacyLevel(FluidState state) {
        return state.isSource() ? 0 : 8 - Math.min(state.getAmount(), 8) + (state.getValue(FALLING) ? 8 : 0);
    }

    private static boolean hasSameAbove(FluidState fluidState, BlockGetter level, BlockPos pos) {
        return fluidState.getType().isSame(level.getFluidState(pos.above()).getType());
    }

    @Override
    public float getHeight(FluidState state, BlockGetter level, BlockPos pos) {
        return hasSameAbove(state, level, pos) ? 1.0F : state.getOwnHeight();
    }

    @Override
    public float getOwnHeight(FluidState state) {
        return state.getAmount() / 9.0F;
    }

    @Override
    public abstract int getAmount(FluidState state);

    @Override
    public VoxelShape getShape(FluidState state, BlockGetter level, BlockPos pos) {
        return state.getAmount() == 9 && hasSameAbove(state, level, pos)
            ? Shapes.block()
            : this.shapes.computeIfAbsent(state, fluidState -> Shapes.box(0.0, 0.0, 0.0, 1.0, fluidState.getHeight(level, pos), 1.0));
    }

    record BlockStatePairKey(BlockState first, BlockState second, Direction direction) {
        @Override
        public boolean equals(Object object) {
            return object instanceof FlowingFluid.BlockStatePairKey blockStatePairKey
                && this.first == blockStatePairKey.first
                && this.second == blockStatePairKey.second
                && this.direction == blockStatePairKey.direction;
        }

        @Override
        public int hashCode() {
            int i = System.identityHashCode(this.first);
            i = 31 * i + System.identityHashCode(this.second);
            return 31 * i + this.direction.hashCode();
        }
    }

    protected class SpreadContext {
        private final BlockGetter level;
        private final BlockPos origin;
        private final Short2ObjectMap<BlockState> stateCache = new Short2ObjectOpenHashMap<>();
        private final Short2BooleanMap holeCache = new Short2BooleanOpenHashMap();

        SpreadContext(final BlockGetter level, final BlockPos origin) {
            this.level = level;
            this.origin = origin;
        }

        public BlockState getBlockState(BlockPos pos) {
            return this.getBlockState(pos, this.getCacheKey(pos));
        }
        // Paper start - Prevent chunk loading from fluid flowing
        public @javax.annotation.Nullable BlockState getBlockStateIfLoaded(BlockPos pos) {
            return this.getBlockState(pos, this.getCacheKey(pos), false);
        }
        // Paper end - Prevent chunk loading from fluid flowing

        private BlockState getBlockState(BlockPos pos, short cacheKey) {
        // Paper start - Prevent chunk loading from fluid flowing
            return getBlockState(pos, cacheKey, true);
        }

        private @javax.annotation.Nullable BlockState getBlockState(BlockPos pos, short packed, boolean load) {
            BlockState blockState = this.stateCache.get(packed);
            if (blockState == null) {
                blockState = load ? level.getBlockState(pos) : level.getBlockStateIfLoaded(pos);
                if (blockState != null) {
                    this.stateCache.put(packed, blockState);
                }
            }
            return blockState;
        // Paper end - Prevent chunk loading from fluid flowing
        }

        public boolean isHole(BlockPos pos) {
            return this.holeCache.computeIfAbsent(this.getCacheKey(pos), s -> {
                BlockState blockState = this.getBlockState(pos, s);
                BlockPos blockPos = pos.below();
                BlockState blockState1 = this.level.getBlockState(blockPos);
                return FlowingFluid.this.isWaterHole(this.level, pos, blockState, blockPos, blockState1);
            });
        }

        private short getCacheKey(BlockPos pos) {
            int i = pos.getX() - this.origin.getX();
            int i1 = pos.getZ() - this.origin.getZ();
            return (short)((i + 128 & 0xFF) << 8 | i1 + 128 & 0xFF);
        }
    }
}
