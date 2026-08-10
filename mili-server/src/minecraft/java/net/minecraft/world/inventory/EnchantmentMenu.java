package net.minecraft.world.inventory;

import java.util.List;
import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.IdMap;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EnchantingTableBlock;

public class EnchantmentMenu extends AbstractContainerMenu {
    static final Identifier EMPTY_SLOT_LAPIS_LAZULI = Identifier.withDefaultNamespace("container/slot/lapis_lazuli");
    private final Container enchantSlots; // Paper - Add missing InventoryHolders - move down
    private final ContainerLevelAccess access;
    private final RandomSource random = RandomSource.create();
    private final DataSlot enchantmentSeed = DataSlot.standalone();
    public final int[] costs = new int[3];
    public final int[] enchantClue = new int[]{-1, -1, -1};
    public final int[] levelClue = new int[]{-1, -1, -1};
    // CraftBukkit start
    private @javax.annotation.Nullable org.bukkit.craftbukkit.inventory.view.CraftEnchantmentView view = null;
    private final org.bukkit.entity.Player player;
    // CraftBukkit end

    public EnchantmentMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, ContainerLevelAccess.NULL);
    }

    public EnchantmentMenu(int containerId, Inventory playerInventory, ContainerLevelAccess access) {
        super(MenuType.ENCHANTMENT, containerId);
        // Paper start - Add missing InventoryHolders
        this.enchantSlots = new SimpleContainer(this.createBlockHolder(access), 2) { // Paper - Add missing InventoryHolders
            @Override
            public void setChanged() {
                super.setChanged();
                EnchantmentMenu.this.slotsChanged(this);
            }

            // CraftBukkit start
            @Override
            public org.bukkit.Location getLocation() {
                return access.getLocation();
            }
            // CraftBukkit end
        };
        // Paper end - Add missing InventoryHolders
        this.access = access;
        this.addSlot(new Slot(this.enchantSlots, 0, 15, 47) {
            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        this.addSlot(new Slot(this.enchantSlots, 1, 35, 47) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(Items.LAPIS_LAZULI);
            }

            @Override
            public Identifier getNoItemIcon() {
                return EnchantmentMenu.EMPTY_SLOT_LAPIS_LAZULI;
            }
        });
        this.addStandardInventorySlots(playerInventory, 8, 84);
        this.addDataSlot(DataSlot.shared(this.costs, 0));
        this.addDataSlot(DataSlot.shared(this.costs, 1));
        this.addDataSlot(DataSlot.shared(this.costs, 2));
        this.addDataSlot(this.enchantmentSeed).set(playerInventory.player.getEnchantmentSeed());
        this.addDataSlot(DataSlot.shared(this.enchantClue, 0));
        this.addDataSlot(DataSlot.shared(this.enchantClue, 1));
        this.addDataSlot(DataSlot.shared(this.enchantClue, 2));
        this.addDataSlot(DataSlot.shared(this.levelClue, 0));
        this.addDataSlot(DataSlot.shared(this.levelClue, 1));
        this.addDataSlot(DataSlot.shared(this.levelClue, 2));
        this.player = (org.bukkit.entity.Player) playerInventory.player.getBukkitEntity(); // CraftBukkit
    }

    @Override
    public void slotsChanged(Container inventory) {
        if (inventory == this.enchantSlots) {
            ItemStack item = inventory.getItem(0);
            if (!item.isEmpty()) { // CraftBukkit - relax condition
                this.access.execute((level, pos) -> {
                    IdMap<Holder<Enchantment>> holderIdMap = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).asHolderIdMap();
                    int i1 = 0;

                    for (BlockPos blockPos : EnchantingTableBlock.BOOKSHELF_OFFSETS) {
                        if (EnchantingTableBlock.isValidBookShelf(level, pos, blockPos)) {
                            i1++;
                        }
                    }

                    this.random.setSeed(this.enchantmentSeed.get());

                    for (int i2 = 0; i2 < 3; i2++) {
                        this.costs[i2] = EnchantmentHelper.getEnchantmentCost(this.random, i2, i1, item);
                        this.enchantClue[i2] = -1;
                        this.levelClue[i2] = -1;
                        if (this.costs[i2] < i2 + 1) {
                            this.costs[i2] = 0;
                        }
                    }

                    for (int i2x = 0; i2x < 3; i2x++) {
                        if (this.costs[i2x] > 0) {
                            List<EnchantmentInstance> enchantmentList = this.getEnchantmentList(level.registryAccess(), item, i2x, this.costs[i2x]);
                            if (!enchantmentList.isEmpty()) {
                                EnchantmentInstance enchantmentInstance = enchantmentList.get(this.random.nextInt(enchantmentList.size()));
                                this.enchantClue[i2x] = holderIdMap.getId(enchantmentInstance.enchantment());
                                this.levelClue[i2x] = enchantmentInstance.level();
                            }
                        }
                    }

                    // CraftBukkit start
                    org.bukkit.craftbukkit.inventory.CraftItemStack craftItemStack = org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(item);
                    org.bukkit.enchantments.EnchantmentOffer[] offers = new org.bukkit.enchantments.EnchantmentOffer[3];
                    for (int j = 0; j < 3; ++j) {
                        org.bukkit.enchantments.Enchantment enchantment = (this.enchantClue[j] >= 0) ? org.bukkit.craftbukkit.enchantments.CraftEnchantment.minecraftHolderToBukkit(holderIdMap.byId(this.enchantClue[j])) : null;
                        offers[j] = (enchantment != null) ? new org.bukkit.enchantments.EnchantmentOffer(enchantment, this.levelClue[j], this.costs[j]) : null;
                    }

                    org.bukkit.event.enchantment.PrepareItemEnchantEvent event = new org.bukkit.event.enchantment.PrepareItemEnchantEvent(this.player, this.getBukkitView(), this.access.getLocation().getBlock(), craftItemStack, offers, i1);
                    event.setCancelled(!item.isEnchantable());
                    level.getCraftServer().getPluginManager().callEvent(event);

                    if (event.isCancelled()) {
                        for (int j = 0; j < 3; ++j) {
                            this.costs[j] = 0;
                            this.enchantClue[j] = -1;
                            this.levelClue[j] = -1;
                        }
                        return;
                    }

                    for (int j = 0; j < 3; j++) {
                        org.bukkit.enchantments.EnchantmentOffer offer = event.getOffers()[j];
                        if (offer != null) {
                            this.costs[j] = offer.getCost();
                            this.enchantClue[j] = holderIdMap.getId(org.bukkit.craftbukkit.enchantments.CraftEnchantment
                                .bukkitToMinecraftHolder(offer.getEnchantment()));
                            this.levelClue[j] = offer.getEnchantmentLevel();
                        } else {
                            if (enchantClue[j] != -1) this.costs[j] = 0;
                            this.enchantClue[j] = -1;
                            this.levelClue[j] = -1;
                        }
                    }
                    // CraftBukkit end

                    this.broadcastChanges();
                });
            } else {
                for (int i = 0; i < 3; i++) {
                    this.costs[i] = 0;
                    this.enchantClue[i] = -1;
                    this.levelClue[i] = -1;
                }
            }
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id >= 0 && id < this.costs.length) {
            ItemStack item = this.enchantSlots.getItem(0);
            ItemStack item1 = this.enchantSlots.getItem(1);
            int i = id + 1;
            if ((item1.isEmpty() || item1.getCount() < i) && !player.hasInfiniteMaterials()) {
                return false;
            } else if (this.costs[id] <= 0
                || item.isEmpty()
                || (player.experienceLevel < i || player.experienceLevel < this.costs[id]) && !player.hasInfiniteMaterials()) {
                return false;
            } else {
                this.access.execute((level, pos) -> {
                    ItemStack itemStack = item; // Paper - diff on change
                    List<EnchantmentInstance> enchantmentList = this.getEnchantmentList(level.registryAccess(), item, id, this.costs[id]);
                    // CraftBukkit start
                    IdMap<Holder<Enchantment>> registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).asHolderIdMap();
                    if (true || !enchantmentList.isEmpty()) {
                        // player.onEnchantmentPerformed(item, i); // Moved down
                        java.util.Map<org.bukkit.enchantments.Enchantment, Integer> enchants = new java.util.HashMap<>();
                        for (EnchantmentInstance instance : enchantmentList) {
                            enchants.put(org.bukkit.craftbukkit.enchantments.CraftEnchantment.minecraftHolderToBukkit(instance.enchantment()), instance.level());
                        }
                        org.bukkit.craftbukkit.inventory.CraftItemStack craftItemStack = org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(itemStack);
                        Holder<Enchantment> holder = registry.byId(this.enchantClue[id]);
                        if (holder == null) return;
                        org.bukkit.enchantments.Enchantment hintedEnchantment = org.bukkit.craftbukkit.enchantments.CraftEnchantment.minecraftHolderToBukkit(holder);
                        int hintedEnchantmentLevel = this.levelClue[id];
                        org.bukkit.event.enchantment.EnchantItemEvent event = new org.bukkit.event.enchantment.EnchantItemEvent((org.bukkit.entity.Player) player.getBukkitEntity(), this.getBukkitView(), this.access.getLocation().getBlock(), craftItemStack, this.costs[id], enchants, hintedEnchantment, hintedEnchantmentLevel, id);
                        level.getCraftServer().getPluginManager().callEvent(event);
                        int itemLevel = event.getExpLevelCost();
                        if (event.isCancelled() || (itemLevel > player.experienceLevel && !player.getAbilities().instabuild) || event.getEnchantsToAdd().isEmpty()) {
                            return;
                        }
                        // CraftBukkit end
                        // Paper start
                        itemStack = org.bukkit.craftbukkit.inventory.CraftItemStack.getOrCloneOnMutation(craftItemStack, event.getItem());
                        if (itemStack != item) {
                            this.enchantSlots.setItem(0, itemStack);
                        }
                        if (itemStack.is(Items.BOOK)) {
                            itemStack = itemStack.transmuteCopy(Items.ENCHANTED_BOOK);
                            this.enchantSlots.setItem(0, itemStack);
                        }
                        // Paper end

                        // CraftBukkit start
                        for (java.util.Map.Entry<org.bukkit.enchantments.Enchantment, Integer> entry : event.getEnchantsToAdd().entrySet()) {
                            Holder<Enchantment> enchant = org.bukkit.craftbukkit.enchantments.CraftEnchantment.bukkitToMinecraftHolder(entry.getKey());
                            if (enchant == null) {
                                continue;
                            }

                            itemStack.enchant(enchant, entry.getValue());
                        }
                        // CraftBukkit end
                        player.onEnchantmentPerformed(item, i);

                        // CraftBukkit - TODO: let plugins change this
                        item1.consume(i, player);
                        if (item1.isEmpty()) {
                            this.enchantSlots.setItem(1, ItemStack.EMPTY);
                        }

                        player.awardStat(Stats.ENCHANT_ITEM);
                        if (player instanceof ServerPlayer) {
                            CriteriaTriggers.ENCHANTED_ITEM.trigger((ServerPlayer)player, itemStack, i);
                        }

                        this.enchantSlots.setChanged();
                        this.enchantmentSeed.set(player.getEnchantmentSeed());
                        this.slotsChanged(this.enchantSlots);
                        level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.0F, level.random.nextFloat() * 0.1F + 0.9F);
                    }
                });
                return true;
            }
        } else {
            Util.logAndPauseIfInIde(player.getPlainTextName() + " pressed invalid button id: " + id);
            return false;
        }
    }

    private List<EnchantmentInstance> getEnchantmentList(RegistryAccess registryAccess, ItemStack stack, int slot, int cost) {
        this.random.setSeed(this.enchantmentSeed.get() + slot);
        Optional<HolderSet.Named<Enchantment>> optional = registryAccess.lookupOrThrow(Registries.ENCHANTMENT).get(EnchantmentTags.IN_ENCHANTING_TABLE);
        if (optional.isEmpty()) {
            return List.of();
        } else {
            List<EnchantmentInstance> list = EnchantmentHelper.selectEnchantment(this.random, stack, cost, optional.get().stream());
            if (stack.is(Items.BOOK) && list.size() > 1) {
                list.remove(this.random.nextInt(list.size()));
            }

            return list;
        }
    }

    public int getGoldCount() {
        ItemStack item = this.enchantSlots.getItem(1);
        return item.isEmpty() ? 0 : item.getCount();
    }

    // Paper start - add enchantment seed update API
    public void setEnchantmentSeed(int seed) {
        this.enchantmentSeed.set(seed);
    }
    // Paper end - add enchantment seed update API

    public int getEnchantmentSeed() {
        return this.enchantmentSeed.get();
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.access.execute((level, pos) -> this.clearContainer(player, this.enchantSlots));
    }

    @Override
    public boolean stillValid(Player player) {
        if (!this.checkReachable) return true; // CraftBukkit
        return stillValid(this.access, player, Blocks.ENCHANTING_TABLE);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack itemStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(slotIndex);
        if (slot != null && slot.hasItem()) {
            ItemStack item = slot.getItem();
            itemStack = item.copy();
            if (slotIndex == 0) {
                if (!this.moveItemStackTo(item, 2, 38, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (slotIndex == 1) {
                if (!this.moveItemStackTo(item, 2, 38, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (item.is(Items.LAPIS_LAZULI)) {
                if (!this.moveItemStackTo(item, 1, 2, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (this.slots.get(0).hasItem() || !this.slots.get(0).mayPlace(item)) {
                    return ItemStack.EMPTY;
                }

                ItemStack itemStack1 = item.copyWithCount(1);
                item.shrink(1);
                this.slots.get(0).setByPlayer(itemStack1);
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

    // CraftBukkit start
    @Override
    public org.bukkit.craftbukkit.inventory.view.CraftEnchantmentView getBukkitView() {
        if (this.view != null) {
            return this.view;
        }

        org.bukkit.craftbukkit.inventory.CraftInventoryEnchanting inventory = new org.bukkit.craftbukkit.inventory.CraftInventoryEnchanting(this.enchantSlots);
        this.view = new org.bukkit.craftbukkit.inventory.view.CraftEnchantmentView(this.player, inventory, this);
        return this.view;
    }
    // CraftBukkit end
}
