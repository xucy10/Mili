package net.minecraft.world.entity.ai.goal;

import java.util.EnumSet;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class PanicGoal extends Goal {
    public static final int WATER_CHECK_DISTANCE_VERTICAL = 1;
    protected final PathfinderMob mob;
    protected final double speedModifier;
    protected double posX;
    protected double posY;
    protected double posZ;
    protected boolean isRunning;
    private final Function<PathfinderMob, TagKey<DamageType>> panicCausingDamageTypes;

    public PanicGoal(PathfinderMob mob, double speedModifier) {
        this(mob, speedModifier, DamageTypeTags.PANIC_CAUSES);
    }

    public PanicGoal(PathfinderMob mob, double speedModifier, TagKey<DamageType> panicCausingDamageTypes) {
        this(mob, speedModifier, pathfinderMob -> panicCausingDamageTypes);
    }

    public PanicGoal(PathfinderMob mob, double speedModifier, Function<PathfinderMob, TagKey<DamageType>> panicCausingDamageTypes) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.panicCausingDamageTypes = panicCausingDamageTypes;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!this.shouldPanic()) {
            return false;
        } else {
            if (this.mob.isOnFire()) {
                BlockPos blockPos = this.lookForWater(this.mob.level(), this.mob, 5);
                if (blockPos != null) {
                    this.posX = blockPos.getX();
                    this.posY = blockPos.getY();
                    this.posZ = blockPos.getZ();
                    return true;
                }
            }

            return this.findRandomPosition();
        }
    }

    protected boolean shouldPanic() {
        return this.mob.getLastDamageSource() != null && this.mob.getLastDamageSource().is(this.panicCausingDamageTypes.apply(this.mob));
    }

    protected boolean findRandomPosition() {
        Vec3 pos = DefaultRandomPos.getPos(this.mob, 5, 4);
        if (pos == null) {
            return false;
        } else {
            this.posX = pos.x;
            this.posY = pos.y;
            this.posZ = pos.z;
            return true;
        }
    }

    public boolean isRunning() {
        return this.isRunning;
    }

    @Override
    public void start() {
        this.mob.getNavigation().moveTo(this.posX, this.posY, this.posZ, this.speedModifier);
        this.isRunning = true;
    }

    @Override
    public void stop() {
        this.isRunning = false;
    }

    @Override
    public boolean canContinueToUse() {
        return !this.mob.getNavigation().isDone();
    }

    protected @Nullable BlockPos lookForWater(BlockGetter level, Entity entity, int range) {
        BlockPos blockPos = entity.blockPosition();
        return !level.getBlockState(blockPos).getCollisionShape(level, blockPos).isEmpty()
            ? null
            : BlockPos.findClosestMatch(entity.blockPosition(), range, 1, blockPos1 -> level.getFluidState(blockPos1).is(FluidTags.WATER)).orElse(null);
    }
}
