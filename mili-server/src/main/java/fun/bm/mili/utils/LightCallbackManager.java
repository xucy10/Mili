package fun.bm.mili.utils;

import net.minecraft.core.BlockPos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class LightCallbackManager {
    private static volatile boolean enabled = false;
    private static final CopyOnWriteArrayList<LightCallback> callbacks = new CopyOnWriteArrayList<>();
    private static final AtomicInteger skyLightChanges = new AtomicInteger();
    private static final AtomicInteger blockLightChanges = new AtomicInteger();
    private static final AtomicInteger totalCallbacks = new AtomicInteger();

    public interface LightCallback {
        void onLightChange(String type, String worldName, BlockPos pos, int oldLevel, int newLevel);
    }

    public static void setEnabled(boolean v) { enabled = v; }
    public static boolean isEnabled() { return enabled; }

    public static void registerCallback(LightCallback callback) {
        callbacks.add(callback);
    }

    public static void unregisterCallback(LightCallback callback) {
        callbacks.remove(callback);
    }

    public static void onSkyLightChange(String worldName, BlockPos pos, int oldLevel, int newLevel) {
        if (!enabled || !fun.bm.mili.config.modules.optimizations.LightingCallbackConfig.trackSkyLight) return;
        if (oldLevel == newLevel) return;
        skyLightChanges.incrementAndGet();
        notifyCallbacks("sky", worldName, pos, oldLevel, newLevel);
    }

    public static void onBlockLightChange(String worldName, BlockPos pos, int oldLevel, int newLevel) {
        if (!enabled || !fun.bm.mili.config.modules.optimizations.LightingCallbackConfig.trackBlockLight) return;
        if (oldLevel == newLevel) return;
        blockLightChanges.incrementAndGet();
        notifyCallbacks("block", worldName, pos, oldLevel, newLevel);
    }

    private static void notifyCallbacks(String type, String worldName, BlockPos pos, int oldLevel, int newLevel) {
        for (LightCallback callback : callbacks) {
            try {
                callback.onLightChange(type, worldName, pos, oldLevel, newLevel);
                totalCallbacks.incrementAndGet();
            } catch (Exception ignored) {}
        }
    }

    public static Map<String, Object> getStats() {
        Map<String, Object> stats = new java.util.LinkedHashMap<>();
        stats.put("Enabled", enabled);
        stats.put("Registered Callbacks", callbacks.size());
        stats.put("Sky Light Changes", skyLightChanges.get());
        stats.put("Block Light Changes", blockLightChanges.get());
        stats.put("Total Callbacks Fired", totalCallbacks.get());
        return stats;
    }
}
