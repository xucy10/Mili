package net.minecraft.network.chat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import org.jspecify.annotations.Nullable;

public final class Style {
    public static final Style EMPTY = new Style(null, null, null, null, null, null, null, null, null, null, null);
    public static final int NO_SHADOW = 0;
    final @Nullable TextColor color;
    final @Nullable Integer shadowColor;
    final @Nullable Boolean bold;
    final @Nullable Boolean italic;
    final @Nullable Boolean underlined;
    final @Nullable Boolean strikethrough;
    final @Nullable Boolean obfuscated;
    final @Nullable ClickEvent clickEvent;
    final @Nullable HoverEvent hoverEvent;
    final @Nullable String insertion;
    final @Nullable FontDescription font;

    private static Style create(
        Optional<TextColor> color,
        Optional<Integer> shadowColor,
        Optional<Boolean> bold,
        Optional<Boolean> italic,
        Optional<Boolean> underlined,
        Optional<Boolean> strikethrough,
        Optional<Boolean> obfuscated,
        Optional<ClickEvent> clickEvent,
        Optional<HoverEvent> hoverEvent,
        Optional<String> insertion,
        Optional<FontDescription> font
    ) {
        Style style = new Style(
            color.orElse(null),
            shadowColor.orElse(null),
            bold.orElse(null),
            italic.orElse(null),
            underlined.orElse(null),
            strikethrough.orElse(null),
            obfuscated.orElse(null),
            clickEvent.orElse(null),
            hoverEvent.orElse(null),
            insertion.orElse(null),
            font.orElse(null)
        );
        return style.equals(EMPTY) ? EMPTY : style;
    }

    private Style(
        @Nullable TextColor color,
        @Nullable Integer shadowColor,
        @Nullable Boolean bold,
        @Nullable Boolean italic,
        @Nullable Boolean underlined,
        @Nullable Boolean strikethrough,
        @Nullable Boolean obfuscated,
        @Nullable ClickEvent clickEvent,
        @Nullable HoverEvent hoverEvent,
        @Nullable String insertion,
        @Nullable FontDescription font
    ) {
        this.color = color;
        this.shadowColor = shadowColor;
        this.bold = bold;
        this.italic = italic;
        this.underlined = underlined;
        this.strikethrough = strikethrough;
        this.obfuscated = obfuscated;
        this.clickEvent = clickEvent;
        this.hoverEvent = hoverEvent;
        this.insertion = insertion;
        this.font = font;
    }

    public @Nullable TextColor getColor() {
        return this.color;
    }

    public @Nullable Integer getShadowColor() {
        return this.shadowColor;
    }

    public boolean isBold() {
        return this.bold == Boolean.TRUE;
    }

    public boolean isItalic() {
        return this.italic == Boolean.TRUE;
    }

    public boolean isStrikethrough() {
        return this.strikethrough == Boolean.TRUE;
    }

    public boolean isUnderlined() {
        return this.underlined == Boolean.TRUE;
    }

    public boolean isObfuscated() {
        return this.obfuscated == Boolean.TRUE;
    }

    public boolean isEmpty() {
        return this == EMPTY;
    }

    public @Nullable ClickEvent getClickEvent() {
        return this.clickEvent;
    }

    public @Nullable HoverEvent getHoverEvent() {
        return this.hoverEvent;
    }

    public @Nullable String getInsertion() {
        return this.insertion;
    }

    public FontDescription getFont() {
        return (FontDescription)(this.font != null ? this.font : FontDescription.DEFAULT);
    }

    private static <T> Style checkEmptyAfterChange(Style style, @Nullable T oldValue, @Nullable T newValue) {
        return oldValue != null && newValue == null && style.equals(EMPTY) ? EMPTY : style;
    }

    public Style withColor(@Nullable TextColor color) {
        return Objects.equals(this.color, color)
            ? this
            : checkEmptyAfterChange(
                new Style(
                    color,
                    this.shadowColor,
                    this.bold,
                    this.italic,
                    this.underlined,
                    this.strikethrough,
                    this.obfuscated,
                    this.clickEvent,
                    this.hoverEvent,
                    this.insertion,
                    this.font
                ),
                this.color,
                color
            );
    }

    public Style withColor(@Nullable ChatFormatting formatting) {
        return this.withColor(formatting != null ? TextColor.fromLegacyFormat(formatting) : null);
    }

    public Style withColor(int color) {
        return this.withColor(TextColor.fromRgb(color));
    }

