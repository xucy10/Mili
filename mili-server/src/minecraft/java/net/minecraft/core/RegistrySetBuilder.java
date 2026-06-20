package net.minecraft.core;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Lifecycle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import org.apache.commons.lang3.mutable.MutableObject;
import org.jspecify.annotations.Nullable;

public class RegistrySetBuilder {
    private final List<RegistrySetBuilder.RegistryStub<?>> entries = new ArrayList<>();

    static <T> HolderGetter<T> wrapContextLookup(final HolderLookup.RegistryLookup<T> lookup) {
        return new RegistrySetBuilder.EmptyTagLookup<T>(lookup) {
            @Override
            public Optional<Holder.Reference<T>> get(ResourceKey<T> resourceKey) {
                return lookup.get(resourceKey);
            }
        };
    }

    static <T> HolderLookup.RegistryLookup<T> lookupFromMap(
        final ResourceKey<? extends Registry<? extends T>> registryKey,
        final Lifecycle registryLifecycle,
        HolderOwner<T> owner,
        final Map<ResourceKey<T>, Holder.Reference<T>> elements
    ) {
        return new RegistrySetBuilder.EmptyTagRegistryLookup<T>(owner) {
            // Paper start - add getValueForCopying method
            @Override
            public Optional<T> getValueForCopying(final ResourceKey<T> resourceKey) {
                return this.get(resourceKey).map(Holder.Reference::value);
            }
            // Paper end - add getValueForCopying method

            @Override
            public ResourceKey<? extends Registry<? extends T>> key() {
                return registryKey;
            }

            @Override
            public Lifecycle registryLifecycle() {
                return registryLifecycle;
            }

            @Override
            public Optional<Holder.Reference<T>> get(ResourceKey<T> resourceKey) {
                return Optional.ofNullable(elements.get(resourceKey));
            }

            @Override
            public Stream<Holder.Reference<T>> listElements() {
                return elements.values().stream();
            }
        };
    }

    public <T> RegistrySetBuilder add(ResourceKey<? extends Registry<T>> key, Lifecycle lifecycle, RegistrySetBuilder.RegistryBootstrap<T> bootstrap) {
        this.entries.add(new RegistrySetBuilder.RegistryStub<>(key, lifecycle, bootstrap));
        return this;
    }

    public <T> RegistrySetBuilder add(ResourceKey<? extends Registry<T>> key, RegistrySetBuilder.RegistryBootstrap<T> bootstrap) {
        return this.add(key, Lifecycle.stable(), bootstrap);
    }

    private RegistrySetBuilder.BuildState createState(RegistryAccess registryAccess) {
        RegistrySetBuilder.BuildState buildState = RegistrySetBuilder.BuildState.create(
            registryAccess, this.entries.stream().map(RegistrySetBuilder.RegistryStub::key)
        );
        this.entries.forEach(entry -> entry.apply(buildState));
        return buildState;
    }

