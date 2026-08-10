package net.minecraft.network.syncher;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public interface EntityDataSerializer<T> {
    StreamCodec<? super RegistryFriendlyByteBuf, T> codec();

    default EntityDataAccessor<T> createAccessor(int id) {
        return new EntityDataAccessor<>(id, this);
    }

    T copy(T value);

    static <T> EntityDataSerializer<T> forValueType(StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        return (ForValueType<T>) () -> codec;
    }

    public interface ForValueType<T> extends EntityDataSerializer<T> {
        @Override
        default T copy(T value) {
            return value;
        }
    }
}
