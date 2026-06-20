package net.minecraft.world.entity.monster.zombie;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.animal.camel.CamelHusk;
import net.minecraft.world.entity.monster.skeleton.Parched;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.LevelEvent;
import org.jspecify.annotations.Nullable;

public class Husk extends Zombie {
    public Husk(EntityType<? extends Husk> type, Level level) {
        super(type, level);
    }

    @Override
    public boolean isSunSensitive() {
        return false;
    }

    @Override
    public SoundEvent getAmbientSound() {
        return SoundEvents.HUSK_AMBIENT;
    }

    @Override
    public SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.HUSK_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.HUSK_DEATH;
    }

    @Override
    protected SoundEvent getStepSound() {
        return SoundEvents.HUSK_STEP;
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean flag = super.doHurtTarget(level, target);
        if (flag && this.getMainHandItem().isEmpty() && target instanceof LivingEntity) {
            float effectiveDifficulty = level.getCurrentDifficultyAt(this.blockPosition()).getEffectiveDifficulty();
            ((LivingEntity)target).addEffect(new MobEffectInstance(MobEffects.HUNGER, 140 * (int)effectiveDifficulty), this, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.ATTACK); // CraftBukkit
        }

        return flag;
    }

    @Override
    protected boolean convertsInWater() {
        return true;
    }

    @Override
    protected void doUnderWaterConversion(ServerLevel level) {
        this.convertToZombieType(level, EntityType.ZOMBIE);
        if (!this.isSilent()) {
            level.levelEvent(null, LevelEvent.SOUND_HUSK_TO_ZOMBIE, this.blockPosition(), 0);
        }
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(
        ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData spawnGroupData
    ) {
        RandomSource random = level.getRandom();
        spawnGroupData = super.finalizeSpawn(level, difficulty, spawnReason, spawnGroupData);
        float specialMultiplier = difficulty.getSpecialMultiplier();
        if (spawnReason != EntitySpawnReason.CONVERSION) {
            this.setCanPickUpLoot(level.getLevel().paperConfig().entities.behavior.mobsCanAlwaysPickUpLoot.zombies || random.nextFloat() < 0.55F * specialMultiplier); // Paper - Add world settings for mobs picking up loot
        }

        if (spawnGroupData != null) {
            spawnGroupData = new Husk.HuskGroupData((Zombie.ZombieGroupData)spawnGroupData);
            ((Husk.HuskGroupData)spawnGroupData).triedToSpawnCamelHusk = spawnReason != EntitySpawnReason.NATURAL;
        }

        if (spawnGroupData instanceof Husk.HuskGroupData huskGroupData && !huskGroupData.triedToSpawnCamelHusk) {
            BlockPos blockPos = this.blockPosition();
            if (level.noCollision(EntityType.CAMEL_HUSK.getSpawnAABB(blockPos.getX() + 0.5, blockPos.getY(), blockPos.getZ() + 0.5))) {
                huskGroupData.triedToSpawnCamelHusk = true;
                if (random.nextFloat() < 0.1F) {
                    this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SPEAR));
                    CamelHusk camelHusk = EntityType.CAMEL_HUSK.create(this.level(), EntitySpawnReason.NATURAL);
                    if (camelHusk != null) {
                        camelHusk.setPos(this.getX(), this.getY(), this.getZ());
                        camelHusk.finalizeSpawn(level, difficulty, spawnReason, null);
                        this.startRiding(camelHusk, true, true);
                        level.addFreshEntity(camelHusk);
                        Parched parched = EntityType.PARCHED.create(this.level(), EntitySpawnReason.NATURAL);
                        if (parched != null) {
                            parched.snapTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), 0.0F);
                            parched.finalizeSpawn(level, difficulty, spawnReason, null);
                            parched.startRiding(camelHusk, false, false);
                            level.addFreshEntityWithPassengers(parched);
                        }
                    }
                }
            }
        }

        return spawnGroupData;
    }

    public static class HuskGroupData extends Zombie.ZombieGroupData {
        public boolean triedToSpawnCamelHusk = false;

        public HuskGroupData(Zombie.ZombieGroupData zombieGroupData) {
            super(zombieGroupData.isBaby, zombieGroupData.canSpawnJockey);
        }
    }
}