    private static HolderLookup.Provider buildProviderWithContext(
        RegistrySetBuilder.UniversalOwner owner, RegistryAccess registryAccess, Stream<HolderLookup.RegistryLookup<?>> lookups
    ) {
        record Entry<T>(HolderLookup.RegistryLookup<T> lookup, RegistryOps.RegistryInfo<T> opsInfo) {
            public static <T> Entry<T> createForContextRegistry(HolderLookup.RegistryLookup<T> lookup) {
                return new Entry<>(new RegistrySetBuilder.EmptyTagLookupWrapper<>(lookup, lookup), RegistryOps.RegistryInfo.fromRegistryLookup(lookup));
            }

            public static <T> Entry<T> createForNewRegistry(RegistrySetBuilder.UniversalOwner owner1, HolderLookup.RegistryLookup<T> lookup) {
                return new Entry<>(
                    new RegistrySetBuilder.EmptyTagLookupWrapper<>(owner1.cast(), lookup),
                    new RegistryOps.RegistryInfo<>(owner1.cast(), lookup, lookup.registryLifecycle())
                );
            }
        }

        final Map<ResourceKey<? extends Registry<?>>, Entry<?>> map = new HashMap<>();
        registryAccess.registries().forEach(registry -> map.put(registry.key(), Entry.createForContextRegistry(registry.value())));
        lookups.forEach(lookup -> map.put(lookup.key(), Entry.createForNewRegistry(owner, lookup)));
        return new HolderLookup.Provider() {
            @Override
            public Stream<ResourceKey<? extends Registry<?>>> listRegistryKeys() {
                return map.keySet().stream();
            }

            <T> Optional<Entry<T>> getEntry(ResourceKey<? extends Registry<? extends T>> registryKey) {
                return Optional.ofNullable((Entry<T>)map.get(registryKey));
            }

            @Override
            public <T> Optional<HolderLookup.RegistryLookup<T>> lookup(ResourceKey<? extends Registry<? extends T>> registryKey) {
                return this.getEntry(registryKey).map(Entry::lookup);
            }

            @Override
            public <V> RegistryOps<V> createSerializationContext(DynamicOps<V> ops) {
                return RegistryOps.create(ops, new RegistryOps.RegistryInfoLookup() {
                    @Override
                    public <T> Optional<RegistryOps.RegistryInfo<T>> lookup(ResourceKey<? extends Registry<? extends T>> registryKey) {
                        return getEntry(registryKey).map(Entry::opsInfo);
                    }

                    // Paper start - add method to get the value for pre-filling builders in the reg mod API
                    @Override
                    public HolderLookup.Provider lookupForValueCopyViaBuilders() {
                        throw new UnsupportedOperationException("Not implemented");
                    }
                    // Paper end - add method to get the value for pre-filling builders in the reg mod API
                });
            }
        };
    }

    public HolderLookup.Provider build(RegistryAccess registryAccess) {
        RegistrySetBuilder.BuildState buildState = this.createState(registryAccess);
        Stream<HolderLookup.RegistryLookup<?>> stream = this.entries
            .stream()
            .map(entry -> entry.collectRegisteredValues(buildState).buildAsLookup(buildState.owner));
        HolderLookup.Provider provider = buildProviderWithContext(buildState.owner, registryAccess, stream);
        buildState.reportNotCollectedHolders();
        buildState.reportUnclaimedRegisteredValues();
        buildState.throwOnError();
        return provider;
    }

    private HolderLookup.Provider createLazyFullPatchedRegistries(
        RegistryAccess registry,
        HolderLookup.Provider lookupProvider,
        Cloner.Factory clonerFactory,
        Map<ResourceKey<? extends Registry<?>>, RegistrySetBuilder.RegistryContents<?>> registries,
        HolderLookup.Provider registryLookupProvider
    ) {
        RegistrySetBuilder.UniversalOwner universalOwner = new RegistrySetBuilder.UniversalOwner();
        MutableObject<HolderLookup.Provider> mutableObject = new MutableObject<>();
        List<HolderLookup.RegistryLookup<?>> list = registries.keySet()
            .stream()
            .map(
                key -> this.createLazyFullPatchedRegistries(
                    universalOwner,
                    clonerFactory,
                    (ResourceKey<? extends Registry<? extends Object>>)key,
                    registryLookupProvider,
                    lookupProvider,
                    mutableObject
                )
            )
            .collect(Collectors.toUnmodifiableList());
        HolderLookup.Provider provider = buildProviderWithContext(universalOwner, registry, list.stream());
        mutableObject.setValue(provider);
        return provider;
    }

