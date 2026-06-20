package net.minecraft.world.level.block.entity;

import com.mojang.logging.LogUtils;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.slf4j.Logger;

public class ChiseledBookShelfBlockEntity extends BlockEntity implements ListBackedContainer, org.leavesmc.leaves.lithium.api.inventory.LithiumTransferConditionInventory { // Leaves - Lithium Sleeping Block Entity
    public static final int MAX_BOOKS_IN_STORAGE = 6;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int DEFAULT_LAST_INTERACTED_SLOT = -1;
    private final NonNullList<ItemStack> items = NonNullList.withSize(6, ItemStack.EMPTY);
    public int lastInteractedSlot = -1;

    // CraftBukkit start - add fields and methods
    public java.util.List<org.bukkit.entity.HumanEntity> transaction = new java.util.ArrayList<>();
    private int maxStack = 1;

    @Override
    public java.util.List<net.minecraft.world.item.ItemStack> getContents() {
        return this.items;
    }

    @Override
    public void onOpen(org.bukkit.craftbukkit.entity.CraftHumanEntity player) {
        this.transaction.add(player);
    }

    @Override
    public void onClose(org.bukkit.craftbukkit.entity.CraftHumanEntity player) {
        this.transaction.remove(player);
    }

    @Override
    public java.util.List<org.bukkit.entity.HumanEntity> getViewers() {
        return this.transaction;
    }

    @Override
    public void setMaxStackSize(int size) {
        this.maxStack = size;
    }

    @Override
    public @javax.annotation.Nullable org.bukkit.Location getLocation() {
        if (this.level == null) return null;
        return org.bukkit.craftbukkit.util.CraftLocation.toBukkit(this.worldPosition, this.level);
    }
    // CraftBukkit end

    public ChiseledBookShelfBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityType.CHISELED_BOOKSHELF, pos, blockState);
    }

    private void updateState(int slot) {
        if (slot >= 0 && slot < 6) {
            this.lastInteractedSlot = slot;
            BlockState blockState = this.getBlockState();

            for (int i = 0; i < ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES.size(); i++) {
                boolean flag = !this.getItem(i).isEmpty();
                BooleanProperty booleanProperty = ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES.get(i);
                blockState = blockState.setValue(booleanProperty, flag);
            }

            Objects.requireNonNull(this.level).setBlock(this.worldPosition, blockState, Block.UPDATE_ALL);
            this.level.gameEvent(GameEvent.BLOCK_CHANGE, this.worldPosition, GameEvent.Context.of(blockState));
        } else {
            LOGGER.error("Expected slot 0-5, got {}", slot);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.items.clear();
        ContainerHelper.loadAllItems(input, this.items);
        this.lastInteractedSlot = input.getIntOr("last_interacted_slot", -1);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.items, true);
        output.putInt("last_interacted_slot", this.lastInteractedSlot);
    }

    @Override
    public int getMaxStackSize() {
        return this.maxStack; // CraftBukkit
    }

    @Override
    public boolean acceptsItemType(ItemStack stack) {
        return stack.is(ItemTags.BOOKSHELF_BOOKS);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack itemStack = Objects.requireNonNullElse(this.getItems().get(slot), ItemStack.EMPTY);
        this.getItems().set(slot, ItemStack.EMPTY);
        if (!itemStack.isEmpty()) {
            if (this.level != null) this.updateState(slot); // CraftBukkit - SPIGOT-7381: check for null world
        }

        return itemStack;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (this.acceptsItemType(stack)) {
            this.getItems().set(slot, stack);
            if (this.level != null) this.updateState(slot); // CraftBukkit - SPIGOT-7381: check for null world
        } else if (stack.isEmpty()) {
            this.removeItem(slot, this.getMaxStackSize());
        }
    }

    @Override
    public boolean canTakeItem(Container target, int slot, ItemStack stack) {
        return target.hasAnyMatching(
            itemStack -> itemStack.isEmpty()
                || ItemStack.isSameItemSameComponents(stack, itemStack) && itemStack.getCount() + stack.getCount() <= target.getMaxStackSize(itemStack)
        );
    }

    @Override
    public NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    public int getLastInteractedSlot() {
        return this.lastInteractedSlot;
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter componentGetter) {
        super.applyImplicitComponents(componentGetter);
        componentGetter.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(this.items);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(this.items));
    }

    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        output.discard("Items");
    }

    @Override public boolean lithium$itemInsertionTestRequiresStackSize1() {return true;} // Leaves - Lithium Sleeping Block Entity
}
