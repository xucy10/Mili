package net.minecraft.world.inventory;

import java.util.List;
import java.util.Optional;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

public class CraftingMenu extends AbstractCraftingMenu {
    private static final int CRAFTING_GRID_WIDTH = 3;
    private static final int CRAFTING_GRID_HEIGHT = 3;
    public static final int RESULT_SLOT = 0;
    private static final int CRAFT_SLOT_START = 1;
    private static final int CRAFT_SLOT_COUNT = 9;
    private static final int CRAFT_SLOT_END = 10;
    private static final int INV_SLOT_START = 10;
    private static final int INV_SLOT_END = 37;
    private static final int USE_ROW_SLOT_START = 37;
    private static final int USE_ROW_SLOT_END = 46;
    public final ContainerLevelAccess access;
    private final Player player;
    private boolean placingRecipe;
    private org.bukkit.craftbukkit.inventory.@Nullable CraftInventoryView view = null; // CraftBukkit

    public CraftingMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, ContainerLevelAccess.NULL);
    }

    public CraftingMenu(int containerId, Inventory playerInventory, ContainerLevelAccess access) {
        super(MenuType.CRAFTING, containerId, 3, 3, playerInventory); // CraftBukkit - pass player
        this.access = access;
        this.player = playerInventory.player;
        this.addResultSlot(this.player, 124, 35);
        this.addCraftingGridSlots(30, 17);
        this.addStandardInventorySlots(playerInventory, 8, 84);
    }

    protected static void slotChangedCraftingGrid(
        AbstractContainerMenu menu,
        ServerLevel level,
        Player player,
        CraftingContainer craftSlots,
        ResultContainer resultSlots,
        @Nullable RecipeHolder<CraftingRecipe> recipe
    ) {
        CraftingInput craftInput = craftSlots.asCraftInput();
        ServerPlayer serverPlayer = (ServerPlayer)player;
        ItemStack itemStack = ItemStack.EMPTY;
        // Paper start - Perf: Improve mass crafting; check last recipe used first
        /*
        When the server crafts all available items in CraftingMenu or InventoryMenu the game
        checks either 4 or 9 times for each individual craft for a matching recipe for that container.
        This check can be expensive if 64 total crafts are being performed with the recipe matching logic
        being run 64 * 9 + 64 times. A breakdown of those times is below. This caches the last matching
        recipe so that it is checked first and only if it doesn't match does the rest of the matching logic run.

        Shift-click crafts are processed one at a time, so shift clicking on an item in the result of a iron block craft
        where all the 9 inputs are full stacks of iron will run 64 iron block crafts. For each of those crafts, the
        'remaining' blocks are calculated. This is due to recipes that have leftover items like buckets. This is done
        for each craft, and done once to get the full 9 leftover items which are usually air. Then 1 item is removed
        from each of the 9 inputs and each time that happens, logic is triggered to update the result itemstack. So
        for each craft, that logic is run 9 times (hence the 64 * 9). The + 64 is from the 64 checks for remaining items.

        After this change, the full iteration over all recipes checking for a match should run once for a full craft to find the
        initial recipe match. Then that recipe will be checked first for all future recipe match checks.

        See also: ResultSlot class
         */
        if (recipe == null) {
            recipe = craftSlots.getCurrentRecipe();
        }
        // Paper end - Perf: Improve mass crafting; check last recipe used first
        Optional<RecipeHolder<CraftingRecipe>> recipeFor = level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, craftInput, level, recipe);
        craftSlots.setCurrentRecipe(recipeFor.orElse(null)); // CraftBukkit
        if (recipeFor.isPresent()) {
            RecipeHolder<CraftingRecipe> recipeHolder = recipeFor.get();
            CraftingRecipe craftingRecipe = recipeHolder.value();
            if (resultSlots.setRecipeUsed(serverPlayer, recipeHolder)) {
                ItemStack itemStack1 = craftingRecipe.assemble(craftInput, level.registryAccess());
                if (itemStack1.isItemEnabled(level.enabledFeatures())) {
                    itemStack = itemStack1;
                }
            }
        }
        itemStack = org.bukkit.craftbukkit.event.CraftEventFactory.callPreCraftEvent(craftSlots, resultSlots, itemStack, menu.getBukkitView(), recipeFor.map(RecipeHolder::value).orElse(null) instanceof net.minecraft.world.item.crafting.RepairItemRecipe); // CraftBukkit

        resultSlots.setItem(0, itemStack);
        menu.setRemoteSlot(0, itemStack);
        serverPlayer.connection.send(new ClientboundContainerSetSlotPacket(menu.containerId, menu.incrementStateId(), 0, itemStack));
    }

    @Override
    public void slotsChanged(Container inventory) {
        if (!this.placingRecipe) {
            this.access.execute((level, pos) -> {
                if (level instanceof ServerLevel serverLevel) {
                    slotChangedCraftingGrid(this, serverLevel, this.player, this.craftSlots, this.resultSlots, null);
                }
            });
        }
    }

    @Override
    public void beginPlacingRecipe() {
        this.placingRecipe = true;
    }

    @Override
    public void finishPlacingRecipe(ServerLevel level, RecipeHolder<CraftingRecipe> recipe) {
        this.placingRecipe = false;
        slotChangedCraftingGrid(this, level, this.player, this.craftSlots, this.resultSlots, recipe);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.access.execute((level, pos) -> this.clearContainer(player, this.craftSlots));
    }

    @Override
    public boolean stillValid(Player player) {
        if (!this.checkReachable) return true; // CraftBukkit
        return stillValid(this.access, player, Blocks.CRAFTING_TABLE);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack itemStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(slotIndex);
        if (slot != null && slot.hasItem()) {
            ItemStack item = slot.getItem();
            itemStack = item.copy();
            if (slotIndex == 0) {
                item.getItem().onCraftedBy(item, player);
                if (!this.moveItemStackTo(item, 10, 46, true)) {
                    return ItemStack.EMPTY;
                }

                slot.onQuickCraft(item, itemStack);
            } else if (slotIndex >= 10 && slotIndex < 46) {
                if (!this.moveItemStackTo(item, 1, 10, false)) {
                    if (slotIndex < 37) {
                        if (!this.moveItemStackTo(item, 37, 46, false)) {
                            return ItemStack.EMPTY;
                        }
                    } else if (!this.moveItemStackTo(item, 10, 37, false)) {
                        return ItemStack.EMPTY;
                    }
                }
            } else if (!this.moveItemStackTo(item, 10, 46, false)) {
                return ItemStack.EMPTY;
            }

            if (item.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            if (item.getCount() == itemStack.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTake(player, item);
            if (slotIndex == 0) {
                player.drop(item, false);
            }
        }

        return itemStack;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != this.resultSlots && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public Slot getResultSlot() {
        return this.slots.get(0);
    }

    @Override
    public List<Slot> getInputGridSlots() {
        return this.slots.subList(1, 10);
    }

    @Override
    public RecipeBookType getRecipeBookType() {
        return RecipeBookType.CRAFTING;
    }

    @Override
    protected Player owner() {
        return this.player;
    }

    // CraftBukkit start
    @Override
    public org.bukkit.craftbukkit.inventory.CraftInventoryView getBukkitView() {
        if (this.view != null) {
            return this.view;
        }

        org.bukkit.craftbukkit.inventory.CraftInventoryCrafting inventory = new org.bukkit.craftbukkit.inventory.CraftInventoryCrafting(this.craftSlots, this.resultSlots);
        this.view = new org.bukkit.craftbukkit.inventory.CraftInventoryView(this.player.getBukkitEntity(), inventory, this);
        return this.view;
    }
    // CraftBukkit end
}
