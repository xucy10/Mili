package net.minecraft.world.entity.ai.gossip;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.DoublePredicate;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.UUIDUtil;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.util.VisibleForDebug;

public class GossipContainer {
    public static final Codec<GossipContainer> CODEC = GossipContainer.GossipEntry.CODEC
        .listOf()
        .xmap(GossipContainer::new, gossipContainer -> gossipContainer.decompress()); // Paper - Perf: Remove streams from hot code
    public static final int DISCARD_THRESHOLD = 2;
    public final Map<UUID, GossipContainer.EntityGossips> gossips = new HashMap<>();

    public GossipContainer() {
    }

    private GossipContainer(List<GossipContainer.GossipEntry> entries) {
        entries.forEach(entry -> this.getOrCreate(entry.target).entries.put(entry.type, entry.value));
    }

    @VisibleForDebug
    public Map<UUID, Object2IntMap<GossipType>> getGossipEntries() {
        Map<UUID, Object2IntMap<GossipType>> map = Maps.newHashMap();
        this.gossips.keySet().forEach(uuid -> {
            GossipContainer.EntityGossips entityGossips = this.gossips.get(uuid);
            map.put(uuid, entityGossips.entries);
        });
        return map;
    }

    public void decay() {
        Iterator<GossipContainer.EntityGossips> iterator = this.gossips.values().iterator();

        while (iterator.hasNext()) {
            GossipContainer.EntityGossips entityGossips = iterator.next();
            entityGossips.decay();
            if (entityGossips.isEmpty()) {
                iterator.remove();
            }
        }
    }

    private Stream<GossipContainer.GossipEntry> unpack() {
        return this.gossips.entrySet().stream().flatMap(entry -> entry.getValue().unpack(entry.getKey()));
    }

    // Paper start - Perf: Remove streams from hot code
    private List<GossipContainer.GossipEntry> decompress() {
        final List<GossipContainer.GossipEntry> list = new it.unimi.dsi.fastutil.objects.ObjectArrayList<>();
        for (final Map.Entry<UUID, GossipContainer.EntityGossips> entry : this.gossips.entrySet()) {
            for (final GossipContainer.GossipEntry cur : entry.getValue().decompress(entry.getKey())) {
                if (cur.weightedValue() != 0) {
                    list.add(cur);
                }
            }
        }
        return list;
    }
    // Paper end - Perf: Remove streams from hot code

    private Collection<GossipContainer.GossipEntry> selectGossipsForTransfer(RandomSource random, int amount) {
        List<GossipContainer.GossipEntry> list = this.decompress(); // Paper - Perf: Remove streams from hot code
        if (list.isEmpty()) {
            return Collections.emptyList();
        } else {
            int[] ints = new int[list.size()];
            int i = 0;

            for (int i1 = 0; i1 < list.size(); i1++) {
                GossipContainer.GossipEntry gossipEntry = list.get(i1);
                i += Math.abs(gossipEntry.weightedValue());
                ints[i1] = i - 1;
            }

            Set<GossipContainer.GossipEntry> set = Sets.newIdentityHashSet();

            for (int i2 = 0; i2 < amount; i2++) {
                int randomInt = random.nextInt(i);
                int i3 = Arrays.binarySearch(ints, randomInt);
                set.add(list.get(i3 < 0 ? -i3 - 1 : i3));
            }

            return set;
        }
    }

    private GossipContainer.EntityGossips getOrCreate(UUID identifier) {
        return this.gossips.computeIfAbsent(identifier, uuid -> new GossipContainer.EntityGossips());
    }

    public void transferFrom(GossipContainer container, RandomSource randomSource, int amount) {
        Collection<GossipContainer.GossipEntry> collection = container.selectGossipsForTransfer(randomSource, amount);
        collection.forEach(entry -> {
            int i = entry.value - entry.type.decayPerTransfer;
            if (i >= 2) {
                this.getOrCreate(entry.target).entries.mergeInt(entry.type, i, GossipContainer::mergeValuesForTransfer);
            }
        });
    }

    public int getReputation(UUID identifier, Predicate<GossipType> gossip) {
        GossipContainer.EntityGossips entityGossips = this.gossips.get(identifier);
        return entityGossips != null ? entityGossips.weightedValue(gossip) : 0;
    }

    public long getCountForType(GossipType gossipType, DoublePredicate gossipPredicate) {
        return this.gossips.values().stream().filter(gossips -> gossipPredicate.test(gossips.entries.getOrDefault(gossipType, 0) * gossipType.weight)).count();
    }

    public void add(UUID identifier, GossipType gossipType, int gossipValue) {
        GossipContainer.EntityGossips entityGossips = this.getOrCreate(identifier);
        entityGossips.entries.mergeInt(gossipType, gossipValue, (i, i1) -> this.mergeValuesForAddition(gossipType, i, i1));
        entityGossips.makeSureValueIsntTooLowOrTooHigh(gossipType);
        if (entityGossips.isEmpty()) {
            this.gossips.remove(identifier);
        }
    }

    public void remove(UUID identifier, GossipType gossipType, int gossipValue) {
        this.add(identifier, gossipType, -gossipValue);
    }

    public void remove(UUID identifier, GossipType gossipType) {
        GossipContainer.EntityGossips entityGossips = this.gossips.get(identifier);
        if (entityGossips != null) {
            entityGossips.remove(gossipType);
            if (entityGossips.isEmpty()) {
                this.gossips.remove(identifier);
            }
        }
    }

