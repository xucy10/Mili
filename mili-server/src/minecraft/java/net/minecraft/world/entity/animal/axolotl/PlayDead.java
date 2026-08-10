package net.minecraft.world.entity.animal.axolotl;

import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;

public class PlayDead extends Behavior<Axolotl> {
    public PlayDead() {
        super(ImmutableMap.of(MemoryModuleType.PLAY_DEAD_TICKS, MemoryStatus.VALUE_PRESENT, MemoryModuleType.HURT_BY_ENTITY, MemoryStatus.VALUE_PRESENT), 200);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Axolotl owner) {
        return owner.isInWater();
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Axolotl entity, long gameTime) {
        return entity.isInWater() && entity.getBrain().hasMemoryValue(MemoryModuleType.PLAY_DEAD_TICKS);
    }

    @Override
    protected void start(ServerLevel level, Axolotl entity, long gameTime) {
        Brain<Axolotl> brain = entity.getBrain();
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
        entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0));
    }
}
