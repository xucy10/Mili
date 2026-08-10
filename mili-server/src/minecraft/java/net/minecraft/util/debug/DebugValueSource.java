package net.minecraft.util.debug;

import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

public interface DebugValueSource {
    void registerDebugValues(ServerLevel level, DebugValueSource.Registration registrar);

    public interface Registration {
        <T> void register(DebugSubscription<T> subscription, DebugValueSource.ValueGetter<T> getter);
    }

    public interface ValueGetter<T> {
        @Nullable T get();
    }
}
