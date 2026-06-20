package net.minecraft.world.entity.ai.behavior;

import com.mojang.datafixers.util.Pair;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.behavior.declarative.Trigger;

public class TriggerGate {
    public static <E extends LivingEntity> OneShot<E> triggerOneShuffled(List<Pair<? extends Trigger<? super E>, Integer>> triggers) {
        return triggerGate(triggers, GateBehavior.OrderPolicy.SHUFFLED, GateBehavior.RunningPolicy.RUN_ONE);
    }

    public static <E extends LivingEntity> OneShot<E> triggerGate(
        List<Pair<? extends Trigger<? super E>, Integer>> triggers, GateBehavior.OrderPolicy orderPolicy, GateBehavior.RunningPolicy runningPolicy
    ) {
        ShufflingList<Trigger<? super E>> shufflingList = new ShufflingList<>();
        triggers.forEach(pair -> shufflingList.add(pair.getFirst(), pair.getSecond()));
        return BehaviorBuilder.create(instance -> instance.point((level, entity, gameTime) -> {
            if (orderPolicy == GateBehavior.OrderPolicy.SHUFFLED) {
                shufflingList.shuffle();
            }

            for (Trigger<? super E> trigger : shufflingList) {
                if (trigger.trigger(level, entity, gameTime) && runningPolicy == GateBehavior.RunningPolicy.RUN_ONE) {
                    break;
                }
            }

            return true;
        }));
    }
}
