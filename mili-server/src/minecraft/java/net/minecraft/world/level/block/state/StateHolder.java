package net.minecraft.world.level.block.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.world.level.block.state.properties.Property;
import org.jspecify.annotations.Nullable;

public abstract class StateHolder<O, S> implements ca.spottedleaf.moonrise.patches.blockstate_propertyaccess.PropertyAccessStateHolder { // Paper - optimise blockstate property access
    public static final String NAME_TAG = "Name";
    public static final String PROPERTIES_TAG = "Properties";
    public static final Function<Entry<Property<?>, Comparable<?>>, String> PROPERTY_ENTRY_TO_STRING_FUNCTION = new Function<Entry<Property<?>, Comparable<?>>, String>() {
        @Override
        public String apply(@Nullable Entry<Property<?>, Comparable<?>> propertyEntry) {
            if (propertyEntry == null) {
                return "<NULL>";
            } else {
                Property<?> property = propertyEntry.getKey();
                return property.getName() + "=" + this.getName(property, propertyEntry.getValue());
            }
        }

        private <T extends Comparable<T>> String getName(Property<T> property, Comparable<?> value) {
            return property.getName((T)value);
        }
    };
    protected final O owner;
    private Reference2ObjectArrayMap<Property<?>, Comparable<?>> values; // Paper - optimise blockstate property access - remove final
    private Map<Property<?>, S[]> neighbours;
    protected final MapCodec<S> propertiesCodec;

    // Paper start - optimise blockstate property access
    protected ca.spottedleaf.moonrise.patches.blockstate_propertyaccess.util.ZeroCollidingReferenceStateTable<O, S> optimisedTable;
    protected final long tableIndex;

    @Override
    public final long moonrise$getTableIndex() {
        return this.tableIndex;
    }
    // Paper end - optimise blockstate property access

    protected StateHolder(O owner, Reference2ObjectArrayMap<Property<?>, Comparable<?>> values, MapCodec<S> propertiesCodec) {
        this.owner = owner;
        this.values = values;
        this.propertiesCodec = propertiesCodec;
        // Paper start - optimise blockstate property access
        this.optimisedTable = new ca.spottedleaf.moonrise.patches.blockstate_propertyaccess.util.ZeroCollidingReferenceStateTable<>(this.values.keySet());
        this.tableIndex = this.optimisedTable.getIndex((StateHolder<O, S>)(Object)this);
        // Paper end - optimise blockstate property access
    }

    public <T extends Comparable<T>> S cycle(Property<T> property) {
        return this.setValue(property, findNextInCollection(property.getPossibleValues(), this.getValue(property)));
    }

    protected static <T> T findNextInCollection(List<T> possibleValues, T currentValue) {
        int i = possibleValues.indexOf(currentValue) + 1;
        return i == possibleValues.size() ? possibleValues.getFirst() : possibleValues.get(i);
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(this.owner);
        if (!this.getValues().isEmpty()) {
            stringBuilder.append('[');
            stringBuilder.append(this.getValues().entrySet().stream().map(PROPERTY_ENTRY_TO_STRING_FUNCTION).collect(Collectors.joining(",")));
            stringBuilder.append(']');
        }

        return stringBuilder.toString();
    }

