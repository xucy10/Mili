package net.minecraft.server.jsonrpc.internalapi;

import java.util.Collection;
import java.util.Optional;
import net.minecraft.server.jsonrpc.methods.ClientInfo;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.ServerOpListEntry;

public interface MinecraftOperatorListService {
    Collection<ServerOpListEntry> getEntries();

    void op(NameAndId nameAndId, Optional<PermissionLevel> level, Optional<Boolean> bypassesPlayerLimit, ClientInfo client);

    void op(NameAndId nameAndId, ClientInfo client);

    void deop(NameAndId nameAndId, ClientInfo client);

    void clear(ClientInfo client);
}
