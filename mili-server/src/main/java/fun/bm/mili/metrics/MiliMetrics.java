package fun.bm.mili.metrics;

import fun.bm.mili.config.modules.misc.BStatsConfig;
import org.bukkit.Bukkit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Minimal bStats metrics class for server implementation reporting.
 */
public class MiliMetrics {
    private static final Logger LOGGER = LoggerFactory.getLogger("MiliMetrics");
    // Mili start - fix: use AtomicBoolean to prevent check-then-act race in start()
    private static final java.util.concurrent.atomic.AtomicBoolean enabled = new java.util.concurrent.atomic.AtomicBoolean(false);
    // Mili end
    private static ScheduledExecutorService scheduler;

    public static void init(int defaultPluginId) {
        if (defaultPluginId <= 0) return;
        start(defaultPluginId);
    }

    public static void init() {
        int pluginId = BStatsConfig.pluginId;
        if (pluginId <= 0) {
            LOGGER.info("[MiliMetrics] bStats disabled (pluginId <= 0)");
            return;
        }
        start(pluginId);
    }

    private static void start(int pluginId) {
        // Mili start - fix: use AtomicBoolean.compareAndSet to prevent race condition
        if (!enabled.compareAndSet(false, true)) return;
        // Mili end

        scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "MiliMetrics");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleWithFixedDelay(() -> {
            try {
                sendMetrics(pluginId);
            // Mili start - fix: catch Throwable instead of Exception to handle Errors
            } catch (Throwable t) {
                LOGGER.debug("[MiliMetrics] Failed to send metrics", t);
            }
            // Mili end
        }, 30, 30, TimeUnit.MINUTES);

        LOGGER.info("[MiliMetrics] Started bStats metrics (pluginId={})", pluginId);
    }

    private static void sendMetrics(int pluginId) {
        String serverUUID = getServerUUID();
        int players = Bukkit.getOnlinePlayers().size();
        int maxPlayers = Bukkit.getMaxPlayers();
        String javaVersion = System.getProperty("java.version");
        String osName = System.getProperty("os.name");
        String osArch = System.getProperty("os.arch");
        String mcVersion = Bukkit.getBukkitVersion().split("-")[0];
        int worldCount = Bukkit.getWorlds().size();

        String json = "{" +
            "\"osname\":\"" + jsonEscape(osName) + "\"," +
            "\"osarch\":\"" + jsonEscape(osArch) + "\"," +
            "\"javaVersion\":\"" + jsonEscape(javaVersion) + "\"," +
            "\"serverImplementation\":\"Mili\"," +
            "\"mcVersion\":\"" + jsonEscape(mcVersion) + "\"," +
            "\"onlinePlayers\":" + players + "," +
            "\"maxPlayers\":" + maxPlayers + "," +
            "\"worldCount\":" + worldCount +
            "}";

        // Mili start - fix: ensure HttpURLConnection is disconnected to prevent connection leak
        java.net.HttpURLConnection conn = null;
        try {
            URL url = new URL("https://bStats.org/submitData/server-implementation");
            conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Accept", "*/*");
            conn.setRequestProperty("Connection", "close");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "Server-Software");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            String postData = "metrics=" + java.net.URLEncoder.encode(json, StandardCharsets.UTF_8);
            conn.getOutputStream().write(postData.getBytes(StandardCharsets.UTF_8));

            int responseCode = conn.getResponseCode();
            LOGGER.debug("[MiliMetrics] Response: {}", responseCode);
        // Mili start - fix: catch Throwable instead of Exception to handle Errors in network request
        } catch (Throwable e) {
            LOGGER.debug("[MiliMetrics] Request failed", e);
        } finally {
            // Mili end
            if (conn != null) {
                conn.disconnect();
            }
        }
        // Mili end
    }

    private static String getServerUUID() {
        try {
            File file = new File(Bukkit.getWorldContainer(), "bStats/metricsId.txt");
            if (file.exists()) {
                String id = new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).trim();
                if (!id.isEmpty()) return id;
            }
            String id = UUID.randomUUID().toString();
            file.getParentFile().mkdirs();
            java.nio.file.Files.write(file.toPath(), id.getBytes(StandardCharsets.UTF_8));
            return id;
        // Mili start - fix: catch Throwable instead of Exception to handle Errors
        } catch (Throwable e) {
            return "unknown";
        }
        // Mili end
    }

    private static String jsonEscape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public static void shutdown() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        // Mili start - fix: use AtomicBoolean set
        enabled.set(false);
        // Mili end
    }
}
