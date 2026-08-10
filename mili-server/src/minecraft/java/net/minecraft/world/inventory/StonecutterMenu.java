package net.minecraft.world.inventory;

import java.util.List;
import java.util.Optional;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SelectableRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

public class StonecutterMenu extends AbstractContainerMenu {
    public static final int INPUT_SLOT = 0;
    public static final int RESULT_SLOT = 1;
    private static final int INV_SLOT_START = 2;
    private static final int INV_SLOT_END = 29;
    private static final int USE_ROW_SLOT_START = 29;
    private static final int USE_ROW_SLOT_END = 38;
    private final ContainerLevelAccess access;
    final DataSlot selectedRecipeIndex = DataSlot.shared(new int[1], 0); // Paper - Add PlayerStonecutterRecipeSelectEvent
    private final Level level;
    private SelectableRecipe.SingleInputSet<StonecutterRecipe> recipesForInput = SelectableRecipe.SingleInputSet.empty();
    private ItemStack input = ItemStack.EMPTY;
    long lastSoundTime;
    final Slot inputSlot;
    final Slot resultSlot;
    Runnable slotUpdateListener = () -> {};
    public final Container container; // Paper - Add missing InventoryHolders - move down
    final ResultContainer resultContainer; // Paper - Add missing InventoryHolders - move down
    // CraftBukkit start
    private @javax.annotation.Nullable org.bukkit.craftbukkit.inventory.view.CraftStonecutterView view = null;
    private final org.bukkit.entity.Player player;

    @Override
    public org.bukkit.craftbukkit.inventory.view.CraftStonecutterView getBukkitView() {
        if (this.view != null) {
            return this.view;
        }

        org.bukkit.craftbukkit.inventory.CraftInventoryStonecutter inventory = new org.bukkit.craftbukkit.inventory.CraftInventoryStonecutter(this.container, this.resultContainer);
        this.view = new org.bukkit.craftbukkit.inventory.view.CraftStonecutterView(this.player, inventory, this);
        return this.view;
    }
    // CraftBukkit end

