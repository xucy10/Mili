package net.minecraft.world.item.crafting;

import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.MapPostProcessing;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

public class MapExtendingRecipe extends ShapedRecipe {
    public MapExtendingRecipe(CraftingBookCategory category) {
        super(
            "",
            category,
            ShapedRecipePattern.of(Map.of('#', Ingredient.of(Items.PAPER), 'x', Ingredient.of(Items.FILLED_MAP)), "###", "#x#", "###"),
            new ItemStack(Items.MAP)
        );
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (!super.matches(input, level)) {
            return false;
        } else {
            ItemStack itemStack = findFilledMap(input);
            if (itemStack.isEmpty()) {
                return false;
            } else {
                MapItemSavedData savedData = MapItem.getSavedData(itemStack, level);
                return savedData != null && !savedData.isExplorationMap() && savedData.scale < 4;
            }
        }
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack itemStack = findFilledMap(input).copyWithCount(1);
        itemStack.set(DataComponents.MAP_POST_PROCESSING, MapPostProcessing.SCALE);
        return itemStack;
    }

    private static ItemStack findFilledMap(CraftingInput input) {
        for (int i = 0; i < input.size(); i++) {
            ItemStack item = input.getItem(i);
            if (item.has(DataComponents.MAP_ID)) {
                return item;
            }
        }

        return ItemStack.EMPTY;
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public RecipeSerializer<MapExtendingRecipe> getSerializer() {
        return RecipeSerializer.MAP_EXTENDING;
    }
}
