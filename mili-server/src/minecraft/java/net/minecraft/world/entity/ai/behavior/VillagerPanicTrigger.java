package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;

public class VillagerPanicTrigger extends Behavior<Villager> {
    public VillagerPanicTrigger() {
        super(ImmutableMap.of());
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager entity, long gameTime) {
        return isHurt(entity) || hasHostile(entity);
    }

    @Override
    protected void start(ServerLevel level, Villager entity, long gameTime) {
        if (isHurt(entity) || hasHostile(entity)) {
            Brain<?> brain = entity.getBrain();
            if (!brain.isActive(Activity.PANIC)) {
                brain.eraseMemory(MemoryModuleType.PATH);
                brain.eraseMemory(MemoryModuleType.WALK_TARGET);
                brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
                brain.eraseMemory(MemoryModuleType.BREED_TARGET);
                brain.eraseMemory(MemoryModuleType.INTERACTION_TARGET);
            }

            brain.setActiveActivityIfPossible(Activity.PANIC);
        }
    }

    @Override
    protected void tick(ServerLevel level, Villager owner, long gameTime) {
        if (gameTime % 100L == 0L) {
            owner.spawnGolemIfNeeded(level, gameTime, 3);
        }
    }

    public static boolean hasHostile(LivingEntity entity) {
        return entity.getBrain().hasMemoryValue(MemoryModuleType.NEAREST_HOSTILE);
    }

    public static boolean isHurt(LivingEntity entity) {
        return entity.getBrain().hasMemoryValue(MemoryModuleType.HURT_BY);
    }
}
