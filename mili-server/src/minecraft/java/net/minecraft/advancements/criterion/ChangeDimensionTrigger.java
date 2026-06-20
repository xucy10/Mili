package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

public class ChangeDimensionTrigger extends SimpleCriterionTrigger<ChangeDimensionTrigger.TriggerInstance> {
    @Override
    public Codec<ChangeDimensionTrigger.TriggerInstance> codec() {
        return ChangeDimensionTrigger.TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, ResourceKey<Level> fromLevel, ResourceKey<Level> toLevel) {
        this.trigger(player, instance -> instance.matches(fromLevel, toLevel));
    }

    public record TriggerInstance(@Override Optional<ContextAwarePredicate> player, Optional<ResourceKey<Level>> from, Optional<ResourceKey<Level>> to)
        implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<ChangeDimensionTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(ChangeDimensionTrigger.TriggerInstance::player),
                    ResourceKey.codec(Registries.DIMENSION).optionalFieldOf("from").forGetter(ChangeDimensionTrigger.TriggerInstance::from),
                    ResourceKey.codec(Registries.DIMENSION).optionalFieldOf("to").forGetter(ChangeDimensionTrigger.TriggerInstance::to)
                )
                .apply(instance, ChangeDimensionTrigger.TriggerInstance::new)
        );

        public static Criterion<ChangeDimensionTrigger.TriggerInstance> changedDimension() {
            return CriteriaTriggers.CHANGED_DIMENSION
                .createCriterion(new ChangeDimensionTrigger.TriggerInstance(Optional.empty(), Optional.empty(), Optional.empty()));
        }

        public static Criterion<ChangeDimensionTrigger.TriggerInstance> changedDimension(ResourceKey<Level> from, ResourceKey<Level> to) {
            return CriteriaTriggers.CHANGED_DIMENSION
                .createCriterion(new ChangeDimensionTrigger.TriggerInstance(Optional.empty(), Optional.of(from), Optional.of(to)));
        }

        public static Criterion<ChangeDimensionTrigger.TriggerInstance> changedDimensionTo(ResourceKey<Level> to) {
            return CriteriaTriggers.CHANGED_DIMENSION
                .createCriterion(new ChangeDimensionTrigger.TriggerInstance(Optional.empty(), Optional.empty(), Optional.of(to)));
        }

        public static Criterion<ChangeDimensionTrigger.TriggerInstance> changedDimensionFrom(ResourceKey<Level> from) {
            return CriteriaTriggers.CHANGED_DIMENSION
                .createCriterion(new ChangeDimensionTrigger.TriggerInstance(Optional.empty(), Optional.of(from), Optional.empty()));
        }

        public boolean matches(ResourceKey<Level> fromLevel, ResourceKey<Level> toLevel) {
            return (!this.from.isPresent() || this.from.get() == fromLevel) && (!this.to.isPresent() || this.to.get() == toLevel);
        }
    }
}
