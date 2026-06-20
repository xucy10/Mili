package net.minecraft.world.level.block.state;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.Encoder;
import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.world.level.block.state.properties.Property;
import org.jspecify.annotations.Nullable;

public class StateDefinition<O, S extends StateHolder<O, S>> {
    static final Pattern NAME_PATTERN = Pattern.compile("^[a-z0-9_]+$");
    private final O owner;
    private final ImmutableSortedMap<String, Property<?>> propertiesByName;
    private final ImmutableList<S> states;

    protected StateDefinition(
        Function<O, S> stateValueFunction, O owner, StateDefinition.Factory<O, S> valueFunction, Map<String, Property<?>> propertiesByName
    ) {
        this.owner = owner;
        this.propertiesByName = ImmutableSortedMap.copyOf(propertiesByName);
        Supplier<S> supplier = () -> stateValueFunction.apply(owner);
        MapCodec<S> mapCodec = MapCodec.of(Encoder.empty(), Decoder.unit(supplier));

        for (Entry<String, Property<?>> entry : this.propertiesByName.entrySet()) {
            mapCodec = appendPropertyCodec(mapCodec, supplier, entry.getKey(), entry.getValue());
        }

        MapCodec<S> mapCodec1 = mapCodec;
        Map<Map<Property<?>, Comparable<?>>, S> map = Maps.newLinkedHashMap();
        List<S> list = Lists.newArrayList();
        Stream<List<Pair<Property<?>, Comparable<?>>>> stream = Stream.of(Collections.emptyList());

        for (Property<?> property : this.propertiesByName.values()) {
            stream = stream.flatMap(list1 -> property.getPossibleValues().stream().map(value -> {
                List<Pair<Property<?>, Comparable<?>>> list2 = Lists.newArrayList(list1);
                list2.add(Pair.of(property, value));
                return list2;
            }));
        }

        stream.forEach(list1 -> {
            Reference2ObjectArrayMap<Property<?>, Comparable<?>> map1 = new Reference2ObjectArrayMap<>(list1.size());

            for (Pair<Property<?>, Comparable<?>> pair : list1) {
                map1.put(pair.getFirst(), pair.getSecond());
            }

            S stateHolder1 = valueFunction.create(owner, map1, mapCodec1);
            map.put(map1, stateHolder1);
            list.add(stateHolder1);
        });

        for (S stateHolder : list) {
            stateHolder.populateNeighbours(map);
        }

        this.states = ImmutableList.copyOf(list);
    }

    private static <S extends StateHolder<?, S>, T extends Comparable<T>> MapCodec<S> appendPropertyCodec(
        MapCodec<S> propertyCodec, Supplier<S> holderSupplier, String value, Property<T> property
    ) {
        return Codec.mapPair(propertyCodec, property.valueCodec().fieldOf(value).orElseGet(elseValue -> {}, () -> property.value(holderSupplier.get())))
            .xmap(holder -> holder.getFirst().setValue(property, holder.getSecond().value()), holder -> Pair.of((S)holder, property.value(holder)));
    }

    public ImmutableList<S> getPossibleStates() {
        return this.states;
    }

    public S any() {
        return this.states.get(0);
    }

    public O getOwner() {
        return this.owner;
    }

    public Collection<Property<?>> getProperties() {
        return this.propertiesByName.values();
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("block", this.owner)
            .add("properties", this.propertiesByName.values().stream().map(Property::getName).collect(Collectors.toList()))
            .toString();
    }

    public @Nullable Property<?> getProperty(String propertyName) {
        return this.propertiesByName.get(propertyName);
    }

    public static class Builder<O, S extends StateHolder<O, S>> {
        private final O owner;
        private final Map<String, Property<?>> properties = Maps.newHashMap();

        public Builder(O owner) {
            this.owner = owner;
        }

        public StateDefinition.Builder<O, S> add(Property<?>... properties) {
            for (Property<?> property : properties) {
                this.validateProperty(property);
                this.properties.put(property.getName(), property);
            }

            return this;
        }

        private <T extends Comparable<T>> void validateProperty(Property<T> property) {
            String name = property.getName();
            if (!StateDefinition.NAME_PATTERN.matcher(name).matches()) {
                throw new IllegalArgumentException(this.owner + " has invalidly named property: " + name);
            } else {
                Collection<T> possibleValues = property.getPossibleValues();
                if (possibleValues.size() <= 1) {
                    throw new IllegalArgumentException(this.owner + " attempted use property " + name + " with <= 1 possible values");
                } else {
                    for (T comparable : possibleValues) {
                        String name1 = property.getName(comparable);
                        if (!StateDefinition.NAME_PATTERN.matcher(name1).matches()) {
                            throw new IllegalArgumentException(this.owner + " has property: " + name + " with invalidly named value: " + name1);
                        }
                    }

                    if (this.properties.containsKey(name)) {
                        throw new IllegalArgumentException(this.owner + " has duplicate property: " + name);
                    }
                }
            }
        }

        public StateDefinition<O, S> create(Function<O, S> stateValueFunction, StateDefinition.Factory<O, S> stateFunction) {
            return new StateDefinition<>(stateValueFunction, this.owner, stateFunction, this.properties);
        }
    }

    public interface Factory<O, S> {
        S create(O owner, Reference2ObjectArrayMap<Property<?>, Comparable<?>> values, MapCodec<S> propertiesCodec);
    }
}
