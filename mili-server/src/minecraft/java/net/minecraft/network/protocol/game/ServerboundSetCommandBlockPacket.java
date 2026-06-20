package net.minecraft.network.protocol.game;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.level.block.entity.CommandBlockEntity;

public class ServerboundSetCommandBlockPacket implements Packet<ServerGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ServerboundSetCommandBlockPacket> STREAM_CODEC = Packet.codec(
        ServerboundSetCommandBlockPacket::write, ServerboundSetCommandBlockPacket::new
    );
    private static final int FLAG_TRACK_OUTPUT = 1;
    private static final int FLAG_CONDITIONAL = 2;
    private static final int FLAG_AUTOMATIC = 4;
    private final BlockPos pos;
    private final String command;
    private final boolean trackOutput;
    private final boolean conditional;
    private final boolean automatic;
    private final CommandBlockEntity.Mode mode;

    public ServerboundSetCommandBlockPacket(
        BlockPos pos, String command, CommandBlockEntity.Mode mode, boolean trackOutput, boolean conditional, boolean automatic
    ) {
        this.pos = pos;
        this.command = command;
        this.trackOutput = trackOutput;
        this.conditional = conditional;
        this.automatic = automatic;
        this.mode = mode;
    }

    private ServerboundSetCommandBlockPacket(FriendlyByteBuf buffer) {
        this.pos = buffer.readBlockPos();
        this.command = buffer.readUtf();
        this.mode = buffer.readEnum(CommandBlockEntity.Mode.class);
        int _byte = buffer.readByte();
        this.trackOutput = (_byte & 1) != 0;
        this.conditional = (_byte & 2) != 0;
        this.automatic = (_byte & 4) != 0;
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.pos);
        buffer.writeUtf(this.command);
        buffer.writeEnum(this.mode);
        int i = 0;
        if (this.trackOutput) {
            i |= 1;
        }

        if (this.conditional) {
            i |= 2;
        }

        if (this.automatic) {
            i |= 4;
        }

        buffer.writeByte(i);
    }

    @Override
    public PacketType<ServerboundSetCommandBlockPacket> type() {
        return GamePacketTypes.SERVERBOUND_SET_COMMAND_BLOCK;
    }

    @Override
    public void handle(ServerGamePacketListener handler) {
        handler.handleSetCommandBlock(this);
    }

    public BlockPos getPos() {
        return this.pos;
    }

    public String getCommand() {
        return this.command;
    }

    public boolean isTrackOutput() {
        return this.trackOutput;
    }

    public boolean isConditional() {
        return this.conditional;
    }

    public boolean isAutomatic() {
        return this.automatic;
    }

    public CommandBlockEntity.Mode getMode() {
        return this.mode;
    }
}
