package net.minecraft.resources;

import com.google.common.collect.MapMaker;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.StreamCodec;

public class ResourceKey<T> {
    private static final ConcurrentMap<ResourceKey.InternKey, ResourceKey<?>> VALUES = new MapMaker().weakValues().makeMap();
    private final Identifier registryName;
    private final Identifier identifier;

    public static <T> Codec<ResourceKey<T>> codec(ResourceKey<? extends Registry<T>> registryKey) {
        return Identifier.CODEC.xmap(path -> create(registryKey, path), ResourceKey::identifier);
    }

    public static <T> StreamCodec<ByteBuf, ResourceKey<T>> streamCodec(ResourceKey<? extends Registry<T>> registryKey) {
        return Identifier.STREAM_CODEC.map(location -> create(registryKey, location), ResourceKey::identifier);
    }

    public static <T> ResourceKey<T> create(ResourceKey<? extends Registry<T>> registryKey, Identifier location) {
        return create(registryKey.identifier, location);
    }

    public static <T> ResourceKey<Registry<T>> createRegistryKey(Identifier location) {
        return create(Registries.ROOT_REGISTRY_NAME, location);
    }

    private static <T> ResourceKey<T> create(Identifier registryName, Identifier location) {
        return (ResourceKey<T>)VALUES.computeIfAbsent(new ResourceKey.InternKey(registryName, location), key -> new ResourceKey(key.registry, key.identifier));
    }

    private ResourceKey(Identifier registryName, Identifier identifier) {
        this.registryName = registryName;
        this.identifier = identifier;
    }

    @Override
    public String toString() {
        return "ResourceKey[" + this.registryName + " / " + this.identifier + "]";
    }

    public boolean isFor(ResourceKey<? extends Registry<?>> registryKey) {
        return this.registryName.equals(registryKey.identifier());
    }

    public <E> Optional<ResourceKey<E>> cast(ResourceKey<? extends Registry<E>> registryKey) {
        return this.isFor(registryKey) ? Optional.of((ResourceKey<E>)this) : Optional.empty();
    }

    public Identifier identifier() {
        return this.identifier;
    }

    public Identifier registry() {
        return this.registryName;
    }

    public ResourceKey<Registry<T>> registryKey() {
        return createRegistryKey(this.registryName);
    }

    record InternKey(Identifier registry, Identifier identifier) {
    }
}
