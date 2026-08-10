package net.minecraft.world.entity.ai.attributes;

import com.mojang.serialization.Codec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public class Attribute {
    public static final Codec<Holder<Attribute>> CODEC = BuiltInRegistries.ATTRIBUTE.holderByNameCodec();
    public static final StreamCodec<RegistryFriendlyByteBuf, Holder<Attribute>> STREAM_CODEC = ByteBufCodecs.holderRegistry(Registries.ATTRIBUTE);
    private final double defaultValue;
    private boolean syncable;
    private final String descriptionId;
    public Attribute.Sentiment sentiment = Attribute.Sentiment.POSITIVE;

    protected Attribute(String descriptionId, double defaultValue) {
        this.defaultValue = defaultValue;
        this.descriptionId = descriptionId;
    }

    public double getDefaultValue() {
        return this.defaultValue;
    }

    public boolean isClientSyncable() {
        return this.syncable;
    }

    public Attribute setSyncable(boolean syncable) {
        this.syncable = syncable;
        return this;
    }

    public Attribute setSentiment(Attribute.Sentiment sentiment) {
        this.sentiment = sentiment;
        return this;
    }

    public double sanitizeValue(double value) {
        return value;
    }

    public String getDescriptionId() {
        return this.descriptionId;
    }

    public ChatFormatting getStyle(boolean isPositive) {
        return this.sentiment.getStyle(isPositive);
    }

    public static enum Sentiment {
        POSITIVE,
        NEUTRAL,
        NEGATIVE;

        public ChatFormatting getStyle(boolean isPositive) {
            return switch (this) {
                case POSITIVE -> isPositive ? ChatFormatting.BLUE : ChatFormatting.RED;
                case NEUTRAL -> ChatFormatting.GRAY;
                case NEGATIVE -> isPositive ? ChatFormatting.RED : ChatFormatting.BLUE;
            };
        }
    }
}
