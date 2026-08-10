package net.minecraft.world.item.crafting;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.item.ItemStack;

public class CraftingInput implements RecipeInput {
    public static final CraftingInput EMPTY = new CraftingInput(0, 0, List.of());
    private final int width;
    private final int height;
    private final List<ItemStack> items;
    private final StackedItemContents stackedContents = new StackedItemContents();
    private final int ingredientCount;

    private CraftingInput(int width, int height, List<ItemStack> items) {
        this.width = width;
        this.height = height;
        this.items = items;
        int i = 0;

        for (ItemStack itemStack : items) {
            if (!itemStack.isEmpty()) {
                i++;
                this.stackedContents.accountStack(itemStack, 1);
            }
        }

        this.ingredientCount = i;
    }

    public static CraftingInput of(int width, int height, List<ItemStack> items) {
        return ofPositioned(width, height, items).input();
    }

    public static CraftingInput.Positioned ofPositioned(int width, int height, List<ItemStack> items) {
        if (width != 0 && height != 0) {
            int i = width - 1;
            int i1 = 0;
            int i2 = height - 1;
            int i3 = 0;

            for (int i4 = 0; i4 < height; i4++) {
                boolean flag = true;

                for (int i5 = 0; i5 < width; i5++) {
                    ItemStack itemStack = items.get(i5 + i4 * width);
                    if (!itemStack.isEmpty()) {
                        i = Math.min(i, i5);
                        i1 = Math.max(i1, i5);
                        flag = false;
                    }
                }

                if (!flag) {
                    i2 = Math.min(i2, i4);
                    i3 = Math.max(i3, i4);
                }
            }

            int i4 = i1 - i + 1;
            int i6 = i3 - i2 + 1;
            if (i4 <= 0 || i6 <= 0) {
                return CraftingInput.Positioned.EMPTY;
            } else if (i4 == width && i6 == height) {
                return new CraftingInput.Positioned(new CraftingInput(width, height, items), i, i2);
            } else {
                List<ItemStack> list = new ArrayList<>(i4 * i6);

                for (int i7 = 0; i7 < i6; i7++) {
                    for (int i8 = 0; i8 < i4; i8++) {
                        int i9 = i8 + i + (i7 + i2) * width;
                        list.add(items.get(i9));
                    }
                }

                return new CraftingInput.Positioned(new CraftingInput(i4, i6, list), i, i2);
            }
        } else {
            return CraftingInput.Positioned.EMPTY;
        }
    }

    @Override
    public ItemStack getItem(int index) {
        return this.items.get(index);
    }

    public ItemStack getItem(int row, int column) {
        return this.items.get(row + column * this.width);
    }

    @Override
    public int size() {
        return this.items.size();
    }

    @Override
    public boolean isEmpty() {
        return this.ingredientCount == 0;
    }

    public StackedItemContents stackedContents() {
        return this.stackedContents;
    }

    public List<ItemStack> items() {
        return this.items;
    }

    public int ingredientCount() {
        return this.ingredientCount;
    }

    public int width() {
        return this.width;
    }

    public int height() {
        return this.height;
    }

    @Override
    public boolean equals(Object other) {
        return other == this
            || other instanceof CraftingInput craftingInput
                && this.width == craftingInput.width
                && this.height == craftingInput.height
                && this.ingredientCount == craftingInput.ingredientCount
                && ItemStack.listMatches(this.items, craftingInput.items);
    }

    @Override
    public int hashCode() {
        int i = ItemStack.hashStackList(this.items);
        i = 31 * i + this.width;
        return 31 * i + this.height;
    }

    public record Positioned(CraftingInput input, int left, int top) {
        public static final CraftingInput.Positioned EMPTY = new CraftingInput.Positioned(CraftingInput.EMPTY, 0, 0);
    }
}