    public StonecutterMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, ContainerLevelAccess.NULL);
    }

    public StonecutterMenu(int containerId, Inventory playerInventory, final ContainerLevelAccess access) {
        super(MenuType.STONECUTTER, containerId);
        this.access = access;
        this.level = playerInventory.player.level();
        // Paper start
        this.container = new SimpleContainer(this.createBlockHolder(access), 1) { // Paper - Add missing InventoryHolders
            @Override
            public void setChanged() {
                super.setChanged();
                StonecutterMenu.this.slotsChanged(this);
                StonecutterMenu.this.slotUpdateListener.run();
            }
            // CraftBukkit start
            @Override
            public org.bukkit.Location getLocation() {
                return access.getLocation();
            }
            // CraftBukkit end
        };
        this.resultContainer = new ResultContainer(this.createBlockHolder(access)); // Paper - Add missing InventoryHolders
        // Paper end
        this.inputSlot = this.addSlot(new Slot(this.container, 0, 20, 33));
        this.resultSlot = this.addSlot(new Slot(this.resultContainer, 1, 143, 33) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public void onTake(Player player, ItemStack stack) {
                stack.onCraftedBy(player, stack.getCount());
                StonecutterMenu.this.resultContainer.awardUsedRecipes(player, this.getRelevantItems());
                ItemStack itemStack = StonecutterMenu.this.inputSlot.remove(1);
                if (!itemStack.isEmpty()) {
                    StonecutterMenu.this.setupResultSlot(StonecutterMenu.this.selectedRecipeIndex.get());
                }

                access.execute((level, pos) -> {
                    long gameTime = level.getGameTime();
                    if (StonecutterMenu.this.lastSoundTime != gameTime) {
                        level.playSound(null, pos, SoundEvents.UI_STONECUTTER_TAKE_RESULT, SoundSource.BLOCKS, 1.0F, 1.0F);
                        StonecutterMenu.this.lastSoundTime = gameTime;
                    }
                });
                super.onTake(player, stack);
            }

            private List<ItemStack> getRelevantItems() {
                return List.of(StonecutterMenu.this.inputSlot.getItem());
            }
        });
        this.addStandardInventorySlots(playerInventory, 8, 84);
        this.addDataSlot(this.selectedRecipeIndex);
        this.player = (org.bukkit.entity.Player) playerInventory.player.getBukkitEntity(); // CraftBukkit
    }

    public int getSelectedRecipeIndex() {
        return this.selectedRecipeIndex.get();
    }

    public SelectableRecipe.SingleInputSet<StonecutterRecipe> getVisibleRecipes() {
        return this.recipesForInput;
    }

    public int getNumberOfVisibleRecipes() {
        return this.recipesForInput.size();
    }

    public boolean hasInputItem() {
        return this.inputSlot.hasItem() && !this.recipesForInput.isEmpty();
    }

    @Override
    public boolean stillValid(Player player) {
        if (!this.checkReachable) return true; // CraftBukkit
        return stillValid(this.access, player, Blocks.STONECUTTER);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (this.selectedRecipeIndex.get() == id) {
            return false;
        } else {
            if (this.isValidRecipeIndex(id)) {
                // Paper start - Add PlayerStonecutterRecipeSelectEvent
                int recipeIndex = id;
                this.selectedRecipeIndex.set(recipeIndex);
                this.selectedRecipeIndex.checkAndClearUpdateFlag(); // mark as changed
                paperEventBlock: if (this.isValidRecipeIndex(id)) {
                    final Optional<RecipeHolder<StonecutterRecipe>> recipe = this.recipesForInput.entries().get(id).recipe().recipe();
                    if (recipe.isEmpty()) break paperEventBlock; // The recipe selected does not have an actual server recipe (presumably its the empty one). Cannot call the event, just break.

                    io.papermc.paper.event.player.PlayerStonecutterRecipeSelectEvent event = new io.papermc.paper.event.player.PlayerStonecutterRecipeSelectEvent((org.bukkit.entity.Player) player.getBukkitEntity(), getBukkitView().getTopInventory(), (org.bukkit.inventory.StonecuttingRecipe) recipe.get().toBukkitRecipe());
                    if (!event.callEvent()) {
                        player.containerMenu.sendAllDataToRemote();
                        return false;
                    }

                    net.minecraft.resources.Identifier key = org.bukkit.craftbukkit.util.CraftNamespacedKey.toMinecraft(event.getStonecuttingRecipe().getKey());
                    if (!recipe.get().id().identifier().equals(key)) { // If the recipe did NOT stay the same
                        for (int newRecipeIndex = 0; newRecipeIndex < this.recipesForInput.entries().size(); newRecipeIndex++) {
                            if (this.recipesForInput.entries().get(newRecipeIndex).recipe().recipe().filter(r -> r.id().identifier().equals(key)).isPresent()) {
                                recipeIndex = newRecipeIndex;
                                break;
                            }
                        }
                    }
                }
                player.containerMenu.sendAllDataToRemote();
                this.selectedRecipeIndex.set(recipeIndex); // set new index, so that listeners can read it
                this.setupResultSlot(recipeIndex);
                // Paper end - Add PlayerStonecutterRecipeSelectEvent
            }

            return true;
        }
    }

    private boolean isValidRecipeIndex(int recipeIndex) {
        return recipeIndex >= 0 && recipeIndex < this.recipesForInput.size();
    }

    @Override
    public void slotsChanged(Container inventory) {
        ItemStack item = this.inputSlot.getItem();
        if (!item.is(this.input.getItem())) {
            this.input = item.copy();
            this.setupRecipeList(item);
        }
        org.bukkit.craftbukkit.event.CraftEventFactory.callPrepareResultEvent(this, RESULT_SLOT); // Paper - Add PrepareResultEvent
    }

    private void setupRecipeList(ItemStack stack) {
        this.selectedRecipeIndex.set(-1);
        this.resultSlot.set(ItemStack.EMPTY);
        if (!stack.isEmpty()) {
            this.recipesForInput = this.level.recipeAccess().stonecutterRecipes().selectByInput(stack);
        } else {
            this.recipesForInput = SelectableRecipe.SingleInputSet.empty();
        }
    }

    void setupResultSlot(int id) {
        Optional<RecipeHolder<StonecutterRecipe>> optional;
        if (!this.recipesForInput.isEmpty() && this.isValidRecipeIndex(id)) {
            SelectableRecipe.SingleInputEntry<StonecutterRecipe> singleInputEntry = this.recipesForInput.entries().get(id);
            optional = singleInputEntry.recipe().recipe();
        } else {
            optional = Optional.empty();
        }

        optional.ifPresentOrElse(recipe -> {
            this.resultContainer.setRecipeUsed((RecipeHolder<?>)recipe);
            this.resultSlot.set(recipe.value().assemble(new SingleRecipeInput(this.container.getItem(0)), this.level.registryAccess()));
        }, () -> {
            this.resultSlot.set(ItemStack.EMPTY);
            this.resultContainer.setRecipeUsed(null);
        });
        this.broadcastChanges();
    }

    @Override
    public MenuType<?> getType() {
        return MenuType.STONECUTTER;
    }

    public void registerUpdateListener(Runnable listener) {
        this.slotUpdateListener = listener;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != this.resultContainer && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack itemStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(slotIndex);
        if (slot != null && slot.hasItem()) {
            ItemStack item = slot.getItem();
            Item item1 = item.getItem();
            itemStack = item.copy();
            if (slotIndex == 1) {
                item1.onCraftedBy(item, player);
                if (!this.moveItemStackTo(item, 2, 38, true)) {
                    return ItemStack.EMPTY;
                }

                slot.onQuickCraft(item, itemStack);
            } else if (slotIndex == 0) {
                if (!this.moveItemStackTo(item, 2, 38, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (this.level.recipeAccess().stonecutterRecipes().acceptsInput(item)) {
                if (!this.moveItemStackTo(item, 0, 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (slotIndex >= 2 && slotIndex < 29) {
                if (!this.moveItemStackTo(item, 29, 38, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (slotIndex >= 29 && slotIndex < 38 && !this.moveItemStackTo(item, 2, 29, false)) {
                return ItemStack.EMPTY;
            }

            if (item.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            }

            slot.setChanged();
            if (item.getCount() == itemStack.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTake(player, item);
            if (slotIndex == 1) {
                player.drop(item, false);
            }

            this.broadcastChanges();
        }

        return itemStack;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.resultContainer.removeItemNoUpdate(1);
        this.access.execute((level, pos) -> this.clearContainer(player, this.container));
    }
}
