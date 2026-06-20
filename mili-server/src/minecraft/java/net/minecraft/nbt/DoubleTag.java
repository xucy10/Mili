package net.minecraft.nbt;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import net.minecraft.util.Mth;

public record DoubleTag(double value) implements NumericTag {
    private static final int SELF_SIZE_IN_BYTES = 16;
    public static final DoubleTag ZERO = new DoubleTag(0.0);
    public static final TagType<DoubleTag> TYPE = new TagType.StaticSize<DoubleTag>() {
        @Override
        public DoubleTag load(DataInput input, NbtAccounter accounter) throws IOException {
            return DoubleTag.valueOf(readAccounted(input, accounter));
        }

        @Override
        public StreamTagVisitor.ValueResult parse(DataInput input, StreamTagVisitor visitor, NbtAccounter accounter) throws IOException {
            return visitor.visit(readAccounted(input, accounter));
        }

        private static double readAccounted(DataInput input, NbtAccounter accounter) throws IOException {
            accounter.accountBytes(16L);
            return input.readDouble();
        }

        @Override
        public int size() {
            return 8;
        }

        @Override
        public String getName() {
            return "DOUBLE";
        }

        @Override
        public String getPrettyName() {
            return "TAG_Double";
        }
    };

    @Deprecated(forRemoval = true)
    public DoubleTag(double value) {
        this.value = value;
    }

    public static DoubleTag valueOf(double value) {
        return value == 0.0 ? ZERO : new DoubleTag(value);
    }

    @Override
    public void write(DataOutput output) throws IOException {
        output.writeDouble(this.value);
    }

    @Override
    public int sizeInBytes() {
        return 16;
    }

    @Override
    public byte getId() {
        return Tag.TAG_DOUBLE;
    }

    @Override
    public TagType<DoubleTag> getType() {
        return TYPE;
    }

    @Override
    public DoubleTag copy() {
        return this;
    }

    @Override
    public void accept(TagVisitor visitor) {
        visitor.visitDouble(this);
    }

    @Override
    public long longValue() {
        return (long)Math.floor(this.value);
    }

    @Override
    public int intValue() {
        return Mth.floor(this.value);
    }

    @Override
    public short shortValue() {
        return (short)(Mth.floor(this.value) & 65535);
    }

    @Override
    public byte byteValue() {
        return (byte)(Mth.floor(this.value) & 0xFF);
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
        stringTagVisitor.visitDouble(this);
        return stringTagVisitor.build();
    }
}
