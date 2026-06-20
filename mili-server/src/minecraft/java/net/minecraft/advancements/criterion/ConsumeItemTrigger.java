package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.core.HolderGetter;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

public class ConsumeItemTrigger extends SimpleCriterionTrigger<ConsumeItemTrigger.TriggerInstance> {
    @Override
    public Codec<ConsumeItemTrigger.TriggerInstance> codec() {
        return ConsumeItemTrigger.TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, ItemStack item) {
        this.trigger(player, instance -> instance.matches(item));
    }

    public record TriggerInstance(@Override Optional<ContextAwarePredicate> player, Optional<ItemPredicate> item)
        implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<ConsumeItemTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(ConsumeItemTrigger.TriggerInstance::player),
                    ItemPredicate.CODEC.optionalFieldOf("item").forGetter(ConsumeItemTrigger.TriggerInstance::item)
                )
                .apply(instance, ConsumeItemTrigger.TriggerInstance::new)
        );

        public static Criterion<ConsumeItemTrigger.TriggerInstance> usedItem() {
            return CriteriaTriggers.CONSUME_ITEM.createCriterion(new ConsumeItemTrigger.TriggerInstance(Optional.empty(), Optional.empty()));
        }

        public static Criterion<ConsumeItemTrigger.TriggerInstance> usedItem(HolderGetter<Item> itemRegistry, ItemLike item) {
            return usedItem(ItemPredicate.Builder.item().of(itemRegistry, item.asItem()));
        }

        public static Criterion<ConsumeItemTrigger.TriggerInstance> usedItem(ItemPredicate.Builder item) {
            return CriteriaTriggers.CONSUME_ITEM.createCriterion(new ConsumeItemTrigger.TriggerInstance(Optional.empty(), Optional.of(item.build())));
        }

        public boolean matches(ItemStack item) {
            return this.item.isEmpty() || this.item.get().test(item);
        }
    }
}
