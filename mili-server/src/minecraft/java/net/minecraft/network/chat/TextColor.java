package net.minecraft.network.chat;

import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Lifecycle;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import org.jspecify.annotations.Nullable;

public final class TextColor {
    private static final String CUSTOM_COLOR_PREFIX = "#";
    public static final Codec<TextColor> CODEC = Codec.STRING.comapFlatMap(TextColor::parseColor, TextColor::serialize);
    private static final Map<ChatFormatting, TextColor> LEGACY_FORMAT_TO_COLOR = Stream.of(ChatFormatting.values())
        .filter(ChatFormatting::isColor)
        .collect(ImmutableMap.toImmutableMap(Function.identity(), formatting -> new TextColor(formatting.getColor(), formatting.getName(), formatting))); // CraftBukkit
    private static final Map<String, TextColor> NAMED_COLORS = LEGACY_FORMAT_TO_COLOR.values()
        .stream()
        .collect(ImmutableMap.toImmutableMap(textColor -> textColor.name, Function.identity()));
    private final int value;
    public final @Nullable String name;
    // CraftBukkit start
    @Nullable
    public final ChatFormatting format;

    private TextColor(int value, String name, ChatFormatting format) {
        this.value = value & 16777215;
        this.name = name;
        this.format = format;
    }

    private TextColor(int value) {
        this.value = value & 16777215;
        this.name = null;
        this.format = null;
    }
    // CraftBukkit end

    public int getValue() {
        return this.value;
    }

    public String serialize() {
        return this.name != null ? this.name : this.formatValue();
    }

    private String formatValue() {
        return String.format(Locale.ROOT, "#%06X", this.value);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else if (other != null && this.getClass() == other.getClass()) {
            TextColor textColor = (TextColor)other;
            return this.value == textColor.value;
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.value, this.name);
    }

    @Override
    public String toString() {
        return this.serialize();
    }

    public static @Nullable TextColor fromLegacyFormat(ChatFormatting formatting) {
        return LEGACY_FORMAT_TO_COLOR.get(formatting);
    }

    public static TextColor fromRgb(int color) {
        return new TextColor(color);
    }

    public static DataResult<TextColor> parseColor(String color) {
        if (color.startsWith("#")) {
            try {
                int i = Integer.parseInt(color.substring(1), 16);
                return i >= 0 && i <= 16777215
                    ? DataResult.success(fromRgb(i), Lifecycle.stable())
                    : DataResult.error(() -> "Color value out of range: " + color);
            } catch (NumberFormatException var2) {
                return DataResult.error(() -> "Invalid color value: " + color);
            }
        } else {
            TextColor textColor = NAMED_COLORS.get(color);
            return textColor == null ? DataResult.error(() -> "Invalid color name: " + color) : DataResult.success(textColor, Lifecycle.stable());
        }
    }
}
