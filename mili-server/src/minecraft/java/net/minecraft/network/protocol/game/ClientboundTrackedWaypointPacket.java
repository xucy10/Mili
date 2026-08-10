package net.minecraft.network.protocol.game;

import io.netty.buffer.ByteBuf;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.IntFunction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.util.ByIdMap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.waypoints.TrackedWaypoint;
import net.minecraft.world.waypoints.TrackedWaypointManager;
import net.minecraft.world.waypoints.Waypoint;
import net.minecraft.world.waypoints.WaypointManager;

public record ClientboundTrackedWaypointPacket(ClientboundTrackedWaypointPacket.Operation operation, TrackedWaypoint waypoint)
    implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundTrackedWaypointPacket> STREAM_CODEC = StreamCodec.composite(
        ClientboundTrackedWaypointPacket.Operation.STREAM_CODEC,
        ClientboundTrackedWaypointPacket::operation,
        TrackedWaypoint.STREAM_CODEC,
        ClientboundTrackedWaypointPacket::waypoint,
        ClientboundTrackedWaypointPacket::new
    );

    public static ClientboundTrackedWaypointPacket removeWaypoint(UUID uuid) {
        return new ClientboundTrackedWaypointPacket(ClientboundTrackedWaypointPacket.Operation.UNTRACK, TrackedWaypoint.empty(uuid));
    }

    public static ClientboundTrackedWaypointPacket addWaypointPosition(UUID uuid, Waypoint.Icon icon, Vec3i position) {
        return new ClientboundTrackedWaypointPacket(ClientboundTrackedWaypointPacket.Operation.TRACK, TrackedWaypoint.setPosition(uuid, icon, position));
    }

    public static ClientboundTrackedWaypointPacket updateWaypointPosition(UUID uuid, Waypoint.Icon icon, Vec3i position) {
        return new ClientboundTrackedWaypointPacket(ClientboundTrackedWaypointPacket.Operation.UPDATE, TrackedWaypoint.setPosition(uuid, icon, position));
    }

    public static ClientboundTrackedWaypointPacket addWaypointChunk(UUID uuid, Waypoint.Icon icon, ChunkPos chunkPos) {
        return new ClientboundTrackedWaypointPacket(ClientboundTrackedWaypointPacket.Operation.TRACK, TrackedWaypoint.setChunk(uuid, icon, chunkPos));
    }

    public static ClientboundTrackedWaypointPacket updateWaypointChunk(UUID uuid, Waypoint.Icon icon, ChunkPos chunkPos) {
        return new ClientboundTrackedWaypointPacket(ClientboundTrackedWaypointPacket.Operation.UPDATE, TrackedWaypoint.setChunk(uuid, icon, chunkPos));
    }

    public static ClientboundTrackedWaypointPacket addWaypointAzimuth(UUID uuid, Waypoint.Icon icon, float angle) {
        return new ClientboundTrackedWaypointPacket(ClientboundTrackedWaypointPacket.Operation.TRACK, TrackedWaypoint.setAzimuth(uuid, icon, angle));
    }

    public static ClientboundTrackedWaypointPacket updateWaypointAzimuth(UUID uuid, Waypoint.Icon icon, float angle) {
        return new ClientboundTrackedWaypointPacket(ClientboundTrackedWaypointPacket.Operation.UPDATE, TrackedWaypoint.setAzimuth(uuid, icon, angle));
    }

    @Override
    public PacketType<ClientboundTrackedWaypointPacket> type() {
        return GamePacketTypes.CLIENTBOUND_WAYPOINT;
    }

    @Override
    public void handle(ClientGamePacketListener handler) {
        handler.handleWaypoint(this);
    }

    public void apply(TrackedWaypointManager waypointManager) {
        this.operation.action.accept(waypointManager, this.waypoint);
    }

    static enum Operation {
        TRACK(WaypointManager::trackWaypoint),
        UNTRACK(WaypointManager::untrackWaypoint),
        UPDATE(WaypointManager::updateWaypoint);

        final BiConsumer<TrackedWaypointManager, TrackedWaypoint> action;
        public static final IntFunction<ClientboundTrackedWaypointPacket.Operation> BY_ID = ByIdMap.continuous(
            Enum::ordinal, values(), ByIdMap.OutOfBoundsStrategy.WRAP
        );
        public static final StreamCodec<ByteBuf, ClientboundTrackedWaypointPacket.Operation> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, Enum::ordinal);

        private Operation(final BiConsumer<TrackedWaypointManager, TrackedWaypoint> action) {
            this.action = action;
        }
    }
}
