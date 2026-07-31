package me.earthme.luminol.config;

import fun.bm.mili.rust.TomlConfigData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public interface IConfigModule {
    default void beforeFinalLoad() {
    }

    default void onLoaded(TomlConfigData configInstance, @Nullable Set<Exception> e) {
    }

    default void onUnloaded(TomlConfigData configInstance) {
    }

    default <T> T get(String keyName, T defaultValue, @NotNull TomlConfigData config) {
        if (!config.contains(keyName)) {
            config.set(keyName, defaultValue);
            return defaultValue;
        }

        return config.get(keyName);
    }
}