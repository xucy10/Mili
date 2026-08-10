package net.minecraft.nbt;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.regex.Pattern;

public class StringTagVisitor implements TagVisitor {
    private static final Pattern UNQUOTED_KEY_MATCH = Pattern.compile("[A-Za-z._]+[A-Za-z0-9._+-]*");
    private final StringBuilder builder = new StringBuilder();

    public String build() {
        return this.builder.toString();
    }

    @Override
    public void visitString(StringTag tag) {
        this.builder.append(StringTag.quoteAndEscape(tag.value()));
    }

    @Override
    public void visitByte(ByteTag tag) {
        this.builder.append(tag.value()).append('b');
    }

    @Override
    public void visitShort(ShortTag tag) {
        this.builder.append(tag.value()).append('s');
    }

    @Override
    public void visitInt(IntTag tag) {
        this.builder.append(tag.value());
    }

    @Override
    public void visitLong(LongTag tag) {
        this.builder.append(tag.value()).append('L');
    }

    @Override
    public void visitFloat(FloatTag tag) {
        this.builder.append(tag.value()).append('f');
    }

    @Override
    public void visitDouble(DoubleTag tag) {
        this.builder.append(tag.value()).append('d');
    }

    @Override
    public void visitByteArray(ByteArrayTag tag) {
        this.builder.append("[B;");
        byte[] asByteArray = tag.getAsByteArray();

        for (int i = 0; i < asByteArray.length; i++) {
            if (i != 0) {
                this.builder.append(',');
            }

            this.builder.append(asByteArray[i]).append('B');
        }

        this.builder.append(']');
    }

    @Override
    public void visitIntArray(IntArrayTag tag) {
        this.builder.append("[I;");
        int[] asIntArray = tag.getAsIntArray();

        for (int i = 0; i < asIntArray.length; i++) {
            if (i != 0) {
                this.builder.append(',');
            }

            this.builder.append(asIntArray[i]);
        }

        this.builder.append(']');
    }

    @Override
    public void visitLongArray(LongArrayTag tag) {
        this.builder.append("[L;");
        long[] asLongArray = tag.getAsLongArray();

        for (int i = 0; i < asLongArray.length; i++) {
            if (i != 0) {
                this.builder.append(',');
            }

            this.builder.append(asLongArray[i]).append('L');
        }

        this.builder.append(']');
    }

    @Override
    public void visitList(ListTag tag) {
        this.builder.append('[');

        for (int i = 0; i < tag.size(); i++) {
            if (i != 0) {
                this.builder.append(',');
            }

            tag.get(i).accept(this);
        }

        this.builder.append(']');
    }

    @Override
    public void visitCompound(CompoundTag tag) {
        this.builder.append('{');
        List<Entry<String, Tag>> list = new ArrayList<>(tag.entrySet());
        list.sort(Entry.comparingByKey());

        for (int i = 0; i < list.size(); i++) {
            Entry<String, Tag> entry = list.get(i);
            if (i != 0) {
                this.builder.append(',');
            }

            this.handleKeyEscape(entry.getKey());
            this.builder.append(':');
            entry.getValue().accept(this);
        }

        this.builder.append('}');
    }

    private void handleKeyEscape(String key) {
        if (!key.equalsIgnoreCase("true") && !key.equalsIgnoreCase("false") && UNQUOTED_KEY_MATCH.matcher(key).matches()) {
            this.builder.append(key);
        } else {
            StringTag.quoteAndEscape(key, this.builder);
        }
    }

    @Override
    public void visitEnd(EndTag tag) {
        this.builder.append("END");
    }
}
