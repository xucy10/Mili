package net.minecraft.network.protocol.game;

import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ClientboundBundlePacket extends BundlePacket<ClientGamePacketListener> {
    public ClientboundBundlePacket(Iterable<Packet<? super ClientGamePacketListener>> packets) {
        super(packets);
    }

    @Override
    public PacketType<ClientboundBundlePacket> type() {
        return GamePacketTypes.CLIENTBOUND_BUNDLE;
    }

    @Override
    public void handle(ClientGamePacketListener handler) {
        handler.handleBundlePacket(this);
    }
}
