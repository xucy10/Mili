package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.player.Player;

public class FollowTemptation extends Behavior<PathfinderMob> {
    public static final int TEMPTATION_COOLDOWN = 100;
    public static final double DEFAULT_CLOSE_ENOUGH_DIST = 2.5;
    public static final double BACKED_UP_CLOSE_ENOUGH_DIST = 3.5;
    private final Function<LivingEntity, Float> speedModifier;
    private final Function<LivingEntity, Double> closeEnoughDistance;
    private final boolean lookInTheEyes;

    public FollowTemptation(Function<LivingEntity, Float> speedModifier) {
        this(speedModifier, livingEntity -> 2.5);
    }

    public FollowTemptation(Function<LivingEntity, Float> speedModifier, Function<LivingEntity, Double> closeEnoughDistance) {
        this(speedModifier, closeEnoughDistance, false);
    }

    public FollowTemptation(Function<LivingEntity, Float> speedModifier, Function<LivingEntity, Double> closeEnoughDistance, boolean lookInTheEyes) {
        super(Util.make(() -> {
            Builder<MemoryModuleType<?>, MemoryStatus> builder = ImmutableMap.builder();
            builder.put(MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED);
            builder.put(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED);
            builder.put(MemoryModuleType.TEMPTATION_COOLDOWN_TICKS, MemoryStatus.VALUE_ABSENT);
            builder.put(MemoryModuleType.IS_TEMPTED, MemoryStatus.VALUE_ABSENT);
            builder.put(MemoryModuleType.TEMPTING_PLAYER, MemoryStatus.VALUE_PRESENT);
            builder.put(MemoryModuleType.BREED_TARGET, MemoryStatus.VALUE_ABSENT);
            builder.put(MemoryModuleType.IS_PANICKING, MemoryStatus.VALUE_ABSENT);
            return builder.build();
        }));
        this.speedModifier = speedModifier;
        this.closeEnoughDistance = closeEnoughDistance;
        this.lookInTheEyes = lookInTheEyes;
    }

    protected float getSpeedModifier(PathfinderMob pathfinder) {
        return this.speedModifier.apply(pathfinder);
    }

    private Optional<Player> getTemptingPlayer(PathfinderMob pathfinder) {
        return pathfinder.getBrain().getMemory(MemoryModuleType.TEMPTING_PLAYER);
    }

    @Override
    protected boolean timedOut(long gameTime) {
        return false;
    }

    @Override
    protected boolean canStillUse(ServerLevel level, PathfinderMob entity, long gameTime) {
        return this.getTemptingPlayer(entity).isPresent()
            && !entity.getBrain().hasMemoryValue(MemoryModuleType.BREED_TARGET)
            && !entity.getBrain().hasMemoryValue(MemoryModuleType.IS_PANICKING);
    }

    @Override
    protected void start(ServerLevel level, PathfinderMob entity, long gameTime) {
        entity.getBrain().setMemory(MemoryModuleType.IS_TEMPTED, true);
    }

    @Override
    protected void stop(ServerLevel level, PathfinderMob entity, long gameTime) {
        Brain<?> brain = entity.getBrain();
        brain.setMemory(MemoryModuleType.TEMPTATION_COOLDOWN_TICKS, 100);
        brain.eraseMemory(MemoryModuleType.IS_TEMPTED);
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
    }

    @Override
    protected void tick(ServerLevel level, PathfinderMob owner, long gameTime) {
        Player player = this.getTemptingPlayer(owner).get();
        Brain<?> brain = owner.getBrain();
        brain.setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(player, true));
        double d = this.closeEnoughDistance.apply(owner);
        if (owner.distanceToSqr(player) < Mth.square(d)) {
            brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        } else {
            brain.setMemory(
                MemoryModuleType.WALK_TARGET,
                new WalkTarget(new EntityTracker(player, this.lookInTheEyes, this.lookInTheEyes), this.getSpeedModifier(owner), 2)
            );
        }
    }
}
