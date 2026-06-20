package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.entity.player.Abilities;

public class ClientboundPlayerAbilitiesPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundPlayerAbilitiesPacket> STREAM_CODEC = Packet.codec(
        ClientboundPlayerAbilitiesPacket::write, ClientboundPlayerAbilitiesPacket::new
    );
    private static final int FLAG_INVULNERABLE = 1;
    private static final int FLAG_FLYING = 2;
    private static final int FLAG_CAN_FLY = 4;
    private static final int FLAG_INSTABUILD = 8;
    private final boolean invulnerable;
    private final boolean isFlying;
    private final boolean canFly;
    private final boolean instabuild;
    private final float flyingSpeed;
    private final float walkingSpeed;

    public ClientboundPlayerAbilitiesPacket(Abilities abilities) {
        this.invulnerable = abilities.invulnerable;
        this.isFlying = abilities.flying;
        this.canFly = abilities.mayfly;
        this.instabuild = abilities.instabuild;
        this.flyingSpeed = abilities.getFlyingSpeed();
        this.walkingSpeed = abilities.getWalkingSpeed();
    }

    private ClientboundPlayerAbilitiesPacket(FriendlyByteBuf buffer) {
        byte _byte = buffer.readByte();
        this.invulnerable = (_byte & 1) != 0;
        this.isFlying = (_byte & 2) != 0;
        this.canFly = (_byte & 4) != 0;
        this.instabuild = (_byte & 8) != 0;
        this.flyingSpeed = buffer.readFloat();
        this.walkingSpeed = buffer.readFloat();
    }

    private void write(FriendlyByteBuf buffer) {
        byte b = 0;
        if (this.invulnerable) {
            b = (byte)(b | 1);
        }

        if (this.isFlying) {
            b = (byte)(b | 2);
        }

        if (this.canFly) {
            b = (byte)(b | 4);
        }

        if (this.instabuild) {
            b = (byte)(b | 8);
        }

        buffer.writeByte(b);
        buffer.writeFloat(this.flyingSpeed);
        buffer.writeFloat(this.walkingSpeed);
    }

    @Override
    public PacketType<ClientboundPlayerAbilitiesPacket> type() {
        return GamePacketTypes.CLIENTBOUND_PLAYER_ABILITIES;
    }

    @Override
    public void handle(ClientGamePacketListener handler) {
        handler.handlePlayerAbilities(this);
    }

    public boolean isInvulnerable() {
        return this.invulnerable;
    }

    public boolean isFlying() {
        return this.isFlying;
    }

    public boolean canFly() {
        return this.canFly;
    }

    public boolean canInstabuild() {
        return this.instabuild;
    }

    public float getFlyingSpeed() {
        return this.flyingSpeed;
    }

    public float getWalkingSpeed() {
        return this.walkingSpeed;
    }
}
