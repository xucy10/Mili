package net.minecraft.server.players;

import com.google.gson.JsonObject;
import java.io.File;
import java.net.SocketAddress;
import net.minecraft.server.notifications.NotificationService;
import org.jspecify.annotations.Nullable;

public class IpBanList extends StoredUserList<String, IpBanListEntry> {
    public IpBanList(File file, NotificationService notificationService) {
        super(file, notificationService);
    }

    @Override
    protected StoredUserEntry<String> createEntry(JsonObject entryData) {
        return new IpBanListEntry(entryData);
    }

    public boolean isBanned(SocketAddress address) {
        String ipFromAddress = this.getIpFromAddress(address);
        return this.contains(ipFromAddress);
    }

    public boolean isBanned(String address) {
        return this.contains(address);
    }

    public @Nullable IpBanListEntry get(SocketAddress address) {
        String ipFromAddress = this.getIpFromAddress(address);
        return this.get(ipFromAddress);
    }

    private String getIpFromAddress(SocketAddress address) {
        String string = address.toString();
        if (string.contains("/")) {
            string = string.substring(string.indexOf(47) + 1);
        }

        if (string.contains(":")) {
            string = string.substring(0, string.indexOf(58));
        }

        return string;
    }

    @Override
    public boolean add(IpBanListEntry entry) {
        if (super.add(entry)) {
            if (entry.getUser() != null) {
                this.notificationService.ipBanned(entry);
            }

            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean remove(String user) {
        if (super.remove(user)) {
            this.notificationService.ipUnbanned(user);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void clear() {
        for (IpBanListEntry ipBanListEntry : this.getEntries()) {
            if (ipBanListEntry.getUser() != null) {
                this.notificationService.ipUnbanned(ipBanListEntry.getUser());
            }
        }

        super.clear();
    }
}
