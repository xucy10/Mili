package net.minecraft.server.players;

import com.google.gson.JsonObject;
import java.util.Date;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public class IpBanListEntry extends BanListEntry<String> {
    public IpBanListEntry(String ip) {
        this(ip, null, null, null, null);
    }

    public IpBanListEntry(String ip, @Nullable Date created, @Nullable String source, @Nullable Date expires, @Nullable String reason) {
        super(ip, created, source, expires, reason);
    }

    @Override
    public Component getDisplayName() {
        return Component.literal(String.valueOf(this.getUser()));
    }

    public IpBanListEntry(JsonObject entryData) {
        super(createIpInfo(entryData), entryData);
    }

    private static String createIpInfo(JsonObject json) {
        return json.has("ip") ? json.get("ip").getAsString() : null;
    }

    @Override
    protected void serialize(JsonObject data) {
        if (this.getUser() != null) {
            data.addProperty("ip", this.getUser());
            super.serialize(data);
        }
    }
}
