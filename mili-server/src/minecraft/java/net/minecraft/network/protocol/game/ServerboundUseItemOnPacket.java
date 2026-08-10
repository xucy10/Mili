package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;

public class ServerboundUseItemOnPacket implements Packet<ServerGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ServerboundUseItemOnPacket> STREAM_CODEC = Packet.codec(
        ServerboundUseItemOnPacket::write, ServerboundUseItemOnPacket::new
    );
    private final BlockHitResult blockHit;
    private final InteractionHand hand;
    private final int sequence;
    public long timestamp; // Spigot

    public ServerboundUseItemOnPacket(InteractionHand hand, BlockHitResult blockHit, int sequence) {
        this.hand = hand;
        this.blockHit = blockHit;
        this.sequence = sequence;
    }

    private ServerboundUseItemOnPacket(FriendlyByteBuf buffer) {
        this.timestamp = System.currentTimeMillis(); // Spigot
        this.hand = buffer.readEnum(InteractionHand.class);
        this.blockHit = buffer.readBlockHitResult();
        this.sequence = buffer.readVarInt();
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeEnum(this.hand);
        buffer.writeBlockHitResult(this.blockHit);
        buffer.writeVarInt(this.sequence);
    }

    @Override
    public PacketType<ServerboundUseItemOnPacket> type() {
        return GamePacketTypes.SERVERBOUND_USE_ITEM_ON;
    }

    @Override
    public void handle(ServerGamePacketListener handler) {
        handler.handleUseItemOn(this);
    }

    public InteractionHand getHand() {
        return this.hand;
    }

    public BlockHitResult getHitResult() {
        return this.blockHit;
    }

    public int getSequence() {
        return this.sequence;
    }
}
