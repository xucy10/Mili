package net.minecraft.world.entity.player;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class Inventory implements Container, Nameable {
    public static final int POP_TIME_DURATION = 5;
    public static final int INVENTORY_SIZE = 36;
    public static final int SELECTION_SIZE = 9;
    public static final int SLOT_OFFHAND = 40;
    public static final int SLOT_BODY_ARMOR = 41;
    public static final int SLOT_SADDLE = 42;
    public static final int NOT_FOUND_INDEX = -1;
    public static final Int2ObjectMap<EquipmentSlot> EQUIPMENT_SLOT_MAPPING = new Int2ObjectArrayMap<>(
        Map.of(
            EquipmentSlot.FEET.getIndex(36),
            EquipmentSlot.FEET,
            EquipmentSlot.LEGS.getIndex(36),
            EquipmentSlot.LEGS,
            EquipmentSlot.CHEST.getIndex(36),
            EquipmentSlot.CHEST,
            EquipmentSlot.HEAD.getIndex(36),
            EquipmentSlot.HEAD,
            40,
            EquipmentSlot.OFFHAND,
            41,
            EquipmentSlot.BODY,
            42,
            EquipmentSlot.SADDLE
        )
    );
    private static final Component DEFAULT_NAME = Component.translatable("container.inventory");
    private final NonNullList<ItemStack> items = NonNullList.withSize(36, ItemStack.EMPTY);
    private int selected;
    public final Player player;
    public final EntityEquipment equipment;
    private int timesChanged;
    // Paper start - add fields and methods
    public static final EquipmentSlot[] EQUIPMENT_SLOTS_SORTED_BY_INDEX = EQUIPMENT_SLOT_MAPPING.int2ObjectEntrySet()
        .stream()
        .sorted(java.util.Comparator.comparingInt(it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry::getIntKey))
        .map(java.util.Map.Entry::getValue).toArray(EquipmentSlot[]::new);
    public java.util.List<org.bukkit.entity.HumanEntity> transaction = new java.util.ArrayList<>();
    private int maxStack = MAX_STACK;

    @Override
    public java.util.List<ItemStack> getContents() {
        java.util.List<ItemStack> combined = new java.util.ArrayList<>(this.items.size() + EQUIPMENT_SLOT_MAPPING.size());
        combined.addAll(this.items);
        for (EquipmentSlot equipmentSlot : EQUIPMENT_SLOTS_SORTED_BY_INDEX) {
            ItemStack itemStack = this.equipment.get(equipmentSlot);
            combined.add(itemStack); // Include empty items
        };
        return combined;
    }

    public java.util.List<ItemStack> getArmorContents() {
        java.util.List<ItemStack> items = new java.util.ArrayList<>(4);
        for (EquipmentSlot equipmentSlot : EQUIPMENT_SLOTS_SORTED_BY_INDEX) {
            if (equipmentSlot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
                items.add(this.equipment.get(equipmentSlot));
            }
        }
        return items;
    }

    public java.util.List<ItemStack> getExtraContent() {
        java.util.List<ItemStack> items = new java.util.ArrayList<>();
        for (EquipmentSlot equipmentSlot : EQUIPMENT_SLOTS_SORTED_BY_INDEX) {
            if (equipmentSlot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) { // Non humanoid armor is considered extra
                items.add(this.equipment.get(equipmentSlot));
            }
        }
        return items;
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
    public org.bukkit.inventory.InventoryHolder getOwner() {
        return this.player.getBukkitEntity();
    }

    @Override
    public int getMaxStackSize() {
        return this.maxStack;
    }

    @Override
    public void setMaxStackSize(int size) {
        this.maxStack = size;
    }

    @Override
    public org.bukkit.Location getLocation() {
        return this.player.getBukkitEntity().getLocation();
    }
    // Paper end - add fields and methods

    public Inventory(Player player, EntityEquipment equipment) {
        this.player = player;
        this.equipment = equipment;
    }

    public int getSelectedSlot() {
        return this.selected;
    }

    public void setSelectedSlot(int slot) {
        if (!isHotbarSlot(slot)) {
            throw new IllegalArgumentException("Invalid selected slot");
        } else {
            this.selected = slot;
        }
    }

    public ItemStack getSelectedItem() {
        return this.items.get(this.selected);
    }

    public ItemStack setSelectedItem(ItemStack stack) {
        return this.items.set(this.selected, stack);
    }

    public static int getSelectionSize() {
        return 9;
    }

    public NonNullList<ItemStack> getNonEquipmentItems() {
        return this.items;
    }

    private boolean hasRemainingSpaceForItem(ItemStack destination, ItemStack origin) {
        return !destination.isEmpty()
            && destination.isStackable()
            && destination.getCount() < this.getMaxStackSize(destination)
            && ItemStack.isSameItemSameComponents(destination, origin); // Paper - check if itemstack is stackable first
    }

    // CraftBukkit start - Watch method above! :D
    public int canHold(ItemStack itemStack) {
        int remains = itemStack.getCount();
        for (int slot = 0; slot < this.items.size(); ++slot) {
            ItemStack itemInSlot = this.getItem(slot);
            if (itemInSlot.isEmpty()) {
                return itemStack.getCount();
            }

            if (this.hasRemainingSpaceForItem(itemInSlot, itemStack)) {
                remains -= (itemInSlot.getMaxStackSize() < this.getMaxStackSize() ? itemInSlot.getMaxStackSize() : this.getMaxStackSize()) - itemInSlot.getCount();
            }
            if (remains <= 0) {
                return itemStack.getCount();
            }
        }

        ItemStack itemInOffhand = this.equipment.get(EquipmentSlot.OFFHAND);
        if (this.hasRemainingSpaceForItem(itemInOffhand, itemStack)) {
            remains -= (itemInOffhand.getMaxStackSize() < this.getMaxStackSize() ? itemInOffhand.getMaxStackSize() : this.getMaxStackSize()) - itemInOffhand.getCount();
        }
        if (remains <= 0) {
            return itemStack.getCount();
        }

        return itemStack.getCount() - remains;
    }
    // CraftBukkit end

    public int getFreeSlot() {
        for (int i = 0; i < this.items.size(); i++) {
            if (this.items.get(i).isEmpty()) {
                return i;
            }
        }

        return -1;
    }

    // Paper start - Add PlayerPickItemEvent
    public void addAndPickItem(ItemStack stack, final int targetSlot) {
        this.setSelectedSlot(targetSlot);
        // Paper end - Add PlayerPickItemEvent
        if (!this.items.get(this.selected).isEmpty()) {
            int freeSlot = this.getFreeSlot();
            if (freeSlot != -1) {
                this.items.set(freeSlot, this.items.get(this.selected));
            }
        }

        this.items.set(this.selected, stack);
    }

    // Paper start - Add PlayerPickItemEvent
    public void pickSlot(int index, final int targetSlot) {
        this.setSelectedSlot(targetSlot);
    // Paper end - Add PlayerPickItemEvent
        ItemStack itemStack = this.items.get(this.selected);
        this.items.set(this.selected, this.items.get(index));
        this.items.set(index, itemStack);
    }

    public static boolean isHotbarSlot(int index) {
        return index >= 0 && index < 9;
    }

    public int findSlotMatchingItem(ItemStack stack) {
        for (int i = 0; i < this.items.size(); i++) {
            if (!this.items.get(i).isEmpty() && ItemStack.isSameItemSameComponents(stack, this.items.get(i))) {
                return i;
            }
        }

        return -1;
    }

    public static boolean isUsableForCrafting(ItemStack stack) {
        return !stack.isDamaged() && !stack.isEnchanted() && !stack.has(DataComponents.CUSTOM_NAME);
    }

    public int findSlotMatchingCraftingIngredient(io.papermc.paper.inventory.recipe.ItemOrExact item, ItemStack stack) { // Paper - Improve exact choice recipe ingredients
        for (int i = 0; i < this.items.size(); i++) {
            ItemStack itemStack = this.items.get(i);
            if (!itemStack.isEmpty()
                && item.matches(itemStack) // Paper - Improve exact choice recipe ingredients
                && (!(item instanceof io.papermc.paper.inventory.recipe.ItemOrExact.Item) || Inventory.isUsableForCrafting(itemStack)) // Paper - Improve exact choice recipe ingredients
                && (stack.isEmpty() || ItemStack.isSameItemSameComponents(stack, itemStack))) {
                return i;
            }
        }

        return -1;
    }

    public int getSuitableHotbarSlot() {
        for (int i = 0; i < 9; i++) {
            int i1 = (this.selected + i) % 9;
            if (this.items.get(i1).isEmpty()) {
                return i1;
            }
        }

        for (int ix = 0; ix < 9; ix++) {
            int i1 = (this.selected + ix) % 9;
            if (!this.items.get(i1).isEnchanted()) {
                return i1;
            }
        }

        return this.selected;
    }

    public int clearOrCountMatchingItems(Predicate<ItemStack> stackPredicate, int maxCount, Container inventory) {
        int i = 0;
        boolean flag = maxCount == 0;
        i += ContainerHelper.clearOrCountMatchingItems(this, stackPredicate, maxCount - i, flag);
        i += ContainerHelper.clearOrCountMatchingItems(inventory, stackPredicate, maxCount - i, flag);
        ItemStack carried = this.player.containerMenu.getCarried();
        i += ContainerHelper.clearOrCountMatchingItems(carried, stackPredicate, maxCount - i, flag);
        if (carried.isEmpty()) {
            this.player.containerMenu.setCarried(ItemStack.EMPTY);
        }

        return i;
    }

    private int addResource(ItemStack stack) {
        int slotWithRemainingSpace = this.getSlotWithRemainingSpace(stack);
        if (slotWithRemainingSpace == -1) {
            slotWithRemainingSpace = this.getFreeSlot();
        }

        return slotWithRemainingSpace == -1 ? stack.getCount() : this.addResource(slotWithRemainingSpace, stack);
    }

    private int addResource(int slot, ItemStack stack) {
        int count = stack.getCount();
        ItemStack item = this.getItem(slot);
        if (item.isEmpty()) {
            item = stack.copyWithCount(0);
            this.setItem(slot, item);
        }

        int i = this.getMaxStackSize(item) - item.getCount();
        int min = Math.min(count, i);
        if (min == 0) {
            return count;
        } else {
            count -= min;
            item.grow(min);
            item.setPopTime(5);
            return count;
        }
    }

    public int getSlotWithRemainingSpace(ItemStack stack) {
        if (this.hasRemainingSpaceForItem(this.getItem(this.selected), stack)) {
            return this.selected;
        } else if (this.hasRemainingSpaceForItem(this.getItem(40), stack)) {
            return 40;
        } else {
            for (int i = 0; i < this.items.size(); i++) {
                if (this.hasRemainingSpaceForItem(this.items.get(i), stack)) {
                    return i;
                }
            }

            return -1;
        }
    }

    public void tick() {
        for (int i = 0; i < this.items.size(); i++) {
            ItemStack item = this.getItem(i);
            if (!item.isEmpty()) {
                item.inventoryTick(this.player.level(), this.player, i == this.selected ? EquipmentSlot.MAINHAND : null);
            }
        }
    }

    public boolean add(ItemStack stack) {
        return this.add(-1, stack);
    }

    public boolean add(int slot, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        } else {
            try {
                if (stack.isDamaged()) {
                    if (slot == -1) {
                        slot = this.getFreeSlot();
                    }

                    if (slot >= 0) {
                        this.items.set(slot, stack.copyAndClear());
                        this.items.get(slot).setPopTime(5);
                        return true;
                    } else if (this.player.hasInfiniteMaterials()) {
                        stack.setCount(0);
                        return true;
                    } else {
                        return false;
                    }
                } else {
                    int count;
                    do {
                        count = stack.getCount();
                        if (slot == -1) {
                            stack.setCount(this.addResource(stack));
                        } else {
                            stack.setCount(this.addResource(slot, stack));
                        }
                    } while (!stack.isEmpty() && stack.getCount() < count);

                    if (stack.getCount() == count && this.player.hasInfiniteMaterials()) {
                        stack.setCount(0);
                        return true;
                    } else {
                        return stack.getCount() < count;
                    }
                }
            } catch (Throwable var6) {
                CrashReport crashReport = CrashReport.forThrowable(var6, "Adding item to inventory");
                CrashReportCategory crashReportCategory = crashReport.addCategory("Item being added");
                crashReportCategory.setDetail("Item ID", Item.getId(stack.getItem()));
                crashReportCategory.setDetail("Item data", stack.getDamageValue());
                crashReportCategory.setDetail("Item name", () -> stack.getHoverName().getString());
                throw new ReportedException(crashReport);
            }
        }
    }

    public void placeItemBackInInventory(ItemStack stack) {
        this.placeItemBackInInventory(stack, true);
    }

    public void placeItemBackInInventory(ItemStack stack, boolean sendPacket) {
        while (!stack.isEmpty()) {
            int slotWithRemainingSpace = this.getSlotWithRemainingSpace(stack);
            if (slotWithRemainingSpace == -1) {
                slotWithRemainingSpace = this.getFreeSlot();
            }

            if (slotWithRemainingSpace == -1) {
                this.player.drop(stack, false);
                break;
            }

            int i = stack.getMaxStackSize() - this.getItem(slotWithRemainingSpace).getCount();
            if (this.add(slotWithRemainingSpace, stack.split(i)) && sendPacket && this.player instanceof ServerPlayer serverPlayer) {
                serverPlayer.connection.send(this.createInventoryUpdatePacket(slotWithRemainingSpace));
            }
        }
    }

    public ClientboundSetPlayerInventoryPacket createInventoryUpdatePacket(int slot) {
        return new ClientboundSetPlayerInventoryPacket(slot, this.getItem(slot).copy());
    }

    @Override
    public ItemStack removeItem(int index, int count) {
        if (index < this.items.size()) {
            return ContainerHelper.removeItem(this.items, index, count);
        } else {
            EquipmentSlot equipmentSlot = EQUIPMENT_SLOT_MAPPING.get(index);
            if (equipmentSlot != null) {
                ItemStack itemStack = this.equipment.get(equipmentSlot);
                if (!itemStack.isEmpty()) {
                    return itemStack.split(count);
                }
            }

            return ItemStack.EMPTY;
        }
    }

    public void removeItem(ItemStack stack) {
        for (int i = 0; i < this.items.size(); i++) {
            if (this.items.get(i) == stack) {
                this.items.set(i, ItemStack.EMPTY);
                return;
            }
        }

        for (EquipmentSlot equipmentSlot : EQUIPMENT_SLOT_MAPPING.values()) {
            ItemStack itemStack = this.equipment.get(equipmentSlot);
            if (itemStack == stack) {
                this.equipment.set(equipmentSlot, ItemStack.EMPTY);
                return;
            }
        }
    }

    @Override
    public ItemStack removeItemNoUpdate(int index) {
        if (index < this.items.size()) {
            ItemStack itemStack = this.items.get(index);
            this.items.set(index, ItemStack.EMPTY);
            return itemStack;
        } else {
            EquipmentSlot equipmentSlot = EQUIPMENT_SLOT_MAPPING.get(index);
            return equipmentSlot != null ? this.equipment.set(equipmentSlot, ItemStack.EMPTY) : ItemStack.EMPTY;
        }
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        if (index < this.items.size()) {
            this.items.set(index, stack);
        }

        EquipmentSlot equipmentSlot = EQUIPMENT_SLOT_MAPPING.get(index);
        if (equipmentSlot != null) {
            this.equipment.set(equipmentSlot, stack);
        }
    }

    public void save(ValueOutput.TypedOutputList<ItemStackWithSlot> output) {
        for (int i = 0; i < this.items.size(); i++) {
            ItemStack itemStack = this.items.get(i);
            if (!itemStack.isEmpty()) {
                output.add(new ItemStackWithSlot(i, itemStack));
            }
        }
    }

    public void load(ValueInput.TypedInputList<ItemStackWithSlot> input) {
        this.items.clear();

        for (ItemStackWithSlot itemStackWithSlot : input) {
            if (itemStackWithSlot.isValidInContainer(this.items.size())) {
                this.setItem(itemStackWithSlot.slot(), itemStackWithSlot.stack());
            }
        }
    }

    @Override
    public int getContainerSize() {
        return this.items.size() + EQUIPMENT_SLOT_MAPPING.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack itemStack : this.items) {
            if (!itemStack.isEmpty()) {
                return false;
            }
        }

        for (EquipmentSlot equipmentSlot : EQUIPMENT_SLOT_MAPPING.values()) {
            if (!this.equipment.get(equipmentSlot).isEmpty()) {
                return false;
            }
        }

        return true;
    }

    @Override
    public ItemStack getItem(int index) {
        if (index < this.items.size()) {
            return this.items.get(index);
        } else {
            EquipmentSlot equipmentSlot = EQUIPMENT_SLOT_MAPPING.get(index);
            return equipmentSlot != null ? this.equipment.get(equipmentSlot) : ItemStack.EMPTY;
        }
    }

    @Override
    public Component getName() {
        return DEFAULT_NAME;
    }

    public void dropAll() {
        for (int i = 0; i < this.items.size(); i++) {
            ItemStack itemStack = this.items.get(i);
            if (!itemStack.isEmpty()) {
                this.player.drop(itemStack, true, false);
                this.items.set(i, ItemStack.EMPTY);
            }
        }

        this.equipment.dropAll(this.player);
    }

    @Override
    public void setChanged() {
        this.timesChanged++;
    }

    public int getTimesChanged() {
        return this.timesChanged;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    public boolean contains(ItemStack stack) {
        for (ItemStack itemStack : this) {
            if (!itemStack.isEmpty() && ItemStack.isSameItemSameComponents(itemStack, stack)) {
                return true;
            }
        }

        return false;
    }

    public boolean contains(TagKey<Item> tag) {
        for (ItemStack itemStack : this) {
            if (!itemStack.isEmpty() && itemStack.is(tag)) {
                return true;
            }
        }

        return false;
    }

    public boolean contains(Predicate<ItemStack> predicate) {
        for (ItemStack itemStack : this) {
            if (predicate.test(itemStack)) {
                return true;
            }
        }

        return false;
    }

    public void replaceWith(Inventory playerInventory) {
        for (int i = 0; i < this.getContainerSize(); i++) {
            this.setItem(i, playerInventory.getItem(i));
        }

        this.setSelectedSlot(playerInventory.getSelectedSlot());
    }

    @Override
    public void clearContent() {
        this.items.clear();
        this.equipment.clear();
    }

    public void fillStackedContents(StackedItemContents contents) {
        for (ItemStack itemStack : this.items) {
            contents.accountSimpleStack(itemStack);
        }
    }

    public ItemStack removeFromSelected(boolean removeStack) {
        ItemStack selectedItem = this.getSelectedItem();
        return selectedItem.isEmpty() ? ItemStack.EMPTY : this.removeItem(this.selected, removeStack ? selectedItem.getCount() : 1);
    }
}
