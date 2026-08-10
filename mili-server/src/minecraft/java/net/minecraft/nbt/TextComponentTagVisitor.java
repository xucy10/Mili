package net.minecraft.nbt;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.slf4j.Logger;

public class TextComponentTagVisitor implements TagVisitor {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int INLINE_LIST_THRESHOLD = 8;
    private static final int MAX_DEPTH = 64;
    private static final int MAX_LENGTH = 128;
    private static final ChatFormatting SYNTAX_HIGHLIGHTING_KEY = ChatFormatting.AQUA;
    private static final ChatFormatting SYNTAX_HIGHLIGHTING_STRING = ChatFormatting.GREEN;
    private static final ChatFormatting SYNTAX_HIGHLIGHTING_NUMBER = ChatFormatting.GOLD;
    private static final ChatFormatting SYNTAX_HIGHLIGHTING_NUMBER_TYPE = ChatFormatting.RED;
    private static final Pattern SIMPLE_VALUE = Pattern.compile("[A-Za-z0-9._+-]+");
    private static final String LIST_OPEN = "[";
    private static final String LIST_CLOSE = "]";
    private static final String LIST_TYPE_SEPARATOR = ";";
    private static final String ELEMENT_SPACING = " ";
    private static final String STRUCT_OPEN = "{";
    private static final String STRUCT_CLOSE = "}";
    private static final String NEWLINE = "\n";
    private static final String NAME_VALUE_SEPARATOR = ": ";
    private static final String ELEMENT_SEPARATOR = String.valueOf(',');
    private static final String WRAPPED_ELEMENT_SEPARATOR = ELEMENT_SEPARATOR + "\n";
    private static final String SPACED_ELEMENT_SEPARATOR = ELEMENT_SEPARATOR + " ";
    private static final Component FOLDED = Component.literal("<...>").withStyle(ChatFormatting.GRAY);
    private static final Component BYTE_TYPE = Component.literal("b").withStyle(SYNTAX_HIGHLIGHTING_NUMBER_TYPE);
    private static final Component SHORT_TYPE = Component.literal("s").withStyle(SYNTAX_HIGHLIGHTING_NUMBER_TYPE);
    private static final Component INT_TYPE = Component.literal("I").withStyle(SYNTAX_HIGHLIGHTING_NUMBER_TYPE);
    private static final Component LONG_TYPE = Component.literal("L").withStyle(SYNTAX_HIGHLIGHTING_NUMBER_TYPE);
    private static final Component FLOAT_TYPE = Component.literal("f").withStyle(SYNTAX_HIGHLIGHTING_NUMBER_TYPE);
    private static final Component DOUBLE_TYPE = Component.literal("d").withStyle(SYNTAX_HIGHLIGHTING_NUMBER_TYPE);
    private static final Component BYTE_ARRAY_TYPE = Component.literal("B").withStyle(SYNTAX_HIGHLIGHTING_NUMBER_TYPE);
    private final String indentation;
    private int indentDepth;
    private int depth;
    private final MutableComponent result = Component.empty();

    public TextComponentTagVisitor(String indentation) {
        this.indentation = indentation;
    }

    public Component visit(Tag tag) {
        tag.accept(this);
        return this.result;
    }

    @Override
    public void visitString(StringTag tag) {
        String string = StringTag.quoteAndEscape(tag.value());
        String sub = string.substring(0, 1);
        Component component = Component.literal(string.substring(1, string.length() - 1)).withStyle(SYNTAX_HIGHLIGHTING_STRING);
        this.result.append(sub).append(component).append(sub);
    }

    @Override
    public void visitByte(ByteTag tag) {
        this.result.append(Component.literal(String.valueOf(tag.value())).withStyle(SYNTAX_HIGHLIGHTING_NUMBER)).append(BYTE_TYPE);
    }

    @Override
    public void visitShort(ShortTag tag) {
        this.result.append(Component.literal(String.valueOf(tag.value())).withStyle(SYNTAX_HIGHLIGHTING_NUMBER)).append(SHORT_TYPE);
    }

    @Override
    public void visitInt(IntTag tag) {
        this.result.append(Component.literal(String.valueOf(tag.value())).withStyle(SYNTAX_HIGHLIGHTING_NUMBER));
    }

    @Override
    public void visitLong(LongTag tag) {
        this.result.append(Component.literal(String.valueOf(tag.value())).withStyle(SYNTAX_HIGHLIGHTING_NUMBER)).append(LONG_TYPE);
    }

    @Override
    public void visitFloat(FloatTag tag) {
        this.result.append(Component.literal(String.valueOf(tag.value())).withStyle(SYNTAX_HIGHLIGHTING_NUMBER)).append(FLOAT_TYPE);
    }

    @Override
    public void visitDouble(DoubleTag tag) {
        this.result.append(Component.literal(String.valueOf(tag.value())).withStyle(SYNTAX_HIGHLIGHTING_NUMBER)).append(DOUBLE_TYPE);
    }

    @Override
    public void visitByteArray(ByteArrayTag tag) {
        this.result.append("[").append(BYTE_ARRAY_TYPE).append(";");
        byte[] asByteArray = tag.getAsByteArray();

        for (int i = 0; i < asByteArray.length && i < 128; i++) {
            MutableComponent mutableComponent = Component.literal(String.valueOf(asByteArray[i])).withStyle(SYNTAX_HIGHLIGHTING_NUMBER);
            this.result.append(" ").append(mutableComponent).append(BYTE_ARRAY_TYPE);
            if (i != asByteArray.length - 1) {
                this.result.append(ELEMENT_SEPARATOR);
            }
        }

        if (asByteArray.length > 128) {
            this.result.append(FOLDED);
        }

        this.result.append("]");
    }

