package net.minecraft.network.codec;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.network.VarInt;

public class IdDispatchCodec<B extends ByteBuf, V, T> implements StreamCodec<B, V> {
    private static final int UNKNOWN_TYPE = -1;
    private final Function<V, ? extends T> typeGetter;
    private final List<IdDispatchCodec.Entry<B, V, T>> byId;
    private final Object2IntMap<T> toId;

    IdDispatchCodec(Function<V, ? extends T> typeGetter, List<IdDispatchCodec.Entry<B, V, T>> byId, Object2IntMap<T> toId) {
        this.typeGetter = typeGetter;
        this.byId = byId;
        this.toId = toId;
    }

    @Override
    public V decode(B buffer) {
        int i = VarInt.read(buffer);
        if (i >= 0 && i < this.byId.size()) {
            IdDispatchCodec.Entry<B, V, T> entry = this.byId.get(i);

            try {
                return (V)entry.serializer.decode(buffer);
            } catch (Exception var5) {
                if (var5 instanceof IdDispatchCodec.DontDecorateException) {
                    throw var5;
                } else {
                    throw new DecoderException("Failed to decode packet '" + entry.type + "'", var5);
                }
            }
        } else {
            throw new DecoderException("Received unknown packet id " + i);
        }
    }

    @Override
    public void encode(B buffer, V value) {
        T object = (T)this.typeGetter.apply(value);
        int orDefault = this.toId.getOrDefault(object, -1);
        if (orDefault == -1) {
            throw new EncoderException("Sending unknown packet '" + object + "'");
        } else {
            VarInt.write(buffer, orDefault);
            IdDispatchCodec.Entry<B, V, T> entry = this.byId.get(orDefault);

            try {
                StreamCodec<? super B, V> streamCodec = (StreamCodec<? super B, V>)entry.serializer;
                streamCodec.encode(buffer, value);
            } catch (Exception var7) {
                if (var7 instanceof IdDispatchCodec.DontDecorateException) {
                    throw var7;
                } else {
                    throw new EncoderException("Failed to encode packet '" + object + "'", var7);
                }
            }
        }
    }

    public static <B extends ByteBuf, V, T> IdDispatchCodec.Builder<B, V, T> builder(Function<V, ? extends T> typeGetter) {
        return new IdDispatchCodec.Builder<>(typeGetter);
    }

    public static class Builder<B extends ByteBuf, V, T> {
        private final List<IdDispatchCodec.Entry<B, V, T>> entries = new ArrayList<>();
        private final Function<V, ? extends T> typeGetter;

        Builder(Function<V, ? extends T> typeGetter) {
            this.typeGetter = typeGetter;
        }

        public IdDispatchCodec.Builder<B, V, T> add(T type, StreamCodec<? super B, ? extends V> serializer) {
            this.entries.add(new IdDispatchCodec.Entry<>(serializer, type));
            return this;
        }

        public IdDispatchCodec<B, V, T> build() {
            Object2IntOpenHashMap<T> map = new Object2IntOpenHashMap<>();
            map.defaultReturnValue(-2);

            for (IdDispatchCodec.Entry<B, V, T> entry : this.entries) {
                int size = map.size();
                int i = map.putIfAbsent(entry.type, size);
                if (i != -2) {
                    throw new IllegalStateException("Duplicate registration for type " + entry.type);
                }
            }

            return new IdDispatchCodec<>(this.typeGetter, List.copyOf(this.entries), map);
        }
    }

    public interface DontDecorateException {
    }

    record Entry<B, V, T>(StreamCodec<? super B, ? extends V> serializer, T type) {
    }
}
