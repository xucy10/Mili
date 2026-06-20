package net.minecraft.server.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import net.minecraft.network.chat.FilterMask;
import net.minecraft.util.GsonHelper;
import org.jspecify.annotations.Nullable;

public class LegacyTextFilter extends ServerTextFilter {
    private static final String ENDPOINT = "v1/chat";
    final URL joinEndpoint;
    final LegacyTextFilter.JoinOrLeaveEncoder joinEncoder;
    final URL leaveEndpoint;
    final LegacyTextFilter.JoinOrLeaveEncoder leaveEncoder;
    private final String authKey;

    private LegacyTextFilter(
        URL chatEndpoint,
        ServerTextFilter.MessageEncoder chatEncoder,
        URL joinEndpoint,
        LegacyTextFilter.JoinOrLeaveEncoder joinEncoder,
        URL leaveEndpoint,
        LegacyTextFilter.JoinOrLeaveEncoder leaveEncoder,
        String authKey,
        ServerTextFilter.IgnoreStrategy chatIgnoreStrategy,
        ExecutorService workerPool
    ) {
        super(chatEndpoint, chatEncoder, chatIgnoreStrategy, workerPool);
        this.joinEndpoint = joinEndpoint;
        this.joinEncoder = joinEncoder;
        this.leaveEndpoint = leaveEndpoint;
        this.leaveEncoder = leaveEncoder;
        this.authKey = authKey;
    }

    public static @Nullable ServerTextFilter createTextFilterFromConfig(String config) {
        try {
            JsonObject jsonObject = GsonHelper.parse(config);
            URI uri = new URI(GsonHelper.getAsString(jsonObject, "apiServer"));
            String asString = GsonHelper.getAsString(jsonObject, "apiKey");
            if (asString.isEmpty()) {
                throw new IllegalArgumentException("Missing API key");
            } else {
                int asInt = GsonHelper.getAsInt(jsonObject, "ruleId", 1);
                String asString1 = GsonHelper.getAsString(jsonObject, "serverId", "");
                String asString2 = GsonHelper.getAsString(jsonObject, "roomId", "Java:Chat");
                int asInt1 = GsonHelper.getAsInt(jsonObject, "hashesToDrop", -1);
                int asInt2 = GsonHelper.getAsInt(jsonObject, "maxConcurrentRequests", 7);
                JsonObject asJsonObject = GsonHelper.getAsJsonObject(jsonObject, "endpoints", null);
                String endpointFromConfig = getEndpointFromConfig(asJsonObject, "chat", "v1/chat");
                boolean flag = endpointFromConfig.equals("v1/chat");
                URL url = uri.resolve("/" + endpointFromConfig).toURL();
                URL endpoint = getEndpoint(uri, asJsonObject, "join", "v1/join");
                URL endpoint1 = getEndpoint(uri, asJsonObject, "leave", "v1/leave");
                LegacyTextFilter.JoinOrLeaveEncoder joinOrLeaveEncoder = profile -> {
                    JsonObject jsonObject1 = new JsonObject();
                    jsonObject1.addProperty("server", asString1);
                    jsonObject1.addProperty("room", asString2);
                    jsonObject1.addProperty("user_id", profile.id().toString());
                    jsonObject1.addProperty("user_display_name", profile.name());
                    return jsonObject1;
                };
                ServerTextFilter.MessageEncoder messageEncoder;
                if (flag) {
                    messageEncoder = (profile, message) -> {
                        JsonObject jsonObject1 = new JsonObject();
                        jsonObject1.addProperty("rule", asInt);
                        jsonObject1.addProperty("server", asString1);
                        jsonObject1.addProperty("room", asString2);
                        jsonObject1.addProperty("player", profile.id().toString());
                        jsonObject1.addProperty("player_display_name", profile.name());
                        jsonObject1.addProperty("text", message);
                        jsonObject1.addProperty("language", "*");
                        return jsonObject1;
                    };
                } else {
                    String string = String.valueOf(asInt);
                    messageEncoder = (profile, message) -> {
                        JsonObject jsonObject1 = new JsonObject();
                        jsonObject1.addProperty("rule_id", string);
                        jsonObject1.addProperty("category", asString1);
                        jsonObject1.addProperty("subcategory", asString2);
                        jsonObject1.addProperty("user_id", profile.id().toString());
                        jsonObject1.addProperty("user_display_name", profile.name());
                        jsonObject1.addProperty("text", message);
                        jsonObject1.addProperty("language", "*");
                        return jsonObject1;
                    };
                }

                ServerTextFilter.IgnoreStrategy ignoreStrategy = ServerTextFilter.IgnoreStrategy.select(asInt1);
                ExecutorService executorService = createWorkerPool(asInt2);
                String string1 = Base64.getEncoder().encodeToString(asString.getBytes(StandardCharsets.US_ASCII));
                return new LegacyTextFilter(
                    url, messageEncoder, endpoint, joinOrLeaveEncoder, endpoint1, joinOrLeaveEncoder, string1, ignoreStrategy, executorService
                );
            }
        } catch (Exception var20) {
            LOGGER.warn("Failed to parse chat filter config {}", config, var20);
            return null;
        }
    }

    @Override
    public TextFilter createContext(GameProfile profile) {
        return new ServerTextFilter.PlayerContext(profile) {
            @Override
            public void join() {
                LegacyTextFilter.this.processJoinOrLeave(
                    this.profile, LegacyTextFilter.this.joinEndpoint, LegacyTextFilter.this.joinEncoder, this.streamExecutor
                );
            }

            @Override
            public void leave() {
                LegacyTextFilter.this.processJoinOrLeave(
                    this.profile, LegacyTextFilter.this.leaveEndpoint, LegacyTextFilter.this.leaveEncoder, this.streamExecutor
                );
            }
        };
    }

    void processJoinOrLeave(GameProfile profile, URL endpoint, LegacyTextFilter.JoinOrLeaveEncoder encoder, Executor streamExecutor) {
        streamExecutor.execute(() -> {
            JsonObject jsonObject = encoder.encode(profile);

            try {
                this.processRequest(jsonObject, endpoint);
            } catch (Exception var6) {
                LOGGER.warn("Failed to send join/leave packet to {} for player {}", endpoint, profile, var6);
            }
        });
    }

    private void processRequest(JsonObject request, URL endpoint) throws IOException {
        HttpURLConnection httpUrlConnection = this.makeRequest(request, endpoint);

        try (InputStream inputStream = httpUrlConnection.getInputStream()) {
            this.drainStream(inputStream);
        }
    }

    @Override
    protected void setAuthorizationProperty(HttpURLConnection connection) {
        connection.setRequestProperty("Authorization", "Basic " + this.authKey);
    }

    @Override
    protected FilteredText filterText(String text, ServerTextFilter.IgnoreStrategy ignoreStrategy, JsonObject response) {
        boolean asBoolean = GsonHelper.getAsBoolean(response, "response", false);
        if (asBoolean) {
            return FilteredText.passThrough(text);
        } else {
            String asString = GsonHelper.getAsString(response, "hashed", null);
            if (asString == null) {
                return FilteredText.fullyFiltered(text);
            } else {
                JsonArray asJsonArray = GsonHelper.getAsJsonArray(response, "hashes");
                FilterMask filterMask = this.parseMask(text, asJsonArray, ignoreStrategy);
                return new FilteredText(text, filterMask);
            }
        }
    }

    @FunctionalInterface
    interface JoinOrLeaveEncoder {
        JsonObject encode(GameProfile profile);
    }
}
