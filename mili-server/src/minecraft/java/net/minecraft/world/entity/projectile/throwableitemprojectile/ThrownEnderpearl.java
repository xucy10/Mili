package net.minecraft.world.entity.projectile.throwableitemprojectile;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class ThrownEnderpearl extends ThrowableItemProjectile {
    private long ticketTimer = 0L;

    public ThrownEnderpearl(EntityType<? extends ThrownEnderpearl> type, Level level) {
        super(type, level);
    }

    public ThrownEnderpearl(Level level, LivingEntity owner, ItemStack item) {
        super(EntityType.ENDER_PEARL, owner, level, item);
    }

    @Override
    public Item getDefaultItem() {
        return Items.ENDER_PEARL;
    }

    @Override
    protected void setOwner(@Nullable EntityReference<Entity> owner) {
        this.deregisterFromCurrentOwner();
        super.setOwner(owner);
        this.registerToCurrentOwner();
    }

    private void deregisterFromCurrentOwner() {
        // Folia - region threading - we remove the registration logic, we do not need to fetch the owner
    }

    private void registerToCurrentOwner() {
        // Folia - region threading - we remove the registration logic, we do not need to fetch the owner
    }

    @Override
    public @Nullable Entity getOwner() {
        return this.owner != null && this.level() instanceof ServerLevel serverLevel ? this.owner.getEntity(serverLevel, Entity.class) : super.getOwner();
    }

    private static @Nullable Entity findOwnerIncludingDeadPlayer(ServerLevel level, UUID id) {
        Entity entityInAnyDimension = level.getEntityInAnyDimension(id);
        return (Entity)(entityInAnyDimension != null ? entityInAnyDimension : level.getServer().getPlayerList().getPlayer(id));
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        result.getEntity().hurt(this.damageSources().thrown(this, this.getOwner()), 0.0F);
    }

    // Folia start - region threading
    private static void attemptTeleport(Entity source, ServerLevel checkWorld, net.minecraft.world.phys.Vec3 to, Entity pearlEntity) {
        final boolean onPortalCooldown = source.isOnPortalCooldown();
        // ignore retired callback, in those cases we do not want to teleport
        source.getBukkitEntity().taskScheduler.schedule(
            (Entity entity) -> {
                if (!isAllowedToTeleportOwner(entity, checkWorld)) {
                    return;
                }
                // source is now an invalid reference, do not use it, use the entity parameter
                net.minecraft.world.phys.Vec3 endermitePos = entity.position();

                // dismount from any vehicles, so we can teleport and to prevent desync
                if (entity.isPassenger()) {
                    entity.unRide();
                }

                if (onPortalCooldown) {
                    entity.setPortalCooldown();
                }

                entity.teleportAsync(
                    checkWorld, to, null, null, null,
                    org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.ENDER_PEARL,
                    // chunk could have been unloaded
                    Entity.TELEPORT_FLAG_TELEPORT_PASSENGERS | Entity.TELEPORT_FLAG_LOAD_CHUNK,
                    (Entity teleported) -> {
                        // entity is now an invalid reference, do not use it, instead use teleported
                        if (teleported instanceof ServerPlayer player) {
                            // connection teleport is already done
                            ServerLevel world = player.level();

                            // endermite spawn chance
                            if (world.random.nextFloat() < 0.05F && world.isSpawningMonsters()) {
                                Endermite entityendermite = (Endermite) EntityType.ENDERMITE.create(world, EntitySpawnReason.TRIGGERED);

                                if (entityendermite != null) {
                                    float yRot = teleported.getYRot();
                                    float xRot = teleported.getXRot();
                                    Runnable spawn = () -> {
                                        entityendermite.snapTo(endermitePos.x, endermitePos.y, endermitePos.z, yRot, xRot);
                                        world.addFreshEntity(entityendermite, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.ENDER_PEARL);
                                    };

                                    if (ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(world, endermitePos, net.minecraft.world.phys.Vec3.ZERO, 1)) {
                                        spawn.run();
                                    } else {
                                        io.papermc.paper.threadedregions.RegionizedServer.getInstance().taskQueue.queueTickTaskQueue(
                                            world,
                                            ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkCoordinate(endermitePos.x),
                                            ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkCoordinate(endermitePos.z),
                                            spawn
                                        );
                                    }
                                }
                            }

                            // damage player
                            teleported.resetFallDistance();
                            player.resetCurrentImpulseContext();
                            player.hurtServer(player.level(), player.damageSources().enderPearl().eventEntityDamager(pearlEntity), 5.0F); // CraftBukkit // Paper - fix DamageSource API // Mili fix - #438: pass pearlEntity instead of player
                            playSound(teleported.level(), to);
                        } else {
                            // reset fall damage so that if the entity was falling they do not instantly die
                            teleported.resetFallDistance();
                            playSound(teleported.level(), to);
                        }
                    }
                );
            },
            null, 1L
        );
    }
    // Folia end - region threading

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);

        for (int i = 0; i < 32; i++) {
            this.level()
                .addParticle(
                    ParticleTypes.PORTAL,
                    this.getX(),
                    this.getY() + this.random.nextDouble() * 2.0,
                    this.getZ(),
                    this.random.nextGaussian(),
                    0.0,
                    this.random.nextGaussian()
                );
        }

        if (this.level() instanceof ServerLevel serverLevel && !this.isRemoved()) {
            // Folia start - region threading
            if (true) {
                // we can't fire events, because we do not actually know where the other entity is located
                if (!ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(this)) {
                    throw new IllegalStateException("Must be on tick thread for ticking entity: " + this);
                }
                Entity entity = this.getOwnerRaw();
                if (entity != null) {
                    attemptTeleport(entity, (ServerLevel)this.level(), this.position(), this); // Mili fix - #438: pass this (pearl entity) as damager
                }
                this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.HIT);
                return;
            }
            // Folia end - region threading
            Entity owner = this.getOwner();
            if (owner != null && isAllowedToTeleportOwner(owner, serverLevel)) {
                Vec3 vec3 = this.oldPosition();
                if (owner instanceof ServerPlayer serverPlayer) {
                    if (serverPlayer.connection.isAcceptingMessages()) {
                        // CraftBukkit start
                        // Store pre teleportation position as the teleport has been moved up.
                        final double preTeleportX = serverPlayer.getX(), preTeleportY = serverPlayer.getY(), preTeleportZ = serverPlayer.getZ();
                        final float preTeleportYRot = serverPlayer.getYRot(), preTeleportXRot = serverPlayer.getXRot();
                        ServerPlayer serverPlayer1 = serverPlayer.teleport(new TeleportTransition(serverLevel, vec3, Vec3.ZERO, 0.0F, 0.0F, Relative.union(Relative.ROTATION, Relative.DELTA), TeleportTransition.DO_NOTHING, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.ENDER_PEARL));
                        if (serverPlayer1 == null) {
                            this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.HIT);
                            return;
                        }
                        // CraftBukkit end
                        if (this.random.nextFloat() < 0.05F && serverLevel.isSpawningMonsters()) {
                            Endermite endermite = EntityType.ENDERMITE.create(serverLevel, EntitySpawnReason.TRIGGERED);
                            if (endermite != null) {
                                endermite.snapTo(preTeleportX, preTeleportY, preTeleportZ, preTeleportYRot, preTeleportXRot); // Paper - spawn endermite at pre teleport position as teleport has been moved up
                                serverLevel.addFreshEntity(endermite, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.ENDER_PEARL); // Paper - add reason
                            }
                        }

                        if (this.isOnPortalCooldown()) {
                            owner.setPortalCooldown();
                        }

                        // CraftBukkit start - moved up
                        // ServerPlayer serverPlayer1 = serverPlayer.teleport(
                        //     new TeleportTransition(
                        //         serverLevel, vec3, Vec3.ZERO, 0.0F, 0.0F, Relative.union(Relative.ROTATION, Relative.DELTA), TeleportTransition.DO_NOTHING
                        //     )
                        // );
                        // CraftBukkit end - moved up
                        if (serverPlayer1 != null) {
                            serverPlayer1.resetFallDistance();
                            serverPlayer1.resetCurrentImpulseContext();
                            serverPlayer1.hurtServer(serverPlayer.level(), this.damageSources().enderPearl().eventEntityDamager(this), 5.0F); // CraftBukkit // Paper - fix DamageSource API
                        }

                        this.playSound(serverLevel, vec3);
                    }
                } else {
                    Entity entity = owner.teleport(
                        new TeleportTransition(serverLevel, vec3, owner.getDeltaMovement(), owner.getYRot(), owner.getXRot(), TeleportTransition.DO_NOTHING)
                    );
                    if (entity != null) {
                        entity.resetFallDistance();
                    }

                    this.playSound(serverLevel, vec3);
                }

                this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.HIT); // CraftBukkit - add Bukkit remove cause
            } else {
                this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.HIT); // CraftBukkit - add Bukkit remove cause
            }
        }
    }

    private static boolean isAllowedToTeleportOwner(Entity entity, Level level) {
        if (entity.level().dimension() == level.dimension()) {
            return !(entity instanceof LivingEntity livingEntity) ? entity.isAlive() : livingEntity.isAlive() && !livingEntity.isSleeping();
        } else {
            return entity.canUsePortal(true);
        }
    }

    @Override
    public void tick() {
        if (this.level() instanceof ServerLevel serverLevel) {
            int var7 = SectionPos.blockToSectionCoord(this.position().x());
            int sectionPosZ = SectionPos.blockToSectionCoord(this.position().z());
            Entity entity = this.owner != null ? findOwnerIncludingDeadPlayer(serverLevel, this.owner.getUUID()) : null;
            if (entity instanceof ServerPlayer serverPlayer
                // && !entity.isAlive() // Luminol - Fix misbehaved ender pearls when player switched dimension
                && (entity.getBukkitEntity().taskScheduler.isRetired() || serverPlayer.getHealth() <= 0.0D) // Luminol - Fix misbehaved ender pearls when player switched dimension
                && !serverPlayer.wonGame
                && serverPlayer.level().getGameRules().get(GameRules.ENDER_PEARLS_VANISH_ON_DEATH)) {
                this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
            } else {
                super.tick();
            }

            if (this.isAlive()) {
                BlockPos blockPos = BlockPos.containing(this.position());
                if ((
                        --this.ticketTimer <= 0L
                            || var7 != SectionPos.blockToSectionCoord(blockPos.getX())
                            || sectionPosZ != SectionPos.blockToSectionCoord(blockPos.getZ())
                    )
                    && entity instanceof ServerPlayer serverPlayer1) {
                    this.ticketTimer = serverPlayer1.registerAndUpdateEnderPearlTicket(this);
                }
            }
        } else {
            super.tick();
        }
    }

    // Folia start - region threading
    @Override
    public void preChangeDimension() {
        super.preChangeDimension();
        // Don't change the owner here, since the tick logic will consider it anyways.
    }
    // Folia end - region threading

    private static void playSound(Level level, Vec3 pos) { // Folia - region threading - static
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS);
    }

    @Override
    public @Nullable Entity teleport(TeleportTransition teleportTransition) {
        Entity entity = super.teleport(teleportTransition);
        if (entity != null) {
            if (!this.level().paperConfig().misc.legacyEnderPearlBehavior) entity.placePortalTicket(BlockPos.containing(entity.position())); // Paper - Allow using old ender pearl behavior
        }

        return entity;
    }

    @Override
    public boolean canTeleport(Level fromLevel, Level toLevel) {
        return fromLevel.getTypeKey() == net.minecraft.world.level.dimension.LevelStem.END && toLevel.getTypeKey() == net.minecraft.world.level.dimension.LevelStem.OVERWORLD && this.getOwner() instanceof ServerPlayer serverPlayer // CraftBukkit
            ? super.canTeleport(fromLevel, toLevel) && serverPlayer.seenCredits
            : super.canTeleport(fromLevel, toLevel);
    }

    @Override
    protected void onInsideBlock(BlockState state) {
        super.onInsideBlock(state);
        if (state.is(Blocks.END_GATEWAY) && this.getOwner() instanceof ServerPlayer serverPlayer) {
            serverPlayer.onInsideBlock(state);
        }
    }

    @Override
    public void onRemoval(Entity.RemovalReason reason) {
        if (reason != Entity.RemovalReason.UNLOADED_WITH_PLAYER) {
            this.deregisterFromCurrentOwner();
        }

        super.onRemoval(reason);
    }

    @Override
    public void onAboveBubbleColumn(boolean downwards, BlockPos pos) {
        Entity.handleOnAboveBubbleColumn(this, downwards, pos);
    }

    @Override
    public void onInsideBubbleColumn(boolean downwards) {
        Entity.handleOnInsideBubbleColumn(this, downwards);
    }
}
