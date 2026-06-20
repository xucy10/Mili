package net.minecraft.world.level.block.state.properties;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.minecraft.util.StringRepresentable;

public final class EnumProperty<T extends Enum<T> & StringRepresentable> extends Property<T> implements ca.spottedleaf.moonrise.patches.blockstate_propertyaccess.PropertyAccess<T> { // Paper - optimise blockstate property access
    private final List<T> values;
    private final Map<String, T> names;
    private final int[] ordinalToIndex;

    // Paper start - optimise blockstate property access
    private int[] idLookupTable;

    @Override
    public final int moonrise$getIdFor(final T value) {
        final Class<T> target = this.getValueClass();
        return ((value.getClass() != target && value.getDeclaringClass() != target)) ? -1 : this.idLookupTable[value.ordinal()];
    }

    private void init() {
        final java.util.Collection<T> values = this.getPossibleValues();
        final Class<T> clazz = this.getValueClass();

        int id = 0;
        this.idLookupTable = new int[clazz.getEnumConstants().length];
        Arrays.fill(this.idLookupTable, -1);
        final T[] byId = (T[])java.lang.reflect.Array.newInstance(clazz, values.size());

        for (final T value : values) {
            final int valueId = id++;
            this.idLookupTable[value.ordinal()] = valueId;
            byId[valueId] = value;
        }

        this.moonrise$setById(byId);
    }
    // Paper end - optimise blockstate property access

    private EnumProperty(String name, Class<T> clazz, List<T> values) {
        super(name, clazz);
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Trying to make empty EnumProperty '" + name + "'");
        } else {
            this.values = List.copyOf(values);
            T[] enums = clazz.getEnumConstants();
            this.ordinalToIndex = new int[enums.length];

            for (T _enum : enums) {
                this.ordinalToIndex[_enum.ordinal()] = values.indexOf(_enum);
            }

            Builder<String, T> builder = ImmutableMap.builder();

            for (T _enum1 : values) {
                String serializedName = _enum1.getSerializedName();
                builder.put(serializedName, _enum1);
            }

            this.names = builder.buildOrThrow();
        }
        this.init(); // Paper - optimise blockstate property access
    }

    @Override
    public List<T> getPossibleValues() {
        return this.values;
    }

    @Override
    public Optional<T> getValue(String value) {
        return Optional.ofNullable(this.names.get(value));
    }

    @Override
    public String getName(T value) {
        return value.getSerializedName();
    }

    @Override
    public int getInternalIndex(T value) {
        return this.ordinalToIndex[value.ordinal()];
    }

    public boolean equals_unused(Object other) { // Paper - Perf: Optimize hashCode/equals
        return this == other || other instanceof EnumProperty<?> enumProperty && super.equals(other) && this.values.equals(enumProperty.values);
    }

    @Override
    public int generateHashCode() {
        int i = super.generateHashCode();
        return 31 * i + this.values.hashCode();
    }

    public static <T extends Enum<T> & StringRepresentable> EnumProperty<T> create(String name, Class<T> clazz) {
        return create(name, clazz, type -> true);
    }

    public static <T extends Enum<T> & StringRepresentable> EnumProperty<T> create(String name, Class<T> clazz, Predicate<T> filter) {
        return create(name, clazz, Arrays.<T>stream(clazz.getEnumConstants()).filter(filter).collect(Collectors.toList()));
    }

    @SafeVarargs
    public static <T extends Enum<T> & StringRepresentable> EnumProperty<T> create(String name, Class<T> clazz, T... values) {
        return create(name, clazz, List.of(values));
    }

    public static <T extends Enum<T> & StringRepresentable> EnumProperty<T> create(String name, Class<T> clazz, List<T> values) {
        return new EnumProperty<>(name, clazz, values);
    }
}
