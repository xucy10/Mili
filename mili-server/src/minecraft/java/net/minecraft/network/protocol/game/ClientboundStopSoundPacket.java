package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import org.jspecify.annotations.Nullable;

public class ClientboundStopSoundPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundStopSoundPacket> STREAM_CODEC = Packet.codec(
        ClientboundStopSoundPacket::write, ClientboundStopSoundPacket::new
    );
    private static final int HAS_SOURCE = 1;
    private static final int HAS_SOUND = 2;
    private final @Nullable Identifier name;
    private final @Nullable SoundSource source;

    public ClientboundStopSoundPacket(@Nullable Identifier name, @Nullable SoundSource source) {
        this.name = name;
        this.source = source;
    }

    private ClientboundStopSoundPacket(FriendlyByteBuf buffer) {
        int _byte = buffer.readByte();
        if ((_byte & 1) > 0) {
            this.source = buffer.readEnum(SoundSource.class);
        } else {
            this.source = null;
        }

        if ((_byte & 2) > 0) {
            this.name = buffer.readIdentifier();
        } else {
            this.name = null;
        }
    }

    private void write(FriendlyByteBuf buffer) {
        if (this.source != null) {
            if (this.name != null) {
                buffer.writeByte(3);
                buffer.writeEnum(this.source);
                buffer.writeIdentifier(this.name);
            } else {
                buffer.writeByte(1);
                buffer.writeEnum(this.source);
            }
        } else if (this.name != null) {
            buffer.writeByte(2);
            buffer.writeIdentifier(this.name);
        } else {
            buffer.writeByte(0);
        }
    }

    @Override
    public PacketType<ClientboundStopSoundPacket> type() {
        return GamePacketTypes.CLIENTBOUND_STOP_SOUND;
    }

    @Override
    public void handle(ClientGamePacketListener handler) {
        handler.handleStopSoundEvent(this);
    }

    public @Nullable Identifier getName() {
        return this.name;
    }

    public @Nullable SoundSource getSource() {
        return this.source;
    }
}
