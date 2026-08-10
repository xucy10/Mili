package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

public class RecipeUnlockedTrigger extends SimpleCriterionTrigger<RecipeUnlockedTrigger.TriggerInstance> {
    @Override
    public Codec<RecipeUnlockedTrigger.TriggerInstance> codec() {
        return RecipeUnlockedTrigger.TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, RecipeHolder<?> recipe) {
        this.trigger(player, trigger -> trigger.matches(recipe));
    }

    public static Criterion<RecipeUnlockedTrigger.TriggerInstance> unlocked(ResourceKey<Recipe<?>> recipe) {
        return CriteriaTriggers.RECIPE_UNLOCKED.createCriterion(new RecipeUnlockedTrigger.TriggerInstance(Optional.empty(), recipe));
    }

    public record TriggerInstance(@Override Optional<ContextAwarePredicate> player, ResourceKey<Recipe<?>> recipe)
        implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<RecipeUnlockedTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(RecipeUnlockedTrigger.TriggerInstance::player),
                    Recipe.KEY_CODEC.fieldOf("recipe").forGetter(RecipeUnlockedTrigger.TriggerInstance::recipe)
                )
                .apply(instance, RecipeUnlockedTrigger.TriggerInstance::new)
        );

        public boolean matches(RecipeHolder<?> recipe) {
            return this.recipe == recipe.id();
        }
    }
}
