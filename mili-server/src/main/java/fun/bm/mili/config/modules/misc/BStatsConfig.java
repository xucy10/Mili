package fun.bm.mili.config.modules.misc;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import fun.bm.mili.metrics.MiliMetrics;
import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Properties;
import java.util.Set;

@ConfigClassInfo(category = EnumConfigCategory.MISC, name = "bstats")
public class BStatsConfig implements IConfigModule {
    private static final Logger LOGGER = LoggerFactory.getLogger("MiliBStats");
    public static int pluginId = 0;

    @Override
    public void onLoaded(CommentedFileConfig configInstance, @Nullable Set<Exception> exs) {
        pluginId = loadPluginId();
        if (pluginId <= 0) {
            LOGGER.info("[MiliBStats] bStats disabled (pluginId=0 or not set)");
            return;
        }
        LOGGER.info("[MiliBStats] bStats enabled, pluginId={}", pluginId);
        MiliMetrics.init();
    }

    private static int loadPluginId() {
        try (InputStream is = BStatsConfig.class.getClassLoader().getResourceAsStream("mili.properties")) {
            if (is == null) {
                LOGGER.warn("[MiliBStats] mili.properties not found");
                return 0;
            }
            Properties props = new Properties();
            props.load(is);
            String val = props.getProperty("bstats_plugin_id", "0").trim();
            return Integer.parseInt(val);
        } catch (Exception e) {
            LOGGER.warn("[MiliBStats] Failed to load plugin id", e);
            return 0;
        }
    }
}
