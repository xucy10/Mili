package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class ItemDurabilityTrigger extends SimpleCriterionTrigger<ItemDurabilityTrigger.TriggerInstance> {
    @Override
    public Codec<ItemDurabilityTrigger.TriggerInstance> codec() {
        return ItemDurabilityTrigger.TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, ItemStack item, int newDurability) {
        this.trigger(player, instance -> instance.matches(item, newDurability));
    }

    public record TriggerInstance(
        @Override Optional<ContextAwarePredicate> player, Optional<ItemPredicate> item, MinMaxBounds.Ints durability, MinMaxBounds.Ints delta
    ) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<ItemDurabilityTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(ItemDurabilityTrigger.TriggerInstance::player),
                    ItemPredicate.CODEC.optionalFieldOf("item").forGetter(ItemDurabilityTrigger.TriggerInstance::item),
                    MinMaxBounds.Ints.CODEC.optionalFieldOf("durability", MinMaxBounds.Ints.ANY).forGetter(ItemDurabilityTrigger.TriggerInstance::durability),
                    MinMaxBounds.Ints.CODEC.optionalFieldOf("delta", MinMaxBounds.Ints.ANY).forGetter(ItemDurabilityTrigger.TriggerInstance::delta)
                )
                .apply(instance, ItemDurabilityTrigger.TriggerInstance::new)
        );

        public static Criterion<ItemDurabilityTrigger.TriggerInstance> changedDurability(Optional<ItemPredicate> item, MinMaxBounds.Ints durability) {
            return changedDurability(Optional.empty(), item, durability);
        }

        public static Criterion<ItemDurabilityTrigger.TriggerInstance> changedDurability(
            Optional<ContextAwarePredicate> player, Optional<ItemPredicate> item, MinMaxBounds.Ints durability
        ) {
            return CriteriaTriggers.ITEM_DURABILITY_CHANGED
                .createCriterion(new ItemDurabilityTrigger.TriggerInstance(player, item, durability, MinMaxBounds.Ints.ANY));
        }

        public boolean matches(ItemStack item, int durability) {
            return (!this.item.isPresent() || this.item.get().test(item))
                && this.durability.matches(item.getMaxDamage() - durability)
                && this.delta.matches(item.getDamageValue() - durability);
        }
    }
}
