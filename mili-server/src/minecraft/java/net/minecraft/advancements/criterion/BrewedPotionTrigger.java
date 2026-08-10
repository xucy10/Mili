package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.alchemy.Potion;

public class BrewedPotionTrigger extends SimpleCriterionTrigger<BrewedPotionTrigger.TriggerInstance> {
    @Override
    public Codec<BrewedPotionTrigger.TriggerInstance> codec() {
        return BrewedPotionTrigger.TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, Holder<Potion> potion) {
        this.trigger(player, trigger -> trigger.matches(potion));
    }

    public record TriggerInstance(@Override Optional<ContextAwarePredicate> player, Optional<Holder<Potion>> potion)
        implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<BrewedPotionTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(BrewedPotionTrigger.TriggerInstance::player),
                    Potion.CODEC.optionalFieldOf("potion").forGetter(BrewedPotionTrigger.TriggerInstance::potion)
                )
                .apply(instance, BrewedPotionTrigger.TriggerInstance::new)
        );

        public static Criterion<BrewedPotionTrigger.TriggerInstance> brewedPotion() {
            return CriteriaTriggers.BREWED_POTION.createCriterion(new BrewedPotionTrigger.TriggerInstance(Optional.empty(), Optional.empty()));
        }

        public boolean matches(Holder<Potion> potion) {
            return !this.potion.isPresent() || this.potion.get().equals(potion);
        }
    }
}
