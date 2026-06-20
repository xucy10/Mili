package net.minecraft.recipebook;

import java.util.Iterator;
import net.minecraft.util.Mth;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;

public interface PlaceRecipeHelper {
    static <T> void placeRecipe(int width, int height, Recipe<?> recipe, Iterable<T> ingredients, PlaceRecipeHelper.Output<T> output) {
        if (recipe instanceof ShapedRecipe shapedRecipe) {
            placeRecipe(width, height, shapedRecipe.getWidth(), shapedRecipe.getHeight(), ingredients, output);
        } else {
            placeRecipe(width, height, width, height, ingredients, output);
        }
    }

    static <T> void placeRecipe(int gridWidth, int gridHeight, int width, int height, Iterable<T> ingredients, PlaceRecipeHelper.Output<T> output) {
        Iterator<T> iterator = ingredients.iterator();
        int i = 0;

        for (int i1 = 0; i1 < gridHeight; i1++) {
            boolean flag = height < gridHeight / 2.0F;
            int floor = Mth.floor(gridHeight / 2.0F - height / 2.0F);
            if (flag && floor > i1) {
                i += gridWidth;
                i1++;
            }

            for (int i2 = 0; i2 < gridWidth; i2++) {
                if (!iterator.hasNext()) {
                    return;
                }

                flag = width < gridWidth / 2.0F;
                floor = Mth.floor(gridWidth / 2.0F - width / 2.0F);
                int i3 = width;
                boolean flag1 = i2 < width;
                if (flag) {
                    i3 = floor + width;
                    flag1 = floor <= i2 && i2 < floor + width;
                }

                if (flag1) {
                    output.addItemToSlot(iterator.next(), i, i2, i1);
                } else if (i3 == i2) {
                    i += gridWidth - i2;
                    break;
                }

                i++;
            }
        }
    }

    @FunctionalInterface
    public interface Output<T> {
        void addItemToSlot(T item, int slot, int x, int y);
    }
}
