package net.minecraft.server.jsonrpc.internalapi;

import java.util.Collection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.jsonrpc.methods.ClientInfo;
import net.minecraft.server.level.ServerPlayer;

public interface MinecraftServerStateService {
    boolean isReady();

    boolean saveEverything(boolean suppressLogs, boolean flush, boolean force, ClientInfo client);

    void halt(boolean waitForShutdown, ClientInfo client);

    void sendSystemMessage(Component message, ClientInfo client);

    void sendSystemMessage(Component message, boolean overlay, Collection<ServerPlayer> players, ClientInfo client);

    void broadcastSystemMessage(Component message, boolean overlay, ClientInfo client);
}
