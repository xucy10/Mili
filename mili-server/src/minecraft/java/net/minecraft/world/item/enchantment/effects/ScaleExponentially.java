package net.minecraft.world.item.enchantment.effects;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.enchantment.LevelBasedValue;

public record ScaleExponentially(LevelBasedValue base, LevelBasedValue exponent) implements EnchantmentValueEffect {
    public static final MapCodec<ScaleExponentially> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                LevelBasedValue.CODEC.fieldOf("base").forGetter(ScaleExponentially::base),
                LevelBasedValue.CODEC.fieldOf("exponent").forGetter(ScaleExponentially::exponent)
            )
            .apply(instance, ScaleExponentially::new)
    );

    @Override
    public float process(int enchantmentLevel, RandomSource random, float value) {
        return (float)(value * Math.pow(this.base.calculate(enchantmentLevel), this.exponent.calculate(enchantmentLevel)));
    }

    @Override
    public MapCodec<ScaleExponentially> codec() {
        return CODEC;
    }
}
