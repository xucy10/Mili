package fun.bm.mili.config.modules.misc;

import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;

@ConfigClassInfo(
    category = EnumConfigCategory.MISC,
    name = "chunk_independent_scheduler",
    comments = """
        Controls the Chunk Independent Scheduler subsystem.
        When enabled, eligible chunks tick independently instead of
        being grouped into fixed regions, improving parallelism for
        low-interaction chunks.
        Mixed mode (default): auto-detects high-interaction chunks
        (redstone, fluid, pistons) and falls back to Folia region mode."""
)
public class ChunkIndependentConfig implements IConfigModule {

    private static volatile ChunkIndependentConfig instance;

    public static ChunkIndependentConfig getInstance() {
        if (instance == null) {
            instance = new ChunkIndependentConfig();
        }
        return instance;
    }

    @ConfigInfo(name = "enabled", comments = "Enable chunk-independent scheduling")
    public static boolean enabled = false;

    @ConfigInfo(name = "mixed_mode", comments = """
        Use mixed mode: independent tick for low-interaction chunks,
        Folia region fallback for high-interaction chunks.""")
    public static boolean mixedMode = true;

    @ConfigInfo(name = "worker_threads", comments = """
        Number of dedicated worker threads. 0 = auto (available processors).""")
    public static int workerThreads = 0;

    @ConfigInfo(name = "cross_chunk_tick_delay", comments = """
        Number of ticks a cross-chunk update may be delayed (1 = one tick).
        Higher values reduce contention but may break 0-tick machines.""")
    public static int crossChunkTickDelay = 1;

    @ConfigInfo(name = "strict_mode", comments = """
        When true, enforces 0-tick cross-chunk operations by falling
        back to Folia region mode for any chunk with active border
        relays. Slightly reduces parallelism.""")
    public static boolean strictMode = false;

    @ConfigInfo(name = "timeout_ms", comments = "Max wait (ms) before a chunk worker times out and falls back.")
    public static long timeoutMs = 50L;

    @ConfigInfo(name = "max_work_stealing_attempts", comments = "Max work-stealing attempts before yielding.")
    public static int maxWorkStealingAttempts = 4;

    @ConfigInfo(name = "debug_logging", comments = "Log scheduler decisions at DEBUG level.")
    public static boolean debugLogging = false;
}
