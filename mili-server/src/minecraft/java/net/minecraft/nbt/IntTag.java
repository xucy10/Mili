package net.minecraft.nbt;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public record IntTag(int value) implements NumericTag {
    private static final int SELF_SIZE_IN_BYTES = 12;
    public static final TagType<IntTag> TYPE = new TagType.StaticSize<IntTag>() {
        @Override
        public IntTag load(DataInput input, NbtAccounter accounter) throws IOException {
            return IntTag.valueOf(readAccounted(input, accounter));
        }

        @Override
        public StreamTagVisitor.ValueResult parse(DataInput input, StreamTagVisitor visitor, NbtAccounter accounter) throws IOException {
            return visitor.visit(readAccounted(input, accounter));
        }

        private static int readAccounted(DataInput input, NbtAccounter accounter) throws IOException {
            accounter.accountBytes(12L);
            return input.readInt();
        }

        @Override
        public int size() {
            return 4;
        }

        @Override
        public String getName() {
            return "INT";
        }

        @Override
        public String getPrettyName() {
            return "TAG_Int";
        }
    };

    @Deprecated(forRemoval = true)
    public IntTag(int value) {
        this.value = value;
    }

    public static IntTag valueOf(int value) {
        return value >= -128 && value <= 1024 ? IntTag.Cache.cache[value - -128] : new IntTag(value);
    }

    @Override
    public void write(DataOutput output) throws IOException {
        output.writeInt(this.value);
    }

    @Override
    public int sizeInBytes() {
        return 12;
    }

    @Override
    public byte getId() {
        return Tag.TAG_INT;
    }

    @Override
    public TagType<IntTag> getType() {
        return TYPE;
    }

    @Override
    public IntTag copy() {
        return this;
    }

    @Override
    public void accept(TagVisitor visitor) {
        visitor.visitInt(this);
    }

    @Override
    public long longValue() {
        return this.value;
    }

    @Override
    public int intValue() {
        return this.value;
    }

    @Override
    public short shortValue() {
        return (short)(this.value & 65535);
    }

    @Override
    public byte byteValue() {
        return (byte)(this.value & 0xFF);
    }

    @Override
    public double doubleValue() {
        return this.value;
    }

    @Override
    public float floatValue() {
        return this.value;
    }

    @Override
    public Number box() {
        return this.value;
    }

    @Override
    public StreamTagVisitor.ValueResult accept(StreamTagVisitor visitor) {
        return visitor.visit(this.value);
    }

    @Override
    public String toString() {
        StringTagVisitor stringTagVisitor = new StringTagVisitor();
        stringTagVisitor.visitInt(this);
        return stringTagVisitor.build();
    }

    static class Cache {
        private static final int HIGH = 1024;
        private static final int LOW = -128;
        static final IntTag[] cache = new IntTag[1153];

        private Cache() {
        }

        static {
            for (int i = 0; i < cache.length; i++) {
                cache[i] = new IntTag(-128 + i);
            }
        }
    }
}