    public Style withShadowColor(int color) {
        return Objects.equals(this.shadowColor, color)
            ? this
            : checkEmptyAfterChange(
                new Style(
                    this.color,
                    color,
                    this.bold,
                    this.italic,
                    this.underlined,
                    this.strikethrough,
                    this.obfuscated,
                    this.clickEvent,
                    this.hoverEvent,
                    this.insertion,
                    this.font
                ),
                this.shadowColor,
                color
            );
    }

    public Style withoutShadow() {
        return this.withShadowColor(0);
    }

    public Style withBold(@Nullable Boolean bold) {
        return Objects.equals(this.bold, bold)
            ? this
            : checkEmptyAfterChange(
                new Style(
                    this.color,
                    this.shadowColor,
                    bold,
                    this.italic,
                    this.underlined,
                    this.strikethrough,
                    this.obfuscated,
                    this.clickEvent,
                    this.hoverEvent,
                    this.insertion,
                    this.font
                ),
                this.bold,
                bold
            );
    }

    public Style withItalic(@Nullable Boolean italic) {
        return Objects.equals(this.italic, italic)
            ? this
            : checkEmptyAfterChange(
                new Style(
                    this.color,
                    this.shadowColor,
                    this.bold,
                    italic,
                    this.underlined,
                    this.strikethrough,
                    this.obfuscated,
                    this.clickEvent,
                    this.hoverEvent,
                    this.insertion,
                    this.font
                ),
                this.italic,
                italic
            );
    }

    public Style withUnderlined(@Nullable Boolean underlined) {
        return Objects.equals(this.underlined, underlined)
            ? this
            : checkEmptyAfterChange(
                new Style(
                    this.color,
                    this.shadowColor,
                    this.bold,
                    this.italic,
                    underlined,
                    this.strikethrough,
                    this.obfuscated,
                    this.clickEvent,
                    this.hoverEvent,
                    this.insertion,
                    this.font
                ),
                this.underlined,
                underlined
            );
    }

    public Style withStrikethrough(@Nullable Boolean strikethrough) {
        return Objects.equals(this.strikethrough, strikethrough)
            ? this
            : checkEmptyAfterChange(
                new Style(
                    this.color,
                    this.shadowColor,
                    this.bold,
                    this.italic,
                    this.underlined,
                    strikethrough,
                    this.obfuscated,
                    this.clickEvent,
                    this.hoverEvent,
                    this.insertion,
                    this.font
                ),
                this.strikethrough,
                strikethrough
            );
    }

    public Style withObfuscated(@Nullable Boolean obfuscated) {
        return Objects.equals(this.obfuscated, obfuscated)
            ? this
            : checkEmptyAfterChange(
                new Style(
                    this.color,
                    this.shadowColor,
                    this.bold,
                    this.italic,
                    this.underlined,
                    this.strikethrough,
                    obfuscated,
                    this.clickEvent,
                    this.hoverEvent,
                    this.insertion,
                    this.font
                ),
                this.obfuscated,
                obfuscated
            );
    }

    public Style withClickEvent(@Nullable ClickEvent clickEvent) {
        return Objects.equals(this.clickEvent, clickEvent)
            ? this
            : checkEmptyAfterChange(
                new Style(
                    this.color,
                    this.shadowColor,
                    this.bold,
                    this.italic,
                    this.underlined,
                    this.strikethrough,
                    this.obfuscated,
                    clickEvent,
                    this.hoverEvent,
                    this.insertion,
                    this.font
                ),
                this.clickEvent,
                clickEvent
            );
    }

    public Style withHoverEvent(@Nullable HoverEvent hoverEvent) {
        return Objects.equals(this.hoverEvent, hoverEvent)
            ? this
            : checkEmptyAfterChange(
                new Style(
                    this.color,
                    this.shadowColor,
                    this.bold,
                    this.italic,
                    this.underlined,
                    this.strikethrough,
                    this.obfuscated,
                    this.clickEvent,
                    hoverEvent,
                    this.insertion,
                    this.font
                ),
                this.hoverEvent,
                hoverEvent
            );
    }

    public Style withInsertion(@Nullable String insertion) {
        return Objects.equals(this.insertion, insertion)
            ? this
            : checkEmptyAfterChange(
                new Style(
                    this.color,
                    this.shadowColor,
                    this.bold,
                    this.italic,
                    this.underlined,
                    this.strikethrough,
                    this.obfuscated,
                    this.clickEvent,
                    this.hoverEvent,
                    insertion,
                    this.font
                ),
                this.insertion,
                insertion
            );
    }

