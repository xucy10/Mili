package net.minecraft.world.item.crafting;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.level.Level;

public class FireworkStarRecipe extends CustomRecipe {
    private static final Map<Item, FireworkExplosion.Shape> SHAPE_BY_ITEM = Map.of(
        Items.FIRE_CHARGE,
        FireworkExplosion.Shape.LARGE_BALL,
        Items.FEATHER,
        FireworkExplosion.Shape.BURST,
        Items.GOLD_NUGGET,
        FireworkExplosion.Shape.STAR,
        Items.SKELETON_SKULL,
        FireworkExplosion.Shape.CREEPER,
        Items.WITHER_SKELETON_SKULL,
        FireworkExplosion.Shape.CREEPER,
        Items.CREEPER_HEAD,
        FireworkExplosion.Shape.CREEPER,
        Items.PLAYER_HEAD,
        FireworkExplosion.Shape.CREEPER,
        Items.DRAGON_HEAD,
        FireworkExplosion.Shape.CREEPER,
        Items.ZOMBIE_HEAD,
        FireworkExplosion.Shape.CREEPER,
        Items.PIGLIN_HEAD,
        FireworkExplosion.Shape.CREEPER
    );
    private static final Ingredient TRAIL_INGREDIENT = Ingredient.of(Items.DIAMOND);
    private static final Ingredient TWINKLE_INGREDIENT = Ingredient.of(Items.GLOWSTONE_DUST);
    private static final Ingredient GUNPOWDER_INGREDIENT = Ingredient.of(Items.GUNPOWDER);

    public FireworkStarRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (input.ingredientCount() < 2) {
            return false;
        } else {
            boolean flag = false;
            boolean flag1 = false;
            boolean flag2 = false;
            boolean flag3 = false;
            boolean flag4 = false;

            for (int i = 0; i < input.size(); i++) {
                ItemStack item = input.getItem(i);
                if (!item.isEmpty()) {
                    if (SHAPE_BY_ITEM.containsKey(item.getItem())) {
                        if (flag2) {
                            return false;
                        }

                        flag2 = true;
                    } else if (TWINKLE_INGREDIENT.test(item)) {
                        if (flag4) {
                            return false;
                        }

                        flag4 = true;
                    } else if (TRAIL_INGREDIENT.test(item)) {
                        if (flag3) {
                            return false;
                        }

                        flag3 = true;
                    } else if (GUNPOWDER_INGREDIENT.test(item)) {
                        if (flag) {
                            return false;
                        }

                        flag = true;
                    } else {
                        if (!(item.getItem() instanceof DyeItem)) {
                            return false;
                        }

                        flag1 = true;
                    }
                }
            }

            return flag && flag1;
        }
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        FireworkExplosion.Shape shape = FireworkExplosion.Shape.SMALL_BALL;
        boolean flag = false;
        boolean flag1 = false;
        IntList list = new IntArrayList();

        for (int i = 0; i < input.size(); i++) {
            ItemStack item = input.getItem(i);
            if (!item.isEmpty()) {
                FireworkExplosion.Shape shape1 = SHAPE_BY_ITEM.get(item.getItem());
                if (shape1 != null) {
                    shape = shape1;
                } else if (TWINKLE_INGREDIENT.test(item)) {
                    flag = true;
                } else if (TRAIL_INGREDIENT.test(item)) {
                    flag1 = true;
                } else if (item.getItem() instanceof DyeItem dyeItem) {
                    list.add(dyeItem.getDyeColor().getFireworkColor());
                }
            }
        }

        ItemStack itemStack = new ItemStack(Items.FIREWORK_STAR);
        itemStack.set(DataComponents.FIREWORK_EXPLOSION, new FireworkExplosion(shape, list, IntList.of(), flag1, flag));
        return itemStack;
    }

    @Override
    public RecipeSerializer<FireworkStarRecipe> getSerializer() {
        return RecipeSerializer.FIREWORK_STAR;
    }
}
