package net.minecraft.server;

import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.Lifecycle;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import joptsimple.util.PathConverter;
import net.minecraft.CrashReport;
import net.minecraft.DefaultUncaughtExceptionHandler;
import net.minecraft.SharedConstants;
import net.minecraft.SuppressForbidden;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtException;
import net.minecraft.nbt.ReportedNbtException;
import net.minecraft.network.chat.Component;
import net.minecraft.obfuscate.DontObfuscate;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.dedicated.DedicatedServerProperties;
import net.minecraft.server.dedicated.DedicatedServerSettings;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.profiling.jfr.Environment;
import net.minecraft.util.profiling.jfr.JvmProfiler;
import net.minecraft.util.worldupdate.WorldUpgrader;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.chunk.storage.RegionFileVersion;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelDataAndDimensions;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelSummary;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.level.storage.WorldData;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class Main {
    private static final Logger LOGGER = LogUtils.getLogger();

    @SuppressForbidden(reason = "System.out needed before bootstrap")
    @DontObfuscate
    public static void main(final OptionSet optionSet) { // CraftBukkit - replaces main(String[] args)
        io.papermc.paper.log.LogManagerShutdownThread.hook(); // Paper - Improved watchdog support
        SharedConstants.tryDetectVersion();
        /* CraftBukkit start - Replace everything
        OptionParser optionParser = new OptionParser();
        OptionSpec<Void> optionSpec = optionParser.accepts("nogui");
        OptionSpec<Void> optionSpec1 = optionParser.accepts("initSettings", "Initializes 'server.properties' and 'eula.txt', then quits");
        OptionSpec<Void> optionSpec2 = optionParser.accepts("demo");
        OptionSpec<Void> optionSpec3 = optionParser.accepts("bonusChest");
        OptionSpec<Void> optionSpec4 = optionParser.accepts("forceUpgrade");
        OptionSpec<Void> optionSpec5 = optionParser.accepts("eraseCache");
        OptionSpec<Void> optionSpec6 = optionParser.accepts("recreateRegionFiles");
        OptionSpec<Void> optionSpec7 = optionParser.accepts("safeMode", "Loads level with vanilla datapack only");
        OptionSpec<Void> optionSpec8 = optionParser.accepts("help").forHelp();
        OptionSpec<String> optionSpec9 = optionParser.accepts("universe").withRequiredArg().defaultsTo(".");
        OptionSpec<String> optionSpec10 = optionParser.accepts("world").withRequiredArg();
        OptionSpec<Integer> optionSpec11 = optionParser.accepts("port").withRequiredArg().ofType(Integer.class).defaultsTo(-1);
        OptionSpec<String> optionSpec12 = optionParser.accepts("serverId").withRequiredArg();
        OptionSpec<Void> optionSpec13 = optionParser.accepts("jfrProfile");
        OptionSpec<Path> optionSpec14 = optionParser.accepts("pidFile").withRequiredArg().withValuesConvertedBy(new PathConverter());
        OptionSpec<String> optionSpec15 = optionParser.nonOptions();

        try {
            OptionSet optionSet = optionParser.parse(args);
            if (optionSet.has(optionSpec8)) {
                optionParser.printHelpOn(System.err);
                return;
            }
            */ // CraftBukkit end
        try {

            Path path = (Path) optionSet.valueOf("pidFile"); // CraftBukkit
            if (path != null) {
                writePidFile(path);
            }

            CrashReport.preload();
            if (optionSet.has("jfrProfile")) { // CraftBukkit
                JvmProfiler.INSTANCE.start(Environment.SERVER);
            }

            me.earthme.luminol.config.ConfigManager.initConfigs(); // Luminol - Luminol config
            io.papermc.paper.plugin.PluginInitializerManager.load(optionSet); // Paper
            Bootstrap.bootStrap();
            Bootstrap.validate();
            Util.startTimerHackThread();
            Path path1 = Paths.get("server.properties");
            DedicatedServerSettings dedicatedServerSettings = new DedicatedServerSettings(optionSet); // CraftBukkit - CLI argument support
            dedicatedServerSettings.forceSave();
            RegionFileVersion.configure(dedicatedServerSettings.getProperties().regionFileComression);
            Path path2 = Paths.get("eula.txt");
            Eula eula = new Eula(path2);
            // Paper start - load config files early for access below if needed
            org.bukkit.configuration.file.YamlConfiguration bukkitConfiguration = io.papermc.paper.configuration.PaperConfigurations.loadLegacyConfigFile((File) optionSet.valueOf("bukkit-settings"));
            org.bukkit.configuration.file.YamlConfiguration spigotConfiguration = io.papermc.paper.configuration.PaperConfigurations.loadLegacyConfigFile((File) optionSet.valueOf("spigot-settings"));
            // Paper end - load config files early for access below if needed
            if (optionSet.has("initSettings")) { // CraftBukkit
                // CraftBukkit start - SPIGOT-5761: Create bukkit.yml and commands.yml if not present
                File configFile = (File) optionSet.valueOf("bukkit-settings");
                org.bukkit.configuration.file.YamlConfiguration configuration = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(configFile);
                configuration.options().copyDefaults(true);
                configuration.setDefaults(org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(Main.class.getClassLoader().getResourceAsStream("configurations/bukkit.yml"), java.nio.charset.StandardCharsets.UTF_8)));
                configuration.save(configFile);

                File commandFile = (File) optionSet.valueOf("commands-settings");
                org.bukkit.configuration.file.YamlConfiguration commandsConfiguration = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(commandFile);
                commandsConfiguration.options().copyDefaults(true);
                commandsConfiguration.setDefaults(org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(Main.class.getClassLoader().getResourceAsStream("configurations/commands.yml"), java.nio.charset.StandardCharsets.UTF_8)));
                commandsConfiguration.save(commandFile);
                // CraftBukkit end
                LOGGER.info("Initialized '{}' and '{}'", path1.toAbsolutePath(), path2.toAbsolutePath());
                return;
            }

            // Spigot start
            boolean eulaAgreed = Boolean.getBoolean("com.mojang.eula.agree");
            if (eulaAgreed) {
                LOGGER.error("You have used the Spigot command line EULA agreement flag.");
                LOGGER.error("By using this setting you are indicating your agreement to Mojang's EULA (https://aka.ms/MinecraftEULA).");
                LOGGER.error("If you do not agree to the above EULA please stop your server and remove this flag immediately.");
            }
            if (!eula.hasAgreedToEULA() && !eulaAgreed) {
                // Spigot end
                LOGGER.info("You need to agree to the EULA in order to run the server. Go to eula.txt for more info.");
                return;
            }

            // Paper start - Detect headless JRE
            String awtException = io.papermc.paper.util.ServerEnvironment.awtDependencyCheck();
            if (awtException != null) {
                LOGGER.error("You are using a headless JRE distribution.");
                LOGGER.error("This distribution is missing certain graphic libraries that the Minecraft server needs to function.");
                LOGGER.error("For instructions on how to install the non-headless JRE, see https://docs.papermc.io/misc/java-install");
                LOGGER.error("");
                LOGGER.error(awtException);
                return;
            }
            // Paper end - Detect headless JRE

            org.spigotmc.SpigotConfig.disabledAdvancements = spigotConfiguration.getStringList("advancements.disabled"); // Paper - fix SPIGOT-5885, must be set early in init

            // Paper start - fix SPIGOT-5824
            File file;
            File userCacheFile = new File(Services.USERID_CACHE_FILE);
            if (optionSet.has("universe")) {
                file = (File) optionSet.valueOf("universe"); // CraftBukkit
                userCacheFile = new File(file, Services.USERID_CACHE_FILE);
            } else {
                file = new File(bukkitConfiguration.getString("settings.world-container", "."));
            }
            // Paper end - fix SPIGOT-5824
            Services services = Services.create(new com.destroystokyo.paper.profile.PaperAuthenticationService(Proxy.NO_PROXY), file, userCacheFile, optionSet); // Paper - pass OptionSet to load paper config files; override authentication service; fix world-container
            // CraftBukkit start
            String string = Optional.ofNullable((String) optionSet.valueOf("world")).orElse(dedicatedServerSettings.getProperties().levelName);
            LevelStorageSource levelStorageSource = LevelStorageSource.createDefault(file.toPath());
            LevelStorageSource.LevelStorageAccess levelStorageAccess = levelStorageSource.validateAndCreateAccess(string, LevelStem.OVERWORLD);
            // CraftBukkit end
            Dynamic<?> dataTag;
            if (levelStorageAccess.hasWorldData()) {
                LevelSummary summary;
                try {
                    dataTag = levelStorageAccess.getDataTag();
                    summary = levelStorageAccess.getSummary(dataTag);
                } catch (NbtException | ReportedNbtException | IOException var41) {
                    LevelStorageSource.LevelDirectory levelDirectory = levelStorageAccess.getLevelDirectory();
                    LOGGER.warn("Failed to load world data from {}", levelDirectory.dataFile(), var41);
                    LOGGER.info("Attempting to use fallback");

                    try {
                        dataTag = levelStorageAccess.getDataTagFallback();
                        summary = levelStorageAccess.getSummary(dataTag);
                    } catch (NbtException | ReportedNbtException | IOException var40) {
                        LOGGER.error("Failed to load world data from {}", levelDirectory.oldDataFile(), var40);
                        LOGGER.error(
                            "Failed to load world data from {} and {}. World files may be corrupted. Shutting down.",
                            levelDirectory.dataFile(),
                            levelDirectory.oldDataFile()
                        );
                        return;
                    }

                    levelStorageAccess.restoreLevelDataFromOld();
                }

                if (summary.requiresManualConversion()) {
                    LOGGER.info("This world must be opened in an older version (like 1.6.4) to be safely converted");
                    return;
                }

                if (!summary.isCompatible()) {
                    LOGGER.info("This world was created by an incompatible version.");
                    return;
                }
            } else {
                dataTag = null;
            }

            Dynamic<?> dynamic = dataTag;
            boolean hasOptionSpec = optionSet.has("safeMode"); // CraftBukkit
            if (hasOptionSpec) {
                LOGGER.warn("Safe mode active, only vanilla datapack will be loaded");
            }

            PackRepository packRepository = ServerPacksSource.createPackRepository(levelStorageAccess);
            // CraftBukkit start
            File bukkitDataPackFolder = new File(levelStorageAccess.getLevelPath(net.minecraft.world.level.storage.LevelResource.DATAPACK_DIR).toFile(), "bukkit");
            if (!bukkitDataPackFolder.exists()) {
                bukkitDataPackFolder.mkdirs();
            }
            File mcMeta = new File(bukkitDataPackFolder, "pack.mcmeta");
            try {
                final var major = SharedConstants.getCurrentVersion().packVersion(net.minecraft.server.packs.PackType.SERVER_DATA).major();
                final var minor = SharedConstants.getCurrentVersion().packVersion(net.minecraft.server.packs.PackType.SERVER_DATA).minor();
                com.google.common.io.Files.asCharSink(mcMeta, java.nio.charset.StandardCharsets.UTF_8).write("""
                    {
                        "pack": {
                            "description": "Data pack for resources provided by Bukkit plugins",
                            "min_format": [%d, %d],
                            "max_format": [%d, %d]
                        }
                    }
                    """.formatted(major, minor, major, minor)
                );
            } catch (java.io.IOException ex) {
                throw new RuntimeException("Could not initialize Bukkit datapack", ex);
            }
            java.util.concurrent.atomic.AtomicReference<WorldLoader.DataLoadContext> worldLoader = new java.util.concurrent.atomic.AtomicReference<>();
            // CraftBukkit end

            WorldStem worldStem;
            try {
                WorldLoader.InitConfig initConfig = loadOrCreateConfig(dedicatedServerSettings.getProperties(), dynamic, hasOptionSpec, packRepository);
                worldStem = Util.<WorldStem>blockUntilDone(
                        executor -> WorldLoader.load(
                            initConfig,
                            context -> {
                                worldLoader.set(context); // CraftBukkit
                                Registry<LevelStem> registry = context.datapackDimensions().lookupOrThrow(Registries.LEVEL_STEM);
                                if (dynamic != null) {
                                    LevelDataAndDimensions levelDataAndDimensions = LevelStorageSource.getLevelDataAndDimensions(
                                        dynamic, context.dataConfiguration(), registry, context.datapackWorldgen()
                                    );
                                    return new WorldLoader.DataLoadOutput<>(
                                        levelDataAndDimensions.worldData(), levelDataAndDimensions.dimensions().dimensionsRegistryAccess()
                                    );
                                } else {
                                    LOGGER.info("No existing world data, creating new world");
                                    return createNewWorldData(
                                        dedicatedServerSettings, context, registry, optionSet.has("demo"), optionSet.has("bonusChest") // CraftBukkit
                                    );
                                }
                            },
                            WorldStem::new,
                            Util.backgroundExecutor(),
                            executor
                        )
                    )
                    .get();
            } catch (Exception var39) {
                LOGGER.warn(
                    "Failed to load datapacks, can't proceed with server load. You can either fix your datapacks or reset to vanilla with --safeMode",
                    (Throwable)var39
                );
                return;
            }

            /*
            RegistryAccess.Frozen frozen = worldStem.registries().compositeAccess();
            WorldData worldData = worldStem.worldData();
            boolean hasOptionSpec1 = optionSet.has(optionSpec6);
            if (optionSet.has(optionSpec4) || hasOptionSpec1) {
                forceUpgrade(levelStorageAccess, worldData, DataFixers.getDataFixer(), optionSet.has(optionSpec5), () -> true, frozen, hasOptionSpec1);
            }

            levelStorageAccess.saveDataTag(frozen, worldData);
            */
            Class.forName(net.minecraft.world.entity.npc.villager.VillagerTrades.class.getName()); // Paper - load this sync so it won't fail later async
            final DedicatedServer dedicatedServer = MinecraftServer.spin(
                thread1 -> {
                    DedicatedServer dedicatedServer1 = new DedicatedServer(
                        // CraftBukkit start
                        optionSet,
                        worldLoader.get(),
                        thread1,
                        levelStorageAccess,
                        packRepository,
                        worldStem,
                        dedicatedServerSettings,
                        DataFixers.getDataFixer(),
                        services
                    );
                    /*
                    dedicatedServer1.setPort(optionSet.valueOf(optionSpec11));
                     */
                    // Paper start
                    if (optionSet.has("serverId")) {
                        dedicatedServer1.setId((String) optionSet.valueOf("serverId"));
                    }
                    dedicatedServer1.setDemo(optionSet.has("demo"));
                    // Paper end
                    /*
                    dedicatedServer1.setId(optionSet.valueOf(optionSpec12));
                     */
                    boolean flag = !optionSet.has("nogui") && !optionSet.nonOptionArguments().contains("nogui");
                    if (flag && !GraphicsEnvironment.isHeadless()) {
                        dedicatedServer1.showGui();
                    }
                    // Paper start
                    if (optionSet.has("port")) {
                        int port = (Integer) optionSet.valueOf("port");
                        if (port > 0) {
                            dedicatedServer1.setPort(port);
                        }
                    }
                    // Paper end

                    return dedicatedServer1;
                }
            );
            /* CraftBukkit start
            Thread thread = new Thread("Server Shutdown Thread") {
                @Override
                public void run() {
                    dedicatedServer.halt(true);
                }
            };
            thread.setUncaughtExceptionHandler(new DefaultUncaughtExceptionHandler(LOGGER));
            Runtime.getRuntime().addShutdownHook(thread);
            */ // CraftBukkit end
        } catch (Throwable var42) {
            LOGGER.error(LogUtils.FATAL_MARKER, "Failed to start the minecraft server", var42);
        }
    }

    public static WorldLoader.DataLoadOutput<WorldData> createNewWorldData( // Paper - public
        DedicatedServerSettings settings, WorldLoader.DataLoadContext context, Registry<LevelStem> stemRegistry, boolean demo, boolean generateBonusChest
    ) {
        LevelSettings levelSettings;
        WorldOptions worldOptions;
        WorldDimensions worldDimensions;
        if (demo) {
            levelSettings = MinecraftServer.DEMO_SETTINGS;
            worldOptions = WorldOptions.DEMO_OPTIONS;
            worldDimensions = WorldPresets.createNormalWorldDimensions(context.datapackWorldgen());
        } else {
            DedicatedServerProperties properties = settings.getProperties();
            levelSettings = new LevelSettings(
                properties.levelName,
                properties.gameMode.get(),
                properties.hardcore,
                properties.difficulty.get(),
                false,
                new GameRules(context.dataConfiguration().enabledFeatures()),
                context.dataConfiguration()
            );
            worldOptions = generateBonusChest ? properties.worldOptions.withBonusChest(true) : properties.worldOptions;
            worldDimensions = properties.createDimensions(context.datapackWorldgen());
        }

        WorldDimensions.Complete complete = worldDimensions.bake(stemRegistry);
        Lifecycle lifecycle = complete.lifecycle().add(context.datapackWorldgen().allRegistriesLifecycle());
        return new WorldLoader.DataLoadOutput<>(
            new PrimaryLevelData(levelSettings, worldOptions, complete.specialWorldProperty(), lifecycle), complete.dimensionsRegistryAccess()
        );
    }

    private static void writePidFile(Path path) {
        try {
            long l = ProcessHandle.current().pid();
            Files.writeString(path, Long.toString(l));
        } catch (IOException var3) {
            throw new UncheckedIOException(var3);
        }
    }

    private static WorldLoader.InitConfig loadOrCreateConfig(
        DedicatedServerProperties dedicatedServerProperties, @Nullable Dynamic<?> dynamic, boolean safeMode, PackRepository packRepository
    ) {
        boolean flag;
        WorldDataConfiguration worldDataConfiguration;
        if (dynamic != null) {
            WorldDataConfiguration dataConfig = LevelStorageSource.readDataConfig(dynamic);
            flag = false;
            worldDataConfiguration = dataConfig;
        } else {
            flag = true;
            worldDataConfiguration = new WorldDataConfiguration(dedicatedServerProperties.initialDataPackConfiguration, FeatureFlags.DEFAULT_FLAGS);
        }

        WorldLoader.PackConfig packConfig = new WorldLoader.PackConfig(packRepository, worldDataConfiguration, safeMode, flag);
        return new WorldLoader.InitConfig(packConfig, Commands.CommandSelection.DEDICATED, dedicatedServerProperties.functionPermissions);
    }

    public static void forceUpgrade(
        LevelStorageSource.LevelStorageAccess levelStorage,
        WorldData worldData,
        DataFixer dataFixer,
        boolean eraseCache,
        BooleanSupplier running,
        RegistryAccess registryAccess,
        boolean recreateRegionFiles
    ) {
        LOGGER.info("Forcing world upgrade! {}", levelStorage.getLevelId()); // CraftBukkit

        try (WorldUpgrader worldUpgrader = new WorldUpgrader(levelStorage, dataFixer, worldData, registryAccess, eraseCache, recreateRegionFiles)) {
            Component component = null;

            while (!worldUpgrader.isFinished()) {
                Component status = worldUpgrader.getStatus();
                if (component != status) {
                    component = status;
                    LOGGER.info(worldUpgrader.getStatus().getString());
                }

                int totalChunks = worldUpgrader.getTotalChunks();
                if (totalChunks > 0) {
                    int i = worldUpgrader.getConverted() + worldUpgrader.getSkipped();
                    LOGGER.info("{}% completed ({} / {} chunks)...", Mth.floor((float)i / totalChunks * 100.0F), i, totalChunks);
                }

                if (!running.getAsBoolean()) {
                    worldUpgrader.cancel();
                } else {
                    try {
                        Thread.sleep(1000L);
                    } catch (InterruptedException var13) {
                    }
                }
            }
        }
    }
}
