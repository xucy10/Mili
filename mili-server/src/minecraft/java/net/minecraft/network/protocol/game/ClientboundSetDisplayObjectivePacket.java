package net.minecraft.network.protocol.game;

import java.util.Objects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import org.jspecify.annotations.Nullable;

public class ClientboundSetDisplayObjectivePacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundSetDisplayObjectivePacket> STREAM_CODEC = Packet.codec(
        ClientboundSetDisplayObjectivePacket::write, ClientboundSetDisplayObjectivePacket::new
    );
    private final DisplaySlot slot;
    private final String objectiveName;

    public ClientboundSetDisplayObjectivePacket(DisplaySlot slot, @Nullable Objective objective) {
        this.slot = slot;
        if (objective == null) {
            this.objectiveName = "";
        } else {
            this.objectiveName = objective.getName();
        }
    }

    private ClientboundSetDisplayObjectivePacket(FriendlyByteBuf buffer) {
        this.slot = buffer.readById(DisplaySlot.BY_ID);
        this.objectiveName = buffer.readUtf();
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeById(DisplaySlot::id, this.slot);
        buffer.writeUtf(this.objectiveName);
    }

    @Override
    public PacketType<ClientboundSetDisplayObjectivePacket> type() {
        return GamePacketTypes.CLIENTBOUND_SET_DISPLAY_OBJECTIVE;
    }

    @Override
    public void handle(ClientGamePacketListener handler) {
        handler.handleSetDisplayObjective(this);
    }

    public DisplaySlot getSlot() {
        return this.slot;
    }

    public @Nullable String getObjectiveName() {
        return Objects.equals(this.objectiveName, "") ? null : this.objectiveName;
    }
}
