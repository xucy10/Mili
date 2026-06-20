package net.minecraft.world.entity.ai.behavior;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

public class DoNothing implements BehaviorControl<LivingEntity> {
    private final int minDuration;
    private final int maxDuration;
    private Behavior.Status status = Behavior.Status.STOPPED;
    private long endTimestamp;

    public DoNothing(int minDuration, int maxDuration) {
        this.minDuration = minDuration;
        this.maxDuration = maxDuration;
    }

    @Override
    public Behavior.Status getStatus() {
        return this.status;
    }

    @Override
    public final boolean tryStart(ServerLevel level, LivingEntity entity, long gameTime) {
        this.status = Behavior.Status.RUNNING;
        int i = this.minDuration + level.getRandom().nextInt(this.maxDuration + 1 - this.minDuration);
        this.endTimestamp = gameTime + i;
        return true;
    }

    @Override
    public final void tickOrStop(ServerLevel level, LivingEntity entity, long gameTime) {
        if (gameTime > this.endTimestamp) {
            this.doStop(level, entity, gameTime);
        }
    }

    @Override
    public final void doStop(ServerLevel level, LivingEntity entity, long gameTime) {
        this.status = Behavior.Status.STOPPED;
    }

    @Override
    public String debugString() {
        return this.getClass().getSimpleName();
    }
}
