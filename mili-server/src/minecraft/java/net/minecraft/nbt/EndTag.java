package net.minecraft.nbt;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public final class EndTag implements Tag {
    private static final int SELF_SIZE_IN_BYTES = 8;
    public static final TagType<EndTag> TYPE = new TagType<EndTag>() {
        @Override
        public EndTag load(DataInput input, NbtAccounter accounter) {
            accounter.accountBytes(8L);
            return EndTag.INSTANCE;
        }

        @Override
        public StreamTagVisitor.ValueResult parse(DataInput input, StreamTagVisitor visitor, NbtAccounter accounter) {
            accounter.accountBytes(8L);
            return visitor.visitEnd();
        }

        @Override
        public void skip(DataInput input, int entries, NbtAccounter accounter) {
        }

        @Override
        public void skip(DataInput input, NbtAccounter accounter) {
        }

        @Override
        public String getName() {
            return "END";
        }

        @Override
        public String getPrettyName() {
            return "TAG_End";
        }
    };
    public static final EndTag INSTANCE = new EndTag();

    private EndTag() {
    }

    @Override
    public void write(DataOutput output) throws IOException {
    }

    @Override
    public int sizeInBytes() {
        return 8;
    }

    @Override
    public byte getId() {
        return Tag.TAG_END;
    }

    @Override
    public TagType<EndTag> getType() {
        return TYPE;
    }

    @Override
    public String toString() {
        StringTagVisitor stringTagVisitor = new StringTagVisitor();
        stringTagVisitor.visitEnd(this);
        return stringTagVisitor.build();
    }

    @Override
    public EndTag copy() {
        return this;
    }

    @Override
    public void accept(TagVisitor visitor) {
        visitor.visitEnd(this);
    }

    @Override
    public StreamTagVisitor.ValueResult accept(StreamTagVisitor visitor) {
        return visitor.visitEnd();
    }
}
