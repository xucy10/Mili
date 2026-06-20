package net.minecraft.server.players;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.authlib.GameProfileRepository;
import com.mojang.logging.LogUtils;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import net.minecraft.util.StringUtil;
import org.slf4j.Logger;

public class CachedUserNameToIdResolver implements UserNameToIdResolver {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int GAMEPROFILES_MRU_LIMIT = 1000;
    private static final int GAMEPROFILES_EXPIRATION_MONTHS = 1;
    private boolean resolveOfflineUsers = true;
    private final Map<String, CachedUserNameToIdResolver.GameProfileInfo> profilesByName = new ConcurrentHashMap<>();
    private final Map<UUID, CachedUserNameToIdResolver.GameProfileInfo> profilesByUUID = new ConcurrentHashMap<>();
    private final GameProfileRepository profileRepository;
    private final Gson gson = new GsonBuilder().create();
    private final File file;
    private final AtomicLong operationCount = new AtomicLong();
    // Paper start - Fix GameProfileCache concurrency
    protected final java.util.concurrent.locks.ReentrantLock stateLock = new java.util.concurrent.locks.ReentrantLock();
    protected final java.util.concurrent.locks.ReentrantLock lookupLock = new java.util.concurrent.locks.ReentrantLock();
    // Paper end - Fix GameProfileCache concurrency

    public CachedUserNameToIdResolver(GameProfileRepository profileRepository, File file) {
        this.profileRepository = profileRepository;
        this.file = file;
        Lists.reverse(this.load()).forEach(this::safeAdd);
    }

    private void safeAdd(CachedUserNameToIdResolver.GameProfileInfo profileInfo) {
        try { this.stateLock.lock(); // Paper - Fix GameProfileCache concurrency
        NameAndId nameAndId = profileInfo.nameAndId();
        profileInfo.setLastAccess(this.getNextOperation());
        this.profilesByName.put(nameAndId.name().toLowerCase(Locale.ROOT), profileInfo);
        this.profilesByUUID.put(nameAndId.id(), profileInfo);
        } finally { this.stateLock.unlock(); } // Paper - Fix GameProfileCache concurrency
    }

    private Optional<NameAndId> lookupGameProfile(GameProfileRepository profileRepository, String name) {
        if (!StringUtil.isValidPlayerName(name, false)) {
            return this.createUnknownProfile(name);
        } else {
            final boolean shouldLookup = !org.apache.commons.lang3.StringUtils.isBlank(name)  // Paper - Don't lookup a profile with a blank name
                && io.papermc.paper.configuration.GlobalConfiguration.get().proxies.isProxyOnlineMode(); // Paper - Add setting for proxy online mode status
            Optional<NameAndId> optional = shouldLookup ? profileRepository.findProfileByName(name).map(NameAndId::new) : Optional.empty(); // Paper - Don't lookup a profile with a blank name
            return optional.isEmpty() ? this.createUnknownProfile(name) : optional;
        }
    }

    private Optional<NameAndId> createUnknownProfile(String name) {
        return !io.papermc.paper.configuration.GlobalConfiguration.get().proxies.isProxyOnlineMode() ? Optional.of(NameAndId.createOffline(name)) : Optional.empty(); // Paper - Add setting for proxy online mode status
    }

    @Override
    public void resolveOfflineUsers(boolean resolveOfflineUsers) {
        this.resolveOfflineUsers = resolveOfflineUsers;
    }

    @Override
    public void add(NameAndId nameAndId) {
        this.addInternal(nameAndId);
    }

    private CachedUserNameToIdResolver.GameProfileInfo addInternal(NameAndId nameAndId) {
        Calendar instance = Calendar.getInstance(TimeZone.getDefault(), Locale.ROOT);
        instance.setTime(new Date());
        instance.add(2, 1);
        Date time = instance.getTime();
        CachedUserNameToIdResolver.GameProfileInfo gameProfileInfo = new CachedUserNameToIdResolver.GameProfileInfo(nameAndId, time);
        this.safeAdd(gameProfileInfo);
        if (!org.spigotmc.SpigotConfig.saveUserCacheOnStopOnly) this.save(true); // Spigot - skip saving if disabled // Paper - Perf: Async GameProfileCache saving
        return gameProfileInfo;
    }

