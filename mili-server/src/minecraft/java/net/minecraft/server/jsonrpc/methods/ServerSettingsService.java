package net.minecraft.server.jsonrpc.methods;

import net.minecraft.server.jsonrpc.internalapi.MinecraftApi;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;

public class ServerSettingsService {
    public static boolean autosave(MinecraftApi api) {
        return api.serverSettingsService().isAutoSave();
    }

    public static boolean setAutosave(MinecraftApi api, boolean autoSave, ClientInfo client) {
        return api.serverSettingsService().setAutoSave(autoSave, client);
    }

    public static Difficulty difficulty(MinecraftApi api) {
        return api.serverSettingsService().getDifficulty();
    }

    public static Difficulty setDifficulty(MinecraftApi api, Difficulty difficulty, ClientInfo client) {
        return api.serverSettingsService().setDifficulty(difficulty, client);
    }

    public static boolean enforceAllowlist(MinecraftApi api) {
        return api.serverSettingsService().isEnforceWhitelist();
    }

    public static boolean setEnforceAllowlist(MinecraftApi api, boolean enforceAllowlist, ClientInfo client) {
        return api.serverSettingsService().setEnforceWhitelist(enforceAllowlist, client);
    }

    public static boolean usingAllowlist(MinecraftApi api) {
        return api.serverSettingsService().isUsingWhitelist();
    }

    public static boolean setUsingAllowlist(MinecraftApi api, boolean usingAllowlist, ClientInfo client) {
        return api.serverSettingsService().setUsingWhitelist(usingAllowlist, client);
    }

    public static int maxPlayers(MinecraftApi api) {
        return api.serverSettingsService().getMaxPlayers();
    }

    public static int setMaxPlayers(MinecraftApi api, int maxPlayers, ClientInfo client) {
        return api.serverSettingsService().setMaxPlayers(maxPlayers, client);
    }

    public static int pauseWhenEmpty(MinecraftApi api) {
        return api.serverSettingsService().getPauseWhenEmptySeconds();
    }

    public static int setPauseWhenEmpty(MinecraftApi api, int pauseWhenEmptySeconds, ClientInfo client) {
        return api.serverSettingsService().setPauseWhenEmptySeconds(pauseWhenEmptySeconds, client);
    }

    public static int playerIdleTimeout(MinecraftApi api) {
        return api.serverSettingsService().getPlayerIdleTimeout();
    }

    public static int setPlayerIdleTimeout(MinecraftApi api, int idleTimeout, ClientInfo client) {
        return api.serverSettingsService().setPlayerIdleTimeout(idleTimeout, client);
    }

    public static boolean allowFlight(MinecraftApi api) {
        return api.serverSettingsService().allowFlight();
    }

    public static boolean setAllowFlight(MinecraftApi api, boolean allowFlight, ClientInfo client) {
        return api.serverSettingsService().setAllowFlight(allowFlight, client);
    }

    public static int spawnProtection(MinecraftApi api) {
        return api.serverSettingsService().getSpawnProtectionRadius();
    }

    public static int setSpawnProtection(MinecraftApi api, int radius, ClientInfo client) {
        return api.serverSettingsService().setSpawnProtectionRadius(radius, client);
    }

    public static String motd(MinecraftApi api) {
        return api.serverSettingsService().getMotd();
    }

    public static String setMotd(MinecraftApi api, String motd, ClientInfo client) {
        return api.serverSettingsService().setMotd(motd, client);
    }

    public static boolean forceGameMode(MinecraftApi api) {
        return api.serverSettingsService().forceGameMode();
    }

    public static boolean setForceGameMode(MinecraftApi api, boolean forceGameMode, ClientInfo client) {
        return api.serverSettingsService().setForceGameMode(forceGameMode, client);
    }

    public static GameType gameMode(MinecraftApi api) {
        return api.serverSettingsService().getGameMode();
    }

    public static GameType setGameMode(MinecraftApi api, GameType gameMode, ClientInfo client) {
        return api.serverSettingsService().setGameMode(gameMode, client);
    }

    public static int viewDistance(MinecraftApi api) {
        return api.serverSettingsService().getViewDistance();
    }

    public static int setViewDistance(MinecraftApi api, int viewDistance, ClientInfo client) {
        return api.serverSettingsService().setViewDistance(viewDistance, client);
    }

    public static int simulationDistance(MinecraftApi api) {
        return api.serverSettingsService().getSimulationDistance();
    }

    public static int setSimulationDistance(MinecraftApi api, int simulationDistance, ClientInfo client) {
        return api.serverSettingsService().setSimulationDistance(simulationDistance, client);
    }

    public static boolean acceptTransfers(MinecraftApi api) {
        return api.serverSettingsService().acceptsTransfers();
    }

    public static boolean setAcceptTransfers(MinecraftApi api, boolean acceptsTransfers, ClientInfo client) {
        return api.serverSettingsService().setAcceptsTransfers(acceptsTransfers, client);
    }

    public static int statusHeartbeatInterval(MinecraftApi api) {
        return api.serverSettingsService().getStatusHeartbeatInterval();
    }

    public static int setStatusHeartbeatInterval(MinecraftApi api, int heartbeatInterval, ClientInfo client) {
        return api.serverSettingsService().setStatusHeartbeatInterval(heartbeatInterval, client);
    }

    public static PermissionLevel operatorUserPermissionLevel(MinecraftApi api) {
        return api.serverSettingsService().getOperatorUserPermissions().level();
    }

    public static PermissionLevel setOperatorUserPermissionLevel(MinecraftApi api, PermissionLevel permissionLevel, ClientInfo client) {
        return api.serverSettingsService().setOperatorUserPermissions(LevelBasedPermissionSet.forLevel(permissionLevel), client).level();
    }

    public static boolean hidesOnlinePlayers(MinecraftApi api) {
        return api.serverSettingsService().hidesOnlinePlayers();
    }

    public static boolean setHidesOnlinePlayers(MinecraftApi api, boolean hidesOnlinePlayers, ClientInfo client) {
        return api.serverSettingsService().setHidesOnlinePlayers(hidesOnlinePlayers, client);
    }

    public static boolean repliesToStatus(MinecraftApi api) {
        return api.serverSettingsService().repliesToStatus();
    }

    public static boolean setRepliesToStatus(MinecraftApi api, boolean repliesToStatus, ClientInfo client) {
        return api.serverSettingsService().setRepliesToStatus(repliesToStatus, client);
    }

    public static int entityBroadcastRangePercentage(MinecraftApi api) {
        return api.serverSettingsService().getEntityBroadcastRangePercentage();
    }

    public static int setEntityBroadcastRangePercentage(MinecraftApi api, int entityBroadcastRangePercentage, ClientInfo client) {
        return api.serverSettingsService().setEntityBroadcastRangePercentage(entityBroadcastRangePercentage, client);
    }
}
