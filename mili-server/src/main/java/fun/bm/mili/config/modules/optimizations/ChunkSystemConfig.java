package fun.bm.mili.config.modules.optimizations;

public class ChunkSystemConfig {

    public static boolean enabled = true;
    public static int asyncThreads = 2;
    public static int maxAsyncQueueSize = 500;
    public static int maxAsyncOpsPerCycle = 20;
    public static long asyncTimeBudgetNs = 10_000_000L;
    public static int maxLoadedChunks = 800;
    public static double unloadSafetyMargin = 0.85;
    public static int hotChunkRadius = 6;
    public static boolean dynamicViewDistance = true;
    public static int minViewDistance = 4;
    public static int maxViewDistance = 16;
    public static double vdDecreaseThreshold = 50.0;
    public static double vdIncreaseThreshold = 10.0;
    public static int unloadCooldownTicks = 60;
    public static int preloadRadius = 3;
    public static boolean enableMetrics = true;
}