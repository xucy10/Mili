package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class UsingItemTrigger extends SimpleCriterionTrigger<UsingItemTrigger.TriggerInstance> {
    @Override
    public Codec<UsingItemTrigger.TriggerInstance> codec() {
        return UsingItemTrigger.TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, ItemStack item) {
        this.trigger(player, instance -> instance.matches(item));
    }

    public record TriggerInstance(@Override Optional<ContextAwarePredicate> player, Optional<ItemPredicate> item)
        implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<UsingItemTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(UsingItemTrigger.TriggerInstance::player),
                    ItemPredicate.CODEC.optionalFieldOf("item").forGetter(UsingItemTrigger.TriggerInstance::item)
                )
                .apply(instance, UsingItemTrigger.TriggerInstance::new)
        );

        public static Criterion<UsingItemTrigger.TriggerInstance> lookingAt(EntityPredicate.Builder player, ItemPredicate.Builder item) {
            return CriteriaTriggers.USING_ITEM
                .createCriterion(new UsingItemTrigger.TriggerInstance(Optional.of(EntityPredicate.wrap(player)), Optional.of(item.build())));
        }

        public boolean matches(ItemStack item) {
            return !this.item.isPresent() || this.item.get().test(item);
        }
    }
}
