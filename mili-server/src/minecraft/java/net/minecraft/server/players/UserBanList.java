package net.minecraft.server.players;

import com.google.gson.JsonObject;
import java.io.File;
import java.util.Objects;
import net.minecraft.server.notifications.NotificationService;

public class UserBanList extends StoredUserList<NameAndId, UserBanListEntry> {
    public UserBanList(File file, NotificationService notificationService) {
        super(file, notificationService);
    }

    @Override
    protected StoredUserEntry<NameAndId> createEntry(JsonObject entryData) {
        return new UserBanListEntry(entryData);
    }

    public boolean isBanned(NameAndId nameAndId) {
        return this.contains(nameAndId);
    }

    @Override
    public String[] getUserList() {
        return this.getEntries().stream().map(StoredUserEntry::getUser).filter(Objects::nonNull).map(NameAndId::name).toArray(String[]::new);
    }

    @Override
    protected String getKeyForUser(NameAndId user) {
        return user.id().toString();
    }

    @Override
    public boolean add(UserBanListEntry entry) {
        if (super.add(entry)) {
            if (entry.getUser() != null) {
                this.notificationService.playerBanned(entry);
            }

            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean remove(NameAndId user) {
        if (super.remove(user)) {
            this.notificationService.playerUnbanned(user);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void clear() {
        for (UserBanListEntry userBanListEntry : this.getEntries()) {
            if (userBanListEntry.getUser() != null) {
                this.notificationService.playerUnbanned(userBanListEntry.getUser());
            }
        }

        super.clear();
    }
}
