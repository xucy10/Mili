package net.minecraft.network.protocol.game;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.item.ItemStack;

public class ClientboundContainerSetSlotPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundContainerSetSlotPacket> STREAM_CODEC = Packet.codec(
        ClientboundContainerSetSlotPacket::write, ClientboundContainerSetSlotPacket::new
    );
    private final int containerId;
    private final int stateId;
    private final int slot;
    private final ItemStack itemStack;

    public ClientboundContainerSetSlotPacket(int containerId, int stateId, int slot, ItemStack itemStack) {
        this.containerId = containerId;
        this.stateId = stateId;
        this.slot = slot;
        this.itemStack = itemStack.copy();
    }

    private ClientboundContainerSetSlotPacket(RegistryFriendlyByteBuf buffer) {
        this.containerId = buffer.readContainerId();
        this.stateId = buffer.readVarInt();
        this.slot = buffer.readShort();
        this.itemStack = ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeContainerId(this.containerId);
        buffer.writeVarInt(this.stateId);
        buffer.writeShort(this.slot);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, this.itemStack);
    }

    @Override
    public PacketType<ClientboundContainerSetSlotPacket> type() {
        return GamePacketTypes.CLIENTBOUND_CONTAINER_SET_SLOT;
    }

    @Override
    public void handle(ClientGamePacketListener handler) {
        handler.handleContainerSetSlot(this);
    }

    public int getContainerId() {
        return this.containerId;
    }

    public int getSlot() {
        return this.slot;
    }

    public ItemStack getItem() {
        return this.itemStack;
    }

    public int getStateId() {
        return this.stateId;
    }
}
