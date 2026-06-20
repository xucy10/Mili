package net.minecraft.world.entity.boss.enderdragon.phases;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.PowerParticleOption;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class DragonSittingFlamingPhase extends AbstractDragonSittingPhase {
    private static final int FLAME_DURATION = 200;
    private static final int SITTING_FLAME_ATTACKS_COUNT = 4;
    private static final int WARMUP_TIME = 10;
    private int flameTicks;
    private int flameCount;
    private @Nullable AreaEffectCloud flame;

    public DragonSittingFlamingPhase(EnderDragon dragon) {
        super(dragon);
    }

    @Override
    public void doClientTick() {
        this.flameTicks++;
        if (this.flameTicks % 2 == 0 && this.flameTicks < 10) {
            Vec3 vec3 = this.dragon.getHeadLookVector(1.0F).normalize();
            vec3.yRot((float) (-Math.PI / 4));
            double x = this.dragon.head.getX();
            double y = this.dragon.head.getY(0.5);
            double z = this.dragon.head.getZ();

            for (int i = 0; i < 8; i++) {
                double d = x + this.dragon.getRandom().nextGaussian() / 2.0;
                double d1 = y + this.dragon.getRandom().nextGaussian() / 2.0;
                double d2 = z + this.dragon.getRandom().nextGaussian() / 2.0;

                for (int i1 = 0; i1 < 6; i1++) {
                    this.dragon
                        .level()
                        .addParticle(
                            PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0F),
                            d,
                            d1,
                            d2,
                            -vec3.x * 0.08F * i1,
                            -vec3.y * 0.6F,
                            -vec3.z * 0.08F * i1
                        );
                }

                vec3.yRot((float) (Math.PI / 16));
            }
        }
    }

    @Override
    public void doServerTick(ServerLevel level) {
        this.flameTicks++;
        if (this.flameTicks >= 200) {
            if (this.flameCount >= 4) {
                this.dragon.getPhaseManager().setPhase(EnderDragonPhase.TAKEOFF);
            } else {
                this.dragon.getPhaseManager().setPhase(EnderDragonPhase.SITTING_SCANNING);
            }
        } else if (this.flameTicks == 10) {
            Vec3 vec3 = new Vec3(this.dragon.head.getX() - this.dragon.getX(), 0.0, this.dragon.head.getZ() - this.dragon.getZ()).normalize();
            float f = 5.0F;
            double d = this.dragon.head.getX() + vec3.x * 5.0 / 2.0;
            double d1 = this.dragon.head.getZ() + vec3.z * 5.0 / 2.0;
            double y = this.dragon.head.getY(0.5);
            double d2 = y;
            BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos(d, y, d1);

            while (level.isEmptyBlock(mutableBlockPos)) {
                if (--d2 < 0.0) {
                    d2 = y;
                    break;
                }

                mutableBlockPos.set(d, d2, d1);
            }

            d2 = Mth.floor(d2) + 1;
            this.flame = new AreaEffectCloud(level, d, d2, d1);
            this.flame.setOwner(this.dragon);
            this.flame.setRadius(5.0F);
            this.flame.setDuration(200);
            this.flame.setCustomParticle(PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0F));
            this.flame.setPotionDurationScale(0.25F);
            this.flame.addEffect(new MobEffectInstance(MobEffects.INSTANT_DAMAGE));
            if (new com.destroystokyo.paper.event.entity.EnderDragonFlameEvent((org.bukkit.entity.EnderDragon) this.dragon.getBukkitEntity(), (org.bukkit.entity.AreaEffectCloud) this.flame.getBukkitEntity()).callEvent()) { // Paper - EnderDragon Events
            level.addFreshEntity(this.flame);
            // Paper start - EnderDragon Events
            } else {
                this.end();
            }
            // Paper end - EnderDragon Events
        }
    }

    @Override
    public void begin() {
        this.flameTicks = 0;
        this.flameCount++;
    }

    @Override
    public void end() {
        if (this.flame != null) {
            this.flame.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
            this.flame = null;
        }
    }

    @Override
    public EnderDragonPhase<DragonSittingFlamingPhase> getPhase() {
        return EnderDragonPhase.SITTING_FLAMING;
    }

    public void resetFlameCount() {
        this.flameCount = 0;
    }
}
