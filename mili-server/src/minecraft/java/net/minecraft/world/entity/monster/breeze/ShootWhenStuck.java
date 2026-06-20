package net.minecraft.world.entity.monster.breeze;

import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;

public class ShootWhenStuck extends Behavior<Breeze> {
    public ShootWhenStuck() {
        super(
            Map.of(
                MemoryModuleType.ATTACK_TARGET,
                MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.BREEZE_JUMP_INHALING,
                MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.BREEZE_JUMP_TARGET,
                MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.WALK_TARGET,
                MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.BREEZE_SHOOT,
                MemoryStatus.VALUE_ABSENT
            )
        );
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Breeze owner) {
        return owner.isPassenger() || owner.isInWater() || owner.getEffect(MobEffects.LEVITATION) != null;
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Breeze entity, long gameTime) {
        return false;
    }

    @Override
    protected void start(ServerLevel level, Breeze entity, long gameTime) {
        entity.getBrain().setMemoryWithExpiry(MemoryModuleType.BREEZE_SHOOT, Unit.INSTANCE, 60L);
    }
}
