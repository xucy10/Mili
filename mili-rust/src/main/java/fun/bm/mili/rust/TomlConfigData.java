package fun.bm.mili.rust;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Rust-backed TOML configuration data store.
 *
 * <p>Replaces NightConfig's {@code CommentedFileConfig} by delegating TOML parsing
 * and serialization to the Rust {@code mili_optimizer} native library via JNI.
 *
 * <p><b>Design:</b>
 * <ul>
 *   <li>Values are stored in a flattened {@code Map<String, Object>} with dot-notation keys.</li>
 *   <li>Comments are stored in a {@code Map<String, String>} keyed by the same dot-notation.</li>
 *   <li>File I/O goes through Rust ({@link RustBridge#configLoad} / {@link RustBridge#configSaveMerge}),
 *       which uses {@code toml_edit} for high-performance parsing with comment preservation.</li>
 *   <li>When the Rust library is not loaded, the engine degrades to a non-persistent
 *       in-memory mode (D1 fix: a missing Rust library no longer aborts server startup).
 *       In that mode the on-disk TOML is deliberately left untouched -- it is neither read
 *       nor written -- so an operator's existing configuration can never be silently
 *       overwritten with defaults.</li>
 * </ul>
 *
 * <p><b>Module constraint:</b> this class lives in the {@code mili-rust} module, whose
 * classpath contains neither NightConfig nor the Mojang logging classes. Only the JDK may
 * be used here; Minecraft-facing helpers belong in {@code mili-server}.</p>
 *
 * <p><b>Thread safety:</b> This class is <b>not</b> thread-safe. All access must be synchronized
 * by the caller (typically {@code ConfigsInstance} ensures single-threaded access during load/reload).
 */
public class TomlConfigData {

    private static final String COMMENT_PREFIX = "__comment__:";
    private static final Gson GSON = new Gson();
    private static final Type STRING_MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();
    private static final Logger LOGGER = Logger.getLogger("Mili");

    private final File file;
    private final Map<String, Object> values = new LinkedHashMap<>();
    private final Map<String, String> comments = new LinkedHashMap<>();

    /** Rust 原生库是否可用；构造时一次性判定，失败降级为非持久化的内存模式。 */
    private final boolean nativeAvailable;

    /**
     * Create a new TomlConfigData backed by the given file.
     *
     * <p>D1 fix: 构造函数不再因 Rust 库缺失而抛出 UnsatisfiedLinkError 导致启动崩溃。
     * 失败时置 {@code nativeAvailable=false} 并 WARN 一次，后续 load/save 走降级路径。
     *
     * @param file the TOML configuration file
     */
    public TomlConfigData(File file) {
        this.file = file;
        boolean loaded = false;
        try {
            RustBridge.load();
            loaded = RustBridge.isLoaded();
        } catch (Throwable t) {
            LOGGER.warning("[Mili] Rust native library unavailable, config engine degrades to in-memory mode: "
                + t.getMessage());
        }
        this.nativeAvailable = loaded;
        if (!loaded) {
            LOGGER.warning("[Mili] Config engine running in degraded (non-persistent) mode: "
                + "values are neither loaded from nor saved to " + file.getAbsolutePath());
        }
    }

    // ========================================================================
    // File I/O
    // ========================================================================

    /**
     * Load the TOML file from disk, parsing it via Rust and populating the in-memory maps.
     *
     * @throws RuntimeException if the Rust library returns an empty result for a non-empty file
     */
    public void load() {
        values.clear();
        comments.clear();

        if (!file.exists()) {
            return;
        }

        if (nativeAvailable) {
            String json = RustBridge.configLoad(file.getAbsolutePath());
            if (json == null || json.isEmpty()) {
                LOGGER.warning("[Mili] Rust configLoad returned empty for non-empty file: "
                    + file.getAbsolutePath());
                return;
            }

            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                String key = entry.getKey();
                if (key.startsWith(COMMENT_PREFIX)) {
                    String commentKey = key.substring(COMMENT_PREFIX.length());
                    comments.put(commentKey, entry.getValue().getAsString());
                } else {
                    values.put(key, jsonElementToObject(entry.getValue()));
                }
            }
        } else {
            // D1 fallback: 降级模式不解析磁盘文件。
            // 原因有二：(1) mili-rust 模块的 classpath 上没有 TOML 解析器，手写解析器
            // 不值得为此维护；(2) 更重要的是，若把"默认值"写回磁盘会覆盖运维人员的既有
            // 配置（数据丢失）。因此降级模式只保证服务器能启动（配置取默认值），
            // 磁盘文件保持原样，并由下面的 WARN 明确告知。
            LOGGER.warning("[Mili] Degraded mode: skipping load of " + file.getAbsolutePath()
                + " -- falling back to built-in defaults");
        }
    }

    /**
     * Save the in-memory configuration to the TOML file via Rust.
     *
     * <p>Uses merge mode to preserve existing comments in the file.
     */
    public void save() {
        if (nativeAvailable) {
            JsonObject root = new JsonObject();

            for (Map.Entry<String, Object> entry : values.entrySet()) {
                root.add(entry.getKey(), GSON.toJsonTree(entry.getValue()));
            }
            for (Map.Entry<String, String> entry : comments.entrySet()) {
                root.addProperty(COMMENT_PREFIX + entry.getKey(), entry.getValue());
            }

            boolean success = RustBridge.configSaveMerge(file.getAbsolutePath(), root.toString());
            if (!success) {
                throw new RuntimeException("Failed to save config file: " + file.getAbsolutePath());
            }
        } else {
            // D1 fallback: 降级模式明确拒绝写盘。
            // 若把内存中的默认值写回，会把用户的配置文件整体覆盖（数据丢失），
            // 风险远高于"改动不持久"，因此这里只告警、不落盘。
            LOGGER.warning("[Mili] Degraded mode: refusing to write " + file.getAbsolutePath()
                + " -- in-memory values are not persisted");
        }
    }

    // ========================================================================
    // Value operations (compatible with CommentedFileConfig API)
    // ========================================================================

    /**
     * Get a value by dot-notation key.
     *
     * @param key dot-notation key (e.g. {@code "section.subsection.key"})
     * @param <T> the expected type
     * @return the value cast to {@code T}, or {@code null} if not found
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) values.get(key);
    }

    /**
     * Get a value by key, returning a default if not present.
     *
     * @param key dot-notation key
     * @param defaultValue the default value to return if key is absent
     * @param <T> the value type
     * @return the value or {@code defaultValue}
     */
    @SuppressWarnings("unchecked")
    public <T> T getOrElse(String key, T defaultValue) {
        Object value = values.get(key);
        if (value == null) {
            values.put(key, defaultValue);
            return defaultValue;
        }
        return (T) value;
    }

    /**
     * Set a value at a dot-notation key.
     *
     * @param key dot-notation key
     * @param value the value to set
     */
    public void set(String key, Object value) {
        values.put(key, value);
    }

    /**
     * Set a value at a dot-notation key and immediately persist to disk.
     *
     * @param key dot-notation key
     * @param value the value to set
     */
    public void setAndSave(String key, Object value) {
        Object oldValue = values.put(key, value);
        try {
            save();
        } catch (RuntimeException e) {
            values.put(key, oldValue);
            throw e;
        }
    }

    /**
     * Add a value at a dot-notation key (same as {@link #set} but only if not present).
     *
     * @param key dot-notation key
     * @param value the value to add
     */
    public void add(String key, Object value) {
        if (!values.containsKey(key)) {
            values.put(key, value);
        }
    }

    /**
     * Remove a value by key.
     *
     * @param key dot-notation key
     */
    public void remove(String key) {
        values.remove(key);
        comments.remove(key);
    }

    /**
     * Check if a key exists.
     *
     * @param key dot-notation key
     * @return {@code true} if the key exists
     */
    public boolean contains(String key) {
        return values.containsKey(key);
    }

    /**
     * Get the comment for a key.
     *
     * @param key dot-notation key
     * @return the comment string, or {@code null} if no comment
     */
    public String getComment(String key) {
        return comments.get(key);
    }

    /**
     * Set the comment for a key.
     *
     * @param key dot-notation key
     * @param comment the comment text
     */
    public void setComment(String key, String comment) {
        if (comment == null || comment.isEmpty()) {
            comments.remove(key);
        } else {
            comments.put(key, comment);
        }
    }

    /**
     * Clear all values and comments.
     */
    public void clear() {
        values.clear();
        comments.clear();
    }

    // ========================================================================
    // Compatibility helpers
    // ========================================================================

    /**
     * Get the value at a path, checking if it's an empty table (sub-section).
     *
     * <p>This method emulates NightConfig's behavior where getting a table path
     * returns an {@code UnmodifiableConfig} object. Returns {@code null} if the
     * path doesn't exist or is a leaf value.
     *
     * @param key dot-notation key
     * @return an {@code EmptyConfigView} if the key has children but no direct value, {@code null} otherwise
     */
    public Object getConfigSection(String key) {
        // Check if any keys start with this prefix (indicating a sub-table)
        String prefix = key.endsWith(".") ? key : key + ".";
        boolean hasChildren = false;
        for (String k : values.keySet()) {
            if (k.startsWith(prefix)) {
                hasChildren = true;
                break;
            }
        }
        if (hasChildren) {
            return new EmptyConfigView();
        }
        return null;
    }

    /**
     * Get all keys in this config.
     *
     * @return a set of all keys
     */
    public Set<String> keySet() {
        return values.keySet();
    }

    /**
     * Get all value entries.
     *
     * @return a set of value entries
     */
    public Set<Map.Entry<String, Object>> entrySet() {
        return values.entrySet();
    }

    /**
     * Get the backing file.
     *
     * @return the configuration file
     */
    public File getFile() {
        return file;
    }

    // ========================================================================
    // Internal helpers
    // ========================================================================

    /**
     * Convert a Gson {@link JsonElement} to a Java object.
     *
     * <p>Handles primitives, strings, arrays (to {@code List<Object>}),
     * and objects (to {@code Map<String, Object>}).
     */
    private static Object jsonElementToObject(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonPrimitive()) {
            var prim = element.getAsJsonPrimitive();
            if (prim.isBoolean()) {
                return prim.getAsBoolean();
            }
            if (prim.isNumber()) {
                // Try int first, then long, then double
                var num = prim.getAsNumber();
                double d = num.doubleValue();
                if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < Long.MAX_VALUE) {
                    long l = (long) d;
                    if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
                        return (int) l;
                    }
                    return l;
                }
                return d;
            }
            return prim.getAsString();
        }
        if (element.isJsonArray()) {
            List<Object> list = new ArrayList<>();
            for (JsonElement item : element.getAsJsonArray()) {
                list.add(jsonElementToObject(item));
            }
            return list;
        }
        if (element.isJsonObject()) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                map.put(entry.getKey(), jsonElementToObject(entry.getValue()));
            }
            return map;
        }
        return null;
    }

    /**
     * A lightweight non-null marker returned by {@link #getConfigSection(String)}
     * when a key has child entries but no direct value.
     *
     * <p>Callers only check for {@code null} vs non-null; the internal state of this
     * object is never accessed.
     */
    public static final class EmptyConfigView {
    }
}
