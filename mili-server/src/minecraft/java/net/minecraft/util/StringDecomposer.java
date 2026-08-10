package net.minecraft.util;

import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;

public class StringDecomposer {
    private static final char REPLACEMENT_CHAR = '\uFFFD';
    private static final Optional<Object> STOP_ITERATION = Optional.of(Unit.INSTANCE);

    private static boolean feedChar(Style style, FormattedCharSink sink, int position, char character) {
        return Character.isSurrogate(character) ? sink.accept(position, style, REPLACEMENT_CHAR) : sink.accept(position, style, character);
    }

    public static boolean iterate(String text, Style style, FormattedCharSink sink) {
        int len = text.length();

        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= len) {
                    if (!sink.accept(i, style, REPLACEMENT_CHAR)) {
                        return false;
                    }
                    break;
                }

                char c1 = text.charAt(i + 1);
                if (Character.isLowSurrogate(c1)) {
                    if (!sink.accept(i, style, Character.toCodePoint(c, c1))) {
                        return false;
                    }

                    i++;
                } else if (!sink.accept(i, style, REPLACEMENT_CHAR)) {
                    return false;
                }
            } else if (!feedChar(style, sink, i, c)) {
                return false;
            }
        }

        return true;
    }

    public static boolean iterateBackwards(String text, Style style, FormattedCharSink sink) {
        int len = text.length();

        for (int i = len - 1; i >= 0; i--) {
            char c = text.charAt(i);
            if (Character.isLowSurrogate(c)) {
                if (i - 1 < 0) {
                    if (!sink.accept(0, style, REPLACEMENT_CHAR)) {
                        return false;
                    }
                    break;
                }

                char c1 = text.charAt(i - 1);
                if (Character.isHighSurrogate(c1)) {
                    if (!sink.accept(--i, style, Character.toCodePoint(c1, c))) {
                        return false;
                    }
                } else if (!sink.accept(i, style, REPLACEMENT_CHAR)) {
                    return false;
                }
            } else if (!feedChar(style, sink, i, c)) {
                return false;
            }
        }

        return true;
    }

    public static boolean iterateFormatted(String text, Style style, FormattedCharSink sink) {
        return iterateFormatted(text, 0, style, sink);
    }

    public static boolean iterateFormatted(String text, int skip, Style style, FormattedCharSink sink) {
        return iterateFormatted(text, skip, style, style, sink);
    }

    public static boolean iterateFormatted(String text, int skip, Style currentStyle, Style defaultStyle, FormattedCharSink sink) {
        int len = text.length();
        Style style = currentStyle;

        for (int i = skip; i < len; i++) {
            char c = text.charAt(i);
            if (c == 167) {
                if (i + 1 >= len) {
                    break;
                }

                char c1 = text.charAt(i + 1);
                ChatFormatting byCode = ChatFormatting.getByCode(c1);
                if (byCode != null) {
                    style = byCode == ChatFormatting.RESET ? defaultStyle : style.applyLegacyFormat(byCode);
                }

                i++;
            } else if (Character.isHighSurrogate(c)) {
                if (i + 1 >= len) {
                    if (!sink.accept(i, style, REPLACEMENT_CHAR)) {
                        return false;
                    }
                    break;
                }

                char c1 = text.charAt(i + 1);
                if (Character.isLowSurrogate(c1)) {
                    if (!sink.accept(i, style, Character.toCodePoint(c, c1))) {
                        return false;
                    }

                    i++;
                } else if (!sink.accept(i, style, REPLACEMENT_CHAR)) {
                    return false;
                }
            } else if (!feedChar(style, sink, i, c)) {
                return false;
            }
        }

        return true;
    }

    public static boolean iterateFormatted(FormattedText text, Style style, FormattedCharSink sink) {
        return text.visit((style1, content) -> iterateFormatted(content, 0, style1, sink) ? Optional.empty() : STOP_ITERATION, style).isEmpty();
    }

    public static String filterBrokenSurrogates(String text) {
        StringBuilder stringBuilder = new StringBuilder();
        iterate(text, Style.EMPTY, (position, style, codePoint) -> {
            stringBuilder.appendCodePoint(codePoint);
            return true;
        });
        return stringBuilder.toString();
    }

    public static String getPlainText(FormattedText text) {
        StringBuilder stringBuilder = new StringBuilder();
        iterateFormatted(text, Style.EMPTY, (position, style, codePoint) -> {
            stringBuilder.appendCodePoint(codePoint);
            return true;
        });
        return stringBuilder.toString();
    }
}
