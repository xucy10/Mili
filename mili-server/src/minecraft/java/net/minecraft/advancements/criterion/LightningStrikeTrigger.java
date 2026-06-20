package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.storage.loot.LootContext;

public class LightningStrikeTrigger extends SimpleCriterionTrigger<LightningStrikeTrigger.TriggerInstance> {
    @Override
    public Codec<LightningStrikeTrigger.TriggerInstance> codec() {
        return LightningStrikeTrigger.TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, LightningBolt lightning, List<Entity> nearbyEntities) {
        List<LootContext> list = nearbyEntities.stream().map(entity -> EntityPredicate.createContext(player, entity)).collect(Collectors.toList());
        LootContext lootContext = EntityPredicate.createContext(player, lightning);
        this.trigger(player, instance -> instance.matches(lootContext, list));
    }

    public record TriggerInstance(
        @Override Optional<ContextAwarePredicate> player, Optional<ContextAwarePredicate> lightning, Optional<ContextAwarePredicate> bystander
    ) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<LightningStrikeTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(LightningStrikeTrigger.TriggerInstance::player),
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("lightning").forGetter(LightningStrikeTrigger.TriggerInstance::lightning),
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("bystander").forGetter(LightningStrikeTrigger.TriggerInstance::bystander)
                )
                .apply(instance, LightningStrikeTrigger.TriggerInstance::new)
        );

        public static Criterion<LightningStrikeTrigger.TriggerInstance> lightningStrike(
            Optional<EntityPredicate> lightning, Optional<EntityPredicate> bystander
        ) {
            return CriteriaTriggers.LIGHTNING_STRIKE
                .createCriterion(new LightningStrikeTrigger.TriggerInstance(Optional.empty(), EntityPredicate.wrap(lightning), EntityPredicate.wrap(bystander)));
        }

        public boolean matches(LootContext playerContext, List<LootContext> entityContexts) {
            return (!this.lightning.isPresent() || this.lightning.get().matches(playerContext))
                && (!this.bystander.isPresent() || !entityContexts.stream().noneMatch(this.bystander.get()::matches));
        }

        @Override
        public void validate(CriterionValidator validator) {
            SimpleCriterionTrigger.SimpleInstance.super.validate(validator);
            validator.validateEntity(this.lightning, "lightning");
            validator.validateEntity(this.bystander, "bystander");
        }
    }
}
