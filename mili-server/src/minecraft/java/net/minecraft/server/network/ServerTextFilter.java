package net.minecraft.server.network;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonWriter;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.FilterMask;
import net.minecraft.server.dedicated.DedicatedServerProperties;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.LenientJsonParser;
import net.minecraft.util.StringUtil;
import net.minecraft.util.Util;
import net.minecraft.util.thread.ConsecutiveExecutor;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public abstract class ServerTextFilter implements AutoCloseable {
    protected static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicInteger WORKER_COUNT = new AtomicInteger(1);
    private static final ThreadFactory THREAD_FACTORY = runnable -> {
        Thread thread = new Thread(runnable);
        thread.setName("Chat-Filter-Worker-" + WORKER_COUNT.getAndIncrement());
        return thread;
    };
    private final URL chatEndpoint;
    private final ServerTextFilter.MessageEncoder chatEncoder;
    final ServerTextFilter.IgnoreStrategy chatIgnoreStrategy;
    final ExecutorService workerPool;

    protected static ExecutorService createWorkerPool(int size) {
        return Executors.newFixedThreadPool(size, THREAD_FACTORY);
    }

    protected ServerTextFilter(
        URL chatEndpoint, ServerTextFilter.MessageEncoder chatEncoder, ServerTextFilter.IgnoreStrategy chatIgnoreStrategy, ExecutorService workerPool
    ) {
        this.chatIgnoreStrategy = chatIgnoreStrategy;
        this.workerPool = workerPool;
        this.chatEndpoint = chatEndpoint;
        this.chatEncoder = chatEncoder;
    }

    protected static URL getEndpoint(URI apiServer, @Nullable JsonObject json, String key, String fallback) throws MalformedURLException {
        String endpointFromConfig = getEndpointFromConfig(json, key, fallback);
        return apiServer.resolve("/" + endpointFromConfig).toURL();
    }

    protected static String getEndpointFromConfig(@Nullable JsonObject json, String key, String fallback) {
        return json != null ? GsonHelper.getAsString(json, key, fallback) : fallback;
    }

    public static @Nullable ServerTextFilter createFromConfig(DedicatedServerProperties config) {
        String string = config.textFilteringConfig;
        if (StringUtil.isBlank(string)) {
            return null;
        } else {
            return switch (config.textFilteringVersion) {
                case 0 -> LegacyTextFilter.createTextFilterFromConfig(string);
                case 1 -> PlayerSafetyServiceTextFilter.createTextFilterFromConfig(string);
                default -> {
                    LOGGER.warn("Could not create text filter - unsupported text filtering version used");
                    yield null;
                }
            };
        }
    }

    protected CompletableFuture<FilteredText> requestMessageProcessing(
        GameProfile profile, String filter, ServerTextFilter.IgnoreStrategy chatIgnoreStrategy, Executor streamExecutor
    ) {
        return filter.isEmpty() ? CompletableFuture.completedFuture(FilteredText.EMPTY) : CompletableFuture.supplyAsync(() -> {
            JsonObject jsonObject = this.chatEncoder.encode(profile, filter);

            try {
                JsonObject jsonObject1 = this.processRequestResponse(jsonObject, this.chatEndpoint);
                return this.filterText(filter, chatIgnoreStrategy, jsonObject1);
            } catch (Exception var6) {
                LOGGER.warn("Failed to validate message '{}'", filter, var6);
                return FilteredText.fullyFiltered(filter);
            }
        }, streamExecutor);
    }

    protected abstract FilteredText filterText(String text, ServerTextFilter.IgnoreStrategy ignoreStrategy, JsonObject response);

    protected FilterMask parseMask(String text, JsonArray hashes, ServerTextFilter.IgnoreStrategy ignoreStrategy) {
        if (hashes.isEmpty()) {
            return FilterMask.PASS_THROUGH;
        } else if (ignoreStrategy.shouldIgnore(text, hashes.size())) {
            return FilterMask.FULLY_FILTERED;
        } else {
            FilterMask filterMask = new FilterMask(text.length());

            for (int i = 0; i < hashes.size(); i++) {
                filterMask.setFiltered(hashes.get(i).getAsInt());
            }

            return filterMask;
        }
    }

    @Override
    public void close() {
        this.workerPool.shutdownNow();
    }

    protected void drainStream(InputStream stream) throws IOException {
        byte[] bytes = new byte[1024];

        while (stream.read(bytes) != -1) {
        }
    }

    private JsonObject processRequestResponse(JsonObject request, URL endpoint) throws IOException {
        HttpURLConnection httpUrlConnection = this.makeRequest(request, endpoint);

        JsonObject var5;
        try (InputStream inputStream = httpUrlConnection.getInputStream()) {
            if (httpUrlConnection.getResponseCode() == 204) {
                return new JsonObject();
            }

            try {
                var5 = LenientJsonParser.parse(new InputStreamReader(inputStream, StandardCharsets.UTF_8)).getAsJsonObject();
            } finally {
                this.drainStream(inputStream);
            }
        }

        return var5;
    }

    protected HttpURLConnection makeRequest(JsonObject request, URL endpoint) throws IOException {
        HttpURLConnection urlConnection = this.getURLConnection(endpoint);
        this.setAuthorizationProperty(urlConnection);
        OutputStreamWriter outputStreamWriter = new OutputStreamWriter(urlConnection.getOutputStream(), StandardCharsets.UTF_8);

        try (JsonWriter jsonWriter = new JsonWriter(outputStreamWriter)) {
            Streams.write(request, jsonWriter);
        } catch (Throwable var11) {
            try {
                outputStreamWriter.close();
            } catch (Throwable var8) {
                var11.addSuppressed(var8);
            }

            throw var11;
        }

        outputStreamWriter.close();
        int responseCode = urlConnection.getResponseCode();
        if (responseCode >= 200 && responseCode < 300) {
            return urlConnection;
        } else {
            throw new ServerTextFilter.RequestFailedException(responseCode + " " + urlConnection.getResponseMessage());
        }
    }

    protected abstract void setAuthorizationProperty(HttpURLConnection connection);

    protected int connectionReadTimeout() {
        return 2000;
    }

    protected HttpURLConnection getURLConnection(URL url) throws IOException {
        HttpURLConnection httpUrlConnection = (HttpURLConnection)url.openConnection();
        httpUrlConnection.setConnectTimeout(15000);
        httpUrlConnection.setReadTimeout(this.connectionReadTimeout());
        httpUrlConnection.setUseCaches(false);
        httpUrlConnection.setDoOutput(true);
        httpUrlConnection.setDoInput(true);
        httpUrlConnection.setRequestMethod("POST");
        httpUrlConnection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        httpUrlConnection.setRequestProperty("Accept", "application/json");
        httpUrlConnection.setRequestProperty("User-Agent", "Minecraft server" + SharedConstants.getCurrentVersion().name());
        return httpUrlConnection;
    }

    public TextFilter createContext(GameProfile profile) {
        return new ServerTextFilter.PlayerContext(profile);
    }

    @FunctionalInterface
    public interface IgnoreStrategy {
        ServerTextFilter.IgnoreStrategy NEVER_IGNORE = (text, numHashes) -> false;
        ServerTextFilter.IgnoreStrategy IGNORE_FULLY_FILTERED = (text, numHashes) -> text.length() == numHashes;

        static ServerTextFilter.IgnoreStrategy ignoreOverThreshold(int threshold) {
            return (text, numHashes) -> numHashes >= threshold;
        }

        static ServerTextFilter.IgnoreStrategy select(int threshold) {
            return switch (threshold) {
                case -1 -> NEVER_IGNORE;
                case 0 -> IGNORE_FULLY_FILTERED;
                default -> ignoreOverThreshold(threshold);
            };
        }

        boolean shouldIgnore(String text, int numHashes);
    }

    @FunctionalInterface
    protected interface MessageEncoder {
        JsonObject encode(GameProfile profile, String message);
    }

    protected class PlayerContext implements TextFilter {
        protected final GameProfile profile;
        protected final Executor streamExecutor;

        protected PlayerContext(final GameProfile profile) {
            this.profile = profile;
            ConsecutiveExecutor consecutiveExecutor = new ConsecutiveExecutor(ServerTextFilter.this.workerPool, "chat stream for " + profile.name());
            this.streamExecutor = consecutiveExecutor::schedule;
        }

        @Override
        public CompletableFuture<List<FilteredText>> processMessageBundle(List<String> texts) {
            List<CompletableFuture<FilteredText>> list = texts.stream()
                .map(
                    string -> ServerTextFilter.this.requestMessageProcessing(
                        this.profile, string, ServerTextFilter.this.chatIgnoreStrategy, this.streamExecutor
                    )
                )
                .collect(ImmutableList.toImmutableList());
            return Util.sequenceFailFast(list).exceptionally(throwable -> ImmutableList.of());
        }

        @Override
        public CompletableFuture<FilteredText> processStreamMessage(String text) {
            return ServerTextFilter.this.requestMessageProcessing(this.profile, text, ServerTextFilter.this.chatIgnoreStrategy, this.streamExecutor);
        }
    }

    protected static class RequestFailedException extends RuntimeException {
        protected RequestFailedException(String message) {
            super(message);
        }
    }
}
