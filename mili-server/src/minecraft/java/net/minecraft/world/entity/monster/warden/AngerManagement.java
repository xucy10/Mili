package net.minecraft.world.entity.monster.warden;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Streams;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

public class AngerManagement {
    @VisibleForTesting
    protected static final int CONVERSION_DELAY = 2;
    @VisibleForTesting
    protected static final int MAX_ANGER = 150;
    private static final int DEFAULT_ANGER_DECREASE = 1;
    private int conversionDelay = Mth.randomBetweenInclusive(RandomSource.create(), 0, 2);
    int highestAnger;
    private static final Codec<Pair<UUID, Integer>> SUSPECT_ANGER_PAIR = RecordCodecBuilder.create(
        instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("uuid").forGetter(Pair::getFirst), ExtraCodecs.NON_NEGATIVE_INT.fieldOf("anger").forGetter(Pair::getSecond)
            )
            .apply(instance, Pair::of)
    );
    private final Predicate<Entity> filter;
    @VisibleForTesting
    protected final ArrayList<Entity> suspects;
    private final AngerManagement.Sorter suspectSorter;
    @VisibleForTesting
    protected final Object2IntMap<Entity> angerBySuspect;
    @VisibleForTesting
    protected final Object2IntMap<UUID> angerByUuid;

    public static Codec<AngerManagement> codec(Predicate<Entity> filter) {
        return RecordCodecBuilder.create(
            instance -> instance.group(
                    SUSPECT_ANGER_PAIR.listOf().fieldOf("suspects").orElse(Collections.emptyList()).forGetter(AngerManagement::createUuidAngerPairs)
                )
                .apply(instance, list -> new AngerManagement(filter, list))
        );
    }

    public AngerManagement(Predicate<Entity> filter, List<Pair<UUID, Integer>> angerByUuid) {
        this.filter = filter;
        this.suspects = new ArrayList<>();
        this.suspectSorter = new AngerManagement.Sorter(this);
        this.angerBySuspect = new Object2IntOpenHashMap<>();
        this.angerByUuid = new Object2IntOpenHashMap<>(angerByUuid.size());
        angerByUuid.forEach(pair -> this.angerByUuid.put(pair.getFirst(), pair.getSecond()));
    }

    private List<Pair<UUID, Integer>> createUuidAngerPairs() {
        return Streams.<Pair<UUID, Integer>>concat(
                this.suspects.stream().map(entity -> Pair.of(entity.getUUID(), this.angerBySuspect.getInt(entity))),
                this.angerByUuid.object2IntEntrySet().stream().map(entry -> Pair.of(entry.getKey(), entry.getIntValue()))
            )
            .collect(Collectors.toList());
    }

    public void tick(ServerLevel level, Predicate<Entity> predicate) {
        this.conversionDelay--;
        if (this.conversionDelay <= 0) {
            this.convertFromUuids(level);
            this.conversionDelay = 2;
        }

        ObjectIterator<Entry<UUID>> objectIterator = this.angerByUuid.object2IntEntrySet().iterator();

        while (objectIterator.hasNext()) {
            Entry<UUID> entry = objectIterator.next();
            int intValue = entry.getIntValue();
            if (intValue <= 1) {
                objectIterator.remove();
            } else {
                entry.setValue(intValue - 1);
            }
        }

        ObjectIterator<Entry<Entity>> objectIterator1 = this.angerBySuspect.object2IntEntrySet().iterator();

        while (objectIterator1.hasNext()) {
            Entry<Entity> entry1 = objectIterator1.next();
            int intValue1 = entry1.getIntValue();
            Entity entity = entry1.getKey();
            Entity.RemovalReason removalReason = entity.getRemovalReason();
            if (intValue1 > 1 && predicate.test(entity) && removalReason == null) {
                entry1.setValue(intValue1 - 1);
            } else {
                this.suspects.remove(entity);
                objectIterator1.remove();
                if (intValue1 > 1 && removalReason != null) {
                    switch (removalReason) {
                        case CHANGED_DIMENSION:
                        case UNLOADED_TO_CHUNK:
                        case UNLOADED_WITH_PLAYER:
                            this.angerByUuid.put(entity.getUUID(), intValue1 - 1);
                    }
                }
            }
        }

        this.sortAndUpdateHighestAnger();
    }

    private void sortAndUpdateHighestAnger() {
        this.highestAnger = 0;
        this.suspects.sort(this.suspectSorter);
        if (this.suspects.size() == 1) {
            this.highestAnger = this.angerBySuspect.getInt(this.suspects.get(0));
        }
    }

    private void convertFromUuids(ServerLevel level) {
        ObjectIterator<Entry<UUID>> objectIterator = this.angerByUuid.object2IntEntrySet().iterator();

        while (objectIterator.hasNext()) {
            Entry<UUID> entry = objectIterator.next();
            int intValue = entry.getIntValue();
            Entity entity = level.getEntity(entry.getKey());
            if (entity != null) {
                this.angerBySuspect.put(entity, intValue);
                this.suspects.add(entity);
                objectIterator.remove();
            }
        }
    }

    public int increaseAnger(Entity entity, int offset) {
        boolean flag = !this.angerBySuspect.containsKey(entity);
        int i = this.angerBySuspect.computeInt(entity, (entity1, integer) -> Math.min(150, (integer == null ? 0 : integer) + offset)); // Paper - diff on change (Warden#increaseAngerAt WardenAngerChangeEvent)
        if (flag) {
            int i1 = this.angerByUuid.removeInt(entity.getUUID());
            i += i1;
            this.angerBySuspect.put(entity, i);
            this.suspects.add(entity);
        }

        this.sortAndUpdateHighestAnger();
        return i;
    }

    public void clearAnger(Entity entity) {
        this.angerBySuspect.removeInt(entity);
        this.suspects.remove(entity);
        this.sortAndUpdateHighestAnger();
    }

    private @Nullable Entity getTopSuspect() {
        return this.suspects.stream().filter(this.filter).findFirst().orElse(null);
    }

    public int getActiveAnger(@Nullable Entity entity) {
        return entity == null ? this.highestAnger : this.angerBySuspect.getInt(entity);
    }

    public Optional<LivingEntity> getActiveEntity() {
        return Optional.ofNullable(this.getTopSuspect()).filter(entity -> entity instanceof LivingEntity).map(entity -> (LivingEntity)entity);
    }

    @VisibleForTesting
    protected record Sorter(AngerManagement angerManagement) implements Comparator<Entity> {
        @Override
        public int compare(Entity first, Entity second) {
            if (first.equals(second)) {
                return 0;
            } else {
                int orDefault = this.angerManagement.angerBySuspect.getOrDefault(first, 0);
                int orDefault1 = this.angerManagement.angerBySuspect.getOrDefault(second, 0);
                this.angerManagement.highestAnger = Math.max(this.angerManagement.highestAnger, Math.max(orDefault, orDefault1));
                boolean isAngry = AngerLevel.byAnger(orDefault).isAngry();
                boolean isAngry1 = AngerLevel.byAnger(orDefault1).isAngry();
                if (isAngry != isAngry1) {
                    return isAngry ? -1 : 1;
                } else {
                    boolean flag = first instanceof Player;
                    boolean flag1 = second instanceof Player;
                    if (flag != flag1) {
                        return flag ? -1 : 1;
                    } else {
                        return Integer.compare(orDefault1, orDefault);
                    }
                }
            }
        }
    }
}
