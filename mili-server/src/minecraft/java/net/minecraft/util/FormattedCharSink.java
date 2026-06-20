package net.minecraft.util;

import net.minecraft.network.chat.Style;

@FunctionalInterface
public interface FormattedCharSink {
    boolean accept(int positionInCurrentSequence, Style style, int codePoint);
}