    @Override
    public void visitIntArray(IntArrayTag tag) {
        this.result.append("[").append(INT_TYPE).append(";");
        int[] asIntArray = tag.getAsIntArray();

        for (int i = 0; i < asIntArray.length && i < 128; i++) {
            this.result.append(" ").append(Component.literal(String.valueOf(asIntArray[i])).withStyle(SYNTAX_HIGHLIGHTING_NUMBER));
            if (i != asIntArray.length - 1) {
                this.result.append(ELEMENT_SEPARATOR);
            }
        }

        if (asIntArray.length > 128) {
            this.result.append(FOLDED);
        }

        this.result.append("]");
    }

    @Override
    public void visitLongArray(LongArrayTag tag) {
        this.result.append("[").append(LONG_TYPE).append(";");
        long[] asLongArray = tag.getAsLongArray();

        for (int i = 0; i < asLongArray.length && i < 128; i++) {
            Component component = Component.literal(String.valueOf(asLongArray[i])).withStyle(SYNTAX_HIGHLIGHTING_NUMBER);
            this.result.append(" ").append(component).append(LONG_TYPE);
            if (i != asLongArray.length - 1) {
                this.result.append(ELEMENT_SEPARATOR);
            }
        }

        if (asLongArray.length > 128) {
            this.result.append(FOLDED);
        }

        this.result.append("]");
    }

    private static boolean shouldWrapListElements(ListTag tag) {
        if (tag.size() >= 8) {
            return false;
        } else {
            for (Tag tag1 : tag) {
                if (!(tag1 instanceof NumericTag)) {
                    return true;
                }
            }

            return false;
        }
    }

    @Override
    public void visitList(ListTag tag) {
        if (tag.isEmpty()) {
            this.result.append("[]");
        } else if (this.depth >= 64) {
            this.result.append("[").append(FOLDED).append("]");
        } else if (!shouldWrapListElements(tag)) {
            this.result.append("[");

            for (int i = 0; i < tag.size(); i++) {
                if (i != 0) {
                    this.result.append(SPACED_ELEMENT_SEPARATOR);
                }

                this.appendSubTag(tag.get(i), false);
            }

            this.result.append("]");
        } else {
            this.result.append("[");
            if (!this.indentation.isEmpty()) {
                this.result.append("\n");
            }

            String repeated = Strings.repeat(this.indentation, this.indentDepth + 1);

            for (int i1 = 0; i1 < tag.size() && i1 < 128; i1++) {
                this.result.append(repeated);
                this.appendSubTag(tag.get(i1), true);
                if (i1 != tag.size() - 1) {
                    this.result.append(this.indentation.isEmpty() ? SPACED_ELEMENT_SEPARATOR : WRAPPED_ELEMENT_SEPARATOR);
                }
            }

            if (tag.size() > 128) {
                this.result.append(repeated).append(FOLDED);
            }

            if (!this.indentation.isEmpty()) {
                this.result.append("\n" + Strings.repeat(this.indentation, this.indentDepth));
            }

            this.result.append("]");
        }
    }

    @Override
    public void visitCompound(CompoundTag tag) {
        if (tag.isEmpty()) {
            this.result.append("{}");
        } else if (this.depth >= 64) {
            this.result.append("{").append(FOLDED).append("}");
        } else {
            this.result.append("{");
            Collection<String> collection = tag.keySet();
            if (LOGGER.isDebugEnabled()) {
                List<String> list = Lists.newArrayList(tag.keySet());
                Collections.sort(list);
                collection = list;
            }

            if (!this.indentation.isEmpty()) {
                this.result.append("\n");
            }

            String repeated = Strings.repeat(this.indentation, this.indentDepth + 1);
            Iterator<String> iterator = collection.iterator();

            while (iterator.hasNext()) {
                String string = iterator.next();
                this.result.append(repeated).append(handleEscapePretty(string)).append(": ");
                this.appendSubTag(tag.get(string), true);
                if (iterator.hasNext()) {
                    this.result.append(this.indentation.isEmpty() ? SPACED_ELEMENT_SEPARATOR : WRAPPED_ELEMENT_SEPARATOR);
                }
            }

            if (!this.indentation.isEmpty()) {
                this.result.append("\n" + Strings.repeat(this.indentation, this.indentDepth));
            }

            this.result.append("}");
        }
    }

    private void appendSubTag(Tag tag, boolean indent) {
        if (indent) {
            this.indentDepth++;
        }

        this.depth++;

        try {
            tag.accept(this);
        } finally {
            if (indent) {
                this.indentDepth--;
            }

            this.depth--;
        }
    }

    protected static Component handleEscapePretty(String text) {
        if (SIMPLE_VALUE.matcher(text).matches()) {
            return Component.literal(text).withStyle(SYNTAX_HIGHLIGHTING_KEY);
        } else {
            String string = StringTag.quoteAndEscape(text);
            String sub = string.substring(0, 1);
            Component component = Component.literal(string.substring(1, string.length() - 1)).withStyle(SYNTAX_HIGHLIGHTING_KEY);
            return Component.literal(sub).append(component).append(sub);
        }
    }

    @Override
    public void visitEnd(EndTag tag) {
    }
}
