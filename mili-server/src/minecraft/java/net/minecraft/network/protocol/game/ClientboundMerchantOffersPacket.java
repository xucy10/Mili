package net.minecraft.network.protocol.game;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.item.trading.MerchantOffers;

public class ClientboundMerchantOffersPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundMerchantOffersPacket> STREAM_CODEC = Packet.codec(
        ClientboundMerchantOffersPacket::write, ClientboundMerchantOffersPacket::new
    );
    private final int containerId;
    private final MerchantOffers offers;
    private final int villagerLevel;
    private final int villagerXp;
    private final boolean showProgress;
    private final boolean canRestock;

    public ClientboundMerchantOffersPacket(int containerId, MerchantOffers offers, int villagerLevel, int villagerXp, boolean showProgress, boolean canRestock) {
        this.containerId = containerId;
        this.offers = offers.copy();
        this.villagerLevel = villagerLevel;
        this.villagerXp = villagerXp;
        this.showProgress = showProgress;
        this.canRestock = canRestock;
    }

    private ClientboundMerchantOffersPacket(RegistryFriendlyByteBuf buffer) {
        this.containerId = buffer.readContainerId();
        this.offers = MerchantOffers.STREAM_CODEC.decode(buffer);
        this.villagerLevel = buffer.readVarInt();
        this.villagerXp = buffer.readVarInt();
        this.showProgress = buffer.readBoolean();
        this.canRestock = buffer.readBoolean();
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeContainerId(this.containerId);
        MerchantOffers.STREAM_CODEC.encode(buffer, this.offers);
        buffer.writeVarInt(this.villagerLevel);
        buffer.writeVarInt(this.villagerXp);
        buffer.writeBoolean(this.showProgress);
        buffer.writeBoolean(this.canRestock);
    }

    @Override
    public PacketType<ClientboundMerchantOffersPacket> type() {
        return GamePacketTypes.CLIENTBOUND_MERCHANT_OFFERS;
    }

    @Override
    public void handle(ClientGamePacketListener handler) {
        handler.handleMerchantOffers(this);
    }

    public int getContainerId() {
        return this.containerId;
    }

    public MerchantOffers getOffers() {
        return this.offers;
    }

    public int getVillagerLevel() {
        return this.villagerLevel;
    }

    public int getVillagerXp() {
        return this.villagerXp;
    }

    public boolean showProgress() {
        return this.showProgress;
    }

    public boolean canRestock() {
        return this.canRestock;
    }
}
