package net.minecraft.network.protocol.game;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.level.block.Block;

public class ClientboundBlockEventPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundBlockEventPacket> STREAM_CODEC = Packet.codec(
        ClientboundBlockEventPacket::write, ClientboundBlockEventPacket::new
    );
    private final BlockPos pos;
    private final int b0;
    private final int b1;
    private final Block block;

    public ClientboundBlockEventPacket(BlockPos pos, Block block, int b0, int b1) {
        this.pos = pos;
        this.block = block;
        this.b0 = b0;
        this.b1 = b1;
    }

    private ClientboundBlockEventPacket(RegistryFriendlyByteBuf buffer) {
        this.pos = buffer.readBlockPos();
        this.b0 = buffer.readUnsignedByte();
        this.b1 = buffer.readUnsignedByte();
        this.block = ByteBufCodecs.registry(Registries.BLOCK).decode(buffer);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.pos);
        buffer.writeByte(this.b0);
        buffer.writeByte(this.b1);
        ByteBufCodecs.registry(Registries.BLOCK).encode(buffer, this.block);
    }

    @Override
    public PacketType<ClientboundBlockEventPacket> type() {
        return GamePacketTypes.CLIENTBOUND_BLOCK_EVENT;
    }

    @Override
    public void handle(ClientGamePacketListener handler) {
        handler.handleBlockEvent(this);
    }

    public BlockPos getPos() {
        return this.pos;
    }

    public int getB0() {
        return this.b0;
    }

    public int getB1() {
        return this.b1;
    }

    public Block getBlock() {
        return this.block;
    }
}
