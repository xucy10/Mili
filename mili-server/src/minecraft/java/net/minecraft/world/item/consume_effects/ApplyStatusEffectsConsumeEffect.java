package net.minecraft.world.item.consume_effects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public record ApplyStatusEffectsConsumeEffect(List<MobEffectInstance> effects, float probability) implements ConsumeEffect {
    public static final MapCodec<ApplyStatusEffectsConsumeEffect> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                MobEffectInstance.CODEC.listOf().fieldOf("effects").forGetter(ApplyStatusEffectsConsumeEffect::effects),
                Codec.floatRange(0.0F, 1.0F).optionalFieldOf("probability", 1.0F).forGetter(ApplyStatusEffectsConsumeEffect::probability)
            )
            .apply(instance, ApplyStatusEffectsConsumeEffect::new)
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, ApplyStatusEffectsConsumeEffect> STREAM_CODEC = StreamCodec.composite(
        MobEffectInstance.STREAM_CODEC.apply(ByteBufCodecs.list()),
        ApplyStatusEffectsConsumeEffect::effects,
        ByteBufCodecs.FLOAT,
        ApplyStatusEffectsConsumeEffect::probability,
        ApplyStatusEffectsConsumeEffect::new
    );

    public ApplyStatusEffectsConsumeEffect(MobEffectInstance effect, float probability) {
        this(List.of(effect), probability);
    }

    public ApplyStatusEffectsConsumeEffect(List<MobEffectInstance> effects) {
        this(effects, 1.0F);
    }

    public ApplyStatusEffectsConsumeEffect(MobEffectInstance effect) {
        this(effect, 1.0F);
    }

    @Override
    public ConsumeEffect.Type<ApplyStatusEffectsConsumeEffect> getType() {
        return ConsumeEffect.Type.APPLY_EFFECTS;
    }

    @Override
    public boolean apply(Level level, ItemStack stack, LivingEntity entity, org.bukkit.event.entity.EntityPotionEffectEvent.Cause cause) { // CraftBukkit
        if (entity.getRandom().nextFloat() >= this.probability) {
            return false;
        } else {
            boolean flag = false;

            for (MobEffectInstance mobEffectInstance : this.effects) {
                if (entity.addEffect(new MobEffectInstance(mobEffectInstance), cause)) { // CraftBukkit
                    flag = true;
                }
            }

            return flag;
        }
    }
}
