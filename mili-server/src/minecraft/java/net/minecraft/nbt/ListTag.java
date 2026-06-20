package net.minecraft.nbt;

import com.google.common.annotations.VisibleForTesting;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

public final class ListTag extends AbstractList<Tag> implements CollectionTag {
    private static final String WRAPPER_MARKER = "";
    private static final int SELF_SIZE_IN_BYTES = 36;
    public static final TagType<ListTag> TYPE = new TagType.VariableSize<ListTag>() {
        @Override
        public ListTag load(DataInput input, NbtAccounter accounter) throws IOException {
            accounter.pushDepth();

            ListTag var3;
            try {
                var3 = loadList(input, accounter);
            } finally {
                accounter.popDepth();
            }

            return var3;
        }

        private static ListTag loadList(DataInput input, NbtAccounter accounter) throws IOException {
            accounter.accountBytes(36L);
            byte _byte = input.readByte();
            int listCount = readListCount(input);
            if (_byte == 0 && listCount > 0) {
                throw new NbtFormatException("Missing type on ListTag");
            } else {
                accounter.accountBytes(4L, listCount);
                TagType<?> type = TagTypes.getType(_byte);
                ListTag listTag = new ListTag(new ArrayList<>(listCount));

                for (int i = 0; i < listCount; i++) {
                    listTag.addAndUnwrap(type.load(input, accounter));
                }

                return listTag;
            }
        }

        @Override
        public StreamTagVisitor.ValueResult parse(DataInput input, StreamTagVisitor visitor, NbtAccounter accounter) throws IOException {
            accounter.pushDepth();

            StreamTagVisitor.ValueResult var4;
            try {
                var4 = parseList(input, visitor, accounter);
            } finally {
                accounter.popDepth();
            }

            return var4;
        }

        private static StreamTagVisitor.ValueResult parseList(DataInput input, StreamTagVisitor visitor, NbtAccounter accounter) throws IOException {
            accounter.accountBytes(36L);
            TagType<?> type = TagTypes.getType(input.readByte());
            int listCount = readListCount(input);
            switch (visitor.visitList(type, listCount)) {
                case HALT:
                    return StreamTagVisitor.ValueResult.HALT;
                case BREAK:
                    type.skip(input, listCount, accounter);
                    return visitor.visitContainerEnd();
                default:
                    accounter.accountBytes(4L, listCount);
                    int i = 0;

                    while (true) {
                        label41: {
                            if (i < listCount) {
                                switch (visitor.visitElement(type, i)) {
                                    case HALT:
                                        return StreamTagVisitor.ValueResult.HALT;
                                    case BREAK:
                                        type.skip(input, accounter);
                                        break;
                                    case SKIP:
                                        type.skip(input, accounter);
                                        break label41;
                                    default:
                                        switch (type.parse(input, visitor, accounter)) {
                                            case HALT:
                                                return StreamTagVisitor.ValueResult.HALT;
                                            case BREAK:
                                                break;
                                            default:
                                                break label41;
                                        }
                                }
                            }

                            int i1 = listCount - 1 - i;
                            if (i1 > 0) {
                                type.skip(input, i1, accounter);
                            }

                            return visitor.visitContainerEnd();
                        }

                        i++;
                    }
            }
        }

        private static int readListCount(DataInput dataInput) throws IOException {
            int _int = dataInput.readInt();
            if (_int < 0) {
                throw new NbtFormatException("ListTag length cannot be negative: " + _int);
            } else {
                return _int;
            }
        }

        @Override
        public void skip(DataInput input, NbtAccounter accounter) throws IOException {
            accounter.pushDepth();

            try {
                TagType<?> type = TagTypes.getType(input.readByte());
                int _int = input.readInt();
                type.skip(input, _int, accounter);
            } finally {
                accounter.popDepth();
            }
        }

        @Override
        public String getName() {
            return "LIST";
        }

        @Override
        public String getPrettyName() {
            return "TAG_List";
        }
    };
    private final List<Tag> list;

