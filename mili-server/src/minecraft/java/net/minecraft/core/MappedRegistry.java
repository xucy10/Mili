package net.minecraft.core;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import com.google.common.collect.ImmutableMap.Builder;
import com.mojang.serialization.Lifecycle;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

public class MappedRegistry<T> implements WritableRegistry<T> {
    private final ResourceKey<? extends Registry<T>> key;
    private final ObjectList<Holder.Reference<T>> byId = new ObjectArrayList<>(256);
    private final Reference2IntMap<T> toId = Util.make(new Reference2IntOpenHashMap<>(2048), map -> map.defaultReturnValue(-1)); // Paper - Perf: Use bigger expected size to reduce collisions
    private final Map<Identifier, Holder.Reference<T>> byLocation = new HashMap<>(2048); // Paper - Perf: Use bigger expected size to reduce collisions
    private final Map<ResourceKey<T>, Holder.Reference<T>> byKey = new HashMap<>(2048); // Paper - Perf: Use bigger expected size to reduce collisions
    private final Map<T, Holder.Reference<T>> byValue = new IdentityHashMap<>(2048); // Paper - Perf: Use bigger expected size to reduce collisions
    private final Map<ResourceKey<T>, RegistrationInfo> registrationInfos = new IdentityHashMap<>(2048); // Paper - Perf: Use bigger expected size to reduce collisions
    private Lifecycle registryLifecycle;
    private final Map<TagKey<T>, HolderSet.Named<T>> frozenTags = new IdentityHashMap<>();
    MappedRegistry.TagSet<T> allTags = MappedRegistry.TagSet.unbound();
    private boolean frozen;
    private @Nullable Map<T, Holder.Reference<T>> unregisteredIntrusiveHolders;
    // Paper start - support pre-filling in registry mod API
    private final Map<Identifier, T> temporaryUnfrozenMap = new HashMap<>();

    @Override
    public Optional<T> getValueForCopying(final ResourceKey<T> resourceKey) {
        return this.frozen ? this.getOptional(resourceKey) : Optional.ofNullable(this.temporaryUnfrozenMap.get(resourceKey.identifier()));
    }
    // Paper end - support pre-filling in registry mod API

    @Override
    public Stream<HolderSet.Named<T>> listTags() {
        return this.getTags();
    }

    // Paper start - fluid method optimisations
    private void injectFluidRegister(
        final ResourceKey<?> resourceKey,
        final T object
    ) {
        if (resourceKey.registryKey() == (Object)net.minecraft.core.registries.Registries.FLUID) {
            for (final net.minecraft.world.level.material.FluidState possibleState : ((net.minecraft.world.level.material.Fluid)object).getStateDefinition().getPossibleStates()) {
                ((ca.spottedleaf.moonrise.patches.fluid.FluidFluidState)(Object)possibleState).moonrise$initCaches();
            }
        }
    }
    // Paper end - fluid method optimisations

    public MappedRegistry(ResourceKey<? extends Registry<T>> key, Lifecycle registryLifecycle) {
        this(key, registryLifecycle, false);
    }

    public MappedRegistry(ResourceKey<? extends Registry<T>> key, Lifecycle registryLifecycle, boolean hasIntrusiveHolders) {
        this.key = key;
        this.registryLifecycle = registryLifecycle;
        if (hasIntrusiveHolders) {
            this.unregisteredIntrusiveHolders = new IdentityHashMap<>();
        }
    }

    @Override
    public ResourceKey<? extends Registry<T>> key() {
        return this.key;
    }

    @Override
    public String toString() {
        return "Registry[" + this.key + " (" + this.registryLifecycle + ")]";
    }

    private void validateWrite() {
        if (this.frozen) {
            throw new IllegalStateException("Registry is already frozen");
        }
    }

    public void validateWrite(ResourceKey<T> key) {
        if (this.frozen) {
            throw new IllegalStateException("Registry is already frozen (trying to add key " + key + ")");
        }
    }

