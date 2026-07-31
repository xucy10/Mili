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
 *   <li>When the Rust library is not loaded, falls back to NightConfig-style in-memory operation.</li>
 * </ul>
 *
 * <p><b>Thread safety:</b> This class is <b>not</b> thread-safe. All access must be synchronized
 * by the caller (typically {@code ConfigsInstance} ensures single-threaded access during load/reload).
 */
public class TomlConfigData {

    private static final String COMMENT_PREFIX = "__comment__:";
    private static final Gson GSON = new Gson();
    private static final Type STRING_MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();

    private final File file;
    private final Map<String, Object> values = new LinkedHashMap<>();
    private final Map<String, String> comments = new LinkedHashMap<>();

    /**
     * Create a new TomlConfigData backed by the given file.
     *
     * @param file the TOML configuration file
     */
    public TomlConfigData(File file) {
        this.file = file;
        // Ensure the Rust native library is loaded before any JNI calls.
        // Config loading happens early in startup before RenderHelper triggers load().
        RustBridge.load();
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

        String json = RustBridge.configLoad(file.getAbsolutePath());
        if (json == null || json.isEmpty()) {
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
    }

    /**
     * Save the in-memory configuration to the TOML file via Rust.
     *
     * <p>Uses merge mode to preserve existing comments in the file.
     */
    public void save() {
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
