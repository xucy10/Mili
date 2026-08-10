package net.minecraft.world.entity.ai;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.collect.ImmutableList.Builder;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.memory.ExpirableValue;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.mutable.MutableObject;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class Brain<E extends LivingEntity> {
    static final Logger LOGGER = LogUtils.getLogger();
    private final Supplier<Codec<Brain<E>>> codec;
    private static final int SCHEDULE_UPDATE_DELAY = 20;
    private final Map<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> memories = new it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap<>(); // Leaf - Replace brain maps with optimized collection
    private final Map<SensorType<? extends Sensor<? super E>>, Sensor<? super E>> sensors = new it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap<>(); // Leaf - Replace brain maps with optimized collection
    private final Map<Integer, Map<Activity, Set<BehaviorControl<? super E>>>> availableBehaviorsByPriority = Maps.newTreeMap();
    private @Nullable EnvironmentAttribute<Activity> schedule;
    private final Map<Activity, Set<Pair<MemoryModuleType<?>, MemoryStatus>>> activityRequirements = new it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap<>(); // Leaf - Replace brain maps with optimized collection
    private final Map<Activity, Set<MemoryModuleType<?>>> activityMemoriesToEraseWhenStopped = new it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap<>(); // Leaf - Replace brain maps with optimized collection
    private Set<Activity> coreActivities = new it.unimi.dsi.fastutil.objects.ObjectOpenHashSet<>(); // Leaf - Replace brain maps with optimized collection
    private final Set<Activity> activeActivities = new it.unimi.dsi.fastutil.objects.ObjectOpenHashSet<>(); // Leaf - Replace brain maps with optimized collection
    private Activity defaultActivity = Activity.IDLE;
    private long lastScheduleUpdate = -9999L;

    public static <E extends LivingEntity> Brain.Provider<E> provider(
        Collection<? extends MemoryModuleType<?>> memoryTypes, Collection<? extends SensorType<? extends Sensor<? super E>>> sensorTypes
    ) {
        return new Brain.Provider<>(memoryTypes, sensorTypes);
    }

    public static <E extends LivingEntity> Codec<Brain<E>> codec(
        final Collection<? extends MemoryModuleType<?>> memoryTypes, final Collection<? extends SensorType<? extends Sensor<? super E>>> sensorTypes
    ) {
        final MutableObject<Codec<Brain<E>>> mutableObject = new MutableObject<>();
        mutableObject.setValue(
            (new MapCodec<Brain<E>>() {
                    @Override
                    public <T> Stream<T> keys(DynamicOps<T> ops) {
                        return memoryTypes.stream()
                            .flatMap(
                                memoryModuleType -> memoryModuleType.getCodec()
                                    .map(codec -> BuiltInRegistries.MEMORY_MODULE_TYPE.getKey((MemoryModuleType<?>)memoryModuleType))
                                    .stream()
                            )
                            .map(identifier -> ops.createString(identifier.toString()));
                    }

                    @Override
                    public <T> DataResult<Brain<E>> decode(DynamicOps<T> ops, MapLike<T> input) {
                        MutableObject<DataResult<Builder<Brain.MemoryValue<?>>>> mutableObject1 = new MutableObject<>(
                            DataResult.success(ImmutableList.builder())
                        );
                        input.entries()
                            .forEach(
                                pair -> {
                                    DataResult<MemoryModuleType<?>> dataResult = BuiltInRegistries.MEMORY_MODULE_TYPE.byNameCodec().parse(ops, pair.getFirst());
                                    DataResult<? extends Brain.MemoryValue<?>> dataResult1 = dataResult.flatMap(
                                        memoryModuleType -> this.captureRead((MemoryModuleType<T>)memoryModuleType, ops, (T)pair.getSecond())
                                    );
                                    mutableObject1.setValue(mutableObject1.get().apply2(Builder::add, dataResult1));
                                }
                            );
                        ImmutableList<Brain.MemoryValue<?>> list = mutableObject1.get()
                            .resultOrPartial(Brain.LOGGER::error)
                            .map(Builder::build)
                            .orElseGet(ImmutableList::of);
                        return DataResult.success(new Brain<>(memoryTypes, sensorTypes, list, mutableObject));
                    }

                    private <T, U> DataResult<Brain.MemoryValue<U>> captureRead(MemoryModuleType<U> memoryModuleType, DynamicOps<T> dynamicOps, T object) {
                        return memoryModuleType.getCodec()
                            .map(DataResult::success)
                            .orElseGet(() -> DataResult.error(() -> "No codec for memory: " + memoryModuleType))
                            .<ExpirableValue<U>>flatMap(codec -> codec.parse(dynamicOps, object))
                            .map(expirableValue -> new Brain.MemoryValue<>(memoryModuleType, Optional.of(expirableValue)));
                    }

                    @Override
                    public <T> RecordBuilder<T> encode(Brain<E> input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
                        input.memories().forEach(memoryValue -> memoryValue.serialize(ops, prefix));
                        return prefix;
                    }
                })
                .fieldOf("memories")
                .codec()
        );
        return mutableObject.get();
    }

    public Brain(
        Collection<? extends MemoryModuleType<?>> memoryModuleTypes,
        Collection<? extends SensorType<? extends Sensor<? super E>>> sensorTypes,
        ImmutableList<Brain.MemoryValue<?>> memoryValues,
        Supplier<Codec<Brain<E>>> codec
    ) {
        this.codec = codec;

        for (MemoryModuleType<?> memoryModuleType : memoryModuleTypes) {
            this.memories.put(memoryModuleType, Optional.empty());
        }

        for (SensorType<? extends Sensor<? super E>> sensorType : sensorTypes) {
            this.sensors.put(sensorType, (Sensor<? super E>)sensorType.create());
        }

        for (Sensor<? super E> sensor : this.sensors.values()) {
            for (MemoryModuleType<?> memoryModuleType1 : sensor.requires()) {
                this.memories.put(memoryModuleType1, Optional.empty());
            }
        }

        for (Brain.MemoryValue<?> memoryValue : memoryValues) {
            memoryValue.setMemoryInternal(this);
        }
    }

    public <T> DataResult<T> serializeStart(DynamicOps<T> ops) {
        return this.codec.get().encodeStart(ops, this);
    }

    Stream<Brain.MemoryValue<?>> memories() {
        return this.memories.entrySet().stream().map(memory -> Brain.MemoryValue.createUnchecked(memory.getKey(), memory.getValue()));
    }

    public boolean hasMemoryValue(MemoryModuleType<?> type) {
        return this.checkMemory(type, MemoryStatus.VALUE_PRESENT);
    }

    public void clearMemories() {
        this.memories.keySet().forEach(memoryModuleType -> this.memories.put((MemoryModuleType<?>)memoryModuleType, Optional.empty()));
    }

    public <U> void eraseMemory(MemoryModuleType<U> type) {
        this.setMemory(type, Optional.empty());
    }

    public <U> void setMemory(MemoryModuleType<U> memoryType, @Nullable U memory) {
        this.setMemory(memoryType, Optional.ofNullable(memory));
    }

    public <U> void setMemoryWithExpiry(MemoryModuleType<U> memoryType, U memory, long timeToLive) {
        this.setMemoryInternal(memoryType, Optional.of(ExpirableValue.of(memory, timeToLive)));
    }

    public <U> void setMemory(MemoryModuleType<U> memoryType, Optional<? extends U> memory) {
        this.setMemoryInternal(memoryType, memory.map(ExpirableValue::of));
    }

    <U> void setMemoryInternal(MemoryModuleType<U> memoryType, Optional<? extends ExpirableValue<?>> memory) {
        if (this.memories.containsKey(memoryType)) {
            if (memory.isPresent() && this.isEmptyCollection(memory.get().getValue())) {
                this.eraseMemory(memoryType);
            } else {
                this.memories.put(memoryType, memory);
            }
        }
    }

    public <U> Optional<U> getMemory(MemoryModuleType<U> type) {
        Optional<? extends ExpirableValue<?>> optional = this.memories.get(type);
        if (optional == null) {
            throw new IllegalStateException("Unregistered memory fetched: " + type);
        } else {
            return (Optional<U>) optional.map(ExpirableValue::getValue);
        }
    }

    public <U> @Nullable Optional<U> getMemoryInternal(MemoryModuleType<U> type) {
        Optional<? extends ExpirableValue<?>> optional = this.memories.get(type);
        return optional == null ? null : (Optional<U>) optional.map(ExpirableValue::getValue);
    }

    public <U> long getTimeUntilExpiry(MemoryModuleType<U> memoryType) {
        Optional<? extends ExpirableValue<?>> optional = this.memories.get(memoryType);
        return optional.map(ExpirableValue::getTimeToLive).orElse(0L);
    }

    @Deprecated
    @VisibleForDebug
    public Map<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> getMemories() {
        return this.memories;
    }

    public <U> boolean isMemoryValue(MemoryModuleType<U> memoryType, U memory) {
        return this.hasMemoryValue(memoryType) && this.getMemory(memoryType).filter(object -> object.equals(memory)).isPresent();
    }

    public boolean checkMemory(MemoryModuleType<?> memoryType, MemoryStatus memoryStatus) {
        Optional<? extends ExpirableValue<?>> optional = this.memories.get(memoryType);
        return optional != null
            && (
                memoryStatus == MemoryStatus.REGISTERED
                    || memoryStatus == MemoryStatus.VALUE_PRESENT && optional.isPresent()
                    || memoryStatus == MemoryStatus.VALUE_ABSENT && optional.isEmpty()
            );
    }

    public void setSchedule(EnvironmentAttribute<Activity> newSchedule) {
        this.schedule = newSchedule;
    }

    public void setCoreActivities(Set<Activity> newActivities) {
        this.coreActivities = newActivities;
    }

    @Deprecated
    @VisibleForDebug
    public Set<Activity> getActiveActivities() {
        return this.activeActivities;
    }

    @Deprecated
    @VisibleForDebug
    public List<BehaviorControl<? super E>> getRunningBehaviors() {
        List<BehaviorControl<? super E>> list = new ObjectArrayList<>();

        for (Map<Activity, Set<BehaviorControl<? super E>>> map : this.availableBehaviorsByPriority.values()) {
            for (Set<BehaviorControl<? super E>> set : map.values()) {
                for (BehaviorControl<? super E> behaviorControl : set) {
                    if (behaviorControl.getStatus() == Behavior.Status.RUNNING) {
                        list.add(behaviorControl);
                    }
                }
            }
        }

        return list;
    }

    public void useDefaultActivity() {
        this.setActiveActivity(this.defaultActivity);
    }

    public Optional<Activity> getActiveNonCoreActivity() {
        for (Activity activity : this.activeActivities) {
            if (!this.coreActivities.contains(activity)) {
                return Optional.of(activity);
            }
        }

        return Optional.empty();
    }

    public void setActiveActivityIfPossible(Activity activity) {
        if (this.activityRequirementsAreMet(activity)) {
            this.setActiveActivity(activity);
        } else {
            this.useDefaultActivity();
        }
    }

    private void setActiveActivity(Activity activity) {
        if (!this.isActive(activity)) {
            this.eraseMemoriesForOtherActivitesThan(activity);
            this.activeActivities.clear();
            this.activeActivities.addAll(this.coreActivities);
            this.activeActivities.add(activity);
        }
    }

    private void eraseMemoriesForOtherActivitesThan(Activity activity) {
        for (Activity activity1 : this.activeActivities) {
            if (activity1 != activity) {
                Set<MemoryModuleType<?>> set = this.activityMemoriesToEraseWhenStopped.get(activity1);
                if (set != null) {
                    for (MemoryModuleType<?> memoryModuleType : set) {
                        this.eraseMemory(memoryModuleType);
                    }
                }
            }
        }
    }

    public void updateActivityFromSchedule(EnvironmentAttributeSystem environmentAttributes, long dayTime, Vec3 pos) {
        if (dayTime - this.lastScheduleUpdate > 20L) {
            this.lastScheduleUpdate = dayTime;
            Activity activity = this.schedule != null ? environmentAttributes.getValue(this.schedule, pos) : Activity.IDLE;
            if (!this.activeActivities.contains(activity)) {
                this.setActiveActivityIfPossible(activity);
            }
        }
    }

    public void setActiveActivityToFirstValid(List<Activity> activities) {
        for (Activity activity : activities) {
            if (this.activityRequirementsAreMet(activity)) {
                this.setActiveActivity(activity);
                break;
            }
        }
    }

    public void setDefaultActivity(Activity newFallbackActivity) {
        this.defaultActivity = newFallbackActivity;
    }

    public void addActivity(Activity activity, int priorityStart, ImmutableList<? extends BehaviorControl<? super E>> tasks) {
        this.addActivity(activity, this.createPriorityPairs(priorityStart, tasks));
    }

    public void addActivityAndRemoveMemoryWhenStopped(
        Activity activity, int priorityStart, ImmutableList<? extends BehaviorControl<? super E>> tasks, MemoryModuleType<?> memoryType
    ) {
        Set<Pair<MemoryModuleType<?>, MemoryStatus>> set = ImmutableSet.of(Pair.of(memoryType, MemoryStatus.VALUE_PRESENT));
        Set<MemoryModuleType<?>> set1 = ImmutableSet.of(memoryType);
        this.addActivityAndRemoveMemoriesWhenStopped(activity, this.createPriorityPairs(priorityStart, tasks), set, set1);
    }

    public void addActivity(Activity activity, ImmutableList<? extends Pair<Integer, ? extends BehaviorControl<? super E>>> tasks) {
        this.addActivityAndRemoveMemoriesWhenStopped(activity, tasks, ImmutableSet.of(), Sets.newHashSet());
    }

    public void addActivityWithConditions(
        Activity activity,
        int priorityStart,
        ImmutableList<? extends BehaviorControl<? super E>> tasks,
        Set<Pair<MemoryModuleType<?>, MemoryStatus>> memoryStatuses
    ) {
        this.addActivityWithConditions(activity, this.createPriorityPairs(priorityStart, tasks), memoryStatuses);
    }

    public void addActivityWithConditions(
        Activity activity,
        ImmutableList<? extends Pair<Integer, ? extends BehaviorControl<? super E>>> tasks,
        Set<Pair<MemoryModuleType<?>, MemoryStatus>> memoryStatuses
    ) {
        this.addActivityAndRemoveMemoriesWhenStopped(activity, tasks, memoryStatuses, Sets.newHashSet());
    }

    public void addActivityAndRemoveMemoriesWhenStopped(
        Activity activity,
        ImmutableList<? extends Pair<Integer, ? extends BehaviorControl<? super E>>> tasks,
        Set<Pair<MemoryModuleType<?>, MemoryStatus>> memoryStatuses,
        Set<MemoryModuleType<?>> memoryTypes
    ) {
        this.activityRequirements.put(activity, memoryStatuses);
        if (!memoryTypes.isEmpty()) {
            this.activityMemoriesToEraseWhenStopped.put(activity, memoryTypes);
        }

        for (Pair<Integer, ? extends BehaviorControl<? super E>> pair : tasks) {
            this.availableBehaviorsByPriority
                .computeIfAbsent(pair.getFirst(), integer -> Maps.newHashMap())
                .computeIfAbsent(activity, activity1 -> Sets.newLinkedHashSet())
                .add((BehaviorControl<? super E>)pair.getSecond());
        }
    }

    @VisibleForTesting
    public void removeAllBehaviors() {
        this.availableBehaviorsByPriority.clear();
    }

    public boolean isActive(Activity activity) {
        return this.activeActivities.contains(activity);
    }

    public Brain<E> copyWithoutBehaviors() {
        Brain<E> brain = new Brain<>(this.memories.keySet(), this.sensors.keySet(), ImmutableList.of(), this.codec);

        for (Entry<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> entry : this.memories.entrySet()) {
            MemoryModuleType<?> memoryModuleType = entry.getKey();
            if (entry.getValue().isPresent()) {
                brain.memories.put(memoryModuleType, entry.getValue());
            }
        }

        return brain;
    }

    public void tick(ServerLevel level, E entity) {
        this.forgetOutdatedMemories(entity); // Luminol - Add config to force clean entity memory that don't belong to current tick region
        this.tickSensors(level, entity);
        this.startEachNonRunningBehavior(level, entity);
        this.tickEachRunningBehavior(level, entity);
    }

    private void tickSensors(ServerLevel level, E brainHolder) {
        for (Sensor<? super E> sensor : this.sensors.values()) {
            sensor.tick(level, brainHolder);
        }
    }

    private void forgetOutdatedMemories(E owner) { // Luminol - Add config to force clean entity memory that don't belong to current tick region
        for (Entry<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> entry : this.memories.entrySet()) {
            if (entry.getValue().isPresent()) {
                ExpirableValue<?> expirableValue = (ExpirableValue<?>)entry.getValue().get();
                // Luminol start - Add config to force clean entity memory that don't belong to current tick region
                final Object value = expirableValue.getValue();
                final net.minecraft.world.level.Level ownerLevel = owner.level();

                // type: entity
                if (me.earthme.luminol.config.modules.fixes.ForceCleanupEntityBrainMemoryConfig.enabledForEntity && value instanceof LivingEntity entity) {
                    if (!ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(entity)) {
                        this.eraseMemory(entry.getKey());
                        continue;
                    }
                }

                // type: block_pos
                if (me.earthme.luminol.config.modules.fixes.ForceCleanupEntityBrainMemoryConfig.enabledForBlockPos && value instanceof net.minecraft.core.BlockPos blockPos) {
                    if (!ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(ownerLevel, blockPos)) {
                        this.eraseMemory(entry.getKey());
                        continue;
                    }
                }

                //type: position_tracker and walk_target
                if (me.earthme.luminol.config.modules.fixes.ForceCleanupEntityBrainMemoryConfig.enabledForPositionTracker) {
                    net.minecraft.world.entity.ai.behavior.PositionTracker tracker = null;
                    
                    if (value instanceof net.minecraft.world.entity.ai.behavior.PositionTracker positionTracker) {
                        tracker = positionTracker;
                    }

                    if (value instanceof net.minecraft.world.entity.ai.memory.WalkTarget walkTarget) {
                        tracker = walkTarget.getTarget();
                    }
                    
                    if (tracker != null && !tracker.checkThread(owner.level())) {
                        this.eraseMemory(entry.getKey());
                        continue;
                    }
                }
                // Luminol end

                if (expirableValue.hasExpired()) {
                    this.eraseMemory(entry.getKey());
                }

                expirableValue.tick();
            }
        }
    }

    public void stopAll(ServerLevel level, E owner) {
        // Folia start - region threading
        List<BehaviorControl<? super E>> behaviors = this.getRunningBehaviors();
        if (behaviors.isEmpty()) {
            // avoid calling getGameTime, as this may be called while portalling an entity - which will cause
            // the world data retrieval to fail
            return;
        }
        // Mili fix - #431: use the level parameter instead of owner.level() to avoid
        // "World mismatch" during cross-dimension teleport (readAdditionalSaveData → refreshBrain → stopAll)
        long gameTime = level.getGameTime();
        // Folia end - region threading

        for (BehaviorControl<? super E> behaviorControl : behaviors) { // Folia - region threading
            behaviorControl.doStop(level, owner, gameTime);
        }
    }

    private void startEachNonRunningBehavior(ServerLevel level, E entity) {
        long gameTime = level.getGameTime();

        for (Map<Activity, Set<BehaviorControl<? super E>>> map : this.availableBehaviorsByPriority.values()) {
            for (Entry<Activity, Set<BehaviorControl<? super E>>> entry : map.entrySet()) {
                Activity activity = entry.getKey();
                if (this.activeActivities.contains(activity)) {
                    for (BehaviorControl<? super E> behaviorControl : entry.getValue()) {
                        if (behaviorControl.getStatus() == Behavior.Status.STOPPED) {
                            behaviorControl.tryStart(level, entity, gameTime);
                        }
                    }
                }
            }
        }
    }

    private void tickEachRunningBehavior(ServerLevel level, E entity) {
        long gameTime = level.getGameTime();

        for (BehaviorControl<? super E> behaviorControl : this.getRunningBehaviors()) {
            behaviorControl.tickOrStop(level, entity, gameTime);
        }
    }

    private boolean activityRequirementsAreMet(Activity activity) {
        if (!this.activityRequirements.containsKey(activity)) {
            return false;
        } else {
            for (Pair<MemoryModuleType<?>, MemoryStatus> pair : this.activityRequirements.get(activity)) {
                MemoryModuleType<?> memoryModuleType = pair.getFirst();
                MemoryStatus memoryStatus = pair.getSecond();
                if (!this.checkMemory(memoryModuleType, memoryStatus)) {
                    return false;
                }
            }

            return true;
        }
    }

    private boolean isEmptyCollection(Object collection) {
        return collection instanceof Collection && ((Collection)collection).isEmpty();
    }

    ImmutableList<? extends Pair<Integer, ? extends BehaviorControl<? super E>>> createPriorityPairs(
        int priorityStart, ImmutableList<? extends BehaviorControl<? super E>> tasks
    ) {
        int i = priorityStart;
        Builder<Pair<Integer, ? extends BehaviorControl<? super E>>> builder = ImmutableList.builder();

        for (BehaviorControl<? super E> behaviorControl : tasks) {
            builder.add(Pair.of(i++, behaviorControl));
        }

        return builder.build();
    }

    public boolean isBrainDead() {
        return this.memories.isEmpty() && this.sensors.isEmpty() && this.availableBehaviorsByPriority.isEmpty();
    }

    static final class MemoryValue<U> {
        private final MemoryModuleType<U> type;
        private final Optional<? extends ExpirableValue<U>> value;

        static <U> Brain.MemoryValue<U> createUnchecked(MemoryModuleType<U> memoryType, Optional<? extends ExpirableValue<?>> memory) {
            return new Brain.MemoryValue<>(memoryType, (Optional<? extends ExpirableValue<U>>)memory);
        }

        MemoryValue(MemoryModuleType<U> type, Optional<? extends ExpirableValue<U>> value) {
            this.type = type;
            this.value = value;
        }

        void setMemoryInternal(Brain<?> brain) {
            brain.setMemoryInternal(this.type, this.value);
        }

        public <T> void serialize(DynamicOps<T> ops, RecordBuilder<T> builder) {
            this.type
                .getCodec()
                .ifPresent(
                    codec -> this.value
                        .ifPresent(
                            expirableValue -> builder.add(
                                BuiltInRegistries.MEMORY_MODULE_TYPE.byNameCodec().encodeStart(ops, this.type),
                                codec.encodeStart(ops, (ExpirableValue<U>)expirableValue)
                            )
                        )
                );
        }
    }

    public static final class Provider<E extends LivingEntity> {
        private final Collection<? extends MemoryModuleType<?>> memoryTypes;
        private final Collection<? extends SensorType<? extends Sensor<? super E>>> sensorTypes;
        private final Codec<Brain<E>> codec;

        Provider(Collection<? extends MemoryModuleType<?>> memoryTypes, Collection<? extends SensorType<? extends Sensor<? super E>>> sensorTypes) {
            this.memoryTypes = memoryTypes;
            this.sensorTypes = sensorTypes;
            this.codec = Brain.codec(memoryTypes, sensorTypes);
        }

        public Brain<E> makeBrain(Dynamic<?> ops) {
            return this.codec
                .parse(ops)
                .resultOrPartial(Brain.LOGGER::error)
                .orElseGet(() -> new Brain<>(this.memoryTypes, this.sensorTypes, ImmutableList.of(), () -> this.codec));
        }
    }
}
