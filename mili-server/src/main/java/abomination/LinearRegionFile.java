package abomination;

import ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO;
import com.github.luben.zstd.ZstdInputStream;
import com.github.luben.zstd.ZstdOutputStream;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FastDecompressor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFileVersion;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.openhft.hashing.LongHashFunction;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class LinearRegionFile implements IRegionFile {
    private static final long SUPERBLOCK = 0xc3ff13183cca9d9aL;
    private static final byte VERSION = 4;
    private static final Logger LOGGER = org.mojang.logging.LogUtils.getLogger();

    private static final int CHUNK_COUNT = 1024;
    private static final int DEFAULT_GRID_SIZE = 8;
    private static final int MAX_COMPRESSION_LEVEL = 22;

    private final Path regionFile;
    private final int compressionLevel;
    private final int gridSize;
    private final int bucketSize;

    private final LZ4Compressor compressor;
    private final LZ4FastDecompressor decompressor;

    private volatile boolean initialized = false;
    private volatile boolean closed = false;

    private final ChunkEntry[] chunks = new ChunkEntry[CHUNK_COUNT];
    private final Bucket[] buckets;
    private final ReentrantLock[] bucketLocks;
    private final AtomicBoolean dirty = new AtomicBoolean(false);

    private final AtomicInteger activeReaders = new AtomicInteger(0);
    private final ReentrantLock writeLock = new ReentrantLock(true);

    private static class ChunkEntry {
        volatile byte[] compressedData;
        volatile int uncompressedSize;
        volatile long timestamp;
        volatile boolean loaded;

        synchronized void set(byte[] data, int size, long ts) {
            this.compressedData = data;
            this.uncompressedSize = size;
            this.timestamp = ts;
            this.loaded = true;
        }

        synchronized byte[] get() {
            return this.compressedData;
        }

        synchronized void clear() {
            this.compressedData = null;
            this.uncompressedSize = 0;
            this.loaded = false;
        }
    }

    private static class Bucket {
        volatile byte[] compressedBucket;
        volatile boolean loaded;
        final Object lock = new Object();
    }

    public LinearRegionFile(RegionStorageInfo storageKey, Path directory, Path path, boolean dsync, int compressionLevel) throws IOException {
        this(storageKey, path, directory, RegionFileVersion.getCompressionFormat(), dsync, compressionLevel);
    }

    public LinearRegionFile(RegionStorageInfo storageKey, Path path, Path directory, RegionFileVersion compressionFormat, boolean dsync, int compressionLevel) throws IOException {
        this.regionFile = path;
        this.compressionLevel = Math.min(Math.max(1, compressionLevel), MAX_COMPRESSION_LEVEL);
        this.gridSize = DEFAULT_GRID_SIZE;
        this.bucketSize = 32 / gridSize;

        this.compressor = LZ4Factory.fastestInstance().fastCompressor();
        this.decompressor = LZ4Factory.fastestInstance().fastDecompressor();

        int bucketCount = gridSize * gridSize;
        this.buckets = new Bucket[bucketCount];
        this.bucketLocks = new ReentrantLock[bucketCount];

        for (int i = 0; i < bucketCount; i++) {
            this.buckets[i] = new Bucket();
            this.bucketLocks[i] = new ReentrantLock(true);
        }

        for (int i = 0; i < CHUNK_COUNT; i++) {
            this.chunks[i] = new ChunkEntry();
        }

        initIfNeeded();
    }

    private void initIfNeeded() throws IOException {
        if (initialized) return;
        writeLock.lock();
        try {
            if (initialized) return;
            if (Files.exists(regionFile)) {
                loadFromFile();
            }
            initialized = true;
        } finally {
            writeLock.unlock();
        }
    }

    private void loadFromFile() throws IOException {
        try {
            byte[] fileContent = Files.readAllBytes(regionFile);
            ByteBuffer buffer = ByteBuffer.wrap(fileContent);

            long superBlock = buffer.getLong();
            if (superBlock != SUPERBLOCK) {
                throw new IOException("Invalid superblock: " + superBlock);
            }

            byte version = buffer.get();
            if (version >= 1 && version <= 3) {
                parseLegacyFormat(buffer, version);
            } else if (version == 4) {
                parseV4Format(buffer);
            } else {
                throw new IOException("Unsupported version: " + version);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load region file: {}", regionFile, e);
            throw e;
        }
    }

    private void parseLegacyFormat(ByteBuffer buffer, byte version) throws IOException {
        if (version == 1 || version == 2) {
            parseV1Format(buffer);
        } else {
            parseV2Format(buffer);
        }
    }

    private void parseV1Format(ByteBuffer buffer) throws IOException {
        buffer.position(buffer.position() + 11);
        int dataCount = buffer.getInt();
        buffer.position(buffer.position() + 8);

        byte[] rawCompressed = new byte[dataCount];
        buffer.get(rawCompressed);

        try (ByteArrayInputStream bais = new ByteArrayInputStream(rawCompressed);
             ZstdInputStream zis = new ZstdInputStream(bais)) {
            ByteBuffer decompressed = ByteBuffer.wrap(zis.readAllBytes());

            int[] starts = new int[CHUNK_COUNT];
            for (int i = 0; i < CHUNK_COUNT; i++) {
                starts[i] = decompressed.getInt();
                decompressed.getInt();
            }

            for (int i = 0; i < CHUNK_COUNT; i++) {
                if (starts[i] > 0) {
                    byte[] chunkData = new byte[starts[i]];
                    decompressed.get(chunkData);
                    compressAndStore(i, chunkData);
                }
            }
        }
    }

    private void parseV2Format(ByteBuffer buffer) throws IOException {
        buffer.getLong();
        int fileGridSize = buffer.get();
        if (fileGridSize != gridSize) {
            LOGGER.warn("Grid size mismatch: file={}, config={}", fileGridSize, gridSize);
        }

        buffer.getInt();
        buffer.getInt();

        int bitmapLength = (CHUNK_COUNT + 7) / 8;
        byte[] bitmap = new byte[bitmapLength];
        buffer.get(bitmap);

        while (buffer.hasRemaining()) {
            byte nameLen = buffer.get();
            if (nameLen == 0) break;
            byte[] nameBytes = new byte[nameLen];
            buffer.get(nameBytes);
            buffer.getInt();
        }

        int totalBuckets = gridSize * gridSize;
        for (int i = 0; i < totalBuckets; i++) {
            int bucketSize = buffer.getInt();
            buffer.get();
            buffer.getLong();

            if (bucketSize > 0 && buckets[i] != null) {
                byte[] bucketData = new byte[bucketSize];
                buffer.get(bucketData);
                buckets[i].compressedBucket = bucketData;
                buckets[i].loaded = true;
            }
        }
    }

    private void parseV4Format(ByteBuffer buffer) throws IOException {
        buffer.getLong();
        int fileGridSize = buffer.get();
        buffer.getInt();
        buffer.getInt();

        for (int i = 0; i < CHUNK_COUNT; i++) {
            int offset = buffer.getInt();
            int size = buffer.getInt();
            long timestamp = buffer.getLong();

            if (size > 0 && offset > 0) {
                int currentPos = buffer.position();
                buffer.position(offset);
                byte[] data = new byte[size];
                buffer.get(data);
                buffer.position(currentPos);

                chunks[i].set(data, size, timestamp);
            }
        }

        long footerSuperBlock = buffer.getLong();
        if (footerSuperBlock != SUPERBLOCK) {
            throw new IOException("Invalid footer superblock");
        }
    }

    private void compressAndStore(int index, byte[] uncompressedData) {
        int maxCompressedLen = compressor.maxCompressedLength(uncompressedData.length);
        byte[] compressed = new byte[maxCompressedLen];
        int compressedLen = compressor.compress(uncompressedData, 0, uncompressedData.length, compressed, 0, maxCompressedLen);

        if (compressedLen < uncompressedData.length) {
            byte[] result = Arrays.copyOf(compressed, compressedLen);
            chunks[index].set(result, uncompressedData.length, System.currentTimeMillis());
        } else {
            chunks[index].set(uncompressedData.clone(), uncompressedData.length, System.currentTimeMillis());
        }
    }

    private int getChunkIndex(int x, int z) {
        return (x & 31) + ((z & 31) << 5);
    }

    private int getBucketIndex(int chunkX, int chunkZ) {
        int bx = Math.floorMod(chunkX, 32) / bucketSize;
        int bz = Math.floorMod(chunkZ, 32) / bucketSize;
        return bx * gridSize + bz;
    }

    private void ensureBucketLoaded(int chunkX, int chunkZ) {
        int bucketIdx = getBucketIndex(chunkX, chunkZ);
        Bucket bucket = buckets[bucketIdx];
        if (bucket == null || bucket.loaded) return;

        synchronized (bucket.lock) {
            if (bucket.loaded) return;
            loadBucket(bucketIdx);
            bucket.loaded = true;
        }
    }

    private void loadBucket(int bucketIdx) {
        Bucket bucket = buckets[bucketIdx];
        if (bucket == null || bucket.compressedBucket == null) return;

        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(bucket.compressedBucket);
            ZstdInputStream zis = new ZstdInputStream(bais);
            ByteBuffer buf = ByteBuffer.wrap(zis.readAllBytes());

            int bx = bucketIdx % gridSize;
            int bz = bucketIdx / gridSize;

            for (int cx = 0; cx < bucketSize; cx++) {
                for (int cz = 0; cz < bucketSize; cz++) {
                    int chunkX = bx * bucketSize + cx;
                    int chunkZ = bz * bucketSize + cz;
                    int chunkIndex = getChunkIndex(chunkX, chunkZ);

                    int dataSize = buf.getInt();
                    long timestamp = buf.getLong();

                    if (dataSize > 0) {
                        byte[] chunkData = new byte[dataSize - 8];
                        buf.get(chunkData);
                        compressAndStore(chunkIndex, chunkData);
                        chunks[chunkIndex].timestamp = timestamp;
                    }
                }
            }

            bucket.compressedBucket = null;
        } catch (IOException e) {
            LOGGER.error("Failed to load bucket {}", bucketIdx, e);
        }
    }

    @Override
    public synchronized boolean doesChunkExist(ChunkPos pos) throws Exception {
        initIfNeeded();
        int idx = getChunkIndex(pos.x, pos.z);
        return chunks[idx].loaded && chunks[idx].compressedData != null;
    }

    @Override
    public synchronized void write(ChunkPos pos, ByteBuffer buffer) {
        initIfNeeded();
        if (closed) return;

        try {
            ensureBucketLoaded(pos.x, pos.z);

            byte[] b = toByteArray(new ByteArrayInputStream(buffer.array()));
            if (b.length > 500 * 1024 * 1024) {
                LOGGER.warn("Chunk too large at ({}, {}), clearing", pos.x, pos.z);
                clear(pos);
                return;
            }

            compressAndStore(getChunkIndex(pos.x, pos.z), b);
            dirty.set(true);
        } catch (Exception e) {
            LOGGER.error("Failed to write chunk at ({}, {})", pos.x, pos.z, e);
        }
    }

    @Override
    public synchronized ByteBuffer read(ChunkPos pos) throws Exception {
        initIfNeeded();
        if (closed) return null;

        try {
            ensureBucketLoaded(pos.x, pos.z);
            int idx = getChunkIndex(pos.x, pos.z);
            ChunkEntry entry = chunks[idx];

            if (!entry.loaded || entry.compressedData == null) {
                return null;
            }

            byte[] compressed = entry.get();
            if (compressed == null) return null;

            byte[] decompressed = new byte[entry.uncompressedSize];
            decompressor.decompress(compressed, 0, decompressed, 0, entry.uncompressedSize);

            return ByteBuffer.wrap(decompressed);
        } catch (Exception e) {
            LOGGER.error("Failed to read chunk at ({}, {})", pos.x, pos.z, e);
            return null;
        }
    }

    @Override
    public synchronized void clear(ChunkPos pos) throws Exception {
        initIfNeeded();
        int idx = getChunkIndex(pos.x, pos.z);
        chunks[idx].clear();
        dirty.set(true);
    }

    @Override
    public synchronized void flush() throws IOException {
        if (!dirty.compareAndSet(true, false)) return;
        if (closed) return;

        writeLock.lock();
        try {
            performFlush();
        } finally {
            writeLock.unlock();
        }
    }

    private void performFlush() throws IOException {
        File tempFile = new File(regionFile.toString() + ".tmp");
        FileOutputStream fos = new FileOutputStream(tempFile);
        DataOutputStream dos = new DataOutputStream(fos);

        dos.writeLong(SUPERBLOCK);
        dos.writeByte(VERSION);
        dos.writeLong(System.currentTimeMillis());
        dos.writeByte(gridSize);

        String fileName = regionFile.getFileName().toString();
        String[] parts = fileName.split("\\.");
        int regionX = 0, regionZ = 0;
        try {
            if (parts.length >= 4) {
                regionX = Integer.parseInt(parts[1]);
                regionZ = Integer.parseInt(parts[2]);
            }
        } catch (NumberFormatException e) {
            LOGGER.warn("Failed to parse region coordinates from {}", fileName);
        }

        dos.writeInt(regionX);
        dos.writeInt(regionZ);

        ByteArrayOutputStream indexBaos = new ByteArrayOutputStream(CHUNK_COUNT * 16);
        DataOutputStream indexDos = new DataOutputStream(indexBaos);

        ByteArrayOutputStream dataBaos = new ByteArrayOutputStream();
        ZstdOutputStream zos = new ZstdOutputStream(dataBaos, compressionLevel);
        DataOutputStream dataDos = new DataOutputStream(zos);

        int dataOffset = 0;

        for (int i = 0; i < CHUNK_COUNT; i++) {
            ChunkEntry entry = chunks[i];

            if (entry.loaded && entry.compressedData != null) {
                indexDos.writeInt(dataOffset);
                indexDos.writeInt(entry.uncompressedSize);
                indexDos.writeLong(entry.timestamp);

                dataDos.writeInt(entry.uncompressedSize);
                dataDos.write(entry.timestamp);
                byte[] data = entry.get();
                if (data != null) {
                    dataDos.write(data);
                }

                dataOffset += entry.uncompressedSize + 12;
            } else {
                indexDos.writeInt(0);
                indexDos.writeInt(0);
                indexDos.writeLong(0);
            }
        }

        dataDos.flush();
        zos.flush();
        zos.close();

        byte[] indexBytes = indexBaos.toByteArray();
        byte[] dataBytes = dataBaos.toByteArray();

        dos.writeInt(indexBytes.length);
        dos.write(indexBytes);
        dos.writeInt(dataBytes.length);
        dos.write(dataBytes);

        dos.writeLong(SUPERBLOCK);

        dos.flush();
        fos.getFD().sync();
        dos.close();
        fos.close();

        Files.move(tempFile.toPath(), regionFile, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public synchronized void close() throws IOException {
        if (closed) return;

        writeLock.lock();
        try {
            flush();
            closed = true;

            for (ChunkEntry entry : chunks) {
                entry.clear();
            }

            for (Bucket bucket : buckets) {
                if (bucket != null) {
                    bucket.compressedBucket = null;
                    bucket.loaded = false;
                }
            }
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public Path getRegionFile() {
        return regionFile;
    }

    @Override
    public ReentrantLock getFileLock() {
        return writeLock;
    }

    private static byte[] toByteArray(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}