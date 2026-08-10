package net.minecraft.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.TeleportTransition;
import org.jspecify.annotations.Nullable;

public interface Portal {
    default int getPortalTransitionTime(ServerLevel level, Entity entity) {
        return 0;
    }

    @Nullable TeleportTransition getPortalDestination(ServerLevel level, Entity entity, BlockPos pos);

    // Folia start - region threading
    public boolean portalAsync(ServerLevel sourceWorld, Entity portalTarget, BlockPos portalPos);
    // Folia end - region threading

    default Portal.Transition getLocalTransition() {
        return Portal.Transition.NONE;
    }

    public static enum Transition {
        CONFUSION,
        NONE;
    }
}
