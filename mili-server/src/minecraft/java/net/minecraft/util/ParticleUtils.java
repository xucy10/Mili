package net.minecraft.util;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class ParticleUtils {
    public static void spawnParticlesOnBlockFaces(Level level, BlockPos pos, ParticleOptions options, IntProvider count) {
        for (Direction direction : Direction.values()) {
            spawnParticlesOnBlockFace(level, pos, options, count, direction, () -> getRandomSpeedRanges(level.random), 0.55);
        }
    }

    public static void spawnParticlesOnBlockFace(
        Level level, BlockPos pos, ParticleOptions options, IntProvider count, Direction direction, Supplier<Vec3> speedSupplier, double spread
    ) {
        int i = count.sample(level.random);

        for (int i1 = 0; i1 < i; i1++) {
            spawnParticleOnFace(level, pos, direction, options, speedSupplier.get(), spread);
        }
    }

    private static Vec3 getRandomSpeedRanges(RandomSource random) {
        return new Vec3(Mth.nextDouble(random, -0.5, 0.5), Mth.nextDouble(random, -0.5, 0.5), Mth.nextDouble(random, -0.5, 0.5));
    }

    public static void spawnParticlesAlongAxis(Direction.Axis axis, Level level, BlockPos pos, double spread, ParticleOptions options, UniformInt count) {
        Vec3 vec3 = Vec3.atCenterOf(pos);
        boolean flag = axis == Direction.Axis.X;
        boolean flag1 = axis == Direction.Axis.Y;
        boolean flag2 = axis == Direction.Axis.Z;
        int i = count.sample(level.random);

        for (int i1 = 0; i1 < i; i1++) {
            double d = vec3.x + Mth.nextDouble(level.random, -1.0, 1.0) * (flag ? 0.5 : spread);
            double d1 = vec3.y + Mth.nextDouble(level.random, -1.0, 1.0) * (flag1 ? 0.5 : spread);
            double d2 = vec3.z + Mth.nextDouble(level.random, -1.0, 1.0) * (flag2 ? 0.5 : spread);
            double d3 = flag ? Mth.nextDouble(level.random, -1.0, 1.0) : 0.0;
            double d4 = flag1 ? Mth.nextDouble(level.random, -1.0, 1.0) : 0.0;
            double d5 = flag2 ? Mth.nextDouble(level.random, -1.0, 1.0) : 0.0;
            level.addParticle(options, d, d1, d2, d3, d4, d5);
        }
    }

    public static void spawnParticleOnFace(Level level, BlockPos pos, Direction direction, ParticleOptions options, Vec3 speed, double spread) {
        Vec3 vec3 = Vec3.atCenterOf(pos);
        int stepX = direction.getStepX();
        int stepY = direction.getStepY();
        int stepZ = direction.getStepZ();
        double d = vec3.x + (stepX == 0 ? Mth.nextDouble(level.random, -0.5, 0.5) : stepX * spread);
        double d1 = vec3.y + (stepY == 0 ? Mth.nextDouble(level.random, -0.5, 0.5) : stepY * spread);
        double d2 = vec3.z + (stepZ == 0 ? Mth.nextDouble(level.random, -0.5, 0.5) : stepZ * spread);
        double d3 = stepX == 0 ? speed.x() : 0.0;
        double d4 = stepY == 0 ? speed.y() : 0.0;
        double d5 = stepZ == 0 ? speed.z() : 0.0;
        level.addParticle(options, d, d1, d2, d3, d4, d5);
    }

    public static void spawnParticleBelow(Level level, BlockPos pos, RandomSource random, ParticleOptions options) {
        double d = pos.getX() + random.nextDouble();
        double d1 = pos.getY() - 0.05;
        double d2 = pos.getZ() + random.nextDouble();
        level.addParticle(options, d, d1, d2, 0.0, 0.0, 0.0);
    }

    public static void spawnParticleInBlock(LevelAccessor level, BlockPos pos, int count, ParticleOptions options) {
        double d = 0.5;
        BlockState blockState = level.getBlockState(pos);
        double d1 = blockState.isAir() ? 1.0 : blockState.getShape(level, pos).max(Direction.Axis.Y);
        spawnParticles(level, pos, count, 0.5, d1, true, options);
    }

    public static void spawnParticles(
        LevelAccessor level, BlockPos pos, int count, double xzSpread, double ySpread, boolean allowInAir, ParticleOptions options
    ) {
        RandomSource random = level.getRandom();

        for (int i = 0; i < count; i++) {
            double d = random.nextGaussian() * 0.02;
            double d1 = random.nextGaussian() * 0.02;
            double d2 = random.nextGaussian() * 0.02;
            double d3 = 0.5 - xzSpread;
            double d4 = pos.getX() + d3 + random.nextDouble() * xzSpread * 2.0;
            double d5 = pos.getY() + random.nextDouble() * ySpread;
            double d6 = pos.getZ() + d3 + random.nextDouble() * xzSpread * 2.0;
            if (allowInAir || !level.getBlockState(BlockPos.containing(d4, d5, d6).below()).isAir()) {
                level.addParticle(options, d4, d5, d6, d, d1, d2);
            }
        }
    }

    public static void spawnSmashAttackParticles(LevelAccessor level, BlockPos pos, int power) {
        Vec3 vec3 = pos.getCenter().add(0.0, 0.5, 0.0);
        BlockParticleOption blockParticleOption = new BlockParticleOption(ParticleTypes.DUST_PILLAR, level.getBlockState(pos));

        for (int i = 0; i < power / 3.0F; i++) {
            double d = vec3.x + level.getRandom().nextGaussian() / 2.0;
            double d1 = vec3.y;
            double d2 = vec3.z + level.getRandom().nextGaussian() / 2.0;
            double d3 = level.getRandom().nextGaussian() * 0.2F;
            double d4 = level.getRandom().nextGaussian() * 0.2F;
            double d5 = level.getRandom().nextGaussian() * 0.2F;
            level.addParticle(blockParticleOption, d, d1, d2, d3, d4, d5);
        }

        for (int i = 0; i < power / 1.5F; i++) {
            double d = vec3.x + 3.5 * Math.cos(i) + level.getRandom().nextGaussian() / 2.0;
            double d1 = vec3.y;
            double d2 = vec3.z + 3.5 * Math.sin(i) + level.getRandom().nextGaussian() / 2.0;
            double d3 = level.getRandom().nextGaussian() * 0.05F;
            double d4 = level.getRandom().nextGaussian() * 0.05F;
            double d5 = level.getRandom().nextGaussian() * 0.05F;
            level.addParticle(blockParticleOption, d, d1, d2, d3, d4, d5);
        }
    }
}
