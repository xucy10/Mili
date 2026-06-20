package net.minecraft.nbt;

import java.io.DataInput;
import java.io.IOException;

public interface TagType<T extends Tag> {
    T load(DataInput input, NbtAccounter accounter) throws IOException;

    StreamTagVisitor.ValueResult parse(DataInput input, StreamTagVisitor visitor, NbtAccounter accounter) throws IOException;

    default void parseRoot(DataInput input, StreamTagVisitor visitor, NbtAccounter nbtAccounter) throws IOException {
        switch (visitor.visitRootEntry(this)) {
            case CONTINUE:
                this.parse(input, visitor, nbtAccounter);
            case HALT:
            default:
                break;
            case BREAK:
                this.skip(input, nbtAccounter);
        }
    }

    void skip(DataInput input, int entries, NbtAccounter accounter) throws IOException;

    void skip(DataInput input, NbtAccounter accounter) throws IOException;

    String getName();

    String getPrettyName();

    static TagType<EndTag> createInvalid(final int id) {
        return new TagType<EndTag>() {
            private IOException createException() {
                return new IOException("Invalid tag id: " + id);
            }

            @Override
            public EndTag load(DataInput input, NbtAccounter accounter) throws IOException {
                throw this.createException();
            }

            @Override
            public StreamTagVisitor.ValueResult parse(DataInput input, StreamTagVisitor visitor, NbtAccounter accounter) throws IOException {
                throw this.createException();
            }

            @Override
            public void skip(DataInput input, int entries, NbtAccounter accounter) throws IOException {
                throw this.createException();
            }

            @Override
            public void skip(DataInput input, NbtAccounter accounter) throws IOException {
                throw this.createException();
            }

            @Override
            public String getName() {
                return "INVALID[" + id + "]";
            }

            @Override
            public String getPrettyName() {
                return "UNKNOWN_" + id;
            }
        };
    }

    public interface StaticSize<T extends Tag> extends TagType<T> {
        @Override
        default void skip(DataInput input, NbtAccounter accounter) throws IOException {
            input.skipBytes(this.size());
        }

        @Override
        default void skip(DataInput input, int entries, NbtAccounter accounter) throws IOException {
            input.skipBytes(this.size() * entries);
        }

        int size();
    }

    public interface VariableSize<T extends Tag> extends TagType<T> {
        @Override
        default void skip(DataInput input, int entries, NbtAccounter accounter) throws IOException {
            for (int i = 0; i < entries; i++) {
                this.skip(input, accounter);
            }
        }
    }
}
