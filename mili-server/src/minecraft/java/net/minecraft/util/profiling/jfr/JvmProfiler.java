package net.minecraft.util.profiling.jfr;

import com.mojang.logging.LogUtils;
import java.net.SocketAddress;
import java.nio.file.Path;
import jdk.jfr.FlightRecorder;
import net.minecraft.core.Holder;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.profiling.jfr.callback.ProfiledDuration;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.storage.RegionFileVersion;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public interface JvmProfiler {
    JvmProfiler INSTANCE = (JvmProfiler)(Runtime.class.getModule().getLayer().findModule("jdk.jfr").isPresent() && FlightRecorder.isAvailable()
        ? JfrProfiler.getInstance()
        : new JvmProfiler.NoOpProfiler());

    boolean start(Environment environment);

    Path stop();

    boolean isRunning();

    boolean isAvailable();

    void onServerTick(float currentAverageTickTime);

    void onClientTick(int fps);

    void onPacketReceived(ConnectionProtocol protocol, PacketType<?> packetType, SocketAddress address, int size);

    void onPacketSent(ConnectionProtocol protocol, PacketType<?> packetType, SocketAddress address, int size);

    void onRegionFileRead(RegionStorageInfo regionStorageInfo, ChunkPos chunkPos, RegionFileVersion version, int bytes);

    void onRegionFileWrite(RegionStorageInfo regionStorageInfo, ChunkPos chunkPos, RegionFileVersion version, int bytes);

    @Nullable ProfiledDuration onWorldLoadedStarted();

    @Nullable ProfiledDuration onChunkGenerate(ChunkPos chunkPos, ResourceKey<Level> level, String name);

    @Nullable ProfiledDuration onStructureGenerate(ChunkPos chunkPos, ResourceKey<Level> level, Holder<Structure> structure);

    public static class NoOpProfiler implements JvmProfiler {
        private static final Logger LOGGER = LogUtils.getLogger();
        static final ProfiledDuration noOpCommit = success -> {};

        @Override
        public boolean start(Environment environment) {
            LOGGER.warn("Attempted to start Flight Recorder, but it's not supported on this JVM");
            return false;
        }

        @Override
        public Path stop() {
            throw new IllegalStateException("Attempted to stop Flight Recorder, but it's not supported on this JVM");
        }

        @Override
        public boolean isRunning() {
            return false;
        }

        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public void onPacketReceived(ConnectionProtocol protocol, PacketType<?> packetType, SocketAddress address, int size) {
        }

        @Override
        public void onPacketSent(ConnectionProtocol protocol, PacketType<?> packetType, SocketAddress address, int size) {
        }

        @Override
        public void onRegionFileRead(RegionStorageInfo regionStorageInfo, ChunkPos chunkPos, RegionFileVersion version, int bytes) {
        }

        @Override
        public void onRegionFileWrite(RegionStorageInfo regionStorageInfo, ChunkPos chunkPos, RegionFileVersion version, int bytes) {
        }

        @Override
        public void onServerTick(float currentAverageTickTime) {
        }

        @Override
        public void onClientTick(int fps) {
        }

        @Override
        public ProfiledDuration onWorldLoadedStarted() {
            return noOpCommit;
        }

        @Override
        public @Nullable ProfiledDuration onChunkGenerate(ChunkPos chunkPos, ResourceKey<Level> level, String name) {
            return null;
        }

        @Override
        public ProfiledDuration onStructureGenerate(ChunkPos chunkPos, ResourceKey<Level> level, Holder<Structure> structure) {
            return noOpCommit;
        }
    }
}
