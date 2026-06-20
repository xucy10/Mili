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

public class UsedTotemTrigger extends SimpleCriterionTrigger<UsedTotemTrigger.TriggerInstance> {
    @Override
    public Codec<UsedTotemTrigger.TriggerInstance> codec() {
        return UsedTotemTrigger.TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, ItemStack item) {
        this.trigger(player, instance -> instance.matches(item));
    }

    public record TriggerInstance(@Override Optional<ContextAwarePredicate> player, Optional<ItemPredicate> item)
        implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<UsedTotemTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(UsedTotemTrigger.TriggerInstance::player),
                    ItemPredicate.CODEC.optionalFieldOf("item").forGetter(UsedTotemTrigger.TriggerInstance::item)
                )
                .apply(instance, UsedTotemTrigger.TriggerInstance::new)
        );

        public static Criterion<UsedTotemTrigger.TriggerInstance> usedTotem(ItemPredicate item) {
            return CriteriaTriggers.USED_TOTEM.createCriterion(new UsedTotemTrigger.TriggerInstance(Optional.empty(), Optional.of(item)));
        }

        public static Criterion<UsedTotemTrigger.TriggerInstance> usedTotem(HolderGetter<Item> itemRegistry, ItemLike item) {
            return CriteriaTriggers.USED_TOTEM
                .createCriterion(
                    new UsedTotemTrigger.TriggerInstance(Optional.empty(), Optional.of(ItemPredicate.Builder.item().of(itemRegistry, item).build()))
                );
        }

        public boolean matches(ItemStack item) {
            return this.item.isEmpty() || this.item.get().test(item);
        }
    }
}
