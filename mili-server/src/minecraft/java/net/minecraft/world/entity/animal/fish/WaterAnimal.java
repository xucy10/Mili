package net.minecraft.world.entity.animal.fish;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.PathType;

public abstract class WaterAnimal extends PathfinderMob {
    public static final int AMBIENT_SOUND_INTERVAL = 120;

    protected WaterAnimal(EntityType<? extends WaterAnimal> type, Level level) {
        super(type, level);
        this.setPathfindingMalus(PathType.WATER, 0.0F);
    }

    @Override
    public boolean checkSpawnObstruction(LevelReader level) {
        return level.isUnobstructed(this);
    }

    @Override
    public int getAmbientSoundInterval() {
        return 120;
    }

    @Override
    protected int getBaseExperienceReward(ServerLevel level) {
        return 1 + this.random.nextInt(3);
    }

    protected void handleAirSupply(ServerLevel level, int airSupply) {
        if (this.isAlive() && !this.isInWater()) {
            this.setAirSupply(airSupply - 1);
            if (this.shouldTakeDrowningDamage()) {
                this.setAirSupply(0);
                this.hurtServer(level, this.damageSources().drown(), 2.0F);
            }
        } else {
            this.setAirSupply(300);
        }
    }

    @Override
    public void baseTick() {
        int airSupply = this.getAirSupply();
        super.baseTick();
        if (this.level() instanceof ServerLevel serverLevel) {
            this.handleAirSupply(serverLevel, airSupply);
        }
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    public static boolean checkSurfaceWaterAnimalSpawnRules(
        EntityType<? extends WaterAnimal> entityType, LevelAccessor level, EntitySpawnReason spawnReason, BlockPos pos, RandomSource random
    ) {
        int seaLevel = level.getSeaLevel();
        int i = seaLevel - 13;
        // Paper start - Make water animal spawn height configurable
        seaLevel = level.getMinecraftWorld().paperConfig().entities.spawning.wateranimalSpawnHeight.maximum.or(seaLevel);
        i = level.getMinecraftWorld().paperConfig().entities.spawning.wateranimalSpawnHeight.minimum.or(i);
        // Paper end - Make water animal spawn height configurable
        return pos.getY() >= i
            && pos.getY() <= seaLevel
            && level.getFluidState(pos.below()).is(FluidTags.WATER)
            && level.getBlockState(pos.above()).is(Blocks.WATER);
    }
}
