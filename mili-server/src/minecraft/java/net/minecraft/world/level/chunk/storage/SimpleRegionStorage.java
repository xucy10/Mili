package net.minecraft.world.level.chunk.storage;

import com.google.common.base.Suppliers;
import com.mojang.datafixers.DataFixer;
import com.mojang.serialization.Dynamic;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

public class SimpleRegionStorage implements ca.spottedleaf.moonrise.patches.chunk_system.storage.ChunkSystemSimpleRegionStorage, AutoCloseable { // Paper - rewrite chunk system
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getClassLogger(); // Paper - rewrite chunk system
    private final RegionFileStorage storage; // Paper - rewrite chunk system
    private final DataFixer fixerUpper;
    private final DataFixTypes dataFixType;
    private final Supplier<LegacyTagFixer> legacyFixer;

    public SimpleRegionStorage(RegionStorageInfo info, Path folder, DataFixer fixerUpper, boolean sync, DataFixTypes dataFixType) {
        this(info, folder, fixerUpper, sync, dataFixType, LegacyTagFixer.EMPTY);
    }

    public SimpleRegionStorage(
        RegionStorageInfo info, Path folder, DataFixer fixerUpper, boolean sync, DataFixTypes dataFixType, Supplier<LegacyTagFixer> legacyFixer
    ) {
        this.fixerUpper = fixerUpper;
        this.dataFixType = dataFixType;
        this.storage = new IOWorker(info, folder, sync).storage; // Paper - rewrite chunk system
        this.legacyFixer = Suppliers.memoize(legacyFixer::get);
    }

    // Paper start - rewrite chunk system
    @Override
    public final RegionFileStorage moonrise$getRegionStorage() {
        return this.storage;
    }
    // Paper end - rewrite chunk system

    public boolean isOldChunkAround(ChunkPos chunkPos, int radius) {
        return true; // Paper - rewrite chunk system
    }

    public CompletableFuture<Optional<CompoundTag>> read(ChunkPos chunkPos) {
        // Paper start - rewrite chunk system
        try {
            return CompletableFuture.completedFuture(Optional.ofNullable(this.storage.read(chunkPos)));
        } catch (final Throwable throwable) {
            return CompletableFuture.failedFuture(throwable);
        }
        // Paper end - rewrite chunk system
    }

    public CompletableFuture<Void> write(ChunkPos chunkPos, CompoundTag data) {
        return this.write(chunkPos, () -> data);
    }

    public CompletableFuture<Void> write(ChunkPos chunkPos, Supplier<CompoundTag> data) {
        // Paper start - guard against possible chunk pos desync
        final Supplier<CompoundTag> guardedPosCheck = () -> {
            CompoundTag nbt = data.get();
            final boolean chunkStorage = this.dataFixType == net.minecraft.util.datafix.DataFixTypes.CHUNK;
            if (chunkStorage && nbt != null && !chunkPos.equals(SerializableChunkData.getChunkCoordinate(nbt))) {
                final String world = (SimpleRegionStorage.this instanceof net.minecraft.server.level.ChunkMap) ? ((net.minecraft.server.level.ChunkMap) SimpleRegionStorage.this).level.getWorld().getName() : null;
                throw new IllegalArgumentException("Chunk coordinate and serialized data do not have matching coordinates, trying to serialize coordinate " + chunkPos
                    + " but compound says coordinate is " + SerializableChunkData.getChunkCoordinate(nbt) + (world == null ? " for an unknown world" : (" for world: " + world)));
            }
            return nbt;
        };
        // Paper end - guard against possible chunk pos desync
        this.markChunkDone(chunkPos);
        // Paper - rewrite chunk system
        try {
            this.storage.write(chunkPos, guardedPosCheck.get());
            return CompletableFuture.completedFuture(null);
        } catch (final Throwable throwable) {
            return CompletableFuture.failedFuture(throwable);
        }
        // Paper end - rewrite chunk system
    }

