package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.InsideBlockEffectType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class BaseFireBlock extends Block {
    private static final int SECONDS_ON_FIRE = 8;
    private static final int MIN_FIRE_TICKS_TO_ADD = 1;
    private static final int MAX_FIRE_TICKS_TO_ADD = 3;
    private final float fireDamage;
    protected static final VoxelShape SHAPE = Block.column(16.0, 0.0, 1.0);

    public BaseFireBlock(BlockBehaviour.Properties properties, float fireDamage) {
        super(properties);
        this.fireDamage = fireDamage;
    }

    @Override
    protected abstract MapCodec<? extends BaseFireBlock> codec();

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return getState(context.getLevel(), context.getClickedPos());
    }

    public static BlockState getState(BlockGetter level, BlockPos pos) {
        BlockPos blockPos = pos.below();
        BlockState blockState = level.getBlockState(blockPos);
        return SoulFireBlock.canSurviveOnBlock(blockState) ? Blocks.SOUL_FIRE.defaultBlockState() : ((FireBlock)Blocks.FIRE).getStateForPlacement(level, pos);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(24) == 0) {
            level.playLocalSound(
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5,
                SoundEvents.FIRE_AMBIENT,
                SoundSource.BLOCKS,
                1.0F + random.nextFloat(),
                random.nextFloat() * 0.7F + 0.3F,
                false
            );
        }

        BlockPos blockPos = pos.below();
        BlockState blockState = level.getBlockState(blockPos);
        if (!this.canBurn(blockState) && !blockState.isFaceSturdy(level, blockPos, Direction.UP)) {
            if (this.canBurn(level.getBlockState(pos.west()))) {
                for (int i = 0; i < 2; i++) {
                    double d = pos.getX() + random.nextDouble() * 0.1F;
                    double d1 = pos.getY() + random.nextDouble();
                    double d2 = pos.getZ() + random.nextDouble();
                    level.addParticle(ParticleTypes.LARGE_SMOKE, d, d1, d2, 0.0, 0.0, 0.0);
                }
            }

            if (this.canBurn(level.getBlockState(pos.east()))) {
                for (int i = 0; i < 2; i++) {
                    double d = pos.getX() + 1 - random.nextDouble() * 0.1F;
                    double d1 = pos.getY() + random.nextDouble();
                    double d2 = pos.getZ() + random.nextDouble();
                    level.addParticle(ParticleTypes.LARGE_SMOKE, d, d1, d2, 0.0, 0.0, 0.0);
                }
            }

            if (this.canBurn(level.getBlockState(pos.north()))) {
                for (int i = 0; i < 2; i++) {
                    double d = pos.getX() + random.nextDouble();
                    double d1 = pos.getY() + random.nextDouble();
                    double d2 = pos.getZ() + random.nextDouble() * 0.1F;
                    level.addParticle(ParticleTypes.LARGE_SMOKE, d, d1, d2, 0.0, 0.0, 0.0);
                }
            }

            if (this.canBurn(level.getBlockState(pos.south()))) {
                for (int i = 0; i < 2; i++) {
                    double d = pos.getX() + random.nextDouble();
                    double d1 = pos.getY() + random.nextDouble();
                    double d2 = pos.getZ() + 1 - random.nextDouble() * 0.1F;
                    level.addParticle(ParticleTypes.LARGE_SMOKE, d, d1, d2, 0.0, 0.0, 0.0);
                }
            }

            if (this.canBurn(level.getBlockState(pos.above()))) {
                for (int i = 0; i < 2; i++) {
                    double d = pos.getX() + random.nextDouble();
                    double d1 = pos.getY() + 1 - random.nextDouble() * 0.1F;
                    double d2 = pos.getZ() + random.nextDouble();
                    level.addParticle(ParticleTypes.LARGE_SMOKE, d, d1, d2, 0.0, 0.0, 0.0);
                }
            }
        } else {
            for (int i = 0; i < 3; i++) {
                double d = pos.getX() + random.nextDouble();
                double d1 = pos.getY() + random.nextDouble() * 0.5 + 0.5;
                double d2 = pos.getZ() + random.nextDouble();
                level.addParticle(ParticleTypes.LARGE_SMOKE, d, d1, d2, 0.0, 0.0, 0.0);
            }
        }
    }

    protected abstract boolean canBurn(BlockState state);

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean pastEdges) {
        if (!new io.papermc.paper.event.entity.EntityInsideBlockEvent(entity.getBukkitEntity(), org.bukkit.craftbukkit.block.CraftBlock.at(level, pos)).callEvent()) { return; } // Paper - Add EntityInsideBlockEvent
        effectApplier.apply(InsideBlockEffectType.CLEAR_FREEZE);
        effectApplier.apply(InsideBlockEffectType.FIRE_IGNITE);
        effectApplier.runAfter(InsideBlockEffectType.FIRE_IGNITE, entity1 -> entity1.hurt(entity1.level().damageSources().inFire(), this.fireDamage));
    }

    public static void fireIgnite(Entity entity, BlockPos pos) { // Paper - track position inside effect was triggered on
        if (!entity.fireImmune()) {
            if (entity.getRemainingFireTicks() < 0) {
                entity.setRemainingFireTicks(entity.getRemainingFireTicks() + 1);
            } else if (entity instanceof ServerPlayer) {
                int randomInt = entity.level().getRandom().nextInt(1, 3);
                entity.setRemainingFireTicks(entity.getRemainingFireTicks() + randomInt);
            }

            if (entity.getRemainingFireTicks() >= 0) {
                // CraftBukkit start
                org.bukkit.event.entity.EntityCombustEvent event = new org.bukkit.event.entity.EntityCombustByBlockEvent(org.bukkit.craftbukkit.block.CraftBlock.at(entity.level(), pos), entity.getBukkitEntity(), 8.0F);
                entity.level().getCraftServer().getPluginManager().callEvent(event);

                if (!event.isCancelled()) {
                    entity.igniteForSeconds(event.getDuration(), false);
                    // Paper start - fix EntityCombustEvent cancellation
                } else {
                    entity.setRemainingFireTicks(entity.getRemainingFireTicks() - 1);
                    // Paper end - fix EntityCombustEvent cancellation
                }
                // CraftBukkit end
            }
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston, net.minecraft.world.item.context.UseOnContext context) { // CraftBukkit - context
        if (!oldState.is(state.getBlock())) {
            if (inPortalDimension(level)) {
                Optional<PortalShape> optional = PortalShape.findEmptyPortalShape(level, pos, Direction.Axis.X);
                if (optional.isPresent()) {
                    optional.get().createPortalBlocks(level, (context == null) ? null : context.getPlayer()); // CraftBukkit - player
                    return;
                }
            }

            if (!state.canSurvive(level, pos)) {
                this.fireExtinguished(level, pos); // CraftBukkit - fuel block broke
            }
        }
    }

    private static boolean inPortalDimension(Level level) {
        return level.getTypeKey() == net.minecraft.world.level.dimension.LevelStem.OVERWORLD || level.getTypeKey() == net.minecraft.world.level.dimension.LevelStem.NETHER; // CraftBukkit - getTypeKey()
    }

    @Override
    protected void spawnDestroyParticles(Level level, Player player, BlockPos pos, BlockState state) {
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            level.levelEvent(null, LevelEvent.SOUND_EXTINGUISH_FIRE, pos, 0);
        }

        return super.playerWillDestroy(level, pos, state, player);
    }

    public static boolean canBePlacedAt(Level level, BlockPos pos, Direction direction) {
        BlockState blockState = level.getBlockState(pos);
        return blockState.isAir() && (getState(level, pos).canSurvive(level, pos) || isPortal(level, pos, direction));
    }

    private static boolean isPortal(Level level, BlockPos pos, Direction direction) {
        if (!inPortalDimension(level)) {
            return false;
        } else {
            BlockPos.MutableBlockPos mutableBlockPos = pos.mutable();
            boolean flag = false;

            for (Direction direction1 : Direction.values()) {
                if (level.getBlockState(mutableBlockPos.set(pos).move(direction1)).is(Blocks.OBSIDIAN)) {
                    flag = true;
                    break;
                }
            }

            if (!flag) {
                return false;
            } else {
                Direction.Axis axis = direction.getAxis().isHorizontal()
                    ? direction.getCounterClockWise().getAxis()
                    : Direction.Plane.HORIZONTAL.getRandomAxis(level.random);
                return PortalShape.findEmptyPortalShape(level, pos, axis).isPresent();
            }
        }
    }

    // CraftBukkit start
    protected void fireExtinguished(net.minecraft.world.level.LevelAccessor world, BlockPos position) {
        if (!org.bukkit.craftbukkit.event.CraftEventFactory.callBlockFadeEvent(world, position, Blocks.AIR.defaultBlockState()).isCancelled()) {
            world.removeBlock(position, false);
        }
    }
    // CraftBukkit end
}
