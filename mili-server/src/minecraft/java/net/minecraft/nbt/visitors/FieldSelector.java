package net.minecraft.nbt.visitors;

import java.util.List;
import net.minecraft.nbt.TagType;

public record FieldSelector(List<String> path, TagType<?> type, String name) {
    public FieldSelector(TagType<?> type, String name) {
        this(List.of(), type, name);
    }

    public FieldSelector(String element, TagType<?> type, String name) {
        this(List.of(element), type, name);
    }

    public FieldSelector(String firstElement, String secondElement, TagType<?> type, String name) {
        this(List.of(firstElement, secondElement), type, name);
    }
}
