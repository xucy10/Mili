package net.minecraft.server.players;

import com.google.gson.JsonObject;
import java.io.File;
import java.util.Objects;
import net.minecraft.server.notifications.NotificationService;

public class ServerOpList extends StoredUserList<NameAndId, ServerOpListEntry> {
    public ServerOpList(File file, NotificationService notificationService) {
        super(file, notificationService);
    }

    @Override
    protected StoredUserEntry<NameAndId> createEntry(JsonObject entryData) {
        return new ServerOpListEntry(entryData);
    }

    @Override
    public String[] getUserList() {
        return this.getEntries().stream().map(StoredUserEntry::getUser).filter(Objects::nonNull).map(NameAndId::name).toArray(String[]::new);
    }

    @Override
    public boolean add(ServerOpListEntry entry) {
        if (super.add(entry)) {
            if (entry.getUser() != null) {
                this.notificationService.playerOped(entry);
            }

            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean remove(NameAndId user) {
        ServerOpListEntry serverOpListEntry = this.get(user);
        if (super.remove(user)) {
            if (serverOpListEntry != null) {
                this.notificationService.playerDeoped(serverOpListEntry);
            }

            return true;
        } else {
            return false;
        }
    }

    @Override
    public void clear() {
        for (ServerOpListEntry serverOpListEntry : this.getEntries()) {
            if (serverOpListEntry.getUser() != null) {
                this.notificationService.playerDeoped(serverOpListEntry);
            }
        }

        super.clear();
    }

    public boolean canBypassPlayerLimit(NameAndId nameAndId) {
        ServerOpListEntry serverOpListEntry = this.get(nameAndId);
        return serverOpListEntry != null && serverOpListEntry.getBypassesPlayerLimit();
    }

    @Override
    protected String getKeyForUser(NameAndId user) {
        return user.id().toString();
    }
}
