package net.minecraft.world.level.storage;

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Streams;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.DataResult.Error;
import com.mojang.serialization.DataResult.Success;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagType;
import net.minecraft.util.ProblemReporter;
import org.jspecify.annotations.Nullable;

public class TagValueInput implements ValueInput {
    private final ProblemReporter problemReporter;
    private final ValueInputContextHelper context;
    public final CompoundTag input;

    private TagValueInput(ProblemReporter problemReporter, ValueInputContextHelper context, CompoundTag input) {
        this.problemReporter = problemReporter;
        this.context = context;
        this.input = input;
    }

    // Paper start - utility methods
    public static ValueInput createGlobal(
        final ProblemReporter problemReporter,
        final CompoundTag compoundTag
    ) {
        return create(problemReporter, net.minecraft.server.MinecraftServer.getServer().registryAccess(), compoundTag);
    }
    // Paper end - utility methods

    public static ValueInput create(ProblemReporter problemReporter, HolderLookup.Provider lookup, CompoundTag input) {
        return new TagValueInput(problemReporter, new ValueInputContextHelper(lookup, NbtOps.INSTANCE), input);
    }

    public static ValueInput.ValueInputList create(ProblemReporter problemReporter, HolderLookup.Provider lookup, List<CompoundTag> input) {
        return new TagValueInput.CompoundListWrapper(problemReporter, new ValueInputContextHelper(lookup, NbtOps.INSTANCE), input);
    }

    @Override
    public <T> Optional<T> read(String key, Codec<T> codec) {
        Tag tag = this.input.get(key);
        if (tag == null) {
            return Optional.empty();
        } else {
            return switch (codec.parse(this.context.ops(), tag)) {
                case Success<T> success -> Optional.of(success.value());
                case Error<T> error -> {
                    this.problemReporter.report(new TagValueInput.DecodeFromFieldFailedProblem(key, tag, error));
                    yield error.partialValue();
                }
                default -> throw new MatchException(null, null);
            };
        }
    }

    @Override
    public <T> Optional<T> read(MapCodec<T> codec) {
        DynamicOps<Tag> dynamicOps = this.context.ops();

        return switch (dynamicOps.getMap(this.input).flatMap(mapLike -> codec.decode(dynamicOps, (MapLike<Tag>)mapLike))) {
            case Success<T> success -> Optional.of(success.value());
            case Error<T> error -> {
                this.problemReporter.report(new TagValueInput.DecodeFromMapFailedProblem(error));
                yield error.partialValue();
            }
            default -> throw new MatchException(null, null);
        };
    }

    private <T extends Tag> @Nullable T getOptionalTypedTag(String key, TagType<T> type) {
        Tag tag = this.input.get(key);
        if (tag == null) {
            return null;
        } else {
            TagType<?> type1 = tag.getType();
            if (type1 != type) {
                this.problemReporter.report(new TagValueInput.UnexpectedTypeProblem(key, type, type1));
                return null;
            } else {
                return (T)tag;
            }
        }
    }

    private @Nullable NumericTag getNumericTag(String key) {
        Tag tag = this.input.get(key);
        if (tag == null) {
            return null;
        } else if (tag instanceof NumericTag numericTag) {
            return numericTag;
        } else {
            this.problemReporter.report(new TagValueInput.UnexpectedNonNumberProblem(key, tag.getType()));
            return null;
        }
    }

    @Override
    public Optional<ValueInput> child(String key) {
        CompoundTag compoundTag = this.getOptionalTypedTag(key, CompoundTag.TYPE);
        return compoundTag != null ? Optional.of(this.wrapChild(key, compoundTag)) : Optional.empty();
    }

    @Override
    public ValueInput childOrEmpty(String key) {
        CompoundTag compoundTag = this.getOptionalTypedTag(key, CompoundTag.TYPE);
        return compoundTag != null ? this.wrapChild(key, compoundTag) : this.context.empty();
    }

    @Override
    public Optional<ValueInput.ValueInputList> childrenList(String key) {
        ListTag listTag = this.getOptionalTypedTag(key, ListTag.TYPE);
        return listTag != null ? Optional.of(this.wrapList(key, this.context, listTag)) : Optional.empty();
    }

