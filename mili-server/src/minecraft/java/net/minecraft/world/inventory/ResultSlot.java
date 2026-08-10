package net.minecraft.world.inventory;

import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

public class ResultSlot extends Slot {
    private final CraftingContainer craftSlots;
    private final Player player;
    private int removeCount;

    public ResultSlot(Player player, CraftingContainer craftSlots, Container container, int slot, int x, int y) {
        super(container, slot, x, y);
        this.player = player;
        this.craftSlots = craftSlots;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return false;
    }

    @Override
    public ItemStack remove(int amount) {
        if (this.hasItem()) {
            this.removeCount = this.removeCount + Math.min(amount, this.getItem().getCount());
        }

        return super.remove(amount);
    }

    @Override
    protected void onQuickCraft(ItemStack stack, int amount) {
        this.removeCount += amount;
        this.checkTakeAchievements(stack);
        // Paper start - add ItemCraftedEvent
        if (!stack.isEmpty()) {
            new io.papermc.paper.event.inventory.ItemCraftedEvent((org.bukkit.entity.Player) this.player.getBukkitEntity(), org.bukkit.craftbukkit.inventory.CraftItemStack.asBukkitCopy(stack)).callEvent();
        }
        // Paper end - add ItemCraftedEvent
    }

    @Override
    protected void onSwapCraft(int numItemsCrafted) {
        this.removeCount += numItemsCrafted;
    }

    @Override
    protected void checkTakeAchievements(ItemStack stack) {
        if (this.removeCount > 0) {
            stack.onCraftedBy(this.player, this.removeCount);
        }

        if (this.container instanceof RecipeCraftingHolder recipeCraftingHolder) {
            recipeCraftingHolder.awardUsedRecipes(this.player, this.craftSlots.getItems());
        }

        this.removeCount = 0;
    }

    private static NonNullList<ItemStack> copyAllInputItems(CraftingInput input) {
        NonNullList<ItemStack> list = NonNullList.withSize(input.size(), ItemStack.EMPTY);

        for (int i = 0; i < list.size(); i++) {
            list.set(i, input.getItem(i));
        }

        return list;
    }

    private NonNullList<ItemStack> getRemainingItems(CraftingInput input, Level level) {
        return level instanceof ServerLevel serverLevel
            ? serverLevel.recipeAccess()
                .getRecipeFor(RecipeType.CRAFTING, input, serverLevel, this.craftSlots.getCurrentRecipe()) // Paper - Perf: Improve mass crafting; check last recipe used first
                .map(recipe -> recipe.value().getRemainingItems(input))
                .orElseGet(() -> copyAllInputItems(input))
            : CraftingRecipe.defaultCraftingReminder(input);
    }

    @Override
    public void onTake(Player player, ItemStack stack) {
        // Paper start - add ItemCraftedEvent
        if (!stack.isEmpty()) {
            new io.papermc.paper.event.inventory.ItemCraftedEvent((org.bukkit.entity.Player) player.getBukkitEntity(), org.bukkit.craftbukkit.inventory.CraftItemStack.asBukkitCopy(stack)).callEvent();
        }
        // Paper end - add ItemCraftedEvent
        this.checkTakeAchievements(stack);
        CraftingInput.Positioned positionedCraftInput = this.craftSlots.asPositionedCraftInput();
        CraftingInput craftingInput = positionedCraftInput.input();
        int left = positionedCraftInput.left();
        int top = positionedCraftInput.top();
        NonNullList<ItemStack> remainingItems = this.getRemainingItems(craftingInput, player.level());

        for (int i = 0; i < craftingInput.height(); i++) {
            for (int i1 = 0; i1 < craftingInput.width(); i1++) {
                int i2 = i1 + left + (i + top) * this.craftSlots.getWidth();
                ItemStack item = this.craftSlots.getItem(i2);
                ItemStack itemStack = remainingItems.get(i1 + i * craftingInput.width());
                if (!item.isEmpty()) {
                    this.craftSlots.removeItem(i2, 1);
                    item = this.craftSlots.getItem(i2);
                }

                if (!itemStack.isEmpty()) {
                    if (item.isEmpty()) {
                        this.craftSlots.setItem(i2, itemStack);
                    } else if (ItemStack.isSameItemSameComponents(item, itemStack)) {
                        itemStack.grow(item.getCount());
                        this.craftSlots.setItem(i2, itemStack);
                    } else if (!this.player.getInventory().add(itemStack)) {
                        this.player.drop(itemStack, false);
                    }
                }
            }
        }
    }

    @Override
    public boolean isFake() {
        return true;
    }
}
