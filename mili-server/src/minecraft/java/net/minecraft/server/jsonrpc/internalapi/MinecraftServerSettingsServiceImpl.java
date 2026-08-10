package net.minecraft.server.jsonrpc.internalapi;

import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.jsonrpc.JsonRpcLogger;
import net.minecraft.server.jsonrpc.methods.ClientInfo;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;

public class MinecraftServerSettingsServiceImpl implements MinecraftServerSettingsService {
    private final DedicatedServer server;
    private final JsonRpcLogger jsonrpcLogger;

    public MinecraftServerSettingsServiceImpl(DedicatedServer server, JsonRpcLogger jsonrpcLogger) {
        this.server = server;
        this.jsonrpcLogger = jsonrpcLogger;
    }

    @Override
    public boolean isAutoSave() {
        return this.server.isAutoSave();
    }

    @Override
    public boolean setAutoSave(boolean autoSave, ClientInfo client) {
        this.jsonrpcLogger.log(client, "Update autosave from {} to {}", this.isAutoSave(), autoSave);
        this.server.setAutoSave(autoSave);
        return this.isAutoSave();
    }

    @Override
    public Difficulty getDifficulty() {
        return this.server.getWorldData().getDifficulty();
    }

    @Override
    public Difficulty setDifficulty(Difficulty difficulty, ClientInfo client) {
        this.jsonrpcLogger.log(client, "Update difficulty from '{}' to '{}'", this.getDifficulty(), difficulty);
        this.server.setDifficulty(difficulty);
        return this.getDifficulty();
    }

    @Override
    public boolean isEnforceWhitelist() {
        return this.server.isEnforceWhitelist();
    }

    @Override
    public boolean setEnforceWhitelist(boolean enforceWhitelist, ClientInfo client) {
        this.jsonrpcLogger.log(client, "Update enforce allowlist from {} to {}", this.isEnforceWhitelist(), enforceWhitelist);
        this.server.setEnforceWhitelist(enforceWhitelist);
        this.server.kickUnlistedPlayers();
        return this.isEnforceWhitelist();
    }

    @Override
    public boolean isUsingWhitelist() {
        return this.server.isUsingWhitelist();
    }

    @Override
    public boolean setUsingWhitelist(boolean usingWhitelist, ClientInfo client) {
        this.jsonrpcLogger.log(client, "Update using allowlist from {} to {}", this.isUsingWhitelist(), usingWhitelist);
        this.server.setUsingWhitelist(usingWhitelist);
        this.server.kickUnlistedPlayers();
        return this.isUsingWhitelist();
    }

    @Override
    public int getMaxPlayers() {
        return this.server.getMaxPlayers();
    }

    @Override
    public int setMaxPlayers(int maxPlayers, ClientInfo client) {
        this.jsonrpcLogger.log(client, "Update max players from {} to {}", this.getMaxPlayers(), maxPlayers);
        this.server.setMaxPlayers(maxPlayers);
        return this.getMaxPlayers();
    }

    @Override
    public int getPauseWhenEmptySeconds() {
        return this.server.pauseWhenEmptySeconds();
    }

    @Override
    public int setPauseWhenEmptySeconds(int pauseWhenEmptySeconds, ClientInfo client) {
        this.jsonrpcLogger.log(client, "Update pause when empty from {} seconds to {} seconds", this.getPauseWhenEmptySeconds(), pauseWhenEmptySeconds);
        this.server.setPauseWhenEmptySeconds(pauseWhenEmptySeconds);
        return this.getPauseWhenEmptySeconds();
    }

    @Override
    public int getPlayerIdleTimeout() {
        return this.server.playerIdleTimeout();
    }

    @Override
    public int setPlayerIdleTimeout(int idleTimeout, ClientInfo client) {
        this.jsonrpcLogger.log(client, "Update player idle timeout from {} minutes to {} minutes", this.getPlayerIdleTimeout(), idleTimeout);
        this.server.setPlayerIdleTimeout(idleTimeout);
        return this.getPlayerIdleTimeout();
    }

    @Override
    public boolean allowFlight() {
        return this.server.allowFlight();
    }

    @Override
    public boolean setAllowFlight(boolean allowFlight, ClientInfo client) {
        this.jsonrpcLogger.log(client, "Update allow flight from {} to {}", this.allowFlight(), allowFlight);
        this.server.setAllowFlight(allowFlight);
        return this.allowFlight();
    }

    @Override
    public int getSpawnProtectionRadius() {
        return this.server.spawnProtectionRadius();
    }

    @Override
    public int setSpawnProtectionRadius(int radius, ClientInfo client) {
        this.jsonrpcLogger.log(client, "Update spawn protection radius from {} to {}", this.getSpawnProtectionRadius(), radius);
        this.server.setSpawnProtectionRadius(radius);
        return this.getSpawnProtectionRadius();
    }

