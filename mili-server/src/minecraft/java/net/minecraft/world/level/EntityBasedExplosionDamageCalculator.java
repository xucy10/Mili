package net.minecraft.world.level;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

public class EntityBasedExplosionDamageCalculator extends ExplosionDamageCalculator {
    private final Entity source;

    public EntityBasedExplosionDamageCalculator(Entity source) {
        this.source = source;
    }

    @Override
    public Optional<Float> getBlockExplosionResistance(Explosion explosion, BlockGetter level, BlockPos pos, BlockState state, FluidState fluid) {
        return super.getBlockExplosionResistance(explosion, level, pos, state, fluid)
            .map(resistance -> this.source.getBlockExplosionResistance(explosion, level, pos, state, fluid, resistance));
    }

    @Override
    public boolean shouldBlockExplode(Explosion explosion, BlockGetter level, BlockPos pos, BlockState state, float power) {
        return this.source.shouldBlockExplode(explosion, level, pos, state, power);
    }
}
