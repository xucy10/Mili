package fun.bm.mili.metrics;

import fun.bm.mili.config.modules.misc.BStatsConfig;
import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Minimal bStats metrics class for server implementation reporting.
 */
public class MiliMetrics {
    private static final Logger LOGGER = LoggerFactory.getLogger("MiliMetrics");
    private static boolean enabled;
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
        if (enabled) return;
        enabled = true;

        scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "MiliMetrics");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleWithFixedDelay(() -> {
            try {
                sendMetrics(pluginId);
            } catch (Exception e) {
                LOGGER.debug("[MiliMetrics] Failed to send metrics", e);
            }
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

        try {
            URL url = new URL("https://bStats.org/submitData/server-implementation");
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
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
        } catch (Exception e) {
            LOGGER.debug("[MiliMetrics] Request failed", e);
        }
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
        } catch (Exception e) {
            return "unknown";
        }
    }

    private static String jsonEscape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public static void shutdown() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        enabled = false;
    }
}
