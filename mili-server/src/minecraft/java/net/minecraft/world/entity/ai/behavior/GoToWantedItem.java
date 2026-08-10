package net.minecraft.world.entity.ai.behavior;

import com.mojang.datafixers.kinds.K1;
import java.util.function.Predicate;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.behavior.declarative.MemoryAccessor;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.item.ItemEntity;

public class GoToWantedItem {
    public static BehaviorControl<LivingEntity> create(float speedModifier, boolean hasTarget, int maxDistToWalk) {
        return create(entity -> true, speedModifier, hasTarget, maxDistToWalk);
    }

    public static <E extends LivingEntity> BehaviorControl<E> create(Predicate<E> canWalkToItem, float speedModifier, boolean hasTarget, int maxDistToWalk) {
        return BehaviorBuilder.create(
            instance -> {
                BehaviorBuilder<E, ? extends MemoryAccessor<? extends K1, WalkTarget>> behaviorBuilder = hasTarget
                    ? instance.registered(MemoryModuleType.WALK_TARGET)
                    : instance.absent(MemoryModuleType.WALK_TARGET);
                return instance.group(
                        instance.registered(MemoryModuleType.LOOK_TARGET),
                        behaviorBuilder,
                        instance.present(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM),
                        instance.registered(MemoryModuleType.ITEM_PICKUP_COOLDOWN_TICKS)
                    )
                    .apply(
                        instance,
                        (lookTarget, walkTarget, nearestVisibleWantedItem, itemPickupCooldownTicks) -> (level, entity, gameTime) -> {
                            ItemEntity itemEntity = instance.get(nearestVisibleWantedItem);
                            if (instance.tryGet(itemPickupCooldownTicks).isEmpty()
                                && canWalkToItem.test(entity)
                                && itemEntity.closerThan(entity, maxDistToWalk)
                                && entity.level().getWorldBorder().isWithinBounds(itemEntity.blockPosition())
                                && entity.canPickUpLoot()) {
                                // CraftBukkit start
                                if (entity instanceof net.minecraft.world.entity.animal.allay.Allay) {
                                    org.bukkit.event.entity.EntityTargetEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callEntityTargetEvent(entity, itemEntity, org.bukkit.event.entity.EntityTargetEvent.TargetReason.CLOSEST_ENTITY);

                                    if (event.isCancelled()) {
                                        return false;
                                    }
                                    if (!(event.getTarget() instanceof org.bukkit.craftbukkit.entity.CraftItem targetItem)) { // Paper - only erase allay memory on non-item targets
                                        nearestVisibleWantedItem.erase();
                                        return false; // Paper - only erase allay memory on non-item targets
                                    }

                                    itemEntity = targetItem.getHandle();
                                }
                                // CraftBukkit end
                                WalkTarget walkTarget1 = new WalkTarget(new EntityTracker(itemEntity, false), speedModifier, 0);
                                lookTarget.set(new EntityTracker(itemEntity, true));
                                walkTarget.set(walkTarget1);
                                return true;
                            } else {
                                return false;
                            }
                        }
                    );
            }
        );
    }
}
