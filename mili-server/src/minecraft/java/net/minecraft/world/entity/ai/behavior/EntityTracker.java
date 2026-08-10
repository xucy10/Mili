package net.minecraft.world.entity.ai.behavior;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.phys.Vec3;

public class EntityTracker implements PositionTracker {
    private final Entity entity;
    private final boolean trackEyeHeight;
    private final boolean targetEyeHeight;

    public EntityTracker(Entity entity, boolean trackEyeHeight) {
        this(entity, trackEyeHeight, false);
    }

    public EntityTracker(Entity entity, boolean trackEyeHeight, boolean targetEyeHeight) {
        this.entity = entity;
        this.trackEyeHeight = trackEyeHeight;
        this.targetEyeHeight = targetEyeHeight;
    }

    @Override
    public Vec3 currentPosition() {
        return this.trackEyeHeight ? this.entity.position().add(0.0, this.entity.getEyeHeight(), 0.0) : this.entity.position();
    }

    @Override
    public BlockPos currentBlockPosition() {
        return this.targetEyeHeight ? BlockPos.containing(this.entity.getEyePosition()) : this.entity.blockPosition();
    }

    @Override
    public boolean isVisibleBy(LivingEntity entity) {
        if (this.entity instanceof LivingEntity livingEntity) {
            if (!livingEntity.isAlive()) {
                return false;
            } else {
                Optional<NearestVisibleLivingEntities> memory = entity.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES);
                return memory.isPresent() && memory.get().contains(livingEntity);
            }
        } else {
            return true;
        }
    }

    public Entity getEntity() {
        return this.entity;
    }

    @Override
    public String toString() {
        return "EntityTracker for " + this.entity;
    }

    // Luminol start - Fix a series issue around entity memory typed GlobalPos and WalkTarget
    @Override
    public boolean checkThread(net.minecraft.world.level.Level currOwnedByLevel) {
        return ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(this.entity);
    }
    // Luminol end
}
