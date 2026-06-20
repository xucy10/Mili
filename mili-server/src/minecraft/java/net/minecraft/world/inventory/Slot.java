package net.minecraft.world.inventory;

import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public class Slot {
    public final int slot;
    public final Container container;
    public int index;
    public final int x;
    public final int y;

    public Slot(Container container, int slot, int x, int y) {
        this.container = container;
        this.slot = slot;
        this.x = x;
        this.y = y;
    }

    public void onQuickCraft(ItemStack oldStack, ItemStack newStack) {
        int i = newStack.getCount() - oldStack.getCount();
        if (i > 0) {
            this.onQuickCraft(newStack, i);
        }
    }

    protected void onQuickCraft(ItemStack stack, int amount) {
    }

    protected void onSwapCraft(int numItemsCrafted) {
    }

    protected void checkTakeAchievements(ItemStack stack) {
    }

    public void onTake(Player player, ItemStack stack) {
        this.setChanged();
    }

    public boolean mayPlace(ItemStack stack) {
        return true;
    }

    public ItemStack getItem() {
        return this.container.getItem(this.slot);
    }

    public boolean hasItem() {
        return !this.getItem().isEmpty();
    }

    public void setByPlayer(ItemStack stack) {
        this.setByPlayer(stack, this.getItem());
    }

    public void setByPlayer(ItemStack newStack, ItemStack oldStack) {
        this.set(newStack);
    }

    public void set(ItemStack stack) {
        this.container.setItem(this.slot, stack);
        this.setChanged();
    }

    public void setChanged() {
        this.container.setChanged();
    }

    public int getMaxStackSize() {
        return this.container.getMaxStackSize();
    }

    public int getMaxStackSize(ItemStack stack) {
        return Math.min(this.getMaxStackSize(), stack.getMaxStackSize());
    }

    public @Nullable Identifier getNoItemIcon() {
        return null;
    }

    public ItemStack remove(int amount) {
        return this.container.removeItem(this.slot, amount);
    }

    public boolean mayPickup(Player player) {
        return true;
    }

    public boolean isActive() {
        return true;
    }

    public Optional<ItemStack> tryRemove(int count, int decrement, Player player) {
        if (!this.mayPickup(player)) {
            return Optional.empty();
        } else if (!this.allowModification(player) && decrement < this.getItem().getCount()) {
            return Optional.empty();
        } else {
            count = Math.min(count, decrement);
            ItemStack itemStack = this.remove(count);
            if (itemStack.isEmpty()) {
                return Optional.empty();
            } else {
                if (this.getItem().isEmpty()) {
                    this.setByPlayer(ItemStack.EMPTY, itemStack);
                }

                return Optional.of(itemStack);
            }
        }
    }

    public ItemStack safeTake(int count, int decrement, Player player) {
        Optional<ItemStack> optional = this.tryRemove(count, decrement, player);
        optional.ifPresent(stack -> this.onTake(player, stack));
        return optional.orElse(ItemStack.EMPTY);
    }

    public ItemStack safeInsert(ItemStack stack) {
        return this.safeInsert(stack, stack.getCount());
    }

    public ItemStack safeInsert(ItemStack stack, int increment) {
        if (!stack.isEmpty() && this.mayPlace(stack)) {
            ItemStack item = this.getItem();
            int min = Math.min(Math.min(increment, stack.getCount()), this.getMaxStackSize(stack) - item.getCount());
            if (min <= 0) {
                return stack;
            } else {
                if (item.isEmpty()) {
                    this.setByPlayer(stack.split(min));
                } else if (ItemStack.isSameItemSameComponents(item, stack)) {
                    stack.shrink(min);
                    item.grow(min);
                    this.setByPlayer(item);
                }

                return stack;
            }
        } else {
            return stack;
        }
    }

    public boolean allowModification(Player player) {
        return this.mayPickup(player) && this.mayPlace(this.getItem());
    }

    public int getContainerSlot() {
        return this.slot;
    }

    public boolean isHighlightable() {
        return true;
    }

    public boolean isFake() {
        return false;
    }
}