    private <T> HolderLookup.RegistryLookup<T> createLazyFullPatchedRegistries(
        HolderOwner<T> owner,
        Cloner.Factory clonerFactory,
        ResourceKey<? extends Registry<? extends T>> registryKey,
        HolderLookup.Provider registryLookupProvider,
        HolderLookup.Provider lookupProvider,
        MutableObject<HolderLookup.Provider> object
    ) {
        Cloner<T> cloner = clonerFactory.cloner(registryKey);
        if (cloner == null) {
            throw new NullPointerException("No cloner for " + registryKey.identifier());
        } else {
            Map<ResourceKey<T>, Holder.Reference<T>> map = new HashMap<>();
            HolderLookup.RegistryLookup<T> registryLookup = registryLookupProvider.lookupOrThrow(registryKey);
            registryLookup.listElements().forEach(element -> {
                ResourceKey<T> resourceKey = element.key();
                RegistrySetBuilder.LazyHolder<T> lazyHolder = new RegistrySetBuilder.LazyHolder<>(owner, resourceKey);
                lazyHolder.supplier = () -> cloner.clone((T)element.value(), registryLookupProvider, object.get());
                map.put(resourceKey, lazyHolder);
            });
            HolderLookup.RegistryLookup<T> registryLookup1 = lookupProvider.lookupOrThrow(registryKey);
            registryLookup1.listElements().forEach(element -> {
                ResourceKey<T> resourceKey = element.key();
                map.computeIfAbsent(resourceKey, resourceKey1 -> {
                    RegistrySetBuilder.LazyHolder<T> lazyHolder = new RegistrySetBuilder.LazyHolder<>(owner, resourceKey);
                    lazyHolder.supplier = () -> cloner.clone((T)element.value(), lookupProvider, object.get());
                    return lazyHolder;
                });
            });
            Lifecycle lifecycle = registryLookup.registryLifecycle().add(registryLookup1.registryLifecycle());
            return lookupFromMap(registryKey, lifecycle, owner, map);
        }
    }

    public RegistrySetBuilder.PatchedRegistries buildPatch(RegistryAccess registryAccess, HolderLookup.Provider lookupProvider, Cloner.Factory clonerFactory) {
        RegistrySetBuilder.BuildState buildState = this.createState(registryAccess);
        Map<ResourceKey<? extends Registry<?>>, RegistrySetBuilder.RegistryContents<?>> map = new HashMap<>();
        this.entries
            .stream()
            .map(entry -> entry.collectRegisteredValues(buildState))
            .forEach(registryContents -> map.put(registryContents.key, (RegistrySetBuilder.RegistryContents<?>)registryContents));
        Set<ResourceKey<? extends Registry<?>>> set = registryAccess.listRegistryKeys().collect(Collectors.toUnmodifiableSet());
        lookupProvider.listRegistryKeys()
            .filter(registry -> !set.contains(registry))
            .forEach(
                registry -> map.putIfAbsent(
                    (ResourceKey<? extends Registry<?>>)registry,
                    new RegistrySetBuilder.RegistryContents<>((ResourceKey<? extends Registry<?>>)registry, Lifecycle.stable(), Map.of())
                )
            );
        Stream<HolderLookup.RegistryLookup<?>> stream = map.values().stream().map(registryContents -> registryContents.buildAsLookup(buildState.owner));
        HolderLookup.Provider provider = buildProviderWithContext(buildState.owner, registryAccess, stream);
        buildState.reportUnclaimedRegisteredValues();
        buildState.throwOnError();
        HolderLookup.Provider provider1 = this.createLazyFullPatchedRegistries(registryAccess, lookupProvider, clonerFactory, map, provider);
        return new RegistrySetBuilder.PatchedRegistries(provider1, provider);
    }

