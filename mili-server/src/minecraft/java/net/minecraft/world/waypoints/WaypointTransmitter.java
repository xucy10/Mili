package net.minecraft.world.waypoints;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundTrackedWaypointPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

public interface WaypointTransmitter extends Waypoint {
    int REALLY_FAR_DISTANCE = 332;

    boolean isTransmittingWaypoint();

    Optional<WaypointTransmitter.Connection> makeWaypointConnectionWith(ServerPlayer player);

    Waypoint.Icon waypointIcon();

    static boolean doesSourceIgnoreReceiver(LivingEntity entity, ServerPlayer player) {
        if (!player.getBukkitEntity().canSee(entity.getBukkitEntity())) return true; // Paper - ignore if entity is hidden from player
        if (player.isSpectator()) {
            return false;
        } else if (!entity.isSpectator() && !entity.hasIndirectPassenger(player)) {
            double min = Math.min(entity.getAttributeValue(Attributes.WAYPOINT_TRANSMIT_RANGE), player.getAttributeValue(Attributes.WAYPOINT_RECEIVE_RANGE));
            return entity.distanceTo(player) >= min;
        } else {
            return true;
        }
    }

    static boolean isChunkVisible(ChunkPos pos, ServerPlayer player) {
        // Paper start - rewrite chunk system
        final ca.spottedleaf.moonrise.patches.chunk_system.player.RegionizedPlayerChunkLoader.PlayerChunkLoaderData playerChunkLoader = ((ca.spottedleaf.moonrise.patches.chunk_system.player.ChunkSystemServerPlayer)player).moonrise$getChunkLoader();
        return playerChunkLoader != null && playerChunkLoader.getSentChunksRaw().contains(ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkKey(pos));
        // Paper end - rewrite chunk system
    }

    static boolean isReallyFar(LivingEntity entity, ServerPlayer player) {
        return entity.distanceTo(player) > 332.0F;
    }

    public interface BlockConnection extends WaypointTransmitter.Connection {
        int distanceManhattan();

        @Override
        default boolean isBroken() {
            return this.distanceManhattan() > 1;
        }
    }

    public interface ChunkConnection extends WaypointTransmitter.Connection {
        int distanceChessboard();

        @Override
        default boolean isBroken() {
            return this.distanceChessboard() > 1;
        }
    }

    public interface Connection {
        void connect();

        void disconnect();

        void update();

        boolean isBroken();
    }

    public static class EntityAzimuthConnection implements WaypointTransmitter.Connection {
        private final LivingEntity source;
        private final Waypoint.Icon icon;
        private final ServerPlayer receiver;
        private volatile float lastAngle; // Luminol - Restore waypoints
        private final java.util.UUID sourceUUID; // Luminol - Restore waypoints (prevent UUID mutation)

        public EntityAzimuthConnection(LivingEntity source, Waypoint.Icon icon, ServerPlayer receiver) {
            this.source = source;
            this.icon = icon;
            this.receiver = receiver;
            Vec3 vec3 = receiver.position().subtract(source.position()).rotateClockwise90();
            this.lastAngle = (float)Mth.atan2(vec3.z(), vec3.x());
            this.sourceUUID = this.source.getUUID(); // Luminol - Restore waypoints (prevent UUID mutation)
        }

        @Override
        public boolean isBroken() {
            return WaypointTransmitter.doesSourceIgnoreReceiver(this.source, this.receiver)
                || WaypointTransmitter.isChunkVisible(this.source.chunkPosition(), this.receiver)
                || !WaypointTransmitter.isReallyFar(this.source, this.receiver);
        }

        @Override
        public void connect() {
            this.receiver.connection.send(ClientboundTrackedWaypointPacket.addWaypointAzimuth(this.sourceUUID, this.icon, this.lastAngle)); // Luminol - Restore waypoints (prevent UUID mutation)
        }

        @Override
        public void disconnect() {
            this.receiver.connection.send(ClientboundTrackedWaypointPacket.removeWaypoint(this.sourceUUID)); // Luminol - Restore waypoints (prevent UUID mutation)
        }

        @Override
        public void update() {
            float fetchedAngle;
            synchronized (this) { // Luminol - Restore waypoints
            Vec3 vec3 = this.receiver.position().subtract(this.source.position()).rotateClockwise90();
            float f = (float)Mth.atan2(vec3.z(), vec3.x());
            fetchedAngle = f; // Luminol - Restore waypoints
            if (Mth.abs(f - this.lastAngle) > 0.008726646F) {
                // this.receiver.connection.send(ClientboundTrackedWaypointPacket.updateWaypointAzimuth(this.sourceUUID, this.icon, f)); // Luminol - Restore waypoints (prevent UUID mutation) (move down)
                this.lastAngle = f;
            }
            } // Luminol - Restore waypoints
            this.receiver.connection.send(ClientboundTrackedWaypointPacket.updateWaypointAzimuth(this.sourceUUID, this.icon, fetchedAngle)); // Luminol - Restore waypoints (prevent UUID mutation)
        }
    }

