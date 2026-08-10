package net.minecraft.network.protocol.game;

import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.NumberFormatTypes;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

public class ClientboundSetObjectivePacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundSetObjectivePacket> STREAM_CODEC = Packet.codec(
        ClientboundSetObjectivePacket::write, ClientboundSetObjectivePacket::new
    );
    public static final int METHOD_ADD = 0;
    public static final int METHOD_REMOVE = 1;
    public static final int METHOD_CHANGE = 2;
    private final String objectiveName;
    private final Component displayName;
    private final ObjectiveCriteria.RenderType renderType;
    private final Optional<NumberFormat> numberFormat;
    private final int method;

    public ClientboundSetObjectivePacket(Objective objective, int method) {
        this.objectiveName = objective.getName();
        this.displayName = objective.getDisplayName();
        this.renderType = objective.getRenderType();
        this.numberFormat = Optional.ofNullable(objective.numberFormat());
        this.method = method;
    }

    private ClientboundSetObjectivePacket(RegistryFriendlyByteBuf buffer) {
        this.objectiveName = buffer.readUtf();
        this.method = buffer.readByte();
        if (this.method != 0 && this.method != 2) {
            this.displayName = CommonComponents.EMPTY;
            this.renderType = ObjectiveCriteria.RenderType.INTEGER;
            this.numberFormat = Optional.empty();
        } else {
            this.displayName = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buffer);
            this.renderType = buffer.readEnum(ObjectiveCriteria.RenderType.class);
            this.numberFormat = NumberFormatTypes.OPTIONAL_STREAM_CODEC.decode(buffer);
        }
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(this.objectiveName);
        buffer.writeByte(this.method);
        if (this.method == 0 || this.method == 2) {
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buffer, this.displayName);
            buffer.writeEnum(this.renderType);
            NumberFormatTypes.OPTIONAL_STREAM_CODEC.encode(buffer, this.numberFormat);
        }
    }

    @Override
    public PacketType<ClientboundSetObjectivePacket> type() {
        return GamePacketTypes.CLIENTBOUND_SET_OBJECTIVE;
    }

    @Override
    public void handle(ClientGamePacketListener handler) {
        handler.handleAddObjective(this);
    }

    public String getObjectiveName() {
        return this.objectiveName;
    }

    public Component getDisplayName() {
        return this.displayName;
    }

    public int getMethod() {
        return this.method;
    }

    public ObjectiveCriteria.RenderType getRenderType() {
        return this.renderType;
    }

    public Optional<NumberFormat> getNumberFormat() {
        return this.numberFormat;
    }
}