    public ListTag() {
        this(new ArrayList<>());
    }

    public ListTag(List<Tag> list) {
        this.list = list;
    }

    private static Tag tryUnwrap(CompoundTag tag) {
        if (tag.size() == 1) {
            Tag tag1 = tag.get("");
            if (tag1 != null) {
                return tag1;
            }
        }

        return tag;
    }

    private static boolean isWrapper(CompoundTag tag) {
        return tag.size() == 1 && tag.contains("");
    }

    private static Tag wrapIfNeeded(byte elementType, Tag tag) {
        if (elementType != Tag.TAG_COMPOUND) {
            return tag;
        } else {
            return tag instanceof CompoundTag compoundTag && !isWrapper(compoundTag) ? compoundTag : wrapElement(tag);
        }
    }

    private static CompoundTag wrapElement(Tag tag) {
        return new CompoundTag(Map.of("", tag));
    }

    @Override
    public void write(DataOutput output) throws IOException {
        byte b = this.identifyRawElementType();
        output.writeByte(b);
        output.writeInt(this.list.size());

        for (Tag tag : this.list) {
            wrapIfNeeded(b, tag).write(output);
        }
    }

    @VisibleForTesting
    public byte identifyRawElementType() {
        byte b = Tag.TAG_END;

        for (Tag tag : this.list) {
            byte id = tag.getId();
            if (b == 0) {
                b = id;
            } else if (b != id) {
                return Tag.TAG_COMPOUND;
            }
        }

        return b;
    }

    public void addAndUnwrap(Tag tag) {
        if (tag instanceof CompoundTag compoundTag) {
            this.add(tryUnwrap(compoundTag));
        } else {
            this.add(tag);
        }
    }

    @Override
    public int sizeInBytes() {
        int i = 36;
        i += 4 * this.list.size();

        for (Tag tag : this.list) {
            i += tag.sizeInBytes();
        }

        return i;
    }

    @Override
    public byte getId() {
        return Tag.TAG_LIST;
    }

    @Override
    public TagType<ListTag> getType() {
        return TYPE;
    }

    @Override
    public String toString() {
        StringTagVisitor stringTagVisitor = new StringTagVisitor();
        stringTagVisitor.visitList(this);
        return stringTagVisitor.build();
    }

    @Override
    public Tag remove(int index) {
        return this.list.remove(index);
    }

    @Override
    public boolean isEmpty() {
        return this.list.isEmpty();
    }

    public Optional<CompoundTag> getCompound(int index) {
        return this.getNullable(index) instanceof CompoundTag compoundTag ? Optional.of(compoundTag) : Optional.empty();
    }

    public CompoundTag getCompoundOrEmpty(int index) {
        return this.getCompound(index).orElseGet(CompoundTag::new);
    }

    public Optional<ListTag> getList(int index) {
        return this.getNullable(index) instanceof ListTag listTag ? Optional.of(listTag) : Optional.empty();
    }

    public ListTag getListOrEmpty(int index) {
        return this.getList(index).orElseGet(ListTag::new);
    }

    public Optional<Short> getShort(int index) {
        return this.getOptional(index).flatMap(Tag::asShort);
    }

    public short getShortOr(int index, short defaultValue) {
        return this.getNullable(index) instanceof NumericTag numericTag ? numericTag.shortValue() : defaultValue;
    }

    public Optional<Integer> getInt(int index) {
        return this.getOptional(index).flatMap(Tag::asInt);
    }

    public int getIntOr(int index, int defaultValue) {
        return this.getNullable(index) instanceof NumericTag numericTag ? numericTag.intValue() : defaultValue;
    }

    public Optional<int[]> getIntArray(int index) {
        return this.getNullable(index) instanceof IntArrayTag intArrayTag ? Optional.of(intArrayTag.getAsIntArray()) : Optional.empty();
    }

