package net.minecraft.network.codec;

import com.google.common.base.Suppliers;
import com.mojang.datafixers.util.Function10;
import com.mojang.datafixers.util.Function11;
import com.mojang.datafixers.util.Function12;
import com.mojang.datafixers.util.Function3;
import com.mojang.datafixers.util.Function4;
import com.mojang.datafixers.util.Function5;
import com.mojang.datafixers.util.Function6;
import com.mojang.datafixers.util.Function7;
import com.mojang.datafixers.util.Function8;
import com.mojang.datafixers.util.Function9;
import io.netty.buffer.ByteBuf;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public interface StreamCodec<B, V> extends StreamDecoder<B, V>, StreamEncoder<B, V> {
    static <B, V> StreamCodec<B, V> of(final StreamEncoder<B, V> encoder, final StreamDecoder<B, V> decoder) {
        return new StreamCodec<B, V>() {
            @Override
            public V decode(B buffer) {
                return decoder.decode(buffer);
            }

            @Override
            public void encode(B buffer, V value) {
                encoder.encode(buffer, value);
            }
        };
    }

    static <B, V> StreamCodec<B, V> ofMember(final StreamMemberEncoder<B, V> encoder, final StreamDecoder<B, V> decoder) {
        return new StreamCodec<B, V>() {
            @Override
            public V decode(B buffer) {
                return decoder.decode(buffer);
            }

            @Override
            public void encode(B buffer, V value) {
                encoder.encode(value, buffer);
            }
        };
    }

    static <B, V> StreamCodec<B, V> unit(final V expectedValue) {
        return new StreamCodec<B, V>() {
            @Override
            public V decode(B buffer) {
                return expectedValue;
            }

            @Override
            public void encode(B buffer, V value) {
                if (!value.equals(expectedValue)) {
                    throw new IllegalStateException("Can't encode '" + value + "', expected '" + expectedValue + "'");
                }
            }
        };
    }

    default <O> StreamCodec<B, O> apply(StreamCodec.CodecOperation<B, V, O> operation) {
        return operation.apply(this);
    }

    default <O> StreamCodec<B, O> map(final Function<? super V, ? extends O> factory, final Function<? super O, ? extends V> getter) {
        return new StreamCodec<B, O>() {
            @Override
            public O decode(B buffer) {
                return (O)factory.apply(StreamCodec.this.decode(buffer));
            }

            @Override
            public void encode(B buffer, O value) {
                StreamCodec.this.encode(buffer, (V)getter.apply(value));
            }
        };
    }

    default <O extends ByteBuf> StreamCodec<O, V> mapStream(final Function<O, ? extends B> bufferFactory) {
        return new StreamCodec<O, V>() {
            @Override
            public V decode(O buffer) {
                B object = (B)bufferFactory.apply(buffer);
                return StreamCodec.this.decode(object);
            }

            @Override
            public void encode(O buffer, V value) {
                B object = (B)bufferFactory.apply(buffer);
                StreamCodec.this.encode(object, value);
            }
        };
    }

    default <U> StreamCodec<B, U> dispatch(
        final Function<? super U, ? extends V> keyGetter, final Function<? super V, ? extends StreamCodec<? super B, ? extends U>> codecGetter
    ) {
        return new StreamCodec<B, U>() {
            @Override
            public U decode(B buffer) {
                V object = StreamCodec.this.decode(buffer);
                StreamCodec<? super B, ? extends U> streamCodec = (StreamCodec<? super B, ? extends U>)codecGetter.apply(object);
                return (U)streamCodec.decode(buffer);
            }

            @Override
            public void encode(B buffer, U value) {
                V object = (V)keyGetter.apply(value);
                StreamCodec<B, U> streamCodec = (StreamCodec<B, U>)codecGetter.apply(object);
                StreamCodec.this.encode(buffer, object);
                streamCodec.encode(buffer, value);
            }
        };
    }

    static <B, C, T1> StreamCodec<B, C> composite(final StreamCodec<? super B, T1> codec, final Function<C, T1> getter, final Function<T1, C> factory) {
        return new StreamCodec<B, C>() {
            @Override
            public C decode(B buffer) {
                T1 object = codec.decode(buffer);
                return factory.apply(object);
            }

            @Override
            public void encode(B buffer, C value) {
                codec.encode(buffer, getter.apply(value));
            }
        };
    }

    static <B, C, T1, T2> StreamCodec<B, C> composite(
        final StreamCodec<? super B, T1> codec1,
        final Function<C, T1> getter1,
        final StreamCodec<? super B, T2> codec2,
        final Function<C, T2> getter2,
        final BiFunction<T1, T2, C> factory
    ) {
        return new StreamCodec<B, C>() {
            @Override
            public C decode(B buffer) {
                T1 object = codec1.decode(buffer);
                T2 object1 = codec2.decode(buffer);
                return factory.apply(object, object1);
            }

            @Override
            public void encode(B buffer, C value) {
                codec1.encode(buffer, getter1.apply(value));
                codec2.encode(buffer, getter2.apply(value));
            }
        };
    }

    static <B, C, T1, T2, T3> StreamCodec<B, C> composite(
        final StreamCodec<? super B, T1> codec1,
        final Function<C, T1> getter1,
        final StreamCodec<? super B, T2> codec2,
        final Function<C, T2> getter2,
        final StreamCodec<? super B, T3> codec3,
        final Function<C, T3> getter3,
        final Function3<T1, T2, T3, C> factory
    ) {
        return new StreamCodec<B, C>() {
            @Override
            public C decode(B buffer) {
                T1 object = codec1.decode(buffer);
                T2 object1 = codec2.decode(buffer);
                T3 object2 = codec3.decode(buffer);
                return factory.apply(object, object1, object2);
            }

            @Override
            public void encode(B buffer, C value) {
                codec1.encode(buffer, getter1.apply(value));
                codec2.encode(buffer, getter2.apply(value));
                codec3.encode(buffer, getter3.apply(value));
            }
        };
    }

    static <B, C, T1, T2, T3, T4> StreamCodec<B, C> composite(
        final StreamCodec<? super B, T1> codec1,
        final Function<C, T1> getter1,
        final StreamCodec<? super B, T2> codec2,
        final Function<C, T2> getter2,
        final StreamCodec<? super B, T3> codec3,
        final Function<C, T3> getter3,
        final StreamCodec<? super B, T4> codec4,
        final Function<C, T4> getter4,
        final Function4<T1, T2, T3, T4, C> factory
    ) {
        return new StreamCodec<B, C>() {
            @Override
            public C decode(B buffer) {
                T1 object = codec1.decode(buffer);
                T2 object1 = codec2.decode(buffer);
                T3 object2 = codec3.decode(buffer);
                T4 object3 = codec4.decode(buffer);
                return factory.apply(object, object1, object2, object3);
            }

            @Override
            public void encode(B buffer, C value) {
                codec1.encode(buffer, getter1.apply(value));
                codec2.encode(buffer, getter2.apply(value));
                codec3.encode(buffer, getter3.apply(value));
                codec4.encode(buffer, getter4.apply(value));
            }
        };
    }

    static <B, C, T1, T2, T3, T4, T5> StreamCodec<B, C> composite(
        final StreamCodec<? super B, T1> codec1,
        final Function<C, T1> getter1,
        final StreamCodec<? super B, T2> codec2,
        final Function<C, T2> getter2,
        final StreamCodec<? super B, T3> codec3,
        final Function<C, T3> getter3,
        final StreamCodec<? super B, T4> codec4,
        final Function<C, T4> getter4,
        final StreamCodec<? super B, T5> codec5,
        final Function<C, T5> getter5,
        final Function5<T1, T2, T3, T4, T5, C> factory
    ) {
        return new StreamCodec<B, C>() {
            @Override
            public C decode(B buffer) {
                T1 object = codec1.decode(buffer);
                T2 object1 = codec2.decode(buffer);
                T3 object2 = codec3.decode(buffer);
                T4 object3 = codec4.decode(buffer);
                T5 object4 = codec5.decode(buffer);
                return factory.apply(object, object1, object2, object3, object4);
            }

            @Override
            public void encode(B buffer, C value) {
                codec1.encode(buffer, getter1.apply(value));
                codec2.encode(buffer, getter2.apply(value));
                codec3.encode(buffer, getter3.apply(value));
                codec4.encode(buffer, getter4.apply(value));
                codec5.encode(buffer, getter5.apply(value));
            }
        };
    }

    static <B, C, T1, T2, T3, T4, T5, T6> StreamCodec<B, C> composite(
        final StreamCodec<? super B, T1> codec1,
        final Function<C, T1> getter1,
        final StreamCodec<? super B, T2> codec2,
        final Function<C, T2> getter2,
        final StreamCodec<? super B, T3> codec3,
        final Function<C, T3> getter3,
        final StreamCodec<? super B, T4> codec4,
        final Function<C, T4> getter4,
        final StreamCodec<? super B, T5> codec5,
        final Function<C, T5> getter5,
        final StreamCodec<? super B, T6> codec6,
        final Function<C, T6> getter6,
        final Function6<T1, T2, T3, T4, T5, T6, C> factory
    ) {
        return new StreamCodec<B, C>() {
            @Override
            public C decode(B buffer) {
                T1 object = codec1.decode(buffer);
                T2 object1 = codec2.decode(buffer);
                T3 object2 = codec3.decode(buffer);
                T4 object3 = codec4.decode(buffer);
                T5 object4 = codec5.decode(buffer);
                T6 object5 = codec6.decode(buffer);
                return factory.apply(object, object1, object2, object3, object4, object5);
            }

            @Override
            public void encode(B buffer, C value) {
                codec1.encode(buffer, getter1.apply(value));
                codec2.encode(buffer, getter2.apply(value));
                codec3.encode(buffer, getter3.apply(value));
                codec4.encode(buffer, getter4.apply(value));
                codec5.encode(buffer, getter5.apply(value));
                codec6.encode(buffer, getter6.apply(value));
            }
        };
    }

    static <B, C, T1, T2, T3, T4, T5, T6, T7> StreamCodec<B, C> composite(
        final StreamCodec<? super B, T1> codec1,
        final Function<C, T1> getter1,
        final StreamCodec<? super B, T2> codec2,
        final Function<C, T2> getter2,
        final StreamCodec<? super B, T3> codec3,
        final Function<C, T3> getter3,
        final StreamCodec<? super B, T4> codec4,
        final Function<C, T4> getter4,
        final StreamCodec<? super B, T5> codec5,
        final Function<C, T5> getter5,
        final StreamCodec<? super B, T6> codec6,
        final Function<C, T6> getter6,
        final StreamCodec<? super B, T7> codec7,
        final Function<C, T7> getter7,
        final Function7<T1, T2, T3, T4, T5, T6, T7, C> factory
    ) {
        return new StreamCodec<B, C>() {
            @Override
            public C decode(B buffer) {
                T1 object = codec1.decode(buffer);
                T2 object1 = codec2.decode(buffer);
                T3 object2 = codec3.decode(buffer);
                T4 object3 = codec4.decode(buffer);
                T5 object4 = codec5.decode(buffer);
                T6 object5 = codec6.decode(buffer);
                T7 object6 = codec7.decode(buffer);
                return factory.apply(object, object1, object2, object3, object4, object5, object6);
            }

            @Override
            public void encode(B buffer, C value) {
                codec1.encode(buffer, getter1.apply(value));
                codec2.encode(buffer, getter2.apply(value));
                codec3.encode(buffer, getter3.apply(value));
                codec4.encode(buffer, getter4.apply(value));
                codec5.encode(buffer, getter5.apply(value));
                codec6.encode(buffer, getter6.apply(value));
                codec7.encode(buffer, getter7.apply(value));
            }
        };
    }

    static <B, C, T1, T2, T3, T4, T5, T6, T7, T8> StreamCodec<B, C> composite(
        final StreamCodec<? super B, T1> codec1,
        final Function<C, T1> getter1,
        final StreamCodec<? super B, T2> codec2,
        final Function<C, T2> getter2,
        final StreamCodec<? super B, T3> codec3,
        final Function<C, T3> getter3,
        final StreamCodec<? super B, T4> codec4,
        final Function<C, T4> getter4,
        final StreamCodec<? super B, T5> codec5,
        final Function<C, T5> getter5,
        final StreamCodec<? super B, T6> codec6,
        final Function<C, T6> getter6,
        final StreamCodec<? super B, T7> codec7,
        final Function<C, T7> getter7,
        final StreamCodec<? super B, T8> codec8,
        final Function<C, T8> getter8,
        final Function8<T1, T2, T3, T4, T5, T6, T7, T8, C> factory
    ) {
        return new StreamCodec<B, C>() {
            @Override
            public C decode(B buffer) {
                T1 object = codec1.decode(buffer);
                T2 object1 = codec2.decode(buffer);
                T3 object2 = codec3.decode(buffer);
                T4 object3 = codec4.decode(buffer);
                T5 object4 = codec5.decode(buffer);
                T6 object5 = codec6.decode(buffer);
                T7 object6 = codec7.decode(buffer);
                T8 object7 = codec8.decode(buffer);
                return factory.apply(object, object1, object2, object3, object4, object5, object6, object7);
            }

            @Override
            public void encode(B buffer, C value) {
                codec1.encode(buffer, getter1.apply(value));
                codec2.encode(buffer, getter2.apply(value));
                codec3.encode(buffer, getter3.apply(value));
                codec4.encode(buffer, getter4.apply(value));
                codec5.encode(buffer, getter5.apply(value));
                codec6.encode(buffer, getter6.apply(value));
                codec7.encode(buffer, getter7.apply(value));
                codec8.encode(buffer, getter8.apply(value));
            }
        };
    }

    static <B, C, T1, T2, T3, T4, T5, T6, T7, T8, T9> StreamCodec<B, C> composite(
        final StreamCodec<? super B, T1> codec1,
        final Function<C, T1> getter1,
        final StreamCodec<? super B, T2> codec2,
        final Function<C, T2> getter2,
        final StreamCodec<? super B, T3> codec3,
        final Function<C, T3> getter3,
        final StreamCodec<? super B, T4> codec4,
        final Function<C, T4> getter4,
        final StreamCodec<? super B, T5> codec5,
        final Function<C, T5> getter5,
        final StreamCodec<? super B, T6> codec6,
        final Function<C, T6> getter6,
        final StreamCodec<? super B, T7> codec7,
        final Function<C, T7> getter7,
        final StreamCodec<? super B, T8> codec8,
        final Function<C, T8> getter8,
        final StreamCodec<? super B, T9> codec9,
        final Function<C, T9> getter9,
        final Function9<T1, T2, T3, T4, T5, T6, T7, T8, T9, C> factory
    ) {
        return new StreamCodec<B, C>() {
            @Override
            public C decode(B buffer) {
                T1 object = codec1.decode(buffer);
                T2 object1 = codec2.decode(buffer);
                T3 object2 = codec3.decode(buffer);
                T4 object3 = codec4.decode(buffer);
                T5 object4 = codec5.decode(buffer);
                T6 object5 = codec6.decode(buffer);
                T7 object6 = codec7.decode(buffer);
                T8 object7 = codec8.decode(buffer);
                T9 object8 = codec9.decode(buffer);
                return factory.apply(object, object1, object2, object3, object4, object5, object6, object7, object8);
            }

            @Override
            public void encode(B buffer, C value) {
                codec1.encode(buffer, getter1.apply(value));
                codec2.encode(buffer, getter2.apply(value));
                codec3.encode(buffer, getter3.apply(value));
                codec4.encode(buffer, getter4.apply(value));
                codec5.encode(buffer, getter5.apply(value));
                codec6.encode(buffer, getter6.apply(value));
                codec7.encode(buffer, getter7.apply(value));
                codec8.encode(buffer, getter8.apply(value));
                codec9.encode(buffer, getter9.apply(value));
            }
        };
    }

    static <B, C, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> StreamCodec<B, C> composite(
        final StreamCodec<? super B, T1> codec1,
        final Function<C, T1> getter1,
        final StreamCodec<? super B, T2> codec2,
        final Function<C, T2> getter2,
        final StreamCodec<? super B, T3> codec3,
        final Function<C, T3> getter3,
        final StreamCodec<? super B, T4> codec4,
        final Function<C, T4> getter4,
        final StreamCodec<? super B, T5> codec5,
        final Function<C, T5> getter5,
        final StreamCodec<? super B, T6> codec6,
        final Function<C, T6> getter6,
        final StreamCodec<? super B, T7> codec7,
        final Function<C, T7> getter7,
        final StreamCodec<? super B, T8> codec8,
        final Function<C, T8> getter8,
        final StreamCodec<? super B, T9> codec9,
        final Function<C, T9> getter9,
        final StreamCodec<? super B, T10> codec10,
        final Function<C, T10> getter10,
        final Function10<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, C> factory
    ) {
        return new StreamCodec<B, C>() {
            @Override
            public C decode(B buffer) {
                T1 object = codec1.decode(buffer);
                T2 object1 = codec2.decode(buffer);
                T3 object2 = codec3.decode(buffer);
                T4 object3 = codec4.decode(buffer);
                T5 object4 = codec5.decode(buffer);
                T6 object5 = codec6.decode(buffer);
                T7 object6 = codec7.decode(buffer);
                T8 object7 = codec8.decode(buffer);
                T9 object8 = codec9.decode(buffer);
                T10 object9 = codec10.decode(buffer);
                return factory.apply(object, object1, object2, object3, object4, object5, object6, object7, object8, object9);
            }

            @Override
            public void encode(B buffer, C value) {
                codec1.encode(buffer, getter1.apply(value));
                codec2.encode(buffer, getter2.apply(value));
                codec3.encode(buffer, getter3.apply(value));
                codec4.encode(buffer, getter4.apply(value));
                codec5.encode(buffer, getter5.apply(value));
                codec6.encode(buffer, getter6.apply(value));
                codec7.encode(buffer, getter7.apply(value));
                codec8.encode(buffer, getter8.apply(value));
                codec9.encode(buffer, getter9.apply(value));
                codec10.encode(buffer, getter10.apply(value));
            }
        };
    }

    static <B, C, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> StreamCodec<B, C> composite(
        final StreamCodec<? super B, T1> codec1,
        final Function<C, T1> getter1,
        final StreamCodec<? super B, T2> codec2,
        final Function<C, T2> getter2,
        final StreamCodec<? super B, T3> codec3,
        final Function<C, T3> getter3,
        final StreamCodec<? super B, T4> codec4,
        final Function<C, T4> getter4,
        final StreamCodec<? super B, T5> codec5,
        final Function<C, T5> getter5,
        final StreamCodec<? super B, T6> codec6,
        final Function<C, T6> getter6,
        final StreamCodec<? super B, T7> codec7,
        final Function<C, T7> getter7,
        final StreamCodec<? super B, T8> codec8,
        final Function<C, T8> getter8,
        final StreamCodec<? super B, T9> codec9,
        final Function<C, T9> getter9,
        final StreamCodec<? super B, T10> codec10,
        final Function<C, T10> getter10,
        final StreamCodec<? super B, T11> codec11,
        final Function<C, T11> getter11,
        final Function11<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, C> factory
    ) {
        return new StreamCodec<B, C>() {
            @Override
            public C decode(B buffer) {
                T1 object = codec1.decode(buffer);
                T2 object1 = codec2.decode(buffer);
                T3 object2 = codec3.decode(buffer);
                T4 object3 = codec4.decode(buffer);
                T5 object4 = codec5.decode(buffer);
                T6 object5 = codec6.decode(buffer);
                T7 object6 = codec7.decode(buffer);
                T8 object7 = codec8.decode(buffer);
                T9 object8 = codec9.decode(buffer);
                T10 object9 = codec10.decode(buffer);
                T11 object10 = codec11.decode(buffer);
                return factory.apply(object, object1, object2, object3, object4, object5, object6, object7, object8, object9, object10);
            }

            @Override
            public void encode(B buffer, C value) {
                codec1.encode(buffer, getter1.apply(value));
                codec2.encode(buffer, getter2.apply(value));
                codec3.encode(buffer, getter3.apply(value));
                codec4.encode(buffer, getter4.apply(value));
                codec5.encode(buffer, getter5.apply(value));
                codec6.encode(buffer, getter6.apply(value));
                codec7.encode(buffer, getter7.apply(value));
                codec8.encode(buffer, getter8.apply(value));
                codec9.encode(buffer, getter9.apply(value));
                codec10.encode(buffer, getter10.apply(value));
                codec11.encode(buffer, getter11.apply(value));
            }
        };
    }

    static <B, C, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> StreamCodec<B, C> composite(
        final StreamCodec<? super B, T1> codec1,
        final Function<C, T1> getter1,
        final StreamCodec<? super B, T2> codec2,
        final Function<C, T2> getter2,
        final StreamCodec<? super B, T3> codec3,
        final Function<C, T3> getter3,
        final StreamCodec<? super B, T4> codec4,
        final Function<C, T4> getter4,
        final StreamCodec<? super B, T5> codec5,
        final Function<C, T5> getter5,
        final StreamCodec<? super B, T6> codec6,
        final Function<C, T6> getter6,
        final StreamCodec<? super B, T7> codec7,
        final Function<C, T7> getter7,
        final StreamCodec<? super B, T8> codec8,
        final Function<C, T8> getter8,
        final StreamCodec<? super B, T9> codec9,
        final Function<C, T9> getter9,
        final StreamCodec<? super B, T10> codec10,
        final Function<C, T10> getter10,
        final StreamCodec<? super B, T11> codec11,
        final Function<C, T11> getter11,
        final StreamCodec<? super B, T12> codec12,
        final Function<C, T12> getter12,
        final Function12<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, C> factory
    ) {
        return new StreamCodec<B, C>() {
            @Override
            public C decode(B buffer) {
                T1 object = codec1.decode(buffer);
                T2 object1 = codec2.decode(buffer);
                T3 object2 = codec3.decode(buffer);
                T4 object3 = codec4.decode(buffer);
                T5 object4 = codec5.decode(buffer);
                T6 object5 = codec6.decode(buffer);
                T7 object6 = codec7.decode(buffer);
                T8 object7 = codec8.decode(buffer);
                T9 object8 = codec9.decode(buffer);
                T10 object9 = codec10.decode(buffer);
                T11 object10 = codec11.decode(buffer);
                T12 object11 = codec12.decode(buffer);
                return factory.apply(object, object1, object2, object3, object4, object5, object6, object7, object8, object9, object10, object11);
            }

            @Override
            public void encode(B buffer, C value) {
                codec1.encode(buffer, getter1.apply(value));
                codec2.encode(buffer, getter2.apply(value));
                codec3.encode(buffer, getter3.apply(value));
                codec4.encode(buffer, getter4.apply(value));
                codec5.encode(buffer, getter5.apply(value));
                codec6.encode(buffer, getter6.apply(value));
                codec7.encode(buffer, getter7.apply(value));
                codec8.encode(buffer, getter8.apply(value));
                codec9.encode(buffer, getter9.apply(value));
                codec10.encode(buffer, getter10.apply(value));
                codec11.encode(buffer, getter11.apply(value));
                codec12.encode(buffer, getter12.apply(value));
            }
        };
    }

    static <B, T> StreamCodec<B, T> recursive(final UnaryOperator<StreamCodec<B, T>> modifier) {
        return new StreamCodec<B, T>() {
            private final Supplier<StreamCodec<B, T>> inner = Suppliers.memoize(() -> modifier.apply(this));

            @Override
            public T decode(B buffer) {
                return this.inner.get().decode(buffer);
            }

            @Override
            public void encode(B buffer, T value) {
                this.inner.get().encode(buffer, value);
            }
        };
    }

    default <S extends B> StreamCodec<S, V> cast() {
        return (StreamCodec<S, V>) this;
    }

    @FunctionalInterface
    public interface CodecOperation<B, S, T> {
        StreamCodec<B, T> apply(StreamCodec<B, S> codec);
    }
}
