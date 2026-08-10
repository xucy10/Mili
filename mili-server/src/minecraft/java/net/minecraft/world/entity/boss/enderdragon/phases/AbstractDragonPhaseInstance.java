package net.minecraft.world.entity.boss.enderdragon.phases;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public abstract class AbstractDragonPhaseInstance implements DragonPhaseInstance {
    protected final EnderDragon dragon;

    public AbstractDragonPhaseInstance(EnderDragon dragon) {
        this.dragon = dragon;
    }

    @Override
    public boolean isSitting() {
        return false;
    }

    @Override
    public void doClientTick() {
    }

    @Override
    public void doServerTick(ServerLevel level) {
    }

    @Override
    public void onCrystalDestroyed(EndCrystal crystal, BlockPos pos, DamageSource damageSource, @Nullable Player player) {
    }

    @Override
    public void begin() {
    }

    @Override
    public void end() {
    }

    @Override
    public float getFlySpeed() {
        return 0.6F;
    }

    @Override
    public @Nullable Vec3 getFlyTargetLocation() {
        return null;
    }

    @Override
    public float onHurt(DamageSource damageSource, float amount) {
        return amount;
    }

    @Override
    public float getTurnSpeed() {
        float f = (float)this.dragon.getDeltaMovement().horizontalDistance() + 1.0F;
        float min = Math.min(f, 40.0F);
        return 0.7F / min / f;
    }
}
