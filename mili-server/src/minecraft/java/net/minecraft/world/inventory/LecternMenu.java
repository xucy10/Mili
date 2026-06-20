package net.minecraft.world.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class LecternMenu extends AbstractContainerMenu {
    private static final int DATA_COUNT = 1;
    private static final int SLOT_COUNT = 1;
    public static final int BUTTON_PREV_PAGE = 1;
    public static final int BUTTON_NEXT_PAGE = 2;
    public static final int BUTTON_TAKE_BOOK = 3;
    public static final int BUTTON_PAGE_JUMP_RANGE_START = 100;
    private final Container lectern;
    private final ContainerData lecternData;
    // CraftBukkit start
    private @javax.annotation.Nullable org.bukkit.craftbukkit.inventory.view.CraftLecternView view = null;
    private final org.bukkit.entity.Player player;

    @Override
    public org.bukkit.craftbukkit.inventory.view.CraftLecternView getBukkitView() {
        if (this.view != null) {
            return this.view;
        }

        org.bukkit.craftbukkit.inventory.CraftInventoryLectern inventory = new org.bukkit.craftbukkit.inventory.CraftInventoryLectern(this.lectern);
        this.view = new org.bukkit.craftbukkit.inventory.view.CraftLecternView(this.player, inventory, this);
        return this.view;
    }
    // CraftBukkit end

    // CraftBukkit start - add player inventory
    public LecternMenu(int containerId, net.minecraft.world.entity.player.Inventory playerinventory) {
        this(containerId, new SimpleContainer(1), new SimpleContainerData(1), playerinventory);
    }

    public LecternMenu(int containerId, Container lectern, ContainerData lecternData, net.minecraft.world.entity.player.Inventory playerinventory) {
        // CraftBukkit end - add player inventory
        super(MenuType.LECTERN, containerId);
        checkContainerSize(lectern, 1);
        checkContainerDataCount(lecternData, 1);
        this.lectern = lectern;
        this.lecternData = lecternData;
        this.addSlot(new Slot(lectern, 0, 0, 0) {
            @Override
            public void setChanged() {
                super.setChanged();
                LecternMenu.this.slotsChanged(this.container);
            }
        });
        this.addDataSlots(lecternData);
        this.player = (org.bukkit.entity.Player) playerinventory.player.getBukkitEntity(); // CraftBukkit
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        io.papermc.paper.event.player.PlayerLecternPageChangeEvent playerLecternPageChangeEvent; org.bukkit.craftbukkit.inventory.CraftInventoryLectern bukkitView; // Paper - Add PlayerLecternPageChangeEvent
        if (id >= 100) {
            int i = id - 100;
            this.setData(0, i);
            return true;
        } else {
            switch (id) {
                case 1: {
                    int i = this.lecternData.get(0);
                    // Paper start - Add PlayerLecternPageChangeEvent
                    bukkitView = (org.bukkit.craftbukkit.inventory.CraftInventoryLectern) getBukkitView().getTopInventory();
                    playerLecternPageChangeEvent = new io.papermc.paper.event.player.PlayerLecternPageChangeEvent((org.bukkit.entity.Player) player.getBukkitEntity(), bukkitView.getHolder(), bukkitView.getBook(), io.papermc.paper.event.player.PlayerLecternPageChangeEvent.PageChangeDirection.LEFT, i, i - 1);
                    if (!playerLecternPageChangeEvent.callEvent()) {
                        return false;
                    }
                    this.setData(0, playerLecternPageChangeEvent.getNewPage());
                    // Paper end - Add PlayerLecternPageChangeEvent
                    return true;
                }
                case 2: {
                    int i = this.lecternData.get(0);
                    // Paper start - Add PlayerLecternPageChangeEvent
                    bukkitView = (org.bukkit.craftbukkit.inventory.CraftInventoryLectern) getBukkitView().getTopInventory();
                    playerLecternPageChangeEvent = new io.papermc.paper.event.player.PlayerLecternPageChangeEvent((org.bukkit.entity.Player) player.getBukkitEntity(), bukkitView.getHolder(), bukkitView.getBook(), io.papermc.paper.event.player.PlayerLecternPageChangeEvent.PageChangeDirection.RIGHT, i, i + 1);
                    if (!playerLecternPageChangeEvent.callEvent()) {
                        return false;
                    }
                    this.setData(0, playerLecternPageChangeEvent.getNewPage());
                    // Paper end - Add PlayerLecternPageChangeEvent
                    return true;
                }
                case 3:
                    if (!player.mayBuild()) {
                        return false;
                    }

                    // CraftBukkit start - Event for taking the book
                    org.bukkit.event.player.PlayerTakeLecternBookEvent event = new org.bukkit.event.player.PlayerTakeLecternBookEvent(this.player, this.getBukkitView().getTopInventory().getHolder());
                    org.bukkit.Bukkit.getServer().getPluginManager().callEvent(event);
                    if (event.isCancelled()) {
                        return false;
                    }
                    // CraftBukkit end
                    ItemStack itemStack = this.lectern.removeItemNoUpdate(0);
                    this.lectern.setChanged();
                    if (!player.getInventory().add(itemStack)) {
                        player.drop(itemStack, false);
                    }

                    return true;
                default:
                    return false;
            }
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setData(int id, int data) {
        super.setData(id, data);
        this.broadcastChanges();
    }

    @Override
    public boolean stillValid(Player player) {
        if (this.lectern instanceof net.minecraft.world.level.block.entity.LecternBlockEntity.LecternInventory && !((net.minecraft.world.level.block.entity.LecternBlockEntity.LecternInventory) this.lectern).getLectern().hasBook()) return false; // CraftBukkit
        if (!this.checkReachable) return true; // CraftBukkit
        return this.lectern.stillValid(player);
    }

    public ItemStack getBook() {
        return this.lectern.getItem(0);
    }

    public int getPage() {
        return this.lecternData.get(0);
    }
}
