package net.minecraft.util.random;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.minecraft.SharedConstants;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Util;
import org.slf4j.Logger;

public record Weighted<T>(T value, int weight) {
    private static final Logger LOGGER = LogUtils.getLogger();

    public Weighted(T value, int weight) {
        if (weight < 0) {
            throw (IllegalArgumentException)Util.pauseInIde(new IllegalArgumentException("Weight should be >= 0"));
        } else {
            if (weight == 0 && SharedConstants.IS_RUNNING_IN_IDE) {
                LOGGER.warn("Found 0 weight, make sure this is intentional!");
            }

            this.value = value;
            this.weight = weight;
        }
    }

    public static <E> Codec<Weighted<E>> codec(Codec<E> elementCodec) {
        return codec(elementCodec.fieldOf("data"));
    }

    public static <E> Codec<Weighted<E>> codec(MapCodec<E> elementCodec) {
        return RecordCodecBuilder.create(
            instance -> instance.group(
                    elementCodec.forGetter((Function<Weighted<E>, E>)(Weighted::value)),
                    ExtraCodecs.NON_NEGATIVE_INT.fieldOf("weight").forGetter(Weighted::weight)
                )
                .apply(instance, (BiFunction<E, Integer, Weighted<E>>)(Weighted::new))
        );
    }

    public static <B extends ByteBuf, T> StreamCodec<B, Weighted<T>> streamCodec(StreamCodec<B, T> elementCodec) {
        return StreamCodec.composite(elementCodec, Weighted::value, ByteBufCodecs.VAR_INT, Weighted::weight, Weighted::new);
    }

    public <U> Weighted<U> map(Function<T, U> mapper) {
        return new Weighted<>(mapper.apply(this.value()), this.weight);
    }
}
