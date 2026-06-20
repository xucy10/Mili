package net.minecraft.server.packs;

import java.util.Map;
import net.minecraft.server.packs.metadata.MetadataSectionType;

public class BuiltInMetadata {
    private static final BuiltInMetadata EMPTY = new BuiltInMetadata(Map.of());
    private final Map<MetadataSectionType<?>, ?> values;

    private BuiltInMetadata(Map<MetadataSectionType<?>, ?> values) {
        this.values = values;
    }

    public <T> T get(MetadataSectionType<T> type) {
        return (T)this.values.get(type);
    }

    public static BuiltInMetadata of() {
        return EMPTY;
    }

    public static <T> BuiltInMetadata of(MetadataSectionType<T> type, T value) {
        return new BuiltInMetadata(Map.of(type, value));
    }

    public static <T1, T2> BuiltInMetadata of(MetadataSectionType<T1> type1, T1 value1, MetadataSectionType<T2> type2, T2 value2) {
        return new BuiltInMetadata(Map.of(type1, value1, type2, (T1)value2));
    }
}
