package net.minecraft.world.level.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.DataResult.Error;
import com.mojang.serialization.DataResult.Success;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.util.ProblemReporter;
import org.jspecify.annotations.Nullable;

public class TagValueOutput implements ValueOutput {
    private final ProblemReporter problemReporter;
    private final DynamicOps<Tag> ops;
    private final CompoundTag output;

    TagValueOutput(ProblemReporter problemReporter, DynamicOps<Tag> ops, CompoundTag output) {
        this.problemReporter = problemReporter;
        this.ops = ops;
        this.output = output;
    }

    // Paper start - utility methods
    public static TagValueOutput createWrappingGlobal(
        final ProblemReporter problemReporter,
        final CompoundTag output
    ) {
        return new TagValueOutput(problemReporter, NbtOps.INSTANCE, output);
    }

    public static TagValueOutput createWrappingWithContext(
        final ProblemReporter problemReporter,
        final HolderLookup.Provider lookup,
        final CompoundTag output
    ) {
        return new TagValueOutput(problemReporter, lookup.createSerializationContext(NbtOps.INSTANCE), output);
    }
    // Paper end - utility methods

    public static TagValueOutput createWithContext(ProblemReporter problemReporter, HolderLookup.Provider lookup) {
        return new TagValueOutput(problemReporter, lookup.createSerializationContext(NbtOps.INSTANCE), new CompoundTag());
    }

    public static TagValueOutput createWithoutContext(ProblemReporter problemReporter) {
        return new TagValueOutput(problemReporter, NbtOps.INSTANCE, new CompoundTag());
    }

    @Override
    public <T> void store(String key, Codec<T> codec, T value) {
        switch (codec.encodeStart(this.ops, value)) {
            case Success<Tag> success:
                this.output.put(key, success.value());
                break;
            case Error<Tag> error:
                this.problemReporter.report(new TagValueOutput.EncodeToFieldFailedProblem(key, value, error));
                error.partialValue().ifPresent(tag -> this.output.put(key, tag));
                break;
            default:
                throw new MatchException(null, null);
        }
    }

    @Override
    public <T> void storeNullable(String key, Codec<T> codec, @Nullable T value) {
        if (value != null) {
            this.store(key, codec, value);
        }
    }

    @Override
    public <T> void store(MapCodec<T> codec, T value) {
        switch (codec.encoder().encodeStart(this.ops, value)) {
            case Success<Tag> success:
                this.output.merge((CompoundTag)success.value());
                break;
            case Error<Tag> error:
                this.problemReporter.report(new TagValueOutput.EncodeToMapFailedProblem(value, error));
                error.partialValue().ifPresent(tag -> this.output.merge((CompoundTag)tag));
                break;
            default:
                throw new MatchException(null, null);
        }
    }

    @Override
    public void putBoolean(String key, boolean value) {
        this.output.putBoolean(key, value);
    }

    @Override
    public void putByte(String key, byte value) {
        this.output.putByte(key, value);
    }

    @Override
    public void putShort(String key, short value) {
        this.output.putShort(key, value);
    }

    @Override
    public void putInt(String key, int value) {
        this.output.putInt(key, value);
    }

    @Override
    public void putLong(String key, long value) {
        this.output.putLong(key, value);
    }

    @Override
    public void putFloat(String key, float value) {
        this.output.putFloat(key, value);
    }

    @Override
    public void putDouble(String key, double value) {
        this.output.putDouble(key, value);
    }

    @Override
    public void putString(String key, String value) {
        this.output.putString(key, value);
    }

    @Override
    public void putIntArray(String key, int[] value) {
        this.output.putIntArray(key, value);
    }

    private ProblemReporter reporterForChild(String name) {
        return this.problemReporter.forChild(new ProblemReporter.FieldPathElement(name));
    }

    @Override
    public ValueOutput child(String key) {
        CompoundTag compoundTag = new CompoundTag();
        this.output.put(key, compoundTag);
        return new TagValueOutput(this.reporterForChild(key), this.ops, compoundTag);
    }

    @Override
    public ValueOutput.ValueOutputList childrenList(String key) {
        ListTag listTag = new ListTag();
        this.output.put(key, listTag);
        return new TagValueOutput.ListWrapper(key, this.problemReporter, this.ops, listTag);
    }

    @Override
    public <T> ValueOutput.TypedOutputList<T> list(String key, Codec<T> elementCodec) {
        ListTag listTag = new ListTag();
        this.output.put(key, listTag);
        return new TagValueOutput.TypedListWrapper<>(this.problemReporter, key, this.ops, elementCodec, listTag);
    }

    @Override
    public void discard(String key) {
        this.output.remove(key);
    }

    @Override
    public boolean isEmpty() {
        return this.output.isEmpty();
    }

    public CompoundTag buildResult() {
        return this.output;
    }

    public record EncodeToFieldFailedProblem(String name, Object value, Error<?> error) implements ProblemReporter.Problem {
        @Override
        public String description() {
            return "Failed to encode value '" + this.value + "' to field '" + this.name + "': " + this.error.message();
        }
    }

    public record EncodeToListFailedProblem(String name, Object value, Error<?> error) implements ProblemReporter.Problem {
        @Override
        public String description() {
            return "Failed to append value '" + this.value + "' to list '" + this.name + "': " + this.error.message();
        }
    }

    public record EncodeToMapFailedProblem(Object value, Error<?> error) implements ProblemReporter.Problem {
        @Override
        public String description() {
            return "Failed to merge value '" + this.value + "' to an object: " + this.error.message();
        }
    }

    static class ListWrapper implements ValueOutput.ValueOutputList {
        private final String fieldName;
        private final ProblemReporter problemReporter;
        private final DynamicOps<Tag> ops;
        private final ListTag output;

        ListWrapper(String fieldName, ProblemReporter problemReporter, DynamicOps<Tag> ops, ListTag output) {
            this.fieldName = fieldName;
            this.problemReporter = problemReporter;
            this.ops = ops;
            this.output = output;
        }

        @Override
        public ValueOutput addChild() {
            int size = this.output.size();
            CompoundTag compoundTag = new CompoundTag();
            this.output.add(compoundTag);
            return new TagValueOutput(this.problemReporter.forChild(new ProblemReporter.IndexedFieldPathElement(this.fieldName, size)), this.ops, compoundTag);
        }

        @Override
        public void discardLast() {
            this.output.removeLast();
        }

        @Override
        public boolean isEmpty() {
            return this.output.isEmpty();
        }
    }

    static class TypedListWrapper<T> implements ValueOutput.TypedOutputList<T> {
        private final ProblemReporter problemReporter;
        private final String name;
        private final DynamicOps<Tag> ops;
        private final Codec<T> codec;
        private final ListTag output;

        TypedListWrapper(ProblemReporter problemReporter, String name, DynamicOps<Tag> ops, Codec<T> codec, ListTag output) {
            this.problemReporter = problemReporter;
            this.name = name;
            this.ops = ops;
            this.codec = codec;
            this.output = output;
        }

        @Override
        public void add(T element) {
            switch (this.codec.encodeStart(this.ops, element)) {
                case Success<Tag> success:
                    this.output.add(success.value());
                    break;
                case Error<Tag> error:
                    this.problemReporter.report(new TagValueOutput.EncodeToListFailedProblem(this.name, element, error));
                    error.partialValue().ifPresent(this.output::add);
                    break;
                default:
                    throw new MatchException(null, null);
            }
        }

        @Override
        public boolean isEmpty() {
            return this.output.isEmpty();
        }
    }
}
