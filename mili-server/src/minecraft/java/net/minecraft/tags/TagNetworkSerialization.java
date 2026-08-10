package net.minecraft.tags;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.core.Holder;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistrySynchronization;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.RegistryLayer;

public class TagNetworkSerialization {
    public static Map<ResourceKey<? extends Registry<?>>, TagNetworkSerialization.NetworkPayload> serializeTagsToNetwork(
        LayeredRegistryAccess<RegistryLayer> registryAccess
    ) {
        return RegistrySynchronization.networkSafeRegistries(registryAccess)
            .map(registry -> Pair.of(registry.key(), serializeToNetwork(registry.value())))
            .filter(pair -> !pair.getSecond().isEmpty())
            .collect(Collectors.toMap(Pair::getFirst, Pair::getSecond));
    }

    private static <T> TagNetworkSerialization.NetworkPayload serializeToNetwork(Registry<T> registry) {
        Map<Identifier, IntList> map = new HashMap<>();
        registry.getTags().forEach(named -> {
            IntList list = new IntArrayList(named.size());

            for (Holder<T> holder : named) {
                if (holder.kind() != Holder.Kind.REFERENCE) {
                    throw new IllegalStateException("Can't serialize unregistered value " + holder);
                }

                list.add(registry.getId(holder.value()));
            }

            map.put(named.key().location(), list);
        });
        return new TagNetworkSerialization.NetworkPayload(map);
    }

    static <T> TagLoader.LoadResult<T> deserializeTagsFromNetwork(Registry<T> registry, TagNetworkSerialization.NetworkPayload payload) {
        ResourceKey<? extends Registry<T>> resourceKey = registry.key();
        Map<TagKey<T>, List<Holder<T>>> map = new HashMap<>();
        payload.tags.forEach((identifier, list) -> {
            TagKey<T> tagKey = TagKey.create(resourceKey, identifier);
            List<Holder<T>> list1 = list.intStream().mapToObj(registry::get).flatMap(Optional::stream).collect(Collectors.toUnmodifiableList());
            map.put(tagKey, list1);
        });
        return new TagLoader.LoadResult<>(resourceKey, map);
    }

    public static final class NetworkPayload {
        public static final TagNetworkSerialization.NetworkPayload EMPTY = new TagNetworkSerialization.NetworkPayload(Map.of());
        final Map<Identifier, IntList> tags;

        NetworkPayload(Map<Identifier, IntList> tags) {
            this.tags = tags;
        }

        public void write(FriendlyByteBuf buffer) {
            buffer.writeMap(this.tags, FriendlyByteBuf::writeIdentifier, FriendlyByteBuf::writeIntIdList);
        }

        public static TagNetworkSerialization.NetworkPayload read(FriendlyByteBuf buffer) {
            return new TagNetworkSerialization.NetworkPayload(buffer.readMap(FriendlyByteBuf::readIdentifier, FriendlyByteBuf::readIntIdList));
        }

        public boolean isEmpty() {
            return this.tags.isEmpty();
        }

        public int size() {
            return this.tags.size();
        }

        public <T> TagLoader.LoadResult<T> resolve(Registry<T> registry) {
            return TagNetworkSerialization.deserializeTagsFromNetwork(registry, this);
        }
    }
}
