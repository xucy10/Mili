package net.minecraft.locale;

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.StringDecomposer;
import org.slf4j.Logger;

public abstract class Language {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    private static final Pattern UNSUPPORTED_FORMAT_PATTERN = Pattern.compile("%(\\d+\\$)?[\\d.]*[df]");
    public static final String DEFAULT = "en_us";
    private static volatile Language instance = loadDefault();

    private static Language loadDefault() {
        DeprecatedTranslationsInfo deprecatedTranslationsInfo = DeprecatedTranslationsInfo.loadFromDefaultResource();
        Map<String, String> map = new HashMap<>();
        BiConsumer<String, String> biConsumer = map::put;
        parseTranslations(biConsumer, "/assets/minecraft/lang/en_us.json");
        deprecatedTranslationsInfo.applyToMap(map);
        final Map<String, String> map1 = Map.copyOf(map);
        return new Language() {
            @Override
            public String getOrDefault(String key, String defaultValue) {
                return map1.getOrDefault(key, defaultValue);
            }

            @Override
            public boolean has(String id) {
                return map1.containsKey(id);
            }

            @Override
            public boolean isDefaultRightToLeft() {
                return false;
            }

            @Override
            public FormattedCharSequence getVisualOrder(FormattedText text) {
                return sink -> text.visit(
                        (style, content) -> StringDecomposer.iterateFormatted(content, style, sink) ? Optional.empty() : FormattedText.STOP_ITERATION,
                        Style.EMPTY
                    )
                    .isPresent();
            }
        };
    }

    private static void parseTranslations(BiConsumer<String, String> output, String languagePath) {
        try (InputStream resourceAsStream = Language.class.getResourceAsStream(languagePath)) {
            loadFromJson(resourceAsStream, output);
        } catch (JsonParseException | IOException var7) {
            LOGGER.error("Couldn't read strings from {}", languagePath, var7);
        }
    }

    public static void loadFromJson(InputStream stream, BiConsumer<String, String> output) {
        JsonObject jsonObject = GSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), JsonObject.class);

        for (Entry<String, JsonElement> entry : jsonObject.entrySet()) {
            String string = UNSUPPORTED_FORMAT_PATTERN.matcher(GsonHelper.convertToString(entry.getValue(), entry.getKey())).replaceAll("%$1s");
            output.accept(entry.getKey(), string);
        }
    }

    public static Language getInstance() {
        return instance;
    }

    public static void inject(Language instance) {
        Language.instance = instance;
    }

    public String getOrDefault(String id) {
        return this.getOrDefault(id, id);
    }

    public abstract String getOrDefault(String key, String defaultValue);

    public abstract boolean has(String id);

    public abstract boolean isDefaultRightToLeft();

    public abstract FormattedCharSequence getVisualOrder(FormattedText text);

    public List<FormattedCharSequence> getVisualOrder(List<FormattedText> text) {
        return text.stream().map(this::getVisualOrder).collect(ImmutableList.toImmutableList());
    }
}
