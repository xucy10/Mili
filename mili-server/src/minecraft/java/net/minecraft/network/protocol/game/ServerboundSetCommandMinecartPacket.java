package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.MinecartCommandBlock;
import net.minecraft.world.level.BaseCommandBlock;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public class ServerboundSetCommandMinecartPacket implements Packet<ServerGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ServerboundSetCommandMinecartPacket> STREAM_CODEC = Packet.codec(
        ServerboundSetCommandMinecartPacket::write, ServerboundSetCommandMinecartPacket::new
    );
    private final int entity;
    private final String command;
    private final boolean trackOutput;

    public ServerboundSetCommandMinecartPacket(int entity, String command, boolean trackOutput) {
        this.entity = entity;
        this.command = command;
        this.trackOutput = trackOutput;
    }

    private ServerboundSetCommandMinecartPacket(FriendlyByteBuf buffer) {
        this.entity = buffer.readVarInt();
        this.command = buffer.readUtf();
        this.trackOutput = buffer.readBoolean();
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.entity);
        buffer.writeUtf(this.command);
        buffer.writeBoolean(this.trackOutput);
    }

    @Override
    public PacketType<ServerboundSetCommandMinecartPacket> type() {
        return GamePacketTypes.SERVERBOUND_SET_COMMAND_MINECART;
    }

    @Override
    public void handle(ServerGamePacketListener handler) {
        handler.handleSetCommandMinecart(this);
    }

    public @Nullable BaseCommandBlock getCommandBlock(Level level) {
        Entity entity = level.getEntity(this.entity);
        return entity instanceof MinecartCommandBlock ? ((MinecartCommandBlock)entity).getCommandBlock() : null;
    }

    public String getCommand() {
        return this.command;
    }

    public boolean isTrackOutput() {
        return this.trackOutput;
    }
}
