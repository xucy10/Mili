package net.minecraft.core.component;

import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMaps;
import it.unimi.dsi.fastutil.objects.ReferenceArraySet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

public final class PatchedDataComponentMap implements DataComponentMap, org.leavesmc.leaves.lithium.common.util.change_tracking.ChangePublisher<net.minecraft.core.component.PatchedDataComponentMap> { // Leaves - Lithium Sleeping Block Entity
    private final DataComponentMap prototype;
    private Reference2ObjectMap<DataComponentType<?>, Optional<?>> patch;
    private boolean copyOnWrite;

    public PatchedDataComponentMap(DataComponentMap prototype) {
        this(prototype, Reference2ObjectMaps.emptyMap(), true);
    }

    private PatchedDataComponentMap(DataComponentMap prototype, Reference2ObjectMap<DataComponentType<?>, Optional<?>> patch, boolean copyOnWrite) {
        this.prototype = prototype;
        this.patch = patch;
        this.copyOnWrite = copyOnWrite;
    }

    public static PatchedDataComponentMap fromPatch(DataComponentMap prototype, DataComponentPatch patch) {
        if (isPatchSanitized(prototype, patch.map)) {
            return new PatchedDataComponentMap(prototype, patch.map, true);
        } else {
            PatchedDataComponentMap patchedDataComponentMap = new PatchedDataComponentMap(prototype);
            patchedDataComponentMap.applyPatch(patch);
            return patchedDataComponentMap;
        }
    }

    private static boolean isPatchSanitized(DataComponentMap prototype, Reference2ObjectMap<DataComponentType<?>, Optional<?>> map) {
        for (Entry<DataComponentType<?>, Optional<?>> entry : Reference2ObjectMaps.fastIterable(map)) {
            Object object = prototype.get(entry.getKey());
            Optional<?> optional = entry.getValue();
            if (optional.isPresent() && optional.get().equals(object)) {
                return false;
            }

            if (optional.isEmpty() && object == null) {
                return false;
            }
        }

        return true;
    }

    @Override
    public <T> @Nullable T get(DataComponentType<? extends T> component) {
        Optional<? extends T> optional = (Optional<? extends T>)this.patch.get(component);
        return (T)(optional != null ? optional.orElse(null) : this.prototype.get(component));
    }

    public boolean hasNonDefault(DataComponentType<?> component) {
        return this.patch.containsKey(component);
    }

    public <T> @Nullable T set(DataComponentType<T> component, @Nullable T value) {
        this.ensureMapOwnership();
        T object = this.prototype.get(component);
        Optional<T> optional;
        if (Objects.equals(value, object)) {
            optional = (Optional<T>)this.patch.remove(component);
        } else {
            optional = (Optional<T>)this.patch.put(component, Optional.ofNullable(value));
        }

        return optional != null ? optional.orElse(object) : object;
    }

    public <T> @Nullable T set(TypedDataComponent<T> component) {
        return this.set(component.type(), component.value());
    }

    public <T> @Nullable T remove(DataComponentType<? extends T> component) {
        this.ensureMapOwnership();
        T object = this.prototype.get(component);
        Optional<? extends T> optional;
        if (object != null) {
            optional = (Optional<? extends T>)this.patch.put(component, Optional.empty());
        } else {
            optional = (Optional<? extends T>)this.patch.remove(component);
        }

        return (T)(optional != null ? optional.orElse(null) : object);
    }

    public void applyPatch(DataComponentPatch patch) {
        this.ensureMapOwnership();

        for (Entry<DataComponentType<?>, Optional<?>> entry : Reference2ObjectMaps.fastIterable(patch.map)) {
            this.applyPatch(entry.getKey(), entry.getValue());
        }
    }

    private void applyPatch(DataComponentType<?> component, Optional<?> value) {
        Object object = this.prototype.get(component);
        if (value.isPresent()) {
            if (value.get().equals(object)) {
                this.patch.remove(component);
            } else {
                this.patch.put(component, value);
            }
        } else if (object != null) {
            this.patch.put(component, Optional.empty());
        } else {
            this.patch.remove(component);
        }
    }

    public void restorePatch(DataComponentPatch patch) {
        this.ensureMapOwnership();
        this.patch.clear();
        this.patch.putAll(patch.map);
    }

    public void clearPatch() {
        this.ensureMapOwnership();
        this.patch.clear();
    }

    public void setAll(DataComponentMap map) {
        for (TypedDataComponent<?> typedDataComponent : map) {
            typedDataComponent.applyTo(this);
        }
    }