    public Optional<long[]> getLongArray(int index) {
        return this.getNullable(index) instanceof LongArrayTag longArrayTag ? Optional.of(longArrayTag.getAsLongArray()) : Optional.empty();
    }

    public Optional<Double> getDouble(int index) {
        return this.getOptional(index).flatMap(Tag::asDouble);
    }

    public double getDoubleOr(int index, double defaultValue) {
        return this.getNullable(index) instanceof NumericTag numericTag ? numericTag.doubleValue() : defaultValue;
    }

    public Optional<Float> getFloat(int index) {
        return this.getOptional(index).flatMap(Tag::asFloat);
    }

    public float getFloatOr(int index, float defaultValue) {
        return this.getNullable(index) instanceof NumericTag numericTag ? numericTag.floatValue() : defaultValue;
    }

    public Optional<String> getString(int index) {
        return this.getOptional(index).flatMap(Tag::asString);
    }

    public String getStringOr(int index, String defaultValue) {
        return this.getNullable(index) instanceof StringTag(String var8) ? var8 : defaultValue;
    }

    private @Nullable Tag getNullable(int index) {
        return index >= 0 && index < this.list.size() ? this.list.get(index) : null;
    }

    private Optional<Tag> getOptional(int index) {
        return Optional.ofNullable(this.getNullable(index));
    }

    @Override
    public int size() {
        return this.list.size();
    }

    @Override
    public Tag get(int index) {
        return this.list.get(index);
    }

    @Override
    public Tag set(int i, Tag tag) {
        return this.list.set(i, tag);
    }

    @Override
    public void add(int i, Tag tag) {
        this.list.add(i, tag);
    }

    @Override
    public boolean setTag(int index, Tag tag) {
        this.list.set(index, tag);
        return true;
    }

    @Override
    public boolean addTag(int index, Tag tag) {
        this.list.add(index, tag);
        return true;
    }

    @Override
    public ListTag copy() {
        List<Tag> list = new ArrayList<>(this.list.size());

        for (Tag tag : this.list) {
            list.add(tag.copy());
        }

        return new ListTag(list);
    }

    @Override
    public Optional<ListTag> asList() {
        return Optional.of(this);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof ListTag && Objects.equals(this.list, ((ListTag)other).list);
    }

    @Override
    public int hashCode() {
        return this.list.hashCode();
    }

    @Override
    public Stream<Tag> stream() {
        return super.stream();
    }

    public Stream<CompoundTag> compoundStream() {
        return this.stream().mapMulti((tag, consumer) -> {
            if (tag instanceof CompoundTag compoundTag) {
                consumer.accept(compoundTag);
            }
        });
    }

    @Override
    public void accept(TagVisitor visitor) {
        visitor.visitList(this);
    }

    @Override
    public void clear() {
        this.list.clear();
    }

    @Override
    public StreamTagVisitor.ValueResult accept(StreamTagVisitor visitor) {
        byte b = this.identifyRawElementType();
        switch (visitor.visitList(TagTypes.getType(b), this.list.size())) {
            case HALT:
                return StreamTagVisitor.ValueResult.HALT;
            case BREAK:
                return visitor.visitContainerEnd();
            default:
                int i = 0;

                while (i < this.list.size()) {
                    Tag tag = wrapIfNeeded(b, this.list.get(i));
                    switch (visitor.visitElement(tag.getType(), i)) {
                        case HALT:
                            return StreamTagVisitor.ValueResult.HALT;
                        case BREAK:
                            return visitor.visitContainerEnd();
                        default:
                            switch (tag.accept(visitor)) {
                                case HALT:
                                    return StreamTagVisitor.ValueResult.HALT;
                                case BREAK:
                                    return visitor.visitContainerEnd();
                            }
                        case SKIP:
                            i++;
                    }
                }

                return visitor.visitContainerEnd();
        }
    }
}
