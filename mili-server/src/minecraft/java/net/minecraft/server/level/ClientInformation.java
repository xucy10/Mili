package net.minecraft.server.level;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.entity.player.Player;

public record ClientInformation(
    String language,
    int viewDistance,
    ChatVisiblity chatVisibility,
    boolean chatColors,
    int modelCustomisation,
    HumanoidArm mainHand,
    boolean textFilteringEnabled,
    boolean allowsListing,
    ParticleStatus particleStatus
) {
    public static final int MAX_LANGUAGE_LENGTH = 16;

    public ClientInformation(FriendlyByteBuf buffer) {
        this(
            buffer.readUtf(16),
            buffer.readByte(),
            buffer.readEnum(ChatVisiblity.class),
            buffer.readBoolean(),
            buffer.readUnsignedByte(),
            buffer.readEnum(HumanoidArm.class),
            buffer.readBoolean(),
            buffer.readBoolean(),
            buffer.readEnum(ParticleStatus.class)
        );
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(this.language);
        buffer.writeByte(this.viewDistance);
        buffer.writeEnum(this.chatVisibility);
        buffer.writeBoolean(this.chatColors);
        buffer.writeByte(this.modelCustomisation);
        buffer.writeEnum(this.mainHand);
        buffer.writeBoolean(this.textFilteringEnabled);
        buffer.writeBoolean(this.allowsListing);
        buffer.writeEnum(this.particleStatus);
    }

    public static ClientInformation createDefault() {
        return new ClientInformation("en_us", 2, ChatVisiblity.FULL, true, 0, Player.DEFAULT_MAIN_HAND, false, false, ParticleStatus.ALL);
    }
}
