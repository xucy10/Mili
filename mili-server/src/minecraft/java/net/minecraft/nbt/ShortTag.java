package net.minecraft.nbt;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public record ShortTag(short value) implements NumericTag {
    private static final int SELF_SIZE_IN_BYTES = 10;
    public static final TagType<ShortTag> TYPE = new TagType.StaticSize<ShortTag>() {
        @Override
        public ShortTag load(DataInput input, NbtAccounter accounter) throws IOException {
            return ShortTag.valueOf(readAccounted(input, accounter));
        }

        @Override
        public StreamTagVisitor.ValueResult parse(DataInput input, StreamTagVisitor visitor, NbtAccounter accounter) throws IOException {
            return visitor.visit(readAccounted(input, accounter));
        }

        private static short readAccounted(DataInput input, NbtAccounter accounter) throws IOException {
            accounter.accountBytes(10L);
            return input.readShort();
        }

        @Override
        public int size() {
            return 2;
        }

        @Override
        public String getName() {
            return "SHORT";
        }

        @Override
        public String getPrettyName() {
            return "TAG_Short";
        }
    };

    @Deprecated(forRemoval = true)
    public ShortTag(short value) {
        this.value = value;
    }

    public static ShortTag valueOf(short value) {
        return value >= -128 && value <= 1024 ? ShortTag.Cache.cache[value - -128] : new ShortTag(value);
    }

    @Override
    public void write(DataOutput output) throws IOException {
        output.writeShort(this.value);
    }

    @Override
    public int sizeInBytes() {
        return 10;
    }

    @Override
    public byte getId() {
        return Tag.TAG_SHORT;
    }

    @Override
    public TagType<ShortTag> getType() {
        return TYPE;
    }

    @Override
    public ShortTag copy() {
        return this;
    }

    @Override
    public void accept(TagVisitor visitor) {
        visitor.visitShort(this);
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
        return this.value;
    }

    @Override
    public byte byteValue() {
        return (byte)(this.value & 255);
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
        stringTagVisitor.visitShort(this);
        return stringTagVisitor.build();
    }

    static class Cache {
        private static final int HIGH = 1024;
        private static final int LOW = -128;
        static final ShortTag[] cache = new ShortTag[1153];

        private Cache() {
        }

        static {
            for (int i = 0; i < cache.length; i++) {
                cache[i] = new ShortTag((short)(-128 + i));
            }
        }
    }
}