    record BuildState(
        RegistrySetBuilder.UniversalOwner owner,
        RegistrySetBuilder.UniversalLookup lookup,
        Map<Identifier, HolderGetter<?>> registries,
        Map<ResourceKey<?>, RegistrySetBuilder.RegisteredValue<?>> registeredValues,
        List<RuntimeException> errors
    ) {
        public static RegistrySetBuilder.BuildState create(RegistryAccess registryAccess, Stream<ResourceKey<? extends Registry<?>>> registries) {
            RegistrySetBuilder.UniversalOwner universalOwner = new RegistrySetBuilder.UniversalOwner();
            List<RuntimeException> list = new ArrayList<>();
            RegistrySetBuilder.UniversalLookup universalLookup = new RegistrySetBuilder.UniversalLookup(universalOwner);
            Builder<Identifier, HolderGetter<?>> builder = ImmutableMap.builder();
            registryAccess.registries()
                .forEach(registryEntry -> builder.put(registryEntry.key().identifier(), RegistrySetBuilder.wrapContextLookup(registryEntry.value())));
            registries.forEach(registryKey -> builder.put(registryKey.identifier(), universalLookup));
            return new RegistrySetBuilder.BuildState(universalOwner, universalLookup, builder.build(), new HashMap<>(), list);
        }

        public <T> BootstrapContext<T> bootstrapContext() {
            return new BootstrapContext<T>() {
                @Override
                public Holder.Reference<T> register(ResourceKey<T> key, T value, Lifecycle registryLifecycle) {
                    RegistrySetBuilder.RegisteredValue<?> registeredValue = BuildState.this.registeredValues
                        .put(key, new RegistrySetBuilder.RegisteredValue(value, registryLifecycle));
                    if (registeredValue != null) {
                        BuildState.this.errors
                            .add(new IllegalStateException("Duplicate registration for " + key + ", new=" + value + ", old=" + registeredValue.value));
                    }

                    return BuildState.this.lookup.getOrCreate(key);
                }

                @Override
                public <S> HolderGetter<S> lookup(ResourceKey<? extends Registry<? extends S>> registryKey) {
                    return (HolderGetter<S>)BuildState.this.registries.getOrDefault(registryKey.identifier(), BuildState.this.lookup);
                }
            };
        }

        public void reportUnclaimedRegisteredValues() {
            this.registeredValues
                .forEach(
                    (resourceKey, registeredValue) -> this.errors
                        .add(new IllegalStateException("Orpaned value " + registeredValue.value + " for key " + resourceKey))
                );
        }

        public void reportNotCollectedHolders() {
            for (ResourceKey<Object> resourceKey : this.lookup.holders.keySet()) {
                this.errors.add(new IllegalStateException("Unreferenced key: " + resourceKey));
            }
        }

        public void throwOnError() {
            if (!this.errors.isEmpty()) {
                IllegalStateException illegalStateException = new IllegalStateException("Errors during registry creation");

                for (RuntimeException runtimeException : this.errors) {
                    illegalStateException.addSuppressed(runtimeException);
                }

                throw illegalStateException;
            }
        }
    }

    abstract static class EmptyTagLookup<T> implements HolderGetter<T> {
        protected final HolderOwner<T> owner;

        protected EmptyTagLookup(HolderOwner<T> owner) {
            this.owner = owner;
        }

        @Override
        public Optional<HolderSet.Named<T>> get(TagKey<T> tagKey) {
            return Optional.of(HolderSet.emptyNamed(this.owner, tagKey));
        }
    }

    static class EmptyTagLookupWrapper<T> extends RegistrySetBuilder.EmptyTagRegistryLookup<T> implements HolderLookup.RegistryLookup.Delegate<T> {
        private final HolderLookup.RegistryLookup<T> parent;

        EmptyTagLookupWrapper(HolderOwner<T> owner, HolderLookup.RegistryLookup<T> parent) {
            super(owner);
            this.parent = parent;
        }

        @Override
        public HolderLookup.RegistryLookup<T> parent() {
            return this.parent;
        }
    }

    abstract static class EmptyTagRegistryLookup<T> extends RegistrySetBuilder.EmptyTagLookup<T> implements HolderLookup.RegistryLookup<T> {
        protected EmptyTagRegistryLookup(HolderOwner<T> owner) {
            super(owner);
        }

        @Override
        public Stream<HolderSet.Named<T>> listTags() {
            throw new UnsupportedOperationException("Tags are not available in datagen");
        }
    }

    static class LazyHolder<T> extends Holder.Reference<T> {
        @Nullable Supplier<T> supplier;

        protected LazyHolder(HolderOwner<T> owner, @Nullable ResourceKey<T> key) {
            super(Holder.Reference.Type.STAND_ALONE, owner, key, null);
        }

        @Override
        protected void bindValue(T value) {
            super.bindValue(value);
            this.supplier = null;
        }

        @Override
        public T value() {
            if (this.supplier != null) {
                this.bindValue(this.supplier.get());
            }

            return super.value();
        }
    }

