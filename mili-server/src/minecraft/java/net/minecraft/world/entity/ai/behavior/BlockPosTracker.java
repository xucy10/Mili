package net.minecraft.world.entity.ai.behavior;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public class BlockPosTracker implements PositionTracker {
    private final BlockPos blockPos;
    private final Vec3 centerPosition;

    public BlockPosTracker(BlockPos blockPos) {
        this.blockPos = blockPos.immutable();
        this.centerPosition = Vec3.atCenterOf(blockPos);
    }

    public BlockPosTracker(Vec3 centerPosition) {
        this.blockPos = BlockPos.containing(centerPosition);
        this.centerPosition = centerPosition;
    }

    @Override
    public Vec3 currentPosition() {
        return this.centerPosition;
    }

    @Override
    public BlockPos currentBlockPosition() {
        return this.blockPos;
    }

    @Override
    public boolean isVisibleBy(LivingEntity entity) {
        return true;
    }

    @Override
    public String toString() {
        return "BlockPosTracker{blockPos=" + this.blockPos + ", centerPosition=" + this.centerPosition + "}";
    }

    // Luminol start - Fix a series issue around entity memory typed GlobalPos and WalkTarget
    @Override
    public boolean checkThread(net.minecraft.world.level.Level currOwnedByLevel) {
        return ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(currOwnedByLevel, this.blockPos);
    }
    // Luminol end
}
