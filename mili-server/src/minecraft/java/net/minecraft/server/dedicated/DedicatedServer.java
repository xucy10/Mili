package net.minecraft.server.dedicated;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.net.HostAndPort;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import io.netty.handler.ssl.SslContext;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;
import net.minecraft.DefaultUncaughtExceptionHandler;
import net.minecraft.DefaultUncaughtExceptionHandlerWithName;
import net.minecraft.SharedConstants;
import net.minecraft.SystemReport;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.ConsoleInput;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerInterface;
import net.minecraft.server.ServerLinks;
import net.minecraft.server.Services;
import net.minecraft.server.WorldStem;
import net.minecraft.server.gui.MinecraftServerGui;
import net.minecraft.server.jsonrpc.JsonRpcNotificationService;
import net.minecraft.server.jsonrpc.ManagementServer;
import net.minecraft.server.jsonrpc.internalapi.MinecraftApi;
import net.minecraft.server.jsonrpc.security.AuthenticationHandler;
import net.minecraft.server.jsonrpc.security.JsonRpcSslContextProvider;
import net.minecraft.server.jsonrpc.security.SecurityConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.progress.LoggingLevelLoadListener;
import net.minecraft.server.network.ServerTextFilter;
import net.minecraft.server.network.TextFilter;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.OldUsersConverter;
import net.minecraft.server.rcon.RconConsoleSource;
import net.minecraft.server.rcon.thread.QueryThreadGs4;
import net.minecraft.server.rcon.thread.RconThread;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.Util;
import net.minecraft.util.debug.DebugSubscriptions;
import net.minecraft.util.debugchart.RemoteDebugSampleType;
import net.minecraft.util.debugchart.RemoteSampleLogger;
import net.minecraft.util.debugchart.SampleLogger;
import net.minecraft.util.debugchart.TpsDebugDimensions;
import net.minecraft.util.monitoring.jmx.MinecraftServerStatistics;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class DedicatedServer extends MinecraftServer implements ServerInterface {
    static final Logger LOGGER = LogUtils.getLogger();
    private static final int CONVERSION_RETRY_DELAY_MS = 5000;
    private static final int CONVERSION_RETRIES = 2;
    private final java.util.Queue<ConsoleInput> serverCommandQueue = new java.util.concurrent.ConcurrentLinkedQueue<>(); // Paper - Perf: use a proper queue
    private @Nullable QueryThreadGs4 queryThreadGs4;
    // private final RconConsoleSource rconConsoleSource; // CraftBukkit - remove field
    private @Nullable RconThread rconThread;
    public DedicatedServerSettings settings;
    private @Nullable MinecraftServerGui gui;
    private final @Nullable ServerTextFilter serverTextFilter;
    private @Nullable RemoteSampleLogger tickTimeLogger;
    private boolean isTickTimeLoggingEnabled;
    public ServerLinks serverLinks;
    private final Map<String, String> codeOfConductTexts;
    private @Nullable ManagementServer jsonRpcServer;
    private long lastHeartbeat;

    public DedicatedServer(
        joptsimple.OptionSet options, net.minecraft.server.WorldLoader.DataLoadContext worldLoader, // CraftBukkit - Signature changed
        Thread serverThread,
        LevelStorageSource.LevelStorageAccess storageSource,
        PackRepository packRepository,
        WorldStem worldStem,
        DedicatedServerSettings settings,
        DataFixer fixerUpper,
        Services services
    ) {
        super(options, worldLoader, serverThread, storageSource, packRepository, worldStem, Proxy.NO_PROXY, fixerUpper, services, LoggingLevelLoadListener.forDedicatedServer()); // CraftBukkit - Signature changed
        this.settings = settings;
        this.setMotd(settings.getProperties().motd.get()); // Paper - set field from initial properties
        //this.rconConsoleSource = new RconConsoleSource(this); // CraftBukkit - remove field
        this.serverTextFilter = ServerTextFilter.createFromConfig(settings.getProperties());
        this.serverLinks = createServerLinks(settings);
        if (settings.getProperties().codeOfConduct) {
            this.codeOfConductTexts = readCodeOfConducts();
        } else {
            this.codeOfConductTexts = Map.of();
        }
    }

    private static Map<String, String> readCodeOfConducts() {
        Path path = Path.of("codeofconduct");
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Code of Conduct folder does not exist: " + path);
        } else {
            try {
                Builder<String, String> builder = ImmutableMap.builder();

                try (Stream<Path> stream = Files.list(path)) {
                    for (Path path1 : stream.toList()) {
                        String string = path1.getFileName().toString();
                        if (string.endsWith(".txt")) {
                            String string1 = string.substring(0, string.length() - 4).toLowerCase(Locale.ROOT);
                            if (!path1.toRealPath().getParent().equals(path.toAbsolutePath())) {
                                throw new IllegalArgumentException(
                                    "Failed to read Code of Conduct file \"" + string + "\" because it links to a file outside the allowed directory"
                                );
                            }

                            try {
                                String string2 = String.join("\n", Files.readAllLines(path1, StandardCharsets.UTF_8));
                                builder.put(string1, StringUtil.stripColor(string2));
                            } catch (IOException var9) {
                                throw new IllegalArgumentException("Failed to read Code of Conduct file " + string, var9);
                            }
                        }
                    }
                }

                return builder.build();
            } catch (IOException var11) {
                throw new IllegalArgumentException("Failed to read Code of Conduct folder", var11);
            }
        }
    }

    private SslContext createSslContext() {
        try {
            return JsonRpcSslContextProvider.createFrom(
                this.getProperties().managementServerTlsKeystore, this.getProperties().managementServerTlsKeystorePassword
            );
        } catch (Exception var2) {
            JsonRpcSslContextProvider.printInstructions();
            throw new IllegalStateException("Failed to configure TLS for the server management protocol", var2);
        }
    }

    @Override
    public boolean initServer() throws IOException {
        int i = this.getProperties().managementServerPort;
        if (this.getProperties().managementServerEnabled) {
            String string = this.settings.getProperties().managementServerSecret;
            if (!SecurityConfig.isValid(string)) {
                throw new IllegalStateException("Invalid management server secret, must be 40 alphanumeric characters");
            }

            String string1 = this.getProperties().managementServerHost;
            HostAndPort hostAndPort = HostAndPort.fromParts(string1, i);
            SecurityConfig securityConfig = new SecurityConfig(string);
            String string2 = this.getProperties().managementServerAllowedOrigins;
            AuthenticationHandler authenticationHandler = new AuthenticationHandler(securityConfig, string2);
            LOGGER.info("Starting json RPC server on {}", hostAndPort);
            this.jsonRpcServer = new ManagementServer(hostAndPort, authenticationHandler);
            MinecraftApi minecraftApi = MinecraftApi.of(this);
            minecraftApi.notificationManager().registerService(new JsonRpcNotificationService(minecraftApi, this.jsonRpcServer));
            if (this.getProperties().managementServerTlsEnabled) {
                SslContext sslContext = this.createSslContext();
                this.jsonRpcServer.startWithTls(minecraftApi, sslContext);
            } else {
                this.jsonRpcServer.startWithoutTls(minecraftApi);
            }
        }

        Thread thread = new Thread("Server console handler") {
            @Override
            public void run() {
                if (!org.bukkit.craftbukkit.Main.useConsole) return; // CraftBukkit
                // Paper start - Use TerminalConsoleAppender
                new com.destroystokyo.paper.console.PaperConsole(DedicatedServer.this).start();
                /*
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

                String string4;
                try {
                    while (!DedicatedServer.this.isStopped() && DedicatedServer.this.isRunning() && (string4 = bufferedReader.readLine()) != null) {
                        DedicatedServer.this.handleConsoleInput(string4, DedicatedServer.this.createCommandSourceStack());
                    }
                } catch (IOException var4) {
                    DedicatedServer.LOGGER.error("Exception handling console input", (Throwable)var4);
                }*/
                // Paper end - Use TerminalConsoleAppender
            }
        };
        // CraftBukkit start - TODO: handle command-line logging arguments
        java.util.logging.Logger global = java.util.logging.Logger.getLogger("");
        global.setUseParentHandlers(false);
        for (java.util.logging.Handler handler : global.getHandlers()) {
            global.removeHandler(handler);
        }
        global.addHandler(new org.bukkit.craftbukkit.util.ForwardLogHandler());

        final org.apache.logging.log4j.Logger logger = org.apache.logging.log4j.LogManager.getRootLogger();

        System.setOut(org.apache.logging.log4j.io.IoBuilder.forLogger(logger).setLevel(org.apache.logging.log4j.Level.INFO).buildPrintStream());
        System.setErr(org.apache.logging.log4j.io.IoBuilder.forLogger(logger).setLevel(org.apache.logging.log4j.Level.WARN).buildPrintStream());
        // CraftBukkit end
        thread.setDaemon(true);
        thread.setUncaughtExceptionHandler(new DefaultUncaughtExceptionHandler(LOGGER));
        // thread.start(); // Paper - Enhance console tab completions for brigadier commands; moved down
        LOGGER.info("Starting minecraft server version {}", SharedConstants.getCurrentVersion().name());
        if (Runtime.getRuntime().maxMemory() / 1024L / 1024L < 512L) {
            LOGGER.warn("To start the server with more ram, launch it as \"java -Xmx1024M -Xms1024M -jar minecraft_server.jar\"");
        }

        // Paper start - detect running as root
        if (io.papermc.paper.util.ServerEnvironment.userIsRootOrAdmin()) {
            LOGGER.warn("****************************");
            LOGGER.warn("YOU ARE RUNNING THIS SERVER AS AN ADMINISTRATIVE OR ROOT USER. THIS IS NOT ADVISED.");
            LOGGER.warn("YOU ARE OPENING YOURSELF UP TO POTENTIAL RISKS WHEN DOING THIS.");
            LOGGER.warn("FOR MORE INFORMATION, SEE https://madelinemiller.dev/blog/root-minecraft-server/");
            LOGGER.warn("****************************");
        }
        // Paper end - detect running as root

        LOGGER.info("Loading properties");
        DedicatedServerProperties properties = this.settings.getProperties();
        if (this.isSingleplayer()) {
            this.setLocalIp("127.0.0.1");
        } else {
            this.setUsesAuthentication(properties.onlineMode);
            this.setPreventProxyConnections(properties.preventProxyConnections);
            this.setLocalIp(properties.serverIp);
        }

        // Spigot start
        this.setPlayerList(new DedicatedPlayerList(this, this.registries(), this.playerDataStorage));
        org.spigotmc.SpigotConfig.init((java.io.File) this.options.valueOf("spigot-settings"));
        org.spigotmc.SpigotConfig.registerCommands();
        // Spigot end
        io.papermc.paper.util.ObfHelper.INSTANCE.getClass(); // Paper - load mappings for stacktrace deobf and etc.
        // Paper start - initialize global and world-defaults configuration
        this.paperConfigurations.initializeGlobalConfiguration(this.registryAccess());
        this.paperConfigurations.initializeWorldDefaultsConfiguration(this.registryAccess());
        // Paper end - initialize global and world-defaults configuration
        me.earthme.luminol.config.ConfigManager.loadConfigFiles(); // Luminol - load config file
        if (false) this.server.spark.enableEarlyIfRequested(); // Paper - spark // Luminol - Force disable builtin spark
        // Paper start - fix converting txt to json file; convert old users earlier after PlayerList creation but before file load/save
        if (this.convertOldUsers()) {
            this.services().nameToIdCache().save(false); // Paper
        }
        this.getPlayerList().loadAndSaveFiles(); // Must be after convertNames
        // Paper end - fix converting txt to json file
        org.spigotmc.WatchdogThread.doStart(org.spigotmc.SpigotConfig.timeoutTime, org.spigotmc.SpigotConfig.restartOnCrash); // Paper - start watchdog thread
        thread.start(); // Paper - Enhance console tab completions for brigadier commands; start console thread after MinecraftServer.console & PaperConfig are initialized
        io.papermc.paper.command.PaperCommands.registerCommands(this); // Paper - setup /paper command
        if (false) this.server.spark.registerCommandBeforePlugins(this.server); // Paper - spark // Luminol - Force disable builtin spark
        com.destroystokyo.paper.Metrics.PaperMetrics.startMetrics(); // Paper - start metrics
        com.destroystokyo.paper.VersionHistoryManager.INSTANCE.getClass(); // Paper - load version history now

        // this.worldData.setGameType(properties.gameMode.get()); // CraftBukkit - moved to world loading
        LOGGER.info("Default game type: {}", properties.gameMode.get());
        // Paper start - Unix domain socket support
        java.net.SocketAddress bindAddress;
        if (this.getLocalIp().startsWith("unix:")) {
            if (!io.netty.channel.epoll.Epoll.isAvailable()) {
                LOGGER.error("**** INVALID CONFIGURATION!");
                LOGGER.error("You are trying to use a Unix domain socket but you're not on a supported OS.");
                return false;
            } else if (!io.papermc.paper.configuration.GlobalConfiguration.get().proxies.velocity.enabled && !org.spigotmc.SpigotConfig.bungee) {
                LOGGER.error("**** INVALID CONFIGURATION!");
                LOGGER.error("Unix domain sockets require IPs to be forwarded from a proxy.");
                return false;
            }
            bindAddress = new io.netty.channel.unix.DomainSocketAddress(this.getLocalIp().substring("unix:".length()));
        } else {
        InetAddress inetAddress = null;
        if (!this.getLocalIp().isEmpty()) {
            inetAddress = InetAddress.getByName(this.getLocalIp());
        }

        if (this.getPort() < 0) {
            this.setPort(properties.serverPort);
        }
        bindAddress = new java.net.InetSocketAddress(inetAddress, this.getPort());
        }
        // Paper end - Unix domain socket support

        this.initializeKeyPair();
        LOGGER.info("Starting Minecraft server on {}:{}", this.getLocalIp().isEmpty() ? "*" : this.getLocalIp(), this.getPort());

        try {
            this.getConnection().startTcpServerListener(bindAddress); // Paper - Unix domain socket support
        } catch (IOException var11) {
            LOGGER.warn("**** FAILED TO BIND TO PORT!");
            LOGGER.warn("The exception was: {}", var11.toString());
            LOGGER.warn("Perhaps a server is already running on that port?");
            if (true) throw new IllegalStateException("Failed to bind to port", var11); // Paper - Propagate failed to bind to port error
            return false;
        }

        // CraftBukkit start
        this.server.loadPlugins();
        this.server.enablePlugins(org.bukkit.plugin.PluginLoadOrder.STARTUP);
        // CraftBukkit end

        // Paper start - Add Velocity IP Forwarding Support
        boolean usingProxy = org.spigotmc.SpigotConfig.bungee || io.papermc.paper.configuration.GlobalConfiguration.get().proxies.velocity.enabled;
        String proxyFlavor = (io.papermc.paper.configuration.GlobalConfiguration.get().proxies.velocity.enabled) ? "Velocity" : "BungeeCord";
        String proxyLink = (io.papermc.paper.configuration.GlobalConfiguration.get().proxies.velocity.enabled) ? "https://docs.papermc.io/velocity/security" : "http://www.spigotmc.org/wiki/firewall-guide/";
        // Paper end - Add Velocity IP Forwarding Support
        if (!this.usesAuthentication() && !me.earthme.luminol.config.modules.misc.DisableWarningConfig.disableOfflineModeWarning) { //Luminol - Add config for offline mod warning
            LOGGER.warn("**** SERVER IS RUNNING IN OFFLINE/INSECURE MODE!");
            LOGGER.warn("The server will make no attempt to authenticate usernames. Beware.");
            // Spigot start
            // Paper start - Add Velocity IP Forwarding Support
            if (usingProxy) {
                LOGGER.warn("Whilst this makes it possible to use {}, unless access to your server is properly restricted, it also opens up the ability for hackers to connect with any username they choose.", proxyFlavor);
                LOGGER.warn("Please see {} for further information.", proxyLink);
                // Paper end - Add Velocity IP Forwarding Support
            } else {
                LOGGER.warn("While this makes the game possible to play without internet access, it also opens up the ability for hackers to connect with any username they choose.");
            }
            // Spigot end
            LOGGER.warn("To change this, set \"online-mode\" to \"true\" in the server.properties file.");
        }

        // CraftBukkit start
        /*
        if (this.convertOldUsers()) {
            this.services.nameToIdCache().save();
        }
        */
        // CraftBukkit end

        if (!OldUsersConverter.serverReadyAfterUserconversion(this)) {
            return false;
        } else {
            // this.setPlayerList(new DedicatedPlayerList(this, this.registries(), this.playerDataStorage)); // CraftBukkit - moved up
            this.tickTimeLogger = new RemoteSampleLogger(TpsDebugDimensions.values().length, this.debugSubscribers(), RemoteDebugSampleType.TICK_TIME);
            long nanos = Util.getNanos();
            this.services.nameToIdCache().resolveOfflineUsers(!this.usesAuthentication());
            LOGGER.info("Preparing level \"{}\"", this.getLevelIdName());
            this.loadLevel(this.storageSource.getLevelId()); // CraftBukkit
            long l = Util.getNanos() - nanos;
            String string3 = String.format(Locale.ROOT, "%.3fs", l / 1.0E9);
            LOGGER.info("Done preparing level \"{}\" ({})", this.getLevelIdName(), string3); // Paper - Improve startup message, add total time
            this.initPostWorld(); // Paper - don't include plugins in world preparation time
            if (properties.announcePlayerAchievements != null) {
                this.worldData.getGameRules().set(GameRules.SHOW_ADVANCEMENT_MESSAGES, properties.announcePlayerAchievements, this.overworld()); // Paper - per-world game rules
            }

            if (properties.enableQuery) {
                LOGGER.info("Starting GS4 status listener");
                this.queryThreadGs4 = QueryThreadGs4.create(this);
            }

            if (properties.enableRcon) {
                LOGGER.info("Starting remote control listener");
                this.rconThread = RconThread.create(this);
            }

            if (false && this.getMaxTickLength() > 0L) { // Spigot - disable
                Thread thread1 = new Thread(new ServerWatchdog(this));
                thread1.setUncaughtExceptionHandler(new DefaultUncaughtExceptionHandlerWithName(LOGGER));
                thread1.setName("Server Watchdog");
                thread1.setDaemon(true);
                thread1.start();
            }

            if (properties.enableJmxMonitoring) {
                MinecraftServerStatistics.registerJmxMonitoring(this);
                LOGGER.info("JMX monitoring enabled");
            }

            this.notificationManager().serverStarted();
            return true;
        }
    }

    // Paper start
    public java.io.File getPluginsFolder() {
        return (java.io.File) this.options.valueOf("plugins");
    }
    // Paper end

    @Override
    public boolean isEnforceWhitelist() {
        return this.settings.getProperties().enforceWhitelist.get();
    }

    @Override
    public void setEnforceWhitelist(boolean enforceWhitelist) {
        this.settings.update(dedicatedServerProperties -> dedicatedServerProperties.enforceWhitelist.update(this.registryAccess(), enforceWhitelist));
    }

    @Override
    public boolean isUsingWhitelist() {
        return this.settings.getProperties().whiteList.get();
    }

    @Override
    public void setUsingWhitelist(boolean usingWhitelist) {
        new com.destroystokyo.paper.event.server.WhitelistToggleEvent(usingWhitelist).callEvent(); // Paper - WhitelistToggleEvent
        this.settings.update(dedicatedServerProperties -> dedicatedServerProperties.whiteList.update(this.registryAccess(), usingWhitelist));
    }

    @Override
    // Folia start - region threading
    public void tickServer(long startTime, long scheduledEnd, long targetBuffer,
                           io.papermc.paper.threadedregions.TickRegions.TickRegionData region) {
        super.tickServer(startTime, scheduledEnd, targetBuffer, region);
        // Folia end - region threading
        if (this.jsonRpcServer != null) {
            this.jsonRpcServer.tick();
        }

        long millis = Util.getMillis();
        int i = this.statusHeartbeatInterval();
        if (i > 0) {
            long l = i * TimeUtil.MILLISECONDS_PER_SECOND;
            if (millis - this.lastHeartbeat >= l) {
                this.lastHeartbeat = millis;
                this.notificationManager().statusHeartbeat();
            }
        }
    }

    @Override
    public boolean saveAllChunks(boolean suppressLogs, boolean flush, boolean force) {
        this.notificationManager().serverSaveStarted();
        boolean flag = super.saveAllChunks(suppressLogs, flush, force);
        this.notificationManager().serverSaveCompleted();
        return flag;
    }

    @Override
    public boolean allowFlight() {
        return this.settings.getProperties().allowFlight.get();
    }

    public void setAllowFlight(boolean allowFlight) {
        this.settings.update(dedicatedServerProperties -> dedicatedServerProperties.allowFlight.update(this.registryAccess(), allowFlight));
    }

    @Override
    public DedicatedServerProperties getProperties() {
        return this.settings.getProperties();
    }

    public void setDifficulty(Difficulty difficulty) {
        this.settings.update(dedicatedServerProperties -> dedicatedServerProperties.difficulty.update(this.registryAccess(), difficulty));
        this.forceDifficulty();
    }

    @Override
    public void forceDifficulty() {
        // this.setDifficulty(this.getProperties().difficulty.get(), true); // Paper - per level difficulty; Don't overwrite level.dat's difficulty, keep current
    }

    public int viewDistance() {
        return this.settings.getProperties().viewDistance.get();
    }

    public void setViewDistance(int viewDistance) {
        this.settings.update(dedicatedServerProperties -> dedicatedServerProperties.viewDistance.update(this.registryAccess(), viewDistance));
        this.getPlayerList().setViewDistance(viewDistance);
    }

    public int simulationDistance() {
        return this.settings.getProperties().simulationDistance.get();
    }

    public void setSimulationDistance(int simulationDistance) {
        this.settings.update(dedicatedServerProperties -> dedicatedServerProperties.simulationDistance.update(this.registryAccess(), simulationDistance));
        this.getPlayerList().setSimulationDistance(simulationDistance);
    }

    @Override
    public SystemReport fillServerSystemReport(SystemReport report) {
        report.setDetail("Is Modded", () -> this.getModdedStatus().fullDescription());
        report.setDetail("Type", () -> "Dedicated Server (map_server.txt)");
        return report;
    }

    @Override
    public void dumpServerProperties(Path path) throws IOException {
        DedicatedServerProperties properties = this.getProperties();

        try (Writer bufferedWriter = Files.newBufferedWriter(path)) {
            bufferedWriter.write(String.format(Locale.ROOT, "sync-chunk-writes=%s%n", properties.syncChunkWrites));
            bufferedWriter.write(String.format(Locale.ROOT, "gamemode=%s%n", properties.gameMode.get()));
            bufferedWriter.write(String.format(Locale.ROOT, "entity-broadcast-range-percentage=%d%n", properties.entityBroadcastRangePercentage.get()));
            bufferedWriter.write(String.format(Locale.ROOT, "max-world-size=%d%n", properties.maxWorldSize));
            bufferedWriter.write(String.format(Locale.ROOT, "view-distance=%d%n", properties.viewDistance.get()));
            bufferedWriter.write(String.format(Locale.ROOT, "simulation-distance=%d%n", properties.simulationDistance.get()));
            bufferedWriter.write(String.format(Locale.ROOT, "generate-structures=%s%n", properties.worldOptions.generateStructures()));
            bufferedWriter.write(String.format(Locale.ROOT, "use-native=%s%n", properties.useNativeTransport));
            bufferedWriter.write(String.format(Locale.ROOT, "rate-limit=%d%n", properties.rateLimitPacketsPerSecond));
        }
    }

    @Override
    public void onServerExit() {
        if (this.serverTextFilter != null) {
            this.serverTextFilter.close();
        }

        if (this.gui != null) {
            this.gui.close();
        }

        if (this.rconThread != null) {
            this.rconThread.stopNonBlocking(); // Paper - don't wait for remote connections
        }

        if (this.queryThreadGs4 != null) {
            // this.remoteStatusListener.stop(); // Paper - don't wait for remote connections
        }

        if (this.jsonRpcServer != null) {
            try {
                this.jsonRpcServer.stop(true);
            } catch (InterruptedException var2) {
                LOGGER.error("Interrupted while stopping the management server", (Throwable)var2);
            }
        }

        this.hasFullyShutdown = true; // Paper - Improved watchdog support
        System.exit(this.abnormalExit ? 70 : 0); // CraftBukkit // Paper - Improved watchdog support
    }

    @Override
    public void tickConnection() {
        super.tickConnection();
        // Folia - region threading
    }

    private static final java.util.concurrent.atomic.AtomicInteger ASYNC_DEBUG_CHUNKS_COUNT = new java.util.concurrent.atomic.AtomicInteger(); // Paper - rewrite chunk system

    public void handleConsoleInput(String msg, CommandSourceStack source) {
        // Paper start - rewrite chunk system
        if (msg.equalsIgnoreCase("paper debug chunks --async")) {
            LOGGER.info("Scheduling async debug chunks");
            Runnable run = () -> {
                LOGGER.info("Async debug chunks executing");
                ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler.dumpAllChunkLoadInfo(this, false);
                org.bukkit.command.CommandSender sender = MinecraftServer.getServer().console;
                java.io.File file = ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler.getChunkDebugFile();
                sender.sendMessage(net.kyori.adventure.text.Component.text("Writing chunk information dump to " + file, net.kyori.adventure.text.format.NamedTextColor.GREEN));
                try {
                    ca.spottedleaf.moonrise.common.util.JsonUtil.writeJson(ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler.debugAllWorlds(this), file);
                    sender.sendMessage(net.kyori.adventure.text.Component.text("Successfully written chunk information!", net.kyori.adventure.text.format.NamedTextColor.GREEN));
                } catch (Throwable thr) {
                    MinecraftServer.LOGGER.warn("Failed to dump chunk information to file " + file.toString(), thr);
                    sender.sendMessage(net.kyori.adventure.text.Component.text("Failed to dump chunk information, see console", net.kyori.adventure.text.format.NamedTextColor.RED));
                }
            };
            Thread t = new Thread(run);
            t.setName("Async debug thread #" + ASYNC_DEBUG_CHUNKS_COUNT.getAndIncrement());
            t.setDaemon(true);
            t.start();
            return;
        }
        // Paper end - rewrite chunk system
        this.serverCommandQueue.add(new ConsoleInput(msg, source)); // Paper - Perf: use proper queue
    }

    public void handleConsoleInputs() {
        // Paper start - Perf: use proper queue
        ConsoleInput consoleInput;
        while ((consoleInput = this.serverCommandQueue.poll()) != null) {
            // Paper end - Perf: use proper queue
            // CraftBukkit start - ServerCommand for preprocessing
            org.bukkit.event.server.ServerCommandEvent event = new org.bukkit.event.server.ServerCommandEvent(this.console, consoleInput.msg);
            this.server.getPluginManager().callEvent(event);
            if (event.isCancelled()) continue;
            consoleInput = new ConsoleInput(event.getCommand(), consoleInput.source);
            // CraftBukkit end
            this.getCommands().performPrefixedCommand(consoleInput.source, consoleInput.msg);
        }
    }

    @Override
    public boolean isDedicatedServer() {
        return true;
    }

    @Override
    public int getRateLimitPacketsPerSecond() {
        return this.getProperties().rateLimitPacketsPerSecond;
    }

    @Override
    public boolean useNativeTransport() {
        return this.getProperties().useNativeTransport;
    }

    @Override
    public DedicatedPlayerList getPlayerList() {
        return (DedicatedPlayerList)super.getPlayerList();
    }

    @Override
    public int getMaxPlayers() {
        return this.settings.getProperties().maxPlayers.get();
    }

    public void setMaxPlayers(int maxPlayers) {
        this.settings.update(dedicatedServerProperties -> dedicatedServerProperties.maxPlayers.update(this.registryAccess(), maxPlayers));
    }

    @Override
    public boolean isPublished() {
        return true;
    }

    @Override
    public String getServerIp() {
        return this.getLocalIp();
    }

    @Override
    public int getServerPort() {
        return this.getPort();
    }

    @Override
    public String getServerName() {
        return this.getMotd();
    }

    public void showGui() {
        if (this.gui == null) {
            this.gui = MinecraftServerGui.showFrameFor(this);
        }
    }

    public int spawnProtectionRadius() {
        return this.getProperties().spawnProtection.get();
    }

    public void setSpawnProtectionRadius(int radius) {
        this.settings.update(dedicatedServerProperties -> dedicatedServerProperties.spawnProtection.update(this.registryAccess(), radius));
    }

    @Override
    public boolean isUnderSpawnProtection(ServerLevel level, BlockPos pos, Player player) {
        LevelData.RespawnData respawnData = level.getRespawnData();
        if (level.dimension() != respawnData.dimension()) {
            return false;
        } else if (this.getPlayerList().getOps().isEmpty()) {
            return false;
        } else if (this.getPlayerList().isOp(player.nameAndId())) {
            return false;
        } else if (this.spawnProtectionRadius() <= 0) {
            return false;
        } else {
            BlockPos blockPos = respawnData.pos();
            int abs = Mth.abs(pos.getX() - blockPos.getX());
            int abs1 = Mth.abs(pos.getZ() - blockPos.getZ());
            int max = Math.max(abs, abs1);
            return max <= this.spawnProtectionRadius();
        }
    }

    @Override
    public boolean repliesToStatus() {
        return this.getProperties().enableStatus.get();
    }

    public void setRepliesToStatus(boolean repliesToStatus) {
        this.settings.update(dedicatedServerProperties -> dedicatedServerProperties.enableStatus.update(this.registryAccess(), repliesToStatus));
    }

    @Override
    public boolean hidesOnlinePlayers() {
        return this.getProperties().hideOnlinePlayers.get();
    }

    public void setHidesOnlinePlayers(boolean hidesOnlinePlayers) {
        this.settings.update(dedicatedServerProperties -> dedicatedServerProperties.hideOnlinePlayers.update(this.registryAccess(), hidesOnlinePlayers));
    }

    @Override
    public LevelBasedPermissionSet operatorUserPermissions() {
        return this.getProperties().opPermissions.get();
    }

    public void setOperatorUserPermissions(LevelBasedPermissionSet permissions) {
        this.settings.update(dedicatedServerProperties -> dedicatedServerProperties.opPermissions.update(this.registryAccess(), permissions));
    }

    @Override
    public PermissionSet getFunctionCompilationPermissions() {
        return this.getProperties().functionPermissions;
    }

    @Override
    public int playerIdleTimeout() {
        return this.settings.getProperties().playerIdleTimeout.get();
    }

    @Override
    public void setPlayerIdleTimeout(int idleTimeout) {
        this.settings.update(dedicatedServerProperties -> dedicatedServerProperties.playerIdleTimeout.update(this.registryAccess(), idleTimeout));
    }

    public int statusHeartbeatInterval() {
        return this.settings.getProperties().statusHeartbeatInterval.get();
    }

    public void setStatusHeartbeatInterval(int heartbeatInterval) {
        this.settings.update(dedicatedServerProperties -> dedicatedServerProperties.statusHeartbeatInterval.update(this.registryAccess(), heartbeatInterval));
    }

    @Override
    public String getMotd() {
        return super.getMotd(); // Paper
    }

    @Override
    public void setMotd(String motd) {
        // Paper start
        super.setMotd(motd);
        this.settings.update(dedicatedServerProperties -> dedicatedServerProperties.motd.update(this.registryAccess(), this.getMotd()));
        // Paper end
    }

    @Override
    public boolean shouldRconBroadcast() {
        return this.getProperties().broadcastRconToOps;
    }

    @Override
    public boolean shouldInformAdmins() {
        return this.getProperties().broadcastConsoleToOps;
    }

    @Override
    public int getAbsoluteMaxWorldSize() {
        return this.getProperties().maxWorldSize;
    }

    @Override
    public int getCompressionThreshold() {
        return this.getProperties().networkCompressionThreshold;
    }

    @Override
    public boolean enforceSecureProfile() {
        DedicatedServerProperties properties = this.getProperties();
        // Paper start - Add setting for proxy online mode status
        return properties.enforceSecureProfile
            && io.papermc.paper.configuration.GlobalConfiguration.get().proxies.isProxyOnlineMode()
            && this.services.canValidateProfileKeys();
        // Paper end - Add setting for proxy online mode status
    }

    @Override
    public boolean logIPs() {
        return this.getProperties().logIPs;
    }

    protected boolean convertOldUsers() {
        boolean flag = false;

        for (int i = 0; !flag && i <= 2; i++) {
            if (i > 0) {
                LOGGER.warn("Encountered a problem while converting the user banlist, retrying in a few seconds");
                this.waitForRetry();
            }

            flag = OldUsersConverter.convertUserBanlist(this);
        }

        boolean flag1 = false;

        for (int var7 = 0; !flag1 && var7 <= 2; var7++) {
            if (var7 > 0) {
                LOGGER.warn("Encountered a problem while converting the ip banlist, retrying in a few seconds");
                this.waitForRetry();
            }

            flag1 = OldUsersConverter.convertIpBanlist(this);
        }

        boolean flag2 = false;

        for (int var8 = 0; !flag2 && var8 <= 2; var8++) {
            if (var8 > 0) {
                LOGGER.warn("Encountered a problem while converting the op list, retrying in a few seconds");
                this.waitForRetry();
            }

            flag2 = OldUsersConverter.convertOpsList(this);
        }

        boolean flag3 = false;

        for (int var9 = 0; !flag3 && var9 <= 2; var9++) {
            if (var9 > 0) {
                LOGGER.warn("Encountered a problem while converting the whitelist, retrying in a few seconds");
                this.waitForRetry();
            }

            flag3 = OldUsersConverter.convertWhiteList(this);
        }

        boolean flag4 = false;

        for (int var10 = 0; !flag4 && var10 <= 2; var10++) {
            if (var10 > 0) {
                LOGGER.warn("Encountered a problem while converting the player save files, retrying in a few seconds");
                this.waitForRetry();
            }

            flag4 = OldUsersConverter.convertPlayers(this);
        }

        return flag || flag1 || flag2 || flag3 || flag4;
    }

    private void waitForRetry() {
        try {
            Thread.sleep(5000L);
        } catch (InterruptedException var2) {
        }
    }

    public long getMaxTickLength() {
        return this.getProperties().maxTickTime;
    }

    @Override
    public int getMaxChainedNeighborUpdates() {
        return this.getProperties().maxChainedNeighborUpdates;
    }

    @Override
    public String getPluginNames() {
        // CraftBukkit start - Whole method
        StringBuilder result = new StringBuilder();
        org.bukkit.plugin.Plugin[] plugins = this.server.getPluginManager().getPlugins();

        result.append(this.server.getName());
        result.append(" on Bukkit ");
        result.append(this.server.getBukkitVersion());

        if (plugins.length > 0 && this.server.getQueryPlugins()) {
            result.append(": ");

            for (int i = 0; i < plugins.length; i++) {
                if (i > 0) {
                    result.append("; ");
                }

                result.append(plugins[i].getDescription().getName());
                result.append(" ");
                result.append(plugins[i].getDescription().getVersion().replaceAll(";", ","));
            }
        }

        return result.toString();
        // CraftBukkit end
    }

    @Override
    public String runCommand(String command) {
        // CraftBukkit start - fire RemoteServerCommandEvent
        throw new UnsupportedOperationException("Not supported - remote source required.");
    }

    public String runCommand(RconConsoleSource rconConsoleSource, String s) {
        if (s.isBlank()) return ""; // Paper - Do not process empty rcon commands

        rconConsoleSource.prepareForCommand();
        final java.util.concurrent.atomic.AtomicReference<String> command = new java.util.concurrent.atomic.AtomicReference<>(s); // Folia start - region threading
        Runnable sync = () -> { // Folia - region threading
            CommandSourceStack wrapper = rconConsoleSource.createCommandSourceStack();
            org.bukkit.event.server.RemoteServerCommandEvent event = new org.bukkit.event.server.RemoteServerCommandEvent(rconConsoleSource.getBukkitSender(wrapper), s);
            this.server.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return;
            }
            this.getCommands().performPrefixedCommand(wrapper, event.getCommand());
        }; // Folia start - region threading
        java.util.concurrent.CompletableFuture
            .runAsync(sync, io.papermc.paper.threadedregions.RegionizedServer.getInstance()::addTask)
            .whenComplete((Void r, Throwable t) -> {
                if (t != null) {
                    LOGGER.error("Error handling command for rcon: " + s, t);
                }
            })
            .join();
        // Folia end - region threading
        return rconConsoleSource.getCommandResponse();
        // CraftBukkit end
    }

    @Override
    public void stopServer() {
        me.earthme.luminol.functions.bars.GlobalServerBarManager.cancelAll(); // Luminol - turn off all bars
        this.notificationManager().serverShuttingDown();
        super.stopServer();
        //Util.shutdownExecutors(); // Paper - Improved watchdog support; moved into super
    }

    @Override
    public boolean isSingleplayerOwner(NameAndId nameAndId) {
        return false;
    }

    @Override
    public int getScaledTrackingDistance(int trackingDistance) {
        return this.entityBroadcastRangePercentage() * trackingDistance / 100;
    }

    public int entityBroadcastRangePercentage() {
        return this.getProperties().entityBroadcastRangePercentage.get();
    }

    public void setEntityBroadcastRangePercentage(int entityBroadcastRangePercentage) {
        this.settings
            .update(
                dedicatedServerProperties -> dedicatedServerProperties.entityBroadcastRangePercentage
                    .update(this.registryAccess(), entityBroadcastRangePercentage)
            );
    }

    @Override
    public String getLevelIdName() {
        return this.storageSource.getLevelId();
    }

    @Override
    public boolean forceSynchronousWrites() {
        return this.settings.getProperties().syncChunkWrites;
    }

    @Override
    public TextFilter createTextFilterForPlayer(ServerPlayer player) {
        return this.serverTextFilter != null ? this.serverTextFilter.createContext(player.getGameProfile()) : TextFilter.DUMMY;
    }

    @Override
    public @Nullable GameType getForcedGameType() {
        return this.forceGameMode() ? this.worldData.getGameType() : null;
    }

    public boolean forceGameMode() {
        return this.settings.getProperties().forceGameMode.get();
    }

    public void setForceGameMode(boolean forceGameMode) {
        this.settings.update(dedicatedServerProperties -> dedicatedServerProperties.forceGameMode.update(this.registryAccess(), forceGameMode));
        this.enforceGameTypeForPlayers(this.getForcedGameType());
    }

    public GameType gameMode() {
        return this.getProperties().gameMode.get();
    }

    public void setGameMode(GameType gameMode) {
        this.settings.update(dedicatedServerProperties -> dedicatedServerProperties.gameMode.update(this.registryAccess(), gameMode));
        this.worldData.setGameType(this.gameMode());
        this.enforceGameTypeForPlayers(this.getForcedGameType());
    }

    @Override
    public Optional<MinecraftServer.ServerResourcePackInfo> getServerResourcePack() {
        return this.settings.getProperties().serverResourcePackInfo;
    }

    @Override
    public void endMetricsRecordingTick() {
        super.endMetricsRecordingTick();
        this.isTickTimeLoggingEnabled = this.debugSubscribers().hasAnySubscriberFor(DebugSubscriptions.DEDICATED_SERVER_TICK_TIME);
    }

    @Override
    public SampleLogger getTickTimeLogger() {
        return this.tickTimeLogger;
    }

    @Override
    public boolean isTickTimeLoggingEnabled() {
        return this.isTickTimeLoggingEnabled;
    }

    @Override
    public boolean acceptsTransfers() {
        return this.settings.getProperties().acceptsTransfers.get();
    }

    public void setAcceptsTransfers(boolean acceptsTransfers) {
        this.settings.update(dedicatedServerProperties -> dedicatedServerProperties.acceptsTransfers.update(this.registryAccess(), acceptsTransfers));
    }

    @Override
    public ServerLinks serverLinks() {
        return this.serverLinks;
    }

    @Override
    public int pauseWhenEmptySeconds() {
        return this.settings.getProperties().pauseWhenEmptySeconds.get();
    }

    public void setPauseWhenEmptySeconds(int pauseWhenEmptySeconds) {
        this.settings.update(dedicatedServerProperties -> dedicatedServerProperties.pauseWhenEmptySeconds.update(this.registryAccess(), pauseWhenEmptySeconds));
    }

    private static ServerLinks createServerLinks(DedicatedServerSettings settings) {
        Optional<URI> optional = parseBugReportLink(settings.getProperties());
        return optional.<ServerLinks>map(uri -> new ServerLinks(List.of(ServerLinks.KnownLinkType.BUG_REPORT.create(uri)))).orElse(ServerLinks.EMPTY);
    }

    private static Optional<URI> parseBugReportLink(DedicatedServerProperties properties) {
        String string = properties.bugReportLink;
        if (string.isEmpty()) {
            return Optional.empty();
        } else {
            try {
                return Optional.of(Util.parseAndValidateUntrustedUri(string));
            } catch (Exception var3) {
                LOGGER.warn("Failed to parse bug link {}", string, var3);
                return Optional.empty();
            }
        }
    }

    @Override
    public Map<String, String> getCodeOfConducts() {
        return this.codeOfConductTexts;
    }

    // CraftBukkit start
    public boolean isDebugging() {
        return this.getProperties().debug;
    }

    @Override
    public org.bukkit.command.CommandSender getBukkitSender(CommandSourceStack wrapper) {
        return this.console;
    }
    // CraftBukkit end
}
