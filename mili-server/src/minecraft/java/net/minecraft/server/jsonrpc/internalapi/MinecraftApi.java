package net.minecraft.server.jsonrpc.internalapi;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.jsonrpc.JsonRpcLogger;
import net.minecraft.server.notifications.NotificationManager;

public class MinecraftApi {
    private final NotificationManager notificationManager;
    private final MinecraftAllowListService allowListService;
    private final MinecraftBanListService banListService;
    private final MinecraftPlayerListService minecraftPlayerListService;
    private final MinecraftGameRuleService gameRuleService;
    private final MinecraftOperatorListService minecraftOperatorListService;
    private final MinecraftServerSettingsService minecraftServerSettingsService;
    private final MinecraftServerStateService minecraftServerStateService;
    private final MinecraftExecutorService executorService;

    public MinecraftApi(
        NotificationManager notificationManager,
        MinecraftAllowListService allowListService,
        MinecraftBanListService banListService,
        MinecraftPlayerListService minecraftPlayerListService,
        MinecraftGameRuleService gameRuleService,
        MinecraftOperatorListService minecraftOperatorListService,
        MinecraftServerSettingsService minecraftServerSettingsService,
        MinecraftServerStateService minecraftServerStateService,
        MinecraftExecutorService executorService
    ) {
        this.notificationManager = notificationManager;
        this.allowListService = allowListService;
        this.banListService = banListService;
        this.minecraftPlayerListService = minecraftPlayerListService;
        this.gameRuleService = gameRuleService;
        this.minecraftOperatorListService = minecraftOperatorListService;
        this.minecraftServerSettingsService = minecraftServerSettingsService;
        this.minecraftServerStateService = minecraftServerStateService;
        this.executorService = executorService;
    }

    public <V> CompletableFuture<V> submit(Supplier<V> supplier) {
        return this.executorService.submit(supplier);
    }

    public CompletableFuture<Void> submit(Runnable task) {
        return this.executorService.submit(task);
    }

    public MinecraftAllowListService allowListService() {
        return this.allowListService;
    }

    public MinecraftBanListService banListService() {
        return this.banListService;
    }

    public MinecraftPlayerListService playerListService() {
        return this.minecraftPlayerListService;
    }

    public MinecraftGameRuleService gameRuleService() {
        return this.gameRuleService;
    }

    public MinecraftOperatorListService operatorListService() {
        return this.minecraftOperatorListService;
    }

    public MinecraftServerSettingsService serverSettingsService() {
        return this.minecraftServerSettingsService;
    }

    public MinecraftServerStateService serverStateService() {
        return this.minecraftServerStateService;
    }

    public NotificationManager notificationManager() {
        return this.notificationManager;
    }

    public static MinecraftApi of(DedicatedServer server) {
        JsonRpcLogger jsonRpcLogger = new JsonRpcLogger();
        MinecraftAllowListServiceImpl minecraftAllowListServiceImpl = new MinecraftAllowListServiceImpl(server, jsonRpcLogger);
        MinecraftBanListServiceImpl minecraftBanListServiceImpl = new MinecraftBanListServiceImpl(server, jsonRpcLogger);
        MinecraftPlayerListServiceImpl minecraftPlayerListServiceImpl = new MinecraftPlayerListServiceImpl(server, jsonRpcLogger);
        MinecraftGameRuleServiceImpl minecraftGameRuleServiceImpl = new MinecraftGameRuleServiceImpl(server, jsonRpcLogger);
        MinecraftOperatorListServiceImpl minecraftOperatorListServiceImpl = new MinecraftOperatorListServiceImpl(server, jsonRpcLogger);
        MinecraftServerSettingsServiceImpl minecraftServerSettingsServiceImpl = new MinecraftServerSettingsServiceImpl(server, jsonRpcLogger);
        MinecraftServerStateServiceImpl minecraftServerStateServiceImpl = new MinecraftServerStateServiceImpl(server, jsonRpcLogger);
        MinecraftExecutorService minecraftExecutorService = new MinecraftExecutorServiceImpl(server);
        return new MinecraftApi(
            server.notificationManager(),
            minecraftAllowListServiceImpl,
            minecraftBanListServiceImpl,
            minecraftPlayerListServiceImpl,
            minecraftGameRuleServiceImpl,
            minecraftOperatorListServiceImpl,
            minecraftServerSettingsServiceImpl,
            minecraftServerStateServiceImpl,
            minecraftExecutorService
        );
    }
}
