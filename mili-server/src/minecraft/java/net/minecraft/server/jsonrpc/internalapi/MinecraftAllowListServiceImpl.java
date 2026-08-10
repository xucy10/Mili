package net.minecraft.server.jsonrpc.internalapi;

import java.util.Collection;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.jsonrpc.JsonRpcLogger;
import net.minecraft.server.jsonrpc.methods.ClientInfo;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.UserWhiteListEntry;

public class MinecraftAllowListServiceImpl implements MinecraftAllowListService {
    private final DedicatedServer server;
    private final JsonRpcLogger jsonrpcLogger;

    public MinecraftAllowListServiceImpl(DedicatedServer server, JsonRpcLogger jsonrpcLogger) {
        this.server = server;
        this.jsonrpcLogger = jsonrpcLogger;
    }

    @Override
    public Collection<UserWhiteListEntry> getEntries() {
        return this.server.getPlayerList().getWhiteList().getEntries();
    }

    @Override
    public boolean add(UserWhiteListEntry entry, ClientInfo client) {
        this.jsonrpcLogger.log(client, "Add player '{}' to allowlist", entry.getUser());
        return this.server.getPlayerList().getWhiteList().add(entry);
    }

    @Override
    public void clear(ClientInfo client) {
        this.jsonrpcLogger.log(client, "Clear allowlist");
        this.server.getPlayerList().getWhiteList().clear();
    }

    @Override
    public void remove(NameAndId nameAndId, ClientInfo client) {
        this.jsonrpcLogger.log(client, "Remove player '{}' from allowlist", nameAndId);
        this.server.getPlayerList().getWhiteList().remove(nameAndId);
    }

    @Override
    public void kickUnlistedPlayers(ClientInfo client) {
        this.jsonrpcLogger.log(client, "Kick unlisted players");
        this.server.kickUnlistedPlayers();
    }
}