    public CompoundTag upgradeChunkTag(CompoundTag tag, int fallbackVersion, @Nullable CompoundTag contextTag, net.minecraft.world.level.@Nullable LevelAccessor levelAccessor) { // CraftBukkit
        int dataVersion = NbtUtils.getDataVersion(tag, fallbackVersion);
        if (dataVersion == SharedConstants.getCurrentVersion().dataVersion().version()) {
            return tag;
        } else {
            try {
                // Paper start - rewrite chunk system
                final net.minecraft.world.level.chunk.storage.LegacyTagFixer legacyFixer = this.legacyFixer.get();
                synchronized (legacyFixer) {
                    tag = legacyFixer.applyFix(tag);
                }
                // Paper end - rewrite chunk system
                // Spigot start - SPIGOT-6806: Quick and dirty way to prevent below zero generation in old chunks, by setting the status to heightmap instead of empty
                boolean stopBelowZero = false;
                final boolean chunkStorage = this.dataFixType == net.minecraft.util.datafix.DataFixTypes.CHUNK;
                if (chunkStorage) {
                    boolean belowZeroGenerationInExistingChunks = (levelAccessor != null) ? ((net.minecraft.server.level.ServerLevel) levelAccessor).spigotConfig.belowZeroGenerationInExistingChunks : org.spigotmc.SpigotConfig.belowZeroGenerationInExistingChunks;

                    if (dataVersion <= 2730 && !belowZeroGenerationInExistingChunks) {
                        stopBelowZero = "full".equals(tag.getCompound("Level").flatMap(l -> l.getString("Status")).orElse(null));
                    }
                }
                // Spigot end
                injectDatafixingContext(tag, contextTag);
                tag = ca.spottedleaf.dataconverter.minecraft.MCDataConverter.convertTag(this.getDataConverterType(), tag, Math.max(this.legacyFixer.get().targetDataVersion(), dataVersion), ca.spottedleaf.dataconverter.minecraft.util.Version.getCurrentVersion()); // Paper - rewrite dataconverter system
                // Spigot start
                if (stopBelowZero) {
                    tag.putString("Status", net.minecraft.core.registries.BuiltInRegistries.CHUNK_STATUS.getKey(net.minecraft.world.level.chunk.status.ChunkStatus.SPAWN).toString());
                }
                // Spigot end
                removeDatafixingContext(tag);
                NbtUtils.addCurrentDataVersion(tag);
                return tag;
            } catch (Exception var8) {
                CrashReport crashReport = CrashReport.forThrowable(var8, "Updated chunk");
                CrashReportCategory crashReportCategory = crashReport.addCategory("Updated chunk details");
                crashReportCategory.setDetail("Data version", dataVersion);
                throw new ReportedException(crashReport);
            }
        }
    }

    // Paper start - rewrite data conversion system
    private ca.spottedleaf.dataconverter.minecraft.datatypes.MCDataType getDataConverterType() {
        if (this.dataFixType == DataFixTypes.CHUNK) {
            return ca.spottedleaf.dataconverter.minecraft.datatypes.MCTypeRegistry.CHUNK;
        } else if (this.dataFixType == DataFixTypes.ENTITY_CHUNK) {
            return ca.spottedleaf.dataconverter.minecraft.datatypes.MCTypeRegistry.ENTITY_CHUNK;
        } else if (this.dataFixType == DataFixTypes.POI_CHUNK) {
            return ca.spottedleaf.dataconverter.minecraft.datatypes.MCTypeRegistry.POI_CHUNK;
        } else {
            throw new UnsupportedOperationException("For " + this.dataFixType.name());
        }
    }
    // Paper end - rewrite data conversion system

    public CompoundTag upgradeChunkTag(CompoundTag tag, int version) {
        return this.upgradeChunkTag(tag, version, null, null); // CraftBukkit
    }

    public Dynamic<Tag> upgradeChunkTag(Dynamic<Tag> tag, int version) {
        return new Dynamic<>(tag.getOps(), this.upgradeChunkTag((CompoundTag)tag.getValue(), version, null, null)); // CraftBukkit
    }

    public static void injectDatafixingContext(CompoundTag tag, @Nullable CompoundTag contextTag) {
        if (contextTag != null) {
            tag.put("__context", contextTag);
        }
    }

    private static void removeDatafixingContext(CompoundTag tag) {
        tag.remove("__context");
    }

    protected void markChunkDone(ChunkPos chunkPos) {
        // Paper start - rewrite chunk system
        final net.minecraft.world.level.chunk.storage.LegacyTagFixer legacyFixer = this.legacyFixer.get();
        synchronized (legacyFixer) {
            legacyFixer.markChunkDone(chunkPos);
        }
        // Paper end - rewrite chunk system
    }

    public CompletableFuture<Void> synchronize(boolean flushStorage) {
        // Paper start - rewrite chunk system
        try {
            this.storage.flush();
            return CompletableFuture.completedFuture(null);
        } catch (final IOException ex) {
            LOGGER.error("Failed to flush chunk storage", ex);
            return CompletableFuture.failedFuture(ex);
        }
        // Paper end - rewrite chunk system
    }

    @Override
    public void close() throws IOException {
        this.storage.close(); // Paper - rewrite chunk system
    }

    public ChunkScanAccess chunkScanner() {
        // Paper start - rewrite chunk system
        // TODO ChunkMap implementation?
        return (chunkPos, streamTagVisitor) -> {
            try {
                this.storage.scanChunk(chunkPos, streamTagVisitor);
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };
        // Paper end - rewrite chunk system
    }

    public RegionStorageInfo storageInfo() {
        return this.storage.info(); // Paper - rewrite chunk system
    }
}
