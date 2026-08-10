package net.minecraft.world.entity.ai.sensing;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;

public abstract class Sensor<E extends LivingEntity> {
    private static final RandomSource RANDOM = RandomSource.createThreadSafe();
    private static final int DEFAULT_SCAN_RATE = 20;
    private static final int DEFAULT_TARGETING_RANGE = 16;
    private static final TargetingConditions TARGET_CONDITIONS = TargetingConditions.forNonCombat().range(16.0);
    private static final TargetingConditions TARGET_CONDITIONS_IGNORE_INVISIBILITY_TESTING = TargetingConditions.forNonCombat()
        .range(16.0)
        .ignoreInvisibilityTesting();
    private static final TargetingConditions ATTACK_TARGET_CONDITIONS = TargetingConditions.forCombat().range(16.0);
    private static final TargetingConditions ATTACK_TARGET_CONDITIONS_IGNORE_INVISIBILITY_TESTING = TargetingConditions.forCombat()
        .range(16.0)
        .ignoreInvisibilityTesting();
    private static final TargetingConditions ATTACK_TARGET_CONDITIONS_IGNORE_LINE_OF_SIGHT = TargetingConditions.forCombat().range(16.0).ignoreLineOfSight();
    private static final TargetingConditions ATTACK_TARGET_CONDITIONS_IGNORE_INVISIBILITY_AND_LINE_OF_SIGHT = TargetingConditions.forCombat()
        .range(16.0)
        .ignoreLineOfSight()
        .ignoreInvisibilityTesting();
    private final int scanRate;
    private long timeToTick;
    private final String configKey; // Paper - configurable sensor tick rate and timings

    public Sensor(int scanRate) {
        // Paper start - configurable sensor tick rate and timings
        String key = io.papermc.paper.util.MappingEnvironment.reobf() ? io.papermc.paper.util.ObfHelper.INSTANCE.deobfClassName(this.getClass().getName()) : this.getClass().getName();
        int lastSeparator = key.lastIndexOf('.');
        if (lastSeparator != -1) {
            key = key.substring(lastSeparator + 1);
        }
        this.configKey = key.toLowerCase(java.util.Locale.ROOT);
        // Paper end
        this.scanRate = scanRate;
        this.timeToTick = RANDOM.nextInt(scanRate);
    }

    public Sensor() {
        this(20);
    }

    public final void tick(ServerLevel level, E entity) {
        if (--this.timeToTick <= 0L) {
            this.timeToTick = java.util.Objects.requireNonNullElse(level.paperConfig().tickRates.sensor.get(entity.getType(), this.configKey), this.scanRate); // Paper - configurable sensor tick rate and timings
            this.updateTargetingConditionRanges(entity);
            this.doTick(level, entity);
        }
    }

    private void updateTargetingConditionRanges(E entity) {
        double attributeValue = entity.getAttributeValue(Attributes.FOLLOW_RANGE);
        TARGET_CONDITIONS.range(attributeValue);
        TARGET_CONDITIONS_IGNORE_INVISIBILITY_TESTING.range(attributeValue);
        ATTACK_TARGET_CONDITIONS.range(attributeValue);
        ATTACK_TARGET_CONDITIONS_IGNORE_INVISIBILITY_TESTING.range(attributeValue);
        ATTACK_TARGET_CONDITIONS_IGNORE_LINE_OF_SIGHT.range(attributeValue);
        ATTACK_TARGET_CONDITIONS_IGNORE_INVISIBILITY_AND_LINE_OF_SIGHT.range(attributeValue);
    }

    protected abstract void doTick(ServerLevel level, E entity);

    public abstract Set<MemoryModuleType<?>> requires();

    public static boolean isEntityTargetable(ServerLevel level, LivingEntity entity, LivingEntity target) {
        return entity.getBrain().isMemoryValue(MemoryModuleType.ATTACK_TARGET, target)
            ? TARGET_CONDITIONS_IGNORE_INVISIBILITY_TESTING.test(level, entity, target)
            : TARGET_CONDITIONS.test(level, entity, target);
    }

    public static boolean isEntityAttackable(ServerLevel level, LivingEntity entity, LivingEntity target) {
        return entity.getBrain().isMemoryValue(MemoryModuleType.ATTACK_TARGET, target)
            ? ATTACK_TARGET_CONDITIONS_IGNORE_INVISIBILITY_TESTING.test(level, entity, target)
            : ATTACK_TARGET_CONDITIONS.test(level, entity, target);
    }

    public static BiPredicate<ServerLevel, LivingEntity> wasEntityAttackableLastNTicks(LivingEntity entity, int ticks) {
        return rememberPositives(ticks, (level, target) -> isEntityAttackable(level, entity, target));
    }

    public static boolean isEntityAttackableIgnoringLineOfSight(ServerLevel level, LivingEntity entity, LivingEntity target) {
        return entity.getBrain().isMemoryValue(MemoryModuleType.ATTACK_TARGET, target)
            ? ATTACK_TARGET_CONDITIONS_IGNORE_INVISIBILITY_AND_LINE_OF_SIGHT.test(level, entity, target)
            : ATTACK_TARGET_CONDITIONS_IGNORE_LINE_OF_SIGHT.test(level, entity, target);
    }

    static <T, U> BiPredicate<T, U> rememberPositives(int ticks, BiPredicate<T, U> predicate) {
        AtomicInteger atomicInteger = new AtomicInteger(0);
        return (object, object1) -> {
            if (predicate.test(object, object1)) {
                atomicInteger.set(ticks);
                return true;
            } else {
                return atomicInteger.decrementAndGet() >= 0;
            }
        };
    }
}