    @Override
    public ValueInput.ValueInputList childrenListOrEmpty(String key) {
        ListTag listTag = this.getOptionalTypedTag(key, ListTag.TYPE);
        return listTag != null ? this.wrapList(key, this.context, listTag) : this.context.emptyList();
    }

    @Override
    public <T> Optional<ValueInput.TypedInputList<T>> list(String key, Codec<T> elementCodec) {
        ListTag listTag = this.getOptionalTypedTag(key, ListTag.TYPE);
        return listTag != null ? Optional.of(this.wrapTypedList(key, listTag, elementCodec)) : Optional.empty();
    }

    @Override
    public <T> ValueInput.TypedInputList<T> listOrEmpty(String key, Codec<T> elementCodec) {
        ListTag listTag = this.getOptionalTypedTag(key, ListTag.TYPE);
        return listTag != null ? this.wrapTypedList(key, listTag, elementCodec) : this.context.emptyTypedList();
    }

    @Override
    public boolean getBooleanOr(String key, boolean defaultValue) {
        NumericTag numericTag = this.getNumericTag(key);
        return numericTag != null ? numericTag.byteValue() != 0 : defaultValue;
    }

    @Override
    public byte getByteOr(String key, byte defaultValue) {
        NumericTag numericTag = this.getNumericTag(key);
        return numericTag != null ? numericTag.byteValue() : defaultValue;
    }

    @Override
    public int getShortOr(String key, short defaultValue) {
        NumericTag numericTag = this.getNumericTag(key);
        return numericTag != null ? numericTag.shortValue() : defaultValue;
    }

    @Override
    public Optional<Integer> getInt(String key) {
        NumericTag numericTag = this.getNumericTag(key);
        return numericTag != null ? Optional.of(numericTag.intValue()) : Optional.empty();
    }

    @Override
    public int getIntOr(String key, int defaultValue) {
        NumericTag numericTag = this.getNumericTag(key);
        return numericTag != null ? numericTag.intValue() : defaultValue;
    }

    @Override
    public long getLongOr(String key, long defaultValue) {
        NumericTag numericTag = this.getNumericTag(key);
        return numericTag != null ? numericTag.longValue() : defaultValue;
    }

    @Override
    public Optional<Long> getLong(String key) {
        NumericTag numericTag = this.getNumericTag(key);
        return numericTag != null ? Optional.of(numericTag.longValue()) : Optional.empty();
    }

    @Override
    public float getFloatOr(String key, float defaultValue) {
        NumericTag numericTag = this.getNumericTag(key);
        return numericTag != null ? numericTag.floatValue() : defaultValue;
    }

    @Override
    public double getDoubleOr(String key, double defaultValue) {
        NumericTag numericTag = this.getNumericTag(key);
        return numericTag != null ? numericTag.doubleValue() : defaultValue;
    }

    @Override
    public Optional<String> getString(String key) {
        StringTag stringTag = this.getOptionalTypedTag(key, StringTag.TYPE);
        return stringTag != null ? Optional.of(stringTag.value()) : Optional.empty();
    }

    @Override
    public String getStringOr(String key, String defaultValue) {
        StringTag stringTag = this.getOptionalTypedTag(key, StringTag.TYPE);
        return stringTag != null ? stringTag.value() : defaultValue;
    }

    @Override
    public Optional<int[]> getIntArray(String key) {
        IntArrayTag intArrayTag = this.getOptionalTypedTag(key, IntArrayTag.TYPE);
        return intArrayTag != null ? Optional.of(intArrayTag.getAsIntArray()) : Optional.empty();
    }

    @Override
    public HolderLookup.Provider lookup() {
        return this.context.lookup();
    }

    private ValueInput wrapChild(String key, CompoundTag tag) {
        return (ValueInput)(tag.isEmpty()
            ? this.context.empty()
            : new TagValueInput(this.problemReporter.forChild(new ProblemReporter.FieldPathElement(key)), this.context, tag));
    }

