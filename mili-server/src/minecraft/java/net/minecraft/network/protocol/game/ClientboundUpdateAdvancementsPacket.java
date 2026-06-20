package net.minecraft.network.protocol.game;

import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.resources.Identifier;

public class ClientboundUpdateAdvancementsPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundUpdateAdvancementsPacket> STREAM_CODEC = Packet.codec(
        ClientboundUpdateAdvancementsPacket::write, ClientboundUpdateAdvancementsPacket::new
    );
    private final boolean reset;
    private final List<AdvancementHolder> added;
    private final Set<Identifier> removed;
    private final Map<Identifier, AdvancementProgress> progress;
    private final boolean showAdvancements;

    public ClientboundUpdateAdvancementsPacket(
        boolean reset, Collection<AdvancementHolder> added, Set<Identifier> removed, Map<Identifier, AdvancementProgress> progress, boolean showAdvancements
    ) {
        this.reset = reset;
        this.added = List.copyOf(added);
        this.removed = Set.copyOf(removed);
        this.progress = Map.copyOf(progress);
        this.showAdvancements = showAdvancements;
    }

    private ClientboundUpdateAdvancementsPacket(RegistryFriendlyByteBuf buffer) {
        this.reset = buffer.readBoolean();
        this.added = AdvancementHolder.LIST_STREAM_CODEC.decode(buffer);
        this.removed = buffer.readCollection(Sets::newLinkedHashSetWithExpectedSize, FriendlyByteBuf::readIdentifier);
        this.progress = buffer.readMap(FriendlyByteBuf::readIdentifier, AdvancementProgress::fromNetwork);
        this.showAdvancements = buffer.readBoolean();
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(this.reset);
        AdvancementHolder.LIST_STREAM_CODEC.encode(buffer, this.added);
        buffer.writeCollection(this.removed, FriendlyByteBuf::writeIdentifier);
        buffer.writeMap(this.progress, FriendlyByteBuf::writeIdentifier, (buffer1, value) -> value.serializeToNetwork(buffer1));
        buffer.writeBoolean(this.showAdvancements);
    }

    @Override
    public PacketType<ClientboundUpdateAdvancementsPacket> type() {
        return GamePacketTypes.CLIENTBOUND_UPDATE_ADVANCEMENTS;
    }

    @Override
    public void handle(ClientGamePacketListener handler) {
        handler.handleUpdateAdvancementsPacket(this);
    }

    public List<AdvancementHolder> getAdded() {
        return this.added;
    }

    public Set<Identifier> getRemoved() {
        return this.removed;
    }

    public Map<Identifier, AdvancementProgress> getProgress() {
        return this.progress;
    }

    public boolean shouldReset() {
        return this.reset;
    }

    public boolean shouldShowAdvancements() {
        return this.showAdvancements;
    }
}
