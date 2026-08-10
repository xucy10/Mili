package net.minecraft.world.entity.monster.skeleton;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

public class Stray extends AbstractSkeleton {
    public Stray(EntityType<? extends Stray> type, Level level) {
        super(type, level);
    }

    public static boolean checkStraySpawnRules(
        EntityType<Stray> entityType, ServerLevelAccessor level, EntitySpawnReason spawnReason, BlockPos pos, RandomSource random
    ) {
        BlockPos blockPos = pos;

        do {
            blockPos = blockPos.above();
        } while (level.getBlockState(blockPos).is(Blocks.POWDER_SNOW));

        return Monster.checkMonsterSpawnRules(entityType, level, spawnReason, pos, random)
            && (EntitySpawnReason.isSpawner(spawnReason) || level.canSeeSky(blockPos.below()));
    }

    @Override
    public SoundEvent getAmbientSound() {
        return SoundEvents.STRAY_AMBIENT;
    }

    @Override
    public SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.STRAY_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.STRAY_DEATH;
    }

    @Override
    SoundEvent getStepSound() {
        return SoundEvents.STRAY_STEP;
    }

    @Override
    protected AbstractArrow getArrow(ItemStack arrow, float velocity, @Nullable ItemStack weapon) {
        AbstractArrow abstractArrow = super.getArrow(arrow, velocity, weapon);
        if (abstractArrow instanceof Arrow) {
            ((Arrow)abstractArrow).addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 600));
        }

        return abstractArrow;
    }
}
