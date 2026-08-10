package net.minecraft.world.item.enchantment.effects;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.enchantment.LevelBasedValue;

public record RemoveBinomial(LevelBasedValue chance) implements EnchantmentValueEffect {
    public static final MapCodec<RemoveBinomial> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(LevelBasedValue.CODEC.fieldOf("chance").forGetter(RemoveBinomial::chance)).apply(instance, RemoveBinomial::new)
    );

    @Override
    public float process(int enchantmentLevel, RandomSource random, float value) {
        float f = this.chance.calculate(enchantmentLevel);
        int i = 0;
        if (!(value <= 128.0F) && !(value * f < 20.0F) && !(value * (1.0F - f) < 20.0F)) {
            double floor = Math.floor(value * f);
            double squareRoot = Math.sqrt(value * f * (1.0F - f));
            i = (int)Math.round(floor + random.nextGaussian() * squareRoot);
            i = Math.clamp((long)i, 0, (int)value);
        } else {
            for (int i1 = 0; i1 < value; i1++) {
                if (random.nextFloat() < f) {
                    i++;
                }
            }
        }

        return value - i;
    }

    @Override
    public MapCodec<RemoveBinomial> codec() {
        return CODEC;
    }
}
