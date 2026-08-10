package net.minecraft.world.entity.vehicle.minecart;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.Hopper;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class MinecartHopper extends AbstractMinecartContainer implements Hopper {
    private static final boolean DEFAULT_ENABLED = true;
    private boolean enabled = true;
    private boolean consumedItemThisFrame = false;

    public MinecartHopper(EntityType<? extends MinecartHopper> type, Level level) {
        super(type, level);
    }

    @Override
    public BlockState getDefaultDisplayBlockState() {
        return Blocks.HOPPER.defaultBlockState();
    }

    @Override
    public int getDefaultDisplayOffset() {
        return 1;
    }

    @Override
    public int getContainerSize() {
        return 5;
    }

    @Override
    public void activateMinecart(ServerLevel level, int x, int y, int z, boolean receivingPower) {
        boolean flag = !receivingPower;
        if (flag != this.isEnabled()) {
            this.setEnabled(flag);
        }
        this.immunize(); // Paper
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public double getLevelX() {
        return this.getX();
    }

    @Override
    public double getLevelY() {
        return this.getY() + 0.5;
    }

    @Override
    public double getLevelZ() {
        return this.getZ();
    }

    @Override
    public boolean isGridAligned() {
        return false;
    }

    @Override
    public void tick() {
        this.consumedItemThisFrame = false;
        super.tick();
        this.tryConsumeItems();
    }

    @Override
    protected double makeStepAlongTrack(BlockPos pos, RailShape railShape, double speed) {
        double d = super.makeStepAlongTrack(pos, railShape, speed);
        this.tryConsumeItems();
        return d;
    }

    private void tryConsumeItems() {
        if (!this.level().isClientSide() && this.isAlive() && this.isEnabled() && !this.consumedItemThisFrame && this.suckInItems()) {
            this.consumedItemThisFrame = true;
            this.setChanged();
        }
    }

    // Mili start - tick every
    @Override
    public void inactiveTick() {
        this.tick();
    }
    // Mili end - tick every

    public boolean suckInItems() {
        if (HopperBlockEntity.suckInItems(this.level(), this)) {
            this.immunize(); // Paper
            return true;
        } else {
            for (ItemEntity itemEntity : this.level()
                .getEntitiesOfClass(ItemEntity.class, this.getBoundingBox().inflate(0.25, 0.0, 0.25), EntitySelector.ENTITY_STILL_ALIVE)) {
                if (HopperBlockEntity.addItem(this, itemEntity)) {
                    this.immunize(); // Paper
                    return true;
                }
            }

            return false;
        }
    }

    @Override
    public Item getDropItem() {
        return Items.HOPPER_MINECART;
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(Items.HOPPER_MINECART);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("Enabled", this.enabled);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.enabled = input.getBooleanOr("Enabled", true);
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory) {
        return new HopperMenu(id, playerInventory, this);
    }

    // Paper start
    public void immunize() {
        this.activatedImmunityTick = Math.max(this.activatedImmunityTick, io.papermc.paper.threadedregions.RegionizedServer.getCurrentTick() + 20);
    }
    // Paper end

}
