package net.minecraft.server.waypoints;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import com.google.common.collect.Tables;
import com.google.common.collect.Sets.SetView;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.waypoints.WaypointManager;
import net.minecraft.world.waypoints.WaypointTransmitter;

public class ServerWaypointManager implements WaypointManager<WaypointTransmitter> {
    private final Set<WaypointTransmitter> waypoints = new HashSet<>();
    private final Set<ServerPlayer> players = new HashSet<>();
    private final Table<ServerPlayer, WaypointTransmitter, WaypointTransmitter.Connection> connections = HashBasedTable.create();

    @Override
    public void trackWaypoint(WaypointTransmitter waypoint) {
        // Folia - region threading
    }

    @Override
    public void updateWaypoint(WaypointTransmitter waypoint) {
        if (this.waypoints.contains(waypoint)) {
            Map<ServerPlayer, WaypointTransmitter.Connection> map = Tables.transpose(this.connections).row(waypoint);
            SetView<ServerPlayer> set = Sets.difference(this.players, map.keySet());

            for (Entry<ServerPlayer, WaypointTransmitter.Connection> entry : ImmutableSet.copyOf(map.entrySet())) {
                this.updateConnection(entry.getKey(), waypoint, entry.getValue());
            }

            for (ServerPlayer serverPlayer : set) {
                this.createConnection(serverPlayer, waypoint);
            }
        }
    }

    @Override
    public void untrackWaypoint(WaypointTransmitter waypoint) {
        this.connections.column(waypoint).forEach((serverPlayer, connection) -> connection.disconnect());
        Tables.transpose(this.connections).row(waypoint).clear();
        this.waypoints.remove(waypoint);
    }

    public void addPlayer(ServerPlayer player) {
        // Folia - region threading
    }

    public void updatePlayer(ServerPlayer player) {
        // Folia - region threading
    }

    public void removePlayer(ServerPlayer player) {
        // Folia - region threading
    }

    public void breakAllConnections() {
        this.connections.values().forEach(WaypointTransmitter.Connection::disconnect);
        this.connections.clear();
    }

    public void remakeConnections(WaypointTransmitter waypoint) {
        for (ServerPlayer serverPlayer : this.players) {
            this.createConnection(serverPlayer, waypoint);
        }
    }

    public Set<WaypointTransmitter> transmitters() {
        return this.waypoints;
    }

    private static boolean isLocatorBarEnabledFor(ServerPlayer player) {
        return player.level().getGameRules().get(GameRules.LOCATOR_BAR);
    }

    private void createConnection(ServerPlayer player, WaypointTransmitter waypoint) {
        if (player != waypoint) {
            if (isLocatorBarEnabledFor(player)) {
                waypoint.makeWaypointConnectionWith(player).ifPresentOrElse(connection -> {
                    this.connections.put(player, waypoint, connection);
                    connection.connect();
                }, () -> {
                    WaypointTransmitter.Connection connection = this.connections.remove(player, waypoint);
                    if (connection != null) {
                        connection.disconnect();
                    }
                });
            }
        }
    }

    private void updateConnection(ServerPlayer player, WaypointTransmitter waypoint, WaypointTransmitter.Connection connection) {
        if (player != waypoint) {
            if (isLocatorBarEnabledFor(player)) {
                if (!connection.isBroken()) {
                    connection.update();
                } else {
                    waypoint.makeWaypointConnectionWith(player).ifPresentOrElse(connection1 -> {
                        connection1.connect();
                        this.connections.put(player, waypoint, connection1);
                    }, () -> {
                        connection.disconnect();
                        this.connections.remove(player, waypoint);
                    });
                }
            }
        }
    }
}
