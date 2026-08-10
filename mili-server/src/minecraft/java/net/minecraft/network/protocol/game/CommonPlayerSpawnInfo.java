package net.minecraft.network.protocol.game;

import java.util.Optional;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import org.jspecify.annotations.Nullable;

public record CommonPlayerSpawnInfo(
    Holder<DimensionType> dimensionType,
    ResourceKey<Level> dimension,
    long seed,
    GameType gameType,
    @Nullable GameType previousGameType,
    boolean isDebug,
    boolean isFlat,
    Optional<GlobalPos> lastDeathLocation,
    int portalCooldown,
    int seaLevel
) {
    public CommonPlayerSpawnInfo(RegistryFriendlyByteBuf buffer) {
        this(
            DimensionType.STREAM_CODEC.decode(buffer),
            buffer.readResourceKey(Registries.DIMENSION),
            buffer.readLong(),
            GameType.byId(buffer.readByte()),
            GameType.byNullableId(buffer.readByte()),
            buffer.readBoolean(),
            buffer.readBoolean(),
            buffer.readOptional(FriendlyByteBuf::readGlobalPos),
            buffer.readVarInt(),
            buffer.readVarInt()
        );
    }

    public void write(RegistryFriendlyByteBuf buffer) {
        DimensionType.STREAM_CODEC.encode(buffer, this.dimensionType);
        buffer.writeResourceKey(this.dimension);
        buffer.writeLong(this.seed);
        buffer.writeByte(this.gameType.getId());
        buffer.writeByte(GameType.getNullableId(this.previousGameType));
        buffer.writeBoolean(this.isDebug);
        buffer.writeBoolean(this.isFlat);
        buffer.writeOptional(this.lastDeathLocation, FriendlyByteBuf::writeGlobalPos);
        buffer.writeVarInt(this.portalCooldown);
        buffer.writeVarInt(this.seaLevel);
    }
}
