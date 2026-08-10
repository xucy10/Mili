package net.minecraft.network.protocol.common;

import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagNetworkSerialization;

public class ClientboundUpdateTagsPacket implements Packet<ClientCommonPacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundUpdateTagsPacket> STREAM_CODEC = Packet.codec(
        ClientboundUpdateTagsPacket::write, ClientboundUpdateTagsPacket::new
    );
    private final Map<ResourceKey<? extends Registry<?>>, TagNetworkSerialization.NetworkPayload> tags;

    public ClientboundUpdateTagsPacket(Map<ResourceKey<? extends Registry<?>>, TagNetworkSerialization.NetworkPayload> tags) {
        this.tags = tags;
    }

    private ClientboundUpdateTagsPacket(FriendlyByteBuf buffer) {
        this.tags = buffer.readMap(FriendlyByteBuf::readRegistryKey, TagNetworkSerialization.NetworkPayload::read);
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeMap(this.tags, FriendlyByteBuf::writeResourceKey, (buffer1, value) -> value.write(buffer1));
    }

    @Override
    public PacketType<ClientboundUpdateTagsPacket> type() {
        return CommonPacketTypes.CLIENTBOUND_UPDATE_TAGS;
    }

    @Override
    public void handle(ClientCommonPacketListener handler) {
        handler.handleUpdateTags(this);
    }

    public Map<ResourceKey<? extends Registry<?>>, TagNetworkSerialization.NetworkPayload> getTags() {
        return this.tags;
    }
}
