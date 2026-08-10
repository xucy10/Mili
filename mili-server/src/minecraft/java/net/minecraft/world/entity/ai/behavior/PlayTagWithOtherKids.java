package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.Maps;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.behavior.declarative.MemoryAccessor;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;

public class PlayTagWithOtherKids {
    private static final int MAX_FLEE_XZ_DIST = 20;
    private static final int MAX_FLEE_Y_DIST = 8;
    private static final float FLEE_SPEED_MODIFIER = 0.6F;
    private static final float CHASE_SPEED_MODIFIER = 0.6F;
    private static final int MAX_CHASERS_PER_TARGET = 5;
    private static final int AVERAGE_WAIT_TIME_BETWEEN_RUNS = 10;

    public static BehaviorControl<PathfinderMob> create() {
        return BehaviorBuilder.create(
            instance -> instance.group(
                    instance.present(MemoryModuleType.VISIBLE_VILLAGER_BABIES),
                    instance.absent(MemoryModuleType.WALK_TARGET),
                    instance.registered(MemoryModuleType.LOOK_TARGET),
                    instance.registered(MemoryModuleType.INTERACTION_TARGET)
                )
                .apply(instance, (visibleVillagerBabies, walkTarget, lookTarget, interactionTarget) -> (level, mob, gameTime) -> {
                    if (level.getRandom().nextInt(10) != 0) {
                        return false;
                    } else {
                        List<LivingEntity> list = instance.get(visibleVillagerBabies);
                        Optional<LivingEntity> optional = list.stream().filter(kid -> isFriendChasingMe(mob, kid)).findAny();
                        if (!optional.isPresent()) {
                            Optional<LivingEntity> optional1 = findSomeoneBeingChased(list);
                            if (optional1.isPresent()) {
                                chaseKid(interactionTarget, lookTarget, walkTarget, optional1.get());
                                return true;
                            } else {
                                list.stream().findAny().ifPresent(kid -> chaseKid(interactionTarget, lookTarget, walkTarget, kid));
                                return true;
                            }
                        } else {
                            for (int i = 0; i < 10; i++) {
                                Vec3 pos = LandRandomPos.getPos(mob, 20, 8);
                                if (pos != null && level.isVillage(BlockPos.containing(pos))) {
                                    walkTarget.set(new WalkTarget(pos, 0.6F, 0));
                                    break;
                                }
                            }

                            return true;
                        }
                    }
                })
        );
    }

    private static void chaseKid(
        MemoryAccessor<?, LivingEntity> interactionTarget,
        MemoryAccessor<?, PositionTracker> lookTarget,
        MemoryAccessor<?, WalkTarget> walkTarget,
        LivingEntity kid
    ) {
        interactionTarget.set(kid);
        lookTarget.set(new EntityTracker(kid, true));
        walkTarget.set(new WalkTarget(new EntityTracker(kid, false), 0.6F, 1));
    }

    private static Optional<LivingEntity> findSomeoneBeingChased(List<LivingEntity> kids) {
        Map<LivingEntity, Integer> map = checkHowManyChasersEachFriendHas(kids);
        return map.entrySet()
            .stream()
            .sorted(Comparator.comparingInt(Entry::getValue))
            .filter(entry -> entry.getValue() > 0 && entry.getValue() <= 5)
            .map(Entry::getKey)
            .findFirst();
    }

    private static Map<LivingEntity, Integer> checkHowManyChasersEachFriendHas(List<LivingEntity> kids) {
        Map<LivingEntity, Integer> map = Maps.newHashMap();
        kids.stream()
            .filter(PlayTagWithOtherKids::isChasingSomeone)
            .forEach(kid -> map.compute(whoAreYouChasing(kid), (entity, chasers) -> chasers == null ? 1 : chasers + 1));
        return map;
    }

    private static LivingEntity whoAreYouChasing(LivingEntity kid) {
        return kid.getBrain().getMemory(MemoryModuleType.INTERACTION_TARGET).get();
    }

    private static boolean isChasingSomeone(LivingEntity kid) {
        return kid.getBrain().getMemory(MemoryModuleType.INTERACTION_TARGET).isPresent();
    }

    private static boolean isFriendChasingMe(LivingEntity entity, LivingEntity kid) {
        return kid.getBrain().getMemory(MemoryModuleType.INTERACTION_TARGET).filter(target -> target == entity).isPresent();
    }
}
