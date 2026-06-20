package net.minecraft.world.entity.projectile.arrow;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public class Arrow extends AbstractArrow {
    private static final int EXPOSED_POTION_DECAY_TIME = 600;
    public static final int NO_EFFECT_COLOR = -1;
    private static final EntityDataAccessor<Integer> ID_EFFECT_COLOR = SynchedEntityData.defineId(Arrow.class, EntityDataSerializers.INT);
    private static final byte EVENT_POTION_PUFF = 0;

    public Arrow(EntityType<? extends Arrow> type, Level level) {
        super(type, level);
    }

    public Arrow(Level level, double x, double y, double z, ItemStack pickupItemStack, @Nullable ItemStack firedFromWeapon) {
        super(EntityType.ARROW, x, y, z, level, pickupItemStack, firedFromWeapon);
        this.updateColor();
    }

    public Arrow(Level level, LivingEntity owner, ItemStack pickupItemStack, @Nullable ItemStack firedFromWeapon) {
        super(EntityType.ARROW, owner, level, pickupItemStack, firedFromWeapon);
        this.updateColor();
    }

    public PotionContents getPotionContents() {
        return this.getPickupItemStackOrigin().getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
    }

    private float getPotionDurationScale() {
        return this.getPickupItemStackOrigin().getOrDefault(DataComponents.POTION_DURATION_SCALE, 1.0F);
    }

    public void setPotionContents(PotionContents potionContents) {
        this.getPickupItemStackOrigin().set(DataComponents.POTION_CONTENTS, potionContents);
        this.updateColor();
    }

    @Override
    public void setPickupItemStack(ItemStack pickupItemStack) {
        super.setPickupItemStack(pickupItemStack);
        this.updateColor();
    }

    public void updateColor() {
        PotionContents potionContents = this.getPotionContents();
        this.entityData.set(ID_EFFECT_COLOR, potionContents.equals(PotionContents.EMPTY) ? -1 : potionContents.getColor());
    }

    public void addEffect(MobEffectInstance effectInstance) {
        this.setPotionContents(this.getPotionContents().withEffectAdded(effectInstance));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ID_EFFECT_COLOR, -1);
    }

    // Paper start - EAR 2
    @Override
    public void inactiveTick() {
        if (this.isInGround()) {
            this.life++;
        }
        super.inactiveTick();
    }
    // Paper end

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            if (this.isInGround()) {
                if (this.inGroundTime % 5 == 0) {
                    this.makeParticle(1);
                }
            } else {
                this.makeParticle(2);
            }
        } else if (this.isInGround() && this.inGroundTime != 0 && !this.getPotionContents().equals(PotionContents.EMPTY) && this.inGroundTime >= 600) {
            this.level().broadcastEntityEvent(this, EVENT_POTION_PUFF);
            this.setPickupItemStack(new ItemStack(Items.ARROW));
        }
    }

    private void makeParticle(int particleAmount) {
        int color = this.getColor();
        if (color != -1 && particleAmount > 0) {
            for (int i = 0; i < particleAmount; i++) {
                this.level()
                    .addParticle(
                        ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, color),
                        this.getRandomX(0.5),
                        this.getRandomY(),
                        this.getRandomZ(0.5),
                        0.0,
                        0.0,
                        0.0
                    );
            }
        }
    }

    public int getColor() {
        return this.entityData.get(ID_EFFECT_COLOR);
    }

    @Override
    protected void doPostHurtEffects(LivingEntity target) {
        super.doPostHurtEffects(target);
        Entity effectSource = this.getEffectSource();
        PotionContents potionContents = this.getPotionContents();
        float potionDurationScale = this.getPotionDurationScale();
        potionContents.forEachEffect(mobEffectInstance -> target.addEffect(mobEffectInstance, effectSource, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.ARROW), potionDurationScale); // CraftBukkit
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(Items.ARROW);
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 0) {
            int color = this.getColor();
            if (color != -1) {
                float f = (color >> 16 & 0xFF) / 255.0F;
                float f1 = (color >> 8 & 0xFF) / 255.0F;
                float f2 = (color >> 0 & 0xFF) / 255.0F;

                for (int i = 0; i < 20; i++) {
                    this.level()
                        .addParticle(
                            ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, f, f1, f2),
                            this.getRandomX(0.5),
                            this.getRandomY(),
                            this.getRandomZ(0.5),
                            0.0,
                            0.0,
                            0.0
                        );
                }
            }
        } else {
            super.handleEntityEvent(id);
        }
    }
}
