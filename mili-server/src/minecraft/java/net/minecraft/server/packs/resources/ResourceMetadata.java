package net.minecraft.server.packs.resources;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.serialization.JsonOps;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.util.GsonHelper;

public interface ResourceMetadata {
    ResourceMetadata EMPTY = new ResourceMetadata() {
        @Override
        public <T> Optional<T> getSection(MetadataSectionType<T> type) {
            return Optional.empty();
        }
    };
    IoSupplier<ResourceMetadata> EMPTY_SUPPLIER = () -> EMPTY;

    static ResourceMetadata fromJsonStream(InputStream stream) throws IOException {
        ResourceMetadata var3;
        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            final JsonObject jsonObject = GsonHelper.parse(bufferedReader);
            var3 = new ResourceMetadata() {
                @Override
                public <T> Optional<T> getSection(MetadataSectionType<T> type) {
                    String string = type.name();
                    if (jsonObject.has(string)) {
                        T orThrow = type.codec().parse(JsonOps.INSTANCE, jsonObject.get(string)).getOrThrow(JsonParseException::new);
                        return Optional.of(orThrow);
                    } else {
                        return Optional.empty();
                    }
                }
            };
        }

        return var3;
    }

    <T> Optional<T> getSection(MetadataSectionType<T> type);

    default <T> Optional<MetadataSectionType.WithValue<T>> getTypedSection(MetadataSectionType<T> type) {
        return this.getSection(type).map(type::withValue);
    }

    default List<MetadataSectionType.WithValue<?>> getTypedSections(Collection<MetadataSectionType<?>> types) {
        return types.stream().map(this::getTypedSection).flatMap(Optional::stream).collect(Collectors.toUnmodifiableList());
    }
}
