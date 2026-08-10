package net.minecraft.server.players;

import com.google.gson.JsonObject;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public abstract class BanListEntry<T> extends StoredUserEntry<T> {
    public static final ThreadLocal<SimpleDateFormat> DATE_FORMAT = ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.ROOT)); // Folia - region threading - SDF is not thread-safe
    public static final String EXPIRES_NEVER = "forever";
    protected final Date created;
    protected final String source;
    protected final @Nullable Date expires;
    protected final @Nullable String reason;

    public BanListEntry(@Nullable T user, @Nullable Date created, @Nullable String source, @Nullable Date expires, @Nullable String reason) {
        super(user);
        this.created = created == null ? new Date() : created;
        this.source = source == null ? "(Unknown)" : source;
        this.expires = expires;
        this.reason = reason;
    }

    protected BanListEntry(@Nullable T user, JsonObject entryData) {
        super(BanListEntry.checkExpiry(user, entryData)); // CraftBukkit

        Date date;
        try {
            date = entryData.has("created") ? DATE_FORMAT.get().parse(entryData.get("created").getAsString()) : new Date(); // Folia - region threading - SDF is not thread-safe
        } catch (ParseException var7) {
            date = new Date();
        }

        this.created = date;
        this.source = entryData.has("source") ? entryData.get("source").getAsString() : "(Unknown)";

        Date date1;
        try {
            date1 = entryData.has("expires") ? DATE_FORMAT.get().parse(entryData.get("expires").getAsString()) : null; // Folia - region threading - SDF is not thread-safe
        } catch (ParseException var6) {
            date1 = null;
        }

        this.expires = date1;
        this.reason = entryData.has("reason") ? entryData.get("reason").getAsString() : null;
    }

    public Date getCreated() {
        return this.created;
    }

    public String getSource() {
        return this.source;
    }

    public @Nullable Date getExpires() {
        return this.expires;
    }

    public @Nullable String getReason() {
        return this.reason;
    }

    public Component getReasonMessage() {
        String reason = this.getReason();
        return reason == null ? Component.translatable("multiplayer.disconnect.banned.reason.default") : Component.literal(reason);
    }

    public abstract Component getDisplayName();

    @Override
    boolean hasExpired() {
        return this.expires != null && this.expires.before(new Date());
    }

    @Override
    protected void serialize(JsonObject data) {
        data.addProperty("created", DATE_FORMAT.get().format(this.created)); // Folia - region threading - SDF is not thread-safe
        data.addProperty("source", this.source);
        data.addProperty("expires", this.expires == null ? "forever" : DATE_FORMAT.get().format(this.expires)); // Folia - region threading - SDF is not thread-safe
        data.addProperty("reason", this.reason);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else if (other != null && this.getClass() == other.getClass()) {
            BanListEntry<?> banListEntry = (BanListEntry<?>)other;
            return Objects.equals(this.source, banListEntry.source)
                && Objects.equals(this.expires, banListEntry.expires)
                && Objects.equals(this.reason, banListEntry.reason)
                && Objects.equals(this.getUser(), banListEntry.getUser());
        } else {
            return false;
        }
    }

    // CraftBukkit start
    private static <T> T checkExpiry(T object, JsonObject jsonobject) {
        Date expires = null;

        try {
            expires = jsonobject.has("expires") ? BanListEntry.DATE_FORMAT.get().parse(jsonobject.get("expires").getAsString()) : null; // Folia - region threading - SDF is not thread-safe
        } catch (ParseException ex) {
            // Guess we don't have a date
        }

        if (expires == null || expires.after(new Date())) {
            return object;
        } else {
            return null;
        }
    }
    // CraftBukkit end
}
