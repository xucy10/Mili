package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.damagesource.CombatTracker;

public class ClientboundPlayerCombatEndPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundPlayerCombatEndPacket> STREAM_CODEC = Packet.codec(
        ClientboundPlayerCombatEndPacket::write, ClientboundPlayerCombatEndPacket::new
    );
    private final int duration;

    public ClientboundPlayerCombatEndPacket(CombatTracker duration) {
        this(duration.getCombatDuration());
    }

    public ClientboundPlayerCombatEndPacket(int duration) {
        this.duration = duration;
    }

    private ClientboundPlayerCombatEndPacket(FriendlyByteBuf buffer) {
        this.duration = buffer.readVarInt();
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.duration);
    }

    @Override
    public PacketType<ClientboundPlayerCombatEndPacket> type() {
        return GamePacketTypes.CLIENTBOUND_PLAYER_COMBAT_END;
    }

    @Override
    public void handle(ClientGamePacketListener handler) {
        handler.handlePlayerCombatEnd(this);
    }
}
