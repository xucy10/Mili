package fun.bm.mili.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fun.bm.mili.config.modules.function.LanguageConfig;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight i18n resolver for Mili's own user-facing strings.
 * <p>
 * Translations are shipped as resources under {@code /assets/mili/lang/<lang>.json}.
 * The active language is selected by the {@code lang} option in the Mili config
 * ({@link LanguageConfig#lang}). Resolution order: selected language -> en_us fallback -> caller fallback.
 */
public final class MiliI18n {
    public static final String TPS_COMMAND_KEY_PREFIX = "mili.tpscommand.";

    private static final Map<String, Map<String, String>> LANG_CACHE = new ConcurrentHashMap<>();
    private static final Object LOCK = new Object();
    private static volatile Map<String, String> active = Map.of();
    private static volatile String activeLang = null;

    private MiliI18n() {}

    /**
     * Resolve a translation key using the language selected in the config.
     *
     * @param key      translation key, e.g. {@code mili.tpsbar.format}
     * @param fallback value returned when neither the selected language nor en_us provides the key
     * @return the translated string, or the fallback
     */
    public static @NotNull String get(@NotNull String key, @NotNull String fallback) {
        final String lang = LanguageConfig.lang;
        Map<String, String> map = active;

        if (map.isEmpty() || !lang.equals(activeLang)) {
            synchronized (LOCK) {
                if (active.isEmpty() || !lang.equals(activeLang)) {
                    active = loadLang(lang);
                    activeLang = lang;
                }
                map = active;
            }
        }

        return map.getOrDefault(key, fallback);
    }

    /**
     * Resolve a translation key and expand {@code %s} placeholders with the given arguments.
     */
    public static @NotNull String get(@NotNull String key, @NotNull String fallback, @NotNull Object... args) {
        if (args.length == 0) {
            return get(key, fallback);
        }
        return String.format(get(key, fallback), args);
    }

    private static @NotNull Map<String, String> loadLang(@NotNull String lang) {
        final Map<String, String> merged = new HashMap<>();
        loadResource("en_us", merged); // base, always loaded as fallback
        if (!"en_us".equals(lang)) {
            loadResource(lang, merged); // selected language overrides en_us
        }
        return Map.copyOf(merged);
    }

    private static void loadResource(@NotNull String lang, @NotNull Map<String, String> into) {
        final String path = "/assets/mili/lang/" + lang + ".json";
        try (InputStream is = MiliI18n.class.getResourceAsStream(path)) {
            if (is == null) {
                return;
            }
            final JsonObject obj = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
            for (final Map.Entry<String, com.google.gson.JsonElement> entry : obj.entrySet()) {
                if (entry.getValue().isJsonPrimitive()) {
                    into.put(entry.getKey(), entry.getValue().getAsString());
                }
            }
        } catch (final Exception ignored) {
            // Malformed or missing language file: keep whatever is already merged (en_us / fallback)
        }
    }
}
