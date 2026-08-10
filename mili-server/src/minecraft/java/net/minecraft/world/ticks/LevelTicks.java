package net.minecraft.world.ticks;

import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongMaps;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.LongPredicate;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Util;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public class LevelTicks<T> implements LevelTickAccess<T> {
    private static final Comparator<LevelChunkTicks<?>> CONTAINER_DRAIN_ORDER = (levelChunkTicks, levelChunkTicks1) -> ScheduledTick.INTRA_TICK_DRAIN_ORDER
        .compare(levelChunkTicks.peek(), levelChunkTicks1.peek());
    private final LongPredicate tickCheck;
    private final Long2ObjectMap<LevelChunkTicks<T>> allContainers = new Long2ObjectOpenHashMap<>();
    private final Long2LongMap nextTickForContainer = Util.make(new Long2LongOpenHashMap(), map -> map.defaultReturnValue(Long.MAX_VALUE));
    private final Queue<LevelChunkTicks<T>> containersToTick = new PriorityQueue<>(CONTAINER_DRAIN_ORDER);
    private final Queue<ScheduledTick<T>> toRunThisTick = new ArrayDeque<>();
    private final List<ScheduledTick<T>> alreadyRunThisTick = new ArrayList<>();
    private final Set<ScheduledTick<?>> toRunThisTickSet = new ObjectOpenCustomHashSet<>(ScheduledTick.UNIQUE_TICK_HASH);
    private final BiConsumer<LevelChunkTicks<T>, ScheduledTick<T>> chunkScheduleUpdater = (levelChunkTicks, scheduledTick) -> {
        if (scheduledTick.equals(levelChunkTicks.peek())) { // Folia - diff on change
            this.updateContainerScheduling(scheduledTick); // Folia - diff on change
        }
    };

    // Folia start - region threading
    public final net.minecraft.server.level.ServerLevel world;
    public final boolean isBlock;

    public void merge(final LevelTicks<T> into, final long tickOffset) {
        // note: containersToTick, toRunThisTick, alreadyRunThisTick, toRunThisTickSet
        // are all transient state, only ever non-empty during tick. But merging regions occurs while there
        // is no tick happening, so we assume they are empty.
        for (final java.util.Iterator<Long2ObjectMap.Entry<LevelChunkTicks<T>>> iterator =
             ((Long2ObjectOpenHashMap<LevelChunkTicks<T>>)this.allContainers).long2ObjectEntrySet().fastIterator();
             iterator.hasNext();) {
            final Long2ObjectMap.Entry<LevelChunkTicks<T>> entry = iterator.next();
            final LevelChunkTicks<T> tickContainer = entry.getValue();
            tickContainer.offsetTicks(tickOffset);
            into.allContainers.put(entry.getLongKey(), tickContainer);
        }
        for (final java.util.Iterator<Long2LongMap.Entry> iterator = ((Long2LongOpenHashMap)this.nextTickForContainer).long2LongEntrySet().fastIterator();
             iterator.hasNext();) {
            final Long2LongMap.Entry entry = iterator.next();
            into.nextTickForContainer.put(entry.getLongKey(), entry.getLongValue() + tickOffset);
        }
    }

    public void split(final int chunkToRegionShift,
                      final it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap<LevelTicks<T>> regionToData) {
        for (final java.util.Iterator<Long2ObjectMap.Entry<LevelChunkTicks<T>>> iterator =
             ((Long2ObjectOpenHashMap<LevelChunkTicks<T>>)this.allContainers).long2ObjectEntrySet().fastIterator();
             iterator.hasNext();) {
            final Long2ObjectMap.Entry<LevelChunkTicks<T>> entry = iterator.next();

            final long chunkKey = entry.getLongKey();
            final int chunkX = ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkX(chunkKey);
            final int chunkZ = ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkZ(chunkKey);

            final long regionSectionKey = ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkKey(
                    chunkX >> chunkToRegionShift, chunkZ >> chunkToRegionShift
            );
            // Should always be non-null, since containers are removed on unload.
            regionToData.get(regionSectionKey).allContainers.put(chunkKey, entry.getValue());
        }
        for (final java.util.Iterator<Long2LongMap.Entry> iterator = ((Long2LongOpenHashMap)this.nextTickForContainer).long2LongEntrySet().fastIterator();
             iterator.hasNext();) {
            final Long2LongMap.Entry entry = iterator.next();
            final long chunkKey = entry.getLongKey();
            final int chunkX = ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkX(chunkKey);
            final int chunkZ = ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkZ(chunkKey);

            final long regionSectionKey = ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkKey(
                    chunkX >> chunkToRegionShift, chunkZ >> chunkToRegionShift
            );

            // Should always be non-null, since containers are removed on unload.
            regionToData.get(regionSectionKey).nextTickForContainer.put(chunkKey, entry.getLongValue());
        }
    }
    // Folia end - region threading

    public LevelTicks(LongPredicate tickCheck, net.minecraft.server.level.ServerLevel world, boolean isBlock) { this.world = world; this.isBlock = isBlock; // Folia - add world and isBlock
        this.tickCheck = tickCheck;
    }

    public void addContainer(ChunkPos chunkPos, LevelChunkTicks<T> chunkTicks) {
        long packedChunkPos = chunkPos.toLong();
        this.allContainers.put(packedChunkPos, chunkTicks);
        ScheduledTick<T> scheduledTick = chunkTicks.peek();
        if (scheduledTick != null) {
            this.nextTickForContainer.put(packedChunkPos, scheduledTick.triggerTick());
        }

        // Folia start - region threading
        final boolean isBlock = this.isBlock;
        final net.minecraft.server.level.ServerLevel world = this.world;
        // make sure the lambda contains no reference to this LevelTicks
        chunkTicks.setOnTickAdded((LevelChunkTicks<T> levelChunkTicks, ScheduledTick<T> tick) -> {
            if (tick.equals(levelChunkTicks.peek())) {
                io.papermc.paper.threadedregions.RegionizedWorldData worldData = world.getCurrentWorldData();
                ((LevelTicks<T>)(isBlock ? worldData.getBlockLevelTicks() : worldData.getFluidLevelTicks())).updateContainerScheduling(tick);
            }
        });
        // Folia end - region threading
    }

    public void removeContainer(ChunkPos chunkPos) {
        long packedChunkPos = chunkPos.toLong();
        LevelChunkTicks<T> levelChunkTicks = this.allContainers.remove(packedChunkPos);
        this.nextTickForContainer.remove(packedChunkPos);
        if (levelChunkTicks != null) {
            levelChunkTicks.setOnTickAdded(null);
        }
    }

    @Override
    public void schedule(ScheduledTick<T> tick) {
        ca.spottedleaf.moonrise.common.util.TickThread.ensureTickThread(this.world, tick.pos(), "Cannot schedule tick for another region!"); // Folia - region threading
        long packedChunkPos = ChunkPos.asLong(tick.pos());
        LevelChunkTicks<T> levelChunkTicks = this.allContainers.get(packedChunkPos);
        if (levelChunkTicks == null) {
            Util.logAndPauseIfInIde("Trying to schedule tick in not loaded position " + tick.pos());
        } else {
            levelChunkTicks.schedule(tick);
        }
    }

    public void tick(long gameTime, int maxAllowedTicks, BiConsumer<BlockPos, T> ticker) {
        ProfilerFiller profilerFiller = Profiler.get();
        profilerFiller.push("collect");
        this.collectTicks(gameTime, maxAllowedTicks, profilerFiller);
        profilerFiller.popPush("run");
        profilerFiller.incrementCounter("ticksToRun", this.toRunThisTick.size());
        this.runCollectedTicks(ticker);
        profilerFiller.popPush("cleanup");
        this.cleanupAfterTick();
        profilerFiller.pop();
    }

    private void collectTicks(long gameTime, int maxAllowedTicks, ProfilerFiller profiler) {
        this.sortContainersToTick(gameTime);
        profiler.incrementCounter("containersToTick", this.containersToTick.size());
        this.drainContainers(gameTime, maxAllowedTicks);
        this.rescheduleLeftoverContainers();
    }

    private void sortContainersToTick(long gameTime) {
        ObjectIterator<Entry> objectIterator = Long2LongMaps.fastIterator(this.nextTickForContainer);

        while (objectIterator.hasNext()) {
            Entry entry = objectIterator.next();
            long longKey = entry.getLongKey();
            long longValue = entry.getLongValue();
            if (longValue <= gameTime) {
                LevelChunkTicks<T> levelChunkTicks = this.allContainers.get(longKey);
                if (levelChunkTicks == null) {
                    objectIterator.remove();
                } else {
                    ScheduledTick<T> scheduledTick = levelChunkTicks.peek();
                    if (scheduledTick == null) {
                        objectIterator.remove();
                    } else if (scheduledTick.triggerTick() > gameTime) {
                        entry.setValue(scheduledTick.triggerTick());
                    } else if (this.tickCheck.test(longKey)) {
                        objectIterator.remove();
                        this.containersToTick.add(levelChunkTicks);
                    }
                }
            }
        }
    }

    private void drainContainers(long gameTime, int maxAllowedTicks) {
        LevelChunkTicks<T> levelChunkTicks;
        while (this.canScheduleMoreTicks(maxAllowedTicks) && (levelChunkTicks = this.containersToTick.poll()) != null) {
            ScheduledTick<T> scheduledTick = levelChunkTicks.poll();
            this.scheduleForThisTick(scheduledTick);
            this.drainFromCurrentContainer(this.containersToTick, levelChunkTicks, gameTime, maxAllowedTicks);
            ScheduledTick<T> scheduledTick1 = levelChunkTicks.peek();
            if (scheduledTick1 != null) {
                if (scheduledTick1.triggerTick() <= gameTime && this.canScheduleMoreTicks(maxAllowedTicks)) {
                    this.containersToTick.add(levelChunkTicks);
                } else {
                    this.updateContainerScheduling(scheduledTick1);
                }
            }
        }
    }

    private void rescheduleLeftoverContainers() {
        for (LevelChunkTicks<T> levelChunkTicks : this.containersToTick) {
            this.updateContainerScheduling(levelChunkTicks.peek());
        }
    }

    private void updateContainerScheduling(ScheduledTick<T> tick) {
        this.nextTickForContainer.put(ChunkPos.asLong(tick.pos()), tick.triggerTick());
    }

    private void drainFromCurrentContainer(Queue<LevelChunkTicks<T>> containersToTick, LevelChunkTicks<T> levelChunkTicks, long gameTime, int maxAllowedTicks) {
        if (this.canScheduleMoreTicks(maxAllowedTicks)) {
            LevelChunkTicks<T> levelChunkTicks1 = containersToTick.peek();
            ScheduledTick<T> scheduledTick = levelChunkTicks1 != null ? levelChunkTicks1.peek() : null;

            while (this.canScheduleMoreTicks(maxAllowedTicks)) {
                ScheduledTick<T> scheduledTick1 = levelChunkTicks.peek();
                if (scheduledTick1 == null
                    || scheduledTick1.triggerTick() > gameTime
                    || scheduledTick != null && ScheduledTick.INTRA_TICK_DRAIN_ORDER.compare(scheduledTick1, scheduledTick) > 0) {
                    break;
                }

                levelChunkTicks.poll();
                this.scheduleForThisTick(scheduledTick1);
            }
        }
    }

    private void scheduleForThisTick(ScheduledTick<T> tick) {
        this.toRunThisTick.add(tick);
    }

    private boolean canScheduleMoreTicks(int maxAllowedTicks) {
        return this.toRunThisTick.size() < maxAllowedTicks;
    }

    private void runCollectedTicks(BiConsumer<BlockPos, T> ticker) {
        // Folia start - profiler
        io.papermc.paper.threadedregions.TickRegionScheduler.getProfiler().addCounter(
            ca.spottedleaf.leafprofiler.LProfilerRegistry.BLOCK_OR_FLUID_TICK_COUNT,
            (long)this.toRunThisTick.size()
        );
        // Folia end - profiler
        while (!this.toRunThisTick.isEmpty()) {
            ScheduledTick<T> scheduledTick = this.toRunThisTick.poll();
            if (!this.toRunThisTickSet.isEmpty()) {
                this.toRunThisTickSet.remove(scheduledTick);
            }

            this.alreadyRunThisTick.add(scheduledTick);
            ticker.accept(scheduledTick.pos(), scheduledTick.type());
        }
    }

    private void cleanupAfterTick() {
        this.toRunThisTick.clear();
        this.containersToTick.clear();
        this.alreadyRunThisTick.clear();
        this.toRunThisTickSet.clear();
    }

    @Override
    public boolean hasScheduledTick(BlockPos pos, T type) {
        LevelChunkTicks<T> levelChunkTicks = this.allContainers.get(ChunkPos.asLong(pos));
        return levelChunkTicks != null && levelChunkTicks.hasScheduledTick(pos, type);
    }

    @Override
    public boolean willTickThisTick(BlockPos pos, T type) {
        this.calculateTickSetIfNeeded();
        return this.toRunThisTickSet.contains(ScheduledTick.probe(type, pos));
    }

    private void calculateTickSetIfNeeded() {
        if (this.toRunThisTickSet.isEmpty() && !this.toRunThisTick.isEmpty()) {
            this.toRunThisTickSet.addAll(this.toRunThisTick);
        }
    }

    private void forContainersInArea(BoundingBox area, LevelTicks.PosAndContainerConsumer<T> action) {
        int sectionPosMinX = SectionPos.posToSectionCoord(area.minX());
        int sectionPosMinZ = SectionPos.posToSectionCoord(area.minZ());
        int sectionPosMaxX = SectionPos.posToSectionCoord(area.maxX());
        int sectionPosMaxZ = SectionPos.posToSectionCoord(area.maxZ());

        for (int i = sectionPosMinX; i <= sectionPosMaxX; i++) {
            for (int i1 = sectionPosMinZ; i1 <= sectionPosMaxZ; i1++) {
                long packedChunkPos = ChunkPos.asLong(i, i1);
                LevelChunkTicks<T> levelChunkTicks = this.allContainers.get(packedChunkPos);
                if (levelChunkTicks != null) {
                    action.accept(packedChunkPos, levelChunkTicks);
                }
            }
        }
    }

    public void clearArea(BoundingBox area) {
        Predicate<ScheduledTick<T>> predicate = scheduledTick -> area.isInside(scheduledTick.pos());
        this.forContainersInArea(area, (pos, container) -> {
            ScheduledTick<T> scheduledTick = container.peek();
            container.removeIf(predicate);
            ScheduledTick<T> scheduledTick1 = container.peek();
            if (scheduledTick1 != scheduledTick) {
                if (scheduledTick1 != null) {
                    this.updateContainerScheduling(scheduledTick1);
                } else {
                    this.nextTickForContainer.remove(pos);
                }
            }
        });
        this.alreadyRunThisTick.removeIf(predicate);
        this.toRunThisTick.removeIf(predicate);
    }

    public void copyArea(BoundingBox area, Vec3i offset) {
        this.copyAreaFrom(this, area, offset);
    }

    public void copyAreaFrom(LevelTicks<T> levelTicks, BoundingBox area, Vec3i offset) {
        List<ScheduledTick<T>> list = new ArrayList<>();
        Predicate<ScheduledTick<T>> predicate = scheduledTick -> area.isInside(scheduledTick.pos());
        levelTicks.alreadyRunThisTick.stream().filter(predicate).forEach(list::add);
        levelTicks.toRunThisTick.stream().filter(predicate).forEach(list::add);
        levelTicks.forContainersInArea(area, (pos, container) -> container.getAll().filter(predicate).forEach(list::add));
        LongSummaryStatistics longSummaryStatistics = list.stream().mapToLong(ScheduledTick::subTickOrder).summaryStatistics();
        long min = longSummaryStatistics.getMin();
        long max = longSummaryStatistics.getMax();
        list.forEach(
            scheduledTick -> this.schedule(
                new ScheduledTick<>(
                    scheduledTick.type(),
                    scheduledTick.pos().offset(offset),
                    scheduledTick.triggerTick(),
                    scheduledTick.priority(),
                    scheduledTick.subTickOrder() - min + max + 1L
                )
            )
        );
    }

    @Override
    public int count() {
        return this.allContainers.values().stream().mapToInt(TickAccess::count).sum();
    }

    @FunctionalInterface
    interface PosAndContainerConsumer<T> {
        void accept(long pos, LevelChunkTicks<T> container);
    }
}
