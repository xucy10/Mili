package net.minecraft.core;

import com.mojang.serialization.Codec;
import net.minecraft.resources.HolderSetCodec;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.resources.ResourceKey;

public class RegistryCodecs {
    public static <E> Codec<HolderSet<E>> homogeneousList(ResourceKey<? extends Registry<E>> registryKey, Codec<E> elementCodec) {
        return homogeneousList(registryKey, elementCodec, false);
    }

    public static <E> Codec<HolderSet<E>> homogeneousList(ResourceKey<? extends Registry<E>> registryKey, Codec<E> elementCodec, boolean disallowInline) {
        return HolderSetCodec.create(registryKey, RegistryFileCodec.create(registryKey, elementCodec), disallowInline);
    }

    public static <E> Codec<HolderSet<E>> homogeneousList(ResourceKey<? extends Registry<E>> registryKey) {
        return homogeneousList(registryKey, false);
    }

    public static <E> Codec<HolderSet<E>> homogeneousList(ResourceKey<? extends Registry<E>> registryKey, boolean disallowInline) {
        return HolderSetCodec.create(registryKey, RegistryFixedCodec.create(registryKey), disallowInline);
    }
}
