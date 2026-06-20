package net.minecraft.advancements;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.ClientAsset;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

public class DisplayInfo {
    public static final Codec<DisplayInfo> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                ItemStack.STRICT_CODEC.fieldOf("icon").forGetter(DisplayInfo::getIcon),
                ComponentSerialization.CODEC.fieldOf("title").forGetter(DisplayInfo::getTitle),
                ComponentSerialization.CODEC.fieldOf("description").forGetter(DisplayInfo::getDescription),
                ClientAsset.ResourceTexture.CODEC.optionalFieldOf("background").forGetter(DisplayInfo::getBackground),
                AdvancementType.CODEC.optionalFieldOf("frame", AdvancementType.TASK).forGetter(DisplayInfo::getType),
                Codec.BOOL.optionalFieldOf("show_toast", true).forGetter(DisplayInfo::shouldShowToast),
                Codec.BOOL.optionalFieldOf("announce_to_chat", true).forGetter(DisplayInfo::shouldAnnounceChat),
                Codec.BOOL.optionalFieldOf("hidden", false).forGetter(DisplayInfo::isHidden)
            )
            .apply(instance, DisplayInfo::new)
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, DisplayInfo> STREAM_CODEC = StreamCodec.ofMember(
        DisplayInfo::serializeToNetwork, DisplayInfo::fromNetwork
    );
    private final Component title;
    private final Component description;
    private final ItemStack icon;
    private final Optional<ClientAsset.ResourceTexture> background;
    private final AdvancementType type;
    private final boolean showToast;
    private final boolean announceChat;
    private final boolean hidden;
    private float x;
    private float y;
    public final io.papermc.paper.advancement.AdvancementDisplay paper = new io.papermc.paper.advancement.PaperAdvancementDisplay(this); // Paper - Add more advancement API

    public DisplayInfo(
        ItemStack icon,
        Component title,
        Component description,
        Optional<ClientAsset.ResourceTexture> background,
        AdvancementType type,
        boolean showToast,
        boolean announceChat,
        boolean hidden
    ) {
        this.title = title;
        this.description = description;
        this.icon = icon;
        this.background = background;
        this.type = type;
        this.showToast = showToast;
        this.announceChat = announceChat;
        this.hidden = hidden;
    }

    public void setLocation(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public Component getTitle() {
        return this.title;
    }

    public Component getDescription() {
        return this.description;
    }

    public ItemStack getIcon() {
        return this.icon;
    }

    public Optional<ClientAsset.ResourceTexture> getBackground() {
        return this.background;
    }

    public AdvancementType getType() {
        return this.type;
    }

    public float getX() {
        return this.x;
    }

    public float getY() {
        return this.y;
    }

    public boolean shouldShowToast() {
        return this.showToast;
    }

    public boolean shouldAnnounceChat() {
        return this.announceChat;
    }

    public boolean isHidden() {
        return this.hidden;
    }

    private void serializeToNetwork(RegistryFriendlyByteBuf buffer) {
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buffer, this.title);
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buffer, this.description);
        ItemStack.STREAM_CODEC.encode(buffer, this.icon);
        buffer.writeEnum(this.type);
        int i = 0;
        if (this.background.isPresent()) {
            i |= 1;
        }

        if (this.showToast) {
            i |= 2;
        }

        if (this.hidden) {
            i |= 4;
        }

        buffer.writeInt(i);
        this.background.map(ClientAsset::id).ifPresent(buffer::writeIdentifier);
        buffer.writeFloat(this.x);
        buffer.writeFloat(this.y);
    }

    private static DisplayInfo fromNetwork(RegistryFriendlyByteBuf buffer) {
        Component component = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buffer);
        Component component1 = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buffer);
        ItemStack itemStack = ItemStack.STREAM_CODEC.decode(buffer);
        AdvancementType advancementType = buffer.readEnum(AdvancementType.class);
        int _int = buffer.readInt();
        Optional<ClientAsset.ResourceTexture> optional = (_int & 1) != 0
            ? Optional.of(new ClientAsset.ResourceTexture(buffer.readIdentifier()))
            : Optional.empty();
        boolean flag = (_int & 2) != 0;
        boolean flag1 = (_int & 4) != 0;
        DisplayInfo displayInfo = new DisplayInfo(itemStack, component, component1, optional, advancementType, flag, false, flag1);
        displayInfo.setLocation(buffer.readFloat(), buffer.readFloat());
        return displayInfo;
    }
}