    @Override
    public Holder.Reference<T> register(ResourceKey<T> key, T value, RegistrationInfo registrationInfo) {
        this.validateWrite(key);
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        if (this.byLocation.containsKey(key.identifier())) {
            throw (IllegalStateException)Util.pauseInIde(new IllegalStateException("Adding duplicate key '" + key + "' to registry"));
        } else if (this.byValue.containsKey(value)) {
            throw (IllegalStateException)Util.pauseInIde(new IllegalStateException("Adding duplicate value '" + value + "' to registry"));
        } else {
            Holder.Reference<T> reference;
            if (this.unregisteredIntrusiveHolders != null) {
                reference = this.unregisteredIntrusiveHolders.remove(value);
                if (reference == null) {
                    throw new AssertionError("Missing intrusive holder for " + key + ":" + value);
                }

                reference.bindKey(key);
            } else {
                reference = this.byKey.computeIfAbsent(key, resourceKey -> Holder.Reference.createStandAlone(this, (ResourceKey<T>)resourceKey));
            }

            this.byKey.put(key, reference);
            this.byLocation.put(key.identifier(), reference);
            this.byValue.put(value, reference);
            int size = this.byId.size();
            this.byId.add(reference);
            this.toId.put(value, size);
            this.registrationInfos.put(key, registrationInfo);
            this.registryLifecycle = this.registryLifecycle.add(registrationInfo.lifecycle());
            this.temporaryUnfrozenMap.put(key.identifier(), value); // Paper - support pre-filling in registry mod API
            this.injectFluidRegister(key, value); // Paper - fluid method optimisations
            return reference;
        }
    }

    @Override
    public @Nullable Identifier getKey(T value) {
        Holder.Reference<T> reference = this.byValue.get(value);
        return reference != null ? reference.key().identifier() : null;
    }

    @Override
    public Optional<ResourceKey<T>> getResourceKey(T value) {
        return Optional.ofNullable(this.byValue.get(value)).map(Holder.Reference::key);
    }

    @Override
    public int getId(@Nullable T value) {
        return this.toId.getInt(value);
    }

    @Override
    public @Nullable T getValue(@Nullable ResourceKey<T> key) {
        return getValueFromNullable(this.byKey.get(key));
    }

    @Override
    public @Nullable T byId(int id) {
        return id >= 0 && id < this.byId.size() ? this.byId.get(id).value() : null;
    }

    @Override
    public Optional<Holder.Reference<T>> get(int index) {
        return index >= 0 && index < this.byId.size() ? Optional.ofNullable(this.byId.get(index)) : Optional.empty();
    }

    @Override
    public Optional<Holder.Reference<T>> get(Identifier key) {
        return Optional.ofNullable(this.byLocation.get(key));
    }

    @Override
    public Optional<Holder.Reference<T>> get(ResourceKey<T> resourceKey) {
        return Optional.ofNullable(this.byKey.get(resourceKey));
    }

    @Override
    public Optional<Holder.Reference<T>> getAny() {
        return this.byId.isEmpty() ? Optional.empty() : Optional.of(this.byId.getFirst());
    }

    @Override
    public Holder<T> wrapAsHolder(T value) {
        Holder.Reference<T> reference = this.byValue.get(value);
        return (Holder<T>)(reference != null ? reference : Holder.direct(value));
    }

    Holder.Reference<T> getOrCreateHolderOrThrow(ResourceKey<T> key) {
        return this.byKey.computeIfAbsent(key, key1 -> {
            if (this.unregisteredIntrusiveHolders != null) {
                throw new IllegalStateException("This registry can't create new holders without value");
            } else {
                this.validateWrite((ResourceKey<T>)key1);
                return Holder.Reference.createStandAlone(this, (ResourceKey<T>)key1);
            }
        });
    }

    @Override
    public int size() {
        return this.byKey.size();
    }

    @Override
    public Optional<RegistrationInfo> registrationInfo(ResourceKey<T> key) {
        return Optional.ofNullable(this.registrationInfos.get(key));
    }

    @Override
    public Lifecycle registryLifecycle() {
        return this.registryLifecycle;
    }

    @Override
    public Iterator<T> iterator() {
        return Iterators.transform(this.byId.iterator(), Holder::value);
    }

    @Override
    public @Nullable T getValue(@Nullable Identifier key) {
        Holder.Reference<T> reference = this.byLocation.get(key);
        return getValueFromNullable(reference);
    }

    private static <T> @Nullable T getValueFromNullable(Holder.@Nullable Reference<T> holder) {
        return holder != null ? holder.value() : null;
    }

    @Override
    public Set<Identifier> keySet() {
        return Collections.unmodifiableSet(this.byLocation.keySet());
    }

    @Override
    public Set<ResourceKey<T>> registryKeySet() {
        return Collections.unmodifiableSet(this.byKey.keySet());
    }

    @Override
    public Set<Entry<ResourceKey<T>, T>> entrySet() {
        return Collections.unmodifiableSet(Util.mapValuesLazy(this.byKey, Holder::value).entrySet());
    }

    @Override
    public Stream<Holder.Reference<T>> listElements() {
        return this.byId.stream();
    }

    @Override
    public Stream<HolderSet.Named<T>> getTags() {
        return this.allTags.getTags();
    }

