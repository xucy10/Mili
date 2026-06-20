package net.minecraft.world;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.SlotProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

public interface Container extends Clearable, SlotProvider, Iterable<ItemStack>, org.leavesmc.leaves.lithium.api.inventory.LithiumCooldownReceivingInventory, org.leavesmc.leaves.lithium.api.inventory.LithiumTransferConditionInventory { // Leaves - Lithium Sleeping Block Entity
    float DEFAULT_DISTANCE_BUFFER = 4.0F;

    int getContainerSize();

    boolean isEmpty();

    ItemStack getItem(int slot);

    ItemStack removeItem(int slot, int amount);

    ItemStack removeItemNoUpdate(int slot);

    void setItem(int slot, ItemStack stack);

    int getMaxStackSize(); // CraftBukkit

    default int getMaxStackSize(ItemStack stack) {
        return Math.min(this.getMaxStackSize(), stack.getMaxStackSize());
    }

    void setChanged();

    boolean stillValid(Player player);

    default void startOpen(ContainerUser user) {
    }

    default void stopOpen(ContainerUser user) {
    }

    default List<ContainerUser> getEntitiesWithContainerOpen() {
        return List.of();
    }

    default boolean canPlaceItem(int slot, ItemStack stack) {
        return true;
    }

    default boolean canTakeItem(Container target, int slot, ItemStack stack) {
        return true;
    }

    default int countItem(Item item) {
        int i = 0;

        for (ItemStack itemStack : this) {
            if (itemStack.getItem().equals(item)) {
                i += itemStack.getCount();
            }
        }

        return i;
    }

    default boolean hasAnyOf(Set<Item> set) {
        return this.hasAnyMatching(item -> !item.isEmpty() && set.contains(item.getItem()));
    }

    default boolean hasAnyMatching(Predicate<ItemStack> predicate) {
        for (ItemStack itemStack : this) {
            if (predicate.test(itemStack)) {
                return true;
            }
        }

        return false;
    }

    static boolean stillValidBlockEntity(BlockEntity blockEntity, Player player) {
        return stillValidBlockEntity(blockEntity, player, 4.0F);
    }

    static boolean stillValidBlockEntity(BlockEntity blockEntity, Player player, float distance) {
        Level level = blockEntity.getLevel();
        BlockPos blockPos = blockEntity.getBlockPos();
        return level != null && level.getBlockEntity(blockPos) == blockEntity && player.isWithinBlockInteractionRange(blockPos, distance);
    }

    @Override
    default @Nullable SlotAccess getSlot(final int slotIndex) {
        return slotIndex >= 0 && slotIndex < this.getContainerSize() ? new SlotAccess() {
            @Override
            public ItemStack get() {
                return Container.this.getItem(slotIndex);
            }

            @Override
            public boolean set(ItemStack carried) {
                Container.this.setItem(slotIndex, carried);
                return true;
            }
        } : null;
    }

    @Override
    default Iterator<ItemStack> iterator() {
        return new Container.ContainerIterator(this);
    }

    public static class ContainerIterator implements Iterator<ItemStack> {
        private final Container container;
        private int index;
        private final int size;

        public ContainerIterator(Container container) {
            this.container = container;
            this.size = container.getContainerSize();
        }

        @Override
        public boolean hasNext() {
            return this.index < this.size;
        }

        @Override
        public ItemStack next() {
            if (!this.hasNext()) {
                throw new NoSuchElementException();
            } else {
                return this.container.getItem(this.index++);
            }
        }
    }

    // CraftBukkit start
    java.util.List<ItemStack> getContents();

    void onOpen(org.bukkit.craftbukkit.entity.CraftHumanEntity player);

    void onClose(org.bukkit.craftbukkit.entity.CraftHumanEntity player);

    java.util.List<org.bukkit.entity.HumanEntity> getViewers();

    @javax.annotation.Nullable org.bukkit.inventory.InventoryHolder getOwner();

    void setMaxStackSize(int size);

    @javax.annotation.Nullable org.bukkit.Location getLocation();

    int MAX_STACK = Item.ABSOLUTE_MAX_STACK_SIZE;
    // CraftBukkit end
}
