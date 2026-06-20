package net.minecraft.world.inventory;

import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

public class BeaconMenu extends AbstractContainerMenu {
    private static final int PAYMENT_SLOT = 0;
    private static final int SLOT_COUNT = 1;
    private static final int DATA_COUNT = 3;
    private static final int INV_SLOT_START = 1;
    private static final int INV_SLOT_END = 28;
    private static final int USE_ROW_SLOT_START = 28;
    private static final int USE_ROW_SLOT_END = 37;
    private static final int NO_EFFECT = 0;
    private final Container beacon; // Paper - Add missing InventoryHolders Move down
    private final PaymentSlot paymentSlot;
    private final ContainerLevelAccess access;
    private final ContainerData beaconData;
    // CraftBukkit start
    private org.bukkit.craftbukkit.inventory.view.@Nullable CraftBeaconView view = null;
    private final net.minecraft.world.entity.player.Inventory inventory;
    // CraftBukkit end

    public BeaconMenu(int containerId, Container container) {
        this(containerId, container, new SimpleContainerData(3), ContainerLevelAccess.NULL);
    }

    public BeaconMenu(int containerId, Container container, ContainerData beaconData, ContainerLevelAccess access) {
        super(MenuType.BEACON, containerId);
        this.inventory = (net.minecraft.world.entity.player.Inventory) container; // CraftBukkit - TODO: check this
        // Paper - Add missing InventoryHolders
        this.beacon = new SimpleContainer(this.createBlockHolder(access), 1) {
            @Override
            public boolean canPlaceItem(int slot, ItemStack stack) {
                return stack.is(ItemTags.BEACON_PAYMENT_ITEMS);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }

            @Override
            public org.bukkit.Location getLocation() {
                return access.getLocation();
            }
        };
        // Paper end
        checkContainerDataCount(beaconData, 3);
        this.beaconData = beaconData;
        this.access = access;
        this.paymentSlot = new BeaconMenu.PaymentSlot(this.beacon, 0, 136, 110);
        this.addSlot(this.paymentSlot);
        this.addDataSlots(beaconData);
        this.addStandardInventorySlots(container, 36, 137);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide()) {
            ItemStack itemStack = this.paymentSlot.remove(this.paymentSlot.getMaxStackSize());
            if (!itemStack.isEmpty()) {
                player.drop(itemStack, false);
            }
        }
    }

    @Override
    public boolean stillValid(Player player) {
        if (!this.checkReachable) return true; // CraftBukkit
        return stillValid(this.access, player, Blocks.BEACON);
    }

    @Override
    public void setData(int id, int data) {
        super.setData(id, data);
        this.broadcastChanges();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack itemStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(slotIndex);
        if (slot != null && slot.hasItem()) {
            ItemStack item = slot.getItem();
            itemStack = item.copy();
            if (slotIndex == 0) {
                if (!this.moveItemStackTo(item, 1, 37, true)) {
                    return ItemStack.EMPTY;
                }

                slot.onQuickCraft(item, itemStack);
            } else if (!this.paymentSlot.hasItem() && this.paymentSlot.mayPlace(item) && item.getCount() == 1) {
                if (!this.moveItemStackTo(item, 0, 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (slotIndex >= 1 && slotIndex < 28) {
                if (!this.moveItemStackTo(item, 28, 37, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (slotIndex >= 28 && slotIndex < 37) {
                if (!this.moveItemStackTo(item, 1, 28, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(item, 1, 37, false)) {
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

    public int getLevels() {
        return this.beaconData.get(0);
    }

    public static int encodeEffect(@Nullable Holder<MobEffect> effect) {
        return effect == null ? 0 : BuiltInRegistries.MOB_EFFECT.asHolderIdMap().getId(effect) + 1;
    }

    public static @Nullable Holder<MobEffect> decodeEffect(int effectId) {
        return effectId == 0 ? null : BuiltInRegistries.MOB_EFFECT.asHolderIdMap().byId(effectId - 1);
    }

    public @Nullable Holder<MobEffect> getPrimaryEffect() {
        return decodeEffect(this.beaconData.get(1));
    }

    public @Nullable Holder<MobEffect> getSecondaryEffect() {
        return decodeEffect(this.beaconData.get(2));
    }
    // Paper start - Add PlayerChangeBeaconEffectEvent
    private static org.bukkit.potion.@Nullable PotionEffectType convert(Optional<Holder<MobEffect>> optionalEffect) {
        return optionalEffect.map(org.bukkit.craftbukkit.potion.CraftPotionEffectType::minecraftHolderToBukkit).orElse(null);
    }
    // Paper end - Add PlayerChangeBeaconEffectEvent

    public void updateEffects(Optional<Holder<MobEffect>> primaryEffect, Optional<Holder<MobEffect>> secondaryEffect) {
        // Paper start - fix MC-174630 - validate secondary power
        if (secondaryEffect.isPresent() && secondaryEffect.get() != net.minecraft.world.effect.MobEffects.REGENERATION && (primaryEffect.isPresent() && secondaryEffect.get() != primaryEffect.get())) {
            secondaryEffect = Optional.empty();
        }
        // Paper end
        if (this.paymentSlot.hasItem()) {
            // Paper start - Add PlayerChangeBeaconEffectEvent
            io.papermc.paper.event.player.PlayerChangeBeaconEffectEvent event = new io.papermc.paper.event.player.PlayerChangeBeaconEffectEvent((org.bukkit.entity.Player) this.inventory.player.getBukkitEntity(), convert(primaryEffect), convert(secondaryEffect), this.access.getLocation().getBlock());
            if (event.callEvent()) {
                // Paper end - Add PlayerChangeBeaconEffectEvent
                this.beaconData.set(1, BeaconMenu.encodeEffect(event.getPrimary() == null ? null : org.bukkit.craftbukkit.potion.CraftPotionEffectType.bukkitToMinecraftHolder(event.getPrimary())));// CraftBukkit - decompile error
                this.beaconData.set(2, BeaconMenu.encodeEffect(event.getSecondary() == null ? null : org.bukkit.craftbukkit.potion.CraftPotionEffectType.bukkitToMinecraftHolder(event.getSecondary())));// CraftBukkit - decompile error
            if (event.willConsumeItem()) { // Paper
            this.paymentSlot.remove(1);
            } // Paper
            this.access.execute(Level::blockEntityChanged);
            } // Paper end - Add PlayerChangeBeaconEffectEvent
        }
    }

    public boolean hasPayment() {
        return !this.beacon.getItem(0).isEmpty();
    }

    static class PaymentSlot extends Slot {
        public PaymentSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.is(ItemTags.BEACON_PAYMENT_ITEMS);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    // CraftBukkit start
    @Override
    public org.bukkit.craftbukkit.inventory.view.CraftBeaconView getBukkitView() {
        if (this.view != null) {
            return this.view;
        }

        org.bukkit.craftbukkit.inventory.CraftInventoryBeacon inventory = new org.bukkit.craftbukkit.inventory.CraftInventoryBeacon(this.beacon);
        this.view = new org.bukkit.craftbukkit.inventory.view.CraftBeaconView(this.inventory.player.getBukkitEntity(), inventory, this);
        return this.view;
    }
    // CraftBukkit end
}
