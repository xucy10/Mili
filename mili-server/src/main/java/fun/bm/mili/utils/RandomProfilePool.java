package fun.bm.mili.utils;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.authlib.GameProfile;
import fun.bm.mili.config.modules.function.ReplayAPIConfig;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class RandomProfilePool {
    private static int lastUsedId = 0;
    private static final Object lock = new Object();
    private static Cache<Integer, GameProfile> cache;
    private static final Object lockCache = new Object();

    public static void init() {
        if (ReplayAPIConfig.enableCache) {
            cache = CacheBuilder.newBuilder()
                    .maximumSize(ReplayAPIConfig.cachePhotographerSize)
                    .expireAfterWrite(ReplayAPIConfig.cachePhotographerTime, TimeUnit.SECONDS)
                    .build();
        }
    }

    public static GameProfile getRandomProfile(String id) {
        // Mili start - fix: add cache != null check to prevent NPE if enableCache toggled after init
        if (ReplayAPIConfig.enableCache && cache != null) {
        // Mili end
            synchronized (lockCache) {
                Map<Integer, GameProfile> snapshot = cache.asMap();
                if (!snapshot.isEmpty()) {
                    Map.Entry<Integer, GameProfile> entry = snapshot.entrySet().iterator().next();
                    cache.invalidate(entry.getKey());
                    GameProfile gp = entry.getValue();
                    return new GameProfile(gp.id(), id, gp.properties());
                }
            }
        }
        return new GameProfile(UUID.randomUUID(), id);
    }

    public static void putProfile(GameProfile profile) {
        // Mili start - fix: add cache != null check to prevent NPE if enableCache toggled after init
        if (ReplayAPIConfig.enableCache && cache != null) {
        // Mili end
            synchronized (lockCache) {
                cache.put(getNextId(), profile);
            }
        }
    }

    private static int getNextId() {
        synchronized (lock) {
            int newId = lastUsedId + 1;
            int iterations = 0;
            while (cache.getIfPresent(newId) != null && iterations < 10_000) {
                newId++;
                iterations++;
            }
            lastUsedId = newId;
            return newId;
        }
    }
}