    @Override
    public final boolean equals(Object other) {
        return super.equals(other);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    public Collection<Property<?>> getProperties() {
        return this.optimisedTable.getProperties(); // Paper - optimise blockstate property access
    }

    public boolean hasProperty(Property<?> property) {
        return property != null && this.optimisedTable.hasProperty(property); // Paper - optimise blockstate property access
    }

    public <T extends Comparable<T>> T getValue(Property<T> property) {
        // Paper start - optimise blockstate property access
        final T ret = this.optimisedTable.get(this.tableIndex, property);
        if (ret != null) {
            return ret;
        }
        throw new IllegalArgumentException("Cannot get property " + property + " as it does not exist in " + this.owner);
        // Paper end - optimise blockstate property access
    }

    public <T extends Comparable<T>> Optional<T> getOptionalValue(Property<T> property) {
        return Optional.ofNullable(this.getNullableValue(property));
    }

    public <T extends Comparable<T>> T getValueOrElse(Property<T> property, T defaultValue) {
        return Objects.requireNonNullElse(this.getNullableValue(property), defaultValue);
    }

    private <T extends Comparable<T>> @Nullable T getNullableValue(Property<T> property) {
        return property == null ? null : this.optimisedTable.get(this.tableIndex, property); // Paper - optimise blockstate property access
    }

    public <T extends Comparable<T>, V extends T> S setValue(Property<T> property, V value) {
        // Paper start - optimise blockstate property access
        final S ret = this.optimisedTable.set(this.tableIndex, property, value);
        if (ret != null) {
            return ret;
        }
        throw new IllegalArgumentException("Cannot set property " + property + " to " + value + " on " + this.owner);
        // Paper end - optimise blockstate property access
    }

    public <T extends Comparable<T>, V extends T> S trySetValue(Property<T> property, V value) {
        // Paper start - optimise blockstate property access
        if (property == null) {
            return (S)(StateHolder<O, S>)(Object)this;
        }
        final S ret = this.optimisedTable.trySet(this.tableIndex, property, value, (S)(StateHolder<O, S>)(Object)this);
        if (ret != null) {
            return ret;
        }
        throw new IllegalArgumentException("Cannot set property " + property + " to " + value + " on " + this.owner);
        // Paper end - optimise blockstate property access
    }

    private <T extends Comparable<T>, V extends T> S setValueInternal(Property<T> property, V value, Comparable<?> comparable) {
        if (comparable.equals(value)) {
            return (S)this;
        } else {
            int internalIndex = property.getInternalIndex((T)value);
            if (internalIndex < 0) {
                throw new IllegalArgumentException("Cannot set property " + property + " to " + value + " on " + this.owner + ", it is not an allowed value");
            } else {
                return (S)this.neighbours.get(property)[internalIndex];
            }
        }
    }

    public void populateNeighbours(Map<Map<Property<?>, Comparable<?>>, S> possibleStateMap) {
        // Paper start - optimise blockstate property access
        final Map<Map<Property<?>, Comparable<?>>, S> map = possibleStateMap;
        if (this.optimisedTable.isLoaded()) {
            return;
        }
        this.optimisedTable.loadInTable(map);

        // de-duplicate the tables
        for (final Map.Entry<Map<Property<?>, Comparable<?>>, S> entry : map.entrySet()) {
            final S value = entry.getValue();
            ((StateHolder<O, S>)value).optimisedTable = this.optimisedTable;
        }

        // remove values arrays
        for (final Map.Entry<Map<Property<?>, Comparable<?>>, S> entry : map.entrySet()) {
            final S value = entry.getValue();
            ((StateHolder<O, S>)value).values = null;
        }

        return;
        // Paper end  optimise blockstate property access
    }

    private Map<Property<?>, Comparable<?>> makeNeighbourValues(Property<?> property, Comparable<?> value) {
        Map<Property<?>, Comparable<?>> map = new Reference2ObjectArrayMap<>(this.values);
        map.put(property, value);
        return map;
    }

    public Map<Property<?>, Comparable<?>> getValues() {
        // Paper start - optimise blockstate property access
        ca.spottedleaf.moonrise.patches.blockstate_propertyaccess.util.ZeroCollidingReferenceStateTable<O, S> table = this.optimisedTable;
        // We have to use this.values until the table is loaded
        return table.isLoaded() ? table.getMapView(this.tableIndex) : this.values;
        // Paper end - optimise blockstate property access
    }

    protected static <O, S extends StateHolder<O, S>> Codec<S> codec(Codec<O> ownerCodec, Function<O, S> defaultStateGetter) {
        return ownerCodec.dispatch(
            "Name",
            stateHolder -> stateHolder.owner,
            object -> {
                S stateHolder = defaultStateGetter.apply((O)object);
                return stateHolder.getValues().isEmpty()
                    ? MapCodec.unit(stateHolder)
                    : stateHolder.propertiesCodec.codec().lenientOptionalFieldOf("Properties").xmap(optional -> optional.orElse(stateHolder), Optional::of);
            }
        );
    }
}
