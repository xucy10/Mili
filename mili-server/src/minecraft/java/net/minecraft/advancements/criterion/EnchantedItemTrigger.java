package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class EnchantedItemTrigger extends SimpleCriterionTrigger<EnchantedItemTrigger.TriggerInstance> {
    @Override
    public Codec<EnchantedItemTrigger.TriggerInstance> codec() {
        return EnchantedItemTrigger.TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, ItemStack item, int levelsSpent) {
        this.trigger(player, instance -> instance.matches(item, levelsSpent));
    }

    public record TriggerInstance(@Override Optional<ContextAwarePredicate> player, Optional<ItemPredicate> item, MinMaxBounds.Ints levels)
        implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<EnchantedItemTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(EnchantedItemTrigger.TriggerInstance::player),
                    ItemPredicate.CODEC.optionalFieldOf("item").forGetter(EnchantedItemTrigger.TriggerInstance::item),
                    MinMaxBounds.Ints.CODEC.optionalFieldOf("levels", MinMaxBounds.Ints.ANY).forGetter(EnchantedItemTrigger.TriggerInstance::levels)
                )
                .apply(instance, EnchantedItemTrigger.TriggerInstance::new)
        );

        public static Criterion<EnchantedItemTrigger.TriggerInstance> enchantedItem() {
            return CriteriaTriggers.ENCHANTED_ITEM
                .createCriterion(new EnchantedItemTrigger.TriggerInstance(Optional.empty(), Optional.empty(), MinMaxBounds.Ints.ANY));
        }

        public boolean matches(ItemStack item, int levels) {
            return (!this.item.isPresent() || this.item.get().test(item)) && this.levels.matches(levels);
        }
    }
}
