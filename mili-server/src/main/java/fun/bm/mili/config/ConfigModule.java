package fun.bm.mili.config;

import fun.bm.mili.rust.TomlConfigData;
import me.earthme.luminol.config.IConfigModule;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public interface ConfigModule extends IConfigModule {
    void onLoaded(TomlConfigData configInstance);

    void onUnloaded(TomlConfigData configInstance);

    @Override
    default void onLoaded(TomlConfigData configInstance, @Nullable Set<Exception> e) {
        onLoaded(configInstance);
    }
}