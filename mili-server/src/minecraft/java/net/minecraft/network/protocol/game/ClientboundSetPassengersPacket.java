package net.minecraft.network.protocol.game;

import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.entity.Entity;

public class ClientboundSetPassengersPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundSetPassengersPacket> STREAM_CODEC = Packet.codec(
        ClientboundSetPassengersPacket::write, ClientboundSetPassengersPacket::new
    );
    private final int vehicle;
    private final int[] passengers;

    public ClientboundSetPassengersPacket(Entity vehicle) {
        this.vehicle = vehicle.getId();
        List<Entity> passengers = vehicle.getPassengers();
        this.passengers = new int[passengers.size()];

        for (int i = 0; i < passengers.size(); i++) {
            this.passengers[i] = passengers.get(i).getId();
        }
    }

    private ClientboundSetPassengersPacket(FriendlyByteBuf buffer) {
        this.vehicle = buffer.readVarInt();
        this.passengers = buffer.readVarIntArray();
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.vehicle);
        buffer.writeVarIntArray(this.passengers);
    }

    @Override
    public PacketType<ClientboundSetPassengersPacket> type() {
        return GamePacketTypes.CLIENTBOUND_SET_PASSENGERS;
    }

    @Override
    public void handle(ClientGamePacketListener handler) {
        handler.handleSetEntityPassengersPacket(this);
    }

    public int[] getPassengers() {
        return this.passengers;
    }

    public int getVehicle() {
        return this.vehicle;
    }
}
