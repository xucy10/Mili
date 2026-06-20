package net.minecraft.advancements.criterion;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.core.HolderGetter;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootContext;
import org.jspecify.annotations.Nullable;

public class KilledByArrowTrigger extends SimpleCriterionTrigger<KilledByArrowTrigger.TriggerInstance> {
    @Override
    public Codec<KilledByArrowTrigger.TriggerInstance> codec() {
        return KilledByArrowTrigger.TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, Collection<Entity> victims, @Nullable ItemStack firedFromWeapon) {
        List<LootContext> list = Lists.newArrayList();
        Set<EntityType<?>> set = Sets.newHashSet();

        for (Entity entity : victims) {
            set.add(entity.getType());
            list.add(EntityPredicate.createContext(player, entity));
        }

        this.trigger(player, triggerInstance -> triggerInstance.matches(list, set.size(), firedFromWeapon));
    }

    public record TriggerInstance(
        @Override Optional<ContextAwarePredicate> player,
        List<ContextAwarePredicate> victims,
        MinMaxBounds.Ints uniqueEntityTypes,
        Optional<ItemPredicate> firedFromWeapon
    ) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<KilledByArrowTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(KilledByArrowTrigger.TriggerInstance::player),
                    EntityPredicate.ADVANCEMENT_CODEC.listOf().optionalFieldOf("victims", List.of()).forGetter(KilledByArrowTrigger.TriggerInstance::victims),
                    MinMaxBounds.Ints.CODEC
                        .optionalFieldOf("unique_entity_types", MinMaxBounds.Ints.ANY)
                        .forGetter(KilledByArrowTrigger.TriggerInstance::uniqueEntityTypes),
                    ItemPredicate.CODEC.optionalFieldOf("fired_from_weapon").forGetter(KilledByArrowTrigger.TriggerInstance::firedFromWeapon)
                )
                .apply(instance, KilledByArrowTrigger.TriggerInstance::new)
        );

        public static Criterion<KilledByArrowTrigger.TriggerInstance> crossbowKilled(HolderGetter<Item> itemRegistry, EntityPredicate.Builder... victims) {
            return CriteriaTriggers.KILLED_BY_ARROW
                .createCriterion(
                    new KilledByArrowTrigger.TriggerInstance(
                        Optional.empty(),
                        EntityPredicate.wrap(victims),
                        MinMaxBounds.Ints.ANY,
                        Optional.of(ItemPredicate.Builder.item().of(itemRegistry, Items.CROSSBOW).build())
                    )
                );
        }

        public static Criterion<KilledByArrowTrigger.TriggerInstance> crossbowKilled(HolderGetter<Item> itemRegistry, MinMaxBounds.Ints uniqueEntityTypes) {
            return CriteriaTriggers.KILLED_BY_ARROW
                .createCriterion(
                    new KilledByArrowTrigger.TriggerInstance(
                        Optional.empty(), List.of(), uniqueEntityTypes, Optional.of(ItemPredicate.Builder.item().of(itemRegistry, Items.CROSSBOW).build())
                    )
                );
        }

        public boolean matches(Collection<LootContext> context, int uniqueEntityTypes, @Nullable ItemStack firedFromWeapon) {
            if (!this.firedFromWeapon.isPresent() || firedFromWeapon != null && this.firedFromWeapon.get().test(firedFromWeapon)) {
                if (!this.victims.isEmpty()) {
                    List<LootContext> list = Lists.newArrayList(context);

                    for (ContextAwarePredicate contextAwarePredicate : this.victims) {
                        boolean flag = false;
                        Iterator<LootContext> iterator = list.iterator();

                        while (iterator.hasNext()) {
                            LootContext lootContext = iterator.next();
                            if (contextAwarePredicate.matches(lootContext)) {
                                iterator.remove();
                                flag = true;
                                break;
                            }
                        }

                        if (!flag) {
                            return false;
                        }
                    }
                }

                return this.uniqueEntityTypes.matches(uniqueEntityTypes);
            } else {
                return false;
            }
        }

        @Override
        public void validate(CriterionValidator validator) {
            SimpleCriterionTrigger.SimpleInstance.super.validate(validator);
            validator.validateEntities(this.victims, "victims");
        }
    }
}
