package net.minecraft.network.syncher;

import com.mojang.logging.LogUtils;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.util.ClassTreeIdRegistry;
import org.apache.commons.lang3.ObjectUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class SynchedEntityData {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_ID_VALUE = 254;
    static final ClassTreeIdRegistry ID_REGISTRY = new ClassTreeIdRegistry();
    private final SyncedDataHolder entity;
    private final SynchedEntityData.DataItem<?>[] itemsById;
    private boolean isDirty;

    SynchedEntityData(SyncedDataHolder entity, SynchedEntityData.DataItem<?>[] itemsById) {
        this.entity = entity;
        this.itemsById = itemsById;
    }

    public static <T> EntityDataAccessor<T> defineId(Class<? extends SyncedDataHolder> clazz, EntityDataSerializer<T> serializer) {
        if (LOGGER.isDebugEnabled()) {
            try {
                Class<?> clazz1 = Class.forName(Thread.currentThread().getStackTrace()[2].getClassName());
                if (!clazz1.equals(clazz)) {
                    LOGGER.debug("defineId called for: {} from {}", clazz, clazz1, new RuntimeException());
                }
            } catch (ClassNotFoundException var3) {
            }
        }

        int i = ID_REGISTRY.define(clazz);
        if (i > 254) {
            throw new IllegalArgumentException("Data value id is too big with " + i + "! (Max is 254)");
        } else {
            return serializer.createAccessor(i);
        }
    }

    public <T> SynchedEntityData.DataItem<T> getItem(EntityDataAccessor<T> key) {
        return (SynchedEntityData.DataItem<T>)this.itemsById[key.id()];
    }

    public <T> T get(EntityDataAccessor<T> key) {
        return this.getItem(key).getValue();
    }

    public <T> void set(EntityDataAccessor<T> key, T value) {
        this.set(key, value, false);
    }

    public <T> void set(EntityDataAccessor<T> key, T value, boolean force) {
        SynchedEntityData.DataItem<T> item = this.getItem(key);
        if (force || ObjectUtils.notEqual(value, item.getValue())) {
            item.setValue(value);
            this.entity.onSyncedDataUpdated(key);
            item.setDirty(true);
            this.isDirty = true;
        }
    }

    // CraftBukkit start - add method from above
    public <T> void markDirty(final EntityDataAccessor<T> entityDataAccessor) {
        this.getItem(entityDataAccessor).setDirty(true);
        this.isDirty = true;
    }
    // CraftBukkit end

    public boolean isDirty() {
        return this.isDirty;
    }

    public @Nullable List<SynchedEntityData.DataValue<?>> packDirty() {
        if (!this.isDirty) {
            return null;
        } else {
            this.isDirty = false;
            List<SynchedEntityData.DataValue<?>> list = new ArrayList<>();

            for (SynchedEntityData.DataItem<?> dataItem : this.itemsById) {
                if (dataItem.isDirty()) {
                    dataItem.setDirty(false);
                    list.add(dataItem.value());
                }
            }

            return list;
        }
    }

    public @Nullable List<SynchedEntityData.DataValue<?>> getNonDefaultValues() {
        List<SynchedEntityData.DataValue<?>> list = null;

        for (SynchedEntityData.DataItem<?> dataItem : this.itemsById) {
            if (!dataItem.isSetToDefault()) {
                if (list == null) {
                    list = new ArrayList<>();
                }

                list.add(dataItem.value());
            }
        }

        return list;
    }

    public void assignValues(List<SynchedEntityData.DataValue<?>> entries) {
        for (SynchedEntityData.DataValue<?> dataValue : entries) {
            SynchedEntityData.DataItem<?> dataItem = this.itemsById[dataValue.id];
            this.assignValue(dataItem, dataValue);
            this.entity.onSyncedDataUpdated(dataItem.getAccessor());
        }

        this.entity.onSyncedDataUpdated(entries);
    }

    private <T> void assignValue(SynchedEntityData.DataItem<T> target, SynchedEntityData.DataValue<?> entry) {
        if (!Objects.equals(entry.serializer(), target.accessor.serializer())) {
            throw new IllegalStateException(
                String.format(
                    Locale.ROOT,
                    "Invalid entity data item type for field %d on entity %s: old=%s(%s), new=%s(%s)",
                    target.accessor.id(),
                    this.entity,
                    target.value,
                    target.value.getClass(),
                    entry.value,
                    entry.value.getClass()
                )
            );
        } else {
            target.setValue((T)entry.value);
        }
    }

    public static class Builder {
        private final SyncedDataHolder entity;
        private final SynchedEntityData.@Nullable DataItem<?>[] itemsById;

        public Builder(SyncedDataHolder entity) {
            this.entity = entity;
            this.itemsById = new SynchedEntityData.DataItem[SynchedEntityData.ID_REGISTRY.getCount(entity.getClass())];
        }

        public <T> SynchedEntityData.Builder define(EntityDataAccessor<T> accessor, T value) {
            int id = accessor.id();
            if (id > this.itemsById.length) {
                throw new IllegalArgumentException("Data value id is too big with " + id + "! (Max is " + this.itemsById.length + ")");
            } else if (this.itemsById[id] != null) {
                throw new IllegalArgumentException("Duplicate id value for " + id + "!");
            } else if (EntityDataSerializers.getSerializedId(accessor.serializer()) < 0) {
                throw new IllegalArgumentException("Unregistered serializer " + accessor.serializer() + " for " + id + "!");
            } else {
                this.itemsById[accessor.id()] = new SynchedEntityData.DataItem<>(accessor, value);
                return this;
            }
        }

        public SynchedEntityData build() {
            for (int i = 0; i < this.itemsById.length; i++) {
                if (this.itemsById[i] == null) {
                    throw new IllegalStateException("Entity " + this.entity.getClass() + " has not defined synched data value " + i);
                }
            }

            return new SynchedEntityData(this.entity, this.itemsById);
        }
    }

    // Paper start
    // We need to pack all as we cannot rely on "non default values" or "dirty" ones.
    // Because these values can possibly be desynced on the client.
    public List<SynchedEntityData.DataValue<?>> packAll() {
        final List<SynchedEntityData.DataValue<?>> list = new ArrayList<>(this.itemsById.length);
        for (final DataItem<?> dataItem : this.itemsById) {
            list.add(dataItem.value());
        }

        return list;
    }
    // Paper end

    public static class DataItem<T> {
        final EntityDataAccessor<T> accessor;
        T value;
        private final T initialValue;
        private boolean dirty;

        public DataItem(EntityDataAccessor<T> accessor, T value) {
            this.accessor = accessor;
            this.initialValue = value;
            this.value = value;
        }

        public EntityDataAccessor<T> getAccessor() {
            return this.accessor;
        }

        public void setValue(T value) {
            this.value = value;
        }

        public T getValue() {
            return this.value;
        }

        public boolean isDirty() {
            return this.dirty;
        }

        public void setDirty(boolean dirty) {
            this.dirty = dirty;
        }

        public boolean isSetToDefault() {
            return this.initialValue.equals(this.value);
        }

        public SynchedEntityData.DataValue<T> value() {
            return SynchedEntityData.DataValue.create(this.accessor, this.value);
        }
    }

    public record DataValue<T>(int id, EntityDataSerializer<T> serializer, T value) {
        public static <T> SynchedEntityData.DataValue<T> create(EntityDataAccessor<T> dataAccessor, T value) {
            EntityDataSerializer<T> entityDataSerializer = dataAccessor.serializer();
            return new SynchedEntityData.DataValue<>(dataAccessor.id(), entityDataSerializer, entityDataSerializer.copy(value));
        }

        public void write(RegistryFriendlyByteBuf buffer) {
            int serializedId = EntityDataSerializers.getSerializedId(this.serializer);
            if (serializedId < 0) {
                throw new EncoderException("Unknown serializer type " + this.serializer);
            } else {
                buffer.writeByte(this.id);
                buffer.writeVarInt(serializedId);
                this.serializer.codec().encode(buffer, this.value);
            }
        }

        public static SynchedEntityData.DataValue<?> read(RegistryFriendlyByteBuf buffer, int id) {
            int varInt = buffer.readVarInt();
            EntityDataSerializer<?> serializer = EntityDataSerializers.getSerializer(varInt);
            if (serializer == null) {
                throw new DecoderException("Unknown serializer type " + varInt);
            } else {
                return read(buffer, id, serializer);
            }
        }

        private static <T> SynchedEntityData.DataValue<T> read(RegistryFriendlyByteBuf buffer, int id, EntityDataSerializer<T> serializer) {
            return new SynchedEntityData.DataValue<>(id, serializer, serializer.codec().decode(buffer));
        }
    }
}
