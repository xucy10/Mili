package net.minecraft.world.inventory;

import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class PlayerEnderChestContainer extends SimpleContainer {
    private @Nullable EnderChestBlockEntity activeChest;
    // CraftBukkit start
    private final Player owner;

    @Override
    public org.bukkit.inventory.InventoryHolder getOwner() {
        return this.owner.getBukkitEntity();
    }

    @Override
    public org.bukkit.@Nullable Location getLocation() {
        return this.activeChest != null ? org.bukkit.craftbukkit.util.CraftLocation.toBukkit(this.activeChest.getBlockPos(), this.activeChest.getLevel()) : null;
    }

    public PlayerEnderChestContainer(Player owner) {
        super(27);
        this.owner = owner;
        // CraftBukkit end
    }

    public void setActiveChest(EnderChestBlockEntity enderChestBlockEntity) {
        this.activeChest = enderChestBlockEntity;
    }

    public boolean isActiveChest(EnderChestBlockEntity enderChest) {
        return this.activeChest == enderChest;
    }

    public void fromSlots(ValueInput.TypedInputList<ItemStackWithSlot> input) {
        for (int i = 0; i < this.getContainerSize(); i++) {
            this.setItem(i, ItemStack.EMPTY);
        }

        for (ItemStackWithSlot itemStackWithSlot : input) {
            if (itemStackWithSlot.isValidInContainer(this.getContainerSize())) {
                this.setItem(itemStackWithSlot.slot(), itemStackWithSlot.stack());
            }
        }
    }

    public void storeAsSlots(ValueOutput.TypedOutputList<ItemStackWithSlot> output) {
        for (int i = 0; i < this.getContainerSize(); i++) {
            ItemStack item = this.getItem(i);
            if (!item.isEmpty()) {
                output.add(new ItemStackWithSlot(i, item));
            }
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return (this.activeChest == null || this.activeChest.stillValid(player)) && super.stillValid(player);
    }

    @Override
    public void startOpen(ContainerUser user) {
        if (this.activeChest != null) {
            this.activeChest.startOpen(user);
        }

        super.startOpen(user);
    }

    @Override
    public void stopOpen(ContainerUser user) {
        if (this.activeChest != null) {
            this.activeChest.stopOpen(user);
        }

        super.stopOpen(user);
        this.activeChest = null;
    }
}
