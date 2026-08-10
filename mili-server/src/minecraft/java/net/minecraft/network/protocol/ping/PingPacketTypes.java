package net.minecraft.network.protocol.ping;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.resources.Identifier;

public class PingPacketTypes {
    public static final PacketType<ClientboundPongResponsePacket> CLIENTBOUND_PONG_RESPONSE = createClientbound("pong_response");
    public static final PacketType<ServerboundPingRequestPacket> SERVERBOUND_PING_REQUEST = createServerbound("ping_request");

    private static <T extends Packet<ClientPongPacketListener>> PacketType<T> createClientbound(String name) {
        return new PacketType<>(PacketFlow.CLIENTBOUND, Identifier.withDefaultNamespace(name));
    }

    private static <T extends Packet<ServerPingPacketListener>> PacketType<T> createServerbound(String name) {
        return new PacketType<>(PacketFlow.SERVERBOUND, Identifier.withDefaultNamespace(name));
    }
}
