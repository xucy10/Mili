package net.minecraft.world.item.enchantment.effects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.function.Function;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.phys.Vec3;

public interface AllOf {
    static <T, A extends T> MapCodec<A> codec(Codec<T> codec, Function<List<T>, A> getter, Function<A, List<T>> factory) {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(codec.listOf().fieldOf("effects").forGetter(factory)).apply(instance, getter));
    }

    static AllOf.EntityEffects entityEffects(EnchantmentEntityEffect... effects) {
        return new AllOf.EntityEffects(List.of(effects));
    }

    static AllOf.LocationBasedEffects locationBasedEffects(EnchantmentLocationBasedEffect... effects) {
        return new AllOf.LocationBasedEffects(List.of(effects));
    }

    static AllOf.ValueEffects valueEffects(EnchantmentValueEffect... effects) {
        return new AllOf.ValueEffects(List.of(effects));
    }

    public record EntityEffects(List<EnchantmentEntityEffect> effects) implements EnchantmentEntityEffect {
        public static final MapCodec<AllOf.EntityEffects> CODEC = AllOf.codec(
            EnchantmentEntityEffect.CODEC, AllOf.EntityEffects::new, AllOf.EntityEffects::effects
        );

        @Override
        public void apply(ServerLevel level, int enchantmentLevel, EnchantedItemInUse item, Entity entity, Vec3 origin) {
            for (EnchantmentEntityEffect enchantmentEntityEffect : this.effects) {
                enchantmentEntityEffect.apply(level, enchantmentLevel, item, entity, origin);
            }
        }

        @Override
        public MapCodec<AllOf.EntityEffects> codec() {
            return CODEC;
        }
    }

    public record LocationBasedEffects(List<EnchantmentLocationBasedEffect> effects) implements EnchantmentLocationBasedEffect {
        public static final MapCodec<AllOf.LocationBasedEffects> CODEC = AllOf.codec(
            EnchantmentLocationBasedEffect.CODEC, AllOf.LocationBasedEffects::new, AllOf.LocationBasedEffects::effects
        );

        @Override
        public void onChangedBlock(ServerLevel level, int enchantmentLevel, EnchantedItemInUse item, Entity entity, Vec3 pos, boolean applyTransientEffects) {
            for (EnchantmentLocationBasedEffect enchantmentLocationBasedEffect : this.effects) {
                enchantmentLocationBasedEffect.onChangedBlock(level, enchantmentLevel, item, entity, pos, applyTransientEffects);
            }
        }

        @Override
        public void onDeactivated(EnchantedItemInUse item, Entity entity, Vec3 pos, int enchantmentLevel) {
            for (EnchantmentLocationBasedEffect enchantmentLocationBasedEffect : this.effects) {
                enchantmentLocationBasedEffect.onDeactivated(item, entity, pos, enchantmentLevel);
            }
        }

        @Override
        public MapCodec<AllOf.LocationBasedEffects> codec() {
            return CODEC;
        }
    }

    public record ValueEffects(List<EnchantmentValueEffect> effects) implements EnchantmentValueEffect {
        public static final MapCodec<AllOf.ValueEffects> CODEC = AllOf.codec(EnchantmentValueEffect.CODEC, AllOf.ValueEffects::new, AllOf.ValueEffects::effects);

        @Override
        public float process(int enchantmentLevel, RandomSource random, float value) {
            for (EnchantmentValueEffect enchantmentValueEffect : this.effects) {
                value = enchantmentValueEffect.process(enchantmentLevel, random, value);
            }

            return value;
        }

        @Override
        public MapCodec<AllOf.ValueEffects> codec() {
            return CODEC;
        }
    }
}
