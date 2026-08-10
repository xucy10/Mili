package net.minecraft.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import com.google.gson.Strictness;
import com.google.gson.internal.Streams;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.Nullable;

public class GsonHelper {
    private static final Gson GSON = new GsonBuilder().create();

    public static boolean isStringValue(JsonObject json, String memberName) {
        return isValidPrimitive(json, memberName) && json.getAsJsonPrimitive(memberName).isString();
    }

    public static boolean isStringValue(JsonElement json) {
        return json.isJsonPrimitive() && json.getAsJsonPrimitive().isString();
    }

    public static boolean isNumberValue(JsonObject json, String memberName) {
        return isValidPrimitive(json, memberName) && json.getAsJsonPrimitive(memberName).isNumber();
    }

    public static boolean isNumberValue(JsonElement json) {
        return json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber();
    }

    public static boolean isBooleanValue(JsonObject json, String memberName) {
        return isValidPrimitive(json, memberName) && json.getAsJsonPrimitive(memberName).isBoolean();
    }

    public static boolean isBooleanValue(JsonElement json) {
        return json.isJsonPrimitive() && json.getAsJsonPrimitive().isBoolean();
    }

    public static boolean isArrayNode(JsonObject json, String memberName) {
        return isValidNode(json, memberName) && json.get(memberName).isJsonArray();
    }

    public static boolean isObjectNode(JsonObject json, String memberName) {
        return isValidNode(json, memberName) && json.get(memberName).isJsonObject();
    }

    public static boolean isValidPrimitive(JsonObject json, String memberName) {
        return isValidNode(json, memberName) && json.get(memberName).isJsonPrimitive();
    }

    public static boolean isValidNode(@Nullable JsonObject json, String memberName) {
        return json != null && json.get(memberName) != null;
    }

    public static JsonElement getNonNull(JsonObject json, String memberName) {
        JsonElement jsonElement = json.get(memberName);
        if (jsonElement != null && !jsonElement.isJsonNull()) {
            return jsonElement;
        } else {
            throw new JsonSyntaxException("Missing field " + memberName);
        }
    }

    public static String convertToString(JsonElement json, String memberName) {
        if (json.isJsonPrimitive()) {
            return json.getAsString();
        } else {
            throw new JsonSyntaxException("Expected " + memberName + " to be a string, was " + getType(json));
        }
    }

    public static String getAsString(JsonObject json, String memberName) {
        if (json.has(memberName)) {
            return convertToString(json.get(memberName), memberName);
        } else {
            throw new JsonSyntaxException("Missing " + memberName + ", expected to find a string");
        }
    }

    @Contract("_,_,!null->!null;_,_,null->_")
    public static @Nullable String getAsString(JsonObject json, String memberName, @Nullable String fallback) {
        return json.has(memberName) ? convertToString(json.get(memberName), memberName) : fallback;
    }

    public static Holder<Item> convertToItem(JsonElement json, String memberName) {
        if (json.isJsonPrimitive()) {
            String asString = json.getAsString();
            return BuiltInRegistries.ITEM
                .get(Identifier.parse(asString))
                .orElseThrow(() -> new JsonSyntaxException("Expected " + memberName + " to be an item, was unknown string '" + asString + "'"));
        } else {
            throw new JsonSyntaxException("Expected " + memberName + " to be an item, was " + getType(json));
        }
    }

    public static Holder<Item> getAsItem(JsonObject json, String memberName) {
        if (json.has(memberName)) {
            return convertToItem(json.get(memberName), memberName);
        } else {
            throw new JsonSyntaxException("Missing " + memberName + ", expected to find an item");
        }
    }

    @Contract("_,_,!null->!null;_,_,null->_")
    public static @Nullable Holder<Item> getAsItem(JsonObject json, String memberName, @Nullable Holder<Item> fallback) {
        return json.has(memberName) ? convertToItem(json.get(memberName), memberName) : fallback;
    }

