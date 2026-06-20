package net.minecraft.nbt;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import net.minecraft.util.Util;

public class SnbtPrinterTagVisitor implements TagVisitor {
    private static final Map<String, List<String>> KEY_ORDER = Util.make(Maps.newHashMap(), map -> {
        map.put("{}", Lists.newArrayList("DataVersion", "author", "size", "data", "entities", "palette", "palettes"));
        map.put("{}.data.[].{}", Lists.newArrayList("pos", "state", "nbt"));
        map.put("{}.entities.[].{}", Lists.newArrayList("blockPos", "pos"));
    });
    private static final Set<String> NO_INDENTATION = Sets.newHashSet("{}.size.[]", "{}.data.[].{}", "{}.palette.[].{}", "{}.entities.[].{}");
    private static final Pattern SIMPLE_VALUE = Pattern.compile("[A-Za-z0-9._+-]+");
    private static final String NAME_VALUE_SEPARATOR = String.valueOf(':');
    private static final String ELEMENT_SEPARATOR = String.valueOf(',');
    private static final String LIST_OPEN = "[";
    private static final String LIST_CLOSE = "]";
    private static final String LIST_TYPE_SEPARATOR = ";";
    private static final String ELEMENT_SPACING = " ";
    private static final String STRUCT_OPEN = "{";
    private static final String STRUCT_CLOSE = "}";
    private static final String NEWLINE = "\n";
    private final String indentation;
    private final int depth;
    private final List<String> path;
    private String result = "";

    public SnbtPrinterTagVisitor() {
        this("    ", 0, Lists.newArrayList());
    }

    public SnbtPrinterTagVisitor(String indentation, int depth, List<String> path) {
        this.indentation = indentation;
        this.depth = depth;
        this.path = path;
    }

    public String visit(Tag tag) {
        tag.accept(this);
        return this.result;
    }

    @Override
    public void visitString(StringTag tag) {
        this.result = StringTag.quoteAndEscape(tag.value());
    }

    @Override
    public void visitByte(ByteTag tag) {
        this.result = tag.value() + "b";
    }

    @Override
    public void visitShort(ShortTag tag) {
        this.result = tag.value() + "s";
    }

    @Override
    public void visitInt(IntTag tag) {
        this.result = String.valueOf(tag.value());
    }

    @Override
    public void visitLong(LongTag tag) {
        this.result = tag.value() + "L";
    }

    @Override
    public void visitFloat(FloatTag tag) {
        this.result = tag.value() + "f";
    }

    @Override
    public void visitDouble(DoubleTag tag) {
        this.result = tag.value() + "d";
    }

    @Override
    public void visitByteArray(ByteArrayTag tag) {
        StringBuilder stringBuilder = new StringBuilder("[").append("B").append(";");
        byte[] asByteArray = tag.getAsByteArray();

        for (int i = 0; i < asByteArray.length; i++) {
            stringBuilder.append(" ").append(asByteArray[i]).append("B");
            if (i != asByteArray.length - 1) {
                stringBuilder.append(ELEMENT_SEPARATOR);
            }
        }

        stringBuilder.append("]");
        this.result = stringBuilder.toString();
    }

    @Override
    public void visitIntArray(IntArrayTag tag) {
        StringBuilder stringBuilder = new StringBuilder("[").append("I").append(";");
        int[] asIntArray = tag.getAsIntArray();

        for (int i = 0; i < asIntArray.length; i++) {
            stringBuilder.append(" ").append(asIntArray[i]);
            if (i != asIntArray.length - 1) {
                stringBuilder.append(ELEMENT_SEPARATOR);
            }
        }

        stringBuilder.append("]");
        this.result = stringBuilder.toString();
    }

    @Override
    public void visitLongArray(LongArrayTag tag) {
        String string = "L";
        StringBuilder stringBuilder = new StringBuilder("[").append("L").append(";");
        long[] asLongArray = tag.getAsLongArray();

        for (int i = 0; i < asLongArray.length; i++) {
            stringBuilder.append(" ").append(asLongArray[i]).append("L");
            if (i != asLongArray.length - 1) {
                stringBuilder.append(ELEMENT_SEPARATOR);
            }
        }

        stringBuilder.append("]");
        this.result = stringBuilder.toString();
    }

    @Override
    public void visitList(ListTag tag) {
        if (tag.isEmpty()) {
            this.result = "[]";
        } else {
            StringBuilder stringBuilder = new StringBuilder("[");
            this.pushPath("[]");
            String string = NO_INDENTATION.contains(this.pathString()) ? "" : this.indentation;
            if (!string.isEmpty()) {
                stringBuilder.append("\n");
            }

            for (int i = 0; i < tag.size(); i++) {
                stringBuilder.append(Strings.repeat(string, this.depth + 1));
                stringBuilder.append(new SnbtPrinterTagVisitor(string, this.depth + 1, this.path).visit(tag.get(i)));
                if (i != tag.size() - 1) {
                    stringBuilder.append(ELEMENT_SEPARATOR).append(string.isEmpty() ? " " : "\n");
                }
            }

            if (!string.isEmpty()) {
                stringBuilder.append("\n").append(Strings.repeat(string, this.depth));
            }

            stringBuilder.append("]");
            this.result = stringBuilder.toString();
            this.popPath();
        }
    }

    @Override
    public void visitCompound(CompoundTag tag) {
        if (tag.isEmpty()) {
            this.result = "{}";
        } else {
            StringBuilder stringBuilder = new StringBuilder("{");
            this.pushPath("{}");
            String string = NO_INDENTATION.contains(this.pathString()) ? "" : this.indentation;
            if (!string.isEmpty()) {
                stringBuilder.append("\n");
            }

            Collection<String> keys = this.getKeys(tag);
            Iterator<String> iterator = keys.iterator();

            while (iterator.hasNext()) {
                String string1 = iterator.next();
                Tag tag1 = tag.get(string1);
                this.pushPath(string1);
                stringBuilder.append(Strings.repeat(string, this.depth + 1))
                    .append(handleEscapePretty(string1))
                    .append(NAME_VALUE_SEPARATOR)
                    .append(" ")
                    .append(new SnbtPrinterTagVisitor(string, this.depth + 1, this.path).visit(tag1));
                this.popPath();
                if (iterator.hasNext()) {
                    stringBuilder.append(ELEMENT_SEPARATOR).append(string.isEmpty() ? " " : "\n");
                }
            }

            if (!string.isEmpty()) {
                stringBuilder.append("\n").append(Strings.repeat(string, this.depth));
            }

            stringBuilder.append("}");
            this.result = stringBuilder.toString();
            this.popPath();
        }
    }

    private void popPath() {
        this.path.remove(this.path.size() - 1);
    }

    private void pushPath(String key) {
        this.path.add(key);
    }

    protected List<String> getKeys(CompoundTag tag) {
        Set<String> set = Sets.newHashSet(tag.keySet());
        List<String> list = Lists.newArrayList();
        List<String> list1 = KEY_ORDER.get(this.pathString());
        if (list1 != null) {
            for (String string : list1) {
                if (set.remove(string)) {
                    list.add(string);
                }
            }

            if (!set.isEmpty()) {
                set.stream().sorted().forEach(list::add);
            }
        } else {
            list.addAll(set);
            Collections.sort(list);
        }

        return list;
    }

    public String pathString() {
        return String.join(".", this.path);
    }

    protected static String handleEscapePretty(String text) {
        return SIMPLE_VALUE.matcher(text).matches() ? text : StringTag.quoteAndEscape(text);
    }

    @Override
    public void visitEnd(EndTag tag) {
    }
}
