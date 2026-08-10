package net.minecraft.server.jsonrpc.internalapi;

import java.util.Collection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.jsonrpc.JsonRpcLogger;
import net.minecraft.server.jsonrpc.methods.ClientInfo;
import net.minecraft.server.players.IpBanListEntry;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.UserBanListEntry;

public class MinecraftBanListServiceImpl implements MinecraftBanListService {
    private final MinecraftServer server;
    private final JsonRpcLogger jsonrpcLogger;

    public MinecraftBanListServiceImpl(MinecraftServer server, JsonRpcLogger jsonrpcLogger) {
        this.server = server;
        this.jsonrpcLogger = jsonrpcLogger;
    }

    @Override
    public void addUserBan(UserBanListEntry entry, ClientInfo client) {
        this.jsonrpcLogger.log(client, "Add player '{}' to banlist. Reason: '{}'", entry.getDisplayName(), entry.getReasonMessage().getString());
        this.server.getPlayerList().getBans().add(entry);
    }

    @Override
    public void removeUserBan(NameAndId nameAndId, ClientInfo client) {
        this.jsonrpcLogger.log(client, "Remove player '{}' from banlist", nameAndId);
        this.server.getPlayerList().getBans().remove(nameAndId);
    }

    @Override
    public void clearUserBans(ClientInfo client) {
        this.server.getPlayerList().getBans().clear();
    }

    @Override
    public Collection<UserBanListEntry> getUserBanEntries() {
        return this.server.getPlayerList().getBans().getEntries();
    }

    @Override
    public Collection<IpBanListEntry> getIpBanEntries() {
        return this.server.getPlayerList().getIpBans().getEntries();
    }

    @Override
    public void addIpBan(IpBanListEntry entry, ClientInfo client) {
        this.jsonrpcLogger.log(client, "Add ip '{}' to ban list", entry.getUser());
        this.server.getPlayerList().getIpBans().add(entry);
    }

    @Override
    public void clearIpBans(ClientInfo client) {
        this.jsonrpcLogger.log(client, "Clear ip ban list");
        this.server.getPlayerList().getIpBans().clear();
    }

    @Override
    public void removeIpBan(String ip, ClientInfo client) {
        this.jsonrpcLogger.log(client, "Remove ip '{}' from ban list", ip);
        this.server.getPlayerList().getIpBans().remove(ip);
    }
}
