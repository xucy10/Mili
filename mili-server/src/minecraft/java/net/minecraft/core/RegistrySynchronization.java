package net.minecraft.core;

import com.mojang.serialization.DynamicOps;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.nbt.Tag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.packs.repository.KnownPack;

public class RegistrySynchronization {
    private static final Set<ResourceKey<? extends Registry<?>>> NETWORKABLE_REGISTRIES = RegistryDataLoader.SYNCHRONIZED_REGISTRIES
        .stream()
        .map(RegistryDataLoader.RegistryData::key)
        .collect(Collectors.toUnmodifiableSet());

    public static void packRegistries(
        DynamicOps<Tag> ops,
        RegistryAccess registryAccess,
        Set<KnownPack> packs,
        BiConsumer<ResourceKey<? extends Registry<?>>, List<RegistrySynchronization.PackedRegistryEntry>> packetSender
    ) {
        RegistryDataLoader.SYNCHRONIZED_REGISTRIES
            .forEach(registryData -> packRegistry(ops, (RegistryDataLoader.RegistryData<?>)registryData, registryAccess, packs, packetSender));
    }

    private static <T> void packRegistry(
        DynamicOps<Tag> ops,
        RegistryDataLoader.RegistryData<T> registryData,
        RegistryAccess registryAccess,
        Set<KnownPack> packs,
        BiConsumer<ResourceKey<? extends Registry<?>>, List<RegistrySynchronization.PackedRegistryEntry>> packetSender
    ) {
        registryAccess.lookup(registryData.key())
            .ifPresent(
                registry -> {
                    List<RegistrySynchronization.PackedRegistryEntry> list = new ArrayList<>(registry.size());
                    registry.listElements()
                        .forEach(
                            value -> {
                                boolean isPresent = registry.registrationInfo(value.key())
                                    .flatMap(RegistrationInfo::knownPackInfo)
                                    .filter(packs::contains)
                                    .isPresent();
                                Optional<Tag> optional;
                                if (isPresent) {
                                    optional = Optional.empty();
                                } else {
                                    Tag tag = registryData.elementCodec()
                                        .encodeStart(ops, value.value())
                                        .getOrThrow(string -> new IllegalArgumentException("Failed to serialize " + value.key() + ": " + string));
                                    optional = Optional.of(tag);
                                }

                                list.add(new RegistrySynchronization.PackedRegistryEntry(value.key().identifier(), optional));
                            }
                        );
                    packetSender.accept(registry.key(), list);
                }
            );
    }

    private static Stream<RegistryAccess.RegistryEntry<?>> ownedNetworkableRegistries(RegistryAccess registryAccess) {
        return registryAccess.registries().filter(registry -> isNetworkable(registry.key()));
    }

    public static Stream<RegistryAccess.RegistryEntry<?>> networkedRegistries(LayeredRegistryAccess<RegistryLayer> registryAccess) {
        return ownedNetworkableRegistries(registryAccess.getAccessFrom(RegistryLayer.WORLDGEN));
    }

    public static Stream<RegistryAccess.RegistryEntry<?>> networkSafeRegistries(LayeredRegistryAccess<RegistryLayer> registryAccess) {
        Stream<RegistryAccess.RegistryEntry<?>> stream = registryAccess.getLayer(RegistryLayer.STATIC).registries();
        Stream<RegistryAccess.RegistryEntry<?>> stream1 = networkedRegistries(registryAccess);
        return Stream.concat(stream1, stream);
    }

    public static boolean isNetworkable(ResourceKey<? extends Registry<?>> registryKey) {
        return NETWORKABLE_REGISTRIES.contains(registryKey);
    }

    public record PackedRegistryEntry(Identifier id, Optional<Tag> data) {
        public static final StreamCodec<ByteBuf, RegistrySynchronization.PackedRegistryEntry> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC,
            RegistrySynchronization.PackedRegistryEntry::id,
            ByteBufCodecs.TAG.apply(ByteBufCodecs::optional),
            RegistrySynchronization.PackedRegistryEntry::data,
            RegistrySynchronization.PackedRegistryEntry::new
        );
    }
}