    public void remove(GossipType gossipType) {
        Iterator<GossipContainer.EntityGossips> iterator = this.gossips.values().iterator();

        while (iterator.hasNext()) {
            GossipContainer.EntityGossips entityGossips = iterator.next();
            entityGossips.remove(gossipType);
            if (entityGossips.isEmpty()) {
                iterator.remove();
            }
        }
    }

    public void clear() {
        this.gossips.clear();
    }

    public void putAll(GossipContainer other) {
        other.gossips.forEach((uuid, gossips) -> this.getOrCreate(uuid).entries.putAll(gossips.entries));
    }

    private static int mergeValuesForTransfer(int value1, int value2) {
        return Math.max(value1, value2);
    }

    private int mergeValuesForAddition(GossipType gossipType, int existing, int additive) {
        int i = existing + additive;
        return i > gossipType.max ? Math.max(gossipType.max, existing) : i;
    }

    public GossipContainer copy() {
        GossipContainer gossipContainer = new GossipContainer();
        gossipContainer.putAll(this);
        return gossipContainer;
    }

    public static class EntityGossips {
        final Object2IntMap<GossipType> entries = new Object2IntOpenHashMap<>();

        public int weightedValue(Predicate<GossipType> gossipType) {
            // Paper start - Perf: Remove streams from hot code
            int weight = 0;
            for (Object2IntMap.Entry<GossipType> entry : entries.object2IntEntrySet()) {
                if (gossipType.test(entry.getKey())) {
                    weight += entry.getIntValue() * entry.getKey().weight;
                }
            }
            return weight;
        }

        public List<GossipContainer.GossipEntry> decompress(UUID uuid) {
            List<GossipContainer.GossipEntry> list = new it.unimi.dsi.fastutil.objects.ObjectArrayList<>();
            for (Object2IntMap.Entry<GossipType> entry : entries.object2IntEntrySet()) {
                list.add(new GossipContainer.GossipEntry(uuid, entry.getKey(), entry.getIntValue()));
            }
            return list;
            // Paper end - Perf: Remove streams from hot code
        }

        public Stream<GossipContainer.GossipEntry> unpack(UUID identifier) {
            return this.entries.object2IntEntrySet().stream().map(gossip -> new GossipContainer.GossipEntry(identifier, gossip.getKey(), gossip.getIntValue()));
        }

        public void decay() {
            ObjectIterator<Entry<GossipType>> objectIterator = this.entries.object2IntEntrySet().iterator();

            while (objectIterator.hasNext()) {
                Entry<GossipType> entry = objectIterator.next();
                int i = entry.getIntValue() - entry.getKey().decayPerDay;
                if (i < 2) {
                    objectIterator.remove();
                } else {
                    entry.setValue(i);
                }
            }
        }

        public boolean isEmpty() {
            return this.entries.isEmpty();
        }

        public void makeSureValueIsntTooLowOrTooHigh(GossipType gossipType) {
            int _int = this.entries.getInt(gossipType);
            if (_int > gossipType.max) {
                this.entries.put(gossipType, gossipType.max);
            }

            if (_int < 2) {
                this.remove(gossipType);
            }
        }

        public void remove(GossipType gossipType) {
            this.entries.removeInt(gossipType);
        }

        // Paper start - Add villager reputation API
        private static final GossipType[] TYPES = GossipType.values();

        public com.destroystokyo.paper.entity.villager.Reputation getPaperReputation() {
            Map<com.destroystokyo.paper.entity.villager.ReputationType, Integer> map = new java.util.EnumMap<>(com.destroystokyo.paper.entity.villager.ReputationType.class);
            for (Object2IntMap.Entry<GossipType> type : this.entries.object2IntEntrySet()) {
                map.put(toApi(type.getKey()), type.getIntValue());
            }

            return new com.destroystokyo.paper.entity.villager.Reputation(map);
        }

        public void assignFromPaperReputation(com.destroystokyo.paper.entity.villager.Reputation rep) {
            for (GossipType type : TYPES) {
                com.destroystokyo.paper.entity.villager.ReputationType api = toApi(type);

                if (rep.hasReputationSet(api)) {
                    int reputation = rep.getReputation(api);
                    if (reputation == 0) {
                        this.entries.removeInt(type);
                    } else {
                        this.entries.put(type, reputation);
                    }
                }
            }
        }

        private static com.destroystokyo.paper.entity.villager.ReputationType toApi(GossipType type) {
            return switch (type) {
                case MAJOR_NEGATIVE -> com.destroystokyo.paper.entity.villager.ReputationType.MAJOR_NEGATIVE;
                case MINOR_NEGATIVE -> com.destroystokyo.paper.entity.villager.ReputationType.MINOR_NEGATIVE;
                case MINOR_POSITIVE -> com.destroystokyo.paper.entity.villager.ReputationType.MINOR_POSITIVE;
                case MAJOR_POSITIVE -> com.destroystokyo.paper.entity.villager.ReputationType.MAJOR_POSITIVE;
                case TRADING -> com.destroystokyo.paper.entity.villager.ReputationType.TRADING;
            };
        }
        // Paper end - Add villager reputation API
    }

    record GossipEntry(UUID target, GossipType type, int value) {
        public static final Codec<GossipContainer.GossipEntry> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    UUIDUtil.CODEC.fieldOf("Target").forGetter(GossipContainer.GossipEntry::target),
                    GossipType.CODEC.fieldOf("Type").forGetter(GossipContainer.GossipEntry::type),
                    ExtraCodecs.POSITIVE_INT.fieldOf("Value").forGetter(GossipContainer.GossipEntry::value)
                )
                .apply(instance, GossipContainer.GossipEntry::new)
        );

        public int weightedValue() {
            return this.value * this.type.weight;
        }
    }
}
