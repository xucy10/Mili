package net.minecraft.world.entity.ai.behavior;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.pathfinder.Path;
import org.apache.commons.lang3.mutable.MutableLong;
import org.jspecify.annotations.Nullable;

public class AcquirePoi {
    public static final int SCAN_RANGE = 48;

    public static BehaviorControl<PathfinderMob> create(
        Predicate<Holder<PoiType>> acquirablePois,
        MemoryModuleType<GlobalPos> acquiringMemory,
        boolean onlyIfAdult,
        Optional<Byte> entityEventId,
        BiPredicate<ServerLevel, BlockPos> predicate
    ) {
        return create(acquirablePois, acquiringMemory, acquiringMemory, onlyIfAdult, entityEventId, predicate);
    }

    public static BehaviorControl<PathfinderMob> create(
        Predicate<Holder<PoiType>> acquirablePois, MemoryModuleType<GlobalPos> acquiringMemory, boolean onlyIfAdult, Optional<Byte> entityEventId
    ) {
        return create(acquirablePois, acquiringMemory, acquiringMemory, onlyIfAdult, entityEventId, (serverLevel, blockPos) -> true);
    }

    public static BehaviorControl<PathfinderMob> create(
        Predicate<Holder<PoiType>> acquirablePois,
        MemoryModuleType<GlobalPos> existingAbsentMemory,
        MemoryModuleType<GlobalPos> acquiringMemory,
        boolean onlyIfAdult,
        Optional<Byte> entityEventId,
        BiPredicate<ServerLevel, BlockPos> predicate
    ) {
        int i = 5;
        int i1 = 20;
        MutableLong mutableLong = new MutableLong(0L);
        Long2ObjectMap<AcquirePoi.JitteredLinearRetry> map = new Long2ObjectOpenHashMap<>();
        OneShot<PathfinderMob> oneShot = BehaviorBuilder.create(
            instance -> instance.group(instance.absent(acquiringMemory))
                .apply(
                    instance,
                    memoryAccessor -> (level, mob, time) -> {
                        if (onlyIfAdult && mob.isBaby()) {
                            return false;
                        } else if (mutableLong.longValue() == 0L) {
                            mutableLong.setValue(level.getGameTime() + level.random.nextInt(20));
                            return false;
                        } else if (level.getGameTime() < mutableLong.longValue()) {
                            return false;
                        } else {
                            mutableLong.setValue(time + 20L + level.getRandom().nextInt(20));
                            if (level.paperConfig().entities.behavior.stuckEntityPoiRetryDelay.enabled() && mob.getNavigation().isStuck()) mutableLong.add(level.paperConfig().entities.behavior.stuckEntityPoiRetryDelay.intValue()); // Paper - Next stuck check delay config
                            PoiManager poiManager = level.getPoiManager();
                            map.long2ObjectEntrySet().removeIf(entry -> !entry.getValue().isStillValid(time));
                            Predicate<BlockPos> predicate1 = pos -> {
                                AcquirePoi.JitteredLinearRetry jitteredLinearRetry = map.get(pos.asLong());
                                if (jitteredLinearRetry == null) {
                                    return true;
                                } else if (!jitteredLinearRetry.shouldRetry(time)) {
                                    return false;
                                } else {
                                    jitteredLinearRetry.markAttempt(time);
                                    return true;
                                }
                            };
                            // Paper start - optimise POI access
                            final java.util.List<Pair<Holder<PoiType>, BlockPos>> poiposes = new java.util.ArrayList<>();
                            io.papermc.paper.util.PoiAccess.findNearestPoiPositions(poiManager, acquirablePois, predicate1, mob.blockPosition(), 48, 48*48, PoiManager.Occupancy.HAS_SPACE, false, 5, poiposes);
                            final Set<Pair<Holder<PoiType>, BlockPos>> set = new java.util.HashSet<>(poiposes.size());
                            for (final Pair<Holder<PoiType>, BlockPos> poiPose : poiposes) {
                                if (predicate.test(level, poiPose.getSecond())) {
                                    set.add(poiPose);
                                }
                            }
                            // Paper end - optimise POI access
                            Path path = findPathToPois(mob, set);
                            if (path != null && path.canReach()) {
                                BlockPos target = path.getTarget();
                                poiManager.getType(target).ifPresent(holder -> {
                                    poiManager.take(acquirablePois, (holder1, blockPos) -> blockPos.equals(target), target, 1);
                                    memoryAccessor.set(GlobalPos.of(level.dimension(), target));
                                    entityEventId.ifPresent(id -> level.broadcastEntityEvent(mob, id));
                                    map.clear();
                                    level.debugSynchronizers().updatePoi(target);
                                });
                            } else {
                                for (Pair<Holder<PoiType>, BlockPos> pair : set) {
                                    map.computeIfAbsent(pair.getSecond().asLong(), l -> new AcquirePoi.JitteredLinearRetry(level.random, time));
                                }
                            }

                            return true;
                        }
                    }
                )
        );
        return acquiringMemory == existingAbsentMemory
            ? oneShot
            : BehaviorBuilder.create(instance -> instance.group(instance.absent(existingAbsentMemory)).apply(instance, memoryAccessor -> oneShot));
    }

    public static @Nullable Path findPathToPois(Mob mob, Set<Pair<Holder<PoiType>, BlockPos>> poiPositions) {
        if (poiPositions.isEmpty()) {
            return null;
        } else {
            Set<BlockPos> set = new HashSet<>();
            int i = 1;

            for (Pair<Holder<PoiType>, BlockPos> pair : poiPositions) {
                i = Math.max(i, pair.getFirst().value().validRange());
                set.add(pair.getSecond());
            }

            return mob.getNavigation().createPath(set, i);
        }
    }

    static class JitteredLinearRetry {
        private static final int MIN_INTERVAL_INCREASE = 40;
        private static final int MAX_INTERVAL_INCREASE = 80;
        private static final int MAX_RETRY_PATHFINDING_INTERVAL = 400;
        private final RandomSource random;
        private long previousAttemptTimestamp;
        private long nextScheduledAttemptTimestamp;
        private int currentDelay;

        JitteredLinearRetry(RandomSource random, long timestamp) {
            this.random = random;
            this.markAttempt(timestamp);
        }

        public void markAttempt(long timestamp) {
            this.previousAttemptTimestamp = timestamp;
            int i = this.currentDelay + this.random.nextInt(40) + 40;
            this.currentDelay = Math.min(i, 400);
            this.nextScheduledAttemptTimestamp = timestamp + this.currentDelay;
        }

        public boolean isStillValid(long timestamp) {
            return timestamp - this.previousAttemptTimestamp < 400L;
        }

        public boolean shouldRetry(long timestamp) {
            return timestamp >= this.nextScheduledAttemptTimestamp;
        }

        @Override
        public String toString() {
            return "RetryMarker{, previousAttemptAt="
                + this.previousAttemptTimestamp
                + ", nextScheduledAttemptAt="
                + this.nextScheduledAttemptTimestamp
                + ", currentDelay="
                + this.currentDelay
                + "}";
        }
    }
}