    private void ensureMapOwnership() {
        if (me.earthme.luminol.config.modules.optimizations.LeavesSleepingBlockEntityConfig.enabled && this.subscriber != null) this.subscriber.lithium$notify((PatchedDataComponentMap) (Object) this, 0); // Leaves - Lithium Sleeping Block Entity
        if (this.copyOnWrite) {
            this.patch = new Reference2ObjectArrayMap<>(this.patch);
            this.copyOnWrite = false;
        }
    }

    @Override
    public Set<DataComponentType<?>> keySet() {
        if (this.patch.isEmpty()) {
            return this.prototype.keySet();
        } else {
            Set<DataComponentType<?>> set = new ReferenceArraySet<>(this.prototype.keySet());

            for (it.unimi.dsi.fastutil.objects.Reference2ObjectMap.Entry<DataComponentType<?>, Optional<?>> entry : Reference2ObjectMaps.fastIterable(
                this.patch
            )) {
                Optional<?> optional = entry.getValue();
                if (optional.isPresent()) {
                    set.add(entry.getKey());
                } else {
                    set.remove(entry.getKey());
                }
            }

            return set;
        }
    }

    @Override
    public Iterator<TypedDataComponent<?>> iterator() {
        if (this.patch.isEmpty()) {
            return this.prototype.iterator();
        } else {
            List<TypedDataComponent<?>> list = new ArrayList<>(this.patch.size() + this.prototype.size());

            for (it.unimi.dsi.fastutil.objects.Reference2ObjectMap.Entry<DataComponentType<?>, Optional<?>> entry : Reference2ObjectMaps.fastIterable(
                this.patch
            )) {
                if (entry.getValue().isPresent()) {
                    list.add(TypedDataComponent.createUnchecked(entry.getKey(), entry.getValue().get()));
                }
            }

            for (TypedDataComponent<?> typedDataComponent : this.prototype) {
                if (!this.patch.containsKey(typedDataComponent.type())) {
                    list.add(typedDataComponent);
                }
            }

            return list.iterator();
        }
    }

    @Override
    public int size() {
        int size = this.prototype.size();

        for (it.unimi.dsi.fastutil.objects.Reference2ObjectMap.Entry<DataComponentType<?>, Optional<?>> entry : Reference2ObjectMaps.fastIterable(this.patch)) {
            boolean isPresent = entry.getValue().isPresent();
            boolean hasDataComponentType = this.prototype.has(entry.getKey());
            if (isPresent != hasDataComponentType) {
                size += isPresent ? 1 : -1;
            }
        }

        return size;
    }

    public DataComponentPatch asPatch() {
        if (this.patch.isEmpty()) {
            return DataComponentPatch.EMPTY;
        } else {
            this.copyOnWrite = true;
            return new DataComponentPatch(this.patch);
        }
    }

    public PatchedDataComponentMap copy() {
        this.copyOnWrite = true;
        return new PatchedDataComponentMap(this.prototype, this.patch, true);
    }

    public DataComponentMap toImmutableMap() {
        return (DataComponentMap)(this.patch.isEmpty() ? this.prototype : this.copy());
    }

    @Override
    public boolean equals(Object other) {
        return this == other
            || other instanceof PatchedDataComponentMap patchedDataComponentMap
                && this.prototype.equals(patchedDataComponentMap.prototype)
                && this.patch.equals(patchedDataComponentMap.patch);
    }

    @Override
    public int hashCode() {
        return this.prototype.hashCode() + this.patch.hashCode() * 31;
    }

    @Override
    public String toString() {
        return "{" + this.stream().map(TypedDataComponent::toString).collect(Collectors.joining(", ")) + "}";
    }

    // Leaves start - Lithium Sleeping Block Entity
    private org.leavesmc.leaves.lithium.common.util.change_tracking.ChangeSubscriber<net.minecraft.core.component.PatchedDataComponentMap> subscriber;

    @Override
    public void lithium$subscribe(org.leavesmc.leaves.lithium.common.util.change_tracking.ChangeSubscriber<net.minecraft.core.component.PatchedDataComponentMap> subscriber, int subscriberData) {
        if (subscriberData != 0) {
            throw new UnsupportedOperationException("ComponentMapImpl does not support subscriber data");
        }
        this.subscriber = org.leavesmc.leaves.lithium.common.util.change_tracking.ChangeSubscriber.combine(this.subscriber, 0, subscriber, 0);
    }

    @Override
    public int lithium$unsubscribe(org.leavesmc.leaves.lithium.common.util.change_tracking.ChangeSubscriber<net.minecraft.core.component.PatchedDataComponentMap> subscriber) {
        this.subscriber = org.leavesmc.leaves.lithium.common.util.change_tracking.ChangeSubscriber.without(this.subscriber, subscriber);
        return 0;
    }
    // Leaves end - Lithium Sleeping Block Entity
}
