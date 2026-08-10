package net.minecraft.server.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.microsoft.aad.msal4j.ClientCredentialFactory;
import com.microsoft.aad.msal4j.ClientCredentialParameters;
import com.microsoft.aad.msal4j.ConfidentialClientApplication;
import com.microsoft.aad.msal4j.IAuthenticationResult;
import com.microsoft.aad.msal4j.IClientCertificate;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import net.minecraft.util.GsonHelper;
import org.jspecify.annotations.Nullable;

public class PlayerSafetyServiceTextFilter extends ServerTextFilter {
    private final ConfidentialClientApplication client;
    private final ClientCredentialParameters clientParameters;
    private final Set<String> fullyFilteredEvents;
    private final int connectionReadTimeoutMs;

    private PlayerSafetyServiceTextFilter(
        URL chatEndpoint,
        ServerTextFilter.MessageEncoder chatEncoder,
        ServerTextFilter.IgnoreStrategy chatIgnoreStrategy,
        ExecutorService workerPool,
        ConfidentialClientApplication client,
        ClientCredentialParameters clientParameters,
        Set<String> fullyFilteredEvents,
        int connectionReadTimeoutMs
    ) {
        super(chatEndpoint, chatEncoder, chatIgnoreStrategy, workerPool);
        this.client = client;
        this.clientParameters = clientParameters;
        this.fullyFilteredEvents = fullyFilteredEvents;
        this.connectionReadTimeoutMs = connectionReadTimeoutMs;
    }

    public static @Nullable ServerTextFilter createTextFilterFromConfig(String config) {
        JsonObject jsonObject = GsonHelper.parse(config);
        URI uri = URI.create(GsonHelper.getAsString(jsonObject, "apiServer"));
        String asString = GsonHelper.getAsString(jsonObject, "apiPath");
        String asString1 = GsonHelper.getAsString(jsonObject, "scope");
        String asString2 = GsonHelper.getAsString(jsonObject, "serverId", "");
        String asString3 = GsonHelper.getAsString(jsonObject, "applicationId");
        String asString4 = GsonHelper.getAsString(jsonObject, "tenantId");
        String asString5 = GsonHelper.getAsString(jsonObject, "roomId", "Java:Chat");
        String asString6 = GsonHelper.getAsString(jsonObject, "certificatePath");
        String asString7 = GsonHelper.getAsString(jsonObject, "certificatePassword", "");
        int asInt = GsonHelper.getAsInt(jsonObject, "hashesToDrop", -1);
        int asInt1 = GsonHelper.getAsInt(jsonObject, "maxConcurrentRequests", 7);
        JsonArray asJsonArray = GsonHelper.getAsJsonArray(jsonObject, "fullyFilteredEvents");
        Set<String> set = new HashSet<>();
        asJsonArray.forEach(jsonElement -> set.add(GsonHelper.convertToString(jsonElement, "filteredEvent")));
        int asInt2 = GsonHelper.getAsInt(jsonObject, "connectionReadTimeoutMs", 2000);

        URL url;
        try {
            url = uri.resolve(asString).toURL();
        } catch (MalformedURLException var26) {
            throw new RuntimeException(var26);
        }

        ServerTextFilter.MessageEncoder messageEncoder = (profile, message) -> {
            JsonObject jsonObject1 = new JsonObject();
            jsonObject1.addProperty("userId", profile.id().toString());
            jsonObject1.addProperty("userDisplayName", profile.name());
            jsonObject1.addProperty("server", asString2);
            jsonObject1.addProperty("room", asString5);
            jsonObject1.addProperty("area", "JavaChatRealms");
            jsonObject1.addProperty("data", message);
            jsonObject1.addProperty("language", "*");
            return jsonObject1;
        };
        ServerTextFilter.IgnoreStrategy ignoreStrategy = ServerTextFilter.IgnoreStrategy.select(asInt);
        ExecutorService executorService = createWorkerPool(asInt1);

        IClientCertificate iClientCertificate;
        try (InputStream inputStream = Files.newInputStream(Path.of(asString6))) {
            iClientCertificate = ClientCredentialFactory.createFromCertificate(inputStream, asString7);
        } catch (Exception var28) {
            LOGGER.warn("Failed to open certificate file");
            return null;
        }

        ConfidentialClientApplication confidentialClientApplication;
        try {
            confidentialClientApplication = ConfidentialClientApplication.builder(asString3, iClientCertificate)
                .sendX5c(true)
                .executorService(executorService)
                .authority(String.format(Locale.ROOT, "https://login.microsoftonline.com/%s/", asString4))
                .build();
        } catch (Exception var25) {
            LOGGER.warn("Failed to create confidential client application");
            return null;
        }

        ClientCredentialParameters clientCredentialParameters = ClientCredentialParameters.builder(Set.of(asString1)).build();
        return new PlayerSafetyServiceTextFilter(
            url, messageEncoder, ignoreStrategy, executorService, confidentialClientApplication, clientCredentialParameters, set, asInt2
        );
    }

    private IAuthenticationResult aquireIAuthenticationResult() {
        return this.client.acquireToken(this.clientParameters).join();
    }

    @Override
    protected void setAuthorizationProperty(HttpURLConnection connection) {
        IAuthenticationResult iAuthenticationResult = this.aquireIAuthenticationResult();
        connection.setRequestProperty("Authorization", "Bearer " + iAuthenticationResult.accessToken());
    }

    @Override
    protected FilteredText filterText(String text, ServerTextFilter.IgnoreStrategy ignoreStrategy, JsonObject response) {
        JsonObject asJsonObject = GsonHelper.getAsJsonObject(response, "result", null);
        if (asJsonObject == null) {
            return FilteredText.fullyFiltered(text);
        } else {
            boolean asBoolean = GsonHelper.getAsBoolean(asJsonObject, "filtered", true);
            if (!asBoolean) {
                return FilteredText.passThrough(text);
            } else {
                for (JsonElement jsonElement : GsonHelper.getAsJsonArray(asJsonObject, "events", new JsonArray())) {
                    JsonObject asJsonObject1 = jsonElement.getAsJsonObject();
                    String asString = GsonHelper.getAsString(asJsonObject1, "id", "");
                    if (this.fullyFilteredEvents.contains(asString)) {
                        return FilteredText.fullyFiltered(text);
                    }
                }

                JsonArray asJsonArray1 = GsonHelper.getAsJsonArray(asJsonObject, "redactedTextIndex", new JsonArray());
                return new FilteredText(text, this.parseMask(text, asJsonArray1, ignoreStrategy));
            }
        }
    }

    @Override
    protected int connectionReadTimeout() {
        return this.connectionReadTimeoutMs;
    }
}
