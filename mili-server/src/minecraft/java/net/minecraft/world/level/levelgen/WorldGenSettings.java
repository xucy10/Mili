package net.minecraft.world.level.levelgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;

public record WorldGenSettings(WorldOptions options, WorldDimensions dimensions) {
    public static final Codec<WorldGenSettings> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(WorldOptions.CODEC.forGetter(WorldGenSettings::options), WorldDimensions.CODEC.forGetter(WorldGenSettings::dimensions))
            .apply(instance, instance.stable(WorldGenSettings::new))
    );

    public static <T> DataResult<T> encode(DynamicOps<T> ops, WorldOptions options, WorldDimensions dimensions) {
        return CODEC.encodeStart(ops, new WorldGenSettings(options, dimensions));
    }

    public static <T> DataResult<T> encode(DynamicOps<T> ops, WorldOptions options, RegistryAccess registryAccess) {
        return encode(ops, options, new WorldDimensions(registryAccess.lookupOrThrow(Registries.LEVEL_STEM)));
    }
}
