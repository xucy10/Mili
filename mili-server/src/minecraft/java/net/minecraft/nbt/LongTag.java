package net.minecraft.nbt;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public record LongTag(long value) implements NumericTag {
    private static final int SELF_SIZE_IN_BYTES = 16;
    public static final TagType<LongTag> TYPE = new TagType.StaticSize<LongTag>() {
        @Override
        public LongTag load(DataInput input, NbtAccounter accounter) throws IOException {
            return LongTag.valueOf(readAccounted(input, accounter));
        }

        @Override
        public StreamTagVisitor.ValueResult parse(DataInput input, StreamTagVisitor visitor, NbtAccounter accounter) throws IOException {
            return visitor.visit(readAccounted(input, accounter));
        }

        private static long readAccounted(DataInput input, NbtAccounter accounter) throws IOException {
            accounter.accountBytes(16L);
            return input.readLong();
        }

        @Override
        public int size() {
            return 8;
        }

        @Override
        public String getName() {
            return "LONG";
        }

        @Override
        public String getPrettyName() {
            return "TAG_Long";
        }
    };

    @Deprecated(forRemoval = true)
    public LongTag(long value) {
        this.value = value;
    }

    public static LongTag valueOf(long value) {
        return value >= -128L && value <= 1024L ? LongTag.Cache.cache[(int)value - -128] : new LongTag(value);
    }

    @Override
    public void write(DataOutput output) throws IOException {
        output.writeLong(this.value);
    }

    @Override
    public int sizeInBytes() {
        return 16;
    }

    @Override
    public byte getId() {
        return Tag.TAG_LONG;
    }

    @Override
    public TagType<LongTag> getType() {
        return TYPE;
    }

    @Override
    public LongTag copy() {
        return this;
    }

    @Override
    public void accept(TagVisitor visitor) {
        visitor.visitLong(this);
    }

    @Override
    public long longValue() {
        return this.value;
    }

    @Override
    public int intValue() {
        return (int)(this.value & -1L);
    }

    @Override
    public short shortValue() {
        return (short)(this.value & 65535L);
    }

    @Override
    public byte byteValue() {
        return (byte)(this.value & 255L);
    }

    @Override
    public double doubleValue() {
        return this.value;
    }

    @Override
    public float floatValue() {
        return (float)this.value;
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
        stringTagVisitor.visitLong(this);
        return stringTagVisitor.build();
    }

    static class Cache {
        private static final int HIGH = 1024;
        private static final int LOW = -128;
        static final LongTag[] cache = new LongTag[1153];

        private Cache() {
        }

        static {
            for (int i = 0; i < cache.length; i++) {
                cache[i] = new LongTag(-128 + i);
            }
        }
    }
}
