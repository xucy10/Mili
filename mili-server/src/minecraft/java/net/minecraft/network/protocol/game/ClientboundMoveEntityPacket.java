package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public abstract class ClientboundMoveEntityPacket implements Packet<ClientGamePacketListener> {
    protected final int entityId;
    protected final short xa;
    protected final short ya;
    protected final short za;
    protected final byte yRot;
    protected final byte xRot;
    protected final boolean onGround;
    protected final boolean hasRot;
    protected final boolean hasPos;

    protected ClientboundMoveEntityPacket(int entityId, short xa, short ya, short za, byte yRot, byte xRot, boolean onGround, boolean hasRot, boolean hasPos) {
        this.entityId = entityId;
        this.xa = xa;
        this.ya = ya;
        this.za = za;
        this.yRot = yRot;
        this.xRot = xRot;
        this.onGround = onGround;
        this.hasRot = hasRot;
        this.hasPos = hasPos;
    }

    @Override
    public abstract PacketType<? extends ClientboundMoveEntityPacket> type();

    @Override
    public void handle(ClientGamePacketListener handler) {
        handler.handleMoveEntity(this);
    }

    @Override
    public String toString() {
        return "Entity_" + super.toString();
    }

    public @Nullable Entity getEntity(Level level) {
        return level.getEntity(this.entityId);
    }

    public short getXa() {
        return this.xa;
    }

    public short getYa() {
        return this.ya;
    }

    public short getZa() {
        return this.za;
    }

    public float getYRot() {
        return Mth.unpackDegrees(this.yRot);
    }

    public float getXRot() {
        return Mth.unpackDegrees(this.xRot);
    }

    public boolean hasRotation() {
        return this.hasRot;
    }

    public boolean hasPosition() {
        return this.hasPos;
    }

    public boolean isOnGround() {
        return this.onGround;
    }

    public static class Pos extends ClientboundMoveEntityPacket {
        public static final StreamCodec<FriendlyByteBuf, ClientboundMoveEntityPacket.Pos> STREAM_CODEC = Packet.codec(
            ClientboundMoveEntityPacket.Pos::write, ClientboundMoveEntityPacket.Pos::read
        );

        public Pos(int entityId, short xa, short ya, short za, boolean onGround) {
            super(entityId, xa, ya, za, (byte)0, (byte)0, onGround, false, true);
        }

        private static ClientboundMoveEntityPacket.Pos read(FriendlyByteBuf buffer) {
            int varInt = buffer.readVarInt();
            short _short = buffer.readShort();
            short _short1 = buffer.readShort();
            short _short2 = buffer.readShort();
            boolean _boolean = buffer.readBoolean();
            return new ClientboundMoveEntityPacket.Pos(varInt, _short, _short1, _short2, _boolean);
        }

        private void write(FriendlyByteBuf buffer) {
            buffer.writeVarInt(this.entityId);
            buffer.writeShort(this.xa);
            buffer.writeShort(this.ya);
            buffer.writeShort(this.za);
            buffer.writeBoolean(this.onGround);
        }

        @Override
        public PacketType<ClientboundMoveEntityPacket.Pos> type() {
            return GamePacketTypes.CLIENTBOUND_MOVE_ENTITY_POS;
        }
    }

    public static class PosRot extends ClientboundMoveEntityPacket {
        public static final StreamCodec<FriendlyByteBuf, ClientboundMoveEntityPacket.PosRot> STREAM_CODEC = Packet.codec(
            ClientboundMoveEntityPacket.PosRot::write, ClientboundMoveEntityPacket.PosRot::read
        );

        public PosRot(int entityId, short xa, short ya, short za, byte yRot, byte xRot, boolean onGround) {
            super(entityId, xa, ya, za, yRot, xRot, onGround, true, true);
        }

        private static ClientboundMoveEntityPacket.PosRot read(FriendlyByteBuf buffer) {
            int varInt = buffer.readVarInt();
            short _short = buffer.readShort();
            short _short1 = buffer.readShort();
            short _short2 = buffer.readShort();
            byte _byte = buffer.readByte();
            byte _byte1 = buffer.readByte();
            boolean _boolean = buffer.readBoolean();
            return new ClientboundMoveEntityPacket.PosRot(varInt, _short, _short1, _short2, _byte, _byte1, _boolean);
        }

        private void write(FriendlyByteBuf buffer) {
            buffer.writeVarInt(this.entityId);
            buffer.writeShort(this.xa);
            buffer.writeShort(this.ya);
            buffer.writeShort(this.za);
            buffer.writeByte(this.yRot);
            buffer.writeByte(this.xRot);
            buffer.writeBoolean(this.onGround);
        }

        @Override
        public PacketType<ClientboundMoveEntityPacket.PosRot> type() {
            return GamePacketTypes.CLIENTBOUND_MOVE_ENTITY_POS_ROT;
        }
    }

    public static class Rot extends ClientboundMoveEntityPacket {
        public static final StreamCodec<FriendlyByteBuf, ClientboundMoveEntityPacket.Rot> STREAM_CODEC = Packet.codec(
            ClientboundMoveEntityPacket.Rot::write, ClientboundMoveEntityPacket.Rot::read
        );

        public Rot(int entityId, byte yRot, byte xRot, boolean onGround) {
            super(entityId, (short)0, (short)0, (short)0, yRot, xRot, onGround, true, false);
        }

        private static ClientboundMoveEntityPacket.Rot read(FriendlyByteBuf buffer) {
            int varInt = buffer.readVarInt();
            byte _byte = buffer.readByte();
            byte _byte1 = buffer.readByte();
            boolean _boolean = buffer.readBoolean();
            return new ClientboundMoveEntityPacket.Rot(varInt, _byte, _byte1, _boolean);
        }

        private void write(FriendlyByteBuf buffer) {
            buffer.writeVarInt(this.entityId);
            buffer.writeByte(this.yRot);
            buffer.writeByte(this.xRot);
            buffer.writeBoolean(this.onGround);
        }

        @Override
        public PacketType<ClientboundMoveEntityPacket.Rot> type() {
            return GamePacketTypes.CLIENTBOUND_MOVE_ENTITY_ROT;
        }
    }
}
