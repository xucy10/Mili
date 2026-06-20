package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;

public class SpearMobsTrigger extends SimpleCriterionTrigger<SpearMobsTrigger.TriggerInstance> {
    @Override
    public Codec<SpearMobsTrigger.TriggerInstance> codec() {
        return SpearMobsTrigger.TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, int count) {
        this.trigger(player, triggerInstance -> triggerInstance.matches(count));
    }

    public record TriggerInstance(@Override Optional<ContextAwarePredicate> player, Optional<Integer> count) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<SpearMobsTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(SpearMobsTrigger.TriggerInstance::player),
                    ExtraCodecs.POSITIVE_INT.optionalFieldOf("count").forGetter(SpearMobsTrigger.TriggerInstance::count)
                )
                .apply(instance, SpearMobsTrigger.TriggerInstance::new)
        );

        public static Criterion<SpearMobsTrigger.TriggerInstance> spearMobs(int count) {
            return CriteriaTriggers.SPEAR_MOBS_TRIGGER.createCriterion(new SpearMobsTrigger.TriggerInstance(Optional.empty(), Optional.of(count)));
        }

        public boolean matches(int requiredCount) {
            return this.count.isEmpty() || requiredCount >= this.count.get();
        }
    }
}
