package net.minecraft.world.entity.monster;

import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.gamerules.GameRules;

public abstract class Monster extends PathfinderMob implements Enemy {
    protected Monster(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 5;
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.HOSTILE;
    }

    @Override
    public void aiStep() {
        this.updateSwingTime();
        this.updateNoActionTime();
        super.aiStep();
    }

    protected void updateNoActionTime() {
        float lightLevelDependentMagicValue = this.getLightLevelDependentMagicValue();
        if (lightLevelDependentMagicValue > 0.5F) {
            this.noActionTime += 2;
        }
    }

    @Override
    public SoundEvent getSwimSound() {
        return SoundEvents.HOSTILE_SWIM;
    }

    @Override
    public SoundEvent getSwimSplashSound() {
        return SoundEvents.HOSTILE_SPLASH;
    }

    @Override
    public SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.HOSTILE_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.HOSTILE_DEATH;
    }

    @Override
    public LivingEntity.Fallsounds getFallSounds() {
        return new LivingEntity.Fallsounds(SoundEvents.HOSTILE_SMALL_FALL, SoundEvents.HOSTILE_BIG_FALL);
    }

    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        return -level.getPathfindingCostFromLightLevels(pos);
    }

    public static boolean isDarkEnoughToSpawn(ServerLevelAccessor level, BlockPos pos, RandomSource random) {
        if (level.getBrightness(LightLayer.SKY, pos) > random.nextInt(32)) {
            return false;
        } else {
            DimensionType dimensionType = level.dimensionType();
            int i = level.getLevel().paperConfig().entities.spawning.monsterSpawnMaxLightLevel.or(dimensionType.monsterSpawnBlockLightLimit()); // Paper - Configurable max block light for monster spawning
            if (i < 15 && level.getBrightness(LightLayer.BLOCK, pos) > i) {
                return false;
            } else {
                int i1 = level.getLevel().isThundering() ? level.getMaxLocalRawBrightness(pos, 10) : level.getMaxLocalRawBrightness(pos);
                return i1 <= dimensionType.monsterSpawnLightTest().sample(random);
            }
        }
    }

    public static boolean checkMonsterSpawnRules(
        EntityType<? extends Mob> entityType, ServerLevelAccessor level, EntitySpawnReason spawnReason, BlockPos pos, RandomSource random
    ) {
        return level.getDifficulty() != Difficulty.PEACEFUL
            && (EntitySpawnReason.ignoresLightRequirements(spawnReason) || isDarkEnoughToSpawn(level, pos, random))
            && checkMobSpawnRules(entityType, level, spawnReason, pos, random);
    }

    public static boolean checkAnyLightMonsterSpawnRules(
        EntityType<? extends Monster> entityType, LevelAccessor level, EntitySpawnReason spawnReason, BlockPos pos, RandomSource random
    ) {
        return level.getDifficulty() != Difficulty.PEACEFUL && checkMobSpawnRules(entityType, level, spawnReason, pos, random);
    }

    public static boolean checkSurfaceMonstersSpawnRules(
        EntityType<? extends Mob> entityType, ServerLevelAccessor level, EntitySpawnReason spawnReason, BlockPos pos, RandomSource random
    ) {
        return checkMonsterSpawnRules(entityType, level, spawnReason, pos, random) && (EntitySpawnReason.isSpawner(spawnReason) || level.canSeeSky(pos));
    }

    public static AttributeSupplier.Builder createMonsterAttributes() {
        return Mob.createMobAttributes().add(Attributes.ATTACK_DAMAGE);
    }

    @Override
    public boolean shouldDropExperience() {
        return true;
    }

    @Override
    protected boolean shouldDropLoot(ServerLevel level) {
        return level.getGameRules().get(GameRules.MOB_DROPS);
    }

    public boolean isPreventingPlayerRest(ServerLevel level, Player player) {
        return true;
    }

    @Override
    public ItemStack getProjectile(ItemStack shootable) {
        if (shootable.getItem() instanceof ProjectileWeaponItem) {
            Predicate<ItemStack> supportedHeldProjectiles = ((ProjectileWeaponItem)shootable.getItem()).getSupportedHeldProjectiles();
            ItemStack heldProjectile = ProjectileWeaponItem.getHeldProjectile(this, supportedHeldProjectiles);
            return heldProjectile.isEmpty() ? new ItemStack(Items.ARROW) : heldProjectile;
        } else {
            return ItemStack.EMPTY;
        }
    }
}
