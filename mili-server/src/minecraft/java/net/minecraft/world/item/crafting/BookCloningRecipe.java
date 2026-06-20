package net.minecraft.world.item.crafting;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;

public class BookCloningRecipe extends CustomRecipe {
    public BookCloningRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (input.ingredientCount() < 2) {
            return false;
        } else {
            boolean flag = false;
            boolean flag1 = false;

            for (int i = 0; i < input.size(); i++) {
                ItemStack item = input.getItem(i);
                if (!item.isEmpty()) {
                    if (item.has(DataComponents.WRITTEN_BOOK_CONTENT)) {
                        if (flag1) {
                            return false;
                        }

                        flag1 = true;
                    } else {
                        if (!item.is(ItemTags.BOOK_CLONING_TARGET)) {
                            return false;
                        }

                        flag = true;
                    }
                }
            }

            return flag1 && flag;
        }
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        int i = 0;
        ItemStack itemStack = ItemStack.EMPTY;

        for (int i1 = 0; i1 < input.size(); i1++) {
            ItemStack item = input.getItem(i1);
            if (!item.isEmpty()) {
                if (item.has(DataComponents.WRITTEN_BOOK_CONTENT)) {
                    if (!itemStack.isEmpty()) {
                        return ItemStack.EMPTY;
                    }

                    itemStack = item;
                } else {
                    if (!item.is(ItemTags.BOOK_CLONING_TARGET)) {
                        return ItemStack.EMPTY;
                    }

                    i++;
                }
            }
        }

        WrittenBookContent writtenBookContent = itemStack.get(DataComponents.WRITTEN_BOOK_CONTENT);
        if (!itemStack.isEmpty() && i >= 1 && writtenBookContent != null) {
            WrittenBookContent writtenBookContent1 = writtenBookContent.tryCraftCopy();
            if (writtenBookContent1 == null) {
                return ItemStack.EMPTY;
            } else {
                ItemStack itemStack1 = itemStack.copyWithCount(i);
                itemStack1.set(DataComponents.WRITTEN_BOOK_CONTENT, writtenBookContent1);
                return itemStack1;
            }
        } else {
            return ItemStack.EMPTY;
        }
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> list = NonNullList.withSize(input.size(), ItemStack.EMPTY);

        for (int i = 0; i < list.size(); i++) {
            ItemStack item = input.getItem(i);
            ItemStack craftingRemainder = item.getItem().getCraftingRemainder();
            if (!craftingRemainder.isEmpty()) {
                list.set(i, craftingRemainder);
            } else if (item.has(DataComponents.WRITTEN_BOOK_CONTENT)) {
                list.set(i, item.copyWithCount(1));
                break;
            }
        }

        return list;
    }

    @Override
    public RecipeSerializer<BookCloningRecipe> getSerializer() {
        return RecipeSerializer.BOOK_CLONING;
    }
}