    private long getNextOperation() {
        return this.operationCount.incrementAndGet();
    }

    // Paper start
    @Override
    public @javax.annotation.Nullable NameAndId getIfCached(String name) {
        try { this.stateLock.lock(); // Paper - Fix GameProfileCache concurrency
        CachedUserNameToIdResolver.GameProfileInfo entry = this.profilesByName.get(name.toLowerCase(Locale.ROOT));
        if (entry == null) {
            return null;
        }
        entry.setLastAccess(this.getNextOperation());
        return entry.nameAndId();
        } finally { this.stateLock.unlock(); } // Paper - Fix GameProfileCache concurrency
    }
    // Paper end

    @Override
    public Optional<NameAndId> get(String name) {
        String string = name.toLowerCase(Locale.ROOT);
        boolean stateLocked = true; try { this.stateLock.lock(); // Paper - Fix GameProfileCache concurrency
        CachedUserNameToIdResolver.GameProfileInfo gameProfileInfo = this.profilesByName.get(string);
        boolean flag = false;
        if (gameProfileInfo != null && new Date().getTime() >= gameProfileInfo.expirationDate.getTime()) {
            this.profilesByUUID.remove(gameProfileInfo.nameAndId().id());
            this.profilesByName.remove(gameProfileInfo.nameAndId().name().toLowerCase(Locale.ROOT));
            flag = true;
            gameProfileInfo = null;
        }

        Optional<NameAndId> optional;
        if (gameProfileInfo != null) {
            gameProfileInfo.setLastAccess(this.getNextOperation());
            optional = Optional.of(gameProfileInfo.nameAndId());
            stateLocked = false; this.stateLock.unlock(); // Paper - Fix GameProfileCache concurrency
        } else {
            Optional<NameAndId> optional1;
            stateLocked = false; this.stateLock.unlock(); // Paper - Fix GameProfileCache concurrency
            try { this.lookupLock.lock(); // Paper - Fix GameProfileCache concurrency
                optional1 = this.lookupGameProfile(this.profileRepository, name); // CraftBukkit - use correct case for offline players
            } finally { this.lookupLock.unlock(); } // Paper - Fix GameProfileCache concurrency
            if (optional1.isPresent()) {
                optional = Optional.of(this.addInternal(optional1.get()).nameAndId());
                flag = false;
            } else {
                optional = Optional.empty();
            }
        }

        if (flag && !org.spigotmc.SpigotConfig.saveUserCacheOnStopOnly) { // Spigot - skip saving if disabled
            this.save(true); // Paper - Perf: Async GameProfileCache saving
        }

        return optional;
        } finally { if (stateLocked) { this.stateLock.unlock(); } } // Paper - Fix GameProfileCache concurrency
    }

    @Override
    public Optional<NameAndId> get(UUID uuid) {
        try { this.stateLock.lock(); // Paper - Fix GameProfileCache concurrency
        CachedUserNameToIdResolver.GameProfileInfo gameProfileInfo = this.profilesByUUID.get(uuid);
        if (gameProfileInfo == null) {
            return Optional.empty();
        } else {
            gameProfileInfo.setLastAccess(this.getNextOperation());
            return Optional.of(gameProfileInfo.nameAndId());
        }
        } finally { this.stateLock.unlock(); } // Paper - Fix GameProfileCache concurrency
    }

