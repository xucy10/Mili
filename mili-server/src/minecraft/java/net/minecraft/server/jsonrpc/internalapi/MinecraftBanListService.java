package net.minecraft.server.jsonrpc.internalapi;

import java.util.Collection;
import net.minecraft.server.jsonrpc.methods.ClientInfo;
import net.minecraft.server.players.IpBanListEntry;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.UserBanListEntry;

public interface MinecraftBanListService {
    void addUserBan(UserBanListEntry entry, ClientInfo client);

    void removeUserBan(NameAndId nameAndId, ClientInfo client);

    Collection<UserBanListEntry> getUserBanEntries();

    Collection<IpBanListEntry> getIpBanEntries();

    void addIpBan(IpBanListEntry entry, ClientInfo client);

    void clearIpBans(ClientInfo client);

    void removeIpBan(String ip, ClientInfo client);

    void clearUserBans(ClientInfo client);
}
