package net.minecraft.world.entity.ai.behavior;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import org.apache.commons.lang3.mutable.MutableInt;

public class SetHiddenState {
    private static final int HIDE_TIMEOUT = 300;

    public static BehaviorControl<LivingEntity> create(int stayHiddenSeconds, int closeEnoughDist) {
        int i = stayHiddenSeconds * 20;
        MutableInt mutableInt = new MutableInt(0);
        return BehaviorBuilder.create(
            instance -> instance.group(instance.present(MemoryModuleType.HIDING_PLACE), instance.present(MemoryModuleType.HEARD_BELL_TIME))
                .apply(instance, (hidingPlace, heardBellTime) -> (level, entity, gameTime) -> {
                    long l = instance.<Long>get(heardBellTime);
                    boolean flag = l + 300L <= gameTime;
                    if (mutableInt.intValue() <= i && !flag) {
                        BlockPos blockPos = instance.get(hidingPlace).pos();
                        if (blockPos.closerThan(entity.blockPosition(), closeEnoughDist)) {
                            mutableInt.increment();
                        }

                        return true;
                    } else {
                        heardBellTime.erase();
                        hidingPlace.erase();
                        entity.getBrain().updateActivityFromSchedule(level.environmentAttributes(), level.getGameTime(), entity.position());
                        mutableInt.setValue(0);
                        return true;
                    }
                })
        );
    }
}
