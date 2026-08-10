package net.minecraft.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SeededContainerLoot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jspecify.annotations.Nullable;

public abstract class RandomizableContainerBlockEntity extends BaseContainerBlockEntity implements RandomizableContainer {
    public @Nullable ResourceKey<LootTable> lootTable;
    public long lootTableSeed = 0L;

    protected RandomizableContainerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    @Override
    public @Nullable ResourceKey<LootTable> getLootTable() {
        return this.lootTable;
    }

    @Override
    public void setLootTable(@Nullable ResourceKey<LootTable> lootTable) {
        this.lootTable = lootTable;
    }

    @Override
    public long getLootTableSeed() {
        return this.lootTableSeed;
    }

    @Override
    public void setLootTableSeed(long seed) {
        this.lootTableSeed = seed;
    }

    @Override
    public boolean isEmpty() {
        this.unpackLootTable(null);
        return super.isEmpty();
    }

    @Override
    public ItemStack getItem(int index) {
        if (index == 0) this.unpackLootTable(null); // Paper - Perf: Optimize Hoppers
        return super.getItem(index);
    }

    @Override
    public ItemStack removeItem(int index, int count) {
        this.unpackLootTable(null);
        return super.removeItem(index, count);
    }

    @Override
    public ItemStack removeItemNoUpdate(int index) {
        this.unpackLootTable(null);
        return super.removeItemNoUpdate(index);
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        this.unpackLootTable(null);
        super.setItem(index, stack);
    }

    @Override
    public boolean canOpen(Player player) {
        return super.canOpen(player) && (this.lootTable == null || !player.isSpectator());
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        if (this.canOpen(player)) {
            this.unpackLootTable(playerInventory.player);
            return this.createMenu(containerId, playerInventory);
        } else {
            BaseContainerBlockEntity.sendChestLockedNotifications(this.getBlockPos().getCenter(), player, this.getDisplayName());
            return null;
        }
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter componentGetter) {
        super.applyImplicitComponents(componentGetter);
        SeededContainerLoot seededContainerLoot = componentGetter.get(DataComponents.CONTAINER_LOOT);
        if (seededContainerLoot != null) {
            this.lootTable = seededContainerLoot.lootTable();
            this.lootTableSeed = seededContainerLoot.seed();
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (this.lootTable != null) {
            components.set(DataComponents.CONTAINER_LOOT, new SeededContainerLoot(this.lootTable, this.lootTableSeed));
        }
    }

    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        super.removeComponentsFromTag(output);
        output.discard("LootTable");
        output.discard("LootTableSeed");
    }

    // Paper start - LootTable API
    final com.destroystokyo.paper.loottable.PaperLootableInventoryData lootableData = new com.destroystokyo.paper.loottable.PaperLootableInventoryData(); // Paper

    @Override
    public com.destroystokyo.paper.loottable.PaperLootableInventoryData lootableData() {
        return this.lootableData;
    }
    // Paper end - LootTable API
}
