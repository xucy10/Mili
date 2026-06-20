package net.minecraft.tags;

import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

public record TagKey<T>(ResourceKey<? extends Registry<T>> registry, Identifier location) {
    private static final Interner<TagKey<?>> VALUES = Interners.newWeakInterner();

    @Deprecated
    public TagKey(ResourceKey<? extends Registry<T>> registry, Identifier location) {
        this.registry = registry;
        this.location = location;
    }

    public static <T> Codec<TagKey<T>> codec(ResourceKey<? extends Registry<T>> registry) {
        return Identifier.CODEC.xmap(path -> create(registry, path), TagKey::location);
    }

    public static <T> Codec<TagKey<T>> hashedCodec(ResourceKey<? extends Registry<T>> registry) {
        return Codec.STRING
            .comapFlatMap(
                location -> location.startsWith("#")
                    ? Identifier.read(location.substring(1)).map(path -> create(registry, path))
                    : DataResult.error(() -> "Not a tag id"),
                tagKey -> "#" + tagKey.location
            );
    }

    public static <T> StreamCodec<ByteBuf, TagKey<T>> streamCodec(ResourceKey<? extends Registry<T>> registry) {
        return Identifier.STREAM_CODEC.map(location -> create(registry, location), TagKey::location);
    }

    public static <T> TagKey<T> create(ResourceKey<? extends Registry<T>> registry, Identifier location) {
        return (TagKey<T>)VALUES.intern(new TagKey<>(registry, location));
    }

    public boolean isFor(ResourceKey<? extends Registry<?>> registry) {
        return this.registry == registry;
    }

    public <E> Optional<TagKey<E>> cast(ResourceKey<? extends Registry<E>> registry) {
        return this.isFor(registry) ? Optional.of((TagKey<E>)this) : Optional.empty();
    }

    @Override
    public String toString() {
        return "TagKey[" + this.registry.identifier() + " / " + this.location + "]";
    }
}
