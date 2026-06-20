package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;

public class PlayerInteractTrigger extends SimpleCriterionTrigger<PlayerInteractTrigger.TriggerInstance> {
    @Override
    public Codec<PlayerInteractTrigger.TriggerInstance> codec() {
        return PlayerInteractTrigger.TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, ItemStack item, Entity entity) {
        LootContext lootContext = EntityPredicate.createContext(player, entity);
        this.trigger(player, instance -> instance.matches(item, lootContext));
    }

    public record TriggerInstance(@Override Optional<ContextAwarePredicate> player, Optional<ItemPredicate> item, Optional<ContextAwarePredicate> entity)
        implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<PlayerInteractTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(PlayerInteractTrigger.TriggerInstance::player),
                    ItemPredicate.CODEC.optionalFieldOf("item").forGetter(PlayerInteractTrigger.TriggerInstance::item),
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("entity").forGetter(PlayerInteractTrigger.TriggerInstance::entity)
                )
                .apply(instance, PlayerInteractTrigger.TriggerInstance::new)
        );

        public static Criterion<PlayerInteractTrigger.TriggerInstance> itemUsedOnEntity(
            Optional<ContextAwarePredicate> player, ItemPredicate.Builder item, Optional<ContextAwarePredicate> entity
        ) {
            return CriteriaTriggers.PLAYER_INTERACTED_WITH_ENTITY
                .createCriterion(new PlayerInteractTrigger.TriggerInstance(player, Optional.of(item.build()), entity));
        }

        public static Criterion<PlayerInteractTrigger.TriggerInstance> equipmentSheared(
            Optional<ContextAwarePredicate> player, ItemPredicate.Builder item, Optional<ContextAwarePredicate> entity
        ) {
            return CriteriaTriggers.PLAYER_SHEARED_EQUIPMENT
                .createCriterion(new PlayerInteractTrigger.TriggerInstance(player, Optional.of(item.build()), entity));
        }

        public static Criterion<PlayerInteractTrigger.TriggerInstance> equipmentSheared(ItemPredicate.Builder item, Optional<ContextAwarePredicate> entity) {
            return CriteriaTriggers.PLAYER_SHEARED_EQUIPMENT
                .createCriterion(new PlayerInteractTrigger.TriggerInstance(Optional.empty(), Optional.of(item.build()), entity));
        }

        public static Criterion<PlayerInteractTrigger.TriggerInstance> itemUsedOnEntity(ItemPredicate.Builder item, Optional<ContextAwarePredicate> entity) {
            return itemUsedOnEntity(Optional.empty(), item, entity);
        }

        public boolean matches(ItemStack item, LootContext lootContext) {
            return (!this.item.isPresent() || this.item.get().test(item)) && (this.entity.isEmpty() || this.entity.get().matches(lootContext));
        }

        @Override
        public void validate(CriterionValidator validator) {
            SimpleCriterionTrigger.SimpleInstance.super.validate(validator);
            validator.validateEntity(this.entity, "entity");
        }
    }
}
