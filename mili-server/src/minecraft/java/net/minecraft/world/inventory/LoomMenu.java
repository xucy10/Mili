package net.minecraft.world.inventory;

import com.google.common.collect.ImmutableList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BannerPatternTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

public class LoomMenu extends AbstractContainerMenu {
    private static final int PATTERN_NOT_SET = -1;
    private static final int INV_SLOT_START = 4;
    private static final int INV_SLOT_END = 31;
    private static final int USE_ROW_SLOT_START = 31;
    private static final int USE_ROW_SLOT_END = 40;
    private final ContainerLevelAccess access;
    final DataSlot selectedBannerPatternIndex = DataSlot.standalone();
    private List<Holder<BannerPattern>> selectablePatterns = List.of();
    Runnable slotUpdateListener = () -> {};
    private final HolderGetter<BannerPattern> patternGetter;
    final Slot bannerSlot;
    final Slot dyeSlot;
    private final Slot patternSlot;
    private final Slot resultSlot;
    long lastSoundTime;
    private final Container inputContainer; // Paper - Add missing InventoryHolders - move down
    private final Container outputContainer; // Paper - Add missing InventoryHolders - move down
    // CraftBukkit start
    private @javax.annotation.Nullable org.bukkit.craftbukkit.inventory.view.CraftLoomView view = null;
    private final org.bukkit.entity.Player player;

    @Override
    public org.bukkit.craftbukkit.inventory.view.CraftLoomView getBukkitView() {
        if (this.view != null) {
            return this.view;
        }

        org.bukkit.craftbukkit.inventory.CraftInventoryLoom inventory = new org.bukkit.craftbukkit.inventory.CraftInventoryLoom(this.inputContainer, this.outputContainer);
        this.view = new org.bukkit.craftbukkit.inventory.view.CraftLoomView(this.player, inventory, this);
        return this.view;
    }
    // CraftBukkit end