    HolderSet.Named<T> getOrCreateTagForRegistration(TagKey<T> key) {
        return this.frozenTags.computeIfAbsent(key, this::createTag);
    }

    private HolderSet.Named<T> createTag(TagKey<T> key) {
        return new HolderSet.Named<>(this, key);
    }

    @Override
    public boolean isEmpty() {
        return this.byKey.isEmpty();
    }

    @Override
    public Optional<Holder.Reference<T>> getRandom(RandomSource random) {
        return Util.getRandomSafe(this.byId, random);
    }

    @Override
    public boolean containsKey(Identifier key) {
        return this.byLocation.containsKey(key);
    }

    @Override
    public boolean containsKey(ResourceKey<T> key) {
        return this.byKey.containsKey(key);
    }

    @Override
    public Registry<T> freeze() {
        if (this.frozen) {
            return this;
        } else {
            this.frozen = true;
            this.temporaryUnfrozenMap.clear(); // Paper - support pre-filling in registry mod API
            this.byValue.forEach((object, reference) -> reference.bindValue((T)object));
            List<Identifier> list = this.byKey
                .entrySet()
                .stream()
                .filter(entry -> !entry.getValue().isBound())
                .map(entry -> entry.getKey().identifier())
                .sorted()
                .toList();
            if (!list.isEmpty()) {
                throw new IllegalStateException("Unbound values in registry " + this.key() + ": " + list);
            } else {
                if (this.unregisteredIntrusiveHolders != null) {
                    if (!this.unregisteredIntrusiveHolders.isEmpty()) {
                        throw new IllegalStateException("Some intrusive holders were not registered: " + this.unregisteredIntrusiveHolders.values());
                    }

                    this.unregisteredIntrusiveHolders = null;
                }

                if (this.allTags.isBound()) {
                    throw new IllegalStateException("Tags already present before freezing");
                } else {
                    List<Identifier> list1 = this.frozenTags
                        .entrySet()
                        .stream()
                        .filter(entry -> !entry.getValue().isBound())
                        .map(entry -> entry.getKey().location())
                        .sorted()
                        .toList();
                    if (!list1.isEmpty()) {
                        throw new IllegalStateException("Unbound tags in registry " + this.key() + ": " + list1);
                    } else {
                        this.allTags = MappedRegistry.TagSet.fromMap(this.frozenTags);
                        this.refreshTagsInHolders();
                        return this;
                    }
                }
            }
        }
    }

    @Override
    public Holder.Reference<T> createIntrusiveHolder(T value) {
        if (this.unregisteredIntrusiveHolders == null) {
            throw new IllegalStateException("This registry can't create intrusive holders");
        } else {
            this.validateWrite();
            return this.unregisteredIntrusiveHolders.computeIfAbsent(value, object -> Holder.Reference.createIntrusive(this, (T)object));
        }
    }

    @Override
    public Optional<HolderSet.Named<T>> get(TagKey<T> tagKey) {
        return this.allTags.get(tagKey);
    }

    private Holder.Reference<T> validateAndUnwrapTagElement(TagKey<T> key, Holder<T> value) {
        if (!value.canSerializeIn(this)) {
            throw new IllegalStateException("Can't create named set " + key + " containing value " + value + " from outside registry " + this);
        } else if (value instanceof Holder.Reference<T> reference) {
            return reference;
        } else {
            throw new IllegalStateException("Found direct holder " + value + " value in tag " + key);
        }
    }

    @Override
    public void bindTag(TagKey<T> tag, List<Holder<T>> values) {
        this.validateWrite();
        this.getOrCreateTagForRegistration(tag).bind(values);
    }

    void refreshTagsInHolders() {
        Map<Holder.Reference<T>, List<TagKey<T>>> map = new IdentityHashMap<>();
        this.byKey.values().forEach(reference -> map.put((Holder.Reference<T>)reference, new ArrayList<>()));
        this.allTags.forEach((tagKey, named) -> {
            for (Holder<T> holder : named) {
                Holder.Reference<T> reference = this.validateAndUnwrapTagElement((TagKey<T>)tagKey, holder);
                map.get(reference).add((TagKey<T>)tagKey);
            }
        });
        map.forEach(Holder.Reference::bindTags);
    }

    public void bindAllTagsToEmpty() {
        this.validateWrite();
        this.frozenTags.values().forEach(named -> named.bind(List.of()));
    }

