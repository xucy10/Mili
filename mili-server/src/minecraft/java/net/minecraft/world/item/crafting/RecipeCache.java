package net.minecraft.world.item.crafting;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Optional;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public class RecipeCache {
    private final RecipeCache.@Nullable Entry[] entries;
    private WeakReference<@Nullable RecipeManager> cachedRecipeManager = new WeakReference<>(null);

    public RecipeCache(int size) {
        this.entries = new RecipeCache.Entry[size];
    }

    public Optional<RecipeHolder<CraftingRecipe>> get(ServerLevel level, CraftingInput craftingInput) {
        if (craftingInput.isEmpty()) {
            return Optional.empty();
        } else {
            this.validateRecipeManager(level);

            for (int i = 0; i < this.entries.length; i++) {
                RecipeCache.Entry entry = this.entries[i];
                if (entry != null && entry.matches(craftingInput)) {
                    this.moveEntryToFront(i);
                    return Optional.ofNullable(entry.value());
                }
            }

            return this.compute(craftingInput, level);
        }
    }

    private void validateRecipeManager(ServerLevel level) {
        RecipeManager recipeManager = level.recipeAccess();
        if (recipeManager != this.cachedRecipeManager.get()) {
            this.cachedRecipeManager = new WeakReference<>(recipeManager);
            Arrays.fill(this.entries, null);
        }
    }

    private Optional<RecipeHolder<CraftingRecipe>> compute(CraftingInput craftingInput, ServerLevel level) {
        Optional<RecipeHolder<CraftingRecipe>> recipeFor = level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, craftingInput, level);
        this.insert(craftingInput, recipeFor.orElse(null));
        return recipeFor;
    }

    private void moveEntryToFront(int index) {
        if (index > 0) {
            RecipeCache.Entry entry = this.entries[index];
            System.arraycopy(this.entries, 0, this.entries, 1, index);
            this.entries[0] = entry;
        }
    }

    private void insert(CraftingInput input, @Nullable RecipeHolder<CraftingRecipe> recipe) {
        NonNullList<ItemStack> list = NonNullList.withSize(input.size(), ItemStack.EMPTY);

        for (int i = 0; i < input.size(); i++) {
            list.set(i, input.getItem(i).copyWithCount(1));
        }

        System.arraycopy(this.entries, 0, this.entries, 1, this.entries.length - 1);
        this.entries[0] = new RecipeCache.Entry(list, input.width(), input.height(), recipe);
    }

    record Entry(NonNullList<ItemStack> key, int width, int height, @Nullable RecipeHolder<CraftingRecipe> value) {
        public boolean matches(CraftingInput input) {
            if (this.width == input.width() && this.height == input.height()) {
                for (int i = 0; i < this.key.size(); i++) {
                    if (!ItemStack.isSameItemSameComponents(this.key.get(i), input.getItem(i))) {
                        return false;
                    }
                }

                return true;
            } else {
                return false;
            }
        }
    }
}
