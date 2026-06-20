package net.minecraft.world.entity.projectile.throwableitemprojectile;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public abstract class ThrowableItemProjectile extends ThrowableProjectile implements ItemSupplier {
    private static final EntityDataAccessor<ItemStack> DATA_ITEM_STACK = SynchedEntityData.defineId(
        ThrowableItemProjectile.class, EntityDataSerializers.ITEM_STACK
    );

    public ThrowableItemProjectile(EntityType<? extends ThrowableItemProjectile> type, Level level) {
        super(type, level);
    }

    public ThrowableItemProjectile(EntityType<? extends ThrowableItemProjectile> type, double x, double y, double z, Level level, ItemStack item) {
        super(type, x, y, z, level);
        this.setItem(item);
    }

    public ThrowableItemProjectile(EntityType<? extends ThrowableItemProjectile> type, LivingEntity owner, Level level, ItemStack item) {
        this(type, owner.getX(), owner.getEyeY() - 0.1F, owner.getZ(), level, item);
        this.setOwner(owner);
    }

    public void setItem(ItemStack stack) {
        this.getEntityData().set(DATA_ITEM_STACK, stack.copyWithCount(1));
    }

    public abstract Item getDefaultItem();

    @Override
    public ItemStack getItem() {
        return this.getEntityData().get(DATA_ITEM_STACK);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_ITEM_STACK, new ItemStack(this.getDefaultItem()));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store("Item", ItemStack.CODEC, this.getItem());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setItem(input.read("Item", ItemStack.CODEC).orElseGet(() -> new ItemStack(this.getDefaultItem())));
    }
}
