package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;

public class LookAndFollowTradingPlayerSink extends Behavior<Villager> {
    private final float speedModifier;

    public LookAndFollowTradingPlayerSink(float speedModifier) {
        super(ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED, MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED), Integer.MAX_VALUE);
        this.speedModifier = speedModifier;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager owner) {
        Player tradingPlayer = owner.getTradingPlayer();
        return owner.isAlive() && tradingPlayer != null && !owner.isInWater() && !owner.hurtMarked && owner.distanceToSqr(tradingPlayer) <= 16.0;
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager entity, long gameTime) {
        return this.checkExtraStartConditions(level, entity);
    }

    @Override
    protected void start(ServerLevel level, Villager entity, long gameTime) {
        this.followPlayer(entity);
    }

    @Override
    protected void stop(ServerLevel level, Villager entity, long gameTime) {
        Brain<?> brain = entity.getBrain();
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
    }

    @Override
    protected void tick(ServerLevel level, Villager owner, long gameTime) {
        this.followPlayer(owner);
    }

    @Override
    protected boolean timedOut(long gameTime) {
        return false;
    }

    private void followPlayer(Villager owner) {
        Brain<?> brain = owner.getBrain();
        brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new EntityTracker(owner.getTradingPlayer(), false), this.speedModifier, 2));
        brain.setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(owner.getTradingPlayer(), true));
    }
}
