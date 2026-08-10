package net.minecraft.world.entity.variant;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.ClientAsset;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

public record ModelAndTexture<T>(T model, ClientAsset.ResourceTexture asset) {
    public ModelAndTexture(T model, Identifier assetId) {
        this(model, new ClientAsset.ResourceTexture(assetId));
    }

    public static <T> MapCodec<ModelAndTexture<T>> codec(Codec<T> modelCodec, T defaultModel) {
        return RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    modelCodec.optionalFieldOf("model", defaultModel).forGetter(ModelAndTexture::model),
                    ClientAsset.ResourceTexture.DEFAULT_FIELD_CODEC.forGetter(ModelAndTexture::asset)
                )
                .apply(instance, ModelAndTexture::new)
        );
    }

    public static <T> StreamCodec<RegistryFriendlyByteBuf, ModelAndTexture<T>> streamCodec(StreamCodec<? super RegistryFriendlyByteBuf, T> modelCodec) {
        return StreamCodec.composite(modelCodec, ModelAndTexture::model, ClientAsset.ResourceTexture.STREAM_CODEC, ModelAndTexture::asset, ModelAndTexture::new);
    }
}
