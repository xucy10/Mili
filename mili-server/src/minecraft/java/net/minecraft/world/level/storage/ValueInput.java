package net.minecraft.world.level.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;

public interface ValueInput {
    <T> Optional<T> read(String key, Codec<T> codec);

    @Deprecated
    <T> Optional<T> read(MapCodec<T> codec);

    Optional<ValueInput> child(String key);

    ValueInput childOrEmpty(String key);

    Optional<ValueInput.ValueInputList> childrenList(String key);

    ValueInput.ValueInputList childrenListOrEmpty(String key);

    <T> Optional<ValueInput.TypedInputList<T>> list(String key, Codec<T> elementCodec);

    <T> ValueInput.TypedInputList<T> listOrEmpty(String key, Codec<T> elementCodec);

    boolean getBooleanOr(String key, boolean defaultValue);

    byte getByteOr(String key, byte defaultValue);

    int getShortOr(String key, short defaultValue);

    Optional<Integer> getInt(String key);

    int getIntOr(String key, int defaultValue);

    long getLongOr(String key, long defaultValue);

    Optional<Long> getLong(String key);

    float getFloatOr(String key, float defaultValue);

    double getDoubleOr(String key, double defaultValue);

    Optional<String> getString(String key);

    String getStringOr(String key, String defaultValue);

    Optional<int[]> getIntArray(String key);

    @Deprecated
    HolderLookup.Provider lookup();

    public interface TypedInputList<T> extends Iterable<T> {
        boolean isEmpty();

        Stream<T> stream();
    }

    public interface ValueInputList extends Iterable<ValueInput> {
        boolean isEmpty();

        Stream<ValueInput> stream();
    }
}
