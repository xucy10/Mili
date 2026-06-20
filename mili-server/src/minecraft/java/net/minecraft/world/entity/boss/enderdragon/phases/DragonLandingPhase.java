package net.minecraft.world.entity.boss.enderdragon.phases;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.PowerParticleOption;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.EndPodiumFeature;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class DragonLandingPhase extends AbstractDragonPhaseInstance {
    private @Nullable Vec3 targetLocation;

    public DragonLandingPhase(EnderDragon dragon) {
        super(dragon);
    }

    @Override
    public void doClientTick() {
        Vec3 vec3 = this.dragon.getHeadLookVector(1.0F).normalize();
        vec3.yRot((float) (-Math.PI / 4));
        double x = this.dragon.head.getX();
        double y = this.dragon.head.getY(0.5);
        double z = this.dragon.head.getZ();

        for (int i = 0; i < 8; i++) {
            RandomSource random = this.dragon.getRandom();
            double d = x + random.nextGaussian() / 2.0;
            double d1 = y + random.nextGaussian() / 2.0;
            double d2 = z + random.nextGaussian() / 2.0;
            Vec3 deltaMovement = this.dragon.getDeltaMovement();
            this.dragon
                .level()
                .addParticle(
                    PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0F),
                    d,
                    d1,
                    d2,
                    -vec3.x * 0.08F + deltaMovement.x,
                    -vec3.y * 0.3F + deltaMovement.y,
                    -vec3.z * 0.08F + deltaMovement.z
                );
            vec3.yRot((float) (Math.PI / 16));
        }
    }

    @Override
    public void doServerTick(ServerLevel level) {
        if (this.targetLocation == null) {
            this.targetLocation = Vec3.atBottomCenterOf(
                level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, this.dragon.getPodium()) // Paper - Allow changing the EnderDragon podium
            );
        }

        if (this.targetLocation.distanceToSqr(this.dragon.getX(), this.dragon.getY(), this.dragon.getZ()) < 1.0) {
            this.dragon.getPhaseManager().getPhase(EnderDragonPhase.SITTING_FLAMING).resetFlameCount();
            this.dragon.getPhaseManager().setPhase(EnderDragonPhase.SITTING_SCANNING);
        }
    }

    @Override
    public float getFlySpeed() {
        return 1.5F;
    }

    @Override
    public float getTurnSpeed() {
        float f = (float)this.dragon.getDeltaMovement().horizontalDistance() + 1.0F;
        float min = Math.min(f, 40.0F);
        return min / f;
    }

    @Override
    public void begin() {
        this.targetLocation = null;
    }

    @Override
    public @Nullable Vec3 getFlyTargetLocation() {
        return this.targetLocation;
    }

    @Override
    public EnderDragonPhase<DragonLandingPhase> getPhase() {
        return EnderDragonPhase.LANDING;
    }
}
