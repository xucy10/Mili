package net.minecraft.server.jsonrpc.internalapi;

import net.minecraft.server.jsonrpc.methods.ClientInfo;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;

public interface MinecraftServerSettingsService {
    boolean isAutoSave();

    boolean setAutoSave(boolean autoSave, ClientInfo client);

    Difficulty getDifficulty();

    Difficulty setDifficulty(Difficulty difficulty, ClientInfo client);

    boolean isEnforceWhitelist();

    boolean setEnforceWhitelist(boolean enforceWhitelist, ClientInfo client);

    boolean isUsingWhitelist();

    boolean setUsingWhitelist(boolean usingWhitelist, ClientInfo client);

    int getMaxPlayers();

    int setMaxPlayers(int maxPlayers, ClientInfo client);

    int getPauseWhenEmptySeconds();

    int setPauseWhenEmptySeconds(int pauseWhenEmptySeconds, ClientInfo client);

    int getPlayerIdleTimeout();

    int setPlayerIdleTimeout(int idleTimeout, ClientInfo client);

    boolean allowFlight();

    boolean setAllowFlight(boolean allowFlight, ClientInfo client);

    int getSpawnProtectionRadius();

    int setSpawnProtectionRadius(int radius, ClientInfo client);

    String getMotd();

    String setMotd(String motd, ClientInfo client);

    boolean forceGameMode();

    boolean setForceGameMode(boolean forceGameMode, ClientInfo client);

    GameType getGameMode();

    GameType setGameMode(GameType gameMode, ClientInfo client);

    int getViewDistance();

    int setViewDistance(int viewDistance, ClientInfo client);

    int getSimulationDistance();

    int setSimulationDistance(int simulationDistance, ClientInfo client);

    boolean acceptsTransfers();

    boolean setAcceptsTransfers(boolean acceptsTransfers, ClientInfo client);

    int getStatusHeartbeatInterval();

    int setStatusHeartbeatInterval(int heartbeatInterval, ClientInfo client);

    LevelBasedPermissionSet getOperatorUserPermissions();

    LevelBasedPermissionSet setOperatorUserPermissions(LevelBasedPermissionSet permissions, ClientInfo client);

    boolean hidesOnlinePlayers();

    boolean setHidesOnlinePlayers(boolean hidesOnlinePlayers, ClientInfo client);

    boolean repliesToStatus();

    boolean setRepliesToStatus(boolean repliesToStatus, ClientInfo client);

    int getEntityBroadcastRangePercentage();

    int setEntityBroadcastRangePercentage(int entityBroadcastRangePercentage, ClientInfo client);
}
