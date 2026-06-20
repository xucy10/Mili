package net.minecraft.world.entity;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public interface Leashable {
    String LEASH_TAG = "leash";
    double LEASH_TOO_FAR_DIST = 12.0;
    double LEASH_ELASTIC_DIST = 6.0;
    double MAXIMUM_ALLOWED_LEASHED_DIST = 16.0;
    Vec3 AXIS_SPECIFIC_ELASTICITY = new Vec3(0.8, 0.2, 0.8);
    float SPRING_DAMPENING = 0.7F;
    double TORSIONAL_ELASTICITY = 10.0;
    double STIFFNESS = 0.11;
    List<Vec3> ENTITY_ATTACHMENT_POINT = ImmutableList.of(new Vec3(0.0, 0.5, 0.5));
    List<Vec3> LEASHER_ATTACHMENT_POINT = ImmutableList.of(new Vec3(0.0, 0.5, 0.0));
    List<Vec3> SHARED_QUAD_ATTACHMENT_POINTS = ImmutableList.of(
        new Vec3(-0.5, 0.5, 0.5), new Vec3(-0.5, 0.5, -0.5), new Vec3(0.5, 0.5, -0.5), new Vec3(0.5, 0.5, 0.5)
    );

    Leashable.@Nullable LeashData getLeashData();

    void setLeashData(Leashable.@Nullable LeashData leashData);

    default boolean isLeashed() {
        return this.getLeashData() != null && this.getLeashData().leashHolder != null;
    }

    default boolean mayBeLeashed() {
        return this.getLeashData() != null;
    }

    default boolean canHaveALeashAttachedTo(Entity entity) {
        return this != entity && !(this.leashDistanceTo(entity) > this.leashSnapDistance()) && this.canBeLeashed();
    }

    default double leashDistanceTo(Entity entity) {
        return entity.getBoundingBox().getCenter().distanceTo(((Entity)this).getBoundingBox().getCenter());
    }

    default boolean canBeLeashed() {
        return true;
    }

    default void setDelayedLeashHolderId(int delayedLeashHolderId) {
        this.setLeashData(new Leashable.LeashData(delayedLeashHolderId));
        dropLeash((Entity & Leashable)this, false, false);
    }

    default void readLeashData(ValueInput input) {
        Leashable.LeashData leashData = input.read("leash", Leashable.LeashData.CODEC).orElse(null);
        if (this.getLeashData() != null && leashData == null) {
            this.removeLeash();
        }

        this.setLeashData(leashData);
    }

    default void writeLeashData(ValueOutput output, Leashable.@Nullable LeashData leashData) {
        // CraftBukkit start - SPIGOT-7487: Don't save (and possible drop) leash, when the holder was removed by a plugin
        if (leashData != null && leashData.leashHolder != null && leashData.leashHolder.pluginRemoved) {
            return;
        }
        // CraftBukkit end
        output.storeNullable("leash", Leashable.LeashData.CODEC, leashData);
    }

    private static <E extends Entity & Leashable> void restoreLeashFromSave(E entity, Leashable.LeashData leashData) {
        if (leashData.delayedLeashInfo != null && entity.level() instanceof ServerLevel serverLevel) {
            Optional<UUID> optional = leashData.delayedLeashInfo.left();
            Optional<BlockPos> optional1 = leashData.delayedLeashInfo.right();
            if (optional.isPresent()) {
                Entity entity1 = serverLevel.getEntity(optional.get());
                if (entity1 != null) {
                    // Luminol start - Fix off region leashing
                    if (!ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(entity1)) {
                        entity.forceDrops = true;
                        entity.spawnAtLocation(serverLevel, Items.LEAD);
                        entity.forceDrops = false;
                        entity.setLeashData(null);
                        return;
                    }
                    // Luminol end
                    setLeashedTo(entity, entity1, true);
                    return;
                }
            } else if (optional1.isPresent()) {
                // Luminol start - Fix off region leashing
                if (!ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(serverLevel, optional1.get())) {
                    entity.forceDrops = true;
                    entity.spawnAtLocation(serverLevel, Items.LEAD);
                    entity.forceDrops = false;
                    entity.setLeashData(null);
                    return;
                }
                // Luminol end
                setLeashedTo(entity, LeashFenceKnotEntity.getOrCreateKnot(serverLevel, optional1.get()), true);
                return;
            }

            if (entity.tickCount > 100) {
                entity.forceDrops = true; // CraftBukkit
                entity.spawnAtLocation(serverLevel, Items.LEAD);
                entity.forceDrops = false; // CraftBukkit
                entity.setLeashData(null);
            }
        }
    }

    default void dropLeash() {
        dropLeash((Entity & Leashable)this, true, true);
    }

    default void removeLeash() {
        dropLeash((Entity & Leashable)this, true, false);
    }

    default void onLeashRemoved() {
    }

    private static <E extends Entity & Leashable> void dropLeash(E entity, boolean broadcastPacket, boolean dropItem) {
        Leashable.LeashData leashData = entity.getLeashData();
        if (leashData != null && leashData.leashHolder != null) {
            entity.setLeashData(null);
            entity.onLeashRemoved();
            if (entity.level() instanceof ServerLevel serverLevel) {
                if (dropItem) {
                    entity.forceDrops = true; // CraftBukkit
                    entity.spawnAtLocation(serverLevel, Items.LEAD);
                    entity.forceDrops = false; // CraftBukkit
                }

                if (broadcastPacket) {
                    serverLevel.getChunkSource().sendToTrackingPlayers(entity, new ClientboundSetEntityLinkPacket(entity, null));
                }

                leashData.leashHolder.notifyLeasheeRemoved(entity);
            }
        }
    }

    static <E extends Entity & Leashable> void tickLeash(ServerLevel level, E entity) {
        Leashable.LeashData leashData = entity.getLeashData();
        if (leashData != null && leashData.delayedLeashInfo != null) {
            restoreLeashFromSave(entity, leashData);
        }

        if (leashData != null && leashData.leashHolder != null) {
            if (!entity.canInteractWithLevel() || !leashData.leashHolder.canInteractWithLevel()) {
                // Paper start - Expand EntityUnleashEvent
                final org.bukkit.event.entity.EntityUnleashEvent event = new org.bukkit.event.entity.EntityUnleashEvent(
                    entity.getBukkitEntity(),
                    !entity.isAlive() ? org.bukkit.event.entity.EntityUnleashEvent.UnleashReason.PLAYER_UNLEASH : org.bukkit.event.entity.EntityUnleashEvent.UnleashReason.HOLDER_GONE,
                    level.getGameRules().get(GameRules.ENTITY_DROPS) && !entity.pluginRemoved
                );
                event.callEvent();
                if (event.isDropLeash()) { // CraftBukkit - SPIGOT-7487: Don't drop leash, when the holder was removed by a plugin
                    // Paper end - Expand EntityUnleashEvent
                    entity.dropLeash();
                } else {
                    entity.removeLeash();
                }
            }

            Entity leashHolder = entity.getLeashHolder();
            if (leashHolder != null && leashHolder.level() == entity.level()) {
                double d = entity.leashDistanceTo(leashHolder);
                entity.whenLeashedTo(leashHolder);
                if (d > entity.leashSnapDistanceOrConfig()) { // Paper - Configurable max leash distance
                    entity.leashTooFarBehaviour();
                } else if (d > entity.leashElasticDistance() - leashHolder.getBbWidth() - entity.getBbWidth()
                    && entity.checkElasticInteractions(leashHolder, leashData)) {
                    entity.onElasticLeashPull();
                } else {
                    entity.closeRangeLeashBehaviour(leashHolder);
                }

                entity.setYRot((float)(entity.getYRot() - leashData.angularMomentum));
                leashData.angularMomentum = leashData.angularMomentum * angularFriction(entity);
            }
        }
    }

    default void onElasticLeashPull() {
        Entity entity = (Entity)this;
        entity.checkFallDistanceAccumulation();
    }

    // Paper start - Configurable max leash distance
    default double leashSnapDistanceOrConfig() {
        if (!(this instanceof final Entity entity)) return leashSnapDistance();
        return entity.level().paperConfig().misc.maxLeashDistance.or(leashSnapDistance());
    }
    // Paper end - Configurable max leash distance
    default double leashSnapDistance() {
        return 12.0;
    }

    default double leashElasticDistance() {
        return 6.0;
    }

    static <E extends Entity & Leashable> float angularFriction(E entity) {
        if (entity.onGround()) {
            return entity.level().getBlockState(entity.getBlockPosBelowThatAffectsMyMovement()).getBlock().getFriction() * 0.91F;
        } else {
            return entity.isInLiquid() ? 0.8F : 0.91F;
        }
    }

    default void whenLeashedTo(Entity entity) {
        entity.notifyLeashHolder(this);
    }

    default void leashTooFarBehaviour() {
        // CraftBukkit start
        boolean dropLeash = true; // Paper
        if (this instanceof Entity entity) {
            // Paper start - Expand EntityUnleashEvent
            final org.bukkit.event.entity.EntityUnleashEvent event = new org.bukkit.event.entity.EntityUnleashEvent(entity.getBukkitEntity(), org.bukkit.event.entity.EntityUnleashEvent.UnleashReason.DISTANCE, true);
            if (!event.callEvent()) return;

            Entity leashHolder = this.getLeashHolder();
            Level level = leashHolder.level();
            dropLeash = event.isDropLeash();
            level.playSound(null, leashHolder.getX(), leashHolder.getY(), leashHolder.getZ(), SoundEvents.LEAD_BREAK, SoundSource.NEUTRAL, 1.0F, 1.0F); // Moved from Leashable#tickLeash
        }
        // CraftBukkit end
        if (dropLeash) {
            this.dropLeash();
        } else {
            this.removeLeash();
        }
        // Paper end - Expand EntityUnleashEvent
    }

    default void closeRangeLeashBehaviour(Entity entity) {
    }

    default boolean checkElasticInteractions(Entity entity, Leashable.LeashData leashData) {
        boolean flag = entity.supportQuadLeashAsHolder() && this.supportQuadLeash();
        List<Leashable.Wrench> list = computeElasticInteraction(
            (Entity & Leashable)this,
            entity,
            flag ? SHARED_QUAD_ATTACHMENT_POINTS : ENTITY_ATTACHMENT_POINT,
            flag ? SHARED_QUAD_ATTACHMENT_POINTS : LEASHER_ATTACHMENT_POINT
        );
        if (list.isEmpty()) {
            return false;
        } else {
            Leashable.Wrench wrench = Leashable.Wrench.accumulate(list).scale(flag ? 0.25 : 1.0);
            leashData.angularMomentum = leashData.angularMomentum + 10.0 * wrench.torque();
            Vec3 vec3 = getHolderMovement(entity).subtract(((Entity)this).getKnownMovement());
            ((Entity)this).addDeltaMovement(wrench.force().multiply(AXIS_SPECIFIC_ELASTICITY).add(vec3.scale(0.11)));
            return true;
        }
    }

    private static Vec3 getHolderMovement(Entity holder) {
        return holder instanceof Mob mob && mob.isNoAi() ? Vec3.ZERO : holder.getKnownMovement();
    }

    private static <E extends Entity & Leashable> List<Leashable.Wrench> computeElasticInteraction(
        E entity, Entity leashHolder, List<Vec3> entityAttachmentPoint, List<Vec3> leasherAttachmentPoint
    ) {
        double d = entity.leashElasticDistance();
        Vec3 holderMovement = getHolderMovement(entity);
        float f = entity.getYRot() * (float) (Math.PI / 180.0);
        Vec3 vec3 = new Vec3(entity.getBbWidth(), entity.getBbHeight(), entity.getBbWidth());
        float f1 = leashHolder.getYRot() * (float) (Math.PI / 180.0);
        Vec3 vec31 = new Vec3(leashHolder.getBbWidth(), leashHolder.getBbHeight(), leashHolder.getBbWidth());
        List<Leashable.Wrench> list = new ArrayList<>();

        for (int i = 0; i < entityAttachmentPoint.size(); i++) {
            Vec3 vec32 = entityAttachmentPoint.get(i).multiply(vec3).yRot(-f);
            Vec3 vec33 = entity.position().add(vec32);
            Vec3 vec34 = leasherAttachmentPoint.get(i).multiply(vec31).yRot(-f1);
            Vec3 vec35 = leashHolder.position().add(vec34);
            computeDampenedSpringInteraction(vec35, vec33, d, holderMovement, vec32).ifPresent(list::add);
        }

        return list;
    }

    private static Optional<Leashable.Wrench> computeDampenedSpringInteraction(
        Vec3 entityAttachmentPoint, Vec3 leasherAttachmentPoint, double elasticDistance, Vec3 knownMovement, Vec3 relativeAttachmentPoint
    ) {
        double d = leasherAttachmentPoint.distanceTo(entityAttachmentPoint);
        if (d < elasticDistance) {
            return Optional.empty();
        } else {
            Vec3 vec3 = entityAttachmentPoint.subtract(leasherAttachmentPoint).normalize().scale(d - elasticDistance);
            double d1 = Leashable.Wrench.torqueFromForce(relativeAttachmentPoint, vec3);
            boolean flag = knownMovement.dot(vec3) >= 0.0;
            if (flag) {
                vec3 = vec3.scale(0.3F);
            }

            return Optional.of(new Leashable.Wrench(vec3, d1));
        }
    }

    default boolean supportQuadLeash() {
        return false;
    }

    default Vec3[] getQuadLeashOffsets() {
        return createQuadLeashOffsets((Entity)this, 0.0, 0.5, 0.5, 0.5);
    }

    static Vec3[] createQuadLeashOffsets(Entity entity, double zOffset, double z, double x, double y) {
        float bbWidth = entity.getBbWidth();
        double d = zOffset * bbWidth;
        double d1 = z * bbWidth;
        double d2 = x * bbWidth;
        double d3 = y * entity.getBbHeight();
        return new Vec3[]{new Vec3(-d2, d3, d1 + d), new Vec3(-d2, d3, -d1 + d), new Vec3(d2, d3, -d1 + d), new Vec3(d2, d3, d1 + d)};
    }

    default Vec3 getLeashOffset(float partialTick) {
        return this.getLeashOffset();
    }

    default Vec3 getLeashOffset() {
        Entity entity = (Entity)this;
        return new Vec3(0.0, entity.getEyeHeight(), entity.getBbWidth() * 0.4F);
    }

    default void setLeashedTo(Entity leashHolder, boolean broadcastPacket) {
        if (this != leashHolder) {
            setLeashedTo((Entity & Leashable)this, leashHolder, broadcastPacket);
        }
    }

    private static <E extends Entity & Leashable> void setLeashedTo(E entity, Entity leashHolder, boolean broadcastPacket) {
        Leashable.LeashData leashData = entity.getLeashData();
        if (leashData == null) {
            leashData = new Leashable.LeashData(leashHolder);
            entity.setLeashData(leashData);
        } else {
            Entity entity1 = leashData.leashHolder;
            leashData.setLeashHolder(leashHolder);
            if (entity1 != null && entity1 != leashHolder) {
                entity1.notifyLeasheeRemoved(entity);
            }
        }

        if (broadcastPacket && entity.level() instanceof ServerLevel serverLevel) {
            serverLevel.getChunkSource().sendToTrackingPlayers(entity, new ClientboundSetEntityLinkPacket(entity, leashHolder));
        }

        if (entity.isPassenger()) {
            entity.stopRiding();
        }
    }

    default @Nullable Entity getLeashHolder() {
        return getLeashHolder((Entity & Leashable)this);
    }

    private static <E extends Entity & Leashable> @Nullable Entity getLeashHolder(E entity) {
        Leashable.LeashData leashData = entity.getLeashData();
        if (leashData == null) {
            return null;
        } else {
            if (leashData.delayedLeashHolderId != 0 && entity.level().isClientSide()) {
                Entity var3 = entity.level().getEntity(leashData.delayedLeashHolderId);
                if (var3 instanceof Entity) {
                    leashData.setLeashHolder(var3);
                }
            }

            return leashData.leashHolder;
        }
    }

    static List<Leashable> leashableLeashedTo(Entity entity) {
        return leashableInArea(entity, leashable -> leashable.getLeashHolder() == entity);
    }

    static List<Leashable> leashableInArea(Entity entity, Predicate<Leashable> predicate) {
        return leashableInArea(entity.level(), entity.getBoundingBox().getCenter(), predicate);
    }

    static List<Leashable> leashableInArea(Level level, Vec3 pos, Predicate<Leashable> predicate) {
        double d = 32.0;
        AABB aabb = AABB.ofSize(pos, 32.0, 32.0, 32.0);
        return level.getEntitiesOfClass(Entity.class, aabb, entity -> entity instanceof Leashable leashable && predicate.test(leashable))
            .stream()
            .map(Leashable.class::cast)
            .toList();
    }

    public static final class LeashData {
        public static final Codec<Leashable.LeashData> CODEC = Codec.xor(UUIDUtil.CODEC.fieldOf("UUID").codec(), BlockPos.CODEC)
            .xmap(
                Leashable.LeashData::new,
                leashData -> {
                    if (leashData.leashHolder instanceof LeashFenceKnotEntity leashFenceKnotEntity) {
                        return Either.right(leashFenceKnotEntity.getPos());
                    } else {
                        return leashData.leashHolder != null
                            ? Either.left(leashData.leashHolder.getUUID())
                            : Objects.requireNonNull(leashData.delayedLeashInfo, "Invalid LeashData had no attachment");
                    }
                }
            );
        int delayedLeashHolderId;
        public @Nullable Entity leashHolder;
        public @Nullable Either<UUID, BlockPos> delayedLeashInfo;
        public double angularMomentum;

        private LeashData(Either<UUID, BlockPos> delayedLeashInfo) {
            this.delayedLeashInfo = delayedLeashInfo;
        }

        LeashData(Entity leashHolder) {
            this.leashHolder = leashHolder;
        }

        LeashData(int delayedLeashHolderId) {
            this.delayedLeashHolderId = delayedLeashHolderId;
        }

        public void setLeashHolder(Entity leashHolder) {
            this.leashHolder = leashHolder;
            this.delayedLeashInfo = null;
            this.delayedLeashHolderId = 0;
        }
    }

    public record Wrench(Vec3 force, double torque) {
        static Leashable.Wrench ZERO = new Leashable.Wrench(Vec3.ZERO, 0.0);

        static double torqueFromForce(Vec3 attachmentPoint, Vec3 force) {
            return attachmentPoint.z * force.x - attachmentPoint.x * force.z;
        }

        static Leashable.Wrench accumulate(List<Leashable.Wrench> wrenches) {
            if (wrenches.isEmpty()) {
                return ZERO;
            } else {
                double d = 0.0;
                double d1 = 0.0;
                double d2 = 0.0;
                double d3 = 0.0;

                for (Leashable.Wrench wrench : wrenches) {
                    Vec3 vec3 = wrench.force;
                    d += vec3.x;
                    d1 += vec3.y;
                    d2 += vec3.z;
                    d3 += wrench.torque;
                }

                return new Leashable.Wrench(new Vec3(d, d1, d2), d3);
            }
        }

        public Leashable.Wrench scale(double scale) {
            return new Leashable.Wrench(this.force.scale(scale), this.torque * scale);
        }
    }
}
