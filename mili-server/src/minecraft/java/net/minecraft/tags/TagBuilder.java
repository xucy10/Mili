package net.minecraft.tags;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;

public class TagBuilder {
    private final List<TagEntry> entries = new ArrayList<>();

    public static TagBuilder create() {
        return new TagBuilder();
    }

    public List<TagEntry> build() {
        return List.copyOf(this.entries);
    }

    public TagBuilder add(TagEntry entry) {
        this.entries.add(entry);
        return this;
    }

    public TagBuilder addElement(Identifier elementLocation) {
        return this.add(TagEntry.element(elementLocation));
    }

    public TagBuilder addOptionalElement(Identifier elementLocation) {
        return this.add(TagEntry.optionalElement(elementLocation));
    }

    public TagBuilder addTag(Identifier tagLocation) {
        return this.add(TagEntry.tag(tagLocation));
    }

    public TagBuilder addOptionalTag(Identifier tagLocation) {
        return this.add(TagEntry.optionalTag(tagLocation));
    }
}