    private static DateFormat createDateFormat() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.ROOT);
    }

    private List<CachedUserNameToIdResolver.GameProfileInfo> load() {
        List<CachedUserNameToIdResolver.GameProfileInfo> list = Lists.newArrayList();

        try {
            Object var9;
            try (Reader reader = Files.newReader(this.file, StandardCharsets.UTF_8)) {
                JsonArray jsonArray = this.gson.fromJson(reader, JsonArray.class);
                if (jsonArray != null) {
                    DateFormat dateFormat = createDateFormat();
                    jsonArray.forEach(jsonElement -> readGameProfile(jsonElement, dateFormat).ifPresent(list::add));
                    return list;
                }

                var9 = list;
            }

            return (List<CachedUserNameToIdResolver.GameProfileInfo>)var9;
        } catch (FileNotFoundException var7) {
            // Spigot start
        } catch (com.google.gson.JsonSyntaxException | NullPointerException ex) {
            LOGGER.warn("Usercache.json is corrupted or has bad formatting. Deleting it to prevent further issues.");
            this.file.delete();
            // Spigot end
        } catch (JsonParseException | IOException var8) {
            LOGGER.warn("Failed to load profile cache {}", this.file, var8);
        }

        return list;
    }

    @Override
    public void save() {
        // Paper start - Perf: Async GameProfileCache saving
        this.save(false);
    }

    @Override
    public void save(boolean asyncSave) {
        // Paper end - Perf: Async GameProfileCache saving
        JsonArray jsonArray = new JsonArray();
        DateFormat dateFormat = createDateFormat();
        this.listTopMRUProfiles(org.spigotmc.SpigotConfig.userCacheCap).forEach(gameProfileInfo -> jsonArray.add(writeGameProfile(gameProfileInfo, dateFormat))); // Spigot // Paper - Fix GameProfileCache concurrency
        String string = this.gson.toJson((JsonElement)jsonArray);

        Runnable save = () -> { // Paper - Perf: Async GameProfileCache saving
        try (Writer writer = Files.newWriter(this.file, StandardCharsets.UTF_8)) {
            writer.write(string);
        } catch (IOException var9) {
        }
        // Paper start - Perf: Async GameProfileCache saving
        };
        if (asyncSave) {
            io.papermc.paper.util.MCUtil.scheduleAsyncTask(save);
        } else {
            save.run();
        }
        // Paper end - Perf: Async GameProfileCache saving
    }

    private Stream<CachedUserNameToIdResolver.GameProfileInfo> getTopMRUProfiles(int limit) {
        // Paper start - Fix GameProfileCache concurrency
        return this.listTopMRUProfiles(limit).stream();
    }

    private List<CachedUserNameToIdResolver.GameProfileInfo> listTopMRUProfiles(int limit) {
        try {
            this.stateLock.lock();
            return this.profilesByUUID.values()
                .stream()
                .sorted(Comparator.comparing(CachedUserNameToIdResolver.GameProfileInfo::lastAccess).reversed())
                .limit(limit)
                .toList();
        } finally {
            this.stateLock.unlock();
        }
    }
    // Paper end - Fix GameProfileCache concurrency

    private static JsonElement writeGameProfile(CachedUserNameToIdResolver.GameProfileInfo profileInfo, DateFormat format) {
        JsonObject jsonObject = new JsonObject();
        profileInfo.nameAndId().appendTo(jsonObject);
        jsonObject.addProperty("expiresOn", format.format(profileInfo.expirationDate()));
        return jsonObject;
    }

    private static Optional<CachedUserNameToIdResolver.GameProfileInfo> readGameProfile(JsonElement element, DateFormat format) {
        if (element.isJsonObject()) {
            JsonObject asJsonObject = element.getAsJsonObject();
            NameAndId nameAndId = NameAndId.fromJson(asJsonObject);
            if (nameAndId != null) {
                JsonElement jsonElement = asJsonObject.get("expiresOn");
                if (jsonElement != null) {
                    String asString = jsonElement.getAsString();

                    try {
                        Date date = format.parse(asString);
                        return Optional.of(new CachedUserNameToIdResolver.GameProfileInfo(nameAndId, date));
                    } catch (ParseException var7) {
                        LOGGER.warn("Failed to parse date {}", asString, var7);
                    }
                }
            }
        }

        return Optional.empty();
    }

    static class GameProfileInfo {
        private final NameAndId nameAndId;
        final Date expirationDate;
        private volatile long lastAccess;

        GameProfileInfo(NameAndId nameAndId, Date expirationDate) {
            this.nameAndId = nameAndId;
            this.expirationDate = expirationDate;
        }

        public NameAndId nameAndId() {
            return this.nameAndId;
        }

        public Date expirationDate() {
            return this.expirationDate;
        }

        public void setLastAccess(long lastAccess) {
            this.lastAccess = lastAccess;
        }

        public long lastAccess() {
            return this.lastAccess;
        }
    }
}
