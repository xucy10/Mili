package net.minecraft.resources;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Lifecycle;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.Registry;

public final class RegistryFileCodec<E> implements Codec<Holder<E>> {
    private final ResourceKey<? extends Registry<E>> registryKey;
    private final Codec<E> elementCodec;
    private final boolean allowInline;

    public static <E> RegistryFileCodec<E> create(ResourceKey<? extends Registry<E>> registryKey, Codec<E> elementCodec) {
        return create(registryKey, elementCodec, true);
    }

    public static <E> RegistryFileCodec<E> create(ResourceKey<? extends Registry<E>> registryKey, Codec<E> elementCodec, boolean allowInline) {
        return new RegistryFileCodec<>(registryKey, elementCodec, allowInline);
    }

    private RegistryFileCodec(ResourceKey<? extends Registry<E>> registryKey, Codec<E> elementCodec, boolean allowInline) {
        this.registryKey = registryKey;
        this.elementCodec = elementCodec;
        this.allowInline = allowInline;
    }

    @Override
    public <T> DataResult<T> encode(Holder<E> input, DynamicOps<T> ops, T prefix) {
        if (ops instanceof RegistryOps<?> registryOps) {
            Optional<HolderOwner<E>> optional = registryOps.owner(this.registryKey);
            if (optional.isPresent()) {
                if (!input.canSerializeIn(optional.get())) {
                    return DataResult.error(() -> "Element " + input + " is not valid in current registry set");
                }

                return input.unwrap()
                    .map(
                        resourceKey -> Identifier.CODEC.encode(resourceKey.identifier(), ops, prefix),
                        element -> this.elementCodec.encode((E)element, ops, prefix)
                    );
            }
        }

        return this.elementCodec.encode(input.value(), ops, prefix);
    }

    @Override
    public <T> DataResult<Pair<Holder<E>, T>> decode(DynamicOps<T> ops, T input) {
        if (ops instanceof RegistryOps<?> registryOps) {
            Optional<HolderGetter<E>> optional = registryOps.getter(this.registryKey);
            if (optional.isEmpty()) {
                return DataResult.error(() -> "Registry does not exist: " + this.registryKey);
            } else {
                HolderGetter<E> holderGetter = optional.get();
                DataResult<Pair<Identifier, T>> dataResult = Identifier.CODEC.decode(ops, input);
                if (dataResult.result().isEmpty()) {
                    return !this.allowInline
                        ? DataResult.error(() -> "Inline definitions not allowed here")
                        : this.elementCodec.decode(ops, input).map(pair1 -> pair1.mapFirst(Holder::direct));
                } else {
                    Pair<Identifier, T> pair = dataResult.result().get();
                    ResourceKey<E> resourceKey = ResourceKey.create(this.registryKey, pair.getFirst());
                    return holderGetter.get(resourceKey)
                        .map(DataResult::success)
                        .orElseGet(() -> DataResult.error(() -> "Failed to get element " + resourceKey))
                        .<Pair<Holder<E>, T>>map(holder -> Pair.of(holder, pair.getSecond()))
                        .setLifecycle(Lifecycle.stable());
                }
            }
        } else {
            return this.elementCodec.decode(ops, input).map(pair1 -> pair1.mapFirst(Holder::direct));
        }
    }

    @Override
    public String toString() {
        return "RegistryFileCodec[" + this.registryKey + " " + this.elementCodec + "]";
    }
}
