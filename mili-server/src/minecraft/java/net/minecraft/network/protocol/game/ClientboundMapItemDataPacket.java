package net.minecraft.network.protocol.game;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jspecify.annotations.Nullable;

public record ClientboundMapItemDataPacket(
    MapId mapId, byte scale, boolean locked, Optional<List<MapDecoration>> decorations, Optional<MapItemSavedData.MapPatch> colorPatch
) implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundMapItemDataPacket> STREAM_CODEC = StreamCodec.composite(
        MapId.STREAM_CODEC,
        ClientboundMapItemDataPacket::mapId,
        ByteBufCodecs.BYTE,
        ClientboundMapItemDataPacket::scale,
        ByteBufCodecs.BOOL,
        ClientboundMapItemDataPacket::locked,
        MapDecoration.STREAM_CODEC.apply(ByteBufCodecs.list()).apply(ByteBufCodecs::optional),
        ClientboundMapItemDataPacket::decorations,
        MapItemSavedData.MapPatch.STREAM_CODEC,
        ClientboundMapItemDataPacket::colorPatch,
        ClientboundMapItemDataPacket::new
    );

    public ClientboundMapItemDataPacket(
        MapId mapId, byte scale, boolean locked, @Nullable Collection<MapDecoration> decorations, MapItemSavedData.@Nullable MapPatch colorPatch
    ) {
        this(mapId, scale, locked, decorations != null ? Optional.of(List.copyOf(decorations)) : Optional.empty(), Optional.ofNullable(colorPatch));
    }

    @Override
    public PacketType<ClientboundMapItemDataPacket> type() {
        return GamePacketTypes.CLIENTBOUND_MAP_ITEM_DATA;
    }

    @Override
    public void handle(ClientGamePacketListener handler) {
        handler.handleMapItemData(this);
    }

    public void applyToMap(MapItemSavedData mapData) {
        this.decorations.ifPresent(mapData::addClientSideDecorations);
        this.colorPatch.ifPresent(mapPatch -> mapPatch.applyToMap(mapData));
    }
}
