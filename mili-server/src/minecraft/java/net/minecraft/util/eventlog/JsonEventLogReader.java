package net.minecraft.util.eventlog;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.Reader;
import org.jspecify.annotations.Nullable;

public interface JsonEventLogReader<T> extends Closeable {
    static <T> JsonEventLogReader<T> create(final Codec<T> codec, Reader reader) {
        final JsonReader jsonReader = new JsonReader(reader);
        jsonReader.setStrictness(Strictness.LENIENT);
        return new JsonEventLogReader<T>() {
            @Override
            public @Nullable T next() throws IOException {
                try {
                    if (!jsonReader.hasNext()) {
                        return null;
                    } else {
                        JsonElement jsonElement = JsonParser.parseReader(jsonReader);
                        return codec.parse(JsonOps.INSTANCE, jsonElement).getOrThrow(IOException::new);
                    }
                } catch (JsonParseException var2) {
                    throw new IOException(var2);
                } catch (EOFException var3) {
                    return null;
                }
            }

            @Override
            public void close() throws IOException {
                jsonReader.close();
            }
        };
    }

    @Nullable T next() throws IOException;
}
