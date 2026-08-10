package net.minecraft.world.entity.ai.behavior;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Util;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.animal.allay.AllayAi;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class GoAndGiveItemsToTarget<E extends LivingEntity & InventoryCarrier> extends Behavior<E> {
    private static final int CLOSE_ENOUGH_DISTANCE_TO_TARGET = 3;
    private static final int ITEM_PICKUP_COOLDOWN_AFTER_THROWING = 60;
    private final Function<LivingEntity, Optional<PositionTracker>> targetPositionGetter;
    private final float speedModifier;

    public GoAndGiveItemsToTarget(Function<LivingEntity, Optional<PositionTracker>> targetPositionGetter, float speedModifier, int duration) {
        super(
            Map.of(
                MemoryModuleType.LOOK_TARGET,
                MemoryStatus.REGISTERED,
                MemoryModuleType.WALK_TARGET,
                MemoryStatus.REGISTERED,
                MemoryModuleType.ITEM_PICKUP_COOLDOWN_TICKS,
                MemoryStatus.REGISTERED
            ),
            duration
        );
        this.targetPositionGetter = targetPositionGetter;
        this.speedModifier = speedModifier;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, E owner) {
        return this.canThrowItemToTarget(owner);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, E entity, long gameTime) {
        return this.canThrowItemToTarget(entity);
    }

    @Override
    protected void start(ServerLevel level, E entity, long gameTime) {
        this.targetPositionGetter.apply(entity).ifPresent(pos -> BehaviorUtils.setWalkAndLookTargetMemories(entity, pos, this.speedModifier, 3));
    }

    @Override
    protected void tick(ServerLevel level, E owner, long gameTime) {
        Optional<PositionTracker> optional = this.targetPositionGetter.apply(owner);
        if (!optional.isEmpty()) {
            PositionTracker positionTracker = optional.get();
            double d = positionTracker.currentPosition().distanceTo(owner.getEyePosition());
            if (d < 3.0) {
                ItemStack itemStack = owner.getInventory().removeItem(0, 1);
                if (!itemStack.isEmpty()) {
                    throwItem(owner, itemStack, getThrowPosition(positionTracker));
                    if (owner instanceof Allay allay) {
                        AllayAi.getLikedPlayer(allay).ifPresent(player -> this.triggerDropItemOnBlock(positionTracker, itemStack, player));
                    }

                    owner.getBrain().setMemory(MemoryModuleType.ITEM_PICKUP_COOLDOWN_TICKS, 60);
                }
            }
        }
    }

    private void triggerDropItemOnBlock(PositionTracker positionTracker, ItemStack stack, ServerPlayer player) {
        BlockPos blockPos = positionTracker.currentBlockPosition().below();
        CriteriaTriggers.ALLAY_DROP_ITEM_ON_BLOCK.trigger(player, blockPos, stack);
    }

    private boolean canThrowItemToTarget(E target) {
        if (target.getInventory().isEmpty()) {
            return false;
        } else {
            Optional<PositionTracker> optional = this.targetPositionGetter.apply(target);
            return optional.isPresent();
        }
    }

    private static Vec3 getThrowPosition(PositionTracker positionTracker) {
        return positionTracker.currentPosition().add(0.0, 1.0, 0.0);
    }

    public static void throwItem(LivingEntity entity, ItemStack stack, Vec3 throwPos) {
        Vec3 vec3 = new Vec3(0.2F, 0.3F, 0.2F);
        BehaviorUtils.throwItem(entity, stack, throwPos, vec3, 0.2F);
        Level level = entity.level();
        if (level.getGameTime() % 7L == 0L && level.random.nextDouble() < 0.9) {
            float random = Util.getRandom(Allay.THROW_SOUND_PITCHES, level.getRandom());
            level.playSound(null, entity, SoundEvents.ALLAY_THROW, SoundSource.NEUTRAL, 1.0F, random);
        }
    }
}
