package net.minecraft.world.entity.ai.memory;

import com.google.common.collect.Iterables;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.sensing.Sensor;

public class NearestVisibleLivingEntities {
    private static final NearestVisibleLivingEntities EMPTY = new NearestVisibleLivingEntities();
    private final List<LivingEntity> nearbyEntities;
    private final Predicate<LivingEntity> lineOfSightTest;

    private NearestVisibleLivingEntities() {
        this.nearbyEntities = List.of();
        this.lineOfSightTest = entity -> false;
    }

    public NearestVisibleLivingEntities(ServerLevel level, LivingEntity entity, List<LivingEntity> nearbyEntities) {
        this.nearbyEntities = nearbyEntities;
        Object2BooleanOpenHashMap<LivingEntity> map = new Object2BooleanOpenHashMap<>(nearbyEntities.size());
        Predicate<LivingEntity> predicate = target -> Sensor.isEntityTargetable(level, entity, target);
        this.lineOfSightTest = target -> map.computeIfAbsent(target, predicate);
    }

    public static NearestVisibleLivingEntities empty() {
        return EMPTY;
    }

    public Optional<LivingEntity> findClosest(Predicate<LivingEntity> predicate) {
        for (LivingEntity livingEntity : this.nearbyEntities) {
            if (predicate.test(livingEntity) && this.lineOfSightTest.test(livingEntity)) {
                return Optional.of(livingEntity);
            }
        }

        return Optional.empty();
    }

    public Iterable<LivingEntity> findAll(Predicate<LivingEntity> predicate) {
        return Iterables.filter(this.nearbyEntities, target -> predicate.test(target) && this.lineOfSightTest.test(target));
    }

    public Stream<LivingEntity> find(Predicate<LivingEntity> predicate) {
        return this.nearbyEntities.stream().filter(target -> predicate.test(target) && this.lineOfSightTest.test(target));
    }

    public boolean contains(LivingEntity entity) {
        return this.nearbyEntities.contains(entity) && this.lineOfSightTest.test(entity);
    }

    public boolean contains(Predicate<LivingEntity> predicate) {
        for (LivingEntity livingEntity : this.nearbyEntities) {
            if (predicate.test(livingEntity) && this.lineOfSightTest.test(livingEntity)) {
                return true;
            }
        }

        return false;
    }
}
