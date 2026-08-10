package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.TheEndPortalBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.EndPlatformFeature;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class EndPortalBlock extends BaseEntityBlock implements Portal {
    public static final MapCodec<EndPortalBlock> CODEC = simpleCodec(EndPortalBlock::new);
    private static final VoxelShape SHAPE = Block.column(16.0, 6.0, 12.0);

    @Override
    public MapCodec<EndPortalBlock> codec() {
        return CODEC;
    }

    protected EndPortalBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TheEndPortalBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getEntityInsideCollisionShape(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
        return state.getShape(level, pos);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean pastEdges) {
        if (!new io.papermc.paper.event.entity.EntityInsideBlockEvent(entity.getBukkitEntity(), org.bukkit.craftbukkit.block.CraftBlock.at(level, pos)).callEvent()) { return; } // Paper - Add EntityInsideBlockEvent
        if (entity.canUsePortal(false)) {
            // CraftBukkit start - Entity in portal
            org.bukkit.event.entity.EntityPortalEnterEvent event = new org.bukkit.event.entity.EntityPortalEnterEvent(entity.getBukkitEntity(), org.bukkit.craftbukkit.util.CraftLocation.toBukkit(pos, level), org.bukkit.PortalType.ENDER); // Paper - add portal type
            level.getCraftServer().getPluginManager().callEvent(event);
            if (event.isCancelled()) return; // Paper - make cancellable
            // CraftBukkit end
            if (false && !level.isClientSide() && level.dimension() == Level.END && entity instanceof ServerPlayer serverPlayer && !serverPlayer.seenCredits) { // Folia - region threading - do not show credits
                if (level.paperConfig().misc.disableEndCredits) {serverPlayer.seenCredits = true; return;} // Paper - Option to disable end credits
                serverPlayer.showEndCredits();
            } else {
                // Luminol start - unsafe teleportation
                if (me.earthme.luminol.config.modules.fixes.UnsafeTeleportationConfig.enabled && !(entity instanceof net.minecraft.world.entity.player.Player)) {
                    entity.endPortalLogicAsync(pos);
                }
                // Luminol end
                entity.setAsInsidePortal(this, pos);
            }
        }
    }

    @Override
    public @Nullable TeleportTransition getPortalDestination(ServerLevel level, Entity entity, BlockPos pos) {
        LevelData.RespawnData respawnData = level.getRespawnData();
        ResourceKey<Level> resourceKey = level.dimension();
        boolean flag = level.getTypeKey() == net.minecraft.world.level.dimension.LevelStem.END; // CraftBukkit - SPIGOT-6152: send back to main overworld in custom ends
        ResourceKey<Level> resourceKey1 = flag ? respawnData.dimension() : Level.END;
        BlockPos blockPos = flag ? respawnData.pos() : ServerLevel.END_SPAWN_POINT;
        ServerLevel level1 = level.getServer().getLevel(resourceKey1);
        if (level1 == null) {
            return null;
        } else {
            Vec3 bottomCenter = blockPos.getBottomCenter();
            float f;
            float f1;
            Set<Relative> set;
            if (!flag) {
                EndPlatformFeature.createEndPlatform(level1, BlockPos.containing(bottomCenter).below(), true, entity); // CraftBukkit
                f = Direction.WEST.toYRot();
                f1 = 0.0F;
                set = Relative.union(Relative.DELTA, Set.of(Relative.X_ROT));
                if (entity instanceof ServerPlayer) {
                    bottomCenter = bottomCenter.subtract(0.0, 1.0, 0.0);
                }
            } else {
                f = respawnData.yaw();
                f1 = respawnData.pitch();
                set = Relative.union(Relative.DELTA, Relative.ROTATION);
                if (entity instanceof ServerPlayer serverPlayer) {
                    return serverPlayer.findRespawnPositionAndUseSpawnBlock(false, TeleportTransition.DO_NOTHING, org.bukkit.event.player.PlayerRespawnEvent.RespawnReason.END_PORTAL); // CraftBukkit
                }

                bottomCenter = entity.adjustSpawnLocation(level1, blockPos).getBottomCenter();
            }

            // CraftBukkit start
            set.removeAll(Relative.ROTATION); // remove relative rotation flags to simplify event mutation
            float absoluteYaw = !flag ? f : entity.getYRot() + f;
            float absolutePitch = entity.getXRot() + f1;
            org.bukkit.craftbukkit.event.PortalEventResult result = org.bukkit.craftbukkit.event.CraftEventFactory.handlePortalEvents(entity, org.bukkit.craftbukkit.util.CraftLocation.toBukkit(bottomCenter, level1, absoluteYaw, absolutePitch), org.bukkit.PortalType.ENDER, 0, 0);
            if (result == null) {
                return null;
            }
            org.bukkit.Location to = result.to();

            return new TeleportTransition(((org.bukkit.craftbukkit.CraftWorld) to.getWorld()).getHandle(), org.bukkit.craftbukkit.util.CraftLocation.toVec3(to), Vec3.ZERO, to.getYaw(), to.getPitch(), set, TeleportTransition.PLAY_PORTAL_SOUND.then(TeleportTransition.PLACE_PORTAL_TICKET), org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.END_PORTAL);
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

        return portalTarget.endPortalLogicAsync(portalPos);
    }
    // Folia end - region threading

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double d = pos.getX() + random.nextDouble();
        double d1 = pos.getY() + 0.8;
        double d2 = pos.getZ() + random.nextDouble();
        level.addParticle(ParticleTypes.SMOKE, d, d1, d2, 0.0, 0.0, 0.0);
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return ItemStack.EMPTY;
    }

    @Override
    protected boolean canBeReplaced(BlockState state, Fluid fluid) {
        return false;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }
}
