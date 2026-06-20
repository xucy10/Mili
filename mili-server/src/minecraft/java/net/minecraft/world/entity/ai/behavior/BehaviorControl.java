package net.minecraft.world.entity.ai.behavior;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

public interface BehaviorControl<E extends LivingEntity> {
    Behavior.Status getStatus();

    boolean tryStart(ServerLevel level, E entity, long gameTime);

    void tickOrStop(ServerLevel level, E entity, long gameTime);

    void doStop(ServerLevel level, E entity, long gameTime);

    String debugString();
}
