package net.minecraft.world.level.block;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.BlockUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class NetherPortalBlock extends Block implements Portal {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final MapCodec<NetherPortalBlock> CODEC = simpleCodec(NetherPortalBlock::new);
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    private static final Map<Direction.Axis, VoxelShape> SHAPES = Shapes.rotateHorizontalAxis(Block.column(4.0, 16.0, 0.0, 16.0));

    @Override
    public MapCodec<NetherPortalBlock> codec() {
        return CODEC;
    }

    public NetherPortalBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(AXIS, Direction.Axis.X));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(AXIS));
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.spigotConfig.enableZombiePigmenPortalSpawns && level.isSpawningMonsters() // Spigot
            && level.environmentAttributes().getValue(EnvironmentAttributes.NETHER_PORTAL_SPAWNS_PIGLINS, pos)
            && random.nextInt(2000) < level.getDifficulty().getId()
            && level.anyPlayerCloseEnoughForSpawning(pos)) {
            while (level.getBlockState(pos).is(this)) {
                pos = pos.below();
            }

            if (level.getBlockState(pos).isValidSpawn(level, pos, EntityType.ZOMBIFIED_PIGLIN)) {
                Entity entity = EntityType.ZOMBIFIED_PIGLIN.spawn(level, pos.above(), EntitySpawnReason.STRUCTURE, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NETHER_PORTAL); // CraftBukkit - set spawn reason to NETHER_PORTAL
                if (entity != null) {
                    entity.setPortalCooldown();
                    // Paper start - Add option to nerf pigmen from nether portals
                    entity.fromNetherPortal = true;
                    if (level.paperConfig().entities.behavior.nerfPigmenFromNetherPortals) ((net.minecraft.world.entity.Mob) entity).aware = false;
                    // Paper end - Add option to nerf pigmen from nether portals
                    Entity vehicle = entity.getVehicle();
                    if (vehicle != null) {
                        vehicle.setPortalCooldown();
                    }
                }
            }
        }
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
        Direction.Axis axis = direction.getAxis();
        Direction.Axis axis1 = state.getValue(AXIS);
        boolean flag = axis1 != axis && axis.isHorizontal();
        return !flag && !neighborState.is(this) && !PortalShape.findAnyShape(level, pos, axis1).isComplete()
            ? Blocks.AIR.defaultBlockState()
            : super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean pastEdges) {
        if (!new io.papermc.paper.event.entity.EntityInsideBlockEvent(entity.getBukkitEntity(), org.bukkit.craftbukkit.block.CraftBlock.at(level, pos)).callEvent()) { return; } // Paper - Add EntityInsideBlockEvent
        if (entity.canUsePortal(false)) {
            // CraftBukkit start - Entity in portal
            org.bukkit.event.entity.EntityPortalEnterEvent event = new org.bukkit.event.entity.EntityPortalEnterEvent(entity.getBukkitEntity(), org.bukkit.craftbukkit.util.CraftLocation.toBukkit(pos, level), org.bukkit.PortalType.NETHER); // Paper - add portal type
            level.getCraftServer().getPluginManager().callEvent(event);
            if (event.isCancelled()) return; // Paper - make cancellable
            // CraftBukkit end
            entity.setAsInsidePortal(this, pos);
        }
    }

    @Override
    public int getPortalTransitionTime(ServerLevel level, Entity entity) {
        return entity instanceof Player player
            ? Math.max(
                0,
                level.getGameRules()
                    .get(player.getAbilities().invulnerable ? GameRules.PLAYERS_NETHER_PORTAL_CREATIVE_DELAY : GameRules.PLAYERS_NETHER_PORTAL_DEFAULT_DELAY)
            )
            : 0;
    }

    @Override
    public @Nullable TeleportTransition getPortalDestination(ServerLevel level, Entity entity, BlockPos pos) {
        // CraftBukkit start
        ResourceKey<Level> resourceKey = level.getTypeKey() == net.minecraft.world.level.dimension.LevelStem.NETHER ? Level.OVERWORLD : Level.NETHER;
        ServerLevel level1 = level.getServer().getLevel(resourceKey);
        // Paper start - Add EntityPortalReadyEvent
        io.papermc.paper.event.entity.EntityPortalReadyEvent portalReadyEvent = new io.papermc.paper.event.entity.EntityPortalReadyEvent(entity.getBukkitEntity(), level1 == null ? null : level1.getWorld(), org.bukkit.PortalType.NETHER);
        if (!portalReadyEvent.callEvent()) {
            entity.portalProcess = null;
            return null;
        }
        level1 = portalReadyEvent.getTargetWorld() == null ? null : ((org.bukkit.craftbukkit.CraftWorld) portalReadyEvent.getTargetWorld()).getHandle();
        // Paper end - Add EntityPortalReadyEvent
        if (level1 == null) {
            return null;
        } else {
            boolean flag = level1.getTypeKey() == net.minecraft.world.level.dimension.LevelStem.NETHER; // CraftBukkit
            WorldBorder worldBorder = level1.getWorldBorder();
            double teleportationScale = DimensionType.getTeleportationScale(level.dimensionType(), level1.dimensionType());
            BlockPos blockPos = worldBorder.clampToBounds(entity.getX() * teleportationScale, entity.getY(), entity.getZ() * teleportationScale);
            // Paper start - Configurable portal search radius
            int portalSearchRadius = level1.paperConfig().environment.portalSearchRadius;
            if (entity.level().paperConfig().environment.portalSearchVanillaDimensionScaling && flag) { // flag = is going to nether
                portalSearchRadius = (int) (portalSearchRadius / level1.dimensionType().coordinateScale());
            }
            // Paper end - Configurable portal search radius
            // CraftBukkit start
            org.bukkit.craftbukkit.event.PortalEventResult result = org.bukkit.craftbukkit.event.CraftEventFactory.handlePortalEvents(entity, org.bukkit.craftbukkit.util.CraftLocation.toBukkit(blockPos, level1), org.bukkit.PortalType.NETHER, portalSearchRadius, level1.paperConfig().environment.portalCreateRadius); // Paper - use custom portal search radius
            if (result == null) {
                return null;
            }
            level1 = ((org.bukkit.craftbukkit.CraftWorld) result.to().getWorld()).getHandle();
            worldBorder = level1.getWorldBorder();
            blockPos = worldBorder.clampToBounds(result.to().getX(), result.to().getY(), result.to().getZ());
            return this.getExitPortal(level1, entity, pos, blockPos, worldBorder, result);
            // CraftBukkit end
        }
    }

    // Folia start - region threading
    @Override
    public boolean portalAsync(ServerLevel sourceWorld, Entity portalTarget, BlockPos portalPos) {
        if (!ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(portalTarget)) {
            return false;
        }
        if (!ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(sourceWorld, portalPos)) {
            return false;
        }

        return portalTarget.netherPortalLogicAsync(portalPos);
    }

    public static BlockUtil.FoundRectangle findPortalAround(ServerLevel world, BlockPos rough, WorldBorder worldBorder, int searchRadius) {
        BlockPos found = world.getPortalForcer().findClosestPortalPosition(rough, worldBorder, searchRadius).orElse(null);
        if (found == null) {
            return null;
        }

        BlockState portalState = world.getBlockStateFromEmptyChunk(found);

        return BlockUtil.getLargestRectangleAround(found, portalState.getValue(BlockStateProperties.HORIZONTAL_AXIS), 21, Direction.Axis.Y, 21, (pos) -> {
            return world.getBlockStateFromEmptyChunk(pos) == portalState;
        });
    }
    // Folia end - region threading

    private @Nullable TeleportTransition getExitPortal(
        ServerLevel level, Entity entity, BlockPos pos, BlockPos exitPos, WorldBorder worldBorder, org.bukkit.craftbukkit.event.PortalEventResult result // CraftBukkit
    ) {
        Optional<BlockPos> optional = level.getPortalForcer().findClosestPortalPosition(exitPos, worldBorder, result.searchRadius()); // CraftBukkit
        BlockUtil.FoundRectangle largestRectangleAround;
        TeleportTransition.PostTeleportTransition postTeleportTransition;
        if (optional.isPresent()) {
            BlockPos blockPos = optional.get();
            BlockState blockState = level.getBlockStateFromEmptyChunk(blockPos); // Folia - region threading
            largestRectangleAround = BlockUtil.getLargestRectangleAround(
                blockPos,
                blockState.getValue(BlockStateProperties.HORIZONTAL_AXIS),
                21,
                Direction.Axis.Y,
                21,
                blockPos1 -> level.getBlockStateFromEmptyChunk(blockPos1) == blockState // Folia - region threading
            );
            postTeleportTransition = TeleportTransition.PLAY_PORTAL_SOUND.then(entity1 -> entity1.placePortalTicket(blockPos));
        } else if (result.canCreatePortal()) { // CraftBukkit
            Direction.Axis axis = entity.level().getBlockState(pos).getOptionalValue(AXIS).orElse(Direction.Axis.X);
            Optional<BlockUtil.FoundRectangle> optional1 = level.getPortalForcer().createPortal(exitPos, axis, entity, result.createRadius()); // CraftBukkit
            if (optional1.isEmpty()) {
                // LOGGER.error("Unable to create a portal, likely target out of worldborder"); // CraftBukkit
                return null;
            }

            largestRectangleAround = optional1.get();
            postTeleportTransition = TeleportTransition.PLAY_PORTAL_SOUND.then(TeleportTransition.PLACE_PORTAL_TICKET);
        }
        // CraftBukkit start
        else {
            return null;
        }
        // CraftBukkit end

        return getDimensionTransitionFromExit(entity, pos, largestRectangleAround, level, postTeleportTransition);
    }

    private static TeleportTransition getDimensionTransitionFromExit(
        Entity entity, BlockPos pos, BlockUtil.FoundRectangle rectangle, ServerLevel level, TeleportTransition.PostTeleportTransition postTeleportTransition
    ) {
        BlockState blockState = entity.level().getBlockState(pos);
        Direction.Axis axis;
        Vec3 relativePortalPosition;
        if (blockState.hasProperty(BlockStateProperties.HORIZONTAL_AXIS)) {
            axis = blockState.getValue(BlockStateProperties.HORIZONTAL_AXIS);
            BlockUtil.FoundRectangle largestRectangleAround = BlockUtil.getLargestRectangleAround(
                pos, axis, 21, Direction.Axis.Y, 21, blockPos -> entity.level().getBlockState(blockPos) == blockState
            );
            relativePortalPosition = entity.getRelativePortalPosition(axis, largestRectangleAround);
        } else {
            axis = Direction.Axis.X;
            relativePortalPosition = new Vec3(0.5, 0.0, 0.0);
        }

        return createDimensionTransition(level, rectangle, axis, relativePortalPosition, entity, postTeleportTransition);
    }

    public static TeleportTransition createDimensionTransition( // Folia - region threading - public
        ServerLevel level,
        BlockUtil.FoundRectangle rectangle,
        Direction.Axis axis,
        Vec3 offset,
        Entity entity,
        TeleportTransition.PostTeleportTransition postTeleportTransition
    ) {
        BlockPos blockPos = rectangle.minCorner;
        BlockState blockState = level.getBlockState(blockPos);
        Direction.Axis axis1 = blockState.getOptionalValue(BlockStateProperties.HORIZONTAL_AXIS).orElse(Direction.Axis.X);
        double d = rectangle.axis1Size;
        double d1 = rectangle.axis2Size;
        EntityDimensions dimensions = entity.getDimensions(entity.getPose());
        int i = axis == axis1 ? 0 : 90;
        double d2 = dimensions.width() / 2.0 + (d - dimensions.width()) * offset.x();
        double d3 = (d1 - dimensions.height()) * offset.y();
        double d4 = 0.5 + offset.z();
        boolean flag = axis1 == Direction.Axis.X;
        Vec3 vec3 = new Vec3(blockPos.getX() + (flag ? d2 : d4), blockPos.getY() + d3, blockPos.getZ() + (flag ? d4 : d2));
        Vec3 vec31 = PortalShape.findCollisionFreePosition(vec3, level, entity, dimensions);
        return new TeleportTransition(level, vec31, Vec3.ZERO, i, 0.0F, Relative.union(Relative.DELTA, Relative.ROTATION), postTeleportTransition, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.NETHER_PORTAL); // CraftBukkit
    }

    @Override
    public Portal.Transition getLocalTransition() {
        return Portal.Transition.CONFUSION;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(100) == 0) {
            level.playLocalSound(
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5,
                SoundEvents.PORTAL_AMBIENT,
                SoundSource.BLOCKS,
                0.5F,
                random.nextFloat() * 0.4F + 0.8F,
                false
            );
        }

        for (int i = 0; i < 4; i++) {
            double d = pos.getX() + random.nextDouble();
            double d1 = pos.getY() + random.nextDouble();
            double d2 = pos.getZ() + random.nextDouble();
            double d3 = (random.nextFloat() - 0.5) * 0.5;
            double d4 = (random.nextFloat() - 0.5) * 0.5;
            double d5 = (random.nextFloat() - 0.5) * 0.5;
            int i1 = random.nextInt(2) * 2 - 1;
            if (!level.getBlockState(pos.west()).is(this) && !level.getBlockState(pos.east()).is(this)) {
                d = pos.getX() + 0.5 + 0.25 * i1;
                d3 = random.nextFloat() * 2.0F * i1;
            } else {
                d2 = pos.getZ() + 0.5 + 0.25 * i1;
                d5 = random.nextFloat() * 2.0F * i1;
            }

            level.addParticle(ParticleTypes.PORTAL, d, d1, d2, d3, d4, d5);
        }
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return ItemStack.EMPTY;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        switch (rotation) {
            case COUNTERCLOCKWISE_90:
            case CLOCKWISE_90:
                switch ((Direction.Axis)state.getValue(AXIS)) {
                    case X:
                        return state.setValue(AXIS, Direction.Axis.Z);
                    case Z:
                        return state.setValue(AXIS, Direction.Axis.X);
                    default:
                        return state;
                }
            default:
                return state;
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }
}
