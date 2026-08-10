package net.minecraft.world.level.block;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.objects.Object2ByteLinkedOpenHashMap;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.IdMapper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class Block extends BlockBehaviour implements ItemLike {
    public static final MapCodec<Block> CODEC = simpleCodec(Block::new);
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Holder.Reference<Block> builtInRegistryHolder = BuiltInRegistries.BLOCK.createIntrusiveHolder(this);
    public static final IdMapper<BlockState> BLOCK_STATE_REGISTRY = new IdMapper<>();
    private static final LoadingCache<VoxelShape, Boolean> SHAPE_FULL_BLOCK_CACHE = CacheBuilder.newBuilder()
        .maximumSize(512L)
        .weakKeys()
        .build(new CacheLoader<VoxelShape, Boolean>() {
            @Override
            public Boolean load(VoxelShape shape) {
                return !Shapes.joinIsNotEmpty(Shapes.block(), shape, BooleanOp.NOT_SAME);
            }
        });
    // Paper start - diff on change - all listed in UpdateFlags annotation
    public static final int UPDATE_NEIGHBORS = 1; // Paper - diff on change - all listed in UpdateFlags annotation
    public static final int UPDATE_CLIENTS = 2; // Paper - diff on change - all listed in UpdateFlags annotation
    public static final int UPDATE_INVISIBLE = 4; // Paper - diff on change - all listed in UpdateFlags annotation
    public static final int UPDATE_IMMEDIATE = 8; // Paper - diff on change - all listed in UpdateFlags annotation
    public static final int UPDATE_KNOWN_SHAPE = 16; // Paper - diff on change - all listed in UpdateFlags annotation
    public static final int UPDATE_SUPPRESS_DROPS = 32; // Paper - diff on change - all listed in UpdateFlags annotation
    public static final int UPDATE_MOVE_BY_PISTON = 64; // Paper - diff on change - all listed in UpdateFlags annotation
    public static final int UPDATE_SKIP_SHAPE_UPDATE_ON_WIRE = 128; // Paper - diff on change - all listed in UpdateFlags annotation
    public static final int UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS = 256; // Paper - diff on change - all listed in UpdateFlags annotation
    public static final int UPDATE_SKIP_ON_PLACE = 512; // Paper - diff on change - all listed in UpdateFlags annotation
    // Paper end - diff on change - all listed in UpdateFlags annotation
    @Block.UpdateFlags
    public static final int UPDATE_NONE = UPDATE_INVISIBLE | UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS; // Paper - help unpick a bit
    @Block.UpdateFlags
    public static final int UPDATE_ALL = UPDATE_NEIGHBORS | UPDATE_CLIENTS; // Paper - help unpick a bit
    @Block.UpdateFlags
    public static final int UPDATE_ALL_IMMEDIATE = UPDATE_NEIGHBORS | UPDATE_CLIENTS | UPDATE_IMMEDIATE; // Paper - help unpick a bit
    @Block.UpdateFlags
    public static final int UPDATE_SKIP_ALL_SIDEEFFECTS = UPDATE_KNOWN_SHAPE | UPDATE_SUPPRESS_DROPS | UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS | UPDATE_SKIP_ON_PLACE; // Paper - help unpick a bit
    public static final float INDESTRUCTIBLE = -1.0F;
    public static final float INSTANT = 0.0F;
    public static final int UPDATE_LIMIT = 512;
    protected final StateDefinition<Block, BlockState> stateDefinition;
    private BlockState defaultBlockState;
    // Paper start - Protect Bedrock and End Portal/Frames from being destroyed
    public final boolean isDestroyable() {
        return io.papermc.paper.configuration.GlobalConfiguration.get().unsupportedSettings.allowPermanentBlockBreakExploits ||
            this != Blocks.BARRIER &&
            this != Blocks.BEDROCK &&
            this != Blocks.END_PORTAL_FRAME &&
            this != Blocks.END_PORTAL &&
            this != Blocks.END_GATEWAY &&
            this != Blocks.COMMAND_BLOCK &&
            this != Blocks.REPEATING_COMMAND_BLOCK &&
            this != Blocks.CHAIN_COMMAND_BLOCK &&
            this != Blocks.STRUCTURE_BLOCK &&
            this != Blocks.JIGSAW;
    }
    // Paper end - Protect Bedrock and End Portal/Frames from being destroyed
    private @Nullable Item item;
    private static final int CACHE_SIZE = 256;
    private static final ThreadLocal<Object2ByteLinkedOpenHashMap<Block.ShapePairKey>> OCCLUSION_CACHE = ThreadLocal.withInitial(() -> {
        Object2ByteLinkedOpenHashMap<Block.ShapePairKey> map = new Object2ByteLinkedOpenHashMap<Block.ShapePairKey>(256, 0.25F) {
            @Override
            protected void rehash(int newN) {
            }
        };
        map.defaultReturnValue((byte)127);
        return map;
    });

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    public static int getId(@Nullable BlockState state) {
        if (state == null) {
            return 0;
        } else {
            int id = BLOCK_STATE_REGISTRY.getId(state);
            return id == -1 ? 0 : id;
        }
    }

    public static BlockState stateById(int id) {
        BlockState blockState = BLOCK_STATE_REGISTRY.byId(id);
        return blockState == null ? Blocks.AIR.defaultBlockState() : blockState;
    }

    public static Block byItem(@Nullable Item item) {
        return item instanceof BlockItem ? ((BlockItem)item).getBlock() : Blocks.AIR;
    }

    public static BlockState pushEntitiesUp(BlockState oldState, BlockState newState, LevelAccessor level, BlockPos pos) {
        VoxelShape voxelShape = Shapes.joinUnoptimized(oldState.getCollisionShape(level, pos), newState.getCollisionShape(level, pos), BooleanOp.ONLY_SECOND)
            .move(pos);
        if (voxelShape.isEmpty()) {
            return newState;
        } else {
            for (Entity entity : level.getEntities(null, voxelShape.bounds())) {
                double d = Shapes.collide(Direction.Axis.Y, entity.getBoundingBox().move(0.0, 1.0, 0.0), List.of(voxelShape), -1.0);
                entity.teleportRelative(0.0, 1.0 + d, 0.0);
            }

            return newState;
        }
    }

    public static VoxelShape box(double x1, double y1, double z1, double x2, double y2, double z2) {
        return Shapes.box(x1 / 16.0, y1 / 16.0, z1 / 16.0, x2 / 16.0, y2 / 16.0, z2 / 16.0);
    }

    public static VoxelShape[] boxes(int count, IntFunction<VoxelShape> boxCreator) {
        return IntStream.rangeClosed(0, count).mapToObj(boxCreator).toArray(VoxelShape[]::new);
    }

    public static VoxelShape cube(double size) {
        return cube(size, size, size);
    }

    public static VoxelShape cube(double xSize, double ySize, double zSize) {
        double d = ySize / 2.0;
        return column(xSize, zSize, 8.0 - d, 8.0 + d);
    }

    public static VoxelShape column(double size, double y1, double y2) {
        return column(size, size, y1, y2);
    }

    public static VoxelShape column(double xSize, double zSize, double y1, double y2) {
        double d = xSize / 2.0;
        double d1 = zSize / 2.0;
        return box(8.0 - d, y1, 8.0 - d1, 8.0 + d, y2, 8.0 + d1);
    }

    public static VoxelShape boxZ(double size, double z1, double z2) {
        return boxZ(size, size, z1, z2);
    }

    public static VoxelShape boxZ(double xSize, double ySize, double z1, double z2) {
        double d = ySize / 2.0;
        return boxZ(xSize, 8.0 - d, 8.0 + d, z1, z2);
    }

    public static VoxelShape boxZ(double xSize, double y1, double y2, double z1, double z2) {
        double d = xSize / 2.0;
        return box(8.0 - d, y1, z1, 8.0 + d, y2, z2);
    }

    public static BlockState updateFromNeighbourShapes(BlockState currentState, LevelAccessor level, BlockPos pos) {
        BlockState blockState = currentState;
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        for (Direction direction : UPDATE_SHAPE_ORDER) {
            mutableBlockPos.setWithOffset(pos, direction);
            blockState = blockState.updateShape(level, level, pos, direction, mutableBlockPos, level.getBlockState(mutableBlockPos), level.getRandom());
        }

        return blockState;
    }

    public static void updateOrDestroy(BlockState oldState, BlockState newState, LevelAccessor level, BlockPos pos, @Block.UpdateFlags int flags) {
        updateOrDestroy(oldState, newState, level, pos, flags, 512);
    }

    public static void updateOrDestroy(
        BlockState oldState, BlockState newState, LevelAccessor level, BlockPos pos, @Block.UpdateFlags int flags, int recursionLeft
    ) {
        if (newState != oldState) {
            if (newState.isAir()) {
                if (!level.isClientSide()) {
                    level.destroyBlock(pos, (flags & UPDATE_SUPPRESS_DROPS) == 0, null, recursionLeft);
                }
            } else {
                level.setBlock(pos, newState, flags & ~UPDATE_SUPPRESS_DROPS, recursionLeft);
            }
        }
    }

    public Block(BlockBehaviour.Properties properties) {
        super(properties);
        StateDefinition.Builder<Block, BlockState> builder = new StateDefinition.Builder<>(this);
        this.createBlockStateDefinition(builder);
        this.stateDefinition = builder.create(Block::defaultBlockState, BlockState::new);
        this.registerDefaultState(this.stateDefinition.any());
        if (SharedConstants.IS_RUNNING_IN_IDE) {
            String simpleName = this.getClass().getSimpleName();
            if (!simpleName.endsWith("Block")) {
                LOGGER.error("Block classes should end with Block and {} doesn't.", simpleName);
            }
        }
    }

    public static boolean isExceptionForConnection(BlockState state) {
        return state.getBlock() instanceof LeavesBlock
            || state.is(Blocks.BARRIER)
            || state.is(Blocks.CARVED_PUMPKIN)
            || state.is(Blocks.JACK_O_LANTERN)
            || state.is(Blocks.MELON)
            || state.is(Blocks.PUMPKIN)
            || state.is(BlockTags.SHULKER_BOXES);
    }

    protected static boolean dropFromBlockInteractLootTable(
        ServerLevel level,
        ResourceKey<LootTable> lootTable,
        BlockState state,
        @Nullable BlockEntity blockEntity,
        @Nullable ItemStack tool,
        @Nullable Entity entity,
        BiConsumer<ServerLevel, ItemStack> dropConsumer
    ) {
        return dropFromLootTable(
            level,
            lootTable,
            builder -> builder.withParameter(LootContextParams.BLOCK_STATE, state)
                .withOptionalParameter(LootContextParams.BLOCK_ENTITY, blockEntity)
                .withOptionalParameter(LootContextParams.INTERACTING_ENTITY, entity)
                .withOptionalParameter(LootContextParams.TOOL, tool)
                .create(LootContextParamSets.BLOCK_INTERACT),
            dropConsumer
        );
    }

    protected static boolean dropFromLootTable(
        ServerLevel level, ResourceKey<LootTable> lootTable, Function<LootParams.Builder, LootParams> params, BiConsumer<ServerLevel, ItemStack> dropConsumer
    ) {
        LootTable lootTable1 = level.getServer().reloadableRegistries().getLootTable(lootTable);
        LootParams lootParams = params.apply(new LootParams.Builder(level));
        List<ItemStack> randomItems = lootTable1.getRandomItems(lootParams);
        if (!randomItems.isEmpty()) {
            randomItems.forEach(itemStack -> dropConsumer.accept(level, itemStack));
            return true;
        } else {
            return false;
        }
    }

    public static boolean shouldRenderFace(BlockState currentState, BlockState neighborState, Direction face) {
        VoxelShape faceOcclusionShape = neighborState.getFaceOcclusionShape(face.getOpposite());
        if (faceOcclusionShape == Shapes.block()) {
            return false;
        } else if (currentState.skipRendering(neighborState, face)) {
            return false;
        } else if (faceOcclusionShape == Shapes.empty()) {
            return true;
        } else {
            VoxelShape faceOcclusionShape1 = currentState.getFaceOcclusionShape(face);
            if (faceOcclusionShape1 == Shapes.empty()) {
                return true;
            } else {
                Block.ShapePairKey shapePairKey = new Block.ShapePairKey(faceOcclusionShape1, faceOcclusionShape);
                Object2ByteLinkedOpenHashMap<Block.ShapePairKey> map = OCCLUSION_CACHE.get();
                byte andMoveToFirst = map.getAndMoveToFirst(shapePairKey);
                if (andMoveToFirst != 127) {
                    return andMoveToFirst != 0;
                } else {
                    boolean flag = Shapes.joinIsNotEmpty(faceOcclusionShape1, faceOcclusionShape, BooleanOp.ONLY_FIRST);
                    if (map.size() == 256) {
                        map.removeLastByte();
                    }

                    map.putAndMoveToFirst(shapePairKey, (byte)(flag ? 1 : 0));
                    return flag;
                }
            }
        }
    }

    public static boolean canSupportRigidBlock(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).isFaceSturdy(level, pos, Direction.UP, SupportType.RIGID);
    }

    public static boolean canSupportCenter(LevelReader level, BlockPos pos, Direction direction) {
        BlockState blockState = level.getBlockState(pos);
        return (direction != Direction.DOWN || !blockState.is(BlockTags.UNSTABLE_BOTTOM_CENTER))
            && blockState.isFaceSturdy(level, pos, direction, SupportType.CENTER);
    }

    public static boolean isFaceFull(VoxelShape shape, Direction face) {
        VoxelShape faceShape = shape.getFaceShape(face);
        return isShapeFullBlock(faceShape);
    }

    public static boolean isShapeFullBlock(VoxelShape shape) {
        return ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)shape).moonrise$isFullBlock(); // Paper - optimise collisions
    }

    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
    }

    public void destroy(LevelAccessor level, BlockPos pos, BlockState state) {
    }

    public static List<ItemStack> getDrops(BlockState state, ServerLevel level, BlockPos pos, @Nullable BlockEntity blockEntity) {
        LootParams.Builder builder = new LootParams.Builder(level)
            .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
            .withParameter(LootContextParams.TOOL, ItemStack.EMPTY)
            .withOptionalParameter(LootContextParams.BLOCK_ENTITY, blockEntity);
        return state.getDrops(builder);
    }

    public static List<ItemStack> getDrops(
        BlockState state, ServerLevel level, BlockPos pos, @Nullable BlockEntity blockEntity, @Nullable Entity entity, ItemStack tool
    ) {
        LootParams.Builder builder = new LootParams.Builder(level)
            .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
            .withParameter(LootContextParams.TOOL, tool)
            .withOptionalParameter(LootContextParams.THIS_ENTITY, entity)
            .withOptionalParameter(LootContextParams.BLOCK_ENTITY, blockEntity);
        return state.getDrops(builder);
    }

    // Paper start - Add BlockBreakBlockEvent
    public static boolean dropResources(BlockState state, LevelAccessor levelAccessor, BlockPos pos, @Nullable BlockEntity blockEntity, BlockPos source) {
        if (levelAccessor instanceof ServerLevel serverLevel) {
            List<org.bukkit.inventory.ItemStack> items = new java.util.ArrayList<>();
            for (ItemStack drop : Block.getDrops(state, serverLevel, pos, blockEntity)) {
                items.add(org.bukkit.craftbukkit.inventory.CraftItemStack.asBukkitCopy(drop));
            }
            Block block = state.getBlock(); // Paper - Properly handle xp dropping
            io.papermc.paper.event.block.BlockBreakBlockEvent event = new io.papermc.paper.event.block.BlockBreakBlockEvent(org.bukkit.craftbukkit.block.CraftBlock.at(levelAccessor, pos), org.bukkit.craftbukkit.block.CraftBlock.at(levelAccessor, source), items);
            event.setExpToDrop(block.getExpDrop(state, serverLevel, pos, net.minecraft.world.item.ItemStack.EMPTY, true)); // Paper - Properly handle xp dropping
            event.callEvent();
            for (org.bukkit.inventory.ItemStack drop : event.getDrops()) {
                popResource(serverLevel, pos, org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(drop));
            }
            state.spawnAfterBreak(serverLevel, pos, ItemStack.EMPTY, false); // Paper - Properly handle xp dropping
            block.popExperience(serverLevel, pos, event.getExpToDrop()); // Paper - Properly handle xp dropping
        }
        return true;
    }
    // Paper end - Add BlockBreakBlockEvent

    public static void dropResources(BlockState state, Level level, BlockPos pos) {
        if (level instanceof ServerLevel) {
            getDrops(state, (ServerLevel)level, pos, null).forEach(stack -> popResource(level, pos, stack));
            state.spawnAfterBreak((ServerLevel)level, pos, ItemStack.EMPTY, true);
        }
    }

    public static void dropResources(BlockState state, LevelAccessor level, BlockPos pos, @Nullable BlockEntity blockEntity) {
        if (level instanceof ServerLevel) {
            getDrops(state, (ServerLevel)level, pos, blockEntity).forEach(stack -> popResource((ServerLevel)level, pos, stack));
            state.spawnAfterBreak((ServerLevel)level, pos, ItemStack.EMPTY, true);
        }
    }

    public static void dropResources(BlockState state, Level level, BlockPos pos, @Nullable BlockEntity blockEntity, @Nullable Entity entity, ItemStack tool) {
        // Paper start - Properly handle xp dropping
        dropResources(state, level, pos, blockEntity, entity, tool, true);
    }
    public static void dropResources(BlockState state, Level level, BlockPos pos, @Nullable BlockEntity blockEntity, @Nullable Entity entity, ItemStack tool, boolean dropExperience) {
        // Paper end - Properly handle xp dropping
        if (level instanceof ServerLevel) {
            getDrops(state, (ServerLevel)level, pos, blockEntity, entity, tool).forEach(stack -> popResource(level, pos, stack));
            state.spawnAfterBreak((ServerLevel)level, pos, tool, dropExperience); // Paper - Properly handle xp dropping
        }
    }

    public static void popResource(Level level, BlockPos pos, ItemStack stack) {
        double d = EntityType.ITEM.getHeight() / 2.0;
        double d1 = pos.getX() + 0.5 + Mth.nextDouble(level.random, -0.25, 0.25);
        double d2 = pos.getY() + 0.5 + Mth.nextDouble(level.random, -0.25, 0.25) - d;
        double d3 = pos.getZ() + 0.5 + Mth.nextDouble(level.random, -0.25, 0.25);
        popResource(level, () -> new ItemEntity(level, d1, d2, d3, stack), stack);
    }

    public static void popResourceFromFace(Level level, BlockPos pos, Direction direction, ItemStack stack) {
        int stepX = direction.getStepX();
        int stepY = direction.getStepY();
        int stepZ = direction.getStepZ();
        double d = EntityType.ITEM.getWidth() / 2.0;
        double d1 = EntityType.ITEM.getHeight() / 2.0;
        double d2 = pos.getX() + 0.5 + (stepX == 0 ? Mth.nextDouble(level.random, -0.25, 0.25) : stepX * (0.5 + d));
        double d3 = pos.getY() + 0.5 + (stepY == 0 ? Mth.nextDouble(level.random, -0.25, 0.25) : stepY * (0.5 + d1)) - d1;
        double d4 = pos.getZ() + 0.5 + (stepZ == 0 ? Mth.nextDouble(level.random, -0.25, 0.25) : stepZ * (0.5 + d));
        double d5 = stepX == 0 ? Mth.nextDouble(level.random, -0.1, 0.1) : stepX * 0.1;
        double d6 = stepY == 0 ? Mth.nextDouble(level.random, 0.0, 0.1) : stepY * 0.1 + 0.1;
        double d7 = stepZ == 0 ? Mth.nextDouble(level.random, -0.1, 0.1) : stepZ * 0.1;
        popResource(level, () -> new ItemEntity(level, d2, d3, d4, stack, d5, d6, d7), stack);
    }

    private static void popResource(Level level, Supplier<ItemEntity> itemEntitySupplier, ItemStack stack) {
        if (level instanceof ServerLevel serverLevel && !stack.isEmpty() && serverLevel.getGameRules().get(GameRules.BLOCK_DROPS)) {
            ItemEntity itemEntity = itemEntitySupplier.get();
            itemEntity.setDefaultPickUpDelay();
            // CraftBukkit start
            if (level.getCurrentWorldData().captureDrops != null) { // Folia - region threading
                level.getCurrentWorldData().captureDrops.add(itemEntity); // Folia - region threading
            } else {
                level.addFreshEntity(itemEntity);
            }
            // CraftBukkit end
        }
    }

    public void popExperience(ServerLevel level, BlockPos pos, int amount) {
        // Paper start - add entity parameter
        popExperience(level, pos, amount, null);
    }

    public void popExperience(ServerLevel level, BlockPos pos, int amount, net.minecraft.world.entity.@Nullable Entity entity) {
        // Paper end - add entity parameter
        if (level.getGameRules().get(GameRules.BLOCK_DROPS)) {
            ExperienceOrb.awardWithDirection(level, Vec3.atCenterOf(pos), net.minecraft.world.phys.Vec3.ZERO, amount, org.bukkit.entity.ExperienceOrb.SpawnReason.BLOCK_BREAK, entity, null); // Paper
        }
    }

    public float getExplosionResistance() {
        return this.explosionResistance;
    }

    public void wasExploded(ServerLevel level, BlockPos pos, Explosion explosion) {
    }

    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
    }

    // Leaves start - alternativeBlockPlacement
    public @Nullable BlockState getRealStateForPlacement(BlockPlaceContext context) {
        BlockState vanillaState = this.getStateForPlacement(context);
        switch (fun.bm.mili.config.modules.function.protocol.AlternativeBlockPlacementProtocolConfig.alternativeBlockPlacement) {
            case fun.bm.mili.enums.EnumAlternativePlaceType.CARPET -> {
                BlockState tryState = org.leavesmc.leaves.protocol.CarpetAlternativeBlockPlacement.alternativeBlockPlacement(this, context);
                if (tryState != null) {
                    return tryState;
                }
            }
            case fun.bm.mili.enums.EnumAlternativePlaceType.CARPET_FIX -> {
                BlockState tryState = org.leavesmc.leaves.protocol.CarpetAlternativeBlockPlacement.alternativeBlockPlacementFix(this, context);
                if (tryState != null) {
                    return tryState;
                }
            }
            case fun.bm.mili.enums.EnumAlternativePlaceType.LITEMATICA -> {
                if (vanillaState != null) {
                    return org.leavesmc.leaves.protocol.LitematicaEasyPlaceProtocol.applyPlacementProtocol(vanillaState, context);
                }
            }
        }
        return vanillaState;
    }
    // Leaves end - alternativeBlockPlacement

    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState();
    }

    @Deprecated @io.papermc.paper.annotation.DoNotUse // Paper - fix drops not preventing stats/food exhaustion
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, ItemStack tool) {
        // Paper start - fix drops not preventing stats/food exhaustion
        this.playerDestroy(level, player, pos, state, blockEntity, tool, true, true);
    }

    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, ItemStack tool, boolean includeDrops, boolean dropExp) {
        // Paper end - fix drops not preventing stats/food exhaustion
        player.awardStat(Stats.BLOCK_MINED.get(this));
        player.causeFoodExhaustion(0.005F, org.bukkit.event.entity.EntityExhaustionEvent.ExhaustionReason.BLOCK_MINED); // CraftBukkit - EntityExhaustionEvent
        if (includeDrops) { // Paper - fix drops not preventing stats/food exhaustion
        Block.dropResources(state, level, pos, blockEntity, player, tool, dropExp); // Paper - Properly handle xp dropping
        } // Paper - fix drops not preventing stats/food exhaustion
    }

    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
    }

    public boolean isPossibleToRespawnInThis(BlockState state) {
        return !state.isSolid() && !state.liquid();
    }

    public MutableComponent getName() {
        return Component.translatable(this.getDescriptionId());
    }

    public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, double fallDistance) {
        entity.causeFallDamage(fallDistance, 1.0F, entity.damageSources().fall());
    }

    public void updateEntityMovementAfterFallOn(BlockGetter level, Entity entity) {
        entity.setDeltaMovement(entity.getDeltaMovement().multiply(1.0, 0.0, 1.0));
    }

    public float getFriction() {
        return this.friction;
    }

    public float getSpeedFactor() {
        return this.speedFactor;
    }

    public float getJumpFactor() {
        return this.jumpFactor;
    }

    protected void spawnDestroyParticles(Level level, Player player, BlockPos pos, BlockState state) {
        level.levelEvent(player, LevelEvent.PARTICLES_DESTROY_BLOCK, pos, getId(state));
    }

    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        this.spawnDestroyParticles(level, player, pos, state);
        if (state.is(BlockTags.GUARDED_BY_PIGLINS) && level instanceof ServerLevel serverLevel) {
            PiglinAi.angerNearbyPiglins(serverLevel, player, false);
        }

        level.gameEvent(GameEvent.BLOCK_DESTROY, pos, GameEvent.Context.of(player, state));
        return state;
    }

    public void handlePrecipitation(BlockState state, Level level, BlockPos pos, Biome.Precipitation precipitation) {
    }

    public boolean dropFromExplosion(Explosion explosion) {
        return true;
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    }

    public StateDefinition<Block, BlockState> getStateDefinition() {
        return this.stateDefinition;
    }

    protected final void registerDefaultState(BlockState state) {
        this.defaultBlockState = state;
    }

    public final BlockState defaultBlockState() {
        return this.defaultBlockState;
    }

    public final BlockState withPropertiesOf(BlockState state) {
        BlockState blockState = this.defaultBlockState();

        for (Property<?> property : state.getBlock().getStateDefinition().getProperties()) {
            if (blockState.hasProperty(property)) {
                blockState = copyProperty(state, blockState, property);
            }
        }

        return blockState;
    }

    private static <T extends Comparable<T>> BlockState copyProperty(BlockState sourceState, BlockState targetState, Property<T> property) {
        return targetState.setValue(property, sourceState.getValue(property));
    }

    @Override
    public Item asItem() {
        if (this.item == null) {
            this.item = Item.byBlock(this);
        }

        return this.item;
    }

    public boolean hasDynamicShape() {
        return this.dynamicShape;
    }

    @Override
    public String toString() {
        return "Block{" + BuiltInRegistries.BLOCK.wrapAsHolder(this).getRegisteredName() + "}";
    }

    @Override
    protected Block asBlock() {
        return this;
    }

    protected Function<BlockState, VoxelShape> getShapeForEachState(Function<BlockState, VoxelShape> shapeGetter) {
        return this.stateDefinition.getPossibleStates().stream().collect(ImmutableMap.toImmutableMap(Function.identity(), shapeGetter))::get;
    }

    protected Function<BlockState, VoxelShape> getShapeForEachState(Function<BlockState, VoxelShape> shapeGetter, Property<?>... properties) {
        Map<? extends Property<?>, Object> map = Arrays.stream(properties)
            .collect(Collectors.toMap(property -> property, property -> property.getPossibleValues().getFirst()));
        ImmutableMap<BlockState, VoxelShape> map1 = this.stateDefinition
            .getPossibleStates()
            .stream()
            .filter(state -> map.entrySet().stream().allMatch(entry -> state.getValue((Property<?>)entry.getKey()) == entry.getValue()))
            .collect(ImmutableMap.toImmutableMap(Function.identity(), shapeGetter));
        return state -> {
            for (Entry<? extends Property<?>, Object> entry : map.entrySet()) {
                state = setValueHelper(state, (Property<?>)entry.getKey(), entry.getValue());
            }

            return map1.get(state);
        };
    }

    private static <S extends StateHolder<?, S>, T extends Comparable<T>> S setValueHelper(S stateHolder, Property<T> property, Object value) {
        return stateHolder.setValue(property, (T) value);
    }

    @Deprecated
    public Holder.Reference<Block> builtInRegistryHolder() {
        return this.builtInRegistryHolder;
    }

    protected int tryDropExperience(ServerLevel level, BlockPos pos, ItemStack heldItem, IntProvider amount) { // CraftBukkit
        int i = EnchantmentHelper.processBlockExperience(level, heldItem, amount.sample(level.getRandom()));
        if (i > 0) {
            // CraftBukkit start
            //this.popExperience(level, pos, i);
            return i;
        }
        return 0;
    }

    public int getExpDrop(BlockState state, ServerLevel level, BlockPos pos, ItemStack stack, boolean dropExperience) {
        return 0;
    }
    // CraftBukkit end

    // Mili start - reset push reaction
    @org.jetbrains.annotations.Nullable
    public net.minecraft.world.level.material.PushReaction getResetPushReaction() {
        return null;
    }
    // Mili end - reset push reaction

    record ShapePairKey(VoxelShape first, VoxelShape second) {
        @Override
        public boolean equals(Object other) {
            return other instanceof Block.ShapePairKey shapePairKey && this.first == shapePairKey.first && this.second == shapePairKey.second;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this.first) * 31 + System.identityHashCode(this.second);
        }
    }

    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE, ElementType.METHOD, ElementType.TYPE_USE})
    @org.intellij.lang.annotations.MagicConstant(flags = {UPDATE_NEIGHBORS, UPDATE_CLIENTS, UPDATE_INVISIBLE, UPDATE_IMMEDIATE, UPDATE_KNOWN_SHAPE, UPDATE_SUPPRESS_DROPS, UPDATE_MOVE_BY_PISTON, UPDATE_SKIP_SHAPE_UPDATE_ON_WIRE, UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS, UPDATE_SKIP_ON_PLACE}) // Paper - add back source-retention annotation for IDE
    public @interface UpdateFlags {
    }
}
