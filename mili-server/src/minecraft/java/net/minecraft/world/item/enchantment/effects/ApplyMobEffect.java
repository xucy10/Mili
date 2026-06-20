package net.minecraft.world.item.enchantment.effects;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import net.minecraft.world.phys.Vec3;

public record ApplyMobEffect(
    HolderSet<MobEffect> toApply, LevelBasedValue minDuration, LevelBasedValue maxDuration, LevelBasedValue minAmplifier, LevelBasedValue maxAmplifier
) implements EnchantmentEntityEffect {
    public static final MapCodec<ApplyMobEffect> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                RegistryCodecs.homogeneousList(Registries.MOB_EFFECT).fieldOf("to_apply").forGetter(ApplyMobEffect::toApply),
                LevelBasedValue.CODEC.fieldOf("min_duration").forGetter(ApplyMobEffect::minDuration),
                LevelBasedValue.CODEC.fieldOf("max_duration").forGetter(ApplyMobEffect::maxDuration),
                LevelBasedValue.CODEC.fieldOf("min_amplifier").forGetter(ApplyMobEffect::minAmplifier),
                LevelBasedValue.CODEC.fieldOf("max_amplifier").forGetter(ApplyMobEffect::maxAmplifier)
            )
            .apply(instance, ApplyMobEffect::new)
    );

    @Override
    public void apply(ServerLevel level, int enchantmentLevel, EnchantedItemInUse item, Entity entity, Vec3 origin) {
        if (entity instanceof LivingEntity livingEntity) {
            RandomSource random = livingEntity.getRandom();
            Optional<Holder<MobEffect>> randomElement = this.toApply.getRandomElement(random);
            if (randomElement.isPresent()) {
                int rounded = Math.round(
                    Mth.randomBetween(random, this.minDuration.calculate(enchantmentLevel), this.maxDuration.calculate(enchantmentLevel)) * 20.0F
                );
                int max = Math.max(
                    0, Math.round(Mth.randomBetween(random, this.minAmplifier.calculate(enchantmentLevel), this.maxAmplifier.calculate(enchantmentLevel)))
                );
                livingEntity.addEffect(new MobEffectInstance(randomElement.get(), rounded, max), org.bukkit.event.entity.EntityPotionEffectEvent.Cause.ATTACK); // CraftBukkit
            }
        }
    }

    @Override
    public MapCodec<ApplyMobEffect> codec() {
        return CODEC;
    }
}
