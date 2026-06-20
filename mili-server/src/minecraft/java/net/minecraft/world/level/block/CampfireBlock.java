package net.minecraft.world.level.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipePropertySet;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class CampfireBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {
    public static final MapCodec<CampfireBlock> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                Codec.BOOL.fieldOf("spawn_particles").forGetter(campfireBlock -> campfireBlock.spawnParticles),
                Codec.intRange(0, 1000).fieldOf("fire_damage").forGetter(campfireBlock -> campfireBlock.fireDamage),
                propertiesCodec()
            )
            .apply(instance, CampfireBlock::new)
    );
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final BooleanProperty SIGNAL_FIRE = BlockStateProperties.SIGNAL_FIRE;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape SHAPE = Block.column(16.0, 0.0, 7.0);
    private static final VoxelShape SHAPE_VIRTUAL_POST = Block.column(4.0, 0.0, 16.0);
    private static final int SMOKE_DISTANCE = 5;
    private final boolean spawnParticles;
    private final int fireDamage;

    @Override
    public MapCodec<CampfireBlock> codec() {
        return CODEC;
    }

    public CampfireBlock(boolean spawnParticles, int fireDamage, BlockBehaviour.Properties properties) {
        super(properties);
        this.spawnParticles = spawnParticles;
        this.fireDamage = fireDamage;
        this.registerDefaultState(
            this.stateDefinition.any().setValue(LIT, true).setValue(SIGNAL_FIRE, false).setValue(WATERLOGGED, false).setValue(FACING, Direction.NORTH)
        );
    }

    @Override
    protected InteractionResult useItemOn(
        ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult
    ) {
        if (level.getBlockEntity(pos) instanceof CampfireBlockEntity campfireBlockEntity) {
            ItemStack itemInHand = player.getItemInHand(hand);
            if (level.recipeAccess().propertySet(RecipePropertySet.CAMPFIRE_INPUT).test(itemInHand)) {
                if (level instanceof ServerLevel serverLevel && campfireBlockEntity.placeFood(serverLevel, player, itemInHand)) {
                    player.awardStat(Stats.INTERACT_WITH_CAMPFIRE);
                    return InteractionResult.SUCCESS_SERVER;
                }

                return InteractionResult.CONSUME;
            }
        }

        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean pastEdges) {
        if (!new io.papermc.paper.event.entity.EntityInsideBlockEvent(entity.getBukkitEntity(), org.bukkit.craftbukkit.block.CraftBlock.at(level, pos)).callEvent()) { return; } // Paper - Add EntityInsideBlockEvent
        if (state.getValue(LIT) && entity instanceof LivingEntity) {
            entity.hurt(level.damageSources().campfire().eventBlockDamager(level, pos), this.fireDamage); // CraftBukkit
        }

        super.entityInside(state, level, pos, entity, effectApplier, pastEdges);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        LevelAccessor level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        boolean flag = level.getFluidState(clickedPos).getType() == Fluids.WATER;
        return this.defaultBlockState()
            .setValue(WATERLOGGED, flag)
            .setValue(SIGNAL_FIRE, this.isSmokeSource(level.getBlockState(clickedPos.below())))
            .setValue(LIT, !flag)
            .setValue(FACING, context.getHorizontalDirection());
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
        if (state.getValue(WATERLOGGED)) {
            scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }

        return direction == Direction.DOWN
            ? state.setValue(SIGNAL_FIRE, this.isSmokeSource(neighborState))
            : super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
    }

    private boolean isSmokeSource(BlockState state) {
        return state.is(Blocks.HAY_BLOCK);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(LIT)) {
            if (random.nextInt(10) == 0) {
                level.playLocalSound(
                    pos.getX() + 0.5,
                    pos.getY() + 0.5,
                    pos.getZ() + 0.5,
                    SoundEvents.CAMPFIRE_CRACKLE,
                    SoundSource.BLOCKS,
                    0.5F + random.nextFloat(),
                    random.nextFloat() * 0.7F + 0.6F,
                    false
                );
            }

            if (this.spawnParticles && random.nextInt(5) == 0) {
                for (int i = 0; i < random.nextInt(1) + 1; i++) {
                    level.addParticle(
                        ParticleTypes.LAVA, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, random.nextFloat() / 2.0F, 5.0E-5, random.nextFloat() / 2.0F
                    );
                }
            }
        }
    }

    public static void dowse(@Nullable Entity entity, LevelAccessor level, BlockPos pos, BlockState state) {
        if (level.isClientSide()) {
            for (int i = 0; i < 20; i++) {
                makeParticles((Level)level, pos, state.getValue(SIGNAL_FIRE), true);
            }
        }

        level.gameEvent(entity, GameEvent.BLOCK_CHANGE, pos);
    }

    @Override
    public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluidState) {
        if (!state.getValue(BlockStateProperties.WATERLOGGED) && fluidState.getType() == Fluids.WATER) {
            boolean litValue = state.getValue(LIT);
            if (litValue) {
                if (!level.isClientSide()) {
                    level.playSound(null, pos, SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.BLOCKS, 1.0F, 1.0F);
                }

                dowse(null, level, pos, state);
            }

            level.setBlock(pos, state.setValue(WATERLOGGED, true).setValue(LIT, false), Block.UPDATE_ALL);
            level.scheduleTick(pos, fluidState.getType(), fluidState.getType().getTickDelay(level));
            return true;
        } else {
            return false;
        }
    }

    @Override
    protected void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
        BlockPos blockPos = hit.getBlockPos();
        if (level instanceof ServerLevel serverLevel
            && projectile.isOnFire()
            && projectile.mayInteract(serverLevel, blockPos)
            && !state.getValue(LIT)
            && !state.getValue(WATERLOGGED)) {
            // CraftBukkit start
            if (org.bukkit.craftbukkit.event.CraftEventFactory.callBlockIgniteEvent(level, blockPos, projectile).isCancelled()) {
                return;
            }
            // CraftBukkit end
            level.setBlock(blockPos, state.setValue(BlockStateProperties.LIT, true), Block.UPDATE_ALL_IMMEDIATE);
        }
    }

    public static void makeParticles(Level level, BlockPos pos, boolean isSignalFire, boolean spawnExtraSmoke) {
        RandomSource random = level.getRandom();
        SimpleParticleType simpleParticleType = isSignalFire ? ParticleTypes.CAMPFIRE_SIGNAL_SMOKE : ParticleTypes.CAMPFIRE_COSY_SMOKE;
        level.addAlwaysVisibleParticle(
            simpleParticleType,
            true,
            pos.getX() + 0.5 + random.nextDouble() / 3.0 * (random.nextBoolean() ? 1 : -1),
            pos.getY() + random.nextDouble() + random.nextDouble(),
            pos.getZ() + 0.5 + random.nextDouble() / 3.0 * (random.nextBoolean() ? 1 : -1),
            0.0,
            0.07,
            0.0
        );
        if (spawnExtraSmoke) {
            level.addParticle(
                ParticleTypes.SMOKE,
                pos.getX() + 0.5 + random.nextDouble() / 4.0 * (random.nextBoolean() ? 1 : -1),
                pos.getY() + 0.4,
                pos.getZ() + 0.5 + random.nextDouble() / 4.0 * (random.nextBoolean() ? 1 : -1),
                0.0,
                0.005,
                0.0
            );
        }
    }

    public static boolean isSmokeyPos(Level level, BlockPos pos) {
        for (int i = 1; i <= 5; i++) {
            BlockPos blockPos = pos.below(i);
            BlockState blockState = level.getBlockState(blockPos);
            if (isLitCampfire(blockState)) {
                return true;
            }

            boolean flag = Shapes.joinIsNotEmpty(SHAPE_VIRTUAL_POST, blockState.getCollisionShape(level, pos, CollisionContext.empty()), BooleanOp.AND);
            if (flag) {
                BlockState blockState1 = level.getBlockState(blockPos.below());
                return isLitCampfire(blockState1);
            }
        }

        return false;
    }

    public static boolean isLitCampfire(BlockState state) {
        return state.hasProperty(LIT) && state.is(BlockTags.CAMPFIRES) && state.getValue(LIT);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT, SIGNAL_FIRE, WATERLOGGED, FACING);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CampfireBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (level instanceof ServerLevel serverLevel) {
            if (state.getValue(LIT)) {
                RecipeManager.CachedCheck<SingleRecipeInput, CampfireCookingRecipe> cachedCheck = RecipeManager.createCheck(RecipeType.CAMPFIRE_COOKING);
                return createTickerHelper(
                    blockEntityType,
                    BlockEntityType.CAMPFIRE,
                    (level1, pos, state1, blockEntity) -> CampfireBlockEntity.cookTick(serverLevel, pos, state1, blockEntity, cachedCheck)
                );
            } else {
                return createTickerHelper(blockEntityType, BlockEntityType.CAMPFIRE, CampfireBlockEntity::cooldownTick);
            }
        } else {
            return state.getValue(LIT) ? createTickerHelper(blockEntityType, BlockEntityType.CAMPFIRE, CampfireBlockEntity::particleTick) : null;
        }
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }

    public static boolean canLight(BlockState state) {
        return state.is(BlockTags.CAMPFIRES, blockStateBase -> blockStateBase.hasProperty(WATERLOGGED) && blockStateBase.hasProperty(LIT))
            && !state.getValue(WATERLOGGED)
            && !state.getValue(LIT);
    }
}
