package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Collection;
import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

public class FishingRodHookedTrigger extends SimpleCriterionTrigger<FishingRodHookedTrigger.TriggerInstance> {
    @Override
    public Codec<FishingRodHookedTrigger.TriggerInstance> codec() {
        return FishingRodHookedTrigger.TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, ItemStack rod, FishingHook entity, Collection<ItemStack> stacks) {
        LootContext lootContext = EntityPredicate.createContext(player, (Entity)(entity.getHookedIn() != null ? entity.getHookedIn() : entity));
        this.trigger(player, instance -> instance.matches(rod, lootContext, stacks));
    }

    public record TriggerInstance(
        @Override Optional<ContextAwarePredicate> player, Optional<ItemPredicate> rod, Optional<ContextAwarePredicate> entity, Optional<ItemPredicate> item
    ) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<FishingRodHookedTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(FishingRodHookedTrigger.TriggerInstance::player),
                    ItemPredicate.CODEC.optionalFieldOf("rod").forGetter(FishingRodHookedTrigger.TriggerInstance::rod),
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("entity").forGetter(FishingRodHookedTrigger.TriggerInstance::entity),
                    ItemPredicate.CODEC.optionalFieldOf("item").forGetter(FishingRodHookedTrigger.TriggerInstance::item)
                )
                .apply(instance, FishingRodHookedTrigger.TriggerInstance::new)
        );

        public static Criterion<FishingRodHookedTrigger.TriggerInstance> fishedItem(
            Optional<ItemPredicate> rod, Optional<EntityPredicate> entity, Optional<ItemPredicate> item
        ) {
            return CriteriaTriggers.FISHING_ROD_HOOKED
                .createCriterion(new FishingRodHookedTrigger.TriggerInstance(Optional.empty(), rod, EntityPredicate.wrap(entity), item));
        }

        public boolean matches(ItemStack rod, LootContext context, Collection<ItemStack> stacks) {
            if (this.rod.isPresent() && !this.rod.get().test(rod)) {
                return false;
            } else if (this.entity.isPresent() && !this.entity.get().matches(context)) {
                return false;
            } else {
                if (this.item.isPresent()) {
                    boolean flag = false;
                    Entity entity = context.getOptionalParameter(LootContextParams.THIS_ENTITY);
                    if (entity instanceof ItemEntity itemEntity && this.item.get().test(itemEntity.getItem())) {
                        flag = true;
                    }

                    for (ItemStack itemStack : stacks) {
                        if (this.item.get().test(itemStack)) {
                            flag = true;
                            break;
                        }
                    }

                    if (!flag) {
                        return false;
                    }
                }

                return true;
            }
        }

        @Override
        public void validate(CriterionValidator validator) {
            SimpleCriterionTrigger.SimpleInstance.super.validate(validator);
            validator.validateEntity(this.entity, "entity");
        }
    }
}