    @Override
    public HolderGetter<T> createRegistrationLookup() {
        this.validateWrite();
        return new HolderGetter<T>() {
            @Override
            public Optional<Holder.Reference<T>> get(ResourceKey<T> resourceKey) {
                return Optional.of(this.getOrThrow(resourceKey));
            }

            @Override
            public Holder.Reference<T> getOrThrow(ResourceKey<T> resourceKey) {
                return MappedRegistry.this.getOrCreateHolderOrThrow(resourceKey);
            }

            @Override
            public Optional<HolderSet.Named<T>> get(TagKey<T> tagKey) {
                return Optional.of(this.getOrThrow(tagKey));
            }

            @Override
            public HolderSet.Named<T> getOrThrow(TagKey<T> tagKey) {
                return MappedRegistry.this.getOrCreateTagForRegistration(tagKey);
            }
        };
    }

    @Override
    public Registry.PendingTags<T> prepareTagReload(TagLoader.LoadResult<T> loadResult) {
        if (!this.frozen) {
            throw new IllegalStateException("Invalid method used for tag loading");
        } else {
            Builder<TagKey<T>, HolderSet.Named<T>> builder = ImmutableMap.builder();
            final Map<TagKey<T>, List<Holder<T>>> map = new HashMap<>();
            loadResult.tags().forEach((tagKey, list) -> {
                HolderSet.Named<T> named = this.frozenTags.get(tagKey);
                if (named == null) {
                    named = this.createTag((TagKey<T>)tagKey);
                }

                builder.put((TagKey<T>)tagKey, named);
                map.put((TagKey<T>)tagKey, List.copyOf(list));
            });
            final ImmutableMap<TagKey<T>, HolderSet.Named<T>> map1 = builder.build();
            final HolderLookup.RegistryLookup<T> registryLookup = new HolderLookup.RegistryLookup.Delegate<T>() {
                @Override
                public HolderLookup.RegistryLookup<T> parent() {
                    return MappedRegistry.this;
                }

                @Override
                public Optional<HolderSet.Named<T>> get(TagKey<T> tagKey) {
                    return Optional.ofNullable(map1.get(tagKey));
                }

                @Override
                public Stream<HolderSet.Named<T>> listTags() {
                    return map1.values().stream();
                }
            };
            return new Registry.PendingTags<T>() {
                @Override
                public ResourceKey<? extends Registry<? extends T>> key() {
                    return MappedRegistry.this.key();
                }

                @Override
                public int size() {
                    return map.size();
                }

                @Override
                public HolderLookup.RegistryLookup<T> lookup() {
                    return registryLookup;
                }

                @Override
                public void apply() {
                    map1.forEach((tagKey, named) -> {
                        List<Holder<T>> list = map.getOrDefault(tagKey, List.of());
                        named.bind(list);
                    });
                    MappedRegistry.this.allTags = MappedRegistry.TagSet.fromMap(map1);
                    MappedRegistry.this.refreshTagsInHolders();
                }
            };
        }
    }

    interface TagSet<T> {
        static <T> MappedRegistry.TagSet<T> unbound() {
            return new MappedRegistry.TagSet<T>() {
                @Override
                public boolean isBound() {
                    return false;
                }

                @Override
                public Optional<HolderSet.Named<T>> get(TagKey<T> key) {
                    throw new IllegalStateException("Tags not bound, trying to access " + key);
                }

                @Override
                public void forEach(BiConsumer<? super TagKey<T>, ? super HolderSet.Named<T>> action) {
                    throw new IllegalStateException("Tags not bound");
                }

                @Override
                public Stream<HolderSet.Named<T>> getTags() {
                    throw new IllegalStateException("Tags not bound");
                }
            };
        }

        static <T> MappedRegistry.TagSet<T> fromMap(final Map<TagKey<T>, HolderSet.Named<T>> map) {
            return new MappedRegistry.TagSet<T>() {
                @Override
                public boolean isBound() {
                    return true;
                }

                @Override
                public Optional<HolderSet.Named<T>> get(TagKey<T> key) {
                    return Optional.ofNullable(map.get(key));
                }

                @Override
                public void forEach(BiConsumer<? super TagKey<T>, ? super HolderSet.Named<T>> action) {
                    map.forEach(action);
                }

                @Override
                public Stream<HolderSet.Named<T>> getTags() {
                    return map.values().stream();
                }
            };
        }

        boolean isBound();

        Optional<HolderSet.Named<T>> get(TagKey<T> key);

        void forEach(BiConsumer<? super TagKey<T>, ? super HolderSet.Named<T>> action);

        Stream<HolderSet.Named<T>> getTags();
    }

    // Paper start
    // used to clear intrusive holders from GameEvent, Item, Block, EntityType, and Fluid from unused instances of those types
    public void clearIntrusiveHolder(final T instance) {
        if (this.unregisteredIntrusiveHolders != null) {
            this.unregisteredIntrusiveHolders.remove(instance);
        }
    }
    // Paper end
}
