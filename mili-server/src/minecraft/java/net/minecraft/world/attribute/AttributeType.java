package net.minecraft.world.attribute;

import com.google.common.collect.ImmutableBiMap;
import com.mojang.serialization.Codec;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Util;
import net.minecraft.world.attribute.modifier.AttributeModifier;

public record AttributeType<Value>(
    Codec<Value> valueCodec,
    Map<AttributeModifier.OperationId, AttributeModifier<Value, ?>> modifierLibrary,
    Codec<AttributeModifier<Value, ?>> modifierCodec,
    LerpFunction<Value> keyframeLerp,
    LerpFunction<Value> stateChangeLerp,
    LerpFunction<Value> spatialLerp,
    LerpFunction<Value> partialTickLerp
) {
    public static <Value> AttributeType<Value> ofInterpolated(
        Codec<Value> valueCodec, Map<AttributeModifier.OperationId, AttributeModifier<Value, ?>> modifierLibrary, LerpFunction<Value> lerp
    ) {
        return ofInterpolated(valueCodec, modifierLibrary, lerp, lerp);
    }

    public static <Value> AttributeType<Value> ofInterpolated(
        Codec<Value> valueCodec,
        Map<AttributeModifier.OperationId, AttributeModifier<Value, ?>> modifierLibrary,
        LerpFunction<Value> spatialLerp,
        LerpFunction<Value> partialTickLerp
    ) {
        return new AttributeType<>(valueCodec, modifierLibrary, createModifierCodec(modifierLibrary), spatialLerp, spatialLerp, spatialLerp, partialTickLerp);
    }

    public static <Value> AttributeType<Value> ofNotInterpolated(
        Codec<Value> valueCodec, Map<AttributeModifier.OperationId, AttributeModifier<Value, ?>> modifierLibrary
    ) {
        return new AttributeType<>(
            valueCodec,
            modifierLibrary,
            createModifierCodec(modifierLibrary),
            LerpFunction.ofStep(1.0F),
            LerpFunction.ofStep(0.0F),
            LerpFunction.ofStep(0.5F),
            LerpFunction.ofStep(0.0F)
        );
    }

    public static <Value> AttributeType<Value> ofNotInterpolated(Codec<Value> valueCodec) {
        return ofNotInterpolated(valueCodec, Map.of());
    }

    private static <Value> Codec<AttributeModifier<Value, ?>> createModifierCodec(
        Map<AttributeModifier.OperationId, AttributeModifier<Value, ?>> modifierLibrary
    ) {
        ImmutableBiMap<AttributeModifier.OperationId, AttributeModifier<Value, ?>> map = ImmutableBiMap.<AttributeModifier.OperationId, AttributeModifier<Value, ?>>builder()
            .put(AttributeModifier.OperationId.OVERRIDE, AttributeModifier.override())
            .putAll(modifierLibrary)
            .buildOrThrow();
        return ExtraCodecs.idResolverCodec(AttributeModifier.OperationId.CODEC, map::get, map.inverse()::get);
    }

    public void checkAllowedModifier(AttributeModifier<Value, ?> modifier) {
        if (modifier != AttributeModifier.override() && !this.modifierLibrary.containsValue(modifier)) {
            throw new IllegalArgumentException("Modifier " + modifier + " is not valid for " + this);
        }
    }

    @Override
    public String toString() {
        return Util.getRegisteredName(BuiltInRegistries.ATTRIBUTE_TYPE, this);
    }
}
