package net.minecraft.server.jsonrpc.internalapi;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.jsonrpc.JsonRpcLogger;
import net.minecraft.server.jsonrpc.methods.ClientInfo;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import org.jspecify.annotations.Nullable;

public class MinecraftPlayerListServiceImpl implements MinecraftPlayerListService {
    private final JsonRpcLogger jsonRpcLogger;
    private final DedicatedServer server;

    public MinecraftPlayerListServiceImpl(DedicatedServer server, JsonRpcLogger jsonRpcLogger) {
        this.jsonRpcLogger = jsonRpcLogger;
        this.server = server;
    }

    @Override
    public List<ServerPlayer> getPlayers() {
        return this.server.getPlayerList().getPlayers();
    }

    @Override
    public @Nullable ServerPlayer getPlayer(UUID id) {
        return this.server.getPlayerList().getPlayer(id);
    }

    @Override
    public Optional<NameAndId> fetchUserByName(String name) {
        return this.server.services().nameToIdCache().get(name);
    }

    @Override
    public Optional<NameAndId> fetchUserById(UUID id) {
        return Optional.ofNullable(this.server.services().sessionService().fetchProfile(id, true)).map(profileResult -> new NameAndId(profileResult.profile()));
    }

    @Override
    public Optional<NameAndId> getCachedUserById(UUID id) {
        return this.server.services().nameToIdCache().get(id);
    }

    @Override
    public Optional<ServerPlayer> getPlayer(Optional<UUID> id, Optional<String> name) {
        if (id.isPresent()) {
            return Optional.ofNullable(this.server.getPlayerList().getPlayer(id.get()));
        } else {
            return name.isPresent() ? Optional.ofNullable(this.server.getPlayerList().getPlayerByName(name.get())) : Optional.empty();
        }
    }

    @Override
    public List<ServerPlayer> getPlayersWithAddress(String address) {
        return this.server.getPlayerList().getPlayersWithAddress(address);
    }

    @Override
    public void remove(ServerPlayer player, ClientInfo client) {
        this.server.getPlayerList().remove(player);
        this.jsonRpcLogger.log(client, "Remove player '{}'", player.getPlainTextName());
    }

    @Override
    public @Nullable ServerPlayer getPlayerByName(String name) {
        return this.server.getPlayerList().getPlayerByName(name);
    }
}
