package net.minecraft.world.entity.projectile.arrow;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SpellParticleOption;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class SpectralArrow extends AbstractArrow {
    private static final int DEFAULT_DURATION = 200;
    public int duration = 200;

    public SpectralArrow(EntityType<? extends SpectralArrow> type, Level level) {
        super(type, level);
    }

    public SpectralArrow(Level level, LivingEntity owner, ItemStack pickupItemStack, @Nullable ItemStack firedFromWeapon) {
        super(EntityType.SPECTRAL_ARROW, owner, level, pickupItemStack, firedFromWeapon);
    }

    public SpectralArrow(Level level, double x, double y, double z, ItemStack pickupItemStack, @Nullable ItemStack firedFromWeapon) {
        super(EntityType.SPECTRAL_ARROW, x, y, z, level, pickupItemStack, firedFromWeapon);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide() && !this.isInGround()) {
            this.level().addParticle(SpellParticleOption.create(ParticleTypes.EFFECT, -1, 1.0F), this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
        }
    }

    @Override
    protected void doPostHurtEffects(LivingEntity target) {
        super.doPostHurtEffects(target);
        MobEffectInstance mobEffectInstance = new MobEffectInstance(MobEffects.GLOWING, this.duration, 0);
        target.addEffect(mobEffectInstance, this.getEffectSource(), org.bukkit.event.entity.EntityPotionEffectEvent.Cause.ARROW); // CraftBukkit
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.duration = input.getIntOr("Duration", 200);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("Duration", this.duration);
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(Items.SPECTRAL_ARROW);
    }
}
