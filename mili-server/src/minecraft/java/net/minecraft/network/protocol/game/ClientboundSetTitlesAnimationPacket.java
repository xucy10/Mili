package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ClientboundSetTitlesAnimationPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundSetTitlesAnimationPacket> STREAM_CODEC = Packet.codec(
        ClientboundSetTitlesAnimationPacket::write, ClientboundSetTitlesAnimationPacket::new
    );
    private final int fadeIn;
    private final int stay;
    private final int fadeOut;

    public ClientboundSetTitlesAnimationPacket(int fadeIn, int stay, int fadeOut) {
        this.fadeIn = fadeIn;
        this.stay = stay;
        this.fadeOut = fadeOut;
    }

    private ClientboundSetTitlesAnimationPacket(FriendlyByteBuf buffer) {
        this.fadeIn = buffer.readInt();
        this.stay = buffer.readInt();
        this.fadeOut = buffer.readInt();
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeInt(this.fadeIn);
        buffer.writeInt(this.stay);
        buffer.writeInt(this.fadeOut);
    }

    @Override
    public PacketType<ClientboundSetTitlesAnimationPacket> type() {
        return GamePacketTypes.CLIENTBOUND_SET_TITLES_ANIMATION;
    }

    @Override
    public void handle(ClientGamePacketListener handler) {
        handler.setTitlesAnimation(this);
    }

    public int getFadeIn() {
        return this.fadeIn;
    }

    public int getStay() {
        return this.stay;
    }

    public int getFadeOut() {
        return this.fadeOut;
    }
}
