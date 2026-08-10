package net.minecraft.util.debug;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.SharedConstants;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;

public class ServerDebugSubscribers {
    private final MinecraftServer server;
    private final Map<DebugSubscription<?>, List<ServerPlayer>> enabledSubscriptions = new ConcurrentHashMap<>(); // Mili fix - #472: thread-safe map

    public ServerDebugSubscribers(MinecraftServer server) {
        this.server = server;
    }

    private List<ServerPlayer> getSubscribersFor(DebugSubscription<?> subscription) {
        return this.enabledSubscriptions.getOrDefault(subscription, List.of()); // ConcurrentHashMap getOrDefault is safe
    }

    public void tick() {
        // Mili fix - #472: use thread-safe collections to prevent ConcurrentModificationException
        // Clear all lists atomically
        this.enabledSubscriptions.values().forEach(List::clear);

        for (ServerPlayer serverPlayer : this.server.getPlayerList().getPlayers()) {
            for (DebugSubscription<?> debugSubscription : serverPlayer.debugSubscriptions()) {
                this.enabledSubscriptions.computeIfAbsent(debugSubscription, debugSubscription1 -> new CopyOnWriteArrayList<>()).add(serverPlayer);
            }
        }

        // Remove empty entries safely using ConcurrentHashMap's removeIf
        this.enabledSubscriptions.entrySet().removeIf(entry -> entry.getValue().isEmpty());
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
