package net.minecraft.world.entity.monster.skeleton;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Shearable;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import org.jspecify.annotations.Nullable;

public class Bogged extends AbstractSkeleton implements Shearable {
    private static final EntityDataAccessor<Boolean> DATA_SHEARED = SynchedEntityData.defineId(Bogged.class, EntityDataSerializers.BOOLEAN);
    private static final String SHEARED_TAG_NAME = "sheared";
    private static final boolean DEFAULT_SHEARED = false;

    public static AttributeSupplier.Builder createAttributes() {
        return AbstractSkeleton.createAttributes().add(Attributes.MAX_HEALTH, 16.0);
    }

    public Bogged(EntityType<? extends Bogged> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SHEARED, false);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("sheared", this.isSheared());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setSheared(input.getBooleanOr("sheared", false));
    }

    public boolean isSheared() {
        return this.entityData.get(DATA_SHEARED);
    }

    public void setSheared(boolean sheared) {
        this.entityData.set(DATA_SHEARED, sheared);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemInHand = player.getItemInHand(hand);
        if (itemInHand.is(Items.SHEARS) && this.readyForShearing()) {
            if (this.level() instanceof ServerLevel serverLevel) {
                // CraftBukkit start
                // Paper start - custom shear drops
                java.util.List<ItemStack> drops = this.generateDefaultDrops(serverLevel, itemInHand);
                org.bukkit.event.player.PlayerShearEntityEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.handlePlayerShearEntityEvent(player, this, itemInHand, hand, drops);
                if (event != null) {
                    if (event.isCancelled()) {
                        return InteractionResult.PASS;
                    }
                    drops = org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(event.getDrops());
                    // Paper end - custom shear drops
                }
                // CraftBukkit end
                this.shear(serverLevel, SoundSource.PLAYERS, itemInHand, drops); // Paper - custom shear drops
                this.gameEvent(GameEvent.SHEAR, player);
                itemInHand.hurtAndBreak(1, player, hand.asEquipmentSlot());
            }

            return InteractionResult.SUCCESS;
        } else {
            return super.mobInteract(player, hand);
        }
    }

    @Override
    public SoundEvent getAmbientSound() {
        return SoundEvents.BOGGED_AMBIENT;
    }

    @Override
    public SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.BOGGED_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.BOGGED_DEATH;
    }

    @Override
    protected SoundEvent getStepSound() {
        return SoundEvents.BOGGED_STEP;
    }

    @Override
    protected AbstractArrow getArrow(ItemStack arrow, float velocity, @Nullable ItemStack weapon) {
        AbstractArrow abstractArrow = super.getArrow(arrow, velocity, weapon);
        if (abstractArrow instanceof Arrow arrow1) {
            arrow1.addEffect(new MobEffectInstance(MobEffects.POISON, 100));
        }

        return abstractArrow;
    }

    @Override
    protected int getHardAttackInterval() {
        return 50;
    }

    @Override
    protected int getAttackInterval() {
        return 70;
    }

    @Override
    public void shear(ServerLevel level, SoundSource source, ItemStack shears) {
        // Paper start - custom shear drops
        this.shear(level, source, shears, this.generateDefaultDrops(level, shears));
    }

    @Override
    public java.util.List<ItemStack> generateDefaultDrops(final ServerLevel level, final ItemStack shears) {
        final java.util.List<ItemStack> drops = new it.unimi.dsi.fastutil.objects.ObjectArrayList<>();
        this.dropFromShearingLootTable(level, BuiltInLootTables.BOGGED_SHEAR, shears, (ignored, stack) -> {
            drops.add(stack);
        });
        return drops;
    }

    @Override
    public void shear(ServerLevel level, SoundSource source, ItemStack shears, java.util.List<ItemStack> drops) {
        // Paper end - custom shear drops
        level.playSound(null, this, SoundEvents.BOGGED_SHEAR, source, 1.0F, 1.0F);
        this.spawnShearedMushrooms(level, shears, drops); // Paper - custom shear drops
        this.setSheared(true);
    }

    // Paper start - custom shear drops
    private void spawnShearedMushrooms(ServerLevel level, ItemStack stack, java.util.List<ItemStack> drops) {
        this.forceDrops = true; // Paper - Add missing forceDrop toggles
        drops.forEach(drop -> this.spawnAtLocation(level, drop, this.getBbHeight()));
        this.forceDrops = false; // Paper - Add missing forceDrop toggles
        // Paper end - custom shear drops
    }

    @Override
    public boolean readyForShearing() {
        return !this.isSheared() && this.isAlive();
    }
}
