package net.minecraft.world.level.block.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.DependantName;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.flag.FeatureElement;
import net.minecraft.world.flag.FeatureFlag;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public abstract class BlockBehaviour implements FeatureElement, org.leavesmc.leaves.lithium.common.block.entity.ShapeUpdateHandlingBlockBehaviour { // Leaves - Lithium Sleeping Block Entity
    protected static final Direction[] UPDATE_SHAPE_ORDER = new Direction[]{
        Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH, Direction.DOWN, Direction.UP
    };
    public final boolean hasCollision;
    protected final float explosionResistance;
    protected final boolean isRandomlyTicking;
    protected final SoundType soundType;
    protected final float friction;
    protected final float speedFactor;
    protected final float jumpFactor;
    protected final boolean dynamicShape;
    protected final FeatureFlagSet requiredFeatures;
    protected final BlockBehaviour.Properties properties;
    protected final Optional<ResourceKey<LootTable>> drops;
    protected final String descriptionId;

    public BlockBehaviour(BlockBehaviour.Properties properties) {
        this.hasCollision = properties.hasCollision;
        this.drops = properties.effectiveDrops();
        this.descriptionId = properties.effectiveDescriptionId();
        this.explosionResistance = properties.explosionResistance;
        this.isRandomlyTicking = properties.isRandomlyTicking;
        this.soundType = properties.soundType;
        this.friction = properties.friction;
        this.speedFactor = properties.speedFactor;
        this.jumpFactor = properties.jumpFactor;
        this.dynamicShape = properties.dynamicShape;
        this.requiredFeatures = properties.requiredFeatures;
        this.properties = properties;
    }

    public BlockBehaviour.Properties properties() {
        return this.properties;
    }

    protected abstract MapCodec<? extends Block> codec();

    protected static <B extends Block> RecordCodecBuilder<B, BlockBehaviour.Properties> propertiesCodec() {
        return BlockBehaviour.Properties.CODEC.fieldOf("properties").forGetter(BlockBehaviour::properties);
    }

    public static <B extends Block> MapCodec<B> simpleCodec(Function<BlockBehaviour.Properties, B> factory) {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(propertiesCodec()).apply(instance, factory));
    }

    protected void updateIndirectNeighbourShapes(BlockState state, LevelAccessor level, BlockPos pos, @Block.UpdateFlags int flags, int recursionLeft) {
    }

    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        switch (pathComputationType) {
            case LAND:
                return !state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            case WATER:
                return state.getFluidState().is(FluidTags.WATER);
            case AIR:
                return !state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            default:
                return false;
        }
    }

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
        if (me.earthme.luminol.config.modules.optimizations.LeavesSleepingBlockEntityConfig.enabled) this.lithium$handleShapeUpdate(level, state, pos, neighborPos, neighborState); // Leaves - Lithium Sleeping Block Entity /* Triggers when a shape update (= update that observers can detect) is sent */
        return state;
    }

    protected boolean skipRendering(BlockState state, BlockState adjacentState, Direction direction) {
        return false;
    }

    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, @Nullable Orientation orientation, boolean movedByPiston) {
    }

    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        org.spigotmc.AsyncCatcher.catchOp("block onPlace"); // Spigot
    }

    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
    }

    // CraftBukkit start
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston, net.minecraft.world.item.context.@Nullable UseOnContext context) {
        this.onPlace(state, level, pos, oldState, movedByPiston);
    }
    // CraftBukkit end

    protected void onExplosionHit(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion, BiConsumer<ItemStack, BlockPos> dropConsumer) {
        if (!state.isAir() && explosion.getBlockInteraction() != Explosion.BlockInteraction.TRIGGER_BLOCK && state.isDestroyable()) { // Paper - Protect Bedrock and End Portal/Frames from being destroyed
            Block block = state.getBlock();
            boolean flag = explosion.getIndirectSourceEntity() instanceof Player;
            if (block.dropFromExplosion(explosion)) {
                BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
                LootParams.Builder builder = new LootParams.Builder(level)
                    .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                    .withParameter(LootContextParams.TOOL, ItemStack.EMPTY)
                    .withOptionalParameter(LootContextParams.BLOCK_ENTITY, blockEntity)
                    .withOptionalParameter(LootContextParams.THIS_ENTITY, explosion.getDirectSourceEntity());
                // CraftBukkit start - add yield
                if (explosion instanceof net.minecraft.world.level.ServerExplosion serverExplosion && serverExplosion.yield < 1.0F) {
                    builder.withParameter(LootContextParams.EXPLOSION_RADIUS, 1.0F / serverExplosion.yield);
                    // CraftBukkit end
                }

                state.spawnAfterBreak(level, pos, ItemStack.EMPTY, flag);
                state.getDrops(builder).forEach(stack -> dropConsumer.accept(stack, pos));
            }

            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            block.wasExploded(level, pos, explosion);
        }
    }

    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        return InteractionResult.PASS;
    }

    protected InteractionResult useItemOn(
        ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult
    ) {
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    protected boolean triggerEvent(BlockState state, Level level, BlockPos pos, int id, int param) {
        return false;
    }

    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    protected boolean useShapeForLightOcclusion(BlockState state) {
        return false;
    }

    protected boolean isSignalSource(BlockState state) {
        return false;
    }

    protected FluidState getFluidState(BlockState state) {
        return Fluids.EMPTY.defaultFluidState();
    }

    protected boolean hasAnalogOutputSignal(BlockState state) {
        return false;
    }

    protected float getMaxHorizontalOffset() {
        return 0.25F;
    }

    protected float getMaxVerticalOffset() {
        return 0.2F;
    }

    @Override
    public FeatureFlagSet requiredFeatures() {
        return this.requiredFeatures;
    }

    protected boolean shouldChangedStateKeepBlockEntity(BlockState state) {
        return false;
    }

    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state;
    }

    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state;
    }

    protected boolean canBeReplaced(BlockState state, BlockPlaceContext useContext) {
        return state.canBeReplaced() && (useContext.getItemInHand().isEmpty() || !useContext.getItemInHand().is(this.asItem())) && (state.isDestroyable() || (useContext.getPlayer() != null && useContext.getPlayer().getAbilities().instabuild)); // Paper - Protect Bedrock and End Portal/Frames from being destroyed
    }

    protected boolean canBeReplaced(BlockState state, Fluid fluid) {
        return state.canBeReplaced() || !state.isSolid();
    }

    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        if (this.drops.isEmpty()) {
            return Collections.emptyList();
        } else {
            LootParams lootParams = params.withParameter(LootContextParams.BLOCK_STATE, state).create(LootContextParamSets.BLOCK);
            ServerLevel level = lootParams.getLevel();
            LootTable lootTable = level.getServer().reloadableRegistries().getLootTable(this.drops.get());
            return lootTable.getRandomItems(lootParams);
        }
    }

    protected long getSeed(BlockState state, BlockPos pos) {
        return Mth.getSeed(pos);
    }

    protected VoxelShape getOcclusionShape(BlockState state) {
        return state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }

    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return this.getCollisionShape(state, level, pos, CollisionContext.empty());
    }

    protected VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    protected int getLightBlock(BlockState state) {
        if (state.isSolidRender()) {
            return 15;
        } else {
            return state.propagatesSkylightDown() ? 0 : 1;
        }
    }

    public @Nullable MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return null;
    }

    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return true;
    }

    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return state.isCollisionShapeFullBlock(level, pos) ? 0.2F : 1.0F;
    }

    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return 0;
    }

    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.hasCollision ? state.getShape(level, pos) : Shapes.empty();
    }

    protected VoxelShape getEntityInsideCollisionShape(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
        return Shapes.block();
    }

    protected boolean isCollisionShapeFullBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return Block.isShapeFullBlock(state.getCollisionShape(level, pos));
    }

    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.getCollisionShape(state, level, pos, context);
    }

    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
    }

    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
    }

    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        float destroySpeed = state.getDestroySpeed(level, pos);
        if (destroySpeed == -1.0F) {
            return 0.0F;
        } else {
            int i = player.hasCorrectToolForDrops(state) ? 30 : 100;
            return player.getDestroySpeed(state) / destroySpeed / i;
        }
    }

    protected void spawnAfterBreak(BlockState state, ServerLevel level, BlockPos pos, ItemStack stack, boolean dropExperience) {
    }

    protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
    }

    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 0;
    }

    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean pastEdges) {
    }

    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 0;
    }

    public final Optional<ResourceKey<LootTable>> getLootTable() {
        return this.drops;
    }

    public final String getDescriptionId() {
        return this.descriptionId;
    }

    protected void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
    }

    protected boolean propagatesSkylightDown(BlockState state) {
        return !Block.isShapeFullBlock(state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) && state.getFluidState().isEmpty();
    }

    protected boolean isRandomlyTicking(BlockState state) {
        return this.isRandomlyTicking;
    }

    protected SoundType getSoundType(BlockState state) {
        return this.soundType;
    }

    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return new ItemStack(this.asItem());
    }

    public abstract Item asItem();

    protected abstract Block asBlock();

    public MapColor defaultMapColor() {
        return this.properties.mapColor.apply(this.asBlock().defaultBlockState());
    }

    public float defaultDestroyTime() {
        return this.properties.destroyTime;
    }

    public abstract static class BlockStateBase extends StateHolder<Block, BlockState> implements ca.spottedleaf.moonrise.patches.starlight.blockstate.StarlightAbstractBlockState, ca.spottedleaf.moonrise.patches.collisions.block.CollisionBlockState { // Paper - rewrite chunk system // Paper - optimise collisions
        private static final Direction[] DIRECTIONS = Direction.values();
        private static final VoxelShape[] EMPTY_OCCLUSION_SHAPES = Util.make(new VoxelShape[DIRECTIONS.length], shape -> Arrays.fill(shape, Shapes.empty()));
        private static final VoxelShape[] FULL_BLOCK_OCCLUSION_SHAPES = Util.make(
            new VoxelShape[DIRECTIONS.length], shape -> Arrays.fill(shape, Shapes.block())
        );
        private final int lightEmission;
        private final boolean useShapeForLightOcclusion;
        private final boolean isAir;
        private final boolean ignitedByLava;
        @Deprecated
        private final boolean liquid;
        @Deprecated
        private boolean legacySolid;
        private final PushReaction pushReaction;
        private final MapColor mapColor;
        public final float destroySpeed;
        private final boolean requiresCorrectToolForDrops;
        private final boolean canOcclude;
        private final BlockBehaviour.StatePredicate isRedstoneConductor;
        private final BlockBehaviour.StatePredicate isSuffocating;
        private final BlockBehaviour.StatePredicate isViewBlocking;
        private final BlockBehaviour.StatePredicate hasPostProcess;
        private final BlockBehaviour.StatePredicate emissiveRendering;
        private final BlockBehaviour.@Nullable OffsetFunction offsetFunction;
        private final boolean spawnTerrainParticles;
        private final NoteBlockInstrument instrument;
        private final boolean replaceable;
        private BlockBehaviour.BlockStateBase.@Nullable Cache cache;
        private FluidState fluidState = Fluids.EMPTY.defaultFluidState();
        private boolean isRandomlyTicking;
        private boolean solidRender;
        private VoxelShape occlusionShape;
        private VoxelShape[] occlusionShapesByFace;
        private boolean propagatesSkylightDown;
        private int lightBlock;

        // Paper start - rewrite chunk system
        private boolean isConditionallyFullOpaque;

        @Override
        public final boolean starlight$isConditionallyFullOpaque() {
            return this.isConditionallyFullOpaque;
        }
        // Paper end - rewrite chunk system
        // Paper start - optimise collisions
        private static final int RANDOM_OFFSET = 704237939;
        private static final Direction[] DIRECTIONS_CACHED = Direction.values();
        private static final java.util.concurrent.atomic.AtomicInteger ID_GENERATOR = new java.util.concurrent.atomic.AtomicInteger();
        private final int id1 = it.unimi.dsi.fastutil.HashCommon.murmurHash3(it.unimi.dsi.fastutil.HashCommon.murmurHash3(ID_GENERATOR.getAndIncrement() + RANDOM_OFFSET) + RANDOM_OFFSET);
        private final int id2 = it.unimi.dsi.fastutil.HashCommon.murmurHash3(it.unimi.dsi.fastutil.HashCommon.murmurHash3(ID_GENERATOR.getAndIncrement() + RANDOM_OFFSET) + RANDOM_OFFSET);
        private boolean occludesFullBlock;
        private boolean emptyCollisionShape;
        private boolean emptyConstantCollisionShape;
        private VoxelShape constantCollisionShape;

        private static void initCaches(final VoxelShape shape, final boolean neighbours) {
            ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)shape).moonrise$isFullBlock();
            ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)shape).moonrise$occludesFullBlock();
            shape.toAabbs();
            if (!shape.isEmpty()) {
                shape.bounds();
            }
            if (neighbours) {
                for (final Direction direction : DIRECTIONS_CACHED) {
                    initCaches(((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)shape).moonrise$getFaceShapeClamped(direction), false);
                    initCaches(shape.getFaceShape(direction), false);
                }
            }
        }

        @Override
        public final boolean moonrise$hasCache() {
            return this.cache != null;
        }

        @Override
        public final boolean moonrise$occludesFullBlock() {
            return this.occludesFullBlock;
        }

        @Override
        public final boolean moonrise$emptyCollisionShape() {
            return this.emptyCollisionShape;
        }

        @Override
        public final boolean moonrise$emptyContextCollisionShape() {
            return this.emptyConstantCollisionShape;
        }

        @Override
        public final int moonrise$uniqueId1() {
            return this.id1;
        }

        @Override
        public final int moonrise$uniqueId2() {
            return this.id2;
        }

        @Override
        public final VoxelShape moonrise$getConstantContextCollisionShape() {
            return this.constantCollisionShape;
        }
        // Paper end - optimise collisions

        protected BlockStateBase(Block owner, Reference2ObjectArrayMap<Property<?>, Comparable<?>> values, MapCodec<BlockState> propertiesCodec) {
            super(owner, values, propertiesCodec);
            BlockBehaviour.Properties properties = owner.properties;
            this.lightEmission = properties.lightEmission.applyAsInt(this.asState());
            this.useShapeForLightOcclusion = owner.useShapeForLightOcclusion(this.asState());
            this.isAir = properties.isAir;
            this.ignitedByLava = properties.ignitedByLava;
            this.liquid = properties.liquid;
            this.pushReaction = properties.pushReaction;
            this.mapColor = properties.mapColor.apply(this.asState());
            this.destroySpeed = properties.destroyTime;
            this.requiresCorrectToolForDrops = properties.requiresCorrectToolForDrops;
            this.canOcclude = properties.canOcclude;
            this.isRedstoneConductor = properties.isRedstoneConductor;
            this.isSuffocating = properties.isSuffocating;
            this.isViewBlocking = properties.isViewBlocking;
            this.hasPostProcess = properties.hasPostProcess;
            this.emissiveRendering = properties.emissiveRendering;
            this.offsetFunction = properties.offsetFunction;
            this.spawnTerrainParticles = properties.spawnTerrainParticles;
            this.instrument = properties.instrument;
            this.replaceable = properties.replaceable;
        }
        // Paper start - Perf: impl cached craft block data, lazy load to fix issue with loading at the wrong time
        private org.bukkit.craftbukkit.block.data.@Nullable CraftBlockData cachedCraftBlockData;

        public org.bukkit.craftbukkit.block.data.CraftBlockData createCraftBlockData() {
            if (this.cachedCraftBlockData == null) this.cachedCraftBlockData = org.bukkit.craftbukkit.block.data.CraftBlockData.createData(this.asState());
            return (org.bukkit.craftbukkit.block.data.CraftBlockData) this.cachedCraftBlockData.clone();
        }
        // Paper end - Perf: impl cached craft block data, lazy load to fix issue with loading at the wrong time


        private boolean calculateSolid() {
            if (this.owner.properties.forceSolidOn) {
                return true;
            } else if (this.owner.properties.forceSolidOff) {
                return false;
            } else if (this.cache == null) {
                return false;
            } else {
                VoxelShape voxelShape = this.cache.collisionShape;
                if (voxelShape.isEmpty()) {
                    return false;
                } else {
                    AABB aabb = voxelShape.bounds();
                    return aabb.getSize() >= 0.7291666666666666 || aabb.getYsize() >= 1.0;
                }
            }
        }

        protected boolean shapeExceedsCube = true; // Paper - moved from actual method to here
        public void initCache() {
            this.fluidState = this.owner.getFluidState(this.asState());
            this.isRandomlyTicking = this.owner.isRandomlyTicking(this.asState());
            if (!this.getBlock().hasDynamicShape()) {
                this.cache = new BlockBehaviour.BlockStateBase.Cache(this.asState());
            }
            this.shapeExceedsCube = this.cache == null || this.cache.largeCollisionShape; // Paper - moved from actual method to here

            this.legacySolid = this.calculateSolid();
            this.occlusionShape = this.canOcclude ? this.owner.getOcclusionShape(this.asState()) : Shapes.empty();
            this.solidRender = Block.isShapeFullBlock(this.occlusionShape);
            if (this.occlusionShape.isEmpty()) {
                this.occlusionShapesByFace = EMPTY_OCCLUSION_SHAPES;
            } else if (this.solidRender) {
                this.occlusionShapesByFace = FULL_BLOCK_OCCLUSION_SHAPES;
            } else {
                this.occlusionShapesByFace = new VoxelShape[DIRECTIONS.length];

                for (Direction direction : DIRECTIONS) {
                    this.occlusionShapesByFace[direction.ordinal()] = this.occlusionShape.getFaceShape(direction);
                }
            }

            this.propagatesSkylightDown = this.owner.propagatesSkylightDown(this.asState());
            this.lightBlock = this.owner.getLightBlock(this.asState());
            // Paper start - rewrite chunk system
            this.isConditionallyFullOpaque = this.canOcclude & this.useShapeForLightOcclusion;
            // Paper end - rewrite chunk system
            // Paper start - optimise collisions
            if (this.cache != null) {
                final VoxelShape collisionShape = this.cache.collisionShape;
                if (this.isAir()) {
                    this.constantCollisionShape = Shapes.empty();
                } else {
                    this.constantCollisionShape = null;
                }
                this.occludesFullBlock = ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)collisionShape).moonrise$occludesFullBlock();
                this.emptyCollisionShape = collisionShape.isEmpty();
                this.emptyConstantCollisionShape = this.constantCollisionShape != null && this.constantCollisionShape.isEmpty();
                // init caches
                initCaches(collisionShape, true);
                if (this.constantCollisionShape != null) {
                    initCaches(this.constantCollisionShape, true);
                }
            } else {
                this.occludesFullBlock = false;
                this.emptyCollisionShape = false;
                this.emptyConstantCollisionShape = false;
                this.constantCollisionShape = null;
            }

            if (this.occlusionShape != null) {
                initCaches(this.occlusionShape, true);
            }
            if (this.occlusionShapesByFace != null) {
                for (final VoxelShape shape : this.occlusionShapesByFace) {
                    initCaches(shape, true);
                }
            }
            // Paper end - optimise collisions
        }

        public Block getBlock() {
            return this.owner;
        }

        public Holder<Block> getBlockHolder() {
            return this.owner.builtInRegistryHolder();
        }

        @Deprecated
        public boolean blocksMotion() {
            Block block = this.getBlock();
            return block != Blocks.COBWEB && block != Blocks.BAMBOO_SAPLING && this.isSolid();
        }

        @Deprecated
        public boolean isSolid() {
            return this.legacySolid;
        }
        // Paper start - Protect Bedrock and End Portal/Frames from being destroyed
        public final boolean isDestroyable() {
            return getBlock().isDestroyable();
        }
        // Paper end - Protect Bedrock and End Portal/Frames from being destroyed

        public boolean isValidSpawn(BlockGetter level, BlockPos pos, EntityType<?> entityType) {
            return this.getBlock().properties.isValidSpawn.test(this.asState(), level, pos, entityType);
        }

        public boolean propagatesSkylightDown() {
            return this.propagatesSkylightDown;
        }

        public int getLightBlock() {
            return this.lightBlock;
        }

        public VoxelShape getFaceOcclusionShape(Direction face) {
            return this.occlusionShapesByFace[face.ordinal()];
        }

        public VoxelShape getOcclusionShape() {
            return this.occlusionShape;
        }

        public final boolean hasLargeCollisionShape() { // Paper
            return this.shapeExceedsCube; // Paper - moved into shape cache init
        }

        public final boolean useShapeForLightOcclusion() { // Paper - Perf: Final for inlining
            return this.useShapeForLightOcclusion;
        }

        public final int getLightEmission() { // Paper - Perf: Final for inlining
            return this.lightEmission;
        }

        public final boolean isAir() { // Paper - Perf: Final for inlining
            return this.isAir;
        }

        public boolean ignitedByLava() {
            return this.ignitedByLava;
        }

        @Deprecated
        public boolean liquid() {
            return this.liquid;
        }

        public MapColor getMapColor(BlockGetter level, BlockPos pos) {
            return this.mapColor;
        }

        public BlockState rotate(Rotation rotation) {
            return this.getBlock().rotate(this.asState(), rotation);
        }

        public BlockState mirror(Mirror mirror) {
            return this.getBlock().mirror(this.asState(), mirror);
        }

        public RenderShape getRenderShape() {
            return this.getBlock().getRenderShape(this.asState());
        }

        public boolean emissiveRendering(BlockGetter level, BlockPos pos) {
            return this.emissiveRendering.test(this.asState(), level, pos);
        }

        public float getShadeBrightness(BlockGetter level, BlockPos pos) {
            return this.getBlock().getShadeBrightness(this.asState(), level, pos);
        }

        public boolean isRedstoneConductor(BlockGetter level, BlockPos pos) {
            return this.isRedstoneConductor.test(this.asState(), level, pos);
        }

        public boolean isSignalSource() {
            return this.getBlock().isSignalSource(this.asState());
        }

        public int getSignal(BlockGetter level, BlockPos pos, Direction direction) {
            return this.getBlock().getSignal(this.asState(), level, pos, direction);
        }

        public boolean hasAnalogOutputSignal() {
            return this.getBlock().hasAnalogOutputSignal(this.asState());
        }

        public int getAnalogOutputSignal(Level level, BlockPos pos, Direction direction) {
            return this.getBlock().getAnalogOutputSignal(this.asState(), level, pos, direction);
        }

        public float getDestroySpeed(BlockGetter level, BlockPos pos) {
            return this.destroySpeed;
        }

        public float getDestroyProgress(Player player, BlockGetter level, BlockPos pos) {
            return this.getBlock().getDestroyProgress(this.asState(), player, level, pos);
        }

        public int getDirectSignal(BlockGetter level, BlockPos pos, Direction direction) {
            return this.getBlock().getDirectSignal(this.asState(), level, pos, direction);
        }

        public PushReaction getPistonPushReaction() {
            return !this.isDestroyable() ? PushReaction.BLOCK : this.pushReaction; // Paper - Protect Bedrock and End Portal/Frames from being destroyed
        }

        public boolean isSolidRender() {
            return this.solidRender;
        }

        public final boolean canOcclude() { // Paper - Perf: Final for inlining
            return this.canOcclude;
        }

        public boolean skipRendering(BlockState state, Direction face) {
            return this.getBlock().skipRendering(this.asState(), state, face);
        }

        public VoxelShape getShape(BlockGetter level, BlockPos pos) {
            return this.getShape(level, pos, CollisionContext.empty());
        }

        public VoxelShape getShape(BlockGetter level, BlockPos pos, CollisionContext context) {
            return this.getBlock().getShape(this.asState(), level, pos, context);
        }

        public VoxelShape getCollisionShape(BlockGetter level, BlockPos pos) {
            return this.cache != null ? this.cache.collisionShape : this.getCollisionShape(level, pos, CollisionContext.empty());
        }

        public VoxelShape getCollisionShape(BlockGetter level, BlockPos pos, CollisionContext context) {
            return this.getBlock().getCollisionShape(this.asState(), level, pos, context);
        }

        public VoxelShape getEntityInsideCollisionShape(BlockGetter level, BlockPos pos, Entity entity) {
            return this.getBlock().getEntityInsideCollisionShape(this.asState(), level, pos, entity);
        }

        public VoxelShape getBlockSupportShape(BlockGetter level, BlockPos pos) {
            return this.getBlock().getBlockSupportShape(this.asState(), level, pos);
        }

        public VoxelShape getVisualShape(BlockGetter level, BlockPos pos, CollisionContext context) {
            return this.getBlock().getVisualShape(this.asState(), level, pos, context);
        }

        public VoxelShape getInteractionShape(BlockGetter level, BlockPos pos) {
            return this.getBlock().getInteractionShape(this.asState(), level, pos);
        }

        public final boolean entityCanStandOn(BlockGetter level, BlockPos pos, Entity entity) {
            return this.entityCanStandOnFace(level, pos, entity, Direction.UP);
        }

        public final boolean entityCanStandOnFace(BlockGetter level, BlockPos pos, Entity entity, Direction face) {
            return Block.isFaceFull(this.getCollisionShape(level, pos, CollisionContext.of(entity)), face);
        }

        public Vec3 getOffset(BlockPos pos) {
            BlockBehaviour.OffsetFunction offsetFunction = this.offsetFunction;
            return offsetFunction != null ? offsetFunction.evaluate(this.asState(), pos) : Vec3.ZERO;
        }

        public boolean hasOffsetFunction() {
            return this.offsetFunction != null;
        }

        public boolean triggerEvent(Level level, BlockPos pos, int id, int param) {
            return this.getBlock().triggerEvent(this.asState(), level, pos, id, param);
        }

        public void handleNeighborChanged(Level level, BlockPos pos, Block neighborBlock, @Nullable Orientation orientation, boolean movedByPiston) {
            this.getBlock().neighborChanged(this.asState(), level, pos, neighborBlock, orientation, movedByPiston);
        }

        public final void updateNeighbourShapes(LevelAccessor level, BlockPos pos, @Block.UpdateFlags int flags) {
            this.updateNeighbourShapes(level, pos, flags, 512);
        }

        public final void updateNeighbourShapes(LevelAccessor level, BlockPos pos, @Block.UpdateFlags int flags, int recursionLeft) {
            BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

            for (Direction direction : BlockBehaviour.UPDATE_SHAPE_ORDER) {
                mutableBlockPos.setWithOffset(pos, direction);
                level.neighborShapeChanged(direction.getOpposite(), mutableBlockPos, pos, this.asState(), flags, recursionLeft);
            }
        }

        public final void updateIndirectNeighbourShapes(LevelAccessor level, BlockPos pos, @Block.UpdateFlags int flags) {
            this.updateIndirectNeighbourShapes(level, pos, flags, 512);
        }

        public void updateIndirectNeighbourShapes(LevelAccessor level, BlockPos pos, @Block.UpdateFlags int flags, int recursionLeft) {
            this.getBlock().updateIndirectNeighbourShapes(this.asState(), level, pos, flags, recursionLeft);
        }

        public void onPlace(Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
            // CraftBukkit start
            this.onPlace(level, pos, oldState, movedByPiston, null);
        }

        public void onPlace(Level level, BlockPos pos, BlockState oldState, boolean movedByPiston, net.minecraft.world.item.context.@Nullable UseOnContext context) {
            this.getBlock().onPlace(this.asState(), level, pos, oldState, movedByPiston, context);
            // CraftBukkit end
        }

        public void affectNeighborsAfterRemoval(ServerLevel level, BlockPos pos, boolean movedByPiston) {
            this.getBlock().affectNeighborsAfterRemoval(this.asState(), level, pos, movedByPiston);
        }

        public void onExplosionHit(ServerLevel level, BlockPos pos, Explosion explosion, BiConsumer<ItemStack, BlockPos> dropConsumer) {
            this.getBlock().onExplosionHit(this.asState(), level, pos, explosion, dropConsumer);
        }

        public void tick(ServerLevel level, BlockPos pos, RandomSource random) {
            this.getBlock().tick(this.asState(), level, pos, random);
        }

        public void randomTick(ServerLevel level, BlockPos pos, RandomSource random) {
            this.getBlock().randomTick(this.asState(), level, pos, random);
        }

        public void entityInside(Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean pastEdges) {
            this.getBlock().entityInside(this.asState(), level, pos, entity, effectApplier, pastEdges);
        }

        public void spawnAfterBreak(ServerLevel level, BlockPos pos, ItemStack stack, boolean dropExperience) {
            this.getBlock().spawnAfterBreak(this.asState(), level, pos, stack, dropExperience);
            if (dropExperience) {this.getBlock().popExperience(level, pos, this.getBlock().getExpDrop(this.asState(), level, pos, stack, true));} // Paper - Properly handle xp dropping
        }

        public List<ItemStack> getDrops(LootParams.Builder lootParams) {
            return this.getBlock().getDrops(this.asState(), lootParams);
        }

        public InteractionResult useItemOn(ItemStack stack, Level level, Player player, InteractionHand hand, BlockHitResult hitResult) {
            return this.getBlock().useItemOn(stack, this.asState(), level, hitResult.getBlockPos(), player, hand, hitResult);
        }

        public InteractionResult useWithoutItem(Level level, Player player, BlockHitResult hitResult) {
            return this.getBlock().useWithoutItem(this.asState(), level, hitResult.getBlockPos(), player, hitResult);
        }

        public void attack(Level level, BlockPos pos, Player player) {
            this.getBlock().attack(this.asState(), level, pos, player);
        }

        public boolean isSuffocating(BlockGetter level, BlockPos pos) {
            return this.isSuffocating.test(this.asState(), level, pos);
        }

        public boolean isViewBlocking(BlockGetter level, BlockPos pos) {
            return this.isViewBlocking.test(this.asState(), level, pos);
        }

        public BlockState updateShape(
            LevelReader level,
            ScheduledTickAccess scheduledTickAccess,
            BlockPos pos,
            Direction direction,
            BlockPos neighborPos,
            BlockState neighborState,
            RandomSource random
        ) {
            return this.getBlock().updateShape(this.asState(), level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
        }

        public boolean isPathfindable(PathComputationType type) {
            return this.getBlock().isPathfindable(this.asState(), type);
        }

        public boolean canBeReplaced(BlockPlaceContext useContext) {
            return this.getBlock().canBeReplaced(this.asState(), useContext);
        }

        public boolean canBeReplaced(Fluid fluid) {
            return this.getBlock().canBeReplaced(this.asState(), fluid);
        }

        public boolean canBeReplaced() {
            return this.replaceable;
        }

        public boolean canSurvive(LevelReader level, BlockPos pos) {
            return this.getBlock().canSurvive(this.asState(), level, pos);
        }

        public boolean hasPostProcess(BlockGetter level, BlockPos pos) {
            return this.hasPostProcess.test(this.asState(), level, pos);
        }

        public @Nullable MenuProvider getMenuProvider(Level level, BlockPos pos) {
            return this.getBlock().getMenuProvider(this.asState(), level, pos);
        }

        public boolean is(TagKey<Block> tag) {
            return this.getBlock().builtInRegistryHolder().is(tag);
        }

        public boolean is(TagKey<Block> tag, Predicate<BlockBehaviour.BlockStateBase> predicate) {
            return this.is(tag) && predicate.test(this);
        }

        public boolean is(HolderSet<Block> holder) {
            return holder.contains(this.getBlock().builtInRegistryHolder());
        }

        public boolean is(Holder<Block> block) {
            return this.is(block.value());
        }

        public Stream<TagKey<Block>> getTags() {
            return this.getBlock().builtInRegistryHolder().tags();
        }

        public boolean hasBlockEntity() {
            return this.getBlock() instanceof EntityBlock;
        }

        public boolean shouldChangedStateKeepBlockEntity(BlockState state) {
            return this.getBlock().shouldChangedStateKeepBlockEntity(state);
        }

        public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockEntityType<T> blockEntityType) {
            return this.getBlock() instanceof EntityBlock ? ((EntityBlock)this.getBlock()).getTicker(level, this.asState(), blockEntityType) : null;
        }

        public boolean is(Block block) {
            return this.getBlock() == block;
        }

        public boolean is(ResourceKey<Block> block) {
            return this.getBlock().builtInRegistryHolder().is(block);
        }

        public final FluidState getFluidState() { // Paper - Perf: Final for inlining
            return this.fluidState;
        }

        public final boolean isRandomlyTicking() { // Paper - Perf: Final for inlining
            return this.isRandomlyTicking;
        }

        public long getSeed(BlockPos pos) {
            return this.getBlock().getSeed(this.asState(), pos);
        }

        public SoundType getSoundType() {
            return this.getBlock().getSoundType(this.asState());
        }

        public void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
            this.getBlock().onProjectileHit(level, state, hit, projectile);
        }

        public boolean isFaceSturdy(BlockGetter level, BlockPos pos, Direction direction) {
            return this.isFaceSturdy(level, pos, direction, SupportType.FULL);
        }

        public boolean isFaceSturdy(BlockGetter level, BlockPos pos, Direction face, SupportType supportType) {
            return this.cache != null ? this.cache.isFaceSturdy(face, supportType) : supportType.isSupporting(this.asState(), level, pos, face);
        }

        public boolean isCollisionShapeFullBlock(BlockGetter level, BlockPos pos) {
            return this.cache != null ? this.cache.isCollisionShapeFullBlock : this.getBlock().isCollisionShapeFullBlock(this.asState(), level, pos);
        }

        public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, boolean includeData) {
            return this.getBlock().getCloneItemStack(level, pos, this.asState(), includeData);
        }

        protected abstract BlockState asState();

        public boolean requiresCorrectToolForDrops() {
            return this.requiresCorrectToolForDrops;
        }

        public boolean shouldSpawnTerrainParticles() {
            return this.spawnTerrainParticles;
        }

        public NoteBlockInstrument instrument() {
            return this.instrument;
        }

        static final class Cache {
            private static final Direction[] DIRECTIONS = Direction.values();
            private static final int SUPPORT_TYPE_COUNT = SupportType.values().length;
            protected final VoxelShape collisionShape;
            protected boolean largeCollisionShape; // Luminol - make mutable
            private final boolean[] faceSturdy;
            protected final boolean isCollisionShapeFullBlock;

            Cache(BlockState state) {
                Block block = state.getBlock();
                this.collisionShape = block.getCollisionShape(state, EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty());
                if (!this.collisionShape.isEmpty() && state.hasOffsetFunction()) {
                    throw new IllegalStateException(
                        String.format(
                            Locale.ROOT,
                            "%s has a collision shape and an offset type, but is not marked as dynamicShape in its properties.",
                            BuiltInRegistries.BLOCK.getKey(block)
                        )
                    );
                } else {
                    // Leaf start - Remove stream in BlockBehaviour cache blockstate
                    for (Direction.Axis axis : Direction.Axis.values()) {
                        if (this.collisionShape.min(axis) < 0.0 || this.collisionShape.max(axis) > 1.0) {
                            this.largeCollisionShape = true;
                            break;
                        }
                    }
                    // Leaf end - Remove stream in BlockBehaviour cache blockstate
                    this.faceSturdy = new boolean[DIRECTIONS.length * SUPPORT_TYPE_COUNT];

                    for (Direction direction : DIRECTIONS) {
                        for (SupportType supportType : SupportType.values()) {
                            this.faceSturdy[getFaceSupportIndex(direction, supportType)] = supportType.isSupporting(
                                state, EmptyBlockGetter.INSTANCE, BlockPos.ZERO, direction
                            );
                        }
                    }

                    this.isCollisionShapeFullBlock = Block.isShapeFullBlock(state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
                }
            }

            public boolean isFaceSturdy(Direction direction, SupportType supportType) {
                return this.faceSturdy[getFaceSupportIndex(direction, supportType)];
            }

            private static int getFaceSupportIndex(Direction direction, SupportType supportType) {
                return direction.ordinal() * SUPPORT_TYPE_COUNT + supportType.ordinal();
            }
        }
    }

    @FunctionalInterface
    public interface OffsetFunction {
        Vec3 evaluate(BlockState state, BlockPos pos);
    }

    public static enum OffsetType {
        NONE,
        XZ,
        XYZ;
    }

    public static class Properties {
        public static final Codec<BlockBehaviour.Properties> CODEC = MapCodec.unitCodec(() -> of());
        Function<BlockState, MapColor> mapColor = state -> MapColor.NONE;
        boolean hasCollision = true;
        SoundType soundType = SoundType.STONE;
        ToIntFunction<BlockState> lightEmission = state -> 0;
        float explosionResistance;
        float destroyTime;
        boolean requiresCorrectToolForDrops;
        boolean isRandomlyTicking;
        float friction = 0.6F;
        float speedFactor = 1.0F;
        float jumpFactor = 1.0F;
        private @Nullable ResourceKey<Block> id;
        private DependantName<Block, Optional<ResourceKey<LootTable>>> drops = key -> Optional.of(
            ResourceKey.create(Registries.LOOT_TABLE, key.identifier().withPrefix("blocks/"))
        );
        private DependantName<Block, String> descriptionId = key -> Util.makeDescriptionId("block", key.identifier());
        boolean canOcclude = true;
        boolean isAir;
        boolean ignitedByLava;
        @Deprecated
        boolean liquid;
        @Deprecated
        boolean forceSolidOff;
        boolean forceSolidOn;
        PushReaction pushReaction = PushReaction.NORMAL;
        boolean spawnTerrainParticles = true;
        NoteBlockInstrument instrument = NoteBlockInstrument.HARP;
        boolean replaceable;
        BlockBehaviour.StateArgumentPredicate<EntityType<?>> isValidSpawn = (state, level, pos, random) -> state.isFaceSturdy(level, pos, Direction.UP)
            && state.getLightEmission() < 14;
        BlockBehaviour.StatePredicate isRedstoneConductor = (state, level, pos) -> state.isCollisionShapeFullBlock(level, pos);
        BlockBehaviour.StatePredicate isSuffocating = (state, level, pos) -> state.blocksMotion() && state.isCollisionShapeFullBlock(level, pos);
        BlockBehaviour.StatePredicate isViewBlocking = this.isSuffocating;
        BlockBehaviour.StatePredicate hasPostProcess = (state, level, pos) -> false;
        BlockBehaviour.StatePredicate emissiveRendering = (state, level, pos) -> false;
        boolean dynamicShape;
        FeatureFlagSet requiredFeatures = FeatureFlags.VANILLA_SET;
        BlockBehaviour.@Nullable OffsetFunction offsetFunction;

        private Properties() {
        }

        public static BlockBehaviour.Properties of() {
            return new BlockBehaviour.Properties();
        }

        public static BlockBehaviour.Properties ofFullCopy(BlockBehaviour blockBehaviour) {
            BlockBehaviour.Properties properties = ofLegacyCopy(blockBehaviour);
            BlockBehaviour.Properties properties1 = blockBehaviour.properties;
            properties.jumpFactor = properties1.jumpFactor;
            properties.isRedstoneConductor = properties1.isRedstoneConductor;
            properties.isValidSpawn = properties1.isValidSpawn;
            properties.hasPostProcess = properties1.hasPostProcess;
            properties.isSuffocating = properties1.isSuffocating;
            properties.isViewBlocking = properties1.isViewBlocking;
            properties.drops = properties1.drops;
            properties.descriptionId = properties1.descriptionId;
            return properties;
        }

        @Deprecated
        public static BlockBehaviour.Properties ofLegacyCopy(BlockBehaviour blockBehaviour) {
            BlockBehaviour.Properties properties = new BlockBehaviour.Properties();
            BlockBehaviour.Properties properties1 = blockBehaviour.properties;
            properties.destroyTime = properties1.destroyTime;
            properties.explosionResistance = properties1.explosionResistance;
            properties.hasCollision = properties1.hasCollision;
            properties.isRandomlyTicking = properties1.isRandomlyTicking;
            properties.lightEmission = properties1.lightEmission;
            properties.mapColor = properties1.mapColor;
            properties.soundType = properties1.soundType;
            properties.friction = properties1.friction;
            properties.speedFactor = properties1.speedFactor;
            properties.dynamicShape = properties1.dynamicShape;
            properties.canOcclude = properties1.canOcclude;
            properties.isAir = properties1.isAir;
            properties.ignitedByLava = properties1.ignitedByLava;
            properties.liquid = properties1.liquid;
            properties.forceSolidOff = properties1.forceSolidOff;
            properties.forceSolidOn = properties1.forceSolidOn;
            properties.pushReaction = properties1.pushReaction;
            properties.requiresCorrectToolForDrops = properties1.requiresCorrectToolForDrops;
            properties.offsetFunction = properties1.offsetFunction;
            properties.spawnTerrainParticles = properties1.spawnTerrainParticles;
            properties.requiredFeatures = properties1.requiredFeatures;
            properties.emissiveRendering = properties1.emissiveRendering;
            properties.instrument = properties1.instrument;
            properties.replaceable = properties1.replaceable;
            return properties;
        }

        public BlockBehaviour.Properties mapColor(DyeColor mapColor) {
            this.mapColor = state -> mapColor.getMapColor();
            return this;
        }

        public BlockBehaviour.Properties mapColor(MapColor mapColor) {
            this.mapColor = state -> mapColor;
            return this;
        }

        public BlockBehaviour.Properties mapColor(Function<BlockState, MapColor> mapColor) {
            this.mapColor = mapColor;
            return this;
        }

        public BlockBehaviour.Properties noCollision() {
            this.hasCollision = false;
            this.canOcclude = false;
            return this;
        }

        public BlockBehaviour.Properties noOcclusion() {
            this.canOcclude = false;
            return this;
        }

        public BlockBehaviour.Properties friction(float friction) {
            this.friction = friction;
            return this;
        }

        public BlockBehaviour.Properties speedFactor(float speedFactor) {
            this.speedFactor = speedFactor;
            return this;
        }

        public BlockBehaviour.Properties jumpFactor(float jumpFactor) {
            this.jumpFactor = jumpFactor;
            return this;
        }

        public BlockBehaviour.Properties sound(SoundType soundType) {
            this.soundType = soundType;
            return this;
        }

        public BlockBehaviour.Properties lightLevel(ToIntFunction<BlockState> lightEmission) {
            this.lightEmission = lightEmission;
            return this;
        }

        public BlockBehaviour.Properties strength(float destroyTime, float explosionResistance) {
            return this.destroyTime(destroyTime).explosionResistance(explosionResistance);
        }

        public BlockBehaviour.Properties instabreak() {
            return this.strength(0.0F);
        }

        public BlockBehaviour.Properties strength(float strength) {
            this.strength(strength, strength);
            return this;
        }

        public BlockBehaviour.Properties randomTicks() {
            this.isRandomlyTicking = true;
            return this;
        }

        public BlockBehaviour.Properties dynamicShape() {
            this.dynamicShape = true;
            return this;
        }

        public BlockBehaviour.Properties noLootTable() {
            this.drops = DependantName.fixed(Optional.empty());
            return this;
        }

        public BlockBehaviour.Properties overrideLootTable(Optional<ResourceKey<LootTable>> lootTable) {
            this.drops = DependantName.fixed(lootTable);
            return this;
        }

        protected Optional<ResourceKey<LootTable>> effectiveDrops() {
            return this.drops.get(Objects.requireNonNull(this.id, "Block id not set"));
        }

        public BlockBehaviour.Properties ignitedByLava() {
            this.ignitedByLava = true;
            return this;
        }

        public BlockBehaviour.Properties liquid() {
            this.liquid = true;
            return this;
        }

        public BlockBehaviour.Properties forceSolidOn() {
            this.forceSolidOn = true;
            return this;
        }

        @Deprecated
        public BlockBehaviour.Properties forceSolidOff() {
            this.forceSolidOff = true;
            return this;
        }

        public BlockBehaviour.Properties pushReaction(PushReaction pushReaction) {
            this.pushReaction = pushReaction;
            return this;
        }

        public BlockBehaviour.Properties air() {
            this.isAir = true;
            return this;
        }

        public BlockBehaviour.Properties isValidSpawn(BlockBehaviour.StateArgumentPredicate<EntityType<?>> isValidSpawn) {
            this.isValidSpawn = isValidSpawn;
            return this;
        }

        public BlockBehaviour.Properties isRedstoneConductor(BlockBehaviour.StatePredicate isRedstoneConductor) {
            this.isRedstoneConductor = isRedstoneConductor;
            return this;
        }

        public BlockBehaviour.Properties isSuffocating(BlockBehaviour.StatePredicate isSuffocating) {
            this.isSuffocating = isSuffocating;
            return this;
        }

        public BlockBehaviour.Properties isViewBlocking(BlockBehaviour.StatePredicate isViewBlocking) {
            this.isViewBlocking = isViewBlocking;
            return this;
        }

        public BlockBehaviour.Properties hasPostProcess(BlockBehaviour.StatePredicate hasPostProcess) {
            this.hasPostProcess = hasPostProcess;
            return this;
        }

        public BlockBehaviour.Properties emissiveRendering(BlockBehaviour.StatePredicate emissiveRendering) {
            this.emissiveRendering = emissiveRendering;
            return this;
        }

        public BlockBehaviour.Properties requiresCorrectToolForDrops() {
            this.requiresCorrectToolForDrops = true;
            return this;
        }

        public BlockBehaviour.Properties destroyTime(float destroyTime) {
            this.destroyTime = destroyTime;
            return this;
        }

        public BlockBehaviour.Properties explosionResistance(float explosionResistance) {
            this.explosionResistance = Math.max(0.0F, explosionResistance);
            return this;
        }

        public BlockBehaviour.Properties offsetType(BlockBehaviour.OffsetType offsetType) {
            this.offsetFunction = switch (offsetType) {
                case NONE -> null;
                case XZ -> (state, pos) -> {
                    Block block = state.getBlock();
                    long seed = Mth.getSeed(pos.getX(), 0, pos.getZ());
                    float maxHorizontalOffset = block.getMaxHorizontalOffset();
                    double d = Mth.clamp(((float)(seed & 15L) / 15.0F - 0.5) * 0.5, (double)(-maxHorizontalOffset), (double)maxHorizontalOffset);
                    double d1 = Mth.clamp(((float)(seed >> 8 & 15L) / 15.0F - 0.5) * 0.5, (double)(-maxHorizontalOffset), (double)maxHorizontalOffset);
                    return new Vec3(d, 0.0, d1);
                };
                case XYZ -> (state, pos) -> {
                    Block block = state.getBlock();
                    long seed = Mth.getSeed(pos.getX(), 0, pos.getZ());
                    double d = ((float)(seed >> 4 & 15L) / 15.0F - 1.0) * block.getMaxVerticalOffset();
                    float maxHorizontalOffset = block.getMaxHorizontalOffset();
                    double d1 = Mth.clamp(((float)(seed & 15L) / 15.0F - 0.5) * 0.5, (double)(-maxHorizontalOffset), (double)maxHorizontalOffset);
                    double d2 = Mth.clamp(((float)(seed >> 8 & 15L) / 15.0F - 0.5) * 0.5, (double)(-maxHorizontalOffset), (double)maxHorizontalOffset);
                    return new Vec3(d1, d, d2);
                };
            };
            return this;
        }

        public BlockBehaviour.Properties noTerrainParticles() {
            this.spawnTerrainParticles = false;
            return this;
        }

        public BlockBehaviour.Properties requiredFeatures(FeatureFlag... requiredFeatures) {
            this.requiredFeatures = FeatureFlags.REGISTRY.subset(requiredFeatures);
            return this;
        }

        public BlockBehaviour.Properties instrument(NoteBlockInstrument instrument) {
            this.instrument = instrument;
            return this;
        }

        public BlockBehaviour.Properties replaceable() {
            this.replaceable = true;
            return this;
        }

        public BlockBehaviour.Properties setId(ResourceKey<Block> id) {
            this.id = id;
            return this;
        }

        public BlockBehaviour.Properties overrideDescription(String description) {
            this.descriptionId = DependantName.fixed(description);
            return this;
        }

        protected String effectiveDescriptionId() {
            return this.descriptionId.get(Objects.requireNonNull(this.id, "Block id not set"));
        }
    }

    @FunctionalInterface
    public interface StateArgumentPredicate<A> {
        boolean test(BlockState state, BlockGetter level, BlockPos pos, A value);
    }

    @FunctionalInterface
    public interface StatePredicate {
        boolean test(BlockState state, BlockGetter level, BlockPos pos);
    }
}
