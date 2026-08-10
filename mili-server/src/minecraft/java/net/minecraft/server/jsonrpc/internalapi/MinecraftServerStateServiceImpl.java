package net.minecraft.server.jsonrpc.internalapi;

import java.util.Collection;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.jsonrpc.JsonRpcLogger;
import net.minecraft.server.jsonrpc.methods.ClientInfo;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public class MinecraftServerStateServiceImpl implements MinecraftServerStateService {
    private final DedicatedServer server;
    private final JsonRpcLogger jsonrpcLogger;

    public MinecraftServerStateServiceImpl(DedicatedServer server, JsonRpcLogger jsonrpcLogger) {
        this.server = server;
        this.jsonrpcLogger = jsonrpcLogger;
    }

    @Override
    public boolean isReady() {
        return this.server.isReady();
    }

    @Override
    public boolean saveEverything(boolean suppressLogs, boolean flush, boolean force, ClientInfo client) {
        this.jsonrpcLogger.log(client, "Save everything. SuppressLogs: {}, flush: {}, force: {}", suppressLogs, flush, force);
        return this.server.saveEverything(suppressLogs, flush, force);
    }

    @Override
    public void halt(boolean waitForShutdown, ClientInfo client) {
        this.jsonrpcLogger.log(client, "Halt server. WaitForShutdown: {}", waitForShutdown);
        this.server.halt(waitForShutdown);
    }

    @Override
    public void sendSystemMessage(Component message, ClientInfo client) {
        this.jsonrpcLogger.log(client, "Send system message: '{}'", message.getString());
        this.server.sendSystemMessage(message);
    }

    @Override
    public void sendSystemMessage(Component message, boolean overlay, Collection<ServerPlayer> players, ClientInfo client) {
        List<String> list = players.stream().map(Player::getPlainTextName).toList();
        this.jsonrpcLogger.log(client, "Send system message to '{}' players (overlay: {}): '{}'", list.size(), overlay, message.getString());

        for (ServerPlayer serverPlayer : players) {
            if (overlay) {
                serverPlayer.sendSystemMessage(message, true);
            } else {
                serverPlayer.sendSystemMessage(message);
            }
        }
    }

    @Override
    public void broadcastSystemMessage(Component message, boolean overlay, ClientInfo client) {
        this.jsonrpcLogger.log(client, "Broadcast system message (overlay: {}): '{}'", overlay, message.getString());

        for (ServerPlayer serverPlayer : this.server.getPlayerList().getPlayers()) {
            if (overlay) {
                serverPlayer.sendSystemMessage(message, true);
            } else {
                serverPlayer.sendSystemMessage(message);
            }
        }
    }
}
