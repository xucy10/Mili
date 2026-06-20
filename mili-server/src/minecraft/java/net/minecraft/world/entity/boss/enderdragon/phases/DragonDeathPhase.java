package net.minecraft.world.entity.boss.enderdragon.phases;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.EndPodiumFeature;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class DragonDeathPhase extends AbstractDragonPhaseInstance {
    private @Nullable Vec3 targetLocation;
    private int time;

    public DragonDeathPhase(EnderDragon dragon) {
        super(dragon);
    }

    @Override
    public void doClientTick() {
        if (this.time++ % 10 == 0) {
            float f = (this.dragon.getRandom().nextFloat() - 0.5F) * 8.0F;
            float f1 = (this.dragon.getRandom().nextFloat() - 0.5F) * 4.0F;
            float f2 = (this.dragon.getRandom().nextFloat() - 0.5F) * 8.0F;
            this.dragon
                .level()
                .addParticle(ParticleTypes.EXPLOSION_EMITTER, this.dragon.getX() + f, this.dragon.getY() + 2.0 + f1, this.dragon.getZ() + f2, 0.0, 0.0, 0.0);
        }
    }

    @Override
    public void doServerTick(ServerLevel level) {
        this.time++;
        if (this.targetLocation == null) {
            BlockPos heightmapPos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, this.dragon.getPodium()); // Paper - Allow changing the EnderDragon podium
            this.targetLocation = Vec3.atBottomCenterOf(heightmapPos);
        }

        double d = this.targetLocation.distanceToSqr(this.dragon.getX(), this.dragon.getY(), this.dragon.getZ());
        if (!(d < 100.0) && !(d > 22500.0) && !this.dragon.horizontalCollision && !this.dragon.verticalCollision) {
            this.dragon.setHealth(1.0F);
        } else {
            this.dragon.setHealth(0.0F);
        }
    }

    @Override
    public void begin() {
        this.targetLocation = null;
        this.time = 0;
    }

    @Override
    public float getFlySpeed() {
        return 3.0F;
    }

    @Override
    public @Nullable Vec3 getFlyTargetLocation() {
        return this.targetLocation;
    }

    @Override
    public EnderDragonPhase<DragonDeathPhase> getPhase() {
        return EnderDragonPhase.DYING;
    }
}
