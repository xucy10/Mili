package net.minecraft.util.debug;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;

public class ServerDebugSubscribers {
    private final MinecraftServer server;
    private final Map<DebugSubscription<?>, List<ServerPlayer>> enabledSubscriptions = new HashMap<>();

    public ServerDebugSubscribers(MinecraftServer server) {
        this.server = server;
    }

    private List<ServerPlayer> getSubscribersFor(DebugSubscription<?> subscription) {
        return this.enabledSubscriptions.getOrDefault(subscription, List.of());
    }

    public void tick() {
        this.enabledSubscriptions.values().forEach(List::clear);

        for (ServerPlayer serverPlayer : this.server.getPlayerList().getPlayers()) {
            for (DebugSubscription<?> debugSubscription : serverPlayer.debugSubscriptions()) {
                this.enabledSubscriptions.computeIfAbsent(debugSubscription, debugSubscription1 -> new ArrayList<>()).add(serverPlayer);
            }
        }

        this.enabledSubscriptions.values().removeIf(List::isEmpty);
    }

    public void broadcastToAll(DebugSubscription<?> subscription, Packet<?> packet) {
        for (ServerPlayer serverPlayer : this.getSubscribersFor(subscription)) {
            serverPlayer.connection.send(packet);
        }
    }

    public Set<DebugSubscription<?>> enabledSubscriptions() {
        return Set.copyOf(this.enabledSubscriptions.keySet());
    }

    public boolean hasAnySubscriberFor(DebugSubscription<?> subscription) {
        return !this.getSubscribersFor(subscription).isEmpty();
    }

    public boolean hasRequiredPermissions(ServerPlayer player) {
        NameAndId nameAndId = player.nameAndId();
        return SharedConstants.IS_RUNNING_IN_IDE && this.server.isSingleplayerOwner(nameAndId) || this.server.getPlayerList().isOp(nameAndId);
    }
}
