package net.minecraft.world.level.chunk.storage;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.logging.LogUtils;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.profiling.jfr.JvmProfiler;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class RegionFile implements AutoCloseable, ca.spottedleaf.moonrise.patches.chunk_system.storage.ChunkSystemRegionFile , abomination.IRegionFile{ // Paper - rewrite chunk system // Luminol - Configurable region file format
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int MAX_CHUNK_SIZE = 500 * 1024 * 1024; // Paper - don't write garbage data to disk if writing serialization fails
    private static final int SECTOR_BYTES = 4096;
    @VisibleForTesting
    protected static final int SECTOR_INTS = 1024;
    private static final int CHUNK_HEADER_SIZE = 5;
    private static final int HEADER_OFFSET = 0;
    private static final ByteBuffer PADDING_BUFFER = ByteBuffer.allocateDirect(1);
    private static final String EXTERNAL_FILE_EXTENSION = ".mcc";
    private static final int EXTERNAL_STREAM_FLAG = 128;
    private static final int EXTERNAL_CHUNK_THRESHOLD = 256;
    private static final int CHUNK_NOT_PRESENT = 0;
    final RegionStorageInfo info;
    private final Path path;
    private final FileChannel file;
    private final Path externalFileDir;
    final RegionFileVersion version;
    private final ByteBuffer header = ByteBuffer.allocateDirect(8192);
    private final IntBuffer offsets;
    private final IntBuffer timestamps;
    @VisibleForTesting
    protected final RegionBitmap usedSectors = new RegionBitmap();

    // Paper start - Attempt to recalculate regionfile header if it is corrupt
    private static long roundToSectors(long bytes) {
        long sectors = bytes >>> 12; // 4096 = 2^12
        long remainingBytes = bytes & 4095;
        long sign = -remainingBytes; // sign is 1 if nonzero
        return sectors + (sign >>> 63);
    }

    private static final net.minecraft.nbt.CompoundTag OVERSIZED_COMPOUND = new net.minecraft.nbt.CompoundTag();

    private net.minecraft.nbt.@Nullable CompoundTag attemptRead(long sector, int chunkDataLength, long fileLength) throws IOException {
        try {
            if (chunkDataLength < 0) {
                return null;
            }

            long offset = sector * 4096L + 4L; // offset for chunk data

            if ((offset + chunkDataLength) > fileLength) {
                return null;
            }

            ByteBuffer chunkData = ByteBuffer.allocate(chunkDataLength);
            if (chunkDataLength != this.file.read(chunkData, offset)) {
                return null;
            }

            ((java.nio.Buffer)chunkData).flip();

            byte compressionType = chunkData.get();
            if (compressionType < 0) { // compressionType & 128 != 0
                // oversized chunk
                return OVERSIZED_COMPOUND;
            }

            RegionFileVersion compression = RegionFileVersion.fromId(compressionType);
            if (compression == null) {
                return null;
            }

            InputStream input = compression.wrap(new ByteArrayInputStream(chunkData.array(), chunkData.position(), chunkDataLength - chunkData.position()));

            return net.minecraft.nbt.NbtIo.read(new DataInputStream(input));
        } catch (Exception ex) {
            return null;
        }
    }

    private int getLength(long sector) throws IOException {
        ByteBuffer length = ByteBuffer.allocate(4);
        if (4 != this.file.read(length, sector * 4096L)) {
            return -1;
        }

        return length.getInt(0);
    }

    private void backupRegionFile() {
        Path backup = this.path.getParent().resolve(this.path.getFileName() + "." + new java.util.Random().nextLong() + ".backup");
        this.backupRegionFile(backup);
    }

    private void backupRegionFile(Path to) {
        try {
            this.file.force(true);
            LOGGER.warn("Backing up regionfile \"" + this.path.toAbsolutePath() + "\" to " + to.toAbsolutePath());
            java.nio.file.Files.copy(this.path, to, java.nio.file.StandardCopyOption.COPY_ATTRIBUTES);
            LOGGER.warn("Backed up the regionfile to " + to.toAbsolutePath());
        } catch (IOException ex) {
            LOGGER.error("Failed to backup to " + to.toAbsolutePath(), ex);
        }
    }

    private static boolean inSameRegionfile(ChunkPos first, ChunkPos second) {
        return (first.x & ~31) == (second.x & ~31) && (first.z & ~31) == (second.z & ~31);
    }

    // note: only call for CHUNK regionfiles
    private final java.util.concurrent.atomic.AtomicInteger recalculateCount = new java.util.concurrent.atomic.AtomicInteger();

    public int getRecalculateCount() {
        return this.recalculateCount.get();
    }

    public boolean recalculateHeader() throws IOException { // Luminol - Configurable region file format // Luminol - Configurable region file format
        if (!this.canRecalcHeader) {
            return false;
        }
        ChunkPos ourLowerLeftPosition = RegionFileStorage.getRegionFileCoordinates(this.path);
        if (ourLowerLeftPosition == null) {
            LOGGER.error("Unable to get chunk location of regionfile " + this.path.toAbsolutePath() + ", cannot recover header");
            return false;
        }
        synchronized (this) {
            this.recalculateCount.getAndIncrement();

            LOGGER.warn("Corrupt regionfile header detected! Attempting to re-calculate header offsets for regionfile " + this.path.toAbsolutePath(), new Throwable());

            // try to backup file so maybe it could be sent to us for further investigation

            this.backupRegionFile();
            net.minecraft.nbt.CompoundTag[] compounds = new net.minecraft.nbt.CompoundTag[32 * 32]; // only in the regionfile (i.e exclude mojang/aikar oversized data)
            int[] rawLengths = new int[32 * 32]; // length of chunk data including 4 byte length field, bytes
            int[] sectorOffsets = new int[32 * 32]; // in sectors
            boolean[] hasAikarOversized = new boolean[32 * 32];

            long fileLength = this.file.size();
            long totalSectors = roundToSectors(fileLength);

            // search the regionfile from start to finish for the most up-to-date chunk data

            for (long i = 2, maxSector = Math.min((long)(Integer.MAX_VALUE >>> 8), totalSectors); i < maxSector; ++i) { // first two sectors are header, skip
                int chunkDataLength = this.getLength(i);
                net.minecraft.nbt.CompoundTag compound = this.attemptRead(i, chunkDataLength, fileLength);
                if (compound == null || compound == OVERSIZED_COMPOUND) {
                    continue;
                }

                ChunkPos chunkPos = SerializableChunkData.getChunkCoordinate(compound);
                if (!inSameRegionfile(ourLowerLeftPosition, chunkPos)) {
                    LOGGER.error("Ignoring absolute chunk " + chunkPos + " in regionfile as it is not contained in the bounds of the regionfile '" + this.path.toAbsolutePath() + "'. It should be in regionfile (" + (chunkPos.x >> 5) + "," + (chunkPos.z >> 5) + ")");
                    continue;
                }
                int location = (chunkPos.x & 31) | ((chunkPos.z & 31) << 5);

                net.minecraft.nbt.CompoundTag otherCompound = compounds[location];

                if (otherCompound != null && SerializableChunkData.getLastWorldSaveTime(otherCompound) > SerializableChunkData.getLastWorldSaveTime(compound)) {
                    continue; // don't overwrite newer data.
                }

                // aikar oversized?
                Path aikarOversizedFile = this.getOversizedFile(chunkPos.x, chunkPos.z);
                boolean isAikarOversized = false;
                if (Files.exists(aikarOversizedFile)) {
                    try {
                        net.minecraft.nbt.CompoundTag aikarOversizedCompound = this.getOversizedData(chunkPos.x, chunkPos.z);
                        if (SerializableChunkData.getLastWorldSaveTime(compound) == SerializableChunkData.getLastWorldSaveTime(aikarOversizedCompound)) {
                            // best we got for an id. hope it's good enough
                            isAikarOversized = true;
                        }
                    } catch (Exception ex) {
                        LOGGER.error("Failed to read aikar oversized data for absolute chunk (" + chunkPos.x + "," + chunkPos.z + ") in regionfile " + this.path.toAbsolutePath() + ", oversized data for this chunk will be lost", ex);
                        // fall through, if we can't read aikar oversized we can't risk corrupting chunk data
                    }
                }

                hasAikarOversized[location] = isAikarOversized;
                compounds[location] = compound;
                rawLengths[location] = chunkDataLength + 4;
                sectorOffsets[location] = (int)i;

                int chunkSectorLength = (int)roundToSectors(rawLengths[location]);
                i += chunkSectorLength;
                --i; // gets incremented next iteration
            }

            // forge style oversized data is already handled by the local search, and aikar data we just hope
            // we get it right as aikar data has no identifiers we could use to try and find its corresponding
            // local data compound

            java.nio.file.Path containingFolder = this.externalFileDir;
            Path[] regionFiles = Files.list(containingFolder).toArray(Path[]::new);
            boolean[] oversized = new boolean[32 * 32];
            RegionFileVersion[] oversizedCompressionTypes = new RegionFileVersion[32 * 32];

            if (regionFiles != null) {
                int lowerXBound = ourLowerLeftPosition.x; // inclusive
                int lowerZBound = ourLowerLeftPosition.z; // inclusive
                int upperXBound = lowerXBound + 32 - 1; // inclusive
                int upperZBound = lowerZBound + 32 - 1; // inclusive

                // read mojang oversized data
                for (Path regionFile : regionFiles) {
                    ChunkPos oversizedCoords = getOversizedChunkPair(regionFile);
                    if (oversizedCoords == null) {
                        continue;
                    }

                    if ((oversizedCoords.x < lowerXBound || oversizedCoords.x > upperXBound) || (oversizedCoords.z < lowerZBound || oversizedCoords.z > upperZBound)) {
                        continue; // not in our regionfile
                    }

                    // ensure oversized data is valid & is newer than data in the regionfile

                    int location = (oversizedCoords.x & 31) | ((oversizedCoords.z & 31) << 5);

                    byte[] chunkData;
                    try {
                        chunkData = Files.readAllBytes(regionFile);
                    } catch (Exception ex) {
                        LOGGER.error("Failed to read oversized chunk data in file " + regionFile.toAbsolutePath() + ", data will be lost", ex);
                        continue;
                    }

                    net.minecraft.nbt.CompoundTag compound = null;

                    // We do not know the compression type, as it's stored in the regionfile. So we need to try all of them
                    RegionFileVersion compression = null;
                    for (RegionFileVersion compressionType : RegionFileVersion.VERSIONS.values()) {
                        try {
                            DataInputStream in = new DataInputStream(compressionType.wrap(new ByteArrayInputStream(chunkData))); // typical java
                            compound = net.minecraft.nbt.NbtIo.read((java.io.DataInput)in);
                            compression = compressionType;
                            break; // reaches here iff readNBT does not throw
                        } catch (Exception ex) {
                            continue;
                        }
                    }

                    if (compound == null) {
                        LOGGER.error("Failed to read oversized chunk data in file " + regionFile.toAbsolutePath() + ", it's corrupt. Its data will be lost");
                        continue;
                    }

                    if (!SerializableChunkData.getChunkCoordinate(compound).equals(oversizedCoords)) {
                        LOGGER.error("Can't use oversized chunk stored in " + regionFile.toAbsolutePath() + ", got absolute chunkpos: " + SerializableChunkData.getChunkCoordinate(compound) + ", expected " + oversizedCoords);
                        continue;
                    }

                    if (compounds[location] == null || SerializableChunkData.getLastWorldSaveTime(compound) > SerializableChunkData.getLastWorldSaveTime(compounds[location])) {
                        oversized[location] = true;
                        oversizedCompressionTypes[location] = compression;
                    }
                }
            }

            // now we need to calculate a new offset header

            int[] calculatedOffsets = new int[32 * 32];
            RegionBitmap newSectorAllocations = new RegionBitmap();
            newSectorAllocations.force(0, 2); // make space for header

            // allocate sectors for normal chunks

            for (int chunkX = 0; chunkX < 32; ++chunkX) {
                for (int chunkZ = 0; chunkZ < 32; ++chunkZ) {
                    int location = chunkX | (chunkZ << 5);

                    if (oversized[location]) {
                        continue;
                    }

                    int rawLength = rawLengths[location]; // bytes
                    int sectorOffset = sectorOffsets[location]; // sectors
                    int sectorLength = (int)roundToSectors(rawLength);

                    if (newSectorAllocations.tryAllocate(sectorOffset, sectorLength)) {
                        calculatedOffsets[location] = sectorOffset << 8 | (sectorLength > 255 ? 255 : sectorLength); // support forge style oversized
                    } else {
                        LOGGER.error("Failed to allocate space for local chunk (overlapping data??) at (" + chunkX + "," + chunkZ + ") in regionfile " + this.path.toAbsolutePath() + ", chunk will be regenerated");
                    }
                }
            }

            // allocate sectors for oversized chunks

            for (int chunkX = 0; chunkX < 32; ++chunkX) {
                for (int chunkZ = 0; chunkZ < 32; ++chunkZ) {
                    int location = chunkX | (chunkZ << 5);

                    if (!oversized[location]) {
                        continue;
                    }

                    int sectorOffset = newSectorAllocations.allocate(1);
                    int sectorLength = 1;

                    try {
                        this.file.write(this.createExternalStub(oversizedCompressionTypes[location]), sectorOffset * 4096);
                        // only allocate in the new offsets if the write succeeds
                        calculatedOffsets[location] = sectorOffset << 8 | (sectorLength > 255 ? 255 : sectorLength); // support forge style oversized
                    } catch (IOException ex) {
                        newSectorAllocations.free(sectorOffset, sectorLength);
                        LOGGER.error("Failed to write new oversized chunk data holder, local chunk at (" + chunkX + "," + chunkZ + ") in regionfile " + this.path.toAbsolutePath() + " will be regenerated");
                    }
                }
            }

            // rewrite aikar oversized data

            this.oversizedCount = 0;
            for (int chunkX = 0; chunkX < 32; ++chunkX) {
                for (int chunkZ = 0; chunkZ < 32; ++chunkZ) {
                    int location = chunkX | (chunkZ << 5);
                    int isAikarOversized = hasAikarOversized[location] ? 1 : 0;

                    this.oversizedCount += isAikarOversized;
                    this.oversized[location] = (byte)isAikarOversized;
                }
            }

            if (this.oversizedCount > 0) {
                try {
                    this.writeOversizedMeta();
                } catch (Exception ex) {
                    LOGGER.error("Failed to write aikar oversized chunk meta, all aikar style oversized chunk data will be lost for regionfile " + this.path.toAbsolutePath(), ex);
                    Files.deleteIfExists(this.getOversizedMetaFile());
                }
            } else {
                Files.deleteIfExists(this.getOversizedMetaFile());
            }

            this.usedSectors.copyFrom(newSectorAllocations);

            // before we overwrite the old sectors, print a summary of the chunks that got changed.

            LOGGER.info("Starting summary of changes for regionfile " + this.path.toAbsolutePath());

            for (int chunkX = 0; chunkX < 32; ++chunkX) {
                for (int chunkZ = 0; chunkZ < 32; ++chunkZ) {
                    int location = chunkX | (chunkZ << 5);

                    int oldOffset = this.offsets.get(location);
                    int newOffset = calculatedOffsets[location];

                    if (oldOffset == newOffset) {
                        continue;
                    }

                    this.offsets.put(location, newOffset); // overwrite incorrect offset

                    if (oldOffset == 0) {
                        // found lost data
                        LOGGER.info("Found missing data for local chunk (" + chunkX + "," + chunkZ + ") in regionfile " + this.path.toAbsolutePath());
                    } else if (newOffset == 0) {
                        LOGGER.warn("Data for local chunk (" + chunkX + "," + chunkZ + ") could not be recovered in regionfile " + this.path.toAbsolutePath() + ", it will be regenerated");
                    } else {
                        LOGGER.info("Local chunk (" + chunkX + "," + chunkZ + ") changed to point to newer data or correct chunk in regionfile " + this.path.toAbsolutePath());
                    }
                }
            }

            LOGGER.info("End of change summary for regionfile " + this.path.toAbsolutePath());

            // simply destroy the timestamp header, it's not used

            for (int i = 0; i < 32 * 32; ++i) {
                this.timestamps.put(i, calculatedOffsets[i] != 0 ? RegionFile.getTimestamp() : 0); // write a valid timestamp for valid chunks, I do not want to find out whatever dumb program actually checks this
            }

            // write new header
            try {
                this.flush();
                this.file.force(true); // try to ensure it goes through...
                LOGGER.info("Successfully wrote new header to disk for regionfile " + this.path.toAbsolutePath());
            } catch (IOException ex) {
                LOGGER.error("Failed to write new header to disk for regionfile " + this.path.toAbsolutePath(), ex);
            }
        }

        return true;
    }

    final boolean canRecalcHeader; // final forces compile fail on new constructor
    // Paper end - Attempt to recalculate regionfile header if it is corrupt

    // Paper start - rewrite chunk system
    @Override
    public final ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO.RegionDataController.WriteData moonrise$startWrite(final net.minecraft.nbt.CompoundTag data, final ChunkPos pos) throws IOException {
        final RegionFile.ChunkBuffer buffer = ((RegionFile)(Object)this).new ChunkBuffer(pos);
        ((ca.spottedleaf.moonrise.patches.chunk_system.storage.ChunkSystemChunkBuffer)buffer).moonrise$setWriteOnClose(false);

        final DataOutputStream out = new DataOutputStream(this.version.wrap(buffer));

        return new ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO.RegionDataController.WriteData(
            data, ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO.RegionDataController.WriteData.WriteResult.WRITE,
            out, ((ca.spottedleaf.moonrise.patches.chunk_system.storage.ChunkSystemChunkBuffer)buffer)::moonrise$write
        );
    }
    // Paper end - rewrite chunk system

    public RegionFile(RegionStorageInfo info, Path path, Path externalFileDir, boolean sync) throws IOException {
        this(info, path, externalFileDir, RegionFileVersion.getCompressionFormat(), sync); // Paper - Configurable region compression format
    }

    public RegionFile(RegionStorageInfo info, Path path, Path externalFileDir, RegionFileVersion version, boolean sync) throws IOException {
        this.info = info;
        this.path = path;
        this.version = version;
        this.initOversizedState(); // Paper
        if (!Files.isDirectory(externalFileDir)) {
            throw new IllegalArgumentException("Expected directory, got " + externalFileDir.toAbsolutePath());
        } else {
            this.externalFileDir = externalFileDir;
            this.canRecalcHeader = RegionFileStorage.isChunkDataFolder(this.externalFileDir); // Paper - add can recalc flag
            this.offsets = this.header.asIntBuffer();
            this.offsets.limit(1024);
            this.header.position(4096);
            this.timestamps = this.header.asIntBuffer();
            if (sync) {
                this.file = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.DSYNC);
            } else {
                this.file = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
            }

            this.usedSectors.force(0, 2);
            this.header.position(0);
            int i = this.file.read(this.header, 0L);
            if (i != -1) {
                if (i != 8192) {
                    LOGGER.warn("Region file {} has truncated header: {}", path, i);
                }

                long size = Files.size(path);

                boolean needsHeaderRecalc = false; // Paper - recalculate header on header corruption
                boolean hasBackedUp = false; // Paper - recalculate header on header corruption
                for (int i1 = 0; i1 < 1024; i1++) { final int headerLocation = i1; // Paper - we expect this to be the header location
                    int i2 = this.offsets.get(i1);
                    if (i2 != 0) {
                        final int sectorNumber = getSectorNumber(i2); // Paper - we expect this to be offset in file in sectors
                        int numSectors = getNumSectors(i2); // Paper - diff on change, we expect this to be sector length of region - watch out for reassignments
                        // Spigot start
                        if (numSectors == 255) {
                            // We're maxed out, so we need to read the proper length from the section
                            ByteBuffer realLen = ByteBuffer.allocate(4);
                            this.file.read(realLen, sectorNumber * 4096);
                            numSectors = (realLen.getInt(0) + 4) / 4096 + 1;
                        }
                        // Spigot end
                        if (sectorNumber < 2) {
                            LOGGER.warn("Region file {} has invalid sector at index: {}; sector {} overlaps with header", path, i1, sectorNumber);
                            //this.offsets.put(i1, 0); // Paper - we catch this, but need it in the header for the summary change
                        } else if (numSectors == 0) {
                            LOGGER.warn("Region file {} has an invalid sector at index: {}; size has to be > 0", path, i1);
                            //this.offsets.put(i1, 0); // Paper - we catch this, but need it in the header for the summary change
                        } else if (sectorNumber * 4096L > size) {
                            LOGGER.warn("Region file {} has an invalid sector at index: {}; sector {} is out of bounds", path, i1, sectorNumber);
                            //this.offsets.put(i1, 0); // Paper - we catch this, but need it in the header for the summary change
                        } else {
                            //this.usedSectors.force(sectorNumber, numSectors); // Paper - move this down so we can check if it fails to allocate
                        }
                        // Paper start - recalculate header on header corruption
                        if (sectorNumber < 2 || numSectors <= 0 || ((long)sectorNumber * 4096L) > size) {
                            if (canRecalcHeader) {
                                LOGGER.error("Detected invalid header for regionfile " + this.path.toAbsolutePath() + "! Recalculating header...");
                                needsHeaderRecalc = true;
                                break;
                            } else {
                                // location = chunkX | (chunkZ << 5);
                                LOGGER.error("Detected invalid header for regionfile " + this.path.toAbsolutePath() +
                                        "! Cannot recalculate, removing local chunk (" + (headerLocation & 31) + "," + (headerLocation >>> 5) + ") from header");
                                if (!hasBackedUp) {
                                    hasBackedUp = true;
                                    this.backupRegionFile();
                                }
                                this.timestamps.put(headerLocation, 0); // be consistent, delete the timestamp too
                                this.offsets.put(headerLocation, 0); // delete the entry from header
                                continue;
                            }
                        }
                        boolean failedToAllocate = !this.usedSectors.tryAllocate(sectorNumber, numSectors);
                        if (failedToAllocate) {
                            LOGGER.error("Overlapping allocation by local chunk (" + (headerLocation & 31) + "," + (headerLocation >>> 5) + ") in regionfile " + this.path.toAbsolutePath());
                        }
                        if (failedToAllocate & !canRecalcHeader) {
                            // location = chunkX | (chunkZ << 5);
                            LOGGER.error("Detected invalid header for regionfile " + this.path.toAbsolutePath() +
                                    "! Cannot recalculate, removing local chunk (" + (headerLocation & 31) + "," + (headerLocation >>> 5) + ") from header");
                            if (!hasBackedUp) {
                                hasBackedUp = true;
                                this.backupRegionFile();
                            }
                            this.timestamps.put(headerLocation, 0); // be consistent, delete the timestamp too
                            this.offsets.put(headerLocation, 0); // delete the entry from header
                            continue;
                        }
                        needsHeaderRecalc |= failedToAllocate;
                        // Paper end - recalculate header on header corruption
                    }
                }
                // Paper start - recalculate header on header corruption
                // we move the recalc here so comparison to old header is correct when logging to console
                if (needsHeaderRecalc) { // true if header gave us overlapping allocations or had other issues
                    LOGGER.error("Recalculating regionfile " + this.path.toAbsolutePath() + ", header gave erroneous offsets & locations");
                    this.recalculateHeader();
                }
                // Paper end
            }
        }
    }

    public Path getPath() {
        return this.path;
    }

    private Path getExternalChunkPath(ChunkPos chunkPos) {
        String string = "c." + chunkPos.x + "." + chunkPos.z + ".mcc"; // Paper - diff on change
        return this.externalFileDir.resolve(string);
    }

    // Paper start
    private static @Nullable ChunkPos getOversizedChunkPair(Path file) {
        String fileName = file.getFileName().toString();

        if (!fileName.startsWith("c.") || !fileName.endsWith(".mcc")) {
            return null;
        }

        String[] split = fileName.split("\\.");

        if (split.length != 4) {
            return null;
        }

        try {
            int x = Integer.parseInt(split[1]);
            int z = Integer.parseInt(split[2]);

            return new ChunkPos(x, z);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
    // Paper end

    public synchronized @Nullable DataInputStream getChunkDataInputStream(ChunkPos chunkPos) throws IOException {
        int offset = this.getOffset(chunkPos);
        if (offset == 0) {
            return null;
        } else {
            int sectorNumber = getSectorNumber(offset);
            int numSectors = getNumSectors(offset);
            // Spigot start
            if (numSectors == 255) {
                ByteBuffer realLen = ByteBuffer.allocate(4);
                this.file.read(realLen, sectorNumber * 4096);
                numSectors = (realLen.getInt(0) + 4) / 4096 + 1;
            }
            // Spigot end
            int i = numSectors * 4096;
            ByteBuffer byteBuffer = ByteBuffer.allocate(i);
            this.file.read(byteBuffer, sectorNumber * 4096);
            byteBuffer.flip();
            if (byteBuffer.remaining() < 5) {
                LOGGER.error("Chunk {} header is truncated: expected {} but read {}", chunkPos, i, byteBuffer.remaining());
                // Paper start - recalculate header on regionfile corruption
                if (this.canRecalcHeader && this.recalculateHeader()) {
                    return this.getChunkDataInputStream(chunkPos);
                }
                // Paper end - recalculate header on regionfile corruption
                return null;
            } else {
                int _int = byteBuffer.getInt();
                byte b = byteBuffer.get();
                if (_int == 0) {
                    LOGGER.warn("Chunk {} is allocated, but stream is missing", chunkPos);
                    // Paper start - recalculate header on regionfile corruption
                    if (this.canRecalcHeader && this.recalculateHeader()) {
                        return this.getChunkDataInputStream(chunkPos);
                    }
                    // Paper end - recalculate header on regionfile corruption
                    return null;
                } else {
                    int i1 = _int - 1;
                    if (isExternalStreamChunk(b)) {
                        if (i1 != 0) {
                            LOGGER.warn("Chunk has both internal and external streams");
                            // Paper start - recalculate header on regionfile corruption
                            if (this.canRecalcHeader && this.recalculateHeader()) {
                                return this.getChunkDataInputStream(chunkPos);
                            }
                            // Paper end - recalculate header on regionfile corruption
                        }

                        // Paper start - recalculate header on regionfile corruption
                        final DataInputStream ret = this.createExternalChunkInputStream(chunkPos, getExternalChunkVersion(b));
                        if (ret == null && this.canRecalcHeader && this.recalculateHeader()) {
                            return this.getChunkDataInputStream(chunkPos);
                        }
                        return ret;
                        // Paper end - recalculate header on regionfile corruption
                    } else if (i1 > byteBuffer.remaining()) {
                        LOGGER.error("Chunk {} stream is truncated: expected {} but read {}", chunkPos, i1, byteBuffer.remaining());
                        // Paper start - recalculate header on regionfile corruption
                        if (this.canRecalcHeader && this.recalculateHeader()) {
                            return this.getChunkDataInputStream(chunkPos);
                        }
                        // Paper end - recalculate header on regionfile corruption
                        return null;
                    } else if (i1 < 0) {
                        LOGGER.error("Declared size {} of chunk {} is negative", _int, chunkPos);
                        // Paper start - recalculate header on regionfile corruption
                        if (this.canRecalcHeader && this.recalculateHeader()) {
                            return this.getChunkDataInputStream(chunkPos);
                        }
                        // Paper end - recalculate header on regionfile corruption
                        return null;
                    } else {
                        JvmProfiler.INSTANCE.onRegionFileRead(this.info, chunkPos, this.version, i1);
                        // Paper start - recalculate header on regionfile corruption
                        final DataInputStream ret = this.createChunkInputStream(chunkPos, b, createStream(byteBuffer, i1));
                        if (ret == null && this.canRecalcHeader && this.recalculateHeader()) {
                            return this.getChunkDataInputStream(chunkPos);
                        }
                        return ret;
                        // Paper end - recalculate header on regionfile corruption
                    }
                }
            }
        }
    }

    private static int getTimestamp() {
        return (int)(Util.getEpochMillis() / 1000L);
    }

    private static boolean isExternalStreamChunk(byte versionByte) {
        return (versionByte & 128) != 0;
    }

    private static byte getExternalChunkVersion(byte versionByte) {
        return (byte)(versionByte & -129);
    }

    private @Nullable DataInputStream createChunkInputStream(ChunkPos chunkPos, byte versionByte, InputStream inputStream) throws IOException {
        RegionFileVersion regionFileVersion = RegionFileVersion.fromId(versionByte);
        if (regionFileVersion == RegionFileVersion.VERSION_CUSTOM) {
            String utf = new DataInputStream(inputStream).readUTF();
            Identifier identifier = Identifier.tryParse(utf);
            if (identifier != null) {
                LOGGER.error("Unrecognized custom compression {}", identifier);
                return null;
            } else {
                LOGGER.error("Invalid custom compression id {}", utf);
                return null;
            }
        } else if (regionFileVersion == null) {
            LOGGER.error("Chunk {} has invalid chunk stream version {}", chunkPos, versionByte);
            return null;
        } else {
            return new DataInputStream(regionFileVersion.wrap(inputStream));
        }
    }

    @Nullable
    private DataInputStream createExternalChunkInputStream(ChunkPos chunkPos, byte versionByte) throws IOException {
        // Paper start - rewrite chunk system
        final DataInputStream is = this.createExternalChunkInputStream0(chunkPos, versionByte);
        if (is == null) {
            return is;
        }
        return new ca.spottedleaf.moonrise.patches.chunk_system.util.stream.ExternalChunkStreamMarker(is);
    }
    private @Nullable DataInputStream createExternalChunkInputStream0(ChunkPos chunkPos, byte versionByte) throws IOException {
        // Paper end - rewrite chunk system
        Path externalChunkPath = this.getExternalChunkPath(chunkPos);
        if (!Files.isRegularFile(externalChunkPath)) {
            LOGGER.error("External chunk path {} is not file", externalChunkPath);
            return null;
        } else {
            return this.createChunkInputStream(chunkPos, versionByte, Files.newInputStream(externalChunkPath));
        }
    }

    private static ByteArrayInputStream createStream(ByteBuffer sourceBuffer, int length) {
        return new ByteArrayInputStream(sourceBuffer.array(), sourceBuffer.position(), length);
    }

    private int packSectorOffset(int sectorOffset, int sectorCount) {
        return sectorOffset << 8 | sectorCount;
    }

    private static int getNumSectors(int packedSectorOffset) {
        return packedSectorOffset & 0xFF;
    }

    private static int getSectorNumber(int packedSectorOffset) {
        return packedSectorOffset >> 8 & 16777215;
    }

    private static int sizeToSectors(int size) {
        return (size + 4096 - 1) / 4096;
    }

    public boolean doesChunkExist(ChunkPos chunkPos) {
        int offset = this.getOffset(chunkPos);
        if (offset == 0) {
            return false;
        } else {
            int sectorNumber = getSectorNumber(offset);
            int numSectors = getNumSectors(offset);
            ByteBuffer byteBuffer = ByteBuffer.allocate(5);

            try {
                this.file.read(byteBuffer, sectorNumber * 4096);
                byteBuffer.flip();
                if (byteBuffer.remaining() != 5) {
                    return false;
                } else {
                    int _int = byteBuffer.getInt();
                    byte b = byteBuffer.get();
                    if (isExternalStreamChunk(b)) {
                        if (!RegionFileVersion.isValidVersion(getExternalChunkVersion(b))) {
                            return false;
                        }

                        if (!Files.isRegularFile(this.getExternalChunkPath(chunkPos))) {
                            return false;
                        }
                    } else {
                        if (!RegionFileVersion.isValidVersion(b)) {
                            return false;
                        }

                        if (_int == 0) {
                            return false;
                        }

                        int i = _int - 1;
                        if (i < 0 || i > 4096 * numSectors) {
                            return false;
                        }
                    }

                    return true;
                }
            } catch (IOException var9) {
                com.destroystokyo.paper.exception.ServerInternalException.reportInternalException(var9); // Paper - ServerExceptionEvent
                return false;
            }
        }
    }

    public DataOutputStream getChunkDataOutputStream(ChunkPos chunkPos) throws IOException {
        return new DataOutputStream(this.version.wrap(new RegionFile.ChunkBuffer(chunkPos)));
    }

    public void flush() throws IOException {
        this.file.force(true);
    }

    public void clear(ChunkPos chunkPos) throws IOException {
        int offsetIndex = getOffsetIndex(chunkPos);
        int i = this.offsets.get(offsetIndex);
        if (i != 0) {
            this.offsets.put(offsetIndex, 0);
            this.timestamps.put(offsetIndex, getTimestamp());
            this.writeHeader();
            Files.deleteIfExists(this.getExternalChunkPath(chunkPos));
            this.usedSectors.free(getSectorNumber(i), getNumSectors(i));
        }
    }

    public synchronized void write(ChunkPos chunkPos, ByteBuffer chunkData) throws IOException { // Luminol - Configurable region file format
        int offsetIndex = getOffsetIndex(chunkPos);
        int i = this.offsets.get(offsetIndex);
        int sectorNumber = getSectorNumber(i);
        int numSectors = getNumSectors(i);
        int i1 = chunkData.remaining();
        int i2 = sizeToSectors(i1);
        int i3;
        RegionFile.CommitOp commitOp;
        if (i2 >= 256) {
            Path externalChunkPath = this.getExternalChunkPath(chunkPos);
            LOGGER.warn("Saving oversized chunk {} ({} bytes} to external file {}", chunkPos, i1, externalChunkPath);
            i2 = 1;
            i3 = this.usedSectors.allocate(i2);
            commitOp = this.writeToExternalFile(externalChunkPath, chunkData);
            ByteBuffer byteBuffer = this.createExternalStub();
            this.file.write(byteBuffer, i3 * 4096);
        } else {
            i3 = this.usedSectors.allocate(i2);
            commitOp = () -> Files.deleteIfExists(this.getExternalChunkPath(chunkPos));
            this.file.write(chunkData, i3 * 4096);
        }

        this.offsets.put(offsetIndex, this.packSectorOffset(i3, i2));
        this.timestamps.put(offsetIndex, getTimestamp());
        this.writeHeader();
        commitOp.run();
        if (sectorNumber != 0) {
            this.usedSectors.free(sectorNumber, numSectors);
        }
    }

    private ByteBuffer createExternalStub() {
        // Paper start - add version param
        return this.createExternalStub(this.version);
    }
    private ByteBuffer createExternalStub(RegionFileVersion version) {
        // Paper end - add version param
        ByteBuffer byteBuffer = ByteBuffer.allocate(5);
        byteBuffer.putInt(1);
        byteBuffer.put((byte)(version.getId() | 128));
        byteBuffer.flip();
        return byteBuffer;
    }

    private RegionFile.CommitOp writeToExternalFile(Path externalChunkFile, ByteBuffer chunkData) throws IOException {
        Path path = Files.createTempFile(this.externalFileDir, "tmp", null);

        try (FileChannel fileChannel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            chunkData.position(5);
            fileChannel.write(chunkData);
            // Paper start - ServerExceptionEvent
        } catch (Throwable throwable) {
            com.destroystokyo.paper.exception.ServerInternalException.reportInternalException(throwable);
            throw throwable;
            // Paper end - ServerExceptionEvent
        }

        return () -> Files.move(path, externalChunkFile, StandardCopyOption.REPLACE_EXISTING);
    }

    private void writeHeader() throws IOException {
        this.header.position(0);
        this.file.write(this.header, 0L);
    }

    private int getOffset(ChunkPos chunkPos) {
        return this.offsets.get(getOffsetIndex(chunkPos));
    }

    public boolean hasChunk(ChunkPos chunkPos) {
        return this.getOffset(chunkPos) != 0;
    }

    private static int getOffsetIndex(ChunkPos chunkPos) {
        return chunkPos.getRegionLocalX() + chunkPos.getRegionLocalZ() * 32;
    }

    @Override
    public void close() throws IOException {
        try {
            this.padToFullSector();
        } finally {
            try {
                this.file.force(true);
            } finally {
                this.file.close();
            }
        }
    }

    private void padToFullSector() throws IOException {
        int i = (int)this.file.size();
        int i1 = sizeToSectors(i) * 4096;
        if (i != i1) {
            ByteBuffer byteBuffer = PADDING_BUFFER.duplicate();
            byteBuffer.position(0);
            this.file.write(byteBuffer, i1 - 1);
        }
    }

    class ChunkBuffer extends ByteArrayOutputStream implements ca.spottedleaf.moonrise.patches.chunk_system.storage.ChunkSystemChunkBuffer { // Paper - rewrite chunk system
        private final ChunkPos pos;

        // Paper start - rewrite chunk system
        private boolean writeOnClose = true;

        @Override
        public final boolean moonrise$getWriteOnClose() {
            return this.writeOnClose;
        }

        @Override
        public final void moonrise$setWriteOnClose(final boolean value) {
            this.writeOnClose = value;
        }

        @Override
        public final void moonrise$write(final abomination.IRegionFile regionFile) throws IOException { // Luminol - Configurable region file format
            regionFile.write(this.pos, ByteBuffer.wrap(this.buf, 0, this.count));
        }
        // Paper end - rewrite chunk system

        public ChunkBuffer(final ChunkPos pos) {
            super(8096);
            super.write(0);
            super.write(0);
            super.write(0);
            super.write(0);
            super.write(RegionFile.this.version.getId());
            this.pos = pos;
        }

        // Paper start - don't write garbage data to disk if writing serialization fails
        @Override
        public void write(final int b) {
            if (this.count > MAX_CHUNK_SIZE) {
                throw new RegionFileStorage.RegionFileSizeException("Region file too large: " + this.count);
            }
            super.write(b);
        }

        @Override
        public void write(final byte[] b, final int off, final int len) {
            if (this.count + len > MAX_CHUNK_SIZE) {
                throw new RegionFileStorage.RegionFileSizeException("Region file too large: " + (this.count + len));
            }
            super.write(b, off, len);
        }
        // Paper end - don't write garbage data to disk if writing serialization fails

        @Override
        public void close() throws IOException {
            ByteBuffer byteBuffer = ByteBuffer.wrap(this.buf, 0, this.count);
            int i = this.count - 5 + 1;
            JvmProfiler.INSTANCE.onRegionFileWrite(RegionFile.this.info, this.pos, RegionFile.this.version, i);
            byteBuffer.putInt(0, i);
            if (this.writeOnClose) { RegionFile.this.write(this.pos, byteBuffer); } // Paper - rewrite chunk system
        }
    }

    interface CommitOp {
        void run() throws IOException;
    }

    // Paper start
    private final byte[] oversized = new byte[1024];
    private int oversizedCount;

    private synchronized void initOversizedState() throws IOException {
        Path metaFile = getOversizedMetaFile();
        if (Files.exists(metaFile)) {
            final byte[] read = java.nio.file.Files.readAllBytes(metaFile);
            System.arraycopy(read, 0, oversized, 0, oversized.length);
            for (byte temp : oversized) {
                oversizedCount += temp;
            }
        }
    }

    private static int getChunkIndex(int x, int z) {
        return (x & 31) + (z & 31) * 32;
    }

    public synchronized boolean isOversized(int x, int z) { // Luminol - Configurable region file format
        return this.oversized[getChunkIndex(x, z)] == 1;
    }

    public synchronized void setOversized(int x, int z, boolean oversized) throws IOException { // Luminol - Configurable region file format
        final int offset = getChunkIndex(x, z);
        boolean previous = this.oversized[offset] == 1;
        this.oversized[offset] = (byte) (oversized ? 1 : 0);
        if (!previous && oversized) {
            oversizedCount++;
        } else if (!oversized && previous) {
            oversizedCount--;
        }
        if (previous && !oversized) {
            Path oversizedFile = getOversizedFile(x, z);
            if (Files.exists(oversizedFile)) {
                Files.delete(oversizedFile);
            }
        }
        if (oversizedCount > 0) {
            if (previous != oversized) {
                writeOversizedMeta();
            }
        } else if (previous) {
            Path oversizedMetaFile = getOversizedMetaFile();
            if (Files.exists(oversizedMetaFile)) {
                Files.delete(oversizedMetaFile);
            }
        }
    }

    private void writeOversizedMeta() throws IOException {
        java.nio.file.Files.write(getOversizedMetaFile(), oversized);
    }

    private Path getOversizedMetaFile() {
        return this.path.getParent().resolve(this.path.getFileName().toString().replaceAll("\\.mca$", "") + ".oversized.nbt");
    }

    private Path getOversizedFile(int x, int z) {
        return this.path.getParent().resolve(this.path.getFileName().toString().replaceAll("\\.mca$", "") + "_oversized_" + x + "_" + z + ".nbt");
    }

    public synchronized net.minecraft.nbt.CompoundTag getOversizedData(int x, int z) throws IOException { // Luminol - Configurable region file format
        Path file = getOversizedFile(x, z);
        try (DataInputStream out = new DataInputStream(new java.io.BufferedInputStream(new java.util.zip.InflaterInputStream(Files.newInputStream(file))))) {
            return net.minecraft.nbt.NbtIo.read((java.io.DataInput) out);
        }

    }
    // Paper end
}
