package net.minecraft.world.item.crafting;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.ImmutableMultimap.Builder;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public class RecipeMap {
    public static final RecipeMap EMPTY = new RecipeMap(ImmutableMultimap.of(), Map.of());
    public final Multimap<RecipeType<?>, RecipeHolder<?>> byType;
    public final Map<ResourceKey<Recipe<?>>, RecipeHolder<?>> byKey;

    private RecipeMap(Multimap<RecipeType<?>, RecipeHolder<?>> byType, Map<ResourceKey<Recipe<?>>, RecipeHolder<?>> byKey) {
        this.byType = byType;
        this.byKey = byKey;
    }

    public static RecipeMap create(Iterable<RecipeHolder<?>> recipes) {
        Builder<RecipeType<?>, RecipeHolder<?>> builder = ImmutableMultimap.builder();
        com.google.common.collect.ImmutableMap.Builder<ResourceKey<Recipe<?>>, RecipeHolder<?>> builder1 = ImmutableMap.builder();

        for (RecipeHolder<?> recipeHolder : recipes) {
            builder.put(recipeHolder.value().getType(), recipeHolder);
            builder1.put(recipeHolder.id(), recipeHolder);
        }

        // CraftBukkit start - mutable
        return new RecipeMap(com.google.common.collect.LinkedHashMultimap.create(builder.build()), com.google.common.collect.Maps.newLinkedHashMap(builder1.build()));
    }

    public void addRecipe(RecipeHolder<?> holder) {
        Collection<RecipeHolder<?>> recipes = this.byType.get(holder.value().getType());

        if (this.byKey.containsKey(holder.id())) {
            throw new IllegalStateException("Duplicate recipe ignored with ID " + holder.id());
        } else {
            recipes.add(holder);
            this.byKey.put(holder.id(), holder);
        }
    }
    // CraftBukkit end

    // Paper start - replace removeRecipe implementation
    public <T extends RecipeInput> boolean removeRecipe(ResourceKey<Recipe<T>> mcKey) {
        //noinspection unchecked
        final RecipeHolder<Recipe<T>> remove = (RecipeHolder<Recipe<T>>) this.byKey.remove(mcKey);
        if (remove == null) {
            return false;
        }
        final Collection<? extends RecipeHolder<? extends Recipe<T>>> recipes = this.byType(remove.value().getType());
        return recipes.remove(remove);
        // Paper end - why are you using a loop???
    }
    // Paper end - replace removeRecipe implementation

    public <I extends RecipeInput, T extends Recipe<I>> Collection<RecipeHolder<T>> byType(RecipeType<T> type) {
        return (Collection)this.byType.get(type);
    }

    public Collection<RecipeHolder<?>> values() {
        return this.byKey.values();
    }

    public @Nullable RecipeHolder<?> byKey(ResourceKey<Recipe<?>> key) {
        return this.byKey.get(key);
    }

    public <I extends RecipeInput, T extends Recipe<I>> Stream<RecipeHolder<T>> getRecipesFor(RecipeType<T> type, I input, Level level) {
        return input.isEmpty() ? Stream.empty() : this.byType(type).stream().filter(recipeHolder -> recipeHolder.value().matches(input, level));
    }
}