    @Override
    public String getMotd() {
        return this.server.getMotd();
    }

    @Override
    public String setMotd(String motd, ClientInfo client) {
        this.jsonrpcLogger.log(client, "Update MOTD from '{}' to '{}'", this.getMotd(), motd);
        this.server.setMotd(motd);
        return this.getMotd();
    }

    @Override
    public boolean forceGameMode() {
        return this.server.forceGameMode();
    }

    @Override
    public boolean setForceGameMode(boolean forceGameMode, ClientInfo client) {
        this.jsonrpcLogger.log(client, "Update force game mode from {} to {}", this.forceGameMode(), forceGameMode);
        this.server.setForceGameMode(forceGameMode);
        return this.forceGameMode();
    }

    @Override
    public GameType getGameMode() {
        return this.server.gameMode();
    }

    @Override
    public GameType setGameMode(GameType gameMode, ClientInfo client) {
        this.jsonrpcLogger.log(client, "Update game mode from '{}' to '{}'", this.getGameMode(), gameMode);
        this.server.setGameMode(gameMode);
        return this.getGameMode();
    }

    @Override
    public int getViewDistance() {
        return this.server.viewDistance();
    }

    @Override
    public int setViewDistance(int viewDistance, ClientInfo client) {
        this.jsonrpcLogger.log(client, "Update view distance from {} to {}", this.getViewDistance(), viewDistance);
        this.server.setViewDistance(viewDistance);
        return this.getViewDistance();
    }

    @Override
    public int getSimulationDistance() {
        return this.server.simulationDistance();
    }

    @Override
    public int setSimulationDistance(int simulationDistance, ClientInfo client) {
        this.jsonrpcLogger.log(client, "Update simulation distance from {} to {}", this.getSimulationDistance(), simulationDistance);
        this.server.setSimulationDistance(simulationDistance);
        return this.getSimulationDistance();
    }

    @Override
    public boolean acceptsTransfers() {
        return this.server.acceptsTransfers();
    }

    @Override
    public boolean setAcceptsTransfers(boolean acceptsTransfers, ClientInfo client) {
        this.jsonrpcLogger.log(client, "Update accepts transfers from {} to {}", this.acceptsTransfers(), acceptsTransfers);
        this.server.setAcceptsTransfers(acceptsTransfers);
        return this.acceptsTransfers();
    }

    @Override
    public int getStatusHeartbeatInterval() {
        return this.server.statusHeartbeatInterval();
    }

    @Override
    public int setStatusHeartbeatInterval(int heartbeatInterval, ClientInfo client) {
        this.jsonrpcLogger.log(client, "Update status heartbeat interval from {} to {}", this.getStatusHeartbeatInterval(), heartbeatInterval);
        this.server.setStatusHeartbeatInterval(heartbeatInterval);
        return this.getStatusHeartbeatInterval();
    }

    @Override
    public LevelBasedPermissionSet getOperatorUserPermissions() {
        return this.server.operatorUserPermissions();
    }

    @Override
    public LevelBasedPermissionSet setOperatorUserPermissions(LevelBasedPermissionSet permissions, ClientInfo client) {
        this.jsonrpcLogger.log(client, "Update operator user permission level from {} to {}", this.getOperatorUserPermissions(), permissions.level());
        this.server.setOperatorUserPermissions(permissions);
        return this.getOperatorUserPermissions();
    }

    @Override
    public boolean hidesOnlinePlayers() {
        return this.server.hidesOnlinePlayers();
    }

    @Override
    public boolean setHidesOnlinePlayers(boolean hidesOnlinePlayers, ClientInfo client) {
        this.jsonrpcLogger.log(client, "Update hides online players from {} to {}", this.hidesOnlinePlayers(), hidesOnlinePlayers);
        this.server.setHidesOnlinePlayers(hidesOnlinePlayers);
        return this.hidesOnlinePlayers();
    }

    @Override
    public boolean repliesToStatus() {
        return this.server.repliesToStatus();
    }

    @Override
    public boolean setRepliesToStatus(boolean repliesToStatus, ClientInfo client) {
        this.jsonrpcLogger.log(client, "Update replies to status from {} to {}", this.repliesToStatus(), repliesToStatus);
        this.server.setRepliesToStatus(repliesToStatus);
        return this.repliesToStatus();
    }

    @Override
    public int getEntityBroadcastRangePercentage() {
        return this.server.entityBroadcastRangePercentage();
    }

    @Override
    public int setEntityBroadcastRangePercentage(int entityBroadcastRangePercentage, ClientInfo client) {
        this.jsonrpcLogger
            .log(client, "Update entity broadcast range percentage from {}% to {}%", this.getEntityBroadcastRangePercentage(), entityBroadcastRangePercentage);
        this.server.setEntityBroadcastRangePercentage(entityBroadcastRangePercentage);
        return this.getEntityBroadcastRangePercentage();
    }
}