    public static boolean convertToBoolean(JsonElement json, String memberName) {
        if (json.isJsonPrimitive()) {
            return json.getAsBoolean();
        } else {
            throw new JsonSyntaxException("Expected " + memberName + " to be a Boolean, was " + getType(json));
        }
    }

    public static boolean getAsBoolean(JsonObject json, String memberName) {
        if (json.has(memberName)) {
            return convertToBoolean(json.get(memberName), memberName);
        } else {
            throw new JsonSyntaxException("Missing " + memberName + ", expected to find a Boolean");
        }
    }

    public static boolean getAsBoolean(JsonObject json, String memberName, boolean fallback) {
        return json.has(memberName) ? convertToBoolean(json.get(memberName), memberName) : fallback;
    }

    public static double convertToDouble(JsonElement json, String memberName) {
        if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
            return json.getAsDouble();
        } else {
            throw new JsonSyntaxException("Expected " + memberName + " to be a Double, was " + getType(json));
        }
    }

    public static double getAsDouble(JsonObject json, String memberName) {
        if (json.has(memberName)) {
            return convertToDouble(json.get(memberName), memberName);
        } else {
            throw new JsonSyntaxException("Missing " + memberName + ", expected to find a Double");
        }
    }

    public static double getAsDouble(JsonObject json, String memberName, double fallback) {
        return json.has(memberName) ? convertToDouble(json.get(memberName), memberName) : fallback;
    }

    public static float convertToFloat(JsonElement json, String memberName) {
        if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
            return json.getAsFloat();
        } else {
            throw new JsonSyntaxException("Expected " + memberName + " to be a Float, was " + getType(json));
        }
    }

    public static float getAsFloat(JsonObject json, String memberName) {
        if (json.has(memberName)) {
            return convertToFloat(json.get(memberName), memberName);
        } else {
            throw new JsonSyntaxException("Missing " + memberName + ", expected to find a Float");
        }
    }

    public static float getAsFloat(JsonObject json, String memberName, float fallback) {
        return json.has(memberName) ? convertToFloat(json.get(memberName), memberName) : fallback;
    }

    public static long convertToLong(JsonElement json, String memberName) {
        if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
            return json.getAsLong();
        } else {
            throw new JsonSyntaxException("Expected " + memberName + " to be a Long, was " + getType(json));
        }
    }

    public static long getAsLong(JsonObject json, String memberName) {
        if (json.has(memberName)) {
            return convertToLong(json.get(memberName), memberName);
        } else {
            throw new JsonSyntaxException("Missing " + memberName + ", expected to find a Long");
        }
    }

    public static long getAsLong(JsonObject json, String memberName, long fallback) {
        return json.has(memberName) ? convertToLong(json.get(memberName), memberName) : fallback;
    }

    public static int convertToInt(JsonElement json, String memberName) {
        if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
            return json.getAsInt();
        } else {
            throw new JsonSyntaxException("Expected " + memberName + " to be a Int, was " + getType(json));
        }
    }

    public static int getAsInt(JsonObject json, String memberName) {
        if (json.has(memberName)) {
            return convertToInt(json.get(memberName), memberName);
        } else {
            throw new JsonSyntaxException("Missing " + memberName + ", expected to find a Int");
        }
    }

    public static int getAsInt(JsonObject json, String memberName, int fallback) {
        return json.has(memberName) ? convertToInt(json.get(memberName), memberName) : fallback;
    }

    public static byte convertToByte(JsonElement json, String memberName) {
        if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
            return json.getAsByte();
        } else {
            throw new JsonSyntaxException("Expected " + memberName + " to be a Byte, was " + getType(json));
        }
    }

    public static byte getAsByte(JsonObject json, String memberName) {
        if (json.has(memberName)) {
            return convertToByte(json.get(memberName), memberName);
        } else {
            throw new JsonSyntaxException("Missing " + memberName + ", expected to find a Byte");
        }
    }

    public static byte getAsByte(JsonObject json, String memberName, byte fallback) {
        return json.has(memberName) ? convertToByte(json.get(memberName), memberName) : fallback;
    }

    public static char convertToCharacter(JsonElement json, String memberName) {
        if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
            return json.getAsCharacter();
        } else {
            throw new JsonSyntaxException("Expected " + memberName + " to be a Character, was " + getType(json));
        }
    }

    public static char getAsCharacter(JsonObject json, String memberName) {
        if (json.has(memberName)) {
            return convertToCharacter(json.get(memberName), memberName);
        } else {
            throw new JsonSyntaxException("Missing " + memberName + ", expected to find a Character");
        }
    }

    public static char getAsCharacter(JsonObject json, String memberName, char fallback) {
        return json.has(memberName) ? convertToCharacter(json.get(memberName), memberName) : fallback;
    }

    public static BigDecimal convertToBigDecimal(JsonElement json, String memberName) {
        if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
            return json.getAsBigDecimal();
        } else {
            throw new JsonSyntaxException("Expected " + memberName + " to be a BigDecimal, was " + getType(json));
        }
    }

    public static BigDecimal getAsBigDecimal(JsonObject json, String memberName) {
        if (json.has(memberName)) {
            return convertToBigDecimal(json.get(memberName), memberName);
        } else {
            throw new JsonSyntaxException("Missing " + memberName + ", expected to find a BigDecimal");
        }
    }

    public static BigDecimal getAsBigDecimal(JsonObject json, String memberName, BigDecimal fallback) {
        return json.has(memberName) ? convertToBigDecimal(json.get(memberName), memberName) : fallback;
    }

    public static BigInteger convertToBigInteger(JsonElement json, String memberName) {
        if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
            return json.getAsBigInteger();
        } else {
            throw new JsonSyntaxException("Expected " + memberName + " to be a BigInteger, was " + getType(json));
        }
    }

    public static BigInteger getAsBigInteger(JsonObject json, String memberName) {
        if (json.has(memberName)) {
            return convertToBigInteger(json.get(memberName), memberName);
        } else {
            throw new JsonSyntaxException("Missing " + memberName + ", expected to find a BigInteger");
        }
    }

    public static BigInteger getAsBigInteger(JsonObject json, String memberName, BigInteger fallback) {
        return json.has(memberName) ? convertToBigInteger(json.get(memberName), memberName) : fallback;
    }

    public static short convertToShort(JsonElement json, String memberName) {
        if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
            return json.getAsShort();
        } else {
            throw new JsonSyntaxException("Expected " + memberName + " to be a Short, was " + getType(json));
        }
    }

    public static short getAsShort(JsonObject json, String memberName) {
        if (json.has(memberName)) {
            return convertToShort(json.get(memberName), memberName);
        } else {
            throw new JsonSyntaxException("Missing " + memberName + ", expected to find a Short");
        }
    }

    public static short getAsShort(JsonObject json, String memberName, short fallback) {
        return json.has(memberName) ? convertToShort(json.get(memberName), memberName) : fallback;
    }

    public static JsonObject convertToJsonObject(JsonElement json, String memberName) {
        if (json.isJsonObject()) {
            return json.getAsJsonObject();
        } else {
            throw new JsonSyntaxException("Expected " + memberName + " to be a JsonObject, was " + getType(json));
        }
    }

    public static JsonObject getAsJsonObject(JsonObject json, String memberName) {
        if (json.has(memberName)) {
            return convertToJsonObject(json.get(memberName), memberName);
        } else {
            throw new JsonSyntaxException("Missing " + memberName + ", expected to find a JsonObject");
        }
    }

    @Contract("_,_,!null->!null;_,_,null->_")
    public static @Nullable JsonObject getAsJsonObject(JsonObject json, String memberName, @Nullable JsonObject fallback) {
        return json.has(memberName) ? convertToJsonObject(json.get(memberName), memberName) : fallback;
    }

    public static JsonArray convertToJsonArray(JsonElement json, String memberName) {
        if (json.isJsonArray()) {
            return json.getAsJsonArray();
        } else {
            throw new JsonSyntaxException("Expected " + memberName + " to be a JsonArray, was " + getType(json));
        }
    }

    public static JsonArray getAsJsonArray(JsonObject json, String memberName) {
        if (json.has(memberName)) {
            return convertToJsonArray(json.get(memberName), memberName);
        } else {
            throw new JsonSyntaxException("Missing " + memberName + ", expected to find a JsonArray");
        }
    }

    @Contract("_,_,!null->!null;_,_,null->_")
    public static @Nullable JsonArray getAsJsonArray(JsonObject json, String memberName, @Nullable JsonArray fallback) {
        return json.has(memberName) ? convertToJsonArray(json.get(memberName), memberName) : fallback;
    }

    public static <T> T convertToObject(@Nullable JsonElement json, String memberName, JsonDeserializationContext context, Class<? extends T> adapter) {
        if (json != null) {
            return context.deserialize(json, adapter);
        } else {
            throw new JsonSyntaxException("Missing " + memberName);
        }
    }

    public static <T> T getAsObject(JsonObject json, String memberName, JsonDeserializationContext context, Class<? extends T> adapter) {
        if (json.has(memberName)) {
            return convertToObject(json.get(memberName), memberName, context, adapter);
        } else {
            throw new JsonSyntaxException("Missing " + memberName);
        }
    }

    @Contract("_,_,!null,_,_->!null;_,_,null,_,_->_")
    public static <T> @Nullable T getAsObject(
        JsonObject json, String memberName, @Nullable T fallback, JsonDeserializationContext context, Class<? extends T> adapter
    ) {
        return json.has(memberName) ? convertToObject(json.get(memberName), memberName, context, adapter) : fallback;
    }

    public static String getType(@Nullable JsonElement json) {
        String string = StringUtils.abbreviateMiddle(String.valueOf(json), "...", 10);
        if (json == null) {
            return "null (missing)";
        } else if (json.isJsonNull()) {
            return "null (json)";
        } else if (json.isJsonArray()) {
            return "an array (" + string + ")";
        } else if (json.isJsonObject()) {
            return "an object (" + string + ")";
        } else {
            if (json.isJsonPrimitive()) {
                JsonPrimitive asJsonPrimitive = json.getAsJsonPrimitive();
                if (asJsonPrimitive.isNumber()) {
                    return "a number (" + string + ")";
                }

                if (asJsonPrimitive.isBoolean()) {
                    return "a boolean (" + string + ")";
                }
            }

            return string;
        }
    }

    public static <T> T fromJson(Gson gson, Reader reader, Class<T> adapter) {
        try {
            JsonReader jsonReader = new JsonReader(reader);
            jsonReader.setStrictness(Strictness.STRICT);
            T object = gson.getAdapter(adapter).read(jsonReader);
            if (object == null) {
                throw new JsonParseException("JSON data was null or empty");
            } else {
                return object;
            }
        } catch (IOException var5) {
            throw new JsonParseException(var5);
        }
    }

    public static <T> @Nullable T fromNullableJson(Gson gson, Reader reader, TypeToken<T> token) {
        try {
            JsonReader jsonReader = new JsonReader(reader);
            jsonReader.setStrictness(Strictness.STRICT);
            return gson.getAdapter(token).read(jsonReader);
        } catch (IOException var4) {
            throw new JsonParseException(var4);
        }
    }

    public static <T> T fromJson(Gson gson, Reader reader, TypeToken<T> type) {
        T object = fromNullableJson(gson, reader, type);
        if (object == null) {
            throw new JsonParseException("JSON data was null or empty");
        } else {
            return object;
        }
    }

    public static <T> @Nullable T fromNullableJson(Gson gson, String json, TypeToken<T> type) {
        return fromNullableJson(gson, new StringReader(json), type);
    }

    public static <T> T fromJson(Gson gson, String json, Class<T> adapter) {
        return fromJson(gson, new StringReader(json), adapter);
    }

    public static JsonObject parse(String json) {
        return parse(new StringReader(json));
    }

    public static JsonObject parse(Reader reader) {
        return fromJson(GSON, reader, JsonObject.class);
    }

    public static JsonArray parseArray(String string) {
        return parseArray(new StringReader(string));
    }

    public static JsonArray parseArray(Reader reader) {
        return fromJson(GSON, reader, JsonArray.class);
    }

    public static String toStableString(JsonElement json) {
        StringWriter stringWriter = new StringWriter();
        JsonWriter jsonWriter = new JsonWriter(stringWriter);

        try {
            writeValue(jsonWriter, json, Comparator.naturalOrder());
        } catch (IOException var4) {
            throw new AssertionError(var4);
        }

        return stringWriter.toString();
    }

    public static void writeValue(JsonWriter writer, @Nullable JsonElement jsonElement, @Nullable Comparator<String> sorter) throws IOException {
        if (jsonElement == null || jsonElement.isJsonNull()) {
            writer.nullValue();
        } else if (jsonElement.isJsonPrimitive()) {
            JsonPrimitive asJsonPrimitive = jsonElement.getAsJsonPrimitive();
            if (asJsonPrimitive.isNumber()) {
                writer.value(asJsonPrimitive.getAsNumber());
            } else if (asJsonPrimitive.isBoolean()) {
                writer.value(asJsonPrimitive.getAsBoolean());
            } else {
                writer.value(asJsonPrimitive.getAsString());
            }
        } else if (jsonElement.isJsonArray()) {
            writer.beginArray();

            for (JsonElement jsonElement1 : jsonElement.getAsJsonArray()) {
                writeValue(writer, jsonElement1, sorter);
            }

            writer.endArray();
        } else {
            if (!jsonElement.isJsonObject()) {
                throw new IllegalArgumentException("Couldn't write " + jsonElement.getClass());
            }

            writer.beginObject();

            for (Entry<String, JsonElement> entry : sortByKeyIfNeeded(jsonElement.getAsJsonObject().entrySet(), sorter)) {
                writer.name(entry.getKey());
                writeValue(writer, entry.getValue(), sorter);
            }

            writer.endObject();
        }
    }

    private static Collection<Entry<String, JsonElement>> sortByKeyIfNeeded(Collection<Entry<String, JsonElement>> entries, @Nullable Comparator<String> sorter) {
        if (sorter == null) {
            return entries;
        } else {
            List<Entry<String, JsonElement>> list = new ArrayList<>(entries);
            list.sort(Entry.comparingByKey(sorter));
            return list;
        }
    }

    public static boolean encodesLongerThan(JsonElement element, int maxLength) {
        try {
            Streams.write(element, new JsonWriter(Streams.writerForAppendable(new GsonHelper.CountedAppendable(maxLength))));
            return false;
        } catch (IllegalStateException var3) {
            return true;
        } catch (IOException var4) {
            throw new UncheckedIOException(var4);
        }
    }

    static class CountedAppendable implements Appendable {
        private int totalCount;
        private final int limit;

        public CountedAppendable(int limit) {
            this.limit = limit;
        }

        private Appendable accountChars(int count) {
            this.totalCount += count;
            if (this.totalCount > this.limit) {
                throw new IllegalStateException("Character count over limit: " + this.totalCount + " > " + this.limit);
            } else {
                return this;
            }
        }

        @Override
        public Appendable append(CharSequence sequence) {
            return this.accountChars(sequence.length());
        }

        @Override
        public Appendable append(CharSequence sequence, int start, int end) {
            return this.accountChars(end - start);
        }

        @Override
        public Appendable append(char character) {
            return this.accountChars(1);
        }
    }
}
