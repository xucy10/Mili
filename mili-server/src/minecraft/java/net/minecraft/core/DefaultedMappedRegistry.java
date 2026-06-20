package net.minecraft.core;

import com.mojang.serialization.Lifecycle;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

public class DefaultedMappedRegistry<T> extends MappedRegistry<T> implements DefaultedRegistry<T> {
    private final Identifier defaultKey;
    private Holder.Reference<T> defaultValue;

    public DefaultedMappedRegistry(String defaultKey, ResourceKey<? extends Registry<T>> key, Lifecycle registryLifecycle, boolean hasIntrusiveHolders) {
        super(key, registryLifecycle, hasIntrusiveHolders);
        this.defaultKey = Identifier.parse(defaultKey);
    }

    @Override
    public Holder.Reference<T> register(ResourceKey<T> key, T value, RegistrationInfo registrationInfo) {
        Holder.Reference<T> reference = super.register(key, value, registrationInfo);
        if (this.defaultKey.equals(key.identifier())) {
            this.defaultValue = reference;
        }

        return reference;
    }

    @Override
    public int getId(@Nullable T value) {
        int i = super.getId(value);
        return i == -1 ? super.getId(this.defaultValue.value()) : i;
    }

    @Override
    public Identifier getKey(T value) {
        Identifier identifier = super.getKey(value);
        return identifier == null ? this.defaultKey : identifier;
    }

    @Override
    public T getValue(@Nullable Identifier key) {
        T object = super.getValue(key);
        return object == null ? this.defaultValue.value() : object;
    }

    @Override
    public Optional<T> getOptional(@Nullable Identifier key) {
        return Optional.ofNullable(super.getValue(key));
    }

    @Override
    public Optional<Holder.Reference<T>> getAny() {
        return Optional.ofNullable(this.defaultValue);
    }

    @Override
    public T byId(int id) {
        T object = super.byId(id);
        return object == null ? this.defaultValue.value() : object;
    }

    @Override
    public Optional<Holder.Reference<T>> getRandom(RandomSource random) {
        return super.getRandom(random).or(() -> Optional.of(this.defaultValue));
    }

    @Override
    public Identifier getDefaultKey() {
        return this.defaultKey;
    }
}
