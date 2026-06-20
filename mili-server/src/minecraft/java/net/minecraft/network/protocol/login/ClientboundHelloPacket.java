package net.minecraft.network.protocol.login;

import java.security.PublicKey;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.util.Crypt;
import net.minecraft.util.CryptException;

public class ClientboundHelloPacket implements Packet<ClientLoginPacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundHelloPacket> STREAM_CODEC = Packet.codec(
        ClientboundHelloPacket::write, ClientboundHelloPacket::new
    );
    private final String serverId;
    private final byte[] publicKey;
    private final byte[] challenge;
    private final boolean shouldAuthenticate;

    public ClientboundHelloPacket(String serverId, byte[] publicKey, byte[] challenge, boolean shouldAuthenticate) {
        this.serverId = serverId;
        this.publicKey = publicKey;
        this.challenge = challenge;
        this.shouldAuthenticate = shouldAuthenticate;
    }

    private ClientboundHelloPacket(FriendlyByteBuf buffer) {
        this.serverId = buffer.readUtf(20);
        this.publicKey = buffer.readByteArray();
        this.challenge = buffer.readByteArray();
        this.shouldAuthenticate = buffer.readBoolean();
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(this.serverId);
        buffer.writeByteArray(this.publicKey);
        buffer.writeByteArray(this.challenge);
        buffer.writeBoolean(this.shouldAuthenticate);
    }

    @Override
    public PacketType<ClientboundHelloPacket> type() {
        return LoginPacketTypes.CLIENTBOUND_HELLO;
    }

    @Override
    public void handle(ClientLoginPacketListener handler) {
        handler.handleHello(this);
    }

    public String getServerId() {
        return this.serverId;
    }

    public PublicKey getPublicKey() throws CryptException {
        return Crypt.byteToPublicKey(this.publicKey);
    }

    public byte[] getChallenge() {
        return this.challenge;
    }

    public boolean shouldAuthenticate() {
        return this.shouldAuthenticate;
    }
}
