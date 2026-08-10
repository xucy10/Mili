package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.core.HolderSet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

public class InventoryChangeTrigger extends SimpleCriterionTrigger<InventoryChangeTrigger.TriggerInstance> {
    @Override
    public Codec<InventoryChangeTrigger.TriggerInstance> codec() {
        return InventoryChangeTrigger.TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, Inventory inventory, ItemStack stack) {
        int i = 0;
        int i1 = 0;
        int i2 = 0;

        for (int i3 = 0; i3 < inventory.getContainerSize(); i3++) {
            ItemStack item = inventory.getItem(i3);
            if (item.isEmpty()) {
                i1++;
            } else {
                i2++;
                if (item.getCount() >= item.getMaxStackSize()) {
                    i++;
                }
            }
        }

        this.trigger(player, inventory, stack, i, i1, i2);
    }

    private void trigger(ServerPlayer player, Inventory inventory, ItemStack stack, int full, int empty, int occupied) {
        this.trigger(player, instance -> instance.matches(inventory, stack, full, empty, occupied));
    }

    public record TriggerInstance(
        @Override Optional<ContextAwarePredicate> player, InventoryChangeTrigger.TriggerInstance.Slots slots, List<ItemPredicate> items
    ) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<InventoryChangeTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(InventoryChangeTrigger.TriggerInstance::player),
                    InventoryChangeTrigger.TriggerInstance.Slots.CODEC
                        .optionalFieldOf("slots", InventoryChangeTrigger.TriggerInstance.Slots.ANY)
                        .forGetter(InventoryChangeTrigger.TriggerInstance::slots),
                    ItemPredicate.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(InventoryChangeTrigger.TriggerInstance::items)
                )
                .apply(instance, InventoryChangeTrigger.TriggerInstance::new)
        );

        public static Criterion<InventoryChangeTrigger.TriggerInstance> hasItems(ItemPredicate.Builder... items) {
            return hasItems(Stream.of(items).map(ItemPredicate.Builder::build).toArray(ItemPredicate[]::new));
        }

        public static Criterion<InventoryChangeTrigger.TriggerInstance> hasItems(ItemPredicate... items) {
            return CriteriaTriggers.INVENTORY_CHANGED
                .createCriterion(new InventoryChangeTrigger.TriggerInstance(Optional.empty(), InventoryChangeTrigger.TriggerInstance.Slots.ANY, List.of(items)));
        }

        public static Criterion<InventoryChangeTrigger.TriggerInstance> hasItems(ItemLike... items) {
            ItemPredicate[] itemPredicates = new ItemPredicate[items.length];

            for (int i = 0; i < items.length; i++) {
                itemPredicates[i] = new ItemPredicate(
                    Optional.of(HolderSet.direct(items[i].asItem().builtInRegistryHolder())), MinMaxBounds.Ints.ANY, DataComponentMatchers.ANY
                );
            }

            return hasItems(itemPredicates);
        }

        public boolean matches(Inventory inventory, ItemStack stack, int full, int empty, int occupied) {
            if (!this.slots.matches(full, empty, occupied)) {
                return false;
            } else if (this.items.isEmpty()) {
                return true;
            } else if (this.items.size() != 1) {
                List<ItemPredicate> list = new ObjectArrayList<>(this.items);
                int containerSize = inventory.getContainerSize();

                for (int i = 0; i < containerSize; i++) {
                    if (list.isEmpty()) {
                        return true;
                    }

                    ItemStack item = inventory.getItem(i);
                    if (!item.isEmpty()) {
                        list.removeIf(item1 -> item1.test(item));
                    }
                }

                return list.isEmpty();
            } else {
                return !stack.isEmpty() && this.items.get(0).test(stack);
            }
        }

        public record Slots(MinMaxBounds.Ints occupied, MinMaxBounds.Ints full, MinMaxBounds.Ints empty) {
            public static final Codec<InventoryChangeTrigger.TriggerInstance.Slots> CODEC = RecordCodecBuilder.create(
                instance -> instance.group(
                        MinMaxBounds.Ints.CODEC
                            .optionalFieldOf("occupied", MinMaxBounds.Ints.ANY)
                            .forGetter(InventoryChangeTrigger.TriggerInstance.Slots::occupied),
                        MinMaxBounds.Ints.CODEC.optionalFieldOf("full", MinMaxBounds.Ints.ANY).forGetter(InventoryChangeTrigger.TriggerInstance.Slots::full),
                        MinMaxBounds.Ints.CODEC.optionalFieldOf("empty", MinMaxBounds.Ints.ANY).forGetter(InventoryChangeTrigger.TriggerInstance.Slots::empty)
                    )
                    .apply(instance, InventoryChangeTrigger.TriggerInstance.Slots::new)
            );
            public static final InventoryChangeTrigger.TriggerInstance.Slots ANY = new InventoryChangeTrigger.TriggerInstance.Slots(
                MinMaxBounds.Ints.ANY, MinMaxBounds.Ints.ANY, MinMaxBounds.Ints.ANY
            );

            public boolean matches(int full, int empty, int occupied) {
                return this.full.matches(full) && this.empty.matches(empty) && this.occupied.matches(occupied);
            }
        }
    }
}
