package net.minecraft.world.item.crafting;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.Level;

public class FireworkRocketRecipe extends CustomRecipe {
    private static final Ingredient PAPER_INGREDIENT = Ingredient.of(Items.PAPER);
    private static final Ingredient GUNPOWDER_INGREDIENT = Ingredient.of(Items.GUNPOWDER);
    private static final Ingredient STAR_INGREDIENT = Ingredient.of(Items.FIREWORK_STAR);

    public FireworkRocketRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (input.ingredientCount() < 2) {
            return false;
        } else {
            boolean flag = false;
            int i = 0;

            for (int i1 = 0; i1 < input.size(); i1++) {
                ItemStack item = input.getItem(i1);
                if (!item.isEmpty()) {
                    if (PAPER_INGREDIENT.test(item)) {
                        if (flag) {
                            return false;
                        }

                        flag = true;
                    } else if (GUNPOWDER_INGREDIENT.test(item)) {
                        if (++i > 3) {
                            return false;
                        }
                    } else if (!STAR_INGREDIENT.test(item)) {
                        return false;
                    }
                }
            }

            return flag && i >= 1;
        }
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        List<FireworkExplosion> list = new ArrayList<>();
        int i = 0;

        for (int i1 = 0; i1 < input.size(); i1++) {
            ItemStack item = input.getItem(i1);
            if (!item.isEmpty()) {
                if (GUNPOWDER_INGREDIENT.test(item)) {
                    i++;
                } else if (STAR_INGREDIENT.test(item)) {
                    FireworkExplosion fireworkExplosion = item.get(DataComponents.FIREWORK_EXPLOSION);
                    if (fireworkExplosion != null) {
                        list.add(fireworkExplosion);
                    }
                }
            }
        }

        ItemStack itemStack = new ItemStack(Items.FIREWORK_ROCKET, 3);
        itemStack.set(DataComponents.FIREWORKS, new Fireworks(i, list));
        return itemStack;
    }

    @Override
    public RecipeSerializer<FireworkRocketRecipe> getSerializer() {
        return RecipeSerializer.FIREWORK_ROCKET;
    }
}
