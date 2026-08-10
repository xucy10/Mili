package net.minecraft.util;

import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import java.util.List;
import net.minecraft.network.chat.Style;

@FunctionalInterface
public interface FormattedCharSequence {
    FormattedCharSequence EMPTY = sink -> true;

    boolean accept(FormattedCharSink sink);

    static FormattedCharSequence codepoint(int codePoint, Style style) {
        return sink -> sink.accept(0, style, codePoint);
    }

    static FormattedCharSequence forward(String text, Style style) {
        return text.isEmpty() ? EMPTY : sink -> StringDecomposer.iterate(text, style, sink);
    }

    static FormattedCharSequence forward(String text, Style style, Int2IntFunction codePointMapper) {
        return text.isEmpty() ? EMPTY : sink -> StringDecomposer.iterate(text, style, decorateOutput(sink, codePointMapper));
    }

    static FormattedCharSequence backward(String text, Style style) {
        return text.isEmpty() ? EMPTY : sink -> StringDecomposer.iterateBackwards(text, style, sink);
    }

    static FormattedCharSequence backward(String text, Style style, Int2IntFunction codePointMapper) {
        return text.isEmpty() ? EMPTY : sink -> StringDecomposer.iterateBackwards(text, style, decorateOutput(sink, codePointMapper));
    }

    static FormattedCharSink decorateOutput(FormattedCharSink sink, Int2IntFunction codePointMapper) {
        return (index, style, codePoint) -> sink.accept(index, style, codePointMapper.apply(codePoint));
    }

    static FormattedCharSequence composite() {
        return EMPTY;
    }

    static FormattedCharSequence composite(FormattedCharSequence sequence) {
        return sequence;
    }

    static FormattedCharSequence composite(FormattedCharSequence first, FormattedCharSequence second) {
        return fromPair(first, second);
    }

    static FormattedCharSequence composite(FormattedCharSequence... parts) {
        return fromList(ImmutableList.copyOf(parts));
    }

    static FormattedCharSequence composite(List<FormattedCharSequence> parts) {
        int size = parts.size();
        switch (size) {
            case 0:
                return EMPTY;
            case 1:
                return parts.get(0);
            case 2:
                return fromPair(parts.get(0), parts.get(1));
            default:
                return fromList(ImmutableList.copyOf(parts));
        }
    }

    static FormattedCharSequence fromPair(FormattedCharSequence first, FormattedCharSequence second) {
        return sink -> first.accept(sink) && second.accept(sink);
    }

    static FormattedCharSequence fromList(List<FormattedCharSequence> parts) {
        return sink -> {
            for (FormattedCharSequence formattedCharSequence : parts) {
                if (!formattedCharSequence.accept(sink)) {
                    return false;
                }
            }

            return true;
        };
    }
}
