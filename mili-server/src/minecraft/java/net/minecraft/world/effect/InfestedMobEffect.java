package net.minecraft.world.effect;

import java.util.function.ToIntFunction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

class InfestedMobEffect extends MobEffect {
    private final float chanceToSpawn;
    private final ToIntFunction<RandomSource> spawnedCount;

    protected InfestedMobEffect(MobEffectCategory category, int color, float chanceToSpawn, ToIntFunction<RandomSource> spawnedCount) {
        super(category, color, ParticleTypes.INFESTED);
        this.chanceToSpawn = chanceToSpawn;
        this.spawnedCount = spawnedCount;
    }

    @Override
    public void onMobHurt(ServerLevel level, LivingEntity entity, int amplifier, DamageSource damageSource, float amount) {
        if (entity.getRandom().nextFloat() <= this.chanceToSpawn) {
            int i = this.spawnedCount.applyAsInt(entity.getRandom());

            for (int i1 = 0; i1 < i; i1++) {
                this.spawnSilverfish(level, entity, entity.getX(), entity.getY() + entity.getBbHeight() / 2.0, entity.getZ());
            }
        }
    }

    private void spawnSilverfish(ServerLevel level, LivingEntity entity, double x, double y, double z) {
        Silverfish silverfish = EntityType.SILVERFISH.create(level, EntitySpawnReason.TRIGGERED);
        if (silverfish != null) {
            RandomSource random = entity.getRandom();
            float f = (float) (Math.PI / 2);
            float f1 = Mth.randomBetween(random, (float) (-Math.PI / 2), (float) (Math.PI / 2));
            Vector3f vector3f = entity.getLookAngle().toVector3f().mul(0.3F).mul(1.0F, 1.5F, 1.0F).rotateY(f1);
            silverfish.snapTo(x, y, z, level.getRandom().nextFloat() * 360.0F, 0.0F);
            silverfish.setDeltaMovement(new Vec3(vector3f));
            // CraftBukkit start
            if (!level.addFreshEntity(silverfish, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.POTION_EFFECT)) {
                return;
            }
            // CraftBukkit end
            silverfish.playSound(SoundEvents.SILVERFISH_HURT);
        }
    }
}
