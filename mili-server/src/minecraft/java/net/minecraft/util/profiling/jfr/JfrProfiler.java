package net.minecraft.util.profiling.jfr;

import com.mojang.logging.LogUtils;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.SocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import jdk.jfr.Configuration;
import jdk.jfr.Event;
import jdk.jfr.FlightRecorder;
import jdk.jfr.FlightRecorderListener;
import jdk.jfr.Recording;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.FileUtil;
import net.minecraft.util.Util;
import net.minecraft.util.profiling.jfr.callback.ProfiledDuration;
import net.minecraft.util.profiling.jfr.event.ChunkGenerationEvent;
import net.minecraft.util.profiling.jfr.event.ChunkRegionReadEvent;
import net.minecraft.util.profiling.jfr.event.ChunkRegionWriteEvent;
import net.minecraft.util.profiling.jfr.event.ClientFpsEvent;
import net.minecraft.util.profiling.jfr.event.NetworkSummaryEvent;
import net.minecraft.util.profiling.jfr.event.PacketReceivedEvent;
import net.minecraft.util.profiling.jfr.event.PacketSentEvent;
import net.minecraft.util.profiling.jfr.event.ServerTickTimeEvent;
import net.minecraft.util.profiling.jfr.event.StructureGenerationEvent;
import net.minecraft.util.profiling.jfr.event.WorldLoadFinishedEvent;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.storage.RegionFileVersion;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class JfrProfiler implements JvmProfiler {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String ROOT_CATEGORY = "Minecraft";
    public static final String WORLD_GEN_CATEGORY = "World Generation";
    public static final String TICK_CATEGORY = "Ticking";
    public static final String NETWORK_CATEGORY = "Network";
    public static final String STORAGE_CATEGORY = "Storage";
    private static final List<Class<? extends Event>> CUSTOM_EVENTS = List.of(
        ChunkGenerationEvent.class,
        ChunkRegionReadEvent.class,
        ChunkRegionWriteEvent.class,
        PacketReceivedEvent.class,
        PacketSentEvent.class,
        NetworkSummaryEvent.class,
        ServerTickTimeEvent.class,
        ClientFpsEvent.class,
        StructureGenerationEvent.class,
        WorldLoadFinishedEvent.class
    );
    private static final String FLIGHT_RECORDER_CONFIG = "/flightrecorder-config.jfc";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = new DateTimeFormatterBuilder()
        .appendPattern("yyyy-MM-dd-HHmmss")
        .toFormatter(Locale.ROOT)
        .withZone(ZoneId.systemDefault());
    private static final JfrProfiler INSTANCE = new JfrProfiler();
    @Nullable Recording recording;
    private int currentFPS;
    private float currentAverageTickTimeServer;
    private final Map<String, NetworkSummaryEvent.SumAggregation> networkTrafficByAddress = new ConcurrentHashMap<>();
    private final Runnable periodicClientFps = () -> new ClientFpsEvent(this.currentFPS).commit();
    private final Runnable periodicServerTickTime = () -> new ServerTickTimeEvent(this.currentAverageTickTimeServer).commit();
    private final Runnable periodicNetworkSummary = () -> {
        Iterator<NetworkSummaryEvent.SumAggregation> iterator = this.networkTrafficByAddress.values().iterator();

        while (iterator.hasNext()) {
            iterator.next().commitEvent();
            iterator.remove();
        }
    };

    private JfrProfiler() {
        CUSTOM_EVENTS.forEach(FlightRecorder::register);
        this.registerPeriodicEvents();
        FlightRecorder.addListener(new FlightRecorderListener() {
            @Override
            public void recordingStateChanged(Recording recording) {
                switch (recording.getState()) {
                    case STOPPED:
                        JfrProfiler.this.registerPeriodicEvents();
                    case NEW:
                    case DELAYED:
                    case RUNNING:
                    case CLOSED:
                }
            }
        });
    }

    void registerPeriodicEvents() {
        addPeriodicEvent(ClientFpsEvent.class, this.periodicClientFps);
        addPeriodicEvent(ServerTickTimeEvent.class, this.periodicServerTickTime);
        addPeriodicEvent(NetworkSummaryEvent.class, this.periodicNetworkSummary);
    }

    private static void addPeriodicEvent(Class<? extends Event> eventType, Runnable event) {
        FlightRecorder.removePeriodicEvent(event);
        FlightRecorder.addPeriodicEvent(eventType, event);
    }

    public static JfrProfiler getInstance() {
        return INSTANCE;
    }

    @Override
    public boolean start(Environment environment) {
        URL resource = JfrProfiler.class.getResource("/flightrecorder-config.jfc");
        if (resource == null) {
            LOGGER.warn("Could not find default flight recorder config at {}", "/flightrecorder-config.jfc");
            return false;
        } else {
            try {
                boolean var4;
                try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(resource.openStream(), StandardCharsets.UTF_8))) {
                    var4 = this.start(bufferedReader, environment);
                }

                return var4;
            } catch (IOException var8) {
                LOGGER.warn("Failed to start flight recorder using configuration at {}", resource, var8);
                return false;
            }
        }
    }

    @Override
    public Path stop() {
        if (this.recording == null) {
            throw new IllegalStateException("Not currently profiling");
        } else {
            this.networkTrafficByAddress.clear();
            Path destination = this.recording.getDestination();
            this.recording.stop();
            return destination;
        }
    }

    @Override
    public boolean isRunning() {
        return this.recording != null;
    }

    @Override
    public boolean isAvailable() {
        return FlightRecorder.isAvailable();
    }

    private boolean start(Reader reader, Environment environment) {
        if (this.isRunning()) {
            LOGGER.warn("Profiling already in progress");
            return false;
        } else {
            try {
                Configuration configuration = Configuration.create(reader);
                String string = DATE_TIME_FORMATTER.format(Instant.now());
                this.recording = Util.make(new Recording(configuration), recording -> {
                    CUSTOM_EVENTS.forEach(recording::enable);
                    recording.setDumpOnExit(true);
                    recording.setToDisk(true);
                    recording.setName(String.format(Locale.ROOT, "%s-%s-%s", environment.getDescription(), SharedConstants.getCurrentVersion().name(), string));
                });
                Path path = Paths.get(String.format(Locale.ROOT, "debug/%s-%s.jfr", environment.getDescription(), string));
                FileUtil.createDirectoriesSafe(path.getParent());
                this.recording.setDestination(path);
                this.recording.start();
                this.setupSummaryListener();
            } catch (ParseException | IOException var6) {
                LOGGER.warn("Failed to start jfr profiling", (Throwable)var6);
                return false;
            }

            LOGGER.info(
                "Started flight recorder profiling id({}):name({}) - will dump to {} on exit or stop command",
                this.recording.getId(),
                this.recording.getName(),
                this.recording.getDestination()
            );
            return true;
        }
    }

    private void setupSummaryListener() {
        FlightRecorder.addListener(new FlightRecorderListener() {
            final SummaryReporter summaryReporter = new SummaryReporter(() -> JfrProfiler.this.recording = null);

            @Override
            public void recordingStateChanged(Recording recording) {
                if (recording == JfrProfiler.this.recording) {
                    switch (recording.getState()) {
                        case STOPPED:
                            this.summaryReporter.recordingStopped(recording.getDestination());
                            FlightRecorder.removeListener(this);
                        case NEW:
                        case DELAYED:
                        case RUNNING:
                        case CLOSED:
                    }
                }
            }
        });
    }

    @Override
    public void onClientTick(int fps) {
        if (ClientFpsEvent.TYPE.isEnabled()) {
            this.currentFPS = fps;
        }
    }

    @Override
    public void onServerTick(float currentAverageTickTime) {
        if (ServerTickTimeEvent.TYPE.isEnabled()) {
            this.currentAverageTickTimeServer = currentAverageTickTime;
        }
    }

    @Override
    public void onPacketReceived(ConnectionProtocol protocol, PacketType<?> packetType, SocketAddress address, int size) {
        if (PacketReceivedEvent.TYPE.isEnabled()) {
            new PacketReceivedEvent(protocol.id(), packetType.flow().id(), packetType.id().toString(), address, size).commit();
        }

        if (NetworkSummaryEvent.TYPE.isEnabled()) {
            this.networkStatFor(address).trackReceivedPacket(size);
        }
    }

    @Override
    public void onPacketSent(ConnectionProtocol protocol, PacketType<?> packetType, SocketAddress address, int size) {
        if (PacketSentEvent.TYPE.isEnabled()) {
            new PacketSentEvent(protocol.id(), packetType.flow().id(), packetType.id().toString(), address, size).commit();
        }

        if (NetworkSummaryEvent.TYPE.isEnabled()) {
            this.networkStatFor(address).trackSentPacket(size);
        }
    }

    private NetworkSummaryEvent.SumAggregation networkStatFor(SocketAddress remoteAddress) {
        return this.networkTrafficByAddress.computeIfAbsent(remoteAddress.toString(), NetworkSummaryEvent.SumAggregation::new);
    }

    @Override
    public void onRegionFileRead(RegionStorageInfo regionStorageInfo, ChunkPos chunkPos, RegionFileVersion version, int bytes) {
        if (ChunkRegionReadEvent.TYPE.isEnabled()) {
            new ChunkRegionReadEvent(regionStorageInfo, chunkPos, version, bytes).commit();
        }
    }

    @Override
    public void onRegionFileWrite(RegionStorageInfo regionStorageInfo, ChunkPos chunkPos, RegionFileVersion version, int bytes) {
        if (ChunkRegionWriteEvent.TYPE.isEnabled()) {
            new ChunkRegionWriteEvent(regionStorageInfo, chunkPos, version, bytes).commit();
        }
    }

    @Override
    public @Nullable ProfiledDuration onWorldLoadedStarted() {
        if (!WorldLoadFinishedEvent.TYPE.isEnabled()) {
            return null;
        } else {
            WorldLoadFinishedEvent worldLoadFinishedEvent = new WorldLoadFinishedEvent();
            worldLoadFinishedEvent.begin();
            return success -> worldLoadFinishedEvent.commit();
        }
    }

    @Override
    public @Nullable ProfiledDuration onChunkGenerate(ChunkPos chunkPos, ResourceKey<Level> level, String name) {
        if (!ChunkGenerationEvent.TYPE.isEnabled()) {
            return null;
        } else {
            ChunkGenerationEvent chunkGenerationEvent = new ChunkGenerationEvent(chunkPos, level, name);
            chunkGenerationEvent.begin();
            return success -> chunkGenerationEvent.commit();
        }
    }

    @Override
    public @Nullable ProfiledDuration onStructureGenerate(ChunkPos chunkPos, ResourceKey<Level> level, Holder<Structure> structure) {
        if (!StructureGenerationEvent.TYPE.isEnabled()) {
            return null;
        } else {
            StructureGenerationEvent structureGenerationEvent = new StructureGenerationEvent(chunkPos, structure, level);
            structureGenerationEvent.begin();
            return success -> {
                structureGenerationEvent.success = success;
                structureGenerationEvent.commit();
            };
        }
    }
}
