package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.storage.loot.LootContext;
import org.jspecify.annotations.Nullable;

public class BredAnimalsTrigger extends SimpleCriterionTrigger<BredAnimalsTrigger.TriggerInstance> {
    @Override
    public Codec<BredAnimalsTrigger.TriggerInstance> codec() {
        return BredAnimalsTrigger.TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, Animal parent, Animal partner, @Nullable AgeableMob child) {
        LootContext lootContext = EntityPredicate.createContext(player, parent);
        LootContext lootContext1 = EntityPredicate.createContext(player, partner);
        LootContext lootContext2 = child != null ? EntityPredicate.createContext(player, child) : null;
        this.trigger(player, instance -> instance.matches(lootContext, lootContext1, lootContext2));
    }

    public record TriggerInstance(
        @Override Optional<ContextAwarePredicate> player,
        Optional<ContextAwarePredicate> parent,
        Optional<ContextAwarePredicate> partner,
        Optional<ContextAwarePredicate> child
    ) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<BredAnimalsTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(BredAnimalsTrigger.TriggerInstance::player),
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("parent").forGetter(BredAnimalsTrigger.TriggerInstance::parent),
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("partner").forGetter(BredAnimalsTrigger.TriggerInstance::partner),
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("child").forGetter(BredAnimalsTrigger.TriggerInstance::child)
                )
                .apply(instance, BredAnimalsTrigger.TriggerInstance::new)
        );

        public static Criterion<BredAnimalsTrigger.TriggerInstance> bredAnimals() {
            return CriteriaTriggers.BRED_ANIMALS
                .createCriterion(new BredAnimalsTrigger.TriggerInstance(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
        }

        public static Criterion<BredAnimalsTrigger.TriggerInstance> bredAnimals(EntityPredicate.Builder child) {
            return CriteriaTriggers.BRED_ANIMALS
                .createCriterion(
                    new BredAnimalsTrigger.TriggerInstance(Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(EntityPredicate.wrap(child)))
                );
        }

        public static Criterion<BredAnimalsTrigger.TriggerInstance> bredAnimals(
            Optional<EntityPredicate> parent, Optional<EntityPredicate> partner, Optional<EntityPredicate> child
        ) {
            return CriteriaTriggers.BRED_ANIMALS
                .createCriterion(
                    new BredAnimalsTrigger.TriggerInstance(
                        Optional.empty(), EntityPredicate.wrap(parent), EntityPredicate.wrap(partner), EntityPredicate.wrap(child)
                    )
                );
        }

        public boolean matches(LootContext parentContext, LootContext partnerContext, @Nullable LootContext childContext) {
            return (!this.child.isPresent() || childContext != null && this.child.get().matches(childContext))
                && (
                    matches(this.parent, parentContext) && matches(this.partner, partnerContext)
                        || matches(this.parent, partnerContext) && matches(this.partner, parentContext)
                );
        }

        private static boolean matches(Optional<ContextAwarePredicate> predicate, LootContext context) {
            return predicate.isEmpty() || predicate.get().matches(context);
        }

        @Override
        public void validate(CriterionValidator validator) {
            SimpleCriterionTrigger.SimpleInstance.super.validate(validator);
            validator.validateEntity(this.parent, "parent");
            validator.validateEntity(this.partner, "partner");
            validator.validateEntity(this.child, "child");
        }
    }
}
