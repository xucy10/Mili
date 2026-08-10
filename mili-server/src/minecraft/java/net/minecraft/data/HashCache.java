package net.minecraft.data;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.mojang.logging.LogUtils;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.WorldVersion;
import org.apache.commons.lang3.mutable.MutableInt;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class HashCache {
    static final Logger LOGGER = LogUtils.getLogger();
    private static final String HEADER_MARKER = "// ";
    private final Path rootDir;
    private final Path cacheDir;
    private final String versionId;
    private final Map<String, HashCache.ProviderCache> caches;
    private final Set<String> cachesToWrite = new HashSet<>();
    final Set<Path> cachePaths = new HashSet<>();
    private final int initialCount;
    private int writes;

    private Path getProviderCachePath(String provider) {
        return this.cacheDir.resolve(Hashing.sha1().hashString(provider, StandardCharsets.UTF_8).toString());
    }

    public HashCache(Path rootDir, Collection<String> providers, WorldVersion version) throws IOException {
        this.versionId = version.id();
        this.rootDir = rootDir;
        this.cacheDir = rootDir.resolve(".cache");
        Files.createDirectories(this.cacheDir);
        Map<String, HashCache.ProviderCache> map = new HashMap<>();
        int i = 0;

        for (String string : providers) {
            Path providerCachePath = this.getProviderCachePath(string);
            this.cachePaths.add(providerCachePath);
            HashCache.ProviderCache cache = readCache(rootDir, providerCachePath);
            map.put(string, cache);
            i += cache.count();
        }

        this.caches = map;
        this.initialCount = i;
    }

    private static HashCache.ProviderCache readCache(Path rootDir, Path cachePath) {
        if (Files.isReadable(cachePath)) {
            try {
                return HashCache.ProviderCache.load(rootDir, cachePath);
            } catch (Exception var3) {
                LOGGER.warn("Failed to parse cache {}, discarding", cachePath, var3);
            }
        }

        return new HashCache.ProviderCache("unknown", ImmutableMap.of());
    }

    public boolean shouldRunInThisVersion(String provider) {
        HashCache.ProviderCache providerCache = this.caches.get(provider);
        return providerCache == null || !providerCache.version.equals(this.versionId);
    }

    public CompletableFuture<HashCache.UpdateResult> generateUpdate(String provider, HashCache.UpdateFunction updateFunction) {
        HashCache.ProviderCache providerCache = this.caches.get(provider);
        if (providerCache == null) {
            throw new IllegalStateException("Provider not registered: " + provider);
        } else {
            HashCache.CacheUpdater cacheUpdater = new HashCache.CacheUpdater(provider, this.versionId, providerCache);
            return updateFunction.update(cacheUpdater).thenApply(object -> cacheUpdater.close());
        }
    }

    public void applyUpdate(HashCache.UpdateResult updateResult) {
        this.caches.put(updateResult.providerId(), updateResult.cache());
        this.cachesToWrite.add(updateResult.providerId());
        this.writes = this.writes + updateResult.writes();
    }

    public void purgeStaleAndWrite() throws IOException {
        final Set<Path> set = new HashSet<>();
        this.caches.forEach((cacheName, providerCache) -> {
            if (this.cachesToWrite.contains(cacheName)) {
                Path providerCachePath = this.getProviderCachePath(cacheName);
                providerCache.save(this.rootDir, providerCachePath, DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(ZonedDateTime.now()) + "\t" + cacheName);
            }

            set.addAll(providerCache.data().keySet());
        });
        set.add(this.rootDir.resolve("version.json"));
        final MutableInt mutableInt = new MutableInt();
        final MutableInt mutableInt1 = new MutableInt();
        Files.walkFileTree(this.rootDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes basicFileAttributes) {
                if (HashCache.this.cachePaths.contains(path)) {
                    return FileVisitResult.CONTINUE;
                } else {
                    mutableInt.increment();
                    if (set.contains(path)) {
                        return FileVisitResult.CONTINUE;
                    } else {
                        try {
                            Files.delete(path);
                        } catch (IOException var4) {
                            HashCache.LOGGER.warn("Failed to delete file {}", path, var4);
                        }

                        mutableInt1.increment();
                        return FileVisitResult.CONTINUE;
                    }
                }
            }
        });
        LOGGER.info(
            "Caching: total files: {}, old count: {}, new count: {}, removed stale: {}, written: {}",
            mutableInt,
            this.initialCount,
            set.size(),
            mutableInt1,
            this.writes
        );
    }

    static class CacheUpdater implements CachedOutput {
        private final String provider;
        private final HashCache.ProviderCache oldCache;
        private final HashCache.ProviderCacheBuilder newCache;
        private final AtomicInteger writes = new AtomicInteger();
        private volatile boolean closed;

        CacheUpdater(String provider, String version, HashCache.ProviderCache oldCache) {
            this.provider = provider;
            this.oldCache = oldCache;
            this.newCache = new HashCache.ProviderCacheBuilder(version);
        }

        private boolean shouldWrite(Path key, HashCode value) {
            return !Objects.equals(this.oldCache.get(key), value) || !Files.exists(key);
        }

        @Override
        public void writeIfNeeded(Path filePath, byte[] data, HashCode hashCode) throws IOException {
            if (this.closed) {
                throw new IllegalStateException("Cannot write to cache as it has already been closed");
            } else {
                if (this.shouldWrite(filePath, hashCode)) {
                    this.writes.incrementAndGet();
                    Files.createDirectories(filePath.getParent());
                    Files.write(filePath, data);
                }

                this.newCache.put(filePath, hashCode);
            }
        }

        public HashCache.UpdateResult close() {
            this.closed = true;
            return new HashCache.UpdateResult(this.provider, this.newCache.build(), this.writes.get());
        }
    }

    record ProviderCache(String version, ImmutableMap<Path, HashCode> data) {
        public @Nullable HashCode get(Path path) {
            return this.data.get(path);
        }

        public int count() {
            return this.data.size();
        }

        public static HashCache.ProviderCache load(Path rootDir, Path cachePath) throws IOException {
            HashCache.ProviderCache var7;
            try (BufferedReader bufferedReader = Files.newBufferedReader(cachePath, StandardCharsets.UTF_8)) {
                String line = bufferedReader.readLine();
                if (!line.startsWith("// ")) {
                    throw new IllegalStateException("Missing cache file header");
                }

                String[] parts = line.substring("// ".length()).split("\t", 2);
                String string = parts[0];
                Builder<Path, HashCode> builder = ImmutableMap.builder();
                bufferedReader.lines().forEach(line1 -> {
                    int index = line1.indexOf(32);
                    builder.put(rootDir.resolve(line1.substring(index + 1)), HashCode.fromString(line1.substring(0, index)));
                });
                var7 = new HashCache.ProviderCache(string, builder.build());
            }

            return var7;
        }

        public void save(Path rootDir, Path cachePath, String date) {
            try (BufferedWriter bufferedWriter = Files.newBufferedWriter(cachePath, StandardCharsets.UTF_8)) {
                bufferedWriter.write("// ");
                bufferedWriter.write(this.version);
                bufferedWriter.write(9);
                bufferedWriter.write(date);
                bufferedWriter.newLine();

                for (Entry<Path, HashCode> entry : this.data.entrySet()) {
                    bufferedWriter.write(entry.getValue().toString());
                    bufferedWriter.write(32);
                    bufferedWriter.write(rootDir.relativize(entry.getKey()).toString());
                    bufferedWriter.newLine();
                }
            } catch (IOException var9) {
                HashCache.LOGGER.warn("Unable write cachefile {}: {}", cachePath, var9);
            }
        }
    }

    record ProviderCacheBuilder(String version, ConcurrentMap<Path, HashCode> data) {
        ProviderCacheBuilder(String version) {
            this(version, new ConcurrentHashMap<>());
        }

        public void put(Path key, HashCode value) {
            this.data.put(key, value);
        }

        public HashCache.ProviderCache build() {
            return new HashCache.ProviderCache(this.version, ImmutableMap.copyOf(this.data));
        }
    }

    @FunctionalInterface
    public interface UpdateFunction {
        CompletableFuture<?> update(CachedOutput output);
    }

    public record UpdateResult(String providerId, HashCache.ProviderCache cache, int writes) {
    }
}
