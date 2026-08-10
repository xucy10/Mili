package net.minecraft.network.chat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.StringDecomposer;

public class SubStringSource {
    private final String plainText;
    private final List<Style> charStyles;
    private final Int2IntFunction reverseCharModifier;

    private SubStringSource(String plainText, List<Style> charStyles, Int2IntFunction reverseCharModifier) {
        this.plainText = plainText;
        this.charStyles = ImmutableList.copyOf(charStyles);
        this.reverseCharModifier = reverseCharModifier;
    }

    public String getPlainText() {
        return this.plainText;
    }

    public List<FormattedCharSequence> substring(int fromIndex, int toIndex, boolean reversed) {
        if (toIndex == 0) {
            return ImmutableList.of();
        } else {
            List<FormattedCharSequence> list = Lists.newArrayList();
            Style style = this.charStyles.get(fromIndex);
            int i = fromIndex;

            for (int i1 = 1; i1 < toIndex; i1++) {
                int i2 = fromIndex + i1;
                Style style1 = this.charStyles.get(i2);
                if (!style1.equals(style)) {
                    String sub = this.plainText.substring(i, i2);
                    list.add(reversed ? FormattedCharSequence.backward(sub, style, this.reverseCharModifier) : FormattedCharSequence.forward(sub, style));
                    style = style1;
                    i = i2;
                }
            }

            if (i < fromIndex + toIndex) {
                String sub1 = this.plainText.substring(i, fromIndex + toIndex);
                list.add(reversed ? FormattedCharSequence.backward(sub1, style, this.reverseCharModifier) : FormattedCharSequence.forward(sub1, style));
            }

            return reversed ? Lists.reverse(list) : list;
        }
    }

    public static SubStringSource create(FormattedText formattedText) {
        return create(formattedText, modifier -> modifier, text -> text);
    }

    public static SubStringSource create(FormattedText formattedText, Int2IntFunction reverseCharModifier, UnaryOperator<String> textTransformer) {
        StringBuilder stringBuilder = new StringBuilder();
        List<Style> list = Lists.newArrayList();
        formattedText.visit((style, content) -> {
            StringDecomposer.iterateFormatted(content, style, (positionInCurrentSequence, style1, codePoint) -> {
                stringBuilder.appendCodePoint(codePoint);
                int i = Character.charCount(codePoint);

                for (int i1 = 0; i1 < i; i1++) {
                    list.add(style1);
                }

                return true;
            });
            return Optional.empty();
        }, Style.EMPTY);
        return new SubStringSource(textTransformer.apply(stringBuilder.toString()), list, reverseCharModifier);
    }
}