    public Style withFont(@Nullable FontDescription font) {
        return Objects.equals(this.font, font)
            ? this
            : checkEmptyAfterChange(
                new Style(
                    this.color,
                    this.shadowColor,
                    this.bold,
                    this.italic,
                    this.underlined,
                    this.strikethrough,
                    this.obfuscated,
                    this.clickEvent,
                    this.hoverEvent,
                    this.insertion,
                    font
                ),
                this.font,
                font
            );
    }

    public Style applyFormat(ChatFormatting formatting) {
        TextColor textColor = this.color;
        Boolean _boolean = this.bold;
        Boolean _boolean1 = this.italic;
        Boolean _boolean2 = this.strikethrough;
        Boolean _boolean3 = this.underlined;
        Boolean _boolean4 = this.obfuscated;
        switch (formatting) {
            case OBFUSCATED:
                _boolean4 = true;
                break;
            case BOLD:
                _boolean = true;
                break;
            case STRIKETHROUGH:
                _boolean2 = true;
                break;
            case UNDERLINE:
                _boolean3 = true;
                break;
            case ITALIC:
                _boolean1 = true;
                break;
            case RESET:
                return EMPTY;
            default:
                textColor = TextColor.fromLegacyFormat(formatting);
        }

        return new Style(
            textColor, this.shadowColor, _boolean, _boolean1, _boolean3, _boolean2, _boolean4, this.clickEvent, this.hoverEvent, this.insertion, this.font
        );
    }

    public Style applyLegacyFormat(ChatFormatting formatting) {
        TextColor textColor = this.color;
        Boolean _boolean = this.bold;
        Boolean _boolean1 = this.italic;
        Boolean _boolean2 = this.strikethrough;
        Boolean _boolean3 = this.underlined;
        Boolean _boolean4 = this.obfuscated;
        switch (formatting) {
            case OBFUSCATED:
                _boolean4 = true;
                break;
            case BOLD:
                _boolean = true;
                break;
            case STRIKETHROUGH:
                _boolean2 = true;
                break;
            case UNDERLINE:
                _boolean3 = true;
                break;
            case ITALIC:
                _boolean1 = true;
                break;
            case RESET:
                return EMPTY;
            default:
                _boolean4 = false;
                _boolean = false;
                _boolean2 = false;
                _boolean3 = false;
                _boolean1 = false;
                textColor = TextColor.fromLegacyFormat(formatting);
        }

        return new Style(
            textColor, this.shadowColor, _boolean, _boolean1, _boolean3, _boolean2, _boolean4, this.clickEvent, this.hoverEvent, this.insertion, this.font
        );
    }

    public Style applyFormats(ChatFormatting... formats) {
        TextColor textColor = this.color;
        Boolean _boolean = this.bold;
        Boolean _boolean1 = this.italic;
        Boolean _boolean2 = this.strikethrough;
        Boolean _boolean3 = this.underlined;
        Boolean _boolean4 = this.obfuscated;

        for (ChatFormatting chatFormatting : formats) {
            switch (chatFormatting) {
                case OBFUSCATED:
                    _boolean4 = true;
                    break;
                case BOLD:
                    _boolean = true;
                    break;
                case STRIKETHROUGH:
                    _boolean2 = true;
                    break;
                case UNDERLINE:
                    _boolean3 = true;
                    break;
                case ITALIC:
                    _boolean1 = true;
                    break;
                case RESET:
                    return EMPTY;
                default:
                    textColor = TextColor.fromLegacyFormat(chatFormatting);
            }
        }

        return new Style(
            textColor, this.shadowColor, _boolean, _boolean1, _boolean3, _boolean2, _boolean4, this.clickEvent, this.hoverEvent, this.insertion, this.font
        );
    }

    public Style applyTo(Style style) {
        if (this == EMPTY) {
            return style;
        } else {
            return style == EMPTY
                ? this
                : new Style(
                    this.color != null ? this.color : style.color,
                    this.shadowColor != null ? this.shadowColor : style.shadowColor,
                    this.bold != null ? this.bold : style.bold,
                    this.italic != null ? this.italic : style.italic,
                    this.underlined != null ? this.underlined : style.underlined,
                    this.strikethrough != null ? this.strikethrough : style.strikethrough,
                    this.obfuscated != null ? this.obfuscated : style.obfuscated,
                    this.clickEvent != null ? this.clickEvent : style.clickEvent,
                    this.hoverEvent != null ? this.hoverEvent : style.hoverEvent,
                    this.insertion != null ? this.insertion : style.insertion,
                    this.font != null ? this.font : style.font
                );
        }
    }

