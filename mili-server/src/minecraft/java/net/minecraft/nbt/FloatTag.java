package net.minecraft.nbt;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import net.minecraft.util.Mth;

public record FloatTag(float value) implements NumericTag {
    private static final int SELF_SIZE_IN_BYTES = 12;
    public static final FloatTag ZERO = new FloatTag(0.0F);
    public static final TagType<FloatTag> TYPE = new TagType.StaticSize<FloatTag>() {
        @Override
        public FloatTag load(DataInput input, NbtAccounter accounter) throws IOException {
            return FloatTag.valueOf(readAccounted(input, accounter));
        }

        @Override
        public StreamTagVisitor.ValueResult parse(DataInput input, StreamTagVisitor visitor, NbtAccounter accounter) throws IOException {
            return visitor.visit(readAccounted(input, accounter));
        }

        private static float readAccounted(DataInput input, NbtAccounter accounter) throws IOException {
            accounter.accountBytes(12L);
            return input.readFloat();
        }

        @Override
        public int size() {
            return 4;
        }

        @Override
        public String getName() {
            return "FLOAT";
        }

        @Override
        public String getPrettyName() {
            return "TAG_Float";
        }
    };

    @Deprecated(forRemoval = true)
    public FloatTag(float value) {
        this.value = value;
    }

    public static FloatTag valueOf(float value) {
        return value == 0.0F ? ZERO : new FloatTag(value);
    }

    @Override
    public void write(DataOutput output) throws IOException {
        output.writeFloat(this.value);
    }

    @Override
    public int sizeInBytes() {
        return 12;
    }

    @Override
    public byte getId() {
        return Tag.TAG_FLOAT;
    }

    @Override
    public TagType<FloatTag> getType() {
        return TYPE;
    }

    @Override
    public FloatTag copy() {
        return this;
    }

    @Override
    public void accept(TagVisitor visitor) {
        visitor.visitFloat(this);
    }

    @Override
    public long longValue() {
        return (long)this.value;
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
        stringTagVisitor.visitFloat(this);
        return stringTagVisitor.build();
    }
}
