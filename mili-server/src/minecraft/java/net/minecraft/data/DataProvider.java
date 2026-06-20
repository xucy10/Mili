package net.minecraft.data;

import com.google.common.hash.Hashing;
import com.google.common.hash.HashingOutputStream;
import com.google.gson.JsonElement;
import com.google.gson.stream.JsonWriter;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Util;
import org.slf4j.Logger;

public interface DataProvider {
    ToIntFunction<String> FIXED_ORDER_FIELDS = Util.make(new Object2IntOpenHashMap<>(), map -> {
        map.put("type", 0);
        map.put("parent", 1);
        map.defaultReturnValue(2);
    });
    Comparator<String> KEY_COMPARATOR = Comparator.comparingInt(FIXED_ORDER_FIELDS).thenComparing(string -> (String)string);
    Logger LOGGER = LogUtils.getLogger();

    CompletableFuture<?> run(CachedOutput output);

    String getName();

    static <T> CompletableFuture<?> saveAll(CachedOutput output, Codec<T> codec, PackOutput.PathProvider pathProvider, Map<Identifier, T> entries) {
        return saveAll(output, codec, pathProvider::json, entries);
    }

    static <T, E> CompletableFuture<?> saveAll(CachedOutput output, Codec<E> codec, Function<T, Path> pathGetter, Map<T, E> entries) {
        return saveAll(output, object -> codec.encodeStart(JsonOps.INSTANCE, (E)object).getOrThrow(), pathGetter, entries);
    }

    static <T, E> CompletableFuture<?> saveAll(CachedOutput output, Function<E, JsonElement> serializer, Function<T, Path> pathGetter, Map<T, E> entries) {
        return CompletableFuture.allOf(entries.entrySet().stream().map(entry -> {
            Path path = pathGetter.apply(entry.getKey());
            JsonElement jsonElement = serializer.apply(entry.getValue());
            return saveStable(output, jsonElement, path);
        }).toArray(CompletableFuture[]::new));
    }

    static <T> CompletableFuture<?> saveStable(CachedOutput output, HolderLookup.Provider registries, Codec<T> codec, T value, Path path) {
        RegistryOps<JsonElement> registryOps = registries.createSerializationContext(JsonOps.INSTANCE);
        return saveStable(output, registryOps, codec, value, path);
    }

    static <T> CompletableFuture<?> saveStable(CachedOutput output, Codec<T> codec, T value, Path path) {
        return saveStable(output, JsonOps.INSTANCE, codec, value, path);
    }

    private static <T> CompletableFuture<?> saveStable(CachedOutput output, DynamicOps<JsonElement> ops, Codec<T> codec, T value, Path path) {
        JsonElement jsonElement = codec.encodeStart(ops, value).getOrThrow();
        return saveStable(output, jsonElement, path);
    }

    static CompletableFuture<?> saveStable(CachedOutput output, JsonElement json, Path path) {
        return CompletableFuture.runAsync(() -> {
            try {
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                HashingOutputStream hashingOutputStream = new HashingOutputStream(Hashing.sha1(), byteArrayOutputStream);

                try (JsonWriter jsonWriter = new JsonWriter(new OutputStreamWriter(hashingOutputStream, StandardCharsets.UTF_8))) {
                    jsonWriter.setSerializeNulls(false);
                    jsonWriter.setIndent("  ");
                    GsonHelper.writeValue(jsonWriter, json, KEY_COMPARATOR);
                }

                output.writeIfNeeded(path, byteArrayOutputStream.toByteArray(), hashingOutputStream.hash());
            } catch (IOException var10) {
                LOGGER.error("Failed to save file to {}", path, var10);
            }
        }, Util.backgroundExecutor().forName("saveStable"));
    }

    @FunctionalInterface
    public interface Factory<T extends DataProvider> {
        T create(PackOutput output);
    }
}
