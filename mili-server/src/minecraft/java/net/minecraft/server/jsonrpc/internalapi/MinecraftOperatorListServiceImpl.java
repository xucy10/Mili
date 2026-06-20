package net.minecraft.server.jsonrpc.internalapi;

import java.util.Collection;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.jsonrpc.JsonRpcLogger;
import net.minecraft.server.jsonrpc.methods.ClientInfo;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.ServerOpListEntry;

public class MinecraftOperatorListServiceImpl implements MinecraftOperatorListService {
    private final MinecraftServer minecraftServer;
    private final JsonRpcLogger jsonrpcLogger;

    public MinecraftOperatorListServiceImpl(MinecraftServer minecraftServer, JsonRpcLogger jsonrpcLogger) {
        this.minecraftServer = minecraftServer;
        this.jsonrpcLogger = jsonrpcLogger;
    }

    @Override
    public Collection<ServerOpListEntry> getEntries() {
        return this.minecraftServer.getPlayerList().getOps().getEntries();
    }

    @Override
    public void op(NameAndId nameAndId, Optional<PermissionLevel> level, Optional<Boolean> bypassesPlayerLimit, ClientInfo client) {
        this.jsonrpcLogger.log(client, "Op '{}'", nameAndId);
        this.minecraftServer.getPlayerList().op(nameAndId, level.map(LevelBasedPermissionSet::forLevel), bypassesPlayerLimit);
    }

    @Override
    public void op(NameAndId nameAndId, ClientInfo client) {
        this.jsonrpcLogger.log(client, "Op '{}'", nameAndId);
        this.minecraftServer.getPlayerList().op(nameAndId);
    }

    @Override
    public void deop(NameAndId nameAndId, ClientInfo client) {
        this.jsonrpcLogger.log(client, "Deop '{}'", nameAndId);
        this.minecraftServer.getPlayerList().deop(nameAndId);
    }

    @Override
    public void clear(ClientInfo client) {
        this.jsonrpcLogger.log(client, "Clear operator list");
        this.minecraftServer.getPlayerList().getOps().clear();
    }
}