    @Override
    public String toString() {
        final StringBuilder stringBuilder = new StringBuilder("{");

        class Collector {
            private boolean isNotFirst;

            private void prependSeparator() {
                if (this.isNotFirst) {
                    stringBuilder.append(',');
                }

                this.isNotFirst = true;
            }

            void addFlagString(String key, @Nullable Boolean value) {
                if (value != null) {
                    this.prependSeparator();
                    if (!value) {
                        stringBuilder.append('!');
                    }

                    stringBuilder.append(key);
                }
            }

            void addValueString(String key, @Nullable Object value) {
                if (value != null) {
                    this.prependSeparator();
                    stringBuilder.append(key);
                    stringBuilder.append('=');
                    stringBuilder.append(value);
                }
            }
        }

        Collector collector = new Collector();
        collector.addValueString("color", this.color);
        collector.addValueString("shadowColor", this.shadowColor);
        collector.addFlagString("bold", this.bold);
        collector.addFlagString("italic", this.italic);
        collector.addFlagString("underlined", this.underlined);
        collector.addFlagString("strikethrough", this.strikethrough);
        collector.addFlagString("obfuscated", this.obfuscated);
        collector.addValueString("clickEvent", this.clickEvent);
        collector.addValueString("hoverEvent", this.hoverEvent);
        collector.addValueString("insertion", this.insertion);
        collector.addValueString("font", this.font);
        stringBuilder.append("}");
        return stringBuilder.toString();
    }

    @Override
    public boolean equals(Object other) {
        return this == other
            || other instanceof Style style
                && this.bold == style.bold
                && Objects.equals(this.getColor(), style.getColor())
                && Objects.equals(this.getShadowColor(), style.getShadowColor())
                && this.italic == style.italic
                && this.obfuscated == style.obfuscated
                && this.strikethrough == style.strikethrough
                && this.underlined == style.underlined
                && Objects.equals(this.clickEvent, style.clickEvent)
                && Objects.equals(this.hoverEvent, style.hoverEvent)
                && Objects.equals(this.insertion, style.insertion)
                && Objects.equals(this.font, style.font);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            this.color,
            this.shadowColor,
            this.bold,
            this.italic,
            this.underlined,
            this.strikethrough,
            this.obfuscated,
            this.clickEvent,
            this.hoverEvent,
            this.insertion
        );
    }

    public static class Serializer {
        public static final MapCodec<Style> MAP_CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    TextColor.CODEC.optionalFieldOf("color").forGetter(serializer -> Optional.ofNullable(serializer.color)),
                    ExtraCodecs.ARGB_COLOR_CODEC.optionalFieldOf("shadow_color").forGetter(serializer -> Optional.ofNullable(serializer.shadowColor)),
                    Codec.BOOL.optionalFieldOf("bold").forGetter(serializer -> Optional.ofNullable(serializer.bold)),
                    Codec.BOOL.optionalFieldOf("italic").forGetter(serializer -> Optional.ofNullable(serializer.italic)),
                    Codec.BOOL.optionalFieldOf("underlined").forGetter(serializer -> Optional.ofNullable(serializer.underlined)),
                    Codec.BOOL.optionalFieldOf("strikethrough").forGetter(serializer -> Optional.ofNullable(serializer.strikethrough)),
                    Codec.BOOL.optionalFieldOf("obfuscated").forGetter(serializer -> Optional.ofNullable(serializer.obfuscated)),
                    ClickEvent.CODEC.optionalFieldOf("click_event").forGetter(serializer -> Optional.ofNullable(serializer.clickEvent)),
                    HoverEvent.CODEC.optionalFieldOf("hover_event").forGetter(serializer -> Optional.ofNullable(serializer.hoverEvent)),
                    Codec.STRING.optionalFieldOf("insertion").forGetter(serializer -> Optional.ofNullable(serializer.insertion)),
                    FontDescription.CODEC.optionalFieldOf("font").forGetter(serializer -> Optional.ofNullable(serializer.font))
                )
                .apply(instance, Style::create)
        );
        public static final Codec<Style> CODEC = MAP_CODEC.codec();
        public static final StreamCodec<RegistryFriendlyByteBuf, Style> TRUSTED_STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistriesTrusted(CODEC);
    }
}
