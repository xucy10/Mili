package net.minecraft.server.jsonrpc.internalapi;

import java.util.Collection;
import net.minecraft.server.jsonrpc.methods.ClientInfo;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.UserWhiteListEntry;

public interface MinecraftAllowListService {
    Collection<UserWhiteListEntry> getEntries();

    boolean add(UserWhiteListEntry entry, ClientInfo client);

    void clear(ClientInfo client);

    void remove(NameAndId nameAndId, ClientInfo client);

    void kickUnlistedPlayers(ClientInfo client);
}
