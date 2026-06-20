package net.minecraft.world.level.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import org.jspecify.annotations.Nullable;

public interface ValueOutput {
    <T> void store(String key, Codec<T> codec, T value);

    <T> void storeNullable(String key, Codec<T> codec, @Nullable T value);

    @Deprecated
    <T> void store(MapCodec<T> codec, T value);

    void putBoolean(String key, boolean value);

    void putByte(String key, byte value);

    void putShort(String key, short value);

    void putInt(String key, int value);

    void putLong(String key, long value);

    void putFloat(String key, float value);

    void putDouble(String key, double value);

    void putString(String key, String value);

    void putIntArray(String key, int[] value);

    ValueOutput child(String key);

    ValueOutput.ValueOutputList childrenList(String key);

    <T> ValueOutput.TypedOutputList<T> list(String key, Codec<T> elementCodec);

    void discard(String key);

    boolean isEmpty();

    public interface TypedOutputList<T> {
        void add(T element);

        boolean isEmpty();
    }

    public interface ValueOutputList {
        ValueOutput addChild();

        void discardLast();

        boolean isEmpty();
    }
}
