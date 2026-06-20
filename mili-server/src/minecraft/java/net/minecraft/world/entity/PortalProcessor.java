package net.minecraft.world.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.portal.TeleportTransition;
import org.jspecify.annotations.Nullable;

public class PortalProcessor {
    private final Portal portal;
    private BlockPos entryPosition;
    private int portalTime;
    private boolean insidePortalThisTick;

    public PortalProcessor(Portal portal, BlockPos entryPosition) {
        this.portal = portal;
        this.entryPosition = entryPosition;
        this.insidePortalThisTick = true;
    }

    public boolean processPortalTeleportation(ServerLevel level, Entity entity, boolean canChangeDimensions) {
        if (!this.insidePortalThisTick) {
            this.decayTick();
            return false;
        } else {
            this.insidePortalThisTick = false;
            return canChangeDimensions && this.portalTime++ >= this.portal.getPortalTransitionTime(level, entity);
        }
    }

    public @Nullable TeleportTransition getPortalDestination(ServerLevel level, Entity entity) {
        return this.portal.getPortalDestination(level, entity, this.entryPosition);
    }

    // Folia start - region threading
    public boolean portalAsync(ServerLevel sourceWorld, Entity portalTarget) {
        return this.portal.portalAsync(sourceWorld, portalTarget, this.entryPosition);
    }
    // Folia end - region threading

    public Portal.Transition getPortalLocalTransition() {
        return this.portal.getLocalTransition();
    }

    private void decayTick() {
        this.portalTime = Math.max(this.portalTime - 4, 0);
    }

    public boolean hasExpired() {
        return this.portalTime <= 0;
    }

    public BlockPos getEntryPosition() {
        return this.entryPosition;
    }

    public void updateEntryPosition(BlockPos entryPosition) {
        this.entryPosition = entryPosition;
    }

    public int getPortalTime() {
        return this.portalTime;
    }

    public boolean isInsidePortalThisTick() {
        return this.insidePortalThisTick;
    }

    public void setAsInsidePortalThisTick(boolean insidePortalThisTick) {
        this.insidePortalThisTick = insidePortalThisTick;
    }

    public boolean isSamePortal(Portal portal) {
        return this.portal == portal;
    }
}
