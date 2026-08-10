package net.minecraft.server.players;

import java.util.Optional;
import java.util.UUID;

public interface UserNameToIdResolver {
    void add(NameAndId nameAndId);

    Optional<NameAndId> get(String name);

    Optional<NameAndId> get(UUID uuid);

    void resolveOfflineUsers(boolean resolveOfflineUsers);

    void save();

    void save(boolean async); // Paper

    @javax.annotation.Nullable NameAndId getIfCached(String name); // Paper
}
