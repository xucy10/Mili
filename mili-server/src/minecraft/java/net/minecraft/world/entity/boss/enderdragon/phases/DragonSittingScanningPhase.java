package net.minecraft.world.entity.boss.enderdragon.phases;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.phys.Vec3;

public class DragonSittingScanningPhase extends AbstractDragonSittingPhase {
    private static final int SITTING_SCANNING_IDLE_TICKS = 100;
    private static final int SITTING_ATTACK_Y_VIEW_RANGE = 10;
    private static final int SITTING_ATTACK_VIEW_RANGE = 20;
    private static final int SITTING_CHARGE_VIEW_RANGE = 150;
    private static final TargetingConditions CHARGE_TARGETING = TargetingConditions.forCombat().range(150.0);
    private final TargetingConditions scanTargeting;
    private int scanningTime;

    public DragonSittingScanningPhase(EnderDragon dragon) {
        super(dragon);
        this.scanTargeting = TargetingConditions.forCombat().range(20.0).selector((entity, level) -> Math.abs(entity.getY() - dragon.getY()) <= 10.0);
    }

    @Override
    public void doServerTick(ServerLevel level) {
        this.scanningTime++;
        LivingEntity nearestPlayer = level.getNearestPlayer(this.scanTargeting, this.dragon, this.dragon.getX(), this.dragon.getY(), this.dragon.getZ());
        if (nearestPlayer != null) {
            if (this.scanningTime > 25) {
                this.dragon.getPhaseManager().setPhase(EnderDragonPhase.SITTING_ATTACKING);
            } else {
                Vec3 vec3 = new Vec3(nearestPlayer.getX() - this.dragon.getX(), 0.0, nearestPlayer.getZ() - this.dragon.getZ()).normalize();
                Vec3 vec31 = new Vec3(
                        Mth.sin(this.dragon.getYRot() * (float) (Math.PI / 180.0)), 0.0, -Mth.cos(this.dragon.getYRot() * (float) (Math.PI / 180.0))
                    )
                    .normalize();
                float f = (float)vec31.dot(vec3);
                float f1 = (float)(Math.acos(f) * 180.0F / (float)Math.PI) + 0.5F;
                if (f1 < 0.0F || f1 > 10.0F) {
                    double d = nearestPlayer.getX() - this.dragon.head.getX();
                    double d1 = nearestPlayer.getZ() - this.dragon.head.getZ();
                    double d2 = Mth.clamp(Mth.wrapDegrees(180.0 - Mth.atan2(d, d1) * 180.0F / (float)Math.PI - this.dragon.getYRot()), -100.0, 100.0);
                    this.dragon.yRotA *= 0.8F;
                    float f2 = (float)Math.sqrt(d * d + d1 * d1) + 1.0F;
                    float f3 = f2;
                    if (f2 > 40.0F) {
                        f2 = 40.0F;
                    }

                    this.dragon.yRotA += (float)d2 * (0.7F / f2 / f3);
                    this.dragon.setYRot(this.dragon.getYRot() + this.dragon.yRotA);
                }
            }
        } else if (this.scanningTime >= 100) {
            nearestPlayer = level.getNearestPlayer(CHARGE_TARGETING, this.dragon, this.dragon.getX(), this.dragon.getY(), this.dragon.getZ());
            this.dragon.getPhaseManager().setPhase(EnderDragonPhase.TAKEOFF);
            if (nearestPlayer != null) {
                this.dragon.getPhaseManager().setPhase(EnderDragonPhase.CHARGING_PLAYER);
                this.dragon
                    .getPhaseManager()
                    .getPhase(EnderDragonPhase.CHARGING_PLAYER)
                    .setTarget(new Vec3(nearestPlayer.getX(), nearestPlayer.getY(), nearestPlayer.getZ()));
            }
        }
    }

    @Override
    public void begin() {
        this.scanningTime = 0;
    }

    @Override
    public EnderDragonPhase<DragonSittingScanningPhase> getPhase() {
        return EnderDragonPhase.SITTING_SCANNING;
    }
}
