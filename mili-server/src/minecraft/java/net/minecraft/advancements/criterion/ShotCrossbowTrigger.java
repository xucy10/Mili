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

public class ShotCrossbowTrigger extends SimpleCriterionTrigger<ShotCrossbowTrigger.TriggerInstance> {
    @Override
    public Codec<ShotCrossbowTrigger.TriggerInstance> codec() {
        return ShotCrossbowTrigger.TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer shooter, ItemStack stack) {
        this.trigger(shooter, instance -> instance.matches(stack));
    }

    public record TriggerInstance(@Override Optional<ContextAwarePredicate> player, Optional<ItemPredicate> item)
        implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<ShotCrossbowTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(ShotCrossbowTrigger.TriggerInstance::player),
                    ItemPredicate.CODEC.optionalFieldOf("item").forGetter(ShotCrossbowTrigger.TriggerInstance::item)
                )
                .apply(instance, ShotCrossbowTrigger.TriggerInstance::new)
        );

        public static Criterion<ShotCrossbowTrigger.TriggerInstance> shotCrossbow(Optional<ItemPredicate> item) {
            return CriteriaTriggers.SHOT_CROSSBOW.createCriterion(new ShotCrossbowTrigger.TriggerInstance(Optional.empty(), item));
        }

        public static Criterion<ShotCrossbowTrigger.TriggerInstance> shotCrossbow(HolderGetter<Item> itemRegistry, ItemLike item) {
            return CriteriaTriggers.SHOT_CROSSBOW
                .createCriterion(
                    new ShotCrossbowTrigger.TriggerInstance(Optional.empty(), Optional.of(ItemPredicate.Builder.item().of(itemRegistry, item).build()))
                );
        }

        public boolean matches(ItemStack item) {
            return this.item.isEmpty() || this.item.get().test(item);
        }
    }
}
