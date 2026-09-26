package fun.bm.mili.utils;

import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class MmapRegionStorage {
    private static volatile boolean enabled = false;
    private static final ConcurrentHashMap<String, MappedRegion> mappedRegions = new ConcurrentHashMap<>();
    private static final AtomicLong totalReads = new AtomicLong();
    private static final AtomicLong totalWrites = new AtomicLong();
    private static final AtomicLong cacheHits = new AtomicLong();
    private static final AtomicLong cacheMisses = new AtomicLong();

    public static void setEnabled(boolean v) { enabled = v; }
    public static boolean isEnabled() { return enabled; }

    public static MappedByteBuffer getOrMap(Path filePath, int size) {
        if (!enabled) return null;

        String key = filePath.toAbsolutePath().toString();
        MappedRegion existing = mappedRegions.get(key);
        if (existing != null && existing.isValid()) {
            cacheHits.incrementAndGet();
            return existing.buffer;
        }

        // Mili start - fix: 缓存未命中且旧条目失效时，不关闭旧的 RandomAccessFile/FileChannel，文件句柄泄漏
        if (existing != null) {
            existing.close();
            mappedRegions.remove(key);
        }
        // Mili end

        cacheMisses.incrementAndGet();

        try {
            long maxSizeMb = fun.bm.mili.config.modules.optimizations.MmapRegionStorageConfig.maxMappedSizeMb;
            long currentMapped = mappedRegions.size() * 4096L / (1024 * 1024);
            if (currentMapped >= maxSizeMb) {
                evictOldest();
            }

            // Mili start - fix: channel.map() 抛异常时 RandomAccessFile 和 FileChannel 未关闭
            RandomAccessFile raf = new RandomAccessFile(filePath.toFile(), "rw");
            FileChannel channel = raf.getChannel();
            MappedByteBuffer buffer;
            try {
                buffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, size);
            } catch (Throwable mapEx) {
                try { channel.close(); } catch (Throwable ignored) {}
                try { raf.close(); } catch (Throwable ignored) {}
                throw mapEx;
            }
            // Mili end

            MappedRegion region = new MappedRegion(buffer, channel, raf, System.currentTimeMillis());
            mappedRegions.put(key, region);

            return buffer;
        // Mili start - fix: catch Throwable instead of Exception to handle Errors (OOM from channel.map)
        } catch (Throwable e) {
            return null;
        }
        // Mili end
    }

    private static void evictOldest() {
        String oldestKey = null;
        long oldestTime = Long.MAX_VALUE;

        for (Map.Entry<String, MappedRegion> entry : mappedRegions.entrySet()) {
            if (entry.getValue().lastAccess < oldestTime) {
                oldestTime = entry.getValue().lastAccess;
                oldestKey = entry.getKey();
            }
        }

        if (oldestKey != null) {
            MappedRegion removed = mappedRegions.remove(oldestKey);
            if (removed != null) removed.close();
        }
    }

    public static Map<String, Object> getStats() {
        Map<String, Object> stats = new java.util.LinkedHashMap<>();
        stats.put("Enabled", enabled);
        stats.put("Mapped Regions", mappedRegions.size());
        stats.put("Cache Hits", cacheHits.get());
        stats.put("Cache Misses", cacheMisses.get());
        long total = cacheHits.get() + cacheMisses.get();
        stats.put("Hit Rate", total > 0 ?
                String.format("%.1f%%", (double) cacheHits.get() / total * 100) : "0%");
        stats.put("Total Reads", totalReads.get());
        stats.put("Total Writes", totalWrites.get());
        return stats;
    }

    private static class MappedRegion {
        final MappedByteBuffer buffer;
        final FileChannel channel;
        final RandomAccessFile raf;
        long lastAccess;

        MappedRegion(MappedByteBuffer buffer, FileChannel channel, RandomAccessFile raf, long lastAccess) {
            this.buffer = buffer;
            this.channel = channel;
            this.raf = raf;
            this.lastAccess = lastAccess;
        }

        boolean isValid() {
            try {
                lastAccess = System.currentTimeMillis();
                // Mili start - fix: isValid() 用 buffer.isLoaded() 判断有效性，但 isLoaded() 只是提示而非有效性检查
                return channel.isOpen();
                // Mili end
            // Mili start - fix: catch Throwable instead of Exception to handle Errors
            } catch (Throwable e) {
                return false;
            }
            // Mili end
        }

        void close() {
            // Mili start - fix: catch Throwable instead of Exception in resource cleanup
            try { channel.force(false); } catch (Throwable ignored) {}
            try { channel.close(); } catch (Throwable ignored) {}
            try { raf.close(); } catch (Throwable ignored) {}
            // Mili end
        }
    }
}
