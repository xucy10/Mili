package net.minecraft.tags;

import com.google.gson.JsonElement;
import com.mojang.datafixers.util.Either;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SequencedSet;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.DependencySorter;
import net.minecraft.util.StrictJsonParser;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class TagLoader<T> {
    private static final Logger LOGGER = LogUtils.getLogger();
    final TagLoader.ElementLookup<T> elementLookup;
    private final String directory;

    public TagLoader(TagLoader.ElementLookup<T> elementLookup, String directory) {
        this.elementLookup = elementLookup;
        this.directory = directory;
    }

    public Map<Identifier, List<TagLoader.EntryWithSource>> load(ResourceManager resourceManager) {
        Map<Identifier, List<TagLoader.EntryWithSource>> map = new HashMap<>();
        FileToIdConverter fileToIdConverter = FileToIdConverter.json(this.directory);

        for (Entry<Identifier, List<Resource>> entry : fileToIdConverter.listMatchingResourceStacks(resourceManager).entrySet()) {
            Identifier identifier = entry.getKey();
            Identifier identifier1 = fileToIdConverter.fileToId(identifier);

            for (Resource resource : entry.getValue()) {
                try (Reader reader = resource.openAsReader()) {
                    JsonElement jsonElement = StrictJsonParser.parse(reader);
                    List<TagLoader.EntryWithSource> list = map.computeIfAbsent(identifier1, path -> new ArrayList<>());
                    TagFile tagFile = TagFile.CODEC.parse(new Dynamic<>(JsonOps.INSTANCE, jsonElement)).getOrThrow();
                    if (tagFile.replace()) {
                        list.clear();
                    }

                    String string = resource.sourcePackId();
                    tagFile.entries().forEach(entry1 -> list.add(new TagLoader.EntryWithSource(entry1, string)));
                } catch (Exception var17) {
                    LOGGER.error("Couldn't read tag list {} from {} in data pack {}", identifier1, identifier, resource.sourcePackId(), var17);
                }
            }
        }

        return map;
    }

    private Either<List<TagLoader.EntryWithSource>, List<T>> tryBuildTag(TagEntry.Lookup<T> lookup, List<TagLoader.EntryWithSource> entries) {
        SequencedSet<T> set = new LinkedHashSet<>();
        List<TagLoader.EntryWithSource> list = new ArrayList<>();

        for (TagLoader.EntryWithSource entryWithSource : entries) {
            if (!entryWithSource.entry().build(lookup, set::add)) {
                list.add(entryWithSource);
            }
        }

        return list.isEmpty() ? Either.right(List.copyOf(set)) : Either.left(list);
    }

    // Paper start - fire tag registrar events
    public Map<Identifier, List<T>> build(Map<Identifier, List<TagLoader.EntryWithSource>> builders, io.papermc.paper.tag.@Nullable TagEventConfig<T, ?> eventConfig) {
        builders = io.papermc.paper.tag.PaperTagListenerManager.INSTANCE.firePreFlattenEvent(builders, eventConfig);
        // Paper end
        final Map<Identifier, List<T>> map = new HashMap<>();
        TagEntry.Lookup<T> lookup = new TagEntry.Lookup<T>() {
            @Override
            public @Nullable T element(Identifier id, boolean required) {
                return (T)TagLoader.this.elementLookup.get(id, required).orElse(null);
            }

            @Override
            public @Nullable Collection<T> tag(Identifier id) {
                return map.get(id);
            }
        };
        DependencySorter<Identifier, TagLoader.SortingEntry> dependencySorter = new DependencySorter<>();
        builders.forEach((path, entries) -> dependencySorter.addEntry(path, new TagLoader.SortingEntry((List<TagLoader.EntryWithSource>)entries)));
        dependencySorter.orderByDependencies(
            (path, sortingEntry) -> this.tryBuildTag(lookup, sortingEntry.entries)
                .ifLeft(
                    list -> LOGGER.error(
                        "Couldn't load tag {} as it is missing following references: {}",
                        path,
                        list.stream().map(Objects::toString).collect(Collectors.joining(", "))
                    )
                )
                .ifRight(list -> map.put(path, (List<T>)list))
        );
        return io.papermc.paper.tag.PaperTagListenerManager.INSTANCE.firePostFlattenEvent(map, eventConfig); // Paper - fire tag registrar events
    }

    public static <T> void loadTagsFromNetwork(TagNetworkSerialization.NetworkPayload payload, WritableRegistry<T> registry) {
        payload.resolve(registry).tags.forEach(registry::bindTag);
    }

    public static List<Registry.PendingTags<?>> loadTagsForExistingRegistries(ResourceManager resourceManager, RegistryAccess registryAccess) {
        // Paper start - tag lifecycle - add cause
        return loadTagsForExistingRegistries(resourceManager, registryAccess, io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent.Cause.INITIAL);
    }

    public static List<Registry.PendingTags<?>> loadTagsForExistingRegistries(ResourceManager resourceManager, RegistryAccess registryAccess, io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent.Cause cause) {
        // Paper end - tag lifecycle - add cause
        return registryAccess.registries()
            .map(registryEntry -> loadPendingTags(resourceManager, registryEntry.value(), cause)) // Paper - tag lifecycle - add cause
            .flatMap(Optional::stream)
            .collect(Collectors.toUnmodifiableList());
    }

    public static <T> void loadTagsForRegistry(ResourceManager resourceManager, WritableRegistry<T> registry) {
        // Paper start - tag lifecycle - add registrar event cause
        loadTagsForRegistry(resourceManager, registry, io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent.Cause.INITIAL);
    }
    public static <T> void loadTagsForRegistry(ResourceManager resourceManager, WritableRegistry<T> registry, io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent.Cause cause) {
        // Paper end - tag lifecycle - add registrar event cause
        ResourceKey<? extends Registry<T>> resourceKey = registry.key();
        TagLoader<Holder<T>> tagLoader = new TagLoader<>(TagLoader.ElementLookup.fromWritableRegistry(registry), Registries.tagsDirPath(resourceKey));
        tagLoader.build(tagLoader.load(resourceManager), io.papermc.paper.tag.PaperTagListenerManager.INSTANCE.createEventConfig(registry, cause)) // Paper - tag lifecycle - add registrar event cause
            .forEach((identifier, list) -> registry.bindTag(TagKey.create(resourceKey, identifier), (List<Holder<T>>)list));
    }

    private static <T> Map<TagKey<T>, List<Holder<T>>> wrapTags(ResourceKey<? extends Registry<T>> registryKey, Map<Identifier, List<Holder<T>>> tags) {
        return tags.entrySet().stream().collect(Collectors.toUnmodifiableMap(entry -> TagKey.create(registryKey, entry.getKey()), Entry::getValue));
    }

    private static <T> Optional<Registry.PendingTags<T>> loadPendingTags(ResourceManager resourceManager, Registry<T> registry, io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent.Cause cause) { // Paper - add registrar event cause
        ResourceKey<? extends Registry<T>> resourceKey = registry.key();
        TagLoader<Holder<T>> tagLoader = new TagLoader<>(
            (TagLoader.ElementLookup<Holder<T>>)TagLoader.ElementLookup.fromFrozenRegistry(registry), Registries.tagsDirPath(resourceKey)
        );
        TagLoader.LoadResult<T> loadResult = new TagLoader.LoadResult<>(resourceKey, wrapTags(registry.key(), tagLoader.build(tagLoader.load(resourceManager), io.papermc.paper.tag.PaperTagListenerManager.INSTANCE.createEventConfig(registry, cause)))); // Paper - add registrar event cause
        return loadResult.tags().isEmpty() ? Optional.empty() : Optional.of(registry.prepareTagReload(loadResult));
    }

    public static List<HolderLookup.RegistryLookup<?>> buildUpdatedLookups(RegistryAccess.Frozen registry, List<Registry.PendingTags<?>> tags) {
        List<HolderLookup.RegistryLookup<?>> list = new ArrayList<>();
        registry.registries().forEach(registryEntry -> {
            Registry.PendingTags<?> pendingTags = findTagsForRegistry(tags, registryEntry.key());
            list.add((HolderLookup.RegistryLookup<?>)(pendingTags != null ? pendingTags.lookup() : registryEntry.value()));
        });
        return list;
    }

    private static Registry.@Nullable PendingTags<?> findTagsForRegistry(List<Registry.PendingTags<?>> tags, ResourceKey<? extends Registry<?>> registryKey) {
        for (Registry.PendingTags<?> pendingTags : tags) {
            if (pendingTags.key() == registryKey) {
                return pendingTags;
            }
        }

        return null;
    }

    public interface ElementLookup<T> {
        Optional<? extends T> get(Identifier id, boolean required);

        static <T> TagLoader.ElementLookup<? extends Holder<T>> fromFrozenRegistry(Registry<T> registry) {
            return (id, required) -> registry.get(id);
        }

        static <T> TagLoader.ElementLookup<Holder<T>> fromWritableRegistry(WritableRegistry<T> registry) {
            HolderGetter<T> holderGetter = registry.createRegistrationLookup();
            return (id, required) -> ((HolderGetter<T>)(required ? holderGetter : registry)).get(ResourceKey.create(registry.key(), id));
        }
    }

    public record EntryWithSource(TagEntry entry, String source) {
        @Override
        public String toString() {
            return this.entry + " (from " + this.source + ")";
        }
    }

    public record LoadResult<T>(ResourceKey<? extends Registry<T>> key, Map<TagKey<T>, List<Holder<T>>> tags) {
    }

    record SortingEntry(List<TagLoader.EntryWithSource> entries) implements DependencySorter.Entry<Identifier> {
        @Override
        public void visitRequiredDependencies(Consumer<Identifier> visitor) {
            this.entries.forEach(entry -> entry.entry.visitRequiredDependencies(visitor));
        }

        @Override
        public void visitOptionalDependencies(Consumer<Identifier> visitor) {
            this.entries.forEach(entry -> entry.entry.visitOptionalDependencies(visitor));
        }
    }
}
