package net.minecraft.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

public class StringUtil {
    private static final Pattern STRIP_COLOR_PATTERN = Pattern.compile("(?i)\\u00A7[0-9A-FK-OR]");
    private static final Pattern LINE_PATTERN = Pattern.compile("\\r\\n|\\v");
    private static final Pattern LINE_END_PATTERN = Pattern.compile("(?:\\r\\n|\\v)$");

    public static String formatTickDuration(int ticks, float ticksPerSecond) {
        int floor = Mth.floor(ticks / ticksPerSecond);
        int i = floor / 60;
        floor %= 60;
        int i1 = i / 60;
        i %= 60;
        return i1 > 0 ? String.format(Locale.ROOT, "%02d:%02d:%02d", i1, i, floor) : String.format(Locale.ROOT, "%02d:%02d", i, floor);
    }

    public static String stripColor(String text) {
        return STRIP_COLOR_PATTERN.matcher(text).replaceAll("");
    }

    public static boolean isNullOrEmpty(@Nullable String string) {
        return StringUtils.isEmpty(string);
    }

    public static String truncateStringIfNecessary(String string, int maxSize, boolean addEllipsis) {
        if (string.length() <= maxSize) {
            return string;
        } else {
            return addEllipsis && maxSize > 3 ? string.substring(0, maxSize - 3) + "..." : string.substring(0, maxSize);
        }
    }

    public static int lineCount(String string) {
        if (string.isEmpty()) {
            return 0;
        } else {
            Matcher matcher = LINE_PATTERN.matcher(string);
            int i = 1;

            while (matcher.find()) {
                i++;
            }

            return i;
        }
    }

    public static boolean endsWithNewLine(String string) {
        return LINE_END_PATTERN.matcher(string).find();
    }

    public static String trimChatMessage(String string) {
        return truncateStringIfNecessary(string, 256, false);
    }

    public static boolean isAllowedChatCharacter(int codePoint) {
        return codePoint != 167 && codePoint >= 32 && codePoint != 127;
    }
    // Luminol start - Add config for username checks
    public static boolean isValidPlayerName(String username){
        return isValidPlayerName(username, !me.earthme.luminol.config.modules.misc.UsernameCheckConfig.enabled);
    }
    // Luminol end

    public static boolean isValidPlayerName(String playerName, boolean byPass) { // Luminol - Add config for username checks
        if (byPass) return playerName.length() <= 16; // Luminol - Add config for username checks
        return playerName.length() <= 16 && playerName.chars().filter(i -> i <= 32 || i >= 127).findAny().isEmpty();
    }

    public static String filterText(String text) {
        return filterText(text, false);
    }

    public static String filterText(String text, boolean allowLineBreaks) {
        StringBuilder stringBuilder = new StringBuilder();

        for (char c : text.toCharArray()) {
            if (isAllowedChatCharacter(c)) {
                stringBuilder.append(c);
            } else if (allowLineBreaks && c == '\n') {
                stringBuilder.append(c);
            }
        }

        return stringBuilder.toString();
    }

    // Paper start - Username validation
    public static boolean isReasonablePlayerName(final String name) {
        if (name.isEmpty() || name.length() > 16) {
            return false;
        }

        for (int i = 0, len = name.length(); i < len; ++i) {
            final char c = name.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || (c == '_' || c == '.')) {
                continue;
            }

            return false;
        }

        return true;
    }
    // Paper end - Username validation

    public static boolean isWhitespace(int character) {
        return Character.isWhitespace(character) || Character.isSpaceChar(character);
    }

    public static boolean isBlank(@Nullable String string) {
        return string == null || string.isEmpty() || string.chars().allMatch(StringUtil::isWhitespace);
    }
}
