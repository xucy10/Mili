package net.minecraft.world.item.crafting;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PlacementInfo {
    public static final int EMPTY_SLOT = -1;
    public static final PlacementInfo NOT_PLACEABLE = new PlacementInfo(List.of(), IntList.of());
    private final List<Ingredient> ingredients;
    private final IntList slotsToIngredientIndex;

    private PlacementInfo(List<Ingredient> ingredients, IntList slotsToIngredientIndex) {
        this.ingredients = ingredients;
        this.slotsToIngredientIndex = slotsToIngredientIndex;
    }

    public static PlacementInfo create(Ingredient ingredient) {
        return ingredient.isEmpty() ? NOT_PLACEABLE : new PlacementInfo(List.of(ingredient), IntList.of(0));
    }

    public static PlacementInfo createFromOptionals(List<Optional<Ingredient>> optionals) {
        int size = optionals.size();
        List<Ingredient> list = new ArrayList<>(size);
        IntList list1 = new IntArrayList(size);
        int i = 0;

        for (Optional<Ingredient> optional : optionals) {
            if (optional.isPresent()) {
                Ingredient ingredient = optional.get();
                if (ingredient.isEmpty()) {
                    return NOT_PLACEABLE;
                }

                list.add(ingredient);
                list1.add(i++);
            } else {
                list1.add(-1);
            }
        }

        return new PlacementInfo(list, list1);
    }

    public static PlacementInfo create(List<Ingredient> ingredients) {
        int size = ingredients.size();
        IntList list = new IntArrayList(size);

        for (int i = 0; i < size; i++) {
            Ingredient ingredient = ingredients.get(i);
            if (ingredient.isEmpty()) {
                return NOT_PLACEABLE;
            }

            list.add(i);
        }

        return new PlacementInfo(ingredients, list);
    }

    public IntList slotsToIngredientIndex() {
        return this.slotsToIngredientIndex;
    }

    public List<Ingredient> ingredients() {
        return this.ingredients;
    }

    public boolean isImpossibleToPlace() {
        return this.slotsToIngredientIndex.isEmpty();
    }
}
