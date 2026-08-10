package net.minecraft.world.effect;

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;

class OozingMobEffect extends MobEffect {
    private static final int RADIUS_TO_CHECK_SLIMES = 2;
    public static final int SLIME_SIZE = 2;
    private final ToIntFunction<RandomSource> spawnedCount;

    protected OozingMobEffect(MobEffectCategory category, int color, ToIntFunction<RandomSource> spawnedCount) {
        super(category, color, ParticleTypes.ITEM_SLIME);
        this.spawnedCount = spawnedCount;
    }

    @VisibleForTesting
    protected static int numberOfSlimesToSpawn(int maxEntityCramming, OozingMobEffect.NearbySlimes nearbySlimes, int spawnCount) {
        return maxEntityCramming < 1 ? spawnCount : Mth.clamp(0, maxEntityCramming - nearbySlimes.count(maxEntityCramming), spawnCount);
    }

    @Override
    public void onMobRemoved(ServerLevel level, LivingEntity entity, int amplifier, Entity.RemovalReason reason) {
        if (reason == Entity.RemovalReason.KILLED) {
            int i = this.spawnedCount.applyAsInt(entity.getRandom());
            int i1 = level.getGameRules().get(GameRules.MAX_ENTITY_CRAMMING);
            int i2 = numberOfSlimesToSpawn(i1, OozingMobEffect.NearbySlimes.closeTo(entity), i);

            for (int i3 = 0; i3 < i2; i3++) {
                this.spawnSlimeOffspring(entity.level(), entity.getX(), entity.getY() + 0.5, entity.getZ());
            }
        }
    }

    private void spawnSlimeOffspring(Level level, double x, double y, double z) {
        Slime slime = EntityType.SLIME.create(level, EntitySpawnReason.TRIGGERED);
        if (slime != null) {
            slime.setSize(2, true);
            slime.snapTo(x, y, z, level.getRandom().nextFloat() * 360.0F, 0.0F);
            level.addFreshEntity(slime, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.POTION_EFFECT); // CraftBukkit
        }
    }

    @FunctionalInterface
    protected interface NearbySlimes {
        int count(int maxEntityCramming);

        static OozingMobEffect.NearbySlimes closeTo(LivingEntity entity) {
            return maxEntityCramming -> {
                List<Slime> list = new ArrayList<>();
                entity.level().getEntities(EntityType.SLIME, entity.getBoundingBox().inflate(2.0), slime -> slime != entity, list, maxEntityCramming);
                return list.size();
            };
        }
    }
}