    public static class EntityBlockConnection implements WaypointTransmitter.BlockConnection {
        private final LivingEntity source;
        private final Waypoint.Icon icon;
        private final ServerPlayer receiver;
        private volatile BlockPos lastPosition; // Luminol - Restore waypoints
        private final java.util.UUID sourceUUID; // Luminol - Restore waypoints (prevent UUID mutation)

        public EntityBlockConnection(LivingEntity source, Waypoint.Icon icon, ServerPlayer receiver) {
            this.source = source;
            this.receiver = receiver;
            this.icon = icon;
            this.lastPosition = source.blockPosition();
            this.sourceUUID = this.source.getUUID(); // Luminol - Restore waypoints (prevent UUID mutation)
        }

        @Override
        public void connect() {
            this.receiver.connection.send(ClientboundTrackedWaypointPacket.addWaypointPosition(this.sourceUUID, this.icon, this.lastPosition)); // Luminol - Restore waypoints (prevent UUID mutation)
        }

        @Override
        public void disconnect() {
            this.receiver.connection.send(ClientboundTrackedWaypointPacket.removeWaypoint(this.sourceUUID)); // Luminol - Restore waypoints (prevent UUID mutation)
        }

        @Override
        public void update() {
            BlockPos fetchedBlockPos;
            synchronized (this) { // Luminol - Restore waypoints
            BlockPos blockPos = this.source.blockPosition();
            fetchedBlockPos = blockPos; // Luminol - Restore waypoints
            if (blockPos.distManhattan(this.lastPosition) > 0) {
                // this.receiver.connection.send(ClientboundTrackedWaypointPacket.updateWaypointPosition(this.sourceUUID, this.icon, blockPos)); // Luminol - Restore waypoints (prevent UUID mutation)  (move down)
                this.lastPosition = blockPos;
            }
            } // Luminol - Restore waypoints
            this.receiver.connection.send(ClientboundTrackedWaypointPacket.updateWaypointPosition(this.sourceUUID, this.icon, fetchedBlockPos)); // Luminol - Restore waypoints (prevent UUID mutation)  (move down)
        }

        @Override
        public int distanceManhattan() {
            return this.lastPosition.distManhattan(this.source.blockPosition());
        }

        @Override
        public boolean isBroken() {
            return WaypointTransmitter.BlockConnection.super.isBroken() || WaypointTransmitter.doesSourceIgnoreReceiver(this.source, this.receiver);
        }
    }

    public static class EntityChunkConnection implements WaypointTransmitter.ChunkConnection {
        private final LivingEntity source;
        private final Waypoint.Icon icon;
        private final ServerPlayer receiver;
        private volatile ChunkPos lastPosition; // Luminol - Restore waypoints
        private final java.util.UUID sourceUUID; // Luminol - Restore waypoints (prevent UUID mutation)

        public EntityChunkConnection(LivingEntity source, Waypoint.Icon icon, ServerPlayer receiver) {
            this.source = source;
            this.icon = icon;
            this.receiver = receiver;
            this.lastPosition = source.chunkPosition();
            this.sourceUUID = this.source.getUUID(); // Luminol - Restore waypoints (prevent UUID mutation)
        }

        @Override
        public int distanceChessboard() {
            return this.lastPosition.getChessboardDistance(this.source.chunkPosition());
        }

        @Override
        public void connect() {
            this.receiver.connection.send(ClientboundTrackedWaypointPacket.addWaypointChunk(this.sourceUUID, this.icon, this.lastPosition)); // Luminol - Restore waypoints (prevent UUID mutable)
        }

        @Override
        public void disconnect() {
            this.receiver.connection.send(ClientboundTrackedWaypointPacket.removeWaypoint(this.sourceUUID)); // Luminol - Restore waypoints (prevent UUID mutable)
        }

        @Override
        public void update() {
            ChunkPos fetchedChunkPos;
            synchronized (this) { // Luminol - Restore waypoints (prevent UUID mutable)
            ChunkPos chunkPos = this.source.chunkPosition();
            fetchedChunkPos = chunkPos; // Luminol - Restore waypoints (prevent UUID mutable)
            if (chunkPos.getChessboardDistance(this.lastPosition) > 0) {
                // this.receiver.connection.send(ClientboundTrackedWaypointPacket.updateWaypointChunk(this.sourceUUID, this.icon, chunkPos)); // Luminol - Restore waypoints (prevent UUID mutable) (move down)
                this.lastPosition = chunkPos;
            }
            } // Luminol - Restore waypoints (prevent UUID mutable)
            this.receiver.connection.send(ClientboundTrackedWaypointPacket.updateWaypointChunk(this.sourceUUID, this.icon, fetchedChunkPos)); // Luminol - Restore waypoints (prevent UUID mutable)
        }

        @Override
        public boolean isBroken() {
            return WaypointTransmitter.ChunkConnection.super.isBroken()
                || WaypointTransmitter.doesSourceIgnoreReceiver(this.source, this.receiver)
                || WaypointTransmitter.isChunkVisible(this.lastPosition, this.receiver);
        }
    }
}
