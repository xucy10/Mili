package net.minecraft.world.entity.player;

import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import org.jspecify.annotations.Nullable;

public class StackedItemContents {
    // Paper start - Improve exact choice recipe ingredients
    private final StackedContents<io.papermc.paper.inventory.recipe.ItemOrExact> raw = new StackedContents<>();
    private io.papermc.paper.inventory.recipe.@Nullable StackedContentsExtrasMap extrasMap;

    public void initializeExtras(final Recipe<?> recipe, final net.minecraft.world.item.crafting.@Nullable CraftingInput input) {
        if (this.extrasMap == null) {
            this.extrasMap = new io.papermc.paper.inventory.recipe.StackedContentsExtrasMap(this.raw);
        }
        this.extrasMap.initialize(recipe);
        if (input != null) this.extrasMap.accountInput(input);
    }

    public void resetExtras() {
        if (this.extrasMap != null && !this.raw.amounts.isEmpty()) {
            this.extrasMap.resetExtras();
        }
    }
    // Paper end - Improve exact choice recipe ingredients

    public void accountSimpleStack(ItemStack stack) {
        if (this.extrasMap != null && this.extrasMap.accountStack(stack, Math.min(64, stack.getCount()))) return; // Paper - Improve exact choice recipe ingredients; max of 64 due to accountStack method below
        if (Inventory.isUsableForCrafting(stack)) {
            this.accountStack(stack);
        }
    }

    public void accountStack(ItemStack stack) {
        this.accountStack(stack, stack.getMaxStackSize());
    }

    public void accountStack(ItemStack stack, int maxStackSize) {
        if (!stack.isEmpty()) {
            int min = Math.min(maxStackSize, stack.getCount());
            if (this.extrasMap != null && !stack.getComponentsPatch().isEmpty() && this.extrasMap.accountStack(stack, min)) return; // Paper - Improve exact choice recipe ingredients; if an exact ingredient, don't include it
            this.raw.account(new io.papermc.paper.inventory.recipe.ItemOrExact.Item(stack.getItemHolder()), min);
        }
    }

    public boolean canCraft(Recipe<?> recipe, StackedContents.@Nullable Output<io.papermc.paper.inventory.recipe.ItemOrExact> output) { // Paper - Improve exact choice recipe ingredients
        return this.canCraft(recipe, 1, output);
    }

    public boolean canCraft(Recipe<?> recipe, int maxCount, StackedContents.@Nullable Output<io.papermc.paper.inventory.recipe.ItemOrExact> output) { // Paper - Improve exact choice recipe ingredients
        PlacementInfo placementInfo = recipe.placementInfo();
        return !placementInfo.isImpossibleToPlace() && this.canCraft(placementInfo.ingredients(), maxCount, output);
    }

    public boolean canCraft(List<? extends StackedContents.IngredientInfo<io.papermc.paper.inventory.recipe.ItemOrExact>> ingredients, StackedContents.@Nullable Output<io.papermc.paper.inventory.recipe.ItemOrExact> output) { // Paper - Improve exact choice recipe ingredients
        return this.canCraft(ingredients, 1, output);
    }

    private boolean canCraft(
        List<? extends StackedContents.IngredientInfo<io.papermc.paper.inventory.recipe.ItemOrExact>> ingredients, int maxCount, StackedContents.@Nullable Output<io.papermc.paper.inventory.recipe.ItemOrExact> output // Paper - Improve exact choice recipe ingredients
    ) {
        return this.raw.tryPick(ingredients, maxCount, output);
    }

    public int getBiggestCraftableStack(Recipe<?> recipe, StackedContents.@Nullable Output<io.papermc.paper.inventory.recipe.ItemOrExact> output) { // Paper - Improve exact choice recipe ingredients
        return this.getBiggestCraftableStack(recipe, Integer.MAX_VALUE, output);
    }

    public int getBiggestCraftableStack(Recipe<?> recipe, int maxCount, StackedContents.@Nullable Output<io.papermc.paper.inventory.recipe.ItemOrExact> output) { // Paper - Improve exact choice recipe ingredients
        return this.raw.tryPickAll(recipe.placementInfo().ingredients(), maxCount, output);
    }

    public void clear() {
        this.raw.clear();
    }
}
