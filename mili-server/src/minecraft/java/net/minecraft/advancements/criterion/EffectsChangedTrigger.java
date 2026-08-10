package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.storage.loot.LootContext;
import org.jspecify.annotations.Nullable;

public class EffectsChangedTrigger extends SimpleCriterionTrigger<EffectsChangedTrigger.TriggerInstance> {
    @Override
    public Codec<EffectsChangedTrigger.TriggerInstance> codec() {
        return EffectsChangedTrigger.TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, @Nullable Entity source) {
        LootContext lootContext = source != null ? EntityPredicate.createContext(player, source) : null;
        this.trigger(player, instance -> instance.matches(player, lootContext));
    }

    public record TriggerInstance(
        @Override Optional<ContextAwarePredicate> player, Optional<MobEffectsPredicate> effects, Optional<ContextAwarePredicate> source
    ) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<EffectsChangedTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(EffectsChangedTrigger.TriggerInstance::player),
                    MobEffectsPredicate.CODEC.optionalFieldOf("effects").forGetter(EffectsChangedTrigger.TriggerInstance::effects),
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("source").forGetter(EffectsChangedTrigger.TriggerInstance::source)
                )
                .apply(instance, EffectsChangedTrigger.TriggerInstance::new)
        );

        public static Criterion<EffectsChangedTrigger.TriggerInstance> hasEffects(MobEffectsPredicate.Builder effects) {
            return CriteriaTriggers.EFFECTS_CHANGED
                .createCriterion(new EffectsChangedTrigger.TriggerInstance(Optional.empty(), effects.build(), Optional.empty()));
        }

        public static Criterion<EffectsChangedTrigger.TriggerInstance> gotEffectsFrom(EntityPredicate.Builder source) {
            return CriteriaTriggers.EFFECTS_CHANGED
                .createCriterion(
                    new EffectsChangedTrigger.TriggerInstance(Optional.empty(), Optional.empty(), Optional.of(EntityPredicate.wrap(source.build())))
                );
        }

        public boolean matches(ServerPlayer player, @Nullable LootContext lootContext) {
            return (!this.effects.isPresent() || this.effects.get().matches((LivingEntity)player))
                && (!this.source.isPresent() || lootContext != null && this.source.get().matches(lootContext));
        }

        @Override
        public void validate(CriterionValidator validator) {
            SimpleCriterionTrigger.SimpleInstance.super.validate(validator);
            validator.validateEntity(this.source, "source");
        }
    }
}