    static ValueInput wrapChild(ProblemReporter problemReporter, ValueInputContextHelper context, CompoundTag tag) {
        return (ValueInput)(tag.isEmpty() ? context.empty() : new TagValueInput(problemReporter, context, tag));
    }

    private ValueInput.ValueInputList wrapList(String key, ValueInputContextHelper context, ListTag tag) {
        return (ValueInput.ValueInputList)(tag.isEmpty() ? context.emptyList() : new TagValueInput.ListWrapper(this.problemReporter, key, context, tag));
    }

    private <T> ValueInput.TypedInputList<T> wrapTypedList(String key, ListTag tag, Codec<T> codec) {
        return (ValueInput.TypedInputList<T>)(tag.isEmpty()
            ? this.context.emptyTypedList()
            : new TagValueInput.TypedListWrapper<>(this.problemReporter, key, this.context, codec, tag));
    }

    static class CompoundListWrapper implements ValueInput.ValueInputList {
        private final ProblemReporter problemReporter;
        private final ValueInputContextHelper context;
        private final List<CompoundTag> list;

        public CompoundListWrapper(ProblemReporter problemReporter, ValueInputContextHelper context, List<CompoundTag> list) {
            this.problemReporter = problemReporter;
            this.context = context;
            this.list = list;
        }

        ValueInput wrapChild(int index, CompoundTag tag) {
            return TagValueInput.wrapChild(this.problemReporter.forChild(new ProblemReporter.IndexedPathElement(index)), this.context, tag);
        }

        @Override
        public boolean isEmpty() {
            return this.list.isEmpty();
        }

        @Override
        public Stream<ValueInput> stream() {
            return Streams.mapWithIndex(this.list.stream(), (compoundTag, l) -> this.wrapChild((int)l, compoundTag));
        }

        @Override
        public Iterator<ValueInput> iterator() {
            final ListIterator<CompoundTag> listIterator = this.list.listIterator();
            return new AbstractIterator<ValueInput>() {
                @Override
                protected @Nullable ValueInput computeNext() {
                    if (listIterator.hasNext()) {
                        int i = listIterator.nextIndex();
                        CompoundTag compoundTag = listIterator.next();
                        return CompoundListWrapper.this.wrapChild(i, compoundTag);
                    } else {
                        return this.endOfData();
                    }
                }
            };
        }
    }

    public record DecodeFromFieldFailedProblem(String name, Tag tag, Error<?> error) implements ProblemReporter.Problem {
        @Override
        public String description() {
            return "Failed to decode value '" + this.tag + "' from field '" + this.name + "': " + this.error.message();
        }
    }

    public record DecodeFromListFailedProblem(String name, int index, Tag tag, Error<?> error) implements ProblemReporter.Problem {
        @Override
        public String description() {
            return "Failed to decode value '" + this.tag + "' from field '" + this.name + "' at index " + this.index + "': " + this.error.message();
        }
    }

    public record DecodeFromMapFailedProblem(Error<?> error) implements ProblemReporter.Problem {
        @Override
        public String description() {
            return "Failed to decode from map: " + this.error.message();
        }
    }

    static class ListWrapper implements ValueInput.ValueInputList {
        private final ProblemReporter problemReporter;
        private final String name;
        final ValueInputContextHelper context;
        private final ListTag list;

        ListWrapper(ProblemReporter problemReporter, String name, ValueInputContextHelper context, ListTag list) {
            this.problemReporter = problemReporter;
            this.name = name;
            this.context = context;
            this.list = list;
        }

        @Override
        public boolean isEmpty() {
            return this.list.isEmpty();
        }

        ProblemReporter reporterForChild(int index) {
            return this.problemReporter.forChild(new ProblemReporter.IndexedFieldPathElement(this.name, index));
        }

        void reportIndexUnwrapProblem(int index, Tag tag) {
            this.problemReporter.report(new TagValueInput.UnexpectedListElementTypeProblem(this.name, index, CompoundTag.TYPE, tag.getType()));
        }

