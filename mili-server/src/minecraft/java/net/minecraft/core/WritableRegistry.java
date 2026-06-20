package net.minecraft.core;

import java.util.List;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;

public interface WritableRegistry<T> extends Registry<T> {
    Holder.Reference<T> register(ResourceKey<T> key, T value, RegistrationInfo registrationInfo);

    void bindTag(TagKey<T> tag, List<Holder<T>> values);

    boolean isEmpty();

    HolderGetter<T> createRegistrationLookup();
}
