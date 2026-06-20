package net.minecraft.world.item.enchantment.effects;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.enchantment.LevelBasedValue;

public record MultiplyValue(LevelBasedValue factor) implements EnchantmentValueEffect {
    public static final MapCodec<MultiplyValue> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(LevelBasedValue.CODEC.fieldOf("factor").forGetter(MultiplyValue::factor)).apply(instance, MultiplyValue::new)
    );

    @Override
    public float process(int enchantmentLevel, RandomSource random, float value) {
        return value * this.factor.calculate(enchantmentLevel);
    }

    @Override
    public MapCodec<MultiplyValue> codec() {
        return CODEC;
    }
}
