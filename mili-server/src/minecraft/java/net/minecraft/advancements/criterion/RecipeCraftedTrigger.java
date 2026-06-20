package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;

public class RecipeCraftedTrigger extends SimpleCriterionTrigger<RecipeCraftedTrigger.TriggerInstance> {
    @Override
    public Codec<RecipeCraftedTrigger.TriggerInstance> codec() {
        return RecipeCraftedTrigger.TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, ResourceKey<Recipe<?>> recipeId, List<ItemStack> ingredients) {
        this.trigger(player, triggerInstance -> triggerInstance.matches(recipeId, ingredients));
    }

    public record TriggerInstance(@Override Optional<ContextAwarePredicate> player, ResourceKey<Recipe<?>> recipeId, List<ItemPredicate> ingredients)
        implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<RecipeCraftedTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(RecipeCraftedTrigger.TriggerInstance::player),
                    Recipe.KEY_CODEC.fieldOf("recipe_id").forGetter(RecipeCraftedTrigger.TriggerInstance::recipeId),
                    ItemPredicate.CODEC.listOf().optionalFieldOf("ingredients", List.of()).forGetter(RecipeCraftedTrigger.TriggerInstance::ingredients)
                )
                .apply(instance, RecipeCraftedTrigger.TriggerInstance::new)
        );

        public static Criterion<RecipeCraftedTrigger.TriggerInstance> craftedItem(ResourceKey<Recipe<?>> recipeId, List<ItemPredicate.Builder> ingredients) {
            return CriteriaTriggers.RECIPE_CRAFTED
                .createCriterion(
                    new RecipeCraftedTrigger.TriggerInstance(Optional.empty(), recipeId, ingredients.stream().map(ItemPredicate.Builder::build).toList())
                );
        }

        public static Criterion<RecipeCraftedTrigger.TriggerInstance> craftedItem(ResourceKey<Recipe<?>> recipeId) {
            return CriteriaTriggers.RECIPE_CRAFTED.createCriterion(new RecipeCraftedTrigger.TriggerInstance(Optional.empty(), recipeId, List.of()));
        }

        public static Criterion<RecipeCraftedTrigger.TriggerInstance> crafterCraftedItem(ResourceKey<Recipe<?>> recipeId) {
            return CriteriaTriggers.CRAFTER_RECIPE_CRAFTED.createCriterion(new RecipeCraftedTrigger.TriggerInstance(Optional.empty(), recipeId, List.of()));
        }

        boolean matches(ResourceKey<Recipe<?>> recipeId, List<ItemStack> ingredients) {
            if (recipeId != this.recipeId) {
                return false;
            } else {
                List<ItemStack> list = new ArrayList<>(ingredients);

                for (ItemPredicate itemPredicate : this.ingredients) {
                    boolean flag = false;
                    Iterator<ItemStack> iterator = list.iterator();

                    while (iterator.hasNext()) {
                        if (itemPredicate.test(iterator.next())) {
                            iterator.remove();
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
    }
}
