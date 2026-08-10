package net.minecraft.server.players;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.server.notifications.NotificationService;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public abstract class StoredUserList<K, V extends StoredUserEntry<K>> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final File file;
    private final Map<String, V> map = Maps.newConcurrentMap(); // Paper - Use ConcurrentHashMap in StoredUserList
    protected final NotificationService notificationService;

    public StoredUserList(File file, NotificationService notificationService) {
        this.file = file;
        this.notificationService = notificationService;
    }

    public File getFile() {
        return this.file;
    }

    public boolean add(V entry) {
        String keyForUser = this.getKeyForUser(entry.getUser());
        V storedUserEntry = this.map.get(keyForUser);
        if (entry.equals(storedUserEntry)) {
            return false;
        } else {
            this.map.put(keyForUser, entry);

            try {
                this.save();
            } catch (IOException var5) {
                LOGGER.warn("Could not save the list after adding a user.", (Throwable)var5);
            }

            return true;
        }
    }

    public @Nullable V get(K user) {
        // Paper start - Use ConcurrentHashMap in StoredUserList
        return this.map.computeIfPresent(this.getKeyForUser(user), (key, value) -> {
            return value.hasExpired() ? null : value;
        });
        // Paper end - Use ConcurrentHashMap in StoredUserList
    }

    public boolean remove(K user) {
        V storedUserEntry = this.map.remove(this.getKeyForUser(user));
        if (storedUserEntry == null) {
            return false;
        } else {
            try {
                this.save();
            } catch (IOException var4) {
                LOGGER.warn("Could not save the list after removing a user.", (Throwable)var4);
            }

            return true;
        }
    }

    public boolean remove(StoredUserEntry<K> entry) {
        return this.remove(Objects.requireNonNull(entry.getUser()));
    }

    public void clear() {
        this.map.clear();

        try {
            this.save();
        } catch (IOException var2) {
            LOGGER.warn("Could not save the list after removing a user.", (Throwable)var2);
        }
    }

    public String[] getUserList() {
        return this.map.keySet().toArray(new String[0]);
    }

    public boolean isEmpty() {
        return this.map.isEmpty();
    }

    protected String getKeyForUser(K user) {
        return user.toString();
    }

    protected boolean contains(K entry) {
        this.removeExpired(); // CraftBukkit - SPIGOT-7589: Consistently remove expired entries to mirror .get(...)
        return this.map.containsKey(this.getKeyForUser(entry));
    }

    private void removeExpired() {
        this.map.values().removeIf(StoredUserEntry::hasExpired); // Paper - Use ConcurrentHashMap in StoredUserList
    }

    protected abstract StoredUserEntry<K> createEntry(JsonObject entryData);

    public Collection<V> getEntries() {
        return this.map.values();
    }

    public void save() throws IOException {
        synchronized (this) { // Folia - region threading
        this.removeExpired(); // Paper - remove expired values before saving
        JsonArray jsonArray = new JsonArray();
        this.map.values().stream().map(storedEntry -> Util.make(new JsonObject(), storedEntry::serialize)).forEach(jsonArray::add);

        try (BufferedWriter writer = Files.newWriter(this.file, StandardCharsets.UTF_8)) {
            GSON.toJson(jsonArray, GSON.newJsonWriter(writer));
        }
        } // Folia - region threading
    }

    public void load() throws IOException {
        synchronized (this) { // Folia - region threading
        if (this.file.exists()) {
            try (BufferedReader reader = Files.newReader(this.file, StandardCharsets.UTF_8)) {
                this.map.clear();
                JsonArray jsonArray = GSON.fromJson(reader, JsonArray.class);
                if (jsonArray == null) {
                    return;
                }

                for (JsonElement jsonElement : jsonArray) {
                    JsonObject jsonObject = GsonHelper.convertToJsonObject(jsonElement, "entry");
                    StoredUserEntry<K> storedUserEntry = this.createEntry(jsonObject);
                    if (storedUserEntry.getUser() != null) {
                        this.map.put(this.getKeyForUser(storedUserEntry.getUser()), (V)storedUserEntry);
                    }
                }
            // Spigot start
            } catch (com.google.gson.JsonParseException | NullPointerException ex) {
                File backup = new File(this.file + ".backup");
                LOGGER.warn("Unable to read file {}, backing it up to {} and creating new copy.", this.file, backup, ex);
                this.file.renameTo(backup);
                this.file.delete();
            }
            // Spigot end
        }
        } // Folia - region threading
    }
}
