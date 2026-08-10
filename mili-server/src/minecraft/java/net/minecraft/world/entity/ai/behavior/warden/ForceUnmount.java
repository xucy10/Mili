package net.minecraft.world.entity.ai.behavior.warden;

import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;

public class ForceUnmount extends Behavior<LivingEntity> {
    public ForceUnmount() {
        super(ImmutableMap.of());
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, LivingEntity owner) {
        return owner.isPassenger();
    }

    @Override
    protected void start(ServerLevel level, LivingEntity entity, long gameTime) {
        entity.unRide();
    }
}
