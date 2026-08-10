package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;

public class GoToPotentialJobSite extends Behavior<Villager> {
    private static final int TICKS_UNTIL_TIMEOUT = 1200;
    final float speedModifier;

    public GoToPotentialJobSite(float speedModifier) {
        super(ImmutableMap.of(MemoryModuleType.POTENTIAL_JOB_SITE, MemoryStatus.VALUE_PRESENT), 1200);
        this.speedModifier = speedModifier;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager owner) {
        return owner.getBrain()
            .getActiveNonCoreActivity()
            .map(activity -> activity == Activity.IDLE || activity == Activity.WORK || activity == Activity.PLAY)
            .orElse(true);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager entity, long gameTime) {
        return entity.getBrain().hasMemoryValue(MemoryModuleType.POTENTIAL_JOB_SITE);
    }

    @Override
    protected void tick(ServerLevel level, Villager owner, long gameTime) {
        BehaviorUtils.setWalkAndLookTargetMemories(owner, owner.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).get().pos(), this.speedModifier, 1);
    }

    @Override
    protected void stop(ServerLevel level, Villager entity, long gameTime) {
        Optional<GlobalPos> memory = entity.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
        memory.ifPresent(globalPos -> {
            BlockPos blockPos = globalPos.pos();
            ServerLevel level1 = level.getServer().getLevel(globalPos.dimension());
            if (level1 != null) {
                io.papermc.paper.threadedregions.RegionizedServer.getInstance().taskQueue.queueTickTaskQueue(level1, blockPos.getX() >> 4, blockPos.getZ() >> 4, () -> { // Folia - region threading
                PoiManager poiManager = level1.getPoiManager();
                if (poiManager.exists(blockPos, holder -> true)) {
                    poiManager.release(blockPos);
                }

                level.debugSynchronizers().updatePoi(blockPos);
                }); // Folia - region threading
            }
        });
        entity.getBrain().eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
    }
}
