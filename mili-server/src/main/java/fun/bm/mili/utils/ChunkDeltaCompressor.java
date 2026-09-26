package fun.bm.mili.utils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class ChunkDeltaCompressor {
    private static volatile boolean enabled = false;
    private static final ConcurrentHashMap<Long, byte[]> snapshots = new ConcurrentHashMap<>();
    // Mili start - fix: snapshots Map 无上限，chunk 卸载后不清理，长期运行 OOM
    private static final int MAX_SNAPSHOTS = 4096;
    // Mili end
    private static final AtomicLong totalCompressions = new AtomicLong();
    private static final AtomicLong bytesSaved = new AtomicLong();
    private static final AtomicLong totalBytes = new AtomicLong();

    public static void setEnabled(boolean v) { enabled = v; }
    public static boolean isEnabled() { return enabled; }

    public static byte[] computeDelta(long chunkKey, byte[] currentState) {
        if (!enabled) return currentState;

        byte[] previous = snapshots.get(chunkKey);
        if (previous == null) {
            // Mili start - fix: snapshots Map 无上限，超过 MAX_SNAPSHOTS 时清理最旧条目
            evictIfFull();
            // Mili end
            snapshots.put(chunkKey, currentState.clone());
            totalBytes.addAndGet(currentState.length);
            return currentState;
        }

        int minLength = Math.min(previous.length, currentState.length);
        int diffCount = 0;

        for (int i = 0; i < minLength; i++) {
            if (previous[i] != currentState[i]) diffCount++;
        }
        diffCount += Math.abs(previous.length - currentState.length);

        totalBytes.addAndGet(currentState.length);

        if (diffCount < currentState.length / 4) {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            // Mili start - fix: Deflater 的 end() 不在 finally 中，异常时 native 内存泄漏
            java.util.zip.Deflater deflater = new java.util.zip.Deflater();
            try {
                deflater.setInput(currentState);
                deflater.finish();
                byte[] buffer = new byte[1024];
                while (!deflater.finished()) {
                    int count = deflater.deflate(buffer);
                    baos.write(buffer, 0, count);
                }

                byte[] compressed = baos.toByteArray();
                if (compressed.length < currentState.length) {
                    totalCompressions.incrementAndGet();
                    bytesSaved.addAndGet(currentState.length - compressed.length);
                    // Mili start - fix: snapshots Map 无上限，超过 MAX_SNAPSHOTS 时清理最旧条目
                    evictIfFull();
                    // Mili end
                    snapshots.put(chunkKey, currentState.clone());
                    return compressed;
                }
            // Mili start - fix: catch Throwable instead of Exception to handle Errors
            } catch (Throwable ignored) {
            // Mili end
            } finally {
                deflater.end();
            }
            // Mili end
        }

        // Mili start - fix: snapshots Map 无上限，超过 MAX_SNAPSHOTS 时清理最旧条目
        evictIfFull();
        // Mili end
        snapshots.put(chunkKey, currentState.clone());
        return currentState;
    }

    public static Map<String, Object> getStats() {
        Map<String, Object> stats = new java.util.LinkedHashMap<>();
        stats.put("Enabled", enabled);
        stats.put("Tracked Chunks", snapshots.size());
        stats.put("Total Compressions", totalCompressions.get());
        stats.put("Bytes Saved", formatSize(bytesSaved.get()));
        stats.put("Total Processed", formatSize(totalBytes.get()));
        return stats;
    }

    // Mili start - fix: snapshots Map 无上限，超过 MAX_SNAPSHOTS 时清理最旧条目
    private static void evictIfFull() {
        if (snapshots.size() >= MAX_SNAPSHOTS) {
            // Remove oldest entries (first quarter) to amortize eviction cost
            int toRemove = MAX_SNAPSHOTS / 4;
            java.util.Iterator<Long> it = snapshots.keySet().iterator();
            for (int i = 0; i < toRemove && it.hasNext(); i++) {
                it.next();
                it.remove();
            }
        }
    }
    // Mili end

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }
}
