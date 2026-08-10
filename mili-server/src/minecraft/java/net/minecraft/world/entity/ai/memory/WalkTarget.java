package net.minecraft.world.entity.ai.memory;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.behavior.PositionTracker;
import net.minecraft.world.phys.Vec3;

public class WalkTarget {
    private final PositionTracker target;
    private final float speedModifier;
    private final int closeEnoughDist;

    public WalkTarget(BlockPos pos, float speedModifier, int closeEnoughDist) {
        this(new BlockPosTracker(pos), speedModifier, closeEnoughDist);
    }

    public WalkTarget(Vec3 vectorPos, float speedModifier, int closeEnoughDist) {
        this(new BlockPosTracker(BlockPos.containing(vectorPos)), speedModifier, closeEnoughDist);
    }

    public WalkTarget(Entity targetEntity, float speedModifier, int closeEnoughDist) {
        this(new EntityTracker(targetEntity, false), speedModifier, closeEnoughDist);
    }

    public WalkTarget(PositionTracker target, float speedModifier, int closeEnoughDist) {
        this.target = target;
        this.speedModifier = speedModifier;
        this.closeEnoughDist = closeEnoughDist;
    }

    public PositionTracker getTarget() {
        return this.target;
    }

    public float getSpeedModifier() {
        return this.speedModifier;
    }

    public int getCloseEnoughDist() {
        return this.closeEnoughDist;
    }
}
