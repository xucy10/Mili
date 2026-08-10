package net.minecraft.resources;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.Registry;
import net.minecraft.util.ExtraCodecs;

public class RegistryOps<T> extends DelegatingOps<T> {
    public final RegistryOps.RegistryInfoLookup lookupProvider;

    public static <T> RegistryOps<T> create(DynamicOps<T> delegate, HolderLookup.Provider registries) {
        return create(delegate, new RegistryOps.HolderLookupAdapter(registries));
    }

    public static <T> RegistryOps<T> create(DynamicOps<T> delegate, RegistryOps.RegistryInfoLookup lookupProvider) {
        return new RegistryOps<>(delegate, lookupProvider);
    }

    public static <T> Dynamic<T> injectRegistryContext(Dynamic<T> dynamic, HolderLookup.Provider registries) {
        return new Dynamic<>(registries.createSerializationContext(dynamic.getOps()), dynamic.getValue());
    }

    private RegistryOps(DynamicOps<T> delegate, RegistryOps.RegistryInfoLookup lookupProvider) {
        super(delegate);
        this.lookupProvider = lookupProvider;
    }

    public <U> RegistryOps<U> withParent(DynamicOps<U> ops) {
        return (RegistryOps<U>)(ops == this.delegate ? this : new RegistryOps<>(ops, this.lookupProvider));
    }

    public <E> Optional<HolderOwner<E>> owner(ResourceKey<? extends Registry<? extends E>> registryKey) {
        return this.lookupProvider.lookup(registryKey).map(RegistryOps.RegistryInfo::owner);
    }

    public <E> Optional<HolderGetter<E>> getter(ResourceKey<? extends Registry<? extends E>> registryKey) {
        return this.lookupProvider.lookup(registryKey).map(RegistryOps.RegistryInfo::getter);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else if (other != null && this.getClass() == other.getClass()) {
            RegistryOps<?> registryOps = (RegistryOps<?>)other;
            return this.delegate.equals(registryOps.delegate) && this.lookupProvider.equals(registryOps.lookupProvider);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return this.delegate.hashCode() * 31 + this.lookupProvider.hashCode();
    }

    public static <E, O> RecordCodecBuilder<O, HolderGetter<E>> retrieveGetter(ResourceKey<? extends Registry<? extends E>> registryKey) {
        return ExtraCodecs.retrieveContext(
                ops -> ops instanceof RegistryOps<?> registryOps
                    ? registryOps.lookupProvider
                        .lookup(registryKey)
                        .map(registryInfo -> DataResult.success(registryInfo.getter(), registryInfo.elementsLifecycle()))
                        .orElseGet(() -> DataResult.error(() -> "Unknown registry: " + registryKey))
                    : DataResult.error(() -> "Not a registry ops")
            )
            .forGetter(object -> null);
    }

    public static <E, O> RecordCodecBuilder<O, Holder.Reference<E>> retrieveElement(ResourceKey<E> key) {
        ResourceKey<? extends Registry<E>> resourceKey = ResourceKey.createRegistryKey(key.registry());
        return ExtraCodecs.retrieveContext(
                ops -> ops instanceof RegistryOps<?> registryOps
                    ? registryOps.lookupProvider
                        .lookup(resourceKey)
                        .flatMap(registryInfo -> registryInfo.getter().get(key))
                        .map(DataResult::success)
                        .orElseGet(() -> DataResult.error(() -> "Can't find value: " + key))
                    : DataResult.error(() -> "Not a registry ops")
            )
            .forGetter(object -> null);
    }

    public static final class HolderLookupAdapter implements RegistryOps.RegistryInfoLookup {
        private final HolderLookup.Provider lookupProvider;
        private final Map<ResourceKey<? extends Registry<?>>, Optional<? extends RegistryOps.RegistryInfo<?>>> lookups = new ConcurrentHashMap<>();

        public HolderLookupAdapter(HolderLookup.Provider lookupProvider) {
            this.lookupProvider = lookupProvider;
        }

        @Override
        public <E> Optional<RegistryOps.RegistryInfo<E>> lookup(ResourceKey<? extends Registry<? extends E>> registryKey) {
            return (Optional<RegistryOps.RegistryInfo<E>>)this.lookups.computeIfAbsent(registryKey, this::createLookup);
        }

        private Optional<RegistryOps.RegistryInfo<Object>> createLookup(ResourceKey<? extends Registry<?>> registryKey) {
            return this.lookupProvider.lookup(registryKey).map(RegistryOps.RegistryInfo::fromRegistryLookup);
        }

        @Override
        public boolean equals(Object other) {
            return this == other
                || other instanceof RegistryOps.HolderLookupAdapter holderLookupAdapter && this.lookupProvider.equals(holderLookupAdapter.lookupProvider);
        }

        @Override
        public int hashCode() {
            return this.lookupProvider.hashCode();
        }

        // Paper start - add method to get the value for pre-filling builders in the reg mod API
        @Override
        public HolderLookup.Provider lookupForValueCopyViaBuilders() {
            return this.lookupProvider;
        }
        // Paper end - add method to get the value for pre-filling builders in the reg mod API
    }

    public record RegistryInfo<T>(HolderOwner<T> owner, HolderGetter<T> getter, Lifecycle elementsLifecycle) {
        public static <T> RegistryOps.RegistryInfo<T> fromRegistryLookup(HolderLookup.RegistryLookup<T> registryLookup) {
            return new RegistryOps.RegistryInfo<>(registryLookup, registryLookup, registryLookup.registryLifecycle());
        }
    }

    public interface RegistryInfoLookup {
        <T> Optional<RegistryOps.RegistryInfo<T>> lookup(ResourceKey<? extends Registry<? extends T>> registryKey);

        HolderLookup.Provider lookupForValueCopyViaBuilders(); // Paper - add method to get the value for pre-filling builders in the reg mod API
    }
}
