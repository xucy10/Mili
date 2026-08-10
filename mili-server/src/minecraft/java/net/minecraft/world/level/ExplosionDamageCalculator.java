package net.minecraft.world.level;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

public class ExplosionDamageCalculator {
    public Optional<Float> getBlockExplosionResistance(Explosion explosion, BlockGetter level, BlockPos pos, BlockState state, FluidState fluid) {
        return state.isAir() && fluid.isEmpty()
            ? Optional.empty()
            : Optional.of(Math.max(state.getBlock().getExplosionResistance(), fluid.getExplosionResistance()));
    }

    public boolean shouldBlockExplode(Explosion explosion, BlockGetter level, BlockPos pos, BlockState state, float power) {
        return true;
    }

    public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
        return true;
    }

    public float getKnockbackMultiplier(Entity entity) {
        return 1.0F;
    }

    public float getEntityDamageAmount(Explosion explosion, Entity entity, float seenPercent) {
        float f = explosion.radius() * 2.0F;
        Vec3 vec3 = explosion.center();
        double d = Math.sqrt(entity.distanceToSqr(vec3)) / f;
        double d1 = (1.0 - d) * seenPercent;
        return (float)((d1 * d1 + d1) / 2.0 * 7.0 * f + 1.0);
    }
}
