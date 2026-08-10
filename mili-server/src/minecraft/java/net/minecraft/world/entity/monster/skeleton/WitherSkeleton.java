package net.minecraft.world.entity.monster.skeleton;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
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
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.pathfinder.PathType;
import org.jspecify.annotations.Nullable;

public class WitherSkeleton extends AbstractSkeleton {
    public WitherSkeleton(EntityType<? extends WitherSkeleton> type, Level level) {
        super(type, level);
        this.setPathfindingMalus(PathType.LAVA, 8.0F);
    }

    @Override
    protected void registerGoals() {
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractPiglin.class, true));
        super.registerGoals();
    }

    @Override
    public SoundEvent getAmbientSound() {
        return SoundEvents.WITHER_SKELETON_AMBIENT;
    }

    @Override
    public SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.WITHER_SKELETON_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.WITHER_SKELETON_DEATH;
    }

    @Override
    SoundEvent getStepSound() {
        return SoundEvents.WITHER_SKELETON_STEP;
    }

    @Override
    public TagKey<Item> getPreferredWeaponType() {
        return null;
    }

    @Override
    public boolean canHoldItem(ItemStack stack) {
        return !stack.is(ItemTags.WITHER_SKELETON_DISLIKED_WEAPONS) && super.canHoldItem(stack);
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
    }

    @Override
    protected void populateDefaultEquipmentEnchantments(ServerLevelAccessor level, RandomSource random, DifficultyInstance difficulty) {
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(
        ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData spawnGroupData
    ) {
        SpawnGroupData spawnGroupData1 = super.finalizeSpawn(level, difficulty, spawnReason, spawnGroupData);
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(4.0);
        this.reassessWeaponGoal();
        return spawnGroupData1;
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        if (!super.doHurtTarget(level, target)) {
            return false;
        } else {
            if (target instanceof LivingEntity) {
                ((LivingEntity)target).addEffect(new MobEffectInstance(MobEffects.WITHER, 200), this, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.ATTACK); // CraftBukkit
            }

            return true;
        }
    }

    @Override
    protected AbstractArrow getArrow(ItemStack arrow, float velocity, @Nullable ItemStack weapon) {
        AbstractArrow abstractArrow = super.getArrow(arrow, velocity, weapon);
        abstractArrow.igniteForSeconds(100.0F);
        return abstractArrow;
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effectInstance) {
        return (!effectInstance.is(net.minecraft.world.effect.MobEffects.WITHER) || !this.level().paperConfig().entities.mobEffects.immuneToWitherEffect.witherSkeleton) && super.canBeAffected(effectInstance); // Paper - Add config for mobs immune to default effects
    }
}
