package net.minecraft.world.item.component;

import com.mojang.serialization.Codec;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.EitherHolder;
import net.minecraft.world.item.equipment.trim.TrimMaterial;

public record ProvidesTrimMaterial(EitherHolder<TrimMaterial> material) {
    public static final Codec<ProvidesTrimMaterial> CODEC = EitherHolder.codec(Registries.TRIM_MATERIAL, TrimMaterial.CODEC)
        .xmap(ProvidesTrimMaterial::new, ProvidesTrimMaterial::material);
    public static final StreamCodec<RegistryFriendlyByteBuf, ProvidesTrimMaterial> STREAM_CODEC = EitherHolder.streamCodec(
            Registries.TRIM_MATERIAL, TrimMaterial.STREAM_CODEC
        )
        .map(ProvidesTrimMaterial::new, ProvidesTrimMaterial::material);

    public ProvidesTrimMaterial(Holder<TrimMaterial> material) {
        this(new EitherHolder<>(material));
    }

    @Deprecated
    public ProvidesTrimMaterial(ResourceKey<TrimMaterial> material) {
        this(new EitherHolder<>(material));
    }

    public Optional<Holder<TrimMaterial>> unwrap(HolderLookup.Provider registries) {
        return this.material.unwrap(registries);
    }
}
