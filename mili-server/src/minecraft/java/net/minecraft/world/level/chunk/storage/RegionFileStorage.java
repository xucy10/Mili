package net.minecraft.world.level.chunk.storage;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StreamTagVisitor;
import net.minecraft.util.ExceptionCollector;
import net.minecraft.util.FileUtil;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

public class RegionFileStorage implements AutoCloseable, ca.spottedleaf.moonrise.patches.chunk_system.io.ChunkSystemRegionFileStorage { // Paper - rewrite chunk system
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger(); // Paper
    public static final String ANVIL_EXTENSION = ".mca";
    private static final int MAX_CACHE_SIZE = 256;
    public final Long2ObjectLinkedOpenHashMap<abomination.IRegionFile> regionCache = new Long2ObjectLinkedOpenHashMap<>();  // Luminol - Configurable region file format
    private final RegionStorageInfo info;
    private final Path folder;
    private final boolean sync;

    // Paper start - recalculate region file headers
    private final boolean isChunkData;

    public static boolean isChunkDataFolder(Path path) {
        return path.toFile().getName().equalsIgnoreCase("region");
    }

    @Nullable
    public static ChunkPos getRegionFileCoordinates(Path file) {
        String fileName = file.getFileName().toString();
        if (!fileName.startsWith("r.") || !fileName.endsWith(getExtensionName())) { // Luminol - Configurable region file format
            return null;
        }

        String[] split = fileName.split("\\.");

        if (split.length != 4) {
            return null;
        }

        try {
            int x = Integer.parseInt(split[1]);
            int z = Integer.parseInt(split[2]);

            return new ChunkPos(x << 5, z << 5);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
    // Paper end
    // Paper start - rewrite chunk system
    private static final int REGION_SHIFT = 5;
    private static final int MAX_NON_EXISTING_CACHE = 1024 * 4;
    private final it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet nonExistingRegionFiles = new it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet();
    private static String getRegionFileName(final int chunkX, final int chunkZ) {
        return "r." + (chunkX >> REGION_SHIFT) + "." + (chunkZ >> REGION_SHIFT) + getExtensionName(); // Luminol - Configurable region file format
    }
    // Luminol start - Configurable region file format
    public static abomination.IRegionFile createNew(RegionStorageInfo info, Path filePath, Path folder, boolean sync) throws IOException{
        final me.earthme.luminol.enums.EnumRegionFormat regionFormat = me.earthme.luminol.config.modules.function.RegionFormatConfig.regionFormat;
        final String fullFileName = filePath.getFileName().toString();
        final String[] fullNameSplit = fullFileName.split("\\.");
        final String extensionName = fullNameSplit[fullNameSplit.length - 1];

        if (!regionFormat.getArgument().equalsIgnoreCase(extensionName)) {
            net.minecraft.server.MinecraftServer.setFatalException(new RuntimeException("Invalid region file format: " + extensionName + " expected " + regionFormat.getArgument()));
            throw new IOException("Invalid region file format: " + extensionName + " expected " + regionFormat.getArgument());
        }

        return regionFormat.getCreator().create(new me.earthme.luminol.utils.RegionCreatorInfo(info, filePath, folder, sync));
    }

    public static String getExtensionName() {
        return "." + me.earthme.luminol.config.modules.function.RegionFormatConfig.regionFormat.getArgument();
    }
    // Luminol end

    private boolean doesRegionFilePossiblyExist(final long position) {
        synchronized (this.nonExistingRegionFiles) {
            if (this.nonExistingRegionFiles.contains(position)) {
                this.nonExistingRegionFiles.addAndMoveToFirst(position);
                return false;
            }
            return true;
        }
    }

    private void createRegionFile(final long position) {
        synchronized (this.nonExistingRegionFiles) {
            this.nonExistingRegionFiles.remove(position);
        }
    }

    private void markNonExisting(final long position) {
        synchronized (this.nonExistingRegionFiles) {
            if (this.nonExistingRegionFiles.addAndMoveToFirst(position)) {
                while (this.nonExistingRegionFiles.size() >= MAX_NON_EXISTING_CACHE) {
                    this.nonExistingRegionFiles.removeLastLong();
                }
            }
        }
    }

    @Override
    public final boolean moonrise$doesRegionFileNotExistNoIO(final int chunkX, final int chunkZ) {
        return !this.doesRegionFilePossiblyExist(ChunkPos.asLong(chunkX >> REGION_SHIFT, chunkZ >> REGION_SHIFT));
    }

    @Override
    public synchronized final abomination.IRegionFile moonrise$getRegionFileIfLoaded(final int chunkX, final int chunkZ) { // Luminol - Configurable region file format
        return this.regionCache.getAndMoveToFirst(ChunkPos.asLong(chunkX >> REGION_SHIFT, chunkZ >> REGION_SHIFT));
    }

    @Override
    public synchronized final abomination.IRegionFile moonrise$getRegionFileIfExists(final int chunkX, final int chunkZ) throws IOException {  // Luminol - Configurable region file format
        final long key = ChunkPos.asLong(chunkX >> REGION_SHIFT, chunkZ >> REGION_SHIFT);

        abomination.IRegionFile ret = this.regionCache.getAndMoveToFirst(key);  // Luminol - Configurable region file format
        if (ret != null) {
            return ret;
        }

        if (!this.doesRegionFilePossiblyExist(key)) {
            return null;
        }

        if (this.regionCache.size() >= io.papermc.paper.configuration.GlobalConfiguration.get().misc.regionFileCacheSize) { // Paper
            this.regionCache.removeLast().close();
        }

        final Path regionPath = this.folder.resolve(getRegionFileName(chunkX, chunkZ));

        if (!java.nio.file.Files.exists(regionPath)) {
            this.markNonExisting(key);
            return null;
        }

        this.createRegionFile(key);

        FileUtil.createDirectoriesSafe(this.folder);

        ret = this.createNew(this.info, regionPath, this.folder, this.sync);  // Luminol - Configurable region file format

        this.regionCache.putAndMoveToFirst(key, ret);

        return ret;
    }

    @Override
    public final ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO.RegionDataController.WriteData moonrise$startWrite(
        final int chunkX, final int chunkZ, final CompoundTag compound
    ) throws IOException {
        if (compound == null) {
            return new ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO.RegionDataController.WriteData(
                compound, ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO.RegionDataController.WriteData.WriteResult.DELETE,
                null, null
            );
        }

        final ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        final abomination.IRegionFile regionFile = this.getRegionFile(pos);  // Luminol - Configurable region file format

        // note: not required to keep regionfile loaded after this call, as the write param takes a regionfile as input
        // (and, the regionfile parameter is unused for writing until the write call)
        final ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO.RegionDataController.WriteData writeData = ((ca.spottedleaf.moonrise.patches.chunk_system.storage.ChunkSystemRegionFile)regionFile).moonrise$startWrite(compound, pos);

        try { // Paper - implement RegionFileSizeException
        try {
            NbtIo.write(compound, writeData.output());
        } finally {
            writeData.output().close();
        }
        // Paper start - implement RegionFileSizeException
        } catch (final RegionFileSizeException ex) {
            // note: it's OK if close() is called, as close() here will not issue a write to the RegionFile
            // see startWrite
            final int maxSize = RegionFile.MAX_CHUNK_SIZE / (1024 * 1024);
            LOGGER.error("Chunk at (" + chunkX + "," + chunkZ + ") in regionfile '" + regionFile.getPath().toString() + "' exceeds max size of " + maxSize + "MiB, it has been deleted from disk.");
            return new ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO.RegionDataController.WriteData(
                compound, ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO.RegionDataController.WriteData.WriteResult.DELETE,
                null, null
            );
        }
        // Paper end - implement RegionFileSizeException

        return writeData;
    }

    @Override
    public final void moonrise$finishWrite(
        final int chunkX, final int chunkZ, final ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO.RegionDataController.WriteData writeData
    ) throws IOException {
        final ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        if (writeData.result() == ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO.RegionDataController.WriteData.WriteResult.DELETE) {
            final abomination.IRegionFile regionFile = this.moonrise$getRegionFileIfExists(chunkX, chunkZ);  // Luminol - Configurable region file format
            if (regionFile != null) {
                regionFile.clear(pos);
            } // else: didn't exist

            return;
        }

        writeData.write().run(this.getRegionFile(pos));
    }

    @Override
    public final ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO.RegionDataController.ReadData moonrise$readData(
        final int chunkX, final int chunkZ
    ) throws IOException {
        final abomination.IRegionFile regionFile = this.moonrise$getRegionFileIfExists(chunkX, chunkZ);  // Luminol - Configurable region file format

        final DataInputStream input = regionFile == null ? null : regionFile.getChunkDataInputStream(new ChunkPos(chunkX, chunkZ));

        if (input == null) {
            return new ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO.RegionDataController.ReadData(
                ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO.RegionDataController.ReadData.ReadResult.NO_DATA, null, null, regionFile == null ? 0 : regionFile.getRecalculateCount() // Paper - Attempt to recalculate regionfile header if it is corrupt
            );
        }

        final ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO.RegionDataController.ReadData ret = new ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO.RegionDataController.ReadData(
            ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO.RegionDataController.ReadData.ReadResult.HAS_DATA, input, null, regionFile.getRecalculateCount() // Paper - Attempt to recalculate regionfile header if it is corrupt
        );

        if (!(input instanceof ca.spottedleaf.moonrise.patches.chunk_system.util.stream.ExternalChunkStreamMarker)) {
            // internal stream, which is fully read
            return ret;
        }

        final CompoundTag syncRead = this.moonrise$finishRead(chunkX, chunkZ, ret);

        if (syncRead == null) {
            // need to try again
            return this.moonrise$readData(chunkX, chunkZ);
        }

        return new ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO.RegionDataController.ReadData(
            ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO.RegionDataController.ReadData.ReadResult.SYNC_READ, null, syncRead, regionFile.getRecalculateCount() // Paper - Attempt to recalculate regionfile header if it is corrupt
        );
    }

    // if the return value is null, then the caller needs to re-try with a new call to readData()
    @Override
    public final CompoundTag moonrise$finishRead(
        final int chunkX, final int chunkZ, final ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO.RegionDataController.ReadData readData
    ) throws IOException {
        try {
            // Paper start - Attempt to recalculate regionfile header if it is corrupt
            final CompoundTag ret = NbtIo.read(readData.input());
            if (!this.isChunkData) {
                return ret;
            }

            final ChunkPos pos = new ChunkPos(chunkX, chunkZ);
            final ChunkPos headerChunkPos = SerializableChunkData.getChunkCoordinate(ret);
            final abomination.IRegionFile regionFile = this.getRegionFile(pos);  // Luminol - Configurable region file format

            if (regionFile.getRecalculateCount() != readData.recalculateCount()) {
                return null;
            }

            if (!headerChunkPos.equals(pos)) {
                LOGGER.error("Attempting to read chunk data at " + pos + " but got chunk data for " + headerChunkPos + " instead! Attempting regionfile recalculation " + regionFile.getPath().toAbsolutePath());
                if (regionFile.recalculateHeader()) {
                    return null;
                }

                LOGGER.error(com.mojang.logging.LogUtils.FATAL_MARKER, "Can't recalculate regionfile header?");
                return ret;
            }

            return ret;
            // Paper end - Attempt to recalculate regionfile header if it is corrupt
        } finally {
            readData.input().close();
        }
    }
    // Paper end - rewrite chunk system
    // Paper start - rewrite chunk system
    public abomination.IRegionFile getRegionFile(ChunkPos chunkcoordintpair) throws IOException {  // Luminol - Configurable region file format
        return this.getRegionFile(chunkcoordintpair, false);
    }
    // Paper end - rewrite chunk system

    protected RegionFileStorage(RegionStorageInfo info, Path folder, boolean sync) { // Paper - protected
        this.folder = folder;
        this.sync = sync;
        this.info = info;
        this.isChunkData = isChunkDataFolder(this.folder); // Paper - recalculate region file headers
    }

    @org.jetbrains.annotations.Contract("_, false -> !null") private abomination.@Nullable IRegionFile getRegionFile(ChunkPos chunkPos, boolean existingOnly) throws IOException { // CraftBukkit
        // Paper start - rewrite chunk system
        if (existingOnly) {
            return this.moonrise$getRegionFileIfExists(chunkPos.x, chunkPos.z);
        }
        synchronized (this) {
            final long key = ChunkPos.asLong(chunkPos.x >> REGION_SHIFT, chunkPos.z >> REGION_SHIFT);

            abomination.IRegionFile ret = this.regionCache.getAndMoveToFirst(key);  // Luminol - Configurable region file format
            if (ret != null) {
                return ret;
            }

            if (this.regionCache.size() >= io.papermc.paper.configuration.GlobalConfiguration.get().misc.regionFileCacheSize) { // Paper
                this.regionCache.removeLast().close();
            }

            final Path regionPath = this.folder.resolve(getRegionFileName(chunkPos.x, chunkPos.z));

            this.createRegionFile(key);

            FileUtil.createDirectoriesSafe(this.folder);

            ret = this.createNew(this.info, regionPath, this.folder, this.sync); // Luminol - Configurable region file format

            this.regionCache.putAndMoveToFirst(key, ret);

            return ret;
        }
        // Paper end - rewrite chunk system
    }

    // Paper start
    private static void printOversizedLog(String msg, Path file, int x, int z) {
        org.apache.logging.log4j.LogManager.getLogger().fatal(msg + " (" + file.toString().replaceAll(".+[\\\\/]", "") + " - " + x + "," + z + ") Go clean it up to remove this message. /minecraft:tp " + (x<<4)+" 128 "+(z<<4) + " - DO NOT REPORT THIS TO PAPER - You may ask for help on Discord, but do not file an issue. These error messages can not be removed.");
    }

    private static CompoundTag readOversizedChunk(abomination.IRegionFile regionfile, ChunkPos chunkCoordinate) throws IOException {  // Luminol - Configurable region file format
        synchronized (regionfile) {
            try (DataInputStream datainputstream = regionfile.getChunkDataInputStream(chunkCoordinate)) {
                CompoundTag oversizedData = regionfile.getOversizedData(chunkCoordinate.x, chunkCoordinate.z);
                CompoundTag chunk = NbtIo.read(datainputstream);
                if (oversizedData == null) {
                    return chunk;
                }
                CompoundTag oversizedLevel = oversizedData.getCompoundOrEmpty("Level");

                mergeChunkList(chunk.getCompoundOrEmpty("Level"), oversizedLevel, "Entities", "Entities");
                mergeChunkList(chunk.getCompoundOrEmpty("Level"), oversizedLevel, "TileEntities", "TileEntities");

                return chunk;
            } catch (Throwable throwable) {
                throwable.printStackTrace();
                throw throwable;
            }
        }
    }

    private static void mergeChunkList(CompoundTag level, CompoundTag oversizedLevel, String key, String oversizedKey) {
        net.minecraft.nbt.ListTag levelList = level.getListOrEmpty(key);
        net.minecraft.nbt.ListTag oversizedList = oversizedLevel.getListOrEmpty(oversizedKey);

        if (!oversizedList.isEmpty()) {
            levelList.addAll(oversizedList);
            level.put(key, levelList);
        }
    }
    // Paper end

    public @Nullable CompoundTag read(ChunkPos chunkPos) throws IOException {
        // CraftBukkit start - SPIGOT-5680: There's no good reason to preemptively create files on read, save that for writing
        abomination.IRegionFile regionFile = this.getRegionFile(chunkPos, true); // Luminol - Configurable region file format
        if (regionFile == null) {
            return null;
        }
        // CraftBukkit end
        // Paper start
        if (regionFile.isOversized(chunkPos.x, chunkPos.z)) {
            printOversizedLog("Loading Oversized Chunk!", regionFile.getPath(), chunkPos.x, chunkPos.z);
            return readOversizedChunk(regionFile, chunkPos);
        }
        // Paper end

        CompoundTag var4;
        try (DataInputStream chunkDataInputStream = regionFile.getChunkDataInputStream(chunkPos)) {
            if (chunkDataInputStream == null) {
                return null;
            }

            var4 = NbtIo.read(chunkDataInputStream);
            // Paper start - recover from corrupt regionfile header
            if (this.isChunkData) {
                ChunkPos headerChunkPos = SerializableChunkData.getChunkCoordinate(var4);
                if (!headerChunkPos.equals(chunkPos)) {
                    net.minecraft.server.MinecraftServer.LOGGER.error("Attempting to read chunk data at " + chunkPos + " but got chunk data for " + headerChunkPos + " instead! Attempting regionfile recalculation for regionfile " + regionFile.getPath().toAbsolutePath());
                    if (regionFile.recalculateHeader()) {
                        return this.read(chunkPos);
                    }
                    net.minecraft.server.MinecraftServer.LOGGER.error("Can't recalculate regionfile header, regenerating chunk " + chunkPos + " for " + regionFile.getPath().toAbsolutePath());
                    return null;
                }
            }
            // Paper end - recover from corrupt regionfile header
        }

        return var4;
    }

    public void scanChunk(ChunkPos chunkPos, StreamTagVisitor visitor) throws IOException {
        // CraftBukkit start - SPIGOT-5680: There's no good reason to preemptively create files on read, save that for writing
        abomination.IRegionFile regionFile = this.getRegionFile(chunkPos, true); // Luminol - Configurable region file format
        if (regionFile == null) {
            return;
        }
        // CraftBukkit end

        try (DataInputStream chunkDataInputStream = regionFile.getChunkDataInputStream(chunkPos)) {
            if (chunkDataInputStream != null) {
                NbtIo.parse(chunkDataInputStream, visitor, NbtAccounter.unlimitedHeap());
            }
        }
    }

    public void write(ChunkPos chunkPos, @Nullable CompoundTag chunkData) throws IOException { // Paper - rewrite chunk system - public
        if (!SharedConstants.DEBUG_DONT_SAVE_WORLD) {
            abomination.IRegionFile regionFile = this.getRegionFile(chunkPos, chunkData == null); // CraftBukkit // Paper - rewrite chunk system // Luminol - Configurable region file format
            // Paper start - rewrite chunk system
            if (regionFile == null) {
                // if the RegionFile doesn't exist, no point in deleting from it
                return;
            }
            // Paper end - rewrite chunk system
            if (chunkData == null) {
                regionFile.clear(chunkPos);
            } else {
            DataOutputStream chunkDataOutputStream = regionFile.getChunkDataOutputStream(chunkPos); // Paper - Only write if successful
            try { // Paper - Only write if successful
                    NbtIo.write(chunkData, chunkDataOutputStream);
                    regionFile.setOversized(chunkPos.x, chunkPos.z, false); // Paper - We don't do this anymore, mojang stores differently, but clear old meta flag if it exists to get rid of our own meta file once last oversized is gone
                // Paper start - don't write garbage data to disk if writing serialization fails
                chunkDataOutputStream.close();
            } catch (final RegionFileSizeException ex) {
                regionFile.clear(chunkPos);
                final int maxSize = RegionFile.MAX_CHUNK_SIZE / (1024 * 1024);
                LOGGER.error("Chunk at (" + chunkPos.x + "," + chunkPos.z + ") in regionfile '" + regionFile.getPath().toString() + "' exceeds max size of " + maxSize + "MiB, it has been deleted from disk.");
                // Paper end - don't write garbage data to disk if writing serialization fails
                }
            }
        }
    }

    @Override
    public void close() throws IOException {
        // Paper start - rewrite chunk system
        synchronized (this) {
            final ExceptionCollector<IOException> exceptionCollector = new ExceptionCollector<>();
            for (final abomination.IRegionFile regionFile : this.regionCache.values()) { // Luminol - Configurable region file format
                try {
                    regionFile.close();
                } catch (final IOException ex) {
                    exceptionCollector.add(ex);
                }
            }
            exceptionCollector.throwIfPresent();
        }
        // Paper end - rewrite chunk system
    }

    public void flush() throws IOException {
        // Paper start - rewrite chunk system
        synchronized (this) {
            final ExceptionCollector<IOException> exceptionCollector = new ExceptionCollector<>();
            for (final abomination.IRegionFile regionFile : this.regionCache.values()) { // Luminol - Configurable region file format
                try {
                    regionFile.flush();
                } catch (final IOException ex) {
                    exceptionCollector.add(ex);
                }
            }

            exceptionCollector.throwIfPresent();
        }
        // Paper end - rewrite chunk system
    }

    public RegionStorageInfo info() {
        return this.info;
    }

    // Paper start - don't write garbage data to disk if writing serialization fails
    public static final class RegionFileSizeException extends RuntimeException {

        public RegionFileSizeException(final String message) {
            super(message);
        }
    }
    // Paper end - don't write garbage data to disk if writing serialization fails
}
