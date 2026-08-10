package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import java.util.Optional;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.villager.Villager;

public class WorkAtPoi extends Behavior<Villager> {
    private static final int CHECK_COOLDOWN = 300;
    private static final double DISTANCE = 1.73;
    private long lastCheck;

    public WorkAtPoi() {
        super(ImmutableMap.of(MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT, MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED));
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager owner) {
        if (level.getGameTime() - this.lastCheck < 300L) {
            return false;
        } else if (level.random.nextInt(2) != 0) {
            return false;
        } else {
            this.lastCheck = level.getGameTime();
            GlobalPos globalPos = owner.getBrain().getMemory(MemoryModuleType.JOB_SITE).get();
            return globalPos.dimension() == level.dimension() && globalPos.pos().closerToCenterThan(owner.position(), 1.73);
        }
    }

    @Override
    protected void start(ServerLevel level, Villager entity, long gameTime) {
        Brain<Villager> brain = entity.getBrain();
        brain.setMemory(MemoryModuleType.LAST_WORKED_AT_POI, gameTime);
        brain.getMemory(MemoryModuleType.JOB_SITE).ifPresent(global -> brain.setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(global.pos())));
        entity.playWorkSound();
        this.useWorkstation(level, entity);
        if (entity.shouldRestock(level)) {
            entity.restock();
        }
    }

    protected void useWorkstation(ServerLevel level, Villager villager) {
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager entity, long gameTime) {
        Optional<GlobalPos> memory = entity.getBrain().getMemory(MemoryModuleType.JOB_SITE);
        if (memory.isEmpty()) {
            return false;
        } else {
            GlobalPos globalPos = memory.get();
            return globalPos.dimension() == level.dimension() && globalPos.pos().closerToCenterThan(entity.position(), 1.73);
        }
    }
}
