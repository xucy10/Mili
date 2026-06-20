package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.inventory.RecipeBookType;

public class ServerboundRecipeBookChangeSettingsPacket implements Packet<ServerGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ServerboundRecipeBookChangeSettingsPacket> STREAM_CODEC = Packet.codec(
        ServerboundRecipeBookChangeSettingsPacket::write, ServerboundRecipeBookChangeSettingsPacket::new
    );
    private final RecipeBookType bookType;
    private final boolean isOpen;
    private final boolean isFiltering;

    public ServerboundRecipeBookChangeSettingsPacket(RecipeBookType bookType, boolean isOpen, boolean isFiltering) {
        this.bookType = bookType;
        this.isOpen = isOpen;
        this.isFiltering = isFiltering;
    }

    private ServerboundRecipeBookChangeSettingsPacket(FriendlyByteBuf buffer) {
        this.bookType = buffer.readEnum(RecipeBookType.class);
        this.isOpen = buffer.readBoolean();
        this.isFiltering = buffer.readBoolean();
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeEnum(this.bookType);
        buffer.writeBoolean(this.isOpen);
        buffer.writeBoolean(this.isFiltering);
    }

    @Override
    public PacketType<ServerboundRecipeBookChangeSettingsPacket> type() {
        return GamePacketTypes.SERVERBOUND_RECIPE_BOOK_CHANGE_SETTINGS;
    }

    @Override
    public void handle(ServerGamePacketListener handler) {
        handler.handleRecipeBookChangeSettingsPacket(this);
    }

    public RecipeBookType getBookType() {
        return this.bookType;
    }

    public boolean isOpen() {
        return this.isOpen;
    }

    public boolean isFiltering() {
        return this.isFiltering;
    }
}
