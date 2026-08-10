package net.minecraft.world.level.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

public class CommandStorage {
    private static final String ID_PREFIX = "command_storage_";
    private final Map<String, CommandStorage.Container> namespaces = new HashMap<>();
    private final DimensionDataStorage storage;

    public CommandStorage(DimensionDataStorage storage) {
        this.storage = storage;
    }

    public CompoundTag get(Identifier id) {
        CommandStorage.Container container = this.getContainer(id.getNamespace());
        return container != null ? container.get(id.getPath()) : new CompoundTag();
    }

    private CommandStorage.@Nullable Container getContainer(String namespace) {
        CommandStorage.Container container = this.namespaces.get(namespace);
        if (container != null) {
            return container;
        } else {
            CommandStorage.Container container1 = this.storage.get(CommandStorage.Container.type(namespace));
            if (container1 != null) {
                this.namespaces.put(namespace, container1);
            }

            return container1;
        }
    }

    private CommandStorage.Container getOrCreateContainer(String namespace) {
        CommandStorage.Container container = this.namespaces.get(namespace);
        if (container != null) {
            return container;
        } else {
            CommandStorage.Container container1 = this.storage.computeIfAbsent(CommandStorage.Container.type(namespace));
            this.namespaces.put(namespace, container1);
            return container1;
        }
    }

    public void set(Identifier id, CompoundTag tag) {
        this.getOrCreateContainer(id.getNamespace()).put(id.getPath(), tag);
    }

    public Stream<Identifier> keys() {
        return this.namespaces.entrySet().stream().flatMap(entry -> entry.getValue().getKeys(entry.getKey()));
    }

    static String createId(String namespace) {
        return "command_storage_" + namespace;
    }

    static class Container extends SavedData {
        public static final Codec<CommandStorage.Container> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Codec.unboundedMap(ExtraCodecs.RESOURCE_PATH_CODEC, CompoundTag.CODEC).fieldOf("contents").forGetter(container -> container.storage)
                )
                .apply(instance, CommandStorage.Container::new)
        );
        private final Map<String, CompoundTag> storage;

        private Container(Map<String, CompoundTag> storage) {
            this.storage = new HashMap<>(storage);
        }

        private Container() {
            this(new HashMap<>());
        }

        public static SavedDataType<CommandStorage.Container> type(String namespace) {
            return new SavedDataType<>(CommandStorage.createId(namespace), CommandStorage.Container::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
        }

        public CompoundTag get(String id) {
            CompoundTag compoundTag = this.storage.get(id);
            return compoundTag != null ? compoundTag : new CompoundTag();
        }

        public void put(String id, CompoundTag tag) {
            if (tag.isEmpty()) {
                this.storage.remove(id);
            } else {
                this.storage.put(id, tag);
            }

            this.setDirty();
        }

        public Stream<Identifier> getKeys(String namespace) {
            return this.storage.keySet().stream().map(string -> Identifier.fromNamespaceAndPath(namespace, string));
        }
    }
}
