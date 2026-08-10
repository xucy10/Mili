package net.minecraft.server.jsonrpc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;
import org.jspecify.annotations.Nullable;

public class JsonRPCUtils {
    public static final String JSON_RPC_VERSION = "2.0";
    public static final String OPEN_RPC_VERSION = "1.3.2";

    public static JsonObject createSuccessResult(JsonElement requestId, JsonElement result) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("jsonrpc", "2.0");
        jsonObject.add("id", requestId);
        jsonObject.add("result", result);
        return jsonObject;
    }

    public static JsonObject createRequest(@Nullable Integer requestId, Identifier method, List<JsonElement> params) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("jsonrpc", "2.0");
        if (requestId != null) {
            jsonObject.addProperty("id", requestId);
        }

        jsonObject.addProperty("method", method.toString());
        if (!params.isEmpty()) {
            JsonArray jsonArray = new JsonArray(params.size());

            for (JsonElement jsonElement : params) {
                jsonArray.add(jsonElement);
            }

            jsonObject.add("params", jsonArray);
        }

        return jsonObject;
    }

    public static JsonObject createError(JsonElement requestId, String message, int code, @Nullable String data) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("jsonrpc", "2.0");
        jsonObject.add("id", requestId);
        JsonObject jsonObject1 = new JsonObject();
        jsonObject1.addProperty("code", code);
        jsonObject1.addProperty("message", message);
        if (data != null && !data.isBlank()) {
            jsonObject1.addProperty("data", data);
        }

        jsonObject.add("error", jsonObject1);
        return jsonObject;
    }

    public static @Nullable JsonElement getRequestId(JsonObject json) {
        return json.get("id");
    }

    public static @Nullable String getMethodName(JsonObject json) {
        return GsonHelper.getAsString(json, "method", null);
    }

    public static @Nullable JsonElement getParams(JsonObject json) {
        return json.get("params");
    }

    public static @Nullable JsonElement getResult(JsonObject json) {
        return json.get("result");
    }

    public static @Nullable JsonObject getError(JsonObject json) {
        return GsonHelper.getAsJsonObject(json, "error", null);
    }
}