        @Override
        public Stream<ValueInput> stream() {
            return Streams.<Tag, ValueInput>mapWithIndex(this.list.stream(), (tag, l) -> {
                if (tag instanceof CompoundTag compoundTag) {
                    return TagValueInput.wrapChild(this.reporterForChild((int)l), this.context, compoundTag);
                } else {
                    this.reportIndexUnwrapProblem((int)l, tag);
                    return null;
                }
            }).filter(Objects::nonNull);
        }

        @Override
        public Iterator<ValueInput> iterator() {
            final Iterator<Tag> iterator = this.list.iterator();
            return new AbstractIterator<ValueInput>() {
                private int index;

                @Override
                protected @Nullable ValueInput computeNext() {
                    while (iterator.hasNext()) {
                        Tag tag = iterator.next();
                        int i = this.index++;
                        if (tag instanceof CompoundTag compoundTag) {
                            return TagValueInput.wrapChild(ListWrapper.this.reporterForChild(i), ListWrapper.this.context, compoundTag);
                        }

                        ListWrapper.this.reportIndexUnwrapProblem(i, tag);
                    }

                    return this.endOfData();
                }
            };
        }
    }

    static class TypedListWrapper<T> implements ValueInput.TypedInputList<T> {
        private final ProblemReporter problemReporter;
        private final String name;
        final ValueInputContextHelper context;
        final Codec<T> codec;
        private final ListTag list;

        TypedListWrapper(ProblemReporter problemReporter, String name, ValueInputContextHelper context, Codec<T> codec, ListTag list) {
            this.problemReporter = problemReporter;
            this.name = name;
            this.context = context;
            this.codec = codec;
            this.list = list;
        }

        @Override
        public boolean isEmpty() {
            return this.list.isEmpty();
        }

        void reportIndexUnwrapProblem(int index, Tag tag, Error<?> error) {
            this.problemReporter.report(new TagValueInput.DecodeFromListFailedProblem(this.name, index, tag, error));
        }

        @Override
        public Stream<T> stream() {
            return Streams.<Tag, T>mapWithIndex(this.list.stream(), (tag, l) -> {
                return (T)(switch (this.codec.parse(this.context.ops(), tag)) {
                    case Success<T> success -> (Object)success.value();
                    case Error<T> error -> {
                        this.reportIndexUnwrapProblem((int)l, tag, error);
                        yield error.partialValue().orElse(null);
                    }
                    default -> throw new MatchException(null, null);
                });
            }).filter(Objects::nonNull);
        }

        @Override
        public Iterator<T> iterator() {
            final ListIterator<Tag> listIterator = this.list.listIterator();
            return new AbstractIterator<T>() {
                @Override
                protected @Nullable T computeNext() {
                    while (listIterator.hasNext()) {
                        int i = listIterator.nextIndex();
                        Tag tag = listIterator.next();
                        switch (TypedListWrapper.this.codec.parse((DynamicOps<T>)TypedListWrapper.this.context.ops(), (T)tag)) {
                            case Success<T> success:
                                return success.value();
                            case Error<T> error:
                                TypedListWrapper.this.reportIndexUnwrapProblem(i, tag, error);
                                if (!error.partialValue().isPresent()) {
                                    break;
                                }

                                return error.partialValue().get();
                            default:
                                throw new MatchException(null, null);
                        }
                    }

                    return (T)this.endOfData();
                }
            };
        }
    }

    public record UnexpectedListElementTypeProblem(String name, int index, TagType<?> expected, TagType<?> actual) implements ProblemReporter.Problem {
        @Override
        public String description() {
            return "Expected list '"
                + this.name
                + "' to contain at index "
                + this.index
                + " value of type "
                + this.expected.getName()
                + ", but got "
                + this.actual.getName();
        }
    }

    public record UnexpectedNonNumberProblem(String name, TagType<?> actual) implements ProblemReporter.Problem {
        @Override
        public String description() {
            return "Expected field '" + this.name + "' to contain number, but got " + this.actual.getName();
        }
    }

    public record UnexpectedTypeProblem(String name, TagType<?> expected, TagType<?> actual) implements ProblemReporter.Problem {
        @Override
        public String description() {
            return "Expected field '" + this.name + "' to contain value of type " + this.expected.getName() + ", but got " + this.actual.getName();
        }
    }
}
