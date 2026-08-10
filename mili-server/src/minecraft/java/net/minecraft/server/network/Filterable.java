package net.minecraft.server.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record Filterable<T>(T raw, Optional<T> filtered) {
    public static <T> Codec<Filterable<T>> codec(Codec<T> codec) {
        Codec<Filterable<T>> codec1 = RecordCodecBuilder.create(
            instance -> instance.group(codec.fieldOf("raw").forGetter(Filterable::raw), codec.optionalFieldOf("filtered").forGetter(Filterable::filtered))
                .apply(instance, Filterable::new)
        );
        Codec<Filterable<T>> codec2 = codec.xmap(Filterable::passThrough, Filterable::raw);
        return Codec.withAlternative(codec1, codec2);
    }

    public static <B extends ByteBuf, T> StreamCodec<B, Filterable<T>> streamCodec(StreamCodec<B, T> codec) {
        return StreamCodec.composite(codec, Filterable::raw, codec.apply(ByteBufCodecs::optional), Filterable::filtered, Filterable::new);
    }

    public static <T> Filterable<T> passThrough(T value) {
        return new Filterable<>(value, Optional.empty());
    }

    public static Filterable<String> from(FilteredText filteredText) {
        return new Filterable<>(filteredText.raw(), filteredText.isFiltered() ? Optional.of(filteredText.filteredOrEmpty()) : Optional.empty());
    }

    public T get(boolean filtered) {
        return filtered ? this.filtered.orElse(this.raw) : this.raw;
    }

    public <U> Filterable<U> map(Function<T, U> mappingFunction) {
        return new Filterable<>(mappingFunction.apply(this.raw), this.filtered.map(mappingFunction));
    }

    public <U> Optional<Filterable<U>> resolve(Function<T, Optional<U>> resolver) {
        Optional<U> optional = resolver.apply(this.raw);
        if (optional.isEmpty()) {
            return Optional.empty();
        } else if (this.filtered.isPresent()) {
            Optional<U> optional1 = resolver.apply(this.filtered.get());
            return optional1.isEmpty() ? Optional.empty() : Optional.of(new Filterable<>(optional.get(), optional1));
        } else {
            return Optional.of(new Filterable<>(optional.get(), Optional.empty()));
        }
    }
}