    public LoomMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, ContainerLevelAccess.NULL);
    }

    public LoomMenu(int containerId, Inventory playerInventory, final ContainerLevelAccess access) {
        super(MenuType.LOOM, containerId);
        this.access = access;
        // CraftBukkit start
        this.inputContainer = new SimpleContainer(this.createBlockHolder(access), 3) { // Paper - Add missing InventoryHolders
            @Override
            public void setChanged() {
                super.setChanged();
                LoomMenu.this.slotsChanged(this);
                LoomMenu.this.slotUpdateListener.run();
            }
        };
        this.outputContainer =  new SimpleContainer(this.createBlockHolder(access), 1) { // Paper - Add missing InventoryHolders
            @Override
            public void setChanged() {
                super.setChanged();
                LoomMenu.this.slotUpdateListener.run();
            }

            @Override
            public org.bukkit.Location getLocation() {
                return access.getLocation();
            }
        };
        // CraftBukkit end
        this.bannerSlot = this.addSlot(new Slot(this.inputContainer, 0, 13, 26) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof BannerItem;
            }
        });
        this.dyeSlot = this.addSlot(new Slot(this.inputContainer, 1, 33, 26) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof DyeItem;
            }
        });
        this.patternSlot = this.addSlot(new Slot(this.inputContainer, 2, 23, 45) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.has(DataComponents.PROVIDES_BANNER_PATTERNS);
            }
        });
        this.resultSlot = this.addSlot(new Slot(this.outputContainer, 0, 143, 57) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public void onTake(Player player, ItemStack stack) {
                LoomMenu.this.bannerSlot.remove(1);
                LoomMenu.this.dyeSlot.remove(1);
                if (!LoomMenu.this.bannerSlot.hasItem() || !LoomMenu.this.dyeSlot.hasItem()) {
                    LoomMenu.this.selectedBannerPatternIndex.set(-1);
                }

                access.execute((level, pos) -> {
                    long gameTime = level.getGameTime();
                    if (LoomMenu.this.lastSoundTime != gameTime) {
                        level.playSound(null, pos, SoundEvents.UI_LOOM_TAKE_RESULT, SoundSource.BLOCKS, 1.0F, 1.0F);
                        LoomMenu.this.lastSoundTime = gameTime;
                    }
                });
                super.onTake(player, stack);
            }
        });
        this.addStandardInventorySlots(playerInventory, 8, 84);
        this.addDataSlot(this.selectedBannerPatternIndex);
        this.patternGetter = playerInventory.player.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
        this.player = (org.bukkit.entity.Player) playerInventory.player.getBukkitEntity(); // CraftBukkit
    }

    @Override
    public boolean stillValid(Player player) {
        if (!this.checkReachable) return true; // CraftBukkit
        return stillValid(this.access, player, Blocks.LOOM);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id >= 0 && id < this.selectablePatterns.size()) {
            // Paper start - Add PlayerLoomPatternSelectEvent
            int selectablePatternIndex = id;
            io.papermc.paper.event.player.PlayerLoomPatternSelectEvent event = new io.papermc.paper.event.player.PlayerLoomPatternSelectEvent((org.bukkit.entity.Player) player.getBukkitEntity(), this.getBukkitView().getTopInventory(), org.bukkit.craftbukkit.block.banner.CraftPatternType.minecraftHolderToBukkit(this.selectablePatterns.get(selectablePatternIndex)));
            if (!event.callEvent()) {
                player.containerMenu.sendAllDataToRemote();
                return false;
            }
            final Holder<BannerPattern> eventPattern = org.bukkit.craftbukkit.block.banner.CraftPatternType.bukkitToMinecraftHolder(event.getPatternType());
            Holder<BannerPattern> selectedPattern = null;
            for (int i = 0; i < this.selectablePatterns.size(); i++) {
                final Holder<BannerPattern> holder = this.selectablePatterns.get(i);
                if (eventPattern.equals(holder)) {
                    selectablePatternIndex = i;
                    selectedPattern = holder;
                    break;
                }
            }
            if (selectedPattern == null) {
                selectedPattern = eventPattern;
                selectablePatternIndex = -1;
            }

            player.containerMenu.sendAllDataToRemote();
            this.selectedBannerPatternIndex.set(selectablePatternIndex);
            this.setupResultSlot(java.util.Objects.requireNonNull(selectedPattern, "selectedPattern was null, this is unexpected"));
            // Paper end - Add PlayerLoomPatternSelectEvent
            return true;
        } else {
            return false;
        }
    }

    private List<Holder<BannerPattern>> getSelectablePatterns(ItemStack stack) {
        if (stack.isEmpty()) {
            return this.patternGetter.get(BannerPatternTags.NO_ITEM_REQUIRED).map(ImmutableList::copyOf).orElse(ImmutableList.of());
        } else {
            TagKey<BannerPattern> tagKey = stack.get(DataComponents.PROVIDES_BANNER_PATTERNS);
            return (List<Holder<BannerPattern>>)(tagKey != null
                ? this.patternGetter.get(tagKey).map(ImmutableList::copyOf).orElse(ImmutableList.of())
                : List.of());
        }
    }

    private boolean isValidPatternIndex(int index) {
        return index >= 0 && index < this.selectablePatterns.size();
    }

    @Override
    public void slotsChanged(Container inventory) {
        ItemStack item = this.bannerSlot.getItem();
        ItemStack item1 = this.dyeSlot.getItem();
        ItemStack item2 = this.patternSlot.getItem();
        if (!item.isEmpty() && !item1.isEmpty()) {
            int i = this.selectedBannerPatternIndex.get();
            boolean isValidPatternIndex = this.isValidPatternIndex(i);
            List<Holder<BannerPattern>> list = this.selectablePatterns;
            this.selectablePatterns = this.getSelectablePatterns(item2);
            Holder<BannerPattern> holder;
            if (this.selectablePatterns.size() == 1) {
                this.selectedBannerPatternIndex.set(0);
                holder = this.selectablePatterns.get(0);
            } else if (!isValidPatternIndex) {
                this.selectedBannerPatternIndex.set(-1);
                holder = null;
            } else {
                Holder<BannerPattern> holder1 = list.get(i);
                int index = this.selectablePatterns.indexOf(holder1);
                if (index != -1) {
                    holder = holder1;
                    this.selectedBannerPatternIndex.set(index);
                } else {
                    holder = null;
                    this.selectedBannerPatternIndex.set(-1);
                }
            }

            if (holder != null) {
                BannerPatternLayers bannerPatternLayers = item.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY);
                boolean flag = bannerPatternLayers.layers().size() >= 6;
                if (flag) {
                    this.selectedBannerPatternIndex.set(-1);
                    this.resultSlot.set(ItemStack.EMPTY);
                } else {
                    this.setupResultSlot(holder);
                }
            } else {
                this.resultSlot.set(ItemStack.EMPTY);
            }

            // this.broadcastChanges(); // Paper - Add PrepareResultEvent; done below
            org.bukkit.craftbukkit.event.CraftEventFactory.callPrepareResultEvent(this, 3); // Paper - Add PrepareResultEvent
        } else {
            this.resultSlot.set(ItemStack.EMPTY);
            this.selectablePatterns = List.of();
            this.selectedBannerPatternIndex.set(-1);
        }
    }

    public List<Holder<BannerPattern>> getSelectablePatterns() {
        return this.selectablePatterns;
    }

    public int getSelectedBannerPatternIndex() {
        return this.selectedBannerPatternIndex.get();
    }

    public void registerUpdateListener(Runnable listener) {
        this.slotUpdateListener = listener;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack itemStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(slotIndex);
        if (slot != null && slot.hasItem()) {
            ItemStack item = slot.getItem();
            itemStack = item.copy();
            if (slotIndex == this.resultSlot.index) {
                if (!this.moveItemStackTo(item, 4, 40, true)) {
                    return ItemStack.EMPTY;
                }

                slot.onQuickCraft(item, itemStack);
            } else if (slotIndex != this.dyeSlot.index && slotIndex != this.bannerSlot.index && slotIndex != this.patternSlot.index) {
                if (item.getItem() instanceof BannerItem) {
                    if (!this.moveItemStackTo(item, this.bannerSlot.index, this.bannerSlot.index + 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (item.getItem() instanceof DyeItem) {
                    if (!this.moveItemStackTo(item, this.dyeSlot.index, this.dyeSlot.index + 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (item.has(DataComponents.PROVIDES_BANNER_PATTERNS)) {
                    if (!this.moveItemStackTo(item, this.patternSlot.index, this.patternSlot.index + 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (slotIndex >= 4 && slotIndex < 31) {
                    if (!this.moveItemStackTo(item, 31, 40, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (slotIndex >= 31 && slotIndex < 40 && !this.moveItemStackTo(item, 4, 31, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(item, 4, 40, false)) {
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
        }

        return itemStack;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.access.execute((level, pos) -> this.clearContainer(player, this.inputContainer));
    }

    private void setupResultSlot(Holder<BannerPattern> pattern) {
        ItemStack item = this.bannerSlot.getItem();
        ItemStack item1 = this.dyeSlot.getItem();
        ItemStack itemStack = ItemStack.EMPTY;
        if (!item.isEmpty() && !item1.isEmpty()) {
            itemStack = item.copyWithCount(1);
            DyeColor dyeColor = ((DyeItem)item1.getItem()).getDyeColor();
            itemStack.update(
                DataComponents.BANNER_PATTERNS,
                BannerPatternLayers.EMPTY,
                // CraftBukkit start
                bannerPatternLayers -> {
                    if (bannerPatternLayers.layers().size() > 20) {
                        bannerPatternLayers = new BannerPatternLayers(List.copyOf(bannerPatternLayers.layers().subList(0, 20)));
                    }
                    return new BannerPatternLayers.Builder().addAll(bannerPatternLayers).add(pattern, dyeColor).build();
                }
                // CraftBukkit end
            );
        }

        if (!ItemStack.matches(itemStack, this.resultSlot.getItem())) {
            this.resultSlot.set(itemStack);
        }
    }

    public Slot getBannerSlot() {
        return this.bannerSlot;
    }

    public Slot getDyeSlot() {
        return this.dyeSlot;
    }

    public Slot getPatternSlot() {
        return this.patternSlot;
    }

    public Slot getResultSlot() {
        return this.resultSlot;
    }
}