    public record PatchedRegistries(HolderLookup.Provider full, HolderLookup.Provider patches) {
    }

    record RegisteredValue<T>(T value, Lifecycle lifecycle) {
    }

    @FunctionalInterface
    public interface RegistryBootstrap<T> {
        void run(BootstrapContext<T> context);
    }

    record RegistryContents<T>(
        ResourceKey<? extends Registry<? extends T>> key, Lifecycle lifecycle, Map<ResourceKey<T>, RegistrySetBuilder.ValueAndHolder<T>> values
    ) {
        public HolderLookup.RegistryLookup<T> buildAsLookup(RegistrySetBuilder.UniversalOwner owner) {
            Map<ResourceKey<T>, Holder.Reference<T>> map = this.values
                .entrySet()
                .stream()
                .collect(Collectors.toUnmodifiableMap(java.util.Map.Entry::getKey, entry -> {
                    RegistrySetBuilder.ValueAndHolder<T> valueAndHolder = entry.getValue();
                    Holder.Reference<T> reference = valueAndHolder.holder().orElseGet(() -> Holder.Reference.createStandAlone(owner.cast(), entry.getKey()));
                    reference.bindValue(valueAndHolder.value().value());
                    return reference;
                }));
            return RegistrySetBuilder.lookupFromMap(this.key, this.lifecycle, owner.cast(), map);
        }
    }

    record RegistryStub<T>(ResourceKey<? extends Registry<T>> key, Lifecycle lifecycle, RegistrySetBuilder.RegistryBootstrap<T> bootstrap) {
        void apply(RegistrySetBuilder.BuildState state) {
            this.bootstrap.run(state.bootstrapContext());
        }

        public RegistrySetBuilder.RegistryContents<T> collectRegisteredValues(RegistrySetBuilder.BuildState buildState) {
            Map<ResourceKey<T>, RegistrySetBuilder.ValueAndHolder<T>> map = new HashMap<>();
            Iterator<java.util.Map.Entry<ResourceKey<?>, RegistrySetBuilder.RegisteredValue<?>>> iterator = buildState.registeredValues.entrySet().iterator();

            while (iterator.hasNext()) {
                java.util.Map.Entry<ResourceKey<?>, RegistrySetBuilder.RegisteredValue<?>> entry = iterator.next();
                ResourceKey<?> resourceKey = entry.getKey();
                if (resourceKey.isFor(this.key)) {
                    RegistrySetBuilder.RegisteredValue<T> registeredValue = (RegistrySetBuilder.RegisteredValue<T>)entry.getValue();
                    Holder.Reference<T> reference = (Holder.Reference<T>)buildState.lookup.holders.remove(resourceKey);
                    map.put((ResourceKey<T>)resourceKey, new RegistrySetBuilder.ValueAndHolder<>(registeredValue, Optional.ofNullable(reference)));
                    iterator.remove();
                }
            }

            return new RegistrySetBuilder.RegistryContents<>(this.key, this.lifecycle, map);
        }
    }

    static class UniversalLookup extends RegistrySetBuilder.EmptyTagLookup<Object> {
        final Map<ResourceKey<Object>, Holder.Reference<Object>> holders = new HashMap<>();

        public UniversalLookup(HolderOwner<Object> owner) {
            super(owner);
        }

        @Override
        public Optional<Holder.Reference<Object>> get(ResourceKey<Object> resourceKey) {
            return Optional.of(this.getOrCreate(resourceKey));
        }

        <T> Holder.Reference<T> getOrCreate(ResourceKey<T> key) {
            return (Holder.Reference<T>)this.holders
                .computeIfAbsent((ResourceKey<Object>) key, resourceKey -> Holder.Reference.createStandAlone(this.owner, (ResourceKey<Object>)resourceKey));
        }
    }

    static class UniversalOwner implements HolderOwner<Object> {
        public <T> HolderOwner<T> cast() {
            return (HolderOwner<T>) this;
        }
    }

    record ValueAndHolder<T>(RegistrySetBuilder.RegisteredValue<T> value, Optional<Holder.Reference<T>> holder) {
    }
}
