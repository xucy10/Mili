package net.minecraft.server.network.config;

import com.mojang.serialization.DynamicOps;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.RegistrySynchronization;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundUpdateTagsPacket;
import net.minecraft.network.protocol.configuration.ClientboundRegistryDataPacket;
import net.minecraft.network.protocol.configuration.ClientboundSelectKnownPacks;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.tags.TagNetworkSerialization;

public class SynchronizeRegistriesTask implements ConfigurationTask {
    public static final ConfigurationTask.Type TYPE = new ConfigurationTask.Type("synchronize_registries");
    private final List<KnownPack> requestedPacks;
    private final LayeredRegistryAccess<RegistryLayer> registries;

    public SynchronizeRegistriesTask(List<KnownPack> requestedPacks, LayeredRegistryAccess<RegistryLayer> registries) {
        this.requestedPacks = requestedPacks;
        this.registries = registries;
    }

    @Override
    public void start(Consumer<Packet<?>> task) {
        task.accept(new ClientboundSelectKnownPacks(this.requestedPacks));
    }

    private void sendRegistries(Consumer<Packet<?>> packetSender, Set<KnownPack> packs) {
        DynamicOps<Tag> dynamicOps = this.registries.compositeAccess().createSerializationContext(NbtOps.INSTANCE);
        RegistrySynchronization.packRegistries(
            dynamicOps,
            this.registries.getAccessFrom(RegistryLayer.WORLDGEN),
            packs,
            (resourceKey, list) -> packetSender.accept(new ClientboundRegistryDataPacket(resourceKey, list))
        );
        packetSender.accept(new ClientboundUpdateTagsPacket(TagNetworkSerialization.serializeTagsToNetwork(this.registries)));
    }

    public void handleResponse(List<KnownPack> packs, Consumer<Packet<?>> packetSender) {
        if (packs.equals(this.requestedPacks)) {
            this.sendRegistries(packetSender, Set.copyOf(this.requestedPacks));
        } else {
            this.sendRegistries(packetSender, Set.of());
        }
    }

    @Override
    public ConfigurationTask.Type type() {
        return TYPE;
    }
}
