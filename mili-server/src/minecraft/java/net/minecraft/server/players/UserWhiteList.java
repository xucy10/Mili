package net.minecraft.server.players;

import com.google.gson.JsonObject;
import java.io.File;
import java.util.Objects;
import net.minecraft.server.notifications.NotificationService;

public class UserWhiteList extends StoredUserList<NameAndId, UserWhiteListEntry> {
    public UserWhiteList(File file, NotificationService notificationService) {
        super(file, notificationService);
    }

    @Override
    protected StoredUserEntry<NameAndId> createEntry(JsonObject entryData) {
        return new UserWhiteListEntry(entryData);
    }

    public boolean isWhiteListed(NameAndId nameAndId) {
        return this.contains(nameAndId);
    }

    @Override
    public boolean add(UserWhiteListEntry entry) {
        // Paper start - Add whitelist events
        if (!new io.papermc.paper.event.server.WhitelistStateUpdateEvent(com.destroystokyo.paper.profile.CraftPlayerProfile.asBukkitCopy(entry.getUser().toUncompletedGameProfile()), io.papermc.paper.event.server.WhitelistStateUpdateEvent.WhitelistStatus.ADDED).callEvent()) {
            return false;
        }
        // Paper end - Add whitelist events
        if (super.add(entry)) {
            if (entry.getUser() != null) {
                this.notificationService.playerAddedToAllowlist(entry.getUser());
            }

            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean remove(NameAndId user) {
        // Paper start - Add whitelist events
        if (!new io.papermc.paper.event.server.WhitelistStateUpdateEvent(com.destroystokyo.paper.profile.CraftPlayerProfile.asBukkitCopy(user.toUncompletedGameProfile()), io.papermc.paper.event.server.WhitelistStateUpdateEvent.WhitelistStatus.REMOVED).callEvent()) {
            return false;
        }
        // Paper end - Add whitelist events
        if (super.remove(user)) {
            this.notificationService.playerRemovedFromAllowlist(user);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void clear() {
        for (UserWhiteListEntry userWhiteListEntry : this.getEntries()) {
            if (userWhiteListEntry.getUser() != null) {
                this.notificationService.playerRemovedFromAllowlist(userWhiteListEntry.getUser());
            }
        }

        super.clear();
    }

    @Override
    public String[] getUserList() {
        return this.getEntries().stream().map(StoredUserEntry::getUser).filter(Objects::nonNull).map(NameAndId::name).toArray(String[]::new);
    }

    @Override
    protected String getKeyForUser(NameAndId user) {
        return user.id().toString();
    }
}
