package net.minecraft.world.item.crafting;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

public class ShieldDecorationRecipe extends CustomRecipe {
    public ShieldDecorationRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (input.ingredientCount() != 2) {
            return false;
        } else {
            boolean flag = false;
            boolean flag1 = false;

            for (int i = 0; i < input.size(); i++) {
                ItemStack item = input.getItem(i);
                if (!item.isEmpty()) {
                    if (item.getItem() instanceof BannerItem) {
                        if (flag1) {
                            return false;
                        }

                        flag1 = true;
                    } else {
                        if (!item.is(Items.SHIELD)) {
                            return false;
                        }

                        if (flag) {
                            return false;
                        }

                        BannerPatternLayers bannerPatternLayers = item.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY);
                        if (!bannerPatternLayers.layers().isEmpty()) {
                            return false;
                        }

                        flag = true;
                    }
                }
            }

            return flag && flag1;
        }
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack itemStack = ItemStack.EMPTY;
        ItemStack itemStack1 = ItemStack.EMPTY;

        for (int i = 0; i < input.size(); i++) {
            ItemStack item = input.getItem(i);
            if (!item.isEmpty()) {
                if (item.getItem() instanceof BannerItem) {
                    itemStack = item;
                } else if (item.is(Items.SHIELD)) {
                    itemStack1 = item.copy();
                }
            }
        }

        if (itemStack1.isEmpty()) {
            return itemStack1;
        } else {
            itemStack1.set(DataComponents.BANNER_PATTERNS, itemStack.get(DataComponents.BANNER_PATTERNS));
            itemStack1.set(DataComponents.BASE_COLOR, ((BannerItem)itemStack.getItem()).getColor());
            return itemStack1;
        }
    }

    @Override
    public RecipeSerializer<ShieldDecorationRecipe> getSerializer() {
        return RecipeSerializer.SHIELD_DECORATION;
    }
}
